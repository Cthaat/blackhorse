package com.ruoyi.lab.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RepairStatusTest
{
    @Test
    void allowsOnlyApprovedTransitions()
    {
        assertThat(RepairStatus.WAIT_ASSIGN.canMoveTo(RepairStatus.WAIT_REPAIR)).isTrue();
        assertThat(RepairStatus.WAIT_REPAIR.canMoveTo(RepairStatus.IN_PROGRESS)).isTrue();
        assertThat(RepairStatus.IN_PROGRESS.canMoveTo(RepairStatus.WAIT_ACCEPTANCE)).isTrue();
        assertThat(RepairStatus.WAIT_ACCEPTANCE.canMoveTo(RepairStatus.IN_PROGRESS)).isTrue();
        assertThat(RepairStatus.WAIT_ACCEPTANCE.canMoveTo(RepairStatus.CLOSED)).isTrue();
        assertThat(RepairStatus.WAIT_ASSIGN.canMoveTo(RepairStatus.CLOSED)).isFalse();
        assertThat(RepairStatus.CLOSED.canMoveTo(RepairStatus.IN_PROGRESS)).isFalse();
    }
}
