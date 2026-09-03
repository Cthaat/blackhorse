package com.ruoyi.lab.dto;

import java.time.OffsetDateTime;
import com.ruoyi.lab.domain.ReservationStatus;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** Reservation list filters. */
public class ReservationQueryDto
{
    @Size(max = 32)
    private String reservationNo;
    @Positive
    private Long deviceId;
    @Positive
    private Long applicantId;
    private ReservationStatus status;
    private OffsetDateTime from;
    private OffsetDateTime to;
    private String sortBy = "createTime";
    private String sortDirection = "desc";

    public String getReservationNo() { return reservationNo; }
    public void setReservationNo(String reservationNo) { this.reservationNo = reservationNo; }
    public Long getDeviceId() { return deviceId; }
    public void setDeviceId(Long deviceId) { this.deviceId = deviceId; }
    public Long getApplicantId() { return applicantId; }
    public void setApplicantId(Long applicantId) { this.applicantId = applicantId; }
    public ReservationStatus getStatus() { return status; }
    public void setStatus(ReservationStatus status) { this.status = status; }
    public OffsetDateTime getFrom() { return from; }
    public void setFrom(OffsetDateTime from) { this.from = from; }
    public OffsetDateTime getTo() { return to; }
    public void setTo(OffsetDateTime to) { this.to = to; }
    public String getSortBy() { return sortBy; }
    public void setSortBy(String sortBy) { this.sortBy = sortBy; }
    public String getSortDirection() { return sortDirection; }
    public void setSortDirection(String sortDirection) { this.sortDirection = sortDirection; }
}
