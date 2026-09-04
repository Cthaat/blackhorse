package com.ruoyi.lab.dto;

import java.time.LocalDateTime;
import com.ruoyi.lab.domain.QualificationScopeType;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** Input for creating a qualification. */
public class QualificationCreateDto
{
    @NotNull @Positive
    private Long userId;
    @NotNull
    private QualificationScopeType scopeType;
    @NotBlank
    private String scopeId;
    @NotNull
    private LocalDateTime validFrom;
    @NotNull
    private LocalDateTime validUntil;

    @AssertTrue(message = "scopeId去除首尾空白后长度必须为1到64")
    public boolean isScopeIdValid()
    {
        if (scopeId == null)
        {
            return true;
        }
        int length = scopeId.trim().length();
        return length >= 1 && length <= 64;
    }

    @AssertTrue(message = "validUntil必须晚于validFrom")
    public boolean isValidityRangeValid()
    {
        return validFrom == null || validUntil == null || validUntil.isAfter(validFrom);
    }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public QualificationScopeType getScopeType() { return scopeType; }
    public void setScopeType(QualificationScopeType scopeType) { this.scopeType = scopeType; }
    public String getScopeId() { return scopeId; }
    public void setScopeId(String scopeId) { this.scopeId = scopeId; }
    public LocalDateTime getValidFrom() { return validFrom; }
    public void setValidFrom(LocalDateTime validFrom) { this.validFrom = validFrom; }
    public LocalDateTime getValidUntil() { return validUntil; }
    public void setValidUntil(LocalDateTime validUntil) { this.validUntil = validUntil; }
}
