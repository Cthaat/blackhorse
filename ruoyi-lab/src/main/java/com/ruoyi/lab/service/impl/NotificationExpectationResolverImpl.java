package com.ruoyi.lab.service.impl;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.LongFunction;
import com.ruoyi.lab.domain.HazardTargetType;
import com.ruoyi.lab.domain.LabDevice;
import com.ruoyi.lab.domain.LabHazard;
import com.ruoyi.lab.domain.LabInspectionTask;
import com.ruoyi.lab.domain.LabRepairOrder;
import com.ruoyi.lab.domain.LabReservation;
import com.ruoyi.lab.domain.LabStatusHistory;
import com.ruoyi.lab.dto.NotificationCommand;
import com.ruoyi.lab.event.NotificationDedupeKey;
import com.ruoyi.lab.mapper.LabDeviceMapper;
import com.ruoyi.lab.mapper.LabHazardMapper;
import com.ruoyi.lab.mapper.LabInspectionTaskMapper;
import com.ruoyi.lab.mapper.LabNotificationRecipientMapper;
import com.ruoyi.lab.mapper.LabRepairOrderMapper;
import com.ruoyi.lab.mapper.LabReservationMapper;
import com.ruoyi.lab.mapper.LabStatusHistoryMapper;
import com.ruoyi.lab.service.NotificationExpectationResolver;
import org.springframework.stereotype.Service;

/** Deterministic notification projection based only on persisted business facts. */
@Service
public class NotificationExpectationResolverImpl implements NotificationExpectationResolver
{
    private static final String LAB_MANAGER = "lab_manager";
    private static final String SAFETY_OFFICER = "lab_safety_officer";

    private final LabStatusHistoryMapper historyMapper;
    private final LabReservationMapper reservationMapper;
    private final LabRepairOrderMapper repairMapper;
    private final LabInspectionTaskMapper inspectionTaskMapper;
    private final LabHazardMapper hazardMapper;
    private final LabDeviceMapper deviceMapper;
    private final LabNotificationRecipientMapper recipientMapper;
    private final com.ruoyi.lab.mapper.LabRestrictionMapper restrictionMapper;

    public NotificationExpectationResolverImpl(LabStatusHistoryMapper historyMapper,
            LabReservationMapper reservationMapper, LabRepairOrderMapper repairMapper,
            LabInspectionTaskMapper inspectionTaskMapper, LabHazardMapper hazardMapper,
            LabDeviceMapper deviceMapper, LabNotificationRecipientMapper recipientMapper,
            com.ruoyi.lab.mapper.LabRestrictionMapper restrictionMapper)
    {
        this.historyMapper = historyMapper;
        this.reservationMapper = reservationMapper;
        this.repairMapper = repairMapper;
        this.inspectionTaskMapper = inspectionTaskMapper;
        this.hazardMapper = hazardMapper;
        this.deviceMapper = deviceMapper;
        this.recipientMapper = recipientMapper;
        this.restrictionMapper = restrictionMapper;
    }

    @Override
    public List<NotificationCommand> resolveHistory(long historyId)
    {
        LabStatusHistory history = historyMapper.selectActiveById(historyId);
        if (history == null || blank(history.getObjectType()) || blank(history.getToStatus()))
        {
            return List.of();
        }
        String type = history.getObjectType().trim().toUpperCase(Locale.ROOT);
        String status = history.getToStatus().trim().toUpperCase(Locale.ROOT);
        String notificationType = type + "_" + status;
        Set<Long> receivers = switch (type)
        {
            case "RESERVATION" -> reservationReceivers(history.getObjectId(), status);
            case "REPAIR_ORDER" -> repairReceivers(history.getObjectId(), status);
            case "INSPECTION_TASK" -> inspectionReceivers(history.getObjectId(), status);
            case "HAZARD" -> hazardReceivers(history.getObjectId(), status);
            case "RESTRICTION" -> restrictionReceivers(history.getObjectId(), status);
            default -> Set.of();
        };
        return commands(receivers, receiver -> NotificationDedupeKey.forHistory(
                historyId, notificationType, receiver), notificationType,
                "业务状态已更新", "业务单据状态已更新为 " + status,
                type, history.getObjectId());
    }

    @Override
    public List<NotificationCommand> resolveInspectionOverdue(long taskId,
            long overdueEventVersion)
    {
        LabInspectionTask task = inspectionTaskMapper.selectActiveById(taskId);
        if (!sameVersion(task == null ? null : task.getOverdueEventVersion(),
                overdueEventVersion) || task.getAssigneeId() == null)
        {
            return List.of();
        }
        return commands(Set.of(task.getAssigneeId()), receiver ->
                        NotificationDedupeKey.forOverdue("INSPECTION_TASK", taskId,
                                overdueEventVersion, receiver),
                "INSPECTION_TASK_OVERDUE", "巡检任务已超期", "请尽快处理超期巡检任务",
                "INSPECTION_TASK", taskId);
    }

    @Override
    public List<NotificationCommand> resolveHazardOverdue(long hazardId,
            long overdueEventVersion)
    {
        LabHazard hazard = hazardMapper.selectActiveById(hazardId);
        if (!sameVersion(hazard == null ? null : hazard.getOverdueEventVersion(),
                overdueEventVersion))
        {
            return List.of();
        }
        Set<Long> receivers = new LinkedHashSet<>();
        add(receivers, hazard.getOwnerId());
        recipients(receivers, laboratoryId(hazard), SAFETY_OFFICER);
        return commands(receivers, receiver -> NotificationDedupeKey.forOverdue("HAZARD",
                        hazardId, overdueEventVersion, receiver),
                "HAZARD_OVERDUE", "隐患整改已超期", "请尽快处理超期隐患",
                "HAZARD", hazardId);
    }

    private Set<Long> reservationReceivers(Long id, String status)
    {
        LabReservation reservation = id == null ? null : reservationMapper.selectActiveById(id);
        if (reservation == null) return Set.of();
        Set<Long> receivers = new LinkedHashSet<>();
        if ("PENDING".equals(status))
            recipients(receivers, laboratoryId(reservation.getDeviceId()), LAB_MANAGER);
        else
            add(receivers, reservation.getApplicantId());
        return receivers;
    }

    private Set<Long> restrictionReceivers(Long id, String status)
    {
        var restriction=restrictionMapper.byId(id,java.time.LocalDateTime.now());
        if (restriction==null) return Set.of();
        Set<Long> receivers=new LinkedHashSet<>();
        if ("APPEAL_PENDING".equals(status))
        {
            recipients(receivers,restriction.laboratoryId,LAB_MANAGER);
            receivers.remove(restriction.userId);
        }
        else add(receivers,restriction.userId);
        return receivers;
    }

    private Set<Long> repairReceivers(Long id, String status)
    {
        LabRepairOrder order = id == null ? null : repairMapper.selectActiveById(id);
        if (order == null) return Set.of();
        Set<Long> receivers = new LinkedHashSet<>();
        switch (status)
        {
            case "WAIT_ASSIGN", "WAIT_ACCEPTANCE" ->
                    recipients(receivers, laboratoryId(order.getDeviceId()), LAB_MANAGER);
            case "WAIT_REPAIR", "IN_PROGRESS" -> add(receivers, order.getAssigneeId());
            case "CLOSED" -> {
                add(receivers, order.getReporterId());
                add(receivers, order.getAssigneeId());
            }
            default -> { }
        }
        return receivers;
    }

    private Set<Long> inspectionReceivers(Long id, String status)
    {
        LabInspectionTask task = id == null ? null : inspectionTaskMapper.selectActiveById(id);
        if (task == null) return Set.of();
        Set<Long> receivers = new LinkedHashSet<>();
        if ("PENDING".equals(status)) add(receivers, task.getAssigneeId());
        else if ("COMPLETED".equals(status))
            recipients(receivers, task.getLaboratoryId(), SAFETY_OFFICER);
        return receivers;
    }

    private Set<Long> hazardReceivers(Long id, String status)
    {
        LabHazard hazard = id == null ? null : hazardMapper.selectActiveById(id);
        if (hazard == null) return Set.of();
        Set<Long> receivers = new LinkedHashSet<>();
        if ("PENDING_REVIEW".equals(status))
            recipients(receivers, laboratoryId(hazard), SAFETY_OFFICER);
        else
            add(receivers, hazard.getOwnerId());
        return receivers;
    }

    private Long laboratoryId(Long deviceId)
    {
        LabDevice device = deviceId == null ? null : deviceMapper.selectById(deviceId);
        return device == null ? null : device.getLaboratoryId();
    }

    private Long laboratoryId(LabHazard hazard)
    {
        if (hazard.getTargetType() == HazardTargetType.LABORATORY) return hazard.getTargetId();
        return hazard.getTargetType() == HazardTargetType.DEVICE
                ? laboratoryId(hazard.getTargetId()) : null;
    }

    private void recipients(Set<Long> receivers, Long laboratoryId, String roleKey)
    {
        if (laboratoryId == null) return;
        List<Long> users = recipientMapper.selectScopedRoleUserIds(laboratoryId, roleKey);
        if (users != null) users.forEach(user -> add(receivers, user));
    }

    private static List<NotificationCommand> commands(Set<Long> receivers,
            LongFunction<String> key, String type, String title, String content,
            String businessType, Long businessId)
    {
        if (businessId == null || businessId <= 0) return List.of();
        List<NotificationCommand> result = new ArrayList<>(receivers.size());
        for (Long receiver : receivers)
            result.add(new NotificationCommand(key.apply(receiver), receiver, type, title,
                    content, businessType, businessId));
        return List.copyOf(result);
    }

    private static boolean sameVersion(Long persisted, long requested)
    {
        return persisted != null && requested > 0 && persisted.longValue() == requested;
    }

    private static void add(Set<Long> receivers, Long receiver)
    {
        if (receiver != null && receiver > 0) receivers.add(receiver);
    }

    private static boolean blank(String value)
    {
        return value == null || value.isBlank();
    }
}
