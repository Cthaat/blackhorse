package com.ruoyi.lab.maintenance;

import java.time.LocalDateTime;
import com.ruoyi.lab.serializer.LabBusinessId;
import com.ruoyi.lab.serializer.LabBusinessTime;

public class MaintenanceVersion
{
    @LabBusinessId public Long id;
    @LabBusinessId public Long planId;
    public String kind;
    public Integer periodDays;
    @LabBusinessTime public LocalDateTime firstDueAt;
    @LabBusinessId public Long responsibleId;
    public String description;
    public String reason;
    @LabBusinessId public Long createdBy;
    @LabBusinessTime public LocalDateTime createdAt;
}
