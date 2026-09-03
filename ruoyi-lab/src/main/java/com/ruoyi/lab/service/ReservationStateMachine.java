package com.ruoyi.lab.service;

import com.ruoyi.lab.domain.ReservationStatus;
import com.ruoyi.lab.exception.LabBusinessException;
import com.ruoyi.lab.exception.LabErrorCode;
import org.springframework.stereotype.Component;

/** Single source of truth for reservation lifecycle transitions. */
@Component
public class ReservationStateMachine
{
    public void assertTransition(ReservationStatus current, ReservationStatus target)
    {
        boolean allowed = switch (current)
        {
            case PENDING -> target == ReservationStatus.APPROVED
                    || target == ReservationStatus.REJECTED
                    || target == ReservationStatus.CANCELLED
                    || target == ReservationStatus.EXPIRED;
            case APPROVED -> target == ReservationStatus.CANCELLED
                    || target == ReservationStatus.CHECKED_OUT
                    || target == ReservationStatus.NO_SHOW;
            default -> false;
        };
        if (!allowed)
        {
            throw new LabBusinessException(LabErrorCode.LAB_ILLEGAL_STATE_TRANSITION,
                    "预约状态变更不合法");
        }
    }
}
