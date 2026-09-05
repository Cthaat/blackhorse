package com.ruoyi.lab.sla;
import java.time.LocalDateTime;
import com.ruoyi.lab.serializer.*;
public class SlaRule
{
    @LabBusinessId public Long id;
    @LabBusinessId public Long laboratoryId;
    public String businessType;
    public String risk;
    public Integer responseHours;
    public Integer processingHours;
    public String reason;
    @LabBusinessId public Long createdBy;
    @LabBusinessTime public LocalDateTime createdAt;
    public boolean builtin;
}
