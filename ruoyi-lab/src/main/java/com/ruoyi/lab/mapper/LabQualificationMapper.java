package com.ruoyi.lab.mapper;

import java.time.LocalDateTime;
import java.util.List;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ruoyi.lab.domain.LabQualification;
import com.ruoyi.lab.domain.QualificationScopeType;
import com.ruoyi.lab.security.LabDataScope;
import com.ruoyi.lab.service.LabSortWhitelist.SortClause;
import org.apache.ibatis.annotations.Param;

/**
 * Persistence operations for qualifications.
 */
public interface LabQualificationMapper extends BaseMapper<LabQualification>
{
    List<LabQualification> selectMine(@Param("userId") Long userId,
            @Param("sort") SortClause sort);

    List<LabQualification> selectListByScope(@Param("scope") LabDataScope scope,
            @Param("userId") Long userId, @Param("scopeType") QualificationScopeType scopeType,
            @Param("sort") SortClause sort);

    LabQualification selectByIdForUpdate(@Param("qualificationId") Long qualificationId);

    int updateDetailsConditionally(@Param("qualification") LabQualification qualification,
            @Param("expectedVersion") Integer expectedVersion);

    int revokeConditionally(@Param("qualificationId") Long qualificationId,
            @Param("expectedVersion") Integer expectedVersion, @Param("revokedAt") LocalDateTime revokedAt,
            @Param("revokeReason") String revokeReason, @Param("updateBy") String updateBy);

    int countValidForDevice(@Param("userId") Long userId, @Param("deviceId") Long deviceId,
            @Param("at") LocalDateTime at);
}
