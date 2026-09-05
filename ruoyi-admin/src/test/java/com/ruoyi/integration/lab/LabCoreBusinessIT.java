package com.ruoyi.integration.lab;

import java.time.OffsetDateTime;
import java.time.LocalDateTime;
import java.util.List;
import com.ruoyi.lab.service.InspectionScheduleService;
import java.util.Map;
import java.util.Set;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.RuoYiApplication;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.core.domain.model.LoginUser;
import com.ruoyi.common.core.redis.RedisCache;
import com.ruoyi.framework.web.service.SysPermissionService;
import com.ruoyi.framework.web.service.TokenService;
import com.ruoyi.lab.service.LabIdempotencyStore;
import com.ruoyi.quartz.service.ISysJobService;
import com.ruoyi.system.service.ISysUserService;
import org.junit.jupiter.api.BeforeEach;
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
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real MySQL + controllers + permission seeds + services + mappers; isolated and rolled back. */
@SpringBootTest(classes = RuoYiApplication.class, properties = {
        "spring.quartz.auto-startup=false", "lab.demo-data.enabled=false" })
@AutoConfigureMockMvc
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "LAB_TEST_WRAPPER_ACTIVE", matches = "true")
@Transactional
class LabCoreBusinessIT
{
    private static final long STUDENT = 95001, MANAGER = 95002, WORKER = 95003, SAFETY = 95004, SYSADMIN = 95005;
    @Autowired InspectionScheduleService schedule;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired ISysUserService users;
    @Autowired SysPermissionService permissions;
    @MockitoBean RedisCache redis;
    @MockitoBean TokenService tokens;
    @MockitoBean LabIdempotencyStore idempotency;
    @MockitoBean ISysJobService jobs;

    @BeforeEach
    void seed()
    {
        assertThat(jdbc.queryForObject("select database()", String.class)).startsWith("lab_test_");
        addUser(STUDENT, "it_student", "lab_student");
        addUser(MANAGER, "it_manager", "lab_manager");
        addUser(WORKER, "it_worker", "lab_repair_worker");
        addUser(SAFETY, "it_safety", "lab_safety_officer");
        addUser(SYSADMIN, "it_sysadmin", "lab_system_admin");
        jdbc.update("insert into lab_laboratory(id,lab_code,name,dept_id,manager_id,location) values(95001,'IT-LAB','联调实验室',100,?,'联调楼')", MANAGER);
        jdbc.update("insert into lab_laboratory(id,lab_code,name,dept_id,manager_id,location) values(95002,'IT-OUT','范围外实验室',101,?,'联调楼')", MANAGER);
        for (int index = 0; index < 3; index++)
        {
            jdbc.update("insert into lab_device(id,asset_no,laboratory_id,name,category_code,risk_level,location,manager_id) values(?,?,95001,'同名设备','MICROSCOPE','LOW','联调楼',?)",
                    95100 + index, "IT-D-" + index, MANAGER);
        }
        jdbc.update("insert into lab_device(id,asset_no,laboratory_id,name,category_code,risk_level,location,manager_id) values(95999,'IT-OUT-D',95002,'范围外设备','MICROSCOPE','LOW','联调楼',?)", MANAGER);
        jdbc.update("insert into lab_qualification(user_id,scope_type,scope_id,laboratory_id,valid_from,valid_until) values(?,'LABORATORY','95001',95001,date_sub(now(),interval 1 day),date_add(now(),interval 30 day))", STUDENT);
        jdbc.update("update sys_config set config_value='0' where config_key='lab.reservation.min-lead-minutes'");
    }

    @Test
    void paginationOptionsAndValidationRespectOrdinaryRoleScope() throws Exception
    {
        for (long actor : new long[] { STUDENT, MANAGER, SAFETY })
        {
            JsonNode first = getJson(actor, "/lab/devices/list?pageNum=1&pageSize=2&sortBy=name&sortDirection=asc");
            JsonNode second = getJson(actor, "/lab/devices/list?pageNum=2&pageSize=2&sortBy=name&sortDirection=asc");
            assertThat(first.path("total").asLong()).isEqualTo(3);
            assertThat(first.path("rows").size()).isEqualTo(2);
            assertThat(second.path("total").asLong()).isEqualTo(3);
            assertThat(second.path("rows").size()).isEqualTo(1);
            assertThat(second.path("rows").get(0).path("id").asText()).isEqualTo("95102");
        }
        // System administration is not a lab business role.
        mvc.perform(get("/lab/devices/list").with(user(login(SYSADMIN))))
                .andExpect(status().isForbidden());
        postJson(SYSADMIN, "/lab/reservations", application(95100), "read-only", 403);
        mvc.perform(get("/lab/devices/list?pageSize=101").with(user(login(STUDENT))))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/lab/devices/95999").with(user(login(STUDENT))))
                .andExpect(status().isForbidden());
        JsonNode options = getJson(MANAGER, "/lab/options/users?roleKey=lab_student&pageNum=1&pageSize=1");
        assertThat(options.path("total").asInt()).isEqualTo(1);
        assertThat(options.path("data").get(0).path("id").asText()).isEqualTo(Long.toString(STUDENT));
        mvc.perform(get("/lab/options/users").with(user(login(STUDENT))))
                .andExpect(status().isForbidden());
        mvc.perform(post("/lab/devices").with(user(login(MANAGER))).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("assetNo", "IT-BAD", "laboratoryId", 95001,
                        "name", "非法风险设备", "categoryCode", "MICROSCOPE", "riskLevel", "UNKNOWN",
                        "location", "联调楼", "managerId", MANAGER))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void studentReservationCheckoutAbnormalReturnAndRepairCloseLoop() throws Exception
    {
        Map<String, Object> application = application(95100);
        JsonNode created = postJson(STUDENT, "/lab/reservations", application, "core-self", 201);
        long reservation = created.path("data").path("id").asLong();
        assertThat(postJson(STUDENT, "/lab/reservations", application, "core-self", 200)
                .path("data").path("id").asLong()).isEqualTo(reservation);
        postJson(MANAGER, "/lab/reservations/" + reservation + "/commands/approve",
                Map.of("expectedVersion", 0, "reason", "同意使用"), null, 200);
        JsonNode usage = postJson(MANAGER, "/lab/usage-records/check-out", Map.of("reservationId", reservation), null, 200);
        long usageId = usage.path("data").path("id").asLong();
        postJson(MANAGER, "/lab/usage-records/" + usageId + "/return",
                Map.of("condition", "FAULT", "faultDescription", "无法正常启动"), null, 200);
        Long repairId = jdbc.queryForObject("select id from lab_repair_order where device_id=95100 and status='WAIT_ASSIGN'", Long.class);
        String repair = "/lab/repair-orders/" + repairId;
        postJson(MANAGER, repair + "/assign", Map.of("assigneeId", WORKER), null, 200);
        postJson(WORKER, repair + "/start", Map.of(), null, 200);
        postJson(WORKER, repair + "/submit-result", Map.of("result", "更换保险丝，试运行正常"), null, 200);
        postJson(MANAGER, repair + "/accept", Map.of("passed", true, "reason", "现场验收通过"), null, 200);
        assertThat(jdbc.queryForObject("select status from lab_device where id=95100", String.class)).isEqualTo("AVAILABLE");
        assertThat(jdbc.queryForObject("select status from lab_reservation where id=?", String.class, reservation)).isEqualTo("COMPLETED");
        assertThat(getJson(STUDENT, "/lab/usage-records").path("total").asInt()).isEqualTo(1);
    }

    @Test
    void delegationHasSeparateAuthorityAuditAndNoSelfApproval() throws Exception
    {
        Map<String, Object> delegated = new java.util.HashMap<>(application(95101));
        delegated.put("applicantId", STUDENT);
        postJson(STUDENT, "/lab/reservations/delegate", delegated, "denied", 403);
        long id = postJson(MANAGER, "/lab/reservations/delegate", delegated, "delegate", 201)
                .path("data").path("id").asLong();
        assertThat(postJson(MANAGER, "/lab/reservations/delegate", delegated, "delegate", 200)
                .path("data").path("submitterId").asLong()).isEqualTo(MANAGER);
        postJson(MANAGER, "/lab/reservations/" + id + "/commands/approve",
                Map.of("expectedVersion", 0, "reason", "尝试自批"), null, 403);
        assertThat(getJson(STUDENT, "/lab/reservations").path("total").asLong()).isEqualTo(1);
        assertThat(getJson(MANAGER, "/lab/dashboard/summary").path("data").path("pendingReservations").asLong()).isZero();
        postJson(1, "/lab/reservations/" + id + "/commands/approve",
                Map.of("expectedVersion", 0, "reason", "独立复核通过"), null, 200);
        assertThat(jdbc.queryForObject("select operator_id from lab_status_history where object_type='RESERVATION' and object_id=? and from_status is null", Long.class, id)).isEqualTo(MANAGER);
    }

    @Test
    void safetyInspectionMajorHazardRectificationAndIndependentReview() throws Exception
    {
        long plan = postJson(SAFETY, "/lab/inspection-plans", Map.of(
                "planName", "电气安全巡检", "laboratoryId", 95001, "frequencyType", "DAILY",
                "intervalValue", 1, "executeTime", "09:00:00", "ownerId", SAFETY,
                "deadlineRule", "AFTER_SCHEDULED", "deadlineOffsetMinutes", 1440,
                "items", List.of(Map.of("itemCode", "POWER", "content", "检查电气绝缘",
                        "sortOrder", 0, "enabled", true))), null, 201).path("data").asLong();
        postJson(SAFETY, "/lab/inspection-plans/" + plan + "/enable", Map.of(), null, 200);
        LocalDateTime now = LocalDateTime.now();
        jdbc.update("update lab_inspection_plan set next_run_at=? where id=?", now.minusMinutes(1), plan);
        assertThat(schedule.generateDueTasks(now, 10)).isEqualTo(1);
        assertThat(schedule.generateDueTasks(now, 10)).isZero();
        long task = getJson(SAFETY, "/lab/inspection-tasks").path("rows").get(0).path("id").asLong();
        long item = getJson(SAFETY, "/lab/inspection-tasks/" + task + "/items").path("data").get(0).path("id").asLong();
        postJson(STUDENT, "/lab/inspection-tasks/" + task + "/start", Map.of(), null, 403);
        postJson(SAFETY, "/lab/inspection-tasks/" + task + "/start", Map.of(), null, 200);
        Map<String, Object> failure = Map.of("result", "FAIL", "description", "电源绝缘失效",
                "severity", "MAJOR", "targetType", "DEVICE", "targetId", 95102, "version", 0);
        mvc.perform(put("/lab/inspection-tasks/" + task + "/items/" + item).with(user(login(SAFETY)))
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(failure)))
                .andExpect(status().isOk());
        postJson(SAFETY, "/lab/inspection-tasks/" + task + "/complete", Map.of(), null, 200);
        long hazard = jdbc.queryForObject("select id from lab_hazard where source_item_id=?", Long.class, item);
        postJson(STUDENT, "/lab/reservations", application(95102), "blocked-by-hazard", 409);
        postJson(SAFETY, "/lab/hazards/" + hazard + "/start-rectification", Map.of(), null, 200);
        long round = postJson(SAFETY, "/lab/hazards/" + hazard + "/rectifications",
                Map.of("description", "修复绝缘并完成检测"), null, 201).path("data").asLong();
        String review = "/lab/hazards/" + hazard + "/rectifications/" + round + "/review";
        postJson(SAFETY, review, Map.of("passed", true, "reason", "尝试自复核"), null, 403);
        postJson(1, review, Map.of("passed", true, "reason", "独立检测合格"), null, 200);
        assertThat(jdbc.queryForObject("select status from lab_hazard where id=?", String.class, hazard)).isEqualTo("CLOSED");
        postJson(STUDENT, "/lab/reservations", application(95102), "unblocked", 201);
    }

    private Map<String, Object> application(long device)
    {
        OffsetDateTime start = OffsetDateTime.now(java.time.ZoneOffset.ofHours(8)).plusMinutes(1);
        return Map.of("deviceId", device, "startTime", start.toString(), "endTime", start.plusHours(1).toString(), "purpose", "核心联调");
    }

    @Test
    void g1RulesCalendarWaitlistClaimAndSnapshotRemainConsistent() throws Exception
    {
        long candidate = 95006;
        addUser(candidate, "it_candidate", "lab_student");
        jdbc.update("insert into lab_qualification(user_id,scope_type,scope_id,laboratory_id,valid_from,valid_until) values(?,'LABORATORY','95001',95001,date_sub(now(),interval 1 day),date_add(now(),interval 30 day))", candidate);
        var start = OffsetDateTime.now(java.time.ZoneOffset.ofHours(8)).plusDays(1).withHour(10).withMinute(0).withSecond(0).withNano(0);
        var definition = new java.util.LinkedHashMap<String, Object>();
        definition.put("name", "日间开放"); definition.put("weekdays", List.of(1,2,3,4,5,6,7));
        definition.put("opensAt", "09:00"); definition.put("closesAt", "17:00"); definition.put("closedDays", List.of());
        definition.put("minLeadMinutes", 0); definition.put("maxAdvanceDays", 10);
        definition.put("minDurationMinutes", 30); definition.put("maxDurationMinutes", 480); definition.put("invitationMinutes", 15);
        var draft = Map.of("deviceId", 95100, "definition", definition);
        postJson(STUDENT, "/lab/reservation-rules", draft, null, 403);
        JsonNode rule = postJson(MANAGER, "/lab/reservation-rules", draft, null, 200).path("data");
        long ruleId = rule.path("id").asLong();
        postJson(MANAGER, "/lab/reservation-rules/" + ruleId + "/commands/publish", Map.of("expectedVersion", 0), null, 200);
        Map<String, Object> request = Map.of("deviceId", 95100, "startTime", start.toString(),
                "endTime", start.plusHours(1).toString(), "purpose", "候补联调");
        long reservation = postJson(STUDENT, "/lab/reservations", request, "g1-first", 201).path("data").path("id").asLong();
        assertThat(jdbc.queryForObject("select rule_version_id from lab_reservation where id=?", Long.class, reservation)).isEqualTo(ruleId);
        assertThat(jdbc.queryForObject("select rule_snapshot from lab_reservation where id=?", String.class, reservation)).contains("日间开放");
        JsonNode queued = postJson(candidate, "/lab/reservation-waitlist", request, "g1-queue", 200).path("data");
        long queuedId = queued.path("id").asLong();
        assertThat(queued.path("status").asText()).isEqualTo("WAITING");
        assertThat(getJson(STUDENT, "/lab/reservation-waitlist").path("total").asInt()).isZero();
        postJson(STUDENT, "/lab/reservations/" + reservation + "/commands/cancel", Map.of("expectedVersion", 0), null, 200);
        JsonNode offer = getJson(candidate, "/lab/reservation-waitlist").path("rows").get(0);
        assertThat(offer.path("status").asText()).isEqualTo("OFFERED");
        assertThat(offer.path("position").asInt()).isEqualTo(1);
        postJson(STUDENT, "/lab/reservations", request, "g1-no-jumping", 409);
        postJson(STUDENT, "/lab/reservation-waitlist/" + queuedId + "/commands/confirm", Map.of("expectedVersion", offer.path("version").asInt()), null, 404);
        JsonNode claimed = postJson(candidate, "/lab/reservation-waitlist/" + queuedId + "/commands/confirm",
                Map.of("expectedVersion", offer.path("version").asInt()), null, 200).path("data");
        assertThat(claimed.path("status").asText()).isEqualTo("PENDING");
        assertThat(postJson(candidate, "/lab/reservation-waitlist/" + queuedId + "/commands/confirm",
                Map.of("expectedVersion", offer.path("version").asInt()), null, 200).path("data").path("id").asText())
                .isEqualTo(claimed.path("id").asText());
        definition.put("closedDays", List.of(Map.of("date", start.toLocalDate().toString(), "reason", "停机校准")));
        long newer = postJson(MANAGER, "/lab/reservation-rules", draft, null, 200).path("data").path("id").asLong();
        postJson(MANAGER, "/lab/reservation-rules/" + newer + "/commands/publish", Map.of("expectedVersion", 0), null, 200);
        assertThat(getJson(candidate, "/lab/reservations/" + claimed.path("id").asText()).path("data").path("ruleVersionId").asLong()).isEqualTo(ruleId);
        JsonNode calendar = getJson(candidate, "/lab/reservation-rules/calendar?deviceId=95100&from=" + start.toLocalDate() + "&to=" + start.toLocalDate()).path("data");
        assertThat(calendar.path("days").get(0).path("open").asBoolean()).isFalse();
        assertThat(calendar.path("days").get(0).path("closedReason").asText()).isEqualTo("停机校准");
        postJson(candidate, "/lab/reservations", request, "g1-closed", 409);
        JsonNode trace = getJson(candidate, "/lab/reservations/" + claimed.path("id").asText() + "/trace").path("data");
        assertThat(trace.path("reservation").path("id").asText()).isEqualTo(claimed.path("id").asText());
    }

    private void addUser(long id, String name, String role)
    {
        jdbc.update("insert into sys_user(user_id,dept_id,user_name,nick_name,status,del_flag) values(?,100,?,?,'0','0')", id, name, name);
        jdbc.update("insert into sys_user_role(user_id,role_id) select ?,role_id from sys_role where role_key=?", id, role);
    }

    private LoginUser login(long id)
    {
        SysUser account = users.selectUserById(id);
        return new LoginUser(id, account.getDeptId(), account, id == 1 ? Set.of("*:*:*") : permissions.getMenuPermission(account));
    }

    private JsonNode getJson(long actor, String path) throws Exception
    {
        return json.readTree(mvc.perform(get(path).with(user(login(actor))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private JsonNode postJson(long actor, String path, Object body, String key, int status) throws Exception
    {
        var request = post(path).with(user(login(actor))).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body));
        if (key != null) { request.header("X-Idempotency-Key", key); }
        return json.readTree(mvc.perform(request).andExpect(status().is(status))
                .andReturn().getResponse().getContentAsString());
    }
}
