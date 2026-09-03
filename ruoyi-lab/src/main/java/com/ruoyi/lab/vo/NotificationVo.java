package com.ruoyi.lab.vo;

import java.time.LocalDateTime;
import com.ruoyi.lab.domain.LabNotification;
import com.ruoyi.lab.domain.NotificationDeliveryStatus;

/** Current user's station notification. */
public record NotificationVo(Long id, String notificationType, String title, String content,
        String businessType, Long businessId, NotificationDeliveryStatus deliveryStatus,
        LocalDateTime readAt, LocalDateTime createTime)
{
    public static NotificationVo from(LabNotification notification)
    {
        return new NotificationVo(notification.getId(), notification.getNotificationType(),
                notification.getTitle(), notification.getContent(), notification.getBusinessType(),
                notification.getBusinessId(), notification.getDeliveryStatus(),
                notification.getReadAt(), notification.getCreateTime());
    }
}
