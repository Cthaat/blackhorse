package com.ruoyi.lab.domain;

import java.util.EnumSet;

public enum HazardStatus
{
    PENDING_RECTIFICATION,
    RECTIFYING,
    PENDING_REVIEW,
    CLOSED;

    public boolean canMoveTo(HazardStatus target)
    {
        if (target == null)
        {
            return false;
        }
        return switch (this)
        {
            case PENDING_RECTIFICATION -> target == RECTIFYING;
            case RECTIFYING -> target == PENDING_REVIEW;
            case PENDING_REVIEW -> EnumSet.of(RECTIFYING, CLOSED).contains(target);
            case CLOSED -> false;
        };
    }

    public boolean blocksWhenMajor()
    {
        return this != CLOSED;
    }
}
