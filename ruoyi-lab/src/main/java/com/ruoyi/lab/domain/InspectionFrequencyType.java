package com.ruoyi.lab.domain;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.temporal.TemporalAdjusters;

/** Controlled inspection frequencies. No executable cron expression is accepted. */
public enum InspectionFrequencyType
{
    DAILY
    {
        @Override
        public LocalDateTime next(LocalDateTime current, int interval)
        {
            return current.plusDays(requireInterval(interval));
        }
    },
    WEEKLY
    {
        @Override
        public LocalDateTime next(LocalDateTime current, int interval)
        {
            return current.plusWeeks(requireInterval(interval));
        }
    },
    MONTHLY
    {
        @Override
        public LocalDateTime next(LocalDateTime current, int interval)
        {
            return current.plusMonths(requireInterval(interval));
        }
    };

    public abstract LocalDateTime next(LocalDateTime current, int interval);

    public LocalDateTime firstAfter(LocalDateTime now, int interval, LocalTime executeTime,
            Integer dayOfWeek, Integer dayOfMonth)
    {
        requireInterval(interval);
        if (now == null || executeTime == null)
        {
            throw new IllegalArgumentException("time is required");
        }
        LocalDateTime candidate;
        switch (this)
        {
            case DAILY -> candidate = LocalDateTime.of(now.toLocalDate(), executeTime);
            case WEEKLY -> {
                if (dayOfWeek == null || dayOfWeek < 1 || dayOfWeek > 7)
                {
                    throw new IllegalArgumentException("dayOfWeek must be between 1 and 7");
                }
                LocalDate date = now.toLocalDate().with(
                        TemporalAdjusters.nextOrSame(DayOfWeek.of(dayOfWeek)));
                candidate = LocalDateTime.of(date, executeTime);
            }
            case MONTHLY -> {
                if (dayOfMonth == null || dayOfMonth < 1 || dayOfMonth > 31)
                {
                    throw new IllegalArgumentException("dayOfMonth must be between 1 and 31");
                }
                YearMonth month = YearMonth.from(now);
                candidate = LocalDateTime.of(month.atDay(Math.min(dayOfMonth, month.lengthOfMonth())),
                        executeTime);
            }
            default -> throw new IllegalStateException("unsupported frequency");
        }
        while (!candidate.isAfter(now))
        {
            candidate = next(candidate, interval);
            if (this == MONTHLY && dayOfMonth != null)
            {
                YearMonth month = YearMonth.from(candidate);
                candidate = LocalDateTime.of(
                        month.atDay(Math.min(dayOfMonth, month.lengthOfMonth())), executeTime);
            }
        }
        return candidate;
    }

    private static int requireInterval(int interval)
    {
        if (interval < 1 || interval > 31)
        {
            throw new IllegalArgumentException("interval must be between 1 and 31");
        }
        return interval;
    }
}
