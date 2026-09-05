package com.ruoyi.lab.mapper;

import java.time.LocalDateTime;
import java.util.List;
import com.ruoyi.lab.sla.*;
import org.apache.ibatis.annotations.*;

public interface LabSlaMapper
{
    // Unlike system-admin scope shortcuts, this predicate requires a current business role.
    String MANAGE="EXISTS(SELECT 1 FROM lab_laboratory l JOIN sys_user u ON u.user_id=#{userId} JOIN sys_user_role ur ON ur.user_id=u.user_id JOIN sys_role role ON role.role_id=ur.role_id WHERE l.id=r.laboratory_id AND l.del_flag='0' AND u.status='0' AND u.del_flag='0' AND role.status='0' AND role.del_flag='0' AND (role.role_key='lab_manager' OR (r.business_type='HAZARD' AND role.role_key='lab_safety_officer')) AND (role.data_scope='1' OR (role.data_scope='2' AND EXISTS(SELECT 1 FROM sys_role_dept rd WHERE rd.role_id=role.role_id AND rd.dept_id=l.dept_id)) OR (role.data_scope='3' AND l.dept_id=u.dept_id) OR (role.data_scope='4' AND EXISTS(SELECT 1 FROM sys_dept dep WHERE dep.dept_id=l.dept_id AND dep.status='0' AND dep.del_flag='0' AND (dep.dept_id=u.dept_id OR FIND_IN_SET(u.dept_id,dep.ancestors)>0))) OR (role.data_scope='5' AND l.manager_id=u.user_id)))";
    String STATE="CASE WHEN r.closed_at IS NOT NULL THEN 'CLOSED' WHEN (r.responded_at IS NULL AND r.response_due_at&lt;=#{now}) OR (r.completed_at IS NULL AND r.processing_due_at&lt;=COALESCE(r.paused_at,#{now})) THEN 'OVERDUE' WHEN r.paused_at IS NOT NULL THEN 'PAUSED' WHEN (r.responded_at IS NULL AND TIMESTAMPDIFF(SECOND,#{now},r.response_due_at)&lt;=r.response_hours*720) OR (r.completed_at IS NULL AND TIMESTAMPDIFF(SECOND,#{now},r.processing_due_at)&lt;=r.processing_hours*720) THEN 'NEAR_DUE' ELSE 'OPEN' END";
    String REPAIR_LINK="(SELECT c.repair_id FROM lab_maintenance_cycle c WHERE r.object_type='MAINTENANCE_CYCLE' AND c.id=r.object_id) AS repairId";
    @Select("SELECT r.*,u.nick_name AS ownerName,"+REPAIR_LINK+" FROM lab_sla_record r LEFT JOIN sys_user u ON u.user_id=r.owner_id WHERE r.id=#{id}") SlaRecord byId(Long id);
    @Select("SELECT * FROM lab_sla_record WHERE id=#{id} FOR UPDATE") SlaRecord locked(Long id);
    @Select("SELECT * FROM lab_sla_record WHERE object_type=#{type} AND object_id=#{id} FOR UPDATE")
    SlaRecord objectLocked(@Param("type") String type,@Param("id") Long id);
    @Select("<script>SELECT r.*,u.nick_name AS ownerName,"+REPAIR_LINK+","+STATE+" AS state FROM lab_sla_record r LEFT JOIN sys_user u ON u.user_id=r.owner_id WHERE (r.owner_id=#{userId} OR "+MANAGE+") <if test='mine'>AND r.owner_id=#{userId}</if><if test='type != null'> AND r.business_type=#{type}</if><if test='state != null'> AND ("+STATE+")=#{state}</if> ORDER BY r.id DESC</script>")
    List<SlaRecord> list(@Param("userId") Long userId,@Param("mine") boolean mine,@Param("type") String type,@Param("state") String state,@Param("now") LocalDateTime now);
    @Select("SELECT "+MANAGE+" FROM (SELECT #{lab} AS laboratory_id,#{type} AS business_type) r")
    boolean canManage(@Param("userId") Long userId,@Param("lab") Long laboratoryId,@Param("type") String type);
    @Select("SELECT COUNT(*) FROM sys_user u JOIN sys_user_role ur ON ur.user_id=u.user_id JOIN sys_role r ON r.role_id=ur.role_id WHERE u.user_id=#{userId} AND u.status='0' AND u.del_flag='0' AND r.status='0' AND r.del_flag='0' AND r.role_key LIKE 'lab\\_%'") int activeBusinessUser(Long userId);
    @Select("SELECT * FROM lab_sla_rule WHERE laboratory_id=#{lab} AND business_type=#{type} AND risk=#{risk} ORDER BY builtin ASC,id DESC LIMIT 1")
    SlaRule activeRule(@Param("lab") Long lab,@Param("type") String type,@Param("risk") String risk);
    @Select("SELECT * FROM lab_sla_rule r WHERE laboratory_id=#{lab} AND "+MANAGE+" ORDER BY id DESC LIMIT 100")
    List<SlaRule> rules(@Param("lab") Long lab,@Param("userId") Long userId);
    @Insert("INSERT INTO lab_sla_rule(laboratory_id,business_type,risk,response_hours,processing_hours,reason,created_by,created_at,builtin) VALUES(#{laboratoryId},#{businessType},#{risk},#{responseHours},#{processingHours},#{reason},#{createdBy},#{createdAt},#{builtin}) ON DUPLICATE KEY UPDATE id=LAST_INSERT_ID(id)")
    @Options(useGeneratedKeys=true,keyProperty="id") int insertRule(SlaRule rule);
    @Insert("INSERT INTO lab_sla_record(object_type,object_id,business_type,risk,laboratory_id,device_id,owner_id,title,rule_version_id,response_hours,processing_hours,opened_at,response_due_at,processing_due_at) VALUES(#{objectType},#{objectId},#{businessType},#{risk},#{laboratoryId},#{deviceId},#{ownerId},#{title},#{ruleVersionId},#{responseHours},#{processingHours},#{openedAt},#{responseDueAt},#{processingDueAt})")
    @Options(useGeneratedKeys=true,keyProperty="id") int insert(SlaRecord record);
    @Update("UPDATE lab_sla_record SET owner_id=#{ownerId},responded_at=#{respondedAt},started_at=#{startedAt},completed_at=#{completedAt},closed_at=#{closedAt},paused_at=#{pausedAt},pause_reason=#{pauseReason},total_paused_seconds=#{totalPausedSeconds},processing_due_at=#{processingDueAt},version=version+1 WHERE id=#{id} AND version=#{version}") int update(SlaRecord record);
    @Insert("INSERT INTO lab_sla_trace(record_id,action,reason,operator_id,created_at) VALUES(#{id},#{action},#{reason},#{actor},#{now})")
    int trace(@Param("id") Long id,@Param("action") String action,@Param("reason") String reason,@Param("actor") Long actor,@Param("now") LocalDateTime now);
    @Select("SELECT id,action,reason,operator_id AS operatorId,created_at AS createdAt FROM lab_sla_trace WHERE record_id=#{id} ORDER BY id DESC LIMIT 100") List<SlaTrace> traces(Long id);
    @Select("SELECT * FROM lab_sla_alert WHERE record_id=#{id} ORDER BY id DESC LIMIT 100") List<SlaAlert> alerts(Long id);
    @Select("SELECT id FROM lab_sla_record WHERE closed_at IS NULL AND (responded_at IS NULL OR completed_at IS NULL) ORDER BY last_checked_at,id LIMIT #{limit}") List<Long> candidates(int limit);
    @Update("UPDATE lab_sla_record SET last_checked_at=#{now} WHERE id=#{id}") int checked(@Param("id") Long id,@Param("now") LocalDateTime now);
    @Insert("INSERT INTO lab_sla_alert(record_id,phase,stage,created_at) VALUES(#{recordId},#{phase},#{stage},#{createdAt}) ON DUPLICATE KEY UPDATE id=LAST_INSERT_ID(id)")
    @Options(useGeneratedKeys=true,keyProperty="id") int alert(SlaAlert alert);
    @Select("SELECT COUNT(*) FROM lab_sla_alert WHERE record_id=#{id} AND phase=#{phase} AND stage=#{stage}") int hasAlert(@Param("id") Long id,@Param("phase") String phase,@Param("stage") String stage);
    @Insert("INSERT INTO lab_sla_notice(alert_id,record_id,receiver_id,title,content) VALUES(#{alertId},#{recordId},#{receiverId},#{title},#{content}) ON DUPLICATE KEY UPDATE id=LAST_INSERT_ID(id)")
    @Options(useGeneratedKeys=true,keyProperty="id") int notice(SlaNotice notice);
    @Select("SELECT * FROM lab_sla_notice WHERE id=#{id}") SlaNotice noticeById(Long id);
    @Select("SELECT n.* FROM lab_sla_notice n WHERE NOT EXISTS(SELECT 1 FROM lab_message_delivery d WHERE d.dedupe_key=CAST(CONCAT('SLA:',n.id,':NOTICE') AS BINARY)) ORDER BY n.id LIMIT #{limit}") List<SlaNotice> missingNotices(int limit);
    @Select("SELECT 'REPAIR_ORDER' AS objectType,r.id AS objectId,'REPAIR' AS businessType,d.risk_level AS risk,d.laboratory_id AS laboratoryId,d.id AS deviceId,COALESCE(r.assignee_id,d.manager_id) AS ownerId,CONCAT('维修 ',r.repair_no) AS title FROM lab_repair_order r JOIN lab_device d ON d.id=r.device_id WHERE r.id=#{id} AND r.del_flag='0' AND r.source_type IN('ACTIVE_REPORT','ABNORMAL_RETURN')") SlaRecord repairMeta(Long id);
    @Select("SELECT 'MAINTENANCE_CYCLE' AS objectType,c.id AS objectId,'MAINTENANCE' AS businessType,d.risk_level AS risk,d.laboratory_id AS laboratoryId,d.id AS deviceId,COALESCE(repair.assignee_id,c.responsible_id) AS ownerId,CONCAT('维护周期 #',c.id) AS title FROM lab_maintenance_cycle c JOIN lab_device d ON d.id=c.device_id LEFT JOIN lab_repair_order repair ON repair.id=c.repair_id WHERE c.id=#{id}") SlaRecord maintenanceMeta(Long id);
    @Select("SELECT 'HAZARD' AS objectType,h.id AS objectId,'HAZARD' AS businessType,h.severity AS risk,CASE WHEN h.target_type='DEVICE' THEN d.laboratory_id ELSE h.target_id END AS laboratoryId,d.id AS deviceId,h.owner_id AS ownerId,CONCAT('隐患 ',h.hazard_no) AS title FROM lab_hazard h LEFT JOIN lab_device d ON h.target_type='DEVICE' AND d.id=h.target_id WHERE h.id=#{id} AND h.del_flag='0'") SlaRecord hazardMeta(Long id);
    @Select("SELECT source_id FROM lab_repair_order WHERE id=#{id} AND source_type IN('MAINTENANCE','CALIBRATION') AND del_flag='0'") Long repairCycle(Long id);
}
