package com.ruoyi.lab.mapper;

import java.time.LocalDateTime;
import java.util.List;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ruoyi.lab.domain.LabRepairOrder;
import com.ruoyi.lab.dto.RepairQueryDto;
import com.ruoyi.lab.security.LabDataScope;
import com.ruoyi.lab.vo.RepairOrderVo;
import org.apache.ibatis.annotations.Param;

/** Persistence and device-first locking operations for repair orders. */
public interface LabRepairOrderMapper extends BaseMapper<LabRepairOrder>
{
    LabRepairOrder selectActiveById(@Param("orderId") Long orderId);

    LabRepairOrder selectByIdForUpdate(@Param("orderId") Long orderId);

    LabRepairOrder selectOpenByDeviceIdForUpdate(@Param("deviceId") Long deviceId);

    List<Long> selectOpenIdsByDeviceIdForUpdate(@Param("deviceId") Long deviceId);

    int assignConditionally(@Param("orderId") Long orderId,
            @Param("expected") String expected, @Param("target") String target,
            @Param("assigneeId") Long assigneeId, @Param("managerId") Long managerId,
            @Param("now") LocalDateTime now, @Param("operatorName") String operatorName);

    int startConditionally(@Param("orderId") Long orderId,
            @Param("expected") String expected, @Param("target") String target,
            @Param("assigneeId") Long assigneeId, @Param("now") LocalDateTime now,
            @Param("operatorName") String operatorName);

    int submitResultConditionally(@Param("orderId") Long orderId,
            @Param("expected") String expected, @Param("target") String target,
            @Param("assigneeId") Long assigneeId, @Param("result") String result,
            @Param("now") LocalDateTime now, @Param("operatorName") String operatorName);

    int saveAcceptanceConditionally(@Param("orderId") Long orderId,
            @Param("expected") String expected, @Param("target") String target,
            @Param("acceptanceResult") String acceptanceResult,
            @Param("reason") String reason, @Param("managerId") Long managerId,
            @Param("now") LocalDateTime now, @Param("operatorName") String operatorName);

    int countActiveUserRole(@Param("userId") Long userId, @Param("roleKey") String roleKey);

    List<RepairOrderVo> selectScopedList(@Param("query") RepairQueryDto query,
            @Param("currentUserId") Long currentUserId, @Param("scope") LabDataScope scope);

    RepairOrderVo selectScopedDetail(@Param("orderId") Long orderId,
            @Param("currentUserId") Long currentUserId, @Param("scope") LabDataScope scope);
}
