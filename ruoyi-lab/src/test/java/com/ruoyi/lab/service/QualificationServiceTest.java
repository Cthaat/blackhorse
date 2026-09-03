package com.ruoyi.lab.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.stream.Stream;
import com.ruoyi.lab.domain.QualificationComputedStatus;
import com.ruoyi.lab.exception.LabBusinessException;
import com.ruoyi.lab.exception.LabErrorCode;
import com.ruoyi.lab.mapper.LabDictionaryMapper;
import com.ruoyi.lab.mapper.LabQualificationMapper;
import com.ruoyi.lab.security.LabDataScopeService;
import com.ruoyi.lab.security.LabObjectPermissionService;
import com.ruoyi.lab.service.impl.QualificationServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class QualificationServiceTest
{
    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-09-03T04:00:00Z"), ZoneId.of("Asia/Shanghai"));
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 3, 12, 0);

    private QualificationService service;

    @BeforeEach
    void setUp()
    {
        service = new QualificationServiceImpl(
                mock(LabQualificationMapper.class),
                mock(LabDictionaryMapper.class),
                mock(LabDataScopeService.class),
                mock(LabObjectPermissionService.class),
                mock(LabSortWhitelist.class),
                mock(LabStatusHistoryService.class),
                FIXED_CLOCK);
    }

    @ParameterizedTest
    @MethodSource("qualificationCases")
    void computesStatus(LocalDateTime validFrom, LocalDateTime validUntil,
            LocalDateTime revokedAt, QualificationComputedStatus expected)
    {
        assertThat(service.computeStatus(validFrom, validUntil, revokedAt, FIXED_CLOCK))
                .isEqualTo(expected);
    }

    @ParameterizedTest
    @MethodSource("invalidValidityRanges")
    void rejectsInvalidValidityRange(LocalDateTime validFrom, LocalDateTime validUntil)
    {
        assertThatThrownBy(() -> service.computeStatus(validFrom, validUntil, null, FIXED_CLOCK))
                .isInstanceOf(LabBusinessException.class)
                .extracting("errorCode")
                .isEqualTo(LabErrorCode.VALIDATION_ERROR);
    }

    @Test
    void rejectsMissingClock()
    {
        assertThatThrownBy(() -> service.computeStatus(NOW, NOW.plusHours(1), null, null))
                .isInstanceOf(LabBusinessException.class)
                .extracting("errorCode")
                .isEqualTo(LabErrorCode.VALIDATION_ERROR);
    }

    private static Stream<Arguments> qualificationCases()
    {
        return Stream.of(
                Arguments.of(NOW.plusNanos(1), NOW.plusHours(1), null,
                        QualificationComputedStatus.NOT_EFFECTIVE),
                Arguments.of(NOW, NOW.plusHours(1), null,
                        QualificationComputedStatus.VALID),
                Arguments.of(NOW.minusHours(1), NOW, null,
                        QualificationComputedStatus.EXPIRED),
                Arguments.of(NOW.minusHours(1), NOW.plusHours(1), NOW.minusMinutes(1),
                        QualificationComputedStatus.REVOKED));
    }

    private static Stream<Arguments> invalidValidityRanges()
    {
        return Stream.of(
                Arguments.of(null, NOW.plusHours(1)),
                Arguments.of(NOW, null),
                Arguments.of(NOW, NOW),
                Arguments.of(NOW.plusSeconds(1), NOW));
    }
}
