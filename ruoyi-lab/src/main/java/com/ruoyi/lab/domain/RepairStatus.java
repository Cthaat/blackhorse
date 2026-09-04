package com.ruoyi.lab.domain;

/** Repair order lifecycle states. */
public enum RepairStatus
{
    WAIT_ASSIGN,
    WAIT_REPAIR,
    IN_PROGRESS,
    WAIT_ACCEPTANCE,
    CLOSED;

    public boolean canMoveTo(RepairStatus target)
    {
        if (target == null)
        {
            return false;
        }
        return switch (this)
        {
            case WAIT_ASSIGN -> target == WAIT_REPAIR;
            case WAIT_REPAIR -> target == IN_PROGRESS;
            case IN_PROGRESS -> target == WAIT_ACCEPTANCE;
            case WAIT_ACCEPTANCE -> target == IN_PROGRESS || target == CLOSED;
            case CLOSED -> false;
        };
    }
}
