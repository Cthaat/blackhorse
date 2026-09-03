package com.ruoyi.lab.service.impl;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
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

    public ReservationLifecycleServiceImpl(LabReservationMapper reservationMapper,
            LabDeviceMapper deviceMapper, LabSystemOperatorProvider operatorProvider,
            LabSystemParameterProvider parameterProvider, LabStatusHistoryService historyService)
    {
        this.reservationMapper = reservationMapper;
        this.deviceMapper = deviceMapper;
        this.operatorProvider = operatorProvider;
        this.parameterProvider = parameterProvider;
        this.historyService = historyService;
    }

    @Override
    @Transactional
    public int expirePending(LocalDateTime now, int batchSize)
    {
        LabSystemOperator operator = operatorProvider.requiredOperator();
        validate(now, batchSize);
        return transition(reservationMapper.selectPendingExpiryCandidates(now, batchSize), now,
                ReservationStatus.PENDING, ReservationStatus.EXPIRED, operator,
                "开始时间已到仍未审批");
    }

    @Override
    @Transactional
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
                changed++;
            }
        }
        return changed;
    }

    private static void validate(LocalDateTime now, int batchSize)
    {
        if (now == null || batchSize < 1 || batchSize > 500)
        {
            throw new LabBusinessException(LabErrorCode.VALIDATION_ERROR, "生命周期任务参数无效");
        }
    }
}
