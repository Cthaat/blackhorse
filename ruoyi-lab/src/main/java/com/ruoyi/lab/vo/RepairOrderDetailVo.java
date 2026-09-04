package com.ruoyi.lab.vo;

import java.util.List;
import java.util.Objects;

/** Repair detail enriched with its complete timeline and client-safe attachments. */
public record RepairOrderDetailVo(RepairOrderVo order, List<StatusHistoryVo> statusHistory,
        List<AttachmentVo> attachments)
{
    public RepairOrderDetailVo
    {
        order = Objects.requireNonNull(order, "order");
        statusHistory = List.copyOf(Objects.requireNonNull(statusHistory, "statusHistory"));
        attachments = List.copyOf(Objects.requireNonNull(attachments, "attachments"));
    }
}
