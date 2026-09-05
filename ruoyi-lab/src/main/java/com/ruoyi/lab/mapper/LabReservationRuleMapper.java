package com.ruoyi.lab.mapper;

import java.time.LocalDateTime;
import java.util.List;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ruoyi.lab.domain.LabReservationRule;
import com.ruoyi.lab.domain.LabReservation;
import org.apache.ibatis.annotations.*;

public interface LabReservationRuleMapper extends BaseMapper<LabReservationRule>
{
    @Select("SELECT * FROM lab_reservation_rule WHERE id=#{id} FOR UPDATE")
    LabReservationRule locked(@Param("id") Long id);

    @Select("SELECT * FROM lab_reservation_rule WHERE device_id=#{deviceId} AND status='PUBLISHED' LIMIT 1 FOR UPDATE")
    LabReservationRule activeLocked(@Param("deviceId") Long deviceId);

    @Select("SELECT * FROM lab_reservation_rule WHERE device_id=#{deviceId} ORDER BY version_number DESC")
    List<LabReservationRule> history(@Param("deviceId") Long deviceId);

    @Select("SELECT * FROM lab_reservation_rule WHERE device_id=#{deviceId} AND status='PUBLISHED' LIMIT 1")
    LabReservationRule active(@Param("deviceId") Long deviceId);

    @Select("SELECT COALESCE(MAX(version_number),0)+1 FROM lab_reservation_rule WHERE device_id=#{deviceId}")
    int nextVersion(@Param("deviceId") Long deviceId);

    @Update("UPDATE lab_reservation_rule SET definition_json=#{json}, revision=revision+1 WHERE id=#{id} AND status='DRAFT' AND revision=#{revision}")
    int edit(@Param("id") Long id, @Param("revision") int revision, @Param("json") String json);

    @Update("UPDATE lab_reservation_rule SET status='RETIRED', revision=revision+1 WHERE device_id=#{deviceId} AND status='PUBLISHED'")
    int retireActive(@Param("deviceId") Long deviceId);

    @Update("UPDATE lab_reservation_rule SET status='PUBLISHED', revision=revision+1, published_by=#{actor}, published_at=#{now} WHERE id=#{id} AND status='DRAFT' AND revision=#{revision}")
    int publish(@Param("id") Long id, @Param("revision") int revision, @Param("actor") Long actor, @Param("now") LocalDateTime now);

    @Select("SELECT * FROM lab_reservation WHERE device_id=#{deviceId} AND del_flag='0' AND status IN ('PENDING','APPROVED') AND end_time>#{now} ORDER BY start_time,id")
    List<LabReservation> futureReservations(@Param("deviceId") Long deviceId, @Param("now") LocalDateTime now);
}
