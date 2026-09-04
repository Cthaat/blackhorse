package com.ruoyi.lab.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;

class NotificationDedupeKeyTest
{
    @Test
    void separatesHistoryRowsAndOverdueRounds()
    {
        assertThat(NotificationDedupeKey.forHistory(801L, "RESERVATION_APPROVED", 18L))
                .isEqualTo(NotificationDedupeKey.forHistory(801L, "RESERVATION_APPROVED", 18L))
                .isNotEqualTo(NotificationDedupeKey.forHistory(802L, "RESERVATION_APPROVED", 18L));
        assertThat(NotificationDedupeKey.forOverdue("INSPECTION_TASK", 91L, 1L, 18L))
                .isNotEqualTo(NotificationDedupeKey.forOverdue("INSPECTION_TASK", 91L, 2L, 18L));
    }

    @Test
    void rejectsNonPositiveIdsAndUnsafeTokens()
    {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> NotificationDedupeKey.forHistory(0L,
                        "RESERVATION_APPROVED", 18L));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> NotificationDedupeKey.forOverdue("../HAZARD",
                        91L, 1L, 18L));
    }
}
