package com.ruoyi.lab.maintenance;

import java.time.LocalDateTime;
import java.util.Objects;
import com.ruoyi.lab.domain.*;
import com.ruoyi.lab.mapper.*;
import com.ruoyi.lab.service.LabStatusHistoryService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

/** Transactional repair acceptance hook; no dependency on the maintenance command service. */
@Service
public class MaintenanceCompletionService
{
    private final LabMaintenanceMapper mapper;
    private final LabAttachmentMapper attachments;
    private final LabStatusHistoryService history;
    public MaintenanceCompletionService(LabMaintenanceMapper mapper,LabAttachmentMapper attachments,LabStatusHistoryService history)
    { this.mapper=mapper;this.attachments=attachments;this.history=history; }

    @Transactional(propagation=Propagation.MANDATORY)
    public void complete(LabRepairOrder repair,Long reportId,Long actor,LocalDateTime now)
    {
        if (!"MAINTENANCE".equals(repair.getSourceType().name()) && !"CALIBRATION".equals(repair.getSourceType().name())) return;
        MaintenanceCycle cycle=mapper.cycleLocked(repair.getSourceId());
        if (cycle==null || !"STARTED".equals(cycle.status) || !Objects.equals(cycle.repairId,repair.getId()))
            throw MaintenancePolicy.conflict("维护周期与维修工单关联无效");
        if ("CALIBRATION".equals(cycle.kind))
        {
            LabAttachment report=reportId==null?null:attachments.selectByIdForUpdate(reportId);
            if (report==null || !"0".equals(report.getDelFlag()) || !"REPAIR_ORDER".equals(report.getBusinessType())
                    || !Objects.equals(report.getBusinessId(),repair.getId()))
                throw MaintenancePolicy.invalid("校准验收必须选择本工单的有效私有报告附件");
        }
        else reportId=null;
        if (mapper.complete(cycle.id,now,reportId)!=1) throw MaintenancePolicy.conflict("维护周期已发生变化");
        mapper.next(cycle.planId,MaintenancePolicy.nextDue(now,cycle.periodDays));
        history.append("MAINTENANCE_CYCLE",cycle.id,"STARTED","COMPLETED",actor,"维修验收完成维护周期");
    }
}
