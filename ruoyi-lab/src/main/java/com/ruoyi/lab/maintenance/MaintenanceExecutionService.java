package com.ruoyi.lab.maintenance;

import java.time.*;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.lab.domain.RepairSourceType;
import com.ruoyi.lab.mapper.LabMaintenanceMapper;
import com.ruoyi.lab.security.LabObjectPermissionService;
import com.ruoyi.lab.service.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
public class MaintenanceExecutionService
{
    private final MaintenanceService plans;
    private final LabMaintenanceMapper mapper;
    private final RepairOrderService repairs;
    private final LabObjectPermissionService permissions;
    private final LabUserDirectory users;
    private final LabStatusHistoryService history;
    private final Clock clock;
    public MaintenanceExecutionService(MaintenanceService plans,LabMaintenanceMapper mapper,RepairOrderService repairs,
            LabObjectPermissionService permissions,LabUserDirectory users,LabStatusHistoryService history,Clock clock)
    { this.plans=plans;this.mapper=mapper;this.repairs=repairs;this.permissions=permissions;this.users=users;this.history=history;this.clock=clock; }

    @Transactional(isolation=Isolation.READ_COMMITTED)
    public MaintenanceCycle start(Long id,MaintenanceCommands.Start command)
    {
        if (!SecurityUtils.hasPermi("lab:maintenance:start")) throw new org.springframework.security.access.AccessDeniedException("无维护启动权限");
        users.assertActiveRole(permissions.currentUserId(),"lab_manager");
        MaintenanceCycle cycle=plans.lockCycle(id);MaintenanceService.expected(command.expectedVersion(),cycle.version);
        String reason=MaintenancePolicy.reason(command.reason());LocalDateTime now=LocalDateTime.now(clock);
        if (!"SCHEDULED".equals(cycle.status) || cycle.windowStart.isAfter(now) || !cycle.windowEnd.isAfter(now))
            throw MaintenancePolicy.conflict("只能在已安排的有效停用窗口内启动维护");
        // Recheck conflicts at the point equipment is actually withdrawn from service.
        if (!mapper.conflicts(cycle.deviceId,id,cycle.windowStart,cycle.windowEnd,now).isEmpty())
            throw MaintenancePolicy.conflict("停用窗口出现新的业务冲突，请重新安排");
        MaintenanceVersion snapshot=mapper.version(cycle.planVersionId);
        users.assertActiveRole(cycle.responsibleId,"lab_repair_worker");
        String description=snapshot.description==null || snapshot.description.isBlank()?"计划维护或校准":snapshot.description;
        var repair=repairs.openMaintenance(cycle.deviceId,id,RepairSourceType.valueOf(cycle.kind),description,permissions.currentUserId());
        MaintenanceService.one(mapper.start(id,repair.getId(),cycle.version));
        repairs.assign(repair.getId(),new com.ruoyi.lab.dto.AssignRepairCommand(cycle.responsibleId),permissions.currentUserId());
        history.append("MAINTENANCE_CYCLE",id,"SCHEDULED","STARTED",permissions.currentUserId(),reason);
        return mapper.cycle(id);
    }
}
