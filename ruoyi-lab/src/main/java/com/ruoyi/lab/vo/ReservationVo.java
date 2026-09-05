package com.ruoyi.lab.vo;

import java.time.LocalDateTime;
import com.ruoyi.lab.domain.LabReservation;
import com.ruoyi.lab.domain.ReservationStatus;
import com.ruoyi.lab.serializer.LabBusinessId;
import com.ruoyi.lab.serializer.LabBusinessTime;

/** Reservation API representation. */
public record ReservationVo(@LabBusinessId Long id, String reservationNo,
        @LabBusinessId Long deviceId, @LabBusinessId Long applicantId,
        @LabBusinessTime LocalDateTime startTime, @LabBusinessTime LocalDateTime endTime,
        String purpose, String remark, ReservationStatus status,
        @LabBusinessId Long approvalBy, @LabBusinessTime LocalDateTime approvalTime,
        String approvalReason, @LabBusinessTime LocalDateTime cancelTime, String cancelReason,
        Integer version, @LabBusinessTime LocalDateTime createTime, @LabBusinessId Long submitterId)
{
    public static ReservationVo from(LabReservation reservation)
    {
        return new ReservationVo(reservation.getId(), reservation.getReservationNo(),
                reservation.getDeviceId(), reservation.getApplicantId(), reservation.getStartTime(),
                reservation.getEndTime(), reservation.getPurpose(), reservation.getRemark(),
                reservation.getStatus(), reservation.getApprovalBy(), reservation.getApprovalTime(),
                reservation.getApprovalReason(), reservation.getCancelTime(),
                reservation.getCancelReason(), reservation.getVersion(), reservation.getCreateTime(),
                reservation.getSubmitterId());
    }
}
