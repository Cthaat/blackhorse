package com.ruoyi.lab.vo;

/** A stable status/count pair returned by database aggregates. */
public class LabMetricVo
{
    private String code;
    private long value;

    public LabMetricVo()
    {
    }

    public LabMetricVo(String code, long value)
    {
        this.code = code;
        this.value = value;
    }

    public String getCode()
    {
        return code;
    }

    public void setCode(String code)
    {
        this.code = code;
    }

    public long getValue()
    {
        return value;
    }

    public void setValue(long value)
    {
        this.value = value;
    }
}
