package com.ruoyi.lab.service;

import com.ruoyi.lab.domain.LabRepairOrder;
import com.ruoyi.lab.domain.LabUsageRecord;
import com.ruoyi.lab.dto.AcceptRepairCommand;
import com.ruoyi.lab.dto.AssignRepairCommand;
import com.ruoyi.lab.dto.ReportFaultCommand;
import com.ruoyi.lab.dto.SubmitRepairResultCommand;
import com.ruoyi.lab.vo.RepairOrderVo;

public interface RepairOrderService
{
    LabRepairOrder openMaintenance(Long deviceId,Long cycleId,
            com.ruoyi.lab.domain.RepairSourceType source,String description,Long actorId);

    LabRepairOrder openOrGetFromAbnormalReturn(LabUsageRecord usage,
            String description, Long reporterId);

    RepairOrderVo reportFault(ReportFaultCommand command, Long reporterId);

    RepairOrderVo assign(Long orderId, AssignRepairCommand command, Long managerId);

    RepairOrderVo start(Long orderId, Long repairerId);

    RepairOrderVo submitResult(Long orderId, SubmitRepairResultCommand command,
            Long repairerId);

    RepairOrderVo accept(Long orderId, AcceptRepairCommand command, Long managerId);
}
