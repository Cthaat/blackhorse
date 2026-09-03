package com.ruoyi.lab.mapper;

import java.time.LocalDateTime;
import java.util.List;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ruoyi.lab.domain.LabUsageRecord;
import com.ruoyi.lab.domain.ReturnCondition;
import com.ruoyi.lab.dto.UsageQueryDto;
import com.ruoyi.lab.security.LabDataScope;
import com.ruoyi.lab.vo.UsageRecordDetailVo;
import com.ruoyi.lab.vo.UsageRecordVo;
import org.apache.ibatis.annotations.Param;

/** Persistence, locking and scoped queries for equipment usage records. */
public interface LabUsageRecordMapper extends BaseMapper<LabUsageRecord>
{
    LabUsageRecord selectActiveById(@Param("usageId") Long usageId);

    LabUsageRecord selectByReservationId(@Param("reservationId") Long reservationId);

    LabUsageRecord selectOpenUsageForUpdate(@Param("usageId") Long usageId);

    int closeConditionally(@Param("usageId") Long usageId,
            @Param("returnedAt") LocalDateTime returnedAt,
            @Param("operatorId") Long operatorId,
            @Param("condition") ReturnCondition condition,
            @Param("note") String note,
            @Param("overdueMinutes") Integer overdueMinutes,
            @Param("operatorName") String operatorName);

    int linkRepairOrderConditionally(@Param("usageId") Long usageId,
            @Param("repairOrderId") Long repairOrderId,
            @Param("operatorName") String operatorName,
            @Param("now") LocalDateTime now);

    List<Long> selectUnreturnedIdsByDeviceIdForUpdate(@Param("deviceId") Long deviceId);

    List<UsageRecordVo> selectScopedList(@Param("query") UsageQueryDto query,
            @Param("currentUserId") Long currentUserId,
            @Param("studentOnly") boolean studentOnly,
            @Param("scope") LabDataScope scope);

    UsageRecordDetailVo selectScopedDetail(@Param("usageId") Long usageId,
            @Param("currentUserId") Long currentUserId,
            @Param("studentOnly") boolean studentOnly,
            @Param("scope") LabDataScope scope);
}
