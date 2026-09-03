package com.ruoyi.lab.service;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import com.ruoyi.lab.dto.ReservationApplyDto;
import com.ruoyi.lab.exception.LabBusinessException;
import com.ruoyi.lab.exception.LabErrorCode;
import org.springframework.stereotype.Service;

/** Normalizes and validates the configurable reservation time policy. */
@Service
public class ReservationPolicy
{
    private static final ZoneOffset API_OFFSET = ZoneOffset.ofHours(8);

    private final LabSystemParameterProvider parameters;
    private final Clock clock;

    public ReservationPolicy(LabSystemParameterProvider parameters, Clock clock)
    {
        this.parameters = parameters;
        this.clock = clock;
    }

    public ValidatedReservation validate(ReservationApplyDto input)
    {
        if (input == null || input.getDeviceId() == null || input.getDeviceId() <= 0
                || input.getStartTime() == null || input.getEndTime() == null)
        {
            throw invalid("预约参数不完整");
        }
        if (!API_OFFSET.equals(input.getStartTime().getOffset())
                || !API_OFFSET.equals(input.getEndTime().getOffset()))
        {
            throw invalid("预约时间必须使用东八区偏移");
        }
        String purpose = normalizeRequired(input.getPurpose(), 200, "预约用途无效");
        String remark = normalizeOptional(input.getRemark(), 500, "预约备注过长");
        LocalDateTime start = input.getStartTime().toLocalDateTime();
        LocalDateTime end = input.getEndTime().toLocalDateTime();
        if (!start.isBefore(end))
        {
            throw invalid("预约开始时间必须早于结束时间");
        }

        int minDuration = parameters.requiredInteger("lab.reservation.min-duration-minutes", 1, 1440);
        int maxDuration = parameters.requiredInteger("lab.reservation.max-duration-minutes", minDuration, 10080);
        Duration duration = Duration.between(start, end);
        if (duration.compareTo(Duration.ofMinutes(minDuration)) < 0
                || duration.compareTo(Duration.ofMinutes(maxDuration)) > 0)
        {
            throw invalid("预约时长不符合规则");
        }

        LocalDateTime now = LocalDateTime.now(clock);
        int minLead = parameters.requiredInteger("lab.reservation.min-lead-minutes", 0, 10080);
        int maxAdvanceDays = parameters.requiredInteger("lab.reservation.max-advance-days", 1, 365);
        if (start.isBefore(now.plusMinutes(minLead)) || start.isAfter(now.plusDays(maxAdvanceDays)))
        {
            throw invalid("预约开始时间不符合提前量规则");
        }
        return new ValidatedReservation(input.getDeviceId(), start, end, purpose, remark);
    }

    private static String normalizeRequired(String value, int maximum, String message)
    {
        String normalized = normalizeOptional(value, maximum, message);
        if (normalized == null)
        {
            throw invalid(message);
        }
        return normalized;
    }

    private static String normalizeOptional(String value, int maximum, String message)
    {
        if (value == null)
        {
            return null;
        }
        String normalized = value.trim();
        if (normalized.isEmpty())
        {
            return null;
        }
        if (normalized.length() > maximum)
        {
            throw invalid(message);
        }
        return normalized;
    }

    private static LabBusinessException invalid(String message)
    {
        return new LabBusinessException(LabErrorCode.VALIDATION_ERROR, message);
    }

    public record ValidatedReservation(Long deviceId, LocalDateTime startTime,
            LocalDateTime endTime, String purpose, String remark)
    {
    }
}
