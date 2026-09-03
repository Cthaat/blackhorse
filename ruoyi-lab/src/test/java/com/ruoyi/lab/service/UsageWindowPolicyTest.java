package com.ruoyi.lab.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import com.ruoyi.lab.exception.LabBusinessException;
import com.ruoyi.lab.exception.LabErrorCode;
import com.ruoyi.lab.service.impl.UsageWindowPolicy;
import org.junit.jupiter.api.Test;

class UsageWindowPolicyTest
{
    private final UsageWindowPolicy policy = new UsageWindowPolicy();
    private final LocalDateTime start = LocalDateTime.of(2026, 9, 3, 10, 0);

    @Test
    void acceptsBothClosedWindowBoundaries()
    {
        assertThatCode(() -> policy.assertWithinWindow(start.minusMinutes(30), start, 30, 15))
                .doesNotThrowAnyException();
        assertThatCode(() -> policy.assertWithinWindow(start.plusMinutes(15), start, 30, 15))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsTimesOutsideWindow()
    {
        assertThatThrownBy(() -> policy.assertWithinWindow(start.minusMinutes(31), start, 30, 15))
                .isInstanceOf(LabBusinessException.class)
                .extracting("errorCode")
                .isEqualTo(LabErrorCode.LAB_ILLEGAL_STATE_TRANSITION);
        assertThatThrownBy(() -> policy.assertWithinWindow(start.plusMinutes(16), start, 30, 15))
                .isInstanceOf(LabBusinessException.class)
                .extracting("errorCode")
                .isEqualTo(LabErrorCode.LAB_ILLEGAL_STATE_TRANSITION);
    }
}
