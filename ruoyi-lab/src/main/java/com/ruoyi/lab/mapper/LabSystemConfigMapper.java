package com.ruoyi.lab.mapper;

import org.apache.ibatis.annotations.Param;

/** Read-only access to whitelisted laboratory policy parameters. */
public interface LabSystemConfigMapper
{
    String selectValueByKey(@Param("configKey") String configKey);
}
