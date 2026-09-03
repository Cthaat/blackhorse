package com.ruoyi.lab.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ruoyi.lab.domain.LabLaboratory;
import org.apache.ibatis.annotations.Param;

/**
 * Persistence operations for laboratories.
 */
public interface LabLaboratoryMapper extends BaseMapper<LabLaboratory>
{
    LabLaboratory selectByIdForUpdate(@Param("laboratoryId") Long laboratoryId);
}
