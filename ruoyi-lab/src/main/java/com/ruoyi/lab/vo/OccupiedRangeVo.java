package com.ruoyi.lab.vo;

import java.time.LocalDateTime;

/** Stable read-only occupied interval contract; intervals are half-open. */
public record OccupiedRangeVo(LocalDateTime startTime, LocalDateTime endTime, String reservationStatus)
{
}
