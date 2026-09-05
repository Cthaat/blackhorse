package com.ruoyi.lab.maintenance;

import java.time.LocalDateTime;
import com.ruoyi.lab.serializer.*;

public record MaintenanceConflict(String kind,@LabBusinessId Long id,
        @LabBusinessTime LocalDateTime startTime,@LabBusinessTime LocalDateTime endTime) { }
