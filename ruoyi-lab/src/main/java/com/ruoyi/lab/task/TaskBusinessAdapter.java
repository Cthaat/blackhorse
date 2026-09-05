package com.ruoyi.lab.task;

import java.util.*;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.lab.domain.*;
import com.ruoyi.lab.dto.*;
import com.ruoyi.lab.mapper.*;
import com.ruoyi.lab.security.*;
import com.ruoyi.lab.service.*;
import jakarta.validation.Validator;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

/** Closed set of business operations; execution always delegates to existing authorized services. */
@Service
public class TaskBusinessAdapter
{
    public record Scope(Set<Long> laboratories, Set<Long> departments) { }
    private final LaboratoryService labs;
    private final DeviceService devices;
    private final ReservationQueryService reservations;
    private final RepairQueryService repairs;
    private final HazardService hazards;
    private final LabObjectPermissionService objects;
    private final LabDataScopeService scopes;
    private final LabLaboratoryMapper labMapper;
    private final LabOptionsMapper options;
    private final LabDictionaryMapper dictionaries;
    private final TaskJson json;
    private final Validator validator;
    private final TaskSourceMapper sources;

    public TaskBusinessAdapter(LaboratoryService labs, DeviceService devices, ReservationQueryService reservations,
            RepairQueryService repairs, HazardService hazards, LabObjectPermissionService objects,
            LabDataScopeService scopes, LabLaboratoryMapper labMapper, LabOptionsMapper options,
            LabDictionaryMapper dictionaries, TaskJson json, Validator validator, TaskSourceMapper sources)
    { this.labs=labs; this.devices=devices; this.reservations=reservations; this.repairs=repairs;
      this.hazards=hazards; this.objects=objects; this.scopes=scopes; this.labMapper=labMapper;
      this.options=options; this.dictionaries=dictionaries; this.json=json; this.validator=validator;this.sources=sources; }

    public static void require(String permission)
    { if (!SecurityUtils.hasPermi(permission)) throw new AccessDeniedException("当前账号无此功能权限"); }

    public void permission(String kind, boolean importing)
    {
        require("lab:task:list");
        require(importing ? "lab:task:import" : "lab:task:export");
        String stem = switch (kind) {
            case "LABORATORY" -> "laboratory"; case "DEVICE" -> "device";
            case "RESERVATION" -> "reservation"; case "REPAIR" -> "repair"; case "HAZARD" -> "hazard";
            default -> throw new IllegalArgumentException("任务类型无效");
        };
        if (importing && !Set.of("LABORATORY","DEVICE").contains(kind)) throw new IllegalArgumentException("此类型不支持导入");
        if (!importing && kind.equals("RESERVATION") && SecurityUtils.hasPermi("lab:reservation:mine")) return;
        require("lab:" + stem + (importing ? ":add" : ":list"));
        if (!importing && Set.of("LABORATORY","DEVICE","REPAIR").contains(kind)) require("lab:"+stem+":query");
    }

    public Scope snapshot()
    {
        Set<Long> ids = new HashSet<>();
        labMapper.selectListByScope(scopes.resolveCurrentScope(), null, null, null).forEach(l -> ids.add(l.getId()));
        Set<Long> departments = new HashSet<>(objects.readableDepartmentIds());
        if (SecurityUtils.isAdmin(SecurityUtils.getUserId()))
            options.selectActiveDepartmentOptions(true, Set.of()).forEach(d -> departments.add(d.id()));
        return new Scope(Set.copyOf(ids), Set.copyOf(departments));
    }

    public Object validateImport(String kind, Map<String,String> values, Scope original)
    {
        permission(kind, true);
        Object dto = kind.equals("DEVICE") ? json.convert(values, DeviceCreateDto.class) : json.convert(values, LaboratoryCreateDto.class);
        var violations = validator.validate(dto);
        if (!violations.isEmpty()) throw new IllegalArgumentException("必填或字段格式无效："+violations.iterator().next().getPropertyPath());
        if (dto instanceof DeviceCreateDto device)
        {
            if (!original.laboratories.contains(device.getLaboratoryId())) throw new AccessDeniedException("目标超出提交时范围");
            objects.assertLaboratoryManageable(device.getLaboratoryId());
            if (labs.getById(device.getLaboratoryId()).getStatus()!=LaboratoryStatus.ENABLED) throw new IllegalArgumentException("实验室已停用");
            if (options.countActiveUserRole(device.getManagerId(),"lab_manager")==0
                    || options.countActiveUserLaboratoryScope(device.getManagerId(),device.getLaboratoryId())==0)
                throw new IllegalArgumentException("负责人不能管理目标实验室");
            if (dictionaries.countEnabledValue("lab_device_category",device.getCategoryCode())==0
                    || dictionaries.countEnabledValue("lab_risk_level",device.getRiskLevel())==0)
                throw new IllegalArgumentException("类别或风险等级无效");
        }
        else if (dto instanceof LaboratoryCreateDto lab)
        {
            if (!original.departments.contains(lab.getDeptId()) || !snapshot().departments.contains(lab.getDeptId()))
                throw new AccessDeniedException("部门超出允许范围");
            if (options.countActiveDepartment(lab.getDeptId())==0
                    || options.countActiveUserRole(lab.getManagerId(),"lab_manager")==0
                    || options.countActiveUserDepartmentScope(lab.getManagerId(),lab.getDeptId())==0)
                throw new IllegalArgumentException("部门或负责人无效");
        }
        return dto;
    }

    public long create(String kind, Map<String,String> values, Scope original)
    {
        Object dto=validateImport(kind,values,original);
        return dto instanceof DeviceCreateDto device
                ? devices.create(device,SecurityUtils.getUsername(),SecurityUtils.getUserId()).getId()
                : labs.create((LaboratoryCreateDto)dto,SecurityUtils.getUsername(),SecurityUtils.getUserId()).getId();
    }

    public List<TaskSourceMapper.Origin> exportIds(String kind, Map<String,String> filters)
    {
        permission(kind,false);
        Set<String> allowed=switch(kind){
            case "LABORATORY" -> Set.of("keyword","status");
            case "DEVICE" -> Set.of("keyword","status","laboratoryId","categoryCode");
            case "RESERVATION" -> Set.of("status","reservationNo","deviceId","applicantId","from","to");
            case "REPAIR" -> Set.of("status","repairNo","deviceId");
            case "HAZARD" -> Set.of("status","severity","ownerId");default->Set.of();};
        if(!allowed.containsAll(filters.keySet()))throw new IllegalArgumentException("存在不支持的筛选条件");
        Map<String,String> query=new HashMap<>(filters);
        if(kind.equals("RESERVATION")) {
            java.time.LocalDateTime from=time(filters.get("from")),to=time(filters.get("to"));
            if(from!=null&&to!=null&&!from.isBefore(to))throw new IllegalArgumentException("时间范围无效");
            if(from!=null)query.put("from",from.toString().replace('T',' '));
            if(to!=null)query.put("to",to.toString().replace('T',' '));
        }
        List<TaskSourceMapper.Origin> ids=new ArrayList<>();
        long maximum=sources.maximum(kind),cursor=0;
        for (int batchNo=0;batchNo<5000;batchNo++)
        {
            var batch=sources.batch(kind,cursor,maximum,query);
            for(var item:batch) {
                cursor=item.id;
                try {exportRow(kind,item.id);ids.add(item);}
                catch(AccessDeniedException denied){ /* Not part of this actor's export. */ }
                catch(com.ruoyi.lab.exception.LabBusinessException denied){
                    if(!Set.of(com.ruoyi.lab.exception.LabErrorCode.LAB_OUT_OF_DATA_SCOPE,com.ruoyi.lab.exception.LabErrorCode.ACCESS_DENIED,com.ruoyi.lab.exception.LabErrorCode.RESOURCE_NOT_FOUND).contains(denied.getErrorCode()))throw denied;
                }
            }
            if (ids.size()>50000) throw new IllegalArgumentException("导出超过五万行，请缩小筛选范围");
            if (batch.size()<100) return List.copyOf(ids);
        }
        throw new IllegalArgumentException("导出超过五万行，请缩小筛选范围");
    }

    public void assertOrigin(TaskSourceMapper.Origin original,String kind,long id)
    {
        if(original==null)throw new AccessDeniedException("结果归属不可验证");
        var current=sources.origin(kind,id);
        if(current==null||!Objects.equals(original.laboratoryId,current.laboratoryId)||!Objects.equals(original.departmentId,current.departmentId))
            throw new AccessDeniedException("数据归属已变化，请重新生成结果");
        exportRow(kind,id);
    }


    public List<String> exportRow(String kind,long id)
    {
        permission(kind,false);
        return switch(kind) {
            case "LABORATORY" -> { var v=labs.getById(id);yield strings(v.getId(),v.getLabCode(),v.getName(),v.getStatus(),v.getLocation()); }
            case "DEVICE" -> { var v=devices.getById(id);yield strings(v.getId(),v.getAssetNo(),v.getName(),v.getStatus(),v.getLocation()); }
            case "RESERVATION" -> { var v=reservations.getById(id,SecurityUtils.getUserId(),SecurityUtils.hasPermi("lab:reservation:list"));yield strings(v.id(),v.reservationNo(),v.purpose(),v.status(),v.startTime()+" 至 "+v.endTime()); }
            case "REPAIR" -> { var v=repairs.detail(id,SecurityUtils.getUserId()).order();yield strings(v.id(),v.repairNo(),v.faultDescription(),v.status(),v.createTime()); }
            case "HAZARD" -> { var v=hazards.get(id);yield strings(v.getId(),v.getHazardNo(),v.getRequirements(),v.getStatus(),v.getDeadline()); }
            default -> throw new IllegalArgumentException("任务类型无效");
        };
    }
    private static List<String> strings(Object... values) { return Arrays.stream(values).map(v->Objects.toString(v,"")).toList(); }
    private static java.time.LocalDateTime time(String value) {
        if(value==null||value.isBlank())return null;
        var parsed=java.time.OffsetDateTime.parse(value);
        if(!parsed.getOffset().equals(java.time.ZoneOffset.ofHours(8)))throw new IllegalArgumentException("预约时间必须使用东八区");
        return parsed.toLocalDateTime();
    }
}
