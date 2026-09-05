package com.ruoyi.lab.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import com.ruoyi.lab.dto.ReservationRuleDefinition;
import com.ruoyi.lab.exception.LabBusinessException;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ReservationRuleEvaluatorTest
{
    private final LocalDateTime now = LocalDateTime.parse("2026-09-07T08:00:00");

    @Test void acceptsExactOpeningAndDurationBoundaries()
    {
        assertThatCode(() -> check(rule(List.of()), "2026-09-07T09:00", "2026-09-07T17:00"))
                .doesNotThrowAnyException();
    }

    @Test void rejectsClosedDateWithReadableReason()
    {
        var closed = new ReservationRuleDefinition.ClosedDay(LocalDate.of(2026, 9, 7), "计量校准");
        assertThatThrownBy(() -> check(rule(List.of(closed)), "2026-09-07T10:00", "2026-09-07T11:00"))
                .isInstanceOf(LabBusinessException.class).hasMessageContaining("当日关闭");
    }

    @Test void rejectsWeekendAndOvernightAndOutsideWindow()
    {
        for (String[] range : List.of(new String[]{"2026-09-12T10:00", "2026-09-12T11:00"},
                new String[]{"2026-09-07T16:00", "2026-09-08T10:00"},
                new String[]{"2026-09-07T16:30", "2026-09-07T17:01"}))
        {
            assertThatThrownBy(() -> check(rule(List.of()), range[0], range[1]))
                    .isInstanceOf(LabBusinessException.class);
        }
    }

    @Test void rejectsInsufficientLeadAndExcessAdvance()
    {
        assertThatThrownBy(() -> check(rule(List.of()), "2026-09-07T08:30", "2026-09-07T09:30"))
                .isInstanceOf(LabBusinessException.class);
        assertThatThrownBy(() -> check(rule(List.of()), "2026-10-12T10:00", "2026-10-12T11:00"))
                .isInstanceOf(LabBusinessException.class);
    }

    private void check(ReservationRuleDefinition rule, String start, String end)
    {
        ReservationRuleEvaluator.validate(rule, LocalDateTime.parse(start), LocalDateTime.parse(end), now);
    }

    private ReservationRuleDefinition rule(List<ReservationRuleDefinition.ClosedDay> closed)
    {
        return new ReservationRuleDefinition("工作日规则", List.of(1, 2, 3, 4, 5), "09:00", "17:00",
                closed, 60, 30, 30, 480, 15);
    }
}
