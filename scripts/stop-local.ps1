[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$MySqlPort = 33306
$RedisPort = 36379
$BackendPort = 8080
$FrontendPort = 5173
$scriptPath = [IO.Path]::GetFullPath([string]$MyInvocation.MyCommand.Path)
$RepositoryRoot = [IO.Path]::GetFullPath((Join-Path (Split-Path -Parent $scriptPath) '..'))
$RuntimeRoot = [IO.Path]::GetFullPath((Join-Path $RepositoryRoot 'target\local-runtime'))
$CredentialsPath = Join-Path $RuntimeRoot 'credentials.json'
$StatePath = Join-Path $RuntimeRoot 'state.json'
$MySqlRootDefaultsPath = Join-Path $RuntimeRoot 'mysql-root.cnf'
$MySqlLoginFileOverride = Join-Path $RuntimeRoot '.disabled-mylogin.cnf'
$MySqlAdmin = 'C:\Program Files\MySQL\MySQL Server 8.0\bin\mysqladmin.exe'

function Assert-RuntimeLocation {
    $expected = [IO.Path]::GetFullPath((Join-Path $RepositoryRoot 'target\local-runtime')).TrimEnd('\')
    $actual = [IO.Path]::GetFullPath($RuntimeRoot).TrimEnd('\')
    if (-not [string]::Equals($actual, $expected, [StringComparison]::OrdinalIgnoreCase)) {
        throw 'The local runtime directory is outside the repository target directory.'
    }
}

function Assert-PlainFile {
    param(
        [Parameter(Mandatory)][string]$Path,
        [Parameter(Mandatory)][string]$Description
    )

    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        throw "$Description is missing: $Path"
    }
    $item = Get-Item -LiteralPath $Path -Force
    if (($item.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) {
        throw "$Description must not be a reparse point."
    }
    return $item.FullName
}

function Set-PrivateFileAcl {
    param([Parameter(Mandatory)][string]$Path)

    $currentSid = [Security.Principal.WindowsIdentity]::GetCurrent().User
    $systemSid = [Security.Principal.SecurityIdentifier]::new(
        [Security.Principal.WellKnownSidType]::LocalSystemSid,
        $null
    )
    try {
        $existing = Get-Acl -LiteralPath $Path
        $ownerSid = $existing.GetOwner([Security.Principal.SecurityIdentifier])
        $rules = @($existing.GetAccessRules(
                $true,
                $false,
                [Security.Principal.SecurityIdentifier]
            ))
        $expectedSids = @($currentSid.Value, $systemSid.Value)
        $isPrivate = $existing.AreAccessRulesProtected -and
            $ownerSid -eq $currentSid -and $rules.Count -eq 2
        foreach ($existingRule in $rules) {
            $isPrivate = $isPrivate -and
                $existingRule.IdentityReference.Value -in $expectedSids -and
                $existingRule.AccessControlType -eq
                    [Security.AccessControl.AccessControlType]::Allow -and
                -not $existingRule.IsInherited -and
                $existingRule.InheritanceFlags -eq
                    [Security.AccessControl.InheritanceFlags]::None -and
                $existingRule.PropagationFlags -eq
                    [Security.AccessControl.PropagationFlags]::None -and
                ($existingRule.FileSystemRights -band
                    [Security.AccessControl.FileSystemRights]::FullControl) -eq
                    [Security.AccessControl.FileSystemRights]::FullControl
        }
        if ($isPrivate) {
            return
        }
    }
    catch {
        # Fall through and establish the expected ACL.
    }
    $security = [Security.AccessControl.FileSecurity]::new()
    foreach ($sid in @($currentSid, $systemSid)) {
        $rule = [Security.AccessControl.FileSystemAccessRule]::new(
            $sid,
            [Security.AccessControl.FileSystemRights]::FullControl,
            [Security.AccessControl.AccessControlType]::Allow
        )
        [void]$security.AddAccessRule($rule)
    }
    $security.SetAccessRuleProtection($true, $false)
    $security.SetOwner($currentSid)
    Set-Acl -LiteralPath $Path -AclObject $security
}

function Write-PrivateJson {
    param(
        [Parameter(Mandatory)][string]$Path,
        [Parameter(Mandatory)]$Value
    )

    [IO.File]::WriteAllText($Path, ($Value | ConvertTo-Json -Depth 8),
        [Text.UTF8Encoding]::new($false))
    Set-PrivateFileAcl -Path $Path
}

function Get-Listeners {
    param([Parameter(Mandatory)][int]$Port)

    return @(Get-NetTCPConnection -State Listen -LocalPort $Port -ErrorAction SilentlyContinue |
        Sort-Object LocalAddress, OwningProcess)
}

function Test-ProcessIdentity {
    param([Parameter(Mandatory)]$Identity)

    try {
        $process = Get-Process -Id ([int]$Identity.processId) -ErrorAction Stop
        try {
            $path = [IO.Path]::GetFullPath($process.MainModule.FileName)
            $ticks = [long]$process.StartTime.ToUniversalTime().Ticks
            return $ticks -eq [long]$Identity.startTimeUtcTicks -and
                [string]::Equals($path, [string]$Identity.executablePath,
                    [StringComparison]::OrdinalIgnoreCase)
        }
        finally {
            $process.Dispose()
        }
    }
    catch {
        return $false
    }
}

function Test-OwnedListener {
    param([Parameter(Mandatory)]$Identity)

    if (-not (Test-ProcessIdentity -Identity $Identity)) {
        return $false
    }
    $listeners = @(Get-Listeners -Port ([int]$Identity.port))
    return $listeners.Count -gt 0 -and
        @($listeners | Where-Object { [int]$_.OwningProcess -ne [int]$Identity.processId }).Count -eq 0 -and
        @($listeners | Where-Object { [string]$_.LocalAddress -ne '127.0.0.1' }).Count -eq 0
}

function Wait-IdentityStopped {
    param(
        [Parameter(Mandatory)]$Identity,
        [ValidateRange(1, 60)][int]$TimeoutSeconds = 15
    )

    $deadline = [DateTime]::UtcNow.AddSeconds($TimeoutSeconds)
    while ((Test-ProcessIdentity -Identity $Identity) -and [DateTime]::UtcNow -lt $deadline) {
        Start-Sleep -Milliseconds 200
    }
    return -not (Test-ProcessIdentity -Identity $Identity)
}

function Stop-ExactProcess {
    param(
        [Parameter(Mandatory)]$Identity,
        [Parameter(Mandatory)][string]$Name
    )

    if (-not (Test-ProcessIdentity -Identity $Identity)) {
        Write-Output "$Name 已停止或进程身份已变化，未执行终止操作。"
        return
    }
    Stop-Process -Id ([int]$Identity.processId) -Force -ErrorAction Stop
    if (-not (Wait-IdentityStopped -Identity $Identity)) {
        throw "$Name retained the same PID, start time and executable path after stop."
    }
    Write-Output "$Name 已停止。"
}

function ConvertTo-NativeArgument {
    param([AllowEmptyString()][Parameter(Mandatory)][string]$Value)

    if ($Value.IndexOf('"') -ge 0 -or $Value.IndexOf("`r") -ge 0 -or $Value.IndexOf("`n") -ge 0) {
        throw 'A native process argument contains an unsupported character.'
    }
    if ($Value -match '[\s&()\[\]{}^=;!''+,`]') {
        return '"' + $Value + '"'
    }
    return $Value
}

function Join-NativeArguments {
    param([AllowEmptyCollection()][Parameter(Mandatory)][string[]]$Arguments)

    return (($Arguments | ForEach-Object { ConvertTo-NativeArgument -Value $_ }) -join ' ')
}

function Invoke-RedisShutdown {
    param([Parameter(Mandatory)][string]$Password)

    $client = [Net.Sockets.TcpClient]::new()
    try {
        $connectTask = $client.ConnectAsync('127.0.0.1', $RedisPort)
        if (-not $connectTask.Wait(5000) -or -not $client.Connected) {
            throw 'Could not connect to the isolated Redis runtime.'
        }
        $client.ReceiveTimeout = 5000
        $client.SendTimeout = 5000
        $stream = $client.GetStream()
        try {
            foreach ($step in @(
                    [pscustomobject]@{ command = @('AUTH', $Password); disconnectAllowed = $false },
                    [pscustomobject]@{ command = @('SHUTDOWN', 'SAVE'); disconnectAllowed = $true }
                )) {
                $builder = [Text.StringBuilder]::new()
                [void]$builder.Append('*').Append($step.command.Count).Append("`r`n")
                foreach ($argument in $step.command) {
                    $bytes = [Text.Encoding]::UTF8.GetBytes([string]$argument)
                    [void]$builder.Append('$').Append($bytes.Length).Append("`r`n")
                    [void]$builder.Append([string]$argument).Append("`r`n")
                }
                $payload = [Text.Encoding]::UTF8.GetBytes($builder.ToString())
                $stream.Write($payload, 0, $payload.Length)
                $stream.Flush()

                $response = [Text.StringBuilder]::new()
                try {
                    while ($true) {
                        $value = $stream.ReadByte()
                        if ($value -lt 0) {
                            if ($step.disconnectAllowed) {
                                break
                            }
                            throw 'Redis disconnected before authentication completed.'
                        }
                        if ($value -eq 13) {
                            if ($stream.ReadByte() -ne 10) {
                                throw 'Redis returned a malformed response.'
                            }
                            break
                        }
                        [void]$response.Append([char]$value)
                    }
                }
                catch [IO.IOException] {
                    if (-not $step.disconnectAllowed) {
                        throw
                    }
                }
                if ($response.Length -gt 0 -and $response.ToString().StartsWith('-',
                        [StringComparison]::Ordinal)) {
                    throw 'Redis rejected the authenticated shutdown command.'
                }
            }
        }
        finally {
            $stream.Dispose()
        }
    }
    finally {
        $client.Dispose()
    }
}

function Invoke-MySqlShutdown {
    $stdoutPath = Join-Path $RuntimeRoot 'logs\mysql-shutdown.out.log'
    $stderrPath = Join-Path $RuntimeRoot 'logs\mysql-shutdown.err.log'
    if (Test-Path -LiteralPath $MySqlLoginFileOverride) {
        return $false
    }
    $previousLoginFile = [Environment]::GetEnvironmentVariable('MYSQL_TEST_LOGIN_FILE', 'Process')
    [Environment]::SetEnvironmentVariable('MYSQL_TEST_LOGIN_FILE', $MySqlLoginFileOverride, 'Process')
    try {
        $process = Start-Process -FilePath $MySqlAdmin `
            -ArgumentList (Join-NativeArguments -Arguments @(
                    "--defaults-file=$MySqlRootDefaultsPath", '--protocol=TCP', '--host=127.0.0.1',
                    "--port=$MySqlPort", '--user=root', 'shutdown')) `
            -WorkingDirectory $RuntimeRoot `
            -WindowStyle Hidden `
            -RedirectStandardOutput $stdoutPath `
            -RedirectStandardError $stderrPath `
            -Wait `
            -PassThru
    }
    finally {
        [Environment]::SetEnvironmentVariable('MYSQL_TEST_LOGIN_FILE', $previousLoginFile, 'Process')
    }
    return $process.ExitCode -eq 0
}

function Stop-RedisProcess {
    param(
        [Parameter(Mandatory)]$Identity,
        [AllowNull()]$Credentials
    )

    if (-not (Test-ProcessIdentity -Identity $Identity)) {
        Write-Output 'Memurai 已停止或进程身份已变化，未执行终止操作。'
        return
    }
    if ((Test-OwnedListener -Identity $Identity) -and $null -ne $Credentials -and
        -not [string]::IsNullOrWhiteSpace([string]$Credentials.redisPassword)) {
        try {
            Invoke-RedisShutdown -Password ([string]$Credentials.redisPassword)
            if (Wait-IdentityStopped -Identity $Identity -TimeoutSeconds 20) {
                Write-Output 'Memurai 已安全关闭。'
                return
            }
        }
        catch {
            Write-Warning 'Memurai graceful shutdown failed; applying identity-checked process fallback.'
        }
    }
    Stop-ExactProcess -Identity $Identity -Name 'Memurai'
}

function Stop-MySqlProcess {
    param([Parameter(Mandatory)]$Identity)

    if (-not (Test-ProcessIdentity -Identity $Identity)) {
        Write-Output 'MySQL 已停止或进程身份已变化，未执行终止操作。'
        return
    }
    if ((Test-OwnedListener -Identity $Identity) -and
        (Test-Path -LiteralPath $MySqlRootDefaultsPath -PathType Leaf) -and
        (Test-Path -LiteralPath $MySqlAdmin -PathType Leaf)) {
        try {
            [void](Assert-PlainFile -Path $MySqlRootDefaultsPath -Description 'MySQL root defaults file')
            [void](Assert-PlainFile -Path $MySqlAdmin -Description 'MySQL administrative client')
            if ((Invoke-MySqlShutdown) -and (Wait-IdentityStopped -Identity $Identity -TimeoutSeconds 30)) {
                Write-Output 'MySQL 已安全关闭。'
                return
            }
        }
        catch {
            Write-Warning 'MySQL graceful shutdown failed; applying identity-checked process fallback.'
        }
    }
    Stop-ExactProcess -Identity $Identity -Name 'MySQL'
}

Assert-RuntimeLocation
if (-not (Test-Path -LiteralPath $StatePath -PathType Leaf)) {
    Write-Output '没有找到本项目的本机运行状态；未停止任何进程。'
    return
}
[void](Assert-PlainFile -Path $StatePath -Description 'Local runtime state file')
try {
    $state = Get-Content -Raw -LiteralPath $StatePath | ConvertFrom-Json
}
catch {
    throw 'The local runtime state file is not valid JSON; no process was stopped.'
}
if ([int]$state.schemaVersion -ne 1 -or
    -not [string]::Equals([IO.Path]::GetFullPath([string]$state.repositoryRoot), $RepositoryRoot,
        [StringComparison]::OrdinalIgnoreCase) -or $null -eq $state.processes) {
    throw 'The local runtime state does not belong to this repository; no process was stopped.'
}
$expectedPorts = [ordered]@{
    mysql = $MySqlPort
    redis = $RedisPort
    backend = $BackendPort
    frontend = $FrontendPort
}
foreach ($name in $expectedPorts.Keys) {
    $property = $state.processes.PSObject.Properties[[string]$name]
    if ($null -eq $property -or $null -eq $property.Value) {
        throw "The local runtime state is missing the $name process; no process was stopped."
    }
    $identity = $property.Value
    if ([int]$identity.processId -le 0 -or [long]$identity.startTimeUtcTicks -le 0 -or
        [string]::IsNullOrWhiteSpace([string]$identity.executablePath) -or
        [int]$identity.port -ne [int]$expectedPorts[$name]) {
        throw "The local runtime state has an invalid $name identity; no process was stopped."
    }
}

$credentials = $null
if (Test-Path -LiteralPath $CredentialsPath -PathType Leaf) {
    [void](Assert-PlainFile -Path $CredentialsPath -Description 'Local credentials file')
    try {
        $credentials = Get-Content -Raw -LiteralPath $CredentialsPath | ConvertFrom-Json
    }
    catch {
        Write-Warning 'Credentials could not be read; Redis will use identity-checked process fallback.'
    }
}

Stop-ExactProcess -Identity $state.processes.frontend -Name '前端'
Stop-ExactProcess -Identity $state.processes.backend -Name '后端'
Stop-RedisProcess -Identity $state.processes.redis -Credentials $credentials
Stop-MySqlProcess -Identity $state.processes.mysql

$remaining = @(
    $state.processes.frontend,
    $state.processes.backend,
    $state.processes.redis,
    $state.processes.mysql
) | Where-Object { Test-ProcessIdentity -Identity $_ }
if (@($remaining).Count -ne 0) {
    throw 'One or more exact recorded processes are still running; state was not changed.'
}

$state.status = 'stopped'
$state | Add-Member -NotePropertyName stoppedAtUtc -NotePropertyValue ([DateTime]::UtcNow.ToString('o')) -Force
Write-PrivateJson -Path $StatePath -Value $state
Write-Output '本项目的四个本机进程均已停止；数据库、Redis 持久数据和凭据均已保留。'
