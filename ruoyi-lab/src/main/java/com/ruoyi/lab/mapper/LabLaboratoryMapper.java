package com.ruoyi.lab.mapper;

import java.util.List;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ruoyi.lab.domain.LabLaboratory;
import com.ruoyi.lab.domain.LaboratoryStatus;
import com.ruoyi.lab.security.LabDataScope;
import com.ruoyi.lab.service.LabSortWhitelist.SortClause;
import org.apache.ibatis.annotations.Param;

/**
 * Persistence operations for laboratories.
 */
public interface LabLaboratoryMapper extends BaseMapper<LabLaboratory>
{
    List<LabLaboratory> selectListByScope(@Param("scope") LabDataScope scope,
            @Param("status") LaboratoryStatus status, @Param("keyword") String keyword,
            @Param("sort") SortClause sort);

    LabLaboratory selectByIdInScope(@Param("laboratoryId") Long laboratoryId,
            @Param("scope") LabDataScope scope);

    LabLaboratory selectByIdForUpdate(@Param("laboratoryId") Long laboratoryId);

    int updateDetailsConditionally(@Param("laboratory") LabLaboratory laboratory,
            @Param("expectedVersion") Integer expectedVersion);
}
