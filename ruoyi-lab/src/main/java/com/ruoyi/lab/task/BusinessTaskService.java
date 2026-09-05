package com.ruoyi.lab.task;

import java.io.*;
import java.time.LocalDateTime;
import java.util.*;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.lab.exception.*;
import com.ruoyi.lab.mapper.BusinessTaskMapper;
import com.ruoyi.lab.storage.StorageService;
import com.ruoyi.lab.serializer.LabBusinessId;
import com.ruoyi.lab.serializer.LabBusinessTime;
import org.slf4j.MDC;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;

@Service
public class BusinessTaskService
{
    public record View(@LabBusinessId Long id,String kind,String direction,String status,int totalCount,
            int successCount,int failureCount,String errorCode,String traceId,@LabBusinessId Long parentId,
            @LabBusinessTime LocalDateTime createdAt,@LabBusinessTime LocalDateTime startedAt,
            @LabBusinessTime LocalDateTime finishedAt,@LabBusinessTime LocalDateTime expiresAt,
            boolean resultAvailable,boolean errorAvailable) { }
    public record RowView(int rowNo,String status,String errorCode,@LabBusinessId Long objectId) { }
    public record Page(List<View> rows,long total) { }
    private final BusinessTaskMapper mapper;
    private final TaskBusinessAdapter business;
    private final TaskActorContext actors;
    private final TaskWorkbook workbook;
    private final StorageService storage;
    private final TaskJson json;
    private final TransactionTemplate tx;
    private final java.util.concurrent.Semaphore precheckSlots=new java.util.concurrent.Semaphore(2);

    public BusinessTaskService(BusinessTaskMapper mapper, TaskBusinessAdapter business, TaskActorContext actors,
            TaskWorkbook workbook, StorageService storage, TaskJson json, PlatformTransactionManager manager)
    { this.mapper=mapper;this.business=business;this.actors=actors;this.workbook=workbook;this.storage=storage;this.json=json;tx=new TransactionTemplate(manager);tx.setTimeout(30); }

    public View precheck(String kind,byte[] bytes)
    {
        if(!precheckSlots.tryAcquire())throw invalid("预检繁忙，请稍后重试");
        try{return performPrecheck(kind,bytes);}finally{precheckSlots.release();}
    }
    private View performPrecheck(String kind,byte[] bytes)
    {
        long actor=SecurityUtils.getUserId();
        return actors.asCurrentActor(actor,()-> {
            business.permission(kind,true);
            var scope=business.snapshot();
            List<Map<String,String>> data;
            try { data=workbook.read(bytes,kind); } catch(IOException e) { throw invalid("文件无法读取，请使用模板"); }
            List<BusinessTaskRow> rows=new ArrayList<>();Set<String> keys=new HashSet<>();
            for (var input:data)
            {
                var row=new BusinessTaskRow();row.rowNo=Integer.parseInt(input.remove("_row"));
                row.payloadJson=json.write(input);row.status="READY";
                try {
                    business.validateImport(kind,input,scope);
                    String key=input.get(kind.equals("DEVICE")?"assetNo":"labCode");
                    if (!keys.add(key.toLowerCase(Locale.ROOT)) || (kind.equals("DEVICE")?mapper.deviceExists(key):mapper.laboratoryExists(key))>0)
                        throw new IllegalArgumentException("编号重复");
                } catch (RuntimeException e) { row.status="INVALID";row.errorCode=safeCode(e); }
                rows.add(row);
            }
            String key=store(bytes);
            String errorsKey;
            try {
                var errors=rows.stream().filter(r->r.status.equals("INVALID")).map(r->List.of(Integer.toString(r.rowNo),r.errorCode)).toList();
                errorsKey=errors.isEmpty()?null:store(workbook.write(List.of("行号","错误码"),errors,"修改原文件后重新上传预检，错误行未导入"));
            }catch(IOException|RuntimeException e){removeFile(key);throw invalid("预检错误文件生成失败");}
            try {
                return tx.execute(s -> {
                    capacity(actor);
                    var task=fresh(kind,"IMPORT","PRECHECKED",actor,json.write(scope));task.inputKey=key;
                    task.errorKey=errorsKey;
                    task.totalCount=rows.size();task.failureCount=(int)rows.stream().filter(r->r.status.equals("INVALID")).count();
                    mapper.insert(task);rows.forEach(r->{r.taskId=task.id;mapper.insertRow(r);});
                    mapper.artifact(task.id,key);if(errorsKey!=null)mapper.artifact(task.id,errorsKey);
                    mapper.audit(task.id,actor,"PRECHECK");return view(mapper.get(task.id));
                });
            } catch(RuntimeException e) { removeFile(key);removeFile(errorsKey);throw e; }
        });
    }

    public View export(String kind,Map<String,String> filters)
    {
        long actor=SecurityUtils.getUserId();
        return actors.asCurrentActor(actor,()-> {
            var ids=business.exportIds(kind,filters);
            return tx.execute(s -> {
                capacity(actor);
                var task=fresh(kind,"EXPORT","QUEUED",actor,"{}");task.filterJson=json.write(filters);
                task.totalCount=ids.size();task.maxId=ids.isEmpty()?0:ids.get(ids.size()-1).id;mapper.insert(task);
                int n=0;
                for (var item:ids) { var row=new BusinessTaskRow();row.taskId=task.id;row.rowNo=++n;
                    row.payloadJson=json.write(new ExportData(item,List.of()));row.status="READY";row.objectId=item.id;mapper.insertRow(row); }
                mapper.audit(task.id,actor,"EXPORT");return view(mapper.get(task.id));
            });
        });
    }

    public View submit(long id)
    {
        long actor=SecurityUtils.getUserId();
        return actors.asCurrentActor(actor,()->tx.execute(s->{
            mapper.gate();var task=own(mapper.lock(id),actor);unexpired(task);business.permission(task.kind,true);
            if(!"PRECHECKED".equals(task.status)||task.totalCount==task.failureCount) throw invalid("没有可提交的数据或任务状态已变化");
            for(var row:allRows(id))if(row.status.equals("READY")) {
                var input=json.read(row.payloadJson,Values.class);
                business.validateImport(task.kind,input,json.read(task.scopeJson,TaskBusinessAdapter.Scope.class));
                if((task.kind.equals("DEVICE")?mapper.deviceExists(input.get("assetNo")):mapper.laboratoryExists(input.get("labCode")))>0)
                    throw invalid("预检后编号已被使用，请重新预检");
            }
            task.status="QUEUED";mapper.save(task);mapper.audit(id,actor,"SUBMIT");return view(task);
        }));
    }
    public View cancel(long id)
    {
        long actor=SecurityUtils.getUserId();
        return actors.asCurrentActor(actor,()->tx.execute(s->{
            TaskBusinessAdapter.require("lab:task:list");var task=own(mapper.lock(id),actor);
            if(!TaskRules.cancellable(task.status)) throw invalid("当前任务不能取消");
            task.status="RUNNING".equals(task.status)?"CANCELLING":"CANCELLED";
            if(task.status.equals("CANCELLED"))task.finishedAt=LocalDateTime.now();
            mapper.save(task);mapper.audit(id,actor,"CANCEL");return view(task);
        }));
    }
    public View retry(long id)
    {
        long actor=SecurityUtils.getUserId();
        return actors.asCurrentActor(actor,()-> {
            var old=own(mapper.get(id),actor);unexpired(old);
            if(!Set.of("FAILED","PARTIAL","CANCELLED").contains(old.status))throw invalid("当前状态不允许重试");
            business.permission(old.kind,old.direction.equals("IMPORT"));
            return tx.execute(s->{
                capacity(actor);
                var task=fresh(old.kind,old.direction,"QUEUED",actor,old.scopeJson);
                task.parentId=old.id;task.filterJson=old.filterJson;task.maxId=old.maxId;
                mapper.insert(task);
                for(var row:allRows(id)) {
                    if(old.direction.equals("IMPORT")&&Set.of("SUCCESS","INVALID").contains(row.status))continue;
                    row.taskId=task.id;row.status="READY";row.errorCode=null;
                    mapper.insertRow(row);task.totalCount++;
                }
                if(task.totalCount==0)throw invalid("没有可重试的行，请修改文件后重新预检");
                mapper.updateTotal(task.id,task.totalCount);mapper.audit(id,actor,"RETRY");return view(mapper.get(task.id));
            });
        });
    }
    public Page list(int page,int size)
    {
        if(page<1||size<1||size>100||page>1000000)throw invalid("分页参数无效");
        long actor=SecurityUtils.getUserId();
        return actors.asCurrentActor(actor,()->{TaskBusinessAdapter.require("lab:task:list");
            return new Page(mapper.list(actor,(page-1)*size,size).stream().map(BusinessTaskService::view).toList(),mapper.count(actor));});
    }
    public View detail(long id) { long actor=SecurityUtils.getUserId();return actors.asCurrentActor(actor,()->{
        TaskBusinessAdapter.require("lab:task:list");return view(own(mapper.get(id),actor));}); }
    public List<RowView> rows(long id,int page,int size)
    {
        detail(id);
        if(page<1||page>100000||size<1||size>100)throw invalid("分页参数无效");
        return mapper.rows(id,(page-1)*size,size).stream().map(r->new RowView(r.rowNo,r.status,r.errorCode,r.objectId)).toList();
    }
    public byte[] download(long id,boolean errors)
    {
        long actor=SecurityUtils.getUserId();
        return actors.asCurrentActor(actor,()->{
            TaskBusinessAdapter.require("lab:task:list");var task=own(mapper.get(id),actor);unexpired(task);
            business.permission(task.kind,task.direction.equals("IMPORT"));
            for(var row:allRows(id)) {
                if(task.direction.equals("EXPORT") && row.status.equals("SUCCESS")) business.assertOrigin(json.read(row.payloadJson,ExportData.class).origin(),task.kind,row.objectId);
                if(task.direction.equals("IMPORT") && !row.status.equals("INVALID")) {
                    var values=json.read(row.payloadJson,Values.class);
                    business.validateImport(task.kind,values,json.read(task.scopeJson,TaskBusinessAdapter.Scope.class));
                }
            }
            String key=errors?task.errorKey:task.resultKey;
            if(key==null)throw invalid("文件尚未生成或已过期");
            try(var input=storage.load(key)){mapper.audit(id,actor,errors?"DOWNLOAD_ERRORS":"DOWNLOAD");return input.readAllBytes();}
            catch(IOException e){throw invalid("结果文件暂时不可用");}
        });
    }
    public List<BusinessTaskRow> allRows(long id)
    {
        List<BusinessTaskRow> rows=new ArrayList<>();
        for(int offset=0;offset<=50000;offset+=100){var batch=mapper.rows(id,offset,100);rows.addAll(batch);if(batch.size()<100)break;}
        return rows;
    }
    private void capacity(long actor)
    { mapper.gate();if(mapper.activeCount()>=100||mapper.userActive(actor)>=2)throw invalid("任务队列已满，请稍后重试"); }
    public String store(byte[] bytes)
    {try{return storage.store(new ByteArrayInputStream(bytes),bytes.length,"xlsx").storageKey();}catch(IOException e){throw invalid("任务文件保存失败");}}
    public void removeFile(String key)
    {if(key!=null)try{storage.delete(key);}catch(IOException ignored){ /* Best effort for unregistered write failures. */ }}
    private static BusinessTask fresh(String kind,String direction,String status,long owner,String scope)
    {
        var task=new BusinessTask();task.ownerId=owner;task.kind=kind;task.direction=direction;task.status=status;
        task.scopeJson=scope;task.filterJson="{}";task.traceId=MDC.get("traceId");task.expiresAt=LocalDateTime.now().plusDays(7);return task;
    }
    private static BusinessTask own(BusinessTask task,long actor)
    {if(task==null||task.ownerId!=actor)throw new AccessDeniedException("无权访问此任务");return task;}
    private static void unexpired(BusinessTask task)
    {if(task.expiresAt.isBefore(LocalDateTime.now()))throw invalid("任务文件已过期，请重新生成");}
    public static View view(BusinessTask t)
    {return new View(t.id,t.kind,t.direction,t.status,t.totalCount,t.successCount,t.failureCount,t.errorCode,t.traceId,t.parentId,
        t.createdAt,t.startedAt,t.finishedAt,t.expiresAt,t.resultKey!=null&&t.expiresAt.isAfter(LocalDateTime.now()),t.errorKey!=null&&t.expiresAt.isAfter(LocalDateTime.now()));}
    public static String safeCode(RuntimeException e)
    {
        if(e instanceof AccessDeniedException)return "ACCESS_DENIED";
        if(e instanceof LabBusinessException b)return b.getErrorCode().name();
        if(e instanceof org.springframework.dao.DuplicateKeyException)return "DUPLICATE_KEY";
        if(e instanceof IllegalArgumentException)return "INVALID_ROW";
        return "PROCESSING_FAILED";
    }
    private static LabBusinessException invalid(String message){return new LabBusinessException(LabErrorCode.VALIDATION_ERROR,message);}
    public static class Values extends LinkedHashMap<String,String> { private static final long serialVersionUID=1L; }
    public record ExportData(com.ruoyi.lab.mapper.TaskSourceMapper.Origin origin,List<String> values) { }
}
