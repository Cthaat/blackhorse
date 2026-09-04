[CmdletBinding()]
param(
    [string]$RepositoryRoot = '',
    [string]$MySqlBase = 'C:\Program Files\MySQL\MySQL Server 8.0',
    [string]$MemuraiHome = 'C:\Program Files\Memurai',
    [string]$JavaHome = 'C:\APP\JDK\jdk_17',
    [string]$MavenCommand = 'C:\Apache\Maven\apache-maven-3.9.16\bin\mvn.cmd'
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

if ([string]::IsNullOrWhiteSpace($RepositoryRoot)) {
    $scriptPath = [IO.Path]::GetFullPath([string]$MyInvocation.MyCommand.Path)
    $RepositoryRoot = [IO.Path]::GetFullPath((Join-Path (Split-Path -Parent $scriptPath) '..'))
}
$MySqlServer = Join-Path $MySqlBase 'bin\mysqld.exe'
$MySqlClient = Join-Path $MySqlBase 'bin\mysql.exe'
$MySqlAdmin = Join-Path $MySqlBase 'bin\mysqladmin.exe'
$MemuraiServer = Join-Path $MemuraiHome 'memurai.exe'
$MemuraiClient = Join-Path $MemuraiHome 'memurai-cli.exe'
$MavenMutexName = 'Global\BlackhorseTask7Maven'
$ExpectedReports = @(
    'TEST-com.ruoyi.integration.discovery.LabTestDiscoveryTest.xml',
    'TEST-com.ruoyi.integration.discovery.LabTestDiscoveryIT.xml',
    'TEST-com.ruoyi.integration.LabCompatibilityProbeMapperTest.xml',
    'TEST-com.ruoyi.integration.web.exception.LabExceptionContractTest.xml',
    'TEST-com.ruoyi.integration.security.LabRoleSeedIT.xml',
    'TEST-com.ruoyi.integration.web.demo.LabDemoAccountInitializerTest.xml',
    'TEST-com.ruoyi.integration.security.LabSystemOperatorLoginIT.xml',
    'TEST-com.ruoyi.integration.web.openapi.LabOpenApiNonProdIT.xml',
    'TEST-com.ruoyi.integration.web.openapi.LabOpenApiProdIT.xml'
)

$SensitiveValues = [System.Collections.Generic.List[string]]::new()
$OwnedMySql = [System.Collections.Generic.List[object]]::new()
$OwnedMemurai = [System.Collections.Generic.List[object]]::new()
$RuntimeRoot = $null
$RunNonce = $null
$MySqlPort = 0
$MemuraiPort = 0
$MySqlAdminDefaults = $null
$OriginalPath = $env:PATH
$VerificationStdout = $null
$VerificationStderr = $null
$SmokeStdout = $null
$SmokeStderr = $null
$MySqlStartRecord = $null
$MemuraiStartRecord = $null
$ProtectedBefore = $null
$TestEvidence = $null
$SmokeEvidence = $null
$VersionEvidence = [ordered]@{}
$CleanupEvidence = [ordered]@{
    MySqlStopped = $false
    MemuraiStopped = $false
    RuntimeRemoved = $false
    ProtectedUnchanged = $false
}
$FinalExitCode = 1
$FailureMessages = [System.Collections.Generic.List[string]]::new()

function Add-SensitiveValue {
    param([AllowEmptyString()][string]$Value)
    if (-not [string]::IsNullOrEmpty($Value) -and -not $SensitiveValues.Contains($Value)) {
        [void]$SensitiveValues.Add($Value)
    }
}

function Protect-Text {
    param([AllowEmptyString()][string]$Text)
    if ($null -eq $Text) {
        return ''
    }
    $protected = $Text
    foreach ($value in @($SensitiveValues | Sort-Object Length -Descending)) {
        if (-not [string]::IsNullOrEmpty($value)) {
            $protected = $protected.Replace($value, '[REDACTED]')
        }
    }
    return $protected
}

function Add-Failure {
    param([Parameter(Mandatory)][string]$Message)
    [void]$FailureMessages.Add((Protect-Text $Message))
}

function Assert-ExistingFile {
    param([Parameter(Mandatory)][string]$Path, [Parameter(Mandatory)][string]$Description)
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        throw "$Description is missing."
    }
    $item = Get-Item -LiteralPath $Path -Force
    if (($item.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) {
        throw "$Description must not be a reparse point."
    }
    return $item.FullName
}

function New-RandomHex {
    param([ValidateRange(1, 128)][int]$ByteCount = 24)
    $bytes = [byte[]]::new($ByteCount)
    $random = [Security.Cryptography.RandomNumberGenerator]::Create()
    try {
        $random.GetBytes($bytes)
    }
    finally {
        $random.Dispose()
    }
    return ([BitConverter]::ToString($bytes) -replace '-', '').ToLowerInvariant()
}

function New-SecureRuntimeRoot {
    $tempParent = [IO.Path]::GetFullPath([IO.Path]::GetTempPath()).TrimEnd('\')
$leaf = 'blackhorse-m1-native-' + (New-RandomHex -ByteCount 16)
    $path = Join-Path $tempParent $leaf
    if (Test-Path -LiteralPath $path) {
        throw 'The generated runtime directory already exists.'
    }
    $directory = [IO.Directory]::CreateDirectory($path)

    $acl = [Security.AccessControl.DirectorySecurity]::new()
    $acl.SetAccessRuleProtection($true, $false)
    $inheritance = [Security.AccessControl.InheritanceFlags]'ContainerInherit, ObjectInherit'
    $propagation = [Security.AccessControl.PropagationFlags]::None
    $allow = [Security.AccessControl.AccessControlType]::Allow
    $fullControl = [Security.AccessControl.FileSystemRights]::FullControl
    $currentSid = [Security.Principal.WindowsIdentity]::GetCurrent().User
    $systemSid = [Security.Principal.SecurityIdentifier]::new('S-1-5-18')
    $acl.AddAccessRule([Security.AccessControl.FileSystemAccessRule]::new(
            $currentSid, $fullControl, $inheritance, $propagation, $allow))
    $acl.AddAccessRule([Security.AccessControl.FileSystemAccessRule]::new(
            $systemSid, $fullControl, $inheritance, $propagation, $allow))
    Set-Acl -LiteralPath $directory.FullName -AclObject $acl
    return $directory.FullName
}

function Assert-SafeRuntimeRoot {
    param([Parameter(Mandatory)][string]$Path)
    $fullPath = [IO.Path]::GetFullPath($Path).TrimEnd('\')
    $tempParent = [IO.Path]::GetFullPath([IO.Path]::GetTempPath()).TrimEnd('\')
    if (-not [string]::Equals([IO.Path]::GetDirectoryName($fullPath), $tempParent,
            [StringComparison]::OrdinalIgnoreCase)) {
        throw 'Runtime cleanup target is outside the operating-system temporary directory.'
    }
    if ([IO.Path]::GetFileName($fullPath) -notmatch '\Ablackhorse-m1-native-[a-f0-9]{32}\z') {
        throw 'Runtime cleanup target does not have the approved isolated leaf name.'
    }
    if ($fullPath -eq $tempParent) {
        throw 'Runtime cleanup target cannot be the temporary directory root.'
    }
    return $fullPath
}

function Assert-RunMarker {
    param([Parameter(Mandatory)][string]$Path, [Parameter(Mandatory)][string]$Nonce)
    $markerPath = Join-Path $Path '.lab-smoke-wrapper-owner'
    if (-not (Test-Path -LiteralPath $markerPath -PathType Leaf)) {
        throw 'Runtime cleanup marker is missing.'
    }
    $actual = [IO.File]::ReadAllText($markerPath, [Text.UTF8Encoding]::new($false)).Trim()
    if (-not [string]::Equals($actual, $Nonce, [StringComparison]::Ordinal)) {
        throw 'Runtime cleanup marker does not match the run nonce.'
    }
}

function Write-SecureTextFile {
    param([Parameter(Mandatory)][string]$Path, [Parameter(Mandatory)][string]$Content)
    $parent = Split-Path -Parent $Path
    if (-not (Test-Path -LiteralPath $parent -PathType Container)) {
        [void][IO.Directory]::CreateDirectory($parent)
    }
    [IO.File]::WriteAllText($Path, $Content, [Text.UTF8Encoding]::new($false))
}

function Get-FreeLoopbackPort {
    param([int[]]$ExcludedPorts = @())
    for ($attempt = 0; $attempt -lt 40; $attempt++) {
        $listener = [Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback, 0)
        try {
            $listener.Start()
            $port = ([Net.IPEndPoint]$listener.LocalEndpoint).Port
        }
        finally {
            $listener.Stop()
        }
        if ($port -notin $ExcludedPorts -and $port -ne 3306 -and $port -ne 6379) {
            return $port
        }
    }
    throw 'Unable to reserve a candidate loopback port.'
}

function Get-ListenerRows {
    param([Parameter(Mandatory)][int]$Port)
    return @(Get-NetTCPConnection -State Listen -LocalPort $Port -ErrorAction SilentlyContinue |
        Sort-Object LocalAddress, OwningProcess |
        Select-Object LocalAddress, LocalPort, OwningProcess)
}

function Wait-TcpState {
    param(
        [Parameter(Mandatory)][int]$Port,
        [Parameter(Mandatory)][bool]$Open,
        [ValidateRange(1, 180)][int]$TimeoutSeconds = 60
    )
    $deadline = [DateTime]::UtcNow.AddSeconds($TimeoutSeconds)
    do {
        $isOpen = $false
        $client = [Net.Sockets.TcpClient]::new()
        try {
            $task = $client.ConnectAsync([Net.IPAddress]::Loopback, $Port)
            $isOpen = $task.Wait(250) -and $client.Connected
        }
        catch {
            $isOpen = $false
        }
        finally {
            $client.Dispose()
        }
        if ($isOpen -eq $Open) {
            return
        }
        Start-Sleep -Milliseconds 200
    } while ([DateTime]::UtcNow -lt $deadline)
    throw "Loopback port $Port did not reach the requested state."
}

function Get-ProtectedSnapshot {
    $services = @(Get-CimInstance Win32_Service -ErrorAction Stop |
        Where-Object { $_.Name -match '(?i)mysql|maria' -or $_.DisplayName -match '(?i)mysql|maria' } |
        Sort-Object Name |
        ForEach-Object {
            [ordered]@{
                Name = [string]$_.Name
                State = [string]$_.State
                StartMode = [string]$_.StartMode
                ProcessId = [uint32]$_.ProcessId
                PathName = [string]$_.PathName
            }
        })
    $listeners = @(Get-ListenerRows -Port 3306 | ForEach-Object {
            $processName = ''
            $startTicks = 0L
            $executablePath = ''
            try {
                $process = Get-Process -Id ([int]$_.OwningProcess) -ErrorAction Stop
                $processName = $process.ProcessName
                $startTicks = $process.StartTime.ToUniversalTime().Ticks
                try { $executablePath = [string]$process.Path } catch { $executablePath = '' }
            }
            catch {
                $processName = '<unavailable>'
            }
            [ordered]@{
                LocalAddress = [string]$_.LocalAddress
                LocalPort = [int]$_.LocalPort
                OwningProcess = [int]$_.OwningProcess
                ProcessName = $processName
                StartTimeUtcTicks = $startTicks
                ExecutablePath = $executablePath
            }
        })
    return [pscustomobject]@{
        Services = $services
        Listeners3306 = $listeners
        Canonical = ([ordered]@{ Services = $services; Listeners3306 = $listeners } |
            ConvertTo-Json -Depth 8 -Compress)
    }
}

function ConvertTo-WindowsCommandLine {
    param([AllowEmptyCollection()][Parameter(Mandatory)][string[]]$Arguments)
    $parts = [System.Collections.Generic.List[string]]::new()
    foreach ($argument in $Arguments) {
        if ($null -eq $argument) {
            throw 'A native process argument cannot be null.'
        }
        if ($argument.Length -gt 0 -and $argument -notmatch '[\s"]') {
            [void]$parts.Add($argument)
            continue
        }

        $quoted = [Text.StringBuilder]::new()
        [void]$quoted.Append([char]34)
        $backslashCount = 0
        foreach ($character in $argument.ToCharArray()) {
            if ($character -eq [char]92) {
                $backslashCount++
                continue
            }
            if ($character -eq [char]34) {
                for ($index = 0; $index -lt ($backslashCount * 2 + 1); $index++) {
                    [void]$quoted.Append([char]92)
                }
                [void]$quoted.Append($character)
                $backslashCount = 0
                continue
            }
            for ($index = 0; $index -lt $backslashCount; $index++) {
                [void]$quoted.Append([char]92)
            }
            [void]$quoted.Append($character)
            $backslashCount = 0
        }
        for ($index = 0; $index -lt ($backslashCount * 2); $index++) {
            [void]$quoted.Append([char]92)
        }
        [void]$quoted.Append([char]34)
        [void]$parts.Add($quoted.ToString())
    }
    return $parts -join ' '
}

function New-ProcessStartInfo {
    param(
        [Parameter(Mandatory)][string]$FileName,
        [string[]]$Arguments = @(),
        [string]$WorkingDirectory = $RepositoryRoot,
        [hashtable]$Environment = @{},
        [switch]$Redirect
    )
    $startInfo = [Diagnostics.ProcessStartInfo]::new()
    $startInfo.FileName = $FileName
    $startInfo.WorkingDirectory = $WorkingDirectory
    $startInfo.UseShellExecute = $false
    $startInfo.CreateNoWindow = $true
    $startInfo.Arguments = ConvertTo-WindowsCommandLine -Arguments $Arguments
    foreach ($key in @($startInfo.EnvironmentVariables.Keys)) {
        if ($key -match '(?i)^(LAB_|SPRING_|MYSQL_|REDISCLI_AUTH$|JAVA_TOOL_OPTIONS$|JDK_JAVA_OPTIONS$|_JAVA_OPTIONS$|MAVEN_ARGS$|MAVEN_OPTS$|M2_HOME$)') {
            [void]$startInfo.EnvironmentVariables.Remove($key)
        }
    }
    foreach ($entry in $Environment.GetEnumerator()) {
        $startInfo.EnvironmentVariables[[string]$entry.Key] = [string]$entry.Value
    }
    if ($Redirect) {
        $startInfo.RedirectStandardOutput = $true
        $startInfo.RedirectStandardError = $true
        $startInfo.RedirectStandardInput = $true
    }
    return $startInfo
}

function Invoke-CapturedProcess {
    param(
        [Parameter(Mandatory)][string]$FileName,
        [string[]]$Arguments = @(),
        [string]$WorkingDirectory = $RepositoryRoot,
        [hashtable]$Environment = @{},
        [AllowNull()][string]$StandardInput = $null,
        [ValidateRange(1, 1200)][int]$TimeoutSeconds = 60
    )
    $startInfo = New-ProcessStartInfo -FileName $FileName -Arguments $Arguments `
        -WorkingDirectory $WorkingDirectory -Environment $Environment -Redirect
    $process = [Diagnostics.Process]::new()
    $process.StartInfo = $startInfo
    try {
        if (-not $process.Start()) {
            throw 'The child process did not start.'
        }
        # Process.MainModule can transiently be null for a child that exits immediately on
        # Windows PowerShell 5.1.  The start request already contains the authoritative
        # executable path, so capture it without racing a second process-table lookup.
        $identity = [pscustomobject]@{
            ProcessId = $process.Id
            ExecutablePath = [IO.Path]::GetFullPath($startInfo.FileName)
            StartTimeUtcTicks = $process.StartTime.ToUniversalTime().Ticks
        }
        $stdoutTask = $process.StandardOutput.ReadToEndAsync()
        $stderrTask = $process.StandardError.ReadToEndAsync()
        if ($null -ne $StandardInput) {
            $process.StandardInput.Write($StandardInput)
        }
        $process.StandardInput.Close()
        if (-not $process.WaitForExit($TimeoutSeconds * 1000)) {
            try {
                Stop-CapturedProcessTree -RootIdentity $identity
            }
            catch {
                throw "Timed-out child process cleanup failed: $($_.Exception.Message)"
            }
            throw "Child process exceeded the $TimeoutSeconds-second timeout."
        }
        $stdout = $stdoutTask.GetAwaiter().GetResult()
        $stderr = $stderrTask.GetAwaiter().GetResult()
        return [pscustomobject]@{
            ExitCode = $process.ExitCode
            Stdout = $stdout
            Stderr = $stderr
        }
    }
    finally {
        $process.Dispose()
    }
}

function Start-OwnedProcess {
    param(
        [Parameter(Mandatory)][string]$FileName,
        [string[]]$Arguments = @(),
        [AllowEmptyCollection()][Parameter(Mandatory)]
        [System.Collections.Generic.List[object]]$Registry,
        [string]$WorkingDirectory = $RuntimeRoot
    )
    $startInfo = New-ProcessStartInfo -FileName $FileName -Arguments $Arguments `
        -WorkingDirectory $WorkingDirectory
    $process = [Diagnostics.Process]::new()
    $process.StartInfo = $startInfo
    if (-not $process.Start()) {
        throw 'The isolated server process did not start.'
    }
    Start-Sleep -Milliseconds 100
    if ($process.HasExited) {
        $exitCode = $process.ExitCode
        $process.Dispose()
        throw "The isolated server process exited during startup with code $exitCode."
    }
    $record = [pscustomobject]@{
        ProcessId = $process.Id
        ExecutablePath = [IO.Path]::GetFullPath($process.MainModule.FileName)
        StartTimeUtcTicks = $process.StartTime.ToUniversalTime().Ticks
    }
    [void]$Registry.Add($record)
    $process.Dispose()
    return $record
}

function Test-OwnedIdentity {
    param([Parameter(Mandatory)]$Identity)
    try {
        $process = Get-Process -Id ([int]$Identity.ProcessId) -ErrorAction Stop
        $livePath = [IO.Path]::GetFullPath($process.MainModule.FileName)
        $liveTicks = $process.StartTime.ToUniversalTime().Ticks
        return $liveTicks -eq [long]$Identity.StartTimeUtcTicks -and
            [string]::Equals($livePath, [string]$Identity.ExecutablePath,
                [StringComparison]::OrdinalIgnoreCase)
    }
    catch {
        return $false
    }
}

function Get-ProcessIdentity {
    param([Parameter(Mandatory)][int]$ProcessId)
    $process = Get-Process -Id $ProcessId -ErrorAction Stop
    try {
        return [pscustomobject]@{
            ProcessId = $process.Id
            ExecutablePath = [IO.Path]::GetFullPath($process.MainModule.FileName)
            StartTimeUtcTicks = $process.StartTime.ToUniversalTime().Ticks
        }
    }
    finally {
        $process.Dispose()
    }
}

function Get-DescendantProcessIdentities {
    param([Parameter(Mandatory)][int]$RootProcessId)
    $queue = [System.Collections.Generic.Queue[object]]::new()
    $queue.Enqueue([pscustomobject]@{ ProcessId = $RootProcessId; Depth = 0 })
    $identities = [System.Collections.Generic.List[object]]::new()
    while ($queue.Count -gt 0) {
        $parent = $queue.Dequeue()
        $children = @(Get-CimInstance Win32_Process -Filter "ParentProcessId = $($parent.ProcessId)" `
                -ErrorAction SilentlyContinue)
        foreach ($child in $children) {
            $childProcessId = [int]$child.ProcessId
            try {
                $identity = Get-ProcessIdentity -ProcessId $childProcessId
                $identity | Add-Member -NotePropertyName Depth -NotePropertyValue ($parent.Depth + 1)
                [void]$identities.Add($identity)
                $queue.Enqueue([pscustomobject]@{
                        ProcessId = $childProcessId
                        Depth = $parent.Depth + 1
                    })
            }
            catch {
                continue
            }
        }
    }
    return @($identities)
}

function Stop-CapturedProcessTree {
    param([Parameter(Mandatory)]$RootIdentity, [ValidateRange(1, 60)][int]$GraceSeconds = 10)
    $descendants = @(Get-DescendantProcessIdentities -RootProcessId ([int]$RootIdentity.ProcessId))
    $targets = @($descendants + @($RootIdentity) | Sort-Object -Property `
            @{ Expression = 'Depth'; Descending = $true }, `
            @{ Expression = 'ProcessId'; Descending = $true })
    foreach ($identity in $targets) {
        if (Test-OwnedIdentity $identity) {
            Stop-Process -Id ([int]$identity.ProcessId) -Force -ErrorAction Stop
        }
    }
    $deadline = [DateTime]::UtcNow.AddSeconds($GraceSeconds)
    do {
        if (-not (Test-OwnedIdentity $RootIdentity)) {
            return
        }
        Start-Sleep -Milliseconds 200
    } while ([DateTime]::UtcNow -lt $deadline)
    if (Test-OwnedIdentity $RootIdentity) {
        throw 'Timed-out child process retained its verified process identity after cleanup.'
    }
}

function Test-DescendantProcess {
    param([Parameter(Mandatory)][int]$ProcessId, [Parameter(Mandatory)][int]$AncestorId)
    $current = $ProcessId
    for ($depth = 0; $depth -lt 32; $depth++) {
        if ($current -eq $AncestorId) {
            return $true
        }
        $row = Get-CimInstance Win32_Process -Filter "ProcessId = $current" -ErrorAction SilentlyContinue
        if ($null -eq $row -or [int]$row.ParentProcessId -le 0 -or [int]$row.ParentProcessId -eq $current) {
            return $false
        }
        $current = [int]$row.ParentProcessId
    }
    return $false
}

function Register-OwnedListener {
    param(
        [Parameter(Mandatory)][int]$Port,
        [Parameter(Mandatory)]$RootIdentity,
        [Parameter(Mandatory)][string]$ExpectedExecutable,
        [AllowEmptyCollection()][Parameter(Mandatory)]
        [System.Collections.Generic.List[object]]$Registry,
        [int[]]$ProtectedProcessIds = @()
    )
    $listeners = @(Get-ListenerRows -Port $Port)
    if ($listeners.Count -eq 0) {
        throw "The isolated listener on port $Port is absent."
    }
    $listenerIds = @($listeners | Select-Object -ExpandProperty OwningProcess -Unique)
    if ($listenerIds.Count -ne 1) {
        throw "The isolated listener on port $Port has ambiguous ownership."
    }
    $listenerId = [int]$listenerIds[0]
    if ($listenerId -in $ProtectedProcessIds) {
        throw 'An isolated port resolved to a protected pre-existing process.'
    }
    if (-not (Test-DescendantProcess -ProcessId $listenerId -AncestorId ([int]$RootIdentity.ProcessId))) {
        throw 'The isolated listener is outside the process tree started by this driver.'
    }
    $process = Get-Process -Id $listenerId -ErrorAction Stop
    $listenerPath = [IO.Path]::GetFullPath($process.MainModule.FileName)
    if (-not [string]::Equals($listenerPath, [IO.Path]::GetFullPath($ExpectedExecutable),
            [StringComparison]::OrdinalIgnoreCase)) {
        throw 'The isolated listener executable identity is unexpected.'
    }
    $addresses = @($listeners | Select-Object -ExpandProperty LocalAddress -Unique)
    if (@($addresses | Where-Object { $_ -ne '127.0.0.1' }).Count -ne 0) {
        throw 'The isolated listener is not restricted to IPv4 loopback.'
    }
    if (-not ($Registry | Where-Object { $_.ProcessId -eq $listenerId })) {
        [void]$Registry.Add([pscustomobject]@{
                ProcessId = $listenerId
                ExecutablePath = $listenerPath
                StartTimeUtcTicks = $process.StartTime.ToUniversalTime().Ticks
            })
    }
    return $listenerId
}

function Invoke-MemuraiCli {
    param([Parameter(Mandatory)][string[]]$Command)
    return Invoke-CapturedProcess -FileName $MemuraiClient `
        -Arguments (@('-h', '127.0.0.1', '-p', [string]$MemuraiPort, '--no-auth-warning') + $Command) `
        -WorkingDirectory $RuntimeRoot -Environment @{ REDISCLI_AUTH = $script:RedisPassword }
}

function Invoke-MySqlAdminShutdown {
    if ([string]::IsNullOrWhiteSpace($MySqlAdminDefaults) -or
        -not (Test-Path -LiteralPath $MySqlAdminDefaults -PathType Leaf)) {
        return
    }
    [void](Invoke-CapturedProcess -FileName $MySqlAdmin `
            -Arguments @("--defaults-extra-file=$MySqlAdminDefaults", 'shutdown') `
            -WorkingDirectory $RuntimeRoot)
}

function Stop-OwnedRegistry {
    param(
        [AllowEmptyCollection()][Parameter(Mandatory)]
        [System.Collections.Generic.List[object]]$Registry,
        [Parameter(Mandatory)][string]$Description
    )
    $deadline = [DateTime]::UtcNow.AddSeconds(20)
    do {
        $remaining = @($Registry | Where-Object { Test-OwnedIdentity $_ })
        if ($remaining.Count -eq 0) {
            return
        }
        Start-Sleep -Milliseconds 250
    } while ([DateTime]::UtcNow -lt $deadline)

    foreach ($identity in @($remaining | Sort-Object ProcessId -Descending)) {
        if (-not (Test-OwnedIdentity $identity)) {
            continue
        }
        Stop-Process -Id ([int]$identity.ProcessId) -Force -ErrorAction Stop
    }
    Start-Sleep -Milliseconds 500
    $stillRunning = @($Registry | Where-Object { Test-OwnedIdentity $_ })
    if ($stillRunning.Count -ne 0) {
        throw "$Description retained an owned process after identity-safe cleanup."
    }
}

function Invoke-Maven {
    param(
        [Parameter(Mandatory)][string[]]$Arguments,
        [Parameter(Mandatory)][hashtable]$Environment,
        [ValidateRange(1, 1200)][int]$TimeoutSeconds = 60
    )
    foreach ($argument in $Arguments) {
        if ($argument -notmatch '\A[A-Za-z0-9_.:,/\\?=-]+\z') {
            throw 'Unsafe Maven argument detected.'
        }
    }
    $startInfo = New-ProcessStartInfo -FileName $env:ComSpec -WorkingDirectory $RepositoryRoot `
        -Environment $Environment -Redirect
    $startInfo.Arguments = '/d /s /c call "' + $MavenCommand + '" ' + ($Arguments -join ' ')
    $process = [Diagnostics.Process]::new()
    $process.StartInfo = $startInfo
    try {
        if (-not $process.Start()) {
            throw 'The Maven process did not start.'
        }
        $identity = [pscustomobject]@{
            ProcessId = $process.Id
            ExecutablePath = [IO.Path]::GetFullPath($startInfo.FileName)
            StartTimeUtcTicks = $process.StartTime.ToUniversalTime().Ticks
        }
        $stdoutTask = $process.StandardOutput.ReadToEndAsync()
        $stderrTask = $process.StandardError.ReadToEndAsync()
        $process.StandardInput.Close()
        if (-not $process.WaitForExit($TimeoutSeconds * 1000)) {
            try {
                Stop-CapturedProcessTree -RootIdentity $identity
            }
            catch {
                throw "Timed-out Maven process cleanup failed: $($_.Exception.Message)"
            }
            throw "Maven process exceeded the $TimeoutSeconds-second timeout."
        }
        return [pscustomobject]@{
            ExitCode = $process.ExitCode
            Stdout = $stdoutTask.GetAwaiter().GetResult()
            Stderr = $stderrTask.GetAwaiter().GetResult()
        }
    }
    finally {
        $process.Dispose()
    }
}

function Invoke-PowerShellScript {
    param(
        [Parameter(Mandatory)][string]$ScriptPath,
        [Parameter(Mandatory)][hashtable]$Environment,
        [ValidateRange(1, 1200)][int]$TimeoutSeconds = 1200
    )
    if (-not (Test-Path -LiteralPath $ScriptPath -PathType Leaf)) {
        throw "Required repository verification script is missing: $ScriptPath"
    }
    $windowsPowerShell = Join-Path $env:SystemRoot 'System32\WindowsPowerShell\v1.0\powershell.exe'
    [void](Assert-ExistingFile -Path $windowsPowerShell -Description 'Windows PowerShell host')
    return Invoke-CapturedProcess -FileName $windowsPowerShell `
        -Arguments @('-NoLogo', '-NoProfile', '-NonInteractive', '-ExecutionPolicy', 'Bypass',
            '-File', $ScriptPath) `
        -WorkingDirectory $RepositoryRoot -Environment $Environment -TimeoutSeconds $TimeoutSeconds
}

function Get-SmokeEvidence {
    param([Parameter(Mandatory)][string]$Output)
    $passes = @($Output -split '\r?\n' | Where-Object { $_ -like 'PASS: *' })
    if ($passes.Count -lt 10) {
        throw 'Foundation smoke output did not contain the required PASS evidence.'
    }
    return [pscustomobject]@{ Passes = $passes.Count }
}

function Get-TestEvidence {
    param([Parameter(Mandatory)][DateTime]$MavenStartedUtc, [Parameter(Mandatory)][string]$MavenOutput)
    $reportRoot = Join-Path $RepositoryRoot 'ruoyi-admin\target\surefire-reports'
    $total = 0
    $failures = 0
    $errors = 0
    $skipped = 0
    $flywayVersion = $null
    $databaseReportsSkipped = 0

    foreach ($reportName in $ExpectedReports) {
        $reportPath = Join-Path $reportRoot $reportName
        if (-not (Test-Path -LiteralPath $reportPath -PathType Leaf)) {
            throw "Expected Surefire report is missing: $reportName"
        }
        $item = Get-Item -LiteralPath $reportPath
        if ($item.LastWriteTimeUtc -lt $MavenStartedUtc.AddSeconds(-5)) {
            throw "Expected Surefire report is stale: $reportName"
        }
        [xml]$document = [IO.File]::ReadAllText($reportPath)
        $suite = $document.testsuite
        $suiteTests = [int]$suite.tests
        $suiteFailures = [int]$suite.failures
        $suiteErrors = [int]$suite.errors
        $suiteSkipped = if ($null -eq $suite.skipped -or $suite.skipped -eq '') { 0 } else { [int]$suite.skipped }
        $total += $suiteTests
        $failures += $suiteFailures
        $errors += $suiteErrors
        $skipped += $suiteSkipped
        if ($reportName -like '*LabRoleSeedIT.xml' -or $reportName -like '*LabSystemOperatorLoginIT.xml') {
            $databaseReportsSkipped += $suiteSkipped
        }
        if ([string]::IsNullOrWhiteSpace($flywayVersion)) {
            $classPathNode = @($suite.properties.property |
                Where-Object { $_.name -eq 'java.class.path' } |
                Select-Object -First 1)
            if ($classPathNode.Count -eq 1) {
                $match = [regex]::Match([string]$classPathNode[0].value,
                    '(?i)flyway-core-(?<version>[0-9]+(?:\.[0-9]+){1,3})\.jar')
                if ($match.Success) {
                    $flywayVersion = $match.Groups['version'].Value
                }
            }
        }
    }
    if ([string]::IsNullOrWhiteSpace($flywayVersion)) {
        $match = [regex]::Match($MavenOutput,
            '(?im)\bFlyway(?: Community Edition)?\s+(?<version>[0-9]+(?:\.[0-9]+){1,3})\b')
        if ($match.Success) {
            $flywayVersion = $match.Groups['version'].Value
        }
    }
    if ([string]::IsNullOrWhiteSpace($flywayVersion)) {
        throw 'The Flyway runtime version could not be proven from fresh test evidence.'
    }
    if ($databaseReportsSkipped -ne 0) {
        throw 'A real-database Task7 test was skipped.'
    }
    if ($failures -ne 0 -or $errors -ne 0 -or $skipped -ne 0) {
        throw "Task7 test reports are not fully green (tests=$total failures=$failures errors=$errors skipped=$skipped)."
    }
    return [pscustomobject]@{
        Tests = $total
        Failures = $failures
        Errors = $errors
        Skipped = $skipped
        FlywayVersion = $flywayVersion
        Reports = $ExpectedReports.Count
    }
}

function Get-SanitizedTail {
    param([AllowEmptyString()][string]$Text, [int]$LineCount = 100)
    $lines = @(($Text -split '\r?\n') | Where-Object { $_ -ne '' })
    return Protect-Text (($lines | Select-Object -Last $LineCount) -join [Environment]::NewLine)
}

try {
    Write-Output 'STAGE: preflight native toolchain and protected MySQL baseline'
    $RepositoryRoot = [IO.Path]::GetFullPath($RepositoryRoot)
    if (-not (Test-Path -LiteralPath $RepositoryRoot -PathType Container)) {
        throw 'Repository root is missing.'
    }
    foreach ($path in @($MySqlServer, $MySqlClient, $MySqlAdmin, $MemuraiServer,
            $MemuraiClient, (Join-Path $JavaHome 'bin\java.exe'), $MavenCommand)) {
        [void](Assert-ExistingFile -Path $path -Description 'Required native executable')
    }
    foreach ($ancestor in @($RepositoryRoot, (Split-Path -Parent $RepositoryRoot),
            (Split-Path -Parent (Split-Path -Parent $RepositoryRoot)))) {
        if (Test-Path -LiteralPath (Join-Path $ancestor '.mvn')) {
            throw 'Repository and ancestor .mvn startup configuration is not allowed.'
        }
    }

    $ProtectedBefore = Get-ProtectedSnapshot
    $protectedProcessIds = @(
        @($ProtectedBefore.Services | ForEach-Object { [int]$_.ProcessId }) +
        @($ProtectedBefore.Listeners3306 | ForEach-Object { [int]$_.OwningProcess }) |
        Where-Object { $_ -gt 0 } | Select-Object -Unique
    )

    $RuntimeRoot = New-SecureRuntimeRoot
    $RunNonce = New-RandomHex -ByteCount 32
    Add-SensitiveValue $RunNonce
    Write-SecureTextFile -Path (Join-Path $RuntimeRoot '.lab-smoke-wrapper-owner') -Content $RunNonce
    $mysqlTemporaryRoot = Join-Path $RuntimeRoot 'mysql-root'
    $memuraiTemporaryRoot = Join-Path $RuntimeRoot 'memurai-root'
    [void][IO.Directory]::CreateDirectory($mysqlTemporaryRoot)
    [void][IO.Directory]::CreateDirectory($memuraiTemporaryRoot)
    Write-SecureTextFile -Path (Join-Path $mysqlTemporaryRoot '.lab-smoke-wrapper-owner') -Content $RunNonce
    Write-SecureTextFile -Path (Join-Path $memuraiTemporaryRoot '.lab-smoke-wrapper-owner') -Content $RunNonce
    [void][IO.Directory]::CreateDirectory((Join-Path $mysqlTemporaryRoot 'mysql-data'))
    [void][IO.Directory]::CreateDirectory((Join-Path $mysqlTemporaryRoot 'mysql-tmp'))
    [void][IO.Directory]::CreateDirectory((Join-Path $memuraiTemporaryRoot 'memurai-data'))
    [void][IO.Directory]::CreateDirectory((Join-Path $RuntimeRoot 'profile'))
    [void][IO.Directory]::CreateDirectory((Join-Path $RuntimeRoot 'config'))

    $MySqlPort = Get-FreeLoopbackPort -ExcludedPorts @(3306, 6379)
    $MemuraiPort = Get-FreeLoopbackPort -ExcludedPorts @(3306, 6379, $MySqlPort)
    if (@(Get-ListenerRows -Port $MySqlPort).Count -ne 0 -or
        @(Get-ListenerRows -Port $MemuraiPort).Count -ne 0) {
        throw 'A selected isolated port was claimed before startup.'
    }

    $databaseName = 'lab_test_' + (New-RandomHex -ByteCount 8)
    $adminUser = 'labadm_' + (New-RandomHex -ByteCount 6)
    $appUser = 'labapp_' + (New-RandomHex -ByteCount 6)
    $adminPassword = 'A9!' + (New-RandomHex -ByteCount 28)
    $appPassword = 'A9!' + (New-RandomHex -ByteCount 28)
    $rootPassword = 'A9!' + (New-RandomHex -ByteCount 28)
    $script:RedisPassword = 'R9!' + (New-RandomHex -ByteCount 28)
    $tokenSecret = 'T9!' + (New-RandomHex -ByteCount 48)
    $demoStudentPassword = 'D9!' + (New-RandomHex -ByteCount 6)
    $demoManagerPassword = 'D9!' + (New-RandomHex -ByteCount 6)
    $demoSafetyPassword = 'D9!' + (New-RandomHex -ByteCount 6)
    $demoRepairPassword = 'D9!' + (New-RandomHex -ByteCount 6)
    $demoAdminPassword = 'D9!' + (New-RandomHex -ByteCount 6)
    $jdbcUrl = "jdbc:mysql://127.0.0.1:$MySqlPort/$databaseName" +
        '?useUnicode=true&characterEncoding=UTF-8&serverTimezone=UTC&allowPublicKeyRetrieval=true&useSSL=false'
    foreach ($secret in @($databaseName, $adminUser, $appUser, $adminPassword, $appPassword,
            $rootPassword, $script:RedisPassword, $tokenSecret, $demoStudentPassword,
            $demoManagerPassword, $demoSafetyPassword, $demoRepairPassword, $demoAdminPassword,
            $jdbcUrl)) {
        Add-SensitiveValue $secret
    }

    $mysqlDataPath = Join-Path $mysqlTemporaryRoot 'mysql-data'
    $mysqlData = $mysqlDataPath.Replace('\', '/')
    $mysqlTmp = (Join-Path $mysqlTemporaryRoot 'mysql-tmp').Replace('\', '/')
    $mysqlErrorLog = (Join-Path $mysqlTemporaryRoot 'mysql-error.log').Replace('\', '/')
    $mysqlPidFile = (Join-Path $mysqlTemporaryRoot 'mysql.pid').Replace('\', '/')
    $mysqlIni = Join-Path $mysqlTemporaryRoot 'mysql.ini'
    $mysqlConfiguration = @"
[mysqld]
basedir=$($MySqlBase.Replace('\', '/'))
datadir=$mysqlData
tmpdir=$mysqlTmp
port=$MySqlPort
bind-address=127.0.0.1
mysqlx=0
skip-log-bin
local-infile=0
character-set-server=utf8mb4
collation-server=utf8mb4_0900_ai_ci
pid-file=$mysqlPidFile
log-error=$mysqlErrorLog
performance-schema=OFF
"@
    Write-SecureTextFile -Path $mysqlIni -Content $mysqlConfiguration

    Write-Output 'STAGE: initialize isolated loopback MySQL and Memurai runtimes'
    $initialization = Invoke-CapturedProcess -FileName $MySqlServer `
        -Arguments @("--defaults-file=$mysqlIni", '--initialize-insecure', '--console') `
        -WorkingDirectory $RuntimeRoot -TimeoutSeconds 180
    if ($initialization.ExitCode -ne 0) {
        throw "MySQL initialize-insecure failed with exit code $($initialization.ExitCode)."
    }

    $MySqlStartRecord = Start-OwnedProcess -FileName $MySqlServer `
        -Arguments @("--defaults-file=$mysqlIni", "--datadir=$mysqlDataPath", "--port=$MySqlPort",
            '--bind-address=127.0.0.1', '--console') -Registry $OwnedMySql
    Wait-TcpState -Port $MySqlPort -Open $true -TimeoutSeconds 90
    $MySqlListenerProcessId = Register-OwnedListener -Port $MySqlPort -RootIdentity $MySqlStartRecord `
            -ExpectedExecutable $MySqlServer -Registry $OwnedMySql `
            -ProtectedProcessIds $protectedProcessIds

    $bootstrapSql = @"
CREATE DATABASE ``$databaseName`` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE USER '$adminUser'@'127.0.0.1' IDENTIFIED BY '$adminPassword';
CREATE USER '$appUser'@'127.0.0.1' IDENTIFIED BY '$appPassword';
GRANT ALL PRIVILEGES ON ``$databaseName``.* TO '$adminUser'@'127.0.0.1';
GRANT CREATE, DROP, PROCESS, SHUTDOWN ON *.* TO '$adminUser'@'127.0.0.1';
GRANT ALL PRIVILEGES ON ``lab_test_verify``.* TO '$adminUser'@'127.0.0.1';
GRANT ALL PRIVILEGES ON ``lab_test_verify``.* TO '$appUser'@'127.0.0.1';
GRANT ALL PRIVILEGES ON ``lab_test_m1_smoke``.* TO '$adminUser'@'127.0.0.1';
GRANT ALL PRIVILEGES ON ``lab_test_m1_smoke``.* TO '$appUser'@'127.0.0.1';
GRANT ALL PRIVILEGES ON ``$databaseName``.* TO '$appUser'@'127.0.0.1';
ALTER USER 'root'@'localhost' IDENTIFIED BY '$rootPassword';
FLUSH PRIVILEGES;
"@
    $bootstrap = Invoke-CapturedProcess -FileName $MySqlClient `
        -Arguments @('--no-defaults', '--protocol=TCP', '--host=127.0.0.1', "--port=$MySqlPort",
            '--user=root', '--connect-timeout=5') `
        -WorkingDirectory $RuntimeRoot -StandardInput $bootstrapSql
    if ($bootstrap.ExitCode -ne 0) {
        $bootstrapDiagnostic = Get-SanitizedTail `
            -Text ($bootstrap.Stdout + [Environment]::NewLine + $bootstrap.Stderr) `
            -LineCount 20
        throw "MySQL account bootstrap failed with exit code $($bootstrap.ExitCode): $bootstrapDiagnostic"
    }
    $bootstrapSql = $null

    $MySqlAdminDefaults = Join-Path $RuntimeRoot 'mysql-admin.cnf'
    Write-SecureTextFile -Path $MySqlAdminDefaults -Content @"
[client]
protocol=TCP
host=127.0.0.1
port=$MySqlPort
user=$adminUser
password=$adminPassword
"@

    $memuraiConfig = Join-Path $memuraiTemporaryRoot 'memurai.conf'
    $memuraiData = (Join-Path $memuraiTemporaryRoot 'memurai-data').Replace('\', '/')
    $memuraiLog = (Join-Path $memuraiTemporaryRoot 'memurai.log').Replace('\', '/')
    Write-SecureTextFile -Path $memuraiConfig -Content @"
bind 127.0.0.1
protected-mode yes
port $MemuraiPort
requirepass $($script:RedisPassword)
save ""
appendonly no
dir "$memuraiData"
logfile "$memuraiLog"
daemonize no
"@
    $MemuraiStartRecord = Start-OwnedProcess -FileName $MemuraiServer `
        -Arguments @($memuraiConfig) -Registry $OwnedMemurai
    Wait-TcpState -Port $MemuraiPort -Open $true -TimeoutSeconds 60
    $MemuraiListenerProcessId = Register-OwnedListener -Port $MemuraiPort -RootIdentity $MemuraiStartRecord `
            -ExpectedExecutable $MemuraiServer -Registry $OwnedMemurai `
            -ProtectedProcessIds $protectedProcessIds
    $ping = Invoke-MemuraiCli -Command @('PING')
    if ($ping.ExitCode -ne 0 -or $ping.Stdout.Trim() -ne 'PONG') {
        throw 'Authenticated Memurai readiness check failed.'
    }

    $mysqlVersionResult = Invoke-CapturedProcess -FileName $MySqlServer -Arguments @('--version')
    $memuraiVersionResult = Invoke-CapturedProcess -FileName $MemuraiServer -Arguments @('--version')
    $javaVersionResult = Invoke-CapturedProcess -FileName (Join-Path $JavaHome 'bin\java.exe') `
        -Arguments @('-version')
    $mysqlVersionMatch = [regex]::Match($mysqlVersionResult.Stdout + $mysqlVersionResult.Stderr,
        '(?i)\bVer\s+(?<version>[0-9]+(?:\.[0-9]+){2,3})')
    $memuraiVersionMatch = [regex]::Match($memuraiVersionResult.Stdout + $memuraiVersionResult.Stderr,
        '(?i)\bv=(?<version>[0-9]+(?:\.[0-9]+){2,3})')
    $javaVersionMatch = [regex]::Match($javaVersionResult.Stdout + $javaVersionResult.Stderr,
        '(?i)version\s+"(?<version>17(?:\.[0-9]+){1,3})')
    if (-not $mysqlVersionMatch.Success -or -not $memuraiVersionMatch.Success -or
        -not $javaVersionMatch.Success) {
        throw 'One or more native runtime versions could not be verified.'
    }
    $VersionEvidence.MySql = $mysqlVersionMatch.Groups['version'].Value
    $VersionEvidence.Memurai = $memuraiVersionMatch.Groups['version'].Value
    $VersionEvidence.Jdk = $javaVersionMatch.Groups['version'].Value

    $externalConfig = Join-Path $RuntimeRoot 'config\application-test.yml'
    Write-SecureTextFile -Path $externalConfig -Content @'
spring:
  datasource:
    druid:
      master:
        url: ${LAB_TEST_DB_URL}
        username: ${LAB_TEST_DB_USERNAME}
        password: ${LAB_TEST_DB_PASSWORD}
      slave:
        enabled: false
  flyway:
    enabled: ${LAB_TEST_FLYWAY_ENABLED}
    url: ${LAB_TEST_DB_URL}
    user: ${LAB_TEST_DB_USERNAME}
    password: ${LAB_TEST_DB_PASSWORD}
    locations: classpath:db/migration
    baseline-on-migrate: false
    clean-disabled: true
    validate-on-migrate: true
    placeholder-replacement: false
  data:
    redis:
      host: ${LAB_TEST_REDIS_HOST}
      port: ${LAB_TEST_REDIS_PORT}
      password: ${LAB_TEST_REDIS_PASSWORD}
      database: 0
  sql:
    init:
      mode: never
  quartz:
    jdbc:
      initialize-schema: never
ruoyi:
  profile: ${LAB_TEST_FILE_ROOT}
'@
    $settingsPath = Join-Path $RuntimeRoot 'maven-settings.xml'
    Write-SecureTextFile -Path $settingsPath -Content @'
<?xml version="1.0" encoding="UTF-8"?>
<settings xmlns="http://maven.apache.org/SETTINGS/1.2.0"
          xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
          xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.2.0 https://maven.apache.org/xsd/settings-1.2.0.xsd">
  <interactiveMode>false</interactiveMode>
</settings>
'@

    $configDirectoryUri = ([Uri]((Join-Path $RuntimeRoot 'config') + [IO.Path]::DirectorySeparatorChar)).AbsoluteUri
    $testEnvironment = @{
        LAB_TEST_WRAPPER_ACTIVE = 'true'
        LAB_TEST_ADMIN_HOST = '127.0.0.1'
        LAB_TEST_ADMIN_PORT = [string]$MySqlPort
        LAB_TEST_ADMIN_USERNAME = $adminUser
        LAB_TEST_ADMIN_PASSWORD = $adminPassword
        LAB_TEST_MYSQL_PID = [string]$MySqlListenerProcessId
        LAB_TEST_DB_URL = $jdbcUrl
        LAB_TEST_DB_USERNAME = $appUser
        LAB_TEST_DB_PASSWORD = $appPassword
        LAB_TEST_FLYWAY_ENABLED = 'true'
        LAB_TEST_REDIS_HOST = '127.0.0.1'
        LAB_TEST_REDIS_PORT = [string]$MemuraiPort
        LAB_TEST_REDIS_PASSWORD = $script:RedisPassword
        LAB_TEST_MEMURAI_PID = [string]$MemuraiListenerProcessId
        LAB_TEST_WRAPPER_NONCE = $RunNonce
        LAB_TEST_MYSQL_ROOT = $mysqlTemporaryRoot
        LAB_TEST_MYSQL_DATADIR = $mysqlDataPath
        LAB_TEST_MYSQL_CONFIG = $mysqlIni
        LAB_TEST_MEMURAI_ROOT = $memuraiTemporaryRoot
        LAB_TEST_MEMURAI_CONFIG = $memuraiConfig
        LAB_TEST_JAVA_HOME = $JavaHome
        LAB_TEST_MYSQL_CLIENT_PATH = $MySqlClient
        LAB_TEST_MYSQL_SERVER_PATH = $MySqlServer
        LAB_TEST_MEMURAI_SERVER_PATH = $MemuraiServer
        LAB_TEST_MEMURAI_CLI_PATH = $MemuraiClient
        LAB_TEST_FILE_ROOT = (Join-Path $RuntimeRoot 'profile')
        LAB_REDIS_HOST = '127.0.0.1'
        LAB_REDIS_PORT = [string]$MemuraiPort
        LAB_REDIS_PASSWORD = $script:RedisPassword
        LAB_REDIS_DATABASE = '0'
        LAB_TOKEN_SECRET = $tokenSecret
        LAB_DEMO_STUDENT_PASSWORD = $demoStudentPassword
        LAB_DEMO_MANAGER_PASSWORD = $demoManagerPassword
        LAB_DEMO_SAFETY_PASSWORD = $demoSafetyPassword
        LAB_DEMO_REPAIR_PASSWORD = $demoRepairPassword
        LAB_DEMO_ADMIN_PASSWORD = $demoAdminPassword
        JAVA_HOME = $JavaHome
        MAVEN_SKIP_RC = 'true'
        PATH = ((Join-Path $JavaHome 'bin') + [IO.Path]::PathSeparator +
            (Split-Path -Parent $MavenCommand) + [IO.Path]::PathSeparator +
            (Split-Path -Parent $MySqlClient) + [IO.Path]::PathSeparator + $OriginalPath)
    }

    $mutex = [Threading.Mutex]::new($false, $MavenMutexName)
    $mutexAcquired = $false
    try {
        try {
            $mutexAcquired = $mutex.WaitOne([TimeSpan]::FromMinutes(15))
        }
        catch [Threading.AbandonedMutexException] {
            $mutexAcquired = $true
        }
        if (-not $mutexAcquired) {
            throw 'Timed out waiting for the Task7 Maven mutex.'
        }

        $mavenVersionResult = Invoke-Maven -Arguments @('-version') -Environment $testEnvironment
        $mavenVersionMatch = [regex]::Match(
            $mavenVersionResult.Stdout + $mavenVersionResult.Stderr,
            '(?im)^Apache Maven (?<version>[0-9]+(?:\.[0-9]+){1,3})\b')
        if ($mavenVersionResult.ExitCode -ne 0 -or -not $mavenVersionMatch.Success -or
            ($mavenVersionResult.Stdout + $mavenVersionResult.Stderr) -notmatch '(?i)Java version:\s*17(?:\.|,)') {
            $mavenVersionDiagnostic = Get-SanitizedTail `
                -Text ($mavenVersionResult.Stdout + [Environment]::NewLine + $mavenVersionResult.Stderr) `
                -LineCount 30
            throw "Maven did not verify against the required JDK 17 runtime (exit=$($mavenVersionResult.ExitCode)): $mavenVersionDiagnostic"
        }
        $VersionEvidence.Maven = $mavenVersionMatch.Groups['version'].Value

        Write-Output 'STAGE: run repository verify gate'
        $verificationScript = Join-Path $RepositoryRoot 'scripts\verify.ps1'
        $smokeScript = Join-Path $RepositoryRoot 'scripts\smoke-foundation.ps1'
        $verificationStartedUtc = [DateTime]::UtcNow
        $verificationResult = Invoke-PowerShellScript -ScriptPath $verificationScript `
            -Environment $testEnvironment -TimeoutSeconds 1200
        $VerificationStdout = $verificationResult.Stdout
        $VerificationStderr = $verificationResult.Stderr
        if ($verificationResult.ExitCode -ne 0) {
            throw "Repository verification gate failed with exit code $($verificationResult.ExitCode)."
        }
        $TestEvidence = Get-TestEvidence -MavenStartedUtc $verificationStartedUtc `
            -MavenOutput ($VerificationStdout + [Environment]::NewLine + $VerificationStderr)

        Write-Output 'STAGE: run isolated foundation smoke worker'
        $smokeResult = Invoke-PowerShellScript -ScriptPath $smokeScript `
            -Environment $testEnvironment -TimeoutSeconds 600
        $SmokeStdout = $smokeResult.Stdout
        $SmokeStderr = $smokeResult.Stderr
        if ($smokeResult.ExitCode -ne 0) {
            throw "Foundation smoke script failed with exit code $($smokeResult.ExitCode)."
        }
        $SmokeEvidence = Get-SmokeEvidence -Output ($SmokeStdout + [Environment]::NewLine + $SmokeStderr)
    }
    finally {
        if ($mutexAcquired) {
            $mutex.ReleaseMutex()
        }
        $mutex.Dispose()
    }

    $FinalExitCode = 0
}
catch {
    Add-Failure $_.Exception.Message
    if (-not [string]::IsNullOrWhiteSpace([string]$_.ScriptStackTrace)) {
        Add-Failure ("Runner stack:`n" + [string]$_.ScriptStackTrace)
    }
    $diagnosticText = $VerificationStdout + [Environment]::NewLine + $VerificationStderr +
        [Environment]::NewLine + $SmokeStdout + [Environment]::NewLine + $SmokeStderr
    if (-not [string]::IsNullOrWhiteSpace($diagnosticText)) {
        $tail = Get-SanitizedTail -Text $diagnosticText
        if (-not [string]::IsNullOrWhiteSpace($tail)) {
            Add-Failure ("Repository verification sanitized tail:`n$tail")
        }
    }
    $FinalExitCode = 1
}
finally {
    Write-Output 'STAGE: identity-safe cleanup and protected baseline verification'
    if ($OwnedMemurai.Count -gt 0) {
        try {
            $shutdown = Invoke-MemuraiCli -Command @('SHUTDOWN', 'NOSAVE')
            if ($shutdown.ExitCode -ne 0 -and @(Get-ListenerRows -Port $MemuraiPort).Count -ne 0) {
                throw 'Memurai graceful shutdown command failed.'
            }
        }
        catch {
            Add-Failure ("Memurai graceful cleanup: $($_.Exception.Message)")
        }
        try {
            Stop-OwnedRegistry -Registry $OwnedMemurai -Description 'Memurai'
            if ($MemuraiPort -gt 0) {
                Wait-TcpState -Port $MemuraiPort -Open $false -TimeoutSeconds 20
            }
            $CleanupEvidence.MemuraiStopped = $true
        }
        catch {
            Add-Failure ("Memurai identity-safe cleanup: $($_.Exception.Message)")
            $FinalExitCode = 1
        }
    }
    else {
        $CleanupEvidence.MemuraiStopped = $true
    }

    if ($OwnedMySql.Count -gt 0) {
        try {
            Invoke-MySqlAdminShutdown
        }
        catch {
            Add-Failure ("MySQL graceful cleanup: $($_.Exception.Message)")
        }
        try {
            Stop-OwnedRegistry -Registry $OwnedMySql -Description 'MySQL'
            if ($MySqlPort -gt 0) {
                Wait-TcpState -Port $MySqlPort -Open $false -TimeoutSeconds 20
            }
            $CleanupEvidence.MySqlStopped = $true
        }
        catch {
            Add-Failure ("MySQL identity-safe cleanup: $($_.Exception.Message)")
            $FinalExitCode = 1
        }
    }
    else {
        $CleanupEvidence.MySqlStopped = $true
    }

    if ($null -ne $ProtectedBefore) {
        try {
            $protectedAfter = Get-ProtectedSnapshot
            if ($ProtectedBefore.Canonical -ne $protectedAfter.Canonical) {
                throw 'Pre-existing MySQL service or port 3306 listener identity changed during the run.'
            }
            $CleanupEvidence.ProtectedUnchanged = $true
        }
        catch {
            Add-Failure ("Protected baseline verification: $($_.Exception.Message)")
            $FinalExitCode = 1
        }
    }

    if (-not [string]::IsNullOrWhiteSpace($RuntimeRoot)) {
        try {
            $safeRoot = Assert-SafeRuntimeRoot $RuntimeRoot
            Assert-RunMarker -Path $safeRoot -Nonce $RunNonce
            $ownedAlive = @($OwnedMySql + $OwnedMemurai | Where-Object { Test-OwnedIdentity $_ })
            if ($ownedAlive.Count -ne 0) {
                throw 'Runtime directory cannot be removed while an owned process remains alive.'
            }
            if (Test-Path -LiteralPath $safeRoot) {
                Remove-Item -LiteralPath $safeRoot -Recurse -Force -ErrorAction Stop
            }
            if (Test-Path -LiteralPath $safeRoot) {
                throw 'Runtime directory remains after cleanup.'
            }
            $CleanupEvidence.RuntimeRemoved = $true
        }
        catch {
            Add-Failure ("Runtime cleanup: $($_.Exception.Message)")
            $FinalExitCode = 1
        }
    }

    if ($FailureMessages.Count -ne 0) {
        $FinalExitCode = 1
    }
}

Write-Output 'M1_NATIVE_EVIDENCE_BEGIN'
Write-Output 'CONTAINER_ENGINE=NOT_USED'
if ($VersionEvidence.Contains('MySql')) { Write-Output "MYSQL_VERSION=$($VersionEvidence.MySql)" }
if ($VersionEvidence.Contains('Memurai')) { Write-Output "MEMURAI_VERSION=$($VersionEvidence.Memurai)" }
if ($VersionEvidence.Contains('Jdk')) { Write-Output "JDK_VERSION=$($VersionEvidence.Jdk)" }
if ($VersionEvidence.Contains('Maven')) { Write-Output "MAVEN_VERSION=$($VersionEvidence.Maven)" }
if ($null -ne $TestEvidence) {
    Write-Output "FLYWAY_VERSION=$($TestEvidence.FlywayVersion)"
    Write-Output "TEST_REPORTS=$($TestEvidence.Reports)"
    Write-Output "TESTS=$($TestEvidence.Tests)"
    Write-Output "FAILURES=$($TestEvidence.Failures)"
    Write-Output "ERRORS=$($TestEvidence.Errors)"
    Write-Output "SKIPPED=$($TestEvidence.Skipped)"
}
if ($null -ne $SmokeEvidence) {
    Write-Output "SMOKE_PASSES=$($SmokeEvidence.Passes)"
}
if ($null -ne $ProtectedBefore) {
    $serviceSummary = @($ProtectedBefore.Services | ForEach-Object { "$($_.Name):$($_.ProcessId):$($_.State)" }) -join ','
    $listenerSummary = @($ProtectedBefore.Listeners3306 | ForEach-Object { [string]$_.OwningProcess }) -join ','
    Write-Output "PROTECTED_MYSQL_SERVICES=$(Protect-Text $serviceSummary)"
    Write-Output "PROTECTED_3306_PIDS=$(Protect-Text $listenerSummary)"
}
Write-Output "MYSQL_OWNED_STOPPED=$($CleanupEvidence.MySqlStopped.ToString().ToUpperInvariant())"
Write-Output "MEMURAI_OWNED_STOPPED=$($CleanupEvidence.MemuraiStopped.ToString().ToUpperInvariant())"
Write-Output "PROTECTED_BASELINE_UNCHANGED=$($CleanupEvidence.ProtectedUnchanged.ToString().ToUpperInvariant())"
Write-Output "RUNTIME_REMOVED=$($CleanupEvidence.RuntimeRemoved.ToString().ToUpperInvariant())"
if ($FailureMessages.Count -ne 0) {
    Write-Output 'FAILURE_DETAILS_BEGIN'
    foreach ($message in $FailureMessages) {
        Write-Output (Protect-Text $message)
    }
    Write-Output 'FAILURE_DETAILS_END'
}
Write-Output "RESULT=$(if ($FinalExitCode -eq 0) { 'PASS' } else { 'FAIL' })"
Write-Output 'M1_NATIVE_EVIDENCE_END'
exit $FinalExitCode
