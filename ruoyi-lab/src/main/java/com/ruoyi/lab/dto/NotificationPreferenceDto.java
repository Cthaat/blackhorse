package com.ruoyi.lab.dto;

import jakarta.validation.constraints.NotNull;

public record NotificationPreferenceDto(@NotNull Boolean optionalReminders) { }
