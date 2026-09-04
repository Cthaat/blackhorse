package com.ruoyi.lab.mapper;

import java.time.LocalDateTime;
import java.util.List;
import com.ruoyi.lab.security.LabDataScope;
import com.ruoyi.lab.vo.LabMetricVo;
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
    List<LabMetricVo> countDeviceStates(@Param("scope") LabDataScope scope);
    List<LabMetricVo> countReservationStates(@Param("userId") Long userId,
            @Param("scope") LabDataScope scope,
            @Param("windowStart") LocalDateTime windowStart,
            @Param("windowEnd") LocalDateTime windowEnd);
    long sumUsageMinutes(@Param("userId") Long userId,
            @Param("scope") LabDataScope scope,
            @Param("windowStart") LocalDateTime windowStart,
            @Param("windowEnd") LocalDateTime windowEnd);
    List<LabMetricVo> countRepairStates(@Param("userId") Long userId,
            @Param("scope") LabDataScope scope,
            @Param("windowStart") LocalDateTime windowStart,
            @Param("windowEnd") LocalDateTime windowEnd);
    List<LabMetricVo> countHazardStates(@Param("userId") Long userId,
            @Param("scope") LabDataScope scope,
            @Param("windowStart") LocalDateTime windowStart,
            @Param("windowEnd") LocalDateTime windowEnd);
}
