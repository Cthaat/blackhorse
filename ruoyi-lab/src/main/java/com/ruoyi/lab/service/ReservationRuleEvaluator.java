package com.ruoyi.lab.service;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.Duration;
import com.ruoyi.lab.dto.ReservationRuleDefinition;
import com.ruoyi.lab.exception.LabBusinessException;
import com.ruoyi.lab.exception.LabErrorCode;

/** Pure rule evaluation, independent of persistence and the request principal. */
public final class ReservationRuleEvaluator
{
    private ReservationRuleEvaluator() { }

    public static void validate(ReservationRuleDefinition rule, LocalDateTime start,
            LocalDateTime end, LocalDateTime now)
    {
        if (!start.isBefore(end) || !start.toLocalDate().equals(end.toLocalDate()))
        {
            throw denied("设备开放规则仅允许同日时段");
        }
        if (!rule.weekdays().contains(start.getDayOfWeek().getValue()))
        {
            throw denied("该星期不开放预约");
        }
        rule.closedDays().stream().filter(day -> day.date().equals(start.toLocalDate()))
                .findFirst().ifPresent(day -> { throw denied("当日关闭，具体原因请查看开放日历"); });
        if (start.toLocalTime().isBefore(LocalTime.parse(rule.opensAt()))
                || end.toLocalTime().isAfter(LocalTime.parse(rule.closesAt())))
        {
            throw denied("时段超出每日开放窗口，请查看开放日历");
        }
        Duration duration = Duration.between(start, end);
        if (duration.compareTo(Duration.ofMinutes(rule.minDurationMinutes())) < 0
                || duration.compareTo(Duration.ofMinutes(rule.maxDurationMinutes())) > 0)
        {
            throw denied("时长不符合设备规则");
        }
        if (start.isBefore(now.plusMinutes(rule.minLeadMinutes()))
                || start.isAfter(now.plusDays(rule.maxAdvanceDays())))
        {
            throw denied("时段不符合设备规则的提前量");
        }
    }

    private static LabBusinessException denied(String message)
    {
        return new LabBusinessException(LabErrorCode.LAB_RESERVATION_RULE_DENIED, message);
    }
}
