package com.ruoyi.lab.service;

import java.time.LocalDateTime;

public interface HazardLifecycleService
{
    int markOverdue(LocalDateTime now, int batchSize);
}
