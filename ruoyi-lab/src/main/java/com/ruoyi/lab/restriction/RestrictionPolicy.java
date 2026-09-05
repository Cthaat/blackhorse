package com.ruoyi.lab.restriction;

import java.time.LocalDateTime;
import com.ruoyi.lab.exception.LabBusinessException;
import com.ruoyi.lab.exception.LabErrorCode;

public final class RestrictionPolicy
{
    private RestrictionPolicy() { }

    public static boolean active(LocalDateTime start, LocalDateTime end,
            LocalDateTime revoked, LocalDateTime now)
    {
        return revoked == null && !start.isAfter(now) && end.isAfter(now);
    }

    public static String reason(String value)
    {
        if (value == null || value.isBlank() || value.length() > 1000)
            throw invalid("原因必须为1至1000字");
        return value.trim();
    }

    public static int days(Integer value, int maximum)
    {
        if (value == null || value < 1 || value > maximum) throw invalid("限制天数必须为1至" + maximum);
        return value;
    }

    public static LabBusinessException invalid(String message)
    {
        return new LabBusinessException(LabErrorCode.VALIDATION_ERROR, message);
    }
}
