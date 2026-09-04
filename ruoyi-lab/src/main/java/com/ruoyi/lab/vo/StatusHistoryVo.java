package com.ruoyi.lab.vo;

import java.time.LocalDateTime;
import com.ruoyi.lab.serializer.LabBusinessId;
import com.ruoyi.lab.serializer.LabBusinessTime;

/** Client-safe, read-only business state transition. */
public record StatusHistoryVo(@LabBusinessId Long id, String objectType,
        @LabBusinessId Long objectId, String fromStatus, String toStatus,
        @LabBusinessId Long operatorId, String operatorName, String reason, String traceId,
        @LabBusinessTime LocalDateTime createTime)
{
}
