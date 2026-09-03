package com.ruoyi.lab.service;

import com.ruoyi.lab.domain.DeviceStatus;
import com.ruoyi.lab.domain.LabDevice;

public interface DeviceStatusTransitionGuard
{
    void assertNoOperationalBlocker(LabDevice lockedDevice, DeviceStatus targetStatus);
}
