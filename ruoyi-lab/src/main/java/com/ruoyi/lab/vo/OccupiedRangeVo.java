package com.ruoyi.lab.vo;

import java.time.LocalDateTime;
import com.ruoyi.lab.serializer.LabBusinessTime;

/** Stable read-only occupied interval contract; intervals are half-open. */
public record OccupiedRangeVo(@LabBusinessTime LocalDateTime startTime,
        @LabBusinessTime LocalDateTime endTime, String reservationStatus)
{
}
