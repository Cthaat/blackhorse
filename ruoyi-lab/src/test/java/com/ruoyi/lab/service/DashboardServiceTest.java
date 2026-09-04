package com.ruoyi.lab.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import com.ruoyi.lab.dto.DashboardQueryDto;
import com.ruoyi.lab.mapper.LabDashboardMapper;
import com.ruoyi.lab.security.LabDataScope;
import com.ruoyi.lab.security.LabDataScopeService;
import com.ruoyi.lab.service.impl.DashboardServiceImpl;
import com.ruoyi.lab.vo.DashboardSnapshotVo;
import com.ruoyi.lab.vo.LabMetricVo;
import org.junit.jupiter.api.Test;

class DashboardServiceTest
{
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-03T04:00:00Z"),
            ZoneId.of("Asia/Shanghai"));

    @Test
    void returnsRoleTodosAndScopedWindowedStatistics()
    {
        LabDashboardMapper mapper = mock(LabDashboardMapper.class);
        LabDataScopeService scopes = mock(LabDataScopeService.class);
        LabDataScope scope = new LabDataScope(7L, false, Set.of(99L));
        OffsetDateTime start = OffsetDateTime.parse("2026-08-01T00:00:00+08:00");
        OffsetDateTime end = OffsetDateTime.parse("2026-09-01T00:00:00+08:00");
        LocalDateTime localStart = start.toLocalDateTime();
        LocalDateTime localEnd = end.toLocalDateTime();
        when(scopes.resolveCurrentScope()).thenReturn(scope);
        when(mapper.countPendingReservations(7L, scope)).thenReturn(2L);
        when(mapper.countOpenUsage(7L, scope)).thenReturn(3L);
        when(mapper.countOpenRepairs(7L, scope)).thenReturn(4L);
        when(mapper.countPendingInspections(7L, scope)).thenReturn(5L);
        when(mapper.countOpenHazards(7L, scope)).thenReturn(6L);
        when(mapper.countUnreadNotifications(7L)).thenReturn(7L);
        when(mapper.countDeviceStates(scope)).thenReturn(List.of(
                new LabMetricVo("AVAILABLE", 8L), new LabMetricVo("FAULT", 1L)));
        when(mapper.countReservationStates(7L, scope, localStart, localEnd)).thenReturn(List.of(
                new LabMetricVo("COMPLETED", 9L), new LabMetricVo("CANCELLED", 2L)));
        when(mapper.sumUsageMinutes(7L, scope, localStart, localEnd)).thenReturn(600L);
        when(mapper.countRepairStates(7L, scope, localStart, localEnd)).thenReturn(List.of(
                new LabMetricVo("CLOSED", 3L), new LabMetricVo("IN_PROGRESS", 1L)));
        when(mapper.countHazardStates(7L, scope, localStart, localEnd)).thenReturn(List.of(
                new LabMetricVo("CLOSED", 1L), new LabMetricVo("RECTIFYING", 3L)));
        DashboardQueryDto query = new DashboardQueryDto();
        query.setStartTime(start);
        query.setEndTime(end);

        DashboardSnapshotVo result = new DashboardServiceImpl(mapper, scopes, CLOCK)
                .snapshot(7L, query);

        assertThat(result.pendingReservations()).isEqualTo(2L);
        assertThat(result.openUsage()).isEqualTo(3L);
        assertThat(result.openRepairs()).isEqualTo(4L);
        assertThat(result.pendingInspections()).isEqualTo(5L);
        assertThat(result.openHazards()).isEqualTo(6L);
        assertThat(result.unreadNotifications()).isEqualTo(7L);
        assertThat(result.deviceStatusCounts()).extracting(LabMetricVo::getCode)
                .containsExactly("AVAILABLE", "FAULT");
        assertThat(result.totalDevices()).isEqualTo(9L);
        assertThat(result.reservationStatusCounts()).extracting(LabMetricVo::getValue)
                .containsExactly(9L, 2L);
        assertThat(result.totalReservations()).isEqualTo(11L);
        assertThat(result.usageMinutes()).isEqualTo(600L);
        assertThat(result.repairStatusCounts()).hasSize(2);
        assertThat(result.totalRepairs()).isEqualTo(4L);
        assertThat(result.hazardStatusCounts()).hasSize(2);
        assertThat(result.totalHazards()).isEqualTo(4L);
        assertThat(result.closedHazards()).isEqualTo(1L);
        assertThat(result.hazardClosureRate()).isEqualByComparingTo(new BigDecimal("25.00"));
        assertThat(result.windowStart()).isEqualTo(start);
        assertThat(result.windowEnd()).isEqualTo(end);
        verify(mapper).countUnreadNotifications(7L);
    }
}
