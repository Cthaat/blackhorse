package com.ruoyi.lab.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CheckOutCommand(@NotNull @Positive Long reservationId,
        @Size(max = 500) String note)
{
}
