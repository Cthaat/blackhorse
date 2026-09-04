package com.ruoyi.lab.dto;

import java.time.OffsetDateTime;
import org.springframework.format.annotation.DateTimeFormat;

/** Optional reporting window; both values must be supplied together. */
public class DashboardQueryDto
{
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private OffsetDateTime startTime;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private OffsetDateTime endTime;

    public OffsetDateTime getStartTime()
    {
        return startTime;
    }

    public void setStartTime(OffsetDateTime startTime)
    {
        this.startTime = startTime;
    }

    public OffsetDateTime getEndTime()
    {
        return endTime;
    }

    public void setEndTime(OffsetDateTime endTime)
    {
        this.endTime = endTime;
    }
}
