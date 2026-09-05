package com.ruoyi.lab.sla;
import java.time.LocalDateTime;
import com.ruoyi.lab.serializer.*;
public class SlaAlert
{
    @LabBusinessId public Long id;
    @LabBusinessId public Long recordId;
    public String phase;
    public String stage;
    @LabBusinessTime public LocalDateTime createdAt;
}
