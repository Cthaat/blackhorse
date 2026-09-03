package com.ruoyi.lab.domain;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.LocalTime;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName(value = "lab_inspection_plan", autoResultMap = true)
public class LabInspectionPlan implements Serializable
{
    private static final long serialVersionUID = 1L;
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    private String planName;
    private Long laboratoryId;
    private InspectionFrequencyType frequencyType;
    private Integer intervalValue;
    private LocalTime executeTime;
    private Integer dayOfWeek;
    private Integer dayOfMonth;
    private LocalDateTime nextRunAt;
    private Long ownerId;
    private String deadlineRule;
    private Integer deadlineOffsetMinutes;
    private InspectionPlanStatus status;
    private Integer version;
    private String createBy;
    private LocalDateTime createTime;
    private String updateBy;
    private LocalDateTime updateTime;
    @TableLogic(value = "0", delval = "2")
    private String delFlag;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getPlanName() { return planName; }
    public void setPlanName(String planName) { this.planName = planName; }
    public Long getLaboratoryId() { return laboratoryId; }
    public void setLaboratoryId(Long laboratoryId) { this.laboratoryId = laboratoryId; }
    public InspectionFrequencyType getFrequencyType() { return frequencyType; }
    public void setFrequencyType(InspectionFrequencyType frequencyType) { this.frequencyType = frequencyType; }
    public Integer getIntervalValue() { return intervalValue; }
    public void setIntervalValue(Integer intervalValue) { this.intervalValue = intervalValue; }
    public LocalTime getExecuteTime() { return executeTime; }
    public void setExecuteTime(LocalTime executeTime) { this.executeTime = executeTime; }
    public Integer getDayOfWeek() { return dayOfWeek; }
    public void setDayOfWeek(Integer dayOfWeek) { this.dayOfWeek = dayOfWeek; }
    public Integer getDayOfMonth() { return dayOfMonth; }
    public void setDayOfMonth(Integer dayOfMonth) { this.dayOfMonth = dayOfMonth; }
    public LocalDateTime getNextRunAt() { return nextRunAt; }
    public void setNextRunAt(LocalDateTime nextRunAt) { this.nextRunAt = nextRunAt; }
    public Long getOwnerId() { return ownerId; }
    public void setOwnerId(Long ownerId) { this.ownerId = ownerId; }
    public String getDeadlineRule() { return deadlineRule; }
    public void setDeadlineRule(String deadlineRule) { this.deadlineRule = deadlineRule; }
    public Integer getDeadlineOffsetMinutes() { return deadlineOffsetMinutes; }
    public void setDeadlineOffsetMinutes(Integer deadlineOffsetMinutes) { this.deadlineOffsetMinutes = deadlineOffsetMinutes; }
    public InspectionPlanStatus getStatus() { return status; }
    public void setStatus(InspectionPlanStatus status) { this.status = status; }
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
