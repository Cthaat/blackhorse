package com.ruoyi.lab.restriction;

import java.util.List;
import jakarta.validation.constraints.*;

public final class RestrictionCommands
{
    private RestrictionCommands() { }
    public record Manual(@NotNull @Positive Long laboratoryId, @NotNull @Positive Long userId,
            @NotNull @Min(1) @Max(365) Integer days, @NotBlank @Size(max=1000) String reason) { }
    public record Reason(@NotBlank @Size(max=1000) String reason) { }
    public record Appeal(@NotBlank @Size(max=1000) String reason,
            @Size(max=10) List<@NotNull @Positive Long> attachmentIds) { }
    public record Decision(@NotNull Boolean approved, @NotBlank @Size(max=1000) String reason) { }
    public record Rule(@NotNull @Positive Long laboratoryId,
            @NotNull @Min(1) @Max(90) Integer days, @NotBlank @Size(max=1000) String reason) { }
}
