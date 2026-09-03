package com.ruoyi.lab.vo;

import java.time.LocalDateTime;
import com.ruoyi.lab.domain.LabRepairOrder;
import com.ruoyi.lab.domain.RepairSourceType;
import com.ruoyi.lab.domain.RepairStatus;

public record RepairOrderVo(Long id, String repairNo, Long deviceId, String assetNo,
        String deviceName, RepairSourceType sourceType, Long sourceId, Long reporterId,
        String faultDescription, Long assigneeId, LocalDateTime assignedAt,
        LocalDateTime startedAt, String repairResult, LocalDateTime resultSubmittedAt,
        String acceptanceResult, String acceptanceReason, Long acceptedBy,
        LocalDateTime acceptedAt, RepairStatus status, Integer version,
        LocalDateTime createTime)
{
    public static RepairOrderVo from(LabRepairOrder order)
    {
        return new RepairOrderVo(order.getId(), order.getRepairNo(), order.getDeviceId(), null,
                null, order.getSourceType(), order.getSourceId(), order.getReporterId(),
                order.getFaultDescription(), order.getAssigneeId(), order.getAssignedAt(),
                order.getStartedAt(), order.getRepairResult(), order.getResultSubmittedAt(),
                order.getAcceptanceResult(), order.getAcceptanceReason(), order.getAcceptedBy(),
                order.getAcceptedAt(), order.getStatus(), order.getVersion(), order.getCreateTime());
    }
}
