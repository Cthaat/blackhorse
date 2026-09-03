package com.ruoyi.lab.mapper;

import java.time.LocalDateTime;
import java.util.List;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ruoyi.lab.domain.LabReservation;
import com.ruoyi.lab.security.LabDataScope;
import com.ruoyi.lab.service.LabSortWhitelist.SortClause;
import com.ruoyi.lab.vo.OccupiedRangeVo;
import org.apache.ibatis.annotations.Param;

/** Persistence and locking operations for reservations. */
public interface LabReservationMapper extends BaseMapper<LabReservation>
{
    LabReservation selectActiveById(@Param("reservationId") Long reservationId);

    LabReservation selectByIdForUpdate(@Param("reservationId") Long reservationId);

    LabReservation selectByApplicantAndIdempotencyKey(@Param("applicantId") Long applicantId,
            @Param("idempotencyKey") String idempotencyKey);

    LabReservation selectByApplicantAndIdempotencyKeyForUpdate(
            @Param("applicantId") Long applicantId,
            @Param("idempotencyKey") String idempotencyKey);

    int clearExpiredIdempotency(@Param("reservationId") Long reservationId,
            @Param("applicantId") Long applicantId,
            @Param("idempotencyKey") String idempotencyKey,
            @Param("now") LocalDateTime now);

    int countActiveOverlaps(@Param("deviceId") Long deviceId,
            @Param("newStart") LocalDateTime newStart, @Param("newEnd") LocalDateTime newEnd,
            @Param("excludeReservationId") Long excludeReservationId);

    int updateStatusConditionally(@Param("reservationId") Long reservationId,
            @Param("expected") String expected, @Param("target") String target);

    int updateDecisionConditionally(@Param("reservationId") Long reservationId,
            @Param("expected") String expected, @Param("target") String target,
            @Param("expectedVersion") Integer expectedVersion, @Param("operatorId") Long operatorId,
            @Param("decisionTime") LocalDateTime decisionTime, @Param("reason") String reason);

    int updateCancellationConditionally(@Param("reservationId") Long reservationId,
            @Param("expected") String expected, @Param("expectedVersion") Integer expectedVersion,
            @Param("cancelTime") LocalDateTime cancelTime, @Param("reason") String reason);

    List<LabReservation> selectMine(@Param("applicantId") Long applicantId,
            @Param("status") String status, @Param("sort") SortClause sort);

    List<LabReservation> selectListByScope(@Param("scope") LabDataScope scope,
            @Param("applicantId") Long applicantId, @Param("deviceId") Long deviceId,
            @Param("status") String status, @Param("reservationNo") String reservationNo,
            @Param("from") LocalDateTime from, @Param("to") LocalDateTime to,
            @Param("sort") SortClause sort);

    List<OccupiedRangeVo> selectOccupiedRanges(@Param("deviceId") Long deviceId,
            @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);
}
