package com.ruoyi.lab.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/** Input for the idempotent qualification revocation command. */
public class QualificationRevokeDto
{
    @NotBlank
    private String reason;
    @NotNull @PositiveOrZero
    private Integer expectedVersion;

    @AssertTrue(message = "reason去除首尾空白后长度必须为1到500")
    public boolean isReasonValid()
    {
        if (reason == null)
        {
            return true;
        }
        int length = reason.trim().length();
        return length >= 1 && length <= 500;
    }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public Integer getExpectedVersion() { return expectedVersion; }
    public void setExpectedVersion(Integer expectedVersion) { this.expectedVersion = expectedVersion; }
}
