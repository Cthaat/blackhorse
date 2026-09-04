package com.ruoyi.lab.service;

public interface DeviceAvailabilityService
{
    void restoreAfterRepair(Long deviceId, Long operatorId);

    void restoreAfterLaboratoryEnabled(Long laboratoryId, Long operatorId);
}
