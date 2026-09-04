package com.ruoyi.lab.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record InspectionPlanItemCommand(
        @NotBlank @Size(max = 32) String itemCode,
        @NotBlank @Size(max = 500) String content,
        @PositiveOrZero int sortOrder,
        @NotNull Boolean enabled)
{
}
