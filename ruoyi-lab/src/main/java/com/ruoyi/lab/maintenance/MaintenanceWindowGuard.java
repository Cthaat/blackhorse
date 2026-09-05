package com.ruoyi.lab.maintenance;

import java.time.*;
import com.ruoyi.lab.mapper.LabMaintenanceMapper;
import com.ruoyi.lab.exception.*;
import org.springframework.stereotype.Service;

/** Caller holds the same device row used by window scheduling. No reverse user/gate locks. */
@Service
public class MaintenanceWindowGuard
{
    private final LabMaintenanceMapper mapper;
    private final Clock clock;
    public MaintenanceWindowGuard(LabMaintenanceMapper mapper,Clock clock) { this.mapper=mapper;this.clock=clock; }
    public void assertAvailable(Long deviceId,LocalDateTime start,LocalDateTime end)
    {
        if (mapper.overlaps(deviceId,start,end)>0)
            throw new LabBusinessException(LabErrorCode.LAB_DEVICE_UNAVAILABLE,"该时段与已安排的维护或校准停用窗口冲突");
    }
    public boolean blocksNow(Long deviceId) { return mapper.blocksNow(deviceId,LocalDateTime.now(clock))>0; }
}
