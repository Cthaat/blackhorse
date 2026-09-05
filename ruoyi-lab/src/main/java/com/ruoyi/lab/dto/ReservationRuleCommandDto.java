package com.ruoyi.lab.dto;

import jakarta.validation.constraints.*;

public record ReservationRuleCommandDto(@NotNull @Min(0) Integer expectedVersion) { }
