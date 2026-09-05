package com.ruoyi.lab.vo;

import java.time.LocalDateTime;
import com.ruoyi.lab.dto.ReservationRuleDefinition;
import com.ruoyi.lab.serializer.LabBusinessId;
import com.ruoyi.lab.serializer.LabBusinessTime;

public record ReservationRuleVo(@LabBusinessId Long id, @LabBusinessId Long deviceId,
        Integer versionNumber, Integer revision, String status, ReservationRuleDefinition definition,
        @LabBusinessTime LocalDateTime createTime, @LabBusinessTime LocalDateTime publishedAt) { }
