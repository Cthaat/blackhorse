package com.ruoyi.lab.security;

import com.ruoyi.lab.exception.LabBusinessException;
import com.ruoyi.lab.exception.LabErrorCode;
import com.ruoyi.lab.mapper.LabHazardMapper;
import com.ruoyi.lab.service.LabHazardBlocker;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Database-backed major-hazard guard shared by reservation, usage and device commands. */
@Service
public class LabHazardBlockerImpl implements LabHazardBlocker
{
    private final LabHazardMapper hazardMapper;

    public LabHazardBlockerImpl(LabHazardMapper hazardMapper)
    {
        this.hazardMapper = hazardMapper;
    }

    @Override
    @Transactional
    public void assertNoMajorHazard(Long deviceId)
    {
        if (hasOpenMajorHazard(deviceId))
        {
            throw new LabBusinessException(LabErrorCode.LAB_MAJOR_HAZARD_BLOCKED,
                    "设备存在未销号重大隐患");
        }
    }

    @Override
    @Transactional
    public boolean hasOpenMajorHazard(Long deviceId)
    {
        if (deviceId == null || deviceId <= 0)
        {
            throw new LabBusinessException(LabErrorCode.VALIDATION_ERROR, "设备编号无效");
        }
        return !hazardMapper.selectOpenMajorHazardIdsForDeviceForUpdate(deviceId).isEmpty();
    }
}
