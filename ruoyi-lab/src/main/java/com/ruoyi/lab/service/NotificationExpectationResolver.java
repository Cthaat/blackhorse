package com.ruoyi.lab.service;

import java.util.List;
import com.ruoyi.lab.dto.NotificationCommand;

/** Rebuilds expected notifications from persisted business facts. */
public interface NotificationExpectationResolver
{
    List<NotificationCommand> resolveHistory(long historyId);

    List<NotificationCommand> resolveInspectionOverdue(long taskId, long overdueEventVersion);

    List<NotificationCommand> resolveHazardOverdue(long hazardId, long overdueEventVersion);
}
