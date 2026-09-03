package com.ruoyi.lab.mapper;

import org.apache.ibatis.annotations.Param;

/** Read-only lookup against enabled RuoYi dictionary types and values. */
public interface LabDictionaryMapper
{
    int countEnabledValue(@Param("dictType") String dictType,
            @Param("dictValue") String dictValue);
}
