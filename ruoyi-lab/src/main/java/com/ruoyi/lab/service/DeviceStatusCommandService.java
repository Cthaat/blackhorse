package com.ruoyi.lab.service;

import com.ruoyi.lab.dto.DeviceStatusCommandDto;

/** Explicit device lifecycle command boundary. */
public interface DeviceStatusCommandService
{
    void changeStatus(Long deviceId, DeviceStatusCommandDto command, Long actorId);
}
