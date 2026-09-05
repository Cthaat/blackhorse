package com.ruoyi.lab.mapper;

import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.*;

/** Fixed, aggregate operations queries. No receiver or business payload leaves this boundary. */
public interface OperationsMapper
{
    @Select("SELECT status,COUNT(*) AS count FROM lab_message_delivery GROUP BY status ORDER BY status")
    @Options(timeout=2) List<StateCount> deliveries();
    @Select("SELECT MIN(create_time) FROM lab_message_delivery WHERE status IN ('PENDING','PROCESSING','RETRY_WAIT')")
    @Options(timeout=2) LocalDateTime oldestDelivery();
    @Select("SELECT id FROM lab_message_delivery WHERE status IN ('PENDING','PROCESSING','RETRY_WAIT') ORDER BY create_time,id LIMIT 1")
    @Options(timeout=2) Long oldestDeliveryId();
    @Select("SELECT id FROM lab_message_delivery WHERE status='MANUAL_REQUIRED' ORDER BY update_time,id LIMIT 1")
    @Options(timeout=2) Long failedDeliveryId();
    @Select("SELECT id FROM lab_business_task WHERE status IN ('QUEUED','RUNNING','CANCELLING') ORDER BY created_at,id LIMIT 1")
    @Options(timeout=2) Long oldestTaskId();
    @Select("SELECT id FROM lab_business_task WHERE status='FAILED' ORDER BY finished_at,id LIMIT 1")
    @Options(timeout=2) Long failedTaskId();
    @Select("SELECT status,COUNT(*) AS count FROM lab_business_task GROUP BY status ORDER BY status")
    @Options(timeout=2) List<StateCount> tasks();
    @Select("SELECT COUNT(*) AS completedCount,AVG(TIMESTAMPDIFF(MICROSECOND,started_at,finished_at)/1000.0) AS averageMillis FROM lab_business_task WHERE finished_at>=#{since} AND started_at IS NOT NULL AND status IN ('SUCCEEDED','PARTIAL')")
    @Options(timeout=2) DurationSummary taskDuration(LocalDateTime since);
    record StateCount(String status,Long count) { }
    record DurationSummary(Long completedCount,Double averageMillis) { }
}
