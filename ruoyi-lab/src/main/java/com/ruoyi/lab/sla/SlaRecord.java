package com.ruoyi.lab.sla;

import java.time.LocalDateTime;
import com.ruoyi.lab.serializer.*;

public class SlaRecord
{
    @LabBusinessId public Long id;
    public String objectType;
    @LabBusinessId public Long objectId;
    public String businessType;
    public String risk;
    @LabBusinessId public Long laboratoryId;
    @LabBusinessId public Long deviceId;
    @LabBusinessId public Long repairId;
    @LabBusinessId public Long ownerId;
    public String ownerName;
    public String title;
    @LabBusinessId public Long ruleVersionId;
    public Integer responseHours;
    public Integer processingHours;
    @LabBusinessTime public LocalDateTime openedAt;
    @LabBusinessTime public LocalDateTime responseDueAt;
    @LabBusinessTime public LocalDateTime processingDueAt;
    @LabBusinessTime public LocalDateTime respondedAt;
    @LabBusinessTime public LocalDateTime startedAt;
    @LabBusinessTime public LocalDateTime completedAt;
    @LabBusinessTime public LocalDateTime closedAt;
    @LabBusinessTime public LocalDateTime pausedAt;
    public String pauseReason;
    public Long totalPausedSeconds;
    public Integer version;
    public String state;
    public boolean canManage;
    public boolean baseline;
}
