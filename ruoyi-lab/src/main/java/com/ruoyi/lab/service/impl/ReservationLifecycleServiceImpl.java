package com.ruoyi.lab.service.impl;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import com.ruoyi.lab.config.LabJobProperties;
import com.ruoyi.lab.domain.LabDevice;
import com.ruoyi.lab.domain.LabReservation;
import com.ruoyi.lab.domain.ReservationStatus;
import com.ruoyi.lab.exception.LabBusinessException;
import com.ruoyi.lab.exception.LabErrorCode;
import com.ruoyi.lab.mapper.LabDeviceMapper;
import com.ruoyi.lab.mapper.LabReservationMapper;
import com.ruoyi.lab.service.LabStatusHistoryService;
import com.ruoyi.lab.service.LabSystemOperator;
import com.ruoyi.lab.service.LabSystemOperatorProvider;
import com.ruoyi.lab.service.LabSystemParameterProvider;
import com.ruoyi.lab.service.ReservationLifecycleService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Device-first and re-entrant reservation lifecycle jobs. */
@Service
public class ReservationLifecycleServiceImpl implements ReservationLifecycleService
{
    private static final String OBJECT_TYPE = "RESERVATION";
    private final LabReservationMapper reservationMapper;
    private final LabDeviceMapper deviceMapper;
    private final LabSystemOperatorProvider operatorProvider;
    private final LabSystemParameterProvider parameterProvider;
    private final LabStatusHistoryService historyService;
    private final com.ruoyi.lab.restriction.RestrictionGuard restrictions;
    private final com.ruoyi.lab.restriction.RestrictionService restrictionService;

    public ReservationLifecycleServiceImpl(LabReservationMapper reservationMapper,
            LabDeviceMapper deviceMapper, LabSystemOperatorProvider operatorProvider,
            LabSystemParameterProvider parameterProvider, LabStatusHistoryService historyService,
            com.ruoyi.lab.restriction.RestrictionGuard restrictions,
            com.ruoyi.lab.restriction.RestrictionService restrictionService)
    {
        this.reservationMapper = reservationMapper;
        this.deviceMapper = deviceMapper;
        this.operatorProvider = operatorProvider;
        this.parameterProvider = parameterProvider;
        this.historyService = historyService;
        this.restrictions = restrictions;
        this.restrictionService = restrictionService;
    }

    @Override
    @Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public int expirePending(LocalDateTime now, int batchSize)
    {
        LabSystemOperator operator = operatorProvider.requiredOperator();
        validate(now, batchSize);
        return transition(reservationMapper.selectPendingExpiryCandidates(now, batchSize), now,
                ReservationStatus.PENDING, ReservationStatus.EXPIRED, operator,
                "开始时间已到仍未审批");
    }

    @Override
    @Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public int markNoShow(LocalDateTime now, int batchSize)
    {
        LabSystemOperator operator = operatorProvider.requiredOperator();
        validate(now, batchSize);
        int lateMinutes = parameterProvider.requiredInteger(
                "lab.usage.checkout.late-minutes", 0, 1440);
        return transition(reservationMapper.selectNoShowCandidates(now.minusMinutes(lateMinutes),
                batchSize), now, ReservationStatus.APPROVED, ReservationStatus.NO_SHOW, operator,
                "超过领用宽限期仍未领用");
    }

    private int transition(List<LabReservation> candidates, LocalDateTime now,
            ReservationStatus from, ReservationStatus to, LabSystemOperator operator, String reason)
    {
        int changed = 0;
        restrictions.lockUsers(candidates.stream().map(LabReservation::getApplicantId).distinct().sorted().toList());
        for (LabReservation candidate : candidates)
        {
            LabDevice device = deviceMapper.selectByIdForUpdate(candidate.getDeviceId());
            if (device == null)
            {
                continue;
            }
            LabReservation locked = reservationMapper.selectByIdForUpdate(candidate.getId());
            if (locked == null || locked.getStatus() != from
                    || !Objects.equals(locked.getDeviceId(), device.getId()))
            {
                continue;
            }
            if (reservationMapper.updateLifecycleConditionally(locked.getId(), from.name(), to.name(),
                    locked.getVersion(), operator.userName(), now) == 1)
            {
                historyService.append(OBJECT_TYPE, locked.getId(), from.name(), to.name(),
                        operator.userId(), reason);
                if (to == ReservationStatus.NO_SHOW)
                    restrictionService.recordNoShow(locked, device.getLaboratoryId(), now, operator.userId());
                changed++;
            }
        }
        return changed;
    }

    private static void validate(LocalDateTime now, int batchSize)
    {
        if (now == null || batchSize < LabJobProperties.MIN_BATCH_SIZE
                || batchSize > LabJobProperties.MAX_BATCH_SIZE)
        {
            throw new LabBusinessException(LabErrorCode.VALIDATION_ERROR, "生命周期任务参数无效");
        }
    }
}
