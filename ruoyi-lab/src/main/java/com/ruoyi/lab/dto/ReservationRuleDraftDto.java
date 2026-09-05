package com.ruoyi.lab.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

public record ReservationRuleDraftDto(@NotNull @Positive Long deviceId,
        @Min(0) Integer expectedVersion, @NotNull @Valid ReservationRuleDefinition definition) { }
