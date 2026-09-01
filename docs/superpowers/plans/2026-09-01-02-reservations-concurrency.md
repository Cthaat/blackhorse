# Laboratory Reservations and Concurrency Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 交付预约查询、申请、批准、驳回、取消、过期和爽约领域命令，在 MySQL 设备行锁下保证同设备有效预约不重叠，并证明 20 个相同时间段并发请求最多一个成功。

**Architecture:** Controller 继续只放在 ruoyi-admin 的 lab 包，预约实体、Mapper、XML、事务 Service、资格与隐患阻断契约、生命周期命令全部放入 ruoyi-lab。MySQL 是并发与幂等的事实源；申请和审批按固定锁顺序锁定设备行后重新校验实验室、设备、资格、隐患和半开时间段冲突，Redis 不参与正确性判定。前端通过动态菜单加载申请、我的预约和审批页面。

**Tech Stack:** Java 17、Spring Boot 3.5.16、RuoYi-Vue 3.9.2、MyBatis-Plus 3.5.17、PageHelper 2.1.1、MySQL 8、Redis 7、Flyway 11.7.2、Springdoc 2.9.0、Knife4j 4.5.0、Vue 3.5.26、Vite 6.4.1、Element Plus 2.13.1、Pinia 3.0.4、Vitest 3.2.4、Node 22、Yarn 1.22.22。

---

## 需求与退出门禁

本计划覆盖 FR-QUA-004、FR-RES-001 至 FR-RES-006；交付 AT-04 的申请与批准部分、完整交付 AT-05、交付 AT-06 的预约提交与审批阶段资格、故障设备和停用实验室阻断部分。领用与归还由计划 03 完成，重大隐患数据与真实阻断实现由计划 04 完成。

本计划结束时必须满足：

- V2_1 数据库可无损升级到 V3_0、V3_1，全新空库也能一次迁移成功；
- 所有时间段使用半开区间 `[start, end)`，仅 PENDING、APPROVED、CHECKED_OUT 参与冲突；
- 预约申请在一个事务内锁设备、重校验、查冲突、插入 PENDING 和状态历史；
- 24 小时保留期内，相同用户、相同 X-Idempotency-Key、相同请求体回读同一 reservationId 的当前表示，不增加预约或历史；相同键不同请求体返回 409；到期键按条件清空后可重新使用；
- 批准重新校验资格、实验室、设备、隐患契约和其他有效预约，驳回必须有原因；
- 学生只能取消本人 PENDING 或 APPROVED，CANCELLED 立即释放时段；
- `expirePending` 与 `markNoShow` 是可由计划 05 Quartz 调用、可重入且带条件更新的领域命令；
- 学生只见本人预约，管理员只见数据范围内预约，详情和命令不能通过猜测 ID 越权；
- 20 个独立事务同时申请同设备同区间时恰好 1 个成功、19 个返回 409，数据库不存在有效重叠记录；
- 后端测试、前端单元测试、Maven 打包和前端生产构建全部通过。

## Task 1: 用 V3_0 建立预约事实表与数据库幂等约束

**Files:**

- Create: ruoyi-admin/src/main/resources/db/migration/V3_0__lab_reservations.sql
- Create: ruoyi-admin/src/test/java/com/ruoyi/integration/lab/migration/LabReservationSchemaMigrationIT.java
- Test: ruoyi-admin/src/test/java/com/ruoyi/integration/lab/migration/LabReservationSchemaMigrationIT.java

- [ ] 1.1 先写迁移测试，通过 `application-test.yml` 的 LAB_TEST_DB_URL、LAB_TEST_DB_USERNAME、LAB_TEST_DB_PASSWORD 连接独立 MySQL 8 测试库，从 V2_1 启动 Flyway，断言预约表、设备外键、预约编号唯一约束、用户幂等唯一约束和冲突查询复合索引存在。该测试放在 ruoyi-admin，ruoyi-lab 不增加启动配置或反向依赖。

    @Test
    void migratesReservationSchemaFromMilestoneTwo() {
        flyway.migrate();
        assertThat(tableNames()).contains("lab_reservation");
        assertThat(indexNames("lab_reservation")).contains(
            "uk_lab_reservation_no",
            "uk_lab_reservation_idempotency",
            "idx_lab_reservation_conflict",
            "idx_lab_reservation_scope",
            "idx_lab_reservation_idempotency_expiry");
        assertThat(foreignKeyNames("lab_reservation"))
            .contains("fk_lab_reservation_device");
    }

- [ ] 1.2 重建专用库，按已验证管理host/port覆盖调用者遗留URL并显式开启测试Flyway，运行测试确认红灯来自 V3_0 尚不存在。

    powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\run-lab-tests.ps1 -DatabaseName lab_test_m3_upgrade -Tests 'LabReservationSchemaMigrationIT'

预期：BUILD FAILURE，失败信息明确指出 lab_reservation 或约束不存在。

- [ ] 1.3 创建 V3_0。预约编号和幂等约束由数据库最终兜底，idempotency_key 只保存服务端校验后的 1 至 64 位 ASCII 字符，request_hash 保存规范化请求的 SHA-256。ReservationPolicy 从 Task 3 起就要启动，因此五个策略参数也必须在 V3_0 首次写入，不能延迟到前端菜单迁移 V3_1。

    CREATE TABLE lab_reservation (
        id BIGINT NOT NULL AUTO_INCREMENT,
        reservation_no VARCHAR(32) NOT NULL,
        device_id BIGINT NOT NULL,
        applicant_id BIGINT NOT NULL,
        start_time DATETIME(3) NOT NULL,
        end_time DATETIME(3) NOT NULL,
        purpose VARCHAR(200) NOT NULL,
        remark VARCHAR(500) NULL,
        status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
        approval_by BIGINT NULL,
        approval_time DATETIME(3) NULL,
        approval_reason VARCHAR(500) NULL,
        cancel_time DATETIME(3) NULL,
        cancel_reason VARCHAR(500) NULL,
        idempotency_key VARCHAR(64) NULL,
        request_hash CHAR(64) NULL,
        idempotency_expires_at DATETIME(3) NULL,
        version INT NOT NULL DEFAULT 0,
        create_by VARCHAR(64) NOT NULL DEFAULT '',
        create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
        update_by VARCHAR(64) NOT NULL DEFAULT '',
        update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
        del_flag CHAR(1) NOT NULL DEFAULT '0',
        PRIMARY KEY (id),
        UNIQUE KEY uk_lab_reservation_no (reservation_no),
        UNIQUE KEY uk_lab_reservation_idempotency (applicant_id, idempotency_key),
        KEY idx_lab_reservation_conflict (device_id, status, start_time, end_time, del_flag),
        KEY idx_lab_reservation_scope (applicant_id, status, start_time, del_flag),
        KEY idx_lab_reservation_idempotency_expiry (idempotency_expires_at),
        CONSTRAINT fk_lab_reservation_device FOREIGN KEY (device_id) REFERENCES lab_device(id)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

    INSERT INTO sys_config
      (config_name, config_key, config_value, config_type,
       create_by, create_time, update_by, update_time, remark)
    SELECT seed.config_name, seed.config_key, seed.config_value, 'Y',
           'admin', NOW(3), '', NULL, 'V3预约与领用固定策略'
    FROM (
      SELECT '最小提前分钟' AS config_name, 'lab.reservation.min-lead-minutes' AS config_key, '30' AS config_value
      UNION ALL SELECT '最大提前天数', 'lab.reservation.max-advance-days', '30'
      UNION ALL SELECT '最短预约分钟', 'lab.reservation.min-duration-minutes', '30'
      UNION ALL SELECT '最长预约分钟', 'lab.reservation.max-duration-minutes', '480'
      UNION ALL SELECT '领用迟到分钟', 'lab.usage.checkout.late-minutes', '15'
    ) seed
    WHERE NOT EXISTS (
      SELECT 1 FROM sys_config current_config
      WHERE current_config.config_key = seed.config_key
    );

- [ ] 1.4 在迁移测试中插入同一用户重复幂等键和重复预约编号，断言 MySQL 拒绝；插入不同用户相同幂等键应成功；把已过期行的 idempotency_key、request_hash、idempotency_expires_at 条件更新为 NULL 后，同一用户可重新使用该键。另断言上述五个 config_key 各恰有一条且值严格为 30、30、30、480、15。时间顺序由 Service 校验与集成测试保证，不依赖 MySQL CHECK 兼容行为。

- [ ] 1.5 运行迁移与静态检查。

    powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\run-lab-tests.ps1 -DatabaseName lab_test_m3_upgrade -Tests 'LabReservationSchemaMigrationIT'
    powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-migrations.ps1

预期：测试全绿；V3_0 校验成功，无版本重复和越序。

- [ ] 1.6 提交预约表。

    git add ruoyi-admin/src/main/resources/db/migration/V3_0__lab_reservations.sql ruoyi-admin/src/test/java/com/ruoyi/integration/lab/migration/LabReservationSchemaMigrationIT.java
    git commit -m "feat: add reservation schema"

## Task 2: 建立半开区间、设备行锁与冲突 Mapper

**Files:**

- Create: ruoyi-lab/src/main/java/com/ruoyi/lab/domain/LabReservation.java
- Create: ruoyi-lab/src/main/java/com/ruoyi/lab/domain/ReservationStatus.java
- Create: ruoyi-lab/src/main/java/com/ruoyi/lab/domain/ReservationInterval.java
- Create: ruoyi-lab/src/main/java/com/ruoyi/lab/mapper/LabReservationMapper.java
- Create: ruoyi-lab/src/main/resources/mapper/lab/LabReservationMapper.xml
- Modify: ruoyi-lab/src/main/java/com/ruoyi/lab/mapper/LabDeviceMapper.java
- Modify: ruoyi-lab/src/main/resources/mapper/lab/LabDeviceMapper.xml
- Create: ruoyi-lab/src/test/java/com/ruoyi/lab/domain/ReservationIntervalTest.java
- Create: ruoyi-admin/src/test/java/com/ruoyi/integration/lab/mapper/LabReservationMapperIT.java
- Test: ruoyi-lab/src/test/java/com/ruoyi/lab/domain/ReservationIntervalTest.java
- Test: ruoyi-admin/src/test/java/com/ruoyi/integration/lab/mapper/LabReservationMapperIT.java

- [ ] 2.1 先写参数化区间测试，固定相邻不冲突、左交叉、右交叉、完全包含、完全被包含、相同区间和零长度拒绝。

    static Stream<Arguments> overlapCases() {
        Instant ten = Instant.parse("2026-09-10T02:00:00Z");
        Instant eleven = Instant.parse("2026-09-10T03:00:00Z");
        return Stream.of(
            Arguments.of(ten, eleven, eleven, eleven.plusSeconds(3600), false),
            Arguments.of(ten, eleven, ten.minusSeconds(1800), ten.plusSeconds(1800), true),
            Arguments.of(ten, eleven, ten.plusSeconds(1800), eleven.plusSeconds(1800), true),
            Arguments.of(ten, eleven, ten.minusSeconds(1800), eleven.plusSeconds(1800), true),
            Arguments.of(ten, eleven, ten.plusSeconds(600), ten.plusSeconds(1200), true),
            Arguments.of(ten, eleven, ten, eleven, true)
        );
    }

- [ ] 2.2 先写 MySQL 集成测试，分别插入八种预约状态，断言只有 PENDING、APPROVED、CHECKED_OUT 会被冲突查询计数；结束时间等于新开始时间和开始时间等于新结束时间都不冲突。

- [ ] 2.3 运行测试确认红灯。

    powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\run-lab-tests.ps1 -DatabaseName lab_test_m3_task -Tests 'ReservationIntervalTest,LabReservationMapperIT'

预期：BUILD FAILURE，原因是 ReservationInterval 和 Mapper statement 尚不存在。

- [ ] 2.4 固定跨计划 Mapper 名称为 `LabReservationMapper`，声明 `int updateStatusConditionally(Long reservationId, String expected, String target)`；冲突 SQL 必须使用半开区间公式且状态集合写死在服务端 XML 中。

    SELECT COUNT(1)
    FROM lab_reservation r
    WHERE r.device_id = #{deviceId}
      AND r.status IN ('PENDING', 'APPROVED', 'CHECKED_OUT')
      AND r.start_time < #{newEnd}
      AND r.end_time > #{newStart}
      AND (#{excludeReservationId} IS NULL OR r.id <> #{excludeReservationId})
      AND r.del_flag = '0'

- [ ] 2.5 在 `LabDeviceMapper` 增加 `selectByIdForUpdate(Long deviceId)`，在 `LabReservationMapper` 增加 `selectByIdForUpdate(Long reservationId)`；两个方法名与装箱类型是计划 03、计划 04 的固定编译契约，SQL 都只锁目标业务行。

    SELECT d.*
    FROM lab_device d
    WHERE d.id = #{deviceId}
      AND d.del_flag = '0'
    FOR UPDATE

    SELECT r.*
    FROM lab_reservation r
    WHERE r.id = #{reservationId}
      AND r.del_flag = '0'
    FOR UPDATE

- [ ] 2.6 所有事务统一遵守 device-first 锁序：申请直接使用请求中的 deviceId 锁 lab_device，再查询冲突并创建预约；批准、驳回和取消先普通读取预约快照取得 deviceId，再锁 lab_device，随后锁或重读 lab_reservation，最后查冲突和写状态。禁止先锁预约再锁设备，避免跨命令死锁。

- [ ] 2.7 运行区间和 Mapper 测试，并用 EXPLAIN 验证冲突查询命中 `idx_lab_reservation_conflict`。

    powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\run-lab-tests.ps1 -DatabaseName lab_test_m3_task -Tests 'ReservationIntervalTest,LabReservationMapperIT'

预期：所有用例通过；相邻区间计数为 0；EXPLAIN 的 key 为 idx_lab_reservation_conflict。

- [ ] 2.8 提交预约持久化与锁契约。

    git add ruoyi-lab/src/main/java/com/ruoyi/lab/domain ruoyi-lab/src/main/java/com/ruoyi/lab/mapper ruoyi-lab/src/main/resources/mapper/lab ruoyi-lab/src/test ruoyi-admin/src/test/java/com/ruoyi/integration/lab/mapper/LabReservationMapperIT.java
    git commit -m "feat: add reservation locking and overlap queries"

## Task 3: 实现事务申请、数据库幂等和重复请求语义

**Files:**

- Create: ruoyi-lab/src/main/java/com/ruoyi/lab/dto/ReservationApplyDto.java
- Create: ruoyi-lab/src/main/java/com/ruoyi/lab/vo/ReservationVo.java
- Create: ruoyi-lab/src/main/java/com/ruoyi/lab/service/ReservationCommandService.java
- Create: ruoyi-lab/src/main/java/com/ruoyi/lab/service/ReservationPolicy.java
- Create: ruoyi-lab/src/main/java/com/ruoyi/lab/service/LabSystemParameterProvider.java
- Create: ruoyi-lab/src/main/java/com/ruoyi/lab/service/LabHazardBlocker.java
- Create: ruoyi-lab/src/main/java/com/ruoyi/lab/service/LabIdempotencyStore.java
- Create: ruoyi-lab/src/main/java/com/ruoyi/lab/service/IdempotencySnapshot.java
- Create: ruoyi-lab/src/main/java/com/ruoyi/lab/service/impl/NoRecordedHazardBlocker.java
- Create: ruoyi-lab/src/main/java/com/ruoyi/lab/service/impl/RedisLabIdempotencyStore.java
- Create: ruoyi-lab/src/main/java/com/ruoyi/lab/service/impl/ReservationCommandServiceImpl.java
- Create: ruoyi-lab/src/main/java/com/ruoyi/lab/service/ReservationRequestHasher.java
- Create: ruoyi-lab/src/main/java/com/ruoyi/lab/mapper/LabSystemConfigMapper.java
- Create: ruoyi-lab/src/main/resources/mapper/lab/LabSystemConfigMapper.xml
- Create: ruoyi-lab/src/test/java/com/ruoyi/lab/service/ReservationApplicationTest.java
- Create: ruoyi-admin/src/test/java/com/ruoyi/integration/lab/service/ReservationIdempotencyIT.java
- Test: ruoyi-lab/src/test/java/com/ruoyi/lab/service/ReservationApplicationTest.java
- Test: ruoyi-admin/src/test/java/com/ruoyi/integration/lab/service/ReservationIdempotencyIT.java

- [ ] 3.1 先写 ReservationPolicy 规则测试，覆盖设备必填、purpose 去空白后 1 至 200 字、remark 最多 500 字、开始早于结束、至少 30 分钟、最多 8 小时、至少提前 30 分钟、最多提前 30 天，并断言四个边界通过 LabSystemParameterProvider 从 sys_config 读取。Provider 由 LabSystemConfigMapper 参数化查询实现，不导入 ruoyi-system Java 类型，保持计划 00 的模块依赖方向。API 输入输出固定带 `+08:00`，服务端、数据库会话与测试统一使用 Asia/Shanghai，DATETIME 按该时区解释。

- [ ] 3.2 先写幂等集成测试：保留期内同用户同键同规范请求体顺序调用和并发调用都返回同一 reservationId/reservationNo，预约与初始历史各只有一条；同键不同设备、时间或用途返回 409；不同用户可复用同一键；固定 Clock 推进至 24 小时后，旧行清空幂等字段并允许产生一条新预约。另用屏障让“过期键清理并重新申请”与旧预约的批准、取消和生命周期命令分别竞争，断言所有路径都先取得各自 device 锁、无死锁或锁等待超时，最终预约状态和幂等键唯一且历史无重复。

- [ ] 3.3 先写阻断测试，断言停用实验室、FAULT/DISABLED/IN_USE/MAINTENANCE 设备和 `LabQualificationGuard` 拒绝都会回滚预约与历史。

- [ ] 3.4 运行测试确认红灯。

    powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\run-lab-tests.ps1 -DatabaseName lab_test_m3_task -Tests 'ReservationApplicationTest,ReservationIdempotencyIT'

预期：BUILD FAILURE，失败由申请 Service、哈希器或阻断契约缺失导致。

- [ ] 3.5 固定隐患跨计划契约名称为 `LabHazardBlocker`。计划 04 将提供读取真实隐患表的实现；在 V3 数据库尚无隐患记录来源时，`NoRecordedHazardBlocker` 作为 `@ConditionalOnMissingBean(LabHazardBlocker.class)` 的 M3 实现，返回无记录，不创建或猜测隐患数据。

    public interface LabHazardBlocker {
        void assertNoMajorHazard(Long deviceId);
        boolean hasOpenMajorHazard(Long deviceId);
    }

- [ ] 3.6 API 的 OffsetDateTime 先按 Asia/Shanghai 转为 LocalDateTime，请求哈希固定对 `deviceId|startLocalDateTime|endLocalDateTime|normalizedPurpose|normalizedRemark` 的 UTF-8 字节计算 SHA-256；不把 JSON 属性顺序、等价的客户端偏移文本或空白差异作为不同请求。X-Idempotency-Key 缺失、非 ASCII、空白或超过 64 字符返回 400。

- [ ] 3.7 固定可选 Redis 加速端口；缓存命中仍须用 requestHash 校验并按 reservationId 回读 MySQL，缓存错误或 Redis 连接失败只记录告警并继续数据库路径。缓存和数据库幂等字段使用相同的 24 小时到期时刻，MySQL 唯一约束始终是并发兜底。

    public interface LabIdempotencyStore {
        Optional<IdempotencySnapshot> get(long userId, String command, String key);
        void put(long userId, String command, String key, IdempotencySnapshot value, Duration ttl);
    }

    public record IdempotencySnapshot(long reservationId, String requestHash) {}

- [ ] 3.8 复用计划 00 的 SRS 固定码 `LAB_RESERVATION_TIME_CONFLICT`、`LAB_MAJOR_HAZARD_BLOCKED` 与 `LAB_DUPLICATE_OPERATION`。在 `@Transactional` 申请方法中按固定次序执行：尝试缓存；只做非锁定查询读取同用户数据库幂等记录，活动记录校验哈希后直接按 reservationId 回读当前表示，过期记录只记下候选ID而不更新；随后先锁请求中的 device；若存在过期候选，再用 `WHERE id = ? AND applicant_id = ? AND idempotency_key = ? AND idempotency_expires_at <= now` 条件清空三个幂等字段；然后以 locking/current read 重新查询该用户与键，确保不复用事务早先的 REPEATABLE READ 快照；最后校验对象可读、实验室 ENABLED、设备 AVAILABLE、当前资格和隐患契约，查询有效重叠，生成唯一预约编号，插入 PENDING、请求哈希、24 小时到期时刻及状态历史。禁止在取得请求 device 锁前执行任何会锁定或更新 lab_reservation 的语句。时间冲突返回 LAB_RESERVATION_TIME_CONFLICT，相同键不同请求返回 LAB_DUPLICATE_OPERATION；唯一约束竞争转为同一预约当前表示或对应 409，不向客户端暴露 SQL 异常。提交后 best-effort 回填缓存；`RedisConnectionFailureException` 不得回滚数据库事务。

    public interface ReservationCommandService {
        ReservationVo apply(long applicantId, String idempotencyKey, ReservationApplyDto request);
    }

- [ ] 3.9 首次申请状态历史固定 `fromStatus = null`、`toStatus = PENDING`、reason=`提交预约申请`，operatorId 为申请人，traceId 来自 TraceIdFilter。事务失败不得留下历史或孤立预约。

- [ ] 3.10 运行单元、事务和幂等测试，额外模拟 `LabIdempotencyStore.get/put` 抛出 RedisConnectionFailureException，断言申请仍依靠 MySQL 成功且重复调用不新增记录。

    powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\run-lab-tests.ps1 -DatabaseName lab_test_m3_task -Tests 'ReservationApplicationTest,ReservationIdempotencyIT'

预期：规则与阻断测试全绿；相同键返回同一结果；不同请求体为 409；数据库无重复预约和重复历史。

- [ ] 3.11 提交预约申请切片。

    git add ruoyi-lab/src/main ruoyi-lab/src/test ruoyi-admin/src/test/java/com/ruoyi/integration/lab/service/ReservationIdempotencyIT.java
    git commit -m "feat: add transactional reservation application"

## Task 4: 实现批准、驳回、取消与状态历史

**Files:**

- Create: ruoyi-lab/src/main/java/com/ruoyi/lab/dto/ReservationDecisionDto.java
- Create: ruoyi-lab/src/main/java/com/ruoyi/lab/dto/ReservationCancelDto.java
- Create: ruoyi-lab/src/main/java/com/ruoyi/lab/service/ReservationStateMachine.java
- Modify: ruoyi-lab/src/main/java/com/ruoyi/lab/service/ReservationCommandService.java
- Modify: ruoyi-lab/src/main/java/com/ruoyi/lab/service/impl/ReservationCommandServiceImpl.java
- Create: ruoyi-admin/src/main/java/com/ruoyi/web/controller/lab/LabReservationController.java
- Create: ruoyi-lab/src/test/java/com/ruoyi/lab/service/ReservationDecisionTest.java
- Create: ruoyi-admin/src/test/java/com/ruoyi/integration/lab/security/ReservationObjectPermissionIT.java
- Create: ruoyi-admin/src/test/java/com/ruoyi/integration/lab/web/LabReservationControllerTest.java
- Test: ruoyi-lab/src/test/java/com/ruoyi/lab/service/ReservationDecisionTest.java
- Test: ruoyi-admin/src/test/java/com/ruoyi/integration/lab/security/ReservationObjectPermissionIT.java
- Test: ruoyi-admin/src/test/java/com/ruoyi/integration/lab/web/LabReservationControllerTest.java

- [ ] 4.1 先写状态机测试，固定 PENDING→APPROVED/REJECTED/CANCELLED/EXPIRED、APPROVED→CANCELLED/CHECKED_OUT/NO_SHOW；本计划实现其中批准、驳回、取消、过期和爽约，其他跳转返回 409。

- [ ] 4.2 先写批准事务测试：申请后资格过期、实验室停用、设备变为 FAULT、隐患 Guard 拒绝、审批本人预约或出现另一有效冲突时批准失败且仍为 PENDING；所有条件满足时变为 APPROVED 并只写一条历史。

- [ ] 4.3 先写驳回与取消测试：空原因驳回为 400；学生仅可取消本人 PENDING/APPROVED；管理员不能冒充申请人取消；REJECTED、CANCELLED、EXPIRED、NO_SHOW、CHECKED_OUT 都不能取消。

- [ ] 4.4 运行测试确认红灯。

    powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\run-lab-tests.ps1 -DatabaseName lab_test_m3_task -Tests 'ReservationDecisionTest,ReservationObjectPermissionIT,LabReservationControllerTest'

预期：BUILD FAILURE，原因是状态机、事务命令或 Controller 尚未实现。

- [ ] 4.5 本 Task 才扩展 ReservationCommandService 与实现类，新增 approve、reject、cancel 三个方法，不在 Task 3 留临时占位实现。批准事务先取预约快照取得 deviceId，按 Task 2 锁顺序锁设备和预约，再以 Asia/Shanghai 的审批 LocalDateTime 调用 `LabQualificationGuard.assertQualified` 与 `LabHazardBlocker.assertNoMajorHazard`，检查实验室 ENABLED、设备 AVAILABLE，并排除当前预约 ID 查询其他冲突。批准人必须拥有预约所属实验室的数据范围且 `approverId != applicantId`，禁止审批本人预约。

- [ ] 4.6 驳回只允许 PENDING，原因去空白后 1 至 500 字；取消只允许当前申请人对 PENDING/APPROVED 执行，原因可选但最多 500 字。所有状态更新使用 `WHERE id = ? AND status = ? AND version = ?`，影响行数不为 1 时返回 409。

- [ ] 4.7 Controller 固定在 `ruoyi-admin/src/main/java/com/ruoyi/web/controller/lab/`，端点固定为：

    POST /lab/reservations
    GET /lab/reservations
    GET /lab/reservations/{id}
    POST /lab/reservations/{id}/commands/approve
    POST /lab/reservations/{id}/commands/reject
    POST /lab/reservations/{id}/commands/cancel

申请读取 X-Idempotency-Key；Controller 使用 `@PreAuthorize` 和 `@Log`，Service 再做对象权限和职责分离检查。成功创建返回 201，重复同键返回 200，状态冲突返回 409，数据越权返回 403。

- [ ] 4.8 每个成功状态变化在同一事务写 lab_status_history；失败、幂等重试回读同一预约当前表示和相同状态条件更新失败都不增加历史。

- [ ] 4.9 运行命令、权限和 WebMvc 测试。

    powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\run-lab-tests.ps1 -DatabaseName lab_test_m3_task -Tests 'ReservationDecisionTest,ReservationObjectPermissionIT,LabReservationControllerTest'

预期：全部通过；批准会复核资格和资产；驳回无原因返回 400；越权返回 403；非法状态返回 409。

- [ ] 4.10 提交审批取消切片。

    git add ruoyi-lab/src/main ruoyi-lab/src/test ruoyi-admin/src/main/java/com/ruoyi/web/controller/lab/LabReservationController.java ruoyi-admin/src/test/java/com/ruoyi/integration/lab/web/LabReservationControllerTest.java ruoyi-admin/src/test/java/com/ruoyi/integration/lab/security/ReservationObjectPermissionIT.java
    git commit -m "feat: add reservation decisions and cancellation"

## Task 5: 实现分域查询与设备占用区间

**Files:**

- Create: ruoyi-lab/src/main/java/com/ruoyi/lab/dto/ReservationQueryDto.java
- Create: ruoyi-lab/src/main/java/com/ruoyi/lab/vo/OccupiedRangeVo.java
- Create: ruoyi-lab/src/main/java/com/ruoyi/lab/service/ReservationQueryService.java
- Create: ruoyi-lab/src/main/java/com/ruoyi/lab/service/impl/ReservationQueryServiceImpl.java
- Modify: ruoyi-lab/src/main/resources/mapper/lab/LabReservationMapper.xml
- Modify: ruoyi-lab/src/main/java/com/ruoyi/lab/service/impl/DeviceServiceImpl.java
- Modify: ruoyi-admin/src/main/java/com/ruoyi/web/controller/lab/LabReservationController.java
- Modify: ruoyi-admin/src/main/java/com/ruoyi/web/controller/lab/LabDeviceController.java
- Modify: ruoyi-admin/src/test/java/com/ruoyi/integration/lab/web/LabReservationControllerTest.java
- Modify: ruoyi-admin/src/test/java/com/ruoyi/integration/lab/web/LabDeviceControllerTest.java
- Create: ruoyi-admin/src/test/java/com/ruoyi/integration/lab/service/ReservationQueryIT.java
- Test: ruoyi-admin/src/test/java/com/ruoyi/integration/lab/service/ReservationQueryIT.java

- [ ] 5.1 先写查询集成测试：学生只能看到 applicantId 为本人；实验室管理员只看到 `LabDataScope.laboratoryIds` 内设备预约；跨范围详情为 403；系统管理员没有业务角色时不能执行业务写操作。

- [ ] 5.2 先写筛选测试，覆盖预约编号、设备、申请人、状态、时间范围组合，pageNum 从 1 开始、pageSize 最大 100、排序只允许 reservationNo/startTime/createTime/status。

- [ ] 5.3 先写设备 occupied-ranges 测试，指定 `[from, to)` 只返回 PENDING、APPROVED、CHECKED_OUT 的交集区间，终态不占用，相邻边界不返回。

- [ ] 5.4 运行测试确认红灯。

    powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\run-lab-tests.ps1 -DatabaseName lab_test_m3_task -Tests 'ReservationQueryIT'

预期：BUILD FAILURE，原因是 QueryService 或 Mapper 查询未实现。

- [ ] 5.5 实现查询 Service，学生分支强制覆盖 applicantId 为当前用户，忽略客户端伪造值；管理分支必须同时拥有 `lab:reservation:list` 和资产数据范围。详情复用相同判定，不以列表按钮作为对象权限依据。

- [ ] 5.6 将计划 01 的 `/lab/devices/{id}/occupied-ranges` 接到 `LabReservationMapper`，响应只含 startTime、endTime、reservationStatus，不暴露申请人用途和备注。输入区间最长 30 天，from 必须早于 to。

- [ ] 5.7 运行查询、权限与 Controller 回归。

    powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\run-lab-tests.ps1 -DatabaseName lab_test_m3_task -Tests 'ReservationQueryIT,LabDeviceControllerTest,LabReservationControllerTest'

预期：学生、管理员数据隔离和 occupied-ranges 全绿；非法分页或排序返回 400。

- [ ] 5.8 提交预约查询切片。

    git add ruoyi-lab/src/main ruoyi-lab/src/test ruoyi-admin/src/main/java/com/ruoyi/web/controller/lab/LabReservationController.java ruoyi-admin/src/main/java/com/ruoyi/web/controller/lab/LabDeviceController.java ruoyi-admin/src/test/java/com/ruoyi/integration/lab/service/ReservationQueryIT.java ruoyi-admin/src/test/java/com/ruoyi/integration/lab/web/LabDeviceControllerTest.java ruoyi-admin/src/test/java/com/ruoyi/integration/lab/web/LabReservationControllerTest.java
    git commit -m "feat: add scoped reservation queries"

## Task 6: 实现过期与爽约领域命令

**Files:**

- Create: ruoyi-lab/src/main/java/com/ruoyi/lab/service/ReservationLifecycleService.java
- Create: ruoyi-lab/src/main/java/com/ruoyi/lab/service/LabSystemOperatorProvider.java
- Create: ruoyi-lab/src/main/java/com/ruoyi/lab/service/LabSystemOperator.java
- Create: ruoyi-lab/src/main/java/com/ruoyi/lab/service/impl/ReservationLifecycleServiceImpl.java
- Create: ruoyi-lab/src/main/java/com/ruoyi/lab/service/impl/LabSystemOperatorProviderImpl.java
- Create: ruoyi-lab/src/main/java/com/ruoyi/lab/mapper/LabSystemUserMapper.java
- Create: ruoyi-lab/src/main/resources/mapper/lab/LabSystemUserMapper.xml
- Modify: ruoyi-lab/src/main/java/com/ruoyi/lab/mapper/LabReservationMapper.java
- Modify: ruoyi-lab/src/main/resources/mapper/lab/LabReservationMapper.xml
- Create: ruoyi-admin/src/test/java/com/ruoyi/integration/lab/service/LabSystemOperatorProviderIT.java
- Create: ruoyi-admin/src/test/java/com/ruoyi/integration/lab/service/ReservationLifecycleServiceIT.java
- Test: ruoyi-admin/src/test/java/com/ruoyi/integration/lab/service/LabSystemOperatorProviderIT.java
- Test: ruoyi-admin/src/test/java/com/ruoyi/integration/lab/service/ReservationLifecycleServiceIT.java

- [ ] 6.1 先写系统操作主体集成测试：从 `lab.system.operator-user-id` 解析出 user_id=9000，回查 `__lab_system_operator__` 且确认 status=`1`、del_flag=`0`、无角色和岗位。配置缺失、非 Long、用户不存在、用户被启用、逻辑删除或绑定任何角色/岗位时，Provider 必须抛安全的 INTERNAL_ERROR，不能回退到 0、当前登录人或 RuoYi 超级管理员。

- [ ] 6.2 先写 fixed-time 与可重入集成测试：PENDING 在 startTime 到达时变 EXPIRED；APPROVED 在 `startTime + lab.usage.checkout.late-minutes` 到达时变 NO_SHOW；未到边界、其他状态和已被并发改变的记录不变。同一 now 连续调用两次，第一次返回变更数，第二次返回 0；状态历史每个对象只增加一条，operator_id 严格为 9000，reason 固定且 traceId 存在。任一系统操作主体验证失败时，预约和历史都不变化。

- [ ] 6.3 运行测试确认红灯。

    powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\run-lab-tests.ps1 -DatabaseName lab_test_m3_task -Tests 'LabSystemOperatorProviderIT,ReservationLifecycleServiceIT'

预期：BUILD FAILURE，原因是系统操作主体 Provider、LifecycleService 与候选查询尚不存在。

- [ ] 6.4 固定计划 05 Quartz 调用契约，不在本计划创建调度器。

    public interface ReservationLifecycleService {
        int expirePending(LocalDateTime now, int batchSize);
        int markNoShow(LocalDateTime now, int batchSize);
    }

    public record LabSystemOperator(Long userId, String userName) {}

    public interface LabSystemOperatorProvider {
        LabSystemOperator requiredOperator();
    }

- [ ] 6.5 每次调用先通过 LabSystemOperatorProvider 验证系统主体，再校验 batchSize 为 1 至 500，从 sys_config 读取并验证领用宽限分钟数。候选查询按 id 升序、限制 batchSize；逐条按统一锁顺序获取设备与预约，执行带旧状态条件更新，只有更新 1 行才写历史。

    UPDATE lab_reservation
    SET status = #{toStatus},
        version = version + 1,
        update_by = #{operatorName},
        update_time = #{now}
    WHERE id = #{id}
      AND status = #{fromStatus}
      AND version = #{version}
      AND del_flag = '0'

- [ ] 6.6 EXPIRED 历史 reason 固定为“开始时间已到仍未审批”，NO_SHOW 固定为“超过领用宽限期仍未领用”；operatorId 必须使用 Provider 返回的计划 00 禁登录系统账号 ID，updateBy 使用其 userName。禁止在 Service 硬编码 9000，禁止写 0、空值、当前请求用户或 Quartz 线程名。

- [ ] 6.7 运行生命周期与竞争测试。

    powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\run-lab-tests.ps1 -DatabaseName lab_test_m3_task -Tests 'LabSystemOperatorProviderIT,ReservationLifecycleServiceIT,ReservationDecisionTest'

预期：边界、可重入和并发状态变化用例全绿；没有重复历史。

- [ ] 6.8 提交生命周期领域命令。

    git add ruoyi-lab/src/main/java/com/ruoyi/lab/service/ReservationLifecycleService.java ruoyi-lab/src/main/java/com/ruoyi/lab/service/LabSystemOperatorProvider.java ruoyi-lab/src/main/java/com/ruoyi/lab/service/LabSystemOperator.java ruoyi-lab/src/main/java/com/ruoyi/lab/service/impl/ReservationLifecycleServiceImpl.java ruoyi-lab/src/main/java/com/ruoyi/lab/service/impl/LabSystemOperatorProviderImpl.java ruoyi-lab/src/main/java/com/ruoyi/lab/mapper/LabSystemUserMapper.java ruoyi-lab/src/main/java/com/ruoyi/lab/mapper/LabReservationMapper.java ruoyi-lab/src/main/resources/mapper/lab/LabSystemUserMapper.xml ruoyi-lab/src/main/resources/mapper/lab/LabReservationMapper.xml ruoyi-admin/src/test/java/com/ruoyi/integration/lab/service/LabSystemOperatorProviderIT.java ruoyi-admin/src/test/java/com/ruoyi/integration/lab/service/ReservationLifecycleServiceIT.java
    git commit -m "feat: add reservation expiry domain commands"

## Task 7: 用 V3_1 接入预约权限菜单与前端流程

**Files:**

- Create: ruoyi-admin/src/main/resources/db/migration/V3_1__lab_reservation_permissions_menus.sql
- Create: ruoyi-ui/src/api/lab/reservation.js
- Create: ruoyi-ui/src/utils/lab/idempotencyKey.js
- Create: ruoyi-ui/src/views/lab/reservation/index.vue
- Create: ruoyi-ui/src/views/lab/reservation/apply.vue
- Create: ruoyi-ui/src/views/lab/reservation/detail.vue
- Create: ruoyi-ui/src/components/lab/ReservationIntervalPicker.vue
- Create: ruoyi-ui/tests/unit/api/lab/reservation.spec.js
- Create: ruoyi-ui/tests/unit/utils/lab/idempotencyKey.spec.js
- Create: ruoyi-ui/tests/unit/views/lab/reservation-apply.spec.js
- Test: ruoyi-ui/tests/unit/api/lab/reservation.spec.js
- Test: ruoyi-ui/tests/unit/utils/lab/idempotencyKey.spec.js
- Test: ruoyi-ui/tests/unit/views/lab/reservation-apply.spec.js

- [ ] 7.1 先写 API 与页面测试，固定申请携带 X-Idempotency-Key、请求期间按钮禁用、网络重试复用原键、用户编辑请求后生成新键、409 显示统一冲突消息，取消或批准成功后刷新详情与占用区间。

    corepack yarn --cwd .\ruoyi-ui test --run tests/unit/api/lab/reservation.spec.js tests/unit/utils/lab/idempotencyKey.spec.js tests/unit/views/lab/reservation-apply.spec.js

预期：测试失败，原因是 API、幂等键工具和页面尚不存在。

- [ ] 7.2 创建 V3_1，使用 2300 至 2399 的固定菜单 ID；学生拥有申请、本人列表、本人详情、取消权限，实验室管理员拥有范围列表、详情、批准、驳回权限。角色关联通过 role_key 子查询完成。V3_1 只负责菜单、按钮和角色权限，不重复写 V3_0 已建立的五个策略参数；迁移测试仍断言这些参数各恰有一条。ReservationPolicy 对缺失、非数字或越界配置启动失败，不静默改用不同规则。

    INSERT INTO sys_menu
      (menu_id, menu_name, parent_id, order_num, path, component, query_param, route_name,
       is_frame, is_cache, menu_type, visible, status, perms, icon,
       create_by, create_time, update_by, update_time, remark)
    VALUES
      (2300, '预约管理', 2000, 2, 'reservations', NULL, NULL, '', 1, 0, 'M', '0', '0', '', 'date', 'admin', NOW(3), '', NULL, 'M3预约并发目录'),
      (2301, '我的预约', 2300, 1, 'mine', 'lab/reservation/index', 'mode=mine', 'MyLabReservations', 1, 0, 'C', '0', '0', 'lab:reservation:mine', 'list', 'admin', NOW(3), '', NULL, ''),
      (2302, '预约审批', 2300, 2, 'approval', 'lab/reservation/index', 'mode=approval', 'LabReservationApproval', 1, 0, 'C', '0', '0', 'lab:reservation:list', 'audit', 'admin', NOW(3), '', NULL, ''),
      (2310, '预约申请', 2301, 1, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'lab:reservation:apply', '#', 'admin', NOW(3), '', NULL, ''),
      (2311, '预约取消', 2301, 2, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'lab:reservation:cancel', '#', 'admin', NOW(3), '', NULL, ''),
      (2320, '预约批准', 2302, 1, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'lab:reservation:approve', '#', 'admin', NOW(3), '', NULL, ''),
      (2321, '预约驳回', 2302, 2, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'lab:reservation:reject', '#', 'admin', NOW(3), '', NULL, '');

- [ ] 7.3 `idempotencyKey.js` 使用 Web Crypto `crypto.randomUUID()`，键只保存在当前表单状态；同一提交的网络重试复用，成功、明确 4xx 或表单字段改变后清除。不得把键放入 localStorage 或 URL。

    export function createIdempotencyKey() {
      if (!globalThis.crypto?.randomUUID) {
        throw new Error('当前浏览器不支持安全的幂等键生成')
      }
      return globalThis.crypto.randomUUID()
    }

- [ ] 7.4 IntervalPicker 使用本地 Asia/Shanghai 时间输入，提交前转换为带 `+08:00` 的 ISO 8601；相邻区间允许选择，占用区间禁用；所有 reservationId/deviceId 保持字符串。

- [ ] 7.5 详情页面按后端状态和按钮权限共同决定操作可见性，服务端 409 后重新拉取详情；批准和驳回对话框禁止重复提交，驳回原因必填。

- [ ] 7.6 运行前端测试与生产构建。

    corepack yarn --cwd .\ruoyi-ui test --run tests/unit/api/lab/reservation.spec.js tests/unit/utils/lab/idempotencyKey.spec.js tests/unit/views/lab/reservation-apply.spec.js
    corepack yarn --cwd .\ruoyi-ui build:prod

预期：指定测试全绿；生产构建退出码为 0；动态路由可解析且提交不会生成重复请求。

- [ ] 7.7 提交菜单和前端。

    git add ruoyi-admin/src/main/resources/db/migration/V3_1__lab_reservation_permissions_menus.sql ruoyi-ui/src/api/lab/reservation.js ruoyi-ui/src/utils/lab/idempotencyKey.js ruoyi-ui/src/views/lab/reservation ruoyi-ui/src/components/lab/ReservationIntervalPicker.vue ruoyi-ui/tests/unit
    git commit -m "feat: add reservation user interface"

## Task 8: 用 20 线程证明并发正确性并建立 M3 证据

**Files:**

- Create: ruoyi-admin/src/test/java/com/ruoyi/integration/lab/acceptance/ReservationConcurrencyIT.java
- Create: ruoyi-admin/src/test/java/com/ruoyi/integration/lab/acceptance/ReservationAcceptanceIT.java
- Create: ruoyi-admin/src/test/java/com/ruoyi/integration/lab/security/ReservationSecurityIT.java
- Create: scripts/smoke-m3-reservations.ps1
- Create: docs/testing/m3-reservations-report.md
- Modify: docs/requirements/lab-management-srs.md
- Test: ruoyi-admin/src/test/java/com/ruoyi/integration/lab/acceptance/ReservationConcurrencyIT.java
- Test: ruoyi-admin/src/test/java/com/ruoyi/integration/lab/acceptance/ReservationAcceptanceIT.java
- Test: ruoyi-admin/src/test/java/com/ruoyi/integration/lab/security/ReservationSecurityIT.java

- [ ] 8.1 先写真实 MySQL 8、非测试事务包裹的 20 线程集成测试。每个线程使用独立幂等键与独立 Spring 事务，同时申请同一 deviceId 和同一 `[start, end)`。

    @Test
    void permitsAtMostOneActiveReservationForTwentyConcurrentRequests() throws Exception {
        int workers = 20;
        ExecutorService pool = Executors.newFixedThreadPool(workers);
        CountDownLatch ready = new CountDownLatch(workers);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<AttemptResult>> futures = new ArrayList<>();
        for (int index = 0; index < workers; index++) {
            String key = "concurrency-" + index;
            futures.add(pool.submit(() -> {
                ready.countDown();
                assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
                try {
                    ReservationVo created = applicationService.apply(
                        studentId, key, sameIntervalRequest());
                    return AttemptResult.success(created.id());
                } catch (LabBusinessException exception) {
                    return AttemptResult.failure(exception.getErrorCode());
                }
            }));
        }
        assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
        start.countDown();
        List<AttemptResult> results = new ArrayList<>();
        for (Future<AttemptResult> future : futures) {
            results.add(future.get(30, TimeUnit.SECONDS));
        }
        pool.shutdownNow();
        assertThat(results.stream().filter(AttemptResult::success).count()).isEqualTo(1);
        assertThat(results.stream().filter(result -> result.errorCode() == LabErrorCode.LAB_RESERVATION_TIME_CONFLICT).count()).isEqualTo(19);
        assertThat(reservationMapper.countActiveOverlaps(deviceId, startTime, endTime, null)).isEqualTo(1);
    }

- [ ] 8.2 增加数据库最终断言：成功预约状态为 PENDING，初始历史恰好一条，失败请求没有残留行；再创建两个首尾相接区间，断言都成功。

- [ ] 8.3 增加 AT-04 部分验收：学生申请→管理员批准，预约从 PENDING 到 APPROVED，两条状态历史顺序、操作人、原因、时间与 traceId 正确；明确不执行领用和归还。

- [ ] 8.4 增加 AT-06 部分验收：申请后资格过期不能批准；FAULT 设备和停用实验室不能申请或批准；`LabHazardBlocker` 被调用且拒绝会回滚。重大隐患真实数据场景保留给计划 04 的联合验收。

- [ ] 8.5 增加 FR-RES-004 至 FR-RES-006 验收：PENDING/APPROVED 取消释放时段；过期与爽约命令 5 分钟内可重复执行；学生与管理员查询范围隔离。

- [ ] 8.6 单独运行 20 线程测试三次，排除偶然串行成功。

    1..3 | ForEach-Object { powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\run-lab-tests.ps1 -DatabaseName lab_test_m3_concurrency -Tests 'ReservationConcurrencyIT'; if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE } }

预期：三次均 BUILD SUCCESS；每次 1 成功、19 个 LAB_RESERVATION_TIME_CONFLICT、有效重叠计数为 1，日志无死锁和锁等待超时。

`smoke-m3-reservations.ps1`不得依赖调用者预先启动的服务。脚本固定使用未占用的loopback端口并先拒绝端口冲突，调用`reset-test-db.ps1`重建`lab_test_m3_smoke`且检查退出码，再由已验证的管理host/port和应用测试账号构造子进程专用`LAB_DB_URL/USERNAME/PASSWORD`，覆盖而不是继承调用者的运行时数据源；同时只从环境变量读取演示账号、JWT密钥及文件根目录。它以`Start-Process -PassThru`启动本次`verify.ps1`刚构建的发布JAR，等待Flyway到V3_1并通过HTTP健康探针后执行申请、批准、取消、过期、爽约和查询剧本。所有步骤放在`try`中，`finally`只按保存的Java进程句柄停止并等待本次子进程；禁止按端口、进程名或`taskkill`结束用户已有进程，日志不得输出连接串、密码或Token。

- [ ] 8.7 运行 M3 全门禁。

    powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify.ps1
    powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\assert-surefire-tests.ps1 -Module ruoyi-admin -RequiredTests 'LabReservationSchemaMigrationIT,LabReservationMapperIT,ReservationIdempotencyIT,ReservationObjectPermissionIT,ReservationQueryIT,LabSystemOperatorProviderIT,ReservationLifecycleServiceIT,ReservationConcurrencyIT,ReservationAcceptanceIT,ReservationSecurityIT'
    powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-migrations.ps1
    corepack yarn --cwd .\ruoyi-ui test
    corepack yarn --cwd .\ruoyi-ui build:prod
    powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\smoke-m3-reservations.ps1

预期：命令均退出 0；报告断言输出 10 个 VERIFIED TEST，证明预约 IT 未被静默跳过；V2_1→V3_1 与空库→V3_1 成功；申请、批准、取消、过期、爽约和查询烟雾链路通过。

- [ ] 8.8 在报告记录 MySQL 隔离级别、20 线程开始屏障、每次成功/冲突数、SQL 索引、最大响应时间、测试 Git 提交、traceId、`lab.system.operator-user-id→禁登录账号→history.operator_id` 的验证链、Surefire 实际执行数量和上述 10 个 IT 的 XML 报告名；不把仅在线程池执行当成并发证据，必须附数据库最终断言。

- [ ] 8.9 在 SRS 追踪附录把 AT-05 标为通过，把 AT-04 与 AT-06 标为“已通过本计划覆盖部分”；FR-RES-005 以领域命令测试为证据，Quartz 触发证据在计划 05 补齐。

- [ ] 8.10 检查空白、敏感信息和工作区范围，提交并标记 M3。

    git diff --check
    rg -n "(password\s*[:=]\s*[^$<{]|BEGIN (RSA|OPENSSH) PRIVATE KEY|Bearer eyJ)" . -g '!target/**' -g '!ruoyi-ui/node_modules/**'
    git status --short
    git add ruoyi-lab/src/test ruoyi-admin/src/test scripts/smoke-m3-reservations.ps1 docs/testing/m3-reservations-report.md docs/requirements/lab-management-srs.md
    git commit -m "test: prove reservation concurrency milestone"
    git tag milestone/m3-reservations

## FR/AT 映射

| 需求 | 实现任务 | 自动化或证据 | M3 判定 |
|---|---|---|---|
| FR-QUA-004 | Task 3、4、8 | ReservationApplicationTest、ReservationDecisionTest、AT-06 部分验收 | 提交与批准完成，领用由计划 03 完成 |
| FR-RES-001 | Task 1、3、7 | ReservationIdempotencyIT、申请页面测试 | 完成 |
| FR-RES-002 | Task 2、3、8 | LabReservationMapperIT、ReservationConcurrencyIT | 完成 |
| FR-RES-003 | Task 4、7、8 | ReservationDecisionTest、批准驳回页面测试 | 完成 |
| FR-RES-004 | Task 4、8 | 取消状态与释放区间验收 | 完成 |
| FR-RES-005 | Task 6、8 | ReservationLifecycleServiceIT | 领域命令完成，调度触发由计划 05 完成 |
| FR-RES-006 | Task 5、7、8 | ReservationQueryIT、ReservationSecurityIT | 完成 |
| AT-04 | Task 4、8 | 申请→批准验收与状态历史 | 本计划范围通过 |
| AT-05 | Task 2、3、8 | 三轮 20 线程 MySQL 集成测试 | 完整通过 |
| AT-06 | Task 3、4、8 | 资格、故障设备、停用实验室及 Hazard Guard 回滚测试 | 本计划范围通过 |

## M3 回归命令

    powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify.ps1
    powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\assert-surefire-tests.ps1 -Module ruoyi-admin -RequiredTests 'LabReservationSchemaMigrationIT,LabReservationMapperIT,ReservationIdempotencyIT,ReservationObjectPermissionIT,ReservationQueryIT,LabSystemOperatorProviderIT,ReservationLifecycleServiceIT,ReservationConcurrencyIT,ReservationAcceptanceIT,ReservationSecurityIT'
    1..3 | ForEach-Object { powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\run-lab-tests.ps1 -DatabaseName lab_test_m3_concurrency -Tests 'ReservationConcurrencyIT'; if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE } }
    powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-migrations.ps1
    corepack yarn --cwd .\ruoyi-ui test
    corepack yarn --cwd .\ruoyi-ui build:prod
    powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\smoke-m3-reservations.ps1
    git status --short

预期：所有命令退出码为 0；10 个必跑 IT 报告存在、执行数大于 0 且无失败、错误或跳过；Flyway schema 版本为 V3_1；每轮并发测试恰好一条有效预约且无死锁；AT-05 完整证据、AT-04 与 AT-06 的 M3 子集证据齐全；工作区干净。
