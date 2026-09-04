package com.ruoyi.lab.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.time.LocalDateTime;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;

class InspectionFrequencyTest
{
    @Test
    void calculatesOnlyControlledDailyWeeklyAndMonthlySchedules()
    {
        LocalDateTime now = LocalDateTime.of(2026, 9, 3, 10, 0);
        assertThat(InspectionFrequencyType.DAILY.firstAfter(now, 1,
                LocalTime.of(9, 0), null, null))
                .isEqualTo(LocalDateTime.of(2026, 9, 4, 9, 0));
        assertThat(InspectionFrequencyType.WEEKLY.firstAfter(now, 1,
                LocalTime.of(8, 0), 5, null))
                .isEqualTo(LocalDateTime.of(2026, 9, 4, 8, 0));
        assertThat(InspectionFrequencyType.MONTHLY.firstAfter(now, 1,
                LocalTime.of(8, 30), null, 31))
                .isEqualTo(LocalDateTime.of(2026, 9, 30, 8, 30));
        assertThatThrownBy(() -> InspectionFrequencyType.WEEKLY.firstAfter(
                now, 1, LocalTime.NOON, 8, null)).isInstanceOf(IllegalArgumentException.class);
    }
}
