package com.ruoyi.lab.mapper;

import java.util.List;
import com.ruoyi.lab.task.BusinessTask;
import com.ruoyi.lab.task.BusinessTaskRow;
import org.apache.ibatis.annotations.*;

/** All user values are bound parameters; claims and checkpoints use row locks. */
public interface BusinessTaskMapper
{
    String COLUMNS = "id,owner_id as ownerId,kind,direction,status,scope_json as scopeJson,filter_json as filterJson,input_key as inputKey,result_key as resultKey,error_key as errorKey,parent_id as parentId,max_id as maxId,total_count as totalCount,success_count as successCount,failure_count as failureCount,cursor_id as cursorId,lease_token as leaseToken,lease_until as leaseUntil,error_code as errorCode,trace_id as traceId,created_at as createdAt,started_at as startedAt,finished_at as finishedAt,expires_at as expiresAt";
    String ROW_COLUMNS = "task_id as taskId,row_no as rowNo,payload_json as payloadJson,status,error_code as errorCode,object_id as objectId";
    @Select("select " + COLUMNS + " from lab_business_task where id=#{id}") BusinessTask get(long id);
    @Select("select " + COLUMNS + " from lab_business_task where id=#{id} for update") BusinessTask lock(long id);
    @Select("select id from lab_business_task_gate where id=1 for update") int gate();
    @Select("select count(*) from lab_business_task where status in ('PRECHECKED','QUEUED','RUNNING','CANCELLING') and expires_at>now(3)") int activeCount();
    @Select("select count(*) from lab_business_task where owner_id=#{owner} and status in ('PRECHECKED','QUEUED','RUNNING','CANCELLING') and expires_at>now(3)") int userActive(long owner);
    @Insert("""
        insert into lab_business_task(owner_id,kind,direction,status,scope_json,filter_json,input_key,error_key,parent_id,
        max_id,total_count,failure_count,trace_id,expires_at)
        values(#{ownerId},#{kind},#{direction},#{status},#{scopeJson},#{filterJson},#{inputKey},#{errorKey},#{parentId},
        #{maxId},#{totalCount},#{failureCount},#{traceId},#{expiresAt})
        """)
    @Options(useGeneratedKeys=true,keyProperty="id") int insert(BusinessTask task);
    @Select("select " + COLUMNS + " from lab_business_task where owner_id=#{owner} order by id desc limit #{limit} offset #{offset}")
    List<BusinessTask> list(@Param("owner") long owner,@Param("offset") int offset,@Param("limit") int limit);
    @Select("select count(*) from lab_business_task where owner_id=#{owner}") long count(long owner);
    @Update("update lab_business_task set status=#{status},error_code=#{errorCode},result_key=#{resultKey},error_key=#{errorKey},success_count=#{successCount},failure_count=#{failureCount},cursor_id=#{cursorId},finished_at=#{finishedAt},lease_until=#{leaseUntil} where id=#{id}")
    int save(BusinessTask task);
    @Update("update lab_business_task set status='RUNNING',lease_token=#{token},lease_until=date_add(now(3), interval 60 second),started_at=coalesce(started_at,now(3)) where id=#{id} and status='QUEUED'")
    int claim(@Param("id") long id,@Param("token") String token);
    @Update("update lab_business_task set lease_until=date_add(now(3), interval 60 second) where id=#{id} and lease_token=#{token} and status in ('RUNNING','CANCELLING') and lease_until>now(3)")
    int heartbeat(@Param("id") long id,@Param("token") String token);
    @Select("select count(*) from lab_business_task where id=#{id} and lease_token=#{token} and lease_until>now(3) and status in ('RUNNING','CANCELLING')")
    int owned(@Param("id") long id,@Param("token") String token);
    @Select("select count(*) from lab_device where asset_no=#{value} and del_flag='0'") int deviceExists(String value);
    @Select("select count(*) from lab_laboratory where lab_code=#{value} and del_flag='0'") int laboratoryExists(String value);
    @Update("update lab_business_task set total_count=#{total} where id=#{id}") int updateTotal(@Param("id")long id,@Param("total")int total);
    @Update("update lab_business_task set status=case when status='CANCELLING' then 'CANCELLED' else 'QUEUED' end,lease_token=null,lease_until=null where status in ('RUNNING','CANCELLING') and lease_until<now(3)") int recover();
    @Select("select id from lab_business_task where status='QUEUED' order by id limit 2") List<Long> queued();
    @Insert("insert into lab_business_task_row(task_id,row_no,payload_json,status,error_code,object_id) values(#{taskId},#{rowNo},#{payloadJson},#{status},#{errorCode},#{objectId})") int insertRow(BusinessTaskRow row);
    @Select("select " + ROW_COLUMNS + " from lab_business_task_row where task_id=#{taskId} order by row_no limit #{limit} offset #{offset}")
    List<BusinessTaskRow> rows(@Param("taskId") long taskId,@Param("offset") int offset,@Param("limit") int limit);
    @Select("select " + ROW_COLUMNS + " from lab_business_task_row where task_id=#{taskId} and status='READY' order by row_no limit 100") List<BusinessTaskRow> pending(long taskId);
    @Update("update lab_business_task_row set status=#{status},error_code=#{errorCode},object_id=#{objectId},payload_json=#{payloadJson} where task_id=#{taskId} and row_no=#{rowNo} and status='READY'") int finishRow(BusinessTaskRow row);
    @Update("update lab_business_task_row set status='READY',error_code=null where task_id=#{id}") int resetExport(long id);
    @Update("update lab_business_task_row set payload_json='{}' where task_id=#{id}") int purgePayload(long id);
    @Insert("insert into lab_business_task_audit(task_id,actor_id,action) values(#{id},#{actor},#{action})")
    int audit(@Param("id") long id,@Param("actor") long actor,@Param("action") String action);
    @Select("select id from lab_business_task where expires_at<now(3) and retention_cleaned=0 and status not in ('RUNNING','CANCELLING','QUEUED') limit 20") List<Long> expired();
    @Update("update lab_business_task set input_key=null,result_key=null,error_key=null,scope_json='{}',filter_json='{}',retention_cleaned=1 where id=#{id}") int clearFiles(long id);
    @Insert("insert into lab_business_task_artifact(task_id,storage_key) values(#{id},#{key})") int artifact(@Param("id")long id,@Param("key")String key);
    @Select("select storage_key from lab_business_task_artifact where task_id=#{id}") List<String> artifacts(long id);
    @Delete("delete from lab_business_task_artifact where task_id=#{id}") int clearArtifacts(long id);
}
