package com.ruoyi.lab.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/** Applicant cancellation input. */
public class ReservationCancelDto
{
    @NotNull
    @PositiveOrZero
    private Integer expectedVersion;

    @Size(max = 500)
    private String reason;

    public Integer getExpectedVersion() { return expectedVersion; }
    public void setExpectedVersion(Integer expectedVersion) { this.expectedVersion = expectedVersion; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
