package com.ruoyi.lab.service;

import java.util.List;
import com.ruoyi.lab.domain.InspectionPlanStatus;
import com.ruoyi.lab.domain.LabInspectionPlan;
import com.ruoyi.lab.dto.InspectionPlanCommand;

public interface InspectionPlanService
{
    Long create(InspectionPlanCommand command, Long actorId, String actorName);
    void update(Long planId, Integer expectedVersion, InspectionPlanCommand command,
            Long actorId, String actorName);
    void enable(Long planId, Long actorId, String actorName);
    void disable(Long planId, Long actorId, String actorName);
    List<LabInspectionPlan> list(InspectionPlanStatus status, String keyword);
    LabInspectionPlan get(Long planId);
}
