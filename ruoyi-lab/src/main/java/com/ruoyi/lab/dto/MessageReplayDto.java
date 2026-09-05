package com.ruoyi.lab.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record MessageReplayDto(@NotBlank @Size(max=200) String reason) { }
