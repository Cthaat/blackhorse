package com.ruoyi.lab.dto;

/** Immutable station-notification payload carried by an after-commit event. */
public record NotificationCommand(String dedupeKey, Long receiverId, String notificationType,
        String title, String content, String businessType, Long businessId)
{
}
