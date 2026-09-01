# Inspection and Hazard Closure Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 实现受控周期巡检、不可变任务快照、隐患登记、多轮整改、职责分离复查以及重大隐患阻断与安全恢复。

**Architecture:** ruoyi-admin 仅提供 lab Controller 和 OpenAPI，ruoyi-lab 负责计划、任务、隐患、整改、对象数据范围和事务。计划生成任务时复制检查项，任务完成与隐患生成同事务；重大隐患作为独立阻断事实，不改写设备状态，销号后重新计算实验室、设备、开放维修和其他重大隐患。

**Tech Stack:** Java 17、Spring Boot 3.5.16、Spring Security、MyBatis-Plus 3.5.17、MySQL 8、Flyway 11.7.2、Quartz、JUnit 5、Spring Boot Test、MockMvc、Vue 3.5.26、Element Plus 2.13.1、Vitest 3.2.4。

---

## 固定接口、范围和需求映射

计划 00、01、02、03 必须全部完成并通过各自退出门禁；集成数据库必须已经包含 V1_0～V4_1。Controller 固定在 ruoyi-admin/src/main/java/com/ruoyi/web/controller/lab，业务代码固定在 ruoyi-lab。供计划 05 Quartz 入口调用的接口必须精确为：

    public interface InspectionScheduleService {
        int generateDueTasks(LocalDateTime now, int batchSize);
    }

    public interface InspectionLifecycleService {
        int markOverdue(LocalDateTime now, int batchSize);
    }

    public interface HazardLifecycleService {
        int markOverdue(LocalDateTime now, int batchSize);
    }

    public interface LabHazardBlocker {
        void assertNoMajorHazard(Long deviceId);
        boolean hasOpenMajorHazard(Long deviceId);
    }

本计划实现三个可幂等调用的领域方法；计划 05 负责 Quartz 注册、五分钟调度窗口、消息和失败补偿，不在本计划宣称 FR-INS-005、FR-HAZ-006 完整通过。

跨域写命令必须沿用 device 优先的全局锁序：先用普通查询解析受影响 deviceId，按升序锁定全部 device，再锁 inspection_task/inspection_item 或 hazard/rectification。锁定后重新校验快照；任何路径不得先锁隐患、任务或整改再反向锁设备。

| 需求或验收 | 任务 | 证据 |
|---|---|---|
| FR-INS-001 | Task 1、2、7 | InspectionPlanServiceTest、InspectionPlanApiTest |
| FR-INS-002 | Task 3 | InspectionScheduleServiceTest、InspectionTaskConcurrencyTest |
| FR-INS-003、FR-INS-004 | Task 4 | InspectionExecutionServiceTest、InspectionCompletionRollbackTest |
| FR-HAZ-001、FR-HAZ-002、FR-AST-003 重大隐患动态守卫 | Task 5、7 | HazardServiceTest、MajorHazardBlockerTest、DeviceStatusMajorHazardGuardIT |
| FR-HAZ-003、FR-HAZ-004、FR-HAZ-005 | Task 6 | RectificationWorkflowServiceTest |
| FR-HAZ-007 | Task 6 | HazardRecoveryServiceTest |
| AT-06 重大隐患部分 | Task 5、9 | MajorHazardReservationIntegrationTest |
| AT-09 巡检至销号部分 | Task 4、6、9 | InspectionHazardAcceptanceTest |
| AT-10 | Task 5、6、9 | HazardBlockRecoveryAcceptanceTest |

## 阶段退出门禁

- 周期仅允许 DAILY、WEEKLY、MONTHLY；DTO、数据库和 Controller 均不接收 Cron 或可执行类名；
- 缺少启用检查项或负责人时计划不能启用，停用计划不删除历史任务；
- 相同 plan_id 与 scheduled_at 并发生成最多一条任务，任务检查项内容为生成时快照；
- 所有检查项完成后才能提交；不合格项必须生成且只能生成一条来源隐患；
- 整改每轮独立保存，复查退回不覆盖前一轮，提交人不能复查本人整改；
- 实验室级重大隐患阻断其全部设备，设备级仅阻断目标设备；
- 真实未关闭重大隐患阻断 AVAILABLE→DISABLED、FAULT→DISABLED、DISABLED→AVAILABLE，阻断时设备与历史均不变化；
- 销号一条隐患不能覆盖其他重大隐患、开放维修、设备故障或实验室停用；
- V5_0/V5_1 顺序迁移、后端测试、前端测试和生产构建全部成功。

## Task 1: 建立巡检、隐患和整改数据库事实

**Files:**

- Create: ruoyi-admin/src/main/resources/db/migration/V5_0__inspection_hazard_rectification.sql
- Create: ruoyi-admin/src/test/java/com/ruoyi/integration/migration/InspectionHazardMigrationTest.java
- Modify: scripts/verify-migrations.ps1
- Test: ruoyi-admin/src/test/java/com/ruoyi/integration/migration/InspectionHazardMigrationTest.java

- [ ] 1.1 先写失败测试，验证六张表、任务调度唯一键、来源检查项唯一键、整改轮次唯一键，以及 lab_inspection_task 的 create_by/update_by 两个非空审计列。

    @Test
    void createsInspectionAndHazardConstraints() {
        assertThat(tableNames()).contains(
            "lab_inspection_plan", "lab_inspection_plan_item",
            "lab_inspection_task", "lab_inspection_item",
            "lab_hazard", "lab_rectification");
        assertThat(indexExists("lab_inspection_task", "uk_inspection_task_schedule")).isTrue();
        assertThat(indexExists("lab_hazard", "uk_hazard_source_item")).isTrue();
        assertThat(indexExists("lab_rectification", "uk_rectification_round")).isTrue();
        assertThat(columnNames("lab_inspection_task"))
            .contains("create_by", "update_by");
        assertThat(nullableFlag("lab_inspection_task", "create_by")).isEqualTo("NO");
        assertThat(nullableFlag("lab_inspection_task", "update_by")).isEqualTo("NO");
        assertThat(columnDefault("lab_inspection_task", "create_by")).isEqualTo("");
        assertThat(columnDefault("lab_inspection_task", "update_by")).isEqualTo("");
    }

- [ ] 1.2 复用计划01已固定的 LAB_TEST_DB_URL、LAB_TEST_DB_USERNAME、LAB_TEST_DB_PASSWORD 数据源和`${LAB_TEST_FLYWAY_ENABLED:false}`开关；随后重置独立 MySQL 8 测试库，显式开启Flyway并确认表不存在。

    powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\run-lab-tests.ps1 `
      -DatabaseName lab_test_m5_task -Tests 'InspectionHazardMigrationTest'

预期：FAIL，缺少 lab_inspection_plan。

- [ ] 1.3 创建 V5_0，使用下列完整表边界；所有内部强归属使用外键，sys_user 仅逻辑引用。

    create table lab_inspection_plan (
      id bigint not null auto_increment,
      plan_name varchar(100) not null,
      laboratory_id bigint not null,
      frequency_type varchar(16) not null,
      interval_value int not null,
      execute_time time not null,
      day_of_week tinyint null,
      day_of_month tinyint null,
      next_run_at datetime(3) not null,
      owner_id bigint not null,
      deadline_rule varchar(24) not null,
      deadline_offset_minutes int not null,
      status varchar(16) not null default 'DISABLED',
      version int not null default 0,
      create_by varchar(64) not null default '',
      create_time datetime(3) not null,
      update_by varchar(64) not null default '',
      update_time datetime(3) null,
      del_flag char(1) not null default '0',
      primary key (id),
      constraint fk_inspection_plan_lab foreign key (laboratory_id) references lab_laboratory(id),
      constraint ck_inspection_frequency check (frequency_type in ('DAILY','WEEKLY','MONTHLY')),
      constraint ck_inspection_interval check (interval_value between 1 and 31),
      constraint ck_inspection_plan_status check (status in ('ENABLED','DISABLED')),
      index idx_inspection_plan_due (status, next_run_at)
    ) engine=innodb default charset=utf8mb4;

    create table lab_inspection_plan_item (
      id bigint not null auto_increment,
      plan_id bigint not null,
      item_code varchar(32) not null,
      content varchar(500) not null,
      sort_order int not null,
      enabled char(1) not null default '1',
      create_time datetime(3) not null,
      update_time datetime(3) null,
      del_flag char(1) not null default '0',
      primary key (id),
      constraint uk_plan_item_code unique (plan_id, item_code),
      constraint fk_plan_item_plan foreign key (plan_id) references lab_inspection_plan(id),
      index idx_plan_item_enabled (plan_id, enabled, sort_order)
    ) engine=innodb default charset=utf8mb4;

    create table lab_inspection_task (
      id bigint not null auto_increment,
      task_no varchar(32) not null,
      plan_id bigint not null,
      laboratory_id bigint not null,
      scheduled_at datetime(3) not null,
      deadline_at datetime(3) not null,
      assignee_id bigint not null,
      status varchar(20) not null default 'PENDING',
      overdue_flag char(1) not null default '0',
      started_at datetime(3) null,
      completed_at datetime(3) null,
      version int not null default 0,
      create_by varchar(64) not null default '',
      create_time datetime(3) not null,
      update_by varchar(64) not null default '',
      update_time datetime(3) null,
      del_flag char(1) not null default '0',
      primary key (id),
      constraint uk_inspection_task_no unique (task_no),
      constraint uk_inspection_task_schedule unique (plan_id, scheduled_at),
      constraint fk_inspection_task_plan foreign key (plan_id) references lab_inspection_plan(id),
      constraint fk_inspection_task_lab foreign key (laboratory_id) references lab_laboratory(id),
      constraint ck_inspection_task_status check (status in ('PENDING','IN_PROGRESS','COMPLETED')),
      index idx_inspection_task_assignee (assignee_id, status, deadline_at)
    ) engine=innodb default charset=utf8mb4;

    create table lab_inspection_item (
      id bigint not null auto_increment,
      task_id bigint not null,
      plan_item_id bigint not null,
      item_code_snapshot varchar(32) not null,
      content_snapshot varchar(500) not null,
      sort_order_snapshot int not null,
      result varchar(16) null,
      description varchar(1000) null,
      severity varchar(16) null,
      target_type varchar(16) null,
      target_id bigint null,
      inspected_by bigint null,
      inspected_at datetime(3) null,
      version int not null default 0,
      create_time datetime(3) not null,
      update_time datetime(3) null,
      del_flag char(1) not null default '0',
      primary key (id),
      constraint uk_task_plan_item unique (task_id, plan_item_id),
      constraint fk_inspection_item_task foreign key (task_id) references lab_inspection_task(id),
      constraint fk_inspection_item_plan_item foreign key (plan_item_id) references lab_inspection_plan_item(id),
      constraint ck_inspection_result check (result is null or result in ('PASS','FAIL','NOT_APPLICABLE')),
      constraint ck_inspection_severity check (severity is null or severity in ('LOW','MEDIUM','HIGH','MAJOR')),
      index idx_inspection_item_task_sort (task_id, sort_order_snapshot)
    ) engine=innodb default charset=utf8mb4;

    create table lab_hazard (
      id bigint not null auto_increment,
      hazard_no varchar(32) not null,
      source_item_id bigint null,
      related_hazard_id bigint null,
      target_type varchar(16) not null,
      target_id bigint not null,
      severity varchar(16) not null,
      owner_id bigint not null,
      deadline datetime(3) not null,
      requirements varchar(2000) not null,
      status varchar(32) not null default 'PENDING_RECTIFICATION',
      overdue_flag char(1) not null default '0',
      version int not null default 0,
      create_by varchar(64) not null default '',
      create_time datetime(3) not null,
      update_by varchar(64) not null default '',
      update_time datetime(3) null,
      del_flag char(1) not null default '0',
      primary key (id),
      constraint uk_hazard_no unique (hazard_no),
      constraint uk_hazard_source_item unique (source_item_id),
      constraint fk_hazard_source_item foreign key (source_item_id) references lab_inspection_item(id),
      constraint fk_hazard_related foreign key (related_hazard_id) references lab_hazard(id),
      constraint ck_hazard_target check (target_type in ('LABORATORY','DEVICE')),
      constraint ck_hazard_severity check (severity in ('LOW','MEDIUM','HIGH','MAJOR')),
      constraint ck_hazard_status check (
        status in ('PENDING_RECTIFICATION','RECTIFYING','PENDING_REVIEW','CLOSED')
      ),
      index idx_hazard_blocker (target_type, target_id, severity, status),
      index idx_hazard_owner_due (owner_id, status, deadline)
    ) engine=innodb default charset=utf8mb4;

    create table lab_rectification (
      id bigint not null auto_increment,
      hazard_id bigint not null,
      round_no int not null,
      submitter_id bigint not null,
      description varchar(2000) not null,
      submitted_at datetime(3) not null,
      reviewer_id bigint null,
      review_result varchar(16) null,
      review_reason varchar(1000) null,
      reviewed_at datetime(3) null,
      create_time datetime(3) not null,
      update_time datetime(3) null,
      version int not null default 0,
      del_flag char(1) not null default '0',
      primary key (id),
      constraint uk_rectification_round unique (hazard_id, round_no),
      constraint fk_rectification_hazard foreign key (hazard_id) references lab_hazard(id),
      constraint ck_rectification_round check (round_no > 0),
      constraint ck_rectification_review check (
        review_result is null or review_result in ('PASSED','REJECTED')
      ),
      index idx_rectification_hazard_time (hazard_id, submitted_at)
    ) engine=innodb default charset=utf8mb4;

- [ ] 1.4 运行迁移检查和测试。

    powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-migrations.ps1
    powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\run-lab-tests.ps1 `
      -DatabaseName lab_test_m5_task -Tests 'InspectionHazardMigrationTest'

预期：两个命令退出码 0；三项唯一约束存在，lab_inspection_task.create_by/update_by 均为 NOT NULL 且默认空字符串。

- [ ] 1.5 提交表结构。

    git add ruoyi-admin/src/main/resources/db/migration/V5_0__inspection_hazard_rectification.sql ruoyi-admin/src/test/java/com/ruoyi/integration/migration/InspectionHazardMigrationTest.java scripts/verify-migrations.ps1
    git commit -m "feat: add inspection and hazard persistence"

## Task 2: 实现受控巡检周期和计划启停

**Files:**

- Create: ruoyi-lab/src/main/java/com/ruoyi/lab/domain/InspectionFrequencyType.java
- Create: ruoyi-lab/src/main/java/com/ruoyi/lab/domain/InspectionPlanStatus.java
- Create: ruoyi-lab/src/main/java/com/ruoyi/lab/domain/LabInspectionPlan.java
- Create: ruoyi-lab/src/main/java/com/ruoyi/lab/domain/LabInspectionPlanItem.java
- Create: ruoyi-lab/src/main/java/com/ruoyi/lab/dto/InspectionPlanCommand.java
- Create: ruoyi-lab/src/main/java/com/ruoyi/lab/service/InspectionPlanService.java
- Create: ruoyi-lab/src/main/java/com/ruoyi/lab/service/impl/InspectionPlanServiceImpl.java
- Create: ruoyi-lab/src/main/java/com/ruoyi/lab/mapper/LabInspectionPlanMapper.java
- Create: ruoyi-lab/src/main/java/com/ruoyi/lab/mapper/LabInspectionPlanItemMapper.java
- Create: ruoyi-lab/src/test/java/com/ruoyi/lab/service/InspectionFrequencyTest.java
- Create: ruoyi-admin/src/test/java/com/ruoyi/integration/service/InspectionPlanServiceTest.java
- Test: ruoyi-admin/src/test/java/com/ruoyi/integration/service/InspectionPlanServiceTest.java

- [ ] 2.1 先写失败测试，覆盖每日、每周、每月 next_run_at，非法 interval/day，缺少 owner、无启用检查项、停用保留任务以及请求体拒绝 cronExpression 和 className。

    @Test
    void refusesToEnablePlanWithoutEnabledItems() {
        long planId = fixtures.disabledPlanWithNoItems();
        assertThatThrownBy(() -> service.enable(planId, safetyOfficerId))
            .isInstanceOf(LabBusinessException.class)
            .extracting("code")
            .isEqualTo(LabErrorCode.VALIDATION_ERROR);
        assertThat(fixtures.planStatus(planId)).isEqualTo("DISABLED");
    }

- [ ] 2.2 运行测试，确认周期类型和服务缺失。

    powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\run-lab-tests.ps1 `
      -DatabaseName lab_test_m5_task -Tests 'InspectionFrequencyTest,InspectionPlanServiceTest'

预期：FAIL，无法解析 InspectionFrequencyType。

- [ ] 2.3 实现受控枚举，不存储、不返回、不执行 Cron 或类名。

    public enum InspectionFrequencyType {
        DAILY {
            public LocalDateTime next(LocalDateTime current, int interval) {
                return current.plusDays(interval);
            }
        },
        WEEKLY {
            public LocalDateTime next(LocalDateTime current, int interval) {
                return current.plusWeeks(interval);
            }
        },
        MONTHLY {
            public LocalDateTime next(LocalDateTime current, int interval) {
                return current.plusMonths(interval);
            }
        };

        public abstract LocalDateTime next(LocalDateTime current, int interval);
    }

    public record InspectionPlanCommand(
        @NotBlank @Size(max = 100) String planName,
        @NotNull Long laboratoryId,
        @NotNull InspectionFrequencyType frequencyType,
        @Min(1) @Max(31) int intervalValue,
        @NotNull LocalTime executeTime,
        Integer dayOfWeek,
        Integer dayOfMonth,
        @NotNull Long ownerId,
        @NotBlank String deadlineRule,
        @Min(1) @Max(43200) int deadlineOffsetMinutes
    ) {}

- [ ] 2.4 实现计划 CRUD 与专用 enable/disable 命令。普通 update DTO 不含 status；enable 在事务内锁计划并验证 owner、实验室范围和至少一个 enabled 检查项。启停使用 expected-status 条件更新，实际变化写状态历史，重复命令返回 HTTP 409 且不写历史。

- [ ] 2.5 运行服务测试。

    powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\run-lab-tests.ps1 `
      -DatabaseName lab_test_m5_task -Tests 'InspectionFrequencyTest,InspectionPlanServiceTest'

预期：PASS；受控周期和启停约束全部通过。

- [ ] 2.6 提交计划领域。

    git add ruoyi-lab ruoyi-admin/src/test/java/com/ruoyi/integration/service/InspectionPlanServiceTest.java
    git commit -m "feat: add controlled inspection plans"

## Task 3: 幂等生成任务并冻结检查项快照

**Files:**

- Create: ruoyi-lab/src/main/java/com/ruoyi/lab/domain/LabInspectionTask.java
- Create: ruoyi-lab/src/main/java/com/ruoyi/lab/domain/LabInspectionItem.java
- Create: ruoyi-lab/src/main/java/com/ruoyi/lab/service/InspectionScheduleService.java
- Create: ruoyi-lab/src/main/java/com/ruoyi/lab/service/impl/InspectionScheduleServiceImpl.java
- Create: ruoyi-lab/src/main/java/com/ruoyi/lab/mapper/LabInspectionTaskMapper.java
- Create: ruoyi-lab/src/main/java/com/ruoyi/lab/mapper/LabInspectionItemMapper.java
- Create: ruoyi-lab/src/main/resources/mapper/lab/LabInspectionPlanMapper.xml
- Create: ruoyi-lab/src/main/resources/mapper/lab/LabInspectionTaskMapper.xml
- Create: ruoyi-admin/src/test/java/com/ruoyi/integration/service/InspectionScheduleServiceTest.java
- Create: ruoyi-admin/src/test/java/com/ruoyi/integration/service/InspectionTaskConcurrencyTest.java
- Test: ruoyi-admin/src/test/java/com/ruoyi/integration/service/InspectionTaskConcurrencyTest.java

- [ ] 3.1 先写失败测试：生成后修改计划项，任务 content_snapshot 不变；两个线程处理同一 scheduled_at 只有一条任务；成功创建时恰有一条 null → PENDING 状态历史，operator_id 来自计划 02 的 LabSystemOperatorProvider 且为禁登录系统操作账号，重复竞争不增加历史。Provider 校验失败时不得写任务、快照、历史或推进计划。

    @Test
    void generatedTaskKeepsOriginalItemSnapshot() {
        long planId = fixtures.enabledDuePlan("检查接地线");
        assertThat(service.generateDueTasks(now, 20)).isEqualTo(1);
        fixtures.renamePlanItem(planId, "检查消防器材");
        assertThat(fixtures.taskItemContents(planId, now))
            .containsExactly("检查接地线");
        assertThat(fixtures.taskAudit(planId, now))
            .extracting(TaskAudit::createBy, TaskAudit::updateBy)
            .containsExactly("__lab_system_operator__", "__lab_system_operator__");
    }

- [ ] 3.2 运行测试，确认服务缺失。

    powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\run-lab-tests.ps1 `
      -DatabaseName lab_test_m5_task -Tests 'InspectionScheduleServiceTest,InspectionTaskConcurrencyTest'

预期：FAIL，generateDueTasks 未实现。

- [ ] 3.3 实现精确接口 int generateDueTasks(LocalDateTime now, int batchSize)。方法在任何业务写入前通过计划 02 的 LabSystemOperatorProvider 取得并校验禁登录系统操作主体；Mapper 使用 FOR UPDATE SKIP LOCKED 查询到期 ENABLED 计划。每个计划在同一事务中写任务、复制 enabled 项、写 null → PENDING 状态历史并推进 next_run_at，history.operator_id 使用 Provider 返回的用户 ID，create_by/update_by 使用其 userName，禁止硬编码 9000、0、当前登录人或 Quartz 线程名。

    @Transactional
    public int generateDueTasks(LocalDateTime now, int batchSize) {
        var systemOperator = systemOperatorProvider.requiredOperator();
        List<LabInspectionPlan> plans =
            planMapper.selectDuePlansForUpdate(now, batchSize);
        int created = 0;
        for (LabInspectionPlan plan : plans) {
            LocalDateTime scheduledAt = plan.getNextRunAt();
            if (taskMapper.existsByPlanAndSchedule(plan.getId(), scheduledAt)) {
                planMapper.advanceNextRun(plan.getId(),
                    plan.frequency().next(scheduledAt, plan.getIntervalValue()));
                continue;
            }
            LabInspectionTask task = taskFactory.from(
                plan, scheduledAt, systemOperator.userName());
            taskMapper.insert(task);
            itemMapper.insertSnapshots(
                task.getId(), planItemMapper.selectEnabledByPlan(plan.getId()));
            historyService.record("INSPECTION_TASK", task.getId(),
                null, "PENDING", systemOperator.userId(), "定时生成巡检任务");
            planMapper.advanceNextRun(plan.getId(),
                plan.frequency().next(scheduledAt, plan.getIntervalValue()));
            created++;
        }
        return created;
    }

- [ ] 3.4 保留 uk_inspection_task_schedule 作为竞态兜底；捕获 DuplicateKeyException 后回查，不生成第二份检查项或第二条初始历史。任务、快照、历史和 next_run_at 任一写入失败时整体回滚。

- [ ] 3.5 运行快照和并发测试。

    powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\run-lab-tests.ps1 `
      -DatabaseName lab_test_m5_task -Tests 'InspectionScheduleServiceTest,InspectionTaskConcurrencyTest'

预期：PASS；同一计划时点任务数 1，修改计划不改变快照。

- [ ] 3.6 提交任务生成。

    git add ruoyi-lab ruoyi-admin/src/test/java/com/ruoyi/integration/service/InspectionScheduleServiceTest.java ruoyi-admin/src/test/java/com/ruoyi/integration/service/InspectionTaskConcurrencyTest.java
    git commit -m "feat: generate idempotent inspection snapshots"

## Task 4: 实现逐项检查和隐患原子生成

**Files:**

- Create: ruoyi-lab/src/main/java/com/ruoyi/lab/domain/InspectionTaskStatus.java
- Create: ruoyi-lab/src/main/java/com/ruoyi/lab/domain/InspectionResult.java
- Create: ruoyi-lab/src/main/java/com/ruoyi/lab/domain/HazardStatus.java
- Create: ruoyi-lab/src/main/java/com/ruoyi/lab/domain/HazardSeverity.java
- Create: ruoyi-lab/src/main/java/com/ruoyi/lab/domain/HazardTargetType.java
- Create: ruoyi-lab/src/main/java/com/ruoyi/lab/domain/LabHazard.java
- Create: ruoyi-lab/src/main/java/com/ruoyi/lab/dto/RecordInspectionItemCommand.java
- Create: ruoyi-lab/src/main/java/com/ruoyi/lab/service/InspectionTaskService.java
- Create: ruoyi-lab/src/main/java/com/ruoyi/lab/service/InspectionLifecycleService.java
- Create: ruoyi-lab/src/main/java/com/ruoyi/lab/service/HazardService.java
- Create: ruoyi-lab/src/main/java/com/ruoyi/lab/service/impl/InspectionTaskServiceImpl.java
- Create: ruoyi-lab/src/main/java/com/ruoyi/lab/service/impl/InspectionLifecycleServiceImpl.java
- Create: ruoyi-lab/src/main/java/com/ruoyi/lab/service/impl/HazardServiceImpl.java
- Create: ruoyi-lab/src/main/java/com/ruoyi/lab/service/impl/HazardAffectedDeviceResolver.java
- Create: ruoyi-lab/src/main/java/com/ruoyi/lab/mapper/LabHazardMapper.java
- Create: ruoyi-lab/src/main/resources/mapper/lab/LabHazardMapper.xml
- Modify: ruoyi-lab/src/main/java/com/ruoyi/lab/mapper/LabDeviceMapper.java
- Create: ruoyi-admin/src/test/java/com/ruoyi/integration/service/InspectionExecutionServiceTest.java
- Create: ruoyi-admin/src/test/java/com/ruoyi/integration/service/InspectionCompletionRollbackTest.java
- Test: ruoyi-admin/src/test/java/com/ruoyi/integration/service/InspectionCompletionRollbackTest.java

- [ ] 4.1 先写失败测试，覆盖 PENDING → IN_PROGRESS → COMPLETED、非负责人、未填完、FAIL 缺描述或风险级别、NOT_APPLICABLE，以及隐患插入失败整体回滚；超期标记另断言update_by来自禁登录系统操作主体、重复调用为0、Provider无效时无变化。

    @Test
    void doesNotCompleteWhenHazardCreationFails() {
        var task = fixtures.inProgressTaskWithFailedItem();
        failureProbe.failNextHazardInsert();
        assertThatThrownBy(() -> service.complete(task.id(), task.assigneeId()))
            .isInstanceOf(DataAccessException.class);
        assertThat(fixtures.taskStatus(task.id())).isEqualTo("IN_PROGRESS");
        assertThat(fixtures.hazardCountForTask(task.id())).isZero();
    }

- [ ] 4.2 运行测试，确认完成服务未实现。

    powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\run-lab-tests.ps1 `
      -DatabaseName lab_test_m5_task -Tests 'InspectionExecutionServiceTest,InspectionCompletionRollbackTest'

预期：FAIL，complete 未定义。

- [ ] 4.3 定义记录命令；FAIL 必须同时给 description、severity、targetType、targetId，PASS 与 NOT_APPLICABLE 清空这些故障字段。

- [ ] 4.4 实现 start、recordItem、complete。complete 先普通查询 task/item 快照，解析所有 FAIL 影响的设备集合并按 deviceId 升序锁定，再锁 task 和全部 item、重新确认负责人/状态/结果；为每个 FAIL 调用 HazardService.createFromInspectionItem，最后条件更新 COMPLETED 并写状态历史。

    @Transactional
    public void complete(Long taskId, Long officerId) {
        InspectionCompletionSnapshot snapshot = completionQuery.load(taskId);
        List<Long> deviceIds = affectedDeviceResolver.resolveSorted(snapshot.failedItems());
        deviceIds.forEach(deviceMapper::selectByIdForUpdate);

        LabInspectionTask task = taskMapper.selectForUpdate(taskId);
        accessService.requireAssignedSafetyOfficer(officerId, task);
        stateGuard.require("IN_PROGRESS", task.getStatus());
        List<LabInspectionItem> items = itemMapper.selectByTaskForUpdate(taskId);
        completionGuard.requireEveryItemRecorded(items);
        items.stream()
            .filter(item -> item.getResult() == InspectionResult.FAIL)
            .forEach(item -> hazardService.createFromInspectionItem(item, officerId));
        affectedRows.requireOne(taskMapper.completeConditionally(
            taskId, "IN_PROGRESS", LocalDateTime.now(clock)));
        historyService.record("INSPECTION_TASK", taskId,
            "IN_PROGRESS", "COMPLETED", officerId, "提交巡检结果");
    }

- [ ] 4.5 `createFromInspectionItem` 以 source_item_id 唯一键保证每个不合格快照最多一条隐患；新建时写 null → PENDING_RECTIFICATION 状态历史，唯一键回读不重复写。start、recordItem、complete 均以 expected status/version 条件更新，重复完成返回 HTTP 409。

- [ ] 4.6 InspectionLifecycleService.markOverdue 在任何写入前校验 LabSystemOperatorProvider，只把 deadline_at < now 且未 COMPLETED 的 overdue_flag 从 0 改为 1，并用系统操作主体的userName写update_by；精确返回条件更新行数，重复调用返回0，不修改主状态或伪造状态历史。Provider无效时不写数据；计划05只为实际更新行追加提交后通知与超期事实补偿。

- [ ] 4.7 运行执行与回滚测试。

    powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\run-lab-tests.ps1 `
      -DatabaseName lab_test_m5_task -Tests 'InspectionExecutionServiceTest,InspectionCompletionRollbackTest'

预期：PASS；失败注入后任务和隐患均无部分提交。

- [ ] 4.8 提交巡检执行。

    git add ruoyi-lab ruoyi-admin/src/test/java/com/ruoyi/integration/service/InspectionExecutionServiceTest.java ruoyi-admin/src/test/java/com/ruoyi/integration/service/InspectionCompletionRollbackTest.java
    git commit -m "feat: complete inspections with atomic hazards"

## Task 5: 实现隐患登记和重大隐患阻断

**Files:**

- Modify: ruoyi-lab/src/main/java/com/ruoyi/lab/domain/HazardStatus.java
- Modify: ruoyi-lab/src/main/java/com/ruoyi/lab/domain/HazardSeverity.java
- Modify: ruoyi-lab/src/main/java/com/ruoyi/lab/domain/HazardTargetType.java
- Modify: ruoyi-lab/src/main/java/com/ruoyi/lab/domain/LabHazard.java
- Create: ruoyi-lab/src/main/java/com/ruoyi/lab/dto/CreateHazardCommand.java
- Modify: ruoyi-lab/src/main/java/com/ruoyi/lab/service/HazardService.java
- Create: ruoyi-lab/src/main/java/com/ruoyi/lab/service/HazardLifecycleService.java
- Modify: ruoyi-lab/src/main/java/com/ruoyi/lab/service/impl/HazardServiceImpl.java
- Create: ruoyi-lab/src/main/java/com/ruoyi/lab/service/impl/HazardLifecycleServiceImpl.java
- Modify: ruoyi-lab/src/main/java/com/ruoyi/lab/service/impl/HazardAffectedDeviceResolver.java
- Create: ruoyi-lab/src/main/java/com/ruoyi/lab/security/LabHazardBlockerImpl.java
- Modify: ruoyi-lab/src/main/java/com/ruoyi/lab/mapper/LabHazardMapper.java
- Modify: ruoyi-lab/src/main/resources/mapper/lab/LabHazardMapper.xml
- Modify: ruoyi-lab/src/main/java/com/ruoyi/lab/mapper/LabDeviceMapper.java
- Create: ruoyi-admin/src/test/java/com/ruoyi/integration/service/HazardServiceTest.java
- Create: ruoyi-admin/src/test/java/com/ruoyi/integration/security/MajorHazardBlockerTest.java
- Create: ruoyi-admin/src/test/java/com/ruoyi/integration/service/DeviceStatusMajorHazardGuardIT.java
- Create: ruoyi-admin/src/test/java/com/ruoyi/integration/reservation/MajorHazardReservationIntegrationTest.java
- Create: ruoyi-admin/src/test/java/com/ruoyi/integration/concurrency/MajorHazardCreationReservationRaceTest.java
- Test: ruoyi-admin/src/test/java/com/ruoyi/integration/security/MajorHazardBlockerTest.java
- Test: ruoyi-admin/src/test/java/com/ruoyi/integration/service/DeviceStatusMajorHazardGuardIT.java

- [ ] 5.1 先写失败测试，覆盖实验室级阻断全部设备、设备级只阻断目标、非 MAJOR 不阻断、CLOSED 不阻断、多个重大隐患任一未关仍阻断，以及 MAJOR 隐患创建与预约批准竞争；隐患超期另断言update_by来自禁登录系统操作主体、重复调用为0、Provider无效时无变化。DeviceStatusMajorHazardGuardIT 必须向真实 lab_hazard 插入未关闭 MAJOR 行，并对 AVAILABLE→DISABLED、FAULT→DISABLED、DISABLED→AVAILABLE 三条候选边逐一断言 LAB_MAJOR_HAZARD_BLOCKED、设备不变且历史不增加；关闭最后一条 MAJOR 且无未归还使用、开放维修后，三条边分别使用独立夹具成功且各只新增一条历史。DISABLED→AVAILABLE 的正例还必须把所属实验室设为 ENABLED，反例另断言实验室 DISABLED 返回 LAB_LABORATORY_DISABLED。

    @Test
    void laboratoryMajorHazardBlocksEveryDeviceInLaboratory() {
        var lab = fixtures.laboratoryWithTwoDevices();
        fixtures.openMajorHazard("LABORATORY", lab.id());
        assertThatThrownBy(() -> blocker.assertNoMajorHazard(lab.firstDeviceId()))
            .isInstanceOf(LabBusinessException.class)
            .extracting("code")
            .isEqualTo(LabErrorCode.LAB_MAJOR_HAZARD_BLOCKED);
        assertThatThrownBy(() -> blocker.assertNoMajorHazard(lab.secondDeviceId()))
            .isInstanceOf(LabBusinessException.class);
    }

    @Test
    void majorHazardCreationSerializesWithReservationApproval() {
        var scenario = fixtures.pendingReservationWithoutHazard();
        transactionProbe.pauseAfterHazardLocksDevice(scenario.deviceId());

        Future<Long> hazard = pool.submit(() -> hazardService.create(
            fixtures.majorDeviceHazardCommand(scenario.deviceId()), safetyOfficerId));
        transactionProbe.awaitDeviceLocked();
        Future<Throwable> approval = pool.submit(() -> catchFailure(
            () -> reservationService.approve(scenario.reservationId(), managerId)));
        transactionProbe.releaseHazardInsert();

        assertThat(hazard.get(5, SECONDS)).isPositive();
        assertThat(approval.get(5, SECONDS))
            .isInstanceOf(LabBusinessException.class)
            .extracting("code").isEqualTo(LabErrorCode.LAB_MAJOR_HAZARD_BLOCKED);
        assertThat(fixtures.reservationStatus(scenario.reservationId())).isEqualTo("PENDING");
    }

    @ParameterizedTest
    @MethodSource("managedDeviceStatusTransitions")
    void realOpenMajorHazardBlocksEveryManagedDeviceStatusTransition(
        DeviceStatus source,
        DeviceStatus target
    ) {
        var device = fixtures.deviceInEnabledLaboratory(source);
        fixtures.openMajorHazard("DEVICE", device.id());

        assertThatThrownBy(() -> deviceStatusCommandService.changeStatus(
            device.id(), new DeviceStatusCommandDto(target, "重大隐患期间变更状态"), managerId))
            .isInstanceOf(LabBusinessException.class)
            .extracting("errorCode")
            .isEqualTo(LabErrorCode.LAB_MAJOR_HAZARD_BLOCKED);

        assertThat(fixtures.deviceStatus(device.id())).isEqualTo(source.name());
        assertThat(fixtures.deviceHistoryCount(device.id())).isZero();
    }

    static Stream<Arguments> managedDeviceStatusTransitions() {
        return Stream.of(
            Arguments.of(DeviceStatus.AVAILABLE, DeviceStatus.DISABLED),
            Arguments.of(DeviceStatus.FAULT, DeviceStatus.DISABLED),
            Arguments.of(DeviceStatus.DISABLED, DeviceStatus.AVAILABLE));
    }

    @ParameterizedTest
    @MethodSource("managedDeviceStatusTransitions")
    void closedLastMajorHazardAllowsManagedDeviceStatusTransition(
        DeviceStatus source,
        DeviceStatus target
    ) {
        var device = fixtures.deviceInEnabledLaboratory(source);
        long hazardId = fixtures.openMajorHazard("DEVICE", device.id());
        fixtures.closeHazard(hazardId);

        deviceStatusCommandService.changeStatus(
            device.id(), new DeviceStatusCommandDto(target, "重大隐患已销号"), managerId);

        assertThat(fixtures.deviceStatus(device.id())).isEqualTo(target.name());
        assertThat(fixtures.deviceHistoryCount(device.id())).isEqualTo(1);
    }

- [ ] 5.2 运行测试，确认真实阻断实现缺失。

    powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\run-lab-tests.ps1 `
      -DatabaseName lab_test_m5_task `
      -Tests 'HazardServiceTest,MajorHazardBlockerTest,DeviceStatusMajorHazardGuardIT,MajorHazardReservationIntegrationTest,MajorHazardCreationReservationRaceTest'

预期：FAIL，LabHazardBlockerImpl 尚不存在或预约未被阻断。

- [ ] 5.3 实现手工登记和巡检来源登记：先从非锁快照解析影响设备；设备级锁一台，实验室级查询现有全部设备 ID 并升序加锁；之后巡检来源再锁 task/item，手工来源直接插入。安全员必须拥有目标实验室范围，targetType 与 targetId 类型匹配，owner 和 deadline 必填，初态固定 PENDING_RECTIFICATION。CLOSED 不可重开，再次发现时创建新隐患并用 relatedHazardId 关联已关闭记录。只有 MAJOR 参与预约阻断，但所有等级沿用同一锁序。

- [ ] 5.4 用真实表实现计划 02 已固定的 LabHazardBlocker，不创建同义接口；`hasOpenMajorHazard` 同时匹配设备自身和设备所属实验室，只查询 severity=MAJOR 且 status<>CLOSED，`assertNoMajorHazard` 在为 true 时抛 LAB_MAJOR_HAZARD_BLOCKED。该 Bean 替换计划 02 的 NoRecordedHazardBlocker 后，计划 03 的 DeviceStatusTransitionGuard、预约提交/批准和领用自动复用真实结果；不得在 Controller 或各命令服务重复拼隐患 SQL。

    @Override
    public boolean hasOpenMajorHazard(Long deviceId) {
        return !hazardMapper
            .selectOpenMajorHazardIdsForDeviceForUpdate(deviceId)
            .isEmpty();
    }

    @Override
    public void assertNoMajorHazard(Long deviceId) {
        if (hasOpenMajorHazard(deviceId)) {
            throw new LabBusinessException(
                LabErrorCode.LAB_MAJOR_HAZARD_BLOCKED, "设备存在未销号重大隐患");
        }
    }

    List<Long> selectOpenMajorHazardIdsForDeviceForUpdate(Long deviceId);

    select h.id
    from lab_hazard h
    join lab_device d on d.id = #{deviceId} and d.del_flag = '0'
    where h.del_flag = '0'
      and h.severity = 'MAJOR'
      and h.status in ('PENDING_RECTIFICATION','RECTIFYING','PENDING_REVIEW')
      and (
        (h.target_type = 'DEVICE' and h.target_id = d.id)
        or
        (h.target_type = 'LABORATORY' and h.target_id = d.laboratory_id)
      )
    order by h.id
    for update

`LabHazardBlockerImpl` 的两个公共方法都必须在事务中使用上述 locking/current read；所有生产调用方先锁目标 device，再调用 blocker。不得改回普通 count，否则命令之前建立的 REPEATABLE READ 快照可能漏掉刚提交的重大隐患。

- [ ] 5.5 HazardLifecycleService.markOverdue 在任何写入前校验 LabSystemOperatorProvider，精确返回新标记数量，只更新 deadline < now、status<>CLOSED、overdue_flag=0，并用系统操作主体的userName写update_by；不修改主状态或伪造状态历史。Provider无效时不写数据；计划05只为实际更新行追加提交后通知与超期事实补偿。

- [ ] 5.6 运行服务、阻断和预约集成测试。

    powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\run-lab-tests.ps1 `
      -DatabaseName lab_test_m5_task `
      -Tests 'HazardServiceTest,MajorHazardBlockerTest,DeviceStatusMajorHazardGuardIT,MajorHazardReservationIntegrationTest,MajorHazardCreationReservationRaceTest'

预期：PASS；预约提交、批准、领用和三条管理员设备启停候选边复用同一个 LabHazardBlocker，真实未关闭 MAJOR 行阻断后不产生设备状态历史。

- [ ] 5.7 提交隐患与阻断。

    git add ruoyi-lab ruoyi-admin/src/test/java/com/ruoyi/integration/service/HazardServiceTest.java ruoyi-admin/src/test/java/com/ruoyi/integration/security/MajorHazardBlockerTest.java ruoyi-admin/src/test/java/com/ruoyi/integration/service/DeviceStatusMajorHazardGuardIT.java ruoyi-admin/src/test/java/com/ruoyi/integration/reservation/MajorHazardReservationIntegrationTest.java ruoyi-admin/src/test/java/com/ruoyi/integration/concurrency/MajorHazardCreationReservationRaceTest.java
    git commit -m "feat: add scoped major hazard blocking"

## Task 6: 实现多轮整改、复查职责分离和恢复

**Files:**

- Create: ruoyi-lab/src/main/java/com/ruoyi/lab/domain/LabRectification.java
- Create: ruoyi-lab/src/main/java/com/ruoyi/lab/dto/SubmitRectificationCommand.java
- Create: ruoyi-lab/src/main/java/com/ruoyi/lab/dto/ReviewRectificationCommand.java
- Create: ruoyi-lab/src/main/java/com/ruoyi/lab/service/RectificationService.java
- Create: ruoyi-lab/src/main/java/com/ruoyi/lab/service/impl/RectificationServiceImpl.java
- Create: ruoyi-lab/src/main/java/com/ruoyi/lab/mapper/LabRectificationMapper.java
- Create: ruoyi-lab/src/main/resources/mapper/lab/LabRectificationMapper.xml
- Modify: ruoyi-lab/src/main/java/com/ruoyi/lab/service/DeviceAvailabilityService.java
- Modify: ruoyi-lab/src/main/java/com/ruoyi/lab/service/impl/DeviceAvailabilityServiceImpl.java
- Modify: ruoyi-lab/src/main/java/com/ruoyi/lab/service/impl/HazardAffectedDeviceResolver.java
- Modify: ruoyi-lab/src/main/java/com/ruoyi/lab/mapper/LabDeviceMapper.java
- Modify: ruoyi-lab/src/main/java/com/ruoyi/lab/mapper/LabHazardMapper.java
- Modify: ruoyi-lab/src/main/resources/mapper/lab/LabHazardMapper.xml
- Modify: ruoyi-lab/src/main/java/com/ruoyi/lab/storage/LabAttachmentObjectAuthorizer.java
- Create: ruoyi-admin/src/test/java/com/ruoyi/integration/service/RectificationWorkflowServiceTest.java
- Create: ruoyi-admin/src/test/java/com/ruoyi/integration/service/HazardRecoveryServiceTest.java
- Create: ruoyi-admin/src/test/java/com/ruoyi/integration/concurrency/ConcurrentHazardClosureRecoveryTest.java
- Create: ruoyi-admin/src/test/java/com/ruoyi/integration/security/RectificationAttachmentAuthorizationTest.java
- Test: ruoyi-admin/src/test/java/com/ruoyi/integration/service/HazardRecoveryServiceTest.java

- [ ] 6.1 先写失败测试：责任人开始整改、第一轮提交、提交人复查被拒、复查退回、第二轮提交、复查通过；断言两轮记录均保留，并覆盖通过/拒绝空 reason 在 Service 直调和 API 两层均被拒绝。恢复测试固定只有无未归还使用记录的 MAINTENANCE 设备可恢复；IN_USE、FAULT、DISABLED、有 returned_at IS NULL 使用记录或仍有其他阻断时均保持原状态且不写恢复历史。

    public record ReviewRectificationCommand(
        boolean passed,
        @NotBlank @Size(max = 1000) String reason
    ) {}

    @Test
    void preservesRejectedRoundAndClosesOnSecondRound() {
        long hazardId = fixtures.pendingHazard(ownerId);
        service.start(hazardId, ownerId);
        long first = service.submit(hazardId,
            new SubmitRectificationCommand("更换接地线并检测"), ownerId);
        service.review(hazardId, first,
            new ReviewRectificationCommand(false, "检测值仍超标"), safetyOfficerId);
        long second = service.submit(hazardId,
            new SubmitRectificationCommand("重新布线并通过绝缘测试"), ownerId);
        service.review(hazardId, second,
            new ReviewRectificationCommand(true, "现场复查合格"), safetyOfficerId);

        assertThat(fixtures.roundNumbers(hazardId)).containsExactly(1, 2);
        assertThat(fixtures.reviewResults(hazardId)).containsExactly("REJECTED", "PASSED");
        assertThat(fixtures.hazardStatus(hazardId)).isEqualTo("CLOSED");
    }

    @Test
    void lastConcurrentClosureRestoresAfterEveryMajorHazardIsClosed() throws Exception {
        assertThat(jdbcTemplate.queryForObject(
            "select @@transaction_isolation", String.class))
            .isEqualTo("REPEATABLE-READ");
        var scenario = fixtures.maintenanceDeviceWithTwoPendingMajorHazardReviews();
        CyclicBarrier barrier = new CyclicBarrier(2);

        Future<?> first = pool.submit(() -> {
            barrier.await();
            service.review(scenario.firstHazardId(), scenario.firstRoundId(),
                new ReviewRectificationCommand(true, "第一项复查通过"), firstReviewerId);
            return null;
        });
        Future<?> second = pool.submit(() -> {
            barrier.await();
            service.review(scenario.secondHazardId(), scenario.secondRoundId(),
                new ReviewRectificationCommand(true, "第二项复查通过"), secondReviewerId);
            return null;
        });

        first.get(10, SECONDS);
        second.get(10, SECONDS);
        assertThat(fixtures.openMajorHazardCount(scenario.deviceId())).isZero();
        assertThat(fixtures.deviceStatus(scenario.deviceId())).isEqualTo("AVAILABLE");
        assertThat(fixtures.availableRecoveryHistoryCount(scenario.deviceId())).isEqualTo(1);
    }

- [ ] 6.2 运行测试，确认整改服务缺失。

    powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\run-lab-tests.ps1 `
      -DatabaseName lab_test_m5_task `
      -Tests 'RectificationWorkflowServiceTest,HazardRecoveryServiceTest,ConcurrentHazardClosureRecoveryTest,RectificationAttachmentAuthorizationTest'

预期：FAIL，RectificationServiceImpl 不存在。

- [ ] 6.3 实现 PENDING_RECTIFICATION → RECTIFYING；仅责任人或经对象范围授权的代办人可开始和提交。expected-status 更新成功时写状态历史，重复开始返回 HTTP 409 且不写历史。

- [ ] 6.4 提交整改只锁 hazard，不在持锁后获取 device 锁；按 max(round_no)+1 创建新行并条件进入 PENDING_REVIEW，写状态历史；uk_rectification_round 兜底并发提交，失败方返回 HTTP 409 且不产生新轮次。

- [ ] 6.5 复查仅安全员可执行，reviewerId 不得等于 submitterId。ReviewRectificationCommand 对通过和拒绝都要求 1～1000 字非空 reason，Service 入口再次 trim 并拒绝空值，直接调用也不能绕过；拒绝回 RECTIFYING，通过写 CLOSED，确保两条复查历史原因均非空。CLOSED 后任何命令返回 409；条件更新影响行数为 0 时不写历史。

    @Transactional
    public void review(
        Long hazardId,
        Long rectificationId,
        ReviewRectificationCommand command,
        Long reviewerId
    ) {
        LabHazard snapshot = hazardMapper.selectById(hazardId);
        notFoundGuard.requireVisible(snapshot);
        List<Long> deviceIds = affectedDeviceResolver.resolveSorted(snapshot);
        deviceIds.forEach(deviceMapper::selectByIdForUpdate);

        LabHazard hazard = hazardMapper.selectForUpdate(hazardId);
        LabRectification round =
            rectificationMapper.selectForUpdate(rectificationId);
        stateGuard.requireSameTarget(snapshot, hazard);
        stateGuard.requireBelongsToHazard(round, hazardId);
        accessService.requireSafetyScope(reviewerId, hazard);
        dutySeparation.requireDifferent(
            reviewerId, round.getSubmitterId(), "整改提交人不能复查本人整改");
        stateGuard.require("PENDING_REVIEW", hazard.getStatus());
        round.review(command, reviewerId, LocalDateTime.now(clock));
        rectificationMapper.saveReview(round);
        if (command.passed()) {
            affectedRows.requireOne(
                hazardMapper.closeConditionally(hazardId, "PENDING_REVIEW"));
            historyService.record("HAZARD", hazardId,
                "PENDING_REVIEW", "CLOSED", reviewerId, command.reason());
            availabilityService.restoreAfterBlockerCleared(
                deviceIds, reviewerId);
        } else {
            affectedRows.requireOne(
                hazardMapper.returnToRectifying(hazardId, "PENDING_REVIEW"));
            historyService.record("HAZARD", hazardId,
                "PENDING_REVIEW", "RECTIFYING", reviewerId, command.reason());
        }
    }

- [ ] 6.6 恢复计算接收已升序的 deviceIds 并逐台锁定。关闭当前 hazard 后，必须复用 Task 5 的 `selectOpenMajorHazardIdsForDeviceForUpdate` 做 locking/current read，不能复用事务早先的一致性快照；该查询由 blocker 与恢复计算共同使用，并统一过滤 h.del_flag='0'、d.del_flag='0'。只有设备当前为 MAINTENANCE、实验室 ENABLED、无 returned_at IS NULL 的使用记录、无开放维修单且锁定重查无其他重大隐患时才条件恢复 AVAILABLE；AVAILABLE、IN_USE、FAULT、DISABLED 等其他状态保持不变。实际恢复写一条设备状态历史，无变化不写。

    select h.id
    from lab_hazard h
    join lab_device d on d.id = #{deviceId} and d.del_flag = '0'
    where h.del_flag = '0'
      and h.severity = 'MAJOR'
      and h.status in ('PENDING_RECTIFICATION','RECTIFYING','PENDING_REVIEW')
      and (
        (h.target_type = 'DEVICE' and h.target_id = d.id)
        or (h.target_type = 'LABORATORY' and h.target_id = d.laboratory_id)
      )
    order by h.id
    for update

默认 MySQL REPEATABLE READ 下，FOR UPDATE 是 current read：两个销号事务先竞争同一 device 锁，后获得锁者在关闭自己的 hazard 后能看到前一事务已提交的 CLOSED，从而正确恢复，避免双方都基于旧快照跳过恢复。

- [ ] 6.7 扩展 LabAttachmentObjectAuthorizer 支持 RECTIFICATION：通过 rectification → hazard → target 解析对象范围；仅本轮 submitter 在 PENDING_REVIEW 前可管理，安全员和有范围的责任人可读，复查完成后只读。复用计划 01 的通用附件接口和存储安全，测试每轮附件不串轮、跨实验室访问 403。

- [ ] 6.8 运行整改、附件授权与恢复测试。

    powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\run-lab-tests.ps1 `
      -DatabaseName lab_test_m5_task `
      -Tests 'RectificationWorkflowServiceTest,HazardRecoveryServiceTest,ConcurrentHazardClosureRecoveryTest,RectificationAttachmentAuthorizationTest'

预期：PASS；两轮记录均保留，任一其他阻断存在时仍不可预约。

- [ ] 6.9 提交整改闭环。

    git add ruoyi-lab ruoyi-admin/src/test/java/com/ruoyi/integration/service/RectificationWorkflowServiceTest.java ruoyi-admin/src/test/java/com/ruoyi/integration/service/HazardRecoveryServiceTest.java ruoyi-admin/src/test/java/com/ruoyi/integration/concurrency/ConcurrentHazardClosureRecoveryTest.java ruoyi-admin/src/test/java/com/ruoyi/integration/security/RectificationAttachmentAuthorizationTest.java
    git commit -m "feat: add multi-round rectification review"

## Task 7: 增加 Controller、权限、菜单和 OpenAPI

**Files:**

- Create: ruoyi-admin/src/main/java/com/ruoyi/web/controller/lab/LabInspectionPlanController.java
- Create: ruoyi-admin/src/main/java/com/ruoyi/web/controller/lab/LabInspectionTaskController.java
- Create: ruoyi-admin/src/main/java/com/ruoyi/web/controller/lab/LabHazardController.java
- Create: ruoyi-admin/src/main/resources/db/migration/V5_1__inspection_hazard_menus_and_dictionaries.sql
- Create: ruoyi-admin/src/test/java/com/ruoyi/integration/web/controller/lab/InspectionPlanApiTest.java
- Create: ruoyi-admin/src/test/java/com/ruoyi/integration/web/controller/lab/InspectionTaskApiTest.java
- Create: ruoyi-admin/src/test/java/com/ruoyi/integration/web/controller/lab/HazardApiTest.java
- Modify: ruoyi-admin/src/main/java/com/ruoyi/web/core/config/SwaggerConfig.java
- Test: ruoyi-admin/src/test/java/com/ruoyi/integration/web/controller/lab/HazardApiTest.java

- [ ] 7.1 先写失败的 MockMvc 测试，覆盖 401、按钮 403、对象范围 404、自复查 403、复查通过但空 reason 为 400、非法状态 409、Cron/className 400，以及没有 setStatus 接口。

- [ ] 7.2 运行 API 测试，确认三个 Controller 尚不存在。

    powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\run-lab-tests.ps1 `
      -DatabaseName lab_test_m5_task -Tests 'InspectionPlanApiTest,InspectionTaskApiTest,HazardApiTest'

预期：FAIL，目标 URL 返回 404。

- [ ] 7.3 创建构造器注入的 Controller，禁止注入 Mapper。固定命令：

    POST /lab/inspection-plans
    PUT /lab/inspection-plans/{id}
    POST /lab/inspection-plans/{id}/enable
    POST /lab/inspection-plans/{id}/disable
    POST /lab/inspection-tasks/{id}/start
    PUT /lab/inspection-tasks/{taskId}/items/{itemId}
    POST /lab/inspection-tasks/{id}/complete
    POST /lab/hazards
    POST /lab/hazards/{id}/start-rectification
    POST /lab/hazards/{id}/rectifications
    POST /lab/hazards/{hazardId}/rectifications/{roundId}/review

- [ ] 7.4 权限固定为 lab:inspection:plan:list/add/edit/enable、lab:inspection:task:list/execute、lab:hazard:list/add/rectify/review；服务层继续校验对象范围和职责分离。

- [ ] 7.5 创建 V5_1，使用固定 menu_id 4400～4430 写入“巡检计划”“巡检任务”“隐患整改”菜单、全部按钮和 frequency/result/severity/hazard_status 字典。字典值必须与 Java 枚举完全一致。

- [ ] 7.6 在 SwaggerConfig 的 lab 分组声明状态枚举、命令 DTO、403/404/409 和 +08:00 时间，不暴露内部 ownerId 覆盖或 status 编辑字段；重复状态命令由唯一键或 expected-status 条件更新返回原结果或 409。

- [ ] 7.7 运行 API、架构和迁移测试。

    powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\run-lab-tests.ps1 `
      -DatabaseName lab_test_m5_task `
      -Tests 'InspectionPlanApiTest,InspectionTaskApiTest,HazardApiTest,LabLayerArchitectureTest,InspectionHazardMigrationTest'

预期：PASS；Controller 不依赖 Mapper，非法命令无法绕过状态机。

- [ ] 7.8 提交接口与权限。

    git add ruoyi-admin
    git commit -m "feat: expose secured inspection and hazard APIs"

## Task 8: 实现巡检与隐患前端

**Files:**

- Create: ruoyi-ui/src/api/lab/inspection.js
- Create: ruoyi-ui/src/api/lab/hazard.js
- Create: ruoyi-ui/src/views/lab/inspection/plan/index.vue
- Create: ruoyi-ui/src/views/lab/inspection/task/index.vue
- Create: ruoyi-ui/src/views/lab/inspection/task/execute.vue
- Create: ruoyi-ui/src/views/lab/hazard/index.vue
- Create: ruoyi-ui/src/views/lab/hazard/detail.vue
- Create: ruoyi-ui/src/components/lab/RectificationTimeline.vue
- Create: ruoyi-ui/tests/unit/lab/inspection-view.spec.js
- Create: ruoyi-ui/tests/unit/lab/hazard-view.spec.js
- Test: ruoyi-ui/tests/unit/lab/hazard-view.spec.js

- [ ] 8.1 先写失败测试：计划表单只有受控周期；未完成全部项时禁用提交；FAIL 显示问题、风险、目标字段；整改时间线保留两轮；提交人看不到复查按钮。

- [ ] 8.2 运行测试并确认页面缺失。

    corepack yarn --cwd .\ruoyi-ui test tests/unit/lab/inspection-view.spec.js tests/unit/lab/hazard-view.spec.js

预期：FAIL，无法解析巡检或隐患页面。

- [ ] 8.3 实现 API，所有状态命令使用明确 URL；提交期间禁用按钮，后端以任务调度键、来源检查项、整改轮次唯一键和 expected-status 条件更新作为重复请求的最终防线。

- [ ] 8.4 计划页面用 DAILY/WEEKLY/MONTHLY 下拉框，不渲染文本 Cron 或类名输入；启用前在前端提示 owner 和检查项，但以后端校验为准。

- [ ] 8.5 执行页固定显示 contentSnapshot；PASS、FAIL、NOT_APPLICABLE 采用字典标签。FAIL 时问题、severity、targetType、targetId 必填。

- [ ] 8.6 隐患详情使用 RectificationTimeline 显示每轮提交人、时间、说明、附件、复查人、结论和原因；每轮复用 AttachmentPanel 且 businessType 固定为 RECTIFICATION、businessId 使用字符串 roundId；重大隐患显示阻断范围。

- [ ] 8.7 运行前端测试和生产构建。

    corepack yarn --cwd .\ruoyi-ui test tests/unit/lab/inspection-view.spec.js tests/unit/lab/hazard-view.spec.js
    corepack yarn --cwd .\ruoyi-ui build:prod

预期：两个测试文件 PASS；生产构建退出码 0。

- [ ] 8.8 提交前端。

    git add ruoyi-ui/src/api/lab ruoyi-ui/src/views/lab/inspection ruoyi-ui/src/views/lab/hazard ruoyi-ui/src/components/lab/RectificationTimeline.vue ruoyi-ui/tests/unit/lab
    git commit -m "feat: add inspection and hazard interfaces"

## Task 9: 验证巡检闭环、阻断和恢复

**Files:**

- Create: ruoyi-admin/src/test/java/com/ruoyi/integration/acceptance/InspectionHazardAcceptanceTest.java
- Create: ruoyi-admin/src/test/java/com/ruoyi/integration/acceptance/HazardBlockRecoveryAcceptanceTest.java
- Create: scripts/smoke-inspection-hazard.ps1
- Create: docs/testing/m4-inspection-hazard-report.md
- Modify: docs/requirements/lab-management-srs.md
- Test: ruoyi-admin/src/test/java/com/ruoyi/integration/acceptance/InspectionHazardAcceptanceTest.java

- [ ] 9.1 先写普通角色失败验收：生成任务、逐项检查产生重大隐患、预约被阻断、第一轮整改被退回、第二轮通过、其他阻断清除后预约恢复。

    @Test
    void closesAfterSecondRoundButRecoversOnlyWithoutOtherBlockers() {
        var flow = acceptance.createMajorHazardFromInspection();
        assertThat(acceptance.submitReservation(flow.deviceId()).status()).isEqualTo(409);
        acceptance.submitAndRejectFirstRound(flow.hazardId());
        acceptance.submitAndPassSecondRound(flow.hazardId());
        assertThat(acceptance.hazardStatus(flow.hazardId())).isEqualTo("CLOSED");
        assertThat(acceptance.submitReservation(flow.deviceId()).status()).isEqualTo(201);
    }

- [ ] 9.2 运行验收测试，确认夹具或流程尚未满足。

    powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\run-lab-tests.ps1 `
      -DatabaseName lab_test_m5_task `
      -Tests 'InspectionHazardAcceptanceTest,HazardBlockRecoveryAcceptanceTest'

预期：在流程完成前 FAIL，输出具体状态断言。

- [ ] 9.3 完成夹具与 smoke-inspection-hazard.ps1。只使用安全员、责任人、实验室管理员和学生普通角色；脚本不打印密码、JWT 或附件内容。

- [ ] 9.4 运行后端、前端和烟雾门禁。

    powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-migrations.ps1
    powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify.ps1
    corepack yarn --cwd .\ruoyi-ui test
    corepack yarn --cwd .\ruoyi-ui build:prod
    powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\smoke-inspection-hazard.ps1

预期：全部退出码 0；重复任务生成不增加记录；多轮整改和阻断恢复验收通过。

- [ ] 9.5 在 m4-inspection-hazard-report.md 记录 Git 提交、V5_0/V5_1、测试数、任务快照、并发唯一约束、事务回滚、职责分离、多轮整改、实验室级/设备级阻断和恢复结果。

- [ ] 9.6 在 SRS 追踪附录将有证据的 FR-INS-001～004、FR-HAZ-001～005、FR-HAZ-007、AT-06 重大隐患部分、AT-09 非超期部分、AT-10 标记为计划 04 已验证；FR-INS-005、FR-HAZ-006 和通知部分仍指向计划 05。

- [ ] 9.7 最终静态检查。

    git diff --check
    rg -n "(TO)(DO)|(TB)(D)|待.{0}补充|password\s*[:=]\s*[^$<{]|BEGIN (RSA|OPENSSH) PRIVATE KEY|Bearer eyJ" . -g '!target/**' -g '!ruoyi-ui/node_modules/**'
    git status --short

预期：无空白错误、占位文本和真实凭据；只包含计划 04 预期变更。

- [ ] 9.8 提交证据并标记阶段。

    git add ruoyi-admin/src/test ruoyi-lab/src/test ruoyi-ui/tests scripts/smoke-inspection-hazard.ps1 docs/testing/m4-inspection-hazard-report.md docs/requirements/lab-management-srs.md
    git commit -m "test: verify inspection and hazard closure"
    git tag milestone/m5-inspection-hazards

## 计划 04 最终回归命令

    powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-migrations.ps1
    powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify.ps1
    corepack yarn --cwd .\ruoyi-ui test
    corepack yarn --cwd .\ruoyi-ui build:prod
    powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\smoke-inspection-hazard.ps1
    git status --short

预期：命令全部退出 0；V5_0/V5_1 已应用；计划任务快照不可变；多轮整改完整；重大隐患阻断与恢复符合 AT-06、AT-09、AT-10；工作区干净。
