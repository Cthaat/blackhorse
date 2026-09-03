package com.ruoyi.lab.service;

import java.time.LocalDateTime;

public interface InspectionLifecycleService
{
    int markOverdue(LocalDateTime now, int batchSize);
}
