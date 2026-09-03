package com.ruoyi.lab.service.impl;

import com.ruoyi.lab.domain.DeviceStatus;
import com.ruoyi.lab.domain.LabDevice;
import com.ruoyi.lab.domain.LabLaboratory;
import com.ruoyi.lab.domain.LaboratoryStatus;
import com.ruoyi.lab.exception.LabBusinessException;
import com.ruoyi.lab.exception.LabErrorCode;
import com.ruoyi.lab.mapper.LabDeviceMapper;
import com.ruoyi.lab.mapper.LabLaboratoryMapper;
import com.ruoyi.lab.mapper.LabRepairOrderMapper;
import com.ruoyi.lab.mapper.LabUsageRecordMapper;
import com.ruoyi.lab.service.DeviceAvailabilityService;
import com.ruoyi.lab.service.LabHazardBlocker;
import com.ruoyi.lab.service.LabStatusHistoryService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Internal-only recovery path after a repair or hazard blocker is cleared. */
@Service
public class DeviceAvailabilityServiceImpl implements DeviceAvailabilityService
{
    private final LabDeviceMapper deviceMapper;
    private final LabLaboratoryMapper laboratoryMapper;
    private final LabUsageRecordMapper usageMapper;
    private final LabRepairOrderMapper repairMapper;
    private final LabHazardBlocker hazardBlocker;
    private final LabStatusHistoryService historyService;

    public DeviceAvailabilityServiceImpl(LabDeviceMapper deviceMapper,
            LabLaboratoryMapper laboratoryMapper, LabUsageRecordMapper usageMapper,
            LabRepairOrderMapper repairMapper, LabHazardBlocker hazardBlocker,
            LabStatusHistoryService historyService)
    {
        this.deviceMapper = deviceMapper;
        this.laboratoryMapper = laboratoryMapper;
        this.usageMapper = usageMapper;
        this.repairMapper = repairMapper;
        this.hazardBlocker = hazardBlocker;
        this.historyService = historyService;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void restoreAfterRepair(Long deviceId, Long operatorId)
    {
        if (deviceId == null || deviceId <= 0 || operatorId == null || operatorId <= 0)
        {
            throw new LabBusinessException(LabErrorCode.VALIDATION_ERROR, "设备恢复参数无效");
        }
        LabDevice device = deviceMapper.selectByIdForUpdate(deviceId);
        if (device == null)
        {
            throw new LabBusinessException(LabErrorCode.RESOURCE_NOT_FOUND, "设备不存在");
        }
        if (device.getStatus() != DeviceStatus.MAINTENANCE)
        {
            return;
        }
        LabLaboratory laboratory = laboratoryMapper.selectById(device.getLaboratoryId());
        if (laboratory == null || laboratory.getStatus() != LaboratoryStatus.ENABLED
                || !usageMapper.selectUnreturnedIdsByDeviceIdForUpdate(deviceId).isEmpty()
                || !repairMapper.selectOpenIdsByDeviceIdForUpdate(deviceId).isEmpty()
                || hazardBlocker.hasOpenMajorHazard(deviceId))
        {
            return;
        }
        if (deviceMapper.updateStatusConditionally(deviceId, DeviceStatus.MAINTENANCE.name(),
                DeviceStatus.AVAILABLE.name()) != 1)
        {
            throw new LabBusinessException(LabErrorCode.LAB_DUPLICATE_OPERATION,
                    "操作已被其他请求处理");
        }
        historyService.append("DEVICE", deviceId, DeviceStatus.MAINTENANCE.name(),
                DeviceStatus.AVAILABLE.name(), operatorId, "维修验收后恢复可用");
    }
}
