package com.ruoyi.lab.mapper;

import java.time.LocalDateTime;
import java.util.List;
import com.ruoyi.lab.domain.LabMessageDelivery;
import com.ruoyi.lab.domain.LabReservationWaitlist;
import com.ruoyi.lab.vo.MessageDeliveryVo;
import com.ruoyi.lab.vo.MessageAttemptVo;
import org.apache.ibatis.annotations.*;

public interface LabMessageDeliveryMapper
{
    String META = "id,event_type AS eventType,source_type AS sourceType,source_id AS sourceId,event_version AS eventVersion,template_version AS templateVersion,status,attempt_count AS attemptCount,next_retry_at AS nextRetryAt,lease_until AS leaseUntil,error_code AS errorCode,trace_id AS traceId,create_time AS createTime,update_time AS updateTime";
    String INTERNAL = META + ",dedupe_key AS dedupeKey,receiver_id AS receiverId,business_type AS businessType,business_id AS businessId,title_snapshot AS titleSnapshot,content_snapshot AS contentSnapshot,execution_version AS executionVersion";
    @Select("SELECT " + INTERNAL + " FROM lab_message_delivery WHERE dedupe_key=#{key}") LabMessageDelivery byKey(String key);
    @Select("SELECT " + INTERNAL + " FROM lab_message_delivery WHERE id=#{id}") LabMessageDelivery byId(Long id);
    @Select("SELECT " + INTERNAL + " FROM lab_message_delivery WHERE id=#{id} FOR UPDATE") LabMessageDelivery locked(Long id);
    @Select("SELECT COUNT(*) FROM lab_notification WHERE dedupe_key=#{key} AND delivery_status='SENT'") int inboxSent(String key);
    @Select("<script>SELECT " + META + " FROM lab_message_delivery WHERE 1=1 <if test='status != null and status != &quot;&quot;'>AND status=#{status}</if><if test='eventType != null and eventType != &quot;&quot;'>AND event_type=#{eventType}</if> ORDER BY id DESC</script>")
    List<MessageDeliveryVo> list(@Param("status") String status, @Param("eventType") String eventType);
    @Select("SELECT " + META + " FROM lab_message_delivery WHERE id=#{id}") MessageDeliveryVo metadata(Long id);
    @Insert("INSERT INTO lab_message_delivery(dedupe_key,receiver_id,event_type,source_type,source_id,event_version,business_type,business_id,template_version,title_snapshot,content_snapshot,status,attempt_count,execution_version,next_retry_at,error_code,trace_id,create_time,update_time) VALUES(#{dedupeKey},#{receiverId},#{eventType},#{sourceType},#{sourceId},#{eventVersion},#{businessType},#{businessId},#{templateVersion},#{titleSnapshot},#{contentSnapshot},#{status},0,0,#{nextRetryAt},#{errorCode},#{traceId},#{createTime},#{updateTime}) ON DUPLICATE KEY UPDATE id=id")
    int register(LabMessageDelivery delivery);
    @Select("SELECT id FROM lab_message_delivery WHERE status IN ('PENDING','RETRY_WAIT') AND next_retry_at<=#{now} AND attempt_count<5 ORDER BY next_retry_at,id LIMIT #{limit}")
    List<Long> due(@Param("now") LocalDateTime now, @Param("limit") int limit);
    @Update("UPDATE lab_message_delivery SET status='PROCESSING',attempt_count=attempt_count+1,execution_version=execution_version+1,lease_until=#{lease},update_time=#{now} WHERE id=#{id} AND status IN ('PENDING','RETRY_WAIT') AND next_retry_at<=#{now} AND attempt_count<5")
    int claim(@Param("id") Long id,@Param("now") LocalDateTime now,@Param("lease") LocalDateTime lease);
    @Update("UPDATE lab_message_delivery SET status=#{status},error_code=#{error},next_retry_at=#{next},lease_until=NULL,update_time=#{now} WHERE id=#{id} AND status='PROCESSING' AND execution_version=#{version}")
    int finish(@Param("id") Long id,@Param("version") int version,@Param("status") String status,@Param("error") String error,@Param("next") LocalDateTime next,@Param("now") LocalDateTime now);
    @Select("SELECT " + INTERNAL + " FROM lab_message_delivery WHERE status='PROCESSING' AND lease_until<=#{now} ORDER BY id LIMIT #{limit} FOR UPDATE")
    List<LabMessageDelivery> expired(@Param("now") LocalDateTime now,@Param("limit") int limit);
    @Insert("INSERT INTO lab_message_delivery_attempt(delivery_id,action,attempt_number,operator_id,reason,result,error_code,trace_id,create_time) VALUES(#{id},#{action},#{attempt},#{operator},#{reason},#{result},#{error},#{trace},#{now})")
    int audit(@Param("id") Long id,@Param("action") String action,@Param("attempt") int attempt,@Param("operator") Long operator,@Param("reason") String reason,@Param("result") String result,@Param("error") String error,@Param("trace") String trace,@Param("now") LocalDateTime now);
    @Select("SELECT id,action,attempt_number AS attemptNumber,operator_id AS operatorId,reason,result,error_code AS errorCode,trace_id AS traceId,create_time AS createTime FROM lab_message_delivery_attempt WHERE delivery_id=#{id} ORDER BY id DESC LIMIT 100")
    List<MessageAttemptVo> attempts(Long id);
    @Update("UPDATE lab_message_delivery SET status='PENDING',attempt_count=0,next_retry_at=#{now},error_code=NULL,execution_version=execution_version+1,update_time=#{now} WHERE id=#{id} AND status='MANUAL_REQUIRED'")
    int replay(@Param("id") Long id,@Param("now") LocalDateTime now);
    @Select("SELECT w.id,w.applicant_id AS applicantId,w.offered_until AS offeredUntil FROM lab_reservation_waitlist w WHERE w.offered_until IS NOT NULL AND NOT EXISTS(SELECT 1 FROM lab_message_delivery d WHERE d.dedupe_key=concat('WAITLIST:',w.id,':OFFERED')) AND NOT EXISTS(SELECT 1 FROM lab_notification n WHERE n.dedupe_key=concat('WAITLIST:',w.id,':OFFERED')) ORDER BY w.id LIMIT #{limit}")
    List<LabReservationWaitlist> missingWaitlists(int limit);
    @Select("SELECT count(*) FROM lab_status_history WHERE id=#{id} AND del_flag='0'") int historyExists(Long id);
    @Select("SELECT count(*) FROM lab_inspection_task WHERE id=#{id} AND overdue_event_version=#{version} AND del_flag='0'") int inspectionExists(@Param("id") Long id,@Param("version") Long version);
    @Select("SELECT count(*) FROM lab_hazard WHERE id=#{id} AND overdue_event_version=#{version} AND del_flag='0'") int hazardExists(@Param("id") Long id,@Param("version") Long version);
    @Select("SELECT count(*) FROM lab_reservation_waitlist WHERE id=#{id} AND offered_until IS NOT NULL") int waitlistExists(Long id);
}
