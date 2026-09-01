# 上游冻结基线

## 固定快照

导入日期：2026-09-01（Asia/Shanghai）。本仓库使用固定提交而不是分支最新状态。

| 组件 | 上游仓库 | 上游分支 | 固定提交 | Git tree | 许可证 |
| --- | --- | --- | --- | --- | --- |
| 后端 | `https://gitee.com/y_project/RuoYi-Vue.git` | `springboot3` | `a51a838b71b446ea27256900efe7ed2faa2a02fd` | `408ccdb2ae9879fff34501ca7b587bfb81dff7dd` | MIT（根目录 `LICENSE`） |
| 前端 | `https://github.com/yangzongzhuan/RuoYi-Vue3.git` | `master` | `838965c5a18d2c61b73ec30c6e288057aaa08b63` | `16da20f5a7060db5d33b023b6793cbc167966d9a` | MIT（`ruoyi-ui/LICENSE`） |

## 导入边界与已知冲突

- 后端导入仓库根目录，排除上游 `.git` 元数据和为本项目批准文档保留的 `docs/` 路径。该快照实际提供的是 `doc/`，已原样导入。
- 冻结提交中的根 `.gitignore` 在导入前已经包含 `.worktrees/`。导入脚本将它作为唯一允许的合并目标，并精确合入后端上游忽略规则，没有覆盖其他根路径；后续质量修复追加的本地秘密文件规则不属于上游变换。
- 前端快照放在 `ruoyi-ui/`，不保留其 `.git` 元数据。
- 冻结提交中的前端 `.gitignore` 相对上游只删除 `yarn.lock`，统一追踪 Yarn 锁文件。`package-lock.json` 仍保持忽略，以避免混用包管理器；后续质量修复追加的 `coverage/` 不属于上游变换。
- 当前仓库按 Git 的 `core.autocrlf=true` 规则正常暂存：可识别文本从上游工作树的 CRLF 规范化为索引中的 LF，二进制内容逐字节保留。该行尾规范化不改变固定提交来源或程序文本内容。
- 上游快照中的 `.env.development`、`.env.staging` 和 `.env.production` 仅包含公开的 Vite 环境名称、基础路径和压缩选项；没有导入裸 `.env`、私钥文件或本地凭据。

## 确定性复核命令

以下 PowerShell 流程不读取当前工作树中的导入文件作为事实源。它按精确提交主题定位唯一冻结提交，将该提交归档到唯一临时目录，再用两个固定上游提交重建期望路径和内容。任一 Git 命令失败、路径缺失或多余、内容不符、忽略规则变换超出批准范围，流程都会以非零状态失败。

```powershell
$ErrorActionPreference = 'Stop'

$freezeSubject = 'chore: freeze ruoyi backend and frontend baselines'
$backendCommit = 'a51a838b71b446ea27256900efe7ed2faa2a02fd'
$backendTree = '408ccdb2ae9879fff34501ca7b587bfb81dff7dd'
$frontendCommit = '838965c5a18d2c61b73ec30c6e288057aaa08b63'
$frontendTree = '16da20f5a7060db5d33b023b6793cbc167966d9a'
$reviewRoot = $null

function Invoke-Git {
    param([Parameter(Mandatory = $true)][string[]] $GitArguments)

    $output = @(& git @GitArguments 2>&1)
    $exitCode = $LASTEXITCODE
    if ($exitCode -ne 0) {
        throw "git $($GitArguments -join ' ') failed with exit code $exitCode.`n$($output -join [Environment]::NewLine)"
    }
    return $output
}

function Get-GitValue {
    param([Parameter(Mandatory = $true)][string[]] $GitArguments)

    $lines = @(Invoke-Git -GitArguments $GitArguments)
    if ($lines.Count -ne 1) {
        throw "Expected one output line from git $($GitArguments -join ' '), got $($lines.Count)."
    }
    return $lines[0].Trim()
}

function Assert-Exact {
    param(
        [Parameter(Mandatory = $true)][string] $Label,
        [Parameter(Mandatory = $true)][string] $Actual,
        [Parameter(Mandatory = $true)][string] $Expected
    )

    if (-not [string]::Equals($Actual, $Expected, [System.StringComparison]::Ordinal)) {
        throw "$Label mismatch: expected '$Expected', got '$Actual'."
    }
}

function Assert-LineSequence {
    param(
        [Parameter(Mandatory = $true)][string] $Label,
        [Parameter(Mandatory = $true)][AllowEmptyString()][string[]] $Expected,
        [Parameter(Mandatory = $true)][AllowEmptyString()][string[]] $Actual
    )

    if ($Expected.Count -ne $Actual.Count) {
        throw "$Label line-count mismatch: expected $($Expected.Count), got $($Actual.Count)."
    }
    for ($index = 0; $index -lt $Expected.Count; $index++) {
        if (-not [string]::Equals($Expected[$index], $Actual[$index], [System.StringComparison]::Ordinal)) {
            throw "$Label differs at line $($index + 1)."
        }
    }
}

function Test-BinaryFile {
    param([Parameter(Mandatory = $true)][string] $Path)

    $stream = [System.IO.File]::OpenRead($Path)
    try {
        $buffer = New-Object byte[] 8000
        $read = $stream.Read($buffer, 0, $buffer.Length)
        for ($index = 0; $index -lt $read; $index++) {
            if ($buffer[$index] -eq 0) {
                return $true
            }
        }
        return $false
    }
    finally {
        $stream.Dispose()
    }
}

function Assert-EquivalentFile {
    param(
        [Parameter(Mandatory = $true)][string] $UpstreamPath,
        [Parameter(Mandatory = $true)][string] $FrozenPath,
        [Parameter(Mandatory = $true)][string] $RelativePath
    )

    if (-not (Test-Path -LiteralPath $UpstreamPath -PathType Leaf)) {
        throw "Missing upstream file: $RelativePath"
    }
    if (-not (Test-Path -LiteralPath $FrozenPath -PathType Leaf)) {
        throw "Missing frozen file: $RelativePath"
    }

    if ((Test-BinaryFile -Path $UpstreamPath) -or (Test-BinaryFile -Path $FrozenPath)) {
        $upstreamHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $UpstreamPath).Hash
        $frozenHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $FrozenPath).Hash
        if (-not [string]::Equals($upstreamHash, $frozenHash, [System.StringComparison]::Ordinal)) {
            throw "Binary content mismatch: $RelativePath"
        }
        return 'binary'
    }

    & git diff --no-index --quiet --ignore-cr-at-eol -- $UpstreamPath $FrozenPath
    $diffExitCode = $LASTEXITCODE
    if ($diffExitCode -eq 1) {
        throw "Text content mismatch beyond CRLF/LF normalization: $RelativePath"
    }
    if ($diffExitCode -ne 0) {
        throw "git diff failed for $RelativePath with exit code $diffExitCode."
    }
    return 'text'
}

try {
    $workspaceRoot = Get-GitValue -GitArguments @('rev-parse', '--show-toplevel')
    $history = @(Invoke-Git -GitArguments @('-C', $workspaceRoot, 'log', '--all', '--format=%H%x09%s'))
    $freezeCommits = @(
        foreach ($line in $history) {
            $parts = $line -split "`t", 2
            if ($parts.Count -eq 2 -and [string]::Equals($parts[1], $freezeSubject, [System.StringComparison]::Ordinal)) {
                $parts[0]
            }
        }
    )
    if ($freezeCommits.Count -ne 1) {
        throw "Expected exactly one commit with subject '$freezeSubject', found $($freezeCommits.Count)."
    }
    $freezeCommit = $freezeCommits[0]
    $freezeParent = Get-GitValue -GitArguments @('-C', $workspaceRoot, 'rev-parse', "${freezeCommit}^")

    $reviewRoot = Join-Path ([System.IO.Path]::GetTempPath()) ("blackhorse-upstream-review-" + [guid]::NewGuid())
    New-Item -ItemType Directory -Path $reviewRoot | Out-Null
    $archivePath = Join-Path $reviewRoot 'frozen-import.zip'
    $frozenRoot = Join-Path $reviewRoot 'frozen'
    $backendRoot = Join-Path $reviewRoot 'backend'
    $frontendRoot = Join-Path $reviewRoot 'frontend'

    Invoke-Git -GitArguments @('-C', $workspaceRoot, 'archive', '--format=zip', "--output=$archivePath", $freezeCommit) | Out-Null
    Expand-Archive -LiteralPath $archivePath -DestinationPath $frozenRoot

    Invoke-Git -GitArguments @('clone', '--branch', 'springboot3', '--single-branch', 'https://gitee.com/y_project/RuoYi-Vue.git', $backendRoot) | Out-Null
    Invoke-Git -GitArguments @('-C', $backendRoot, 'checkout', '--detach', $backendCommit) | Out-Null
    Invoke-Git -GitArguments @('clone', 'https://github.com/yangzongzhuan/RuoYi-Vue3.git', $frontendRoot) | Out-Null
    Invoke-Git -GitArguments @('-C', $frontendRoot, 'checkout', '--detach', $frontendCommit) | Out-Null

    Assert-Exact -Label 'Backend commit' -Actual (Get-GitValue -GitArguments @('-C', $backendRoot, 'rev-parse', 'HEAD')) -Expected $backendCommit
    Assert-Exact -Label 'Backend tree' -Actual (Get-GitValue -GitArguments @('-C', $backendRoot, 'rev-parse', 'HEAD^{tree}')) -Expected $backendTree
    Assert-Exact -Label 'Frontend commit' -Actual (Get-GitValue -GitArguments @('-C', $frontendRoot, 'rev-parse', 'HEAD')) -Expected $frontendCommit
    Assert-Exact -Label 'Frontend tree' -Actual (Get-GitValue -GitArguments @('-C', $frontendRoot, 'rev-parse', 'HEAD^{tree}')) -Expected $frontendTree

    $backendTracked = @(
        Invoke-Git -GitArguments @('-C', $backendRoot, '-c', 'core.quotepath=false', 'ls-files') |
            Where-Object { $_ -cnotlike 'docs/*' }
    )
    $frontendTracked = @(Invoke-Git -GitArguments @('-C', $frontendRoot, '-c', 'core.quotepath=false', 'ls-files'))
    $actualPaths = @(
        Invoke-Git -GitArguments @(
            '-C', $workspaceRoot, '-c', 'core.quotepath=false', 'diff-tree', '--root',
            '--no-commit-id', '--name-only', '-r', $freezeCommit
        )
    )

    $expectedSet = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::Ordinal)
    foreach ($path in $backendTracked) {
        if (-not $expectedSet.Add($path)) {
            throw "Duplicate expected backend path: $path"
        }
    }
    foreach ($path in $frontendTracked) {
        $mappedPath = "ruoyi-ui/$path"
        if (-not $expectedSet.Add($mappedPath)) {
            throw "Duplicate expected frontend path: $mappedPath"
        }
    }
    if (-not $expectedSet.Add('docs/architecture/upstream-baseline.md')) {
        throw 'Duplicate expected baseline document path.'
    }

    $actualSet = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::Ordinal)
    foreach ($path in $actualPaths) {
        if (-not $actualSet.Add($path)) {
            throw "Duplicate path reported by frozen commit: $path"
        }
    }
    $missingPaths = @($expectedSet | Where-Object { -not $actualSet.Contains($_) } | Sort-Object)
    $extraPaths = @($actualSet | Where-Object { -not $expectedSet.Contains($_) } | Sort-Object)
    if ($missingPaths.Count -or $extraPaths.Count) {
        throw "Frozen path-set mismatch.`nMissing: $($missingPaths -join ', ')`nExtra: $($extraPaths -join ', ')"
    }

    $binaryCount = 0
    $textCount = 0
    foreach ($path in $backendTracked | Where-Object { $_ -cne '.gitignore' }) {
        $kind = Assert-EquivalentFile `
            -UpstreamPath (Join-Path $backendRoot $path) `
            -FrozenPath (Join-Path $frozenRoot $path) `
            -RelativePath $path
        if ($kind -ceq 'binary') { $binaryCount++ } else { $textCount++ }
    }
    foreach ($path in $frontendTracked | Where-Object { $_ -cne '.gitignore' }) {
        $mappedPath = "ruoyi-ui/$path"
        $kind = Assert-EquivalentFile `
            -UpstreamPath (Join-Path $frontendRoot $path) `
            -FrozenPath (Join-Path $frozenRoot $mappedPath) `
            -RelativePath $mappedPath
        if ($kind -ceq 'binary') { $binaryCount++ } else { $textCount++ }
    }

    $backendIgnoreLines = @(Get-Content -LiteralPath (Join-Path $backendRoot '.gitignore'))
    $expectedRootIgnoreLines = @('.worktrees/', '') + $backendIgnoreLines
    $frozenRootIgnoreLines = @(Get-Content -LiteralPath (Join-Path $frozenRoot '.gitignore'))
    Assert-LineSequence -Label 'Root .gitignore transform' -Expected $expectedRootIgnoreLines -Actual $frozenRootIgnoreLines

    $frontendIgnoreLines = @(Get-Content -LiteralPath (Join-Path $frontendRoot '.gitignore'))
    $yarnIgnoreRules = @($frontendIgnoreLines | Where-Object { $_ -ceq 'yarn.lock' })
    if ($yarnIgnoreRules.Count -ne 1) {
        throw "Expected one upstream yarn.lock ignore rule, found $($yarnIgnoreRules.Count)."
    }
    $expectedFrontendIgnoreLines = @($frontendIgnoreLines | Where-Object { $_ -cne 'yarn.lock' })
    $frozenFrontendIgnoreLines = @(Get-Content -LiteralPath (Join-Path $frozenRoot 'ruoyi-ui/.gitignore'))
    Assert-LineSequence -Label 'Frontend .gitignore transform' -Expected $expectedFrontendIgnoreLines -Actual $frozenFrontendIgnoreLines

    Invoke-Git -GitArguments @(
        '-C', $workspaceRoot, 'diff', '--check', $freezeParent, $freezeCommit, '--',
        '.gitignore', 'ruoyi-ui/.gitignore', 'docs/architecture/upstream-baseline.md'
    ) | Out-Null

    Write-Output "Verified frozen commit: $freezeCommit"
    Write-Output "Verified changed paths: $($actualSet.Count)"
    Write-Output "Verified imported content: $textCount text files and $binaryCount binary files"
    Write-Output 'Verified the two approved .gitignore transformations and scoped whitespace check.'
}
finally {
    if ($null -ne $reviewRoot -and (Test-Path -LiteralPath $reviewRoot -PathType Container)) {
        $reviewItem = Get-Item -Force -LiteralPath $reviewRoot
        if (($reviewItem.Attributes -band [System.IO.FileAttributes]::ReparsePoint) -ne 0) {
            throw "Refusing to remove reparse-point review directory: $($reviewItem.FullName)"
        }
        $resolvedReviewRoot = [System.IO.Path]::GetFullPath((Resolve-Path -LiteralPath $reviewRoot).Path)
        $tempRoot = [System.IO.Path]::GetFullPath([System.IO.Path]::GetTempPath())
        $separator = [System.IO.Path]::DirectorySeparatorChar.ToString()
        if (-not $tempRoot.EndsWith($separator, [System.StringComparison]::Ordinal)) {
            $tempRoot += $separator
        }
        $reviewLeaf = Split-Path -Leaf $resolvedReviewRoot
        if (-not $resolvedReviewRoot.StartsWith($tempRoot, [System.StringComparison]::OrdinalIgnoreCase) -or
            $reviewLeaf -notlike 'blackhorse-upstream-review-*') {
            throw "Refusing to remove unsafe review directory: $resolvedReviewRoot"
        }
        Remove-Item -LiteralPath $resolvedReviewRoot -Recurse -Force
        if (Test-Path -LiteralPath $resolvedReviewRoot) {
            throw "Review directory still exists after cleanup: $resolvedReviewRoot"
        }
    }
}
```

冻结导入提交包含上游原有的尾随空白和 tab 缩进；全量 `git diff --check` 的冻结记录为 exit 2、1,759 条诊断、涉及 308 个上游文件。为保持固定快照，这些内容被有意保留而不机械改写。上述流程只对三个由本仓库维护的 Task 1 文件执行限定 `diff --check`，并另行逐文件校验全部上游内容。

上述两个固定提交的后续升级只能通过单独的 ADR 提出、评审和批准；不得直接移动分支基线或静默替换上游文件。
