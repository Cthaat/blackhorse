package com.ruoyi.integration.lab;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.RuoYiApplication;
import com.ruoyi.common.core.domain.model.LoginUser;
import com.ruoyi.common.core.redis.RedisCache;
import com.ruoyi.framework.web.service.SysPermissionService;
import com.ruoyi.framework.web.service.TokenService;
import com.ruoyi.lab.service.LabIdempotencyStore;
import com.ruoyi.lab.service.ReservationWaitlistCoordinator;
import com.ruoyi.quartz.service.ISysJobService;
import com.ruoyi.system.service.ISysUserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Committed transactions in a disposable native MySQL database, never the local demo DB. */
@SpringBootTest(classes = RuoYiApplication.class, properties = {
        "spring.quartz.auto-startup=false", "lab.demo-data.enabled=false" })
@AutoConfigureMockMvc
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "LAB_TEST_WRAPPER_ACTIVE", matches = "true")
class LabG1ConcurrencyIT
{
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired ISysUserService users;
    @Autowired SysPermissionService permissions;
    @Autowired ReservationWaitlistCoordinator coordinator;
    @MockitoBean RedisCache redis;
    @MockitoBean TokenService tokens;
    @MockitoBean LabIdempotencyStore idempotency;
    @MockitoBean ISysJobService jobs;

    @Test
    void twentyApplicantsHaveOneWinnerAndExpiredOrRevokedOffersPromoteNext() throws Exception
    {
        assertThat(jdbc.queryForObject("select database()", String.class)).startsWith("lab_test_");
        jdbc.update("insert into lab_laboratory(id,lab_code,name,dept_id,manager_id,location) values(96001,'G1-RACE','并发实验室',100,1,'验证楼')");
        jdbc.update("insert into lab_device(id,asset_no,laboratory_id,name,category_code,risk_level,location,manager_id) values(96100,'G1-RACE-D',96001,'并发设备','MICROSCOPE','LOW','验证楼',1)");
        List<LoginUser> actors = new ArrayList<>();
        for (long id = 96010; id < 96030; id++)
        {
            jdbc.update("insert into sys_user(user_id,dept_id,user_name,nick_name,status,del_flag) values(?,100,?,?,'0','0')", id, "g1_" + id, "g1_" + id);
            jdbc.update("insert into sys_user_role(user_id,role_id) select ?,role_id from sys_role where role_key='lab_student'", id);
            jdbc.update("insert into lab_qualification(user_id,scope_type,scope_id,laboratory_id,valid_from,valid_until) values(?,'LABORATORY','96001',96001,date_sub(now(),interval 1 day),date_add(now(),interval 30 day))", id);
            var account = users.selectUserById(id);
            actors.add(new LoginUser(id, account.getDeptId(), account, permissions.getMenuPermission(account)));
        }
        OffsetDateTime start = OffsetDateTime.now(ZoneOffset.ofHours(8)).plusDays(2).withHour(10).withMinute(0).withSecond(0).withNano(0);
        Map<String, Object> request = Map.of("deviceId", 96100, "startTime", start.toString(), "endTime", start.plusHours(1).toString(), "purpose", "并发验证");
        String body = json.writeValueAsString(request);
        var ready = new CountDownLatch(20);
        var go = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(20);
        List<Future<Integer>> attempts = new ArrayList<>();
        try
        {
            for (LoginUser actor : actors)
            {
                attempts.add(pool.submit(() -> {
                    ready.countDown();
                    if (!go.await(10, TimeUnit.SECONDS)) { throw new IllegalStateException("Start barrier timed out"); }
                    return mvc.perform(post("/lab/reservations").with(user(actor)).contentType(MediaType.APPLICATION_JSON)
                            .header("X-Idempotency-Key", "race-" + actor.getUserId()).content(body))
                            .andReturn().getResponse().getStatus();
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            go.countDown();
            List<Integer> outcomes = new ArrayList<>();
            for (Future<Integer> attempt : attempts) { outcomes.add(attempt.get(45, TimeUnit.SECONDS)); }
            assertThat(outcomes).containsOnly(201, 409);
            assertThat(outcomes.stream().filter(code -> code == 201).count()).isEqualTo(1);
        }
        finally { go.countDown(); pool.shutdownNow(); }
        assertThat(jdbc.queryForObject("select count(*) from lab_reservation where device_id=96100 and status='PENDING'", Integer.class)).isEqualTo(1);
        long winner = jdbc.queryForObject("select applicant_id from lab_reservation where device_id=96100", Long.class);
        long reservation = jdbc.queryForObject("select id from lab_reservation where device_id=96100", Long.class);
        List<LoginUser> candidates = actors.stream().filter(actor -> actor.getUserId() != winner).limit(3).toList();
        List<Long> entries = new ArrayList<>();
        for (LoginUser actor : candidates)
        {
            JsonNode entry = send(actor, "/lab/reservation-waitlist", request, "queue-" + actor.getUserId()).path("data");
            assertThat(entry.path("status").asText()).isEqualTo("WAITING");
            entries.add(entry.path("id").asLong());
        }
        send(actors.stream().filter(actor -> actor.getUserId() == winner).findFirst().orElseThrow(),
                "/lab/reservations/" + reservation + "/commands/cancel", Map.of("expectedVersion", 0), null);
        assertThat(state(entries.get(0))).isEqualTo("OFFERED");
        jdbc.update("update lab_reservation_waitlist set offered_until=date_sub(now(),interval 1 minute) where id=?", entries.get(0));
        coordinator.advanceDevice(96100L);
        assertThat(state(entries.get(0))).isEqualTo("EXPIRED");
        assertThat(state(entries.get(1))).isEqualTo("OFFERED");
        jdbc.update("update lab_qualification set revoked_at=now() where user_id=?", candidates.get(1).getUserId());
        coordinator.advanceDevice(96100L);
        assertThat(state(entries.get(1))).isEqualTo("INELIGIBLE");
        assertThat(state(entries.get(2))).isEqualTo("OFFERED");
        coordinator.advanceDevice(96100L);
        assertThat(jdbc.queryForObject("select count(*) from lab_notification where business_type='WAITLIST' and business_id in (?,?,?) and delivery_status='SENT'", Integer.class, entries.toArray())).isEqualTo(3);
        int version = jdbc.queryForObject("select version from lab_reservation_waitlist where id=?", Integer.class, entries.get(2));
        JsonNode claimed = send(candidates.get(2), "/lab/reservation-waitlist/" + entries.get(2) + "/commands/confirm", Map.of("expectedVersion", version), null).path("data");
        assertThat(claimed.path("status").asText()).isEqualTo("PENDING");
        assertThat(state(entries.get(2))).isEqualTo("ACCEPTED");
    }

    private String state(long id)
    {
        return jdbc.queryForObject("select status from lab_reservation_waitlist where id=?", String.class, id);
    }

    private JsonNode send(LoginUser actor, String path, Object body, String key) throws Exception
    {
        var request = post(path).with(user(actor)).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        if (key != null) { request.header("X-Idempotency-Key", key); }
        return json.readTree(mvc.perform(request).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }
}
