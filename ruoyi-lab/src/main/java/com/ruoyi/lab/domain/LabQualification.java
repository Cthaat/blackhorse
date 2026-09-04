package com.ruoyi.lab.domain;

import java.io.Serializable;
import java.time.LocalDateTime;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * User qualification persisted by {@code lab_qualification}.
 */
@TableName(value = "lab_qualification", autoResultMap = true)
public class LabQualification implements Serializable
{
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long userId;

    private QualificationScopeType scopeType;

    private String scopeId;

    private Long laboratoryId;

    @TableField(exist = false)
    private String laboratoryName;

    private LocalDateTime validFrom;

    private LocalDateTime validUntil;

    private LocalDateTime revokedAt;

    private String revokeReason;

    private Integer version;

    private String createBy;

    private LocalDateTime createTime;

    private String updateBy;

    private LocalDateTime updateTime;

    @TableLogic(value = "0", delval = "2")
    private String delFlag;

    public Long getId()
    {
        return id;
    }

    public void setId(Long id)
    {
        this.id = id;
    }

    public Long getUserId()
    {
        return userId;
    }

    public void setUserId(Long userId)
    {
        this.userId = userId;
    }

    public QualificationScopeType getScopeType()
    {
        return scopeType;
    }

    public void setScopeType(QualificationScopeType scopeType)
    {
        this.scopeType = scopeType;
    }

    public String getScopeId()
    {
        return scopeId;
    }

    public void setScopeId(String scopeId)
    {
        this.scopeId = scopeId;
    }

    public Long getLaboratoryId()
    {
        return laboratoryId;
    }

    public void setLaboratoryId(Long laboratoryId)
    {
        this.laboratoryId = laboratoryId;
    }

    public String getLaboratoryName()
    {
        return laboratoryName;
    }

    public void setLaboratoryName(String laboratoryName)
    {
        this.laboratoryName = laboratoryName;
    }

    public LocalDateTime getValidFrom()
    {
        return validFrom;
    }

    public void setValidFrom(LocalDateTime validFrom)
    {
        this.validFrom = validFrom;
    }

    public LocalDateTime getValidUntil()
    {
        return validUntil;
    }

    public void setValidUntil(LocalDateTime validUntil)
    {
        this.validUntil = validUntil;
    }

    public LocalDateTime getRevokedAt()
    {
        return revokedAt;
    }

    public void setRevokedAt(LocalDateTime revokedAt)
    {
        this.revokedAt = revokedAt;
    }

    public String getRevokeReason()
    {
        return revokeReason;
    }

    public void setRevokeReason(String revokeReason)
    {
        this.revokeReason = revokeReason;
    }

    public Integer getVersion()
    {
        return version;
    }

    public void setVersion(Integer version)
    {
        this.version = version;
    }

    public String getCreateBy()
    {
        return createBy;
    }

    public void setCreateBy(String createBy)
    {
        this.createBy = createBy;
    }

    public LocalDateTime getCreateTime()
    {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime)
    {
        this.createTime = createTime;
    }

    public String getUpdateBy()
    {
        return updateBy;
    }

    public void setUpdateBy(String updateBy)
    {
        this.updateBy = updateBy;
    }

    public LocalDateTime getUpdateTime()
    {
        return updateTime;
    }

    public void setUpdateTime(LocalDateTime updateTime)
    {
        this.updateTime = updateTime;
    }

    public String getDelFlag()
    {
        return delFlag;
    }

    public void setDelFlag(String delFlag)
    {
        this.delFlag = delFlag;
    }
}
