package com.ruoyi.lab.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;

/**
 * Read-only access to RuoYi data-scope facts without depending on system-domain types.
 */
public interface LabDataScopeMapper
{
    boolean hasAllLaboratoryScope(@Param("userId") Long userId);

    List<Long> selectScopedLaboratoryIds(@Param("userId") Long userId);
}
