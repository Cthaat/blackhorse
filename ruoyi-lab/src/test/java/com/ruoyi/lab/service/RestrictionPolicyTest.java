package com.ruoyi.lab.service;

import static org.assertj.core.api.Assertions.*;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class RestrictionPolicyTest
{
    @Test void restrictionIntervalIsHalfOpenAndRevocationWins() throws Exception
    {
        Class<?> policy = Class.forName("com.ruoyi.lab.restriction.RestrictionPolicy");
        var active = policy.getMethod("active", LocalDateTime.class, LocalDateTime.class,
                LocalDateTime.class, LocalDateTime.class);
        var start = LocalDateTime.of(2026, 9, 5, 10, 0);
        assertThat(active.invoke(null, start, start.plusDays(7), null, start)).isEqualTo(true);
        assertThat(active.invoke(null, start, start.plusDays(7), null, start.plusDays(7))).isEqualTo(false);
        assertThat(active.invoke(null, start, start.plusDays(7), start, start)).isEqualTo(false);
    }
}
