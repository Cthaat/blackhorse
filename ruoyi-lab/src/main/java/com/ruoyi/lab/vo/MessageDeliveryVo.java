package com.ruoyi.lab.vo;

import java.time.LocalDateTime;
import com.ruoyi.lab.serializer.LabBusinessId;
import com.ruoyi.lab.serializer.LabBusinessTime;

/** Deliberately excludes receiver, dedupe key and rendered private body. */
public record MessageDeliveryVo(@LabBusinessId Long id, String eventType, String sourceType, @LabBusinessId Long sourceId,
        Long eventVersion, String templateVersion, String status, Integer attemptCount,
        @LabBusinessTime LocalDateTime nextRetryAt, @LabBusinessTime LocalDateTime leaseUntil, String errorCode, String traceId,
        @LabBusinessTime LocalDateTime createTime, @LabBusinessTime LocalDateTime updateTime) { }
