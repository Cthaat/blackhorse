[CmdletBinding()]
param(
    [string]$RunnerPath = '',
    [string]$RepositoryRoot = ''
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

if ([string]::IsNullOrWhiteSpace($RunnerPath)) {
    $contractPath = [IO.Path]::GetFullPath([string]$MyInvocation.MyCommand.Path)
    $scriptsRoot = Split-Path -Parent (Split-Path -Parent $contractPath)
    $RunnerPath = Join-Path $scriptsRoot 'run-m1-native.ps1'
}
if ([string]::IsNullOrWhiteSpace($RepositoryRoot)) {
    $RepositoryRoot = [IO.Path]::GetFullPath((Join-Path (Split-Path -Parent $RunnerPath) '..'))
}

function Assert-Contract {
    param([Parameter(Mandatory)][bool]$Condition, [Parameter(Mandatory)][string]$Message)
    if (-not $Condition) { throw "CONTRACT FAIL: $Message" }
}

function Invoke-Child {
    param([Parameter(Mandatory)][string]$FileName, [Parameter(Mandatory)][string[]]$Arguments,
        [Parameter(Mandatory)][hashtable]$Environment,
        [ValidateRange(1, 120)][int]$TimeoutSeconds = 30)
    $info = [Diagnostics.ProcessStartInfo]::new()
    $info.FileName = $FileName
    $info.UseShellExecute = $false
    $info.CreateNoWindow = $true
    $info.RedirectStandardOutput = $true
    $info.RedirectStandardError = $true
    $info.Arguments = (@($Arguments | ForEach-Object {
                '"' + ([string]$_).Replace('"', '\"') + '"'
            }) -join ' ')
    foreach ($key in @($info.EnvironmentVariables.Keys)) {
        if ($key -match '(?i)^(LAB_|SPRING_|RUOYI_|TOKEN_|MYSQL_|REDISCLI_AUTH$)') {
            [void]$info.EnvironmentVariables.Remove($key)
        }
    }
    foreach ($entry in $Environment.GetEnumerator()) { $info.EnvironmentVariables[$entry.Key] = $entry.Value }
    $process = [Diagnostics.Process]::new()
    $process.StartInfo = $info
    try {
        Assert-Contract $process.Start() 'contract child could not be started'
        $stdoutTask = $process.StandardOutput.ReadToEndAsync()
        $stderrTask = $process.StandardError.ReadToEndAsync()
        if (-not $process.WaitForExit($TimeoutSeconds * 1000)) {
            $process.Kill()
            if (-not $process.WaitForExit(5000)) {
                throw 'contract child did not stop after its timeout'
            }
            throw "contract child exceeded the $TimeoutSeconds-second timeout"
        }
        return [pscustomobject]@{
            ExitCode = $process.ExitCode
            Output = $stdoutTask.GetAwaiter().GetResult() + [Environment]::NewLine +
                $stderrTask.GetAwaiter().GetResult()
        }
    }
    finally { $process.Dispose() }
}

function Get-Listeners {
    param([Parameter(Mandatory)][int[]]$Ports)
    return @($Ports | ForEach-Object {
        @(Get-NetTCPConnection -State Listen -LocalPort $_ -ErrorAction SilentlyContinue |
            Sort-Object LocalAddress, OwningProcess |
            ForEach-Object { "$($_.LocalAddress):$($_.LocalPort):$($_.OwningProcess)" }) -join ','
    }) -join '|'
}

Assert-Contract (Test-Path -LiteralPath $RunnerPath -PathType Leaf) 'runner script is missing'
$tokens = $null
$parseErrors = $null
$ast = [Management.Automation.Language.Parser]::ParseFile($RunnerPath, [ref]$tokens, [ref]$parseErrors)
Assert-Contract ($parseErrors.Count -eq 0) ('runner has PowerShell parse errors: ' +
    (@($parseErrors | ForEach-Object Message) -join '; '))

$source = [IO.File]::ReadAllText($RunnerPath)
$requiredLiterals = @(
    'C:\Program Files\MySQL\MySQL Server 8.0',
    'C:\Program Files\Memurai',
    'Join-Path $MySqlBase ''bin\mysqld.exe''',
    'Join-Path $MySqlBase ''bin\mysql.exe''',
    'Join-Path $MemuraiHome ''memurai.exe''',
    'Join-Path $MemuraiHome ''memurai-cli.exe''',
    'C:\APP\JDK\jdk_17',
    'C:\Apache\Maven\apache-maven-3.9.16\bin\mvn.cmd',
    'Global\BlackhorseTask7Maven',
    'scripts\verify.ps1',
    'scripts\smoke-foundation.ps1',
    '.lab-smoke-wrapper-owner',
    'LAB_TEST_WRAPPER_NONCE',
    'LAB_TEST_MYSQL_ROOT',
    'LAB_TEST_MYSQL_DATADIR',
    'LAB_TEST_MYSQL_CONFIG',
    'LAB_TEST_MEMURAI_ROOT',
    'LAB_TEST_MEMURAI_CONFIG',
    'LAB_TEST_JAVA_HOME',
    'LAB_TEST_MYSQL_CLIENT_PATH',
    'LAB_TEST_MYSQL_SERVER_PATH',
    'LAB_TEST_MEMURAI_SERVER_PATH',
    'LAB_TEST_MEMURAI_CLI_PATH',
    'LAB_TEST_WRAPPER_ACTIVE',
    'LAB_TEST_MYSQL_PID',
    'LAB_TEST_MEMURAI_PID',
    'LAB_TOKEN_SECRET',
    'LAB_DEMO_STUDENT_PASSWORD',
    'LAB_DEMO_MANAGER_PASSWORD',
    'LAB_DEMO_SAFETY_PASSWORD',
    'LAB_DEMO_REPAIR_PASSWORD',
    'LAB_DEMO_ADMIN_PASSWORD',
    'GRANT CREATE, DROP, PROCESS, SHUTDOWN ON *.*',
    'GRANT ALL PRIVILEGES ON ``lab_test_verify``.*',
    'GRANT ALL PRIVILEGES ON ``lab_test_m1_smoke``.*',
    '--initialize-insecure',
    'bind-address=127.0.0.1',
    'protected-mode yes',
    'appendonly no',
    'save ""',
    'Test-OwnedIdentity',
    'Register-OwnedListener',
    'Assert-RunMarker',
    'PROTECTED_BASELINE_UNCHANGED',
    'CONTAINER_ENGINE=NOT_USED'
    ,'RandomNumberGenerator]::Create()'
    ,'$random.GetBytes($bytes)'
    ,'$random.Dispose()'
    ,'ConvertTo-WindowsCommandLine'
    ,'EnvironmentVariables'
    ,'Stop-CapturedProcessTree'
    ,'TimeoutSeconds'
)
foreach ($literal in $requiredLiterals) {
    Assert-Contract $source.Contains($literal) "required safety/control literal is absent: $literal"
}

$forbiddenPatterns = @(
    '(?im)\bdocker(?:\.exe)?\b',
    '(?im)\b(?:Start|Stop|Restart)-Service\b',
    '(?im)\b(?:New|Set|Remove)-NetFirewallRule\b',
    '(?im)\bsc(?:\.exe)?\s+(?:start|stop|delete|config)\b',
    '(?im)\bnet(?:\.exe)?\s+(?:start|stop)\b',
    '(?im)Write-(?:Host|Output|Information|Verbose|Debug).*password',
    '(?im)Write-(?:Host|Output|Information|Verbose|Debug).*credential'
)
foreach ($pattern in $forbiddenPatterns) {
    Assert-Contract (-not [regex]::IsMatch($source, $pattern)) "forbidden construct matched: $pattern"
}
Assert-Contract (-not [regex]::IsMatch($source, 'WaitForExit\s*\(\s*\)')) 'runner contains an unbounded WaitForExit() call'
Assert-Contract (-not $source.Contains('ArgumentList')) 'runner contains the .NET Core-only ProcessStartInfo.ArgumentList API'

Assert-Contract ([regex]::IsMatch($source, '(?s)\$MySqlListenerProcessId\s*=\s*Register-OwnedListener')) 'MySQL listener PID is not captured from an identity-checked listener'
Assert-Contract ([regex]::IsMatch($source, '(?s)\$MemuraiListenerProcessId\s*=\s*Register-OwnedListener')) 'Memurai listener PID is not captured from an identity-checked listener'
Assert-Contract ([regex]::IsMatch($source, '(?s)Assert-RunMarker\s+-Path\s+\$safeRoot\s+-Nonce\s+\$RunNonce')) 'runtime directory removal is not bound to the generated nonce marker'

$windowsPowerShell = Join-Path $env:SystemRoot 'System32\WindowsPowerShell\v1.0\powershell.exe'
$randomFunction = @($ast.Find({
            param($node)
            $node -is [Management.Automation.Language.FunctionDefinitionAst] -and
                $node.Name -eq 'New-RandomHex'
        }, $true) | Select-Object -First 1)
Assert-Contract ($randomFunction.Count -eq 1) 'runner random generator function is missing'
$randomProbePath = Join-Path ([IO.Path]::GetTempPath()) ('blackhorse-m1-random-contract-' + [Guid]::NewGuid().ToString('N') + '.ps1')
try {
    [IO.File]::WriteAllText($randomProbePath, $randomFunction[0].Extent.Text +
        "`n`$value = New-RandomHex -ByteCount 24; if (`$value -notmatch '^[0-9a-f]{48}$') { exit 9 }",
        [Text.UTF8Encoding]::new($false))
    $randomProbe = Invoke-Child -FileName $windowsPowerShell -Arguments @(
        '-NoLogo', '-NoProfile', '-NonInteractive', '-ExecutionPolicy', 'Bypass', '-File', $randomProbePath) -Environment @{}
    Assert-Contract ($randomProbe.ExitCode -eq 0) 'New-RandomHex did not execute successfully under Windows PowerShell 5.1'
}
finally {
    if (Test-Path -LiteralPath $randomProbePath) {
        Remove-Item -LiteralPath $randomProbePath -Force -ErrorAction Stop
    }
}

$processFunctions = @($ast.FindAll({
            param($node)
            $node -is [Management.Automation.Language.FunctionDefinitionAst] -and
                ($node.Name -eq 'ConvertTo-WindowsCommandLine' -or
                    $node.Name -eq 'New-ProcessStartInfo' -or
                    $node.Name -eq 'Get-ProcessIdentity' -or
                    $node.Name -eq 'Invoke-CapturedProcess')
        }, $true) | Sort-Object Name)
Assert-Contract ($processFunctions.Count -eq 4) 'runner PS5 process capture functions are missing'
$processProbePath = Join-Path ([IO.Path]::GetTempPath()) ('blackhorse-m1-process-contract-' + [Guid]::NewGuid().ToString('N') + '.ps1')
try {
    $processProbe = @($processFunctions | ForEach-Object { $_.Extent.Text }) -join [Environment]::NewLine
    $processProbe += @'
$RepositoryRoot = [IO.Path]::GetTempPath()
$command = Join-Path $env:SystemRoot 'System32\WindowsPowerShell\v1.0\powershell.exe'
$startInfo = New-ProcessStartInfo -FileName $command -Arguments @(
    '-NoLogo', '-NoProfile', '-NonInteractive', '-Command',
    "if (`$env:BH_M1_CONTRACT_ENV -ne 'OK') { exit 7 }; exit 0") `
    -WorkingDirectory $RepositoryRoot -Environment @{ BH_M1_CONTRACT_ENV = 'OK' } -Redirect
if ($startInfo.PSObject.Properties.Name -contains 'ArgumentList') { exit 8 }
if ([string]::IsNullOrWhiteSpace($startInfo.Arguments)) { exit 9 }
$process = [Diagnostics.Process]::new()
$process.StartInfo = $startInfo
try {
    if (-not $process.Start()) { exit 10 }
    if (-not $process.WaitForExit(5000)) { $process.Kill(); exit 11 }
    if ($process.ExitCode -ne 0) { exit 12 }
}
finally {
    $process.Dispose()
}

$script:getProcessCallCount = 0
function Get-Process {
    [CmdletBinding()]
    param([Parameter(Mandatory)][int]$Id)
    $script:getProcessCallCount++
    $fakeProcess = [pscustomobject]@{
        Id = $Id
        MainModule = [pscustomobject]@{ FileName = $null }
        StartTime = [DateTime]::UtcNow
    }
    $fakeProcess | Add-Member -MemberType ScriptMethod -Name Dispose -Value {}
    return $fakeProcess
}

$normalExitMarker = 'BH_M1_CAPTURED_PROCESS_NORMAL_EXIT'
try {
    $captured = Invoke-CapturedProcess -FileName $command -Arguments @(
        '-NoLogo', '-NoProfile', '-NonInteractive', '-Command',
        "Write-Output '$normalExitMarker'; exit 23") `
        -WorkingDirectory $RepositoryRoot -Environment @{} -TimeoutSeconds 10
}
catch {
    [Console]::Error.WriteLine(
        'normal-exit process capture raised an exception: ' + $_.Exception.Message)
    exit 13
}
if ($captured.ExitCode -ne 23) { exit 14 }
if ($captured.Stdout -notmatch [regex]::Escape($normalExitMarker)) { exit 15 }
if ($script:getProcessCallCount -ne 0) { exit 16 }
'@
    [IO.File]::WriteAllText($processProbePath, $processProbe, [Text.UTF8Encoding]::new($false))
    $processProbeResult = Invoke-Child -FileName $windowsPowerShell -Arguments @(
        '-NoLogo', '-NoProfile', '-NonInteractive', '-ExecutionPolicy', 'Bypass', '-File', $processProbePath) -Environment @{}
    Assert-Contract ($processProbeResult.ExitCode -eq 0) `
        ('normal captured-process completion is not race-safe under Windows PowerShell 5.1. Output: ' +
        $processProbeResult.Output)
}
finally {
    if (Test-Path -LiteralPath $processProbePath) {
        Remove-Item -LiteralPath $processProbePath -Force -ErrorAction Stop
    }
}

$smokePath = Join-Path $RepositoryRoot 'scripts\smoke-foundation.ps1'
Assert-Contract (Test-Path -LiteralPath $smokePath -PathType Leaf) 'smoke script is missing'
$smokeSource = [IO.File]::ReadAllText($smokePath)
Assert-Contract (-not [regex]::IsMatch(
        $smokeSource,
        '(?m)^\s*\$javaVersionOutput\s*=\s*&\s*\$javaPath\s+-version\b')) `
    'smoke invokes java -version directly; normal native stderr terminates Windows PowerShell 5.1'
Assert-Contract ([regex]::IsMatch(
        $smokeSource,
        '(?s)\$javaVersionOutput\s*=\s*Invoke-NativeCapture\b.*?-Arguments\s+''-version''.*?-IncludeStandardError\b')) `
    'smoke JDK check is not using the bounded native capture helper with stderr included'
$listenersBefore = Get-Listeners -Ports @(3306, 6379, 18080)
$missingInput = Invoke-Child -FileName $windowsPowerShell -Arguments @(
    '-NoLogo', '-NoProfile', '-NonInteractive', '-ExecutionPolicy', 'Bypass', '-File', $smokePath) -Environment @{}
$listenersAfter = Get-Listeners -Ports @(3306, 6379, 18080)
Assert-Contract ($missingInput.ExitCode -ne 0) 'smoke script unexpectedly accepted missing wrapper input'
Assert-Contract ($missingInput.Output -match 'LAB_TEST_WRAPPER_ACTIVE') 'smoke script did not reject missing wrapper input before execution'
Assert-Contract ($listenersBefore -eq $listenersAfter) 'missing-input contract changed a protected listener state'

$forgedRoot = Join-Path ([IO.Path]::GetTempPath()) ('blackhorse-m1-forged-contract-' + [Guid]::NewGuid().ToString('N'))
try {
    $forgedDataDirectory = Join-Path $forgedRoot 'mysql-data'
    $forgedMySqlConfig = Join-Path $forgedRoot 'mysql.ini'
    $forgedMemuraiConfig = Join-Path $forgedRoot 'memurai.conf'
    $forgedNonce = 'f' * 32
    [void][IO.Directory]::CreateDirectory($forgedDataDirectory)
    [IO.File]::WriteAllText((Join-Path $forgedRoot '.lab-smoke-wrapper-owner'), $forgedNonce,
        [Text.UTF8Encoding]::new($false))
    [IO.File]::WriteAllText($forgedMySqlConfig, '[mysqld]' + [Environment]::NewLine +
        'datadir=' + $forgedDataDirectory + [Environment]::NewLine + 'port=65531' +
        [Environment]::NewLine + 'bind-address=127.0.0.1' + [Environment]::NewLine +
        'mysqlx=0', [Text.UTF8Encoding]::new($false))
    [IO.File]::WriteAllText($forgedMemuraiConfig, 'port 65532' + [Environment]::NewLine +
        'bind 127.0.0.1', [Text.UTF8Encoding]::new($false))
    $forgedInputs = @{
    LAB_TEST_WRAPPER_ACTIVE = 'true'; LAB_TEST_WRAPPER_NONCE = $forgedNonce;
    LAB_TEST_MYSQL_ROOT = $forgedRoot; LAB_TEST_MYSQL_DATADIR = $forgedDataDirectory; LAB_TEST_MYSQL_CONFIG = $forgedMySqlConfig;
    LAB_TEST_MEMURAI_ROOT = $forgedRoot; LAB_TEST_MEMURAI_CONFIG = $forgedMemuraiConfig;
    LAB_TEST_JAVA_HOME = 'C:\APP\JDK\jdk_17'; LAB_TEST_MYSQL_CLIENT_PATH = 'C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe';
    LAB_TEST_MYSQL_SERVER_PATH = 'C:\Program Files\MySQL\MySQL Server 8.0\bin\mysqld.exe';
    LAB_TEST_MEMURAI_SERVER_PATH = 'C:\Program Files\Memurai\memurai.exe'; LAB_TEST_MEMURAI_CLI_PATH = 'C:\Program Files\Memurai\memurai-cli.exe';
    LAB_TEST_ADMIN_HOST = '127.0.0.1';
    LAB_TEST_ADMIN_PORT = '65531'; LAB_TEST_MYSQL_PID = '999999';
    LAB_TEST_ADMIN_USERNAME = 'admin'; LAB_TEST_ADMIN_PASSWORD = 'safe-test-password';
    LAB_TEST_DB_USERNAME = 'app'; LAB_TEST_DB_PASSWORD = 'safe-test-password';
    LAB_REDIS_HOST = '127.0.0.1'; LAB_REDIS_PORT = '65532'; LAB_TEST_MEMURAI_PID = '999998';
    LAB_REDIS_PASSWORD = 'safe-test-password'; LAB_TOKEN_SECRET = ('x' * 64);
    LAB_DEMO_STUDENT_PASSWORD = 'safe01'; LAB_DEMO_MANAGER_PASSWORD = 'safe02';
    LAB_DEMO_SAFETY_PASSWORD = 'safe03'; LAB_DEMO_REPAIR_PASSWORD = 'safe04';
    LAB_DEMO_ADMIN_PASSWORD = 'safe05'
    }
    $listenersBefore = Get-Listeners -Ports @(3306, 6379, 18080)
    $forgedIdentity = Invoke-Child -FileName $windowsPowerShell -Arguments @(
    '-NoLogo', '-NoProfile', '-NonInteractive', '-ExecutionPolicy', 'Bypass', '-File', $smokePath) -Environment $forgedInputs
    $listenersAfter = Get-Listeners -Ports @(3306, 6379, 18080)
    Assert-Contract ($forgedIdentity.ExitCode -ne 0) 'smoke script unexpectedly accepted forged listener identity'
    Assert-Contract ($listenersBefore -eq $listenersAfter) 'forged identity contract changed a protected listener state'
    $forgedDiagnostic = $forgedIdentity.Output.Replace('safe-test-password', '[redacted]').Replace(
        $forgedNonce, '[nonce]')
    Assert-Contract ($forgedIdentity.Output -match '(?i)(does not exist|isolated MySQL listener|built application JAR|Get-NetTCPConnection)') `
        ('forged identity contract did not fail during preflight validation. Output: ' + $forgedDiagnostic)
}
finally {
    if (Test-Path -LiteralPath $forgedRoot) {
        Remove-Item -LiteralPath $forgedRoot -Recurse -Force -ErrorAction Stop
    }
}

Write-Output 'CONTRACT PASS'
