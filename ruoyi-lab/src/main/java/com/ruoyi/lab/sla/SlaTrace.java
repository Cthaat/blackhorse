package com.ruoyi.lab.sla;
import java.time.LocalDateTime;
import com.ruoyi.lab.serializer.*;
public record SlaTrace(@LabBusinessId Long id,String action,String reason,@LabBusinessId Long operatorId,
        @LabBusinessTime LocalDateTime createdAt) { }
