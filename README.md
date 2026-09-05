# 实验室安全与设备管理系统

基于 Spring Boot 3、Spring Security、MyBatis、MySQL、Redis、Vue 3 和 Element Plus 实现的前后端分离实验室管理系统。

当前业务覆盖实验室、设备、资格、预约、领用归还、维修、巡检、隐患整改、通知和角色权限。

本文提供两套独立的 Windows 本地运行方法，均不使用 Docker：

- **快速启动**：安装基础软件后，由项目脚本管理独立的数据库、Redis、前后端进程和演示数据。
- **完整手动部署**：自己安装软件、初始化 MySQL、创建数据库和账号、配置 Redis、下载依赖、设置环境变量并逐个启动服务，不调用项目启动脚本。

以下命令使用 **Windows PowerShell 5.1 或 PowerShell 7**，不是 CMD 或 Git Bash。示例源码目录是 `C:\Code\blackhorse`；路径不同请对应替换。SQL 代码块在 `mysql>` 中执行，INI、Redis 和 Nginx 配置块保存为指定文件，不要当作 PowerShell 命令执行。

## 目录

- [一、环境准备与软件下载](#prerequisites)
- [二、快速启动](#quick-start)
- [三、手动安装与初始化 MySQL](#manual-mysql)
- [四、手动配置 Redis 兼容服务](#manual-redis)
- [五、手动配置、构建和启动后端](#manual-backend)
- [六、手动安装依赖和启动前端](#manual-frontend)
- [七、登录、检查和日常启停](#verify-and-restart)
- [八、可选：打包前端并用 Nginx 本地部署](#local-release)
- [九、常见问题](#troubleshooting)
- [十、项目结构与更多文档](#project-layout)

<a id="prerequisites"></a>

## 一、环境准备与软件下载

### 1.1 安装基础软件

以下版本与当前仓库的 `pom.xml`、`ruoyi-ui/package.json` 和本机启动脚本对应。不要直接改用网页默认推荐的其他 Node/JDK 大版本。

| 软件 | 本项目使用的版本 | 官方下载与安装说明 |
|---|---|---|
| Git | Windows x64 版本 | [Git for Windows](https://git-scm.com/install/windows)，安装时允许命令行使用 Git |
| JDK | **17**，不是只有 JRE | [Eclipse Temurin](https://adoptium.net/temurin/releases/?version=17)，选择 Windows / x64 / JDK / 17 |
| Maven | **3.9.x**，本机脚本默认路径为 3.9.16 | [Maven 下载](https://maven.apache.org/download.cgi)，下载 Binary zip 并解压；[安装说明](https://maven.apache.org/install.html) |
| Node.js | **22.x** | [Node.js 下载](https://nodejs.org/en/download)，选择 22.x 和 Windows x64 安装包，包含 npm |
| Yarn Classic | **1.22.22** | 安装 Node 后执行下方命令；[Yarn Classic 安装说明](https://classic.yarnpkg.com/lang/en/docs/install/) |
| MySQL Community Server | **8.0.x** | [MySQL 8.0 下载](https://dev.mysql.com/downloads/mysql/8.0.html)，需包含 `mysqld.exe` 和 `mysql.exe`，不能只安装 Workbench |
| Redis 兼容服务 | Windows 原生 **Memurai** | [Memurai 下载](https://www.memurai.com/get-memurai)，选择稳定版本；[Windows 安装说明](https://docs.memurai.com/en/installation) |

本项目 Windows 路径使用 Memurai，不需要 WSL 或 Docker。Developer Edition 仅用于开发/测试，存在连续运行 10 天后需重启等限制，不可当作无限制生产版本使用，具体以 [官方版本说明](https://www.memurai.com/get-memurai) 为准。

安装 JDK、Maven 后，打开 Windows“编辑账户的环境变量”：

1. 新建用户变量 `JAVA_HOME`，值为 JDK 17 的安装目录，**不要带 `\bin`**。
2. 在用户 `Path` 中分别增加 `%JAVA_HOME%\bin` 和 Maven 安装目录的 `bin` 路径，不要覆盖原有 `Path`。
3. Node 安装程序通常会设置 `Path`；修改后重新打开 PowerShell。

也可以仅对当前窗口设置路径（先替换成自己机器上的实际目录）：

```powershell
$env:JAVA_HOME = 'C:\APP\JDK\jdk_17'
$env:Path = "$env:JAVA_HOME\bin;C:\Apache\Maven\apache-maven-3.9.16\bin;$env:Path"
npm.cmd install --global yarn@1.22.22

git --version
java -version
javac -version
mvn.cmd -version
node --version
yarn.cmd --version
```

确认 `java`、`javac` 和 `mvn -version` 显示的 Java 都是 17，Node 是 `v22.x`，Yarn 是 `1.22.22`。本文使用 `npm.cmd`、`yarn.cmd`，避免 PowerShell 错误选择受执行策略限制的同名 `.ps1` 文件。前端依赖以已提交的 `yarn.lock` 为准，不要混用 npm 安装并另生成一套锁文件。

### 1.2 获取代码

首次下载，在 PowerShell 中执行：

```powershell
New-Item -ItemType Directory -Path C:\Code -Force | Out-Null
Set-Location C:\Code
git clone https://github.com/Cthaat/blackhorse.git
Set-Location C:\Code\blackhorse
```

已有仓库时直接进入目录，不要重复克隆或覆盖自己的改动。

### 1.3 选择一种启动方式

| 项目 | 快速启动脚本 | 本文手动部署 |
|---|---|---|
| MySQL 端口 | `33306` | `3306`，已占用时复用合适的现有实例或自行换端口 |
| Redis 端口 | `36379` | `6379`，必须与配置一致 |
| 数据库名 / 应用账号 | `lab_management` / `lab_app` | `lab_management_manual` / `lab_manual` |
| 后端 / 开发前端 | `8080` / `5173` | `8080` / `5173` |
| 数据与配置目录 | 仓库下 `target/local-runtime` | 仓库外 `C:\LabLocal\blackhorse` |
| 进程管理 | `start-local.ps1` / `stop-local.ps1` | 各服务命令、前台窗口或自己配置的 Windows 服务 |

两种方式的前后端端口相同，**不要同时运行**。切换前先停止原方式启动的进程，不要按端口盲目结束其他程序。两套数据库是独立的，账号密码和业务数据不会自动同步。

<a id="quick-start"></a>

## 二、快速启动

### 2.1 安装前提

先完成第 1 节的基础软件安装。启动脚本**不是软件安装器**：它不会替你下载 JDK、Maven、MySQL、Memurai 或 Node。MySQL 和 Memurai 只需安装好可执行程序，不必预先创建本项目的 Windows 服务、数据库或导入 SQL。

在仓库根目录执行。以下默认路径适合当前开发机；其他机器请使用后一组参数：

```powershell
Set-Location C:\Code\blackhorse
.\scripts\start-local.ps1
```

自定义软件路径（示例路径必须替换为实际安装目录）：

```powershell
.\scripts\start-local.ps1 `
  -JavaHome 'C:\Tools\jdk-17' `
  -MavenCommand 'C:\Tools\apache-maven-3.9.16\bin\mvn.cmd' `
  -MySqlHome 'C:\Tools\mysql-8.0' `
  -MemuraiHome 'C:\Program Files\Memurai'
```

脚本会构建后端（跳过测试）、在缺少 `node_modules` 时安装前端依赖、初始化独立 MySQL 数据目录及应用账号、启动 Memurai、运行数据库迁移和演示数据初始化，再启动前后端。四个服务均只监听本机 `127.0.0.1`。

首次构建需要联网下载 Maven/Yarn 依赖。只有已经存在可用的 `ruoyi-admin/target/ruoyi-admin.jar` 时，才可加 `-SkipBuild`。修改后端代码后不要使用这个选项。已有进程全部健康时重复运行脚本会复用它们，**不会热更新正在运行的后端 JAR**。

### 2.2 访问和凭据

- 前端：[http://127.0.0.1:5173](http://127.0.0.1:5173)
- 后端验证码检查：[http://127.0.0.1:8080/captchaImage](http://127.0.0.1:8080/captchaImage)
- 账号密码：用本机编辑器打开 `target/local-runtime/credentials.json`；`rootAdmin` 是 `admin`，`demoAccounts` 是五类演示账号。**没有统一的公开默认密码**。
- 运行状态：`target/local-runtime/state.json`；服务日志：`target/local-runtime/logs`。
- 构建失败先看 `target/local-runtime/maven-build.log` 或 `target/local-runtime/yarn-install.log`。

该文件同时含数据库、Redis 和签名密钥，不要截图分享、上传或加入 Git。演示启动会按凭据文件校准 `admin` 和演示账号密码；在界面修改这几个账号的密码后，再次运行演示初始化可能恢复为文件中的密码。需要保留自行管理的账号密码时，使用手动部署并按第 5.5 节关闭初始化开关。

### 2.3 停止和更新

```powershell
Set-Location C:\Code\blackhorse
.\scripts\stop-local.ps1
# 修改代码或更新依赖后重新构建启动
.\scripts\start-local.ps1
```

停止脚本只管理记录为本项目所有的进程，并保留数据。前端依赖变化后，先进入 `ruoyi-ui` 执行 `yarn.cmd install --frozen-lockfile`，再启动。

当前停止脚本**不接收路径参数**；MySQL 优雅关闭使用默认安装目录下的 `mysqladmin.exe`。自定义 MySQL 路径且默认客户端不存在时，会回退为校验进程身份后终止。需要自行控制数据服务和优雅关闭时，推荐采用后面的手动部署流程。

**重要：脚本的数据和凭据在根目录 `target/local-runtime` 中。不要对这套数据所在的根目录执行 `mvn clean`，不要删除根目录 `target`，也不要把它当作可随意清理的构建缓存。** 备份应包含数据库导出、凭据、附件和 Redis 数据；备份文件也必须保密。

<a id="manual-mysql"></a>

## 三、手动安装与初始化 MySQL

从本节到第 7 节无需调用任何项目启动脚本。所有密码都由你自己设置，不使用当前开发机的凭据。

### 3.1 安装文件和数据目录

从第 1 节的 MySQL 下载页选择 Windows x64 ZIP，解压并将包含 `bin` 的目录放在 `C:\Tools\mysql-8.0`。解压目录内应直接存在 `bin\mysqld.exe` 和 `bin\mysql.exe`。如果提示缺少 Visual C++ 运行库，按 MySQL 安装提示安装微软对应的 x64 运行库后重试。

如果机器上已有可用 MySQL 8.0 服务，可以复用它，**跳过本节的初始化及额外实例启动，直接到第 3.3 节**，并把所有 `3306` 和客户端路径改为实际值。不要重新初始化已有实例的数据目录。

对于新建实例，先检查端口：

```powershell
Get-NetTCPConnection -State Listen -LocalPort 3306,6379,8080,5173 -ErrorAction SilentlyContinue |
  Select-Object LocalAddress,LocalPort,OwningProcess
```

某端口有输出表示已经被使用，先确认进程用途；不要直接终止。下面假设端口可用。

```powershell
New-Item -ItemType Directory -Force -Path `
  C:\LabLocal\blackhorse\mysql\data, `
  C:\LabLocal\blackhorse\redis\data, `
  C:\LabLocal\blackhorse\files\attachments, `
  C:\LabLocal\blackhorse\files\profile, `
  C:\LabLocal\blackhorse\logs | Out-Null
```

用编辑器新建 `C:\LabLocal\blackhorse\mysql\my.ini`，保存为 UTF-8，内容如下。配置文件中的路径使用 `/`：

```ini
[mysqld]
basedir=C:/Tools/mysql-8.0
datadir=C:/LabLocal/blackhorse/mysql/data
port=3306
bind-address=127.0.0.1
mysqlx=0
character-set-server=utf8mb4
collation-server=utf8mb4_0900_ai_ci
local-infile=0
```

只在这个**新建且空的 data 目录**上执行一次初始化：

```powershell
& 'C:\Tools\mysql-8.0\bin\mysqld.exe' '--defaults-file=C:\LabLocal\blackhorse\mysql\my.ini' --initialize --console
```

初始化输出中会有 `root@localhost` 的临时随机密码，请自行保管；初始化结束后进程退出是正常的。不要分享这段输出。这里采用有临时密码的初始化方式，见 [MySQL 数据目录初始化说明](https://dev.mysql.com/doc/refman/8.0/en/data-directory-initialization.html)。

### 3.2 启动 MySQL（窗口 A）

打开一个 PowerShell 窗口，执行并保持运行：

```powershell
& 'C:\Tools\mysql-8.0\bin\mysqld.exe' '--defaults-file=C:\LabLocal\blackhorse\mysql\my.ini' --console
```

看到 `ready for connections` 后，在**另一个 PowerShell 窗口**连接。`-p` 后不写密码，在提示符中输入第 3.1 节生成的临时密码：

```powershell
& 'C:\Tools\mysql-8.0\bin\mysql.exe' --protocol=TCP -h 127.0.0.1 -P 3306 -u root -p --connect-expired-password
```

新实例先在 `mysql>` 中修改 root 密码。把下面占位内容换成自己设置的强密码，不要原样执行；示例密码建议使用字母、数字和常见符号，不包含 SQL 单引号或反斜杠，以免转义错误：

```sql
ALTER USER 'root'@'localhost' IDENTIFIED BY '<自行设置的MySQL管理员密码>';
```

### 3.3 创建业务数据库和专用账号

如果复用已有实例，使用其 MySQL 管理员密码连接，而不是新实例的临时密码。在 `mysql>` 中执行下面 SQL，先替换应用密码占位内容：

```sql
CREATE DATABASE lab_management_manual
  CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE USER 'lab_manual'@'127.0.0.1' IDENTIFIED BY '<自行设置的数据库应用密码>';
GRANT ALL PRIVILEGES ON lab_management_manual.* TO 'lab_manual'@'127.0.0.1';
SHOW GRANTS FOR 'lab_manual'@'127.0.0.1';
EXIT;
```

应用账号的权限仅限本项目数据库；建表、建索引和结构升级都由 Flyway 执行，所以首次初始化需要库内 DDL 权限。不要配置应用连接为 `root`，也不需要授予全服务器权限。

如果提示数据库或用户已存在，先检查是否为本项目已有数据；本教程故意不覆盖它们。改用新的库名/账号或确认已有配置，不要通过删库来“修复”。

用应用账号重新登录，输入刚设置的**数据库应用密码**：

```powershell
& 'C:\Tools\mysql-8.0\bin\mysql.exe' --protocol=TCP -h 127.0.0.1 -P 3306 -u lab_manual -p lab_management_manual
```

在 `mysql>` 中执行：

```sql
SELECT DATABASE(), CURRENT_USER(), VERSION();
SHOW TABLES;
EXIT;
```

全新数据库此时应当没有业务表。**不要导入 `sql/upstream/ry_20260417.sql` 或 `quartz.sql`，也不要手工逐个执行 `V*.sql`。** 当前有效迁移位于 `ruoyi-admin/src/main/resources/db/migration`，已包含基础表、Quartz 表、实验室业务表、菜单、字典及增量升级。后端首次启动时自动按版本执行并登记 `flyway_schema_history`；手工导入会破坏这个流程。

<a id="manual-redis"></a>

## 四、手动配置 Redis 兼容服务

### 4.1 安装 Memurai

运行官网下载的 Memurai MSI。安装程序需要管理员权限；本教程选择**不注册 Windows 服务、不添加防火墙放行规则**，后续普通窗口启动一个只监听本机的实例。默认软件目录为 `C:\Program Files\Memurai`。

如果已安装 Memurai，无需重装。先确认 `6379` 是否已由它的 Windows 服务使用：要么复用并配置那个实例，要么为本教程选择另一个端口，不能启动两个同端口实例。

### 4.2 配置文件

用编辑器新建 `C:\LabLocal\blackhorse\redis\memurai.conf`，将密码占位内容改成自己设置的 Redis 强密码（建议不含双引号或反斜杠），保存为 UTF-8：

```text
bind 127.0.0.1
protected-mode yes
port 6379
requirepass "<自行设置的Redis密码>"
dir "C:/LabLocal/blackhorse/redis/data"
dbfilename dump.rdb
save 60 1
appendonly yes
daemonize no
```

这是私有配置文件，包含密码，不要放到仓库中或分享。`C:\LabLocal\blackhorse` 应只供当前开发用户和确有需要的服务账号访问。

### 4.3 启动及验证（窗口 B）

新开 PowerShell 窗口，启动并保持运行：

```powershell
& 'C:\Program Files\Memurai\memurai.exe' 'C:\LabLocal\blackhorse\redis\memurai.conf'
```

在另一个窗口执行客户端检查，按提示输入 Redis 密码：

```powershell
& 'C:\Program Files\Memurai\memurai-cli.exe' -h 127.0.0.1 -p 6379 --askpass ping
```

返回 `PONG` 才继续。应用默认使用 Redis 的 **0 号库**，请使用独立本地实例，避免和其他项目共用键空间。若复用自己的 Redis 服务，也必须保持地址、端口、密码与下一节一致。

<a id="manual-backend"></a>

## 五、手动配置、构建和启动后端

### 5.1 下载后端依赖并构建

在仓库根目录执行，不要只在 `ruoyi-admin` 子目录孤立构建：

```powershell
Set-Location C:\Code\blackhorse
mvn.cmd -pl ruoyi-admin -am -DskipTests package
```

Maven 会根据根 `pom.xml` 下载依赖并构建所有必要模块，不用手工下载 Spring Boot、JDBC 或其他 JAR。首次执行需要网络，看到 `BUILD SUCCESS` 后检查产物：

```powershell
Test-Path .\ruoyi-admin\target\ruoyi-admin.jar
```

应返回 `True`。`-DskipTests` 是跳过测试执行、先得到运行包，不代表测试已经通过。此处不执行 `clean`，以免误清理快速启动的数据目录。

### 5.2 设置后端连接配置（窗口 C）

新开一个用于运行后端的 PowerShell，确认 JDK 17 的路径设置仍有效。在**这个窗口**执行以下内容；两个密码通过隐藏输入读取，不写入命令历史：

```powershell
Set-Location C:\Code\blackhorse
$env:SERVER_ADDRESS = '127.0.0.1'
$env:SERVER_PORT = '8080'
$env:SPRING_PROFILES_ACTIVE = 'druid'
$env:LAB_DB_URL = 'jdbc:mysql://127.0.0.1:3306/lab_management_manual?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia%2FShanghai&allowPublicKeyRetrieval=true&useSSL=false'
$env:LAB_DB_USERNAME = 'lab_manual'
$env:LAB_DB_PASSWORD = [System.Net.NetworkCredential]::new('', (Read-Host '输入第3.3节的数据库应用密码' -AsSecureString)).Password
$env:LAB_DB_SLAVE_ENABLED = 'false'
$env:LAB_REDIS_HOST = '127.0.0.1'
$env:LAB_REDIS_PORT = '6379'
$env:LAB_REDIS_PASSWORD = [System.Net.NetworkCredential]::new('', (Read-Host '输入第4.2节的Redis密码' -AsSecureString)).Password
$env:LAB_FILE_ROOT = 'C:\LabLocal\blackhorse\files\attachments'
$env:LAB_PROFILE_ROOT = 'C:\LabLocal\blackhorse\files\profile'
$env:LAB_LOG_ROOT = 'C:\LabLocal\blackhorse\logs'
```

再用密码管理器生成并保存一个随机签名密钥（例如至少 32 字节随机数据对应的 64 位十六进制文本），在同一窗口输入：

```powershell
$env:LAB_TOKEN_SECRET = [System.Net.NetworkCredential]::new('', (Read-Host '输入并保管固定的随机Token签名密钥' -AsSecureString)).Password
```

| 配置 | 对应代码与作用 |
|---|---|
| `LAB_DB_URL` / `LAB_DB_USERNAME` / `LAB_DB_PASSWORD` | 同时供 Druid 数据源和 Flyway 使用，三项缺一不可 |
| `LAB_REDIS_HOST` / `LAB_REDIS_PORT` / `LAB_REDIS_PASSWORD` | Spring Data Redis 连接；Redis 库索引在当前 `application.yml` 中为 0 |
| `LAB_TOKEN_SECRET` | 固定签名密钥；不配置时会产生随机默认值，重启会导致原登录令牌失效 |
| `LAB_FILE_ROOT` | 受权限保护的实验室附件存储，不应直接暴露为静态目录 |
| `LAB_PROFILE_ROOT` | 头像、临时上传及导出等系统文件路径 |
| `LAB_LOG_ROOT` | Logback 日志路径 |
| `SPRING_PROFILES_ACTIVE` | 本地使用 `druid`；不要只改为 `prod` 而漏掉数据源配置 |

`useSSL=false` 和 `allowPublicKeyRetrieval=true` 仅用于本文受限的本机连接，不是远程生产数据库的安全配置范本。文件目录必须对启动 Java 的用户可写。

**环境变量只对当前窗口及其启动的子进程生效。** 新开窗口、IDE 或重启电脑不会继承这里设置的临时值，需要重新配置；Spring Boot 不会自动读取仓库根目录的 `.env`。可以把非敏感配置保存到自己的笔记，并在运行时重新输入密码，不要把真实密码写进已跟踪的 `application*.yml`。

### 5.3 首次初始化管理员、角色账号和演示数据

为了让新库能直接登录体验，在**首次启动前、同一个后端窗口**设置：

```powershell
$env:LAB_DEMO_DATA_ENABLED = 'true'
$env:LAB_ROOT_ADMIN_PASSWORD = [System.Net.NetworkCredential]::new('', (Read-Host '设置 admin 登录密码（5至20字符，建议16至20字符）' -AsSecureString)).Password
$env:LAB_DEMO_STUDENT_PASSWORD = [System.Net.NetworkCredential]::new('', (Read-Host '设置 lab_student 登录密码' -AsSecureString)).Password
$env:LAB_DEMO_MANAGER_PASSWORD = [System.Net.NetworkCredential]::new('', (Read-Host '设置 lab_manager 登录密码' -AsSecureString)).Password
$env:LAB_DEMO_SAFETY_PASSWORD = [System.Net.NetworkCredential]::new('', (Read-Host '设置 lab_safety_officer 登录密码' -AsSecureString)).Password
$env:LAB_DEMO_REPAIR_PASSWORD = [System.Net.NetworkCredential]::new('', (Read-Host '设置 lab_repair_worker 登录密码' -AsSecureString)).Password
$env:LAB_DEMO_ADMIN_PASSWORD = [System.Net.NetworkCredential]::new('', (Read-Host '设置 lab_system_admin 登录密码' -AsSecureString)).Password
```

六个登录密码均须为 **5～20 个字符且非空白**，建议为每个账号分别设置 16～20 位随机强密码，并保存到密码管理器。登录密码不是 MySQL/Redis 密码，也不是 Token 密钥，不要混用。初始化会生成角色账号、实验室、设备、资格和各业务状态的演示数据及示例附件，不需要手工插入业务 SQL。

**这个开关不只是“补充数据”：每次启用都会按上述环境变量校准 `admin` 和受管演示账号的密码、资料及角色。** 已有同名但不属于初始化器管理的账号、部分残缺的演示数据等会导致初始化拒绝，不能把它当作任意业务数据库的修复工具。`prod` 模式明确禁止启用演示初始化。

此时保持 `LAB_DEMO_DATA_ENABLED=true`，继续下一节首次启动；完成初始化后才执行第 5.5 节，不能在首次启动前提前关闭。

### 5.4 启动后端及自动建表

MySQL（窗口 A）和 Memurai（窗口 B）正常运行后，在已配置好环境变量的窗口 C 执行并保持运行：

```powershell
java -jar .\ruoyi-admin\target\ruoyi-admin.jar
```

首次启动会按顺序执行：连接数据库 → 校验/执行 Flyway 迁移 → 初始化框架数据 → 根据开关初始化演示账号及业务数据。请等待迁移和初始化结束，不能仅以端口出现监听判断全部成功；若初始化报错，应检查完整日志。

在另一个窗口检查匿名验证码接口，只展示结果字段、不输出验证码图像：

```powershell
$captchaCheck = Invoke-RestMethod 'http://127.0.0.1:8080/captchaImage'
$captchaCheck | Select-Object code,captchaEnabled
```

正常应返回业务码 `200`。本地接口文档可访问 [Swagger UI](http://127.0.0.1:8080/swagger-ui.html) 或 [Knife4j](http://127.0.0.1:8080/doc.html)；业务接口仍需登录令牌。

重新用第 3.3 节的应用账号连接数据库，在 `mysql>` 中确认迁移和账号：

```sql
SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank;
SHOW TABLES LIKE 'lab_%';
SELECT user_name, status FROM sys_user
WHERE user_name IN ('admin','lab_student','lab_manager','lab_safety_officer','lab_repair_worker','lab_system_admin');
```

迁移记录应全部 `success=1`；启用首次演示初始化后应有六个可用账号。不要在这里查询或分享密码散列。日后拉取带新迁移的代码，备份数据库后重新构建并启动，Flyway 会执行尚未应用的版本；**不得编辑已执行的旧迁移文件或手改校验和**。

### 5.5 首次成功后关闭演示初始化

确认上一节启动成功、数据库中已有六个可用账号后，在窗口 C 按 `Ctrl+C` 正常停止后端，再在这个窗口执行：

```powershell
$env:LAB_DEMO_DATA_ENABLED = 'false'
java -jar .\ruoyi-admin\target\ruoyi-admin.jar
```

以后始终使用 `false`，已创建的账号和数据不会因此被删除，界面修改的密码也不会再被初始化器改回。关闭后无需再配置六个初始化密码，但数据库、Redis 和签名密钥仍需保留。等待后端重新启动成功后，继续配置前端。

<a id="manual-frontend"></a>

## 六、手动安装依赖和启动前端

新开 PowerShell（窗口 D），执行：

```powershell
Set-Location C:\Code\blackhorse\ruoyi-ui
yarn.cmd install --frozen-lockfile
```

等待安装成功。不是直接双击 `index.html`，也不要跳过依赖安装。

仓库的 `.env.development` 已将 API 前缀设置为 `/dev-api`。在窗口 D 显式指定实际后端地址，然后启动：

```powershell
$env:VITE_APP_PROXY_TARGET = 'http://127.0.0.1:8080'
yarn.cmd dev --host 127.0.0.1 --port 5173 --strictPort
```

打开 [http://127.0.0.1:5173](http://127.0.0.1:5173)。浏览器请求 `/dev-api/...`，由 Vite 转发到 `8080` 并去掉 `/dev-api` 前缀，因此无需修改业务页面里的 API 路径。

也可以用编辑器新建 `ruoyi-ui/.env.development.local` 保存非敏感代理配置：

```dotenv
VITE_APP_PROXY_TARGET=http://127.0.0.1:8080
```

修改 `.env` 或后端端口后，需要重启 Vite；已设置的同名进程环境变量优先于 `.env` 文件。**不要在任何 `VITE_*` 变量中放数据库密码或 Token 签名密钥**，前端环境变量可能进入浏览器构建产物。

<a id="verify-and-restart"></a>

## 七、登录、检查和日常启停

### 7.1 登录账号

| 用户名 | 角色/用途 | 手动部署时密码来源 |
|---|---|---|
| `admin` | 超级管理员，管理账号、权限和全部业务 | `LAB_ROOT_ADMIN_PASSWORD` |
| `lab_student` | 学生，资格、个人预约和使用等 | `LAB_DEMO_STUDENT_PASSWORD` |
| `lab_manager` | 实验室管理员，资产、审批及业务处置 | `LAB_DEMO_MANAGER_PASSWORD` |
| `lab_safety_officer` | 安全员，巡检和隐患相关工作 | `LAB_DEMO_SAFETY_PASSWORD` |
| `lab_repair_worker` | 维修人员，维修工单处理 | `LAB_DEMO_REPAIR_PASSWORD` |
| `lab_system_admin` | 普通系统管理员，不等同于超级管理员 | `LAB_DEMO_ADMIN_PASSWORD` |

使用对应密码，并填写图片中算式的计算结果。首页功能入口取决于该账号的实际权限；普通角色少一些菜单是预期行为。新环境建议先用 `admin` 登录检查，再逐一体验普通角色。

首次密码提醒属于安全提示，按界面引导修改密码；修改前按第 5.5 节关闭演示初始化。不要套用其他若依项目教程的默认用户名/密码。本项目没有公开注册入口。

### 7.2 最小运行检查

- 验证码正常加载、登录成功，首页可以看到对应角色入口。
- 设备列表和实验室列表能加载真实数据，浏览器请求没有连接拒绝或代理 502。
- MySQL 中 `flyway_schema_history` 没有失败记录；Redis 客户端能收到 `PONG`。
- 后端日志在 `C:\LabLocal\blackhorse\logs`；前端错误看窗口 D 和浏览器开发者工具。

演示数据时间基于**首次初始化时刻**生成，不会每天自动刷新；过期预约、超时任务等可能随时间和定时任务变化，不要把数量变化误判为部署失败。

### 7.3 手动停止与再次启动

1. 在窗口 D 按 `Ctrl+C` 停止 Vite；若已改用 Nginx，按第 8 节优雅退出。
2. 在窗口 C 按 `Ctrl+C` 停止 Spring Boot，等待退出。
3. 在另一个窗口执行下方 Memurai、MySQL 关闭命令，输入各自管理员密码。仅对本文自己启动的实例操作，不停止他人共享服务。

```powershell
& 'C:\Program Files\Memurai\memurai-cli.exe' -h 127.0.0.1 -p 6379 --askpass shutdown save
& 'C:\Tools\mysql-8.0\bin\mysqladmin.exe' --protocol=TCP -h 127.0.0.1 -P 3306 -u root -p shutdown
```

下次按 **MySQL → Memurai → 后端 → 前端** 的顺序启动。不要再次执行 `--initialize`、建库 SQL 或首次演示初始化。新开的后端窗口要重新设置第 5.2 节连接变量及 `LAB_DEMO_DATA_ENABLED=false`。

手动部署不使用项目的 `state.json`，所以 `stop-local.ps1` **不会代替你管理这些手动进程**。数据库、附件、私有配置和密码需要自行备份；备份目录不要放在 `target` 或 `node_modules` 中。需要开机自启时再按各软件官方说明注册服务，本文的前台运行不等于已安装自启服务。

<a id="local-release"></a>

## 八、可选：打包前端并用 Nginx 本地部署

前面是便于开发的 Vite 运行方式。想在不运行 Vite 的情况下访问打包后的前端，可以继续本节；后端、MySQL 和 Memurai 仍需要运行。

### 8.1 构建静态文件

```powershell
Set-Location C:\Code\blackhorse\ruoyi-ui
yarn.cmd build:prod
```

产物在 `ruoyi-ui/dist`。`.env.production` 的 API 前缀为 `/prod-api`，所以不能简单用任意静态服务器打开后就期待接口可用。`yarn preview` 不是本项目完整部署方案，也不会自动得到下方 `/prod-api` 代理。

### 8.2 安装和配置 Nginx

从 [Nginx 官方下载页](https://nginx.org/en/download.html) 下载 Windows 稳定版 ZIP，解压到 `C:\LabLocal\nginx`，确认其下直接有 `nginx.exe`、`conf`、`html`。参考 [Windows 运行说明](https://nginx.org/en/docs/windows.html)。这里仅用于本机打包验证，不承诺 Windows Nginx 的生产性能。

新建 `C:\LabLocal\nginx\html\lab`，将 `dist` 里的**全部内容**复制进去，让 `html\lab\index.html` 直接存在。后续更新前先备份旧发布目录，避免混入旧资源。

备份已有 `conf/nginx.conf`，然后用以下完整配置替换这个**新安装的本项目 Nginx 实例**的配置；不要覆盖机器上其他业务正在使用的 Nginx 配置：

```nginx
worker_processes 1;
events { worker_connections 1024; }
http {
    include mime.types;
    default_type application/octet-stream;
    sendfile on;
    server {
        listen 127.0.0.1:8088;
        server_name localhost;
        root C:/LabLocal/nginx/html/lab;
        index index.html;
        client_max_body_size 20m;

        location / {
            try_files $uri $uri/ /index.html;
        }
        location /prod-api/ {
            proxy_pass http://127.0.0.1:8080/;
            proxy_set_header Host $host;
            proxy_set_header X-Real-IP $remote_addr;
            proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
            proxy_set_header X-Forwarded-Proto $scheme;
        }
    }
}
```

`proxy_pass` 尾部的 `/` 用来去掉 `/prod-api/` 前缀；漏掉它会把错误路径传给后端。`try_files` 让刷新 Vue 业务路由时仍返回 `index.html`。上传仍受后端单文件 10 MB、单请求 20 MB 等业务限制。

### 8.3 启动、检查和关闭

```powershell
Set-Location C:\LabLocal\nginx
.\nginx.exe -t
# 只有配置检查通过后才启动这个本机实例
if ($LASTEXITCODE -ne 0) { throw 'Nginx 配置检查失败，请先修复' }
Start-Process -FilePath 'C:\LabLocal\nginx\nginx.exe' -WorkingDirectory 'C:\LabLocal\nginx' -WindowStyle Hidden
```

访问 [http://127.0.0.1:8088](http://127.0.0.1:8088)，确认可以登录，直接刷新业务页面也不报 404；访问 `http://127.0.0.1:8088/prod-api/captchaImage` 应返回验证码 JSON。

修改配置后先检查再重载：

```powershell
Set-Location C:\LabLocal\nginx
.\nginx.exe -t
if ($LASTEXITCODE -ne 0) { throw 'Nginx 配置检查失败，请先修复' }
.\nginx.exe -s reload
```

不再使用这个实例时，从同一个 Nginx 目录优雅关闭：

```powershell
Set-Location C:\LabLocal\nginx
.\nginx.exe -s quit
```

这是**本机部署**，不是公网生产上线教程。若未来上线，至少需要 TLS、正式数据库与备份、合适的 Redis/Memurai 许可和服务守护，并关闭演示初始化；后端使用 `SPRING_PROFILES_ACTIVE=druid,prod` 时会关闭接口文档。不能把这里的本地密码、HTTP、端口或开发服务直接暴露到公网。

<a id="troubleshooting"></a>

## 九、常见问题

| 现象 | 检查和处理 |
|---|---|
| 找不到 `java` / `mvn` / `yarn` | 检查实际安装目录、`JAVA_HOME`、`Path`，新开终端后再验证；Maven 使用的 JDK 也必须是 17 |
| 不允许运行 `start-local.ps1` | 审阅脚本后可仅对本次进程执行 `powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\start-local.ps1`；有组织策略时遵守策略，不要全局关闭安全限制；也可选择手动部署 |
| Node engine 不兼容或锁文件安装失败 | 使用 Node 22 和 Yarn 1.22.22，不加 `--ignore-engines`，不要删除 `yarn.lock` 重新解析全部依赖 |
| Maven/Yarn 下载失败 | 检查网络、代理及终端使用的仓库。根 POM 配置了阿里云仓库；需要镜像/代理时修改自己的 Maven `settings.xml`，不要提交含认证信息的配置；可在网络恢复后重试 `mvn.cmd -U -pl ruoyi-admin -am -DskipTests package` |
| `Access denied for user` | 检查是否连错 `3306/33306`，应用账号是不是 `lab_manual`，密码是否为数据库应用密码，以及 MySQL 账号允许的主机是否匹配 `127.0.0.1` |
| `Unknown database` | 第 3.3 节还没建库，或 `LAB_DB_URL` 中的库名不一致；Flyway 建表，不负责替你创建 MySQL 数据库 |
| `Could not resolve placeholder LAB_DB_*` | 环境变量没有设置到启动 Java 的那个窗口；IDE 需要另外配置运行环境，根 `.env` 不会自动加载 |
| Flyway 提示非空库没有 history 或校验和不匹配 | 常见于手工导入旧 SQL、改了历史迁移或连接到错误库；先备份并定位，不要关闭校验、开启 baseline 或随意 repair 来掩盖问题 |
| Redis `NOAUTH` / `WRONGPASS` / 连接拒绝 | 用 `memurai-cli --askpass ping` 验证；确保服务运行、密码一致，且没有混淆 `6379/36379`；Developer Edition 长时间运行后可能需要重启 |
| 演示初始化提示缺少环境变量或账号/数据冲突 | 首次需六个 5～20 字符密码；已有业务库不要反复强行初始化。首次成功后使用 `LAB_DEMO_DATA_ENABLED=false`，不要删除记录或改受管标记绕过校验 |
| 密码在重启后变回去 | 演示初始化仍开启；按第 5.5 节关闭后再修改密码。快速脚本会始终启用演示初始化并使用本机凭据文件 |
| 前端打开但验证码失败 / 请求 502 | 先检查后端 `/captchaImage`；Vite 使用 `/dev-api`，Nginx 打包部署使用 `/prod-api`。确认代理目标地址，修改 `.env` 后重启前端 |
| API 返回 401/403 或普通角色没有某个菜单 | 分别检查登录是否过期、角色权限及业务数据范围，不要通过去掉权限校验解决 |
| 端口被占用或 Vite 换到了 5174 | 本文使用 `--strictPort` 防止静默换端口；先确认占用者。手动换后端端口时同步修改代理目标，快速脚本端口为固定值 |
| 附件/导出提示路径权限错误 | 检查 `LAB_FILE_ROOT`、`LAB_PROFILE_ROOT` 和 `LAB_LOG_ROOT` 是否存在且当前用户可写；不要将附件根目录配置为 Nginx 公共静态目录 |
| 修改后端代码后没有生效 | 停止旧 Java 进程，重新构建，再启动新 JAR；快速脚本运行中会复用旧进程，`-SkipBuild` 也不会生成新包 |

<a id="project-layout"></a>

## 十、项目结构与更多文档

| 目录/文件 | 用途 |
|---|---|
| `ruoyi-admin` | Spring Boot 入口、配置、Flyway 迁移和本地演示初始化 |
| `ruoyi-lab` | 实验室核心业务、权限与状态流转 |
| `ruoyi-framework` / `ruoyi-common` / `ruoyi-system` / `ruoyi-quartz` | 安全、公共能力、系统管理与调度模块；保留这些技术模块名称不代表它们是不需要的业务页面 |
| `ruoyi-ui` | Vue 3 前端，依赖锁文件是 `yarn.lock` |
| `scripts/start-local.ps1` / `scripts/stop-local.ps1` | 可选的 Windows 本机进程管理工具 |
| `ruoyi-admin/src/main/resources/db/migration` | 唯一有效的版本化数据库迁移入口 |
| `sql/upstream` | 上游 SQL 留档，不作为新环境部署导入入口 |

更多背景可阅读[需求规格说明书](docs/requirements/lab-management-srs.md)、[总体设计](docs/superpowers/specs/2026-09-01-lab-management-design.md)和[前端本轮实施与验证范围](docs/superpowers/plans/2026-09-05-lab-workspace-ui.md)。部署操作以本文和当前代码配置为准，早期计划中的历史状态不代表当前实现状态。

后续功能见[求职导向扩展需求池](docs/requirements/lab-extension-backlog.md)：面向 Java 后端实习／校招／初级岗位，包含原有 26 项与新增 16 项候选、前后端交付边界、验收目标和推荐开发顺序。当前建议先深化预约规则／候补与业务追溯，再做可靠投递、异步导入导出和可观测性。需求池条目不代表已经实现，也不要求全部完成后才能用于求职展示。
