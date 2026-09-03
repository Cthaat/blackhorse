package com.ruoyi.lab.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ruoyi.lab.domain.LabDevice;
import org.apache.ibatis.annotations.Param;

/**
 * Persistence operations for devices.
 */
public interface LabDeviceMapper extends BaseMapper<LabDevice>
{
    LabDevice selectByIdForUpdate(@Param("deviceId") Long deviceId);

    int updateStatusConditionally(@Param("deviceId") Long deviceId,
            @Param("expected") String expected, @Param("target") String target);
}
