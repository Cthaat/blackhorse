package com.ruoyi.lab.service.impl;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Objects;
import com.ruoyi.lab.domain.DeviceStatus;
import com.ruoyi.lab.domain.LabDevice;
import com.ruoyi.lab.domain.LabLaboratory;
import com.ruoyi.lab.domain.LabRepairOrder;
import com.ruoyi.lab.domain.LabReservation;
import com.ruoyi.lab.domain.LabUsageRecord;
import com.ruoyi.lab.domain.LaboratoryStatus;
import com.ruoyi.lab.domain.ReservationStatus;
import com.ruoyi.lab.dto.CheckOutCommand;
import com.ruoyi.lab.dto.ReturnUsageCommand;
import com.ruoyi.lab.exception.LabBusinessException;
import com.ruoyi.lab.exception.LabErrorCode;
import com.ruoyi.lab.mapper.LabDeviceMapper;
import com.ruoyi.lab.mapper.LabLaboratoryMapper;
import com.ruoyi.lab.mapper.LabReservationMapper;
import com.ruoyi.lab.mapper.LabUsageRecordMapper;
import com.ruoyi.lab.security.LabObjectPermissionService;
import com.ruoyi.lab.service.LabHazardBlocker;
import com.ruoyi.lab.service.LabQualificationGuard;
import com.ruoyi.lab.service.LabStatusHistoryService;
import com.ruoyi.lab.service.LabSystemParameterProvider;
import com.ruoyi.lab.service.RepairOrderService;
import com.ruoyi.lab.service.UsageCommandService;
import com.ruoyi.lab.vo.UsageRecordVo;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Device-first transactional checkout and return workflow. */
@Service
public class UsageCommandServiceImpl implements UsageCommandService
{
    private static final int DEFAULT_EARLY_MINUTES = 30;
    private static final String LATE_MINUTES_KEY = "lab.usage.checkout.late-minutes";

    private final LabReservationMapper reservationMapper;
    private final LabDeviceMapper deviceMapper;
    private final LabLaboratoryMapper laboratoryMapper;
    private final LabUsageRecordMapper usageMapper;
    private final LabObjectPermissionService objectPermissionService;
    private final LabQualificationGuard qualificationGuard;
    private final LabHazardBlocker hazardBlocker;
    private final LabStatusHistoryService historyService;
    private final LabSystemParameterProvider parameterProvider;
    private final UsageWindowPolicy windowPolicy;
    private final RepairOrderService repairOrderService;
    private final Clock clock;
    private final com.ruoyi.lab.restriction.RestrictionGuard restrictions;

    public UsageCommandServiceImpl(LabReservationMapper reservationMapper,
            LabDeviceMapper deviceMapper, LabLaboratoryMapper laboratoryMapper,
            LabUsageRecordMapper usageMapper,
            LabObjectPermissionService objectPermissionService,
            LabQualificationGuard qualificationGuard, LabHazardBlocker hazardBlocker,
            LabStatusHistoryService historyService,
            LabSystemParameterProvider parameterProvider, UsageWindowPolicy windowPolicy,
            RepairOrderService repairOrderService, Clock clock,
            com.ruoyi.lab.restriction.RestrictionGuard restrictions)
    {
        this.reservationMapper = reservationMapper;
        this.deviceMapper = deviceMapper;
        this.laboratoryMapper = laboratoryMapper;
        this.usageMapper = usageMapper;
        this.objectPermissionService = objectPermissionService;
        this.qualificationGuard = qualificationGuard;
        this.hazardBlocker = hazardBlocker;
        this.historyService = historyService;
        this.parameterProvider = parameterProvider;
        this.windowPolicy = windowPolicy;
        this.repairOrderService = repairOrderService;
        this.clock = clock;
        this.restrictions = restrictions;
    }

    @Override
    @Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public UsageRecordVo checkOut(CheckOutCommand command, Long operatorId)
    {
        if (command == null)
        {
            throw validation("领用命令不能为空");
        }
        long reservationId = requirePositive(command.reservationId(), "预约编号无效");
        long actorId = requirePositive(operatorId, "操作用户编号无效");
        String note = optionalText(command.note(), 500, "领用备注长度无效");

        LabReservation snapshot = requireReservation(reservationId);
        restrictions.lockDeviceUsers(snapshot.getDeviceId(), snapshot.getApplicantId());
        LabDevice device = requireDeviceForUpdate(snapshot.getDeviceId());
        LabReservation reservation = reservationMapper.selectByIdForUpdate(reservationId);
        if (reservation == null)
        {
            throw notFound("预约不存在");
        }
        if (!Objects.equals(snapshot.getDeviceId(), reservation.getDeviceId())
                || !Objects.equals(device.getId(), reservation.getDeviceId()))
        {
            throw duplicateOperation();
        }
        objectPermissionService.assertDeviceManageable(device.getId());

        LabUsageRecord existing = usageMapper.selectByReservationId(reservationId);
        if (existing != null)
        {
            return UsageRecordVo.from(existing);
        }
        requireStatus(reservation.getStatus(), ReservationStatus.APPROVED,
                "预约当前不能办理领用");
        restrictions.assertAllowed(reservation.getApplicantId(), device.getLaboratoryId());
        requireStatus(device.getStatus(), DeviceStatus.AVAILABLE, "设备当前不可领用");

        LabLaboratory laboratory = laboratoryMapper.selectByIdForUpdate(device.getLaboratoryId());
        if (laboratory == null)
        {
            throw notFound("实验室不存在");
        }
        if (laboratory.getStatus() != LaboratoryStatus.ENABLED)
        {
            throw new LabBusinessException(LabErrorCode.LAB_LABORATORY_DISABLED, "实验室已停用");
        }

        LocalDateTime now = LocalDateTime.now(clock);
        int lateMinutes = parameterProvider.requiredInteger(LATE_MINUTES_KEY, 0, 1440);
        windowPolicy.assertWithinWindow(now, reservation.getStartTime(),
                DEFAULT_EARLY_MINUTES, lateMinutes);
        qualificationGuard.assertQualified(reservation.getApplicantId(), device.getId(), now);
        hazardBlocker.assertNoMajorHazard(device.getId());

        LabUsageRecord usage = newUsage(reservation, actorId, note, now);
        try
        {
            usageMapper.insert(usage);
        }
        catch (DuplicateKeyException exception)
        {
            LabUsageRecord replay = usageMapper.selectByReservationId(reservationId);
            if (replay != null)
            {
                return UsageRecordVo.from(replay);
            }
            throw duplicateOperation();
        }
        requireOne(reservationMapper.updateStatusConditionally(reservationId,
                ReservationStatus.APPROVED.name(), ReservationStatus.CHECKED_OUT.name()));
        requireOne(deviceMapper.updateStatusConditionally(device.getId(),
                DeviceStatus.AVAILABLE.name(), DeviceStatus.IN_USE.name()));
        historyService.append("RESERVATION", reservationId, ReservationStatus.APPROVED.name(),
                ReservationStatus.CHECKED_OUT.name(), actorId, "办理设备领用");
        historyService.append("DEVICE", device.getId(), DeviceStatus.AVAILABLE.name(),
                DeviceStatus.IN_USE.name(), actorId, "预约设备领用");
        return UsageRecordVo.from(usage);
    }

    @Override
    @Transactional
    public UsageRecordVo returnUsage(Long usageId, ReturnUsageCommand command, Long operatorId)
    {
        long id = requirePositive(usageId, "使用记录编号无效");
        long actorId = requirePositive(operatorId, "操作用户编号无效");
        if (command == null || command.condition() == null)
        {
            throw validation("归还命令无效");
        }
        String note = optionalText(command.note(), 500, "归还备注长度无效");
        String faultDescription = command.condition().isAbnormal()
                ? requiredText(command.faultDescription(), 1000, "异常归还必须填写故障描述")
                : null;

        LabUsageRecord snapshot = usageMapper.selectActiveById(id);
        if (snapshot == null)
        {
            throw notFound("使用记录不存在");
        }
        LabDevice device = requireDeviceForUpdate(snapshot.getDeviceId());
        LabReservation reservation = reservationMapper.selectByIdForUpdate(snapshot.getReservationId());
        LabUsageRecord usage = usageMapper.selectOpenUsageForUpdate(id);
        if (reservation == null || usage == null)
        {
            throw illegalState("使用记录已归还或预约不存在");
        }
        if (!Objects.equals(device.getId(), usage.getDeviceId())
                || !Objects.equals(reservation.getId(), usage.getReservationId()))
        {
            throw duplicateOperation();
        }
        objectPermissionService.assertDeviceManageable(device.getId());
        requireStatus(reservation.getStatus(), ReservationStatus.CHECKED_OUT,
                "预约当前不能办理归还");
        if (device.getStatus() != DeviceStatus.IN_USE && device.getStatus() != DeviceStatus.FAULT)
        {
            throw illegalState("设备当前不能办理归还");
        }

        LocalDateTime now = LocalDateTime.now(clock);
        int overdueMinutes = overdueMinutes(reservation.getEndTime(), now);
        requireOne(usageMapper.closeConditionally(id, now, actorId, command.condition(), note,
                overdueMinutes, Long.toString(actorId)));
        requireOne(reservationMapper.updateStatusConditionally(reservation.getId(),
                ReservationStatus.CHECKED_OUT.name(), ReservationStatus.COMPLETED.name()));

        if (command.condition().isAbnormal())
        {
            if (device.getStatus() == DeviceStatus.IN_USE)
            {
                requireOne(deviceMapper.updateStatusConditionally(device.getId(),
                        DeviceStatus.IN_USE.name(), DeviceStatus.FAULT.name()));
                historyService.append("DEVICE", device.getId(), DeviceStatus.IN_USE.name(),
                        DeviceStatus.FAULT.name(), actorId, "设备异常归还");
            }
            LabRepairOrder repair = repairOrderService.openOrGetFromAbnormalReturn(
                    usage, faultDescription, actorId);
            requireOne(usageMapper.linkRepairOrderConditionally(id, repair.getId(),
                    Long.toString(actorId), now));
            usage.setRepairOrderId(repair.getId());
        }
        else
        {
            requireStatus(device.getStatus(), DeviceStatus.IN_USE, "故障设备不能正常归还");
            requireOne(deviceMapper.updateStatusConditionally(device.getId(),
                    DeviceStatus.IN_USE.name(), DeviceStatus.AVAILABLE.name()));
            historyService.append("DEVICE", device.getId(), DeviceStatus.IN_USE.name(),
                    DeviceStatus.AVAILABLE.name(), actorId, "设备正常归还");
        }
        historyService.append("RESERVATION", reservation.getId(),
                ReservationStatus.CHECKED_OUT.name(), ReservationStatus.COMPLETED.name(),
                actorId, "办理设备归还");
        usage.setReturnedAt(now);
        usage.setReturnOperatorId(actorId);
        usage.setReturnCondition(command.condition());
        usage.setReturnNote(note);
        usage.setOverdueMinutes(overdueMinutes);
        return UsageRecordVo.from(usage);
    }

    private static LabUsageRecord newUsage(LabReservation reservation, long actorId,
            String note, LocalDateTime now)
    {
        LabUsageRecord usage = new LabUsageRecord();
        usage.setReservationId(reservation.getId());
        usage.setDeviceId(reservation.getDeviceId());
        usage.setUserId(reservation.getApplicantId());
        usage.setCheckoutOperatorId(actorId);
        usage.setCheckedOutAt(now);
        usage.setCheckoutNote(note);
        usage.setOverdueMinutes(0);
        usage.setVersion(0);
        usage.setCreateBy(Long.toString(actorId));
        usage.setCreateTime(now);
        usage.setUpdateBy(Long.toString(actorId));
        usage.setUpdateTime(now);
        usage.setDelFlag("0");
        return usage;
    }

    private LabReservation requireReservation(long reservationId)
    {
        LabReservation reservation = reservationMapper.selectActiveById(reservationId);
        if (reservation == null)
        {
            throw notFound("预约不存在");
        }
        return reservation;
    }

    private LabDevice requireDeviceForUpdate(Long deviceId)
    {
        LabDevice device = deviceMapper.selectByIdForUpdate(deviceId);
        if (device == null)
        {
            throw notFound("设备不存在");
        }
        return device;
    }

    private static int overdueMinutes(LocalDateTime endTime, LocalDateTime returnedAt)
    {
        if (endTime == null || !returnedAt.isAfter(endTime))
        {
            return 0;
        }
        return (int) Math.min(Integer.MAX_VALUE, Duration.between(endTime, returnedAt).toMinutes());
    }

    private static <T> void requireStatus(T actual, T expected, String message)
    {
        if (actual != expected)
        {
            throw illegalState(message);
        }
    }

    private static void requireOne(int rows)
    {
        if (rows != 1)
        {
            throw duplicateOperation();
        }
    }

    private static long requirePositive(Long value, String message)
    {
        if (value == null || value <= 0)
        {
            throw validation(message);
        }
        return value;
    }

    private static String requiredText(String value, int max, String message)
    {
        String normalized = value == null ? null : value.trim();
        if (normalized == null || normalized.isEmpty() || normalized.length() > max)
        {
            throw validation(message);
        }
        return normalized;
    }

    private static String optionalText(String value, int max, String message)
    {
        if (value == null)
        {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > max)
        {
            throw validation(message);
        }
        return normalized.isEmpty() ? null : normalized;
    }

    private static LabBusinessException validation(String message)
    {
        return new LabBusinessException(LabErrorCode.VALIDATION_ERROR, message);
    }

    private static LabBusinessException notFound(String message)
    {
        return new LabBusinessException(LabErrorCode.RESOURCE_NOT_FOUND, message);
    }

    private static LabBusinessException illegalState(String message)
    {
        return new LabBusinessException(LabErrorCode.LAB_ILLEGAL_STATE_TRANSITION, message);
    }

    private static LabBusinessException duplicateOperation()
    {
        return new LabBusinessException(LabErrorCode.LAB_DUPLICATE_OPERATION,
                "操作已被其他请求处理");
    }
}
