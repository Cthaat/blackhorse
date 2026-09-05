package com.ruoyi.lab.maintenance;

import java.time.*;
import java.util.*;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.lab.domain.*;
import com.ruoyi.lab.exception.*;
import com.ruoyi.lab.mapper.*;
import com.ruoyi.lab.security.*;
import com.ruoyi.lab.service.*;
import com.ruoyi.lab.vo.StatusHistoryVo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
public class MaintenanceService
{
    private final LabMaintenanceMapper mapper;
    private final LabDeviceMapper devices;
    private final LabObjectPermissionService permissions;
    private final LabDataScopeService scopes;
    private final LabUserDirectory users;
    private final LabStatusHistoryService history;
    private final LabStatusHistoryMapper histories;
    private final LabSystemOperatorProvider operators;
    private final Clock clock;

    public MaintenanceService(LabMaintenanceMapper mapper,LabDeviceMapper devices,
            LabObjectPermissionService permissions,LabDataScopeService scopes,LabUserDirectory users,
            LabStatusHistoryService history,LabStatusHistoryMapper histories,LabSystemOperatorProvider operators,Clock clock)
    {
        this.mapper=mapper;this.devices=devices;this.permissions=permissions;this.scopes=scopes;
        this.users=users;this.history=history;this.histories=histories;this.operators=operators;this.clock=clock;
    }
    public record Detail(MaintenancePlan plan,List<MaintenanceVersion> versions,List<MaintenanceCycle> cycles,List<StatusHistoryVo> history) { }
    public record WindowResult(boolean scheduled,List<MaintenanceConflict> conflicts,MaintenanceCycle cycle) { }

    public List<MaintenancePlan> plans(Long deviceId,Boolean enabled,String due)
    {
        authorize("list");
        String filter=due==null||due.isBlank()?null:due;
        if (filter!=null && !Set.of("SOON","OVERDUE").contains(filter)) throw MaintenancePolicy.invalid("临期筛选无效");
        var scope=scopes.resolveCurrentScope();
        return LabPage.query(() -> mapper.plans(scope,deviceId,enabled,filter,now(),now().plusDays(7)));
    }
    public List<MaintenanceCycle> cycles(Long deviceId,String status)
    {
        authorize("list");
        String filter=status==null||status.isBlank()?null:status;
        if (filter!=null && !Set.of("PLANNED","SCHEDULED","STARTED","COMPLETED").contains(filter)) throw MaintenancePolicy.invalid("周期状态无效");
        var scope=scopes.resolveCurrentScope();
        return LabPage.query(() -> mapper.cycles(scope,deviceId,filter));
    }
    public Detail detail(Long id)
    {
        authorize("list");MaintenancePlan plan=required(id);permissions.assertDeviceManageable(plan.deviceId);
        return new Detail(plan,mapper.versions(id),mapper.planCycles(id),histories.selectByObject("MAINTENANCE_PLAN",id));
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public MaintenancePlan create(MaintenanceCommands.Plan command)
    {
        authorize("edit");validate(command);lockDevice(command.deviceId());permissions.assertDeviceManageable(command.deviceId());
        MaintenancePlan plan=new MaintenancePlan();plan.deviceId=command.deviceId();plan.nextDueAt=MaintenancePolicy.time(command.firstDueAt());
        plan.createdBy=permissions.currentUserId();plan.createdAt=now();mapper.insertPlan(plan);
        var version=version(plan.id,command);mapper.insertVersion(version);
        one(mapper.activate(plan.id,version.id,plan.nextDueAt,0));
        history.append("MAINTENANCE_PLAN",plan.id,null,"ENABLED",plan.createdBy,version.reason);
        return required(plan.id);
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public MaintenancePlan edit(Long id,MaintenanceCommands.Plan command)
    {
        authorize("edit");validate(command);MaintenancePlan original=required(id);lockDevice(original.deviceId);
        permissions.assertDeviceManageable(original.deviceId);MaintenancePlan plan=mapper.planLocked(id);expected(command.expectedVersion(),plan.version);
        if (!Objects.equals(command.deviceId(),plan.deviceId)) throw MaintenancePolicy.invalid("维护计划不能更换设备");
        var version=version(id,command);mapper.insertVersion(version);
        LocalDateTime next=mapper.openCycle(id)==null?version.firstDueAt:plan.nextDueAt;
        if (mapper.openCycle(id)==null && mapper.countCycleDue(id,next)>0)
            throw MaintenancePolicy.invalid("该到期时间已有执行周期，请选择新的到期时间");
        one(mapper.activate(id,version.id,next,plan.version));
        history.append("MAINTENANCE_PLAN",id,null,"VERSION_PUBLISHED",permissions.currentUserId(),version.reason);
        return required(id);
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public MaintenancePlan toggle(Long id,MaintenanceCommands.Toggle command)
    {
        authorize("edit");MaintenancePlan original=required(id);lockDevice(original.deviceId);permissions.assertDeviceManageable(original.deviceId);
        MaintenancePlan plan=mapper.planLocked(id);expected(command.expectedVersion(),plan.version);
        String reason=MaintenancePolicy.reason(command.reason());
        if (command.enabled()==null) throw MaintenancePolicy.invalid("请选择计划启停状态");
        one(mapper.toggle(id,command.enabled(),plan.version));
        history.append("MAINTENANCE_PLAN",id,plan.enabled?"ENABLED":"DISABLED",command.enabled()?"ENABLED":"DISABLED",permissions.currentUserId(),reason);
        return required(id);
    }
    public List<Long> due(LocalDateTime now,int limit)
    {
        if (now==null || limit<1 || limit>1000) throw MaintenancePolicy.invalid("生成批量必须为1至1000");
        return mapper.due(now,limit);
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public boolean generate(Long id,LocalDateTime at)
    {
        var actor=operators.requiredOperator();MaintenancePlan original=required(id);lockDevice(original.deviceId);
        MaintenancePlan plan=mapper.planLocked(id);
        if (!plan.enabled || plan.nextDueAt.isAfter(at) || mapper.openCycle(id)!=null) return false;
        MaintenanceCycle cycle=new MaintenanceCycle();cycle.planId=id;cycle.planVersionId=plan.currentVersionId;
        cycle.deviceId=plan.deviceId;cycle.kind=plan.kind;cycle.periodDays=plan.periodDays;cycle.responsibleId=plan.responsibleId;
        cycle.dueAt=plan.nextDueAt;cycle.createdAt=at;mapper.insertCycle(cycle);
        history.append("MAINTENANCE_CYCLE",cycle.id,null,"PLANNED",actor.userId(),"维护计划到期，等待管理员安排窗口");
        return true;
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public WindowResult schedule(Long id,MaintenanceCommands.Window command)
    {
        authorize("schedule");MaintenanceCycle cycle=lockCycle(id);expected(command.expectedVersion(),cycle.version);
        if (!Set.of("PLANNED","SCHEDULED").contains(cycle.status)) throw MaintenancePolicy.conflict("仅待安排周期可以设置窗口");
        String reason=MaintenancePolicy.reason(command.reason());
        LocalDateTime start=MaintenancePolicy.time(command.startTime()),end=MaintenancePolicy.time(command.endTime());
        MaintenancePolicy.window(start,end,now());
        List<MaintenanceConflict> conflicts=mapper.conflicts(cycle.deviceId,id,start,end,now());
        if (!conflicts.isEmpty()) return new WindowResult(false,conflicts,cycle);
        one(mapper.schedule(id,start,end,cycle.version));
        history.append("MAINTENANCE_CYCLE",id,cycle.status,"SCHEDULED",permissions.currentUserId(),reason);
        return new WindowResult(true,List.of(),mapper.cycle(id));
    }
    public MaintenanceCycle lockCycle(Long id)
    {
        MaintenanceCycle snapshot=mapper.cycle(id);
        if (snapshot==null) throw missing();
        lockDevice(snapshot.deviceId);permissions.assertDeviceManageable(snapshot.deviceId);
        MaintenanceCycle locked=mapper.cycleLocked(id);if (locked==null) throw missing();return locked;
    }
    private void validate(MaintenanceCommands.Plan command)
    {
        if (command==null || command.deviceId()==null || command.deviceId()<1 || command.periodDays()==null
                || !Set.of("MAINTENANCE","CALIBRATION").contains(command.kind()==null?"":command.kind())) throw MaintenancePolicy.invalid("维护计划参数无效");
        MaintenancePolicy.nextDue(now(),command.periodDays());MaintenancePolicy.time(command.firstDueAt());MaintenancePolicy.reason(command.reason());
        if (command.description()!=null && command.description().length()>1000) throw MaintenancePolicy.invalid("说明最多1000字");
        users.assertActiveRole(command.responsibleId(),"lab_repair_worker");
    }
    private MaintenanceVersion version(Long id,MaintenanceCommands.Plan command)
    {
        MaintenanceVersion v=new MaintenanceVersion();v.planId=id;v.kind=command.kind();v.periodDays=command.periodDays();
        v.firstDueAt=MaintenancePolicy.time(command.firstDueAt());v.responsibleId=command.responsibleId();v.description=command.description();
        v.reason=MaintenancePolicy.reason(command.reason());v.createdBy=permissions.currentUserId();v.createdAt=now();return v;
    }
    private MaintenancePlan required(Long id) { MaintenancePlan p=mapper.plan(id);if (p==null) throw missing();return p; }
    private LabDevice lockDevice(Long id) { LabDevice d=devices.selectByIdForUpdate(id);if (d==null) throw missing();return d; }
    private void authorize(String action)
    {
        if (!SecurityUtils.hasPermi("lab:maintenance:"+action)) throw new LabBusinessException(LabErrorCode.ACCESS_DENIED,"无维护计划操作权限");
        users.assertActiveRole(permissions.currentUserId(),"lab_manager");
    }
    public static void expected(Integer expected,Integer actual) { if (expected==null || !expected.equals(actual)) throw MaintenancePolicy.conflict("记录已变化，请刷新"); }
    public static void one(int rows) { if (rows!=1) throw MaintenancePolicy.conflict("记录已变化，请刷新"); }
    private LocalDateTime now() { return LocalDateTime.now(clock); }
    private static LabBusinessException missing() { return new LabBusinessException(LabErrorCode.RESOURCE_NOT_FOUND,"维护对象不存在"); }
}
