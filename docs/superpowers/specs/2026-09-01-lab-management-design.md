# 高校实验室设备共享预约与安全巡检闭环管理系统

## 总体设计说明书

| 项目 | 内容 |
|---|---|
| 文档版本 | V1.0 审阅版 |
| 编制日期 | 2026-09-01 |
| 对应需求 | docs/requirements/lab-management-srs.md |
| 对应策划 | docs/planning/lab-management-project-charter.md |
| 文档状态 | 已完成内部自检，待用户书面审核 |

## 1. 设计结论

系统采用RuoYi-Vue Spring Boot 3分支作为基础平台，使用前后端分离的模块化单体架构。通用认证、RBAC、菜单、部门、字典、日志、Quartz和代码生成由若依提供；实验室业务集中在独立ruoyi-lab模块。

V1只部署一个后端应用和一套响应式Vue 3前端。MySQL是业务事实和并发正确性的最终来源；Redis用于登录会话、缓存、重复提交保护和短期互斥，但不替代数据库事务和约束。

设计围绕三条相互关联的流程：

1. 安全准入—预约—审批—领用—归还；
2. 异常归还或故障—维修—管理员验收；
3. 巡检—隐患—整改—复查—销号。

安全资格、设备状态和未销号重大隐患共同参与预约提交、批准及领用校验，使资产共享和安全巡检形成同一个业务系统。

## 2. 设计原则

- **范围优先：** 4～6周内先完整实现业务闭环，不引入微服务、消息队列、工作流引擎和真实物联网。
- **数据库负责正确性：** 预约并发、唯一使用记录和开放维修单由事务、行锁、条件更新及唯一约束共同保证。
- **显式状态命令：** 普通编辑接口不能修改状态，状态变化通过审批、领用、归还、验收、复查等命令完成。
- **接口与实体分离：** Controller使用Request DTO和Response VO，不直接暴露数据库实体。
- **对象级权限：** 菜单和按钮权限之外，服务层必须校验本人、部门、实验室和工单归属。
- **历史不可覆盖：** 使用记录、整改轮次、任务检查结果和状态变化只追加，不由普通接口覆盖。
- **外部能力可替换：** 文件存储通过端口抽象，V1使用本地实现，不绑定云厂商。
- **按垂直切片交付：** 每个功能同时完成数据库、后端、前端、权限、文档和测试。

## 3. 技术基线

| 层级 | 技术 |
|---|---|
| 基础平台 | RuoYi-Vue 3.9.2的springboot3维护分支，初始化时固定具体提交 |
| Java | Java 17、Maven |
| 后端 | Spring Boot 3.5.x、Spring Framework 6.2.x、Spring Security、JWT |
| 持久层 | MyBatis-Plus 3.5.x Boot 3 Starter、Mapper XML、Flyway、Druid |
| 数据 | MySQL 8、Redis 7 |
| 调度 | RuoYi Quartz |
| 接口文档 | OpenAPI 3、Springdoc、Knife4j 4.x Jakarta Starter |
| 前端 | Vue 3、JavaScript、Vite、Element Plus、Pinia、Axios、ECharts |
| 测试 | JUnit 5、Mockito、MockMvc、数据库集成测试、前端测试、端到端冒烟测试 |

初始化时先执行兼容性探针：固定若依后端及Vue 3前端提交，输出Maven依赖树和前端锁文件，验证登录、菜单、MyBatis-Plus分页、Knife4j文档和生产构建。只有探针通过后才开始业务开发。

## 4. 系统上下文

系统外部参与者和交互如下：

| 参与者 | 输入 | 输出 |
|---|---|---|
| 学生 | 预约、取消、故障描述 | 可用时段、审批结果、资格和使用记录 |
| 实验室管理员 | 设备、资格、审批、领还、维修验收 | 待办、设备及利用统计 |
| 安全员 | 巡检计划、检查结果、隐患、复查结论 | 巡检待办、超期和隐患统计 |
| 维修人员 | 维修过程和结果 | 分派工单和验收结果 |
| 系统管理员 | 账号、角色、菜单、字典、参数 | 日志、任务和运行信息 |

V1没有必须依赖的校外系统、硬件或付费服务。所有演示账号和数据由本系统初始化。

## 5. 逻辑架构

### 5.1 运行关系

用户浏览器访问Vue 3静态页面，前端通过REST和JWT访问ruoyi-admin。ruoyi-admin加载ruoyi-system、ruoyi-lab、ruoyi-quartz、ruoyi-generator和若依通用模块。ruoyi-lab通过MyBatis-Plus和Mapper XML访问MySQL，通过受限接口使用Redis及文件存储。

Nginx只在发布环境负责静态文件及/api反向代理；本机开发可以使用Vite代理直接访问后端。

### 5.2 后端模块

| 模块 | 责任 | 禁止事项 |
|---|---|---|
| ruoyi-admin | 启动、全局配置、实验室Web Controller、OpenAPI入口 | 不写业务规则，不直接访问Mapper |
| ruoyi-common | 通用常量、基础异常、工具和公共类型 | 不依赖实验室业务 |
| ruoyi-framework | 安全、JWT、数据权限、日志和Web基础设施 | 不包含实验室状态机 |
| ruoyi-system | 用户、角色、部门、菜单、字典和参数 | 不反向依赖ruoyi-lab |
| ruoyi-lab | 实体、DTO/VO、应用服务、领域规则、Mapper、文件端口和状态历史 | 不依赖前端，不绕过权限服务 |
| ruoyi-quartz | 任务注册和调度入口，委托ruoyi-lab服务执行 | 不直接写业务表 |
| ruoyi-generator | 开发期生成基础骨架 | 生成代码不能绕过人工校验和测试 |

### 5.3 ruoyi-lab内部结构

- domain：实验室、设备、资格、预约、使用、维修、巡检、隐患及状态枚举；
- dto：创建、更新、查询和业务命令入参；
- vo：列表、详情、工作台和状态历史响应；
- service：接口定义；
- service.impl：事务边界、权限校验、状态迁移和跨表编排；
- mapper：MyBatis-Plus BaseMapper及复杂查询接口；
- resources/mapper/lab：复杂查询XML；
- security：业务对象数据范围和职责分离；
- storage：StorageService端口及本地文件实现；
- event：事务提交后通知事件；
- exception：稳定业务错误码和异常。

### 5.4 前端结构

- src/api/lab：按领域划分API；
- src/views/lab：实验室、设备、资格、预约、使用、维修、巡检、隐患和工作台页面；
- src/components/lab：状态标签、时间段选择、附件和状态历史等复用组件；
- src/store/modules：用户、字典和消息状态；
- src/utils：时间、幂等键和下载工具。

前端只负责交互校验和显示，不能自行决定数据范围或合法状态迁移。

## 6. 数据设计

### 6.1 业务表

| 表 | 关键字段与职责 |
|---|---|
| lab_laboratory | lab_code、name、dept_id、manager_id、location、status |
| lab_device | asset_no、laboratory_id、category_code、risk_level、status、version |
| lab_qualification | user_id、scope_type、scope_id、valid_from、valid_until、revoked_at |
| lab_reservation | reservation_no、device_id、applicant_id、start_time、end_time、status、approval fields |
| lab_usage_record | reservation_id、device_id、user_id、checked_out_at、returned_at、return_condition |
| lab_repair_order | repair_no、device_id、source_type、assignee_id、status、result、acceptance |
| lab_inspection_plan | laboratory_id、frequency_type、interval_value、execute_time、next_run_at、owner_id、deadline_rule、status |
| lab_inspection_plan_item | plan_id、item_code、content、sort_order、enabled |
| lab_inspection_task | plan_id、scheduled_at、deadline_at、assignee_id、status、overdue_flag |
| lab_inspection_item | task_id、plan_item_id、content_snapshot、result、description、severity |
| lab_hazard | hazard_no、source_item_id、target_type、target_id、severity、owner_id、deadline、status |
| lab_rectification | hazard_id、round_no、submitter_id、description、reviewer_id、review_result |
| lab_notification | receiver_id、type、title、business_type、business_id、read_at |
| lab_attachment | business_type、business_id、original_name、stored_name、mime_type、size、storage_key |
| lab_status_history | object_type、object_id、from_status、to_status、operator_id、reason、trace_id |

巡检计划检查项和实际任务检查结果分表保存。任务生成时复制检查项内容到content_snapshot，因此后续修改计划不会改变历史巡检证据。

### 6.2 关系

- sys_dept一对多lab_laboratory；
- lab_laboratory一对多lab_device、lab_inspection_plan和实验室级lab_hazard；
- lab_device一对多lab_reservation、lab_usage_record、lab_repair_order和设备级lab_hazard；
- lab_inspection_plan一对多lab_inspection_plan_item及lab_inspection_task；
- lab_inspection_task一对多lab_inspection_item；
- 不合格lab_inspection_item最多创建一条来源隐患；
- lab_hazard一对多lab_rectification；
- 业务对象一对多lab_attachment和lab_status_history。

### 6.3 约束和索引

1. 实验室编码、资产编号和各类业务编号使用唯一索引。
2. lab_usage_record.reservation_id唯一，保证一次预约只有一条使用记录。
3. lab_inspection_task对plan_id和scheduled_at建立唯一约束。
4. lab_rectification对hazard_id和round_no建立唯一约束。
5. 预约建立device_id、status、start_time、end_time组合查询索引。
6. 资格建立user_id、scope_type、scope_id、valid_from、valid_until索引。
7. 隐患建立target_type、target_id、severity、status和deadline索引。
8. 状态历史建立object_type、object_id和create_time索引。
9. 业务表内部强归属关系使用外键，对sys_user和sys_dept使用逻辑引用。
10. 所有主键为BIGINT，输出JSON时序列化为字符串。
11. 已执行Flyway迁移只允许追加，不允许修改。

MySQL不能直接使用通用排他约束阻止时间区间重叠，因此预约正确性通过设备行锁、事务内冲突查询和状态命令复核实现。

## 7. 预约一致性设计

### 7.1 时间语义

预约区间使用左闭右开[startTime, endTime)。上午10:00结束的预约与10:00开始的预约相邻但不冲突。

冲突表达式：

existing.startTime < new.endTime，并且existing.endTime > new.startTime。

只有PENDING、APPROVED和CHECKED_OUT参与冲突判断。

### 7.2 创建预约

1. 校验DTO、时间范围和幂等键；
2. 根据当前用户确定申请人，忽略前端伪造用户ID；
3. 开启数据库事务并使用SELECT FOR UPDATE锁定目标设备；
4. 校验实验室启用、设备可用、有效资格和重大隐患；
5. 查询有效状态下的重叠预约；
6. 无冲突时插入PENDING预约和状态历史；
7. 提交事务；
8. 提交后生成审批站内消息；
9. 返回预约编号和当前状态。

批准预约时重复执行设备锁、资格、设备、隐患和冲突校验，防止申请后条件变化。

### 7.3 Redis边界

- 保存短期幂等结果和重复提交标识；
- 缓存设备详情、字典和可用时段查询结果；
- 可以在进入数据库事务前按设备ID申请短期互斥以降低热点竞争；
- Redis失败时降级到数据库校验，不允许跳过行锁和冲突查询；
- 任何Redis结果都不能直接证明预约成功。

### 7.4 领用和归还

领用在事务中锁定预约和设备，重新校验领用窗口、资格、设备、隐患和状态。成功后同时创建使用记录、将预约改为CHECKED_OUT并将设备改为IN_USE。

正常归还同时关闭使用记录、将预约改为COMPLETED并恢复设备AVAILABLE。异常归还同时关闭使用记录、完成预约、将设备改为FAULT并创建或关联开放维修单。任一写入失败时整体回滚。

## 8. 状态机设计

### 8.1 预约

PENDING可以批准为APPROVED、驳回为REJECTED、取消为CANCELLED或自动过期为EXPIRED。APPROVED可以取消、领用为CHECKED_OUT或自动爽约为NO_SHOW。CHECKED_OUT归还后为COMPLETED。所有终态不可恢复。

### 8.2 设备

AVAILABLE领用后进入IN_USE，正常归还恢复AVAILABLE。故障或异常归还进入FAULT，开始维修进入MAINTENANCE，管理员验收通过且无其他阻断时恢复AVAILABLE。管理员可以按规则停用为DISABLED。

重大隐患是独立阻断条件，不通过修改设备状态表达，避免隐患销号错误覆盖设备真实故障。

### 8.3 维修

WAIT_ASSIGN → WAIT_REPAIR → IN_PROGRESS → WAIT_ACCEPTANCE → CLOSED。

验收不通过从WAIT_ACCEPTANCE退回IN_PROGRESS，设备保持MAINTENANCE。只有实验室管理员可以执行验收。

### 8.4 巡检

PENDING → IN_PROGRESS → COMPLETED。超期为独立标记。任务生成后保存计划检查项快照，计划修改不影响已生成任务。

### 8.5 隐患

PENDING_RECTIFICATION → RECTIFYING → PENDING_REVIEW → CLOSED。

复查不通过退回RECTIFYING。每次整改建立新的round_no记录。CLOSED不可重开，重新发现时创建新隐患并保留关联。

所有状态命令在服务层通过显式允许迁移表校验，非法迁移抛出LAB_ILLEGAL_STATE_TRANSITION并返回HTTP 409。

## 9. 关键业务数据流

### 9.1 预约主流程

学生选择设备和时间 → 前端校验 → 后端权限和DTO校验 → 数据库设备行锁 → 资格/状态/隐患/冲突校验 → 保存待审批预约 → 通知管理员 → 管理员批准并重新校验 → 到场办理领用 → 创建使用记录 → 归还并结束使用。

### 9.2 维修流程

故障上报或异常归还 → 设备故障 → 创建开放工单 → 管理员分派 → 维修人员开始及提交结果 → 设备保持维修中 → 管理员验收 → 通过后按阻断条件恢复可用，不通过退回维修。

### 9.3 安全流程

Quartz按计划幂等生成任务 → 安全员逐项检查 → 不合格项生成实验室级或设备级隐患 → 指派责任人 → 多轮整改和附件 → 安全员复查 → 通过后销号 → 重新计算阻断条件。

## 10. API设计

### 10.1 资源组

- /lab/laboratories
- /lab/devices
- /lab/qualifications
- /lab/reservations
- /lab/usage-records
- /lab/repair-orders
- /lab/inspection-plans
- /lab/inspection-tasks
- /lab/hazards
- /lab/notifications
- /lab/dashboard
- /lab/attachments

查询使用GET，创建使用POST，普通资料修改使用PUT，逻辑删除使用DELETE。状态变化使用明确命令，例如approve、check-out、return、accept和review，不提供setStatus接口。

### 10.2 DTO和VO

- 创建及更新DTO只包含允许由调用者输入的字段，忽略用户ID、状态和审计字段；
- 查询DTO定义筛选、分页和白名单排序；
- 命令DTO包含原因、幂等键和必要业务数据；
- VO使用字符串形式的BIGINT ID和带+08:00偏移的时间；
- 列表VO不携带大附件和完整状态历史，详情接口按需返回。

### 10.3 响应与错误

在兼容若依code、msg、data和分页total、rows字段的同时使用正确HTTP状态：

- 400：请求参数、文件或时间范围无效；
- 401：未登录或Token无效；
- 403：功能权限、对象范围或职责分离失败；
- 404：对象不存在或对当前用户不可见；
- 409：预约冲突、非法状态、资格失效、重大隐患或重复命令；
- 500：未预期错误。

500响应只返回通用提示和追踪编号。日志记录异常摘要和追踪编号，不记录JWT、密码、文件内容或敏感配置。

## 11. 权限与安全

### 11.1 权限层次

1. Spring Security验证身份；
2. 若依权限注解验证菜单及按钮权限；
3. 服务层根据本人、部门、负责实验室和工单归属验证对象范围；
4. 职责分离规则阻止自批、自验和自复查；
5. Mapper查询附加允许的数据范围，不能只在前端过滤。

对无权知道是否存在的对象返回404；对已知对象但无命令权限的操作返回403。

### 11.2 输入与输出

- Bean Validation负责格式、长度、必填和枚举；
- 服务层负责时间、状态、资格和关联对象等业务校验；
- Mapper使用参数绑定，排序字段通过白名单映射；
- 备注作为纯文本处理并在显示时转义；
- 上传同时校验扩展名、MIME、大小和可识别文件头；
- 文件随机命名，保存于Web根目录之外。

### 11.3 凭据

数据库密码、Redis密码、JWT密钥和文件根目录通过环境变量或外部配置提供。仓库只保存无秘密的配置示例。演示数据不包含真实个人信息。

## 12. 文件存储

ruoyi-lab定义StorageService：

- store：写入文件并返回storageKey；
- load：按storageKey读取；
- delete：仅用于未被业务引用的临时文件；
- metadata：返回大小、MIME和校验信息。

V1提供LocalStorageService，将文件保存到配置目录并使用随机文件名。lab_attachment保存业务对象、原文件名和storageKey。下载前重新校验业务对象权限，不能仅凭附件ID访问。

未来的MinIO或OSS实现只替换StorageService，不改变业务表和Controller契约。

## 13. 定时任务与消息

### 13.1 任务

- 固定调度器扫描next_run_at已到期的启用计划并生成巡检任务；
- 将开始时间已到的PENDING预约设为EXPIRED；
- 将超过领用宽限期的APPROVED预约设为NO_SHOW；
- 标记巡检和整改超期；
- 补发可识别的失败站内消息。

业务人员只配置每日、每周、每月等受控频率和执行时间，不直接提交可执行类名或任意Cron表达式。固定调度器使用Asia/Shanghai时区、数据库时间、条件更新和“计划ID+计划执行时间”唯一键。重复运行不得重复生成任务、改变终态或重复发送相同业务消息。

### 13.2 消息

核心业务事务内写状态和状态历史，事务提交后通过Spring事务事件创建站内消息。消息失败不回滚核心状态，并记录可查询错误。V1不引入消息队列。

## 14. 前端交互设计

### 14.1 页面

| 角色 | 页面 |
|---|---|
| 学生 | 设备查询、设备详情、可用时段、预约申请、我的预约、资格、使用记录、故障上报 |
| 实验室管理员 | 实验室、设备、资格、预约审批、领用归还、维修分派和验收、工作台 |
| 安全员 | 巡检计划、任务执行、隐患、整改复查、安全工作台 |
| 维修人员 | 我的维修工单、维修过程和提交结果 |
| 系统管理员 | 若依系统管理、日志、字典和任务 |

### 14.2 交互规则

- 不同角色登录后显示不同首页和待办；
- 状态使用统一字典、颜色和中文含义；
- 状态命令显示确认对话框及必要原因字段；
- 冲突、资格和隐患阻断显示后端返回的明确原因；
- 提交按钮生成幂等键并在请求期间禁用；
- 375px宽度下学生核心页面可操作，复杂管理表格允许横向滚动；
- 详情页统一展示状态历史和附件。

## 15. 事务、异常和恢复

### 15.1 事务边界

下列命令各自形成完整事务：

- 创建和批准预约；
- 领用和归还；
- 故障上报与开放维修单建立；
- 维修提交和验收；
- 巡检完成及隐患生成；
- 整改提交和复查；
- 隐患销号及阻断条件重新计算。

状态历史与核心状态在同一事务内写入。站内消息在事务提交后写入。

### 15.2 并发更新

- 热点预约使用设备行锁；
- 普通资料和状态命令使用version或状态条件更新；
- 更新影响行数为零时重新读取并返回409；
- 定时任务使用状态条件和唯一约束保证幂等；
- 同一设备开放维修单通过设备行锁和开放状态查询保证唯一。

### 15.3 恢复

应用重启后所有业务真相从MySQL恢复。Redis缓存可以清空重建。定时任务按数据库状态继续处理，不依赖内存队列。文件元数据和本地文件目录必须一起备份。

## 16. 测试设计

### 16.1 单元测试

- 区间边界：相邻、包含、被包含、部分重叠和跨日；
- 资格：未生效、有效、过期、撤销和范围覆盖；
- 各状态机合法及非法迁移；
- 重大隐患的实验室级和设备级阻断；
- 维修及整改复核退回；
- 数据范围和职责分离规则。

### 16.2 集成和接口测试

- Flyway从空库迁移；
- Mapper复杂查询及数据范围；
- 事务回滚和设备行锁；
- 20线程相同时段预约；
- JWT、按钮权限、对象越权；
- 400、401、403、404、409和500响应；
- 附件类型、大小、路径和下载权限；
- 定时任务重复执行。

### 16.3 端到端

1. 学生具备资格，提交预约，管理员批准、领用并正常归还；
2. 异常归还生成维修单，维修人员处理，管理员驳回一次后最终验收；
3. 巡检产生重大隐患，预约被阻断，整改复查退回一次后销号并恢复。

### 16.4 质量目标

- 核心业务Service行覆盖率不低于80%，后端总体不低于60%；
- 阻断级和严重级缺陷为零；
- 自动化测试、前端构建、空库迁移和端到端冒烟均作为发布门禁。

## 17. 部署设计

### 17.1 开发

- Vite开发服务器代理/api到Spring Boot；
- MySQL、Redis使用独立开发实例；
- 文件保存到开发专用目录；
- 敏感值使用本地外部配置。

### 17.2 演示发布

- Vue 3生产构建由Nginx提供；
- Nginx将/api代理到单个Spring Boot实例；
- Spring Boot连接MySQL、Redis和持久文件目录；
- 不依赖公网，准备离线依赖和预构建产物。

### 17.3 备份

发布前备份MySQL和文件目录。验收时恢复到新数据库及新文件目录，并核对实验室、设备、预约、维修、隐患、附件和状态历史。

## 18. 被否决的方案

| 方案 | 否决原因 |
|---|---|
| 微服务和Spring Cloud | 规模不需要，增加部署、事务和排障成本 |
| 完全从零搭建RBAC | 4～6周内挤占原创业务时间 |
| 管理端加独立H5或小程序 | 双端重复开发，主流程延期风险高 |
| Redis锁作为预约最终依据 | 锁失效、网络分区或缓存丢失会破坏正确性 |
| 直接修改状态字段 | 可以绕过资格、权限、事务和历史 |
| 巡检计划项和任务结果共用一张可编辑记录 | 修改计划会污染历史证据 |
| 重大隐患直接改写设备状态 | 多个阻断条件相互覆盖，销号时容易错误恢复 |
| 阿里云OSS工具类写死 | 密钥风险和厂商耦合，不利于本机演示 |
| AI、物联网和在线考试 | 不属于两条核心闭环，超出周期 |

## 19. 设计自检结果

- **占位符检查：** 文档没有未决占位符，所有V1范围均有明确边界。
- **一致性检查：** 角色、状态、数据表、接口、错误和验收与需求规格说明书一致。
- **范围检查：** P2增强功能不进入V1发布门槛。
- **歧义检查：** 预约区间、冲突状态、领用窗口、隐患影响范围、复核退回和定时任务时区均已明确。
- **可验证性检查：** 核心规则均映射到自动化或端到端验收场景。

## 20. 参考资料

- 教学知识库：<https://my.feishu.cn/wiki/Rj5OwGGoMisdrnkOtwvc7uZin1d>
- RuoYi-Vue官方仓库：<https://gitee.com/y_project/RuoYi-Vue>
- Spring Boot 3.5系统要求：<https://docs.spring.io/spring-boot/3.5/system-requirements.html>
- MyBatis-Plus安装说明：<https://baomidou.com/en/getting-started/install/>
- Knife4j快速开始：<https://doc.xiaominfo.com/docs/quick-start>
- 教育部《高等学校实验室安全规范》：<https://www.moe.gov.cn/srcsite/A16/moe_784/202302/t20230220_1045998.html>
- 教育部《高等学校实验室安全分级分类管理办法（试行）》：<https://www.moe.gov.cn/srcsite/A16/s7062/202404/t20240419_1126415.html>
