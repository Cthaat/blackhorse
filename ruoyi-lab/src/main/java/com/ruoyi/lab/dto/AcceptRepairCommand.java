package com.ruoyi.lab.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AcceptRepairCommand(boolean passed,
        @NotBlank @Size(max = 1000) String reason)
{
}
