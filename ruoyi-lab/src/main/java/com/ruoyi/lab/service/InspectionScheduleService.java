package com.ruoyi.lab.service;

import java.time.LocalDateTime;

public interface InspectionScheduleService
{
    int generateDueTasks(LocalDateTime now, int batchSize);
}
