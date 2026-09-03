package com.ruoyi.lab.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record AssignRepairCommand(@NotNull @Positive Long assigneeId)
{
}
