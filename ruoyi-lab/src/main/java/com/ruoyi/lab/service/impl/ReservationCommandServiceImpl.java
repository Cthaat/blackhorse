package com.ruoyi.lab.service.impl;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import com.ruoyi.lab.domain.DeviceStatus;
import com.ruoyi.lab.domain.LabDevice;
import com.ruoyi.lab.domain.LabLaboratory;
import com.ruoyi.lab.domain.LabReservation;
import com.ruoyi.lab.domain.LaboratoryStatus;
import com.ruoyi.lab.domain.ReservationStatus;
import com.ruoyi.lab.dto.ReservationApplyDto;
import com.ruoyi.lab.dto.ReservationCancelDto;
import com.ruoyi.lab.dto.ReservationDecisionDto;
import com.ruoyi.lab.exception.LabBusinessException;
import com.ruoyi.lab.exception.LabErrorCode;
import com.ruoyi.lab.mapper.LabDeviceMapper;
import com.ruoyi.lab.mapper.LabLaboratoryMapper;
import com.ruoyi.lab.mapper.LabReservationMapper;
import com.ruoyi.lab.security.LabObjectPermissionService;
import com.ruoyi.lab.service.IdempotencySnapshot;
import com.ruoyi.lab.service.LabHazardBlocker;
import com.ruoyi.lab.service.LabIdempotencyStore;
import com.ruoyi.lab.service.LabQualificationGuard;
import com.ruoyi.lab.service.LabStatusHistoryService;
import com.ruoyi.lab.service.ReservationCommandService;
import com.ruoyi.lab.service.ReservationPolicy;
import com.ruoyi.lab.service.ReservationPolicy.ValidatedReservation;
import com.ruoyi.lab.service.ReservationRequestHasher;
import com.ruoyi.lab.service.ReservationStateMachine;
import com.ruoyi.lab.vo.ReservationVo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Device-first locking implementation of reservation commands. */
@Service
public class ReservationCommandServiceImpl implements ReservationCommandService
{
    private static final Logger LOG = LoggerFactory.getLogger(ReservationCommandServiceImpl.class);
    private static final String OBJECT_TYPE = "RESERVATION";
    private static final Duration IDEMPOTENCY_TTL = Duration.ofHours(24);
    private static final DateTimeFormatter NUMBER_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final LabReservationMapper reservationMapper;
    private final LabDeviceMapper deviceMapper;
    private final LabLaboratoryMapper laboratoryMapper;
    private final LabObjectPermissionService objectPermissionService;
    private final LabQualificationGuard qualificationGuard;
    private final LabHazardBlocker hazardBlocker;
    private final LabStatusHistoryService historyService;
    private final ReservationPolicy policy;
    private final ReservationRequestHasher requestHasher;
    private final ReservationStateMachine stateMachine;
    private final LabIdempotencyStore idempotencyStore;
    private final Clock clock;

    public ReservationCommandServiceImpl(LabReservationMapper reservationMapper,
            LabDeviceMapper deviceMapper, LabLaboratoryMapper laboratoryMapper,
            LabObjectPermissionService objectPermissionService,
            LabQualificationGuard qualificationGuard, LabHazardBlocker hazardBlocker,
            LabStatusHistoryService historyService, ReservationPolicy policy,
            ReservationRequestHasher requestHasher, ReservationStateMachine stateMachine,
            LabIdempotencyStore idempotencyStore, Clock clock)
    {
        this.reservationMapper = reservationMapper;
        this.deviceMapper = deviceMapper;
        this.laboratoryMapper = laboratoryMapper;
        this.objectPermissionService = objectPermissionService;
        this.qualificationGuard = qualificationGuard;
        this.hazardBlocker = hazardBlocker;
        this.historyService = historyService;
        this.policy = policy;
        this.requestHasher = requestHasher;
        this.stateMachine = stateMachine;
        this.idempotencyStore = idempotencyStore;
        this.clock = clock;
    }

    @Override
    @Transactional
    public ReservationVo apply(long applicantId, String idempotencyKey, ReservationApplyDto request)
    {
        requirePositive(applicantId, "用户编号无效");
        String key = requireIdempotencyKey(idempotencyKey);
        ValidatedReservation validated = policy.validate(request);
        String requestHash = requestHasher.hash(validated);
        LocalDateTime now = LocalDateTime.now(clock);

        Optional<IdempotencySnapshot> cacheHint = safeCacheGet(applicantId, key);
        if (cacheHint.isPresent() && Objects.equals(cacheHint.get().requestHash(), requestHash))
        {
            LabReservation cached = reservationMapper.selectActiveById(cacheHint.get().reservationId());
            if (isActiveIdempotency(cached, applicantId, key, requestHash, now))
            {
                return ReservationVo.from(cached);
            }
        }

        LabReservation existing = reservationMapper.selectByApplicantAndIdempotencyKey(applicantId, key);
        if (isUnexpired(existing, now))
        {
            return replayOrConflict(existing, requestHash);
        }

        LabDevice device = deviceMapper.selectByIdForUpdate(validated.deviceId());
        if (device == null)
        {
            throw new LabBusinessException(LabErrorCode.RESOURCE_NOT_FOUND, "设备不存在");
        }
        if (existing != null && !isUnexpired(existing, now))
        {
            reservationMapper.clearExpiredIdempotency(existing.getId(), applicantId, key, now);
        }

        LabReservation current = reservationMapper.selectByApplicantAndIdempotencyKeyForUpdate(applicantId, key);
        if (current != null)
        {
            return replayOrConflict(current, requestHash);
        }

        assertReservable(device, applicantId, validated.startTime());
        if (reservationMapper.countActiveOverlaps(device.getId(), validated.startTime(),
                validated.endTime(), null) > 0)
        {
            throw new LabBusinessException(LabErrorCode.LAB_RESERVATION_TIME_CONFLICT,
                    "预约时间与已有记录冲突");
        }

        LabReservation reservation = newReservation(applicantId, key, requestHash, validated, now);
        try
        {
            reservationMapper.insert(reservation);
        }
        catch (DuplicateKeyException exception)
        {
            LabReservation winner = reservationMapper.selectByApplicantAndIdempotencyKeyForUpdate(applicantId, key);
            if (winner != null)
            {
                return replayOrConflict(winner, requestHash);
            }
            throw duplicateOperation();
        }
        historyService.append(OBJECT_TYPE, reservation.getId(), null,
                ReservationStatus.PENDING.name(), applicantId, "提交预约申请");
        registerCachePut(applicantId, key, reservation.getId(), requestHash);
        return ReservationVo.from(reservation);
    }

    @Override
    @Transactional
    public ReservationVo approve(Long reservationId, ReservationDecisionDto command,
            Long approverId, String username)
    {
        LockedReservation locked = lockReservationDeviceFirst(reservationId);
        objectPermissionService.assertDeviceManageable(locked.device().getId());
        requirePositive(approverId, "用户编号无效");
        if (Objects.equals(locked.reservation().getApplicantId(), approverId))
        {
            throw new LabBusinessException(LabErrorCode.ACCESS_DENIED, "不能审批本人预约");
        }
        stateMachine.assertTransition(locked.reservation().getStatus(), ReservationStatus.APPROVED);
        assertExpectedVersion(command == null ? null : command.getExpectedVersion(),
                locked.reservation().getVersion());
        String reason = requireReason(command.getReason());
        assertReservable(locked.device(), locked.reservation().getApplicantId(),
                locked.reservation().getStartTime());
        if (reservationMapper.countActiveOverlaps(locked.device().getId(),
                locked.reservation().getStartTime(), locked.reservation().getEndTime(),
                locked.reservation().getId()) > 0)
        {
            throw new LabBusinessException(LabErrorCode.LAB_RESERVATION_TIME_CONFLICT,
                    "预约时间与已有记录冲突");
        }
        LocalDateTime now = LocalDateTime.now(clock);
        if (reservationMapper.updateDecisionConditionally(locked.reservation().getId(),
                ReservationStatus.PENDING.name(), ReservationStatus.APPROVED.name(),
                locked.reservation().getVersion(), approverId, now, reason) != 1)
        {
            throw duplicateOperation();
        }
        historyService.append(OBJECT_TYPE, locked.reservation().getId(),
                ReservationStatus.PENDING.name(), ReservationStatus.APPROVED.name(), approverId, reason);
        return ReservationVo.from(requireActive(locked.reservation().getId()));
    }

    @Override
    @Transactional
    public ReservationVo reject(Long reservationId, ReservationDecisionDto command,
            Long approverId, String username)
    {
        LockedReservation locked = lockReservationDeviceFirst(reservationId);
        objectPermissionService.assertDeviceManageable(locked.device().getId());
        requirePositive(approverId, "用户编号无效");
        stateMachine.assertTransition(locked.reservation().getStatus(), ReservationStatus.REJECTED);
        assertExpectedVersion(command == null ? null : command.getExpectedVersion(),
                locked.reservation().getVersion());
        String reason = requireReason(command.getReason());
        if (reservationMapper.updateDecisionConditionally(locked.reservation().getId(),
                ReservationStatus.PENDING.name(), ReservationStatus.REJECTED.name(),
                locked.reservation().getVersion(), approverId, LocalDateTime.now(clock), reason) != 1)
        {
            throw duplicateOperation();
        }
        historyService.append(OBJECT_TYPE, locked.reservation().getId(),
                ReservationStatus.PENDING.name(), ReservationStatus.REJECTED.name(), approverId, reason);
        return ReservationVo.from(requireActive(locked.reservation().getId()));
    }

    @Override
    @Transactional
    public ReservationVo cancel(Long reservationId, ReservationCancelDto command,
            Long applicantId, String username)
    {
        LockedReservation locked = lockReservationDeviceFirst(reservationId);
        requirePositive(applicantId, "用户编号无效");
        if (!Objects.equals(locked.reservation().getApplicantId(), applicantId))
        {
            throw new LabBusinessException(LabErrorCode.LAB_OUT_OF_DATA_SCOPE,
                    "对象不在当前数据范围内");
        }
        stateMachine.assertTransition(locked.reservation().getStatus(), ReservationStatus.CANCELLED);
        assertExpectedVersion(command == null ? null : command.getExpectedVersion(),
                locked.reservation().getVersion());
        String reason = optionalReason(command.getReason());
        ReservationStatus previous = locked.reservation().getStatus();
        if (reservationMapper.updateCancellationConditionally(locked.reservation().getId(),
                previous.name(), locked.reservation().getVersion(), LocalDateTime.now(clock), reason) != 1)
        {
            throw duplicateOperation();
        }
        historyService.append(OBJECT_TYPE, locked.reservation().getId(), previous.name(),
                ReservationStatus.CANCELLED.name(), applicantId,
                reason == null ? "申请人取消预约" : reason);
        return ReservationVo.from(requireActive(locked.reservation().getId()));
    }

    private LockedReservation lockReservationDeviceFirst(Long reservationId)
    {
        long id = requirePositive(reservationId, "预约编号无效");
        LabReservation snapshot = requireActive(id);
        LabDevice device = deviceMapper.selectByIdForUpdate(snapshot.getDeviceId());
        if (device == null)
        {
            throw new LabBusinessException(LabErrorCode.RESOURCE_NOT_FOUND, "设备不存在");
        }
        LabReservation locked = reservationMapper.selectByIdForUpdate(id);
        if (locked == null)
        {
            throw notFound();
        }
        if (!Objects.equals(snapshot.getDeviceId(), locked.getDeviceId()))
        {
            throw duplicateOperation();
        }
        return new LockedReservation(locked, device);
    }

    private void assertReservable(LabDevice device, long applicantId, LocalDateTime qualifiedAt)
    {
        if (device.getStatus() != DeviceStatus.AVAILABLE)
        {
            throw new LabBusinessException(LabErrorCode.LAB_DEVICE_UNAVAILABLE, "设备当前不可预约");
        }
        LabLaboratory laboratory = laboratoryMapper.selectByIdForUpdate(device.getLaboratoryId());
        if (laboratory == null)
        {
            throw new LabBusinessException(LabErrorCode.RESOURCE_NOT_FOUND, "实验室不存在");
        }
        if (laboratory.getStatus() != LaboratoryStatus.ENABLED)
        {
            throw new LabBusinessException(LabErrorCode.LAB_LABORATORY_DISABLED, "实验室已停用");
        }
        qualificationGuard.assertQualified(applicantId, device.getId(), qualifiedAt);
        hazardBlocker.assertNoMajorHazard(device.getId());
    }

    private LabReservation newReservation(long applicantId, String key, String requestHash,
            ValidatedReservation input, LocalDateTime now)
    {
        LabReservation reservation = new LabReservation();
        reservation.setReservationNo("LR" + now.format(NUMBER_TIME)
                + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        reservation.setDeviceId(input.deviceId());
        reservation.setApplicantId(applicantId);
        reservation.setStartTime(input.startTime());
        reservation.setEndTime(input.endTime());
        reservation.setPurpose(input.purpose());
        reservation.setRemark(input.remark());
        reservation.setStatus(ReservationStatus.PENDING);
        reservation.setIdempotencyKey(key);
        reservation.setRequestHash(requestHash);
        reservation.setIdempotencyExpiresAt(now.plus(IDEMPOTENCY_TTL));
        reservation.setVersion(0);
        reservation.setCreateBy(Long.toString(applicantId));
        reservation.setCreateTime(now);
        reservation.setUpdateBy(Long.toString(applicantId));
        reservation.setUpdateTime(now);
        reservation.setDelFlag("0");
        return reservation;
    }

    private Optional<IdempotencySnapshot> safeCacheGet(long applicantId, String key)
    {
        try
        {
            return idempotencyStore.get(applicantId, "reservation-apply", key);
        }
        catch (RuntimeException exception)
        {
            LOG.warn("Reservation idempotency cache read failed; continuing with database");
            return Optional.empty();
        }
    }

    private void registerCachePut(long applicantId, String key, long reservationId, String requestHash)
    {
        Runnable action = () -> {
            try
            {
                idempotencyStore.put(applicantId, "reservation-apply", key,
                        new IdempotencySnapshot(reservationId, requestHash), IDEMPOTENCY_TTL);
            }
            catch (RuntimeException exception)
            {
                LOG.warn("Reservation idempotency cache write failed; database remains authoritative");
            }
        };
        if (!TransactionSynchronizationManager.isSynchronizationActive())
        {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization()
        {
            @Override
            public void afterCommit()
            {
                action.run();
            }
        });
    }

    private static boolean isActiveIdempotency(LabReservation reservation, long applicantId,
            String key, String hash, LocalDateTime now)
    {
        return reservation != null && Objects.equals(reservation.getApplicantId(), applicantId)
                && Objects.equals(reservation.getIdempotencyKey(), key)
                && Objects.equals(reservation.getRequestHash(), hash) && isUnexpired(reservation, now);
    }

    private static boolean isUnexpired(LabReservation reservation, LocalDateTime now)
    {
        return reservation != null && reservation.getIdempotencyExpiresAt() != null
                && reservation.getIdempotencyExpiresAt().isAfter(now);
    }

    private static ReservationVo replayOrConflict(LabReservation reservation, String requestHash)
    {
        if (!Objects.equals(reservation.getRequestHash(), requestHash))
        {
            throw new LabBusinessException(LabErrorCode.LAB_DUPLICATE_OPERATION,
                    "幂等键已用于不同预约请求");
        }
        return ReservationVo.from(reservation);
    }

    private LabReservation requireActive(long reservationId)
    {
        LabReservation reservation = reservationMapper.selectActiveById(reservationId);
        if (reservation == null)
        {
            throw notFound();
        }
        return reservation;
    }

    private static String requireIdempotencyKey(String value)
    {
        if (value == null || value.isBlank() || value.length() > 64
                || value.chars().anyMatch(character -> character < 0x21 || character > 0x7e))
        {
            throw new LabBusinessException(LabErrorCode.VALIDATION_ERROR, "幂等键格式无效");
        }
        return value;
    }

    private static String requireReason(String value)
    {
        String normalized = optionalReason(value);
        if (normalized == null)
        {
            throw new LabBusinessException(LabErrorCode.VALIDATION_ERROR, "审批原因不能为空");
        }
        return normalized;
    }

    private static String optionalReason(String value)
    {
        if (value == null)
        {
            return null;
        }
        String normalized = value.trim();
        if (normalized.isEmpty())
        {
            return null;
        }
        if (normalized.length() > 500)
        {
            throw new LabBusinessException(LabErrorCode.VALIDATION_ERROR, "操作原因长度无效");
        }
        return normalized;
    }

    private static long requirePositive(Long value, String message)
    {
        if (value == null || value <= 0)
        {
            throw new LabBusinessException(LabErrorCode.VALIDATION_ERROR, message);
        }
        return value;
    }

    private static void assertExpectedVersion(Integer requested, Integer current)
    {
        if (requested == null || !Objects.equals(requested, current))
        {
            throw duplicateOperation();
        }
    }

    private static LabBusinessException notFound()
    {
        return new LabBusinessException(LabErrorCode.RESOURCE_NOT_FOUND, "预约不存在");
    }

    private static LabBusinessException duplicateOperation()
    {
        return new LabBusinessException(LabErrorCode.LAB_DUPLICATE_OPERATION, "操作已被其他请求处理");
    }

    private record LockedReservation(LabReservation reservation, LabDevice device)
    {
    }
}
