package com.ruoyi.lab.vo;

import java.time.LocalDateTime;
import com.ruoyi.lab.domain.LabUsageRecord;
import com.ruoyi.lab.domain.ReturnCondition;

public record UsageRecordVo(Long id, Long reservationId, Long deviceId,
        String reservationNo, String assetNo, String deviceName,
        LocalDateTime checkedOutAt, LocalDateTime returnedAt,
        ReturnCondition returnCondition)
{
    public static UsageRecordVo from(LabUsageRecord usage)
    {
        return new UsageRecordVo(usage.getId(), usage.getReservationId(), usage.getDeviceId(),
                null, null, null, usage.getCheckedOutAt(), usage.getReturnedAt(),
                usage.getReturnCondition());
    }
}
