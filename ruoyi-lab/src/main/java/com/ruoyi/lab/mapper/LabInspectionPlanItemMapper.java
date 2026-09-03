package com.ruoyi.lab.mapper;

import java.time.LocalDateTime;
import java.util.List;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ruoyi.lab.domain.LabInspectionPlanItem;
import org.apache.ibatis.annotations.Param;

public interface LabInspectionPlanItemMapper extends BaseMapper<LabInspectionPlanItem>
{
    List<LabInspectionPlanItem> selectByPlan(@Param("planId") Long planId);
    List<LabInspectionPlanItem> selectEnabledByPlan(@Param("planId") Long planId);
    int countEnabledByPlan(@Param("planId") Long planId);
    int retireByPlan(@Param("planId") Long planId, @Param("updateTime") LocalDateTime updateTime);
}
