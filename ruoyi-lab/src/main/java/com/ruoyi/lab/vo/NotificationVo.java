package com.ruoyi.lab.vo;

import java.time.LocalDateTime;
import com.ruoyi.lab.domain.LabNotification;
import com.ruoyi.lab.domain.NotificationDeliveryStatus;
import com.ruoyi.lab.serializer.LabBusinessId;
import com.ruoyi.lab.serializer.LabBusinessTime;

/** Current user's station notification. */
public record NotificationVo(@LabBusinessId Long id, String notificationType, String title,
        String content, String businessType, @LabBusinessId Long businessId,
        NotificationDeliveryStatus deliveryStatus, @LabBusinessTime LocalDateTime readAt,
        @LabBusinessTime LocalDateTime createTime)
{
    public static NotificationVo from(LabNotification notification)
    {
        return new NotificationVo(notification.getId(), notification.getNotificationType(),
                notification.getTitle(), notification.getContent(), notification.getBusinessType(),
                notification.getBusinessId(), notification.getDeliveryStatus(),
                notification.getReadAt(), notification.getCreateTime());
    }
}
