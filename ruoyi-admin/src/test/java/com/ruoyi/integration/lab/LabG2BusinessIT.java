package com.ruoyi.integration.lab;

import java.time.LocalDateTime;
import java.util.*;
import com.ruoyi.RuoYiApplication;
import com.ruoyi.common.core.redis.RedisCache;
import com.ruoyi.framework.web.service.TokenService;
import com.ruoyi.lab.dto.NotificationCommand;
import com.ruoyi.lab.mapper.*;
import com.ruoyi.lab.service.*;
import com.ruoyi.lab.task.*;
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

/** Committed transactions in a fresh native test database: no demo reset and no external sends. */
@SpringBootTest(classes=RuoYiApplication.class,properties={"spring.quartz.auto-startup=false","lab.demo-data.enabled=false"})
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named="LAB_TEST_WRAPPER_ACTIVE",matches="true")
class LabG2BusinessIT
{
    @Autowired JdbcTemplate jdbc;
    @Autowired BusinessTaskService tasks;
    @Autowired BusinessTaskWorker worker;
    @Autowired BusinessTaskMapper mapper;
    @Autowired TaskActorContext actors;
    @Autowired TaskWorkbook workbook;
    @Autowired MessageDeliveryStore deliveries;
    @Autowired MessageDeliveryEngine engine;
    @Autowired LabNotificationPersistenceService inbox;
    @Autowired LabMessageDeliveryMapper deliveryMapper;
    @MockitoBean BusinessTaskRuntime runtime;
    @MockitoBean RedisCache redis;
    @MockitoBean TokenService tokens;
    @MockitoBean LabIdempotencyStore idempotency;
    @MockitoBean ISysJobService jobs;

    @Test void nativeTaskImportExportRecoveryAndRevocation() throws Exception
    {
        assertThat(jdbc.queryForObject("select database()",String.class)).startsWith("lab_test_");
        seedUser(97001,"g2_it_manager","lab_manager");seedUser(97002,"g2_it_student","lab_student");
        jdbc.update("insert into lab_laboratory(id,lab_code,name,dept_id,manager_id,location) values(97001,'G2-IT','导入验证实验室',100,97001,'验证楼')");
        byte[] bytes=workbook.write(TaskWorkbook.columns("DEVICE"),List.of(
                List.of("G2-NEW","97001","新设备","MICROSCOPE","型号","LOW","验证楼","97001",""),
                List.of("G2-BAD","97001","错误设备","INVALID","型号","LOW","验证楼","97001","")),null);
        var pre=actors.asCurrentActor(97001,()->tasks.precheck("DEVICE",bytes));
        assertThat(pre.totalCount()).isEqualTo(2);assertThat(pre.failureCount()).isEqualTo(1);
        assertThat(pre.errorAvailable()).isTrue();
        actors.asCurrentActor(97001,()->tasks.submit(pre.id()));run(pre.id());
        var done=mapper.get(pre.id());assertThat(done.status).isEqualTo("PARTIAL");assertThat(done.successCount).isEqualTo(1);
        assertThat(mapper.artifacts(pre.id())).hasSize(4); // Input, precheck errors, result, final errors all retained for expiry cleanup.
        assertThat(jdbc.queryForObject("select count(*) from lab_device where asset_no='G2-NEW'",Integer.class)).isEqualTo(1);
        // A committed row remains successful after lease recovery; replaying the task must not insert it again.
        jdbc.update("update lab_business_task set status='RUNNING',lease_until=date_sub(now(),interval 1 minute) where id=?",pre.id());
        assertThat(mapper.recover()).isEqualTo(1);run(pre.id());
        assertThat(jdbc.queryForObject("select count(*) from lab_device where asset_no='G2-NEW'",Integer.class)).isEqualTo(1);
        var export=actors.asCurrentActor(97001,()->tasks.export("DEVICE",Map.of("keyword","G2-NEW")));run(export.id());
        assertThat(mapper.get(export.id()).status).isEqualTo("SUCCEEDED");
        assertThat(actors.asCurrentActor(97001,()->tasks.download(export.id(),false))).isNotEmpty();
        assertThatThrownBy(()->actors.asCurrentActor(97002,()->tasks.download(export.id(),false))).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        jdbc.update("update lab_laboratory set dept_id=101 where id=97001");
        assertThatThrownBy(()->actors.asCurrentActor(97001,()->tasks.download(export.id(),false))).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        jdbc.update("update lab_laboratory set dept_id=100 where id=97001");
        var cancelled=actors.asCurrentActor(97001,()->tasks.export("DEVICE",Map.of()));
        actors.asCurrentActor(97001,()->tasks.cancel(cancelled.id()));
        assertThat(mapper.get(cancelled.id()).status).isEqualTo("CANCELLED");
        assertThat(mapper.claim(cancelled.id(),UUID.randomUUID().toString())).isZero();
    }

    @Test void deliveryRegistrationAndLostAcknowledgementUseRealTransactions()
    {
        seedUser(97003,"g2_it_recipient","lab_student");
        jdbc.update("insert into lab_status_history(id,object_type,object_id,to_status,operator_id,reason,trace_id) values(97003,'DEVICE',97003,'AVAILABLE',1,'验证通知','00000000-0000-0000-0000-000000000003')");
        var command=new NotificationCommand("history:97003:AVAILABLE:97003",97003L,"DEVICE_AVAILABLE","设备通知","设备状态已更新","DEVICE",97003L);
        long id=deliveries.register(command);assertThat(deliveries.register(command)).isEqualTo(id);
        var claimed=deliveries.claim(id,LocalDateTime.now());assertThat(claimed).isNotNull();
        inbox.insertSent(command);
        jdbc.update("update lab_message_delivery set lease_until=date_sub(now(),interval 1 minute) where id=?",id);
        deliveries.recover(LocalDateTime.now(),100);
        assertThat(deliveryMapper.byId(id).status).isEqualTo("DELIVERED");
        engine.registerAndDeliver(command);
        assertThat(jdbc.queryForObject("select count(*) from lab_notification where dedupe_key=?",Integer.class,command.dedupeKey())).isEqualTo(1);
    }
    private void run(long id){String token=UUID.randomUUID().toString();assertThat(mapper.claim(id,token)).isEqualTo(1);worker.run(id,token);}
    private void seedUser(long id,String name,String role)
    {
        jdbc.update("insert into sys_user(user_id,dept_id,user_name,nick_name,status,del_flag) values(?,100,?,?,'0','0')",id,name,name);
        jdbc.update("insert into sys_user_role(user_id,role_id) select ?,role_id from sys_role where role_key=?",id,role);
    }
}
