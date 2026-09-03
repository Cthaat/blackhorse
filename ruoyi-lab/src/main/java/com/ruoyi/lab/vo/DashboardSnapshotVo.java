package com.ruoyi.lab.vo;

/** Role-aware operational counts for the laboratory workbench. */
public record DashboardSnapshotVo(long pendingReservations, long openUsage,
        long openRepairs, long pendingInspections, long openHazards,
        long unreadNotifications)
{
}
