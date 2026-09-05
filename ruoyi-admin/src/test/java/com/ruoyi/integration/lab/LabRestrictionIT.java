package com.ruoyi.integration.lab;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.*;
import com.ruoyi.RuoYiApplication;
import com.ruoyi.common.core.redis.RedisCache;
import com.ruoyi.framework.web.service.TokenService;
import com.ruoyi.lab.domain.LabReservation;
import com.ruoyi.lab.exception.LabBusinessException;
import com.ruoyi.lab.restriction.*;
import com.ruoyi.lab.service.LabIdempotencyStore;
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

@SpringBootTest(classes=RuoYiApplication.class, properties={"spring.quartz.auto-startup=false", "lab.demo-data.enabled=false"})
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named="LAB_TEST_WRAPPER_ACTIVE", matches="true")
class LabRestrictionIT
{
    @Autowired JdbcTemplate jdbc;
    @Autowired RestrictionService restrictions;
    @Autowired RestrictionGuard guard;
    @Autowired TaskActorContext actors;
    @Autowired TransactionTemplate transactions;
    @MockitoBean BusinessTaskRuntime runtime;
    @MockitoBean RedisCache redis;
    @MockitoBean TokenService tokens;
    @MockitoBean LabIdempotencyStore idempotency;
    @MockitoBean ISysJobService jobs;

    @Test void overlappingRestrictionsAppealAndScopeUseCommittedTransactions()
    {
        assertThat(jdbc.queryForObject("select database()", String.class)).startsWith("lab_test_");
        seedUser(98001,"restriction_manager","lab_manager");
        seedUser(98002,"restriction_student","lab_student");
        seedUser(98003,"restriction_other","lab_student");
        jdbc.update("insert into lab_laboratory(id,lab_code,name,dept_id,manager_id,location) values(98001,'RESTRICTION-IT','限制验证',100,98001,'测试楼')");
        var first = actors.asCurrentActor(98001, () -> restrictions.manual(new RestrictionCommands.Manual(98001L,98002L,7,"第一条事实")));
        var second = actors.asCurrentActor(98001, () -> restrictions.manual(new RestrictionCommands.Manual(98001L,98002L,3,"第二条事实")));
        assertThatThrownBy(() -> guard.assertAllowed(98002L,98001L)).isInstanceOf(LabBusinessException.class);
        assertThatThrownBy(() -> actors.asCurrentActor(98003, () -> restrictions.detail(first.id))).isInstanceOf(LabBusinessException.class);
        actors.asCurrentActor(98002, () -> restrictions.appeal(first.id,new RestrictionCommands.Appeal("事实有误，请复核",List.of())));
        assertThatThrownBy(() -> actors.asCurrentActor(98002, () -> restrictions.review(first.id,new RestrictionCommands.Decision(true,"自行通过")))).isInstanceOf(LabBusinessException.class);
        actors.asCurrentActor(98001, () -> restrictions.review(first.id,new RestrictionCommands.Decision(true,"复核通过")));
        assertThat(actors.asCurrentActor(98002, () -> restrictions.detail(first.id)).restriction().status).isEqualTo("REVOKED");
        assertThatThrownBy(() -> guard.assertAllowed(98002L,98001L)).isInstanceOf(LabBusinessException.class);
        actors.asCurrentActor(98001, () -> restrictions.revoke(second.id,"独立复核解除"));
        guard.assertAllowed(98002L,98001L);
        assertThatThrownBy(() -> actors.asCurrentActor(98002, () -> restrictions.appeal(first.id,new RestrictionCommands.Appeal("重复申诉",List.of())))).isInstanceOf(LabBusinessException.class);
    }

    @Test void newNoShowIsUniqueAndPreEnableFactsAreNotPunished()
    {
        seedUser(98004,"restriction_noshow","lab_student");
        jdbc.update("insert into lab_laboratory(id,lab_code,name,dept_id,manager_id,location) values(98004,'NO-SHOW-IT','爽约验证',100,1,'测试楼')");
        LabReservation current = new LabReservation(); current.setId(98004L); current.setApplicantId(98004L);
        LabReservation historical = new LabReservation(); historical.setId(98005L); historical.setApplicantId(98004L);
        LocalDateTime fact = LocalDateTime.now().plusSeconds(1);
        transactions.executeWithoutResult(status -> {
            guard.lockUsers(List.of(98004L));
            restrictions.recordNoShow(current,98004L,fact,1L);
            restrictions.recordNoShow(current,98004L,fact.plusDays(1),1L);
            restrictions.recordNoShow(historical,98004L,fact.minusYears(1),1L);
        });
        assertThat(jdbc.queryForObject("select count(*) from lab_restriction where user_id=98004",Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select timestampdiff(day,starts_at,ends_at) from lab_restriction where user_id=98004",Integer.class)).isEqualTo(7);
        assertThat(jdbc.queryForObject("select count(*) from lab_restriction r join lab_restriction_rule v on v.id=r.rule_version_id where r.user_id=98004 and v.days=7",Integer.class)).isEqualTo(1);
    }

    @Test void concurrentAdmissionWaitsForRestrictionCommit() throws Exception
    {
        seedUser(98006,"restriction_lock_manager","lab_manager");
        seedUser(98007,"restriction_lock_student","lab_student");
        jdbc.update("insert into lab_laboratory(id,lab_code,name,dept_id,manager_id,location) values(98006,'LOCK-IT','并发限制验证',100,98006,'测试楼')");
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch registered = new CountDownLatch(1), release = new CountDownLatch(1), admissionStarted = new CountDownLatch(1);
        try {
            Future<Long> restriction = pool.submit(() -> actors.asCurrentActor(98006, () -> transactions.execute(status -> {
                var result = restrictions.manual(new RestrictionCommands.Manual(98006L,98007L,7,"并发登记"));
                registered.countDown();
                try { if (!release.await(10,TimeUnit.SECONDS)) throw new IllegalStateException("test release timeout"); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
                return result.id;
            })));
            assertThat(registered.await(5,TimeUnit.SECONDS)).isTrue();
            Future<Boolean> admission = pool.submit(() -> transactions.execute(status -> {
                admissionStarted.countDown();
                guard.lockUsers(List.of(98007L));
                try { guard.assertAllowed(98007L,98006L); return true; }
                catch (LabBusinessException rejected) { return false; }
            }));
            assertThat(admissionStarted.await(5,TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> admission.get(150,TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            release.countDown();
            assertThat(restriction.get(5,TimeUnit.SECONDS)).isPositive();
            assertThat(admission.get(5,TimeUnit.SECONDS)).isFalse();
        } finally {
            release.countDown(); pool.shutdownNow(); pool.awaitTermination(5,TimeUnit.SECONDS);
        }
    }

    private void seedUser(long id,String name,String role)
    {
        jdbc.update("insert into sys_user(user_id,dept_id,user_name,nick_name,status,del_flag) values(?,100,?,?,'0','0')",id,name,name);
        jdbc.update("insert into sys_user_role(user_id,role_id) select ?,role_id from sys_role where role_key=?",id,role);
    }
}
