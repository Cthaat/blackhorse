package com.ruoyi.lab.service;

/** Idempotent creation and compensation of station notifications. */
public interface NotificationDeliveryService
{
    long deliver(NotificationEvent event);

    int compensateDue(int batchSize);

    record NotificationEvent(String dedupeKey, Long receiverId, String notificationType,
            String title, String content, String businessType, Long businessId)
    {
    }
}
