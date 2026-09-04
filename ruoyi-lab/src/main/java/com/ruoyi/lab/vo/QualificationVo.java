package com.ruoyi.lab.vo;

import java.time.LocalDateTime;
import com.ruoyi.lab.domain.LabQualification;
import com.ruoyi.lab.domain.QualificationComputedStatus;
import com.ruoyi.lab.domain.QualificationScopeType;
import com.ruoyi.lab.serializer.LabBusinessId;
import com.ruoyi.lab.serializer.LabBusinessTime;

/** Current read representation of a qualification. */
public class QualificationVo
{
    @LabBusinessId
    private Long id;
    @LabBusinessId
    private Long userId;
    private QualificationScopeType scopeType;
    private String scopeId;
    @LabBusinessId
    private Long laboratoryId;
    private String laboratoryName;
    @LabBusinessTime
    private LocalDateTime validFrom;
    @LabBusinessTime
    private LocalDateTime validUntil;
    @LabBusinessTime
    private LocalDateTime revokedAt;
    private String revokeReason;
    private QualificationComputedStatus status;
    private Integer version;
    @LabBusinessTime
    private LocalDateTime createTime;
    @LabBusinessTime
    private LocalDateTime updateTime;

    public static QualificationVo from(LabQualification source, QualificationComputedStatus status)
    {
        QualificationVo target = new QualificationVo();
        target.id = source.getId();
        target.userId = source.getUserId();
        target.scopeType = source.getScopeType();
        target.scopeId = source.getScopeId();
        target.laboratoryId = source.getLaboratoryId();
        target.laboratoryName = source.getLaboratoryName();
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
    public Long getLaboratoryId() { return laboratoryId; }
    public String getLaboratoryName() { return laboratoryName; }
    public LocalDateTime getValidFrom() { return validFrom; }
    public LocalDateTime getValidUntil() { return validUntil; }
    public LocalDateTime getRevokedAt() { return revokedAt; }
    public String getRevokeReason() { return revokeReason; }
    public QualificationComputedStatus getStatus() { return status; }
    public Integer getVersion() { return version; }
    public LocalDateTime getCreateTime() { return createTime; }
    public LocalDateTime getUpdateTime() { return updateTime; }
}
