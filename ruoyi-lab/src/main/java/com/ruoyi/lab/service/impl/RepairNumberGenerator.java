package com.ruoyi.lab.service.impl;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Generates compact, collision-resistant human-facing repair numbers. */
@Component
public class RepairNumberGenerator
{
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private final Clock clock;

    public RepairNumberGenerator(Clock clock)
    {
        this.clock = clock;
    }

    public String next()
    {
        return "RP" + LocalDateTime.now(clock).format(TIME)
                + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}
