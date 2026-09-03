package com.ruoyi.lab.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/** Approval or rejection command input. */
public class ReservationDecisionDto
{
    @NotNull
    @PositiveOrZero
    private Integer expectedVersion;

    @NotBlank
    @Size(max = 500)
    private String reason;

    public Integer getExpectedVersion() { return expectedVersion; }
    public void setExpectedVersion(Integer expectedVersion) { this.expectedVersion = expectedVersion; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
