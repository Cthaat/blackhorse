package com.ruoyi.lab.domain;

/** Equipment condition observed during return. */
public enum ReturnCondition
{
    NORMAL,
    DAMAGED,
    FAULT;

    public boolean isAbnormal()
    {
        return this != NORMAL;
    }
}
