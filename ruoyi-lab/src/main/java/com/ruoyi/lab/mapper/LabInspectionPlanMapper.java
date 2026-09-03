package com.ruoyi.lab.mapper;

import java.time.LocalDateTime;
import java.util.List;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ruoyi.lab.domain.InspectionPlanStatus;
import com.ruoyi.lab.domain.LabInspectionPlan;
import com.ruoyi.lab.security.LabDataScope;
import org.apache.ibatis.annotations.Param;

public interface LabInspectionPlanMapper extends BaseMapper<LabInspectionPlan>
{
    LabInspectionPlan selectActiveById(@Param("planId") Long planId);
    LabInspectionPlan selectForUpdate(@Param("planId") Long planId);
    List<LabInspectionPlan> selectListByScope(@Param("scope") LabDataScope scope,
            @Param("status") InspectionPlanStatus status, @Param("keyword") String keyword);
    List<LabInspectionPlan> selectDuePlansForUpdate(@Param("now") LocalDateTime now,
            @Param("batchSize") int batchSize);
    int updateDetailsConditionally(@Param("plan") LabInspectionPlan plan,
            @Param("expectedVersion") Integer expectedVersion);
    int updateStatusConditionally(@Param("planId") Long planId,
            @Param("expected") String expected, @Param("target") String target,
            @Param("updateBy") String updateBy, @Param("updateTime") LocalDateTime updateTime);
    int advanceNextRun(@Param("planId") Long planId,
            @Param("expectedNextRun") LocalDateTime expectedNextRun,
            @Param("nextRun") LocalDateTime nextRun, @Param("updateBy") String updateBy,
            @Param("updateTime") LocalDateTime updateTime);
}
