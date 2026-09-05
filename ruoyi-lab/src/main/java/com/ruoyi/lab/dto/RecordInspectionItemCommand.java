package com.ruoyi.lab.dto;

import com.ruoyi.lab.domain.HazardSeverity;
import com.ruoyi.lab.domain.HazardTargetType;
import com.ruoyi.lab.domain.InspectionResult;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record RecordInspectionItemCommand(
        @NotNull InspectionResult result,
        @Size(max = 1000) String description,
        HazardSeverity severity,
        HazardTargetType targetType,
        @Positive Long targetId,
        @NotNull @PositiveOrZero Integer version)
{
}
