package com.ruoyi.lab.service;

import static org.assertj.core.api.Assertions.*;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class MaintenancePolicyTest
{
    @Test void nextCycleUsesAcceptedTimeAndHalfOpenWindowsDoNotBlockAdjacentBookings() throws Exception
    {
        assertThatCode(() -> Class.forName("com.ruoyi.lab.maintenance.MaintenancePolicy")).doesNotThrowAnyException();
        Class<?> policy=Class.forName("com.ruoyi.lab.maintenance.MaintenancePolicy");
        LocalDateTime accepted=LocalDateTime.of(2026,9,5,12,0);
        assertThat(policy.getMethod("nextDue",LocalDateTime.class,int.class).invoke(null,accepted,30))
                .isEqualTo(accepted.plusDays(30));
        var overlap=policy.getMethod("overlaps",LocalDateTime.class,LocalDateTime.class,LocalDateTime.class,LocalDateTime.class);
        assertThat(overlap.invoke(null,accepted,accepted.plusHours(1),accepted.plusHours(1),accepted.plusHours(2))).isEqualTo(false);
        assertThat(overlap.invoke(null,accepted,accepted.plusHours(1),accepted.plusMinutes(30),accepted.plusHours(2))).isEqualTo(true);
    }
}
