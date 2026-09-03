package com.ruoyi.lab.vo;

import java.time.LocalDateTime;
import com.ruoyi.lab.domain.ReturnCondition;

public record UsageRecordDetailVo(Long id, Long reservationId, Long deviceId,
        Long userId, Long repairOrderId, String reservationNo, String assetNo,
        String deviceName, LocalDateTime checkedOutAt, String checkoutNote,
        LocalDateTime returnedAt, ReturnCondition returnCondition, String returnNote)
{
}
