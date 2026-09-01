# Usage and Repair Workflow Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在已批准的预约、设备和资格基础上，实现可并发验证、可追溯且不可绕过状态机的领用归还与维修闭环。

**Architecture:** Web Controller 固定放在 ruoyi-admin，所有状态规则、事务、行锁、对象级权限和跨表编排集中在 ruoyi-lab。领用、归还、主动报修和维修验收均以 MySQL 事务为事实边界；使用记录唯一约束和开放维修单唯一约束作为并发兜底，状态历史与核心状态同事务写入。

**Tech Stack:** Java 17、Spring Boot 3.5.16、Spring Security、MyBatis-Plus 3.5.17、PageHelper 2.1.1、MySQL 8、Flyway 11.7.2、JUnit 5、Spring Boot Test、MockMvc、Vue 3.5.26、Element Plus 2.13.1、Vitest 3.2.4。

---

## 前置条件、固定契约与范围

执行本计划前，计划 00、01、02 必须完成并通过各自退出门禁。以下跨计划类型和方法名在执行前通过编译测试固定，不允许在本计划中创建第二套同义服务：

    public interface LabQualificationGuard {
        void assertQualified(Long userId, Long deviceId, LocalDateTime at);
        boolean isQualified(Long userId, Long deviceId, LocalDateTime at);
    }

    public interface LabHazardBlocker {
        void assertNoMajorHazard(Long deviceId);
        boolean hasOpenMajorHazard(Long deviceId);
    }

    public interface LabDeviceMapper extends BaseMapper<LabDevice> {
        LabDevice selectByIdForUpdate(Long deviceId);
        int updateStatusConditionally(Long deviceId, String expected, String target);
    }

    public interface LabReservationMapper extends BaseMapper<LabReservation> {
        LabReservation selectByIdForUpdate(Long reservationId);
        int updateStatusConditionally(Long reservationId, String expected, String target);
    }

计划 01 已冻结普通设备状态命令的三参数入口 `changeStatus(Long deviceId, DeviceStatusCommandDto command, Long actorId)`；本计划只收紧该入口的状态规则，不增加两参数重载、不调整参数顺序。

所有跨表命令沿用计划 02 的全局锁序：先用普通查询取得不可变 ID 快照，再按 device → reservation → usage_record 或 device → repair_order 加锁；涉及多台设备时按 device_id 升序加锁。锁定后必须重新校验对象归属和状态，普通快照不得作为最终写入依据。

本计划不提供普通 PUT 接口修改 lab_usage_record 或 lab_repair_order.status，不实现维修人员验收本人结果，不把重大隐患伪装成设备状态，不实现通知和统计；通知、定时任务与统计由计划 05 承接。

## 需求映射

| 需求或验收 | 实现任务 | 自动化证据 |
|---|---|---|
| FR-AST-003 动态状态守卫 | Task 6 | DeviceStatusTransitionGuardTest |
| FR-USE-001、FR-USE-002 | Task 1～3 | UsageCheckoutServiceTest、UsageCommandApiTest |
| FR-USE-003、FR-USE-004 | Task 4、7 | UsageReturnServiceTest、UsageCommandApiTest |
| FR-USE-005 | Task 3、7、8 | UsageQueryServiceTest、UsageCommandApiTest、usage-view.spec.js |
| FR-REP-001、FR-REP-002 | Task 4、5 | RepairOpenConcurrencyTest、RepairAssignmentServiceTest |
| FR-REP-003、FR-REP-004 | Task 6 | RepairWorkflowServiceTest |
| FR-REP-005 | Task 6～8 | RepairQueryServiceTest、repair-view.spec.js |
| AT-04 | Task 9 | UsageRepairAcceptanceTest.normalUsageFlow |
| AT-07 | Task 4、9 | UsageRepairRollbackTest、UsageRepairAcceptanceTest.abnormalReturn |
| AT-08 | Task 6、9 | UsageRepairAcceptanceTest.repairRejectedThenAccepted |

## 阶段退出门禁

- APPROVED 预约只有在领用窗口内且资格、实验室、设备和重大隐患复核通过时才能进入 CHECKED_OUT；
- 同一 reservation_id 只有一条使用记录，同一设备同一时刻只有一条未归还使用记录；
- AVAILABLE→DISABLED、FAULT→DISABLED、DISABLED→AVAILABLE 在未归还使用、开放维修或重大隐患任一存在时均失败且不写历史；启用还要求所属实验室 ENABLED；
- 正常归还原子完成使用关闭、预约 COMPLETED 和设备 AVAILABLE；
- 异常归还原子完成使用关闭、预约 COMPLETED、设备 FAULT 和维修单创建或关联，注入异常时全部回滚；
- 每次设备、预约或维修状态实际变化都在同一事务写 lab_status_history；唯一键回读、无实际变化和 HTTP 409 不新增历史；
- 主动报修与异常归还并发时，同一设备最多一张非 CLOSED 工单；
- 工单严格执行 WAIT_ASSIGN → WAIT_REPAIR → IN_PROGRESS → WAIT_ACCEPTANCE → CLOSED，验收退回仅回到 IN_PROGRESS；
- 维修人员只能处理分派给本人的工单，不能验收本人维修结果；实验室管理员只能操作授权实验室对象；
- 后端任务级测试、计划 03 全量测试、前端单测与生产构建全部通过；
- V4_0、V4_1 能从计划 02 数据库顺序升级，已执行迁移未被修改。

## Task 1: 建立使用与维修数据库约束

**Files:**

- Create: ruoyi-admin/src/main/resources/db/migration/V4_0__usage_and_repair.sql
- Create: ruoyi-admin/src/test/java/com/ruoyi/integration/migration/UsageRepairMigrationTest.java
- Modify: scripts/verify-migrations.ps1
- Test: ruoyi-admin/src/test/java/com/ruoyi/integration/migration/UsageRepairMigrationTest.java

- [ ] 1.1 先写失败的迁移测试，验证两张表、两个业务唯一键、开放记录生成列和关键索引。

    @SpringBootTest
    @ActiveProfiles("test")
    class UsageRepairMigrationTest {
        @Autowired
        JdbcTemplate jdbc;

        @Test
        void createsUsageAndRepairConstraints() {
            assertThat(tableCount("lab_usage_record")).isEqualTo(1);
            assertThat(tableCount("lab_repair_order")).isEqualTo(1);
            assertThat(indexCount("lab_usage_record", "uk_usage_reservation")).isEqualTo(1);
            assertThat(indexCount("lab_usage_record", "uk_usage_open_device")).isEqualTo(1);
            assertThat(indexCount("lab_repair_order", "uk_repair_no")).isEqualTo(1);
            assertThat(indexCount("lab_repair_order", "uk_repair_open_device")).isEqualTo(1);
        }

        private int tableCount(String table) {
            return jdbc.queryForObject(
                "select count(*) from information_schema.tables where table_schema=database() and table_name=?",
                Integer.class, table);
        }

        private int indexCount(String table, String index) {
            return jdbc.queryForObject(
                "select count(distinct index_name) from information_schema.statistics where table_schema=database() and table_name=? and index_name=?",
                Integer.class, table, index);
        }
    }

- [ ] 1.2 复用计划01已固定的`LAB_TEST_DB_URL/USERNAME/PASSWORD`与`${LAB_TEST_FLYWAY_ENABLED:false}`配置；默认仍关闭，迁移测试显式开启。随后重置独立 MySQL 8 测试库并确认测试因表不存在而失败。

    powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\run-lab-tests.ps1 `
      -DatabaseName lab_test_m4_task -Tests 'UsageRepairMigrationTest'

预期：FAIL，首个失败断言指出 lab_usage_record 不存在。

- [ ] 1.3 创建 V4_0，完整定义使用记录、维修工单、外键、乐观锁和数据库唯一开放工单约束。

    create table lab_usage_record (
      id bigint not null auto_increment,
      reservation_id bigint not null,
      device_id bigint not null,
      user_id bigint not null,
      checkout_operator_id bigint not null,
      checked_out_at datetime(3) not null,
      checkout_note varchar(500) null,
      returned_at datetime(3) null,
      return_operator_id bigint null,
      return_condition varchar(20) null,
      return_note varchar(500) null,
      repair_order_id bigint null,
      overdue_minutes int not null default 0,
      version int not null default 0,
      create_by varchar(64) not null default '',
      create_time datetime(3) not null,
      update_by varchar(64) not null default '',
      update_time datetime(3) null,
      del_flag char(1) not null default '0',
      open_device_id bigint generated always as (
        case when returned_at is null then device_id else null end
      ) stored,
      primary key (id),
      constraint uk_usage_reservation unique (reservation_id),
      constraint uk_usage_open_device unique (open_device_id),
      constraint fk_usage_reservation foreign key (reservation_id) references lab_reservation(id),
      constraint fk_usage_device foreign key (device_id) references lab_device(id),
      constraint ck_usage_return_condition check (
        return_condition is null or return_condition in ('NORMAL','DAMAGED','FAULT')
      ),
      index idx_usage_user_time (user_id, checked_out_at),
      index idx_usage_device_time (device_id, checked_out_at),
      index idx_usage_repair_order (repair_order_id)
    ) engine=innodb default charset=utf8mb4 comment='设备领用归还记录';

    create table lab_repair_order (
      id bigint not null auto_increment,
      repair_no varchar(32) not null,
      device_id bigint not null,
      source_type varchar(24) not null,
      source_id bigint null,
      reporter_id bigint not null,
      fault_description varchar(1000) not null,
      assignee_id bigint null,
      assigned_by bigint null,
      assigned_at datetime(3) null,
      started_at datetime(3) null,
      repair_result varchar(2000) null,
      result_submitted_at datetime(3) null,
      acceptance_result varchar(16) null,
      acceptance_reason varchar(1000) null,
      accepted_by bigint null,
      accepted_at datetime(3) null,
      status varchar(24) not null,
      version int not null default 0,
      create_by varchar(64) not null default '',
      create_time datetime(3) not null,
      update_by varchar(64) not null default '',
      update_time datetime(3) null,
      del_flag char(1) not null default '0',
      open_device_id bigint generated always as (
        case when status <> 'CLOSED' then device_id else null end
      ) stored,
      primary key (id),
      constraint uk_repair_no unique (repair_no),
      constraint uk_repair_open_device unique (open_device_id),
      constraint fk_repair_device foreign key (device_id) references lab_device(id),
      constraint fk_repair_source_usage foreign key (source_id) references lab_usage_record(id),
      constraint ck_repair_source check (source_type in ('ACTIVE_REPORT','ABNORMAL_RETURN')),
      constraint ck_repair_source_id check (
        (source_type = 'ACTIVE_REPORT' and source_id is null)
        or (source_type = 'ABNORMAL_RETURN' and source_id is not null)
      ),
      constraint ck_repair_status check (
        status in ('WAIT_ASSIGN','WAIT_REPAIR','IN_PROGRESS','WAIT_ACCEPTANCE','CLOSED')
      ),
      constraint ck_repair_acceptance check (
        acceptance_result is null or acceptance_result in ('PASSED','REJECTED')
      ),
      index idx_repair_assignee_status (assignee_id, status),
      index idx_repair_device_created (device_id, create_time)
    ) engine=innodb default charset=utf8mb4 comment='设备维修工单';

    alter table lab_usage_record
      add constraint fk_usage_repair_order
      foreign key (repair_order_id) references lab_repair_order(id);

- [ ] 1.4 扩展迁移静态检查，拒绝缺少双下划线的版本文件、重复版本以及本迁移中的 DROP、CREATE DATABASE、USE。

    powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-migrations.ps1

预期：退出码 0，V4_0 被识别且没有危险 SQL。

- [ ] 1.5 重新运行迁移测试。

    powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\run-lab-tests.ps1 `
      -DatabaseName lab_test_m4_task -Tests 'UsageRepairMigrationTest'

预期：PASS，两张表、四项关键唯一约束、外键和检查约束断言全部通过。

- [ ] 1.6 提交数据库边界。

    git add ruoyi-admin/src/main/resources/db/migration/V4_0__usage_and_repair.sql ruoyi-admin/src/test/java/com/ruoyi/integration/migration/UsageRepairMigrationTest.java scripts/verify-migrations.ps1
    git commit -m "feat: add usage and repair persistence constraints"

## Task 2: 固定状态机、命令对象和锁查询

**Files:**

- Create: ruoyi-lab/src/main/java/com/ruoyi/lab/domain/LabUsageRecord.java
- Create: ruoyi-lab/src/main/java/com/ruoyi/lab/domain/LabRepairOrder.java
- Create: ruoyi-lab/src/main/java/com/ruoyi/lab/domain/ReturnCondition.java
- Create: ruoyi-lab/src/main/java/com/ruoyi/lab/domain/RepairStatus.java
- Create: ruoyi-lab/src/main/java/com/ruoyi/lab/domain/RepairSourceType.java
- Create: ruoyi-lab/src/main/java/com/ruoyi/lab/dto/CheckOutCommand.java
- Create: ruoyi-lab/src/main/java/com/ruoyi/lab/dto/ReturnUsageCommand.java
- Create: ruoyi-lab/src/main/java/com/ruoyi/lab/dto/ReportFaultCommand.java
- Create: ruoyi-lab/src/main/java/com/ruoyi/lab/dto/AssignRepairCommand.java
- Create: ruoyi-lab/src/main/java/com/ruoyi/lab/dto/SubmitRepairResultCommand.java
- Create: ruoyi-lab/src/main/java/com/ruoyi/lab/dto/AcceptRepairCommand.java
- Create: ruoyi-lab/src/main/java/com/ruoyi/lab/mapper/LabUsageRecordMapper.java
- Create: ruoyi-lab/src/main/java/com/ruoyi/lab/mapper/LabRepairOrderMapper.java
- Create: ruoyi-lab/src/main/resources/mapper/lab/LabUsageRecordMapper.xml
- Create: ruoyi-lab/src/main/resources/mapper/lab/LabRepairOrderMapper.xml
- Create: ruoyi-lab/src/test/java/com/ruoyi/lab/domain/RepairStatusTest.java
- Create: ruoyi-admin/src/test/java/com/ruoyi/integration/mapper/UsageRepairLockMapperTest.java
- Test: ruoyi-lab/src/test/java/com/ruoyi/lab/domain/RepairStatusTest.java
- Test: ruoyi-admin/src/test/java/com/ruoyi/integration/mapper/UsageRepairLockMapperTest.java

- [ ] 2.1 先写状态机失败测试，覆盖全部合法边和非法跳转。

    class RepairStatusTest {
        @Test
        void allowsOnlyApprovedTransitions() {
            assertThat(RepairStatus.WAIT_ASSIGN.canMoveTo(RepairStatus.WAIT_REPAIR)).isTrue();
            assertThat(RepairStatus.WAIT_REPAIR.canMoveTo(RepairStatus.IN_PROGRESS)).isTrue();
            assertThat(RepairStatus.IN_PROGRESS.canMoveTo(RepairStatus.WAIT_ACCEPTANCE)).isTrue();
            assertThat(RepairStatus.WAIT_ACCEPTANCE.canMoveTo(RepairStatus.IN_PROGRESS)).isTrue();
            assertThat(RepairStatus.WAIT_ACCEPTANCE.canMoveTo(RepairStatus.CLOSED)).isTrue();
            assertThat(RepairStatus.WAIT_ASSIGN.canMoveTo(RepairStatus.CLOSED)).isFalse();
            assertThat(RepairStatus.CLOSED.canMoveTo(RepairStatus.IN_PROGRESS)).isFalse();
        }
    }

- [ ] 2.2 运行状态测试，确认 RepairStatus 尚不存在。

    mvn -pl ruoyi-lab -am -DskipTests=false -Dsurefire.failIfNoSpecifiedTests=false "-Dtest=RepairStatusTest" test

预期：FAIL，编译错误为 cannot find symbol RepairStatus。

- [ ] 2.3 实现完整枚举和 Bean Validation 命令，并复用计划 00 的 SRS 固定码 `LAB_REPAIR_ALREADY_OPEN`（映射 HTTP 409，保留给无法安全回读原工单的开放工单冲突）；本期重复主动报修能够回读原工单时仍返回原结果。不允许命令 DTO 接收 status、operatorId、userId 或审计字段。

    public enum RepairStatus {
        WAIT_ASSIGN,
        WAIT_REPAIR,
        IN_PROGRESS,
        WAIT_ACCEPTANCE,
        CLOSED;

        public boolean canMoveTo(RepairStatus target) {
            return switch (this) {
                case WAIT_ASSIGN -> target == WAIT_REPAIR;
                case WAIT_REPAIR -> target == IN_PROGRESS;
                case IN_PROGRESS -> target == WAIT_ACCEPTANCE;
                case WAIT_ACCEPTANCE -> target == IN_PROGRESS || target == CLOSED;
                case CLOSED -> false;
            };
        }
    }

    public enum ReturnCondition {
        NORMAL, DAMAGED, FAULT;

        public boolean isAbnormal() {
            return this != NORMAL;
        }
    }

    public record CheckOutCommand(
        @NotNull Long reservationId,
        @Size(max = 500) String note
    ) {}

    public record ReturnUsageCommand(
        @NotNull ReturnCondition condition,
        @Size(max = 500) String note,
        @Size(max = 1000) String faultDescription
    ) {
        @AssertTrue(message = "异常归还必须填写故障描述")
        public boolean hasFaultDescriptionWhenAbnormal() {
            return !condition.isAbnormal()
                || faultDescription != null && !faultDescription.isBlank();
        }
    }

    public record AcceptRepairCommand(
        boolean passed,
        @NotBlank @Size(max = 1000) String reason
    ) {}

- [ ] 2.4 先写 Mapper 集成测试，两个会改变状态的查询都必须持有 FOR UPDATE 锁，条件更新必须在旧状态不匹配时返回 0。

    @Test
    void conditionUpdateRejectsStaleRepairState() {
        long orderId = fixtures.repairOrder(RepairStatus.WAIT_ASSIGN);
        int rows = repairOrderMapper.updateStatusConditionally(
            orderId, "WAIT_REPAIR", "IN_PROGRESS", 9L, now);
        assertThat(rows).isZero();
        assertThat(repairOrderMapper.selectById(orderId).getStatus())
            .isEqualTo("WAIT_ASSIGN");
    }

- [ ] 2.5 创建 Mapper 接口和 XML；锁查询必须按主键查询且带 FOR UPDATE，开放工单查询不得包含 CLOSED。新增锁序测试断言服务只允许 device → reservation → usage_record 或 device → repair_order，禁止反向取锁。

    <select id="selectOpenByDeviceIdForUpdate"
            resultType="com.ruoyi.lab.domain.LabRepairOrder">
      select *
      from lab_repair_order
      where device_id = #{deviceId}
        and status in ('WAIT_ASSIGN','WAIT_REPAIR','IN_PROGRESS','WAIT_ACCEPTANCE')
      for update
    </select>

    <update id="updateStatusConditionally">
      update lab_repair_order
      set status = #{target},
          update_by = #{operatorName},
          update_time = #{now},
          version = version + 1
      where id = #{id}
        and status = #{expected}
    </update>

    <select id="selectOpenUsageForUpdate"
            resultType="com.ruoyi.lab.domain.LabUsageRecord">
      select *
      from lab_usage_record
      where id = #{usageId}
        and returned_at is null
      for update
    </select>

- [ ] 2.6 运行领域和 Mapper 测试。

    powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\run-lab-tests.ps1 `
      -DatabaseName lab_test_m4_task -Tests 'RepairStatusTest,UsageRepairLockMapperTest'

预期：PASS；非法跳转、陈旧状态和锁查询断言全部通过。

- [ ] 2.7 提交模型和持久层。

    git add ruoyi-lab/src/main/java/com/ruoyi/lab/domain ruoyi-lab/src/main/java/com/ruoyi/lab/dto ruoyi-lab/src/main/java/com/ruoyi/lab/mapper ruoyi-lab/src/main/resources/mapper/lab ruoyi-lab/src/test ruoyi-admin/src/test/java/com/ruoyi/integration/mapper/UsageRepairLockMapperTest.java
    git commit -m "feat: add usage and repair state models"

## Task 3: 实现领用窗口、三重复核和唯一领用

**Files:**

- Create: ruoyi-lab/src/main/java/com/ruoyi/lab/service/UsageCommandService.java
- Create: ruoyi-lab/src/main/java/com/ruoyi/lab/service/impl/UsageCommandServiceImpl.java
- Create: ruoyi-lab/src/main/java/com/ruoyi/lab/service/UsageQueryService.java
- Create: ruoyi-lab/src/main/java/com/ruoyi/lab/service/impl/UsageQueryServiceImpl.java
- Create: ruoyi-lab/src/main/java/com/ruoyi/lab/service/impl/UsageWindowPolicy.java
- Create: ruoyi-lab/src/main/java/com/ruoyi/lab/dto/UsageQueryDto.java
- Create: ruoyi-lab/src/main/java/com/ruoyi/lab/vo/UsageRecordVo.java
- Create: ruoyi-lab/src/main/java/com/ruoyi/lab/vo/UsageRecordDetailVo.java
- Modify: ruoyi-lab/src/main/java/com/ruoyi/lab/mapper/LabUsageRecordMapper.java
- Modify: ruoyi-lab/src/main/resources/mapper/lab/LabUsageRecordMapper.xml
- Modify: ruoyi-lab/src/main/java/com/ruoyi/lab/mapper/LabReservationMapper.java
- Modify: ruoyi-lab/src/main/java/com/ruoyi/lab/mapper/LabDeviceMapper.java
- Create: ruoyi-lab/src/test/java/com/ruoyi/lab/service/UsageWindowPolicyTest.java
- Create: ruoyi-admin/src/test/java/com/ruoyi/integration/service/UsageCheckoutServiceTest.java
- Create: ruoyi-admin/src/test/java/com/ruoyi/integration/service/UsageQueryServiceTest.java
- Test: ruoyi-admin/src/test/java/com/ruoyi/integration/service/UsageCheckoutServiceTest.java
- Test: ruoyi-admin/src/test/java/com/ruoyi/integration/service/UsageQueryServiceTest.java

- [ ] 3.1 先写失败测试，覆盖窗口边界、资格在批准后过期、实验室停用、设备非 AVAILABLE、重大隐患、重复领用，以及学生/实验室管理员的使用记录列表和详情数据范围。

    @Test
    void rejectsCheckoutWhenQualificationExpiredAfterApproval() {
        var scenario = fixtures.approvedReservation();
        fixtures.expireQualification(scenario.applicantId(), clock.instant().minusSeconds(1));

        assertThatThrownBy(() -> service.checkOut(
            new CheckOutCommand(scenario.reservationId(), "现场核验"), managerId))
            .isInstanceOf(LabBusinessException.class)
            .extracting("code")
            .isEqualTo(LabErrorCode.LAB_QUALIFICATION_INVALID);

        assertThat(fixtures.reservationStatus(scenario.reservationId())).isEqualTo("APPROVED");
        assertThat(fixtures.usageCount(scenario.reservationId())).isZero();
    }

    @Test
    void createsExactlyOneUsageRecord() {
        var scenario = fixtures.approvedReservationInCheckoutWindow();
        UsageRecordVo first = service.checkOut(
            new CheckOutCommand(scenario.reservationId(), null), managerId);

        UsageRecordVo repeated = service.checkOut(
            new CheckOutCommand(scenario.reservationId(), null), managerId);

        assertThat(repeated.id()).isEqualTo(first.id());
        assertThat(fixtures.usageCount(scenario.reservationId())).isEqualTo(1);
        assertThat(fixtures.reservationStatus(scenario.reservationId())).isEqualTo("CHECKED_OUT");
        assertThat(fixtures.deviceStatus(scenario.deviceId())).isEqualTo("IN_USE");
    }

- [ ] 3.2 运行测试，确认服务未实现而失败。

    powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\run-lab-tests.ps1 `
      -DatabaseName lab_test_m4_task `
      -Tests 'UsageWindowPolicyTest,UsageCheckoutServiceTest,UsageQueryServiceTest'

预期：FAIL，编译错误指向 UsageCommandServiceImpl 或 UsageQueryService。

- [ ] 3.3 实现 UsageWindowPolicy，从若依系统参数读取 lab.usage.checkout.early-minutes 和 lab.usage.checkout.late-minutes，默认分别为 30 和 15；边界采用闭区间。

    public void assertWithinWindow(
        LocalDateTime now,
        LocalDateTime reservationStart,
        int earlyMinutes,
        int lateMinutes
    ) {
        LocalDateTime earliest = reservationStart.minusMinutes(earlyMinutes);
        LocalDateTime latest = reservationStart.plusMinutes(lateMinutes);
        if (now.isBefore(earliest) || now.isAfter(latest)) {
            throw new LabBusinessException(
                LabErrorCode.LAB_ILLEGAL_STATE_TRANSITION,
                "当前不在允许的领用时间窗口内");
        }
    }

- [ ] 3.4 实现事务服务：先普通查询 reservation 快照取得 deviceId，再依次锁 device、reservation，最后查询 usage；对象范围、窗口、实验室、设备、资格和隐患均在持锁后复核。

    @Transactional
    public UsageRecordVo checkOut(
        CheckOutCommand command,
        Long operatorId
    ) {
        LabReservation snapshot = reservationMapper.selectById(command.reservationId());
        notFoundGuard.requireVisible(snapshot);

        LabDevice device = deviceMapper.selectByIdForUpdate(snapshot.getDeviceId());
        LabReservation reservation =
            reservationMapper.selectByIdForUpdate(command.reservationId());
        stateGuard.requireSameDevice(snapshot.getDeviceId(), reservation.getDeviceId());
        accessService.requireManagedReservation(operatorId, reservation);

        LabUsageRecord existing =
            usageRecordMapper.selectByReservationId(command.reservationId());
        if (existing != null) {
            return assembler.toVo(existing);
        }
        stateGuard.require("APPROVED", reservation.getStatus());

        laboratoryGuard.assertEnabled(device.getLaboratoryId());
        stateGuard.require("AVAILABLE", device.getStatus());
        usageWindowPolicy.assertWithinWindow(
            LocalDateTime.now(clock), reservation.getStartTime(),
            checkoutConfig.earlyMinutes(), checkoutConfig.lateMinutes());
        qualificationGuard.assertQualified(
            reservation.getApplicantId(), device.getId(), LocalDateTime.now(clock));
        hazardBlocker.assertNoMajorHazard(device.getId());

        LabUsageRecord usage = LabUsageRecord.checkOut(
            reservation, operatorId, LocalDateTime.now(clock), command.note());
        usageRecordMapper.insert(usage);
        affectedRows.requireOne(reservationMapper.updateStatusConditionally(
            reservation.getId(), "APPROVED", "CHECKED_OUT"));
        affectedRows.requireOne(deviceMapper.updateStatusConditionally(
            device.getId(), "AVAILABLE", "IN_USE"));
        historyService.record("RESERVATION", reservation.getId(),
            "APPROVED", "CHECKED_OUT", operatorId, "办理领用");
        historyService.record("DEVICE", device.getId(),
            "AVAILABLE", "IN_USE", operatorId, "预约领用");
        return assembler.toVo(usage);
    }

- [ ] 3.5 `reservation_id` 已存在时在持有预约行锁后回读并返回原使用记录；`open_device_id` 等其他唯一键冲突统一转换为 LAB_DUPLICATE_OPERATION 和 HTTP 409。不得新增通用命令幂等表。

- [ ] 3.6 完整实现 FR-USE-005 查询切片。DTO 不接收 userId、laboratoryIds 或任意 SQL 排序表达式；学生强制 `u.user_id=currentUserId`，管理角色使用计划 01 的 LabDataScope，详情复用相同范围。Service 与 VO 契约固定为：

    public record UsageQueryDto(
        @Size(max = 32) String reservationNo,
        @Size(max = 64) String assetNo,
        ReturnCondition returnCondition,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime checkedOutFrom,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime checkedOutTo,
        @Pattern(regexp = "id|reservationNo|assetNo|checkedOutAt|returnedAt") String sortBy,
        @Pattern(regexp = "asc|desc") String sortDirection
    ) {}

    public record UsageRecordVo(
        Long id,
        Long reservationId,
        Long deviceId,
        String reservationNo,
        String assetNo,
        String deviceName,
        LocalDateTime checkedOutAt,
        LocalDateTime returnedAt,
        ReturnCondition returnCondition
    ) {}

    public record UsageRecordDetailVo(
        Long id,
        Long reservationId,
        Long deviceId,
        Long userId,
        Long repairOrderId,
        String reservationNo,
        String assetNo,
        String deviceName,
        LocalDateTime checkedOutAt,
        String checkoutNote,
        LocalDateTime returnedAt,
        ReturnCondition returnCondition,
        String returnNote
    ) {}

    public interface UsageQueryService {
        List<UsageRecordVo> list(UsageQueryDto query, Long currentUserId);
        UsageRecordDetailVo detail(Long usageId, Long currentUserId);
    }

- [ ] 3.7 在 LabUsageRecordMapper/XML 实现 `selectScopedList` 与 `selectScopedDetail`；所有表过滤 del_flag，空管理范围直接返回空列表或 404，排序用 `<choose>` 白名单，禁止 `${}` 拼接。

    List<UsageRecordVo> selectScopedList(
        @Param("query") UsageQueryDto query,
        @Param("currentUserId") Long currentUserId,
        @Param("studentOnly") boolean studentOnly,
        @Param("allLaboratories") boolean allLaboratories,
        @Param("laboratoryIds") Set<Long> laboratoryIds);

    UsageRecordDetailVo selectScopedDetail(
        @Param("usageId") Long usageId,
        @Param("currentUserId") Long currentUserId,
        @Param("studentOnly") boolean studentOnly,
        @Param("allLaboratories") boolean allLaboratories,
        @Param("laboratoryIds") Set<Long> laboratoryIds);

    select u.id, u.reservation_id, u.device_id, u.user_id, u.repair_order_id,
           r.reservation_no, d.asset_no, d.device_name,
           u.checked_out_at, u.checkout_note, u.returned_at,
           u.return_condition, u.return_note
    from lab_usage_record u
    join lab_reservation r on r.id = u.reservation_id and r.del_flag = '0'
    join lab_device d on d.id = u.device_id and d.del_flag = '0'
    where u.del_flag = '0'
      and (#{studentOnly} = false or u.user_id = #{currentUserId})
      and (#{studentOnly} = true or #{allLaboratories} = true
           or d.laboratory_id in
           <foreach collection="laboratoryIds" item="labId" open="(" separator="," close=")">
             #{labId}
           </foreach>)
    <if test="query.reservationNo != null and query.reservationNo != ''">
      and r.reservation_no = #{query.reservationNo}
    </if>
    <if test="query.assetNo != null and query.assetNo != ''">
      and d.asset_no = #{query.assetNo}
    </if>
    <if test="query.returnCondition != null">
      and u.return_condition = #{query.returnCondition}
    </if>
    <if test="query.checkedOutFrom != null">
      and u.checked_out_at &gt;= #{query.checkedOutFrom}
    </if>
    <if test="query.checkedOutTo != null">
      and u.checked_out_at &lt; #{query.checkedOutTo}
    </if>
    <choose>
      <when test="query.sortBy == 'reservationNo'">order by r.reservation_no</when>
      <when test="query.sortBy == 'assetNo'">order by d.asset_no</when>
      <when test="query.sortBy == 'checkedOutAt'">order by u.checked_out_at</when>
      <when test="query.sortBy == 'returnedAt'">order by u.returned_at</when>
      <otherwise>order by u.id</otherwise>
    </choose>
    <choose>
      <when test="query.sortDirection == 'asc'">asc</when>
      <otherwise>desc</otherwise>
    </choose>

`selectScopedDetail` 复用完全相同的列、连接和范围片段，追加 `and u.id = #{usageId}` 且不带排序；结果为空时 Service 统一返回对象不可见的 404。

- [ ] 3.8 UsageQueryServiceTest 固定验证：学生只能读本人；管理员只能读授权实验室；跨范围详情为 404；预约号、资产号、归还结果、领用时间组合筛选正确；pageSize 最大 100；非法 sortBy/sortDirection 为 400；BIGINT 由全局 Jackson 规则输出字符串。

- [ ] 3.9 运行领用与查询测试并检查事务提交后的三对象状态。

    powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\run-lab-tests.ps1 `
      -DatabaseName lab_test_m4_task `
      -Tests 'UsageWindowPolicyTest,UsageCheckoutServiceTest,UsageQueryServiceTest'

预期：PASS；使用记录 1 条，预约 CHECKED_OUT，设备 IN_USE，状态历史 2 条，学生和管理员查询范围断言全部通过。

- [ ] 3.10 提交领用和使用查询。

    git add ruoyi-lab ruoyi-admin/src/test/java/com/ruoyi/integration/service/UsageCheckoutServiceTest.java ruoyi-admin/src/test/java/com/ruoyi/integration/service/UsageQueryServiceTest.java
    git commit -m "feat: implement guarded equipment checkout and usage queries"

## Task 4: 实现正常归还、异常归还与事务回滚

**Files:**

- Modify: ruoyi-lab/src/main/java/com/ruoyi/lab/service/UsageCommandService.java
- Modify: ruoyi-lab/src/main/java/com/ruoyi/lab/service/impl/UsageCommandServiceImpl.java
- Create: ruoyi-lab/src/main/java/com/ruoyi/lab/service/RepairOrderService.java
- Create: ruoyi-lab/src/main/java/com/ruoyi/lab/service/impl/RepairOrderServiceImpl.java
- Create: ruoyi-lab/src/main/java/com/ruoyi/lab/service/impl/RepairNumberGenerator.java
- Create: ruoyi-admin/src/test/java/com/ruoyi/integration/service/UsageReturnServiceTest.java
- Create: ruoyi-admin/src/test/java/com/ruoyi/integration/service/UsageRepairRollbackTest.java
- Create: ruoyi-admin/src/test/java/com/ruoyi/integration/service/RepairOpenConcurrencyTest.java
- Test: ruoyi-admin/src/test/java/com/ruoyi/integration/service/UsageRepairRollbackTest.java

- [ ] 4.1 先写失败测试，分别断言正常归还、异常归还关联既有开放工单、异常归还新建工单以及任一写入失败时四类记录回滚。

    @Test
    void abnormalReturnRollsBackEveryWriteWhenRepairInsertFails() {
        var scenario = fixtures.checkedOutUsage();
        failureProbe.failNextRepairInsert();

        assertThatThrownBy(() -> service.returnUsage(
            scenario.usageId(),
            new ReturnUsageCommand(ReturnCondition.FAULT, "无法开机", "电源指示灯不亮"),
            managerId))
            .isInstanceOf(DataAccessException.class);

        assertThat(fixtures.usageReturnedAt(scenario.usageId())).isNull();
        assertThat(fixtures.reservationStatus(scenario.reservationId())).isEqualTo("CHECKED_OUT");
        assertThat(fixtures.deviceStatus(scenario.deviceId())).isEqualTo("IN_USE");
        assertThat(fixtures.openRepairCount(scenario.deviceId())).isZero();
    }

- [ ] 4.2 运行测试，确认异常归还尚未形成单事务。

    powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\run-lab-tests.ps1 `
      -DatabaseName lab_test_m4_task `
      -Tests 'UsageReturnServiceTest,UsageRepairRollbackTest,RepairOpenConcurrencyTest'

预期：FAIL，正常归还或异常归还断言至少一项失败。

- [ ] 4.3 实现归还：先普通查询 usage 快照取得 deviceId、reservationId，再依次锁 device、reservation、usage；仅未归还使用记录且预约 CHECKED_OUT 可执行。

    @Transactional
    public UsageRecordVo returnUsage(
        Long usageId,
        ReturnUsageCommand command,
        Long operatorId
    ) {
        LabUsageRecord snapshot = usageMapper.selectById(usageId);
        notFoundGuard.requireVisible(snapshot);

        LabDevice device = deviceMapper.selectByIdForUpdate(snapshot.getDeviceId());
        LabReservation reservation =
            reservationMapper.selectByIdForUpdate(snapshot.getReservationId());
        LabUsageRecord usage = usageMapper.selectOpenUsageForUpdate(usageId);
        if (usage == null) {
            throw new LabBusinessException(
                LabErrorCode.LAB_ILLEGAL_STATE_TRANSITION, "使用记录已归还或不存在");
        }
        if (!Objects.equals(usage.getDeviceId(), device.getId())
            || !Objects.equals(usage.getReservationId(), reservation.getId())) {
            throw new LabBusinessException(
                LabErrorCode.LAB_ILLEGAL_STATE_TRANSITION, "领用归属在加锁期间发生变化");
        }
        accessService.requireManagedDevice(operatorId, usage.getDeviceId());
        stateGuard.require("CHECKED_OUT", reservation.getStatus());
        stateGuard.requireOneOf(device.getStatus(), "IN_USE", "FAULT");

        usage.close(command, operatorId, LocalDateTime.now(clock));
        affectedRows.requireOne(usageMapper.closeConditionally(usage));
        affectedRows.requireOne(reservationMapper.updateStatusConditionally(
            reservation.getId(), "CHECKED_OUT", "COMPLETED"));

        if (command.condition().isAbnormal()) {
            int changed = deviceMapper.updateStatusConditionally(
                device.getId(), "IN_USE", "FAULT");
            if (changed == 1) {
                historyService.record("DEVICE", device.getId(),
                    "IN_USE", "FAULT", operatorId, "异常归还");
            } else {
                stateGuard.require("FAULT", device.getStatus());
            }
            LabRepairOrder repairOrder = repairOrderService.openOrGetFromAbnormalReturn(
                usage, command.faultDescription(), operatorId);
            affectedRows.requireOne(usageMapper.linkRepairOrderConditionally(
                usage.getId(), repairOrder.getId()));
        } else {
            affectedRows.requireOne(deviceMapper.updateStatusConditionally(
                device.getId(), "IN_USE", "AVAILABLE"));
            historyService.record("DEVICE", device.getId(),
                "IN_USE", "AVAILABLE", operatorId, "正常归还");
        }
        historyService.record("RESERVATION", reservation.getId(),
            "CHECKED_OUT", "COMPLETED", operatorId, "办理归还");
        return assembler.toVo(usage);
    }

- [ ] 4.4 RepairOrderServiceImpl.openOrGetFromAbnormalReturn 使用 Propagation.MANDATORY，先查开放工单，再创建 WAIT_ASSIGN；数据库唯一约束冲突时回查并返回同一工单。无论新建还是复用，归还事务都把 usage.repair_order_id 条件关联到该工单，关联失败整体回滚。

    @Transactional(propagation = Propagation.MANDATORY)
    public LabRepairOrder openOrGetFromAbnormalReturn(
        LabUsageRecord usage,
        String description,
        Long reporterId
    ) {
        LabRepairOrder open =
            repairMapper.selectOpenByDeviceIdForUpdate(usage.getDeviceId());
        if (open != null) {
            return open;
        }
        LabRepairOrder created = LabRepairOrder.open(
            numberGenerator.next(),
            usage.getDeviceId(),
            RepairSourceType.ABNORMAL_RETURN,
            usage.getId(),
            reporterId,
            description,
            LocalDateTime.now(clock));
        repairMapper.insert(created);
        historyService.record("REPAIR_ORDER", created.getId(),
            null, "WAIT_ASSIGN", reporterId, "异常归还自动报修");
        return created;
    }

- [ ] 4.5 重复归还由 `closeConditionally` 的 `returned_at is null` 条件更新或当前状态检查返回 HTTP 409，不得返回一次新的成功结果。

- [ ] 4.6 使用 CyclicBarrier 启动 20 个“主动报修或异常归还”线程，断言只有一个 open_device_id、无死锁、没有两个开放工单。

    powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\run-lab-tests.ps1 `
      -DatabaseName lab_test_m4_task -Tests 'RepairOpenConcurrencyTest'

预期：PASS；20 个调用完成，openRepairCount 等于 1。

- [ ] 4.7 运行归还与回滚测试。

    powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\run-lab-tests.ps1 `
      -DatabaseName lab_test_m4_task `
      -Tests 'UsageReturnServiceTest,UsageRepairRollbackTest,RepairOpenConcurrencyTest'

预期：PASS；正常、异常、复用开放工单和故障注入回滚全部通过。

- [ ] 4.8 提交归还事务。

    git add ruoyi-lab ruoyi-admin/src/test/java/com/ruoyi/integration/service/UsageReturnServiceTest.java ruoyi-admin/src/test/java/com/ruoyi/integration/service/UsageRepairRollbackTest.java ruoyi-admin/src/test/java/com/ruoyi/integration/service/RepairOpenConcurrencyTest.java
    git commit -m "feat: implement atomic normal and abnormal returns"

## Task 5: 实现主动报修与管理员分派

**Files:**

- Modify: ruoyi-lab/src/main/java/com/ruoyi/lab/service/RepairOrderService.java
- Modify: ruoyi-lab/src/main/java/com/ruoyi/lab/service/impl/RepairOrderServiceImpl.java
- Create: ruoyi-lab/src/main/java/com/ruoyi/lab/service/RepairWorkerDirectory.java
- Create: ruoyi-lab/src/main/java/com/ruoyi/lab/vo/RepairOrderVo.java
- Create: ruoyi-admin/src/test/java/com/ruoyi/integration/service/RepairReportServiceTest.java
- Create: ruoyi-admin/src/test/java/com/ruoyi/integration/service/RepairAssignmentServiceTest.java
- Test: ruoyi-admin/src/test/java/com/ruoyi/integration/service/RepairAssignmentServiceTest.java

- [ ] 5.1 先写失败测试，覆盖有权用户报修、越权对象、重复报修、非维修角色分派和 WAIT_ASSIGN 以外分派。

    @Test
    void rejectsAssignmentToUserWithoutRepairRole() {
        long orderId = fixtures.openRepairOrder();
        long ordinaryUserId = fixtures.userWithoutRole("lab_repair_worker");

        assertThatThrownBy(() -> service.assign(
            orderId, new AssignRepairCommand(ordinaryUserId), managerId))
            .isInstanceOf(LabBusinessException.class)
            .extracting("code")
            .isEqualTo(LabErrorCode.ACCESS_DENIED);

        assertThat(fixtures.repairStatus(orderId)).isEqualTo("WAIT_ASSIGN");
    }

- [ ] 5.2 运行测试，确认报修和分派命令尚不存在。

    powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\run-lab-tests.ps1 `
      -DatabaseName lab_test_m4_task -Tests 'RepairReportServiceTest,RepairAssignmentServiceTest'

预期：FAIL，编译错误指向 reportFault 或 assign。

- [ ] 5.3 实现主动报修：锁 device 后再查询或锁开放 repair_order，校验调用者对设备有报告权限，将 AVAILABLE 或 IN_USE 条件更新为 FAULT，并创建或返回同一开放工单。

    @Transactional
    public RepairOrderVo reportFault(
        ReportFaultCommand command,
        Long reporterId
    ) {
        LabDevice device = deviceMapper.selectByIdForUpdate(command.deviceId());
        accessService.requireFaultReportAccess(reporterId, device);
        stateGuard.requireOneOf(device.getStatus(), "AVAILABLE", "IN_USE", "FAULT");
        if (!"FAULT".equals(device.getStatus())) {
            affectedRows.requireOne(deviceMapper.updateStatusConditionally(
                device.getId(), device.getStatus(), "FAULT"));
            historyService.record("DEVICE", device.getId(),
                device.getStatus(), "FAULT", reporterId, "主动报修");
        }
        LabRepairOrder order = openOrGet(
            device.getId(), RepairSourceType.ACTIVE_REPORT, null,
            reporterId, command.description());
        return assembler.toVo(order);
    }

- [ ] 5.4 实现管理员分派：先普通查询工单快照取得 deviceId，锁 device 后再锁 repair_order；校验实验室数据范围、维修角色和对象状态，条件更新 WAIT_ASSIGN → WAIT_REPAIR，并记录 assignee、assigned_by、assigned_at 和一条工单状态历史；重复分派或陈旧状态影响行数为 0 时返回 HTTP 409 且不写历史。

- [ ] 5.5 开放工单的重复主动报修由 `open_device_id` 唯一键兜底；同设备已有开放工单时回读并返回该工单，不得新增通用命令幂等记录。设备实际变为 FAULT 和新工单实际创建时各写一条状态历史；回读既有工单时不重复写。

- [ ] 5.6 运行报修与分派测试。

    powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\run-lab-tests.ps1 `
      -DatabaseName lab_test_m4_task -Tests 'RepairReportServiceTest,RepairAssignmentServiceTest'

预期：PASS；重复报修返回同一开放工单，错误角色和越权操作均无状态变化。

- [ ] 5.7 提交报修与分派。

    git add ruoyi-lab ruoyi-admin/src/test/java/com/ruoyi/integration/service/RepairReportServiceTest.java ruoyi-admin/src/test/java/com/ruoyi/integration/service/RepairAssignmentServiceTest.java
    git commit -m "feat: add fault reporting and repair assignment"

## Task 6: 实现维修、验收退回和验收通过

**Files:**

- Modify: ruoyi-lab/src/main/java/com/ruoyi/lab/service/RepairOrderService.java
- Modify: ruoyi-lab/src/main/java/com/ruoyi/lab/service/impl/RepairOrderServiceImpl.java
- Modify: ruoyi-lab/src/main/java/com/ruoyi/lab/service/DeviceStatusCommandService.java
- Modify: ruoyi-lab/src/main/java/com/ruoyi/lab/service/impl/DeviceStatusCommandServiceImpl.java
- Create: ruoyi-lab/src/main/java/com/ruoyi/lab/service/DeviceStatusTransitionGuard.java
- Create: ruoyi-lab/src/main/java/com/ruoyi/lab/service/impl/DeviceStatusTransitionGuardImpl.java
- Modify: ruoyi-lab/src/main/java/com/ruoyi/lab/domain/DeviceStatus.java
- Modify: ruoyi-lab/src/main/java/com/ruoyi/lab/mapper/LabUsageRecordMapper.java
- Modify: ruoyi-lab/src/main/java/com/ruoyi/lab/mapper/LabRepairOrderMapper.java
- Modify: ruoyi-lab/src/main/resources/mapper/lab/LabUsageRecordMapper.xml
- Modify: ruoyi-lab/src/main/resources/mapper/lab/LabRepairOrderMapper.xml
- Modify: ruoyi-lab/src/test/java/com/ruoyi/lab/service/DeviceStateMachineTest.java
- Create: ruoyi-lab/src/main/java/com/ruoyi/lab/service/DeviceAvailabilityService.java
- Create: ruoyi-lab/src/main/java/com/ruoyi/lab/service/impl/DeviceAvailabilityServiceImpl.java
- Create: ruoyi-lab/src/main/java/com/ruoyi/lab/service/RepairQueryService.java
- Create: ruoyi-lab/src/main/java/com/ruoyi/lab/service/impl/RepairQueryServiceImpl.java
- Modify: ruoyi-lab/src/main/java/com/ruoyi/lab/storage/LabAttachmentObjectAuthorizer.java
- Create: ruoyi-admin/src/test/java/com/ruoyi/integration/service/RepairWorkflowServiceTest.java
- Create: ruoyi-admin/src/test/java/com/ruoyi/integration/service/RepairQueryServiceTest.java
- Create: ruoyi-admin/src/test/java/com/ruoyi/integration/service/DeviceManualRecoveryGuardTest.java
- Create: ruoyi-admin/src/test/java/com/ruoyi/integration/service/DeviceStatusTransitionGuardTest.java
- Modify: ruoyi-admin/src/test/java/com/ruoyi/integration/mapper/UsageRepairLockMapperTest.java
- Create: ruoyi-admin/src/test/java/com/ruoyi/integration/security/RepairAttachmentAuthorizationTest.java
- Create: ruoyi-admin/src/test/java/com/ruoyi/integration/concurrency/UsageRepairCrossCommandConcurrencyTest.java
- Test: ruoyi-admin/src/test/java/com/ruoyi/integration/service/RepairWorkflowServiceTest.java
- Test: ruoyi-admin/src/test/java/com/ruoyi/integration/service/DeviceStatusTransitionGuardTest.java

- [ ] 6.1 先写失败测试，执行完整序列：分派、被分派人开始、提交结果、管理员退回、再次提交、管理员通过；并覆盖非本人处理、自验、陈旧状态、通过/退回空 reason 以及其他阻断条件。同时修改计划 01 的 DeviceStateMachineTest，新增 MAINTENANCE → AVAILABLE 普通命令被拒绝且不写历史的断言；计划 01 已禁止 FAULT → AVAILABLE，DISABLED → AVAILABLE 仍保留为候选资产启用命令，但必须通过本任务的动态守卫。

    @Test
    void rejectsOnceThenClosesAfterSecondRepair() {
        var order = fixtures.assignedRepair(repairerId);
        service.start(order.id(), repairerId);
        service.submitResult(order.id(),
            new SubmitRepairResultCommand("更换损坏电源模块"), repairerId);
        service.accept(order.id(),
            new AcceptRepairCommand(false, "持续压力测试未通过"), managerId);

        assertThat(fixtures.repairStatus(order.id())).isEqualTo("IN_PROGRESS");
        assertThat(fixtures.deviceStatus(order.deviceId())).isEqualTo("MAINTENANCE");

        service.submitResult(order.id(),
            new SubmitRepairResultCommand("更换模块并完成四小时压力测试"), repairerId);
        service.accept(order.id(),
            new AcceptRepairCommand(true, "现场验收通过"), managerId);

        assertThat(fixtures.repairStatus(order.id())).isEqualTo("CLOSED");
        assertThat(fixtures.deviceStatus(order.deviceId())).isEqualTo("AVAILABLE");
        assertThat(fixtures.repairHistoryStatuses(order.id()))
            .containsExactly("WAIT_ASSIGN", "WAIT_REPAIR", "IN_PROGRESS",
                "WAIT_ACCEPTANCE", "IN_PROGRESS", "WAIT_ACCEPTANCE", "CLOSED");
    }

    @Test
    void rejectsManualAvailableWhileRepairOrderIsOpen() {
        var device = fixtures.faultDeviceWithOpenRepair();

        assertThatThrownBy(() -> deviceStatusCommandService.changeStatus(
            device.id(),
            new DeviceStatusCommandDto(DeviceStatus.AVAILABLE, "管理员尝试手工恢复"),
            managerId))
            .isInstanceOf(LabBusinessException.class)
            .extracting("errorCode")
            .isEqualTo(LabErrorCode.LAB_ILLEGAL_STATE_TRANSITION);

        assertThat(fixtures.deviceStatus(device.id())).isEqualTo("FAULT");
        assertThat(fixtures.openRepairCount(device.id())).isEqualTo(1);
    }

    static Stream<Arguments> blockedManagedTransitions() {
        return Stream.of(DeviceStatus.AVAILABLE, DeviceStatus.FAULT, DeviceStatus.DISABLED)
            .flatMap(source -> Stream.of(BlockerKind.values()).map(blocker -> Arguments.of(
                source,
                source == DeviceStatus.DISABLED ? DeviceStatus.AVAILABLE : DeviceStatus.DISABLED,
                blocker)));
    }

    @ParameterizedTest
    @MethodSource("blockedManagedTransitions")
    void rejectsEveryManagedTransitionWhenAnyDynamicBlockerExists(
        DeviceStatus source,
        DeviceStatus target,
        BlockerKind blocker
    ) {
        var device = fixtures.deviceInEnabledLaboratory(source);
        fixtures.addBlocker(device.id(), blocker);

        assertThatThrownBy(() -> deviceStatusCommandService.changeStatus(
            device.id(), new DeviceStatusCommandDto(target, "资产状态命令"), managerId))
            .isInstanceOf(LabBusinessException.class)
            .extracting("errorCode")
            .isEqualTo(blocker.errorCode());

        assertThat(fixtures.deviceStatus(device.id())).isEqualTo(source.name());
        assertThat(fixtures.deviceHistoryCount(device.id())).isZero();
    }

    private enum BlockerKind {
        UNRETURNED_USAGE(LabErrorCode.LAB_DEVICE_UNAVAILABLE),
        OPEN_REPAIR(LabErrorCode.LAB_REPAIR_ALREADY_OPEN),
        OPEN_MAJOR_HAZARD(LabErrorCode.LAB_MAJOR_HAZARD_BLOCKED);

        private final LabErrorCode errorCode;

        BlockerKind(LabErrorCode errorCode) {
            this.errorCode = errorCode;
        }

        LabErrorCode errorCode() {
            return errorCode;
        }
    }

    @Test
    void enablingAlsoRequiresEnabledLaboratory() {
        var device = fixtures.disabledDeviceInDisabledLaboratory();

        assertThatThrownBy(() -> deviceStatusCommandService.changeStatus(
            device.id(), new DeviceStatusCommandDto(DeviceStatus.AVAILABLE, "重新启用"), managerId))
            .isInstanceOf(LabBusinessException.class)
            .extracting("errorCode")
            .isEqualTo(LabErrorCode.LAB_LABORATORY_DISABLED);

        assertThat(fixtures.deviceStatus(device.id())).isEqualTo("DISABLED");
        assertThat(fixtures.deviceHistoryCount(device.id())).isZero();
    }

    @ParameterizedTest
    @CsvSource({
        "AVAILABLE, DISABLED",
        "FAULT, DISABLED",
        "DISABLED, AVAILABLE"
    })
    void permitsManagedTransitionOnlyAfterEveryDynamicBlockerIsClear(
        DeviceStatus source,
        DeviceStatus target
    ) {
        var device = fixtures.deviceInEnabledLaboratory(source);

        deviceStatusCommandService.changeStatus(
            device.id(), new DeviceStatusCommandDto(target, "全部阻断已清除"), managerId);

        assertThat(fixtures.deviceStatus(device.id())).isEqualTo(target.name());
        assertThat(fixtures.deviceHistoryCount(device.id())).isEqualTo(1);
    }

DeviceStatusTransitionGuardTest 的 `OPEN_MAJOR_HAZARD` 夹具使用测试上下文替换的 `LabHazardBlocker` Bean，让 `assertNoMajorHazard(deviceId)` 抛 `LAB_MAJOR_HAZARD_BLOCKED`，不得访问本里程碑尚不存在的 lab_hazard 表；计划 04 Task 5 必须再用真实 lab_hazard 行重复同一矩阵。

- [ ] 6.2 运行测试并确认状态机命令未实现。

    powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\run-lab-tests.ps1 `
      -DatabaseName lab_test_m4_task `
      -Tests 'DeviceStateMachineTest,RepairWorkflowServiceTest,RepairQueryServiceTest,RepairAttachmentAuthorizationTest,DeviceManualRecoveryGuardTest,DeviceStatusTransitionGuardTest,UsageRepairLockMapperTest,UsageRepairCrossCommandConcurrencyTest'

预期：FAIL，start、submitResult、accept 或 DeviceStatusTransitionGuard 尚未实现。

- [ ] 6.3 实现 start：先普通查询工单快照取得 deviceId，锁 device 后确认该设备不存在 returned_at IS NULL 的使用记录，再锁 repair_order；存在未归还使用记录时返回 HTTP 409 且不改状态。仅 assignee 可将 WAIT_REPAIR 改为 IN_PROGRESS，同时将设备 FAULT 改为 MAINTENANCE；两个实际变化各写一条状态历史，条件更新影响行数为 0 时返回 HTTP 409 且不写历史。

- [ ] 6.4 实现 submitResult：沿用 device → repair_order 锁序，仅 assignee 可将 IN_PROGRESS 改为 WAIT_ACCEPTANCE，保存完整 repair_result 和 result_submitted_at，并写工单状态历史；设备保持 MAINTENANCE，重复提交或陈旧状态返回 HTTP 409 且不写历史。

- [ ] 6.5 实现 accept：仅授权实验室管理员可验收，accepted_by 不得等于 assignee；AcceptRepairCommand 对通过和退回都要求 1～1000 字非空 reason，Service 入口再次 trim 并拒绝空值，直接调用也不能绕过，确保两条验收历史原因非空。退回回到 IN_PROGRESS，通过关闭工单后调用 DeviceAvailabilityService；重复验收或陈旧状态返回 HTTP 409。

    @Transactional
    public RepairOrderVo accept(
        Long orderId,
        AcceptRepairCommand command,
        Long managerId
    ) {
        LabRepairOrder snapshot = repairMapper.selectById(orderId);
        notFoundGuard.requireVisible(snapshot);
        LabDevice device = deviceMapper.selectByIdForUpdate(snapshot.getDeviceId());
        LabRepairOrder order = repairMapper.selectByIdForUpdate(orderId);
        if (!Objects.equals(order.getDeviceId(), device.getId())) {
            throw new LabBusinessException(
                LabErrorCode.LAB_ILLEGAL_STATE_TRANSITION, "维修工单归属在加锁期间发生变化");
        }
        accessService.requireManagedDevice(managerId, order.getDeviceId());
        dutySeparation.requireDifferent(managerId, order.getAssigneeId(), "维修人员不能验收本人结果");
        stateGuard.require("WAIT_ACCEPTANCE", order.getStatus());

        if (!command.passed()) {
            order.rejectAcceptance(managerId, command.reason(), LocalDateTime.now(clock));
            repairMapper.saveAcceptanceRejection(order);
            historyService.record("REPAIR_ORDER", orderId,
                "WAIT_ACCEPTANCE", "IN_PROGRESS", managerId, command.reason());
            return assembler.toVo(order);
        }

        order.passAcceptance(managerId, command.reason(), LocalDateTime.now(clock));
        repairMapper.saveAcceptancePassed(order);
        historyService.record("REPAIR_ORDER", orderId,
            "WAIT_ACCEPTANCE", "CLOSED", managerId, command.reason());
        availabilityService.restoreAfterRepair(order.getDeviceId(), managerId);
        return assembler.toVo(order);
    }

- [ ] 6.6 使用计划 01 冻结的 `changeStatus(Long deviceId, DeviceStatusCommandDto command, Long actorId)` 三参数签名收紧 DeviceStatusCommandService 与 DeviceStatus 状态机：普通资产状态命令不得将 FAULT 或 MAINTENANCE 改为 AVAILABLE，即使操作者是管理员且当前未发现开放工单也必须返回 HTTP 409。唯一维修恢复入口是内部 DeviceAvailabilityService，Controller 不直接暴露该服务。对 AVAILABLE→DISABLED、FAULT→DISABLED、DISABLED→AVAILABLE，DeviceStatusCommandServiceImpl 必须先锁 device，再调用唯一的 DeviceStatusTransitionGuard；守卫依次查询未归还使用、开放维修、LabHazardBlocker，启用时最后校验所属实验室 ENABLED。任一失败均在状态条件更新和历史插入之前抛出，事务内不得产生部分历史。

    public interface DeviceStatusTransitionGuard {
        void assertNoOperationalBlocker(LabDevice lockedDevice, DeviceStatus targetStatus);
    }

    @Override
    public void assertNoOperationalBlocker(
        LabDevice lockedDevice,
        DeviceStatus targetStatus
    ) {
        DeviceStatus sourceStatus = lockedDevice.getStatus();
        boolean managedToggle =
            targetStatus == DeviceStatus.DISABLED
                && (sourceStatus == DeviceStatus.AVAILABLE || sourceStatus == DeviceStatus.FAULT)
            || sourceStatus == DeviceStatus.DISABLED
                && targetStatus == DeviceStatus.AVAILABLE;
        if (!managedToggle) {
            return;
        }
        if (!usageRecordMapper.selectUnreturnedIdsByDeviceIdForUpdate(
                lockedDevice.getId()).isEmpty()) {
            throw new LabBusinessException(
                LabErrorCode.LAB_DEVICE_UNAVAILABLE, "设备存在未归还使用记录");
        }
        if (!repairOrderMapper.selectOpenIdsByDeviceIdForUpdate(
                lockedDevice.getId()).isEmpty()) {
            throw new LabBusinessException(
                LabErrorCode.LAB_REPAIR_ALREADY_OPEN, "设备存在开放维修单");
        }
        hazardBlocker.assertNoMajorHazard(lockedDevice.getId());
        if (targetStatus == DeviceStatus.AVAILABLE) {
            LabLaboratory laboratory = laboratoryMapper.selectByIdForUpdate(
                lockedDevice.getLaboratoryId());
            if (laboratory == null || laboratory.getStatus() != LaboratoryStatus.ENABLED) {
                throw new LabBusinessException(
                    LabErrorCode.LAB_LABORATORY_DISABLED, "所属实验室未启用");
            }
        }
    }

LabUsageRecordMapper 与 LabRepairOrderMapper 固定增加以下 locking/current-read 方法，XML 过滤 `del_flag='0'`，开放维修状态只包含 WAIT_ASSIGN、WAIT_REPAIR、IN_PROGRESS、WAIT_ACCEPTANCE，并按主键排序后 `FOR UPDATE`。禁止用普通 `exists/count`，因为命令先前的对象快照可能已建立 REPEATABLE READ 一致性视图。所有创建、领用、归还、报修、维修和隐患写路径均已先取得同一 device 行锁，因此守卫在 device 锁后的 current read 不允许与新阻断事实并发穿透。

    List<Long> selectUnreturnedIdsByDeviceIdForUpdate(Long deviceId);
    List<Long> selectOpenIdsByDeviceIdForUpdate(Long deviceId);

    <select id="selectUnreturnedIdsByDeviceIdForUpdate" resultType="long">
      select id
      from lab_usage_record
      where device_id = #{deviceId}
        and returned_at is null
        and del_flag = '0'
      order by id
      for update
    </select>

    <select id="selectOpenIdsByDeviceIdForUpdate" resultType="long">
      select id
      from lab_repair_order
      where device_id = #{deviceId}
        and status in ('WAIT_ASSIGN','WAIT_REPAIR','IN_PROGRESS','WAIT_ACCEPTANCE')
        and del_flag = '0'
      order by id
      for update
    </select>

- [ ] 6.7 DeviceAvailabilityService 锁设备后仅在设备当前为 MAINTENANCE、实验室启用、无 returned_at IS NULL 的使用记录、无其他开放维修单且无未销号重大隐患时条件恢复 AVAILABLE；AVAILABLE、IN_USE、FAULT、DISABLED 等其他当前状态一律保持不变。只有实际恢复时写一条设备状态历史，否则不写历史，供计划 04 隐患销号时再次重算。

- [ ] 6.8 RepairQueryService 统一应用对象数据范围，详情按 create_time 返回报修、分派、维修提交、退回和验收历史及附件元数据。

- [ ] 6.9 扩展 LabAttachmentObjectAuthorizer 支持 REPAIR_ORDER：报告人只读本人提交工单，assignee 可在处理阶段上传，实验室管理员按对象范围管理；CLOSED 后附件只读。复用计划 01 的大小、数量、签名和存储边界，RepairAttachmentAuthorizationTest 覆盖跨实验室上传/下载 403。

- [ ] 6.10 增加跨命令并发测试：用 CyclicBarrier 同时触发同设备的异常归还与主动报修、维修开始与管理员状态命令，循环 20 轮并设置 10 秒超时；允许一个命令成功、另一个返回 409，但不得出现 MySQL deadlock、锁等待超时或部分历史。

    @Test
    void crossCommandsNeverAcquireDeviceLocksInReverseOrder() {
        assertTimeoutPreemptively(Duration.ofSeconds(10), () -> {
            var scenario = fixtures.checkedOutUsageReadyForConcurrentFaults();
            List<Throwable> failures = concurrent.runAtBarrier(
                () -> usageService.returnUsage(scenario.usageId(),
                    new ReturnUsageCommand(ReturnCondition.FAULT, "并发归还", "无法启动"), managerId),
                () -> repairService.reportFault(
                    new ReportFaultCommand(scenario.deviceId(), "并发报修"), reporterId));
            assertThat(failures).allMatch(this::isExpectedConflictOrSuccess);
            assertThat(failures).noneMatch(this::isDeadlockOrLockTimeout);
            assertThat(fixtures.openRepairCount(scenario.deviceId())).isEqualTo(1);
        });
    }

- [ ] 6.11 运行维修闭环、附件授权、手工恢复、防死锁和查询测试。

    powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\run-lab-tests.ps1 `
      -DatabaseName lab_test_m4_task `
      -Tests 'DeviceStateMachineTest,RepairWorkflowServiceTest,RepairQueryServiceTest,RepairAttachmentAuthorizationTest,DeviceManualRecoveryGuardTest,DeviceStatusTransitionGuardTest,UsageRepairLockMapperTest,UsageRepairCrossCommandConcurrencyTest'

预期：PASS；退回后可再次提交，通过后仅在无阻断条件时 AVAILABLE；三条管理员启停候选边在任一动态阻断存在时均返回对应 409 且不写历史，全部阻断清除后才各成功一次。

- [ ] 6.12 提交维修闭环。

    git add ruoyi-lab ruoyi-admin/src/test/java/com/ruoyi/integration/service/RepairWorkflowServiceTest.java ruoyi-admin/src/test/java/com/ruoyi/integration/service/RepairQueryServiceTest.java ruoyi-admin/src/test/java/com/ruoyi/integration/security/RepairAttachmentAuthorizationTest.java ruoyi-admin/src/test/java/com/ruoyi/integration/service/DeviceManualRecoveryGuardTest.java ruoyi-admin/src/test/java/com/ruoyi/integration/service/DeviceStatusTransitionGuardTest.java ruoyi-admin/src/test/java/com/ruoyi/integration/mapper/UsageRepairLockMapperTest.java ruoyi-admin/src/test/java/com/ruoyi/integration/concurrency/UsageRepairCrossCommandConcurrencyTest.java
    git commit -m "feat: complete repair processing and acceptance"

## Task 7: 暴露受权限保护的命令 API 和菜单字典

**Files:**

- Create: ruoyi-admin/src/main/java/com/ruoyi/web/controller/lab/LabUsageRecordController.java
- Create: ruoyi-admin/src/main/java/com/ruoyi/web/controller/lab/LabRepairOrderController.java
- Create: ruoyi-admin/src/main/resources/db/migration/V4_1__usage_repair_menus_and_dictionaries.sql
- Create: ruoyi-admin/src/test/java/com/ruoyi/integration/web/controller/lab/UsageCommandApiTest.java
- Create: ruoyi-admin/src/test/java/com/ruoyi/integration/web/controller/lab/RepairCommandApiTest.java
- Modify: ruoyi-admin/src/main/java/com/ruoyi/web/core/config/SwaggerConfig.java
- Test: ruoyi-admin/src/test/java/com/ruoyi/integration/web/controller/lab/RepairCommandApiTest.java

- [ ] 7.1 先写 MockMvc 失败测试，覆盖使用记录分页/详情的数据范围与筛选、登录、按钮权限、对象范围、职责分离、DTO 校验、验收通过但空 reason 返回 400、409 状态冲突和不存在普通状态编辑接口。

    mockMvc.perform(put("/lab/repair-orders/{id}", orderId)
            .with(jwtFor(managerId))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"status\":\"CLOSED\"}"))
        .andExpect(status().isMethodNotAllowed());

    mockMvc.perform(post("/lab/repair-orders/{id}/accept", orderId)
            .with(jwtFor(repairerId))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"passed\":true,\"reason\":\"通过\"}"))
        .andExpect(status().isForbidden());

- [ ] 7.2 运行 API 测试，确认 Controller 尚不存在。

    powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\run-lab-tests.ps1 `
      -DatabaseName lab_test_m4_task -Tests 'UsageCommandApiTest,RepairCommandApiTest'

预期：FAIL，目标 URL 返回 404 或 Controller 编译缺失。

- [ ] 7.3 创建固定 Controller。Controller 只做身份、权限、Bean Validation 和 service 调用，不注入 Mapper。

    @RestController
    @RequestMapping("/lab/usage-records")
    @Tag(name = "实验室设备领用归还")
    public class LabUsageRecordController extends BaseController {
        private final UsageCommandService commandService;
        private final UsageQueryService queryService;

        public LabUsageRecordController(
            UsageCommandService commandService,
            UsageQueryService queryService
        ) {
            this.commandService = commandService;
            this.queryService = queryService;
        }

        @PreAuthorize("@ss.hasPermi('lab:usage:list')")
        @GetMapping
        public TableDataInfo list(@Valid UsageQueryDto query) {
            startPage();
            return getDataTable(queryService.list(query, getUserId()));
        }

        @PreAuthorize("@ss.hasPermi('lab:usage:query')")
        @GetMapping("/{id}")
        public AjaxResult detail(@PathVariable Long id) {
            return success(queryService.detail(id, getUserId()));
        }

        @PreAuthorize("@ss.hasPermi('lab:usage:checkout')")
        @PostMapping("/check-out")
        public AjaxResult checkOut(
            @Valid @RequestBody CheckOutCommand command
        ) {
            return success(commandService.checkOut(command, getUserId()));
        }

        @PreAuthorize("@ss.hasPermi('lab:usage:return')")
        @PostMapping("/{id}/return")
        public AjaxResult returnUsage(
            @PathVariable Long id,
            @Valid @RequestBody ReturnUsageCommand command
        ) {
            return success(commandService.returnUsage(id, command, getUserId()));
        }
    }

- [ ] 7.4 Repair Controller 暴露 GET 列表和详情以及 POST /report、/{id}/assign、/{id}/start、/{id}/submit-result、/{id}/accept；权限固定为：

    lab:repair:list
    lab:repair:query
    lab:repair:report
    lab:repair:assign
    lab:repair:process
    lab:repair:accept

- [ ] 7.5 创建 V4_1，写入“领用归还”和“维修工单”菜单、上述按钮权限、lab_return_condition 与 lab_repair_status 字典；迁移使用明确列名和固定唯一 menu_id 4300～4315。

    insert into sys_menu
      (menu_id, menu_name, parent_id, order_num, path, component, query_param,
       route_name, is_frame, is_cache, menu_type, visible, status, perms, icon,
       create_by, create_time, update_by, update_time, remark)
    values
      (4300, '领用归还', 2000, 3, 'usage', 'lab/usage/index', null,
       'LabUsage', 1, 0, 'C', '0', '0', 'lab:usage:list', 'time-range',
       'admin', sysdate(), '', null, '实验室设备领用归还'),
      (4310, '维修工单', 2000, 4, 'repair', 'lab/repair/index', null,
       'LabRepair', 1, 0, 'C', '0', '0', 'lab:repair:list', 'tool',
       'admin', sysdate(), '', null, '实验室设备维修闭环');

    insert into sys_menu
      (menu_id, menu_name, parent_id, order_num, path, component, query_param,
       route_name, is_frame, is_cache, menu_type, visible, status, perms, icon,
       create_by, create_time, update_by, update_time, remark)
    values
      (4301, '设备领用', 4300, 1, '', '', null, '', 1, 0, 'F', '0', '0',
       'lab:usage:checkout', '#', 'admin', sysdate(), '', null, ''),
      (4302, '设备归还', 4300, 2, '', '', null, '', 1, 0, 'F', '0', '0',
       'lab:usage:return', '#', 'admin', sysdate(), '', null, ''),
      (4303, '领用详情', 4300, 3, '', '', null, '', 1, 0, 'F', '0', '0',
       'lab:usage:query', '#', 'admin', sysdate(), '', null, ''),
      (4311, '维修查询', 4310, 1, '', '', null, '', 1, 0, 'F', '0', '0',
       'lab:repair:query', '#', 'admin', sysdate(), '', null, ''),
      (4312, '提交故障', 4310, 2, '', '', null, '', 1, 0, 'F', '0', '0',
       'lab:repair:report', '#', 'admin', sysdate(), '', null, ''),
      (4313, '维修分派', 4310, 3, '', '', null, '', 1, 0, 'F', '0', '0',
       'lab:repair:assign', '#', 'admin', sysdate(), '', null, ''),
      (4314, '维修处理', 4310, 4, '', '', null, '', 1, 0, 'F', '0', '0',
       'lab:repair:process', '#', 'admin', sysdate(), '', null, ''),
      (4315, '维修验收', 4310, 5, '', '', null, '', 1, 0, 'F', '0', '0',
       'lab:repair:accept', '#', 'admin', sysdate(), '', null, '');

- [ ] 7.6 在 SwaggerConfig 的 lab 分组覆盖 com.ruoyi.web.controller.lab，记录 Authorization、409 错误码和全部状态枚举；领用、归还、分派、维修和验收的重复请求行为明确记录为数据库唯一键回读或 HTTP 409。

- [ ] 7.7 运行 API、架构和迁移测试。

    powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\run-lab-tests.ps1 `
      -DatabaseName lab_test_m4_task `
      -Tests 'UsageCommandApiTest,RepairCommandApiTest,LabLayerArchitectureTest,UsageRepairMigrationTest'

预期：PASS；Controller 不依赖 Mapper，权限、HTTP 状态和迁移全部通过。

- [ ] 7.8 提交 API 与权限。

    git add ruoyi-admin ruoyi-lab
    git commit -m "feat: expose secured usage and repair commands"

## Task 8: 实现领用与维修前端

**Files:**

- Create: ruoyi-ui/src/api/lab/usage.js
- Create: ruoyi-ui/src/api/lab/repair.js
- Create: ruoyi-ui/src/views/lab/usage/index.vue
- Create: ruoyi-ui/src/views/lab/repair/index.vue
- Create: ruoyi-ui/src/views/lab/repair/detail.vue
- Create: ruoyi-ui/src/components/lab/RepairTimeline.vue
- Create: ruoyi-ui/tests/unit/lab/usage-api.spec.js
- Create: ruoyi-ui/tests/unit/lab/usage-view.spec.js
- Create: ruoyi-ui/tests/unit/lab/repair-view.spec.js
- Test: ruoyi-ui/tests/unit/lab/repair-view.spec.js

- [ ] 8.1 先写失败的 API 单测，断言命令 URL、HTTP 方法、请求体以及 BIGINT 字符串不被 Number 转换。

    it('returns abnormal usage without coercing the id', async () => {
      await returnUsage('9007199254740993', {
        condition: 'FAULT',
        note: '无法开机',
        faultDescription: '电源指示灯不亮'
      })

      expect(request).toHaveBeenCalledWith({
        url: '/lab/usage-records/9007199254740993/return',
        method: 'post',
        data: {
          condition: 'FAULT',
          note: '无法开机',
          faultDescription: '电源指示灯不亮'
        }
      })
    })

- [ ] 8.2 运行前端测试并确认模块不存在。

    corepack yarn --cwd .\ruoyi-ui test tests/unit/lab/usage-api.spec.js tests/unit/lab/usage-view.spec.js tests/unit/lab/repair-view.spec.js

预期：FAIL，无法解析 @/api/lab/usage 或页面组件。

- [ ] 8.3 实现 API 文件。前端在提交期间禁用同一按钮；后端仍以唯一键和 expected-status 条件更新作为重复请求的最终防线。

    import request from '@/utils/request'

    export function checkOut(data) {
      return request({
        url: '/lab/usage-records/check-out',
        method: 'post',
        data
      })
    }

    export function returnUsage(id, data) {
      return request({
        url: '/lab/usage-records/' + id + '/return',
        method: 'post',
        data
      })
    }

- [ ] 8.4 领用归还页面按权限显示按钮；异常归还时 faultDescription 必填；提交期间禁用按钮，成功后刷新列表，409 显示服务端 msg。

- [ ] 8.5 维修页面按角色显示：管理员可分派和验收，维修人员只可处理本人 WAIT_REPAIR/IN_PROGRESS 工单，学生和其他有权限用户可提交故障；详情用 RepairTimeline 展示完整状态历史，并复用 AttachmentPanel 按后端返回的 canManage 显示工单附件。

- [ ] 8.6 在 375px 视口运行组件测试，核心命令可操作；管理表格允许横向滚动且不隐藏状态和操作列。

- [ ] 8.7 运行前端测试与生产构建。

    corepack yarn --cwd .\ruoyi-ui test tests/unit/lab/usage-api.spec.js tests/unit/lab/usage-view.spec.js tests/unit/lab/repair-view.spec.js
    corepack yarn --cwd .\ruoyi-ui build:prod

预期：三个测试文件全部 PASS；生产构建退出码 0。

- [ ] 8.8 提交前端。

    git add ruoyi-ui/src/api/lab ruoyi-ui/src/views/lab/usage ruoyi-ui/src/views/lab/repair ruoyi-ui/src/components/lab/RepairTimeline.vue ruoyi-ui/tests/unit/lab
    git commit -m "feat: add usage and repair user interfaces"

## Task 9: 建立事务、权限和验收场景证据

**Files:**

- Create: ruoyi-admin/src/test/java/com/ruoyi/integration/acceptance/UsageRepairAcceptanceTest.java
- Create: ruoyi-admin/src/test/java/com/ruoyi/integration/acceptance/UsageRepairPermissionTest.java
- Create: scripts/smoke-usage-repair.ps1
- Create: docs/testing/m4-usage-repair-report.md
- Modify: docs/requirements/lab-management-srs.md
- Test: ruoyi-admin/src/test/java/com/ruoyi/integration/acceptance/UsageRepairAcceptanceTest.java

- [ ] 9.1 先写三个失败的普通角色验收测试：正常领用归还、异常归还、维修验收退回后通过；测试不得使用超级管理员绕过权限。

    @Test
    void abnormalReturnCreatesOrLinksOneRepairOrderAtomically() {
        var approved = api.asLabManager(managerToken).approve(reservationId);
        var usage = api.asLabManager(managerToken).checkOut(
            approved.id());
        var returned = api.asLabManager(managerToken).returnAbnormal(
            usage.id(), "外壳破损", "壳体在使用中开裂");

        assertThat(returned.reservationStatus()).isEqualTo("COMPLETED");
        assertThat(returned.deviceStatus()).isEqualTo("FAULT");
        assertThat(returned.repairStatus()).isEqualTo("WAIT_ASSIGN");
        assertThat(sql.openRepairCount(returned.deviceId())).isEqualTo(1);
    }

- [ ] 9.2 运行验收测试，确认至少一个场景失败。

    powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\run-lab-tests.ps1 `
      -DatabaseName lab_test_m4_task -Tests 'UsageRepairAcceptanceTest,UsageRepairPermissionTest'

预期：在验收夹具完成前 FAIL，输出明确的缺失前置数据或断言。

- [ ] 9.3 完成验收夹具与 smoke-usage-repair.ps1。脚本仅从环境变量读取五角色凭据，不打印密码、Token 或完整响应头；脚本重复执行归还、分派、维修和验收命令时断言 HTTP 409，重复领用和报修断言数据库记录数始终为 1。

- [ ] 9.4 运行计划 03 后端全量回归。

    powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify.ps1

预期：BUILD SUCCESS；领用、归还、维修、权限、迁移和架构测试全绿。

- [ ] 9.5 运行前端全量测试、生产构建和烟雾脚本。

    corepack yarn --cwd .\ruoyi-ui test
    corepack yarn --cwd .\ruoyi-ui build:prod
    powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\smoke-usage-repair.ps1

预期：Vitest 全绿，生产构建成功，烟雾脚本退出码 0。

- [ ] 9.6 在 m4-usage-repair-report.md 记录 Git 提交、Flyway V4_0/V4_1、测试数量、三条验收场景、并发开放工单结果和事务故障注入结果；不记录凭据。

- [ ] 9.7 在 SRS 追踪附录仅将有可复现证据的 FR-USE-001～005、FR-REP-001～005、AT-04、AT-07、AT-08 标记为计划 03 已验证。

- [ ] 9.8 执行最终静态自检。

    git diff --check
    rg -n "(TO)(DO)|(TB)(D)|待.{0}补充|password\s*[:=]\s*[^$<{]|BEGIN (RSA|OPENSSH) PRIVATE KEY|Bearer eyJ" . -g '!target/**' -g '!ruoyi-ui/node_modules/**'
    git status --short

预期：diff 检查通过；无占位文本和真实凭据；状态只包含计划 03 的预期文件。

- [ ] 9.9 提交验收证据并标记阶段。

    git add ruoyi-admin/src/test ruoyi-lab/src/test ruoyi-ui/tests scripts/smoke-usage-repair.ps1 docs/testing/m4-usage-repair-report.md docs/requirements/lab-management-srs.md
    git commit -m "test: verify usage and repair acceptance flows"
    git tag milestone/m4-usage-repairs

## 计划 03 最终回归命令

    powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-migrations.ps1
    powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify.ps1
    corepack yarn --cwd .\ruoyi-ui test
    corepack yarn --cwd .\ruoyi-ui build:prod
    powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\smoke-usage-repair.ps1
    git status --short

预期：所有命令退出码 0；数据库同一预约仅一条使用记录、同一设备仅一张开放维修单；工作区干净。
