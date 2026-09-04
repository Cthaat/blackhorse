package com.ruoyi.web.core.demo;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.ruoyi.lab.storage.AttachmentPolicy;
import com.ruoyi.lab.storage.AttachmentPolicy.ValidatedAttachment;
import com.ruoyi.lab.storage.StorageService;
import com.ruoyi.lab.storage.StoredObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Opt-in, fail-closed local demonstration data. Every identity is preflighted
 * before the first write; an already complete managed data set is left untouched.
 */
@Component
@Order(200)
public class LabDemoDataInitializer implements ApplicationRunner
{
    private static final Logger LOG = LoggerFactory.getLogger(LabDemoDataInitializer.class);

    private static final String ENABLED_VARIABLE = "LAB_DEMO_DATA_ENABLED";
    private static final Profiles PRODUCTION_PROFILE = Profiles.of("prod");
    private static final String ACCOUNT_MARKER = "LAB_DEMO_ACCOUNT_V1";
    private static final String DATA_MARKER = "lab-demo-data-v1";
    private static final long DEMO_DEPARTMENT_ID = 103L;
    private static final long SYSTEM_OPERATOR_ID = 9000L;
    private static final long SYSTEM_OPERATOR_CONFIG_ID = 100L;
    private static final String SYSTEM_OPERATOR_CONFIG_KEY = "lab.system.operator-user-id";
    private static final String LOCK_CONFIG_SQL =
            "select config_value from sys_config where config_id=? and config_key=? for update";
    private static final int ROOT_ROW_COUNT = 39;
    private static final String PNG_MIME_TYPE = "image/png";
    private static final byte[] DEMO_PNG = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=");
    private static final String LABORATORY_ATTACHMENT_NAME = "实验室安全须知.png";
    private static final String DEVICE_ATTACHMENT_NAME = "显微镜操作卡.png";
    private static final String QUALIFICATION_ATTACHMENT_NAME = "资格培训证明.png";
    private static final String REPAIR_ATTACHMENT_NAME = "维修现场照片.png";
    private static final String RECTIFICATION_ATTACHMENT_NAME = "整改验收照片.png";

    private static final List<AccountSpec> ACCOUNTS = List.of(
            new AccountSpec("lab_student", 100L, "lab_student"),
            new AccountSpec("lab_manager", 101L, "lab_manager"),
            new AccountSpec("lab_safety_officer", 102L, "lab_safety_officer"),
            new AccountSpec("lab_repair_worker", 103L, "lab_repair_worker"),
            new AccountSpec("lab_system_admin", 104L, "lab_system_admin"));

    private static final List<LaboratorySpec> LABORATORIES = List.of(
            new LaboratorySpec("DEMO-LAB-ANALYSIS", "分析测试中心", "lab_manager",
                    "理工楼 A201", "面向材料与化学样品的公共分析平台", "ENABLED"),
            new LaboratorySpec("DEMO-LAB-MAKER", "智能制造实验室", "lab_manager",
                    "工程训练楼 B106", "用于加工、装配和工程训练的受控场地", "DISABLED"),
            new LaboratorySpec("DEMO-LAB-INNOVATION", "学生创新工作室", "lab_manager",
                    "创新楼 C305", "面向学生项目开放，由实验室管理员承担日常值守", "ENABLED"));

    private static final List<DeviceSpec> DEVICES = List.of(
            new DeviceSpec("DEMO-AST-001", "DEMO-LAB-INNOVATION", "倒置荧光显微镜",
                    "MICROSCOPE", "IX53", "MEDIUM", "C305-01", "lab_manager", "AVAILABLE"),
            new DeviceSpec("DEMO-AST-002", "DEMO-LAB-INNOVATION", "数字示波器",
                    "OSCILLOSCOPE", "MSO2024B", "LOW", "C305-02", "lab_manager", "AVAILABLE"),
            new DeviceSpec("DEMO-AST-003", "DEMO-LAB-INNOVATION", "熔融沉积 3D 打印机",
                    "PRINTER_3D", "Raise3D Pro3", "MEDIUM", "C305-03", "lab_manager", "IN_USE"),
            new DeviceSpec("DEMO-AST-004", "DEMO-LAB-ANALYSIS", "傅里叶红外光谱仪",
                    "SPECTROMETER", "Nicolet iS20", "MEDIUM", "A201-01", "lab_manager", "AVAILABLE"),
            new DeviceSpec("DEMO-AST-005", "DEMO-LAB-ANALYSIS", "高速冷冻离心机",
                    "CENTRIFUGE", "Avanti J-26S", "HIGH", "A201-02", "lab_manager", "FAULT"),
            new DeviceSpec("DEMO-AST-006", "DEMO-LAB-ANALYSIS", "净气型通风橱",
                    "FUME_HOOD", "Captair 321", "HIGH", "A201-03", "lab_manager", "MAINTENANCE"),
            new DeviceSpec("DEMO-AST-007", "DEMO-LAB-ANALYSIS", "环境综合监测仪",
                    "ENV_MONITOR", "EVM-7", "LOW", "A201-04", "lab_manager", "AVAILABLE"),
            new DeviceSpec("DEMO-AST-008", "DEMO-LAB-MAKER", "光纤激光切割机",
                    "LASER_CUTTER", "HSG G3015", "MAJOR", "B106-01", "lab_manager", "FAULT"),
            new DeviceSpec("DEMO-AST-009", "DEMO-LAB-MAKER", "机电综合测试台",
                    "TEST_BENCH", "DLJD-ZH01", "HIGH", "B106-02", "lab_manager", "MAINTENANCE"),
            new DeviceSpec("DEMO-AST-010", "DEMO-LAB-MAKER", "电子焊接工作台",
                    "WELDING_STATION", "Weller WX2", "MEDIUM", "B106-03", "lab_manager", "DISABLED"));

    private static final List<ReservationSpec> RESERVATIONS = List.of(
            new ReservationSpec("DEMO-RSV-PENDING", "DEMO-AST-001", "PENDING", 48, 50, false),
            new ReservationSpec("DEMO-RSV-APPROVED", "DEMO-AST-002", "APPROVED", 24, 26, true),
            new ReservationSpec("DEMO-RSV-REJECTED", "DEMO-AST-004", "REJECTED", -240, -238, true),
            new ReservationSpec("DEMO-RSV-CANCELLED", "DEMO-AST-007", "CANCELLED", 12, 14, false),
            new ReservationSpec("DEMO-RSV-EXPIRED", "DEMO-AST-001", "EXPIRED", -48, -46, false),
            new ReservationSpec("DEMO-RSV-NO-SHOW", "DEMO-AST-002", "NO_SHOW", -24, -22, true),
            new ReservationSpec("DEMO-RSV-CHECKED-OUT", "DEMO-AST-003", "CHECKED_OUT", -1, 2, true),
            new ReservationSpec("DEMO-RSV-COMPLETE-OK", "DEMO-AST-004", "COMPLETED", -168, -166, true),
            new ReservationSpec("DEMO-RSV-COMPLETE-DAMAGE", "DEMO-AST-005", "COMPLETED", -120, -118, true),
            new ReservationSpec("DEMO-RSV-COMPLETE-FAULT", "DEMO-AST-006", "COMPLETED", -72, -70, true));

    private static final List<RepairSpec> REPAIRS = List.of(
            new RepairSpec("DEMO-RPR-WAIT-ASSIGN", "DEMO-AST-005", "ABNORMAL_RETURN",
                    "DEMO-RSV-COMPLETE-DAMAGE", "WAIT_ASSIGN"),
            new RepairSpec("DEMO-RPR-WAIT-REPAIR", "DEMO-AST-008", "ACTIVE_REPORT",
                    null, "WAIT_REPAIR"),
            new RepairSpec("DEMO-RPR-IN-PROGRESS", "DEMO-AST-006", "ABNORMAL_RETURN",
                    "DEMO-RSV-COMPLETE-FAULT", "IN_PROGRESS"),
            new RepairSpec("DEMO-RPR-WAIT-ACCEPT", "DEMO-AST-009", "ACTIVE_REPORT",
                    null, "WAIT_ACCEPTANCE"),
            new RepairSpec("DEMO-RPR-CLOSED", "DEMO-AST-004", "ACTIVE_REPORT",
                    null, "CLOSED"));

    private static final List<PlanSpec> PLANS = List.of(
            new PlanSpec("分析测试中心每日安全巡检", "DEMO-LAB-ANALYSIS", "DAILY", 1,
                    LocalTime.of(9, 0), 180, "ENABLED",
                    List.of(new PlanItemSpec("GAS", "检查气路阀门及气瓶固定", 1),
                            new PlanItemSpec("ELECTRIC", "检查设备电源及接地状态", 2),
                            new PlanItemSpec("PPE", "检查个人防护用品配置", 3))),
            new PlanSpec("创新工作室每周开放检查", "DEMO-LAB-INNOVATION", "WEEKLY", 1,
                    LocalTime.of(14, 0), 240, "ENABLED",
                    List.of(new PlanItemSpec("AISLE", "检查疏散通道是否畅通", 1),
                            new PlanItemSpec("TOOL", "检查工具归位及防护罩", 2))),
            new PlanSpec("智能制造实验室月度停用检查", "DEMO-LAB-MAKER", "MONTHLY", 1,
                    LocalTime.of(10, 0), 360, "DISABLED",
                    List.of(new PlanItemSpec("ESTOP", "检查急停开关功能", 1),
                            new PlanItemSpec("SIGN", "检查停用标识和警戒区域", 2))));

    private static final List<TaskSpec> TASKS = List.of(
            new TaskSpec("DEMO-INS-COMPLETED", "分析测试中心每日安全巡检", -120, 3,
                    "COMPLETED", false),
            new TaskSpec("DEMO-INS-IN-PROGRESS", "分析测试中心每日安全巡检", -1, 4,
                    "IN_PROGRESS", false),
            new TaskSpec("DEMO-INS-PENDING-FUTURE", "创新工作室每周开放检查", 24, 4,
                    "PENDING", false),
            new TaskSpec("DEMO-INS-PENDING-OVERDUE", "分析测试中心每日安全巡检", -48, 3,
                    "PENDING", true));

    private static final List<HazardSpec> HAZARDS = List.of(
            new HazardSpec("DEMO-HZD-PENDING", "DEVICE", "DEMO-AST-005", "LOW",
                    "lab_repair_worker", "PENDING_RECTIFICATION", false),
            new HazardSpec("DEMO-HZD-RECTIFYING", "LABORATORY", "DEMO-LAB-ANALYSIS", "MEDIUM",
                    "lab_manager", "RECTIFYING", true),
            new HazardSpec("DEMO-HZD-PENDING-REVIEW", "DEVICE", "DEMO-AST-008", "HIGH",
                    "lab_manager", "PENDING_REVIEW", false),
            new HazardSpec("DEMO-HZD-CLOSED", "LABORATORY", "DEMO-LAB-MAKER", "MAJOR",
                    "lab_manager", "CLOSED", false));

    private final Environment environment;
    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;
    private final AttachmentPolicy attachmentPolicy;
    private final StorageService storageService;

    public LabDemoDataInitializer(Environment environment, JdbcTemplate jdbcTemplate, Clock clock,
            AttachmentPolicy attachmentPolicy, StorageService storageService)
    {
        this.environment = environment;
        this.jdbcTemplate = jdbcTemplate;
        this.clock = clock;
        this.attachmentPolicy = attachmentPolicy;
        this.storageService = storageService;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments arguments)
    {
        if (!"true".equalsIgnoreCase(environment.getProperty(ENABLED_VARIABLE)))
        {
            return;
        }
        if (environment.acceptsProfiles(PRODUCTION_PROFILE))
        {
            throw new IllegalStateException("Demo data must not be enabled in production.");
        }
        requireTransaction();
        lockAndValidateSystemOperator();
        DemoUsers users = validateDemoUsers();
        validateDeviceCategories();
        PreflightResult preflight = preflight(users);
        if (preflight.existing())
        {
            return;
        }

        LocalDateTime capturedNow = LocalDateTime.now(clock);
        LocalDateTime now = capturedNow.withNano(capturedNow.getNano() / 1_000_000 * 1_000_000);
        SeedIds ids = preflight.ids();
        seedLaboratories(ids, users, now);
        seedDevices(ids, users, now);
        seedQualifications(ids, users, now);
        seedReservations(ids, users, now);
        seedUsageRecords(ids, users, now);
        seedRepairOrders(ids, users, now);
        linkUsageRepairs(ids, now);
        seedInspectionPlans(ids, users, now);
        seedInspectionTasksAndItems(ids, users, now);
        seedHazardsAndRectifications(ids, users, now);
        seedStatusHistories(ids, users, now);
        seedNotifications(ids, users, now);
        seedAttachments(ids, now);
    }

    private void requireTransaction()
    {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.isSynchronizationActive())
        {
            throw new IllegalStateException("Demo data initialization requires an active transaction.");
        }
    }

    private void lockAndValidateSystemOperator()
    {
        List<String> values = jdbcTemplate.queryForList(LOCK_CONFIG_SQL, String.class,
                SYSTEM_OPERATOR_CONFIG_ID, SYSTEM_OPERATOR_CONFIG_KEY);
        if (!List.of(Long.toString(SYSTEM_OPERATOR_ID)).equals(values))
        {
            throw new IllegalStateException("Required system operator configuration is unavailable.");
        }
        Integer operatorCount = jdbcTemplate.queryForObject(
                "select count(*) from sys_user where user_id=? and user_name=? and status='1' and del_flag='0'",
                Integer.class, SYSTEM_OPERATOR_ID, "__lab_system_operator__");
        if (!Integer.valueOf(1).equals(operatorCount))
        {
            throw new IllegalStateException("Reserved system operator account is unavailable.");
        }
    }

    private DemoUsers validateDemoUsers()
    {
        Map<String, Long> userIds = new LinkedHashMap<>();
        for (AccountSpec spec : ACCOUNTS)
        {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "select user_id,remark,status,del_flag from sys_user where user_name=?",
                    spec.userName());
            if (rows.size() != 1)
            {
                throw new IllegalStateException("Demo account identity is missing or ambiguous: " + spec.userName());
            }
            Map<String, Object> user = rows.get(0);
            if (!ACCOUNT_MARKER.equals(text(user, "remark"))
                    || !"0".equals(text(user, "status"))
                    || !"0".equals(text(user, "del_flag")))
            {
                throw new IllegalStateException("Demo account is not managed and active: " + spec.userName());
            }
            long userId = number(user, "user_id");
            List<Map<String, Object>> roles = jdbcTemplate.queryForList(
                    "select r.role_id,r.role_key,r.status,r.del_flag "
                            + "from sys_user_role ur join sys_role r on r.role_id=ur.role_id "
                            + "where ur.user_id=?",
                    userId);
            if (roles.size() != 1 || number(roles.get(0), "role_id") != spec.roleId()
                    || !spec.roleKey().equals(text(roles.get(0), "role_key"))
                    || !"0".equals(text(roles.get(0), "status"))
                    || !"0".equals(text(roles.get(0), "del_flag")))
            {
                throw new IllegalStateException("Demo account role mapping is invalid: " + spec.userName());
            }
            Integer postCount = jdbcTemplate.queryForObject(
                    "select count(*) from sys_user_post where user_id=?", Integer.class, userId);
            if (!Integer.valueOf(0).equals(postCount))
            {
                throw new IllegalStateException("Demo account unexpectedly has a post: " + spec.userName());
            }
            userIds.put(spec.userName(), userId);
        }
        return new DemoUsers(Map.copyOf(userIds));
    }

    private void validateDeviceCategories()
    {
        Integer typeCount = jdbcTemplate.queryForObject(
                "select count(*) from sys_dict_type where dict_id=? and dict_type=? and status='0'",
                Integer.class, 504L, "lab_device_category");
        if (!Integer.valueOf(1).equals(typeCount))
        {
            throw new IllegalStateException("Required device-category dictionary is unavailable.");
        }
        for (DeviceSpec device : DEVICES)
        {
            Integer valueCount = jdbcTemplate.queryForObject(
                    "select count(*) from sys_dict_data where dict_type=? and dict_value=? and status='0'",
                    Integer.class, "lab_device_category", device.categoryCode());
            if (!Integer.valueOf(1).equals(valueCount))
            {
                throw new IllegalStateException("Required device category is unavailable: "
                        + device.categoryCode());
            }
        }
    }

    private PreflightResult preflight(DemoUsers users)
    {
        attachmentPolicy.validate("演示附件.png", PNG_MIME_TYPE, DEMO_PNG, 0);
        SeedIds ids = new SeedIds();
        int present = 0;
        present += loadManagedRoots("lab_laboratory", "lab_code",
                LABORATORIES.stream().map(LaboratorySpec::code).toList(), ids.laboratories);
        present += loadManagedRoots("lab_device", "asset_no",
                DEVICES.stream().map(DeviceSpec::assetNo).toList(), ids.devices);
        present += loadManagedRoots("lab_reservation", "reservation_no",
                RESERVATIONS.stream().map(ReservationSpec::reservationNo).toList(), ids.reservations);
        present += loadManagedRoots("lab_repair_order", "repair_no",
                REPAIRS.stream().map(RepairSpec::repairNo).toList(), ids.repairs);
        present += loadManagedRoots("lab_inspection_plan", "plan_name",
                PLANS.stream().map(PlanSpec::planName).toList(), ids.plans);
        present += loadManagedRoots("lab_inspection_task", "task_no",
                TASKS.stream().map(TaskSpec::taskNo).toList(), ids.tasks);
        present += loadManagedRoots("lab_hazard", "hazard_no",
                HAZARDS.stream().map(HazardSpec::hazardNo).toList(), ids.hazards);

        if (present == 0)
        {
            validateFreshPreflight(users);
            return new PreflightResult(false, ids);
        }
        if (present != ROOT_ROW_COUNT)
        {
            throw new IllegalStateException("Refusing a partial laboratory demo data set: "
                    + present + " of " + ROOT_ROW_COUNT + " root rows exist.");
        }
        validateExistingChildren(ids, users);
        return new PreflightResult(true, ids);
    }

    private int loadManagedRoots(String table, String keyColumn, List<String> keys,
            Map<String, Long> destination)
    {
        for (String key : keys)
        {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "select id,create_by,del_flag from " + table + " where " + keyColumn + "=?", key);
            if (rows.size() > 1)
            {
                throw new IllegalStateException("Demo natural key is ambiguous: " + table + "." + keyColumn);
            }
            if (rows.isEmpty())
            {
                continue;
            }
            Map<String, Object> row = rows.get(0);
            requireOwnedActive(row, table + "." + keyColumn + "=" + key);
            destination.put(key, number(row, "id"));
        }
        return destination.size();
    }

    private void validateFreshPreflight(DemoUsers users)
    {
        for (String table : List.of("lab_qualification", "lab_usage_record", "lab_attachment",
                "lab_notification"))
        {
            Integer count = jdbcTemplate.queryForObject(
                    "select count(*) from " + table + " where create_by=?", Integer.class, DATA_MARKER);
            if (!Integer.valueOf(0).equals(count))
            {
                throw new IllegalStateException("Refusing residual managed demo rows in " + table);
            }
        }
        Integer historyCount = jdbcTemplate.queryForObject(
                "select count(*) from lab_status_history where trace_id like ?", Integer.class, "DEMO-H-%");
        Integer welcomeCount = jdbcTemplate.queryForObject(
                "select count(*) from lab_notification where dedupe_key like ?", Integer.class,
                "demo:welcome:%");
        Integer receiverNotificationCount = jdbcTemplate.queryForObject(
                "select count(*) from lab_notification where receiver_id in (?,?,?,?,?)",
                Integer.class, users.id("lab_student"), users.id("lab_manager"),
                users.id("lab_safety_officer"), users.id("lab_repair_worker"),
                users.id("lab_system_admin"));
        Integer qualificationCollision = jdbcTemplate.queryForObject(
                "select count(*) from lab_qualification where user_id=?",
                Integer.class, users.id("lab_student"));
        Integer attachmentNameCount = jdbcTemplate.queryForObject(
                "select count(*) from lab_attachment where original_name in (?,?,?,?,?)",
                Integer.class, LABORATORY_ATTACHMENT_NAME, DEVICE_ATTACHMENT_NAME,
                QUALIFICATION_ATTACHMENT_NAME, REPAIR_ATTACHMENT_NAME,
                RECTIFICATION_ATTACHMENT_NAME);
        if (!Integer.valueOf(0).equals(historyCount) || !Integer.valueOf(0).equals(welcomeCount)
                || !Integer.valueOf(0).equals(receiverNotificationCount)
                || !Integer.valueOf(0).equals(qualificationCollision)
                || !Integer.valueOf(0).equals(attachmentNameCount))
        {
            throw new IllegalStateException("Reserved demo child identities already exist.");
        }
    }

    private void validateExistingChildren(SeedIds ids, DemoUsers users)
    {
        loadExistingQualifications(ids, users);
        for (String reservationKey : List.of("DEMO-RSV-CHECKED-OUT", "DEMO-RSV-COMPLETE-OK",
                "DEMO-RSV-COMPLETE-DAMAGE", "DEMO-RSV-COMPLETE-FAULT"))
        {
            ids.usage.put(reservationKey, requireOwnedChild(
                    "select id,create_by,del_flag from lab_usage_record where reservation_id=?",
                    "usage for " + reservationKey, ids.reservations.get(reservationKey)));
        }
        loadExistingPlanItems(ids);
        loadExistingInspectionItems(ids);
        ids.rectifications.put("DEMO-HZD-RECTIFYING", requireActiveChild(
                "select id,del_flag from lab_rectification where hazard_id=? and round_no=1",
                "rectification round for DEMO-HZD-RECTIFYING", ids.hazards.get("DEMO-HZD-RECTIFYING")));
        ids.rectifications.put("DEMO-HZD-PENDING-REVIEW", requireActiveChild(
                "select id,del_flag from lab_rectification where hazard_id=? and round_no=1",
                "rectification round for DEMO-HZD-PENDING-REVIEW", ids.hazards.get("DEMO-HZD-PENDING-REVIEW")));
        ids.rectifications.put("DEMO-HZD-CLOSED", requireActiveChild(
                "select id,del_flag from lab_rectification where hazard_id=? and round_no=1",
                "rectification round for DEMO-HZD-CLOSED", ids.hazards.get("DEMO-HZD-CLOSED")));

        for (HistorySubject subject : historySubjects(ids, users))
        {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "select id,object_type,object_id,to_status,trace_id,del_flag "
                            + "from lab_status_history where trace_id=?",
                    subject.traceId());
            if (rows.size() != 1)
            {
                throw new IllegalStateException("Managed demo history is missing or ambiguous: "
                        + subject.traceId());
            }
            Map<String, Object> row = rows.get(0);
            if (!"0".equals(text(row, "del_flag"))
                    || !subject.objectType().equals(text(row, "object_type"))
                    || subject.objectId() != number(row, "object_id")
                    || !subject.toStatus().equals(text(row, "to_status")))
            {
                throw new IllegalStateException("Managed demo history identity has drifted: "
                        + subject.traceId());
            }
            ids.histories.put(subject.traceId(), number(row, "id"));
        }
        for (NotificationSpec notification : notificationSpecs(ids, users))
        {
            requireOwnedChild(
                    "select id,create_by,'0' as del_flag from lab_notification where dedupe_key=?",
                    "notification " + notification.dedupeKey(), notification.dedupeKey());
        }
        verifyExistingAttachments(ids);
    }

    private void loadExistingQualifications(SeedIds ids, DemoUsers users)
    {
        long student = users.id("lab_student");
        ids.qualifications.put("VALID", requireOwnedChild(
                "select id,create_by,del_flag from lab_qualification "
                        + "where user_id=? and laboratory_id=? "
                        + "and scope_type='LABORATORY' and scope_id=?",
                "valid qualification", student, ids.laboratories.get("DEMO-LAB-INNOVATION"),
                Long.toString(ids.laboratories.get("DEMO-LAB-INNOVATION"))));
        ids.qualifications.put("NOT_EFFECTIVE", requireOwnedChild(
                "select id,create_by,del_flag from lab_qualification "
                        + "where user_id=? and laboratory_id=? "
                        + "and scope_type='DEVICE_CATEGORY' and scope_id=?",
                "not-effective qualification", student,
                ids.laboratories.get("DEMO-LAB-INNOVATION"), "MICROSCOPE"));
        ids.qualifications.put("EXPIRED", requireOwnedChild(
                "select id,create_by,del_flag from lab_qualification "
                        + "where user_id=? and laboratory_id=? "
                        + "and scope_type='LABORATORY' and scope_id=?",
                "expired qualification", student, ids.laboratories.get("DEMO-LAB-ANALYSIS"),
                Long.toString(ids.laboratories.get("DEMO-LAB-ANALYSIS"))));
        ids.qualifications.put("REVOKED", requireOwnedChild(
                "select id,create_by,del_flag from lab_qualification "
                        + "where user_id=? and laboratory_id=? "
                        + "and scope_type='DEVICE_CATEGORY' and scope_id=?",
                "revoked qualification", student,
                ids.laboratories.get("DEMO-LAB-ANALYSIS"), "SPECTROMETER"));
    }

    private void loadExistingPlanItems(SeedIds ids)
    {
        for (PlanSpec plan : PLANS)
        {
            for (PlanItemSpec item : plan.items())
            {
                String key = plan.planName() + "/" + item.itemCode();
                ids.planItems.put(key, requireActiveChild(
                        "select id,del_flag from lab_inspection_plan_item where plan_id=? and item_code=?",
                        "plan item " + key, ids.plans.get(plan.planName()), item.itemCode()));
            }
        }
    }

    private void loadExistingInspectionItems(SeedIds ids)
    {
        for (TaskSpec task : TASKS)
        {
            PlanSpec plan = plan(task.planName());
            for (PlanItemSpec item : plan.items())
            {
                String planItemKey = plan.planName() + "/" + item.itemCode();
                String taskItemKey = task.taskNo() + "/" + item.itemCode();
                ids.inspectionItems.put(taskItemKey, requireActiveChild(
                        "select id,del_flag from lab_inspection_item where task_id=? and plan_item_id=?",
                        "inspection item " + taskItemKey, ids.tasks.get(task.taskNo()),
                        ids.planItems.get(planItemKey)));
            }
        }
    }

    private long requireOwnedChild(String sql, String identity, Object... arguments)
    {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, arguments);
        if (rows.size() != 1)
        {
            throw new IllegalStateException("Managed demo child is missing or ambiguous: " + identity);
        }
        requireOwnedActive(rows.get(0), identity);
        return number(rows.get(0), "id");
    }

    private long requireActiveChild(String sql, String identity, Object... arguments)
    {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, arguments);
        if (rows.size() != 1 || !"0".equals(text(rows.get(0), "del_flag")))
        {
            throw new IllegalStateException("Managed demo child is missing, deleted, or ambiguous: " + identity);
        }
        return number(rows.get(0), "id");
    }

    private void requireOwnedActive(Map<String, Object> row, String identity)
    {
        if (!DATA_MARKER.equals(text(row, "create_by")) || !"0".equals(text(row, "del_flag")))
        {
            throw new IllegalStateException("Refusing to take over an unmanaged or deleted row: " + identity);
        }
    }

    private void seedLaboratories(SeedIds ids, DemoUsers users, LocalDateTime now)
    {
        String sql = "insert into lab_laboratory "
                + "(lab_code,name,dept_id,manager_id,location,description,status,version,create_by,create_time,update_by,update_time,del_flag) "
                + "values (?,?,?,?,?,?,?,0,?,?,?,?,'0')";
        for (LaboratorySpec lab : LABORATORIES)
        {
            long id = insert(sql, lab.code(), lab.name(), DEMO_DEPARTMENT_ID,
                    users.id(lab.managerUser()), lab.location(), lab.description(), lab.status(),
                    DATA_MARKER, now.minusDays(60), DATA_MARKER, now.minusDays(60));
            ids.laboratories.put(lab.code(), id);
        }
    }

    private void seedDevices(SeedIds ids, DemoUsers users, LocalDateTime now)
    {
        String sql = "insert into lab_device "
                + "(asset_no,laboratory_id,name,category_code,model,risk_level,location,manager_id,description,status,version,create_by,create_time,update_by,update_time,del_flag) "
                + "values (?,?,?,?,?,?,?,?,?,?,0,?,?,?,?,'0')";
        for (DeviceSpec device : DEVICES)
        {
            long id = insert(sql, device.assetNo(), ids.laboratories.get(device.laboratoryCode()),
                    device.name(), device.categoryCode(), device.model(), device.riskLevel(),
                    device.location(), users.id(device.managerUser()), "本地演示设备，资产信息均为虚构",
                    device.status(), DATA_MARKER, now.minusDays(45), DATA_MARKER, now.minusDays(2));
            ids.devices.put(device.assetNo(), id);
        }
    }

    private void seedQualifications(SeedIds ids, DemoUsers users, LocalDateTime now)
    {
        long student = users.id("lab_student");
        ids.qualifications.put("VALID", insertQualification(student,
                ids.laboratories.get("DEMO-LAB-INNOVATION"), "LABORATORY",
                Long.toString(ids.laboratories.get("DEMO-LAB-INNOVATION")),
                now.minusDays(30), now.plusDays(90), null, null, now));
        ids.qualifications.put("NOT_EFFECTIVE", insertQualification(student,
                ids.laboratories.get("DEMO-LAB-INNOVATION"), "DEVICE_CATEGORY",
                "MICROSCOPE", now.plusDays(7), now.plusDays(180), null, null, now));
        ids.qualifications.put("EXPIRED", insertQualification(student,
                ids.laboratories.get("DEMO-LAB-ANALYSIS"), "LABORATORY",
                Long.toString(ids.laboratories.get("DEMO-LAB-ANALYSIS")),
                now.minusDays(180), now.minusDays(1), null, null, now));
        ids.qualifications.put("REVOKED", insertQualification(student,
                ids.laboratories.get("DEMO-LAB-ANALYSIS"), "DEVICE_CATEGORY",
                "SPECTROMETER", now.minusDays(30), now.plusDays(90), now.minusDays(1),
                "演示：复训到期前暂停资格", now));
    }

    private long insertQualification(long student, long laboratoryId, String scopeType, String scopeId,
            LocalDateTime validFrom, LocalDateTime validUntil, LocalDateTime revokedAt,
            String revokeReason, LocalDateTime now)
    {
        return insert("insert into lab_qualification "
                        + "(user_id,laboratory_id,scope_type,scope_id,valid_from,valid_until,revoked_at,revoke_reason,version,create_by,create_time,update_by,update_time,del_flag) "
                        + "values (?,?,?,?,?,?,?,?,0,?,?,?,?,'0')",
                student, laboratoryId, scopeType, scopeId, validFrom, validUntil, revokedAt, revokeReason,
                DATA_MARKER, now.minusDays(20), DATA_MARKER, now.minusDays(1));
    }

    private void seedReservations(SeedIds ids, DemoUsers users, LocalDateTime now)
    {
        String sql = "insert into lab_reservation "
                + "(reservation_no,device_id,applicant_id,start_time,end_time,purpose,remark,status,approval_by,approval_time,approval_reason,cancel_time,cancel_reason,idempotency_key,request_hash,idempotency_expires_at,version,create_by,create_time,update_by,update_time,del_flag) "
                + "values (?,?,?,?,?,?,?,?,?,?,?,?,?,null,null,null,0,?,?,?,?,'0')";
        for (ReservationSpec reservation : RESERVATIONS)
        {
            LocalDateTime start = now.plusHours(reservation.startHours());
            LocalDateTime end = now.plusHours(reservation.endHours());
            Long approvalBy = reservation.decisionRecorded() ? users.id("lab_manager") : null;
            LocalDateTime approvalTime = reservation.decisionRecorded()
                    ? (start.isAfter(now) ? now.minusHours(2) : start.minusHours(12)) : null;
            String approvalReason = null;
            if (reservation.decisionRecorded())
            {
                approvalReason = "REJECTED".equals(reservation.status())
                        ? "演示：样品信息不完整，预约未通过" : "演示：安全条件核验通过";
            }
            LocalDateTime cancelTime = "CANCELLED".equals(reservation.status()) ? now.minusHours(6) : null;
            String cancelReason = cancelTime == null ? null : "演示：实验安排调整，申请人主动取消";
            LocalDateTime createdAt = start.isAfter(now) ? now.minusDays(2) : start.minusDays(2);
            long id = insert(sql, reservation.reservationNo(), ids.devices.get(reservation.deviceKey()),
                    users.id("lab_student"), start, end, "课程项目样品测试与数据采集",
                    "本记录由本地演示数据初始化器创建", reservation.status(), approvalBy,
                    approvalTime, approvalReason, cancelTime, cancelReason, DATA_MARKER,
                    createdAt, DATA_MARKER, now.minusHours(1));
            ids.reservations.put(reservation.reservationNo(), id);
        }
    }

    private void seedUsageRecords(SeedIds ids, DemoUsers users, LocalDateTime now)
    {
        seedUsage(ids, users, now, "DEMO-RSV-CHECKED-OUT", null, null, 0);
        seedUsage(ids, users, now, "DEMO-RSV-COMPLETE-OK", "NORMAL",
                "设备清洁后正常归还", 0);
        seedUsage(ids, users, now, "DEMO-RSV-COMPLETE-DAMAGE", "DAMAGED",
                "转子外观损伤，已停止使用并报修", 18);
        seedUsage(ids, users, now, "DEMO-RSV-COMPLETE-FAULT", "FAULT",
                "风速报警持续出现，已转维修处理", 5);
    }

    private void seedUsage(SeedIds ids, DemoUsers users, LocalDateTime now,
            String reservationKey, String returnCondition, String returnNote, int overdueMinutes)
    {
        ReservationSpec reservation = reservation(reservationKey);
        LocalDateTime checkout = now.plusHours(reservation.startHours()).minusMinutes(5);
        LocalDateTime returned = returnCondition == null ? null
                : now.plusHours(reservation.endHours()).minusMinutes(10);
        Long returnOperator = returned == null ? null : users.id("lab_manager");
        long id = insert("insert into lab_usage_record "
                        + "(reservation_id,device_id,user_id,checkout_operator_id,checked_out_at,checkout_note,returned_at,return_operator_id,return_condition,return_note,repair_order_id,overdue_minutes,version,create_by,create_time,update_by,update_time,del_flag) "
                        + "values (?,?,?,?,?,?,?,?,?,?,null,?,0,?,?,?,?,'0')",
                ids.reservations.get(reservationKey), ids.devices.get(reservation.deviceKey()),
                users.id("lab_student"), users.id("lab_manager"), checkout,
                "演示领用：已核对预约与设备外观", returned, returnOperator, returnCondition,
                returnNote, overdueMinutes, DATA_MARKER, checkout, DATA_MARKER, returned);
        ids.usage.put(reservationKey, id);
    }

    private void seedRepairOrders(SeedIds ids, DemoUsers users, LocalDateTime now)
    {
        String sql = "insert into lab_repair_order "
                + "(repair_no,device_id,source_type,source_id,reporter_id,fault_description,assignee_id,assigned_by,assigned_at,started_at,repair_result,result_submitted_at,acceptance_result,acceptance_reason,accepted_by,accepted_at,status,version,create_by,create_time,update_by,update_time,del_flag) "
                + "values (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,0,?,?,?,?,'0')";
        for (RepairSpec repair : REPAIRS)
        {
            boolean assigned = !"WAIT_ASSIGN".equals(repair.status());
            boolean started = List.of("IN_PROGRESS", "WAIT_ACCEPTANCE", "CLOSED").contains(repair.status());
            boolean resultSubmitted = List.of("WAIT_ACCEPTANCE", "CLOSED").contains(repair.status());
            boolean closed = "CLOSED".equals(repair.status());
            Long sourceId = repair.sourceUsageKey() == null ? null : ids.usage.get(repair.sourceUsageKey());
            LocalDateTime createdAt = repair.sourceUsageKey() == null ? now.minusDays(5)
                    : now.plusHours(reservation(repair.sourceUsageKey()).endHours()).plusMinutes(5);
            Long assignee = assigned ? users.id("lab_repair_worker") : null;
            Long assignedBy = assigned ? users.id("lab_manager") : null;
            LocalDateTime assignedAt = assigned ? createdAt.plusHours(8) : null;
            LocalDateTime startedAt = started ? createdAt.plusHours(16) : null;
            String result = resultSubmitted ? "已完成故障点处理和空载试运行，关键参数恢复正常" : null;
            LocalDateTime submittedAt = resultSubmitted ? createdAt.plusHours(30) : null;
            LocalDateTime acceptedAt = closed ? createdAt.plusHours(40) : null;
            long id = insert(sql, repair.repairNo(), ids.devices.get(repair.deviceKey()),
                    repair.sourceType(), sourceId, users.id("lab_student"),
                    "演示故障：运行中出现异常告警，需要按规程排查", assignee, assignedBy,
                    assignedAt, startedAt, result, submittedAt, closed ? "PASSED" : null,
                    closed ? "功能与安全联锁复核通过" : null,
                    closed ? users.id("lab_manager") : null, closed ? acceptedAt : null,
                    repair.status(), DATA_MARKER, createdAt, DATA_MARKER,
                    closed ? acceptedAt : now.minusHours(2));
            ids.repairs.put(repair.repairNo(), id);
        }
    }

    private void linkUsageRepairs(SeedIds ids, LocalDateTime now)
    {
        linkUsageRepair(ids, "DEMO-RSV-COMPLETE-DAMAGE", "DEMO-RPR-WAIT-ASSIGN", now);
        linkUsageRepair(ids, "DEMO-RSV-COMPLETE-FAULT", "DEMO-RPR-IN-PROGRESS", now);
    }

    private void linkUsageRepair(SeedIds ids, String usageKey, String repairKey, LocalDateTime now)
    {
        int rows = jdbcTemplate.update(
                "update lab_usage_record set repair_order_id=?,update_by=?,update_time=? "
                        + "where id=? and repair_order_id is null and del_flag='0'",
                ids.repairs.get(repairKey), DATA_MARKER, now, ids.usage.get(usageKey));
        requireSingleRow(rows, "link usage and repair " + usageKey);
    }

    private void seedInspectionPlans(SeedIds ids, DemoUsers users, LocalDateTime now)
    {
        String sql = "insert into lab_inspection_plan "
                + "(plan_name,laboratory_id,frequency_type,interval_value,execute_time,day_of_week,day_of_month,next_run_at,owner_id,deadline_rule,deadline_offset_minutes,status,version,create_by,create_time,update_by,update_time,del_flag) "
                + "values (?,?,?,?,?,?,?,?,?,?,?, ?,0,?,?,?,?,'0')";
        for (PlanSpec plan : PLANS)
        {
            LocalDateTime nextRun = switch (plan.frequencyType())
            {
                case "DAILY" -> now.toLocalDate().plusDays(1).atTime(plan.executeTime());
                case "WEEKLY" -> now.toLocalDate().plusDays(7).atTime(plan.executeTime());
                case "MONTHLY" -> now.toLocalDate().plusMonths(1).atTime(plan.executeTime());
                default -> throw new IllegalStateException("Unsupported demo frequency");
            };
            Integer dayOfWeek = "WEEKLY".equals(plan.frequencyType())
                    ? nextRun.getDayOfWeek().getValue() : null;
            Integer dayOfMonth = "MONTHLY".equals(plan.frequencyType())
                    ? nextRun.getDayOfMonth() : null;
            long planId = insert(sql, plan.planName(), ids.laboratories.get(plan.laboratoryCode()),
                    plan.frequencyType(), plan.intervalValue(), plan.executeTime(), dayOfWeek, dayOfMonth,
                    nextRun, users.id("lab_safety_officer"), "AFTER_SCHEDULED",
                    plan.deadlineOffsetMinutes(), plan.status(), DATA_MARKER, now.minusDays(30),
                    DATA_MARKER, now.minusDays(1));
            ids.plans.put(plan.planName(), planId);
            for (PlanItemSpec item : plan.items())
            {
                long itemId = insert("insert into lab_inspection_plan_item "
                                + "(plan_id,item_code,content,sort_order,enabled,create_time,update_time,del_flag) "
                                + "values (?,?,?,?,'1',?,null,'0')",
                        planId, item.itemCode(), item.content(), item.sortOrder(), now.minusDays(30));
                ids.planItems.put(plan.planName() + "/" + item.itemCode(), itemId);
            }
        }
    }

    private void seedInspectionTasksAndItems(SeedIds ids, DemoUsers users, LocalDateTime now)
    {
        for (TaskSpec task : TASKS)
        {
            PlanSpec plan = plan(task.planName());
            LocalDateTime scheduled = now.plusHours(task.scheduledOffsetHours());
            LocalDateTime deadline = scheduled.plusHours(task.deadlineOffsetHours());
            LocalDateTime startedAt = List.of("IN_PROGRESS", "COMPLETED").contains(task.status())
                    ? scheduled.plusMinutes(10) : null;
            LocalDateTime completedAt = "COMPLETED".equals(task.status())
                    ? scheduled.plusHours(1) : null;
            LocalDateTime overdueSetAt = task.overdue() ? now.minusDays(1) : null;
            LocalDateTime createdAt = scheduled.isAfter(now) ? now.minusHours(1)
                    : scheduled.minusHours(1);
            long taskId = insert("insert into lab_inspection_task "
                            + "(task_no,plan_id,laboratory_id,scheduled_at,deadline_at,assignee_id,status,overdue_flag,overdue_set_at,overdue_event_version,started_at,completed_at,version,create_by,create_time,update_by,update_time,del_flag) "
                            + "values (?,?,?,?,?,?,?, ?,?,?,?, ?,0,?,?,?,?,'0')",
                    task.taskNo(), ids.plans.get(plan.planName()),
                    ids.laboratories.get(plan.laboratoryCode()), scheduled, deadline,
                    users.id("lab_safety_officer"), task.status(), task.overdue() ? "1" : "0",
                    overdueSetAt, task.overdue() ? 1L : 0L, startedAt, completedAt,
                    DATA_MARKER, createdAt, DATA_MARKER,
                    completedAt == null ? startedAt : completedAt);
            ids.tasks.put(task.taskNo(), taskId);
            int itemIndex = 0;
            for (PlanItemSpec item : plan.items())
            {
                String result = null;
                String description = null;
                String severity = null;
                String targetType = null;
                Long targetId = null;
                Long inspectedBy = null;
                LocalDateTime inspectedAt = null;
                if ("COMPLETED".equals(task.status()))
                {
                    result = List.of("PASS", "FAIL", "NOT_APPLICABLE").get(itemIndex);
                    inspectedBy = users.id("lab_safety_officer");
                    inspectedAt = scheduled.plusMinutes(30 + itemIndex * 5L);
                    if ("FAIL".equals(result))
                    {
                        description = "离心机电源线绝缘层局部磨损";
                        severity = "LOW";
                        targetType = "DEVICE";
                        targetId = ids.devices.get("DEMO-AST-005");
                    }
                }
                else if ("IN_PROGRESS".equals(task.status()) && itemIndex == 0)
                {
                    result = "PASS";
                    inspectedBy = users.id("lab_safety_officer");
                    inspectedAt = now.minusMinutes(20);
                }
                long itemId = insert("insert into lab_inspection_item "
                                + "(task_id,plan_item_id,item_code_snapshot,content_snapshot,sort_order_snapshot,result,description,severity,target_type,target_id,inspected_by,inspected_at,version,create_time,update_time,del_flag) "
                                + "values (?,?,?,?,?,?,?,?,?,?,?,?,0,?,?, '0')",
                        taskId, ids.planItems.get(plan.planName() + "/" + item.itemCode()),
                        item.itemCode(), item.content(), item.sortOrder(), result, description, severity,
                        targetType, targetId, inspectedBy, inspectedAt, createdAt, inspectedAt);
                ids.inspectionItems.put(task.taskNo() + "/" + item.itemCode(), itemId);
                itemIndex++;
            }
        }
    }

    private void seedHazardsAndRectifications(SeedIds ids, DemoUsers users, LocalDateTime now)
    {
        String sql = "insert into lab_hazard "
                + "(hazard_no,source_item_id,related_hazard_id,target_type,target_id,severity,owner_id,deadline,requirements,status,overdue_flag,overdue_set_at,overdue_event_version,version,create_by,create_time,update_by,update_time,del_flag) "
                + "values (?,?,null,?,?,?,?,?,?,?,?,?,?,0,?,?,?,?,'0')";
        for (HazardSpec hazard : HAZARDS)
        {
            Long sourceItem = "DEMO-HZD-PENDING".equals(hazard.hazardNo())
                    ? ids.inspectionItems.get("DEMO-INS-COMPLETED/ELECTRIC") : null;
            long targetId = "DEVICE".equals(hazard.targetType())
                    ? ids.devices.get(hazard.targetKey()) : ids.laboratories.get(hazard.targetKey());
            LocalDateTime deadline = hazard.overdue() ? now.minusDays(1)
                    : ("CLOSED".equals(hazard.status()) ? now.minusDays(2) : now.plusDays(7));
            LocalDateTime createdAt = sourceItem == null ? now.minusDays(6) : now.minusDays(4);
            long id = insert(sql, hazard.hazardNo(), sourceItem, hazard.targetType(), targetId,
                    hazard.severity(), users.id(hazard.ownerUser()), deadline,
                    "按安全操作规程完成隔离、整改并保留复查证据", hazard.status(),
                    hazard.overdue() ? "1" : "0", hazard.overdue() ? now.minusHours(12) : null,
                    hazard.overdue() ? 1L : 0L, DATA_MARKER, createdAt, DATA_MARKER,
                    now.minusHours(3));
            ids.hazards.put(hazard.hazardNo(), id);
        }

        ids.rectifications.put("DEMO-HZD-RECTIFYING", insertRectification(ids, users, now,
                "DEMO-HZD-RECTIFYING", "已清理作业区并补充临时警示标识",
                "REJECTED", "警示标识位置不醒目，请补充固定隔离带"));
        ids.rectifications.put("DEMO-HZD-PENDING-REVIEW", insertRectification(ids, users, now,
                "DEMO-HZD-PENDING-REVIEW", "已更换防护罩联锁开关并上传试运行记录",
                null, null));
        ids.rectifications.put("DEMO-HZD-CLOSED", insertRectification(ids, users, now,
                "DEMO-HZD-CLOSED", "已完成急停回路检测和操作人员复训",
                "PASSED", "现场复查通过，隐患予以销号"));
    }

    private long insertRectification(SeedIds ids, DemoUsers users, LocalDateTime now,
            String hazardKey, String description, String reviewResult, String reviewReason)
    {
        LocalDateTime submittedAt = now.minusDays(3);
        boolean reviewed = reviewResult != null;
        return insert("insert into lab_rectification "
                        + "(hazard_id,round_no,submitter_id,description,submitted_at,reviewer_id,review_result,review_reason,reviewed_at,create_time,update_time,version,del_flag) "
                        + "values (?,1,?,?,?,?,?,?,?,?,?,0,'0')",
                ids.hazards.get(hazardKey), users.id("lab_manager"), description, submittedAt,
                reviewed ? users.id("lab_safety_officer") : null, reviewResult, reviewReason,
                reviewed ? now.minusDays(2) : null, submittedAt, reviewed ? now.minusDays(2) : null);
    }

    private void seedStatusHistories(SeedIds ids, DemoUsers users, LocalDateTime now)
    {
        int sequence = 0;
        for (HistorySubject subject : historySubjects(ids, users))
        {
            long id = insert("insert into lab_status_history "
                            + "(object_type,object_id,from_status,to_status,operator_id,reason,trace_id,create_time,del_flag) "
                            + "values (?,?,null,?,?,?,?,?,'0')",
                    subject.objectType(), subject.objectId(), subject.toStatus(), subject.operatorId(),
                    subject.reason(), subject.traceId(), now.minusMinutes(90).plusMinutes(sequence++));
            ids.histories.put(subject.traceId(), id);
        }
    }

    private List<HistorySubject> historySubjects(SeedIds ids, DemoUsers users)
    {
        List<HistorySubject> subjects = new ArrayList<>();
        for (int index = 0; index < LABORATORIES.size(); index++)
        {
            LaboratorySpec lab = LABORATORIES.get(index);
            subjects.add(new HistorySubject("DEMO-H-LAB-" + twoDigits(index + 1), "LABORATORY",
                    ids.laboratories.get(lab.code()), lab.status(), users.id(lab.managerUser()),
                    "演示实验室建档"));
        }
        for (int index = 0; index < DEVICES.size(); index++)
        {
            DeviceSpec device = DEVICES.get(index);
            subjects.add(new HistorySubject("DEMO-H-DEV-" + twoDigits(index + 1), "DEVICE",
                    ids.devices.get(device.assetNo()), device.status(), users.id(device.managerUser()),
                    "演示设备当前状态快照"));
        }
        List<String> qualificationStates = List.of("VALID", "NOT_EFFECTIVE", "EXPIRED", "REVOKED");
        for (int index = 0; index < qualificationStates.size(); index++)
        {
            String state = qualificationStates.get(index);
            subjects.add(new HistorySubject("DEMO-H-QUAL-" + twoDigits(index + 1), "QUALIFICATION",
                    ids.qualifications.get(state), state, users.id("lab_manager"), "演示资格状态快照"));
        }
        for (int index = 0; index < RESERVATIONS.size(); index++)
        {
            ReservationSpec reservation = RESERVATIONS.get(index);
            long operator = switch (reservation.status())
            {
                case "PENDING", "CANCELLED" -> users.id("lab_student");
                case "EXPIRED", "NO_SHOW" -> SYSTEM_OPERATOR_ID;
                default -> users.id("lab_manager");
            };
            subjects.add(new HistorySubject("DEMO-H-RSV-" + twoDigits(index + 1), "RESERVATION",
                    ids.reservations.get(reservation.reservationNo()), reservation.status(), operator,
                    "演示预约状态快照"));
        }
        for (int index = 0; index < REPAIRS.size(); index++)
        {
            RepairSpec repair = REPAIRS.get(index);
            long operator = switch (repair.status())
            {
                case "WAIT_ASSIGN" -> users.id("lab_student");
                case "WAIT_REPAIR" -> users.id("lab_manager");
                case "IN_PROGRESS", "WAIT_ACCEPTANCE" -> users.id("lab_repair_worker");
                default -> users.id("lab_manager");
            };
            subjects.add(new HistorySubject("DEMO-H-RPR-" + twoDigits(index + 1), "REPAIR_ORDER",
                    ids.repairs.get(repair.repairNo()), repair.status(), operator,
                    "演示维修状态快照"));
        }
        for (int index = 0; index < PLANS.size(); index++)
        {
            PlanSpec plan = PLANS.get(index);
            subjects.add(new HistorySubject("DEMO-H-PLAN-" + twoDigits(index + 1), "INSPECTION_PLAN",
                    ids.plans.get(plan.planName()), plan.status(), users.id("lab_safety_officer"),
                    "演示巡检计划状态快照"));
        }
        for (int index = 0; index < TASKS.size(); index++)
        {
            TaskSpec task = TASKS.get(index);
            long operator = "PENDING".equals(task.status()) ? SYSTEM_OPERATOR_ID
                    : users.id("lab_safety_officer");
            subjects.add(new HistorySubject("DEMO-H-TASK-" + twoDigits(index + 1), "INSPECTION_TASK",
                    ids.tasks.get(task.taskNo()), task.status(), operator, "演示巡检任务状态快照"));
        }
        for (int index = 0; index < HAZARDS.size(); index++)
        {
            HazardSpec hazard = HAZARDS.get(index);
            long operator = "PENDING_RECTIFICATION".equals(hazard.status())
                    ? users.id("lab_safety_officer") : users.id(hazard.ownerUser());
            subjects.add(new HistorySubject("DEMO-H-HZD-" + twoDigits(index + 1), "HAZARD",
                    ids.hazards.get(hazard.hazardNo()), hazard.status(), operator,
                    "演示隐患状态快照"));
        }
        return List.copyOf(subjects);
    }

    private void seedNotifications(SeedIds ids, DemoUsers users, LocalDateTime now)
    {
        List<NotificationSpec> notifications = notificationSpecs(ids, users);
        for (int index = 0; index < notifications.size(); index++)
        {
            NotificationSpec notification = notifications.get(index);
            boolean failed = index == 1 || index == 12;
            LocalDateTime readAt = !failed && index % 4 == 0 ? now.minusMinutes(2) : null;
            insert("insert into lab_notification "
                            + "(dedupe_key,receiver_id,notification_type,title,content,business_type,business_id,delivery_status,attempt_count,next_retry_at,last_error_code,read_at,create_by,create_time,update_by,update_time) "
                            + "values (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                    notification.dedupeKey(), notification.receiverId(), notification.notificationType(),
                    notification.title(), notification.content(), notification.businessType(),
                    notification.businessId(), failed ? "FAILED" : "SENT", failed ? 2 : 1,
                    failed ? now.plusDays(30) : null, failed ? "DEMO_DELIVERY_UNAVAILABLE" : null,
                    readAt, DATA_MARKER, now.minusMinutes(notifications.size() - index),
                    failed ? DATA_MARKER : "", failed ? now : null);
        }
    }

    private List<NotificationSpec> notificationSpecs(SeedIds ids, DemoUsers users)
    {
        List<NotificationSpec> result = new ArrayList<>();
        for (int index = 0; index < RESERVATIONS.size(); index++)
        {
            ReservationSpec reservation = RESERVATIONS.get(index);
            long receiver = "PENDING".equals(reservation.status())
                    ? users.id("lab_manager") : users.id("lab_student");
            addHistoryNotification(result, ids, "DEMO-H-RSV-" + twoDigits(index + 1),
                    "RESERVATION_" + reservation.status(), receiver, "RESERVATION",
                    ids.reservations.get(reservation.reservationNo()));
        }
        for (int index = 0; index < REPAIRS.size(); index++)
        {
            RepairSpec repair = REPAIRS.get(index);
            String type = "REPAIR_ORDER_" + repair.status();
            String trace = "DEMO-H-RPR-" + twoDigits(index + 1);
            if (List.of("WAIT_ASSIGN", "WAIT_ACCEPTANCE").contains(repair.status()))
            {
                addHistoryNotification(result, ids, trace, type, users.id("lab_manager"),
                        "REPAIR_ORDER", ids.repairs.get(repair.repairNo()));
            }
            else if (List.of("WAIT_REPAIR", "IN_PROGRESS").contains(repair.status()))
            {
                addHistoryNotification(result, ids, trace, type, users.id("lab_repair_worker"),
                        "REPAIR_ORDER", ids.repairs.get(repair.repairNo()));
            }
            else
            {
                addHistoryNotification(result, ids, trace, type, users.id("lab_student"),
                        "REPAIR_ORDER", ids.repairs.get(repair.repairNo()));
                addHistoryNotification(result, ids, trace, type, users.id("lab_repair_worker"),
                        "REPAIR_ORDER", ids.repairs.get(repair.repairNo()));
            }
        }
        for (int index = 0; index < TASKS.size(); index++)
        {
            TaskSpec task = TASKS.get(index);
            if ("PENDING".equals(task.status()) || "COMPLETED".equals(task.status()))
            {
                addHistoryNotification(result, ids, "DEMO-H-TASK-" + twoDigits(index + 1),
                        "INSPECTION_TASK_" + task.status(), users.id("lab_safety_officer"),
                        "INSPECTION_TASK", ids.tasks.get(task.taskNo()));
            }
        }
        for (int index = 0; index < HAZARDS.size(); index++)
        {
            HazardSpec hazard = HAZARDS.get(index);
            long receiver = "PENDING_REVIEW".equals(hazard.status())
                    ? users.id("lab_safety_officer") : users.id(hazard.ownerUser());
            addHistoryNotification(result, ids, "DEMO-H-HZD-" + twoDigits(index + 1),
                    "HAZARD_" + hazard.status(), receiver, "HAZARD",
                    ids.hazards.get(hazard.hazardNo()));
        }
        long overdueTaskId = ids.tasks.get("DEMO-INS-PENDING-OVERDUE");
        result.add(new NotificationSpec(
                "overdue:inspection_task:" + overdueTaskId + ":1:" + users.id("lab_safety_officer"),
                users.id("lab_safety_officer"), "INSPECTION_TASK_OVERDUE", "巡检任务已超期",
                "请尽快处理超期巡检任务", "INSPECTION_TASK", overdueTaskId));
        long overdueHazardId = ids.hazards.get("DEMO-HZD-RECTIFYING");
        for (String receiverName : List.of("lab_manager", "lab_safety_officer"))
        {
            long receiver = users.id(receiverName);
            result.add(new NotificationSpec(
                    "overdue:hazard:" + overdueHazardId + ":1:" + receiver, receiver,
                    "HAZARD_OVERDUE", "隐患整改已超期", "请尽快处理超期隐患",
                    "HAZARD", overdueHazardId));
        }
        for (AccountSpec account : ACCOUNTS)
        {
            result.add(new NotificationSpec("demo:welcome:" + account.userName(),
                    users.id(account.userName()), "DEMO_WELCOME", "欢迎使用实验室管理演示",
                    "已为当前角色准备待办、历史与已完成样例", "LABORATORY",
                    ids.laboratories.get("DEMO-LAB-INNOVATION")));
        }
        return List.copyOf(result);
    }

    private void addHistoryNotification(List<NotificationSpec> result, SeedIds ids, String traceId,
            String notificationType, long receiver, String businessType, long businessId)
    {
        long historyId = ids.histories.get(traceId);
        result.add(new NotificationSpec(
                "history:" + historyId + ":" + notificationType + ":" + receiver,
                receiver, notificationType, "业务状态已更新",
                "业务单据状态已更新为 " + notificationType.substring(notificationType.indexOf('_') + 1),
                businessType, businessId));
    }

    private void seedAttachments(SeedIds ids, LocalDateTime now)
    {
        for (AttachmentSpec attachment : attachmentSpecs(ids))
        {
            ValidatedAttachment validated = attachmentPolicy.validate(attachment.originalName(),
                    PNG_MIME_TYPE, DEMO_PNG, 0);
            StoredObject stored;
            try
            {
                stored = storageService.store(new ByteArrayInputStream(DEMO_PNG), DEMO_PNG.length,
                        validated.extension());
            }
            catch (IOException exception)
            {
                throw new IllegalStateException("Unable to store a demo attachment.", exception);
            }
            registerRollbackCleanup(stored.storageKey());
            insert("insert into lab_attachment "
                            + "(business_type,business_id,original_name,stored_name,mime_type,size,storage_key,sha256,create_by,create_time,del_flag) "
                            + "values (?,?,?,?,?,?,?,?,?,?,'0')",
                    attachment.businessType(), attachment.businessId(), validated.originalName(),
                    stored.storedName(), validated.mimeType(), stored.sizeBytes(), stored.storageKey(),
                    stored.sha256(), DATA_MARKER, now);
        }
    }

    private void verifyExistingAttachments(SeedIds ids)
    {
        String expectedHash = sha256(DEMO_PNG);
        for (AttachmentSpec attachment : attachmentSpecs(ids))
        {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "select id,create_by,del_flag,storage_key,size,sha256,mime_type "
                            + "from lab_attachment where business_type=? and business_id=? and original_name=?",
                    attachment.businessType(), attachment.businessId(), attachment.originalName());
            if (rows.size() != 1)
            {
                throw new IllegalStateException("Managed demo attachment is missing or ambiguous: "
                        + attachment.originalName());
            }
            Map<String, Object> row = rows.get(0);
            requireOwnedActive(row, "attachment " + attachment.originalName());
            if (!PNG_MIME_TYPE.equals(text(row, "mime_type"))
                    || number(row, "size") != DEMO_PNG.length
                    || !expectedHash.equals(text(row, "sha256")))
            {
                throw new IllegalStateException("Managed demo attachment metadata has drifted: "
                        + attachment.originalName());
            }
            try (InputStream input = storageService.load(text(row, "storage_key")))
            {
                if (!MessageDigest.isEqual(DEMO_PNG, input.readAllBytes()))
                {
                    throw new IllegalStateException("Managed demo attachment content has drifted: "
                            + attachment.originalName());
                }
            }
            catch (IOException exception)
            {
                throw new IllegalStateException("Managed demo attachment is unavailable: "
                        + attachment.originalName(), exception);
            }
        }
    }

    private List<AttachmentSpec> attachmentSpecs(SeedIds ids)
    {
        return List.of(
                new AttachmentSpec("LABORATORY", ids.laboratories.get("DEMO-LAB-INNOVATION"),
                        LABORATORY_ATTACHMENT_NAME),
                new AttachmentSpec("DEVICE", ids.devices.get("DEMO-AST-001"),
                        DEVICE_ATTACHMENT_NAME),
                new AttachmentSpec("QUALIFICATION", ids.qualifications.get("VALID"),
                        QUALIFICATION_ATTACHMENT_NAME),
                new AttachmentSpec("REPAIR_ORDER", ids.repairs.get("DEMO-RPR-IN-PROGRESS"),
                        REPAIR_ATTACHMENT_NAME),
                new AttachmentSpec("RECTIFICATION", ids.rectifications.get("DEMO-HZD-PENDING-REVIEW"),
                        RECTIFICATION_ATTACHMENT_NAME));
    }

    private void registerRollbackCleanup(String storageKey)
    {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization()
        {
            @Override
            public void afterCompletion(int status)
            {
                if (status != STATUS_COMMITTED)
                {
                    try
                    {
                        storageService.delete(storageKey);
                    }
                    catch (IOException exception)
                    {
                        LOG.warn("Unable to remove a demo attachment after transaction rollback");
                    }
                }
            }
        });
    }

    private long insert(String sql, Object... parameters)
    {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        int rows = jdbcTemplate.update(connection ->
        {
            PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int index = 0; index < parameters.length; index++)
            {
                statement.setObject(index + 1, parameters[index]);
            }
            return statement;
        }, keyHolder);
        requireSingleRow(rows, "insert managed demo row");
        Number key = keyHolder.getKey();
        if (key == null || key.longValue() <= 0)
        {
            throw new IllegalStateException("Database did not return a generated demo row id.");
        }
        return key.longValue();
    }

    private static void requireSingleRow(int rows, String action)
    {
        if (rows != 1)
        {
            throw new IllegalStateException("Failed to " + action + ": affected rows=" + rows);
        }
    }

    private static PlanSpec plan(String planName)
    {
        return PLANS.stream().filter(item -> item.planName().equals(planName)).findFirst()
                .orElseThrow(() -> new IllegalStateException("Unknown demo plan: " + planName));
    }

    private static ReservationSpec reservation(String reservationNo)
    {
        return RESERVATIONS.stream().filter(item -> item.reservationNo().equals(reservationNo)).findFirst()
                .orElseThrow(() -> new IllegalStateException("Unknown demo reservation: " + reservationNo));
    }

    private static String twoDigits(int value)
    {
        return value < 10 ? "0" + value : Integer.toString(value);
    }

    private static String text(Map<String, Object> row, String column)
    {
        Object value = row.get(column);
        return value == null ? null : value.toString();
    }

    private static long number(Map<String, Object> row, String column)
    {
        Object value = row.get(column);
        if (!(value instanceof Number number))
        {
            throw new IllegalStateException("Expected numeric database column: " + column);
        }
        return number.longValue();
    }

    private static String sha256(byte[] content)
    {
        try
        {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        }
        catch (NoSuchAlgorithmException exception)
        {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private record AccountSpec(String userName, long roleId, String roleKey)
    {
    }

    private record LaboratorySpec(String code, String name, String managerUser, String location,
            String description, String status)
    {
    }

    private record DeviceSpec(String assetNo, String laboratoryCode, String name, String categoryCode,
            String model, String riskLevel, String location, String managerUser, String status)
    {
    }

    private record ReservationSpec(String reservationNo, String deviceKey, String status,
            long startHours, long endHours, boolean decisionRecorded)
    {
    }

    private record RepairSpec(String repairNo, String deviceKey, String sourceType,
            String sourceUsageKey, String status)
    {
    }

    private record PlanItemSpec(String itemCode, String content, int sortOrder)
    {
    }

    private record PlanSpec(String planName, String laboratoryCode, String frequencyType,
            int intervalValue, LocalTime executeTime, int deadlineOffsetMinutes, String status,
            List<PlanItemSpec> items)
    {
    }

    private record TaskSpec(String taskNo, String planName, long scheduledOffsetHours,
            long deadlineOffsetHours, String status, boolean overdue)
    {
    }

    private record HazardSpec(String hazardNo, String targetType, String targetKey, String severity,
            String ownerUser, String status, boolean overdue)
    {
    }

    private record HistorySubject(String traceId, String objectType, long objectId, String toStatus,
            long operatorId, String reason)
    {
    }

    private record NotificationSpec(String dedupeKey, long receiverId, String notificationType,
            String title, String content, String businessType, long businessId)
    {
    }

    private record AttachmentSpec(String businessType, long businessId, String originalName)
    {
    }

    private record DemoUsers(Map<String, Long> userIds)
    {
        private long id(String userName)
        {
            Long value = userIds.get(userName);
            if (value == null || value <= 0)
            {
                throw new IllegalStateException("Unknown demo user: " + userName);
            }
            return value;
        }
    }

    private record PreflightResult(boolean existing, SeedIds ids)
    {
    }

    private static final class SeedIds
    {
        private final Map<String, Long> laboratories = new LinkedHashMap<>();
        private final Map<String, Long> devices = new LinkedHashMap<>();
        private final Map<String, Long> qualifications = new LinkedHashMap<>();
        private final Map<String, Long> reservations = new LinkedHashMap<>();
        private final Map<String, Long> usage = new LinkedHashMap<>();
        private final Map<String, Long> repairs = new LinkedHashMap<>();
        private final Map<String, Long> plans = new LinkedHashMap<>();
        private final Map<String, Long> planItems = new LinkedHashMap<>();
        private final Map<String, Long> tasks = new LinkedHashMap<>();
        private final Map<String, Long> inspectionItems = new LinkedHashMap<>();
        private final Map<String, Long> hazards = new LinkedHashMap<>();
        private final Map<String, Long> rectifications = new LinkedHashMap<>();
        private final Map<String, Long> histories = new LinkedHashMap<>();
    }
}
