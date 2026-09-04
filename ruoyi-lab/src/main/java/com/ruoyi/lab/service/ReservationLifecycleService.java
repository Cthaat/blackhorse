package com.ruoyi.lab.service;

import java.time.LocalDateTime;

/** Idempotent reservation expiry commands called by Quartz. */
public interface ReservationLifecycleService
{
    int expirePending(LocalDateTime now, int batchSize);

    int markNoShow(LocalDateTime now, int batchSize);
}
