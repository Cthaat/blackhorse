package com.ruoyi.lab.service.impl;

import com.ruoyi.lab.service.LabHazardBlocker;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Service;

/** M3 fallback used before the hazard tables are introduced. */
@Service
@ConditionalOnMissingBean(LabHazardBlocker.class)
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
