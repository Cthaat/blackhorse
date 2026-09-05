package com.ruoyi.lab.domain;

import java.time.LocalDateTime;
import com.baomidou.mybatisplus.annotation.*;

@TableName("lab_reservation_waitlist")
public class LabReservationWaitlist
{
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long deviceId;
    private Long applicantId;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String purpose;
    private String remark;
    private String status;
    private LocalDateTime offeredUntil;
    private Long reservationId;
    private Integer version;
    private String reason;
    private String idempotencyKey;
    private String requestHash;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    public Long getId() { return id; }
    public void setId(Long value) { this.id = value; }
    public Long getDeviceId() { return deviceId; }
    public void setDeviceId(Long value) { this.deviceId = value; }
    public Long getApplicantId() { return applicantId; }
    public void setApplicantId(Long value) { this.applicantId = value; }
    public LocalDateTime getStartTime() { return startTime; }
    public void setStartTime(LocalDateTime value) { this.startTime = value; }
    public LocalDateTime getEndTime() { return endTime; }
    public void setEndTime(LocalDateTime value) { this.endTime = value; }
    public String getPurpose() { return purpose; }
    public void setPurpose(String value) { this.purpose = value; }
    public String getRemark() { return remark; }
    public void setRemark(String value) { this.remark = value; }
    public String getStatus() { return status; }
    public void setStatus(String value) { this.status = value; }
    public LocalDateTime getOfferedUntil() { return offeredUntil; }
    public void setOfferedUntil(LocalDateTime value) { this.offeredUntil = value; }
    public Long getReservationId() { return reservationId; }
    public void setReservationId(Long value) { this.reservationId = value; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer value) { this.version = value; }
    public String getReason() { return reason; }
    public void setReason(String value) { this.reason = value; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String value) { this.idempotencyKey = value; }
    public String getRequestHash() { return requestHash; }
    public void setRequestHash(String value) { this.requestHash = value; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime value) { this.createTime = value; }
    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime value) { this.updateTime = value; }
}
