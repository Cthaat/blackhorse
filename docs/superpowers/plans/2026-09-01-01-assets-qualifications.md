# Laboratory Assets and Qualifications Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 交付实验室、设备、资格、受控附件和业务状态历史的可运行纵向切片，使学生只能看到资格覆盖的资产，管理人员只能操作数据范围内对象，并为预约阶段提供稳定的设备与资格校验契约。

**Architecture:** Controller 固定放在 ruoyi-admin 的 lab 包，只做鉴权、参数绑定和响应转换；实体、Mapper、XML、Service、对象级授权、LocalStorageService 与状态规则全部集中在 ruoyi-lab。MySQL 保存资产、资格、附件元数据和状态历史，文件保存到 Web 根目录之外的本地目录；列表、详情、下载和命令共享同一对象权限判定。

**Tech Stack:** Java 17、Spring Boot 3.5.16、RuoYi-Vue 3.9.2、MyBatis-Plus 3.5.17、PageHelper 2.1.1、MySQL 8、Redis 7、Flyway 11.7.2、Springdoc 2.9.0、Knife4j 4.5.0、Vue 3.5.26、Vite 6.4.1、Element Plus 2.13.1、Pinia 3.0.4、Vitest 3.2.4、Node 22、Yarn 1.22.22。

---

## 需求与退出门禁

本计划覆盖 FR-SYS-003、FR-SYS-005、FR-AST-001 至 FR-AST-005、FR-QUA-001 至 FR-QUA-003；完整交付 AT-03，交付 AT-06 的停用实验室、故障设备和过期资格部分，交付 AT-15 的非法附件、越权下载和非法排序部分。

本计划结束时必须满足：

- V1_2 数据库可无损升级到 V2_0、V2_1，全新空库也能一次迁移成功；
- 实验室编码、设备资产编号唯一，普通编辑 DTO 不包含 status；
- 设备状态仅能通过专用命令按状态机变化，非法跳转返回 HTTP 409 且原数据不变；
- 学生资产列表只返回本人有效资格覆盖的实验室或设备类别，学生资格接口只返回本人记录；
- 管理员的列表、详情、状态命令、附件上传和下载都受同一部门数据范围与对象归属约束；
- 单个附件不超过 10 MiB，每个业务对象最多 5 个，只接受 JPG、JPEG、PNG、PDF，扩展名、声明 MIME 和文件签名必须一致；
- 文件使用随机存储名并位于 Web 根目录之外，任何路径片段都不能直接参与磁盘路径拼接；
- 关键状态变化同时写入操作日志与 lab_status_history，含对象、前后状态、操作人、原因、时间和 traceId；
- 后端测试、前端单元测试、Maven 打包和前端生产构建全部通过。

## Task 1: 用 V2_0 建立五张业务表

**Files:**

- Create: ruoyi-admin/src/main/resources/db/migration/V2_0__lab_assets_qualifications.sql
- Create: ruoyi-admin/src/test/java/com/ruoyi/integration/lab/migration/LabAssetSchemaMigrationIT.java
- Test: ruoyi-admin/src/test/java/com/ruoyi/integration/lab/migration/LabAssetSchemaMigrationIT.java

- [ ] 1.1 先写迁移集成测试，复用计划 00 已有的 `application-test.yml`：通过 LAB_TEST_DB_URL、LAB_TEST_DB_USERNAME、LAB_TEST_DB_PASSWORD 连接独立 MySQL 8 测试库，并由 `${LAB_TEST_FLYWAY_ENABLED:false}` 保证普通测试默认关闭 Flyway、迁移与阶段门禁显式开启。从仅包含 V1_0、V1_1、V1_2 的状态启动 Flyway，再断言五张表、唯一约束、外键和关键索引存在。该测试放在 ruoyi-admin，ruoyi-lab 不增加启动配置或反向依赖。

    @Test
    void migratesAssetsAndQualificationsFromMilestoneOne() {
        flyway.migrate();
        assertThat(tableNames()).contains(
            "lab_laboratory", "lab_device", "lab_qualification",
            "lab_attachment", "lab_status_history");
        assertThat(indexNames("lab_laboratory")).contains("uk_lab_laboratory_code");
        assertThat(indexNames("lab_device")).contains("uk_lab_device_asset_no", "idx_lab_device_query");
        assertThat(indexNames("lab_qualification")).contains("idx_lab_qualification_user_validity");
        assertThat(indexNames("lab_attachment")).contains("idx_lab_attachment_object");
    }

- [ ] 1.2 重建专用库，覆盖调用者遗留URL并显式开启测试Flyway，运行单测确认红灯来自 V2_0 尚不存在。

    powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\run-lab-tests.ps1 `
      -DatabaseName lab_test_m2_task -Tests 'LabAssetSchemaMigrationIT'

预期：BUILD FAILURE，失败信息明确指出五张业务表不存在；不得出现连接失败或测试未发现。

- [ ] 1.3 复用计划00已创建并经过负向验证的`scripts/reset-test-db.ps1`；用非法数据库名、远程管理host和越界端口各运行一次，必须在调用mysql前拒绝。后续所有迁移测试只向该脚本传入精确`lab_test_*`名称，不复制第二套重置逻辑。

- [ ] 1.4 创建 V2_0，完整表结构如下；sys_user、sys_dept 只保存逻辑引用，业务表之间使用外键，服务端、数据库会话和测试统一使用 Asia/Shanghai，DATETIME 按该时区解释。

    CREATE TABLE lab_laboratory (
        id BIGINT NOT NULL AUTO_INCREMENT,
        lab_code VARCHAR(32) NOT NULL,
        name VARCHAR(100) NOT NULL,
        dept_id BIGINT NOT NULL,
        manager_id BIGINT NOT NULL,
        location VARCHAR(200) NOT NULL,
        description VARCHAR(500) NULL,
        status VARCHAR(20) NOT NULL DEFAULT 'ENABLED',
        version INT NOT NULL DEFAULT 0,
        create_by VARCHAR(64) NOT NULL DEFAULT '',
        create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
        update_by VARCHAR(64) NOT NULL DEFAULT '',
        update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
        del_flag CHAR(1) NOT NULL DEFAULT '0',
        PRIMARY KEY (id),
        UNIQUE KEY uk_lab_laboratory_code (lab_code),
        KEY idx_lab_laboratory_scope (dept_id, status, del_flag)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

    CREATE TABLE lab_device (
        id BIGINT NOT NULL AUTO_INCREMENT,
        asset_no VARCHAR(64) NOT NULL,
        laboratory_id BIGINT NOT NULL,
        name VARCHAR(100) NOT NULL,
        category_code VARCHAR(32) NOT NULL,
        model VARCHAR(100) NULL,
        risk_level VARCHAR(20) NOT NULL,
        location VARCHAR(200) NOT NULL,
        manager_id BIGINT NOT NULL,
        description VARCHAR(1000) NULL,
        status VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE',
        version INT NOT NULL DEFAULT 0,
        create_by VARCHAR(64) NOT NULL DEFAULT '',
        create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
        update_by VARCHAR(64) NOT NULL DEFAULT '',
        update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
        del_flag CHAR(1) NOT NULL DEFAULT '0',
        PRIMARY KEY (id),
        UNIQUE KEY uk_lab_device_asset_no (asset_no),
        KEY idx_lab_device_query (laboratory_id, category_code, status, del_flag),
        CONSTRAINT fk_lab_device_laboratory FOREIGN KEY (laboratory_id) REFERENCES lab_laboratory(id)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

    CREATE TABLE lab_qualification (
        id BIGINT NOT NULL AUTO_INCREMENT,
        user_id BIGINT NOT NULL,
        scope_type VARCHAR(20) NOT NULL,
        scope_id VARCHAR(64) NOT NULL,
        valid_from DATETIME(3) NOT NULL,
        valid_until DATETIME(3) NOT NULL,
        revoked_at DATETIME(3) NULL,
        revoke_reason VARCHAR(500) NULL,
        version INT NOT NULL DEFAULT 0,
        create_by VARCHAR(64) NOT NULL DEFAULT '',
        create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
        update_by VARCHAR(64) NOT NULL DEFAULT '',
        update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
        del_flag CHAR(1) NOT NULL DEFAULT '0',
        PRIMARY KEY (id),
        KEY idx_lab_qualification_user_validity (user_id, valid_from, valid_until, revoked_at, del_flag),
        KEY idx_lab_qualification_scope (scope_type, scope_id, valid_from, valid_until, revoked_at)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

    CREATE TABLE lab_attachment (
        id BIGINT NOT NULL AUTO_INCREMENT,
        business_type VARCHAR(32) NOT NULL,
        business_id BIGINT NOT NULL,
        original_name VARCHAR(255) NOT NULL,
        stored_name VARCHAR(80) NOT NULL,
        mime_type VARCHAR(100) NOT NULL,
        size BIGINT NOT NULL,
        storage_key VARCHAR(255) NOT NULL,
        sha256 CHAR(64) NOT NULL,
        create_by VARCHAR(64) NOT NULL DEFAULT '',
        create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
        del_flag CHAR(1) NOT NULL DEFAULT '0',
        PRIMARY KEY (id),
        UNIQUE KEY uk_lab_attachment_storage_key (storage_key),
        KEY idx_lab_attachment_object (business_type, business_id, del_flag)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

    CREATE TABLE lab_status_history (
        id BIGINT NOT NULL AUTO_INCREMENT,
        object_type VARCHAR(32) NOT NULL,
        object_id BIGINT NOT NULL,
        from_status VARCHAR(32) NULL,
        to_status VARCHAR(32) NOT NULL,
        operator_id BIGINT NOT NULL,
        reason VARCHAR(500) NOT NULL,
        trace_id VARCHAR(64) NOT NULL,
        create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
        del_flag CHAR(1) NOT NULL DEFAULT '0',
        PRIMARY KEY (id),
        KEY idx_lab_status_history_object (object_type, object_id, create_time)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

- [ ] 1.5 在迁移测试中增加 `valid_until > valid_from`、唯一编码和外键失败用例；日期顺序由 Service 与数据库测试共同保证，因为 MySQL 版本兼容基线不依赖 CHECK 约束。

- [ ] 1.6 重置专用测试库，显式开启测试Flyway，运行迁移测试和 Flyway 静态检查。LabAssetSchemaMigrationIT 断言flyway_schema_history按V1_0、V1_1、V1_2、V2_0顺序成功且业务约束存在。

    powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\run-lab-tests.ps1 `
      -DatabaseName lab_test_m2_task -Tests 'LabAssetSchemaMigrationIT'
    powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-migrations.ps1

预期：测试全绿；Flyway 输出 V2_0 校验成功且无重复版本。

- [ ] 1.7 提交数据库切片。

    git add ruoyi-admin/src/main/resources/db/migration/V2_0__lab_assets_qualifications.sql ruoyi-admin/src/test/java/com/ruoyi/integration/lab/migration/LabAssetSchemaMigrationIT.java
    git commit -m "feat: add asset and qualification schema"

## Task 2: 建立资产领域模型、Mapper 与排序白名单

**Files:**

- Create: ruoyi-lab/src/main/java/com/ruoyi/lab/domain/LabLaboratory.java
- Create: ruoyi-lab/src/main/java/com/ruoyi/lab/domain/LabDevice.java
- Create: ruoyi-lab/src/main/java/com/ruoyi/lab/domain/LabQualification.java
- Create: ruoyi-lab/src/main/java/com/ruoyi/lab/domain/LabAttachment.java
- Create: ruoyi-lab/src/main/java/com/ruoyi/lab/domain/LabStatusHistory.java
- Create: ruoyi-lab/src/main/java/com/ruoyi/lab/domain/LaboratoryStatus.java
- Create: ruoyi-lab/src/main/java/com/ruoyi/lab/domain/DeviceStatus.java
- Create: ruoyi-lab/src/main/java/com/ruoyi/lab/domain/QualificationScopeType.java
- Create: ruoyi-lab/src/main/java/com/ruoyi/lab/mapper/LabLaboratoryMapper.java
- Create: ruoyi-lab/src/main/java/com/ruoyi/lab/mapper/LabDeviceMapper.java
- Create: ruoyi-lab/src/main/java/com/ruoyi/lab/mapper/LabQualificationMapper.java
- Create: ruoyi-lab/src/main/java/com/ruoyi/lab/mapper/LabAttachmentMapper.java
- Create: ruoyi-lab/src/main/java/com/ruoyi/lab/mapper/LabStatusHistoryMapper.java
- Create: ruoyi-lab/src/main/java/com/ruoyi/lab/mapper/LabDataScopeMapper.java
- Create: ruoyi-lab/src/main/resources/mapper/lab/LabLaboratoryMapper.xml
- Create: ruoyi-lab/src/main/resources/mapper/lab/LabDeviceMapper.xml
- Create: ruoyi-lab/src/main/resources/mapper/lab/LabQualificationMapper.xml
- Create: ruoyi-lab/src/main/resources/mapper/lab/LabDataScopeMapper.xml
- Create: ruoyi-lab/src/main/java/com/ruoyi/lab/service/LabSortWhitelist.java
- Create: ruoyi-lab/src/main/java/com/ruoyi/lab/security/LabDataScope.java
- Create: ruoyi-lab/src/main/java/com/ruoyi/lab/security/LabDataScopeService.java
- Create: ruoyi-lab/src/test/java/com/ruoyi/lab/service/LabSortWhitelistTest.java
- Create: ruoyi-admin/src/test/java/com/ruoyi/integration/lab/mapper/LabAssetMapperXmlIT.java
- Test: ruoyi-lab/src/test/java/com/ruoyi/lab/service/LabSortWhitelistTest.java
- Test: ruoyi-admin/src/test/java/com/ruoyi/integration/lab/mapper/LabAssetMapperXmlIT.java

- [ ] 2.1 先写排序白名单测试，覆盖 laboratory 的 name/labCode/createTime、device 的 assetNo/name/status/createTime、qualification 的 validUntil/createTime，并断言 `updatexml(1,1,1)`、带空格和未知字段返回 400 对应的 LAB 参数错误。

    @ParameterizedTest
    @ValueSource(strings = {"updatexml(1,1,1)", "name desc", "unknown", "create_time"})
    void rejectsUnmappedSortKey(String sortKey) {
        assertThatThrownBy(() -> whitelist.resolve("device", sortKey, "asc"))
            .isInstanceOf(LabBusinessException.class)
            .extracting("errorCode")
            .isEqualTo(LabErrorCode.VALIDATION_ERROR);
    }

- [ ] 2.2 运行测试确认红灯。

    mvn -pl ruoyi-lab -am -Dtest=LabSortWhitelistTest -Dsurefire.failIfNoSpecifiedTests=false test

预期：BUILD FAILURE，原因是 LabSortWhitelist 尚不存在。

- [ ] 2.3 实现不可由客户端直接提供 SQL 标识符的白名单；映射结果只允许 Mapper XML 中固定列名。

    public final class LabSortWhitelist {
        private static final Map<String, Map<String, String>> COLUMNS = Map.of(
            "laboratory", Map.of("name", "l.name", "labCode", "l.lab_code", "createTime", "l.create_time"),
            "device", Map.of("assetNo", "d.asset_no", "name", "d.name", "status", "d.status", "createTime", "d.create_time"),
            "qualification", Map.of("validUntil", "q.valid_until", "createTime", "q.create_time")
        );

        public SortClause resolve(String resource, String key, String direction) {
            String column = Optional.ofNullable(COLUMNS.get(resource))
                .map(columns -> columns.get(key))
                .orElseThrow(() -> new LabBusinessException(LabErrorCode.VALIDATION_ERROR, "非法排序字段"));
            String order = switch (direction.toLowerCase(Locale.ROOT)) {
                case "asc" -> "ASC";
                case "desc" -> "DESC";
                default -> throw new LabBusinessException(LabErrorCode.VALIDATION_ERROR, "非法排序方向");
            };
            return new SortClause(column, order);
        }

        public record SortClause(String column, String direction) {}
    }

- [ ] 2.4 为五张表建立显式实体和 Mapper。普通写入使用 BaseMapper；组合检索、学生资格覆盖查询和数据范围查询写在 XML 中。LabDataScopeMapper 只读查询 RuoYi 的角色数据范围与部门树，不导入 ruoyi-system Java 类型，保持计划 00 的模块依赖方向。`LabDeviceMapper` 名称作为后续预约、领用和维修计划的固定跨模块契约，并声明 `int updateStatusConditionally(Long deviceId, String expected, String target)`，不得改名或另建同义 Mapper。

- [ ] 2.5 Mapper XML 将 `SortClause.column` 与 `SortClause.direction` 作为已校验值插入 ORDER BY，其他过滤条件一律使用 `#{}`；所有查询固定带 `del_flag = '0'`。LabDataScope.empty() 在 Service 层直接返回空页，restricted() 才生成绑定参数的 laboratory_id IN 条件，禁止用空 IN 或缺少范围条件表示无权限。

- [ ] 2.6 运行测试和 MyBatis XML 装载测试。

    powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\run-lab-tests.ps1 `
      -DatabaseName lab_test_m2_task -Tests 'LabSortWhitelistTest,LabAssetMapperXmlIT'

预期：白名单用例全绿，所有 Mapper statement 可装载且参数占位正确。

- [ ] 2.7 提交领域持久化层。

    git add ruoyi-lab/src/main/java/com/ruoyi/lab/domain ruoyi-lab/src/main/java/com/ruoyi/lab/mapper ruoyi-lab/src/main/java/com/ruoyi/lab/service/LabSortWhitelist.java ruoyi-lab/src/main/java/com/ruoyi/lab/security/LabDataScope.java ruoyi-lab/src/main/java/com/ruoyi/lab/security/LabDataScopeService.java ruoyi-lab/src/main/resources/mapper/lab ruoyi-lab/src/test ruoyi-admin/src/test/java/com/ruoyi/integration/lab/mapper/LabAssetMapperXmlIT.java
    git commit -m "feat: add asset qualification persistence layer"

## Task 3: 实现实验室、设备与显式状态命令

**Files:**

- Create: ruoyi-lab/src/main/java/com/ruoyi/lab/dto/LaboratoryCreateDto.java
- Create: ruoyi-lab/src/main/java/com/ruoyi/lab/dto/LaboratoryUpdateDto.java
- Create: ruoyi-lab/src/main/java/com/ruoyi/lab/dto/DeviceCreateDto.java
- Create: ruoyi-lab/src/main/java/com/ruoyi/lab/dto/DeviceUpdateDto.java
- Create: ruoyi-lab/src/main/java/com/ruoyi/lab/dto/DeviceStatusCommandDto.java
- Create: ruoyi-lab/src/main/java/com/ruoyi/lab/vo/LaboratoryVo.java
- Create: ruoyi-lab/src/main/java/com/ruoyi/lab/vo/DeviceVo.java
- Create: ruoyi-lab/src/main/java/com/ruoyi/lab/service/LaboratoryService.java
- Create: ruoyi-lab/src/main/java/com/ruoyi/lab/service/DeviceService.java
- Create: ruoyi-lab/src/main/java/com/ruoyi/lab/service/DeviceStatusCommandService.java
- Create: ruoyi-lab/src/main/java/com/ruoyi/lab/service/impl/LaboratoryServiceImpl.java
- Create: ruoyi-lab/src/main/java/com/ruoyi/lab/service/impl/DeviceServiceImpl.java
- Create: ruoyi-lab/src/main/java/com/ruoyi/lab/service/impl/DeviceStatusCommandServiceImpl.java
- Modify: ruoyi-lab/src/main/java/com/ruoyi/lab/mapper/LabLaboratoryMapper.java
- Modify: ruoyi-lab/src/main/resources/mapper/lab/LabLaboratoryMapper.xml
- Create: ruoyi-lab/src/main/java/com/ruoyi/lab/security/LabDataScopeServiceImpl.java
- Create: ruoyi-lab/src/main/java/com/ruoyi/lab/security/LabObjectPermissionService.java
- Create: ruoyi-lab/src/main/java/com/ruoyi/lab/security/LabObjectPermissionServiceImpl.java
- Create: ruoyi-admin/src/main/java/com/ruoyi/web/controller/lab/LabLaboratoryController.java
- Create: ruoyi-admin/src/main/java/com/ruoyi/web/controller/lab/LabDeviceController.java
- Create: ruoyi-lab/src/test/java/com/ruoyi/lab/service/DeviceStateMachineTest.java
- Create: ruoyi-admin/src/test/java/com/ruoyi/integration/lab/security/LabObjectPermissionIT.java
- Create: ruoyi-admin/src/test/java/com/ruoyi/integration/lab/web/LabLaboratoryControllerTest.java
- Create: ruoyi-admin/src/test/java/com/ruoyi/integration/lab/web/LabDeviceControllerTest.java
- Test: ruoyi-lab/src/test/java/com/ruoyi/lab/service/DeviceStateMachineTest.java
- Test: ruoyi-admin/src/test/java/com/ruoyi/integration/lab/security/LabObjectPermissionIT.java
- Test: ruoyi-admin/src/test/java/com/ruoyi/integration/lab/web/LabLaboratoryControllerTest.java
- Test: ruoyi-admin/src/test/java/com/ruoyi/integration/lab/web/LabDeviceControllerTest.java

- [ ] 3.1 先写状态机测试，固定允许的资产阶段候选跳转；M2 只实现 AVAILABLE→FAULT、AVAILABLE→DISABLED、FAULT→DISABLED、DISABLED→AVAILABLE，拒绝 FAULT→AVAILABLE、相同状态、未知来源和普通更新改状态。AVAILABLE→DISABLED、FAULT→DISABLED 与 DISABLED→AVAILABLE 只是静态状态机允许的候选边，最终是否执行必须由 DeviceStatusCommandServiceImpl 在锁定设备后做动态守卫，不能只调用枚举的 `canMoveTo`。FAULT 只能在计划 03 的维修闭环中经内部 FAULT→MAINTENANCE→AVAILABLE 恢复，IN_USE 与 MAINTENANCE 路径由计划 03 实现。

    @Test
    void rejectsIllegalTransitionWithoutPersisting() {
        Long managerId = 2001L;
        givenDevice(1001L, DeviceStatus.DISABLED, 3);
        assertThatThrownBy(() -> deviceStatusCommandService.changeStatus(1001L,
            new DeviceStatusCommandDto(DeviceStatus.FAULT, "设备未启用不得直接报故障"),
            managerId))
            .isInstanceOf(LabBusinessException.class)
            .extracting("errorCode")
            .isEqualTo(LabErrorCode.LAB_ILLEGAL_STATE_TRANSITION);
        assertThat(reloadDevice(1001L).getStatus()).isEqualTo(DeviceStatus.DISABLED);
        assertThat(historyCount(1001L)).isZero();
    }

    @Test
    void disabledDeviceCannotBeEnabledWhileLaboratoryIsDisabled() {
        Long managerId = 2001L;
        givenLaboratory(7L, LaboratoryStatus.DISABLED);
        givenDevice(1002L, 7L, DeviceStatus.DISABLED, 2);

        assertThatThrownBy(() -> deviceStatusCommandService.changeStatus(1002L,
            new DeviceStatusCommandDto(DeviceStatus.AVAILABLE, "重新启用设备"),
            managerId))
            .isInstanceOf(LabBusinessException.class)
            .extracting("errorCode")
            .isEqualTo(LabErrorCode.LAB_LABORATORY_DISABLED);

        assertThat(reloadDevice(1002L).getStatus()).isEqualTo(DeviceStatus.DISABLED);
        assertThat(historyCount(1002L)).isZero();
    }

- [ ] 3.2 先写对象权限集成测试：同部门管理员可列表、详情、编辑、改状态；跨部门管理员对详情和命令得到 403；学生只能查询有效资格覆盖的设备；直接猜测 ID 不扩大范围。

- [ ] 3.3 运行两类测试确认红灯。

    powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\run-lab-tests.ps1 `
      -DatabaseName lab_test_m2_task -Tests 'DeviceStateMachineTest,LabObjectPermissionIT'

预期：BUILD FAILURE，失败由 Service、Controller 或授权实现缺失导致。

- [ ] 3.4 复用计划 00 已建立的 SRS 固定码 `LAB_DEVICE_UNAVAILABLE`、`LAB_LABORATORY_DISABLED`、`LAB_ILLEGAL_STATE_TRANSITION`、`LAB_OUT_OF_DATA_SCOPE`、`LAB_QUALIFICATION_INVALID`、`LAB_DUPLICATE_OPERATION`，再实现 LaboratoryService 和 DeviceService。DeviceService 只负责设备资料与查询，`DeviceStatusCommandService` 专门负责设备状态命令。该命令服务先按对象范围取得快照，再用 `LabDeviceMapper.selectByIdForUpdate` 锁定设备并重新校验；DISABLED→AVAILABLE 必须随后用 `LabLaboratoryMapper.selectByIdForUpdate` 做 locking/current read 并要求所属实验室状态为 ENABLED，否则返回 `LAB_LABORATORY_DISABLED`，设备和历史均不变化。M2 尚无使用、维修和隐患事实表，因此三项下游阻断在本里程碑中按“无记录”成立；计划 03 必须在同一服务路径、同一 device 锁之后加入“无未归还使用、无开放维修、无未关闭重大隐患”三项动态守卫，计划 04 再用真实隐患表验证，禁止另建可绕过的状态入口。创建、更新、详情与列表都先取得当前用户数据范围；停用实验室使用 `/lab/laboratories/{id}/commands/disable`，启用使用 `/commands/enable`；设备状态使用 `/lab/devices/{id}/commands/change-status` 并由 Controller 调用 DeviceStatusCommandService。普通更新 DTO 不声明 status、version、审计列或 delFlag。

    public interface DeviceStatusCommandService {
        void changeStatus(Long deviceId, DeviceStatusCommandDto command, Long actorId);
    }

    public interface LabLaboratoryMapper extends BaseMapper<LabLaboratory> {
        LabLaboratory selectByIdForUpdate(Long laboratoryId);
    }

    <select id="selectByIdForUpdate"
            resultType="com.ruoyi.lab.domain.LabLaboratory">
      select *
      from lab_laboratory
      where id = #{laboratoryId}
        and del_flag = '0'
      for update
    </select>

Controller 从当前登录上下文取得 actorId 后传入三参方法，禁止从请求体接受 actorId；DeviceStatusCommandServiceImpl 用 actorId 做对象权限、操作日志和 lab_status_history.operator_id。所有单元、集成与后续计划调用都使用该三参契约，不保留二参重载。

- [ ] 3.5 `LabDataScopeService.resolveCurrentScope()` 作为跨计划固定入口，返回 `LabDataScope(long userId, boolean allLaboratories, Set<Long> laboratoryIds)`；`LabObjectPermissionService` 基于该快照提供完整统一入口，Controller 不自行拼部门条件。

    public record LabDataScope(long userId, boolean allLaboratories, Set<Long> laboratoryIds) {
        public LabDataScope {
            laboratoryIds = Set.copyOf(Objects.requireNonNull(laboratoryIds, "laboratoryIds"));
        }

        public boolean restricted() {
            return !allLaboratories;
        }

        public boolean empty() {
            return restricted() && laboratoryIds.isEmpty();
        }
    }

    public interface LabDataScopeService {
        LabDataScope resolveCurrentScope();
    }

    public interface LabObjectPermissionService {
        void assertLaboratoryReadable(long laboratoryId);
        void assertLaboratoryManageable(long laboratoryId);
        void assertDeviceReadable(long deviceId);
        void assertDeviceManageable(long deviceId);
        Set<Long> readableDepartmentIds();
        long currentUserId();
    }

- [ ] 3.6 创建实验室和设备时分别写 `null→ENABLED`、`null→AVAILABLE` 初始历史；每个后续状态命令在同一事务中执行乐观更新并插入 LabStatusHistory。`reason` 去首尾空白后必须为 1 至 500 字，traceId 从计划 00 的 TraceIdFilter 上下文读取。Controller 增加 `@PreAuthorize`，Service 再做对象权限，方法加 RuoYi `@Log` 记录操作日志；纯资料修改只写操作日志，不伪造状态变化。

- [ ] 3.7 实现设备详情的占用时间查询契约 `/lab/devices/{id}/occupied-ranges?from=&to=`。M2 在预约表尚未创建时返回空数组；接口与 DTO 必须稳定，计划 02 将由 LabReservationMapper 提供 PENDING、APPROVED、CHECKED_OUT 时间段。

- [ ] 3.8 运行 Service、对象权限和 WebMvc 测试。

    powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\run-lab-tests.ps1 `
      -DatabaseName lab_test_m2_task `
      -Tests 'DeviceStateMachineTest,LabObjectPermissionIT,LabLaboratoryControllerTest,LabDeviceControllerTest'

预期：所有测试通过；非法跳转与实验室停用时的设备启用均为 409，跨范围为 403，学生查询无越权数据，状态历史仅成功命令增加一条。实验室 ENABLED 且 M2 不存在下游阻断事实时，DISABLED→AVAILABLE 成功并只写一条历史。

- [ ] 3.9 提交资产纵向切片。

    git add ruoyi-lab/src/main ruoyi-lab/src/test ruoyi-admin/src/main/java/com/ruoyi/web/controller/lab ruoyi-admin/src/test/java/com/ruoyi/integration/lab/security/LabObjectPermissionIT.java ruoyi-admin/src/test/java/com/ruoyi/integration/lab/web
    git commit -m "feat: add laboratory and device lifecycle"

## Task 4: 实现资格状态、覆盖判定与本人查询

**Files:**

- Create: ruoyi-lab/src/main/java/com/ruoyi/lab/dto/QualificationCreateDto.java
- Create: ruoyi-lab/src/main/java/com/ruoyi/lab/dto/QualificationUpdateDto.java
- Create: ruoyi-lab/src/main/java/com/ruoyi/lab/dto/QualificationRevokeDto.java
- Create: ruoyi-lab/src/main/java/com/ruoyi/lab/vo/QualificationVo.java
- Create: ruoyi-lab/src/main/java/com/ruoyi/lab/domain/QualificationComputedStatus.java
- Create: ruoyi-lab/src/main/java/com/ruoyi/lab/service/QualificationService.java
- Create: ruoyi-lab/src/main/java/com/ruoyi/lab/service/LabQualificationGuard.java
- Create: ruoyi-lab/src/main/java/com/ruoyi/lab/service/impl/QualificationServiceImpl.java
- Create: ruoyi-admin/src/main/java/com/ruoyi/web/controller/lab/LabQualificationController.java
- Create: ruoyi-lab/src/test/java/com/ruoyi/lab/service/QualificationServiceTest.java
- Create: ruoyi-admin/src/test/java/com/ruoyi/integration/lab/service/LabQualificationGuardIT.java
- Create: ruoyi-admin/src/test/java/com/ruoyi/integration/lab/web/LabQualificationControllerTest.java
- Test: ruoyi-lab/src/test/java/com/ruoyi/lab/service/QualificationServiceTest.java
- Test: ruoyi-admin/src/test/java/com/ruoyi/integration/lab/service/LabQualificationGuardIT.java
- Test: ruoyi-admin/src/test/java/com/ruoyi/integration/lab/web/LabQualificationControllerTest.java

- [ ] 4.1 先写参数化测试，以固定 Clock 覆盖 NOT_EFFECTIVE、VALID、EXPIRED、REVOKED 四种计算状态；边界固定为 `validFrom <= now && now < validUntil && revokedAt == null` 才有效。

    @ParameterizedTest
    @MethodSource("qualificationCases")
    void computesStatus(LocalDateTime validFrom, LocalDateTime validUntil, LocalDateTime revokedAt,
                        QualificationComputedStatus expected) {
        assertThat(service.computeStatus(validFrom, validUntil, revokedAt, fixedClock))
            .isEqualTo(expected);
    }

- [ ] 4.2 先写覆盖判定集成测试：LABORATORY 范围匹配设备所属实验室、DEVICE_CATEGORY 范围匹配设备类别、任一有效记录即可通过；未来生效、到期、撤销、其他用户和其他范围都拒绝并返回 LAB_QUALIFICATION_INVALID。

- [ ] 4.3 运行测试确认红灯。

    powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\run-lab-tests.ps1 `
      -DatabaseName lab_test_m2_task -Tests 'QualificationServiceTest,LabQualificationGuardIT'

预期：BUILD FAILURE，原因是资格 Service 与 Guard 尚不存在。

- [ ] 4.4 固定跨计划契约名称为 `LabQualificationGuard`，计划 02 和计划 03 只通过该接口复核资格。

    public interface LabQualificationGuard {
        void assertQualified(Long userId, Long deviceId, LocalDateTime at);
        boolean isQualified(Long userId, Long deviceId, LocalDateTime at);
    }

- [ ] 4.5 实现新增、更新、撤销和查询。`validUntil` 必须严格晚于 `validFrom`；LABORATORY 的 scopeId 必须解析为存在且在管理范围内的实验室 ID；DEVICE_CATEGORY 必须存在于字典。新增写 `null→当前计算状态` 历史；更新若使当前计算状态变化则写一条前后状态历史；撤销是带版本条件的命令并写 `原计算状态→REVOKED`，已撤销再次撤销回读同一资格的当前表示而不重复写历史。时间自然流逝导致 NOT_EFFECTIVE、VALID、EXPIRED 变化时不补写命令历史，查询状态始终按当前时间计算。

- [ ] 4.6 提供 `/lab/qualifications/mine`，忽略任何 userId 查询参数并从 SecurityContext 取本人 ID；管理查询 `/lab/qualifications` 必须同时有按钮权限和实验室数据范围。学生访问他人详情统一返回 403。

- [ ] 4.7 使用 fixed Clock 运行单元与集成测试。

    powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\run-lab-tests.ps1 `
      -DatabaseName lab_test_m2_task `
      -Tests 'QualificationServiceTest,LabQualificationGuardIT,LabQualificationControllerTest'

预期：四种状态、两种范围和本人隔离测试全绿；过期资格不能覆盖设备。

- [ ] 4.8 提交资格纵向切片。

    git add ruoyi-lab/src/main ruoyi-lab/src/test ruoyi-admin/src/main/java/com/ruoyi/web/controller/lab/LabQualificationController.java ruoyi-admin/src/test/java/com/ruoyi/integration/lab/service/LabQualificationGuardIT.java ruoyi-admin/src/test/java/com/ruoyi/integration/lab/web/LabQualificationControllerTest.java
    git commit -m "feat: add scoped qualification management"

## Task 5: 实现对象授权的本地受控附件

**Files:**

- Create: ruoyi-lab/src/main/java/com/ruoyi/lab/storage/StorageService.java
- Create: ruoyi-lab/src/main/java/com/ruoyi/lab/storage/StoredObject.java
- Create: ruoyi-lab/src/main/java/com/ruoyi/lab/storage/LocalStorageService.java
- Create: ruoyi-lab/src/main/java/com/ruoyi/lab/storage/AttachmentPolicy.java
- Create: ruoyi-lab/src/main/java/com/ruoyi/lab/storage/LabAttachmentObjectAuthorizer.java
- Create: ruoyi-lab/src/main/java/com/ruoyi/lab/service/AttachmentService.java
- Create: ruoyi-lab/src/main/java/com/ruoyi/lab/service/impl/AttachmentServiceImpl.java
- Create: ruoyi-lab/src/main/java/com/ruoyi/lab/config/LabStorageProperties.java
- Create: ruoyi-admin/src/main/java/com/ruoyi/web/controller/lab/LabAttachmentController.java
- Modify: ruoyi-admin/src/main/resources/application.yml
- Create: ruoyi-lab/src/test/java/com/ruoyi/lab/storage/AttachmentPolicyTest.java
- Create: ruoyi-lab/src/test/java/com/ruoyi/lab/storage/LocalStorageServiceTest.java
- Create: ruoyi-admin/src/test/java/com/ruoyi/integration/lab/security/AttachmentAuthorizationIT.java
- Create: ruoyi-admin/src/test/java/com/ruoyi/integration/lab/web/LabAttachmentControllerTest.java
- Test: ruoyi-lab/src/test/java/com/ruoyi/lab/storage/AttachmentPolicyTest.java
- Test: ruoyi-lab/src/test/java/com/ruoyi/lab/storage/LocalStorageServiceTest.java
- Test: ruoyi-admin/src/test/java/com/ruoyi/integration/lab/security/AttachmentAuthorizationIT.java
- Test: ruoyi-admin/src/test/java/com/ruoyi/integration/lab/web/LabAttachmentControllerTest.java

- [ ] 5.1 先写策略测试，构造真实 PNG、JPEG、PDF 文件头，覆盖允许类型、伪扩展名、双扩展名、声明 MIME 不符、空文件、10 MiB 边界、10 MiB 加 1 字节和第 6 个附件。

- [ ] 5.2 先写存储测试，使用 `@TempDir` 验证随机 storedName、SHA-256、原名不进入路径、`../` 和绝对路径都不能逃离根目录，删除不存在对象具有幂等语义。

- [ ] 5.3 先写对象权限集成测试：可读实验室或设备可下载；跨部门管理员、无资格学生和仅知道附件 ID 的用户得到 403；不存在附件在有范围用户下返回 404。

- [ ] 5.4 运行测试确认红灯。

    powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\run-lab-tests.ps1 `
      -DatabaseName lab_test_m2_task `
      -Tests 'AttachmentPolicyTest,LocalStorageServiceTest,AttachmentAuthorizationIT'

预期：BUILD FAILURE，原因是存储端口、策略与对象授权尚未实现。

- [ ] 5.5 实现不泄露磁盘路径的存储端口。

    public interface StorageService {
        StoredObject store(InputStream input, long contentLength, String extension) throws IOException;
        InputStream load(String storageKey) throws IOException;
        void delete(String storageKey) throws IOException;
    }

    public record StoredObject(String storageKey, String storedName, long sizeBytes, String sha256) {}

- [ ] 5.6 `LocalStorageService` 启动时将 `lab.storage.local-root` 解析为绝对规范路径并拒绝位于 classpath、static、public、项目源码或临时上传目录内的配置；写入使用 UUID 随机名与原子移动，下载只接受数据库查出的 storageKey。

    lab:
      storage:
        local-root: ${LAB_FILE_ROOT}
        max-file-size: 10MB
        max-files-per-object: 5

- [ ] 5.7 上传事务先按 businessType 锁定实验室、设备或资格业务对象行并校验写权限，再统计附件数量；同一对象的并发上传由对象行锁串行化，确保永远不超过 5 个。随后校验扩展名、MIME、签名与大小，保存文件后插入元数据；数据库失败时删除新文件。下载先按附件 ID 查元数据，再按 businessType 与 businessId 调用对象授权，最后加载 storageKey。删除同样先锁对象、校验权限并提交元数据删除，事务提交后再删除磁盘对象，磁盘删除失败记录可重试告警且不得恢复下载权限。

- [ ] 5.8 Controller 只放在 `ruoyi-admin/src/main/java/com/ruoyi/web/controller/lab/`，端点固定为 `POST /lab/attachments`、`GET /lab/attachments/{id}/content`、`DELETE /lab/attachments/{id}`；上传使用 multipart/form-data，下载设置经过 RFC 5987 编码的原文件名，响应禁止暴露 storageKey 和本机路径。

- [ ] 5.9 运行附件测试并执行路径敏感信息扫描。

    powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\run-lab-tests.ps1 `
      -DatabaseName lab_test_m2_task `
      -Tests 'AttachmentPolicyTest,LocalStorageServiceTest,AttachmentAuthorizationIT,LabAttachmentControllerTest'
    rg -n "storageKey|local-root" ruoyi-admin/src/main/java ruoyi-ui/src

预期：测试全绿；扫描结果只允许服务端内部字段或配置声明，Controller 响应类与前端对象不包含 storageKey 和本机绝对路径。

- [ ] 5.10 提交附件纵向切片。

    git add ruoyi-lab/src/main ruoyi-lab/src/test ruoyi-admin/src/main/java/com/ruoyi/web/controller/lab/LabAttachmentController.java ruoyi-admin/src/main/resources/application.yml ruoyi-admin/src/test/java/com/ruoyi/integration/lab/security/AttachmentAuthorizationIT.java ruoyi-admin/src/test/java/com/ruoyi/integration/lab/web/LabAttachmentControllerTest.java
    git commit -m "feat: add authorized local attachments"

## Task 6: 用 V2_1 接入菜单、字典与资产资格前端

**Files:**

- Create: ruoyi-admin/src/main/resources/db/migration/V2_1__lab_asset_menus_dictionaries.sql
- Create: ruoyi-ui/src/api/lab/laboratory.js
- Create: ruoyi-ui/src/api/lab/device.js
- Create: ruoyi-ui/src/api/lab/qualification.js
- Create: ruoyi-ui/src/api/lab/attachment.js
- Create: ruoyi-ui/src/views/lab/laboratory/index.vue
- Create: ruoyi-ui/src/views/lab/device/index.vue
- Create: ruoyi-ui/src/views/lab/device/detail.vue
- Create: ruoyi-ui/src/views/lab/qualification/index.vue
- Create: ruoyi-ui/src/views/lab/qualification/mine.vue
- Create: ruoyi-ui/src/components/lab/StatusHistory.vue
- Create: ruoyi-ui/src/components/lab/AttachmentPanel.vue
- Create: ruoyi-ui/tests/unit/api/lab/assets.spec.js
- Create: ruoyi-ui/tests/unit/views/lab/qualification-mine.spec.js
- Test: ruoyi-ui/tests/unit/api/lab/assets.spec.js
- Test: ruoyi-ui/tests/unit/views/lab/qualification-mine.spec.js

- [ ] 6.1 先写 API 测试，固定路径、HTTP 方法、BigInt 字符串 ID、分页参数和非法 sortBy 在请求前被拒绝；再写本人资格页面测试，断言不会发送 userId 且显示四种计算状态。

    corepack yarn --cwd .\ruoyi-ui test --run tests/unit/api/lab/assets.spec.js tests/unit/views/lab/qualification-mine.spec.js

预期：测试失败，原因是 API 模块和页面尚不存在。

- [ ] 6.2 创建 V2_1，使用 2200 至 2299 的固定菜单 ID，插入实验室、设备、资格与本人资格菜单及按钮权限；重复权限字符不得出现。字典至少包含 `lab_laboratory_status`、`lab_device_status`、`lab_risk_level`、`lab_qualification_scope_type`。

    INSERT INTO sys_menu
      (menu_id, menu_name, parent_id, order_num, path, component, query_param, route_name,
       is_frame, is_cache, menu_type, visible, status, perms, icon,
       create_by, create_time, update_by, update_time, remark)
    VALUES
      (2200, '实验室资产', 2000, 1, 'assets', NULL, NULL, '', 1, 0, 'M', '0', '0', '', 'build', 'admin', NOW(3), '', NULL, 'M2资产资格目录'),
      (2201, '实验室管理', 2200, 1, 'laboratories', 'lab/laboratory/index', NULL, 'LabLaboratories', 1, 0, 'C', '0', '0', 'lab:laboratory:list', 'office-building', 'admin', NOW(3), '', NULL, ''),
      (2202, '设备管理', 2200, 2, 'devices', 'lab/device/index', NULL, 'LabDevices', 1, 0, 'C', '0', '0', 'lab:device:list', 'monitor', 'admin', NOW(3), '', NULL, ''),
      (2203, '资格管理', 2200, 3, 'qualifications', 'lab/qualification/index', NULL, 'LabQualifications', 1, 0, 'C', '0', '0', 'lab:qualification:list', 'education', 'admin', NOW(3), '', NULL, ''),
      (2204, '我的资格', 2200, 4, 'my-qualifications', 'lab/qualification/mine', NULL, 'MyLabQualifications', 1, 0, 'C', '0', '0', 'lab:qualification:mine', 'user', 'admin', NOW(3), '', NULL, '');

    INSERT INTO sys_menu
      (menu_id, menu_name, parent_id, order_num, path, component, query_param, route_name,
       is_frame, is_cache, menu_type, visible, status, perms, icon,
       create_by, create_time, update_by, update_time, remark)
    VALUES
      (2210, '实验室新增', 2201, 1, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'lab:laboratory:add', '#', 'admin', NOW(3), '', NULL, ''),
      (2211, '实验室修改', 2201, 2, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'lab:laboratory:edit', '#', 'admin', NOW(3), '', NULL, ''),
      (2212, '实验室状态', 2201, 3, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'lab:laboratory:status', '#', 'admin', NOW(3), '', NULL, ''),
      (2220, '设备新增', 2202, 1, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'lab:device:add', '#', 'admin', NOW(3), '', NULL, ''),
      (2221, '设备修改', 2202, 2, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'lab:device:edit', '#', 'admin', NOW(3), '', NULL, ''),
      (2222, '设备状态', 2202, 3, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'lab:device:status', '#', 'admin', NOW(3), '', NULL, ''),
      (2223, '附件管理', 2202, 4, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'lab:attachment:manage', '#', 'admin', NOW(3), '', NULL, ''),
      (2230, '资格新增', 2203, 1, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'lab:qualification:add', '#', 'admin', NOW(3), '', NULL, ''),
      (2231, '资格修改', 2203, 2, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'lab:qualification:edit', '#', 'admin', NOW(3), '', NULL, ''),
      (2232, '资格撤销', 2203, 3, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'lab:qualification:revoke', '#', 'admin', NOW(3), '', NULL, '');

- [ ] 6.3 在同一迁移中为学生授予设备查询、详情、本人资格和本人可读附件权限；为实验室管理员授予授权范围的资产与资格管理权限；为安全员授予资格管理权限。角色关联使用计划 00 已固定的 role_key 子查询，不硬编码 role_id。

- [ ] 6.4 前端 API 统一位于 `ruoyi-ui/src/api/lab/`，页面统一位于 `ruoyi-ui/src/views/lab/`。列表传 pageNum、pageSize、sortBy、sortDirection；所有 ID 保持字符串，不做 Number 转换。

- [ ] 6.5 实验室、设备和资格页面按按钮权限显示命令；隐藏按钮只改善界面，不能替代后端校验。AttachmentPanel 只接收 businessType、businessId 与 canManage，不接收 storageKey。

- [ ] 6.6 运行前端测试与生产构建。

    corepack yarn --cwd .\ruoyi-ui test --run tests/unit/api/lab/assets.spec.js tests/unit/views/lab/qualification-mine.spec.js
    corepack yarn --cwd .\ruoyi-ui build:prod

预期：指定测试全绿；生产构建退出码为 0；动态路由组件均可解析。

- [ ] 6.7 提交菜单、字典和前端。

    git add ruoyi-admin/src/main/resources/db/migration/V2_1__lab_asset_menus_dictionaries.sql ruoyi-ui/src/api/lab ruoyi-ui/src/views/lab ruoyi-ui/src/components/lab ruoyi-ui/tests/unit
    git commit -m "feat: add asset qualification user interface"

## Task 7: 建立 M2 验收证据与回归门禁

**Files:**

- Create: ruoyi-admin/src/test/java/com/ruoyi/integration/lab/acceptance/AssetQualificationAcceptanceIT.java
- Create: ruoyi-admin/src/test/java/com/ruoyi/integration/lab/security/AssetQualificationSecurityIT.java
- Create: scripts/smoke-m2-assets-qualifications.ps1
- Create: docs/testing/m2-assets-qualifications-report.md
- Modify: docs/requirements/lab-management-srs.md
- Test: ruoyi-admin/src/test/java/com/ruoyi/integration/lab/acceptance/AssetQualificationAcceptanceIT.java
- Test: ruoyi-admin/src/test/java/com/ruoyi/integration/lab/security/AssetQualificationSecurityIT.java

- [ ] 7.1 先写 AT-03 验收测试：管理员创建实验室和设备，安全员分别授予 LABORATORY 与 DEVICE_CATEGORY 资格，学生列表只含被任一资格覆盖的设备，本人资格显示 VALID，其他学生与跨部门管理员不能读取详情。

- [ ] 7.2 增加 AT-06 部分测试：实验室停用后资产仍可审计查询但不能作为新业务目标；FAULT 与 DISABLED 设备向可预约 Guard 返回阻断；资格在 `validUntil` 边界立即失效。

- [ ] 7.3 增加 AT-15 部分测试：伪装 PDF 的脚本、超过 10 MiB、第 6 个附件、非法 sortBy 返回 400；猜测附件 ID 和跨部门下载返回 403；响应与日志不出现 storageKey、绝对路径或文件内容。

- [ ] 7.4 运行 M2 后端门禁。

    powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify.ps1
    powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\assert-surefire-tests.ps1 -Module ruoyi-admin -RequiredTests 'LabAssetSchemaMigrationIT,LabAssetMapperXmlIT,LabObjectPermissionIT,LabQualificationGuardIT,AttachmentAuthorizationIT,AssetQualificationAcceptanceIT,AssetQualificationSecurityIT'
    powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-migrations.ps1

预期：BUILD SUCCESS；报告断言输出 7 个 VERIFIED TEST，证明资产资格 IT 未被静默跳过；资产资格相关单元、集成、WebMvc 与迁移测试全绿；V1_2→V2_1 和空库→V2_1 都成功。

- [ ] 7.5 运行前端与安全门禁。

    corepack yarn --cwd .\ruoyi-ui install --frozen-lockfile
    corepack yarn --cwd .\ruoyi-ui test
    corepack yarn --cwd .\ruoyi-ui build:prod
    git diff --check
    rg -n "(password\s*[:=]\s*[^$<{]|BEGIN (RSA|OPENSSH) PRIVATE KEY|Bearer eyJ|\.\./\.\./)" . -g '!target/**' -g '!ruoyi-ui/node_modules/**'

预期：安装、测试和构建均退出 0；diff 检查通过；敏感扫描无真实凭据和可利用路径拼接。

- [ ] 7.6 smoke 脚本使用环境变量中的演示账号完成“实验室→设备→资格→学生查询→附件上传与下载→越权下载拒绝”，只输出 HTTP 状态、业务编号和 traceId，不输出 Token、密码、文件存储键或绝对路径。

    powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\smoke-m2-assets-qualifications.ps1

预期：脚本退出码为 0；授权请求成功，越权与非法输入按 403/400 拒绝。

- [ ] 7.7 在报告中记录 Git 提交、Flyway 版本、Surefire 实际执行数量、上述 7 个 IT 的 XML 报告名、演示数据 ID、traceId 和 AT 证据；在 SRS 追踪附录把 AT-03 标为通过，把 AT-06 与 AT-15 标为“已通过本计划覆盖部分”，不得标成完整通过。

- [ ] 7.8 检查工作区只包含本任务计划内文件后提交并标记 M2。

    git status --short
    git add ruoyi-lab/src/test ruoyi-admin/src/test scripts/smoke-m2-assets-qualifications.ps1 docs/testing/m2-assets-qualifications-report.md docs/requirements/lab-management-srs.md
    git commit -m "test: prove asset qualification milestone"
    git tag milestone/m2-assets-qualifications

## FR/AT 映射

| 需求 | 实现任务 | 自动化或证据 | M2 判定 |
|---|---|---|---|
| FR-SYS-003 | Task 3、4、5 | LabObjectPermissionIT、AttachmentAuthorizationIT、AssetQualificationSecurityIT | 完成 |
| FR-SYS-005 | Task 3、4 | DeviceStateMachineTest、状态历史断言、操作日志 smoke | 完成 |
| FR-AST-001 | Task 1、3、6 | 唯一约束、实验室命令测试、前端测试 | 完成 |
| FR-AST-002 | Task 1、2、3、6 | Mapper 查询测试、资产页面测试 | 完成 |
| FR-AST-003 | Task 3 | DeviceStateMachineTest、LabDeviceControllerTest | M2 静态边与实验室启用守卫完成，计划 03/04 接入下游动态事实 |
| FR-AST-004 | Task 3 | 设备详情与 occupied-ranges 契约测试 | M2 契约完成，M3 接入真实预约区间 |
| FR-AST-005 | Task 1、5、6 | AttachmentPolicyTest、AttachmentAuthorizationIT | 完成 |
| FR-QUA-001 | Task 1、4、6 | QualificationServiceTest、Controller 测试 | 完成 |
| FR-QUA-002 | Task 4 | LabQualificationGuardIT | 完成 |
| FR-QUA-003 | Task 4、6 | mine 接口与页面测试 | 完成 |
| AT-03 | Task 7 | AssetQualificationAcceptanceIT、M2 smoke | 完整通过 |
| AT-06 | Task 3、4、7 | 停用实验室、故障设备、过期资格测试 | 本计划范围通过 |
| AT-15 | Task 2、5、7 | 非法排序、非法附件、越权下载测试 | 本计划范围通过 |

## M2 回归命令

    powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify.ps1
    powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\assert-surefire-tests.ps1 -Module ruoyi-admin -RequiredTests 'LabAssetSchemaMigrationIT,LabAssetMapperXmlIT,LabObjectPermissionIT,LabQualificationGuardIT,AttachmentAuthorizationIT,AssetQualificationAcceptanceIT,AssetQualificationSecurityIT'
    powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-migrations.ps1
    corepack yarn --cwd .\ruoyi-ui test
    corepack yarn --cwd .\ruoyi-ui build:prod
    powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\smoke-m2-assets-qualifications.ps1
    git status --short

预期：所有命令退出码为 0；7 个必跑 IT 报告存在、执行数大于 0 且无失败、错误或跳过；Flyway schema 版本为 V2_1；AT-03 全链路成功；AT-06 与 AT-15 的 M2 子集有可复现证据；工作区干净。
