package com.ruoyi.lab.mapper;

import java.time.LocalDateTime;
import java.util.List;
import com.ruoyi.lab.security.LabDataScope;
import com.ruoyi.lab.vo.ReservationTraceVo.Node;
import com.ruoyi.lab.vo.StatusHistoryVo;
import org.apache.ibatis.annotations.Param;

/** Bounded trace reads; callers authorize each object before loading evidence. */
public interface LabReservationTraceMapper
{
    List<StatusHistoryVo> history(@Param("reservationId") Long reservationId,
            @Param("repairId") Long repairId, @Param("limit") int limit);
    List<Node> notifications(@Param("reservationId") Long reservationId,
            @Param("usageId") Long usageId, @Param("repairId") Long repairId,
            @Param("receiverId") Long receiverId, @Param("limit") int limit);
    List<Node> hazards(@Param("deviceId") Long deviceId, @Param("viewerId") Long viewerId,
            @Param("scope") LabDataScope scope, @Param("limit") int limit);
    Integer qualificationCount(@Param("deviceId") Long deviceId,
            @Param("applicantId") Long applicantId, @Param("viewerId") Long viewerId,
            @Param("scope") LabDataScope scope, @Param("at") LocalDateTime at);
}
