package com.ruoyi.lab.mapper;

import java.time.LocalDateTime;
import java.util.List;
import com.ruoyi.lab.restriction.*;
import com.ruoyi.lab.security.LabDataScope;
import org.apache.ibatis.annotations.*;

public interface LabRestrictionMapper
{
    String COLUMNS = "r.*,l.name AS laboratoryName,u.nick_name AS userName,CASE WHEN r.revoked_at IS NOT NULL THEN 'REVOKED' WHEN r.ends_at>#{now} THEN 'ACTIVE' ELSE 'EXPIRED' END AS status";
    String JOINS = " FROM lab_restriction r LEFT JOIN lab_laboratory l ON l.id=r.laboratory_id LEFT JOIN sys_user u ON u.user_id=r.user_id ";

    @Select("SELECT enabled_at FROM lab_restriction_gate WHERE id=1 FOR UPDATE")
    LocalDateTime lockGate();
    @Insert("INSERT INTO lab_restriction_user_lock(user_id) VALUES(#{userId}) ON DUPLICATE KEY UPDATE user_id=user_id")
    int ensureUser(Long userId);
    @Select("SELECT user_id FROM lab_restriction_user_lock WHERE user_id=#{userId} FOR UPDATE")
    Long lockUser(Long userId);
    @Select("SELECT DISTINCT applicant_id FROM lab_reservation_waitlist WHERE device_id=#{deviceId} AND status IN ('WAITING','OFFERED') ORDER BY applicant_id")
    List<Long> deviceUsers(Long deviceId);
    @Select("SELECT COUNT(*) FROM lab_restriction WHERE user_id=#{userId} AND laboratory_id=#{laboratoryId} AND revoked_at IS NULL AND starts_at<=#{now} AND ends_at>#{now}")
    int activeCount(@Param("userId") Long userId, @Param("laboratoryId") Long laboratoryId, @Param("now") LocalDateTime now);
    @Select("SELECT " + COLUMNS + JOINS + " WHERE r.id=#{id}")
    RestrictionRecord byId(@Param("id") Long id, @Param("now") LocalDateTime now);
    @Select("SELECT * FROM lab_restriction WHERE id=#{id} FOR UPDATE")
    RestrictionRecord locked(Long id);
    @Select("<script>SELECT " + COLUMNS + JOINS + " WHERE 1=1 "
            + "<if test='userId != null'>AND r.user_id=#{userId}</if>"
            + "<if test='laboratoryId != null'>AND r.laboratory_id=#{laboratoryId}</if>"
            + "<if test='scope != null and !scope.allLaboratories'>AND r.laboratory_id IN <foreach collection='scope.laboratoryIds' item='lab' open='(' separator=',' close=')'>#{lab}</foreach></if>"
            + "<if test='status == &quot;ACTIVE&quot;'>AND r.revoked_at IS NULL AND r.ends_at>#{now}</if>"
            + "<if test='status == &quot;EXPIRED&quot;'>AND r.revoked_at IS NULL AND r.ends_at&lt;=#{now}</if>"
            + "<if test='status == &quot;REVOKED&quot;'>AND r.revoked_at IS NOT NULL</if> ORDER BY r.id DESC</script>")
    List<RestrictionRecord> list(@Param("userId") Long userId, @Param("laboratoryId") Long laboratoryId,
            @Param("scope") LabDataScope scope, @Param("status") String status, @Param("now") LocalDateTime now);
    @Insert("INSERT INTO lab_restriction(laboratory_id,user_id,source,source_reservation_id,reason,starts_at,ends_at,rule_version_id,rule_snapshot,created_by,created_at) VALUES(#{laboratoryId},#{userId},#{source},#{sourceReservationId},#{reason},#{startsAt},#{endsAt},#{ruleVersionId},#{ruleSnapshot},#{createdBy},#{createdAt})")
    @Options(useGeneratedKeys=true, keyProperty="id")
    int insert(RestrictionRecord row);
    @Select("SELECT * FROM lab_restriction WHERE source='NO_SHOW' AND source_reservation_id=#{id}")
    RestrictionRecord noShow(Long id);
    @Update("UPDATE lab_restriction SET revoked_at=#{now},revoked_by=#{actor},revoke_reason=#{reason} WHERE id=#{id} AND revoked_at IS NULL")
    int revoke(@Param("id") Long id, @Param("actor") Long actor, @Param("reason") String reason, @Param("now") LocalDateTime now);
    @Select("SELECT * FROM lab_restriction_rule WHERE laboratory_id=#{labId} ORDER BY id DESC LIMIT 100")
    List<RestrictionRule> rules(Long labId);
    @Select("SELECT * FROM lab_restriction_rule WHERE laboratory_id=#{labId} ORDER BY id DESC LIMIT 1")
    RestrictionRule activeRule(Long labId);
    @Insert("INSERT INTO lab_restriction_rule(laboratory_id,days,reason,created_by,created_at) VALUES(#{laboratoryId},#{days},#{reason},#{createdBy},#{createdAt})")
    @Options(useGeneratedKeys=true,keyProperty="id")
    int publish(RestrictionRule row);
    @Select("SELECT * FROM lab_restriction_appeal WHERE restriction_id=#{id}")
    RestrictionAppeal appeal(Long id);
    @Select("SELECT * FROM lab_restriction_appeal WHERE restriction_id=#{id} FOR UPDATE")
    RestrictionAppeal appealLocked(Long id);
    @Insert("INSERT INTO lab_restriction_appeal(restriction_id,reason,status,created_at) VALUES(#{restrictionId},#{reason},'PENDING',#{createdAt})")
    @Options(useGeneratedKeys=true,keyProperty="id")
    int insertAppeal(RestrictionAppeal row);
    @Insert("INSERT INTO lab_restriction_evidence(appeal_id,attachment_id) VALUES(#{appealId},#{attachmentId})")
    int evidence(@Param("appealId") Long appealId, @Param("attachmentId") Long attachmentId);
    @Select("SELECT attachment_id FROM lab_restriction_evidence WHERE appeal_id=#{id} ORDER BY attachment_id")
    List<Long> evidenceIds(Long id);
    @Update("UPDATE lab_restriction_appeal SET status=#{status},reviewer_id=#{actor},review_reason=#{reason},reviewed_at=#{now} WHERE restriction_id=#{id} AND status='PENDING'")
    int review(@Param("id") Long id, @Param("status") String status, @Param("actor") Long actor,
            @Param("reason") String reason, @Param("now") LocalDateTime now);
}
