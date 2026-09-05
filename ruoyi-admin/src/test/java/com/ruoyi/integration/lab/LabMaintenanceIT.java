package com.ruoyi.integration.lab;

import java.time.*;
import com.ruoyi.RuoYiApplication;
import com.ruoyi.common.core.redis.RedisCache;
import com.ruoyi.framework.web.service.TokenService;
import com.ruoyi.lab.dto.*;
import com.ruoyi.lab.maintenance.*;
import com.ruoyi.lab.mapper.LabMaintenanceMapper;
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
class LabMaintenanceIT
{
    @Autowired JdbcTemplate jdbc;
    @Autowired MaintenanceService plans;
    @Autowired MaintenanceExecutionService executions;
    @Autowired MaintenanceWindowGuard windows;
    @Autowired LabMaintenanceMapper mapper;
    @Autowired RepairOrderService repairs;
    @Autowired TaskActorContext actors;
    @MockitoBean BusinessTaskRuntime runtime;
    @MockitoBean RedisCache redis;
    @MockitoBean TokenService tokens;
    @MockitoBean LabIdempotencyStore idempotency;
    @MockitoBean ISysJobService jobs;

    @Test void calibrationKeepsSnapshotAndRequiresItsOwnPrivateReport()
    {
        seed(99401);
        OffsetDateTime due=OffsetDateTime.now(ZoneOffset.ofHours(8)).minusMinutes(1).withNano(0);
        var plan=actors.asCurrentActor(99401L,()->plans.create(command(99401,due,30,null)));
        assertThat(plan.enabled).isTrue();
        assertThat(plans.generate(plan.id,LocalDateTime.now())).isTrue();
        assertThat(plans.generate(plan.id,LocalDateTime.now())).isFalse();
        var cycle=mapper.openCycle(plan.id);
        assertThat(cycle.repairId).isNull();assertThat(status(99401)).isEqualTo("AVAILABLE");
        actors.asCurrentActor(99401L,()->plans.edit(plan.id,command(99401,due.plusDays(4),60,plan.version)));
        assertThat(mapper.openCycle(plan.id).periodDays).isEqualTo(30);
        var scheduled=actors.asCurrentActor(99401L,()->plans.schedule(cycle.id,new MaintenanceCommands.Window(due,due.plusHours(2),0,"安排校准")));
        assertThat(scheduled.scheduled()).isTrue();
        assertThatThrownBy(()->windows.assertAvailable(99401L,due.toLocalDateTime(),due.plusMinutes(10).toLocalDateTime())).hasMessageContaining("窗口");
        var started=actors.asCurrentActor(99401L,()->executions.start(cycle.id,new MaintenanceCommands.Start(1,"启动校准")));
        assertThat(started.repairId).isNotNull();
        assertThat(jdbc.queryForObject("select status from lab_repair_order where id=?",String.class,started.repairId)).isEqualTo("WAIT_REPAIR");
        actors.asCurrentActor(99402L,()->repairs.start(started.repairId,99402L));
        actors.asCurrentActor(99402L,()->repairs.submitResult(started.repairId,new SubmitRepairResultCommand("校准完成"),99402L));
        assertThatThrownBy(()->actors.asCurrentActor(99401L,()->repairs.accept(started.repairId,new AcceptRepairCommand(true,"验收"),99401L))).hasMessageContaining("报告");
        assertThat(mapper.cycle(cycle.id).status).isEqualTo("STARTED");
        jdbc.update("insert into lab_attachment(id,business_type,business_id,original_name,stored_name,mime_type,size,storage_key,sha256) values(99401,'REPAIR_ORDER',?,'report.pdf','report.pdf','application/pdf',5,'test-maintenance-report',repeat('a',64))",started.repairId);
        var current=mapper.plan(plan.id);
        actors.asCurrentActor(99401L,()->plans.toggle(plan.id,new MaintenanceCommands.Toggle(false,current.version,"停用后续计划")));
        actors.asCurrentActor(99401L,()->repairs.accept(started.repairId,new AcceptRepairCommand(true,"验收合格",99401L),99401L));
        var completed=mapper.cycle(cycle.id);
        assertThat(completed.status).isEqualTo("COMPLETED");assertThat(completed.reportAttachmentId).isEqualTo(99401L);
        assertThat(mapper.plan(plan.id).enabled).isFalse();
        assertThat(mapper.plan(plan.id).nextDueAt).isEqualTo(completed.completedAt.plusDays(30));
        assertThat(status(99401)).isEqualTo("AVAILABLE");
        var finished=mapper.plan(plan.id);
        assertThatThrownBy(()->actors.asCurrentActor(99401L,()->plans.edit(plan.id,command(99401,due,60,finished.version)))).hasMessageContaining("已有执行周期");
    }

    @Test void windowConflictsDoNotCancelReservationsOrCreateRepair()
    {
        seed(99501);
        OffsetDateTime due=OffsetDateTime.now(ZoneOffset.ofHours(8)).minusMinutes(1).withNano(0);
        var plan=actors.asCurrentActor(99501L,()->plans.create(command(99501,due,20,null)));
        plans.generate(plan.id,LocalDateTime.now());var cycle=mapper.openCycle(plan.id);
        jdbc.update("insert into lab_reservation(id,reservation_no,device_id,applicant_id,start_time,end_time,purpose,status) values(99501,'MAINT-WINDOW',99501,99501,?,?,'现有预约','APPROVED')",due.toLocalDateTime(),due.plusHours(1).toLocalDateTime());
        var rejected=actors.asCurrentActor(99501L,()->plans.schedule(cycle.id,new MaintenanceCommands.Window(due,due.plusHours(2),0,"冲突窗口")));
        assertThat(rejected.scheduled()).isFalse();assertThat(rejected.conflicts()).anySatisfy(c->assertThat(c.id()).isEqualTo(99501L));
        assertThat(mapper.cycle(cycle.id).status).isEqualTo("PLANNED");
        assertThat(jdbc.queryForObject("select status from lab_reservation where id=99501",String.class)).isEqualTo("APPROVED");
        assertThat(jdbc.queryForObject("select count(*) from lab_repair_order where device_id=99501",Integer.class)).isZero();
        var adjacent=actors.asCurrentActor(99501L,()->plans.schedule(cycle.id,new MaintenanceCommands.Window(due.plusHours(1),due.plusHours(2),0,"无冲突窗口")));
        assertThat(adjacent.scheduled()).isTrue();
        assertThatThrownBy(()->actors.asCurrentActor(99501L,()->executions.start(cycle.id,new MaintenanceCommands.Start(1,"提前开始")))).hasMessageContaining("有效停用窗口");
    }
    @Test void concurrentDueGenerationCreatesOneCycleAndNeverReusesFaultOrder() throws Exception
    {
        seed(99601);
        OffsetDateTime due=OffsetDateTime.now(ZoneOffset.ofHours(8)).minusMinutes(1).withNano(0);
        var plan=actors.asCurrentActor(99601L,()->plans.create(command(99601,due,20,null)));
        var pool=java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            var first=pool.submit(()->plans.generate(plan.id,LocalDateTime.now()));
            var second=pool.submit(()->plans.generate(plan.id,LocalDateTime.now()));
            assertThat(java.util.List.of(first.get(10,java.util.concurrent.TimeUnit.SECONDS),second.get(10,java.util.concurrent.TimeUnit.SECONDS))).containsExactlyInAnyOrder(true,false);
        } finally {pool.shutdownNow();}
        var cycle=mapper.openCycle(plan.id);
        actors.asCurrentActor(99601L,()->plans.schedule(cycle.id,new MaintenanceCommands.Window(due,due.plusHours(2),0,"安排窗口")));
        actors.asCurrentActor(99601L,()->repairs.reportFault(new ReportFaultCommand(99601L,"另一个真实故障"),99601L));
        assertThatThrownBy(()->actors.asCurrentActor(99601L,()->executions.start(cycle.id,new MaintenanceCommands.Start(1,"启动维护")))).isInstanceOf(com.ruoyi.lab.exception.LabBusinessException.class);
        assertThat(mapper.cycle(cycle.id).repairId).isNull();
        assertThat(jdbc.queryForObject("select count(*) from lab_repair_order where device_id=99601",Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select source_type from lab_repair_order where device_id=99601",String.class)).isEqualTo("ACTIVE_REPORT");
    }
    private MaintenanceCommands.Plan command(long device,OffsetDateTime due,int days,Integer version)
    { return new MaintenanceCommands.Plan(device,"CALIBRATION",days,due,device+1,"周期校准","发布计划",version); }
    private String status(long device) { return jdbc.queryForObject("select status from lab_device where id=?",String.class,device); }
    private void seed(long id)
    {
        assertThat(jdbc.queryForObject("select database()",String.class)).startsWith("lab_test_");
        for(int i=0;i<2;i++) {
            jdbc.update("insert into sys_user(user_id,dept_id,user_name,nick_name,status,del_flag) values(?,100,?,?,'0','0')",id+i,"maint-"+(id+i),"维护测试");
            jdbc.update("insert into sys_user_role(user_id,role_id) select ?,role_id from sys_role where role_key=?",id+i,i==0?"lab_manager":"lab_repair_worker");
        }
        jdbc.update("insert into lab_laboratory(id,lab_code,name,dept_id,manager_id,location) values(?,?,'维护测试',100,?,'测试楼')",id,"MAINT-"+id,id);
        jdbc.update("insert into lab_device(id,asset_no,laboratory_id,name,category_code,risk_level,location,manager_id,status) values(?,?,?,'测试设备','MICROSCOPE','LOW','测试楼',?,'AVAILABLE')",id,"MAINT-"+id,id,id);
    }
}
