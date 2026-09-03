package com.ruoyi.lab.service.impl;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import com.ruoyi.lab.domain.LabNotification;
import com.ruoyi.lab.exception.LabBusinessException;
import com.ruoyi.lab.exception.LabErrorCode;
import com.ruoyi.lab.mapper.LabNotificationMapper;
import com.ruoyi.lab.service.NotificationService;
import com.ruoyi.lab.vo.NotificationVo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Receiver-bound notification query and read marker. */
@Service
public class NotificationServiceImpl implements NotificationService
{
    private final LabNotificationMapper notificationMapper;
    private final Clock clock;

    public NotificationServiceImpl(LabNotificationMapper notificationMapper, Clock clock)
    {
        this.notificationMapper = notificationMapper;
        this.clock = clock;
    }

    @Override
    public List<NotificationVo> listMine(Long currentUserId, boolean unreadOnly)
    {
        long userId = requirePositive(currentUserId);
        return notificationMapper.selectMine(userId, unreadOnly).stream()
                .map(NotificationVo::from).toList();
    }

    @Override
    public NotificationVo getMine(Long notificationId, Long currentUserId)
    {
        return NotificationVo.from(requireMine(notificationId, currentUserId));
    }

    @Override
    @Transactional
    public NotificationVo markRead(Long notificationId, Long currentUserId)
    {
        LabNotification notification = requireMine(notificationId, currentUserId);
        if (notification.getReadAt() == null)
        {
            notificationMapper.markReadMine(notification.getId(), notification.getReceiverId(),
                    LocalDateTime.now(clock));
        }
        return NotificationVo.from(requireMine(notificationId, currentUserId));
    }

    private LabNotification requireMine(Long notificationId, Long currentUserId)
    {
        long id = requirePositive(notificationId);
        long userId = requirePositive(currentUserId);
        LabNotification notification = notificationMapper.selectMineById(id, userId);
        if (notification == null)
        {
            throw new LabBusinessException(LabErrorCode.RESOURCE_NOT_FOUND, "通知不存在");
        }
        return notification;
    }

    private static long requirePositive(Long value)
    {
        if (value == null || value <= 0)
        {
            throw new LabBusinessException(LabErrorCode.VALIDATION_ERROR, "对象编号无效");
        }
        return value;
    }
}
