package com.ruoyi.lab.vo;

import java.time.LocalDateTime;
import com.ruoyi.lab.domain.LabRepairOrder;
import com.ruoyi.lab.domain.RepairSourceType;
import com.ruoyi.lab.domain.RepairStatus;
import com.ruoyi.lab.serializer.LabBusinessId;
import com.ruoyi.lab.serializer.LabBusinessTime;

public record RepairOrderVo(@LabBusinessId Long id, String repairNo,
        @LabBusinessId Long deviceId, String assetNo, String deviceName,
        RepairSourceType sourceType, @LabBusinessId Long sourceId,
        @LabBusinessId Long reporterId, String faultDescription,
        @LabBusinessId Long assigneeId, @LabBusinessTime LocalDateTime assignedAt,
        @LabBusinessTime LocalDateTime startedAt, String repairResult,
        @LabBusinessTime LocalDateTime resultSubmittedAt, String acceptanceResult,
        String acceptanceReason, @LabBusinessId Long acceptedBy,
        @LabBusinessTime LocalDateTime acceptedAt, RepairStatus status, Integer version,
        @LabBusinessTime LocalDateTime createTime)
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
