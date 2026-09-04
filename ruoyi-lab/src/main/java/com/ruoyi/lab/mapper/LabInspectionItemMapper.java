package com.ruoyi.lab.mapper;

import java.time.LocalDateTime;
import java.util.List;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ruoyi.lab.domain.LabInspectionItem;
import org.apache.ibatis.annotations.Param;

public interface LabInspectionItemMapper extends BaseMapper<LabInspectionItem>
{
    LabInspectionItem selectForUpdate(@Param("itemId") Long itemId);
    List<LabInspectionItem> selectByTask(@Param("taskId") Long taskId);
    List<LabInspectionItem> selectByTaskForUpdate(@Param("taskId") Long taskId);
    int recordConditionally(@Param("item") LabInspectionItem item,
            @Param("expectedVersion") Integer expectedVersion,
            @Param("inspectedAt") LocalDateTime inspectedAt);
}
