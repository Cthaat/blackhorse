package com.ruoyi.lab.service;

/** Cross-milestone contract for reservation and usage hazard blocking. */
public interface LabHazardBlocker
{
    void assertNoMajorHazard(Long deviceId);

    boolean hasOpenMajorHazard(Long deviceId);
}
