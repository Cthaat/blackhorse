package com.ruoyi.lab.vo;

import java.time.LocalDateTime;
import com.ruoyi.lab.serializer.LabBusinessId;
import com.ruoyi.lab.serializer.LabBusinessTime;

public record MessageAttemptVo(@LabBusinessId Long id, String action, Integer attemptNumber, @LabBusinessId Long operatorId,
        String reason, String result, String errorCode, String traceId, @LabBusinessTime LocalDateTime createTime) { }
