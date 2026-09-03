package com.ruoyi.lab.service;

import java.time.LocalDateTime;

/** Stable qualification coverage contract used by reservation and usage workflows. */
public interface LabQualificationGuard
{
    void assertQualified(Long userId, Long deviceId, LocalDateTime at);

    boolean isQualified(Long userId, Long deviceId, LocalDateTime at);
}
