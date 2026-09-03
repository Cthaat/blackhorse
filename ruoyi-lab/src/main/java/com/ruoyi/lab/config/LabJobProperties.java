package com.ruoyi.lab.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/** Bounded batch settings shared by laboratory lifecycle jobs. */
@Validated
@Component
@ConfigurationProperties(prefix = "lab.jobs")
public class LabJobProperties
{
    @Min(1)
    @Max(1000)
    private int batchSize = 200;

    public int getBatchSize()
    {
        return batchSize;
    }

    public void setBatchSize(int batchSize)
    {
        this.batchSize = batchSize;
    }
}
