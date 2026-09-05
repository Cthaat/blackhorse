package com.ruoyi.lab.vo;

import java.time.LocalDateTime;
import com.ruoyi.lab.serializer.LabBusinessId;
import com.ruoyi.lab.serializer.LabBusinessTime;

public record ReservationWaitlistVo(@LabBusinessId Long id, @LabBusinessId Long deviceId,
        @LabBusinessTime LocalDateTime startTime, @LabBusinessTime LocalDateTime endTime, String purpose,
        String status, Integer position, @LabBusinessTime LocalDateTime offeredUntil,
        @LabBusinessId Long reservationId, Integer version, String reason,
        @LabBusinessTime LocalDateTime createTime) { }
