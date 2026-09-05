package com.ruoyi.lab.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** Ordinary apply requests cannot choose another owner. */
public class ReservationDelegateDto extends ReservationApplyDto
{
    @NotNull @Positive
    private Long applicantId;
    public Long getApplicantId() { return applicantId; }
    public void setApplicantId(Long applicantId) { this.applicantId = applicantId; }
}
