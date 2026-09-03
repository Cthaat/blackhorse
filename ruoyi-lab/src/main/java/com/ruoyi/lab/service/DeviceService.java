package com.ruoyi.lab.service;

import java.time.LocalDateTime;
import java.util.List;
import com.ruoyi.lab.domain.DeviceStatus;
import com.ruoyi.lab.dto.DeviceCreateDto;
import com.ruoyi.lab.dto.DeviceUpdateDto;
import com.ruoyi.lab.vo.DeviceVo;
import com.ruoyi.lab.vo.OccupiedRangeVo;

/** Device profile and read operations. Lifecycle changes use DeviceStatusCommandService. */
public interface DeviceService
{
    List<DeviceVo> list(Long laboratoryId, String categoryCode, DeviceStatus status,
            String keyword, String sortBy, String sortDirection);

    DeviceVo getById(Long deviceId);

    DeviceVo create(DeviceCreateDto input, String username, Long actorId);

    void update(Long deviceId, DeviceUpdateDto input, String username);

    List<OccupiedRangeVo> occupiedRanges(Long deviceId, LocalDateTime from, LocalDateTime to);
}
