package com.ruoyi.lab.service.impl;

import java.time.Clock;
import java.time.LocalDateTime;
import com.ruoyi.lab.domain.LabNotification;
import com.ruoyi.lab.domain.NotificationDeliveryStatus;
import com.ruoyi.lab.dto.NotificationCommand;
import com.ruoyi.lab.exception.LabBusinessException;
import com.ruoyi.lab.exception.LabErrorCode;
import com.ruoyi.lab.mapper.LabNotificationMapper;
import com.ruoyi.lab.service.LabNotificationPersistenceService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** The only writer for SENT/FAILED station-notification rows. */
@Service
public class LabNotificationPersistenceServiceImpl implements LabNotificationPersistenceService
{
    private final LabNotificationMapper notificationMapper;
    private final Clock clock;

    public LabNotificationPersistenceServiceImpl(LabNotificationMapper notificationMapper,
            Clock clock)
    {
        this.notificationMapper = notificationMapper;
        this.clock = clock;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public long insertSent(NotificationCommand command)
    {
        validate(command);
        LocalDateTime now = LocalDateTime.now(clock);
        LabNotification existing = notificationMapper.selectByDedupeKey(command.dedupeKey());
        if (existing != null)
        {
            if (existing.getDeliveryStatus() == NotificationDeliveryStatus.FAILED)
            {
                notificationMapper.markSent(existing.getId(), now);
            }
            return existing.getId();
        }
        LabNotification notification = from(command, NotificationDeliveryStatus.SENT, now);
        try
        {
            notificationMapper.insert(notification);
            return notification.getId();
        }
        catch (DuplicateKeyException duplicate)
        {
            LabNotification winner = notificationMapper.selectByDedupeKey(command.dedupeKey());
            if (winner != null)
            {
                return winner.getId();
            }
            throw duplicate;
        }
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailed(NotificationCommand command, String errorCode,
            LocalDateTime nextRetryAt)
    {
        validate(command);
        if (errorCode == null || errorCode.isBlank() || errorCode.length() > 64
                || nextRetryAt == null)
        {
            throw validation();
        }
        LocalDateTime now = LocalDateTime.now(clock);
        LabNotification existing = notificationMapper.selectByDedupeKey(command.dedupeKey());
        if (existing != null)
        {
            if (existing.getDeliveryStatus() == NotificationDeliveryStatus.FAILED)
            {
                notificationMapper.markFailed(existing.getId(), nextRetryAt, errorCode, now);
            }
            return;
        }
        LabNotification failed = from(command, NotificationDeliveryStatus.FAILED, now);
        failed.setNextRetryAt(nextRetryAt);
        failed.setLastErrorCode(errorCode);
        try
        {
            notificationMapper.insert(failed);
        }
        catch (DuplicateKeyException duplicate)
        {
            LabNotification winner = notificationMapper.selectByDedupeKey(command.dedupeKey());
            if (winner != null && winner.getDeliveryStatus() == NotificationDeliveryStatus.FAILED)
            {
                notificationMapper.markFailed(winner.getId(), nextRetryAt, errorCode, now);
                return;
            }
            if (winner == null)
            {
                throw duplicate;
            }
        }
    }

    private static LabNotification from(NotificationCommand command,
            NotificationDeliveryStatus status, LocalDateTime now)
    {
        LabNotification notification = new LabNotification();
        notification.setDedupeKey(command.dedupeKey());
        notification.setReceiverId(command.receiverId());
        notification.setNotificationType(command.notificationType());
        notification.setTitle(command.title());
        notification.setContent(command.content());
        notification.setBusinessType(command.businessType());
        notification.setBusinessId(command.businessId());
        notification.setDeliveryStatus(status);
        notification.setAttemptCount(1);
        notification.setCreateBy("system");
        notification.setCreateTime(now);
        notification.setUpdateBy("");
        return notification;
    }

    private static void validate(NotificationCommand command)
    {
        if (command == null || blank(command.dedupeKey()) || command.dedupeKey().length() > 128
                || command.receiverId() == null || command.receiverId() <= 0
                || blank(command.notificationType()) || command.notificationType().length() > 32
                || blank(command.title()) || command.title().length() > 128
                || blank(command.content()) || command.content().length() > 500
                || blank(command.businessType()) || command.businessType().length() > 32
                || command.businessId() == null || command.businessId() <= 0)
        {
            throw validation();
        }
    }

    private static boolean blank(String value)
    {
        return value == null || value.isBlank();
    }

    private static LabBusinessException validation()
    {
        return new LabBusinessException(LabErrorCode.VALIDATION_ERROR, "通知事件参数无效");
    }
}
