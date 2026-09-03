package com.ruoyi.lab.service.impl;

import java.time.LocalDateTime;
import com.ruoyi.lab.exception.LabBusinessException;
import com.ruoyi.lab.exception.LabErrorCode;
import org.springframework.stereotype.Component;

/** Closed checkout window around the reservation start time. */
@Component
public class UsageWindowPolicy
{
    public void assertWithinWindow(LocalDateTime now, LocalDateTime reservationStart,
            int earlyMinutes, int lateMinutes)
    {
        if (now == null || reservationStart == null || earlyMinutes < 0 || lateMinutes < 0)
        {
            throw new LabBusinessException(LabErrorCode.VALIDATION_ERROR, "领用时间参数无效");
        }
        LocalDateTime earliest = reservationStart.minusMinutes(earlyMinutes);
        LocalDateTime latest = reservationStart.plusMinutes(lateMinutes);
        if (now.isBefore(earliest) || now.isAfter(latest))
        {
            throw new LabBusinessException(LabErrorCode.LAB_ILLEGAL_STATE_TRANSITION,
                    "当前不在允许的领用时间窗口内");
        }
    }
}
