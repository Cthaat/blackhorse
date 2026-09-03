package com.ruoyi.lab.vo;

import java.time.LocalDateTime;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.ruoyi.lab.domain.LabQualification;
import com.ruoyi.lab.domain.QualificationComputedStatus;
import com.ruoyi.lab.domain.QualificationScopeType;

/** Current read representation of a qualification. */
public class QualificationVo
{
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long userId;
    private QualificationScopeType scopeType;
    private String scopeId;
    private LocalDateTime validFrom;
    private LocalDateTime validUntil;
    private LocalDateTime revokedAt;
    private String revokeReason;
    private QualificationComputedStatus status;
    private Integer version;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    public static QualificationVo from(LabQualification source, QualificationComputedStatus status)
    {
        QualificationVo target = new QualificationVo();
        target.id = source.getId();
        target.userId = source.getUserId();
        target.scopeType = source.getScopeType();
        target.scopeId = source.getScopeId();
        target.validFrom = source.getValidFrom();
        target.validUntil = source.getValidUntil();
        target.revokedAt = source.getRevokedAt();
        target.revokeReason = source.getRevokeReason();
        target.status = status;
        target.version = source.getVersion();
        target.createTime = source.getCreateTime();
        target.updateTime = source.getUpdateTime();
        return target;
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public QualificationScopeType getScopeType() { return scopeType; }
    public String getScopeId() { return scopeId; }
    public LocalDateTime getValidFrom() { return validFrom; }
    public LocalDateTime getValidUntil() { return validUntil; }
    public LocalDateTime getRevokedAt() { return revokedAt; }
    public String getRevokeReason() { return revokeReason; }
    public QualificationComputedStatus getStatus() { return status; }
    public Integer getVersion() { return version; }
    public LocalDateTime getCreateTime() { return createTime; }
    public LocalDateTime getUpdateTime() { return updateTime; }
}
