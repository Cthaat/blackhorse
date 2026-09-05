package com.ruoyi.lab.maintenance;

import java.time.OffsetDateTime;
import jakarta.validation.constraints.*;

public final class MaintenanceCommands
{
    private MaintenanceCommands() { }
    public record Plan(@NotNull @Positive Long deviceId,@NotBlank @Pattern(regexp="MAINTENANCE|CALIBRATION") String kind,
            @NotNull @Min(1) @Max(3650) Integer periodDays,@NotNull OffsetDateTime firstDueAt,
            @NotNull @Positive Long responsibleId,@Size(max=1000) String description,
            @NotBlank @Size(max=500) String reason,@Min(0) Integer expectedVersion) { }
    public record Toggle(@NotNull Boolean enabled,@NotNull @Min(0) Integer expectedVersion,
            @NotBlank @Size(max=500) String reason) { }
    public record Window(@NotNull OffsetDateTime startTime,@NotNull OffsetDateTime endTime,
            @NotNull @Min(0) Integer expectedVersion,@NotBlank @Size(max=500) String reason) { }
    public record Start(@NotNull @Min(0) Integer expectedVersion,@NotBlank @Size(max=500) String reason) { }
}
