package com.ruoyi.lab.domain;

import java.time.Instant;
import java.util.Objects;

/** Immutable half-open interval [start, end). */
public record ReservationInterval(Instant start, Instant end)
{
    public ReservationInterval
    {
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(end, "end");
        if (!start.isBefore(end))
        {
            throw new IllegalArgumentException("reservation start must be before end");
        }
    }

    public boolean overlaps(ReservationInterval other)
    {
        Objects.requireNonNull(other, "other");
        return start.isBefore(other.end) && end.isAfter(other.start);
    }
}
