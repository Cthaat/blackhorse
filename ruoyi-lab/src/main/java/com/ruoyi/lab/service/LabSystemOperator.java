package com.ruoyi.lab.service;

/** Disabled, unprivileged account used only for scheduled audit history. */
public record LabSystemOperator(Long userId, String userName)
{
}
