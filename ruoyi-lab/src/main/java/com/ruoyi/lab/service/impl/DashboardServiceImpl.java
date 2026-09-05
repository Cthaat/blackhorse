package com.ruoyi.lab.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.lab.config.LabTimeConfig;
import com.ruoyi.lab.dto.DashboardQueryDto;
import com.ruoyi.lab.exception.LabBusinessException;
import com.ruoyi.lab.exception.LabErrorCode;
import com.ruoyi.lab.mapper.LabDashboardMapper;
import com.ruoyi.lab.security.LabDataScope;
import com.ruoyi.lab.security.LabDataScopeService;
import com.ruoyi.lab.service.DashboardService;
import com.ruoyi.lab.vo.DashboardSnapshotVo;
import com.ruoyi.lab.vo.LabMetricVo;
import org.springframework.stereotype.Service;

/** MySQL aggregate dashboard constrained by the current data scope. */
@Service
public class DashboardServiceImpl implements DashboardService
{
    private static final int DEFAULT_WINDOW_DAYS = 30;
    private static final int MAX_WINDOW_DAYS = 366;

    private final LabDashboardMapper dashboardMapper;
    private final LabDataScopeService dataScopeService;
    private final Clock clock;

    public DashboardServiceImpl(LabDashboardMapper dashboardMapper,
            LabDataScopeService dataScopeService, Clock clock)
    {
        this.dashboardMapper = dashboardMapper;
        this.dataScopeService = dataScopeService;
        this.clock = clock;
    }

    @Override
    public DashboardSnapshotVo snapshot(Long currentUserId, DashboardQueryDto query)
    {
        if (currentUserId == null || currentUserId <= 0)
        {
            throw new LabBusinessException(LabErrorCode.VALIDATION_ERROR, "用户编号无效");
        }
        LabDataScope scope = dataScopeService.resolveCurrentScope();
        if (scope.userId() != currentUserId)
        {
            throw new LabBusinessException(LabErrorCode.LAB_OUT_OF_DATA_SCOPE,
                    "对象不在当前数据范围内");
        }
        Window window = resolveWindow(query);
        LocalDateTime windowStart = window.start().toLocalDateTime();
        LocalDateTime windowEnd = window.end().toLocalDateTime();
        boolean devices = SecurityUtils.hasPermi("lab:device:list");
        boolean reservations = SecurityUtils.hasPermi("lab:reservation:list") || SecurityUtils.hasPermi("lab:reservation:mine");
        boolean usage = SecurityUtils.hasPermi("lab:usage:list");
        boolean repairs = SecurityUtils.hasPermi("lab:repair:list");
        boolean hazards = SecurityUtils.hasPermi("lab:hazard:list");
        boolean inspections = SecurityUtils.hasPermi("lab:inspection:task:list");
        LabDataScope reservationScope = SecurityUtils.hasPermi("lab:reservation:list") ? scope : null;
        List<LabMetricVo> deviceStates = devices ? safe(dashboardMapper.countDeviceStates(scope, LocalDateTime.now(clock))) : List.of();
        List<LabMetricVo> reservationStates = reservations ? safe(dashboardMapper.countReservationStates(
                currentUserId, reservationScope, windowStart, windowEnd)) : List.of();
        List<LabMetricVo> repairStates = repairs ? safe(dashboardMapper.countRepairStates(
                currentUserId, scope, windowStart, windowEnd)) : List.of();
        List<LabMetricVo> hazardStates = hazards ? safe(dashboardMapper.countHazardStates(
                currentUserId, scope, windowStart, windowEnd)) : List.of();
        long totalHazards = total(hazardStates);
        long closedHazards = value(hazardStates, "CLOSED");
        return new DashboardSnapshotVo(
                reservations ? dashboardMapper.countPendingReservations(currentUserId, reservationScope) : 0,
                usage ? dashboardMapper.countOpenUsage(currentUserId, scope) : 0,
                repairs ? dashboardMapper.countOpenRepairs(currentUserId, scope) : 0,
                inspections ? dashboardMapper.countPendingInspections(currentUserId, scope) : 0,
                hazards ? dashboardMapper.countOpenHazards(currentUserId, scope) : 0,
                SecurityUtils.hasPermi("lab:notification:list") ? dashboardMapper.countUnreadNotifications(currentUserId) : 0,
                window.start(), window.end(), deviceStates, total(deviceStates),
                reservationStates, total(reservationStates),
                usage ? dashboardMapper.sumUsageMinutes(currentUserId, scope, windowStart, windowEnd) : 0,
                repairStates, total(repairStates), hazardStates, totalHazards, closedHazards,
                closureRate(closedHazards, totalHazards));
    }

    private Window resolveWindow(DashboardQueryDto query)
    {
        OffsetDateTime start = query == null ? null : query.getStartTime();
        OffsetDateTime end = query == null ? null : query.getEndTime();
        if (start == null && end == null)
        {
            end = OffsetDateTime.ofInstant(clock.instant(), LabTimeConfig.LAB_ZONE);
            start = end.minusDays(DEFAULT_WINDOW_DAYS);
        }
        else if (start == null || end == null)
        {
            throw validation("统计开始和结束时间必须同时填写");
        }
        start = start.atZoneSameInstant(LabTimeConfig.LAB_ZONE).toOffsetDateTime();
        end = end.atZoneSameInstant(LabTimeConfig.LAB_ZONE).toOffsetDateTime();
        Duration duration = Duration.between(start.toInstant(), end.toInstant());
        if (duration.isZero() || duration.isNegative()
                || duration.compareTo(Duration.ofDays(MAX_WINDOW_DAYS)) > 0)
        {
            throw validation("统计时间范围无效或超过366天");
        }
        return new Window(start, end);
    }

    private static List<LabMetricVo> safe(List<LabMetricVo> values)
    {
        return values == null ? List.of() : List.copyOf(values);
    }

    private static long total(List<LabMetricVo> metrics)
    {
        return metrics.stream().mapToLong(LabMetricVo::getValue).sum();
    }

    private static long value(List<LabMetricVo> metrics, String code)
    {
        return metrics.stream().filter(metric -> code.equals(metric.getCode()))
                .mapToLong(LabMetricVo::getValue).sum();
    }

    private static BigDecimal closureRate(long closed, long total)
    {
        if (total == 0)
        {
            return BigDecimal.ZERO.setScale(2);
        }
        return BigDecimal.valueOf(closed).multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);
    }

    private static LabBusinessException validation(String message)
    {
        return new LabBusinessException(LabErrorCode.VALIDATION_ERROR, message);
    }

    private record Window(OffsetDateTime start, OffsetDateTime end)
    {
    }
}
