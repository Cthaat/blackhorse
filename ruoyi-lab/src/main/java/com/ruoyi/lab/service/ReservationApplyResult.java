package com.ruoyi.lab.service;

import java.util.Objects;
import com.ruoyi.lab.vo.ReservationVo;

/** Internal result used to distinguish a new reservation from an idempotent replay. */
public record ReservationApplyResult(ReservationVo reservation, boolean replayed)
{
    public ReservationApplyResult
    {
        Objects.requireNonNull(reservation, "reservation");
    }
}
