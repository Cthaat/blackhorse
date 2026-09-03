package com.ruoyi.lab.mapper;

import com.ruoyi.lab.security.LabDataScope;
import org.apache.ibatis.annotations.Param;

/** Database-side dashboard aggregates. */
public interface LabDashboardMapper
{
    long countPendingReservations(@Param("userId") Long userId, @Param("scope") LabDataScope scope);
    long countOpenUsage(@Param("userId") Long userId, @Param("scope") LabDataScope scope);
    long countOpenRepairs(@Param("userId") Long userId, @Param("scope") LabDataScope scope);
    long countPendingInspections(@Param("userId") Long userId, @Param("scope") LabDataScope scope);
    long countOpenHazards(@Param("userId") Long userId, @Param("scope") LabDataScope scope);
    long countUnreadNotifications(@Param("userId") Long userId);
}
