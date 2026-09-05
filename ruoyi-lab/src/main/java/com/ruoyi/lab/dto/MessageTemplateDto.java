package com.ruoyi.lab.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record MessageTemplateDto(@NotBlank @Pattern(regexp="[A-Z0-9_]{1,32}") String eventType,
        @NotBlank @Size(max=128) String title, @NotBlank @Size(max=500) String content) { }
