package com.ruoyi.lab.sla;
import jakarta.validation.constraints.*;
public final class SlaCommands
{
    private SlaCommands() { }
    public record Rule(@NotNull @Positive Long laboratoryId,
            @NotBlank @Pattern(regexp="REPAIR|MAINTENANCE|HAZARD") String businessType,
            @NotBlank @Pattern(regexp="LOW|MEDIUM|HIGH|MAJOR") String risk,
            @NotNull @Min(1) @Max(720) Integer responseHours,
            @NotNull @Min(1) @Max(8760) Integer processingHours,
            @NotBlank @Size(max=500) String reason) { }
    public record ClockCommand(@NotNull @Min(0) Integer expectedVersion,@NotBlank @Size(max=500) String reason) { }
}
