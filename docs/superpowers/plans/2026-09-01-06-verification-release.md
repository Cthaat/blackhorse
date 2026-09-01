# Verification and Release Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在不增加业务功能和业务迁移的前提下，闭合49项FR、15项NFR和16项AT，建立覆盖率、端到端、安全、性能、故障、安装升级、备份恢复及正式发布证据。

**Architecture:** 本计划把已经实现的系统视为发布候选，只增加测试、验证脚本、发布配置、文档和发现缺陷所必需的最小修正。后端质量由JaCoCo、JUnit、MockMvc和数据库集成测试保证，前端由Vitest覆盖率和Playwright保证，性能由专用测试库、固定种子和k6保证；所有验证统一收口到可重复执行的PowerShell门禁。

**Tech Stack:** Java 17、Maven 3.9、JaCoCo 0.8.13、JUnit 5、MockMvc、MySQL 8、Redis 7、Vue 3.5.26、Vitest 3.2.4、Playwright、k6、PowerShell 7、Nginx、Flyway 11.7.2、Git。

---

## 范围、禁令与退出门禁

本计划不创建任何业务表、业务菜单或业务状态，不新增Flyway业务迁移。数据库最新版本必须继续为计划05的V6_1。发现需求缺口时回到对应计划的Service、Mapper、Controller或页面做最小修复，并在提交信息中使用`fix`，不得把新业务包装成“测试修正”。

所有使用Spring上下文、MockMvc、JdbcTemplate、Flyway或真实DataSource的集成测试位于`ruoyi-admin/src/test/java/com/ruoyi/integration/`，使用`application-test.yml`和`LAB_TEST_DB_URL/USERNAME/PASSWORD`连接独立MySQL 8测试库。创建或删除测试库只能使用`LAB_TEST_ADMIN_HOST/PORT/USERNAME/PASSWORD`，且名称必须以`lab_test_`开头；不使用H2或Testcontainers。

本计划闭合：

- FR-SYS-001～006、FR-AST-001～005、FR-QUA-001～004、FR-RES-001～007；
- FR-USE-001～005、FR-REP-001～005、FR-INS-001～005、FR-HAZ-001～007；
- FR-COM-001～002、FR-RPT-001～002、FR-API-001，共49项FR；
- NFR-PERF-001、NFR-CONC-001、NFR-SEC-001～004、NFR-REL-001～002、NFR-OBS-001、NFR-USA-001、NFR-COMP-001、NFR-MAIN-001、NFR-TEST-001、NFR-DOC-001、NFR-BACKUP-001，共15项NFR；
- AT-01～AT-16，共16项验收。

发布必须满足：

- `ruoyi-lab`总体行覆盖率不低于60%，核心Service实现行覆盖率不低于80%，所有状态机分支有测试；
- Lab前端纳入统计的代码行、语句和函数覆盖率不低于70%，分支不低于60%；
- 四个Playwright套件全绿；
- 非法排序、对象越权、非法附件和敏感信息测试全绿；
- 4核8GB基准环境下分页P95不超过2秒、状态命令P95不超过3秒、工作台首次请求P95不超过5秒、HTTP失败率低于1%；
- 20线程并发预约最多一条有效记录，Redis故障时数据库正确性不变；
- 空库安装、M5数据库升级、MySQL与附件备份恢复通过；
- 阻断级和严重级缺陷为零，工作区干净后才能创建`v1.0.0`标签。

## 强制TDD执行规则

每个Task严格按以下顺序执行：

- [ ] 先创建测试、Pester断言或门禁断言；
- [ ] 运行定向命令并保存预期失败原因；
- [ ] 只增加使当前门禁通过的测试基础设施或最小缺陷修正；
- [ ] 重跑定向测试和受影响回归；
- [ ] 通过后才提交该Task。仅生成报告的Task以“证据文件不存在或校验器拒绝缺失证据”作为红灯。

## 可重复验证与证据输出契约

计划 05 的 `scripts/verify.ps1` 是全量 Maven 门禁的唯一入口：它校验本机管理环境，重建固定安全库 `lab_test_m5_verify`，按已验证的 `LAB_TEST_ADMIN_HOST/PORT` 覆盖进程级 `LAB_TEST_DB_URL`，显式启用测试 Flyway，再执行 `mvn clean verify` 和 Surefire 报告反查。本计划不得绕过该包装器直接对继承来的数据库 URL 执行全量集成测试。

本计划所有生成证据的 PowerShell 脚本统一接收 `-EvidenceDirectory`：生成模式默认写入 `docs/testing/evidence`；只读复验模式必须传入本次唯一的 `target/release-verify/<guid>`。脚本只能在已解析且位于仓库内的这两个根目录之一创建输出，禁止写入其他 tracked 路径。创建GUID唯一临时数据库或附件恢复目录的脚本必须支持`-Cleanup`，且只删除本次成功创建、名称匹配`^lab_test_[a-z0-9_]+$`、主机为本机的目标。`run-lab-tests.ps1`使用的阶段固定库以及E2E、性能、并发固定库每次运行前必须安全重建；E2E和性能脚本额外支持`-Cleanup`，其他固定库由统一发布门禁在调用前登记到自身清理栈，并在外层`finally`精确删除。阶段内独立运行可保留固定测试库供审计，但统一发布和`-VerifyOnly`不得泄漏本次创建或重建的外部资源。若后续步骤仍需消费`lab_test_e2e`，只能延迟到最后一个消费者结束后清理，不得提前删除。这样同一门禁可以连续执行，且`-VerifyOnly`不修改tracked文件。

## Task 1: 建立49 FR、15 NFR、16 AT机器可检验追踪

**Files:**

- Create: `docs/testing/v1-traceability.csv`
- Create: `scripts/verify-traceability.ps1`
- Create: `ruoyi-admin/src/test/java/com/ruoyi/traceability/TraceabilityTable.java`
- Create: `ruoyi-admin/src/test/java/com/ruoyi/traceability/AcceptanceEvidenceTest.java`
- Modify: `docs/requirements/lab-management-srs.md`
- Modify: `scripts/verify.ps1`

- [ ] **Step 1.1: 先写失败的追踪验证测试**

`AcceptanceEvidenceTest` 读取CSV，断言FR为49、NFR为15、AT为16，每行都有非空的`plan_task`、`test_path`、`test_name`、`evidence_path`和合法状态。计划00～05已经完成且测试、阶段证据都存在的行初始化为`PASS`；本计划后续任务才会创建的测试或证据初始化为`PLANNED`。普通门禁要求所有`PASS`行的测试和证据路径都存在，最终发布使用`-RequireEvidence`额外要求所有行均已变为`PASS`。

```java
@Test
void traceabilityContainsEveryApprovedRequirement() throws Exception {
    Path repositoryRoot = TraceabilityTable.findRepositoryRoot(
        Path.of(System.getProperty("user.dir")));
    TraceabilityTable table = TraceabilityTable.load(
        repositoryRoot.resolve("docs/testing/v1-traceability.csv"));
    assertThat(table.idsStartingWith("FR-")).hasSize(49);
    assertThat(table.idsStartingWith("NFR-")).hasSize(15);
    assertThat(table.idsStartingWith("AT-")).hasSize(16);
    assertThat(table.rows()).allSatisfy(row -> {
        assertThat(row.planTask()).isNotBlank();
        assertThat(row.testPath()).isNotBlank();
        assertThat(row.testName()).isNotBlank();
        assertThat(row.evidencePath()).isNotBlank();
        assertThat(row.status()).isIn("PLANNED", "PASS");
        if (row.status().equals("PASS")) {
            assertThat(Files.exists(repositoryRoot.resolve(row.testPath())))
                .as(row.requirementId())
                .isTrue();
            assertThat(Files.exists(repositoryRoot.resolve(row.evidencePath())))
                .as(row.requirementId() + " evidence")
                .isTrue();
        }
    });
}
```

`TraceabilityTable`使用以下完整实现；CSV字段不允许包含逗号，因此不引入额外CSV依赖：

```java
package com.ruoyi.traceability;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

final class TraceabilityTable {
    private static final String HEADER =
        "requirement_id,plan_task,test_path,test_name,evidence_path,status";
    private final List<Row> rows;

    private TraceabilityTable(List<Row> rows) {
        this.rows = List.copyOf(rows);
    }

    static TraceabilityTable load(Path csvPath) throws IOException {
        List<String> lines = Files.readAllLines(csvPath, StandardCharsets.UTF_8);
        if (lines.isEmpty() || !HEADER.equals(lines.get(0))) {
            throw new IllegalArgumentException("Unexpected traceability header: " + csvPath);
        }
        List<Row> rows = lines.stream().skip(1)
            .filter(line -> !line.isBlank())
            .map(TraceabilityTable::parseRow)
            .toList();
        Set<String> uniqueIds = new HashSet<>();
        for (Row row : rows) {
            if (!uniqueIds.add(row.requirementId())) {
                throw new IllegalArgumentException(
                    "Duplicate requirement_id: " + row.requirementId());
            }
        }
        return new TraceabilityTable(rows);
    }

    static Path findRepositoryRoot(Path start) {
        for (Path current = start.toAbsolutePath().normalize();
             current != null; current = current.getParent()) {
            if (Files.isRegularFile(current.resolve("pom.xml"))
                && Files.isRegularFile(current.resolve(
                    "docs/requirements/lab-management-srs.md"))) {
                return current;
            }
        }
        throw new IllegalStateException("Repository root not found from " + start);
    }

    private static Row parseRow(String line) {
        String[] fields = line.split(",", -1);
        if (fields.length != 6) {
            throw new IllegalArgumentException("Expected 6 CSV fields: " + line);
        }
        validateRelativePath(fields[2]);
        validateRelativePath(fields[4]);
        return new Row(fields[0], fields[1], fields[2], fields[3], fields[4], fields[5]);
    }

    private static void validateRelativePath(String value) {
        if (value.contains("\\") || Path.of(value).isAbsolute()
            || value.equals("..") || value.startsWith("../") || value.contains("/../")) {
            throw new IllegalArgumentException("Path must be repository-relative: " + value);
        }
    }

    Set<String> idsStartingWith(String prefix) {
        return rows.stream().map(Row::requirementId)
            .filter(id -> id.startsWith(prefix)).collect(Collectors.toUnmodifiableSet());
    }

    List<Row> rows() {
        return rows;
    }

    record Row(String requirementId, String planTask, String testPath,
               String testName, String evidencePath, String status) { }
}
```

CSV中的`test_path`和`evidence_path`必须是使用正斜杠的仓库相对完整路径，不能只写文件名或绝对路径。

- [ ] **Step 1.2: 运行测试确认失败**

    mvn -pl ruoyi-admin -am -DskipTests=false -Dsurefire.failIfNoSpecifiedTests=false "-Dtest=AcceptanceEvidenceTest" test

预期：FAIL，`v1-traceability.csv`尚不存在。

- [ ] **Step 1.3: 创建追踪CSV并固定分组证据**

CSV表头固定为：

```csv
requirement_id,plan_task,test_path,test_name,evidence_path,status
```

逐项展开需求ID，不能只写`FR-RES-*`。每完成本计划一个Task，就把该Task覆盖的行改为`PASS`并填写已经生成的证据路径；禁止在证据不存在时预先标记`PASS`。证据分组固定如下：

| ID范围 | 主要测试文件 |
|---|---|
| FR-SYS-001～006 | `LabRoleSeedIT.java`、`LabLayerArchitectureTest.java`、`LabExceptionContractTest.java`、`LabAuthorizationMatrixIT.java` |
| FR-AST-001～005 | `AssetQualificationAcceptanceIT.java`、`LabObjectPermissionIT.java`、`AttachmentAuthorizationIT.java` |
| FR-QUA-001～004 | `QualificationServiceTest.java`、`LabQualificationGuardIT.java` |
| FR-RES-001～007 | `ReservationApplicationTest.java`、`ReservationAcceptanceIT.java`、`ReservationConcurrencyIT.java`、`LabScheduledLifecycleIT.java` |
| FR-USE-001～005 | `UsageCheckoutServiceTest.java`、`UsageReturnServiceTest.java`、`UsageRepairRollbackTest.java`、`UsageRepairAcceptanceTest.java` |
| FR-REP-001～005 | `RepairAssignmentServiceTest.java`、`RepairWorkflowServiceTest.java`、`RepairQueryServiceTest.java`、`UsageRepairAcceptanceTest.java` |
| FR-INS-001～005 | `InspectionPlanServiceTest.java`、`InspectionScheduleServiceTest.java`、`InspectionExecutionServiceTest.java`、`InspectionHazardAcceptanceTest.java`、`LabScheduledLifecycleIT.java` |
| FR-HAZ-001～007 | `HazardServiceTest.java`、`RectificationWorkflowServiceTest.java`、`HazardBlockRecoveryAcceptanceTest.java`、`LabScheduledLifecycleIT.java` |
| FR-COM-001～002 | `LabNotificationControllerIT.java`、`LabNotificationAfterCommitIT.java`、`LabNotificationCompensationIT.java` |
| FR-RPT-001～002 | `LabDashboardMapperIT.java`、`LabDashboardControllerIT.java` |
| FR-API-001 | `ruoyi-admin/src/test/java/com/ruoyi/integration/openapi/LabOpenApiContractIT.java` |
| NFR-PERF-001 | `docs/testing/evidence/k6-summary.json` |
| NFR-CONC-001 | `ReservationConcurrencyIT.java`、`RepairOpenConcurrencyTest.java`、`UsageRepairCrossCommandConcurrencyTest.java` |
| NFR-SEC-001～004 | `LabAuthorizationMatrixIT.java`、`LabSortWhitelistTest.java`、`LabAttachmentSecurityIT.java`、`docs/testing/evidence/secret-scan.txt` |
| NFR-REL-001～002 | `UsageRepairRollbackTest.java`、`LabScheduledLifecycleIT.java`、`LabNotificationAfterCommitIT.java`、`LabNotificationCompensationIT.java` |
| NFR-OBS-001 | `LabExceptionContractTest.java`、`SensitiveLogIT.java` |
| NFR-USA-001、NFR-COMP-001 | Playwright四套件和`docs/testing/evidence/browser-matrix.md` |
| NFR-MAIN-001 | `LabLayerArchitectureTest.java` |
| NFR-TEST-001 | JaCoCo与Vitest覆盖率报告 |
| NFR-DOC-001 | `scripts/verify-docs.ps1` |
| NFR-BACKUP-001 | `docs/testing/evidence/restore-manifest.json` |
| AT-01～16 | 对应自动化测试、烟雾脚本和`docs/testing/v1-test-report.md`章节 |

`v1-traceability.csv`初始内容必须逐行写成下面这80条，不得用范围或通配符代替。后续Task只更新对应行的`test_path`、`evidence_path`和`status`，不得改变需求ID集合：

```csv
requirement_id,plan_task,test_path,test_name,evidence_path,status
FR-SYS-001,P00-T7,ruoyi-admin/src/test/java/com/ruoyi/integration/web/demo/LabDemoAccountInitializerTest.java,LabDemoAccountInitializerTest,docs/testing/m1-foundation-report.md,PASS
FR-SYS-002,P06-T5,ruoyi-admin/src/test/java/com/ruoyi/integration/security/LabAuthorizationMatrixIT.java,LabAuthorizationMatrixIT,docs/testing/evidence/security-report.md,PLANNED
FR-SYS-003,P01-T3,ruoyi-admin/src/test/java/com/ruoyi/integration/lab/security/LabObjectPermissionIT.java,LabObjectPermissionIT,docs/testing/m2-assets-qualifications-report.md,PASS
FR-SYS-004,P00-T7,ruoyi-admin/src/test/java/com/ruoyi/integration/security/LabRoleSeedIT.java,LabRoleSeedIT,docs/testing/m1-foundation-report.md,PASS
FR-SYS-005,P03-T9,ruoyi-admin/src/test/java/com/ruoyi/integration/acceptance/UsageRepairAcceptanceTest.java,UsageRepairAcceptanceTest,docs/testing/m4-usage-repair-report.md,PASS
FR-SYS-006,P00-T6,ruoyi-admin/src/test/java/com/ruoyi/integration/web/exception/LabExceptionContractTest.java,LabExceptionContractTest,docs/testing/m1-foundation-report.md,PASS
FR-AST-001,P01-T3,ruoyi-admin/src/test/java/com/ruoyi/integration/lab/web/LabLaboratoryControllerTest.java,LabLaboratoryControllerTest,docs/testing/m2-assets-qualifications-report.md,PASS
FR-AST-002,P01-T3,ruoyi-admin/src/test/java/com/ruoyi/integration/lab/web/LabDeviceControllerTest.java,LabDeviceControllerTest,docs/testing/m2-assets-qualifications-report.md,PASS
FR-AST-003,P01-T3,ruoyi-lab/src/test/java/com/ruoyi/lab/service/DeviceStateMachineTest.java,DeviceStateMachineTest,docs/testing/m2-assets-qualifications-report.md,PASS
FR-AST-004,P02-T8,ruoyi-admin/src/test/java/com/ruoyi/integration/lab/acceptance/ReservationAcceptanceIT.java,ReservationAcceptanceIT,docs/testing/m3-reservations-report.md,PASS
FR-AST-005,P01-T5,ruoyi-admin/src/test/java/com/ruoyi/integration/lab/security/AttachmentAuthorizationIT.java,AttachmentAuthorizationIT,docs/testing/m2-assets-qualifications-report.md,PASS
FR-QUA-001,P01-T4,ruoyi-lab/src/test/java/com/ruoyi/lab/service/QualificationServiceTest.java,QualificationServiceTest,docs/testing/m2-assets-qualifications-report.md,PASS
FR-QUA-002,P01-T4,ruoyi-admin/src/test/java/com/ruoyi/integration/lab/service/LabQualificationGuardIT.java,LabQualificationGuardIT,docs/testing/m2-assets-qualifications-report.md,PASS
FR-QUA-003,P01-T7,ruoyi-admin/src/test/java/com/ruoyi/integration/lab/acceptance/AssetQualificationAcceptanceIT.java,AssetQualificationAcceptanceIT,docs/testing/m2-assets-qualifications-report.md,PASS
FR-QUA-004,P02-T8,ruoyi-admin/src/test/java/com/ruoyi/integration/lab/acceptance/ReservationAcceptanceIT.java,ReservationAcceptanceIT,docs/testing/m3-reservations-report.md,PASS
FR-RES-001,P02-T3,ruoyi-admin/src/test/java/com/ruoyi/integration/lab/service/ReservationIdempotencyIT.java,ReservationIdempotencyIT,docs/testing/m3-reservations-report.md,PASS
FR-RES-002,P02-T8,ruoyi-admin/src/test/java/com/ruoyi/integration/lab/acceptance/ReservationConcurrencyIT.java,ReservationConcurrencyIT,docs/testing/m3-reservations-report.md,PASS
FR-RES-003,P02-T4,ruoyi-lab/src/test/java/com/ruoyi/lab/service/ReservationDecisionTest.java,ReservationDecisionTest,docs/testing/m3-reservations-report.md,PASS
FR-RES-004,P02-T4,ruoyi-lab/src/test/java/com/ruoyi/lab/service/ReservationDecisionTest.java,ReservationDecisionTest,docs/testing/m3-reservations-report.md,PASS
FR-RES-005,P05-T7,ruoyi-admin/src/test/java/com/ruoyi/integration/jobs/LabScheduledLifecycleIT.java,LabScheduledLifecycleIT,docs/testing/m5-jobs-messages-dashboard-report.md,PASS
FR-RES-006,P02-T5,ruoyi-admin/src/test/java/com/ruoyi/integration/lab/service/ReservationQueryIT.java,ReservationQueryIT,docs/testing/m3-reservations-report.md,PASS
FR-RES-007,P05-T2,ruoyi-admin/src/test/java/com/ruoyi/integration/notification/LabNotificationAfterCommitIT.java,LabNotificationAfterCommitIT,docs/testing/m5-jobs-messages-dashboard-report.md,PASS
FR-USE-001,P03-T3,ruoyi-admin/src/test/java/com/ruoyi/integration/service/UsageCheckoutServiceTest.java,UsageCheckoutServiceTest,docs/testing/m4-usage-repair-report.md,PASS
FR-USE-002,P03-T3,ruoyi-admin/src/test/java/com/ruoyi/integration/service/UsageCheckoutServiceTest.java,UsageCheckoutServiceTest,docs/testing/m4-usage-repair-report.md,PASS
FR-USE-003,P03-T4,ruoyi-admin/src/test/java/com/ruoyi/integration/service/UsageReturnServiceTest.java,UsageReturnServiceTest,docs/testing/m4-usage-repair-report.md,PASS
FR-USE-004,P03-T4,ruoyi-admin/src/test/java/com/ruoyi/integration/service/UsageRepairRollbackTest.java,UsageRepairRollbackTest,docs/testing/m4-usage-repair-report.md,PASS
FR-USE-005,P03-T3,ruoyi-admin/src/test/java/com/ruoyi/integration/service/UsageQueryServiceTest.java,UsageQueryServiceTest,docs/testing/m4-usage-repair-report.md,PASS
FR-REP-001,P03-T5,ruoyi-admin/src/test/java/com/ruoyi/integration/service/RepairReportServiceTest.java,RepairReportServiceTest,docs/testing/m4-usage-repair-report.md,PASS
FR-REP-002,P03-T5,ruoyi-admin/src/test/java/com/ruoyi/integration/service/RepairAssignmentServiceTest.java,RepairAssignmentServiceTest,docs/testing/m4-usage-repair-report.md,PASS
FR-REP-003,P03-T6,ruoyi-admin/src/test/java/com/ruoyi/integration/service/RepairWorkflowServiceTest.java,RepairWorkflowServiceTest,docs/testing/m4-usage-repair-report.md,PASS
FR-REP-004,P03-T6,ruoyi-admin/src/test/java/com/ruoyi/integration/service/RepairWorkflowServiceTest.java,RepairWorkflowServiceTest,docs/testing/m4-usage-repair-report.md,PASS
FR-REP-005,P03-T6,ruoyi-admin/src/test/java/com/ruoyi/integration/service/RepairQueryServiceTest.java,RepairQueryServiceTest,docs/testing/m4-usage-repair-report.md,PASS
FR-INS-001,P04-T2,ruoyi-admin/src/test/java/com/ruoyi/integration/service/InspectionPlanServiceTest.java,InspectionPlanServiceTest,docs/testing/m4-inspection-hazard-report.md,PASS
FR-INS-002,P04-T3,ruoyi-admin/src/test/java/com/ruoyi/integration/service/InspectionScheduleServiceTest.java,InspectionScheduleServiceTest,docs/testing/m4-inspection-hazard-report.md,PASS
FR-INS-003,P04-T4,ruoyi-admin/src/test/java/com/ruoyi/integration/service/InspectionExecutionServiceTest.java,InspectionExecutionServiceTest,docs/testing/m4-inspection-hazard-report.md,PASS
FR-INS-004,P04-T4,ruoyi-admin/src/test/java/com/ruoyi/integration/service/InspectionCompletionRollbackTest.java,InspectionCompletionRollbackTest,docs/testing/m4-inspection-hazard-report.md,PASS
FR-INS-005,P05-T7,ruoyi-admin/src/test/java/com/ruoyi/integration/jobs/LabScheduledLifecycleIT.java,LabScheduledLifecycleIT,docs/testing/m5-jobs-messages-dashboard-report.md,PASS
FR-HAZ-001,P04-T5,ruoyi-admin/src/test/java/com/ruoyi/integration/service/HazardServiceTest.java,HazardServiceTest,docs/testing/m4-inspection-hazard-report.md,PASS
FR-HAZ-002,P04-T5,ruoyi-admin/src/test/java/com/ruoyi/integration/security/MajorHazardBlockerTest.java,MajorHazardBlockerTest,docs/testing/m4-inspection-hazard-report.md,PASS
FR-HAZ-003,P04-T6,ruoyi-admin/src/test/java/com/ruoyi/integration/service/RectificationWorkflowServiceTest.java,RectificationWorkflowServiceTest,docs/testing/m4-inspection-hazard-report.md,PASS
FR-HAZ-004,P04-T6,ruoyi-admin/src/test/java/com/ruoyi/integration/service/RectificationWorkflowServiceTest.java,RectificationWorkflowServiceTest,docs/testing/m4-inspection-hazard-report.md,PASS
FR-HAZ-005,P04-T6,ruoyi-admin/src/test/java/com/ruoyi/integration/service/RectificationWorkflowServiceTest.java,RectificationWorkflowServiceTest,docs/testing/m4-inspection-hazard-report.md,PASS
FR-HAZ-006,P05-T7,ruoyi-admin/src/test/java/com/ruoyi/integration/jobs/LabScheduledLifecycleIT.java,LabScheduledLifecycleIT,docs/testing/m5-jobs-messages-dashboard-report.md,PASS
FR-HAZ-007,P04-T6,ruoyi-admin/src/test/java/com/ruoyi/integration/service/HazardRecoveryServiceTest.java,HazardRecoveryServiceTest,docs/testing/m4-inspection-hazard-report.md,PASS
FR-COM-001,P05-T5,ruoyi-admin/src/test/java/com/ruoyi/integration/controller/LabNotificationControllerIT.java,LabNotificationControllerIT,docs/testing/m5-jobs-messages-dashboard-report.md,PASS
FR-COM-002,P05-T7,ruoyi-admin/src/test/java/com/ruoyi/integration/jobs/LabScheduledLifecycleIT.java,LabScheduledLifecycleIT,docs/testing/m5-jobs-messages-dashboard-report.md,PASS
FR-RPT-001,P05-T6,ruoyi-admin/src/test/java/com/ruoyi/integration/controller/LabDashboardControllerIT.java,LabDashboardControllerIT,docs/testing/m5-jobs-messages-dashboard-report.md,PASS
FR-RPT-002,P05-T6,ruoyi-admin/src/test/java/com/ruoyi/integration/dashboard/LabDashboardMapperIT.java,LabDashboardMapperIT,docs/testing/m5-jobs-messages-dashboard-report.md,PASS
FR-API-001,P06-T5,ruoyi-admin/src/test/java/com/ruoyi/integration/openapi/LabOpenApiContractIT.java,LabOpenApiContractIT,docs/api/openapi-lab.json,PLANNED
NFR-PERF-001,P06-T6,scripts/performance/run-performance.ps1,run-performance.ps1,docs/testing/evidence/k6-summary.json,PLANNED
NFR-CONC-001,P06-T7,ruoyi-admin/src/test/java/com/ruoyi/integration/concurrency/ReservationReleaseConcurrencyIT.java,ReservationReleaseConcurrencyIT,docs/testing/evidence/concurrency-fault-report.md,PLANNED
NFR-SEC-001,P06-T5,ruoyi-admin/src/test/java/com/ruoyi/integration/security/LabAuthorizationMatrixIT.java,LabAuthorizationMatrixIT,docs/testing/evidence/security-report.md,PLANNED
NFR-SEC-002,P06-T5,ruoyi-lab/src/test/java/com/ruoyi/lab/service/LabSortWhitelistTest.java,LabSortWhitelistTest,docs/testing/evidence/security-report.md,PLANNED
NFR-SEC-003,P06-T5,ruoyi-admin/src/test/java/com/ruoyi/integration/security/LabAttachmentSecurityIT.java,LabAttachmentSecurityIT,docs/testing/evidence/security-report.md,PLANNED
NFR-SEC-004,P06-T5,scripts/scan-secrets.ps1,scan-secrets.ps1,docs/testing/evidence/secret-scan.txt,PLANNED
NFR-REL-001,P03-T4,ruoyi-admin/src/test/java/com/ruoyi/integration/service/UsageRepairRollbackTest.java,UsageRepairRollbackTest,docs/testing/m4-usage-repair-report.md,PASS
NFR-REL-002,P05-T3,ruoyi-admin/src/test/java/com/ruoyi/integration/notification/LabNotificationCompensationIT.java,LabNotificationCompensationIT,docs/testing/m5-jobs-messages-dashboard-report.md,PASS
NFR-OBS-001,P06-T5,ruoyi-admin/src/test/java/com/ruoyi/integration/security/SensitiveLogIT.java,SensitiveLogIT,docs/testing/evidence/security-report.md,PLANNED
NFR-USA-001,P06-T4,ruoyi-ui/tests/e2e/authorization-responsive.spec.js,authorization-responsive.spec.js,docs/testing/evidence/browser-matrix.md,PLANNED
NFR-COMP-001,P06-T4,ruoyi-ui/tests/e2e/authorization-responsive.spec.js,authorization-responsive.spec.js,docs/testing/evidence/browser-matrix.md,PLANNED
NFR-MAIN-001,P00-T3,ruoyi-admin/src/test/java/com/ruoyi/architecture/LabLayerArchitectureTest.java,LabLayerArchitectureTest,docs/testing/m1-foundation-report.md,PASS
NFR-TEST-001,P06-T2,scripts/verify-backend-coverage.ps1,verify-backend-coverage.ps1,docs/testing/evidence/backend-coverage.md,PLANNED
NFR-DOC-001,P06-T9,scripts/verify-docs.ps1,verify-docs.ps1,docs/release/v1.0.0-checklist.md,PLANNED
NFR-BACKUP-001,P06-T9,scripts/restore-lab.ps1,restore-lab.ps1,docs/testing/evidence/restore-manifest.json,PLANNED
AT-01,P06-T8,scripts/verify-fresh-install.ps1,verify-fresh-install.ps1,docs/testing/evidence/fresh-install-report.md,PLANNED
AT-02,P06-T5,ruoyi-admin/src/test/java/com/ruoyi/integration/security/LabAuthorizationMatrixIT.java,LabAuthorizationMatrixIT,docs/testing/evidence/security-report.md,PLANNED
AT-03,P01-T7,ruoyi-admin/src/test/java/com/ruoyi/integration/lab/acceptance/AssetQualificationAcceptanceIT.java,AssetQualificationAcceptanceIT,docs/testing/m2-assets-qualifications-report.md,PASS
AT-04,P03-T9,ruoyi-admin/src/test/java/com/ruoyi/integration/acceptance/UsageRepairAcceptanceTest.java,UsageRepairAcceptanceTest,docs/testing/m4-usage-repair-report.md,PASS
AT-05,P02-T8,ruoyi-admin/src/test/java/com/ruoyi/integration/lab/acceptance/ReservationConcurrencyIT.java,ReservationConcurrencyIT,docs/testing/m3-reservations-report.md,PASS
AT-06,P04-T9,ruoyi-admin/src/test/java/com/ruoyi/integration/reservation/MajorHazardReservationIntegrationTest.java,MajorHazardReservationIntegrationTest,docs/testing/m4-inspection-hazard-report.md,PASS
AT-07,P03-T4,ruoyi-admin/src/test/java/com/ruoyi/integration/service/UsageRepairRollbackTest.java,UsageRepairRollbackTest,docs/testing/m4-usage-repair-report.md,PASS
AT-08,P03-T9,ruoyi-admin/src/test/java/com/ruoyi/integration/acceptance/UsageRepairAcceptanceTest.java,UsageRepairAcceptanceTest,docs/testing/m4-usage-repair-report.md,PASS
AT-09,P04-T9,ruoyi-admin/src/test/java/com/ruoyi/integration/acceptance/InspectionHazardAcceptanceTest.java,InspectionHazardAcceptanceTest,docs/testing/m4-inspection-hazard-report.md,PASS
AT-10,P04-T9,ruoyi-admin/src/test/java/com/ruoyi/integration/acceptance/HazardBlockRecoveryAcceptanceTest.java,HazardBlockRecoveryAcceptanceTest,docs/testing/m4-inspection-hazard-report.md,PASS
AT-11,P05-T7,ruoyi-admin/src/test/java/com/ruoyi/integration/jobs/LabScheduledLifecycleIT.java,LabScheduledLifecycleIT,docs/testing/m5-jobs-messages-dashboard-report.md,PASS
AT-12,P05-T6,ruoyi-admin/src/test/java/com/ruoyi/integration/dashboard/LabDashboardMapperIT.java,LabDashboardMapperIT,docs/testing/m5-jobs-messages-dashboard-report.md,PASS
AT-13,P06-T5,ruoyi-admin/src/test/java/com/ruoyi/integration/openapi/LabOpenApiContractIT.java,LabOpenApiContractIT,docs/api/openapi-lab.json,PLANNED
AT-14,P06-T9,scripts/verify-release.ps1,verify-release.ps1,docs/testing/v1-test-report.md,PLANNED
AT-15,P06-T5,ruoyi-admin/src/test/java/com/ruoyi/integration/security/LabAttachmentSecurityIT.java,LabAttachmentSecurityIT,docs/testing/evidence/security-report.md,PLANNED
AT-16,P06-T9,scripts/restore-lab.ps1,restore-lab.ps1,docs/testing/evidence/restore-manifest.json,PLANNED
```

- [ ] **Step 1.4: 创建PowerShell集合校验**

```powershell
param([switch]$RequireEvidence)
$ErrorActionPreference = 'Stop'
$srsPath = Join-Path $PSScriptRoot '..\docs\requirements\lab-management-srs.md'
$csvPath = Join-Path $PSScriptRoot '..\docs\testing\v1-traceability.csv'
$srs = Get-Content -Raw -LiteralPath $srsPath
$required = [regex]::Matches($srs, '\b(?:FR|NFR)-[A-Z]+-[0-9]{3}\b|\bAT-[0-9]{2}\b') |
  ForEach-Object Value | Sort-Object -Unique
$rows = Import-Csv -LiteralPath $csvPath
$tracked = $rows.requirement_id | Sort-Object -Unique
$duplicates = $rows | Group-Object requirement_id |
  Where-Object Count -gt 1 | ForEach-Object Name
if ($duplicates) { throw "Duplicate traceability IDs: $($duplicates -join ', ')" }
$missing = Compare-Object $required $tracked | Where-Object SideIndicator -eq '<=' | ForEach-Object InputObject
$extra = Compare-Object $required $tracked | Where-Object SideIndicator -eq '=>' | ForEach-Object InputObject
if ($missing) { throw "Missing traceability IDs: $($missing -join ', ')" }
if ($extra) { throw "Unknown traceability IDs: $($extra -join ', ')" }
foreach ($row in $rows) {
  foreach ($field in 'plan_task','test_path','test_name','evidence_path','status') {
    if ([string]::IsNullOrWhiteSpace($row.$field)) {
      throw "Empty $field for $($row.requirement_id)"
    }
  }
  if ($row.status -notin 'PLANNED','PASS') {
    throw "Invalid status for $($row.requirement_id): $($row.status)"
  }
  if ($row.status -eq 'PASS' -and -not (Test-Path -LiteralPath (Join-Path $PSScriptRoot "..\$($row.test_path)"))) {
    throw "Missing test path for $($row.requirement_id): $($row.test_path)"
  }
  if ($row.status -eq 'PASS' -and -not (Test-Path -LiteralPath (Join-Path $PSScriptRoot "..\$($row.evidence_path)"))) {
    throw "Missing evidence for $($row.requirement_id): $($row.evidence_path)"
  }
  if ($RequireEvidence -and $row.status -ne 'PASS') {
    throw "Requirement is not PASS: $($row.requirement_id)"
  }
  if ($RequireEvidence -and -not (Test-Path -LiteralPath (Join-Path $PSScriptRoot "..\$($row.test_path)"))) {
    throw "Missing test path for $($row.requirement_id): $($row.test_path)"
  }
  if ($RequireEvidence -and -not (Test-Path -LiteralPath (Join-Path $PSScriptRoot "..\$($row.evidence_path)"))) {
    throw "Missing evidence for $($row.requirement_id): $($row.evidence_path)"
  }
}
Write-Output "Traceability complete: $($tracked.Count) IDs"
```

- [ ] **Step 1.5: 运行追踪门禁**

    powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-traceability.ps1
    mvn -pl ruoyi-admin -am -DskipTests=false -Dsurefire.failIfNoSpecifiedTests=false "-Dtest=AcceptanceEvidenceTest" test

预期：输出`Traceability complete: 80 IDs`；JUnit测试PASS。

- [ ] **Step 1.6: 提交追踪基线**

    git add docs/testing/v1-traceability.csv docs/requirements/lab-management-srs.md scripts ruoyi-admin/src/test
    git commit -m "test: map every v1 requirement to evidence"

## Task 2: 强制后端JaCoCo覆盖率和状态机分支

**Files:**

- Modify: `pom.xml`
- Modify: `ruoyi-admin/pom.xml`
- Modify: `ruoyi-lab/pom.xml`
- Create: `ruoyi-lab/src/test/java/com/ruoyi/lab/state/AllStateTransitionsTest.java`
- Create: `scripts/verify-backend-coverage.ps1`
- Create: `docs/testing/evidence/backend-coverage.md`

- [ ] **Step 2.1: 写状态转换参数化测试并确认缺口会失败**

测试逐项覆盖预约、设备、维修、巡检和隐患设计表中的合法与非法迁移。非法迁移必须返回稳定409且原状态不变。

- [ ] **Step 2.2: 配置JaCoCo规则**

根POM固定`jacoco-maven-plugin` 0.8.13，并让所有后端模块在测试前执行`prepare-agent`。`ruoyi-admin/pom.xml`在verify阶段执行`report-aggregate`，聚合admin中的Spring集成测试和lab模块类；覆盖率门禁读取聚合XML，不能使用遗漏admin集成测试的lab单模块报告。

```xml
<execution>
  <id>report-aggregate</id>
  <phase>verify</phase>
  <goals><goal>report-aggregate</goal></goals>
  <configuration>
    <outputDirectory>${project.build.directory}/site/jacoco-aggregate</outputDirectory>
  </configuration>
</execution>
```

`verify-backend-coverage.ps1`解析`ruoyi-admin/target/site/jacoco-aggregate/jacoco.xml`，只汇总包名以`com/ruoyi/lab`开头的类：全部lab代码行覆盖率至少60%，类名以`ServiceImpl`结尾的实现合计至少80%。排除仅限MyBatis实体getter/setter、生成Mapper代理和配置类；状态机、权限、阻断、事务编排和生命周期服务不得排除。

- [ ] **Step 2.3: 运行覆盖率门禁并补齐缺失测试**

    powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify.ps1
    powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-backend-coverage.ps1

预期：安全包装器重建并只连接`lab_test_m5_verify`，BUILD SUCCESS；`ruoyi-admin/target/site/jacoco-aggregate/jacoco.xml`存在；lab总体行覆盖率至少60%，核心Service实现至少80%。

- [ ] **Step 2.4: 保存不含机器绝对路径的证据摘要**

`backend-coverage.md`记录Git提交、测试数、失败数、总体行覆盖率、核心Service最低覆盖率和状态转换用例数，不提交HTML报告目录。

- [ ] **Step 2.5: 提交后端覆盖率门禁**

    git add pom.xml ruoyi-admin/pom.xml ruoyi-lab scripts/verify-backend-coverage.ps1 docs/testing/evidence/backend-coverage.md
    git commit -m "test: enforce backend coverage and state branches"

## Task 3: 强制Lab前端覆盖率

**Files:**

- Modify: `ruoyi-ui/vitest.config.js`
- Modify: `ruoyi-ui/package.json`
- Create: `scripts/verify-frontend-coverage.ps1`
- Create: `docs/testing/evidence/frontend-coverage.md`
- Modify: `docs/testing/v1-traceability.csv`
- Test: `ruoyi-ui/tests/unit/**/*.spec.js`

- [ ] **Step 3.1: 将Lab源文件纳入覆盖率并先运行失败门禁**

覆盖范围固定为`src/api/lab/**`、`src/utils/lab/**`、`src/store/modules/lab*.js`、`src/components/lab/**`和`src/views/lab/**`。配置：

```javascript
coverage: {
  provider: 'v8',
  reporter: ['text', 'json-summary', 'html'],
  reportsDirectory: process.env.LAB_FRONTEND_COVERAGE_DIR || '../target/frontend-coverage',
  include: [
    'src/api/lab/**/*.js',
    'src/utils/lab/**/*.js',
    'src/store/modules/lab*.js',
    'src/components/lab/**/*.{js,vue}',
    'src/views/lab/**/*.{js,vue}'
  ],
  thresholds: {
    lines: 70,
    statements: 70,
    functions: 70,
    branches: 60
  }
}
```

`verify-frontend-coverage.ps1`与统一发布门禁必须在启动Vitest前设置`LAB_FRONTEND_COVERAGE_DIR`：普通生成模式使用仓库根目录下的`target/frontend-coverage`，`-VerifyOnly`使用本次`EvidenceDirectory`下的`frontend-coverage-raw`。校验脚本从该目录读取`coverage-summary.json`，不得在`ruoyi-ui/coverage`或其他tracked路径生成原始报告。

运行：

    corepack yarn --cwd .\ruoyi-ui test:coverage

预期：如果任一阈值未达到则退出1并明确显示缺口。

- [ ] **Step 3.2: 按缺口补充真实行为测试**

优先补充状态按钮可见性、409文案、幂等按钮、附件限制、消息未读数、角色工作台和375px核心交互。禁止用无断言快照抬高覆盖率。

- [ ] **Step 3.3: 运行并记录覆盖率**

    corepack yarn --cwd .\ruoyi-ui test:coverage
    powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-frontend-coverage.ps1

预期：四个阈值均通过；JSON摘要与`frontend-coverage.md`数值一致。

- [ ] **Step 3.4: 提交前端覆盖率门禁**

后端与前端覆盖率均通过后，把`NFR-TEST-001`更新为`PASS`并保留后端、前端两个报告在测试总报告中的引用，然后先运行普通追踪门禁。

    powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-traceability.ps1
    git add ruoyi-ui scripts/verify-frontend-coverage.ps1 docs/testing/evidence/frontend-coverage.md docs/testing/v1-traceability.csv
    git commit -m "test: enforce lab frontend coverage"

## Task 4: 建立Playwright端到端验收

**Files:**

- Modify: `ruoyi-ui/package.json`
- Create: `ruoyi-ui/playwright.config.js`
- Create: `ruoyi-ui/tests/e2e/support/accounts.js`
- Create: `scripts/e2e/reset-e2e.ps1`
- Create: `scripts/e2e/seed-e2e.sql`
- Create: `ruoyi-ui/tests/e2e/normal-reservation.spec.js`
- Create: `ruoyi-ui/tests/e2e/repair-loop.spec.js`
- Create: `ruoyi-ui/tests/e2e/inspection-hazard.spec.js`
- Create: `ruoyi-ui/tests/e2e/authorization-responsive.spec.js`
- Create: `docs/testing/evidence/browser-matrix.md`
- Modify: `docs/testing/v1-traceability.csv`
- Modify: `ruoyi-ui/src/views/lab/reservation/index.vue`
- Modify: `ruoyi-ui/src/views/lab/usage/index.vue`
- Modify: `ruoyi-ui/src/views/lab/repair/index.vue`
- Modify: `ruoyi-ui/src/views/lab/inspection/task/index.vue`
- Modify: `ruoyi-ui/src/views/lab/hazard/index.vue`
- Create: `scripts/run-e2e.ps1`

- [ ] **Step 4.1: 固定Playwright配置和测试标识**

增加`@playwright/test`和`test:e2e`脚本。页面仅增加稳定`data-testid`，不得改变业务。配置完整核心如下：

```javascript
import { defineConfig, devices } from '@playwright/test'
import path from 'node:path'

const artifactRoot = path.resolve(
  process.env.LAB_E2E_ARTIFACT_DIR || '../target/playwright'
)

export default defineConfig({
  testDir: './tests/e2e',
  fullyParallel: false,
  retries: 1,
  workers: 1,
  outputDir: path.join(artifactRoot, 'artifacts'),
  reporter: [['list'], ['json', { outputFile: path.join(artifactRoot, 'e2e-results.json') }]],
  use: {
    baseURL: process.env.LAB_E2E_BASE_URL || 'http://127.0.0.1:4173',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure'
  },
  projects: [
    { name: 'chrome-current', use: { ...devices['Desktop Chrome'], channel: 'chrome' } },
    { name: 'edge-current', use: { ...devices['Desktop Chrome'], channel: 'msedge' } },
    { name: 'chrome-previous', use: { ...devices['Desktop Chrome'], launchOptions: { executablePath: process.env.LAB_CHROME_PREVIOUS_EXECUTABLE } } },
    { name: 'edge-previous', use: { ...devices['Desktop Chrome'], launchOptions: { executablePath: process.env.LAB_EDGE_PREVIOUS_EXECUTABLE } } },
    { name: 'student-mobile', use: { ...devices['Pixel 7'], channel: 'chrome' }, testMatch: /authorization-responsive/ }
  ]
})
```

`playwright.config.js`必须在启动时校验两个上一主版本可执行文件环境变量非空且文件存在，不能静默退化为当前版本。`run-e2e.ps1`分别读取Chrome、Edge当前渠道和两个指定可执行文件的实际主版本，断言各自`previousMajor = currentMajor - 1`；不相邻、产品名不符或无法读取版本都退出1。包装器在普通模式把`LAB_E2E_ARTIFACT_DIR`设为仓库根目录下的`target/playwright`，在`-VerifyOnly`中设为本次`EvidenceDirectory`下的`playwright-raw`，禁止写入`ruoyi-ui/test-results`。脚本按统一`-EvidenceDirectory`契约写出`browser-matrix.md`，内容包含四个桌面浏览器的产品名、完整版本、可执行文件SHA-256、分辨率和结果；发布门禁要求Chrome和Edge的当前及前一主要版本都执行四套桌面剧本，375px项目执行学生核心剧本。

- [ ] **Step 4.2: 创建可重复的数据重置入口**

`reset-e2e.ps1`包装计划00已有的`scripts/reset-test-db.ps1`并把目标严格固定为`lab_test_e2e`。它先校验本机`LAB_TEST_ADMIN_HOST/PORT`，每次都重建该库并检查子脚本退出码，再按同一host/port和固定库名构造当前测试进程使用的`LAB_TEST_DB_URL`；禁止信任或仅校验调用者原有URL。发布JAR不包含`application-test.yml`，因此`run-e2e.ps1`还必须在启动子进程前，用刚构造的同一JDBC URL和`LAB_TEST_DB_USERNAME/PASSWORD`覆盖脚本进程的`LAB_DB_URL/USERNAME/PASSWORD`，让Java子进程继承这些运行时变量；禁止继承调用者原有`LAB_DB_*`。DROP/CREATE只使用`LAB_TEST_ADMIN_HOST/PORT/USERNAME/PASSWORD`。固定顺序是：重建空库 → 设置固定loopback后端端口和运行时数据源 → 启动后端并等待Flyway完成13条迁移和健康检查 → 使用应用数据库账号导入`scripts/e2e/seed-e2e.sql` → 调用只读探针核对种子数量 → 启动前端 → 执行Playwright。禁止在Flyway建表前导入种子。系统不得新增任何`/lab/demo/reset`或生产可见测试接口，E2E账号密码只从`LAB_E2E_*`环境变量读取。

`run-e2e.ps1`启动前先确认后端和前端目标端口未被占用，若已占用则退出，禁止按端口结束现有进程。脚本用`Start-Process -PassThru`直接启动发布JAR的`java`进程和Vite CLI的`node`进程，分别保存`$backendProcess`与`$frontendProcess`；所有健康检查、种子和Playwright步骤放在`try`中，`finally`只对这两个非空且尚未退出的进程句柄调用`Stop-Process -Id $process.Id`并`Wait-Process`。传入`-Cleanup`时，进程停止后只在本次重建标志为真且本机及固定库名再次校验通过时删除`lab_test_e2e`。启动失败和测试失败也必须执行同一`finally`，不得使用`taskkill`、按进程名批量结束或杀死调用者原有服务。

- [ ] **Step 4.3: 编写四套完整剧本**

- `normal-reservation.spec.js`：学生申请，管理员批准、领用、正常归还；
- `repair-loop.spec.js`：异常归还，维修人员处理，管理员驳回一次后通过；
- `inspection-hazard.spec.js`：巡检生成重大隐患，预约409，整改退回一次，销号后恢复；
- `authorization-responsive.spec.js`：五角色菜单和直接URL越权，375px学生申请与消息入口。

每个状态操作后同时断言页面标签和详情API状态，不能只断言成功提示。

- [ ] **Step 4.4: 运行端到端测试**

    powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\run-e2e.ps1

预期：四个桌面浏览器矩阵中的四套剧本及移动端核心剧本全部PASS；失败时保留trace和截图，成功发布不提交二进制trace；`browser-matrix.md`包含五个项目的实际版本和通过数。

- [ ] **Step 4.5: 提交E2E门禁**

四浏览器矩阵和375px剧本全部通过后，把`NFR-USA-001`与`NFR-COMP-001`更新为`PASS`并先运行普通追踪门禁。

    powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-traceability.ps1
    git add ruoyi-ui scripts/e2e scripts/run-e2e.ps1 docs/testing/evidence/browser-matrix.md docs/testing/v1-traceability.csv
    git commit -m "test: cover v1 workflows with playwright"

## Task 5: 完成授权、非法排序、附件和敏感信息安全门禁

**Files:**

- Create: `ruoyi-admin/src/test/java/com/ruoyi/integration/security/LabAuthorizationMatrixIT.java`
- Modify: `ruoyi-lab/src/test/java/com/ruoyi/lab/service/LabSortWhitelistTest.java`
- Create: `ruoyi-admin/src/test/java/com/ruoyi/integration/security/LabAttachmentSecurityIT.java`
- Create: `ruoyi-admin/src/test/java/com/ruoyi/integration/security/SensitiveLogIT.java`
- Create: `ruoyi-admin/src/test/java/com/ruoyi/integration/openapi/LabOpenApiContractIT.java`
- Modify: `ruoyi-admin/src/test/java/com/ruoyi/integration/web/openapi/LabOpenApiProdIT.java`
- Modify: `ruoyi-lab/src/main/java/com/ruoyi/lab/service/LabSortWhitelist.java`
- Create: `scripts/scan-secrets.ps1`
- Create: `docs/testing/evidence/security-report.md`
- Create: `docs/testing/evidence/secret-scan.txt`
- Modify: `docs/testing/v1-traceability.csv`

- [ ] **Step 5.1: 写失败的排序白名单测试**

```java
@ParameterizedTest
@ValueSource(strings = {
    "create_time desc; drop table lab_device",
    "extractvalue(1,concat(0x7e,user()))",
    "asset_no/**/desc",
    "unknown_column"
})
void rejectsUnsafeSortFields(String input) {
    assertThatThrownBy(() -> whitelist.resolve("device", input, "asc"))
        .isInstanceOf(LabBusinessException.class);
}
```

- [ ] **Step 5.2: 实现枚举白名单并替换Lab列表排序入口**

复用计划01建立的 LabSortWhitelist，不创建第二套排序抽象。按资源扩展白名单，至少覆盖`createTime`、`updateTime`、`assetNo`、`startTime`、`deadline`，方向只允许`asc`或`desc`。Mapper XML只使用白名单返回的常量列名，禁止`${param.orderBy}`和前端原始字符串。

- [ ] **Step 5.3: 完成五角色对象矩阵测试**

逐项覆盖列表、详情、附件、状态命令、消息和统计。系统管理员没有业务角色时只能使用系统管理，不得执行实验室业务写操作。职责分离覆盖审批本人预约、验收本人维修和复查本人整改。

- [ ] **Step 5.4: 完成OpenAPI契约测试**

`LabOpenApiContractIT`使用`@ActiveProfiles("test")`和安全测试数据源，验证开发/测试边界：未登录读取`/v3/api-docs/lab`和Knife4j静态入口为200，但未携带JWT调用任一受保护`/lab/**`端点仍为401，缺少业务权限为403。它还要求Lab分组包含全部`/lab/**`端点、JWT Bearer安全方案、请求字段、响应模型、状态枚举以及400、401、403、404、409、500主要错误响应，并把文档操作ID与Spring MVC实际映射集合比较，缺失或重复都失败。生产关闭边界必须扩展计划00已有的`LabOpenApiProdIT`：同时激活`test,prod`，再以最高优先级测试属性强制覆盖运行时数据源为包装器构造的`LAB_TEST_DB_*`，从而真实加载`application-prod.yml`并验证`SwaggerConfig @Profile("!prod")`未创建、`springdoc.api-docs.enabled=false`、`springdoc.swagger-ui.enabled=false`和`knife4j.enable=false`全部生效。该测试不得读取任何生产连接配置。

- [ ] **Step 5.5: 完成附件攻击测试**

测试伪造扩展名、伪造MIME、超过10MB、第6个附件、`../`路径、绝对路径、他人附件ID和带脚本的SVG。JPG、JPEG、PNG和PDF使用文件特征校验；SVG始终拒绝。

- [ ] **Step 5.6: 创建敏感扫描脚本**

脚本扫描私钥头、JWT、数据库URL内密码、`password=`硬编码、真实手机号和身份证模式，同时排除`target`、`node_modules`和明确的测试假值。输出按统一`-EvidenceDirectory`契约写为`secret-scan.txt`，文件只保存“检查项、文件数、命中数0、Git提交”。

- [ ] **Step 5.7: 运行安全和接口契约门禁**

    powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\run-lab-tests.ps1 -DatabaseName lab_test_release_security -Tests 'LabAuthorizationMatrixIT,LabSortWhitelistTest,LabAttachmentSecurityIT,SensitiveLogIT,LabOpenApiContractIT,LabOpenApiProdIT'
    powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\scan-secrets.ps1

计划00创建的`run-lab-tests.ps1`必须校验本机管理host/port，重建精确的`lab_test_release_security`，覆盖`LAB_TEST_DB_URL`并显式启用测试Flyway；禁止直接执行继承调用者数据库URL的Maven安全IT。预期：全部测试PASS；扫描退出0；所有越权请求为403或隐藏对象存在性的404。

- [ ] **Step 5.8: 提交安全和接口契约收口**

测试与扫描全绿后，把`FR-SYS-002`、`FR-API-001`、`NFR-SEC-001～004`、`NFR-OBS-001`、`AT-02`、`AT-13`和`AT-15`逐行更新为`PASS`，运行普通追踪门禁后提交。

    powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-traceability.ps1
    git add ruoyi-admin ruoyi-lab ruoyi-framework scripts/scan-secrets.ps1 docs/testing/evidence docs/testing/v1-traceability.csv
    git commit -m "test: harden authorization sorting and attachments"

## Task 6: 建立性能数据和k6发布门禁

**Files:**

- Create: `ruoyi-admin/src/test/java/com/ruoyi/integration/performance/PerformanceDataSeederIT.java`
- Create: `scripts/performance/seed-performance.ps1`
- Create: `scripts/performance/browse-dashboard.js`
- Create: `scripts/performance/state-commands.js`
- Create: `scripts/performance/run-performance.ps1`
- Create: `docs/testing/evidence/performance-environment.md`
- Create: `docs/testing/evidence/k6-summary.json`
- Modify: `docs/testing/v1-traceability.csv`

- [ ] **Step 6.1: 创建只允许专用测试库的种子**

`seed-performance.ps1`要求数据库名匹配`^lab_test_perf_[a-z0-9_]+$`，否则退出1。脚本每次先校验本机管理host/port，通过`reset-test-db.ps1`重建传入库，再按同一host/port和该库名构造并覆盖`LAB_TEST_DB_URL`，解析回读JDBC URL并断言schema与入参完全一致；随后显式设置`LAB_TEST_FLYWAY_ENABLED=true`并确认13条迁移全部成功、当前版本为V6_1，才运行`PerformanceDataSeederIT`，禁止在空库直接播种或信任调用者遗留URL。测试批量写入100个实验室、5000台设备、99500条终态历史预约，并额外准备500条互不冲突的APPROVED状态命令数据，使`lab_reservation`总数严格为100000；结束时断言精确数量并写出`target/performance/state-ids.json`。禁止连接开发、演示或生产库。

- [ ] **Step 6.2: 先运行种子数量测试**

    powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\performance\seed-performance.ps1 -DatabaseName lab_test_perf_v1

预期：输出`laboratories=100 devices=5000 reservations=100000 stateCommands=500`。

- [ ] **Step 6.3: 创建浏览与工作台k6脚本**

```javascript
import http from 'k6/http'
import { check } from 'k6'
import { Trend, Rate } from 'k6/metrics'

const listDuration = new Trend('lab_list_duration', true)
const dashboardDuration = new Trend('lab_dashboard_duration', true)
const failures = new Rate('lab_request_failures')

export const options = {
  scenarios: {
    browse: { executor: 'constant-vus', vus: 50, duration: '2m' }
  },
  thresholds: {
    lab_list_duration: ['p(95)<2000'],
    lab_dashboard_duration: ['p(95)<5000'],
    lab_request_failures: ['rate<0.01']
  }
}

export function setup() {
  const response = http.post(`${__ENV.LAB_BASE_URL}/login`, JSON.stringify({
    username: __ENV.LAB_PERF_USERNAME,
    password: __ENV.LAB_PERF_PASSWORD
  }), { headers: { 'Content-Type': 'application/json' } })
  check(response, { 'login succeeds': r => r.status === 200 && r.json('token') })
  return { token: response.json('token') }
}

export default function (data) {
  const params = { headers: { Authorization: `Bearer ${data.token}` } }
  const list = http.get(`${__ENV.LAB_BASE_URL}/lab/devices?pageNum=1&pageSize=20`, params)
  listDuration.add(list.timings.duration)
  failures.add(list.status !== 200)
  check(list, { 'device list succeeds': r => r.status === 200 })

  const dashboard = http.get(`${__ENV.LAB_BASE_URL}/lab/dashboard/summary`, params)
  dashboardDuration.add(dashboard.timings.duration)
  failures.add(dashboard.status !== 200)
  check(dashboard, { 'dashboard succeeds': r => r.status === 200 })
}
```

- [ ] **Step 6.4: 创建状态命令脚本**

`state-commands.js`使用`shared-iterations`、`vus: 50`、`iterations: 500`，从`state-ids.json`按全局迭代号领取不同APPROVED预约，调用领用命令并记录`lab_state_command_duration`，阈值为`p(95)<3000`、失败率低于1%。每个ID严格使用一次，不能用409冲突伪造性能通过。`run-performance.ps1`每次运行必须先调用上述种子脚本重建并重新播种目标库，不能复用已消费的500个ID；摘要按统一`-EvidenceDirectory`契约输出。

`run-performance.ps1`不得接受任意远程`LAB_BASE_URL`或依赖预先运行的服务。它固定选择一个未占用的loopback端口，端口已占用时立即失败且不得结束占用者；播种完成后按已验证的管理host/port和目标库构造运行时`LAB_DB_URL`，以`LAB_TEST_DB_USERNAME/PASSWORD`覆盖子进程数据库账号，并把`LAB_BASE_URL`固定为本次loopback地址。脚本用`Start-Process -PassThru`启动当前发布JAR，等待Flyway当前版本V6_1和HTTP健康探针，再依次运行两个k6脚本。启动、检查、k6和证据写入全部位于`try`中，`finally`先停止并等待保存句柄对应的本次Java子进程；传`-Cleanup`时再只删除本次成功重建且名称、主机复核通过的性能库。失败也不得遗留进程或本次要求清理的数据库，日志和摘要不得包含连接串、密码或Token。

- [ ] **Step 6.5: 运行基准并保存摘要**

    powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\performance\run-performance.ps1 -DatabaseName lab_test_perf_v1

预期：k6退出0；三个P95和失败率阈值全部通过；JSON摘要不包含账号、密码、Token和绝对路径。

- [ ] **Step 6.6: 提交性能门禁**

性能阈值全绿后，把`NFR-PERF-001`更新为`PASS`并先运行普通追踪门禁。

    powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-traceability.ps1
    git add ruoyi-admin/src/test scripts/performance docs/testing/evidence docs/testing/v1-traceability.csv
    git commit -m "test: add reproducible performance release gate"

## Task 7: 复测数据库并发和Redis故障边界

**Files:**

- Create: `ruoyi-admin/src/test/java/com/ruoyi/integration/concurrency/ReservationReleaseConcurrencyIT.java`
- Create: `ruoyi-admin/src/test/java/com/ruoyi/integration/concurrency/ReservationRedisFailureIT.java`
- Create: `ruoyi-admin/src/test/java/com/ruoyi/integration/concurrency/OpenRepairConcurrencyIT.java`
- Create: `scripts/verify-concurrency-faults.ps1`
- Create: `docs/testing/evidence/concurrency-fault-report.md`
- Modify: `docs/testing/v1-traceability.csv`

- [ ] **Step 7.1: 写20线程发布复测**

使用`CountDownLatch`同时释放20个独立事务，预约同一设备同一区间。断言一个成功、十九个409、有效预约查询为1、状态历史为1。重复执行测试10轮。

- [ ] **Step 7.2: 写Redis故障测试**

让计划02定义的`LabIdempotencyStore`抛出`RedisConnectionFailureException`，随后提交两个相同时段预约。断言首个请求仍经MySQL设备行锁成功，第二个请求返回409，数据库只有一条有效预约。日志包含安全错误码但不包含Redis密码和请求体。

- [ ] **Step 7.3: 写开放维修单并发测试**

20线程同时对同一设备报修，断言最多一张未关闭工单；其余请求返回原工单或409，设备最终为FAULT或MAINTENANCE。

- [ ] **Step 7.4: 运行故障与并发门禁**

`verify-concurrency-faults.ps1`复用计划05的安全数据库预检规则，但使用专用固定库`lab_test_release_concurrency`：校验本机管理host/port，调用`reset-test-db.ps1`重建该库，按同一host/port构造并覆盖`LAB_TEST_DB_URL`，显式设置`LAB_TEST_FLYWAY_ENABLED=true`，再运行三个定向测试并按统一`-EvidenceDirectory`契约生成`concurrency-fault-report.md`。脚本不得继承调用者原有数据库URL。

    powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-concurrency-faults.ps1

预期：连续10轮全部PASS；无重复有效预约、使用记录或开放维修单。

- [ ] **Step 7.5: 提交故障验证**

十轮并发与Redis故障测试全绿后，把`NFR-CONC-001`更新为`PASS`并先运行普通追踪门禁。

    powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-traceability.ps1
    git add ruoyi-admin/src/test scripts/verify-concurrency-faults.ps1 docs/testing/evidence/concurrency-fault-report.md docs/testing/v1-traceability.csv
    git commit -m "test: revalidate database correctness without redis"

## Task 8: 验证空库安装和上一里程碑升级

**Files:**

- Create: `scripts/verify-fresh-install.ps1`
- Create: `scripts/verify-upgrade.ps1`
- Create: `scripts/seed-m5-upgrade.ps1`
- Create: `docs/testing/evidence/fresh-install-report.md`
- Create: `docs/testing/evidence/m5-upgrade-report.md`
- Modify: `scripts/verify-migrations.ps1`
- Modify: `docs/testing/v1-traceability.csv`

- [ ] **Step 8.1: 加强迁移静态门禁**

断言迁移版本严格为V1_0、V1_1、V1_2、V2_0、V2_1、V3_0、V3_1、V4_0、V4_1、V5_0、V5_1、V6_0、V6_1；计划06不得增加V7或修改已执行文件。保存每个迁移SHA-256到报告。

- [ ] **Step 8.2: 空库安装到唯一新数据库**

`verify-fresh-install.ps1`生成`lab_test_fresh_<guid>`名称并验证`^lab_test_[a-z0-9_]+$`后创建数据库，以非生产`acceptance`配置启动发布候选。脚本必须先确认固定loopback端口未占用，用已验证的管理host/port与应用测试账号覆盖子进程`LAB_DB_URL/USERNAME/PASSWORD`，保存`Start-Process -PassThru`返回的Java句柄，等待健康检查，再确认13条Flyway成功记录、五角色种子、业务菜单和V6任务；该配置允许读取Knife4j文档但业务接口仍需JWT。进程停止必须位于`finally`并只使用保存句柄。报告按统一`-EvidenceDirectory`契约输出。脚本默认保留数据库供人工审计；发布门禁与`-VerifyOnly`传`-Cleanup`时，`finally`还必须只删除本次成功创建且名称精确校验的数据库，即使启动或验收中途失败也不能泄漏资源。

    powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-fresh-install.ps1

预期：脚本退出0；最新迁移为V6_1；登录、Knife4j和基础查询成功。

- [ ] **Step 8.3: 验证M5数据库升级**

`verify-upgrade.ps1`不假设Git标签中包含数据库备份。脚本先验证`milestone/m5-integrated`可解析到提交，再在任务专属临时目录创建只读Git worktree；用该标签代码迁移新的`lab_test_upgrade_source_<guid>`数据库，执行`scripts/seed-m5-upgrade.ps1`写入覆盖15张表和各开放状态的固定M5数据，记录行数与状态摘要并用`mysqldump --single-transaction`导出。随后把dump恢复到新的`lab_test_upgrade_target_<guid>`，再用当前发布候选执行Flyway validate和启动检查。M5代码与当前候选若需启动，都必须各用已预检的独立loopback端口、覆盖到对应测试库的`LAB_DB_*`，并保存各自`Start-Process -PassThru`句柄。全部逻辑置于`try/finally`：先只按句柄停止仍在运行的本次Java进程，再用`git worktree list`核对精确路径后执行`git worktree remove --force`移除本次临时worktree；发布门禁与`-VerifyOnly`传`-Cleanup`时，最后删除本次创建且名称校验通过的源库和目标库，即使迁移、导出、恢复或启动失败也执行清理。默认只保留两个数据库供人工审计，不保留临时worktree或进程。报告按统一`-EvidenceDirectory`契约输出。

    powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-upgrade.ps1 -BaselineTag milestone/m5-integrated

预期：退出0；M5标签数据库可导出并恢复；当前候选Flyway validate成功；15张表的行数、开放状态摘要和抽样附件哈希前后完全一致。

- [ ] **Step 8.4: 提交安装升级证据**

空库与M5升级均通过后，把`AT-01`更新为`PASS`并先运行普通追踪门禁。

    powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-traceability.ps1
    git add scripts/verify-fresh-install.ps1 scripts/verify-upgrade.ps1 scripts/seed-m5-upgrade.ps1 scripts/verify-migrations.ps1 docs/testing/evidence docs/testing/v1-traceability.csv
    git commit -m "test: verify fresh install and m5 upgrade"

## Task 9: 完成备份恢复、交付文档和发布标签

**Files:**

- Create: `scripts/backup-lab.ps1`
- Create: `scripts/restore-lab.ps1`
- Create: `scripts/verify-release.ps1`
- Create: `scripts/verify-docs.ps1`
- Create: `scripts/export-database-dictionary.ps1`
- Create: `scripts/export-openapi.ps1`
- Create: `docs/database/lab-dictionary.md`
- Create: `docs/api/openapi-lab.json`
- Create: `docs/deployment/deployment-guide.md`
- Create: `docs/user/user-guide.md`
- Create: `docs/troubleshooting/troubleshooting-guide.md`
- Create: `docs/testing/v1-test-report.md`
- Create: `docs/demo/v1-demo-script.md`
- Create: `docs/thesis/defense-material-index.md`
- Create: `docs/release/v1.0.0-checklist.md`
- Create: `docs/testing/evidence/restore-manifest.json`
- Modify: `docs/README.md`
- Modify: `docs/testing/v1-traceability.csv`

- [ ] **Step 9.1: 创建安全备份脚本**

`backup-lab.ps1`验证输出目录不是工作区根、用户目录或磁盘根；为本次调用创建唯一临时目录和MySQL客户端配置，先把ACL收紧为仅当前用户可读，再用`--defaults-extra-file`调用`mysqldump --single-transaction --routines --triggers`并归档附件目录。配置文件和临时目录路径只保存在内存变量中，所有备份逻辑放在`try`，`finally`用已解析的精确`-LiteralPath`删除本次文件和目录；删除失败必须让门禁失败，日志不得打印配置内容、密码或带凭据连接串。清单记录数据库名、UTC时间、Git提交、SQL和附件归档SHA-256、核心表行数，不记录凭据。

- [ ] **Step 9.2: 创建只恢复到新目标的脚本**

`restore-lab.ps1`的验收模式要求目标数据库匹配`^lab_test_restore_[a-z0-9_]+$`且不存在，附件目标目录必须为空且位于显式参数路径。脚本不覆盖现有数据库或文件目录；恢复后重新计算SHA-256和核心行数，并按统一`-EvidenceDirectory`契约写出`restore-manifest.json`。脚本分别记录“目标数据库由本次成功创建”和“附件目录由本次成功创建”标志；传`-Cleanup`时在`finally`中只删除标志为真的精确目标，即使恢复或核验失败也执行，任何预先存在或名称不匹配的目标都拒绝处理。清理本身失败必须使验收失败。

- [ ] **Step 9.3: 执行一次完整恢复验收**

    $runId = [guid]::NewGuid().ToString('N')
    $runRoot = Join-Path (Resolve-Path '.').Path "target\backup-restore\$runId"
    $backupDirectory = Join-Path $runRoot 'backup'
    $attachmentDirectory = Join-Path $runRoot 'attachments'
    $restoreDatabase = "lab_test_restore_$runId"
    powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\backup-lab.ps1 -DatabaseName lab_test_e2e -OutputDirectory $backupDirectory
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\restore-lab.ps1 -BackupDirectory $backupDirectory -DatabaseName $restoreDatabase -AttachmentDirectory $attachmentDirectory -EvidenceDirectory .\docs\testing\evidence -Cleanup
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

预期：两个脚本退出0；实验室、设备、预约、使用、维修、巡检、隐患、整改、通知、附件和状态历史行数一致；抽样附件SHA-256一致。

- [ ] **Step 9.4: 先写失败的文档一致性门禁**

`verify-docs.ps1`读取`docs/README.md`列出的文档清单，要求需求、设计、数据库字典、OpenAPI导出、部署、用户、测试和故障排查文档存在；校验Markdown相对链接、唯一H1、成对代码围栏、迁移版本、15张业务表、五角色键和V1版本号，并用`(TO)(DO)`、`(TB)(D)`、`待.{0}补充`扫描占位标记及未解析模板变量。`export-database-dictionary.ps1`从已迁移测试库的`information_schema`确定性生成15张业务表的列、类型、空值、默认值、主外键和索引；`export-openapi.ps1`从测试配置的`/v3/api-docs/lab`导出并规范化JSON，删除服务器地址和机器相关字段。先运行文档门禁并确认因本任务交付文档尚不存在而失败。

    powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-docs.ps1

预期：FAIL，明确列出尚未创建的交付文档，不得只返回通用错误。

- [ ] **Step 9.5: 编写六类交付文档、生成两项机器文档并通过门禁**

- 部署手册：环境变量、MySQL、Redis、文件目录、Nginx、JAR、启动停止、健康检查和回滚；
- 用户手册：五类角色入口和两条主闭环；
- 故障排查：数据库、Redis、Flyway、文件权限、401、403、409、Quartz和日志traceId；
- 测试报告：80项追踪、覆盖率、E2E、安全、性能、故障、安装和恢复结论；
- 演示脚本：正常预约、维修退回、安全整改退回三段，包含数据重置和预计时长；
- 数据库字典与OpenAPI：先运行两个导出脚本，再核对15张表、全部`/lab/**`操作、Bearer安全方案和主要错误响应；
- 答辩材料索引：架构图、ER图、状态图、时序图、并发证据、截图和创新点对应文件。

`export-database-dictionary.ps1`只允许本机管理host和精确`lab_test_e2e`，忽略调用者遗留URL并按已验证host/port构造应用连接；导出前断言Flyway为V6_1且15张业务表齐全。`export-openapi.ps1`不得访问调用者预先运行的URL：它复用同一安全库，覆盖发布JAR子进程的`LAB_DB_URL/USERNAME/PASSWORD`，预检固定loopback端口，保存`Start-Process -PassThru`句柄，等待V6_1与HTTP健康后读取`/v3/api-docs/lab`；规范化与写文件位于`try`，`finally`只停止并等待该句柄。两个导出器都按统一`-EvidenceDirectory`边界写临时证据或批准的机器文档，不输出连接串和Token。

    powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\export-database-dictionary.ps1 -DatabaseName lab_test_e2e
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\export-openapi.ps1
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-docs.ps1
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

预期：输出`Documentation verified`并退出0，清单中的每个相对链接都能解析到仓库内真实文件。

- [ ] **Step 9.6: 创建统一发布门禁**

`verify-release.ps1`支持证据生成模式和`-VerifyOnly`模式，任一步失败立即退出非0。

生成模式按顺序执行：迁移静态检查、文档一致性检查、普通追踪检查、计划05的安全`scripts/verify.ps1`、后端覆盖率、Yarn冻结安装、前端覆盖率、生产构建、Playwright、安全扫描、20线程并发、k6、空库安装、M5升级和备份恢复。它把证据写入`docs/testing/evidence`。唯一临时库的子脚本直接传`-Cleanup`；`lab_test_e2e`需供后续备份恢复消费，统一门禁先不让E2E子脚本提前删除，而是把该固定库和性能固定库登记到自身外层`finally`，在所有消费者完成或任一步失败后精确清理。只有全部成功且对应文件存在后，才把`NFR-DOC-001`、`NFR-BACKUP-001`、`AT-14`和`AT-16`四行从`PLANNED`改为`PASS`，再调用`verify-docs.ps1`和`verify-traceability.ps1 -RequireEvidence`。

`-VerifyOnly`首先要求`git status --porcelain=v1 --untracked-files=all`为空，创建唯一`target/release-verify/<guid>`作为`-EvidenceDirectory`，然后重复迁移、文档、追踪、后端、前端、E2E、安全、并发、性能、空库、升级和备份恢复门禁；唯一临时数据库步骤传`-Cleanup`，E2E和性能每次先安全重建固定测试库并由统一门禁外层`finally`在最后一个消费者结束后清理。该模式不运行数据库字典或OpenAPI导出、不更新CSV、不写`docs/**`，只校验已经提交的机器文档和PASS证据；结束时再次要求同一git状态为空。任何脚本不支持临时证据目录、清理失败或试图修改tracked文件时VerifyOnly立即失败。

    powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-release.ps1

预期：脚本退出0并生成`docs/testing/v1-test-report.md`引用的全部文本或JSON证据。

- [ ] **Step 9.7: 执行最终静态自检**

    git diff --check
    rg -n "password\s*[:=]\s*[^$<{]|BEGIN (RSA|OPENSSH) PRIVATE KEY|Bearer eyJ" . -g '!target/**' -g '!ruoyi-ui/node_modules/**' -g '!docs/superpowers/plans/**'
    git status --short

预期：空白检查通过；计划外占位词和真实敏感信息命中数为0；状态只包含本任务文档、脚本、测试和已批准的最小缺陷修正。

- [ ] **Step 9.8: 生成证据并提交发布材料**

    powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-release.ps1
    powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-traceability.ps1 -RequireEvidence
    git add scripts docs ruoyi-admin/src/test ruoyi-lab/src/test ruoyi-ui/tests
    git commit -m "docs: finalize v1 release and defense materials"

- [ ] **Step 9.9: 创建候选和正式标签**

    git status --short
    git tag -a v1.0.0-rc1 -m "Laboratory management V1 release candidate"
    powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-release.ps1 -VerifyOnly
    git status --short
    git tag -a v1.0.0 -m "Laboratory management V1"
    git show --stat --oneline v1.0.0

预期：创建rc1前工作区为空；VerifyOnly完整门禁退出0且执行后工作区仍为空；`v1.0.0`指向同一已验证发布提交。计划不自动推送分支或标签。

## V1 最终发布检查点

    powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-release.ps1 -VerifyOnly
    git status --short
    git tag --points-at HEAD

预期：验证脚本退出0；工作区无输出；HEAD同时具有`v1.0.0-rc1`和`v1.0.0`标签；`docs/testing/v1-traceability.csv`包含80个唯一需求及验收ID。
