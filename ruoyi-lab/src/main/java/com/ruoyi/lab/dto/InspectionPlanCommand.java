package com.ruoyi.lab.dto;

import java.time.LocalTime;
import java.util.List;
import com.ruoyi.lab.domain.InspectionFrequencyType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record InspectionPlanCommand(
        @NotBlank @Size(max = 100) String planName,
        @NotNull @Positive Long laboratoryId,
        @NotNull InspectionFrequencyType frequencyType,
        @Min(1) @Max(31) int intervalValue,
        @NotNull LocalTime executeTime,
        @Min(1) @Max(7) Integer dayOfWeek,
        @Min(1) @Max(31) Integer dayOfMonth,
        @NotNull @Positive Long ownerId,
        @NotBlank @Size(max = 24) String deadlineRule,
        @Min(1) @Max(43200) int deadlineOffsetMinutes,
        @Valid @Size(max = 100) List<InspectionPlanItemCommand> items)
{
}
