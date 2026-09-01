# Scheduled Jobs, Notifications, and Dashboard Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在预约、维修和安全闭环已经可用的基础上，交付事务提交后站内通知、基于状态历史的失败补偿、六类幂等定时任务、按角色工作台和受数据范围约束的数据库聚合统计。

**Architecture:** 核心业务事务只写业务对象和 `lab_status_history`，并发布Spring事务事件；状态通知以唯一 `history_id` 标识一次真实迁移，超期提醒以业务表中的 `overdue_flag`、`overdue_set_at` 和单调 `overdue_event_version` 标识一次超期事实。监听器仅在事务成功提交后，以独立事务创建或更新 `lab_notification`。通知瞬时失败时尽力保存FAILED记录，补偿任务同时重试FAILED记录，并使用相同事件标识从状态历史及超期事实对账补建缺失通知；`ruoyi-quartz`只能调用 `ruoyi-lab` Service，工作台统计在MySQL端聚合并复用 `LabDataScopeService`。

**Tech Stack:** Java 17、Spring Boot 3.5.16、Spring Transaction Events、MyBatis-Plus 3.5.17、MySQL 8、RuoYi Quartz、Redis 7、JUnit 5、Mockito、MockMvc、Vue 3.5.26、Pinia 3.0.4、Element Plus 2.13.1、ECharts、Vitest 3.2.4。

---

## 需求、测试环境与退出门禁

覆盖FR-RES-005/007、FR-INS-002/005、FR-HAZ-006、FR-COM-001/002、FR-RPT-001/002，闭合AT-09、AT-11、AT-12。

所有使用 `@SpringBootTest`、MockMvc、JdbcTemplate、Flyway或真实DataSource的测试必须放在 `ruoyi-admin/src/test/java/com/ruoyi/integration/`。它们使用 `ruoyi-admin/src/test/resources/application-test.yml` 和 `LAB_TEST_DB_*` 指向独立MySQL 8测试库，不引入H2或Testcontainers。除 `LabM6MigrationIT` 使用任务专属随机数据库夹具外，所有定向数据库IT都必须通过计划00的包装器执行，例如 `scripts/run-lab-tests.ps1 -DatabaseName 'lab_test_m6_notification' -Tests 'LabNotificationMigrationIT'`；数据库名必须匹配安全前缀，禁止直接用Maven继承调用者遗留的数据库URL。`ruoyi-lab/src/test`只保存不启动Spring上下文的纯单元测试。

阶段结束时必须满足：

- 核心事务回滚不生成通知，核心事务提交后通知失败不回滚核心状态；
- 业务表仍为批准设计中的15张，不创建通知发件箱或额外业务表；
- 同一状态历史或同一超期事件版本、同一接收人最多一条通知；同一对象后续产生的新历史或新超期版本不得被旧 `dedupe_key` 吞掉，FAILED重试及事实对账均可安全重跑；
- 预约过期、爽约、巡检生成、巡检超期、整改超期和通知补偿每分钟运行且禁止并发；
- `ruoyi-quartz`不依赖lab Mapper、实体或MyBatis API；
- 四类业务角色只看到本人或授权实验室范围内的消息、待办和统计；
- AT-09、AT-11、AT-12的自动化及M5烟雾证据全部通过。

## 跨计划固定接口

```java
package com.ruoyi.lab.service;

import java.time.LocalDateTime;

public interface ReservationLifecycleService {
    int expirePending(LocalDateTime now, int batchSize);
    int markNoShow(LocalDateTime now, int batchSize);
}

public interface InspectionScheduleService {
    int generateDueTasks(LocalDateTime now, int batchSize);
}

public interface InspectionLifecycleService {
    int markOverdue(LocalDateTime now, int batchSize);
}

public interface HazardLifecycleService {
    int markOverdue(LocalDateTime now, int batchSize);
}
```

统计必须复用计划01的 `LabDataScopeService.resolveCurrentScope()`，返回 `LabDataScope(long userId, boolean allLaboratories, Set<Long> laboratoryIds)`；不得重新实现范围规则。

## 强制TDD执行规则

每个Task严格按以下顺序执行，不能在首次失败证据之前编写实现：

- [ ] 先创建该Task列出的测试或脚本断言；
- [ ] 运行该Task给出的定向命令并保存预期失败原因；
- [ ] 只编写让当前失败转绿的最小实现；
- [ ] 重跑定向测试及受影响回归，全部通过后检查差异；
- [ ] 只提交该Task列出的文件。若首次测试意外通过，必须加强断言直至能证明缺失行为。

## Task 1: 建立唯一的通知表和投递状态

**Files:**

- Create: `ruoyi-admin/src/main/resources/db/migration/V6_0__lab_notification.sql`
- Create: `ruoyi-lab/src/main/java/com/ruoyi/lab/domain/LabNotification.java`
- Create: `ruoyi-lab/src/main/java/com/ruoyi/lab/domain/NotificationDeliveryStatus.java`
- Create: `ruoyi-lab/src/main/java/com/ruoyi/lab/mapper/LabNotificationMapper.java`
- Create: `ruoyi-lab/src/main/resources/mapper/lab/LabNotificationMapper.xml`
- Create: `scripts/verify-v6-0-migration.ps1`
- Test: `ruoyi-admin/src/test/java/com/ruoyi/integration/notification/LabNotificationMigrationIT.java`

- [ ] **Step 1.1: 写失败的迁移测试**

```java
@SpringBootTest
@ActiveProfiles("test")
class LabNotificationMigrationIT {
    @Autowired JdbcTemplate jdbcTemplate;

    @Test
    void createsOneNotificationTableWithDeliveryColumns() {
        Integer migrationCount = jdbcTemplate.queryForObject(
            "select count(*) from flyway_schema_history where version='6.0' and success=1",
            Integer.class);
        Integer count = jdbcTemplate.queryForObject(
            "select count(*) from information_schema.tables where table_schema=database() and table_name='lab_notification'",
            Integer.class);
        List<String> columns = jdbcTemplate.queryForList(
            "select column_name from information_schema.columns where table_schema=database() and table_name='lab_notification'",
            String.class);
        assertThat(migrationCount).isEqualTo(1);
        assertThat(count).isEqualTo(1);
        assertThat(columns).contains("dedupe_key", "delivery_status", "attempt_count", "next_retry_at", "last_error_code");
        assertThat(columns).doesNotContain("outbox_id");
        assertThat(columnsOf("lab_inspection_task"))
            .contains("overdue_set_at", "overdue_event_version");
        assertThat(columnsOf("lab_hazard"))
            .contains("overdue_set_at", "overdue_event_version");
    }

    private List<String> columnsOf(String tableName) {
        return jdbcTemplate.queryForList(
            "select column_name from information_schema.columns "
                + "where table_schema=database() and table_name=? order by ordinal_position",
            String.class,
            tableName);
    }
}
```

- [ ] **Step 1.2: 运行并确认失败**

`verify-v6-0-migration.ps1`先调用计划00的`reset-test-db.ps1`重建精确数据库`lab_test_m6_notification`，并在调用后立即检查 `$LASTEXITCODE`；任何非零结果原样退出，不能继续连接旧库。随后通过计划00的 `run-lab-tests.ps1` 运行定向测试；包装器负责再次校验安全库名、设置隔离的 `LAB_TEST_DB_URL` 与 `LAB_TEST_FLYWAY_ENABLED=true`。`application-test.yml`继续以`${LAB_TEST_FLYWAY_ENABLED:false}`为默认值，禁止其他测试意外迁移共享库。

```powershell
$ErrorActionPreference = 'Stop'
foreach ($name in 'LAB_TEST_DB_USERNAME', 'LAB_TEST_DB_PASSWORD') {
  if ([string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($name))) {
    throw "Missing required environment variable: $name"
  }
}
& powershell -NoProfile -ExecutionPolicy Bypass -File "$PSScriptRoot\reset-test-db.ps1" -DatabaseName 'lab_test_m6_notification'
if ($LASTEXITCODE -ne 0) {
  exit $LASTEXITCODE
}
& powershell -NoProfile -ExecutionPolicy Bypass -File "$PSScriptRoot\run-lab-tests.ps1" `
  -DatabaseName 'lab_test_m6_notification' `
  -Tests 'LabNotificationMigrationIT'
exit $LASTEXITCODE
```

    powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-v6-0-migration.ps1

预期：FAIL；Flyway先成功执行到V5_1，测试明确断言当前版本不是V6_0且`lab_notification`尚不存在，不得因数据库连接或测试未发现而失败。

- [ ] **Step 1.3: 创建完整V6_0迁移**

```sql
CREATE TABLE lab_notification (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '通知主键',
    dedupe_key VARCHAR(128) NOT NULL COMMENT '业务事件去重键',
    receiver_id BIGINT NOT NULL COMMENT '接收用户',
    notification_type VARCHAR(32) NOT NULL COMMENT '通知类型',
    title VARCHAR(128) NOT NULL COMMENT '标题',
    content VARCHAR(500) NOT NULL COMMENT '安全中文内容',
    business_type VARCHAR(32) NOT NULL COMMENT '业务对象类型',
    business_id BIGINT NOT NULL COMMENT '业务对象主键',
    delivery_status VARCHAR(16) NOT NULL COMMENT 'SENT或FAILED',
    attempt_count INT NOT NULL DEFAULT 1 COMMENT '投递次数',
    next_retry_at DATETIME(3) NULL COMMENT '下次补偿时间',
    last_error_code VARCHAR(64) NULL COMMENT '安全错误码',
    read_at DATETIME(3) NULL COMMENT '阅读时间',
    create_by VARCHAR(64) NOT NULL DEFAULT 'system',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_by VARCHAR(64) NOT NULL DEFAULT '',
    update_time DATETIME(3) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_lab_notification_dedupe (dedupe_key),
    KEY idx_lab_notification_receiver_read (receiver_id, read_at, create_time),
    KEY idx_lab_notification_retry (delivery_status, next_retry_at, id),
    KEY idx_lab_notification_business (business_type, business_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='实验室站内通知';

ALTER TABLE lab_inspection_task
    ADD COLUMN overdue_set_at DATETIME(3) NULL COMMENT '本轮超期首次标记时间' AFTER overdue_flag,
    ADD COLUMN overdue_event_version BIGINT NOT NULL DEFAULT 0 COMMENT '超期事件单调版本' AFTER overdue_set_at;

ALTER TABLE lab_hazard
    ADD COLUMN overdue_set_at DATETIME(3) NULL COMMENT '本轮超期首次标记时间' AFTER overdue_flag,
    ADD COLUMN overdue_event_version BIGINT NOT NULL DEFAULT 0 COMMENT '超期事件单调版本' AFTER overdue_set_at;

UPDATE lab_inspection_task
SET overdue_set_at = COALESCE(update_time, deadline_at), overdue_event_version = 1
WHERE overdue_flag = '1' AND overdue_event_version = 0;

UPDATE lab_hazard
SET overdue_set_at = COALESCE(update_time, deadline), overdue_event_version = 1
WHERE overdue_flag = '1' AND overdue_event_version = 0;
```

V5_1升级时可能已经存在 `overdue_flag='1'` 的事实，V6_0必须用原 `update_time`，为空时分别回退到 `deadline_at` 或 `deadline`，并把事件版本初始化为1；禁止留下flag为1但事件标识为空或0的不可补偿记录。

- [ ] **Step 1.4: 创建实体、枚举和Mapper**

```java
package com.ruoyi.lab.domain;

public enum NotificationDeliveryStatus {
    SENT,
    FAILED
}
```

Mapper XML提供按dedupeKey查询、插入SENT、插入或更新FAILED、选择到期FAILED ID、按本人分页和本人条件标记已读。通知表不复制 `history_id` 或超期字段，来源事实仍由状态历史及业务表维护；`dedupe_key` 必须携带来源事实的不可变标识。所有参数使用 `#{}`。

- [ ] **Step 1.5: 运行迁移验证并提交**

    powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-migrations.ps1
    powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-v6-0-migration.ps1
    git add ruoyi-admin/src/main/resources/db/migration/V6_0__lab_notification.sql ruoyi-admin/src/test/java/com/ruoyi/integration/notification/LabNotificationMigrationIT.java ruoyi-lab scripts/verify-v6-0-migration.ps1
    git commit -m "feat: add notification delivery persistence"

预期：测试PASS；数据库不存在通知发件箱表。

## Task 2: 事务提交后创建通知

**Files:**

- Create: `ruoyi-lab/src/main/java/com/ruoyi/lab/dto/NotificationCommand.java`
- Create: `ruoyi-lab/src/main/java/com/ruoyi/lab/event/NotificationDedupeKey.java`
- Create: `ruoyi-lab/src/main/java/com/ruoyi/lab/event/LabNotificationRequested.java`
- Create: `ruoyi-lab/src/main/java/com/ruoyi/lab/event/LabNotificationAfterCommitListener.java`
- Create: `ruoyi-lab/src/main/java/com/ruoyi/lab/service/LabNotificationDeliveryService.java`
- Create: `ruoyi-lab/src/main/java/com/ruoyi/lab/service/LabNotificationPersistenceService.java`
- Create: `ruoyi-lab/src/main/java/com/ruoyi/lab/service/impl/LabNotificationDeliveryServiceImpl.java`
- Create: `ruoyi-lab/src/main/java/com/ruoyi/lab/service/impl/LabNotificationPersistenceServiceImpl.java`
- Modify: `ruoyi-lab/src/main/java/com/ruoyi/lab/service/impl/ReservationCommandServiceImpl.java`
- Modify: `ruoyi-lab/src/main/java/com/ruoyi/lab/service/impl/ReservationLifecycleServiceImpl.java`
- Modify: `ruoyi-lab/src/main/java/com/ruoyi/lab/service/impl/RepairOrderServiceImpl.java`
- Modify: `ruoyi-lab/src/main/java/com/ruoyi/lab/service/impl/InspectionTaskServiceImpl.java`
- Modify: `ruoyi-lab/src/main/java/com/ruoyi/lab/service/impl/InspectionScheduleServiceImpl.java`
- Modify: `ruoyi-lab/src/main/java/com/ruoyi/lab/service/impl/InspectionLifecycleServiceImpl.java`
- Modify: `ruoyi-lab/src/main/java/com/ruoyi/lab/service/impl/HazardServiceImpl.java`
- Modify: `ruoyi-lab/src/main/java/com/ruoyi/lab/service/impl/HazardLifecycleServiceImpl.java`
- Modify: `ruoyi-lab/src/main/java/com/ruoyi/lab/mapper/LabInspectionTaskMapper.java`
- Modify: `ruoyi-lab/src/main/resources/mapper/lab/LabInspectionTaskMapper.xml`
- Modify: `ruoyi-lab/src/main/java/com/ruoyi/lab/mapper/LabHazardMapper.java`
- Modify: `ruoyi-lab/src/main/resources/mapper/lab/LabHazardMapper.xml`
- Create: `ruoyi-lab/src/test/java/com/ruoyi/lab/event/NotificationDedupeKeyTest.java`
- Test: `ruoyi-admin/src/test/java/com/ruoyi/integration/notification/LabNotificationAfterCommitIT.java`

- [ ] **Step 2.1: 写提交与回滚测试**

```java
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class LabNotificationAfterCommitIT {
    @Autowired ApplicationEventPublisher publisher;
    @MockitoBean LabNotificationDeliveryService deliveryService;

    @Test
    void deliversOnlyAfterCommit() {
        NotificationCommand command = fixture.command(
            NotificationDedupeKey.forHistory(801L, "RESERVATION_APPROVED", 18L));
        publisher.publishEvent(new LabNotificationRequested(command));
        verify(deliveryService, never()).deliverSafely(command);
        TestTransaction.flagForCommit();
        TestTransaction.end();
        verify(deliveryService).deliverSafely(command);
    }

    @Test
    void doesNotDeliverAfterRollback() {
        NotificationCommand command = fixture.command(
            NotificationDedupeKey.forHistory(802L, "RESERVATION_REJECTED", 18L));
        publisher.publishEvent(new LabNotificationRequested(command));
        TestTransaction.flagForRollback();
        TestTransaction.end();
        verify(deliveryService, never()).deliverSafely(command);
    }
}
```

- [ ] **Step 2.2: 运行并确认失败**

    powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\run-lab-tests.ps1 -DatabaseName 'lab_test_m6_notification_after_commit' -Tests 'NotificationDedupeKeyTest,LabNotificationAfterCommitIT'

预期：FAIL，事件和监听器尚不存在。

- [ ] **Step 2.3: 定义命令、事件和监听器**

```java
package com.ruoyi.lab.dto;

public record NotificationCommand(
    String dedupeKey, Long receiverId, String notificationType,
    String title, String content, String businessType, Long businessId
) { }
```

所有生产者和补偿器必须复用同一键工厂，禁止在各Service中手工拼接：

```java
package com.ruoyi.lab.event;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

public final class NotificationDedupeKey {
    private static final Pattern TOKEN = Pattern.compile("[A-Z0-9_]{1,32}");

    private NotificationDedupeKey() { }

    public static String forHistory(long historyId, String notificationType, long receiverId) {
        return "history:" + positive(historyId, "historyId") + ":"
            + token(notificationType, "notificationType") + ":"
            + positive(receiverId, "receiverId");
    }

    public static String forOverdue(
        String businessType, long objectId, long overdueEventVersion, long receiverId
    ) {
        return "overdue:" + token(businessType, "businessType").toLowerCase(Locale.ROOT) + ":"
            + positive(objectId, "objectId") + ":"
            + positive(overdueEventVersion, "overdueEventVersion") + ":"
            + positive(receiverId, "receiverId");
    }

    private static String token(String value, String field) {
        String normalized = Objects.requireNonNull(value, field).trim().toUpperCase(Locale.ROOT);
        if (!TOKEN.matcher(normalized).matches()) {
            throw new IllegalArgumentException(field + " must match " + TOKEN.pattern());
        }
        return normalized;
    }

    private static long positive(long value, String field) {
        if (value <= 0) {
            throw new IllegalArgumentException(field + " must be positive");
        }
        return value;
    }
}
```

`NotificationDedupeKeyTest`必须使用下列断言证明同一 `historyId/eventVersion + receiverId` 得到相同键，不同 `historyId` 即使对象和目标状态相同也得到不同键，不同 `overdueEventVersion` 得到不同键，并拒绝0、负数和非法类型Token：

```java
package com.ruoyi.lab.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import org.junit.jupiter.api.Test;

class NotificationDedupeKeyTest {
    @Test
    void separatesHistoryRowsAndOverdueRounds() {
        assertThat(NotificationDedupeKey.forHistory(801L, "RESERVATION_APPROVED", 18L))
            .isEqualTo(NotificationDedupeKey.forHistory(801L, "RESERVATION_APPROVED", 18L))
            .isNotEqualTo(NotificationDedupeKey.forHistory(802L, "RESERVATION_APPROVED", 18L));
        assertThat(NotificationDedupeKey.forOverdue("INSPECTION_TASK", 91L, 1L, 18L))
            .isNotEqualTo(NotificationDedupeKey.forOverdue("INSPECTION_TASK", 91L, 2L, 18L));
    }

    @Test
    void rejectsNonPositiveIdsAndUnsafeTokens() {
        assertThatIllegalArgumentException()
            .isThrownBy(() -> NotificationDedupeKey.forHistory(0L, "RESERVATION_APPROVED", 18L));
        assertThatIllegalArgumentException()
            .isThrownBy(() -> NotificationDedupeKey.forOverdue("../HAZARD", 91L, 1L, 18L));
    }
}
```

```java
package com.ruoyi.lab.event;

import com.ruoyi.lab.dto.NotificationCommand;
public record LabNotificationRequested(NotificationCommand command) { }
```

```java
@Component
@RequiredArgsConstructor
public class LabNotificationAfterCommitListener {
    private final LabNotificationDeliveryService deliveryService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(LabNotificationRequested event) {
        deliveryService.deliverSafely(event.command());
    }
}
```

- [ ] **Step 2.4: 实现独立事务投递**

固定采用独立Bean避免Spring事务自调用失效：`LabNotificationDeliveryServiceImpl.deliverSafely`调用注入的 `LabNotificationPersistenceService`；其 `insertSent` 与 `recordFailed` 分别标注 `@Transactional(propagation = Propagation.REQUIRES_NEW)`。`insertSent`唯一键冲突视为已投递；外层捕获瞬时异常后调用`recordFailed`插入或更新FAILED，`next_retry_at`为当前时间加1分钟。若FAILED记录也因数据库不可用而无法写入，只记录dedupeKey、安全错误码和traceId，由状态历史对账补建。禁止在同一实现类中自调用REQUIRES_NEW方法。

- [ ] **Step 2.5: 在核心服务发布稳定事件**

事件键固定为：

```text
history:{historyId}:{notificationType}:{receiverId}
overdue:inspection_task:{taskId}:{overdueEventVersion}:{receiverId}
overdue:hazard:{hazardId}:{overdueEventVersion}:{receiverId}
```

状态命令顺序为“更新业务状态 → 写 `lab_status_history` 并取得数据库生成的正数 `history.id` → 用该ID构造键并发布事件 → 返回”。巡检生成沿用计划04同事务写入的 null → PENDING 历史后发布分派事件；写历史或取得ID失败必须回滚，禁止退回对象ID加状态的旧键。相同对象以后再次发生相同目标状态时会产生新的 `history.id`，因此必须得到新的通知。

巡检与隐患超期使用行锁或等价条件更新，把 `overdue_flag` 从0改为1的同一条SQL同时写 `overdue_set_at=#{now}`、执行 `overdue_event_version=overdue_event_version+1` 并推进通用 `version`；更新成功后在同一事务读取该行的正数 `overdue_event_version`，用它发布 `INSPECTION_OVERDUE` 或 `HAZARD_OVERDUE`。主状态不变且不伪造状态迁移；只有实际0→1的行发布一次，重复调用影响0行且不重复发布。若未来业务把flag复位后再次超期，单调版本必须递增并产生新键。Controller不得创建通知。

- [ ] **Step 2.6: 回归并提交**

    powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\run-lab-tests.ps1 -DatabaseName 'lab_test_m6_notification_events' -Tests 'NotificationDedupeKeyTest,LabNotificationAfterCommitIT,ReservationApplicationTest,ReservationDecisionTest,RepairWorkflowServiceTest,InspectionExecutionServiceTest,HazardServiceTest'
    git add ruoyi-lab ruoyi-admin/src/test
    git commit -m "feat: create notifications after transaction commit"

预期：PASS；回滚场景不调用投递服务。

## Task 3: 从FAILED记录和状态历史执行幂等补偿

**Files:**

- Create: `ruoyi-lab/src/main/java/com/ruoyi/lab/service/LabNotificationCompensationService.java`
- Create: `ruoyi-lab/src/main/java/com/ruoyi/lab/service/NotificationExpectationResolver.java`
- Create: `ruoyi-lab/src/main/java/com/ruoyi/lab/service/impl/LabNotificationCompensationServiceImpl.java`
- Create: `ruoyi-lab/src/main/java/com/ruoyi/lab/service/impl/NotificationExpectationResolverImpl.java`
- Modify: `ruoyi-lab/src/main/java/com/ruoyi/lab/mapper/LabNotificationMapper.java`
- Modify: `ruoyi-lab/src/main/java/com/ruoyi/lab/mapper/LabStatusHistoryMapper.java`
- Modify: `ruoyi-lab/src/main/java/com/ruoyi/lab/mapper/LabInspectionTaskMapper.java`
- Modify: `ruoyi-lab/src/main/java/com/ruoyi/lab/mapper/LabHazardMapper.java`
- Modify: `ruoyi-lab/src/main/resources/mapper/lab/LabNotificationMapper.xml`
- Create: `ruoyi-lab/src/main/resources/mapper/lab/LabStatusHistoryMapper.xml`
- Modify: `ruoyi-lab/src/main/resources/mapper/lab/LabInspectionTaskMapper.xml`
- Modify: `ruoyi-lab/src/main/resources/mapper/lab/LabHazardMapper.xml`
- Test: `ruoyi-admin/src/test/java/com/ruoyi/integration/notification/LabNotificationCompensationIT.java`

- [ ] **Step 3.1: 写FAILED重试和缺失对账测试**

准备一条FAILED通知，并为同一业务对象、同一目标状态写入两条不同 `history_id` 的可通知状态历史；第一条已有SENT通知，第二条缺失。再准备一条 `overdue_event_version=1` 已通知的巡检事实，将其flag按测试夹具模拟复位后再次标记为超期并得到版本2。连续补偿两次，断言两个history键和两个overdue版本键各自独立、每个键最多一条SENT，版本1通知的readAt不变，版本2不会被版本1吞掉。

- [ ] **Step 3.2: 运行并确认失败**

    powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\run-lab-tests.ps1 -DatabaseName 'lab_test_m6_notification_compensation' -Tests 'LabNotificationCompensationIT'

预期：FAIL，补偿服务尚不存在。

- [ ] **Step 3.3: 实现FAILED重试SQL**

```xml
<select id="selectRetryableIds" resultType="long">
  SELECT id FROM lab_notification
  WHERE delivery_status='FAILED' AND attempt_count &lt; 10
    AND next_retry_at &lt;= #{now}
  ORDER BY next_retry_at, id LIMIT #{batchSize}
</select>

<update id="markSent">
  UPDATE lab_notification
  SET delivery_status='SENT', attempt_count=attempt_count+1,
      next_retry_at=NULL, last_error_code=NULL, update_time=#{now}
  WHERE id=#{id} AND delivery_status='FAILED'
</update>
```

- [ ] **Step 3.4: 实现状态历史对账**

按 `history_id` 递增扫描对象类型为RESERVATION/REPAIR_ORDER/INSPECTION_TASK/HAZARD的全部可通知历史，不设置会遗漏长期故障的时间截断；每个候选必须携带真实 `history_id`，并通过 `NotificationDedupeKey.forHistory` 构造键。另分别扫描 `lab_inspection_task` 与 `lab_hazard` 中 `overdue_flag='1' AND overdue_set_at IS NOT NULL AND overdue_event_version>0` 的超期事实，候选必须携带持久化的 `overdue_event_version`，并通过 `NotificationDedupeKey.forOverdue` 构造键，禁止使用扫描时间、当前通用version或仅用objectId构造键。

Mapper结合业务对象归属、目标状态或超期事实计算候选接收人，并用 `NOT EXISTS` 排除已存在的精确dedupeKey；Resolver再次校验对象、来源事实、接收人和键，缺失时调用同一独立事务投递方法。巡检超期通知assignee，隐患超期通知owner及数据范围内安全员；每类每批最多 `batchSize` 条。接口固定为：

```java
package com.ruoyi.lab.service;

import java.time.LocalDateTime;

public interface LabNotificationCompensationService {
    int retryFailed(LocalDateTime now, int batchSize);
    int reconcileStatusHistory(LocalDateTime now, int batchSize);
}
```

```java
package com.ruoyi.lab.service;

import com.ruoyi.lab.dto.NotificationCommand;
import java.util.List;

public interface NotificationExpectationResolver {
    List<NotificationCommand> resolveHistory(long historyId);
    List<NotificationCommand> resolveInspectionOverdue(long taskId, long overdueEventVersion);
    List<NotificationCommand> resolveHazardOverdue(long hazardId, long overdueEventVersion);
}
```

- [ ] **Step 3.5: 验证并提交**

    powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\run-lab-tests.ps1 -DatabaseName 'lab_test_m6_notification_compensation' -Tests 'LabNotificationCompensationIT'
    git add ruoyi-lab ruoyi-admin/src/test
    git commit -m "feat: reconcile failed notifications from status history"

预期：PASS；运行两次后每个来源事件与接收人的dedupeKey仍只有一条，不同historyId或不同overdueEventVersion均保留独立通知。

## Task 4: 一次性迁移V6菜单权限并注册六类Quartz任务

**Files:**

- Create: `ruoyi-admin/src/main/resources/db/migration/V6_1__lab_jobs_messages_dashboard_seed.sql`
- Create: `ruoyi-quartz/src/main/java/com/ruoyi/quartz/task/LabLifecycleJob.java`
- Create: `ruoyi-lab/src/main/java/com/ruoyi/lab/config/LabJobProperties.java`
- Modify: `ruoyi-admin/src/main/resources/application.yml`
- Create: `ruoyi-admin/src/test/java/com/ruoyi/integration/support/LocalMySqlTestDatabase.java`
- Create: `ruoyi-lab/src/test/java/com/ruoyi/lab/config/LabJobPropertiesTest.java`
- Test: `ruoyi-quartz/src/test/java/com/ruoyi/quartz/task/LabLifecycleJobTest.java`
- Test: `ruoyi-admin/src/test/java/com/ruoyi/integration/lab/migration/LabM6MigrationIT.java`
- Create: `scripts/verify-m6-migration.ps1`
- Modify: `ruoyi-quartz/pom.xml`
- Modify: `ruoyi-admin/src/test/java/com/ruoyi/architecture/LabLayerArchitectureTest.java`

- [ ] **Step 4.1: 先写固定时钟委托测试和迁移红灯测试**

`LabJobPropertiesTest`使用Jakarta Validator直接校验默认值200、边界1和1000合法、0与1001各产生 `batchSize` 约束错误。`LabLifecycleJobTest`显式传入批量200的 `LabJobProperties`，断言六个方法传入 `LocalDateTime.of(2026,9,1,9,0)` 与批量200，通知补偿依次调用 `retryFailed` 和 `reconcileStatusHistory`；测试还反射断言Job没有 `@Value` 字段。

`LabM6MigrationIT`是放在ruoyi-admin集成测试目录中的纯JUnit迁移测试，不使用`@SpringBootTest`，避免Spring在断言V5_1之前自动迁移到最新版本。每个方法通过`LocalMySqlTestDatabase.create(suffix)`取得自己创建的唯一数据库，再用`LAB_TEST_DB_USERNAME/PASSWORD`显式构造Flyway；因此定向测试和后续`clean verify`都不依赖前一个方法留下的版本状态。

```java
@Test
void emptyDatabaseMigratesThroughV6_1() throws Exception {
    try (LocalMySqlTestDatabase database = LocalMySqlTestDatabase.create("m6_empty")) {
        Flyway flyway = flyway(database.jdbcUrl(), MigrationVersion.LATEST);
        MigrateResult result = flyway.migrate();
        assertThat(result.success).isTrue();
        assertThat(currentVersion(database)).isEqualTo("6.1");
        assertM6Seeds(database);
    }
}

@Test
void v5_1DatabaseUpgradesExactlyOnceToV6_1() throws Exception {
    try (LocalMySqlTestDatabase database = LocalMySqlTestDatabase.create("m6_upgrade")) {
        Flyway toV5 = flyway(database.jdbcUrl(), MigrationVersion.fromVersion("5.1"));
        assertThat(toV5.migrate().success).isTrue();
        assertThat(currentVersion(database)).isEqualTo("5.1");
        assertThat(tableExists(database, "lab_notification")).isFalse();
        seedLegacyOverdueFacts(database);

        Flyway latest = flyway(database.jdbcUrl(), MigrationVersion.LATEST);
        assertThat(latest.migrate().success).isTrue();
        assertThat(currentVersion(database)).isEqualTo("6.1");
        assertThat(latest.migrate().migrationsExecuted).isZero();
        assertM6Seeds(database);
        assertLegacyOverdueFactsBackfilled(database);
    }
}
```

`seedLegacyOverdueFacts`在V5_1数据库各写一条合法的已超期巡检任务和隐患，设置flag=1、固定update_time并满足全部外键；`assertLegacyOverdueFactsBackfilled`断言升级后两行的 `overdue_event_version=1` 且 `overdue_set_at` 等于原update_time。`LocalMySqlTestDatabase`只允许`LAB_TEST_ADMIN_HOST`为`localhost`或`127.0.0.1`，用`lab_test_<suffix>_<12位随机小写十六进制>`生成名称并再次匹配`^lab_test_[a-z0-9_]+$`；通过JDBC管理连接创建数据库，应用连接仍使用`LAB_TEST_DB_USERNAME/PASSWORD`。`close()`只删除本实例成功创建且名称完全匹配的数据库；创建失败不执行DROP，日志不输出密码或JDBC凭据。测试明确关闭并行执行，避免管理操作交叉。

`assertM6Seeds`必须断言：V6_0通知表及两个超期事件字段存在；`sys_config` 中不存在 `lab.jobs.batch-size`；六个job的invoke target精确匹配；`lab:notification:list`、`lab:notification:read`、`lab:dashboard:view`各出现一次；三个菜单权限均已关联五个固定role_key。批量大小属于应用配置，不由Flyway或数据库种子断言。

- [ ] **Step 4.2: 用独立临时MySQL数据库确认红灯来自V6_1不存在**

```powershell
$ErrorActionPreference = 'Stop'
foreach ($name in 'LAB_TEST_ADMIN_HOST', 'LAB_TEST_ADMIN_PORT',
  'LAB_TEST_ADMIN_USERNAME', 'LAB_TEST_ADMIN_PASSWORD',
  'LAB_TEST_DB_USERNAME', 'LAB_TEST_DB_PASSWORD') {
  if ([string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($name))) {
    throw "Missing required environment variable: $name"
  }
}
if ($env:LAB_TEST_ADMIN_HOST -notin 'localhost', '127.0.0.1') {
  throw 'LAB_TEST_ADMIN_HOST must be localhost or 127.0.0.1'
}
mvn -pl ruoyi-admin -am -DskipTests=false -Dsurefire.failIfNoSpecifiedTests=false "-Dtest=LabM6MigrationIT#v5_1DatabaseUpgradesExactlyOnceToV6_1" test
```

预期：BUILD FAILURE，原因是V6_1、六类任务或菜单权限尚不存在；不得出现测试未发现、数据库连接失败或权限不足。

- [ ] **Step 4.3: 实现只依赖Service的任务Bean**

在`ruoyi-quartz/pom.xml`增加`ruoyi-lab`编译依赖和JUnit/Mockito测试依赖。生产运行直接注入计划00中唯一的 LabTimeConfig Clock Bean；本计划不得创建第二个生产 Clock。批量配置只来自 `application.yml` 的 `LAB_JOBS_BATCH_SIZE` 环境变量，默认200；`LabJobProperties`使用类型化、启动期校验，范围固定1至1000。Quartz Job构造注入该属性Bean，禁止使用 `@Value`、`sys_config` 或静态常量。单元测试直接传入 `Clock.fixed`，不依赖机器默认时区。

```yaml
lab:
  jobs:
    batch-size: ${LAB_JOBS_BATCH_SIZE:200}
```

```java
package com.ruoyi.lab.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Component
@Validated
@ConfigurationProperties(prefix = "lab.jobs")
public class LabJobProperties {
    @Min(1)
    @Max(1000)
    private int batchSize = 200;

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }
}
```

```java
package com.ruoyi.lab.config;

import static org.assertj.core.api.Assertions.assertThat;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

class LabJobPropertiesTest {
    @Test
    void defaultsToTwoHundredAndAcceptsInclusiveBounds() {
        LabJobProperties defaults = new LabJobProperties();
        assertThat(defaults.getBatchSize()).isEqualTo(200);
        assertThat(LabJobProperties.class.isAnnotationPresent(Validated.class)).isTrue();
        assertThat(LabJobProperties.class.getAnnotation(ConfigurationProperties.class).prefix())
            .isEqualTo("lab.jobs");
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            Validator validator = factory.getValidator();
            assertThat(validator.validate(properties(1))).isEmpty();
            assertThat(validator.validate(properties(1000))).isEmpty();
        }
    }

    @Test
    void rejectsValuesOutsideOneToOneThousand() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            Validator validator = factory.getValidator();
            assertThat(validator.validate(properties(0)))
                .extracting(v -> v.getPropertyPath().toString())
                .containsExactly("batchSize");
            assertThat(validator.validate(properties(1001)))
                .extracting(v -> v.getPropertyPath().toString())
                .containsExactly("batchSize");
        }
    }

    private LabJobProperties properties(int batchSize) {
        LabJobProperties properties = new LabJobProperties();
        properties.setBatchSize(batchSize);
        return properties;
    }
}
```

```java
@Component("labLifecycleJob")
@RequiredArgsConstructor
public class LabLifecycleJob {
    private final ReservationLifecycleService reservations;
    private final InspectionScheduleService schedules;
    private final InspectionLifecycleService inspections;
    private final HazardLifecycleService hazards;
    private final LabNotificationCompensationService notifications;
    private final LabJobProperties jobProperties;
    private final Clock clock;

    public void expirePendingReservations() { reservations.expirePending(now(), batchSize()); }
    public void markNoShowReservations() { reservations.markNoShow(now(), batchSize()); }
    public void generateInspectionTasks() { schedules.generateDueTasks(now(), batchSize()); }
    public void markInspectionOverdue() { inspections.markOverdue(now(), batchSize()); }
    public void markHazardOverdue() { hazards.markOverdue(now(), batchSize()); }
    public void compensateNotifications() {
        notifications.retryFailed(now(), batchSize());
        notifications.reconcileStatusHistory(now(), batchSize());
    }
    private int batchSize() { return jobProperties.getBatchSize(); }
    private LocalDateTime now() { return LocalDateTime.now(clock); }
}
```

- [ ] **Step 4.4: 一次性创建完整V6_1迁移**

V6_1必须在本步骤首次创建并写全以下内容，后续Task禁止修改这个已执行迁移；它不得写入 `lab.jobs.batch-size` 或任何等价 `sys_config` 参数：

- 六条`LAB_SYSTEM` Quartz job，Cron均为`0 * * * * ?`、禁止并发、错过后执行一次；
- 工作台菜单与`lab:dashboard:view`；
- 消息中心菜单与`lab:notification:list`；
- 标记已读按钮与`lab:notification:read`；
- 三个菜单权限与五个固定`role_key`的角色关联。

菜单使用未占用的固定ID：

```sql
INSERT INTO sys_menu
  (menu_id, menu_name, parent_id, order_num, path, component, query_param, route_name,
   is_frame, is_cache, menu_type, visible, status, perms, icon,
   create_by, create_time, update_by, update_time, remark)
VALUES
  (4600, '实验室工作台', 2000, 6, 'dashboard', 'lab/dashboard/index', NULL, 'LabDashboard',
   1, 0, 'C', '0', '0', 'lab:dashboard:view', 'dashboard', 'admin', NOW(3), '', NULL, 'V6角色工作台'),
  (4610, '消息中心', 2000, 7, 'notifications', 'lab/notification/index', NULL, 'LabNotifications',
   1, 0, 'C', '0', '0', 'lab:notification:list', 'message', 'admin', NOW(3), '', NULL, 'V6站内消息'),
  (4611, '消息已读', 4610, 1, '#', '', NULL, '',
   1, 0, 'F', '0', '0', 'lab:notification:read', '#', 'admin', NOW(3), '', NULL, '');

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.role_id, m.menu_id
FROM sys_role r
JOIN sys_menu m ON m.menu_id IN (4600, 4610, 4611)
WHERE r.role_key IN
  ('lab_student', 'lab_manager', 'lab_safety_officer', 'lab_repair_worker', 'lab_system_admin');
```

六个job的invoke target必须逐条写入迁移：

```sql
INSERT INTO sys_job
  (job_id, job_name, job_group, invoke_target, cron_expression,
   misfire_policy, concurrent, status, create_by, create_time, update_by, update_time, remark)
VALUES
  (6000, '实验室预约过期', 'LAB_SYSTEM', 'labLifecycleJob.expirePendingReservations()',
   '0 * * * * ?', '2', '1', '0', 'admin', NOW(3), '', NULL, 'PENDING到时转EXPIRED'),
  (6001, '实验室预约爽约', 'LAB_SYSTEM', 'labLifecycleJob.markNoShowReservations()',
   '0 * * * * ?', '2', '1', '0', 'admin', NOW(3), '', NULL, 'APPROVED超宽限转NO_SHOW'),
  (6002, '实验室巡检生成', 'LAB_SYSTEM', 'labLifecycleJob.generateInspectionTasks()',
   '0 * * * * ?', '2', '1', '0', 'admin', NOW(3), '', NULL, '按受控周期生成巡检任务'),
  (6003, '实验室巡检超期', 'LAB_SYSTEM', 'labLifecycleJob.markInspectionOverdue()',
   '0 * * * * ?', '2', '1', '0', 'admin', NOW(3), '', NULL, '标记巡检超期'),
  (6004, '实验室整改超期', 'LAB_SYSTEM', 'labLifecycleJob.markHazardOverdue()',
   '0 * * * * ?', '2', '1', '0', 'admin', NOW(3), '', NULL, '标记整改超期'),
  (6005, '实验室通知补偿', 'LAB_SYSTEM', 'labLifecycleJob.compensateNotifications()',
   '0 * * * * ?', '2', '1', '0', 'admin', NOW(3), '', NULL, '重试FAILED并对账状态历史');
```

- [ ] **Step 4.5: 创建可重复的空库与V5_1升级脚本**

`verify-m6-migration.ps1`只验证本机管理环境和应用测试凭据后运行两个独立方法；数据库的创建与精确清理由上述Java测试夹具负责，脚本不得创建共享库或复用`LAB_TEST_DB_URL`。脚本顺序固定为：

```powershell
$ErrorActionPreference = 'Stop'
foreach ($name in 'LAB_TEST_ADMIN_HOST', 'LAB_TEST_ADMIN_PORT',
  'LAB_TEST_ADMIN_USERNAME', 'LAB_TEST_ADMIN_PASSWORD',
  'LAB_TEST_DB_USERNAME', 'LAB_TEST_DB_PASSWORD') {
  if ([string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($name))) {
    throw "Missing required environment variable: $name"
  }
}

if ($env:LAB_TEST_ADMIN_HOST -notin 'localhost', '127.0.0.1') {
  throw 'LAB_TEST_ADMIN_HOST must be localhost or 127.0.0.1'
}
& mvn -pl ruoyi-admin -am -DskipTests=false -Dsurefire.failIfNoSpecifiedTests=false '-Dtest=LabM6MigrationIT' test
exit $LASTEXITCODE
```

- [ ] **Step 4.6: 运行迁移、任务和架构测试**

    powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-m6-migration.ps1
    mvn -pl ruoyi-quartz,ruoyi-admin -am -DskipTests=false -Dsurefire.failIfNoSpecifiedTests=false "-Dtest=LabJobPropertiesTest,LabLifecycleJobTest,LabLayerArchitectureTest" test

预期：空库和V5_1升级均到V6_1；第二次migrate执行0条；数据库没有批量参数，应用属性默认200且拒绝范围外值；六job、三个权限及15条角色菜单关系存在；Quartz没有Mapper、实体或MyBatis依赖，也没有 `@Value` 字段。

- [ ] **Step 4.7: 一次性提交V6_1及其测试**

    git add ruoyi-admin/src/main/resources/db/migration/V6_1__lab_jobs_messages_dashboard_seed.sql ruoyi-admin/src/main/resources/application.yml ruoyi-admin/src/test/java/com/ruoyi/integration/lab/migration/LabM6MigrationIT.java ruoyi-admin/src/test/java/com/ruoyi/integration/support/LocalMySqlTestDatabase.java ruoyi-admin/src/test/java/com/ruoyi/architecture/LabLayerArchitectureTest.java ruoyi-lab/src/main/java/com/ruoyi/lab/config/LabJobProperties.java ruoyi-lab/src/test/java/com/ruoyi/lab/config/LabJobPropertiesTest.java ruoyi-quartz scripts/verify-m6-migration.ps1
    git commit -m "feat: add v6 jobs menus and permissions"

## Task 5: 交付本人消息中心

**Files:**

- Create: `ruoyi-lab/src/main/java/com/ruoyi/lab/dto/NotificationQueryDto.java`
- Create: `ruoyi-lab/src/main/java/com/ruoyi/lab/vo/LabNotificationVo.java`
- Create: `ruoyi-lab/src/main/java/com/ruoyi/lab/service/LabNotificationService.java`
- Create: `ruoyi-lab/src/main/java/com/ruoyi/lab/service/impl/LabNotificationServiceImpl.java`
- Create: `ruoyi-admin/src/main/java/com/ruoyi/web/controller/lab/LabNotificationController.java`
- Test: `ruoyi-admin/src/test/java/com/ruoyi/integration/controller/LabNotificationControllerIT.java`
- Create: `ruoyi-ui/src/api/lab/notification.js`
- Create: `ruoyi-ui/src/store/modules/labNotification.js`
- Create: `ruoyi-ui/src/views/lab/notification/index.vue`
- Test: `ruoyi-ui/tests/unit/store/labNotification.spec.js`

本Task使用Task 4已经写入V6_1的消息菜单和权限，不得再次编辑任何已执行迁移。

- [ ] **Step 5.1: 写本人、他人和FAILED隐藏测试**

本人只看到SENT通知；他人通知ID和FAILED通知ID返回404。标记已读必须同时限制id、当前receiverId、SENT和readAt为空。

- [ ] **Step 5.2: 实现固定目录Controller**

```java
@RestController
@RequestMapping("/lab/notifications")
@RequiredArgsConstructor
public class LabNotificationController extends BaseController {
    private final LabNotificationService service;
    @PreAuthorize("@ss.hasPermi('lab:notification:list')")
    @GetMapping public TableDataInfo list(NotificationQueryDto query) {
        startPage(); return getDataTable(service.listMine(query));
    }
    @PreAuthorize("@ss.hasPermi('lab:notification:list')")
    @GetMapping("/unread-count") public AjaxResult unreadCount() {
        return AjaxResult.success(Map.of("count", service.countUnread()));
    }
    @PreAuthorize("@ss.hasPermi('lab:notification:read')")
    @PutMapping("/{id}/read") public AjaxResult markRead(@PathVariable long id) {
        service.markMineRead(id); return AjaxResult.success();
    }
}
```

- [ ] **Step 5.3: 实现前端API、Pinia和页面**

```javascript
import request from '@/utils/request'
export const listNotifications = params => request({ url: '/lab/notifications', method: 'get', params })
export const getUnreadNotificationCount = () => request({ url: '/lab/notifications/unread-count', method: 'get' })
export const markNotificationRead = id => request({ url: `/lab/notifications/${id}/read`, method: 'put' })
```

页面提供未读筛选、分页、业务标签、时间、标记已读和授权详情跳转。Pinia测试断言重复标记不会使未读数为负。

- [ ] **Step 5.4: 运行并提交**

    powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\run-lab-tests.ps1 -DatabaseName 'lab_test_m6_notification_controller' -Tests 'LabNotificationControllerIT'
    corepack yarn --cwd .\ruoyi-ui test --run tests/unit/store/labNotification.spec.js
    git add ruoyi-lab ruoyi-admin ruoyi-ui
    git commit -m "feat: add personal notification center"

预期：测试PASS；越权和FAILED通知不可见。

## Task 6: 交付角色工作台和数据库聚合统计

**Files:**

- Create: `ruoyi-lab/src/main/java/com/ruoyi/lab/dto/DashboardQueryDto.java`
- Create: `ruoyi-lab/src/main/java/com/ruoyi/lab/vo/LabDashboardVo.java`
- Create: `ruoyi-lab/src/main/java/com/ruoyi/lab/vo/LabMetricVo.java`
- Create: `ruoyi-lab/src/main/java/com/ruoyi/lab/mapper/LabDashboardMapper.java`
- Create: `ruoyi-lab/src/main/resources/mapper/lab/LabDashboardMapper.xml`
- Create: `ruoyi-lab/src/main/java/com/ruoyi/lab/service/LabDashboardService.java`
- Create: `ruoyi-lab/src/main/java/com/ruoyi/lab/service/impl/LabDashboardServiceImpl.java`
- Create: `ruoyi-admin/src/main/java/com/ruoyi/web/controller/lab/LabDashboardController.java`
- Test: `ruoyi-admin/src/test/java/com/ruoyi/integration/dashboard/LabDashboardMapperIT.java`
- Test: `ruoyi-admin/src/test/java/com/ruoyi/integration/controller/LabDashboardControllerIT.java`
- Create: `ruoyi-ui/src/api/lab/dashboard.js`
- Create: `ruoyi-ui/src/views/lab/dashboard/index.vue`
- Test: `ruoyi-ui/tests/unit/views/lab/dashboard.spec.js`

本Task使用Task 4已经写入V6_1的dashboard菜单、`lab:dashboard:view`及角色关联，不得再次编辑任何已执行迁移。

- [ ] **Step 6.1: 写两部门隔离测试**

管理员A不得统计实验室B；学生只统计本人；维修人员只统计分派给本人。空范围返回全零，不能退化成全库。

- [ ] **Step 6.2: 实现绑定参数的数据库聚合**

```xml
<select id="countDeviceStates" resultType="com.ruoyi.lab.vo.LabMetricVo">
  SELECT d.status AS code, COUNT(*) AS value
  FROM lab_device d WHERE d.del_flag='0'
  <if test="!scope.allLaboratories">
    AND d.laboratory_id IN
    <foreach collection="scope.laboratoryIds" item="labId" open="(" separator="," close=")">
      #{labId}
    </foreach>
  </if>
  GROUP BY d.status ORDER BY d.status
</select>
```

注意计划01固定主键为 `id`，逻辑删除为 `del_flag`；外键字段仍为 `laboratory_id`。Service必须调用 `LabDataScopeService.resolveCurrentScope()`；当`allLaboratories=false`且`laboratoryIds`为空时，在调用Mapper前直接返回所有指标为0的VO，禁止生成`IN ()`或退化为全库查询。禁止 `${}` 拼接。

- [ ] **Step 6.3: 实现Controller和Vue工作台**

Controller固定为 `GET /lab/dashboard/summary`，权限为 `lab:dashboard:view`。Vue按角色显示学生预约资格、管理员审批维修、安全员巡检隐患、维修人员个人工单；统计只消费后端聚合结果。

- [ ] **Step 6.4: 验证并提交**

    powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\run-lab-tests.ps1 -DatabaseName 'lab_test_m6_dashboard' -Tests 'LabDashboardMapperIT,LabDashboardControllerIT'
    corepack yarn --cwd .\ruoyi-ui test --run tests/unit/views/lab/dashboard.spec.js
    corepack yarn --cwd .\ruoyi-ui build:prod
    git add ruoyi-lab ruoyi-admin ruoyi-ui
    git commit -m "feat: add scoped role dashboards and statistics"

预期：测试PASS；生产构建成功；跨部门统计为0。

## Task 7: 完成M5定时重跑与验收证据

**Files:**

- Test: `ruoyi-admin/src/test/java/com/ruoyi/integration/jobs/LabScheduledLifecycleIT.java`
- Create: `scripts/smoke-m5-jobs.ps1`
- Create: `docs/testing/m5-jobs-messages-dashboard-report.md`
- Modify: `scripts/verify.ps1`
- Modify: `docs/requirements/lab-management-srs.md`

- [ ] **Step 7.1: 写六任务运行两次的集成测试**

固定2026-09-01 09:00:00，准备到期PENDING、超宽限APPROVED、到期巡检计划、超期巡检任务、超期隐患、FAILED通知、缺失通知的状态历史以及缺失超期通知的事实行。执行六任务两次，断言EXPIRED、NO_SHOW、巡检任务、两个超期标记均各1条；状态通知键都包含真实historyId，两个超期事实都保存非空overdueSetAt和正数overdueEventVersion，预约申请人、巡检assignee、隐患owner和安全员应得的每个键也各1条，第二次不增加历史、事件或通知。测试再构造同一对象的第二条相同目标状态历史以及递增后的第二轮超期版本，断言它们产生独立通知。

- [ ] **Step 7.2: 运行集成测试**

    powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\run-lab-tests.ps1 -DatabaseName 'lab_test_m6_scheduled_lifecycle' -Tests 'LabScheduledLifecycleIT'

预期：PASS；处理不晚于到期后五分钟；第二次运行计数不变。

- [ ] **Step 7.3: 通过阶段clean verify证明所有IT实际执行**

计划00的Surefire契约必须包含`**/*Test.java`、`**/*Tests.java`和`**/*IT.java`。本Task修改`scripts/verify.ps1`：先要求并校验`LAB_TEST_ADMIN_HOST/PORT/USERNAME/PASSWORD`与`LAB_TEST_DB_USERNAME/PASSWORD`，只允许管理主机为`localhost`或`127.0.0.1`；再调用计划00的 `run-lab-tests.ps1 -DatabaseName 'lab_test_m5_verify' -CleanVerify`。包装器负责重建精确本机库、覆盖调用者遗留值、构造进程级 `LAB_TEST_DB_URL`、设置 `LAB_TEST_FLYWAY_ENABLED=true` 并运行 `mvn clean verify`，`verify.ps1` 必须检查包装器退出码。完成后读取`ruoyi-admin/target/surefire-reports/TEST-*.xml`并断言至少包含以下集成测试。这样阶段门禁不会绕开统一数据库入口，也不依赖调用者上一次留下的数据库版本。

```text
LabNotificationMigrationIT
LabNotificationAfterCommitIT
LabNotificationCompensationIT
LabM6MigrationIT
LabNotificationControllerIT
LabDashboardMapperIT
LabDashboardControllerIT
LabScheduledLifecycleIT
```

    powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify.ps1

预期：BUILD SUCCESS；上述八个IT均在Surefire XML中出现且failure、error、skipped均为0。若任一IT没有报告，即使Maven返回0也视为阶段失败。

- [ ] **Step 7.4: 创建并运行M5烟雾脚本**

脚本把数据库名固定为`lab_test_m5_verify`，按已验证的`LAB_TEST_ADMIN_HOST/PORT`自行构造并覆盖`LAB_TEST_DB_URL`，先只读确认Flyway最新版本为V6_1，再使用`LAB_TEST_DB_USERNAME/PASSWORD`和演示账号触发固定任务Bean、查询本人消息、验证他人消息404、读取四角色工作台；它不重建数据库，只消费Step 7.3的已验证结果，且只输出状态码、业务码、traceId和计数。

    powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\smoke-m5-jobs.ps1

预期：烟雾脚本退出0；阶段verify已在Step 7.3完整通过。

- [ ] **Step 7.5: 写报告、追踪并提交M5**

报告记录Git提交、V6_0/V6_1、V5_1旧超期事实回填、`LAB_JOBS_BATCH_SIZE`默认值与越界拒绝、六任务首次与重跑计数、事务回滚、FAILED重试、不同historyId与不同超期事件版本的独立通知、状态历史补建、四角色统计和命令。SRS追踪附录把本计划需求及AT-09/11/12标记为“实现及M5证据已建立”。

    git diff --check
    rg -n "notification_outbox|BEGIN (RSA|OPENSSH) PRIVATE KEY|Bearer eyJ" ruoyi-admin ruoyi-lab ruoyi-quartz ruoyi-ui scripts -g '!target/**' -g '!node_modules/**' -g '!docs/superpowers/plans/**'
    git add scripts docs ruoyi-admin ruoyi-lab ruoyi-quartz ruoyi-ui
    git commit -m "test: prove scheduled jobs messages and dashboards"
    git tag milestone/m5-integrated

预期：搜索无输出；提交后工作区干净。

## M5 完整回归命令

    powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-migrations.ps1
    powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify.ps1
    corepack yarn --cwd .\ruoyi-ui install --frozen-lockfile
    corepack yarn --cwd .\ruoyi-ui test
    corepack yarn --cwd .\ruoyi-ui build:prod
    powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\smoke-m5-jobs.ps1
    git status --short

预期：全部命令退出0；Flyway最新版本为V6_1；业务表总数仍为15；任务重跑无重复记录，新historyId或新超期事件版本不丢通知；批量配置默认200且拒绝0与1001；工作区干净。
