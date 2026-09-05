package com.ruoyi.lab.mapper;

import java.time.LocalDateTime;
import java.util.List;
import com.ruoyi.lab.maintenance.*;
import com.ruoyi.lab.security.LabDataScope;
import org.apache.ibatis.annotations.*;

public interface LabMaintenanceMapper
{
    String PLAN="p.*,v.kind,v.period_days,v.first_due_at,v.responsible_id,v.description,d.name AS deviceName,d.asset_no AS assetNo,d.laboratory_id AS laboratoryId";
    String PLAN_FROM=" FROM lab_maintenance_plan p LEFT JOIN lab_maintenance_version v ON v.id=p.current_version_id JOIN lab_device d ON d.id=p.device_id ";
    String SCOPE="<choose><when test='scope != null and scope.allLaboratories'></when><when test='scope != null and !scope.laboratoryIds.isEmpty()'> AND d.laboratory_id IN <foreach collection='scope.laboratoryIds' item='lab' open='(' separator=',' close=')'>#{lab}</foreach></when><otherwise> AND 1=0</otherwise></choose>";
    @Select("SELECT "+PLAN+PLAN_FROM+" WHERE p.id=#{id}") MaintenancePlan plan(Long id);
    @Select("SELECT "+PLAN+PLAN_FROM+" WHERE p.id=#{id} FOR UPDATE") MaintenancePlan planLocked(Long id);
    @Select("<script>SELECT "+PLAN+PLAN_FROM+" WHERE d.del_flag='0' "+SCOPE
            +"<if test='deviceId != null'> AND p.device_id=#{deviceId}</if><if test='enabled != null'> AND p.enabled=#{enabled}</if>"
            +"<if test='due == &quot;OVERDUE&quot;'> AND p.next_due_at&lt;#{now}</if><if test='due == &quot;SOON&quot;'> AND p.next_due_at>=#{now} AND p.next_due_at&lt;=#{soon}</if> ORDER BY p.next_due_at,p.id</script>")
    List<MaintenancePlan> plans(@Param("scope") LabDataScope scope,@Param("deviceId") Long deviceId,@Param("enabled") Boolean enabled,
            @Param("due") String due,@Param("now") LocalDateTime now,@Param("soon") LocalDateTime soon);
    @Insert("INSERT INTO lab_maintenance_plan(device_id,enabled,next_due_at,version,created_by,created_at) VALUES(#{deviceId},1,#{nextDueAt},0,#{createdBy},#{createdAt})")
    @Options(useGeneratedKeys=true,keyProperty="id") int insertPlan(MaintenancePlan row);
    @Insert("INSERT INTO lab_maintenance_version(plan_id,kind,period_days,first_due_at,responsible_id,description,reason,created_by,created_at) VALUES(#{planId},#{kind},#{periodDays},#{firstDueAt},#{responsibleId},#{description},#{reason},#{createdBy},#{createdAt})")
    @Options(useGeneratedKeys=true,keyProperty="id") int insertVersion(MaintenanceVersion row);
    @Update("UPDATE lab_maintenance_plan SET current_version_id=#{versionId},next_due_at=#{next},version=version+1 WHERE id=#{id} AND version=#{expected}")
    int activate(@Param("id") Long id,@Param("versionId") Long versionId,@Param("next") LocalDateTime next,@Param("expected") int expected);
    @Update("UPDATE lab_maintenance_plan SET enabled=#{enabled},version=version+1 WHERE id=#{id} AND version=#{expected}")
    int toggle(@Param("id") Long id,@Param("enabled") boolean enabled,@Param("expected") int expected);
    @Select("SELECT * FROM lab_maintenance_version WHERE plan_id=#{id} ORDER BY id DESC LIMIT 100") List<MaintenanceVersion> versions(Long id);
    @Select("SELECT * FROM lab_maintenance_version WHERE id=#{id}") MaintenanceVersion version(Long id);
    @Select("SELECT p.id FROM lab_maintenance_plan p WHERE p.enabled=1 AND p.next_due_at<=#{now} AND NOT EXISTS(SELECT 1 FROM lab_maintenance_cycle c WHERE c.plan_id=p.id AND c.status<>'COMPLETED') ORDER BY p.next_due_at,p.id LIMIT #{limit}")
    List<Long> due(@Param("now") LocalDateTime now,@Param("limit") int limit);
    @Select("SELECT * FROM lab_maintenance_cycle WHERE id=#{id}") MaintenanceCycle cycle(Long id);
    @Select("SELECT * FROM lab_maintenance_cycle WHERE id=#{id} FOR UPDATE") MaintenanceCycle cycleLocked(Long id);
    @Select("SELECT * FROM lab_maintenance_cycle WHERE plan_id=#{id} AND status<>'COMPLETED' LIMIT 1") MaintenanceCycle openCycle(Long id);
    @Select("SELECT COUNT(*) FROM lab_maintenance_cycle WHERE plan_id=#{id} AND due_at=#{due}")
    int countCycleDue(@Param("id") Long id,@Param("due") LocalDateTime due);
    @Select("SELECT * FROM lab_maintenance_cycle WHERE plan_id=#{id} ORDER BY due_at DESC,id DESC LIMIT 100") List<MaintenanceCycle> planCycles(Long id);
    @Select("<script>SELECT c.*,d.name AS deviceName FROM lab_maintenance_cycle c JOIN lab_device d ON d.id=c.device_id WHERE d.del_flag='0' "+SCOPE
            +"<if test='deviceId != null'> AND c.device_id=#{deviceId}</if><if test='status != null'> AND c.status=#{status}</if> ORDER BY c.due_at,c.id</script>")
    List<MaintenanceCycle> cycles(@Param("scope") LabDataScope scope,@Param("deviceId") Long deviceId,@Param("status") String status);
    @Insert("INSERT INTO lab_maintenance_cycle(plan_id,plan_version_id,device_id,kind,period_days,responsible_id,due_at,status,version,created_at) VALUES(#{planId},#{planVersionId},#{deviceId},#{kind},#{periodDays},#{responsibleId},#{dueAt},'PLANNED',0,#{createdAt})")
    @Options(useGeneratedKeys=true,keyProperty="id") int insertCycle(MaintenanceCycle row);
    @Update("UPDATE lab_maintenance_cycle SET window_start=#{start},window_end=#{end},status='SCHEDULED',version=version+1 WHERE id=#{id} AND version=#{expected} AND status IN('PLANNED','SCHEDULED')")
    int schedule(@Param("id") Long id,@Param("start") LocalDateTime start,@Param("end") LocalDateTime end,@Param("expected") int expected);
    @Update("UPDATE lab_maintenance_cycle SET repair_id=#{repairId},status='STARTED',version=version+1 WHERE id=#{id} AND status='SCHEDULED' AND version=#{expected}")
    int start(@Param("id") Long id,@Param("repairId") Long repairId,@Param("expected") int expected);
    @Update("UPDATE lab_maintenance_cycle SET status='COMPLETED',completed_at=#{now},report_attachment_id=#{reportId},version=version+1 WHERE id=#{id} AND status='STARTED'")
    int complete(@Param("id") Long id,@Param("now") LocalDateTime now,@Param("reportId") Long reportId);
    @Update("UPDATE lab_maintenance_plan SET next_due_at=#{next},version=version+1 WHERE id=#{id}")
    int next(@Param("id") Long id,@Param("next") LocalDateTime next);
    @Select("SELECT COUNT(*) FROM lab_maintenance_cycle WHERE device_id=#{deviceId} AND status IN('SCHEDULED','STARTED') AND window_start<#{end} AND window_end>#{start}")
    int overlaps(@Param("deviceId") Long deviceId,@Param("start") LocalDateTime start,@Param("end") LocalDateTime end);
    @Select("SELECT window_start AS startTime,window_end AS endTime,'MAINTENANCE_WINDOW' AS reservationStatus FROM lab_maintenance_cycle WHERE device_id=#{deviceId} AND status IN('SCHEDULED','STARTED') AND window_start<#{end} AND window_end>#{start} ORDER BY window_start LIMIT 1000")
    List<com.ruoyi.lab.vo.OccupiedRangeVo> occupied(@Param("deviceId") Long deviceId,@Param("start") LocalDateTime start,@Param("end") LocalDateTime end);
    @Select("SELECT COUNT(*) FROM lab_maintenance_cycle WHERE device_id=#{deviceId} AND (status='STARTED' OR (status='SCHEDULED' AND window_start<=#{now} AND window_end>#{now}))")
    int blocksNow(@Param("deviceId") Long deviceId,@Param("now") LocalDateTime now);
    @Select("SELECT 'RESERVATION' AS kind,id,start_time AS startTime,end_time AS endTime FROM lab_reservation WHERE device_id=#{deviceId} AND del_flag='0' AND status IN('PENDING','APPROVED','CHECKED_OUT') AND start_time<#{end} AND end_time>#{start} "
            +"UNION ALL SELECT 'WAITLIST',id,start_time,end_time FROM lab_reservation_waitlist WHERE device_id=#{deviceId} AND status='OFFERED' AND offered_until>#{now} AND start_time<#{end} AND end_time>#{start} "
            +"UNION ALL SELECT 'MAINTENANCE',id,window_start,window_end FROM lab_maintenance_cycle WHERE device_id=#{deviceId} AND id<>#{id} AND status IN('SCHEDULED','STARTED') AND window_start<#{end} AND window_end>#{start} ORDER BY startTime,id LIMIT 100")
    List<MaintenanceConflict> conflicts(@Param("deviceId") Long deviceId,@Param("id") Long id,
            @Param("start") LocalDateTime start,@Param("end") LocalDateTime end,@Param("now") LocalDateTime now);
}
