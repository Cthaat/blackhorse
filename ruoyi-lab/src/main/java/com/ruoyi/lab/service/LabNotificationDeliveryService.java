package com.ruoyi.lab.service;

import com.ruoyi.lab.dto.NotificationCommand;

/** Best-effort boundary called after the core business transaction commits. */
public interface LabNotificationDeliveryService
{
    void deliverSafely(NotificationCommand command);
}
