# G2 可靠后台处理实施计划

> **For agentic workers:** 使用 subagent-driven-development 按任务实施，先需求审查再质量审查。用户已批准书面设计；禁止工作树，保持在当前仓库，普通实现步骤不再请求确认。

**Goal:** 完成消息模板与可靠投递、异步导入导出和授权运维页面的前后端闭环。

**Architecture:** MySQL 持久事实及执行登记，现有通知去重、Quartz 和私有存储优先复用。异步任务使用当前权限与提交范围交集、逐行事务检查点及条件领取；只启用站内渠道。

**Tech Stack:** Java 17、Spring Boot 3、MyBatis、MySQL、Redis、Vue 3、Element Plus、现有 POI；指标依赖遵循 Boot 版本管理。

## 任务 1：消息闭环

Files：新增迁移 `ruoyi-admin/src/main/resources/db/migration/V8_0__message_delivery_center.sql`；新增 `ruoyi-lab/src/main/java/com/ruoyi/lab/message/` 的模板、投递、偏好模型／服务／mapper；新控制器 `LabMessageCenterController`；修改现有 NotificationDelivery／Compensation 以统一入口。新增 `ruoyi-ui/src/api/lab/messageCenter.js` 及 `views/lab/message-center/`。

- [ ] 先补最小失败用例：模板未知变量拒绝、版本不可变、次数 5 后隔离、重复登记／投递不重复收件箱。
- [ ] 实现事实登记、现有失败记录兼容、有限退避、租约回收、脱敏查询、审计重放；原事实扫描负责补登，不能双重重试。
- [ ] 实现模板草稿／发布／预览／历史、白名单渲染与快照、本人可选提醒偏好、只读渠道状态。
- [ ] 接通消息管理页面，覆盖分页筛选、详情重放、模板编辑与偏好，不泄露他人正文。
- [ ] 定向 Maven／Vitest 验证，需求与质量审查后提交。

## 任务 2：异步任务闭环

Files：迁移 `V8_1__async_business_tasks.sql`；新增 `ruoyi-lab/src/main/java/com/ruoyi/lab/task/` 持久模型、mapper、授权、工作器、XLSX 编解码及服务；控制器 `LabBusinessTaskController`；新增 `ruoyi-ui/src/api/lab/businessTasks.js`、`views/lab/task-center/`，现有五类业务列表增加导出入口。

- [ ] 最小失败用例覆盖公式拒绝、行数限制、状态转移、取消保留已提交行、所有者／当前权限检查。
- [ ] 实现模板、持久预检、确认入队、进度与逐行错误；复用原实验室／设备创建服务，成功行与检查点同事务。
- [ ] 实现五类授权导出、稳定游标／最大 ID 边界、结果对象清单及下载撤权检查。
- [ ] 实现原子队列上限、2 工作线程、60 秒租约／15 秒心跳、恢复检查点、取消、关联新任务重试、私有结果及 7 天过期清理。
- [ ] 实现任务中心和导入预检页面，绑定业务列表的导出筛选；定向验证并经两阶段审查提交。

## 任务 3：运维与集成

Files：新增 `ruoyi-admin/src/main/java/com/ruoyi/web/controller/lab/LabOperationsController.java`、关联号过滤器与指标采集器；新增 `ruoyi-ui/src/api/lab/operations.js`、`views/lab/operations/index.vue`；迁移 `V8_2__g2_workspace_permissions.sql`，修改 `workspaceNavigation.js`（必要时）及依赖配置。

- [ ] 定义并验证低基数采样、无样本未知、关联号合法性、受限端点权限；只读运维不授予业务读取权限。
- [ ] 采集依赖健康、JVM、连接池、HTTP 次数／错误／P95、投递积压、任务数／耗时，标明真实时间窗和采样时间。
- [ ] 运维页面接通刷新／错误／空态／告警；将关联号加入 G2 持久任务和投递记录。
- [ ] 备份本机数据库，构建后通过既有原生启动脚本应用迁移并运行前后端。
- [ ] 隔离数据验证事实恢复、幂等、取消／续行、撤权；浏览器检查角色入口、主线操作和桌面／窄屏布局。
- [ ] 更新需求池真实状态、记录命令与结果、已知限制；仅提交项目变更并推送，保留 `.vscode/`。

## 验证命令与完成口径

Java 使用 `C:/APP/JDK/jdk_17`，Maven 使用 `C:/Apache/Maven/apache-maven-3.9.16/bin/mvn.cmd`。定向测试：`mvn.cmd -pl ruoyi-admin -am -Dtest=<新增及受影响测试类> -Dsurefire.failIfNoSpecifiedTests=false test`；预期指定用例通过，不以未执行的测试作证据。打包：`mvn.cmd -pl ruoyi-admin -am -DskipTests package`。运行中的 jar 在 Windows 可能锁定，先按项目脚本停止拥有的运行实例再打包。

前端在 `ruoyi-ui` 使用现有 Vitest 脚本选择新增测试，随后 `yarn.cmd build:prod`；预期定向测试和构建成功。每次代码改动后只重跑相关检查；大规模 E2E、压测及正式求职材料不在本次执行范围。

不将“文件存在”“编译成功”当成闭环完成；新接口必须有可用页面、授权和错误反馈，故障恢复须有实际证据，未验证部分如实保留。
