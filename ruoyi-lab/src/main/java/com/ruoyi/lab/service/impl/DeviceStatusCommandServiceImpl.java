package com.ruoyi.lab.service.impl;

import java.util.Objects;
import com.ruoyi.lab.domain.DeviceStatus;
import com.ruoyi.lab.domain.LabDevice;
import com.ruoyi.lab.domain.LabLaboratory;
import com.ruoyi.lab.domain.LaboratoryStatus;
import com.ruoyi.lab.dto.DeviceStatusCommandDto;
import com.ruoyi.lab.exception.LabBusinessException;
import com.ruoyi.lab.exception.LabErrorCode;
import com.ruoyi.lab.mapper.LabDeviceMapper;
import com.ruoyi.lab.mapper.LabLaboratoryMapper;
import com.ruoyi.lab.security.LabDataScope;
import com.ruoyi.lab.security.LabDataScopeService;
import com.ruoyi.lab.service.DeviceStatusCommandService;
import com.ruoyi.lab.service.LabStatusHistoryService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Locked and audited device lifecycle command handler. */
@Service
public class DeviceStatusCommandServiceImpl implements DeviceStatusCommandService
{
    private static final String OBJECT_TYPE = "DEVICE";

    private final LabDeviceMapper deviceMapper;
    private final LabLaboratoryMapper laboratoryMapper;
    private final LabDataScopeService dataScopeService;
    private final LabStatusHistoryService historyService;

    public DeviceStatusCommandServiceImpl(LabDeviceMapper deviceMapper,
            LabLaboratoryMapper laboratoryMapper, LabDataScopeService dataScopeService,
            LabStatusHistoryService historyService)
    {
        this.deviceMapper = deviceMapper;
        this.laboratoryMapper = laboratoryMapper;
        this.dataScopeService = dataScopeService;
        this.historyService = historyService;
    }

    @Override
    @Transactional
    public void changeStatus(Long deviceId, DeviceStatusCommandDto command, Long actorId)
    {
        if (deviceId == null || deviceId <= 0 || command == null || command.getTargetStatus() == null)
        {
            throw new LabBusinessException(LabErrorCode.VALIDATION_ERROR, "设备状态命令无效");
        }
        String reason = requireReason(command.getReason());
        LabDataScope scope = dataScopeService.resolveCurrentScope();
        if (!Objects.equals(scope.userId(), actorId))
        {
            throw outOfScope();
        }

        LabDevice snapshot = deviceMapper.selectByIdInScope(deviceId, scope);
        if (snapshot == null)
        {
            if (scope.allLaboratories())
            {
                throw new LabBusinessException(LabErrorCode.RESOURCE_NOT_FOUND, "设备不存在");
            }
            throw outOfScope();
        }

        LabDevice locked = deviceMapper.selectByIdForUpdate(deviceId);
        if (locked == null)
        {
            throw new LabBusinessException(LabErrorCode.RESOURCE_NOT_FOUND, "设备不存在");
        }
        if (scope.restricted() && !scope.laboratoryIds().contains(locked.getLaboratoryId()))
        {
            throw outOfScope();
        }

        DeviceStatus current = locked.getStatus();
        DeviceStatus target = command.getTargetStatus();
        if (current == null || !current.canMoveTo(target))
        {
            throw new LabBusinessException(LabErrorCode.LAB_ILLEGAL_STATE_TRANSITION, "设备状态变更不合法");
        }
        if (current == DeviceStatus.DISABLED && target == DeviceStatus.AVAILABLE)
        {
            LabLaboratory laboratory = laboratoryMapper.selectByIdForUpdate(locked.getLaboratoryId());
            if (laboratory == null)
            {
                throw new LabBusinessException(LabErrorCode.RESOURCE_NOT_FOUND, "实验室不存在");
            }
            if (scope.restricted() && !scope.laboratoryIds().contains(laboratory.getId()))
            {
                throw outOfScope();
            }
            if (laboratory.getStatus() != LaboratoryStatus.ENABLED)
            {
                throw new LabBusinessException(LabErrorCode.LAB_LABORATORY_DISABLED, "实验室已停用");
            }
        }

        if (deviceMapper.updateStatusConditionally(deviceId, current.name(), target.name()) != 1)
        {
            throw new LabBusinessException(LabErrorCode.LAB_DUPLICATE_OPERATION, "操作已被其他请求处理");
        }
        historyService.append(OBJECT_TYPE, deviceId, current.name(), target.name(), actorId, reason);
    }

    private static String requireReason(String value)
    {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty() || normalized.length() > 500)
        {
            throw new LabBusinessException(LabErrorCode.VALIDATION_ERROR, "状态变更原因长度无效");
        }
        return normalized;
    }

    private static LabBusinessException outOfScope()
    {
        return new LabBusinessException(LabErrorCode.LAB_OUT_OF_DATA_SCOPE, "对象不在当前数据范围内");
    }
}
