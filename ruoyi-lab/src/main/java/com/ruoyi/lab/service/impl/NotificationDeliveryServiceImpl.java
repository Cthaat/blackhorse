package com.ruoyi.lab.service.impl;

import java.time.Clock;
import java.time.LocalDateTime;
import com.ruoyi.lab.domain.LabNotification;
import com.ruoyi.lab.domain.NotificationDeliveryStatus;
import com.ruoyi.lab.exception.LabBusinessException;
import com.ruoyi.lab.exception.LabErrorCode;
import com.ruoyi.lab.mapper.LabNotificationMapper;
import com.ruoyi.lab.service.NotificationDeliveryService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** MySQL-deduplicated station notification delivery. */
@Service
public class NotificationDeliveryServiceImpl implements NotificationDeliveryService
{
    private final LabNotificationMapper notificationMapper;
    private final Clock clock;

    public NotificationDeliveryServiceImpl(LabNotificationMapper notificationMapper, Clock clock)
    {
        this.notificationMapper = notificationMapper;
        this.clock = clock;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public long deliver(NotificationEvent event)
    {
        validate(event);
        LabNotification existing = notificationMapper.selectByDedupeKey(event.dedupeKey());
        if (existing != null)
        {
            return existing.getId();
        }
        LabNotification notification = from(event);
        try
        {
            notificationMapper.insert(notification);
            return notification.getId();
        }
        catch (DuplicateKeyException exception)
        {
            LabNotification winner = notificationMapper.selectByDedupeKey(event.dedupeKey());
            if (winner != null)
            {
                return winner.getId();
            }
            throw exception;
        }
    }

    @Override
    @Transactional
    public int compensateDue(int batchSize)
    {
        if (batchSize < 1 || batchSize > 1000)
        {
            throw new LabBusinessException(LabErrorCode.VALIDATION_ERROR, "补偿批量大小无效");
        }
        LocalDateTime now = LocalDateTime.now(clock);
        int changed = 0;
        for (Long id : notificationMapper.selectDueFailedIds(now, batchSize))
        {
            changed += notificationMapper.markSent(id, now);
        }
        return changed;
    }

    private LabNotification from(NotificationEvent event)
    {
        LabNotification notification = new LabNotification();
        notification.setDedupeKey(event.dedupeKey());
        notification.setReceiverId(event.receiverId());
        notification.setNotificationType(event.notificationType());
        notification.setTitle(event.title());
        notification.setContent(event.content());
        notification.setBusinessType(event.businessType());
        notification.setBusinessId(event.businessId());
        notification.setDeliveryStatus(NotificationDeliveryStatus.SENT);
        notification.setAttemptCount(1);
        notification.setCreateBy("system");
        notification.setCreateTime(LocalDateTime.now(clock));
        notification.setUpdateBy("");
        return notification;
    }

    private static void validate(NotificationEvent event)
    {
        if (event == null || blank(event.dedupeKey()) || event.dedupeKey().length() > 128
                || event.receiverId() == null || event.receiverId() <= 0
                || blank(event.notificationType()) || event.notificationType().length() > 32
                || blank(event.title()) || event.title().length() > 128
                || blank(event.content()) || event.content().length() > 500
                || blank(event.businessType()) || event.businessType().length() > 32
                || event.businessId() == null || event.businessId() <= 0)
        {
            throw new LabBusinessException(LabErrorCode.VALIDATION_ERROR, "通知事件参数无效");
        }
    }

    private static boolean blank(String value)
    {
        return value == null || value.isBlank();
    }
}
