package com.ruoyi.lab.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class ReservationIntervalTest
{
    @Test
    void usesHalfOpenOverlapSemantics()
    {
        Instant ten = Instant.parse("2026-09-10T02:00:00Z");
        Instant eleven = ten.plusSeconds(3600);
        ReservationInterval base = new ReservationInterval(ten, eleven);

        assertThat(base.overlaps(new ReservationInterval(eleven, eleven.plusSeconds(3600)))).isFalse();
        assertThat(base.overlaps(new ReservationInterval(ten.minusSeconds(1), ten.plusSeconds(1)))).isTrue();
        assertThat(base.overlaps(new ReservationInterval(ten.plusSeconds(1), eleven.plusSeconds(1)))).isTrue();
        assertThat(base.overlaps(new ReservationInterval(ten, eleven))).isTrue();
    }

    @Test
    void rejectsEmptyOrReverseIntervals()
    {
        Instant value = Instant.parse("2026-09-10T02:00:00Z");
        assertThatThrownBy(() -> new ReservationInterval(value, value))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ReservationInterval(value, value.minusSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
