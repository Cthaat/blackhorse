package com.ruoyi.lab.domain;

import java.io.Serializable;
import java.time.LocalDateTime;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

/** Equipment checkout and return record. */
@TableName(value = "lab_usage_record", autoResultMap = true)
public class LabUsageRecord implements Serializable
{
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    private Long reservationId;
    private Long deviceId;
    private Long userId;
    private Long checkoutOperatorId;
    private LocalDateTime checkedOutAt;
    private String checkoutNote;
    private LocalDateTime returnedAt;
    private Long returnOperatorId;
    private ReturnCondition returnCondition;
    private String returnNote;
    private Long repairOrderId;
    private Integer overdueMinutes;
    private Integer version;
    private String createBy;
    private LocalDateTime createTime;
    private String updateBy;
    private LocalDateTime updateTime;

    @TableLogic(value = "0", delval = "2")
    private String delFlag;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getReservationId() { return reservationId; }
    public void setReservationId(Long reservationId) { this.reservationId = reservationId; }
    public Long getDeviceId() { return deviceId; }
    public void setDeviceId(Long deviceId) { this.deviceId = deviceId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getCheckoutOperatorId() { return checkoutOperatorId; }
    public void setCheckoutOperatorId(Long checkoutOperatorId) { this.checkoutOperatorId = checkoutOperatorId; }
    public LocalDateTime getCheckedOutAt() { return checkedOutAt; }
    public void setCheckedOutAt(LocalDateTime checkedOutAt) { this.checkedOutAt = checkedOutAt; }
    public String getCheckoutNote() { return checkoutNote; }
    public void setCheckoutNote(String checkoutNote) { this.checkoutNote = checkoutNote; }
    public LocalDateTime getReturnedAt() { return returnedAt; }
    public void setReturnedAt(LocalDateTime returnedAt) { this.returnedAt = returnedAt; }
    public Long getReturnOperatorId() { return returnOperatorId; }
    public void setReturnOperatorId(Long returnOperatorId) { this.returnOperatorId = returnOperatorId; }
    public ReturnCondition getReturnCondition() { return returnCondition; }
    public void setReturnCondition(ReturnCondition returnCondition) { this.returnCondition = returnCondition; }
    public String getReturnNote() { return returnNote; }
    public void setReturnNote(String returnNote) { this.returnNote = returnNote; }
    public Long getRepairOrderId() { return repairOrderId; }
    public void setRepairOrderId(Long repairOrderId) { this.repairOrderId = repairOrderId; }
    public Integer getOverdueMinutes() { return overdueMinutes; }
    public void setOverdueMinutes(Integer overdueMinutes) { this.overdueMinutes = overdueMinutes; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
    public String getCreateBy() { return createBy; }
    public void setCreateBy(String createBy) { this.createBy = createBy; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
    public String getUpdateBy() { return updateBy; }
    public void setUpdateBy(String updateBy) { this.updateBy = updateBy; }
    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }
    public String getDelFlag() { return delFlag; }
    public void setDelFlag(String delFlag) { this.delFlag = delFlag; }
}
