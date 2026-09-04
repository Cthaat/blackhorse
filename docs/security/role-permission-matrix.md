# 实验室管理角色权限矩阵

## 1. V1_2 边界

V1_2 只建立角色、实验室根目录、系统管理员的最小 RuoYi 系统管理权限，以及禁登录的系统操作主体。实验室业务页面、按钮和接口尚未实现，因此四个业务角色在 V1_2 仅关联 `menu_id=2000`，不授予任何 `lab:*` 按钮或接口权限。后续迁移必须随真实业务接口逐项追加权限。

系统管理员也只获得系统基础管理能力。实验室业务写操作仍要求同时拥有相应业务角色，不能因为 `lab_system_admin` 而绕过职责分离或对象数据范围。

## 2. 固定角色与数据范围

| role_id | role_key | 角色名称 | data_scope | 默认数据范围 | V1_2 菜单 |
|---:|---|---|---:|---|---|
| 100 | `lab_student` | 学生 | 5 | 仅本人 | 实验室管理根目录 |
| 101 | `lab_manager` | 实验室管理员 | 3 | 本部门 | 实验室管理根目录 |
| 102 | `lab_safety_officer` | 安全员 | 3 | 本部门 | 实验室管理根目录 |
| 103 | `lab_repair_worker` | 维修人员 | 5 | 仅本人 | 实验室管理根目录 |
| 104 | `lab_system_admin` | 系统管理员 | 1 | 全部系统管理数据 | 实验室管理根目录和第 3 节的系统菜单 |

五个角色均为 `status=0`、`del_flag=0`。实验室根目录固定为 `menu_id=2000`、`path=lab`，自身没有权限字符串。

## 3. 系统管理员的最小系统菜单

下表 ID 和权限字符串来自 V1_0 的实际种子。`lab_system_admin` 只关联表内项目及其必要父目录。

| 功能 | 菜单 | 列表权限 | 按钮 ID 与权限 |
|---|---|---|---|
| 必要父目录 | 1 系统管理、2 系统监控、108 日志管理 | 无 | 无 |
| 用户管理 | 100 | `system:user:list` | 1000 `system:user:query`；1004 `system:user:export` |
| 角色管理 | 101 | `system:role:list` | 1007 `system:role:query`；1011 `system:role:export` |
| 菜单管理 | 102 | `system:menu:list` | 1012 `system:menu:query` |
| 部门管理 | 103 | `system:dept:list` | 1016 `system:dept:query`；1017 `system:dept:add`；1018 `system:dept:edit`；1019 `system:dept:remove` |
| 字典管理 | 105 | `system:dict:list` | 1025 `system:dict:query`；1026 `system:dict:add`；1027 `system:dict:edit`；1028 `system:dict:remove`；1029 `system:dict:export` |
| 参数设置 | 106 | `system:config:list` | 1030 `system:config:query`；1031 `system:config:add`；1032 `system:config:edit`；1033 `system:config:remove`；1034 `system:config:export` |
| 操作日志 | 500 | `monitor:operlog:list` | 1039 `monitor:operlog:query`；1040 `monitor:operlog:remove`；1041 `monitor:operlog:export` |
| 登录日志 | 501 | `monitor:logininfor:list` | 1042 `monitor:logininfor:query`；1043 `monitor:logininfor:remove`；1044 `monitor:logininfor:export`；1045 `monitor:logininfor:unlock` |
| 定时任务 | 110 | `monitor:job:list` | 1049 `monitor:job:query`；1050 `monitor:job:add`；1051 `monitor:job:edit`；1052 `monitor:job:remove`；1053 `monitor:job:changeStatus`；1054 `monitor:job:export` |
| 实验室入口 | 2000 | 无 | V1_2 无 `lab:*` 按钮或接口权限 |

明确不授予岗位管理、通知公告、在线用户、数据监控（Druid）、服务监控、缓存监控及缓存列表、表单构建、代码生成、系统接口和若依官网，也不授予系统工具父目录。对应菜单 ID `3, 4, 104, 107, 109, 111, 112, 113, 114, 115, 116, 117` 及按钮 ID `1001..1003, 1005, 1006, 1008..1010, 1013..1015, 1020..1024, 1035..1038, 1046..1048, 1055..1060` 均不在角色映射中。用户、角色和菜单的新增、修改、删除、导入、重置密码及授权等 IAM 写操作仅允许内置超级管理员执行；`lab_system_admin` 只有这些模块的列表、查询和导出权限，不能修改自身角色映射或给自身追加角色。

## 4. V1_2 接口权限矩阵

| 接口 | 认证与权限条件 | 适用角色 | 成功 / 失败边界 | 性质 |
|---|---|---|---|---|
| `GET /lab/security-probe` | 必须已认证；`isAuthenticated()`；不要求 `lab:*` 权限 | 五个固定角色，以及其他合法的已认证主体 | 认证有效返回 204 且无响应体；匿名或登录状态失效返回统一 401 `UNAUTHENTICATED` | 永久、只读、无副作用的认证链路探针，不是业务写接口 |

除上述探针外，V1_2 尚未定义实验室业务接口权限：`lab_student`、`lab_manager`、`lab_safety_officer` 和 `lab_repair_worker` 的权限分别随后续学生、管理、安全和维修接口追加；`lab_system_admin` 不自动取得这些业务权限，业务写操作必须叠加相应业务角色。系统管理员仅拥有第 3 节列出的 RuoYi 系统权限。

菜单和按钮只负责功能可见性，不能替代服务端的数据范围、对象归属和职责分离校验。

## 5. 五个演示账号默认值

V1_2 不写入五个可登录演示账号及其密码。非生产演示账号初始化逻辑按以下固定默认值创建账号：

| 用户名 | 昵称 | 部门 | 用户 ID | 角色 | 岗位 |
|---|---|---:|---|---|---|
| `lab_student` | 演示学生 | 103 | 数据库自增 | 仅 `lab_student` | 无 |
| `lab_manager` | 演示实验室管理员 | 103 | 数据库自增 | 仅 `lab_manager` | 无 |
| `lab_safety_officer` | 演示安全员 | 103 | 数据库自增 | 仅 `lab_safety_officer` | 无 |
| `lab_repair_worker` | 演示维修人员 | 103 | 数据库自增 | 仅 `lab_repair_worker` | 无 |
| `lab_system_admin` | 演示系统管理员 | 103 | 数据库自增 | 仅 `lab_system_admin` | 无 |

每个账号恰好绑定一个角色且不绑定岗位。密码不属于数据库迁移内容，不能在 SQL 或本文档中保存。

## 6. 禁登录系统操作主体

系统自动领域命令使用独立主体，不复用演示账号或 RuoYi 超级管理员：

| 字段 | 固定值 |
|---|---|
| `user_id` | 9000 |
| `user_name` | `__lab_system_operator__` |
| `nick_name` | 实验室系统任务 |
| `dept_id` | `NULL` |
| `password` | `!NO_LOGIN!`（不可通过 BCrypt 登录） |
| `status` / `del_flag` | `1` / `0` |
| 角色 / 岗位 | 均无 |
| 配置 | `config_id=100`，`lab.system.operator-user-id=9000` |

固定用户名 `__lab_system_operator__` 长度为 23，超过当前 `UserConstants.USERNAME_MAX_LENGTH=20`，因此 HTTP 登录会在用户名预检阶段直接以统一 401 `UNAUTHENTICATED` 拒绝，且不会进入认证管理器或生成 Token。这只是第一层防护，不是唯一保护：数据库仍同时保持 `status=1`、`password=!NO_LOGIN!`、无角色、无岗位。不应为该主体放宽全局用户名上限。

该主体只能作为自动领域命令写入状态历史时的 `operator_id`。禁止启用账号、设置可登录密码、绑定任何角色或岗位，也禁止作为登录失败时的回退用户。

## 7. 公共字典决策

V1_0 已定义并填充 `sys_normal_disable`（正常/停用）与 `sys_yes_no`（是/否）。V1_2 直接复用这两个公共字典，不新增同义的 `lab_common_status`，也不复制公共状态字典数据。

## 8. 迁移冲突策略

角色 ID/key、实验室菜单 ID/path、系统操作主体 ID/name、配置 ID/key 中任一身份已被占用时，迁移在首条持久写入前失败。迁移还会先核验部门 103、两个公共字典，以及第 3 节引用的每个 V1_0 菜单的路由、类型、状态和权限身份。校验只使用会话临时表，不使用 `INSERT IGNORE` 或 `ON DUPLICATE KEY UPDATE` 接管既有数据。
