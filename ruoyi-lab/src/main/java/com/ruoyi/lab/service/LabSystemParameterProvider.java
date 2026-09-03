package com.ruoyi.lab.service;

/** Validated access to laboratory policy values stored in sys_config. */
public interface LabSystemParameterProvider
{
    int requiredInteger(String key, int minimum, int maximum);
}
