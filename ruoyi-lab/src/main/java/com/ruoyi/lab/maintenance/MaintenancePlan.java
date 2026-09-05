package com.ruoyi.lab.maintenance;

import java.time.LocalDateTime;
import com.ruoyi.lab.serializer.LabBusinessId;
import com.ruoyi.lab.serializer.LabBusinessTime;

public class MaintenancePlan
{
    @LabBusinessId public Long id;
    @LabBusinessId public Long deviceId;
    public String deviceName;
    public String assetNo;
    @LabBusinessId public Long laboratoryId;
    public String kind;
    public Integer periodDays;
    @LabBusinessTime public LocalDateTime firstDueAt;
    @LabBusinessId public Long responsibleId;
    public String description;
    public Boolean enabled;
    @LabBusinessId public Long currentVersionId;
    public Integer version;
    @LabBusinessTime public LocalDateTime nextDueAt;
    @LabBusinessId public Long createdBy;
    @LabBusinessTime public LocalDateTime createdAt;
}
