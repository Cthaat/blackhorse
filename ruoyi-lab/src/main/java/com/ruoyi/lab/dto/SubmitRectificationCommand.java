package com.ruoyi.lab.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SubmitRectificationCommand(
        @NotBlank @Size(max = 2000) String description)
{
}
