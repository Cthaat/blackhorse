package com.ruoyi.lab.vo;

import java.time.LocalDateTime;
import com.ruoyi.lab.domain.LabUsageRecord;
import com.ruoyi.lab.domain.ReturnCondition;
import com.ruoyi.lab.serializer.LabBusinessId;
import com.ruoyi.lab.serializer.LabBusinessTime;

public record UsageRecordVo(@LabBusinessId Long id, @LabBusinessId Long reservationId,
        @LabBusinessId Long deviceId,
        String reservationNo, String assetNo, String deviceName,
        @LabBusinessTime LocalDateTime checkedOutAt,
        @LabBusinessTime LocalDateTime returnedAt,
        ReturnCondition returnCondition)
{
    public static UsageRecordVo from(LabUsageRecord usage)
    {
        return new UsageRecordVo(usage.getId(), usage.getReservationId(), usage.getDeviceId(),
                null, null, null, usage.getCheckedOutAt(), usage.getReturnedAt(),
                usage.getReturnCondition());
    }
}
