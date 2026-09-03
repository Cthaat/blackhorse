package com.ruoyi.lab.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record ReportFaultCommand(@NotNull @Positive Long deviceId,
        @NotBlank @Size(max = 1000) String description)
{
}
