# ADR-0001: MyBatis-Plus 与 PageHelper 共存边界

## 状态

Accepted

## 日期

2026-09-02

## 背景

RuoYi 原有分页 API 由 PageHelper 提供，而实验室模块需要 MyBatis-Plus 的
`BaseMapper` 与 `Wrapper`。PageHelper 2.1.1 的 starter 会传递引入原生
`mybatis-spring-boot-starter`；若再直接加入 MyBatis-Plus starter，会同时存在两套
Boot 集成入口。另一方面，项目显式创建的原生 `SqlSessionFactoryBean` 不会注入
MyBatis-Plus 的通用 CRUD 语句，`BaseMapper.selectById` 会出现
`Invalid bound statement`。

## 决策

- 简单的单表 CRUD 和条件查询使用 MyBatis-Plus `BaseMapper` / `Wrapper`。
- 复杂联表、聚合、锁查询和需要精确 SQL 审计的查询继续使用 Mapper XML。
- V1 唯一分页机制是 PageHelper，保留现有 RuoYi 分页 API。
- 不启用 MyBatis-Plus `PaginationInnerInterceptor`，避免重复改写分页与 count SQL。
- 不引入 `mybatis-plus-jsqlparser`；当前兼容层不需要基于 JSqlParser 的分页能力。
- PageHelper starter 排除其传递的原生 `mybatis-spring-boot-starter`，framework
  显式依赖 `mybatis-plus-spring-boot3-starter` 3.5.17。
- 根依赖管理显式收敛 `org.mybatis:mybatis` 3.5.19。common 只依赖原生
  MyBatis API，lab 因使用 `BaseMapper` 继续直接依赖 MyBatis-Plus core。
- 项目唯一自定义工厂使用 `MybatisSqlSessionFactoryBean`，并保留既有
  DataSource、type aliases、mapper locations、MyBatis config location 和
  PageHelper 自动挂载行为。
- 真实数据库测试只接受安全 wrapper 传入的、逗号分隔且大小写不重复的简单测试
  类名。通配符、方法选择器和全限定类名不属于 V1 wrapper 契约。
- wrapper 在 reset 前完成参数、凭据、可执行文件、脚本、源码、报告目录和 reparse
  边界的全部非数据库预检。定向模式和 `CleanVerify` 都逐文件清除已有的普通
  `TEST-*.xml`，拒绝目录或 reparse entry；reset 后立即运行 Maven。`CleanVerify`
  还在命令行显式关闭 Maven 的 clean/test skip 开关，防止 settings 或 `.mvn`
  注入跳过构建阶段。Maven 成功仍须由 `assert-surefire-tests.ps1` 验证本次要求的
  每个报告确实存在、至少执行一个测试且没有 failure、error 或 skip；`CleanVerify`
  固定要求本次新生成的 `LabCompatibilityProbeMapperTest` 报告通过。
- wrapper 设置 `LAB_TEST_WRAPPER_ACTIVE=true`。marker 缺失或空白表示普通构建，兼容
  测试在 Spring 上下文创建前跳过；marker 存在但不是精确的 `true`，或 marker=true
  时 URL、端口、`lab_test_*` 库名、应用凭据、Flyway 任一安全条件非法，条件评估
  直接失败，不能以 skip 形成假 GREEN。
- master 和 slave 属性都钉到同一个 wrapper 构造的 loopback 测试库，slave 同时强制
  `enabled=false`。wrapper 清除高优先级 JVM/Maven 注入通道，并在成功或失败后按
  “原先存在/不存在及原值”精确恢复全部被修改的进程环境变量；在 PowerShell 7 中
  局部关闭原生命令错误升级，仍由显式 `$LASTEXITCODE` 检查保留 Maven 原退出码。

## 验证

依赖边界使用以下命令核对，输出保存在不提交的 `target/evidence`：

```powershell
$null = New-Item -ItemType Directory -Force -Path .\target\evidence
mvn -pl ruoyi-admin -am dependency:tree `
  "-Dincludes=org.mybatis*,com.baomidou:mybatis-plus*,com.github.pagehelper:*" 2>&1 |
  Tee-Object -FilePath .\target\evidence\mybatis-dependency-tree.txt
```

结果必须同时满足：不存在 `org.mybatis.spring.boot:mybatis-spring-boot-starter`，
不存在 `com.baomidou:mybatis-plus-jsqlparser*`，MyBatis 只解析为 3.5.19，且
MyBatis-Plus Boot 3 starter 是唯一 Boot 集成入口。PageHelper 6.1.1 自身使用的
`com.github.jsqlparser:jsqlparser:4.7` 不属于上述禁止坐标。

真实数据库兼容性只通过安全包装器验证：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\run-lab-tests.ps1 `
  -DatabaseName lab_test_m1_compatibility `
  -Tests 'LabCompatibilityProbeMapperTest'
```

测试必须同时断言 `BaseMapper.selectById` 的实体映射，以及 PageHelper 的总数、
页码、页大小和排序内容。普通 `mvn clean verify` 不提供测试库环境时跳过该真实库
测试，不连接或迁移任何数据库。普通构建的兼容测试报告应为 2 个 skipped 且没有
Spring 启动日志；安全 wrapper 的报告必须为 2 个执行、0 failure、0 error、0 skip。

## 已知兼容风险

PageHelper starter 2.1.1 的构建基线来自 Spring Boot 2.7，而项目运行于 Spring Boot
3.5；排除其原生 starter 后，当前只通过本 ADR 的 PageHelper count/LIMIT 与
MyBatis-Plus `BaseMapper` 基础路径集成测试证明兼容，并不代表 PageHelper 的全部
Boot 自动配置路径均已覆盖。升级 Spring Boot、PageHelper、MyBatis 或
MyBatis-Plus 时必须保留该真实数据库回归和依赖树门禁。

## 后果与回滚边界

后续业务代码不能自行增加第二个分页拦截器。若回滚本决策，必须作为一个整体同时
恢复原生 starter、原生 `SqlSessionFactoryBean` 和不依赖 `BaseMapper` 的 Mapper
实现；只回滚其中一项会重新造成双 starter 或通用 CRUD 语句缺失。数据库结构和
业务数据不属于本 ADR 的回滚范围。
