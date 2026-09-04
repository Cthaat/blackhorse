package com.ruoyi.lab.security;

import java.util.Objects;
import java.util.Set;

/**
 * Immutable laboratory visibility snapshot for one user.
 */
public record LabDataScope(long userId, boolean allLaboratories, Set<Long> laboratoryIds)
{
    public LabDataScope
    {
        laboratoryIds = Set.copyOf(Objects.requireNonNull(laboratoryIds, "laboratoryIds"));
    }

    public boolean restricted()
    {
        return !allLaboratories;
    }

    public boolean empty()
    {
        return restricted() && laboratoryIds.isEmpty();
    }
}
