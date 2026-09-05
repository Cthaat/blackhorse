package com.ruoyi.lab.task;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import com.ruoyi.lab.mapper.BusinessTaskMapper;
import com.ruoyi.lab.storage.StorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** One committed row and its checkpoint share a transaction. No business effects are retried blindly. */
@Service
public class BusinessTaskWorker
{
    private static final Logger LOG=LoggerFactory.getLogger(BusinessTaskWorker.class);
    private final BusinessTaskMapper mapper;
    private final BusinessTaskService service;
    private final TaskBusinessAdapter business;
    private final TaskActorContext actors;
    private final TaskJson json;
    private final TaskWorkbook workbook;
    private final StorageService storage;
    private final TransactionTemplate tx;
    public BusinessTaskWorker(BusinessTaskMapper mapper,BusinessTaskService service,TaskBusinessAdapter business,
            TaskActorContext actors,TaskJson json,TaskWorkbook workbook,StorageService storage,PlatformTransactionManager manager)
    {this.mapper=mapper;this.service=service;this.business=business;this.actors=actors;this.json=json;this.workbook=workbook;this.storage=storage;
        tx=new TransactionTemplate(manager);tx.setTimeout(25);}

    public void run(long id,String token)
    {
        var initial=mapper.get(id);String previous=MDC.get("traceId");
        if(initial.traceId!=null)MDC.put("traceId",initial.traceId);
        try {
            if(initial.direction.equals("EXPORT")) tx.executeWithoutResult(s->{var t=locked(id,token);mapper.resetExport(id);t.successCount=0;t.failureCount=0;t.cursorId=0;mapper.save(t);});
            boolean keep=true;
            while(keep) {
                var batch=mapper.pending(id);if(batch.isEmpty())break;
                for(var row:batch) {
                    try { keep=actors.asCurrentActor(initial.ownerId,()->process(row,token)); }
                    catch(RuntimeException e) { keep=recordFailure(row,token,e); }
                    if(!keep)break;
                }
            }
            finish(id,token,null);
        } catch(RuntimeException e) {
            LOG.warn("Business task {} stopped, code={}",id,BusinessTaskService.safeCode(e));
            try { finish(id,token,BusinessTaskService.safeCode(e)); }
            catch(RuntimeException lost) { LOG.warn("Business task {} completion deferred to lease recovery",id); }
        } finally {if(previous==null)MDC.remove("traceId");else MDC.put("traceId",previous);}
    }

    private boolean process(BusinessTaskRow row,String token)
    {
        return Boolean.TRUE.equals(tx.execute(s->{
            var task=locked(row.taskId,token);
            if(task.status.equals("CANCELLING"))return false;
            if(task.expiresAt.isBefore(LocalDateTime.now()))throw new IllegalArgumentException("任务已过期");
            if(task.direction.equals("IMPORT")) row.objectId=business.create(task.kind,json.read(row.payloadJson,BusinessTaskService.Values.class),json.read(task.scopeJson,TaskBusinessAdapter.Scope.class));
            else {
                var data=json.read(row.payloadJson,BusinessTaskService.ExportData.class);
                business.assertOrigin(data.origin(),task.kind,row.objectId);
                row.payloadJson=json.write(new BusinessTaskService.ExportData(data.origin(),business.exportRow(task.kind,row.objectId)));
            }
            row.status="SUCCESS";row.errorCode=null;
            if(mapper.finishRow(row)!=1)throw new IllegalStateException("检查点已变化");
            task.successCount++;task.cursorId=row.rowNo;mapper.save(task);return true;
        }));
    }
    private boolean recordFailure(BusinessTaskRow row,String token,RuntimeException error)
    {
        String code=BusinessTaskService.safeCode(error);
        if(code.equals("PROCESSING_FAILED")||code.equals("ACCESS_DENIED"))throw error;
        return Boolean.TRUE.equals(tx.execute(s->{
            var task=locked(row.taskId,token);if(task.status.equals("CANCELLING"))return false;
            row.status="FAILED";row.errorCode=code;
            if(mapper.finishRow(row)!=1)throw new IllegalStateException("检查点已变化");
            task.failureCount++;task.cursorId=row.rowNo;mapper.save(task);return true;
        }));
    }
    private BusinessTask locked(long id,String token)
    {
        var task=mapper.lock(id);
        if(task==null||mapper.owned(id,token)!=1)throw new IllegalStateException("执行租约已失效");
        return task;
    }
    private void finish(long id,String token,String error)
    {
        var task=mapper.get(id);
        List<List<String>> output=new ArrayList<>(),errors=new ArrayList<>();
        for(var row:service.allRows(id)) {
            if(task.direction.equals("EXPORT")) {
                if(row.status.equals("SUCCESS"))output.add(json.read(row.payloadJson,BusinessTaskService.ExportData.class).values());
            } else output.add(List.of(Integer.toString(row.rowNo),row.status,Objects.toString(row.objectId,""),Objects.toString(row.errorCode,"")));
            if(Set.of("INVALID","FAILED").contains(row.status))errors.add(List.of(Integer.toString(row.rowNo),row.errorCode));
        }
        String resultKey=null,errorKey=null;
        try {
            // Failed/cancelled export artifacts are deliberately never downloadable.
            boolean exportAllowed=task.direction.equals("EXPORT")&&error==null&&!task.status.equals("CANCELLING");
            if(task.direction.equals("IMPORT")||exportAllowed)resultKey=service.store(workbook.write(
                    task.direction.equals("IMPORT")?List.of("行号","状态","对象编号","错误码"):List.of("编号","业务编号","名称或说明","状态","时间或位置"),output,
                    "任务 "+id+"；筛选 "+task.filterJson+"；开始 "+task.startedAt+"；生成 "+LocalDateTime.now()+"；逐批当前数据，非事务快照"));
            if(!errors.isEmpty())errorKey=service.store(workbook.write(List.of("行号","错误码"),errors,"错误行不写入业务数据，请修正源文件后重新预检"));
            String result=resultKey,failed=errorKey;
            tx.executeWithoutResult(s->{var current=locked(id,token);
                current.status=current.status.equals("CANCELLING")?"CANCELLED":error!=null?"FAILED":TaskRules.result(current.successCount,current.failureCount);
                if(current.direction.equals("EXPORT")&&current.status.equals("CANCELLED")){service.removeFile(result);current.resultKey=null;}else current.resultKey=result;
                current.errorKey=failed;current.errorCode=error;current.finishedAt=LocalDateTime.now();current.leaseUntil=null;
                if(current.resultKey!=null)mapper.artifact(id,current.resultKey);
                if(failed!=null)mapper.artifact(id,failed);
                mapper.save(current);mapper.audit(id,current.ownerId,"FINISH_"+current.status);
            });
        } catch(IOException|RuntimeException e) {service.removeFile(resultKey);service.removeFile(errorKey);throw new IllegalStateException("任务完成保存失败",e);}
    }
    public void cleanup()
    {
        for(long id:mapper.expired()) {
            try {for(String key:mapper.artifacts(id))storage.delete(key);
                tx.executeWithoutResult(s->{mapper.clearFiles(id);mapper.purgePayload(id);mapper.clearArtifacts(id);});
            } catch(IOException e){LOG.warn("Expired task {} cleanup will retry",id);}
        }
    }
}
