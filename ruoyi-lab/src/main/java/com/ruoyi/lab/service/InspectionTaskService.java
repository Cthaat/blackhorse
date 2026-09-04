package com.ruoyi.lab.service;

import java.util.List;
import com.ruoyi.lab.domain.InspectionTaskStatus;
import com.ruoyi.lab.domain.LabInspectionItem;
import com.ruoyi.lab.domain.LabInspectionTask;
import com.ruoyi.lab.dto.RecordInspectionItemCommand;

public interface InspectionTaskService
{
    List<LabInspectionTask> list(InspectionTaskStatus status, Long assigneeId);
    LabInspectionTask get(Long taskId);
    List<LabInspectionItem> items(Long taskId);
    void start(Long taskId, Long actorId, String actorName);
    void recordItem(Long taskId, Long itemId, RecordInspectionItemCommand command,
            Long actorId);
    void complete(Long taskId, Long actorId, String actorName);
}
