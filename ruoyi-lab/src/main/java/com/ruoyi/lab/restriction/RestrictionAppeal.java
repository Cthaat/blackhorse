package com.ruoyi.lab.restriction;

import java.time.LocalDateTime;
import com.ruoyi.lab.serializer.LabBusinessId;
import com.ruoyi.lab.serializer.LabBusinessTime;
import java.util.List;

public class RestrictionAppeal
{
    @LabBusinessId public Long id;
    @LabBusinessId public Long restrictionId;
    public String reason;
    public String status;
    public String reviewReason;
    @LabBusinessId public Long reviewerId;
    @LabBusinessTime public LocalDateTime createdAt;
    @LabBusinessTime public LocalDateTime reviewedAt;
    @com.fasterxml.jackson.databind.annotation.JsonSerialize(contentUsing=com.fasterxml.jackson.databind.ser.std.ToStringSerializer.class)
    public List<Long> attachmentIds = List.of();
}
