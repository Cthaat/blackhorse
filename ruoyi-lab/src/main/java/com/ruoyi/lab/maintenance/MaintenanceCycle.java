package com.ruoyi.lab.maintenance;

import java.time.LocalDateTime;
import com.ruoyi.lab.serializer.LabBusinessId;
import com.ruoyi.lab.serializer.LabBusinessTime;

public class MaintenanceCycle
{
    @LabBusinessId public Long id;
    @LabBusinessId public Long planId;
    @LabBusinessId public Long planVersionId;
    @LabBusinessId public Long deviceId;
    public String deviceName;
    public String kind;
    public Integer periodDays;
    @LabBusinessId public Long responsibleId;
    @LabBusinessTime public LocalDateTime dueAt;
    public String status;
    @LabBusinessTime public LocalDateTime windowStart;
    @LabBusinessTime public LocalDateTime windowEnd;
    @LabBusinessId public Long repairId;
    @LabBusinessId public Long reportAttachmentId;
    @LabBusinessTime public LocalDateTime completedAt;
    public Integer version;
    @LabBusinessTime public LocalDateTime createdAt;
}
