package com.ruoyi.lab.service;

import java.time.LocalDateTime;
import com.ruoyi.lab.dto.NotificationCommand;

/** Independent-transaction persistence used by delivery and compensation. */
public interface LabNotificationPersistenceService
{
    long insertSent(NotificationCommand command);

    void recordFailed(NotificationCommand command, String errorCode,
            LocalDateTime nextRetryAt);
}
