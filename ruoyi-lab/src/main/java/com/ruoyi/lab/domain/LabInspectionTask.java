package com.ruoyi.lab.domain;

import java.io.Serializable;
import java.time.LocalDateTime;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.ruoyi.lab.serializer.LabBusinessId;
import com.ruoyi.lab.serializer.LabBusinessTime;

@TableName(value = "lab_inspection_task", autoResultMap = true)
public class LabInspectionTask implements Serializable
{
    private static final long serialVersionUID = 1L;
    @TableId(value = "id", type = IdType.AUTO)
    @LabBusinessId
    private Long id;
    private String taskNo;
    @LabBusinessId
    private Long planId;
    @LabBusinessId
    private Long laboratoryId;
    @LabBusinessTime
    private LocalDateTime scheduledAt;
    @LabBusinessTime
    private LocalDateTime deadlineAt;
    @LabBusinessId
    private Long assigneeId;
    private InspectionTaskStatus status;
    private String overdueFlag;
    @LabBusinessTime
    private LocalDateTime overdueSetAt;
    private Long overdueEventVersion;
    @LabBusinessTime
    private LocalDateTime startedAt;
    @LabBusinessTime
    private LocalDateTime completedAt;
    private Integer version;
    private String createBy;
    @LabBusinessTime
    private LocalDateTime createTime;
    private String updateBy;
    @LabBusinessTime
    private LocalDateTime updateTime;
    @TableLogic(value = "0", delval = "2")
    private String delFlag;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTaskNo() { return taskNo; }
    public void setTaskNo(String taskNo) { this.taskNo = taskNo; }
    public Long getPlanId() { return planId; }
    public void setPlanId(Long planId) { this.planId = planId; }
    public Long getLaboratoryId() { return laboratoryId; }
    public void setLaboratoryId(Long laboratoryId) { this.laboratoryId = laboratoryId; }
    public LocalDateTime getScheduledAt() { return scheduledAt; }
    public void setScheduledAt(LocalDateTime scheduledAt) { this.scheduledAt = scheduledAt; }
    public LocalDateTime getDeadlineAt() { return deadlineAt; }
    public void setDeadlineAt(LocalDateTime deadlineAt) { this.deadlineAt = deadlineAt; }
    public Long getAssigneeId() { return assigneeId; }
    public void setAssigneeId(Long assigneeId) { this.assigneeId = assigneeId; }
    public InspectionTaskStatus getStatus() { return status; }
    public void setStatus(InspectionTaskStatus status) { this.status = status; }
    public String getOverdueFlag() { return overdueFlag; }
    public void setOverdueFlag(String overdueFlag) { this.overdueFlag = overdueFlag; }
    public LocalDateTime getOverdueSetAt() { return overdueSetAt; }
    public void setOverdueSetAt(LocalDateTime overdueSetAt) { this.overdueSetAt = overdueSetAt; }
    public Long getOverdueEventVersion() { return overdueEventVersion; }
    public void setOverdueEventVersion(Long overdueEventVersion) { this.overdueEventVersion = overdueEventVersion; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }
    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }
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
