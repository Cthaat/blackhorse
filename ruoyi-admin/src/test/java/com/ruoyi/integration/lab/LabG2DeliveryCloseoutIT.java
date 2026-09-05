package com.ruoyi.integration.lab;

import java.time.LocalDateTime;
import com.ruoyi.RuoYiApplication;
import com.ruoyi.common.core.redis.RedisCache;
import com.ruoyi.framework.web.service.TokenService;
import com.ruoyi.lab.dto.NotificationCommand;
import com.ruoyi.lab.mapper.*;
import com.ruoyi.lab.service.*;
import com.ruoyi.lab.task.TaskActorContext;
import com.ruoyi.quartz.service.ISysJobService;
import com.ruoyi.web.service.BusinessTaskRuntime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(classes=RuoYiApplication.class,properties={"spring.quartz.auto-startup=false","lab.demo-data.enabled=false"})
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named="LAB_TEST_WRAPPER_ACTIVE",matches="true")
class LabG2DeliveryCloseoutIT
{
    @Autowired JdbcTemplate jdbc;
    @Autowired LabStatusHistoryMapper history;
    @Autowired LabMessageDeliveryMapper mapper;
    @Autowired LabHazardMapper hazards;
    @Autowired LabInspectionTaskMapper inspections;
    @Autowired MessageDeliveryStore store;
    @Autowired MessageDeliveryEngine engine;
    @Autowired TaskActorContext actors;
    @MockitoBean BusinessTaskRuntime runtime;
    @MockitoBean RedisCache redis;
    @MockitoBean TokenService tokens;
    @MockitoBean LabIdempotencyStore idempotency;
    @MockitoBean ISysJobService jobs;

    @Test void mixedCollationHistoryCompensationKeepsItsExactDedupeIdentity()
    {
        assertThat(jdbc.queryForObject("select database()",String.class)).startsWith("lab_test_");
        jdbc.execute("alter table lab_message_delivery modify dedupe_key varchar(128) character set utf8mb4 collate utf8mb4_0900_ai_ci not null");
        jdbc.update("insert into lab_laboratory(id,lab_code,name,dept_id,manager_id,location) values(97901,'G2-COLLATION','排序规则验证',100,1,'验证楼')");
        jdbc.update("insert into lab_device(id,asset_no,laboratory_id,name,category_code,risk_level,location,manager_id) values(97901,'G2-COLLATION',97901,'设备','MICROSCOPE','LOW','验证楼',1)");
        jdbc.update("insert into lab_reservation(id,reservation_no,device_id,applicant_id,start_time,end_time,purpose,status) values(97901,'G2-COLLATION',97901,1,now(),date_add(now(),interval 1 hour),'验证','APPROVED')");
        jdbc.update("insert into lab_status_history(id,object_type,object_id,to_status,operator_id,reason,trace_id) values(97901,'RESERVATION',97901,'APPROVED',1,'验证','g2-collation')");
        assertThat(history.selectNotificationCandidateIds(1000)).contains(97901L);
        store.register(new NotificationCommand("history:97901:RESERVATION_APPROVED:1",1L,"RESERVATION_APPROVED","验证","验证","RESERVATION",97901L));
        assertThat(history.selectNotificationCandidateIds(1000)).doesNotContain(97901L);
        assertThatCode(() -> mapper.missingWaitlists(10)).doesNotThrowAnyException();
        assertThatCode(() -> hazards.selectUnreconciledOverdue(10)).doesNotThrowAnyException();
        assertThatCode(() -> inspections.selectUnreconciledOverdue(10)).doesNotThrowAnyException();
    }

    @Test void earlyRetryRequiresPermissionAndPreservesAttemptsUntilWorkerActuallySends()
    {
        assertThat(jdbc.queryForObject("select database()",String.class)).startsWith("lab_test_");
        jdbc.update("insert into lab_status_history(id,object_type,object_id,to_status,operator_id,reason,trace_id) values(97902,'DEVICE',97902,'AVAILABLE',1,'验证','g2-early-retry')");
        long id=store.register(new NotificationCommand("history:97902:DEVICE_AVAILABLE:1",1L,"DEVICE_AVAILABLE","验证","原始快照","DEVICE",97902L));
        jdbc.update("update lab_message_delivery set status='RETRY_WAIT',attempt_count=2,next_retry_at=date_add(now(),interval 30 day) where id=?",id);
        actors.asCurrentActor(1L,() -> {store.retryNow(id,"确认恢复，提前重试",1L);return null;});
        var pending=mapper.byId(id);
        assertThat(pending.status).isEqualTo("PENDING");assertThat(pending.attemptCount).isEqualTo(2);
        assertThat(pending.contentSnapshot).isEqualTo("原始快照");
        assertThat(mapper.attempts(id)).anySatisfy(a -> {assertThat(a.action()).isEqualTo("RETRY_NOW");assertThat(a.attemptNumber()).isEqualTo(2);});
        engine.retryDue(LocalDateTime.now().plusSeconds(1),100);
        assertThat(mapper.byId(id).status).isEqualTo("DELIVERED");
        assertThat(mapper.byId(id).attemptCount).isEqualTo(3);
    }
}
