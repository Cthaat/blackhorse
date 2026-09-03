package com.ruoyi.lab.service.impl;

import java.util.Set;
import com.ruoyi.lab.mapper.LabSystemConfigMapper;
import com.ruoyi.lab.service.LabSystemParameterProvider;
import org.springframework.stereotype.Service;

/** Fail-fast database implementation for the small laboratory parameter allowlist. */
@Service
public class DatabaseLabSystemParameterProvider implements LabSystemParameterProvider
{
    private static final Set<String> ALLOWED_KEYS = Set.of(
            "lab.reservation.min-lead-minutes",
            "lab.reservation.max-advance-days",
            "lab.reservation.min-duration-minutes",
            "lab.reservation.max-duration-minutes",
            "lab.usage.checkout.late-minutes");

    private final LabSystemConfigMapper configMapper;

    public DatabaseLabSystemParameterProvider(LabSystemConfigMapper configMapper)
    {
        this.configMapper = configMapper;
    }

    @Override
    public int requiredInteger(String key, int minimum, int maximum)
    {
        if (!ALLOWED_KEYS.contains(key))
        {
            throw new IllegalStateException("laboratory parameter key is not allowed");
        }
        String raw = configMapper.selectValueByKey(key);
        try
        {
            int value = Integer.parseInt(raw);
            if (value < minimum || value > maximum)
            {
                throw new IllegalStateException("laboratory parameter value is out of range");
            }
            return value;
        }
        catch (NumberFormatException | NullPointerException exception)
        {
            throw new IllegalStateException("laboratory parameter is missing or invalid", exception);
        }
    }
}
