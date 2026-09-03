package com.ruoyi.lab.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import com.ruoyi.lab.dto.ReservationApplyDto;
import com.ruoyi.lab.exception.LabBusinessException;
import org.junit.jupiter.api.Test;

class ReservationPolicyTest
{
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-03T04:00:00Z"), ZONE);

    @Test
    void acceptsExactPolicyBoundaries()
    {
        ReservationPolicy.ValidatedReservation validated = policy().validate(request(
                OffsetDateTime.parse("2026-09-03T12:30:00+08:00"),
                OffsetDateTime.parse("2026-09-03T13:00:00+08:00")));
        assertThat(validated.purpose()).isEqualTo("显微观察");
    }

    @Test
    void rejectsDurationOneSecondBeyondMaximum()
    {
        assertThatThrownBy(() -> policy().validate(request(
                OffsetDateTime.parse("2026-09-03T12:30:00+08:00"),
                OffsetDateTime.parse("2026-09-03T20:30:01+08:00"))))
                .isInstanceOf(LabBusinessException.class);
    }

    private static ReservationPolicy policy()
    {
        LabSystemParameterProvider parameters = (key, minimum, maximum) -> switch (key)
        {
            case "lab.reservation.min-lead-minutes" -> 30;
            case "lab.reservation.max-advance-days" -> 30;
            case "lab.reservation.min-duration-minutes" -> 30;
            case "lab.reservation.max-duration-minutes" -> 480;
            default -> throw new IllegalArgumentException(key);
        };
        return new ReservationPolicy(parameters, CLOCK);
    }

    private static ReservationApplyDto request(OffsetDateTime start, OffsetDateTime end)
    {
        ReservationApplyDto request = new ReservationApplyDto();
        request.setDeviceId(10L);
        request.setStartTime(start);
        request.setEndTime(end);
        request.setPurpose("  显微观察  ");
        return request;
    }
}
