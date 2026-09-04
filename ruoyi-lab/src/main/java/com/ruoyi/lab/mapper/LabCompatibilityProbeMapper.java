package com.ruoyi.lab.mapper;

import java.util.List;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ruoyi.lab.domain.LabCompatibilityProbe;
import org.apache.ibatis.annotations.Select;

/**
 * Mapper used to verify MyBatis-Plus CRUD and PageHelper coexistence.
 */
public interface LabCompatibilityProbeMapper extends BaseMapper<LabCompatibilityProbe>
{
    @Select("SELECT id, probe_name AS probeName, sort_order AS sortOrder "
            + "FROM lab_compatibility_probe ORDER BY sort_order ASC")
    List<LabCompatibilityProbe> selectOrdered();
}
