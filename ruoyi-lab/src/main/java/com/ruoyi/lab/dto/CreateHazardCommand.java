package com.ruoyi.lab.dto;

import java.time.LocalDateTime;
import com.ruoyi.lab.domain.HazardSeverity;
import com.ruoyi.lab.domain.HazardTargetType;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CreateHazardCommand(
        @NotNull HazardTargetType targetType,
        @NotNull @Positive Long targetId,
        @NotNull HazardSeverity severity,
        @NotNull @Positive Long ownerId,
        @NotNull @Future LocalDateTime deadline,
        @NotBlank @Size(max = 2000) String requirements,
        @Positive Long relatedHazardId)
{
}
