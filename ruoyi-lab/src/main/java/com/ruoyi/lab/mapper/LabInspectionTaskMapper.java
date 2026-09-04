package com.ruoyi.lab.mapper;

import java.time.LocalDateTime;
import java.util.List;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ruoyi.lab.domain.InspectionTaskStatus;
import com.ruoyi.lab.domain.LabInspectionTask;
import com.ruoyi.lab.security.LabDataScope;
import org.apache.ibatis.annotations.Param;

public interface LabInspectionTaskMapper extends BaseMapper<LabInspectionTask>
{
    LabInspectionTask selectActiveById(@Param("taskId") Long taskId);
    LabInspectionTask selectForUpdate(@Param("taskId") Long taskId);
    List<LabInspectionTask> selectListByScope(@Param("scope") LabDataScope scope,
            @Param("assigneeId") Long assigneeId, @Param("status") InspectionTaskStatus status);
    int existsByPlanAndSchedule(@Param("planId") Long planId,
            @Param("scheduledAt") LocalDateTime scheduledAt);
    int startConditionally(@Param("taskId") Long taskId, @Param("expectedVersion") Integer version,
            @Param("startedAt") LocalDateTime startedAt, @Param("updateBy") String updateBy);
    int completeConditionally(@Param("taskId") Long taskId, @Param("expectedVersion") Integer version,
            @Param("completedAt") LocalDateTime completedAt, @Param("updateBy") String updateBy);
    List<LabInspectionTask> selectOverdueCandidates(@Param("now") LocalDateTime now,
            @Param("limit") int limit);
    int markOneOverdue(@Param("taskId") Long taskId,
            @Param("expectedVersion") Integer expectedVersion,
            @Param("now") LocalDateTime now, @Param("updateBy") String updateBy);
    List<LabInspectionTask> selectUnreconciledOverdue(@Param("limit") int limit);
}
