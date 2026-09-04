package com.ruoyi.lab.mapper;

import java.util.List;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ruoyi.lab.domain.LabStatusHistory;
import com.ruoyi.lab.vo.StatusHistoryVo;
import org.apache.ibatis.annotations.Param;

/**
 * Persistence operations for business status history.
 */
public interface LabStatusHistoryMapper extends BaseMapper<LabStatusHistory>
{
    LabStatusHistory selectActiveById(@Param("historyId") Long historyId);

    List<Long> selectNotificationCandidateIds(@Param("limit") int limit);

    List<StatusHistoryVo> selectByObject(@Param("objectType") String objectType,
            @Param("objectId") Long objectId);
}
