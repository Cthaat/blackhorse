package com.ruoyi.lab.domain;

import java.time.LocalDateTime;
import com.baomidou.mybatisplus.annotation.*;

@TableName("lab_reservation_rule")
public class LabReservationRule
{
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long deviceId;
    private Integer versionNumber;
    private Integer revision;
    private String status;
    private String definitionJson;
    private Long createBy;
    private LocalDateTime createTime;
    private Long publishedBy;
    private LocalDateTime publishedAt;

    public Long getId() { return id; }
    public void setId(Long value) { this.id = value; }
    public Long getDeviceId() { return deviceId; }
    public void setDeviceId(Long value) { this.deviceId = value; }
    public Integer getVersionNumber() { return versionNumber; }
    public void setVersionNumber(Integer value) { this.versionNumber = value; }
    public Integer getRevision() { return revision; }
    public void setRevision(Integer value) { this.revision = value; }
    public String getStatus() { return status; }
    public void setStatus(String value) { this.status = value; }
    public String getDefinitionJson() { return definitionJson; }
    public void setDefinitionJson(String value) { this.definitionJson = value; }
    public Long getCreateBy() { return createBy; }
    public void setCreateBy(Long value) { this.createBy = value; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime value) { this.createTime = value; }
    public Long getPublishedBy() { return publishedBy; }
    public void setPublishedBy(Long value) { this.publishedBy = value; }
    public LocalDateTime getPublishedAt() { return publishedAt; }
    public void setPublishedAt(LocalDateTime value) { this.publishedAt = value; }
}
