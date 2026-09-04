package com.ruoyi.lab.mapper;

import java.time.LocalDateTime;
import java.util.List;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ruoyi.lab.domain.LabNotification;
import org.apache.ibatis.annotations.Param;

/** Notification persistence with immutable deduplication keys. */
public interface LabNotificationMapper extends BaseMapper<LabNotification>
{
    LabNotification selectByDedupeKey(@Param("dedupeKey") String dedupeKey);

    LabNotification selectMineById(@Param("notificationId") Long notificationId,
            @Param("receiverId") Long receiverId);

    List<LabNotification> selectMine(@Param("receiverId") Long receiverId,
            @Param("unreadOnly") boolean unreadOnly);

    List<Long> selectDueFailedIds(@Param("now") LocalDateTime now, @Param("limit") int limit);

    List<LabNotification> selectRetryable(@Param("now") LocalDateTime now,
            @Param("limit") int limit);

    int markReadMine(@Param("notificationId") Long notificationId,
            @Param("receiverId") Long receiverId, @Param("readAt") LocalDateTime readAt);

    int markSent(@Param("notificationId") Long notificationId,
            @Param("updateTime") LocalDateTime updateTime);

    int markFailed(@Param("notificationId") Long notificationId,
            @Param("nextRetryAt") LocalDateTime nextRetryAt,
            @Param("lastErrorCode") String lastErrorCode,
            @Param("updateTime") LocalDateTime updateTime);
}
