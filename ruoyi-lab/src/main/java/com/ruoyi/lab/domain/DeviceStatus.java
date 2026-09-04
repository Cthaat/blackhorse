package com.ruoyi.lab.domain;

/**
 * Device lifecycle state.
 */
public enum DeviceStatus
{
    AVAILABLE,
    IN_USE,
    FAULT,
    MAINTENANCE,
    DISABLED;

    /**
     * Returns whether this state has an M2 lifecycle edge to {@code target}.
     */
    public boolean canMoveTo(DeviceStatus target)
    {
        if (target == null || target == this)
        {
            return false;
        }
        return switch (this)
        {
            case AVAILABLE -> target == FAULT || target == DISABLED;
            case FAULT -> target == DISABLED;
            case DISABLED -> target == AVAILABLE;
            case IN_USE, MAINTENANCE -> false;
        };
    }
}
