package com.ruoyi.lab.vo;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/** Role-aware operational counts for the laboratory workbench. */
public record DashboardSnapshotVo(long pendingReservations, long openUsage,
        long openRepairs, long pendingInspections, long openHazards,
        long unreadNotifications, OffsetDateTime windowStart, OffsetDateTime windowEnd,
        List<LabMetricVo> deviceStatusCounts, long totalDevices,
        List<LabMetricVo> reservationStatusCounts, long totalReservations,
        long usageMinutes, List<LabMetricVo> repairStatusCounts, long totalRepairs,
        List<LabMetricVo> hazardStatusCounts,
        long totalHazards, long closedHazards, BigDecimal hazardClosureRate)
{
    public DashboardSnapshotVo
    {
        deviceStatusCounts = copy(deviceStatusCounts);
        reservationStatusCounts = copy(reservationStatusCounts);
        repairStatusCounts = copy(repairStatusCounts);
        hazardStatusCounts = copy(hazardStatusCounts);
        hazardClosureRate = hazardClosureRate == null ? BigDecimal.ZERO : hazardClosureRate;
    }

    private static List<LabMetricVo> copy(List<LabMetricVo> values)
    {
        return values == null ? List.of() : List.copyOf(values);
    }
}
