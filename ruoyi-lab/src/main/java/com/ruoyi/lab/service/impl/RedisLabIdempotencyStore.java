package com.ruoyi.lab.service.impl;

import java.time.Duration;
import java.util.Optional;
import com.ruoyi.lab.service.IdempotencySnapshot;
import com.ruoyi.lab.service.LabIdempotencyStore;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/** Compact Redis implementation. Callers deliberately treat failures as cache misses. */
@Service
public class RedisLabIdempotencyStore implements LabIdempotencyStore
{
    private static final String PREFIX = "lab:idempotency:";
    private final StringRedisTemplate redisTemplate;

    public RedisLabIdempotencyStore(StringRedisTemplate redisTemplate)
    {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public Optional<IdempotencySnapshot> get(long userId, String command, String key)
    {
        String raw = redisTemplate.opsForValue().get(cacheKey(userId, command, key));
        if (raw == null)
        {
            return Optional.empty();
        }
        int separator = raw.indexOf(':');
        if (separator <= 0 || separator == raw.length() - 1)
        {
            return Optional.empty();
        }
        try
        {
            return Optional.of(new IdempotencySnapshot(Long.parseLong(raw.substring(0, separator)),
                    raw.substring(separator + 1)));
        }
        catch (NumberFormatException exception)
        {
            return Optional.empty();
        }
    }

    @Override
    public void put(long userId, String command, String key, IdempotencySnapshot value, Duration ttl)
    {
        redisTemplate.opsForValue().set(cacheKey(userId, command, key),
                value.reservationId() + ":" + value.requestHash(), ttl);
    }

    private static String cacheKey(long userId, String command, String key)
    {
        return PREFIX + command + ":" + userId + ":" + key;
    }
}
