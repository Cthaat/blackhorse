package com.ruoyi.lab.service.impl;

import com.ruoyi.lab.service.LabHazardBlocker;

/** Legacy fallback retained only for explicit isolated unit construction. */
public class NoRecordedHazardBlocker implements LabHazardBlocker
{
    @Override
    public void assertNoMajorHazard(Long deviceId)
    {
        // No hazard source exists before M5; failover is replaced once that module is present.
    }

    @Override
    public boolean hasOpenMajorHazard(Long deviceId)
    {
        return false;
    }
}
