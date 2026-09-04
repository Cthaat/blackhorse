package com.ruoyi.lab.service.impl;

import com.ruoyi.lab.domain.DeviceStatus;
import com.ruoyi.lab.domain.LabDevice;
import com.ruoyi.lab.domain.LabLaboratory;
import com.ruoyi.lab.domain.LaboratoryStatus;
import com.ruoyi.lab.exception.LabBusinessException;
import com.ruoyi.lab.exception.LabErrorCode;
import com.ruoyi.lab.mapper.LabLaboratoryMapper;
import com.ruoyi.lab.mapper.LabRepairOrderMapper;
import com.ruoyi.lab.mapper.LabUsageRecordMapper;
import com.ruoyi.lab.service.DeviceStatusTransitionGuard;
import com.ruoyi.lab.service.LabHazardBlocker;
import org.springframework.stereotype.Service;

/** Locks operational blockers after the caller has locked the device row. */
@Service
public class DeviceStatusTransitionGuardImpl implements DeviceStatusTransitionGuard
{
    private final LabUsageRecordMapper usageMapper;
    private final LabRepairOrderMapper repairMapper;
    private final LabLaboratoryMapper laboratoryMapper;
    private final LabHazardBlocker hazardBlocker;

    public DeviceStatusTransitionGuardImpl(LabUsageRecordMapper usageMapper,
            LabRepairOrderMapper repairMapper, LabLaboratoryMapper laboratoryMapper,
            LabHazardBlocker hazardBlocker)
    {
        this.usageMapper = usageMapper;
        this.repairMapper = repairMapper;
        this.laboratoryMapper = laboratoryMapper;
        this.hazardBlocker = hazardBlocker;
    }

    @Override
    public void assertNoOperationalBlocker(LabDevice lockedDevice, DeviceStatus targetStatus)
    {
        if (lockedDevice == null || lockedDevice.getStatus() == null || targetStatus == null)
        {
            throw new LabBusinessException(LabErrorCode.VALIDATION_ERROR, "设备状态命令无效");
        }
        DeviceStatus source = lockedDevice.getStatus();
        boolean managedToggle = targetStatus == DeviceStatus.DISABLED
                && (source == DeviceStatus.AVAILABLE || source == DeviceStatus.FAULT)
                || source == DeviceStatus.DISABLED && targetStatus == DeviceStatus.AVAILABLE;
        if (!managedToggle)
        {
            return;
        }
        if (!usageMapper.selectUnreturnedIdsByDeviceIdForUpdate(lockedDevice.getId()).isEmpty())
        {
            throw new LabBusinessException(LabErrorCode.LAB_DEVICE_UNAVAILABLE,
                    "设备存在未归还使用记录");
        }
        if (!repairMapper.selectOpenIdsByDeviceIdForUpdate(lockedDevice.getId()).isEmpty())
        {
            throw new LabBusinessException(LabErrorCode.LAB_REPAIR_ALREADY_OPEN,
                    "设备存在开放维修工单");
        }
        hazardBlocker.assertNoMajorHazard(lockedDevice.getId());
        if (targetStatus == DeviceStatus.AVAILABLE)
        {
            LabLaboratory laboratory = laboratoryMapper.selectByIdForUpdate(
                    lockedDevice.getLaboratoryId());
            if (laboratory == null)
            {
                throw new LabBusinessException(LabErrorCode.RESOURCE_NOT_FOUND, "实验室不存在");
            }
            if (laboratory.getStatus() != LaboratoryStatus.ENABLED)
            {
                throw new LabBusinessException(LabErrorCode.LAB_LABORATORY_DISABLED,
                        "实验室已停用");
            }
        }
    }
}
