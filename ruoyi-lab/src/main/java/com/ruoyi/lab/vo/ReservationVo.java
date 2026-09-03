package com.ruoyi.lab.vo;

import java.time.LocalDateTime;
import com.ruoyi.lab.domain.LabReservation;
import com.ruoyi.lab.domain.ReservationStatus;

/** Reservation API representation. */
public record ReservationVo(Long id, String reservationNo, Long deviceId, Long applicantId,
        LocalDateTime startTime, LocalDateTime endTime, String purpose, String remark,
        ReservationStatus status, Long approvalBy, LocalDateTime approvalTime,
        String approvalReason, LocalDateTime cancelTime, String cancelReason,
        Integer version, LocalDateTime createTime)
{
    public static ReservationVo from(LabReservation reservation)
    {
        return new ReservationVo(reservation.getId(), reservation.getReservationNo(),
                reservation.getDeviceId(), reservation.getApplicantId(), reservation.getStartTime(),
                reservation.getEndTime(), reservation.getPurpose(), reservation.getRemark(),
                reservation.getStatus(), reservation.getApprovalBy(), reservation.getApprovalTime(),
                reservation.getApprovalReason(), reservation.getCancelTime(),
                reservation.getCancelReason(), reservation.getVersion(), reservation.getCreateTime());
    }
}
