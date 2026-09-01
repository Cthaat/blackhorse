# 数据库迁移与基线操作

## 安全边界

应用只从 `classpath:db/migration` 执行 Flyway 迁移。`sql/upstream` 保存冻结的
RuoYi 与 Quartz 上游 SQL，仅用于来源审计，禁止在应用启动或生产发布流程中直接执行。

所有环境的 Flyway 配置固定为：启动时启用、执行前校验、禁止 clean、禁止自动
baseline。已执行的版本迁移不得修改；后续数据库变化只能追加更高版本。

提交或部署前必须在仓库根目录运行：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-migrations.ps1
```

验证器拒绝危险 DDL、`CREATE/ALTER/DROP/RENAME USER`、`SET PASSWORD`、
`IDENTIFIED ... BY`、SQL 语句
起始处的 `USE`、`PREPARE`/`EXECUTE` 动态 SQL、`DELIMITER` 指令、MySQL/MariaDB 可执行
注释、明文密码赋值、重复语义版本、异常文件、
子目录及 reparse point。任何 `${...}` 形式的 Flyway placeholder 也一律拒绝，运行时同时
固定 `placeholder-replacement=false`，避免校验后的文本被二次替换。错误只报告文件名、
类别和行号，不输出可能包含凭据的 SQL 行。

验证器同时拒绝迁移中的任何 `SET ... sql_mode`（包括 SESSION、GLOBAL 与 `@@` 形式）。
部署前必须使用与应用相同的受限账号核对 `SELECT @@SESSION.sql_mode`，确认不含
`NO_BACKSLASH_ESCAPES` 或 `ANSI_QUOTES`；发现不兼容模式时应暂停并由数据库管理员审计
实例/账号配置，应用和迁移脚本不得主动修改生产 `sql_mode` 来绕过差异。

## 运行时环境变量

| 变量 | 要求 |
| --- | --- |
| `LAB_DB_URL` | 必填；指向本次部署明确选择的 MySQL schema |
| `LAB_DB_USERNAME` / `LAB_DB_PASSWORD` | 必填；通过部署平台的秘密存储提供 |
| `LAB_FILE_ROOT` | 必填；可写且位于 Web 根目录之外 |
| `LAB_TOKEN_SECRET` | 必填；使用秘密存储生成的高熵随机值 |
| `LAB_REDIS_PASSWORD` | 必填；通过部署平台的秘密存储提供 Redis 凭据 |
| `LAB_DB_SLAVE_ENABLED` | 可选，默认 `false` |
| `LAB_DB_SLAVE_URL` / `LAB_DB_SLAVE_USERNAME` / `LAB_DB_SLAVE_PASSWORD` | 仅启用独立从库时提供，不得回退到主库或仓库默认值 |
| `LAB_DRUID_STAT_USERNAME` / `LAB_DRUID_STAT_PASSWORD` | 仅激活 `druid-stat` profile 时必填，必须由秘密存储提供且没有默认值 |
| `LAB_DRUID_STAT_ALLOW` | Druid 监控来源白名单，默认仅 `127.0.0.1`；只能提供逗号分隔的具体 IP |

仓库、命令行参数、日志和证据文件中不得保存数据库、Redis、JWT 或 Druid 真实凭据。

Flyway 的 `url`、`user`、`password` 显式复用 `LAB_DB_URL`、`LAB_DB_USERNAME`、
`LAB_DB_PASSWORD`，使迁移使用 Flyway 自己的原生 DataSource，而业务访问继续使用
Druid。两者仍是同一个仅限目标 schema 的应用账号，不需要也不得为应用启动提供
实例管理员账号。该行为遵循
[Spring Boot 3.5 的 Flyway DataSource 配置说明](https://docs.spring.io/spring-boot/how-to/data-initialization.html#howto.data-initialization.migration-tool.flyway)：
设置 `spring.flyway.url` 或 `spring.flyway.user` 会启用独立的 Flyway DataSource。

Druid 监控在默认配置中始终关闭。只有同时激活 `druid`、`druid-stat` profiles 并提供
非空白的监控用户名和密码时才允许启用；缺失、空字符串或全空白的任一凭据都会让应用
在创建普通应用 Bean 前启动失败。不得通过空值、宽泛白名单或在默认 profile 中重新打开
监控端点。白名单显式为空、全空白、包含通配符、任意地址或 CIDR 等非具体 IP 时同样
启动失败。启动校验先检查原始 `LAB_DRUID_STAT_USERNAME/PASSWORD`，再读取最终生效的
`spring.datasource.druid.*` 配置：未激活
`druid-stat` 时任何监控组件被高优先级属性打开都会失败；激活后，用高优先级属性把最终
用户名/密码覆盖为空白，或把白名单覆盖为宽泛、任意地址，也无法绕过校验。部署清单还
必须审计 `SPRING_DATASOURCE_DRUID_*`。

部署前必须审计并清除或固定所有不受控的高优先级配置来源，包括
`SPRING_FLYWAY_*`、`SPRING_DATASOURCE_*`、`SPRING_CONFIG_*`、`SPRING_PROFILES_*`、
`SPRING_AUTOCONFIGURE_*`、`SPRING_APPLICATION_JSON` 以及 JVM/Maven 注入参数。审计必须
包含 relaxed-binding、无分隔下划线、indexed/group 等同义环境变量，不能只检查文档中的
标准拼写。生产平台只允许
来自本次发布清单和秘密存储的等价配置，禁止额外 ConfigData 把 Flyway 指向其他 schema、
历史表或迁移目录。仓库隔离测试 wrapper 的外层进程从不修改调用者环境；它按原始环境
变量名规范化 `.`、`-` 和大小写，移除 child 中全部 canonical `SPRING_*`、`LAB_*`、
`RUOYI_*`、`TOKEN_*`、`SERVER_*`、`LOGGING_*`、`MAVEN_*`、`MYSQL_*` 及 JVM option
通道，再只通过 child 环境写入受信白名单。worker 重新校验自身物理路径与 SHA-256 后才
执行 reset/Maven，退出时整个隔离环境随 child 销毁，因此调用者中“不存在/空值/原值”
天然保持不变。

该白名单把 ConfigData 固定为打包的 `classpath:/application*.yml`，同时固定
`spring.sql.init.mode=never` 与 `spring.quartz.jdbc.initialize-schema=never`。因此即使工作
目录或 `./config/` 存在同名文件，或环境残留 init SQL、target、skip、命名/placeholder 等
Flyway 属性，也不得参与测试启动；`FlywayAutoConfiguration` 也不能被残留 exclude 关闭。
wrapper 还拒绝仓库或祖先 `.mvn` 启动配置、禁用 Maven rc、固定 JDK 17 与 Maven basedir，
并同时用无秘密的最小 user/global settings 隔离 Maven profile、`argLine` 和 JVM option
注入。reset 后管理员凭据不会进入 Maven child。mysql 与 Maven 可执行文件必须来自调用者
已审计的 `PATH`/物理安装位置；生产部署应采用等价的可信配置白名单，而不能依赖 Spring
Boot、Maven 或当前用户的默认搜索。

## 全新空库安装

1. 使用管理通道创建一个全新的空 schema，并确认其中没有业务表或
   `flyway_schema_history`。不要复用开发、演示或生产 schema。
2. 备份部署配置，将上表中的必填变量通过进程环境或秘密存储注入。数据库账号需要
   在目标 schema 中创建表、索引及写入基础种子的权限，但不需要创建或删除数据库。
3. 运行迁移静态检查，然后启动当前版本应用：

   ```powershell
   powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-migrations.ps1
   mvn -pl ruoyi-admin -am spring-boot:run
   ```

4. 启动日志应显示 `1.0` 与 `1.1` 均成功。只读核对：

   ```sql
   SELECT version, description, type, success
   FROM flyway_schema_history
   ORDER BY installed_rank;
   ```

   空库结果应恰有两个成功的 SQL 迁移：`1.0` 建立 RuoYi 的 20 张表及基础种子，
   `1.1` 建立 Quartz 的 11 张表。
5. `V1_0` 为满足上游机械基线保留了上游 `admin` 种子账号及
   `sys.user.initPassword` 的已知弱初始凭据配置；迁移成功不代表凭据安全。开放流量前，
   必须通过不会记录秘密的安全运维通道轮换或停用种子管理员账号，修改初始密码配置，
   并验证所有上游默认凭据均无法登录。不得把新旧密码写入命令行、工单、日志或证据。
6. 完成应用健康检查和登录烟雾检查后再开放流量。MySQL DDL 失败可能留下部分结构；
   此时停止当前应用，删除并重建本次明确创建且仍未投入使用的空 schema，再从头迁移。
   不得用 Flyway clean 或 repair 把部分执行结果伪装成可用基线。

## 已有 RuoYi 数据库的人工基线

已有数据库不能直接按空库流程启动，因为 `baseline-on-migrate` 永远保持 `false`。
只有在维护窗口内完成以下人工审计后，才可以登记人工基线：

1. 建立可恢复的完整备份，并演练恢复；记录数据库实例、schema、应用提交和审计人。
2. 确认没有既有 `flyway_schema_history`，并逐项比较现有表和基础种子。对每张表保存并
   机械对照 `SHOW CREATE TABLE`，审计列类型、NULL 约束、default/generated 表达式、
   主键、unique/普通索引、外键及 ON UPDATE/DELETE 级联、表与列的字符集/collation。
   任何缺表、差异、冲突对象或不明自定义都必须先形成审计结论，禁止用 baseline 掩盖差异。
3. 运行 `verify-migrations.ps1`，确认仓库迁移本身通过静态门禁。
4. 根据审计结果只选择以下一种版本，不允许估算：

   - 现有库与 `V1_0` 的 RuoYi 20 张表及种子完全等价，但尚无 Quartz 表：将
     `FLYWAY_BASELINE_VERSION` 精确设为 `1.0`。baseline 后由应用正常执行 `V1_1`。
   - 现有库同时与 `V1_0` 和 `V1_1` 的最终状态完全等价，即 RuoYi 20 张表及种子、
     Quartz 11 张表均已存在：将 `FLYWAY_BASELINE_VERSION` 精确设为 `1.1`。

5. 只使用固定版本 `11.7.2` 的独立 Flyway CLI，并记录二进制来源、校验和及 `flyway -v`
   输出。连接信息和 baseline 版本放入权限受限、未跟踪的安全配置文件；其中必须设置
   `flyway.validateMigrationNaming=true`，并将 `flyway.locations` 精确设置为绝对
   `filesystem:` 路径，指向当前检出仓库的
   `ruoyi-admin/src/main/resources/db/migration`。禁止依赖 CLI 默认的 `filesystem:sql`。

   在 baseline 前先使用同一个安全配置执行：

   ```text
   flyway -configFiles=<未跟踪安全配置文件> info
   ```

   `info` 的 resolved migration 清单必须恰好包含本仓 `V1_0`、`V1_1`，不得出现其他
   filesystem/classpath 位置或版本。确认后再以相同配置人工执行一次：

   ```text
   flyway -configFiles=<未跟踪安全配置文件> baseline
   flyway -configFiles=<未跟踪安全配置文件> validate
   ```

   不要把密码放在命令行、脚本或 shell 历史中。核对历史表的 baseline 版本与人工
   选择完全一致；使用 `1.0` 时还必须确认随后新增一条成功的 `1.1` SQL 迁移。
6. 启动应用并再次执行结构、种子、登录和 Quartz 检查。DDL 或校验失败时立即停机并
   从第 1 步备份恢复，查清差异后重新走审计流程；不得 clean、repair、删除历史行或
   修改迁移文件来强行通过。

## 生产禁令

- 禁止在生产或任何共享环境设置 `baseline-on-migrate=true`；本仓库配置必须持续为
  `false`。已有库只能走上面的备份、人工审计和显式 `baseline 1.0` 或 `1.1` 流程。
- 禁止运行 `flyway clean` 或 `flyway repair`、手工删除历史行、修改已执行迁移或直接执行
  `sql/upstream` 文件。
- 禁止为了绕过校验而重命名旧迁移或重复使用语义相同的版本，例如 `1_0` 与 `1.0`。
