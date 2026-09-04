package com.ruoyi.lab.dto;

import com.ruoyi.lab.domain.DeviceStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Explicit device lifecycle command.
 */
public class DeviceStatusCommandDto
{
    @NotNull
    private DeviceStatus targetStatus;

    @NotBlank
    @Size(max = 500)
    private String reason;

    public DeviceStatusCommandDto()
    {
    }

    public DeviceStatusCommandDto(DeviceStatus targetStatus, String reason)
    {
        this.targetStatus = targetStatus;
        setReason(reason);
    }

    public DeviceStatus getTargetStatus()
    {
        return targetStatus;
    }

    public void setTargetStatus(DeviceStatus targetStatus)
    {
        this.targetStatus = targetStatus;
    }

    public String getReason()
    {
        return reason;
    }

    public void setReason(String reason)
    {
        this.reason = reason == null ? null : reason.trim();
    }
}
