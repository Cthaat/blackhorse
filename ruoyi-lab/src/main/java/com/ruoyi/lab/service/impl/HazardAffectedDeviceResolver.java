package com.ruoyi.lab.service.impl;

import java.util.List;
import com.ruoyi.lab.domain.HazardTargetType;
import com.ruoyi.lab.domain.LabDevice;
import com.ruoyi.lab.exception.LabBusinessException;
import com.ruoyi.lab.exception.LabErrorCode;
import com.ruoyi.lab.mapper.LabDeviceMapper;
import com.ruoyi.lab.mapper.LabHazardMapper;
import org.springframework.stereotype.Component;

@Component
public class HazardAffectedDeviceResolver
{
    private final LabHazardMapper hazardMapper;
    private final LabDeviceMapper deviceMapper;

    public HazardAffectedDeviceResolver(LabHazardMapper hazardMapper, LabDeviceMapper deviceMapper)
    {
        this.hazardMapper = hazardMapper;
        this.deviceMapper = deviceMapper;
    }

    public List<Long> resolveSorted(HazardTargetType targetType, Long targetId)
    {
        if (targetType == null || targetId == null || targetId <= 0)
        {
            throw new LabBusinessException(LabErrorCode.VALIDATION_ERROR, "隐患目标参数无效");
        }
        if (targetType == HazardTargetType.DEVICE)
        {
            LabDevice device = deviceMapper.selectById(targetId);
            if (device == null || !"0".equals(device.getDelFlag()))
            {
                throw new LabBusinessException(LabErrorCode.RESOURCE_NOT_FOUND, "隐患目标不存在");
            }
            return List.of(targetId);
        }
        return hazardMapper.selectDeviceIdsByLaboratory(targetId).stream().sorted().toList();
    }
}
