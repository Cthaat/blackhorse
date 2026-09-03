package com.ruoyi.lab.service.impl;

import java.time.LocalDateTime;
import com.ruoyi.lab.exception.LabBusinessException;
import com.ruoyi.lab.exception.LabErrorCode;
import com.ruoyi.lab.mapper.LabQualificationMapper;
import com.ruoyi.lab.service.LabQualificationGuard;
import org.springframework.stereotype.Service;

/** Database-backed qualification coverage guard. */
@Service
public class LabQualificationGuardImpl implements LabQualificationGuard
{
    private final LabQualificationMapper qualificationMapper;

    public LabQualificationGuardImpl(LabQualificationMapper qualificationMapper)
    {
        this.qualificationMapper = qualificationMapper;
    }

    @Override
    public void assertQualified(Long userId, Long deviceId, LocalDateTime at)
    {
        if (!isQualified(userId, deviceId, at))
        {
            throw new LabBusinessException(LabErrorCode.LAB_QUALIFICATION_INVALID,
                    "当前用户没有覆盖该设备的有效资格");
        }
    }

    @Override
    public boolean isQualified(Long userId, Long deviceId, LocalDateTime at)
    {
        requirePositive(userId, "用户编号无效");
        requirePositive(deviceId, "设备编号无效");
        if (at == null)
        {
            throw validation("资格校验时间不能为空");
        }
        return qualificationMapper.countValidForDevice(userId, deviceId, at) > 0;
    }

    private static long requirePositive(Long value, String message)
    {
        if (value == null || value <= 0)
        {
            throw validation(message);
        }
        return value;
    }

    private static LabBusinessException validation(String message)
    {
        return new LabBusinessException(LabErrorCode.VALIDATION_ERROR, message);
    }
}
