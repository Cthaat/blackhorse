package com.ruoyi.lab.service;

import java.time.Duration;
import java.util.Optional;

/** Optional cache acceleration for command idempotency. */
public interface LabIdempotencyStore
{
    Optional<IdempotencySnapshot> get(long userId, String command, String key);

    void put(long userId, String command, String key, IdempotencySnapshot value, Duration ttl);
}
