# 上游冻结基线

## 固定快照

导入日期：2026-09-01（Asia/Shanghai）。本仓库使用固定提交而不是分支最新状态。

| 组件 | 上游仓库 | 上游分支 | 固定提交 | Git tree | 许可证 |
| --- | --- | --- | --- | --- | --- |
| 后端 | `https://gitee.com/y_project/RuoYi-Vue.git` | `springboot3` | `a51a838b71b446ea27256900efe7ed2faa2a02fd` | `408ccdb2ae9879fff34501ca7b587bfb81dff7dd` | MIT（根目录 `LICENSE`） |
| 前端 | `https://github.com/yangzongzhuan/RuoYi-Vue3.git` | `master` | `838965c5a18d2c61b73ec30c6e288057aaa08b63` | `16da20f5a7060db5d33b023b6793cbc167966d9a` | MIT（`ruoyi-ui/LICENSE`） |

## 导入边界与已知冲突

- 后端导入仓库根目录，排除上游 `.git` 元数据和为本项目批准文档保留的 `docs/` 路径。该快照实际提供的是 `doc/`，已原样导入。
- 根 `.gitignore` 在导入前已经包含 `.worktrees/`。导入脚本将它作为唯一允许的合并目标；本文件保留该规则并合入后端上游忽略规则，没有覆盖其他根路径。
- 前端快照放在 `ruoyi-ui/`，不保留其 `.git` 元数据。
- 前端上游 `.gitignore` 原本忽略 `yarn.lock`；本仓库删除该规则，统一追踪 Yarn 锁文件。`package-lock.json` 仍保持忽略，以避免混用包管理器。
- 当前仓库按 Git 的 `core.autocrlf=true` 规则正常暂存：可识别文本从上游工作树的 CRLF 规范化为索引中的 LF，二进制内容逐字节保留。该行尾规范化不改变固定提交来源或程序文本内容。
- 上游快照中的 `.env.development`、`.env.staging` 和 `.env.production` 仅包含公开的 Vite 环境名称、基础路径和压缩选项；没有导入裸 `.env`、私钥文件或本地凭据。

## 复核命令

在仓库外的任务专属临时目录重新获取上游，并核对固定提交：

```powershell
$importRoot = Join-Path ([System.IO.Path]::GetTempPath()) ("blackhorse-upstream-review-" + [guid]::NewGuid())
New-Item -ItemType Directory -Path $importRoot | Out-Null

git clone --branch springboot3 --single-branch https://gitee.com/y_project/RuoYi-Vue.git (Join-Path $importRoot 'backend')
git -C (Join-Path $importRoot 'backend') checkout --detach a51a838b71b446ea27256900efe7ed2faa2a02fd
git -C (Join-Path $importRoot 'backend') rev-parse HEAD
git -C (Join-Path $importRoot 'backend') rev-parse 'HEAD^{tree}'

git clone https://github.com/yangzongzhuan/RuoYi-Vue3.git (Join-Path $importRoot 'frontend')
git -C (Join-Path $importRoot 'frontend') checkout --detach 838965c5a18d2c61b73ec30c6e288057aaa08b63
git -C (Join-Path $importRoot 'frontend') rev-parse HEAD
git -C (Join-Path $importRoot 'frontend') rev-parse 'HEAD^{tree}'
```

在当前仓库核对导入边界：

```powershell
$workspaceRoot = git rev-parse --show-toplevel
Get-ChildItem -LiteralPath $workspaceRoot -Directory -Force -Recurse -Filter .git
rg --files -g '.git' -g '*.pem' -g '*.key' -g '.env'
git diff --check
git status --short
```

上述两个固定提交的后续升级只能通过单独的 ADR 提出、评审和批准；不得直接移动分支基线或静默替换上游文件。
