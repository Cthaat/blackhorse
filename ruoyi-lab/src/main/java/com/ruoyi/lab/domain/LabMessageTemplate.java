package com.ruoyi.lab.domain;

import java.time.LocalDateTime;
import com.ruoyi.lab.serializer.LabBusinessId;
import com.ruoyi.lab.serializer.LabBusinessTime;

public class LabMessageTemplate
{
    @LabBusinessId public Long id;
    public String eventType;
    public String title;
    public String content;
    public String status;
    @LabBusinessId public Long operatorId;
    @LabBusinessTime public LocalDateTime createTime;
    @LabBusinessTime public LocalDateTime publishTime;
}
