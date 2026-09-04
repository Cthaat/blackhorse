package com.ruoyi.lab.service;

import java.util.List;
import com.ruoyi.lab.vo.NotificationVo;

/** Current-user message center operations. */
public interface NotificationService
{
    List<NotificationVo> listMine(Long currentUserId, boolean unreadOnly);

    NotificationVo getMine(Long notificationId, Long currentUserId);

    NotificationVo markRead(Long notificationId, Long currentUserId);
}
