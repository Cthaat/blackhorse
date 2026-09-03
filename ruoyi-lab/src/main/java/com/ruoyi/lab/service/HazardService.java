package com.ruoyi.lab.service;

import java.util.List;
import com.ruoyi.lab.domain.HazardSeverity;
import com.ruoyi.lab.domain.HazardStatus;
import com.ruoyi.lab.domain.LabHazard;
import com.ruoyi.lab.domain.LabInspectionItem;
import com.ruoyi.lab.domain.LabRectification;
import com.ruoyi.lab.dto.CreateHazardCommand;

public interface HazardService
{
    Long create(CreateHazardCommand command, Long actorId, String actorName);
    Long createFromInspectionItem(LabInspectionItem item, Long actorId, String actorName);
    List<LabHazard> list(HazardStatus status, HazardSeverity severity, Long ownerId);
    LabHazard get(Long hazardId);
    List<LabRectification> rectifications(Long hazardId);
}
