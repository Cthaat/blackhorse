package com.ruoyi.lab.service;

import java.time.*;
import java.util.ArrayList;
import java.util.List;
import com.ruoyi.lab.dto.ReservationApplyDto;
import com.ruoyi.lab.dto.ReservationRuleDefinition;
import com.ruoyi.lab.exception.LabBusinessException;
import com.ruoyi.lab.exception.LabErrorCode;
import com.ruoyi.lab.mapper.LabReservationMapper;
import com.ruoyi.lab.mapper.LabReservationRuleMapper;
import com.ruoyi.lab.mapper.LabReservationWaitlistMapper;
import com.ruoyi.lab.security.LabObjectPermissionService;
import com.ruoyi.lab.serializer.LabBusinessId;
import com.ruoyi.lab.serializer.LabBusinessTime;
import com.ruoyi.lab.vo.OccupiedRangeVo;
import com.ruoyi.lab.vo.ReservationRuleVo;
import org.springframework.stereotype.Service;

/** Bounded read models; a simulation never grants qualification or takes a reservation slot. */
@Service
public class ReservationRuleQueryService
{
    private final ReservationRuleService rules;
    private final ReservationPolicy policy;
    private final LabReservationMapper reservations;
    private final LabReservationRuleMapper ruleMapper;
    private final LabObjectPermissionService permissions;
    private final Clock clock;
    private final LabReservationWaitlistMapper waitlist;
    private final com.ruoyi.lab.mapper.LabMaintenanceMapper maintenance;

    public ReservationRuleQueryService(ReservationRuleService rules, ReservationPolicy policy,
            LabReservationMapper reservations, LabReservationRuleMapper ruleMapper,
            LabObjectPermissionService permissions, Clock clock, LabReservationWaitlistMapper waitlist,
            com.ruoyi.lab.mapper.LabMaintenanceMapper maintenance)
    {
        this.rules = rules;
        this.policy = policy;
        this.reservations = reservations;
        this.ruleMapper = ruleMapper;
        this.permissions = permissions;
        this.clock = clock;
        this.waitlist = waitlist;
        this.maintenance = maintenance;
    }

    public CalendarView calendar(Long deviceId, LocalDate from, LocalDate to)
    {
        permissions.assertDeviceReadable(deviceId);
        if (from == null || to == null || to.isBefore(from) || from.plusDays(30).isBefore(to))
        {
            throw new LabBusinessException(LabErrorCode.VALIDATION_ERROR, "日历范围必须为一至三十一天");
        }
        ReservationRuleVo rule = rules.active(deviceId);
        List<CalendarDay> days = new ArrayList<>();
        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1))
        {
            boolean open = true;
            String reason = null;
            if (rule != null)
            {
                ReservationRuleDefinition def = rule.definition();
                open = def.weekdays().contains(date.getDayOfWeek().getValue());
                if (!open) { reason = "非开放星期"; }
                for (var closed : def.closedDays())
                {
                    if (closed.date().equals(date)) { open = false; reason = closed.reason(); break; }
                }
            }
            days.add(new CalendarDay(date, open, rule == null ? "00:00" : rule.definition().opensAt(),
                    rule == null ? "24:00" : rule.definition().closesAt(), reason));
        }
        List<OccupiedRangeVo> occupied = new ArrayList<>(reservations.selectOccupiedRanges(deviceId,
                from.atStartOfDay(), to.plusDays(1).atStartOfDay()));
        occupied.addAll(waitlist.occupied(deviceId, from.atStartOfDay(), to.plusDays(1).atStartOfDay(), LocalDateTime.now(clock)));
        occupied.addAll(maintenance.occupied(deviceId,from.atStartOfDay(),to.plusDays(1).atStartOfDay()));
        occupied.sort(java.util.Comparator.comparing(OccupiedRangeVo::startTime));
        return new CalendarView(rule, rules.globalLimits(), days, occupied);
    }

    public Simulation simulate(ReservationApplyDto request, Long ruleId)
    {
        permissions.assertDeviceReadable(request.getDeviceId());
        ReservationRuleVo rule = ruleId == null ? rules.active(request.getDeviceId()) : rules.readableVersion(ruleId);
        if (rule != null && !rule.deviceId().equals(request.getDeviceId()))
        {
            throw new LabBusinessException(LabErrorCode.VALIDATION_ERROR, "规则与设备不匹配");
        }
        try
        {
            var range = policy.validate(request);
            if (rule != null) { ReservationRuleEvaluator.validate(rule.definition(), range.startTime(), range.endTime(), LocalDateTime.now(clock)); }
            if (reservations.countActiveOverlaps(range.deviceId(), range.startTime(), range.endTime(), null) > 0)
            {
                return new Simulation(false, "LAB_RESERVATION_TIME_CONFLICT", "时段已有预约占用", rule);
            }
            if (waitlist.holds(range.deviceId(), range.startTime(), range.endTime(), LocalDateTime.now(clock), null) > 0)
            {
                return new Simulation(false, "LAB_WAITLIST_HOLD_CONFLICT", "时段处于候补确认占位中", rule);
            }
            return new Simulation(true, "RULE_CHECK_PASSED", "时段规则检查通过，提交时仍须校验资格、设备和安全状态", rule);
        }
        catch (LabBusinessException rejected)
        {
            return new Simulation(false, rejected.getErrorCode().name(), rejected.getMessage(), rule);
        }
    }

    public List<Impact> impact(Long ruleId)
    {
        ReservationRuleVo rule = rules.readableVersion(ruleId);
        LocalDateTime now = LocalDateTime.now(clock);
        return LabPage.query(() -> ruleMapper.futureReservations(rule.deviceId(), now), reservation -> {
            String reason = null;
            try { ReservationRuleEvaluator.validate(rule.definition(), reservation.getStartTime(), reservation.getEndTime(), now); }
            catch (LabBusinessException rejected) { reason = rejected.getMessage(); }
            return new Impact(reservation.getId(), reservation.getReservationNo(), reservation.getStartTime(),
                    reservation.getEndTime(), reason != null, reason);
        });
    }

    public record CalendarDay(LocalDate date, boolean open, String opensAt, String closesAt, String closedReason) { }
    public record CalendarView(ReservationRuleVo rule, ReservationRuleService.GlobalLimits global,
            List<CalendarDay> days, List<OccupiedRangeVo> occupied) { }
    public record Simulation(boolean allowed, String code, String message, ReservationRuleVo rule) { }
    public record Impact(@LabBusinessId Long reservationId, String reservationNo,
            @LabBusinessTime LocalDateTime startTime, @LabBusinessTime LocalDateTime endTime,
            boolean affected, String reason) { }
}
