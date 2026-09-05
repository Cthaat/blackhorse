# G1 扩展 Implementation Plan

> **For agentic workers:** 使用 subagent-driven-development，按任务完成需求审查与质量审查；本轮书面方案已经批准，不再询问普通开发步骤。不创建工作树。

**Goal:** 交付实验室范围限制／申诉与二维码资产前后端闭环，然后继续 G2 收尾及 G3 三批。

**Architecture:** 独立限制事实与规则版本，复用现有预约、候补、领用和私有附件授权；统一用户锁与设备仲裁顺序。复用 G2 投递基础和现有角色工作台。

**Tech Stack:** Java 17、Spring Boot 3、MyBatis、MySQL、Vue 3、Element Plus，二维码依赖先核对现有包并固定兼容版本。

## Task 1：限制／申诉后端

Files：新增 `ruoyi-lab/src/main/java/com/ruoyi/lab/restriction/`，`ruoyi-lab/src/main/java/com/ruoyi/lab/mapper/LabRestrictionMapper.java`，`ruoyi-admin/src/main/java/com/ruoyi/web/controller/lab/LabRestrictionController.java`，迁移 `V9_0__reservation_restrictions.sql`。修改 ReservationCommandServiceImpl、ReservationLifecycleServiceImpl、ReservationWaitlistService／Coordinator 和领用服务校验入口。

- [ ] 最小规则测试先失败再实现：结束边界不生效、7 天默认、重复来源不延长、审批人不能是本人。
- [ ] 实现规则版本、有限期手工限制、新爽约自动事实、一次申诉及审批；命令与历史原子提交、事实去重，历史数据不追罚。
- [ ] 将限制判定与锁接入普通／代办预约、候补加入／递补／确认、领用；不得静默取消旧预约或限制归还。
- [ ] 所有列表／详情／命令和证据附件按本人或当前实验室管理范围授权。提供 DTO／VO 和稳定错误码，不返回敏感数据库实体。
- [ ] 接入消息事实补登与审计；最小 Java 测试与编译通过，分别进行需求和质量审查。

## Task 2：限制／申诉页面

Files：`ruoyi-ui/src/api/lab/restrictions.js`、`ruoyi-ui/src/views/lab/restrictions/index.vue` 及同目录组件；菜单迁移与 Task 1 协调。

- [ ] 先写 API 契约及核心表单测试，再实现接口封装。
- [ ] 页面覆盖本人限制、申诉／证据、管理员手工登记／解除、规则版本及审批；有限分页、明确到期／叠加语义、确认及错误态。
- [ ] 使用可搜索授权用户／实验室选项，前端校验不代替后端权限。沿用现有现代工作台和窄屏布局。
- [ ] Vitest 定向测试及生产构建，后端隔离数据库验证关键权限与并发边界，真实浏览器检查需要用户正常登录。

## Task 3：二维码资产

Files：设备标签控制器／授权服务，`ruoyi-ui/src/views/lab/asset-scan/`、设备页二维码组件及 `ruoyi-ui/src/utils/labAssetCode.js`。

- [ ] 先验证允许的项目源、正整数设备 ID、拒绝外链／凭据／非法参数，再接入编解码。
- [ ] 标签单张与最多 100 张打印，扫码安全登录回跳、设备详情与预约／报修／授权领还入口；所有动作复用业务 API。
- [ ] 站内相机扫描在安全环境下可用，拒绝权限或不支持时提供编号查询，不更改本机监听地址。
- [ ] 验证编码识别、权限与定向前端测试，构建后更新真实交付状态。

## 后续衔接（保持用户顺序）

- [ ] G2：重新读取实际投递积压／警告原因，再针对性修复；隔离实例验证执行中取消与真实重启，补普通角色撤权，不扩大测试范围。
- [ ] G3.1：设备维护／校准计划版本、周期、负责人、启停与临期工作台。
- [ ] G3.2：周期唯一生成工单、停用窗口冲突仲裁、维修执行与验收复用、校准附件及下一周期。
- [ ] G3.3：维修／维护／隐患 SLA 快照、响应与处理计时、暂停恢复、临期／到期／24 小时升级防重、队列与时间线。

G2 与 G3 在各批开始前按已批准设计补对应精确文件任务清单，不改变业务默认值；不以本清单表示实现完成。

## 验证命令与版本管理

Java 使用 `C:/APP/JDK/jdk_17`，Maven 使用 `C:/Apache/Maven/apache-maven-3.9.16/bin/mvn.cmd`：`mvn.cmd -q -pl ruoyi-admin -am -Dtest=LabRestrictionPolicyTest -Dsurefire.failIfNoSpecifiedTests=false test`，先得到缺失行为断言失败，再通过。实际新增测试名随实现记录，不将未执行用例标为通过。

前端 `C:/nvm4w/nodejs/yarn.CMD test tests/unit/restrictions.spec.js`，再 `yarn.CMD build:prod`。原生集成沿用 `scripts/run-lab-tests.ps1` 的新隔离 `lab_test_` 数据库，绝不重置业务库。构建前停止项目拥有的 jar 实例，数据库迁移前备份。各批 `git diff --check` 后仅提交项目文件；保留 `.vscode/`，沿用当前分支推送。
