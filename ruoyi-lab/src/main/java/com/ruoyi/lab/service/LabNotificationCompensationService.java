package com.ruoyi.lab.service;

import java.time.LocalDateTime;

/** Retries failed rows and reconciles missing rows from durable business facts. */
public interface LabNotificationCompensationService
{
    int retryFailed(LocalDateTime now, int batchSize);

    int reconcileStatusHistory(LocalDateTime now, int batchSize);
}
