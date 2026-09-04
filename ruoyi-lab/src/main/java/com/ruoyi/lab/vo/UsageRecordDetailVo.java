package com.ruoyi.lab.vo;

import java.time.LocalDateTime;
import com.ruoyi.lab.domain.ReturnCondition;
import com.ruoyi.lab.serializer.LabBusinessId;
import com.ruoyi.lab.serializer.LabBusinessTime;

public record UsageRecordDetailVo(@LabBusinessId Long id,
        @LabBusinessId Long reservationId, @LabBusinessId Long deviceId,
        @LabBusinessId Long userId, @LabBusinessId Long checkoutOperatorId,
        @LabBusinessId Long returnOperatorId, Integer overdueMinutes,
        @LabBusinessId Long repairOrderId, String reservationNo,
        String assetNo, String deviceName, @LabBusinessTime LocalDateTime checkedOutAt,
        String checkoutNote, @LabBusinessTime LocalDateTime returnedAt,
        ReturnCondition returnCondition, String returnNote)
{
}
