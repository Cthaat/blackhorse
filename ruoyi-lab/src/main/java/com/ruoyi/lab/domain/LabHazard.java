package com.ruoyi.lab.domain;

import java.io.Serializable;
import java.time.LocalDateTime;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.ruoyi.lab.serializer.LabBusinessId;
import com.ruoyi.lab.serializer.LabBusinessTime;

@TableName(value = "lab_hazard", autoResultMap = true)
public class LabHazard implements Serializable
{
    private static final long serialVersionUID = 1L;
    @TableId(value = "id", type = IdType.AUTO)
    @LabBusinessId
    private Long id;
    private String hazardNo;
    @LabBusinessId
    private Long sourceItemId;
    @LabBusinessId
    private Long relatedHazardId;
    private HazardTargetType targetType;
    @LabBusinessId
    private Long targetId;
    private HazardSeverity severity;
    @LabBusinessId
    private Long ownerId;
    @LabBusinessTime
    private LocalDateTime deadline;
    private String requirements;
    private HazardStatus status;
    private String overdueFlag;
    @LabBusinessTime
    private LocalDateTime overdueSetAt;
    private Long overdueEventVersion;
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
    public String getHazardNo() { return hazardNo; }
    public void setHazardNo(String hazardNo) { this.hazardNo = hazardNo; }
    public Long getSourceItemId() { return sourceItemId; }
    public void setSourceItemId(Long sourceItemId) { this.sourceItemId = sourceItemId; }
    public Long getRelatedHazardId() { return relatedHazardId; }
    public void setRelatedHazardId(Long relatedHazardId) { this.relatedHazardId = relatedHazardId; }
    public HazardTargetType getTargetType() { return targetType; }
    public void setTargetType(HazardTargetType targetType) { this.targetType = targetType; }
    public Long getTargetId() { return targetId; }
    public void setTargetId(Long targetId) { this.targetId = targetId; }
    public HazardSeverity getSeverity() { return severity; }
    public void setSeverity(HazardSeverity severity) { this.severity = severity; }
    public Long getOwnerId() { return ownerId; }
    public void setOwnerId(Long ownerId) { this.ownerId = ownerId; }
    public LocalDateTime getDeadline() { return deadline; }
    public void setDeadline(LocalDateTime deadline) { this.deadline = deadline; }
    public String getRequirements() { return requirements; }
    public void setRequirements(String requirements) { this.requirements = requirements; }
    public HazardStatus getStatus() { return status; }
    public void setStatus(HazardStatus status) { this.status = status; }
    public String getOverdueFlag() { return overdueFlag; }
    public void setOverdueFlag(String overdueFlag) { this.overdueFlag = overdueFlag; }
    public LocalDateTime getOverdueSetAt() { return overdueSetAt; }
    public void setOverdueSetAt(LocalDateTime overdueSetAt) { this.overdueSetAt = overdueSetAt; }
    public Long getOverdueEventVersion() { return overdueEventVersion; }
    public void setOverdueEventVersion(Long overdueEventVersion) { this.overdueEventVersion = overdueEventVersion; }
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
