package com.ruoyi.lab.restriction;

import java.time.LocalDateTime;
import com.ruoyi.lab.serializer.LabBusinessId;
import com.ruoyi.lab.serializer.LabBusinessTime;

public class RestrictionRule
{
    @LabBusinessId public Long id;
    @LabBusinessId public Long laboratoryId;
    public Integer days;
    public String reason;
    @LabBusinessId public Long createdBy;
    @LabBusinessTime public LocalDateTime createdAt;
}
