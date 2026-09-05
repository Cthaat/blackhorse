package com.ruoyi.lab.service;

import java.time.*;
import java.util.List;
import com.ruoyi.lab.domain.*;
import com.ruoyi.lab.dto.NotificationCommand;
import com.ruoyi.lab.dto.ReservationApplyDto;
import com.ruoyi.lab.exception.LabBusinessException;
import com.ruoyi.lab.exception.LabErrorCode;
import com.ruoyi.lab.mapper.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Every queue operation is serialized with normal reservations by the same device row. */
@Service
public class ReservationWaitlistCoordinator
{
    private final LabReservationWaitlistMapper queue;
    private final LabDeviceMapper devices;
    private final LabLaboratoryMapper laboratories;
    private final LabReservationMapper reservations;
    private final ReservationPolicy policy;
    private final ReservationRuleService rules;
    private final LabQualificationGuard qualifications;
    private final LabHazardBlocker hazards;
    private final LabUserDirectory users;
    private final LabNotificationDeliveryService delivery;
    private final Clock clock;
    private final com.ruoyi.lab.restriction.RestrictionGuard restrictions;
    private final com.ruoyi.lab.maintenance.MaintenanceWindowGuard maintenance;

    public ReservationWaitlistCoordinator(LabReservationWaitlistMapper queue, LabDeviceMapper devices,
            LabLaboratoryMapper laboratories, LabReservationMapper reservations, ReservationPolicy policy,
            ReservationRuleService rules, LabQualificationGuard qualifications, LabHazardBlocker hazards,
            LabUserDirectory users, LabNotificationDeliveryService delivery, Clock clock,
            com.ruoyi.lab.restriction.RestrictionGuard restrictions,com.ruoyi.lab.maintenance.MaintenanceWindowGuard maintenance)
    {
        this.queue = queue;
        this.devices = devices;
        this.laboratories = laboratories;
        this.reservations = reservations;
        this.policy = policy;
        this.rules = rules;
        this.qualifications = qualifications;
        this.hazards = hazards;
        this.users = users;
        this.delivery = delivery;
        this.clock = clock;
        this.restrictions = restrictions;
        this.maintenance = maintenance;
    }

    public List<Long> dueDevices() { return queue.dueDevices(); }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void advanceDevice(Long deviceId)
    {
        restrictions.lockDeviceUsers(deviceId, null);
        LabDevice device = devices.selectByIdForUpdate(deviceId);
        if (device != null) { reconcileLocked(device); }
    }

    /** Caller already holds the device lock and a READ_COMMITTED transaction. */
    public void reconcileLocked(LabDevice device)
    {
        LocalDateTime now = LocalDateTime.now(clock);
        List<LabReservationWaitlist> entries = queue.queue(device.getId());
        for (LabReservationWaitlist entry : entries)
        {
            if ("OFFERED".equals(entry.getStatus()) && !entry.getOfferedUntil().isAfter(now))
            {
                change(entry, "EXPIRED", "候补邀请已过期", null, null, now);
                continue;
            }
            try { validateCandidate(device, entry); }
            catch (LabBusinessException rejected)
            {
                if (rejected.getErrorCode() == LabErrorCode.INTERNAL_ERROR) { throw rejected; }
                change(entry, "INELIGIBLE", rejected.getMessage(), null, null, now);
            }
        }
        for (LabReservationWaitlist entry : entries)
        {
            if ("WAITING".equals(entry.getStatus())
                    && reservations.countActiveOverlaps(device.getId(), entry.getStartTime(), entry.getEndTime(), null) == 0
                    && queue.holds(device.getId(), entry.getStartTime(), entry.getEndTime(), now, null) == 0)
            {
                var rule = rules.active(device.getId());
                int lead = Math.max(rules.globalLimits().minLeadMinutes(), rule == null ? 0 : rule.definition().minLeadMinutes());
                int minutes = rule == null ? 15 : rule.definition().invitationMinutes();
                LocalDateTime until = now.plusMinutes(minutes);
                LocalDateTime latest = entry.getStartTime().minusMinutes(lead);
                if (until.isAfter(latest)) { until = latest; }
                if (!until.isAfter(now)) { change(entry, "EXPIRED", "已超过候补确认时间", null, null, now); continue; }
                change(entry, "OFFERED", "时段已空出，请在期限内确认", until, null, now);
            }
            if ("OFFERED".equals(entry.getStatus())) { notifyAfterCommit(entry); }
        }
        queue.touched(device.getId(), now);
    }

    public void validateCandidate(LabDevice device, LabReservationWaitlist entry)
    {
        maintenance.assertAvailable(device.getId(),entry.getStartTime(),entry.getEndTime());
        restrictions.assertAllowed(entry.getApplicantId(), device.getLaboratoryId());
        policy.validate(request(entry));
        rules.validateForApply(device.getId(), entry.getStartTime(), entry.getEndTime());
        users.assertActiveRole(entry.getApplicantId(), "lab_student");
        if (device.getStatus() != DeviceStatus.AVAILABLE)
        {
            throw new LabBusinessException(LabErrorCode.LAB_DEVICE_UNAVAILABLE, "设备当前不可预约");
        }
        LabLaboratory lab = laboratories.selectByIdForUpdate(device.getLaboratoryId());
        if (lab == null || lab.getStatus() != LaboratoryStatus.ENABLED)
        {
            throw new LabBusinessException(LabErrorCode.LAB_LABORATORY_DISABLED, "实验室已停用");
        }
        qualifications.assertQualified(entry.getApplicantId(), device.getId(), entry.getStartTime());
        hazards.assertNoMajorHazard(device.getId());
    }

    public void assertNoHold(Long deviceId, LocalDateTime start, LocalDateTime end, Long excludeId)
    {
        if (queue.holds(deviceId, start, end, LocalDateTime.now(clock), excludeId) > 0)
        {
            throw new LabBusinessException(LabErrorCode.LAB_WAITLIST_HOLD_CONFLICT, "该时段处于候补确认占位中");
        }
    }

    public void change(LabReservationWaitlist entry, String status, String reason,
            LocalDateTime until, Long reservationId, LocalDateTime now)
    {
        if (queue.transition(entry.getId(), entry.getVersion(), status, reason, until, reservationId, now) != 1)
        {
            throw new LabBusinessException(LabErrorCode.LAB_DUPLICATE_OPERATION, "候补状态已变化，请刷新");
        }
        entry.setStatus(status);
        entry.setReason(reason);
        entry.setOfferedUntil(until);
        entry.setReservationId(reservationId);
        entry.setVersion(entry.getVersion() + 1);
    }

    private void notifyAfterCommit(LabReservationWaitlist entry)
    {
        NotificationCommand event = new NotificationCommand("WAITLIST:" + entry.getId() + ":OFFERED",
                entry.getApplicantId(), "WAITLIST_OFFERED", "预约候补确认邀请",
                "您申请的时段已空出，请在" + entry.getOfferedUntil() + "前打开我的预约中的开放日历与候补确认。确认后仍需审批。",
                "WAITLIST", entry.getId());
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { delivery.deliverSafely(event); }
        });
    }

    public static ReservationApplyDto request(LabReservationWaitlist row)
    {
        ReservationApplyDto request = new ReservationApplyDto();
        request.setDeviceId(row.getDeviceId());
        request.setStartTime(row.getStartTime().atOffset(ZoneOffset.ofHours(8)));
        request.setEndTime(row.getEndTime().atOffset(ZoneOffset.ofHours(8)));
        request.setPurpose(row.getPurpose());
        request.setRemark(row.getRemark());
        return request;
    }
}
