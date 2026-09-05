package com.ruoyi.integration.lab;

import com.ruoyi.RuoYiApplication;
import com.ruoyi.common.core.redis.RedisCache;
import com.ruoyi.framework.web.service.TokenService;
import com.ruoyi.lab.mapper.BusinessTaskMapper;
import com.ruoyi.lab.service.LabIdempotencyStore;
import com.ruoyi.lab.task.*;
import com.ruoyi.quartz.service.ISysJobService;
import com.ruoyi.web.service.BusinessTaskRuntime;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@SpringBootTest(classes=RuoYiApplication.class,properties={"spring.quartz.auto-startup=false","lab.demo-data.enabled=false"})
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named="LAB_TEST_WRAPPER_ACTIVE",matches="true")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LabG2RecoveryIT
{
    @Autowired JdbcTemplate jdbc;
    @Autowired BusinessTaskService tasks;
    @Autowired BusinessTaskWorker worker;
    @Autowired TaskActorContext actors;
    @Autowired TaskWorkbook workbook;
    @MockitoSpyBean BusinessTaskMapper mapper;
    @MockitoBean BusinessTaskRuntime runtime;
    @MockitoBean RedisCache redis;
    @MockitoBean TokenService tokens;
    @MockitoBean LabIdempotencyStore idempotency;
    @MockitoBean ISysJobService jobs;

    @BeforeAll void seed()
    {
        assertThat(jdbc.queryForObject("select database()",String.class)).startsWith("lab_test_");
        for (long id : List.of(98101L,98102L)) {
            jdbc.update("insert into sys_user(user_id,dept_id,user_name,nick_name,status,del_flag) values(?,100,?,?,'0','0')",id,"g2_recovery_"+id,"g2_recovery_"+id);
            jdbc.update("insert into sys_user_role(user_id,role_id) select ?,role_id from sys_role where role_key=?",id,id==98101?"lab_manager":"lab_student");
        }
        jdbc.update("insert into lab_laboratory(id,lab_code,name,dept_id,manager_id,location) values(98101,'G2-RECOVERY','恢复验证实验室',100,98101,'验证楼')");
    }

    @Test void realWorkerJvmInterruptionResumesOnlyUncommittedRows() throws Exception
    {
        long id = importTask("G2-PROCESS");
        Path log = Path.of("target", "g2-worker-" + UUID.randomUUID() + ".log").toAbsolutePath();
        Process interrupted = launch(id,"interrupt",log);
        try {
            awaitMarker(interrupted,log,"G2_CHECKPOINT_READY");
            assertThat(jdbc.queryForObject("select count(*) from lab_device where asset_no like 'G2-PROCESS-%'",Integer.class)).isEqualTo(1);
        } finally {
            interrupted.destroyForcibly();
            assertThat(interrupted.waitFor(15,TimeUnit.SECONDS)).isTrue();
        }
        assertThat(mapper.get(id).status).isEqualTo("RUNNING");
        assertThat(mapper.get(id).successCount).isEqualTo(1);
        // Shorten only the test lease; the JVM was really killed above, no business checkpoint is rewritten.
        jdbc.update("update lab_business_task set lease_until=date_sub(now(),interval 1 second) where id=?",id);
        Path resumedLog = Path.of("target", "g2-worker-resumed-" + UUID.randomUUID() + ".log").toAbsolutePath();
        Process resumed = launch(id,"resume",resumedLog);
        try {
            assertThat(resumed.waitFor(90,TimeUnit.SECONDS)).as("restarted worker completed; log %s",resumedLog).isTrue();
            assertThat(resumed.exitValue()).as("worker log %s",resumedLog).isZero();
        } finally { if(resumed.isAlive()) resumed.destroyForcibly(); }
        assertThat(mapper.get(id).status).isEqualTo("SUCCEEDED");
        assertThat(mapper.get(id).successCount).isEqualTo(2);
        assertThat(jdbc.queryForObject("select count(*) from lab_device where asset_no like 'G2-PROCESS-%'",Integer.class)).isEqualTo(2);
    }

    @Test void cancellationDuringExecutionRetainsCommittedRow() throws Exception
    {
        long id = importTask("G2-CANCEL");
        var atBoundary = new CountDownLatch(1);
        var resume = new CountDownLatch(1);
        var calls = new AtomicInteger();
        doAnswer(call -> {
            if(calls.incrementAndGet()==2) {
                atBoundary.countDown();
                if(!resume.await(10,TimeUnit.SECONDS)) throw new IllegalStateException("Test boundary timeout");
            }
            var rows = jdbc.query("select row_no from lab_business_task_row where task_id=? and status='READY' order by row_no limit 1",
                    (rs,n)->rs.getInt(1),id);
            if(rows.isEmpty()) return List.<BusinessTaskRow>of();
            return mapper.rows(id,rows.get(0)-2,1);
        }).when(mapper).pending(eq(id));
        String token=UUID.randomUUID().toString();
        assertThat(mapper.claim(id,token)).isEqualTo(1);
        var executor=Executors.newSingleThreadExecutor();
        try {
            var running=executor.submit(()->worker.run(id,token));
            assertThat(atBoundary.await(15,TimeUnit.SECONDS)).isTrue();
            assertThat(actors.asCurrentActor(98101,()->tasks.cancel(id)).status()).isEqualTo("CANCELLING");
            resume.countDown();
            running.get(20,TimeUnit.SECONDS);
            assertThat(mapper.get(id).status).isEqualTo("CANCELLED");
            assertThat(mapper.get(id).successCount).isEqualTo(1);
            assertThat(jdbc.queryForObject("select count(*) from lab_device where asset_no like 'G2-CANCEL-%'",Integer.class)).isEqualTo(1);
        } finally {resume.countDown();executor.shutdownNow();reset(mapper);}
    }

    @Test void ordinaryRoleCannotDownloadAfterRoleRevocation() throws Exception
    {
        long seeded=importTask("G2-DOWNLOAD");run(seeded);
        var exported=actors.asCurrentActor(98102,()->tasks.export("DEVICE",Map.of("keyword","G2-DOWNLOAD")));
        run(exported.id());
        assertThat(actors.asCurrentActor(98102,()->tasks.download(exported.id(),false))).isNotEmpty();
        jdbc.update("delete from sys_user_role where user_id=98102");
        try {
            assertThatThrownBy(()->actors.asCurrentActor(98102,()->tasks.download(exported.id(),false)))
                    .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        } finally {jdbc.update("insert into sys_user_role(user_id,role_id) select 98102,role_id from sys_role where role_key='lab_student'");}
    }

    private long importTask(String prefix) throws Exception
    {
        byte[] bytes=workbook.write(TaskWorkbook.columns("DEVICE"),List.of(
                List.of(prefix+"-1","98101","验证设备一","MICROSCOPE","型号","LOW","验证楼","98101",""),
                List.of(prefix+"-2","98101","验证设备二","MICROSCOPE","型号","LOW","验证楼","98101","")),null);
        var pre=actors.asCurrentActor(98101,()->tasks.precheck("DEVICE",bytes));
        assertThat(pre.failureCount()).isZero();
        actors.asCurrentActor(98101,()->tasks.submit(pre.id()));return pre.id();
    }
    private void run(long id){String token=UUID.randomUUID().toString();assertThat(mapper.claim(id,token)).isEqualTo(1);worker.run(id,token);}
    private static Process launch(long id,String mode,Path log) throws Exception
    {
        String classpath=System.getProperty("surefire.test.class.path",System.getProperty("java.class.path"));
        return new ProcessBuilder(Path.of(System.getProperty("java.home"),"bin","java").toString(),"-cp",classpath,
                LabG2WorkerProbe.class.getName(),Long.toString(id),mode).redirectErrorStream(true).redirectOutput(log.toFile()).start();
    }
    private static void awaitMarker(Process process,Path log,String marker) throws Exception
    {
        long end=System.nanoTime()+Duration.ofSeconds(90).toNanos();
        while(System.nanoTime()<end && process.isAlive()) {
            // Marker is ASCII; tolerate Windows console encoding and a partially written multibyte line.
            if(Files.exists(log)&&Files.readString(log,java.nio.charset.StandardCharsets.ISO_8859_1).contains(marker))return;
            Thread.sleep(100);
        }
        fail("Worker did not reach checkpoint; inspect isolated log " + log);
    }
}
