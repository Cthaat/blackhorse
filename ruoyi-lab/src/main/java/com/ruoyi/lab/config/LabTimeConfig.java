package com.ruoyi.lab.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Shared production time source for laboratory business logic.
 */
@Configuration(proxyBeanMethods = false)
public class LabTimeConfig
{
    public static final ZoneId LAB_ZONE = ZoneId.of("Asia/Shanghai");

    @Bean
    public Clock labClock()
    {
        return Clock.system(LAB_ZONE);
    }
}
