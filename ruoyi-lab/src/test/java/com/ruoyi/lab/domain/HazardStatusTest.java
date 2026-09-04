package com.ruoyi.lab.domain;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

class HazardStatusTest
{
    @Test
    void permitsOnlyMultiRoundRectificationTransitions()
    {
        assertThat(HazardStatus.PENDING_RECTIFICATION.canMoveTo(HazardStatus.RECTIFYING)).isTrue();
        assertThat(HazardStatus.RECTIFYING.canMoveTo(HazardStatus.PENDING_REVIEW)).isTrue();
        assertThat(HazardStatus.PENDING_REVIEW.canMoveTo(HazardStatus.RECTIFYING)).isTrue();
        assertThat(HazardStatus.PENDING_REVIEW.canMoveTo(HazardStatus.CLOSED)).isTrue();
        assertThat(HazardStatus.CLOSED.canMoveTo(HazardStatus.RECTIFYING)).isFalse();
        assertThat(HazardStatus.PENDING_RECTIFICATION.canMoveTo(HazardStatus.CLOSED)).isFalse();
    }
}
