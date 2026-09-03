package com.ruoyi.lab.mapper;

import java.util.List;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ruoyi.lab.domain.DeviceStatus;
import com.ruoyi.lab.domain.LabDevice;
import com.ruoyi.lab.security.LabDataScope;
import com.ruoyi.lab.service.LabSortWhitelist.SortClause;
import org.apache.ibatis.annotations.Param;

/**
 * Persistence operations for devices.
 */
public interface LabDeviceMapper extends BaseMapper<LabDevice>
{
    List<LabDevice> selectListByScope(@Param("scope") LabDataScope scope,
            @Param("laboratoryId") Long laboratoryId, @Param("categoryCode") String categoryCode,
            @Param("status") DeviceStatus status, @Param("keyword") String keyword,
            @Param("sort") SortClause sort);

    LabDevice selectByIdInScope(@Param("deviceId") Long deviceId,
            @Param("scope") LabDataScope scope);

    LabDevice selectByIdForUpdate(@Param("deviceId") Long deviceId);

    int updateDetailsConditionally(@Param("device") LabDevice device,
            @Param("expectedVersion") Integer expectedVersion);

    int updateStatusConditionally(@Param("deviceId") Long deviceId,
            @Param("expected") String expected, @Param("target") String target);
}
