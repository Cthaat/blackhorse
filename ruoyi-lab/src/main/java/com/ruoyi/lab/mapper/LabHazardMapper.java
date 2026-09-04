package com.ruoyi.lab.mapper;

import java.time.LocalDateTime;
import java.util.List;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ruoyi.lab.domain.HazardSeverity;
import com.ruoyi.lab.domain.HazardStatus;
import com.ruoyi.lab.domain.LabHazard;
import com.ruoyi.lab.security.LabDataScope;
import org.apache.ibatis.annotations.Param;

public interface LabHazardMapper extends BaseMapper<LabHazard>
{
    LabHazard selectActiveById(@Param("hazardId") Long hazardId);
    LabHazard selectForUpdate(@Param("hazardId") Long hazardId);
    LabHazard selectBySourceItem(@Param("sourceItemId") Long sourceItemId);
    List<LabHazard> selectListByScope(@Param("scope") LabDataScope scope,
            @Param("viewerId") Long viewerId, @Param("ownerId") Long ownerId,
            @Param("status") HazardStatus status, @Param("severity") HazardSeverity severity);
    List<Long> selectDeviceIdsByLaboratory(@Param("laboratoryId") Long laboratoryId);
    List<Long> selectOpenMajorHazardIdsForDeviceForUpdate(@Param("deviceId") Long deviceId);
    int updateStatusConditionally(@Param("hazardId") Long hazardId,
            @Param("expected") String expected, @Param("target") String target,
            @Param("updateBy") String updateBy, @Param("updateTime") LocalDateTime updateTime);
    List<LabHazard> selectOverdueCandidates(@Param("now") LocalDateTime now,
            @Param("limit") int limit);
    int markOneOverdue(@Param("hazardId") Long hazardId,
            @Param("expectedVersion") Integer expectedVersion,
            @Param("now") LocalDateTime now, @Param("updateBy") String updateBy);
    List<LabHazard> selectUnreconciledOverdue(@Param("limit") int limit);
    int countOpenUsageForDevice(@Param("deviceId") Long deviceId);
    int countOpenRepairForDevice(@Param("deviceId") Long deviceId);
}
