package com.ruoyi.lab.security;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.lab.domain.LabDevice;
import com.ruoyi.lab.domain.LabLaboratory;
import com.ruoyi.lab.exception.LabBusinessException;
import com.ruoyi.lab.exception.LabErrorCode;
import com.ruoyi.lab.mapper.LabDeviceMapper;
import com.ruoyi.lab.mapper.LabLaboratoryMapper;
import org.springframework.stereotype.Service;

/** Fail-closed implementation of laboratory and device object checks. */
@Service
public class LabObjectPermissionServiceImpl implements LabObjectPermissionService
{
    private final LabDataScopeService dataScopeService;
    private final LabLaboratoryMapper laboratoryMapper;
    private final LabDeviceMapper deviceMapper;
    private final Clock clock;

    public LabObjectPermissionServiceImpl(LabDataScopeService dataScopeService,
            LabLaboratoryMapper laboratoryMapper, LabDeviceMapper deviceMapper, Clock clock)
    {
        this.dataScopeService = dataScopeService;
        this.laboratoryMapper = laboratoryMapper;
        this.deviceMapper = deviceMapper;
        this.clock = clock;
    }

    @Override
    public void assertLaboratoryReadable(long laboratoryId)
    {
        requireLaboratory(laboratoryId, dataScopeService.resolveCurrentScope());
    }

    @Override
    public void assertLaboratoryManageable(long laboratoryId)
    {
        assertLaboratoryReadable(laboratoryId);
    }

    @Override
    public void assertDeviceReadable(long deviceId)
    {
        LabDataScope scope = dataScopeService.resolveCurrentScope();
        LabDevice device = deviceMapper.selectByIdReadable(deviceId, scope, scope.userId(),
                LocalDateTime.now(clock));
        if (device != null)
        {
            return;
        }
        if (scope.allLaboratories())
        {
            throw new LabBusinessException(LabErrorCode.RESOURCE_NOT_FOUND, "设备不存在");
        }
        throw outOfScope();
    }

    @Override
    public void assertDeviceManageable(long deviceId)
    {
        requireDevice(deviceId, dataScopeService.resolveCurrentScope());
    }

    @Override
    public Set<Long> readableDepartmentIds()
    {
        LabDataScope scope = dataScopeService.resolveCurrentScope();
        Set<Long> departmentIds = new HashSet<>();
        Long currentDepartmentId = SecurityUtils.getDeptId();
        if (currentDepartmentId != null)
        {
            departmentIds.add(currentDepartmentId);
        }
        List<LabLaboratory> laboratories = laboratoryMapper.selectListByScope(scope, null, null, null);
        if (laboratories != null)
        {
            laboratories.stream().map(LabLaboratory::getDeptId).forEach(departmentIds::add);
        }
        return Set.copyOf(departmentIds);
    }

    @Override
    public long currentUserId()
    {
        return SecurityUtils.getUserId();
    }

    private LabLaboratory requireLaboratory(long id, LabDataScope scope)
    {
        LabLaboratory laboratory = laboratoryMapper.selectByIdInScope(id, scope);
        if (laboratory != null)
        {
            return laboratory;
        }
        if (scope.allLaboratories() || scope.laboratoryIds().contains(id))
        {
            throw new LabBusinessException(LabErrorCode.RESOURCE_NOT_FOUND, "实验室不存在");
        }
        throw outOfScope();
    }

    private LabDevice requireDevice(long id, LabDataScope scope)
    {
        LabDevice device = deviceMapper.selectByIdInScope(id, scope);
        if (device != null)
        {
            return device;
        }
        if (scope.allLaboratories())
        {
            throw new LabBusinessException(LabErrorCode.RESOURCE_NOT_FOUND, "设备不存在");
        }
        throw outOfScope();
    }

    private static LabBusinessException outOfScope()
    {
        return new LabBusinessException(LabErrorCode.LAB_OUT_OF_DATA_SCOPE, "对象不在当前数据范围内");
    }
}
