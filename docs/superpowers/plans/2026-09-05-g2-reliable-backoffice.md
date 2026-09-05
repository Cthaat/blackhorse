# G2 可靠后台处理实施计划

> **For agentic workers:** 使用 subagent-driven-development 按任务实施，先需求审查再质量审查。用户已批准书面设计；禁止工作树，保持在当前仓库，普通实现步骤不再请求确认。

**Goal:** 完成消息模板与可靠投递、异步导入导出和授权运维页面的前后端闭环。

**Architecture:** MySQL 持久事实及执行登记，现有通知去重、Quartz 和私有存储优先复用。异步任务使用当前权限与提交范围交集、逐行事务检查点及条件领取；只启用站内渠道。

**Tech Stack:** Java 17、Spring Boot 3、MyBatis、MySQL、Redis、Vue 3、Element Plus、现有 POI；指标依赖遵循 Boot 版本管理。

## 任务 1：消息闭环

Files：新增迁移 `ruoyi-admin/src/main/resources/db/migration/V8_0__message_delivery_center.sql`；新增 `ruoyi-lab/src/main/java/com/ruoyi/lab/message/` 的模板、投递、偏好模型／服务／mapper；新控制器 `LabMessageCenterController`；修改现有 NotificationDelivery／Compensation 以统一入口。新增 `ruoyi-ui/src/api/lab/messageCenter.js` 及 `views/lab/message-center/`。

- [x] 先补最小失败用例：模板未知变量拒绝、版本不可变、次数 5 后隔离、重复登记／投递不重复收件箱。
- [x] 实现事实登记、现有失败记录兼容、有限退避、租约回收、脱敏查询、审计重放；原事实扫描负责补登，不能双重重试。
- [x] 实现模板草稿／发布／预览／历史、白名单渲染与快照、本人可选提醒偏好、只读渠道状态。
- [x] 接通消息管理页面，覆盖分页筛选、详情重放、模板编辑与偏好，不泄露他人正文。
- [x] 定向 Maven／Vitest 验证，需求与质量审查后提交。

## 任务 2：异步任务闭环

Files：迁移 `V8_1__async_business_tasks.sql`；新增 `ruoyi-lab/src/main/java/com/ruoyi/lab/task/` 持久模型、mapper、授权、工作器、XLSX 编解码及服务；控制器 `LabBusinessTaskController`；新增 `ruoyi-ui/src/api/lab/businessTasks.js`、`views/lab/task-center/`，现有五类业务列表增加导出入口。

- [x] 定向用例覆盖公式拒绝、行数限制、状态策略、所有者／对象范围下载检查；隔离数据库验证取消后不能领取、检查点恢复不重复创建。执行中取消的完整进程演练留待后续。
- [x] 实现模板、持久预检、确认入队、进度与逐行错误；复用原实验室／设备创建服务，成功行与检查点同事务。
- [x] 实现五类授权导出、稳定游标／最大 ID 边界、结果对象清单及下载撤权检查。
- [x] 实现原子队列上限、2 工作线程、60 秒租约／15 秒心跳、恢复检查点、取消、关联新任务重试、私有结果及 7 天过期清理。
- [x] 实现任务中心和导入预检页面，绑定业务列表的导出筛选；定向验证并经两阶段审查提交。

## 任务 3：运维与集成

Files：新增 `ruoyi-admin/src/main/java/com/ruoyi/web/controller/lab/LabOperationsController.java`、关联号过滤器与指标采集器；新增 `ruoyi-ui/src/api/lab/operations.js`、`views/lab/operations/index.vue`；迁移 `V8_2__g2_workspace_permissions.sql`，修改 `workspaceNavigation.js`（必要时）及依赖配置。

- [x] 定义并验证低基数采样、无样本未知、关联号合法性、受限端点权限；只读运维不授予业务读取权限。
- [x] 采集依赖健康、JVM、连接池、HTTP 次数／错误／P95、投递积压、任务数／耗时，标明真实时间窗和采样时间。
- [x] 运维页面接通刷新／错误／空态／告警；将关联号加入 G2 持久任务和投递记录。
- [x] 备份本机数据库，构建后通过既有原生启动脚本应用迁移并运行前后端。
- [x] 隔离数据验证事实恢复、幂等、排队取消／检查点续行、对象范围变化后的下载拒绝。
- [x] 实验室管理员浏览器验证：设备报表 10 行成功、逐行详情、结果入口、通知偏好保存；任务中心桌面／390px 窄屏截图检查，整页宽度未溢出。
- [ ] 系统管理员浏览器验证消息模板、投递和运维页；完整角色 E2E 与真实杀进程恢复不计作已通过。
- [x] 更新需求池真实状态、记录命令与结果、已知限制；仅提交项目变更并推送，保留 `.vscode/`。

## 验证命令与完成口径

Java 使用 `C:/APP/JDK/jdk_17`，Maven 使用 `C:/Apache/Maven/apache-maven-3.9.16/bin/mvn.cmd`。定向测试：`mvn.cmd -pl ruoyi-admin -am -Dtest=<新增及受影响测试类> -Dsurefire.failIfNoSpecifiedTests=false test`；预期指定用例通过，不以未执行的测试作证据。打包：`mvn.cmd -pl ruoyi-admin -am -DskipTests package`。运行中的 jar 在 Windows 可能锁定，先按项目脚本停止拥有的运行实例再打包。

前端在 `ruoyi-ui` 使用现有 Vitest 脚本选择新增测试，随后 `yarn.cmd build:prod`；预期定向测试和构建成功。每次代码改动后只重跑相关检查；大规模 E2E、压测及正式求职材料不在本次执行范围。

不将“文件存在”“编译成功”当成闭环完成；新接口必须有可用页面、授权和错误反馈，故障恢复须有实际证据，未验证部分如实保留。

## 本轮执行证据（2026-09-05）

- 消息定向 Java 17 项通过；任务规则／XLSX 4 项通过；运维指标／健康 6 项通过。前端消息 3 项、任务 API 2 项、运维 2 项定向测试通过；生产构建成功（1879 modules）。三个模块均完成需求与静态质量审查。
- `scripts/run-lab-tests.ps1 -DatabaseName lab_test_g2_native_20260905_02 -Tests LabG2BusinessIT`：2 项真实隔离 MySQL 集成通过。覆盖导入一行成功一行无效、错误文件、成功检查点恢复不重复创建、设备导出私有下载、跨用户拒绝、对象部门改变后拒绝下载、排队取消、投递登记去重及收件箱写入后确认丢失恢复。恢复使用过期租约模拟，不是实际杀进程或双实例测试。
- `mvn.cmd -q -pl ruoyi-admin -am -DskipTests package` 成功；`scripts/start-local.ps1 -SkipBuild` 成功。本机 Flyway V8.0、V8.1、V8.2 均 success=1；8080、5173、33306、36379 均只监听 127.0.0.1；内部账号 9000 保持禁用登录。
- 迁移前完整数据库备份保存于忽略目录 `target/local-runtime/g2-before-20260905-2036.sql`（1,525,689 bytes），未重置本机演示数据。浏览器生成的设备导出任务 1 保留作演示，10 行全部成功。
- XLSX 预检同步执行但受 2 个解析槽限制，预检任务持久化并计入容量；后台 2 个工作线程。导出提交时按固定最大 ID 和 100 条游标批次冻结授权对象清单，最多扫描 500,000 候选／导出 50,000 行，执行及下载重验权限与原始归属。没有据此宣称已完成性能优化。
- 复用现有 POI、Spring、MyBatis、Druid 与私有存储，不新增第三方服务；自有有界 HTTP 采集器按 MVC 路由模板聚合，5 分钟计数、最多 10,000 延迟样本和 100 条路由，页面明确截断口径。
- 候补状态迁移现保留邀请截止时间以支持事实补登；此前已被清空的历史截止时间不伪造重建。历史 Element Plus 警告仍有记录，本次任务操作未观察到 console error，但不能宣称零 UI 问题。
- 暂未执行：全部五类报表的角色组合浏览器回归、真实进程中断／双实例、执行中取消的时序演练、全角色 E2E、压测与正式求职材料。
