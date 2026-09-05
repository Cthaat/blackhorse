package com.ruoyi.lab.service;

import com.ruoyi.lab.dto.ReservationApplyDto;
import com.ruoyi.lab.dto.ReservationDelegateDto;
import com.ruoyi.lab.dto.ReservationCancelDto;
import com.ruoyi.lab.dto.ReservationDecisionDto;
import com.ruoyi.lab.vo.ReservationVo;

/** Reservation creation and lifecycle commands. */
public interface ReservationCommandService
{
    ReservationVo confirmWaitlist(Long waitlistId, int expectedVersion, Long userId);

    ReservationApplyResult apply(long applicantId, String idempotencyKey,
            ReservationApplyDto request);

    ReservationApplyResult delegate(long actorId, String idempotencyKey, ReservationDelegateDto request);

    ReservationVo approve(Long reservationId, ReservationDecisionDto command,
            Long approverId, String username);

    ReservationVo reject(Long reservationId, ReservationDecisionDto command,
            Long approverId, String username);

    ReservationVo cancel(Long reservationId, ReservationCancelDto command,
            Long applicantId, String username);
}
