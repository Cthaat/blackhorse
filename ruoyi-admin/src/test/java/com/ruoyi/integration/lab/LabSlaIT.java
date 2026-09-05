package com.ruoyi.integration.lab;

import com.ruoyi.RuoYiApplication;
import com.ruoyi.common.core.redis.RedisCache;
import com.ruoyi.framework.web.service.TokenService;
import com.ruoyi.lab.dto.*;
import com.ruoyi.lab.service.*;
import com.ruoyi.lab.sla.*;
import com.ruoyi.lab.mapper.LabSlaMapper;
import com.ruoyi.lab.maintenance.*;
import com.ruoyi.lab.domain.*;
import java.time.*;
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
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(classes=RuoYiApplication.class,properties={"spring.quartz.auto-startup=false","lab.demo-data.enabled=false"})
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named="LAB_TEST_WRAPPER_ACTIVE",matches="true")
class LabSlaIT
{
    @Autowired JdbcTemplate jdbc;
    @Autowired RepairOrderService repairs;
    @Autowired TaskActorContext actors;
    @Autowired TransactionTemplate tx;
    @Autowired SlaService sla;
    @Autowired SlaAlertService alerts;
    @Autowired LabSlaMapper mapper;
    @Autowired MessageDeliveryEngine messages;
    @Autowired MaintenanceService maintenance;
    @Autowired MaintenanceExecutionService maintenanceExecution;
    @Autowired com.ruoyi.lab.mapper.LabMaintenanceMapper maintenanceMapper;
    @Autowired HazardService hazards;
    @Autowired RectificationService rectifications;
    @MockitoBean BusinessTaskRuntime runtime;
    @MockitoBean RedisCache redis;
    @MockitoBean TokenService tokens;
    @MockitoBean LabIdempotencyStore idempotency;
    @MockitoBean ISysJobService jobs;

    @Test void sourceCreationAndSlaRollbackTogether()
    {
        seed(99701);
        assertThatThrownBy(()->actors.asCurrentActor(99701L,()->tx.execute(status->{
            repairs.reportFault(new ReportFaultCommand(99701L,"测试回滚"),99701L);
            assertThat(jdbc.queryForObject("select count(*) from lab_sla_record where device_id=99701",Integer.class)).isEqualTo(1);
            throw new IllegalStateException("rollback-probe");
        }))).hasMessageContaining("rollback-probe");
        assertThat(jdbc.queryForObject("select count(*) from lab_sla_record where device_id=99701",Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from lab_repair_order where device_id=99701",Integer.class)).isZero();
    }
    @Test void pauseResumeSnapshotsOwnerScopeAndDurableAlertsFollowRealRepairLifecycle()
    {
        seed(99801);
        var repair=actors.asCurrentActor(99801L,()->repairs.reportFault(new ReportFaultCommand(99801L,"SLA 维修"),99801L));
        long id=jdbc.queryForObject("select id from lab_sla_record where object_type='REPAIR_ORDER' and object_id=?",Long.class,repair.id());
        var initial=mapper.byId(id);assertThat(initial.ruleVersionId).isNotNull();assertThat(initial.processingHours).isEqualTo(72);
        actors.asCurrentActor(99801L,()->sla.publish(new SlaCommands.Rule(99801L,"REPAIR","LOW",1,2,"更改仅影响新业务")));
        assertThat(mapper.byId(id).processingHours).isEqualTo(72);
        actors.asCurrentActor(99801L,()->sla.clock(id,true,new SlaCommands.ClockCommand(initial.version,"等待外部部件")));
        jdbc.update("update lab_sla_record set paused_at=date_sub(paused_at,interval 2 hour) where id=?",id);
        var paused=mapper.byId(id);
        actors.asCurrentActor(99801L,()->sla.clock(id,false,new SlaCommands.ClockCommand(paused.version,"因".repeat(500))));
        var resumed=mapper.byId(id);
        assertThat(resumed.responseDueAt).isEqualTo(initial.responseDueAt);
        assertThat(resumed.processingDueAt).isAfterOrEqualTo(initial.processingDueAt.plusHours(2));
        assertThat(resumed.totalPausedSeconds).isGreaterThanOrEqualTo(7200);
        assertThatThrownBy(()->actors.asCurrentActor(1L,()->sla.detail(id))).hasMessageContaining("权限");
        assertThat(actors.asCurrentActor(99802L,()->sla.list(null,null,true))).isEmpty();
        actors.asCurrentActor(99801L,()->sla.clock(id,true,new SlaCommands.ClockCommand(resumed.version,"再次等待")));
        jdbc.update("update lab_sla_record set response_due_at=date_sub(now(),interval 30 hour) where id=?",id);
        alerts.scan(id,LocalDateTime.now());alerts.scan(id,LocalDateTime.now());
        assertThat(mapper.alerts(id)).hasSize(2).allSatisfy(a->assertThat(a.phase).isEqualTo("RESPONSE"));
        assertThat(actors.asCurrentActor(99801L,()->sla.detail(id)).record().state).isEqualTo("OVERDUE");
        assertThat(messages.backfillSla(100)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from lab_sla_notice where record_id=?",Integer.class,id)).isGreaterThanOrEqualTo(2);
        assertThat(jdbc.queryForObject("select count(*) from lab_message_delivery where business_type='SLA' and business_id=? and source_type='SLA_NOTICE'",Integer.class,id)).isGreaterThanOrEqualTo(2);
        actors.asCurrentActor(99801L,()->repairs.assign(repair.id(),new AssignRepairCommand(99802L),99801L));
        assertThat(mapper.byId(id).respondedAt).isNotNull();assertThat(mapper.byId(id).ownerId).isEqualTo(99802L);
        assertThat(actors.asCurrentActor(99802L,()->sla.list(null,null,true))).extracting(r->r.id).contains(id);
        assertThatThrownBy(()->actors.asCurrentActor(99802L,()->sla.clock(id,false,new SlaCommands.ClockCommand(mapper.byId(id).version,"不允许本人暂停")))).hasMessageContaining("权限");
        actors.asCurrentActor(99802L,()->repairs.start(repair.id(),99802L));
        actors.asCurrentActor(99802L,()->repairs.submitResult(repair.id(),new SubmitRepairResultCommand("处理完成"),99802L));
        assertThat(mapper.byId(id).pausedAt).isNull();assertThat(mapper.byId(id).completedAt).isNotNull();
        alerts.scan(id,LocalDateTime.now().plusDays(30));assertThat(mapper.alerts(id)).hasSize(2);
        actors.asCurrentActor(99801L,()->repairs.accept(repair.id(),new AcceptRepairCommand(false,"请继续处理"),99801L));
        assertThat(mapper.byId(id).completedAt).isNull();
        actors.asCurrentActor(99802L,()->repairs.submitResult(repair.id(),new SubmitRepairResultCommand("重新完成"),99802L));
        actors.asCurrentActor(99801L,()->repairs.accept(repair.id(),new AcceptRepairCommand(true,"验收通过"),99801L));
        assertThat(mapper.byId(id).closedAt).isNotNull();alerts.scan(id,LocalDateTime.now().plusYears(1));
        assertThat(mapper.alerts(id)).hasSize(2);
    }
    @Test void maintenanceUsesOneCycleClockAndHazardTracksReviewWithoutHistoricalBackfill()
    {
        seed(99901);
        var due=OffsetDateTime.now(ZoneOffset.ofHours(8)).minusMinutes(1);
        var plan=actors.asCurrentActor(99901L,()->maintenance.create(new MaintenanceCommands.Plan(99901L,"MAINTENANCE",30,due,99902L,"维护","发布",null)));
        maintenance.generate(plan.id,LocalDateTime.now());var cycle=maintenanceMapper.openCycle(plan.id);
        long id=jdbc.queryForObject("select id from lab_sla_record where object_type='MAINTENANCE_CYCLE' and object_id=?",Long.class,cycle.id);
        actors.asCurrentActor(99901L,()->maintenance.schedule(cycle.id,new MaintenanceCommands.Window(due,due.plusHours(2),0,"安排")));
        assertThat(mapper.byId(id).respondedAt).isNotNull();
        var execution=actors.asCurrentActor(99901L,()->maintenanceExecution.start(cycle.id,new MaintenanceCommands.Start(1,"启动")));
        assertThat(mapper.byId(id).startedAt).isNotNull();assertThat(mapper.byId(id).repairId).isEqualTo(execution.repairId);
        assertThat(jdbc.queryForObject("select count(*) from lab_sla_record where object_type='REPAIR_ORDER' and object_id=?",Integer.class,execution.repairId)).isZero();
        actors.asCurrentActor(99902L,()->repairs.start(execution.repairId,99902L));
        actors.asCurrentActor(99902L,()->repairs.submitResult(execution.repairId,new SubmitRepairResultCommand("维护完成"),99902L));
        assertThat(mapper.byId(id).completedAt).isNotNull();
        actors.asCurrentActor(99901L,()->repairs.accept(execution.repairId,new AcceptRepairCommand(true,"通过"),99901L));
        assertThat(mapper.byId(id).closedAt).isNotNull();
        long hazard=actors.asCurrentActor(99901L,()->hazards.create(new CreateHazardCommand(HazardTargetType.DEVICE,99901L,HazardSeverity.MAJOR,99902L,LocalDateTime.now().plusDays(2),"整改要求",null),99901L,"sla-99901"));
        long hazardSla=jdbc.queryForObject("select id from lab_sla_record where object_type='HAZARD' and object_id=?",Long.class,hazard);
        assertThat(mapper.byId(hazardSla).responseHours).isEqualTo(1);
        actors.asCurrentActor(99902L,()->{rectifications.start(hazard,99902L,"sla-99902");return null;});
        assertThat(mapper.byId(hazardSla).respondedAt).isNotNull();assertThat(mapper.byId(hazardSla).startedAt).isNotNull();
        long round=actors.asCurrentActor(99902L,()->rectifications.submit(hazard,new SubmitRectificationCommand("整改完成"),99902L,"sla-99902"));
        assertThat(mapper.byId(hazardSla).completedAt).isNotNull();
        actors.asCurrentActor(99901L,()->{rectifications.review(hazard,round,new ReviewRectificationCommand(true,"通过"),99901L,"sla-99901");return null;});
        assertThat(mapper.byId(hazardSla).closedAt).isNotNull();
        jdbc.update("insert into lab_repair_order(id,repair_no,device_id,source_type,reporter_id,fault_description,status,create_time) values(99901,'SLA-LEGACY-OPEN',99901,'ACTIVE_REPORT',99901,'历史开放工单','WAIT_ASSIGN',date_sub(now(),interval 1 year))");
        actors.asCurrentActor(99901L,()->repairs.assign(99901L,new AssignRepairCommand(99902L),99901L));
        assertThat(jdbc.queryForObject("select count(*) from lab_sla_record where object_type='REPAIR_ORDER' and object_id=99901",Integer.class)).isZero();
    }
    @Test void alertFactsRollbackBeforeSendAndMissingNoticeIsCompensatedExactlyOnce()
    {
        seed(99301);
        var published=actors.asCurrentActor(99301L,()->sla.publish(new SlaCommands.Rule(99301L,"REPAIR","LOW",1,2,"人工规则优先")));
        SlaRule lateDefault=new SlaRule();lateDefault.laboratoryId=99301L;lateDefault.businessType="REPAIR";lateDefault.risk="LOW";
        lateDefault.responseHours=8;lateDefault.processingHours=72;lateDefault.reason="模拟并发默认初始化";lateDefault.createdBy=99301L;lateDefault.createdAt=LocalDateTime.now();lateDefault.builtin=true;
        mapper.insertRule(lateDefault);assertThat(lateDefault.id).isGreaterThan(published.id);
        assertThat(mapper.activeRule(99301L,"REPAIR","LOW").id).isEqualTo(published.id);
        var repair=actors.asCurrentActor(99301L,()->repairs.reportFault(new ReportFaultCommand(99301L,"告警事务测试"),99301L));
        long id=jdbc.queryForObject("select id from lab_sla_record where object_type='REPAIR_ORDER' and object_id=?",Long.class,repair.id());
        assertThatThrownBy(()->tx.execute(status->{
            jdbc.update("update lab_sla_record set response_due_at=date_sub(now(),interval 30 hour) where id=?",id);
            alerts.scan(id,LocalDateTime.now());
            assertThat(jdbc.queryForObject("select count(*) from lab_sla_notice where record_id=?",Integer.class,id)).isPositive();
            assertThat(jdbc.queryForObject("select count(*) from lab_message_delivery where business_type='SLA' and business_id=?",Integer.class,id)).isZero();
            throw new IllegalStateException("notice-rollback");
        })).hasMessageContaining("notice-rollback");
        assertThat(mapper.alerts(id)).isEmpty();
        SlaAlert fact=new SlaAlert();fact.recordId=id;fact.phase="RESPONSE";fact.stage="NEAR_DUE";fact.createdAt=LocalDateTime.now();
        tx.execute(status->{mapper.alert(fact);SlaNotice notice=new SlaNotice();notice.alertId=fact.id;notice.recordId=id;notice.receiverId=99301L;notice.title="时效提醒";notice.content="已持久化但登记前进程中断的事实";mapper.notice(notice);return null;});
        assertThat(messages.backfillSla(100)).isEqualTo(1);assertThat(messages.backfillSla(100)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from lab_notification where business_type='SLA' and business_id=?",Integer.class,id)).isEqualTo(1);
    }
    private void seed(long id)
    {
        assertThat(jdbc.queryForObject("select database()",String.class)).startsWith("lab_test_");
        for(int i=0;i<2;i++) {
            jdbc.update("insert into sys_user(user_id,dept_id,user_name,nick_name,status,del_flag) values(?,100,?,?,'0','0')",id+i,"sla-"+(id+i),"SLA测试");
            jdbc.update("insert into sys_user_role(user_id,role_id) select ?,role_id from sys_role where role_key=?",id+i,i==0?"lab_manager":"lab_repair_worker");
        }
        jdbc.update("insert into lab_laboratory(id,lab_code,name,dept_id,manager_id,location) values(?,?,'SLA测试',100,?,'测试楼')",id,"SLA-"+id,id);
        jdbc.update("insert into lab_device(id,asset_no,laboratory_id,name,category_code,risk_level,location,manager_id,status) values(?,?,?,'测试设备','MICROSCOPE','LOW','测试楼',?,'AVAILABLE')",id,"SLA-"+id,id,id);
    }
}
