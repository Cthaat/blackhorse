package com.ruoyi.lab.mapper;

import java.time.LocalDateTime;
import java.util.List;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ruoyi.lab.domain.LabReservationWaitlist;
import com.ruoyi.lab.vo.OccupiedRangeVo;
import org.apache.ibatis.annotations.*;

public interface LabReservationWaitlistMapper extends BaseMapper<LabReservationWaitlist>
{
    @Select("SELECT * FROM lab_reservation_waitlist WHERE applicant_id=#{userId} AND device_id=#{deviceId} AND start_time=#{start} AND end_time=#{end} AND status IN ('WAITING','OFFERED') LIMIT 1")
    LabReservationWaitlist existing(@Param("userId") Long userId, @Param("deviceId") Long deviceId,
            @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Select("SELECT * FROM lab_reservation_waitlist WHERE id=#{id} FOR UPDATE")
    LabReservationWaitlist locked(@Param("id") Long id);

    @Select("SELECT * FROM lab_reservation_waitlist WHERE applicant_id=#{userId} AND idempotency_key=#{key}")
    LabReservationWaitlist byKey(@Param("userId") Long userId, @Param("key") String key);

    @Select("SELECT * FROM lab_reservation_waitlist WHERE device_id=#{deviceId} AND status IN ('WAITING','OFFERED') ORDER BY create_time,id LIMIT 200 FOR UPDATE")
    List<LabReservationWaitlist> queue(@Param("deviceId") Long deviceId);

    @Select("SELECT COUNT(*) FROM lab_reservation_waitlist WHERE device_id=#{deviceId} AND status IN ('WAITING','OFFERED')")
    int activeCount(@Param("deviceId") Long deviceId);

    @Select("<script>SELECT * FROM lab_reservation_waitlist WHERE applicant_id=#{userId}<if test='deviceId != null'> AND device_id=#{deviceId}</if><if test='status != null'> AND status=#{status}</if> ORDER BY create_time DESC,id DESC</script>")
    List<LabReservationWaitlist> mine(@Param("userId") Long userId, @Param("deviceId") Long deviceId, @Param("status") String status);

    @Select("SELECT COUNT(*) FROM lab_reservation_waitlist WHERE device_id=#{row.deviceId} AND status IN ('WAITING','OFFERED') AND start_time<#{row.endTime} AND end_time>#{row.startTime} AND (create_time<#{row.createTime} OR (create_time=#{row.createTime} AND id<=#{row.id}))")
    int position(@Param("row") LabReservationWaitlist row);

    @Select("SELECT COUNT(*) FROM lab_reservation_waitlist WHERE device_id=#{deviceId} AND status='OFFERED' AND offered_until>#{now} AND start_time<#{end} AND end_time>#{start} AND (#{excludeId} IS NULL OR id<>#{excludeId})")
    int holds(@Param("deviceId") Long deviceId, @Param("start") LocalDateTime start, @Param("end") LocalDateTime end,
            @Param("now") LocalDateTime now, @Param("excludeId") Long excludeId);

    @Select("SELECT start_time,end_time,'WAITLIST_HOLD' AS reservation_status FROM lab_reservation_waitlist WHERE device_id=#{deviceId} AND status='OFFERED' AND offered_until>#{now} AND start_time<#{to} AND end_time>#{from} ORDER BY start_time,id")
    List<OccupiedRangeVo> occupied(@Param("deviceId") Long deviceId, @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to, @Param("now") LocalDateTime now);

    @Select("SELECT device_id FROM lab_reservation_waitlist WHERE status IN ('WAITING','OFFERED') GROUP BY device_id ORDER BY MIN(update_time),device_id LIMIT 100")
    List<Long> dueDevices();

    @Update("UPDATE lab_reservation_waitlist SET status=#{status},reason=#{reason},offered_until=COALESCE(#{until},offered_until),reservation_id=#{reservationId},version=version+1,update_time=#{now} WHERE id=#{id} AND version=#{version}")
    int transition(@Param("id") Long id, @Param("version") int version, @Param("status") String status,
            @Param("reason") String reason, @Param("until") LocalDateTime until,
            @Param("reservationId") Long reservationId, @Param("now") LocalDateTime now);

    @Update("UPDATE lab_reservation_waitlist SET update_time=#{now} WHERE device_id=#{deviceId} AND status IN ('WAITING','OFFERED')")
    int touched(@Param("deviceId") Long deviceId, @Param("now") LocalDateTime now);
}
