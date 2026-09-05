package com.ruoyi.lab.dto;

import java.time.LocalDate;
import java.util.List;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

/** A device rule further narrows the global reservation time boundaries. */
public record ReservationRuleDefinition(
        @NotBlank @Size(max = 80) String name,
        @NotEmpty @Size(max = 7) List<@NotNull @Min(1) @Max(7) Integer> weekdays,
        @NotNull @Pattern(regexp = "(?:[01][0-9]|2[0-3]):[0-5][0-9]") String opensAt,
        @NotNull @Pattern(regexp = "(?:[01][0-9]|2[0-3]):[0-5][0-9]") String closesAt,
        @NotNull @Size(max = 366) List<@NotNull @Valid ClosedDay> closedDays,
        @NotNull @Min(0) @Max(10080) Integer minLeadMinutes,
        @NotNull @Min(1) @Max(365) Integer maxAdvanceDays,
        @NotNull @Min(1) @Max(1440) Integer minDurationMinutes,
        @NotNull @Min(1) @Max(1440) Integer maxDurationMinutes,
        @NotNull @Min(1) @Max(1440) Integer invitationMinutes)
{
    public record ClosedDay(@NotNull LocalDate date, @NotBlank @Size(max = 120) String reason) { }
}
