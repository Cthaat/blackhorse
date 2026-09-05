[CmdletBinding()]
param(
    [switch]$SkipBuild,
    [string]$MySqlHome = 'C:\Program Files\MySQL\MySQL Server 8.0',
    [string]$MemuraiHome = 'C:\Program Files\Memurai',
    [string]$JavaHome = 'C:\APP\JDK\jdk_17',
    [string]$MavenCommand = 'C:\Apache\Maven\apache-maven-3.9.16\bin\mvn.cmd'
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$MySqlPort = 33306
$RedisPort = 36379
$BackendPort = 8080
$FrontendPort = 5173
$DatabaseName = 'lab_management'
$AppDatabaseUser = 'lab_app'
$DemoUserNames = @(
    'lab_student',
    'lab_manager',
    'lab_safety_officer',
    'lab_repair_worker',
    'lab_system_admin'
)

$scriptPath = [IO.Path]::GetFullPath([string]$MyInvocation.MyCommand.Path)
$RepositoryRoot = [IO.Path]::GetFullPath((Join-Path (Split-Path -Parent $scriptPath) '..'))
$RuntimeRoot = [IO.Path]::GetFullPath((Join-Path $RepositoryRoot 'target\local-runtime'))
$CredentialsPath = Join-Path $RuntimeRoot 'credentials.json'
$StatePath = Join-Path $RuntimeRoot 'state.json'
$MySqlRootDefaultsPath = Join-Path $RuntimeRoot 'mysql-root.cnf'
$MySqlAppDefaultsPath = Join-Path $RuntimeRoot 'mysql-app.cnf'
$MySqlLoginFileOverride = Join-Path $RuntimeRoot '.disabled-mylogin.cnf'
$MySqlServer = Join-Path $MySqlHome 'bin\mysqld.exe'
$MySqlClient = Join-Path $MySqlHome 'bin\mysql.exe'
$MemuraiServer = Join-Path $MemuraiHome 'memurai.exe'
$NodeCommand = $null
$YarnCommand = $null
$StartedProcesses = [System.Collections.Generic.List[object]]::new()

function Assert-LeafFile {
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
    if ($item.FullName.IndexOf('"') -ge 0 -or $item.FullName.IndexOf("`r") -ge 0 -or
        $item.FullName.IndexOf("`n") -ge 0) {
        throw "$Description contains an unsupported path character."
    }
    return $item.FullName
}

function Assert-RuntimeLocation {
    $expected = [IO.Path]::GetFullPath((Join-Path $RepositoryRoot 'target\local-runtime')).TrimEnd('\')
    $actual = [IO.Path]::GetFullPath($RuntimeRoot).TrimEnd('\')
    if (-not [string]::Equals($actual, $expected, [StringComparison]::OrdinalIgnoreCase)) {
        throw 'The local runtime directory is outside the repository target directory.'
    }
}

function Test-PrivateAcl {
    param(
        [Parameter(Mandatory)][string]$Path,
        [switch]$Directory
    )

    try {
        $currentSid = [Security.Principal.WindowsIdentity]::GetCurrent().User
        $systemSid = [Security.Principal.SecurityIdentifier]::new(
            [Security.Principal.WellKnownSidType]::LocalSystemSid,
            $null
        )
        $security = Get-Acl -LiteralPath $Path
        $ownerSid = $security.GetOwner([Security.Principal.SecurityIdentifier])
        $rules = @($security.GetAccessRules(
                $true,
                $false,
                [Security.Principal.SecurityIdentifier]
            ))
        if (-not $security.AreAccessRulesProtected -or $ownerSid -ne $currentSid -or
            $rules.Count -ne 2) {
            return $false
        }
        $expectedSids = @($currentSid.Value, $systemSid.Value)
        foreach ($rule in $rules) {
            if ($rule.IdentityReference.Value -notin $expectedSids -or
                $rule.AccessControlType -ne [Security.AccessControl.AccessControlType]::Allow -or
                $rule.IsInherited -or
                ($rule.FileSystemRights -band [Security.AccessControl.FileSystemRights]::FullControl) -ne
                    [Security.AccessControl.FileSystemRights]::FullControl -or
                $rule.PropagationFlags -ne [Security.AccessControl.PropagationFlags]::None) {
                return $false
            }
            if ($Directory) {
                $requiredInheritance = [Security.AccessControl.InheritanceFlags]::ContainerInherit -bor
                    [Security.AccessControl.InheritanceFlags]::ObjectInherit
                if (($rule.InheritanceFlags -band $requiredInheritance) -ne $requiredInheritance) {
                    return $false
                }
            }
            elseif ($rule.InheritanceFlags -ne [Security.AccessControl.InheritanceFlags]::None) {
                return $false
            }
        }
        return $true
    }
    catch {
        return $false
    }
}

function Set-PrivateAcl {
    param(
        [Parameter(Mandatory)][string]$Path,
        [switch]$Directory
    )

    if (Test-PrivateAcl -Path $Path -Directory:$Directory) {
        return
    }

    $currentSid = [Security.Principal.WindowsIdentity]::GetCurrent().User
    $systemSid = [Security.Principal.SecurityIdentifier]::new(
        [Security.Principal.WellKnownSidType]::LocalSystemSid,
        $null
    )
    if ($Directory) {
        $security = [Security.AccessControl.DirectorySecurity]::new()
        $inheritance = [Security.AccessControl.InheritanceFlags]::ContainerInherit -bor
            [Security.AccessControl.InheritanceFlags]::ObjectInherit
        $propagation = [Security.AccessControl.PropagationFlags]::None
        foreach ($sid in @($currentSid, $systemSid)) {
            $rule = [Security.AccessControl.FileSystemAccessRule]::new(
                $sid,
                [Security.AccessControl.FileSystemRights]::FullControl,
                $inheritance,
                $propagation,
                [Security.AccessControl.AccessControlType]::Allow
            )
            [void]$security.AddAccessRule($rule)
        }
        $security.SetAccessRuleProtection($true, $false)
        $security.SetOwner($currentSid)
        Set-Acl -LiteralPath $Path -AclObject $security
        return
    }

    $fileSecurity = [Security.AccessControl.FileSecurity]::new()
    foreach ($sid in @($currentSid, $systemSid)) {
        $rule = [Security.AccessControl.FileSystemAccessRule]::new(
            $sid,
            [Security.AccessControl.FileSystemRights]::FullControl,
            [Security.AccessControl.AccessControlType]::Allow
        )
        [void]$fileSecurity.AddAccessRule($rule)
    }
    $fileSecurity.SetAccessRuleProtection($true, $false)
    $fileSecurity.SetOwner($currentSid)
    Set-Acl -LiteralPath $Path -AclObject $fileSecurity
}

function Write-PrivateTextFile {
    param(
        [Parameter(Mandatory)][string]$Path,
        [AllowEmptyString()][Parameter(Mandatory)][string]$Content
    )

    $parent = Split-Path -Parent $Path
    if (-not (Test-Path -LiteralPath $parent -PathType Container)) {
        [void][IO.Directory]::CreateDirectory($parent)
    }
    [IO.File]::WriteAllText($Path, $Content, [Text.UTF8Encoding]::new($false))
    Set-PrivateAcl -Path $Path
}

function Write-PrivateJson {
    param(
        [Parameter(Mandatory)][string]$Path,
        [Parameter(Mandatory)]$Value
    )

    Write-PrivateTextFile -Path $Path -Content ($Value | ConvertTo-Json -Depth 8)
}

function New-RandomSecret {
    param(
        [Parameter(Mandatory)][string]$Prefix,
        [ValidateRange(8, 128)][int]$ByteCount = 24
    )

    $bytes = [byte[]]::new($ByteCount)
    $random = [Security.Cryptography.RandomNumberGenerator]::Create()
    try {
        $random.GetBytes($bytes)
    }
    finally {
        $random.Dispose()
    }
    return $Prefix + (([BitConverter]::ToString($bytes)) -replace '-', '').ToLowerInvariant()
}

function New-Credentials {
    $demoAccounts = @(
        [ordered]@{ username = 'lab_student'; password = (New-RandomSecret -Prefix 'D9!' -ByteCount 8) },
        [ordered]@{ username = 'lab_manager'; password = (New-RandomSecret -Prefix 'D9!' -ByteCount 8) },
        [ordered]@{ username = 'lab_safety_officer'; password = (New-RandomSecret -Prefix 'D9!' -ByteCount 8) },
        [ordered]@{ username = 'lab_repair_worker'; password = (New-RandomSecret -Prefix 'D9!' -ByteCount 8) },
        [ordered]@{ username = 'lab_system_admin'; password = (New-RandomSecret -Prefix 'D9!' -ByteCount 8) }
    )
    return [ordered]@{
        schemaVersion = 1
        generatedAtUtc = [DateTime]::UtcNow.ToString('o')
        mysql = [ordered]@{
            database = $DatabaseName
            appUsername = $AppDatabaseUser
            rootPassword = (New-RandomSecret -Prefix 'A9!' -ByteCount 28)
            appPassword = (New-RandomSecret -Prefix 'A9!' -ByteCount 28)
        }
        redisPassword = (New-RandomSecret -Prefix 'R9!' -ByteCount 28)
        tokenSecret = (New-RandomSecret -Prefix 'T9!' -ByteCount 48)
        rootAdmin = [ordered]@{
            username = 'admin'
            password = (New-RandomSecret -Prefix 'D9!' -ByteCount 8)
        }
        demoAccounts = $demoAccounts
    }
}

function Read-Credentials {
    if (-not (Test-Path -LiteralPath $CredentialsPath -PathType Leaf)) {
        $credentials = New-Credentials
        Write-PrivateJson -Path $CredentialsPath -Value $credentials
        return [pscustomobject]$credentials
    }

    [void](Assert-LeafFile -Path $CredentialsPath -Description 'Local credentials file')
    Set-PrivateAcl -Path $CredentialsPath
    try {
        $credentials = Get-Content -Raw -LiteralPath $CredentialsPath | ConvertFrom-Json
    }
    catch {
        throw 'The local credentials file is not valid JSON.'
    }
    if ([int]$credentials.schemaVersion -ne 1 -or
        [string]$credentials.mysql.database -ne $DatabaseName -or
        [string]$credentials.mysql.appUsername -ne $AppDatabaseUser -or
        [string]::IsNullOrWhiteSpace([string]$credentials.mysql.rootPassword) -or
        [string]::IsNullOrWhiteSpace([string]$credentials.mysql.appPassword) -or
        [string]::IsNullOrWhiteSpace([string]$credentials.redisPassword) -or
        [string]::IsNullOrWhiteSpace([string]$credentials.tokenSecret) -or
        [string]$credentials.rootAdmin.username -ne 'admin' -or
        ([string]$credentials.rootAdmin.password).Length -lt 5 -or
        ([string]$credentials.rootAdmin.password).Length -gt 20) {
        throw 'The local credentials file does not match the expected schema.'
    }
    $actualDemoUsers = @($credentials.demoAccounts | ForEach-Object { [string]$_.username })
    if ($actualDemoUsers.Count -ne $DemoUserNames.Count -or
        @($DemoUserNames | Where-Object { $_ -notin $actualDemoUsers }).Count -ne 0 -or
        @($credentials.demoAccounts | Where-Object {
                ([string]$_.password).Length -lt 5 -or ([string]$_.password).Length -gt 20
            }).Count -ne 0) {
        throw 'The local credentials file does not contain the five required demo accounts.'
    }
    return $credentials
}

function Get-CommandPath {
    param(
        [Parameter(Mandatory)][string]$Name,
        [Parameter(Mandatory)][string]$Description
    )

    $command = Get-Command $Name -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($null -eq $command -or [string]::IsNullOrWhiteSpace([string]$command.Source)) {
        throw "$Description is missing from PATH."
    }
    return Assert-LeafFile -Path $command.Source -Description $Description
}

function Get-Listeners {
    param([Parameter(Mandatory)][int]$Port)

    return @(Get-NetTCPConnection -State Listen -LocalPort $Port -ErrorAction SilentlyContinue |
        Sort-Object LocalAddress, OwningProcess)
}

function Assert-PortAvailable {
    param([Parameter(Mandatory)][int]$Port)

    $listeners = @(Get-Listeners -Port $Port)
    if ($listeners.Count -ne 0) {
        $owners = ($listeners | Select-Object -ExpandProperty OwningProcess -Unique) -join ', '
        throw "Required local port $Port is already occupied (PID: $owners). No process was changed."
    }
}

function Get-ProcessIdentity {
    param(
        [Parameter(Mandatory)][int]$ProcessId,
        [Parameter(Mandatory)][int]$Port
    )

    $process = Get-Process -Id $ProcessId -ErrorAction Stop
    try {
        return [pscustomobject][ordered]@{
            processId = [int]$process.Id
            startTimeUtc = $process.StartTime.ToUniversalTime().ToString('o')
            startTimeUtcTicks = [long]$process.StartTime.ToUniversalTime().Ticks
            executablePath = [IO.Path]::GetFullPath($process.MainModule.FileName)
            port = $Port
        }
    }
    finally {
        $process.Dispose()
    }
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

function Stop-ExactStartedProcess {
    param([Parameter(Mandatory)]$Identity)

    if (-not (Test-ProcessIdentity -Identity $Identity)) {
        return
    }
    Stop-Process -Id ([int]$Identity.processId) -Force -ErrorAction Stop
    $deadline = [DateTime]::UtcNow.AddSeconds(15)
    while ((Test-ProcessIdentity -Identity $Identity) -and [DateTime]::UtcNow -lt $deadline) {
        Start-Sleep -Milliseconds 200
    }
    if (Test-ProcessIdentity -Identity $Identity) {
        throw 'A process started by this invocation could not be stopped safely.'
    }
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

function Start-HiddenOwnedProcess {
    param(
        [Parameter(Mandatory)][string]$FilePath,
        [AllowEmptyCollection()][Parameter(Mandatory)][string[]]$Arguments,
        [Parameter(Mandatory)][string]$WorkingDirectory,
        [Parameter(Mandatory)][string]$StandardOutputPath,
        [Parameter(Mandatory)][string]$StandardErrorPath,
        [Parameter(Mandatory)][int]$Port,
        [hashtable]$Environment = @{}
    )

    $previous = @{}
    foreach ($key in $Environment.Keys) {
        $previous[$key] = [Environment]::GetEnvironmentVariable([string]$key, 'Process')
        [Environment]::SetEnvironmentVariable([string]$key, [string]$Environment[$key], 'Process')
    }
    try {
        $process = Start-Process -FilePath $FilePath `
            -ArgumentList (Join-NativeArguments -Arguments $Arguments) `
            -WorkingDirectory $WorkingDirectory `
            -WindowStyle Hidden `
            -RedirectStandardOutput $StandardOutputPath `
            -RedirectStandardError $StandardErrorPath `
            -PassThru
    }
    finally {
        foreach ($key in $Environment.Keys) {
            [Environment]::SetEnvironmentVariable([string]$key, $previous[$key], 'Process')
        }
    }
    Start-Sleep -Milliseconds 300
    if ($process.HasExited) {
        throw "A local runtime process exited during startup. See $StandardErrorPath"
    }
    $identity = Get-ProcessIdentity -ProcessId $process.Id -Port $Port
    [void]$StartedProcesses.Add($identity)
    return $identity
}

function Start-HiddenHandoffProcess {
    param(
        [Parameter(Mandatory)][string]$FilePath,
        [AllowEmptyCollection()][Parameter(Mandatory)][string[]]$Arguments,
        [Parameter(Mandatory)][string]$WorkingDirectory,
        [Parameter(Mandatory)][string]$StandardOutputPath,
        [Parameter(Mandatory)][string]$StandardErrorPath
    )

    $process = Start-Process -FilePath $FilePath `
        -ArgumentList (Join-NativeArguments -Arguments $Arguments) `
        -WorkingDirectory $WorkingDirectory `
        -WindowStyle Hidden `
        -RedirectStandardOutput $StandardOutputPath `
        -RedirectStandardError $StandardErrorPath `
        -PassThru
    $launchProcessId = [int]$process.Id
    $process.Dispose()
    return $launchProcessId
}

function Wait-OwnedListener {
    param(
        [Parameter(Mandatory)]$Identity,
        [ValidateRange(1, 180)][int]$TimeoutSeconds = 60
    )

    $deadline = [DateTime]::UtcNow.AddSeconds($TimeoutSeconds)
    do {
        if (-not (Test-ProcessIdentity -Identity $Identity)) {
            throw "The process expected on port $($Identity.port) exited before becoming ready."
        }
        # Use one listener snapshot: the process can bind between two reads.
        $listeners = @(Get-Listeners -Port ([int]$Identity.port))
        if ($listeners.Count -gt 0) {
            $unexpected = @($listeners | Where-Object {
                [int]$_.OwningProcess -ne [int]$Identity.processId -or
                [string]$_.LocalAddress -ne '127.0.0.1'
            })
            if ($unexpected.Count -gt 0) {
                throw "Port $($Identity.port) has an unexpected owner or non-loopback binding."
            }
            if (Test-ProcessIdentity -Identity $Identity) { return }
        }
        Start-Sleep -Milliseconds 250
    } while ([DateTime]::UtcNow -lt $deadline)
    throw "The process did not open loopback port $($Identity.port) in time."
}

function Wait-MySqlOwnedListener {
    param(
        [Parameter(Mandatory)][string]$PidFilePath,
        [Parameter(Mandatory)][string]$ConfigurationPath,
        [ValidateRange(1, 180)][int]$TimeoutSeconds = 90
    )

    $deadline = [DateTime]::UtcNow.AddSeconds($TimeoutSeconds)
    do {
        if (Test-Path -LiteralPath $PidFilePath -PathType Leaf) {
            $pidText = (Get-Content -Raw -LiteralPath $PidFilePath).Trim()
            if ($pidText -match '\A[1-9][0-9]*\z') {
                $serverPid = [int]$pidText
                $serverIdentity = $null
                try {
                    $serverIdentity = Get-ProcessIdentity -ProcessId $serverPid -Port $MySqlPort
                }
                catch {
                    # A stale pid file can remain between clean server starts.
                }
                if ($null -ne $serverIdentity) {
                    if (-not [string]::Equals(
                            [string]$serverIdentity.executablePath,
                            [IO.Path]::GetFullPath($MySqlServer),
                            [StringComparison]::OrdinalIgnoreCase)) {
                        throw 'The MySQL pid file identified an unexpected executable.'
                    }
                    $processRecord = Get-CimInstance Win32_Process -Filter "ProcessId = $serverPid"
                    if ($null -eq $processRecord -or
                        [string]::IsNullOrWhiteSpace([string]$processRecord.CommandLine) -or
                        ([string]$processRecord.CommandLine).IndexOf(
                            $ConfigurationPath,
                            [StringComparison]::OrdinalIgnoreCase) -lt 0) {
                        throw 'The MySQL pid file identified a process outside this project runtime.'
                    }
                    if (@($StartedProcesses | Where-Object {
                                [int]$_.processId -eq $serverPid
                            }).Count -eq 0) {
                        [void]$StartedProcesses.Add($serverIdentity)
                    }
                    $listeners = @(Get-Listeners -Port $MySqlPort)
                    if ($listeners.Count -gt 0) {
                        if (@($listeners | Where-Object {
                                    [int]$_.OwningProcess -ne $serverPid -or
                                    [string]$_.LocalAddress -ne '127.0.0.1'
                                }).Count -ne 0) {
                            throw "Port $MySqlPort was claimed by an unexpected process or address."
                        }
                        return $serverIdentity
                    }
                }
            }
        }
        Start-Sleep -Milliseconds 250
    } while ([DateTime]::UtcNow -lt $deadline)
    throw 'The isolated MySQL process did not open its verified loopback listener in time.'
}

function Wait-VerifiedHandoffListener {
    param(
        [Parameter(Mandatory)][int]$Port,
        [Parameter(Mandatory)][string]$ExpectedExecutablePath,
        [Parameter(Mandatory)][string]$CommandLineToken,
        [ValidateRange(1, 180)][int]$TimeoutSeconds = 60
    )

    $deadline = [DateTime]::UtcNow.AddSeconds($TimeoutSeconds)
    do {
        $listeners = @(Get-Listeners -Port $Port)
        if ($listeners.Count -gt 0) {
            $ownerIds = @($listeners | Select-Object -ExpandProperty OwningProcess -Unique)
            if ($ownerIds.Count -ne 1 -or
                @($listeners | Where-Object {
                        [string]$_.LocalAddress -ne '127.0.0.1'
                    }).Count -ne 0) {
                throw "Port $Port was claimed by an unexpected process or address."
            }
            $ownerId = [int]$ownerIds[0]
            $identity = Get-ProcessIdentity -ProcessId $ownerId -Port $Port
            $processRecord = Get-CimInstance Win32_Process -Filter "ProcessId = $ownerId"
            if (-not [string]::Equals(
                    [string]$identity.executablePath,
                    [IO.Path]::GetFullPath($ExpectedExecutablePath),
                    [StringComparison]::OrdinalIgnoreCase) -or
                $null -eq $processRecord -or
                [string]::IsNullOrWhiteSpace([string]$processRecord.CommandLine) -or
                ([string]$processRecord.CommandLine).IndexOf(
                    $CommandLineToken,
                    [StringComparison]::OrdinalIgnoreCase) -lt 0) {
                throw "Port $Port is not owned by this project's expected runtime process."
            }
            [void]$StartedProcesses.Add($identity)
            return $identity
        }
        Start-Sleep -Milliseconds 250
    } while ([DateTime]::UtcNow -lt $deadline)
    throw "The verified local runtime process did not open loopback port $Port in time."
}

function Wait-HttpReady {
    param(
        [Parameter(Mandatory)][string]$Url,
        [Parameter(Mandatory)]$Identity,
        [ValidateRange(1, 180)][int]$TimeoutSeconds = 90
    )

    $deadline = [DateTime]::UtcNow.AddSeconds($TimeoutSeconds)
    do {
        if (-not (Test-ProcessIdentity -Identity $Identity)) {
            throw "The web process for $Url exited during readiness checking."
        }
        try {
            $request = [Net.HttpWebRequest]::Create($Url)
            $request.Method = 'GET'
            $request.Timeout = 2000
            $request.ReadWriteTimeout = 2000
            $response = $request.GetResponse()
            try {
                $statusCode = [int]$response.StatusCode
                if ($statusCode -ge 200 -and $statusCode -lt 500) {
                    return
                }
            }
            finally {
                $response.Dispose()
            }
        }
        catch [Net.WebException] {
            if ($null -ne $_.Exception.Response) {
                $statusCode = [int]$_.Exception.Response.StatusCode
                $_.Exception.Response.Dispose()
                if ($statusCode -ge 200 -and $statusCode -lt 500) {
                    return
                }
            }
        }
        Start-Sleep -Milliseconds 500
    } while ([DateTime]::UtcNow -lt $deadline)
    throw "HTTP readiness check timed out: $Url"
}

function Invoke-ProcessWithInput {
    param(
        [Parameter(Mandatory)][string]$FilePath,
        [AllowEmptyCollection()][Parameter(Mandatory)][string[]]$Arguments,
        [AllowEmptyString()][Parameter(Mandatory)][string]$StandardInput,
        [ValidateRange(1, 300)][int]$TimeoutSeconds = 30
    )

    $startInfo = [Diagnostics.ProcessStartInfo]::new()
    $startInfo.FileName = $FilePath
    $startInfo.Arguments = Join-NativeArguments -Arguments $Arguments
    $startInfo.WorkingDirectory = $RuntimeRoot
    $startInfo.UseShellExecute = $false
    $startInfo.CreateNoWindow = $true
    $startInfo.RedirectStandardInput = $true
    $startInfo.RedirectStandardOutput = $true
    $startInfo.RedirectStandardError = $true
    $startInfo.EnvironmentVariables['MYSQL_TEST_LOGIN_FILE'] = $MySqlLoginFileOverride
    $process = [Diagnostics.Process]::new()
    $process.StartInfo = $startInfo
    try {
        if (-not $process.Start()) {
            throw 'A native client process could not be started.'
        }
        $process.StandardInput.Write($StandardInput)
        $process.StandardInput.Close()
        $stdoutTask = $process.StandardOutput.ReadToEndAsync()
        $stderrTask = $process.StandardError.ReadToEndAsync()
        if (-not $process.WaitForExit($TimeoutSeconds * 1000)) {
            $process.Kill()
            throw 'A native client process timed out.'
        }
        $stdout = $stdoutTask.GetAwaiter().GetResult()
        $stderr = $stderrTask.GetAwaiter().GetResult()
        return [pscustomobject]@{
            exitCode = $process.ExitCode
            stdout = $stdout
            stderr = $stderr
        }
    }
    finally {
        $process.Dispose()
    }
}

function Invoke-RedisCommand {
    param(
        [Parameter(Mandatory)][string[][]]$Commands,
        [ValidateRange(1, 30)][int]$TimeoutSeconds = 5,
        [switch]$AllowDisconnect
    )

    $client = [Net.Sockets.TcpClient]::new()
    try {
        $connectTask = $client.ConnectAsync('127.0.0.1', $RedisPort)
        if (-not $connectTask.Wait($TimeoutSeconds * 1000) -or -not $client.Connected) {
            throw 'Could not connect to the isolated Redis runtime.'
        }
        $client.ReceiveTimeout = $TimeoutSeconds * 1000
        $client.SendTimeout = $TimeoutSeconds * 1000
        $stream = $client.GetStream()
        try {
            foreach ($command in $Commands) {
                $builder = [Text.StringBuilder]::new()
                [void]$builder.Append('*').Append($command.Count).Append("`r`n")
                foreach ($argument in $command) {
                    $argumentBytes = [Text.Encoding]::UTF8.GetBytes([string]$argument)
                    [void]$builder.Append('$').Append($argumentBytes.Length).Append("`r`n")
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
                            if ($AllowDisconnect) {
                                break
                            }
                            throw 'Redis closed the connection before acknowledging a command.'
                        }
                        if ($value -eq 13) {
                            $lineFeed = $stream.ReadByte()
                            if ($lineFeed -ne 10) {
                                throw 'Redis returned a malformed response.'
                            }
                            break
                        }
                        [void]$response.Append([char]$value)
                    }
                }
                catch [IO.IOException] {
                    if (-not $AllowDisconnect) {
                        throw
                    }
                }
                if ($response.Length -gt 0 -and $response.ToString().StartsWith('-',
                        [StringComparison]::Ordinal)) {
                    throw 'Redis rejected a local runtime command.'
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

function Invoke-MavenBuild {
    $logPath = Join-Path $RuntimeRoot 'maven-build.log'
    $oldJavaHome = [Environment]::GetEnvironmentVariable('JAVA_HOME', 'Process')
    [Environment]::SetEnvironmentVariable('JAVA_HOME', $JavaHome, 'Process')
    try {
        Push-Location $RepositoryRoot
        try {
            & $MavenCommand -pl ruoyi-admin -am -DskipTests package *> $logPath
            if ($LASTEXITCODE -ne 0) {
                throw "Backend build failed. See $logPath"
            }
        }
        finally {
            Pop-Location
        }
    }
    finally {
        [Environment]::SetEnvironmentVariable('JAVA_HOME', $oldJavaHome, 'Process')
    }
}

function Invoke-YarnInstallIfNeeded {
    $nodeModules = Join-Path $RepositoryRoot 'ruoyi-ui\node_modules'
    if (Test-Path -LiteralPath $nodeModules -PathType Container) {
        return
    }
    $logPath = Join-Path $RuntimeRoot 'yarn-install.log'
    Push-Location (Join-Path $RepositoryRoot 'ruoyi-ui')
    try {
        & $YarnCommand install --frozen-lockfile *> $logPath
        if ($LASTEXITCODE -ne 0) {
            throw "Frontend dependency installation failed. See $logPath"
        }
    }
    finally {
        Pop-Location
    }
}

function Write-MySqlClientDefaults {
    param([Parameter(Mandatory)]$Credentials)

    Write-PrivateTextFile -Path $MySqlRootDefaultsPath -Content @"
[client]
protocol=TCP
host=127.0.0.1
port=$MySqlPort
user=root
password=$([string]$Credentials.mysql.rootPassword)
"@
    Write-PrivateTextFile -Path $MySqlAppDefaultsPath -Content @"
[client]
protocol=TCP
host=127.0.0.1
port=$MySqlPort
user=$AppDatabaseUser
password=$([string]$Credentials.mysql.appPassword)
database=$DatabaseName
"@
}

function Invoke-MySqlProbe {
    param([Parameter(Mandatory)][string]$DefaultsPath)

    return Invoke-ProcessWithInput -FilePath $MySqlClient `
        -Arguments @("--defaults-file=$DefaultsPath", '--protocol=TCP', '--host=127.0.0.1',
            "--port=$MySqlPort", '--batch', '--skip-column-names') `
        -StandardInput "SELECT 1;`n"
}

function Initialize-MySqlAccounts {
    param([Parameter(Mandatory)]$Credentials)

    $bootstrapMarker = Join-Path $RuntimeRoot 'mysql-bootstrap.complete'
    if (Test-Path -LiteralPath $bootstrapMarker -PathType Leaf) {
        $rootProbe = Invoke-MySqlProbe -DefaultsPath $MySqlRootDefaultsPath
        $appProbe = Invoke-MySqlProbe -DefaultsPath $MySqlAppDefaultsPath
        if ($rootProbe.exitCode -ne 0 -or $appProbe.exitCode -ne 0) {
            throw 'The persisted MySQL credentials no longer match the isolated database.'
        }
        return
    }

    $rootPassword = [string]$Credentials.mysql.rootPassword
    $appPassword = [string]$Credentials.mysql.appPassword
    $bootstrapSql = @"
CREATE DATABASE IF NOT EXISTS ``$DatabaseName`` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE USER IF NOT EXISTS '$AppDatabaseUser'@'127.0.0.1' IDENTIFIED BY '$appPassword';
ALTER USER '$AppDatabaseUser'@'127.0.0.1' IDENTIFIED BY '$appPassword';
GRANT ALL PRIVILEGES ON ``$DatabaseName``.* TO '$AppDatabaseUser'@'127.0.0.1';
ALTER USER 'root'@'localhost' IDENTIFIED BY '$rootPassword';
FLUSH PRIVILEGES;
"@

    $authenticated = Invoke-ProcessWithInput -FilePath $MySqlClient `
        -Arguments @("--defaults-file=$MySqlRootDefaultsPath", '--protocol=TCP', '--host=127.0.0.1',
            "--port=$MySqlPort", '--user=root', '--batch') `
        -StandardInput $bootstrapSql
    if ($authenticated.exitCode -ne 0) {
        $insecure = Invoke-ProcessWithInput -FilePath $MySqlClient `
            -Arguments @('--no-defaults', '--protocol=TCP', '--host=127.0.0.1',
                "--port=$MySqlPort", '--user=root', '--connect-timeout=5', '--batch') `
            -StandardInput $bootstrapSql
        if ($insecure.exitCode -ne 0) {
            throw 'MySQL account bootstrap failed; no credentials were printed or changed outside the isolated runtime.'
        }
    }
    $bootstrapSql = $null
    $probe = Invoke-MySqlProbe -DefaultsPath $MySqlAppDefaultsPath
    if ($probe.exitCode -ne 0 -or $probe.stdout.Trim() -ne '1') {
        throw 'The isolated MySQL application account could not be verified.'
    }
    [IO.File]::WriteAllText($bootstrapMarker, [DateTime]::UtcNow.ToString('o'),
        [Text.UTF8Encoding]::new($false))
}

function Get-DemoPassword {
    param(
        [Parameter(Mandatory)]$Credentials,
        [Parameter(Mandatory)][string]$Username
    )

    $account = @($Credentials.demoAccounts | Where-Object { [string]$_.username -eq $Username })
    if ($account.Count -ne 1) {
        throw "Demo account credentials are missing for $Username."
    }
    return [string]$account[0].password
}

function Get-ExistingState {
    if (-not (Test-Path -LiteralPath $StatePath -PathType Leaf)) {
        return $null
    }
    try {
        return Get-Content -Raw -LiteralPath $StatePath | ConvertFrom-Json
    }
    catch {
        throw 'The local runtime state file is not valid JSON.'
    }
}

function Get-StateIdentities {
    param([Parameter(Mandatory)]$State)

    return @(
        $State.processes.mysql,
        $State.processes.redis,
        $State.processes.backend,
        $State.processes.frontend
    ) | Where-Object { $null -ne $_ }
}

function Write-LaunchSummary {
    Write-Output '实验室安全与设备管理系统已在本机启动。'
    Write-Output "前端地址: http://127.0.0.1:$FrontendPort"
    Write-Output "后端地址: http://127.0.0.1:$BackendPort"
    Write-Output ('演示用户名: ' + ($DemoUserNames -join ', '))
    Write-Output "密码与本机凭据: $CredentialsPath"
}

try {
    Assert-RuntimeLocation
    foreach ($path in @($MySqlServer, $MySqlClient, $MemuraiServer,
            (Join-Path $JavaHome 'bin\java.exe'), $MavenCommand)) {
        [void](Assert-LeafFile -Path $path -Description 'Required native executable')
    }
    $MySqlServer = Assert-LeafFile -Path $MySqlServer -Description 'MySQL server'
    $MySqlClient = Assert-LeafFile -Path $MySqlClient -Description 'MySQL client'
    $MemuraiServer = Assert-LeafFile -Path $MemuraiServer -Description 'Memurai server'
    $JavaCommand = Assert-LeafFile -Path (Join-Path $JavaHome 'bin\java.exe') -Description 'Java 17 runtime'
    $MavenCommand = Assert-LeafFile -Path $MavenCommand -Description 'Maven command'
    $NodeCommand = Get-CommandPath -Name 'node.exe' -Description 'Node.js runtime'
    $YarnCommand = Get-CommandPath -Name 'yarn.cmd' -Description 'Yarn 1 command'

    [void][IO.Directory]::CreateDirectory($RuntimeRoot)
    Set-PrivateAcl -Path $RuntimeRoot -Directory
    foreach ($directory in @('mysql\data', 'mysql\tmp', 'redis\data', 'logs', 'files')) {
        [void][IO.Directory]::CreateDirectory((Join-Path $RuntimeRoot $directory))
    }
    if (Test-Path -LiteralPath $MySqlLoginFileOverride) {
        throw 'The reserved MySQL login-file override path already exists; no process was started.'
    }

    if (-not (Test-Path -LiteralPath $CredentialsPath -PathType Leaf) -and
        (Test-Path -LiteralPath (Join-Path $RuntimeRoot 'mysql\data\mysql') -PathType Container)) {
        throw 'Persisted MySQL data exists without its credentials file; no data or process was changed.'
    }
    $credentials = Read-Credentials
    Write-MySqlClientDefaults -Credentials $credentials

    $existingState = Get-ExistingState
    if ($null -ne $existingState -and $null -ne $existingState.processes) {
        $existingIdentities = @(Get-StateIdentities -State $existingState)
        $runningIdentities = @($existingIdentities | Where-Object { Test-ProcessIdentity -Identity $_ })
        if ($runningIdentities.Count -eq 4 -and
            @($runningIdentities | Where-Object { -not (Test-OwnedListener -Identity $_) }).Count -eq 0) {
            Wait-HttpReady -Url "http://127.0.0.1:$BackendPort/captchaImage" `
                -Identity $existingState.processes.backend -TimeoutSeconds 20
            Wait-HttpReady -Url "http://127.0.0.1:$FrontendPort/" `
                -Identity $existingState.processes.frontend -TimeoutSeconds 20
            Write-LaunchSummary
            return
        }
        if ($runningIdentities.Count -gt 0) {
            throw 'A partial local runtime is still active. Run scripts\stop-local.ps1 before restarting.'
        }
    }

    foreach ($port in @($MySqlPort, $RedisPort, $BackendPort, $FrontendPort)) {
        Assert-PortAvailable -Port $port
    }

    if (-not $SkipBuild) {
        Write-Output '正在构建后端（跳过测试）...'
        Invoke-MavenBuild
    }
    Invoke-YarnInstallIfNeeded

    $jarPath = Assert-LeafFile -Path (Join-Path $RepositoryRoot 'ruoyi-admin\target\ruoyi-admin.jar') `
        -Description 'Backend executable jar'
    $viteEntry = Assert-LeafFile -Path (Join-Path $RepositoryRoot 'ruoyi-ui\node_modules\vite\bin\vite.js') `
        -Description 'Vite entry script'

    $mysqlDataPath = Join-Path $RuntimeRoot 'mysql\data'
    $mysqlSystemSchema = Join-Path $mysqlDataPath 'mysql'
    $mysqlConfigurationPath = Join-Path $RuntimeRoot 'mysql\mysql.ini'
    $mysqlConfiguration = @"
[mysqld]
basedir=$($MySqlHome.Replace('\', '/'))
datadir=$($mysqlDataPath.Replace('\', '/'))
tmpdir=$((Join-Path $RuntimeRoot 'mysql\tmp').Replace('\', '/'))
port=$MySqlPort
bind-address=127.0.0.1
mysqlx=0
skip-log-bin
local-infile=0
character-set-server=utf8mb4
collation-server=utf8mb4_0900_ai_ci
pid-file=$((Join-Path $RuntimeRoot 'mysql\mysql.pid').Replace('\', '/'))
log-error=$((Join-Path $RuntimeRoot 'logs\mysql.log').Replace('\', '/'))
performance-schema=OFF
"@
    [IO.File]::WriteAllText($mysqlConfigurationPath, $mysqlConfiguration,
        [Text.UTF8Encoding]::new($false))

    if (-not (Test-Path -LiteralPath $mysqlSystemSchema -PathType Container)) {
        $existingData = @(Get-ChildItem -LiteralPath $mysqlDataPath -Force -ErrorAction Stop)
        if ($existingData.Count -ne 0) {
            throw 'The isolated MySQL data directory is incomplete; it was not modified.'
        }
        $initializeStdout = Join-Path $RuntimeRoot 'logs\mysql-initialize.out.log'
        $initializeStderr = Join-Path $RuntimeRoot 'logs\mysql-initialize.err.log'
        $initialize = Start-Process -FilePath $MySqlServer `
            -ArgumentList (Join-NativeArguments -Arguments @(
                    "--defaults-file=$mysqlConfigurationPath", '--initialize-insecure', '--console')) `
            -WorkingDirectory $RuntimeRoot `
            -WindowStyle Hidden `
            -RedirectStandardOutput $initializeStdout `
            -RedirectStandardError $initializeStderr `
            -Wait `
            -PassThru
        if ($initialize.ExitCode -ne 0) {
            throw "MySQL initialization failed. See $initializeStderr"
        }
    }

    [void](Start-HiddenHandoffProcess -FilePath $MySqlServer `
        -Arguments @("--defaults-file=$mysqlConfigurationPath", '--console') `
        -WorkingDirectory $RuntimeRoot `
        -StandardOutputPath (Join-Path $RuntimeRoot 'logs\mysql-console.out.log') `
        -StandardErrorPath (Join-Path $RuntimeRoot 'logs\mysql-console.err.log'))
    $mysqlIdentity = Wait-MySqlOwnedListener `
        -PidFilePath (Join-Path $RuntimeRoot 'mysql\mysql.pid') `
        -ConfigurationPath $mysqlConfigurationPath `
        -TimeoutSeconds 90
    Initialize-MySqlAccounts -Credentials $credentials

    $redisConfigurationPath = Join-Path $RuntimeRoot 'redis\memurai.conf'
    $redisConfiguration = @"
bind 127.0.0.1
protected-mode yes
port $RedisPort
save 60 1
appendonly no
dir "$((Join-Path $RuntimeRoot 'redis\data').Replace('\', '/'))"
logfile "$((Join-Path $RuntimeRoot 'logs\memurai.log').Replace('\', '/'))"
daemonize no
"@
    [IO.File]::WriteAllText($redisConfigurationPath, $redisConfiguration,
        [Text.UTF8Encoding]::new($false))
    [void](Start-HiddenHandoffProcess -FilePath $MemuraiServer `
        -Arguments @($redisConfigurationPath) `
        -WorkingDirectory $RuntimeRoot `
        -StandardOutputPath (Join-Path $RuntimeRoot 'logs\memurai-console.out.log') `
        -StandardErrorPath (Join-Path $RuntimeRoot 'logs\memurai-console.err.log'))
    $redisIdentity = Wait-VerifiedHandoffListener -Port $RedisPort `
        -ExpectedExecutablePath $MemuraiServer `
        -CommandLineToken $redisConfigurationPath `
        -TimeoutSeconds 60
    Invoke-RedisCommand -Commands @(,@('CONFIG', 'SET', 'requirepass', [string]$credentials.redisPassword))
    Invoke-RedisCommand -Commands @(
        @('AUTH', [string]$credentials.redisPassword),
        @('PING')
    )

    $jdbcUrl = "jdbc:mysql://127.0.0.1:$MySqlPort/$DatabaseName" +
        '?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia%2FShanghai&allowPublicKeyRetrieval=true&useSSL=false'
    $backendEnvironment = @{
        SERVER_ADDRESS = '127.0.0.1'
        SERVER_PORT = [string]$BackendPort
        LAB_DB_URL = $jdbcUrl
        LAB_DB_USERNAME = $AppDatabaseUser
        LAB_DB_PASSWORD = [string]$credentials.mysql.appPassword
        LAB_DB_SLAVE_ENABLED = 'false'
        LAB_REDIS_HOST = '127.0.0.1'
        LAB_REDIS_PORT = [string]$RedisPort
        LAB_REDIS_DATABASE = '0'
        LAB_REDIS_PASSWORD = [string]$credentials.redisPassword
        LAB_TOKEN_SECRET = [string]$credentials.tokenSecret
        LAB_DEMO_DATA_ENABLED = 'true'
        LAB_ROOT_ADMIN_PASSWORD = [string]$credentials.rootAdmin.password
        LAB_DEMO_STUDENT_PASSWORD = Get-DemoPassword -Credentials $credentials -Username 'lab_student'
        LAB_DEMO_MANAGER_PASSWORD = Get-DemoPassword -Credentials $credentials -Username 'lab_manager'
        LAB_DEMO_SAFETY_PASSWORD = Get-DemoPassword -Credentials $credentials -Username 'lab_safety_officer'
        LAB_DEMO_REPAIR_PASSWORD = Get-DemoPassword -Credentials $credentials -Username 'lab_repair_worker'
        LAB_DEMO_ADMIN_PASSWORD = Get-DemoPassword -Credentials $credentials -Username 'lab_system_admin'
        LAB_FILE_ROOT = (Join-Path $RuntimeRoot 'files\attachments')
        LAB_PROFILE_ROOT = (Join-Path $RuntimeRoot 'files\profile')
        LAB_LOG_ROOT = (Join-Path $RuntimeRoot 'logs')
        JAVA_HOME = $JavaHome
    }
    $backendIdentity = Start-HiddenOwnedProcess -FilePath $JavaCommand `
        -Arguments @('-jar', $jarPath) `
        -WorkingDirectory $RepositoryRoot `
        -StandardOutputPath (Join-Path $RuntimeRoot 'logs\backend.out.log') `
        -StandardErrorPath (Join-Path $RuntimeRoot 'logs\backend.err.log') `
        -Port $BackendPort `
        -Environment $backendEnvironment
    Wait-OwnedListener -Identity $backendIdentity -TimeoutSeconds 120
    Wait-HttpReady -Url "http://127.0.0.1:$BackendPort/captchaImage" `
        -Identity $backendIdentity -TimeoutSeconds 120

    $frontendEnvironment = @{
        VITE_APP_PROXY_TARGET = "http://127.0.0.1:$BackendPort"
        BROWSER = 'none'
    }
    $frontendIdentity = Start-HiddenOwnedProcess -FilePath $NodeCommand `
        -Arguments @($viteEntry, '--host', '127.0.0.1', '--port', [string]$FrontendPort,
            '--strictPort') `
        -WorkingDirectory (Join-Path $RepositoryRoot 'ruoyi-ui') `
        -StandardOutputPath (Join-Path $RuntimeRoot 'logs\frontend.out.log') `
        -StandardErrorPath (Join-Path $RuntimeRoot 'logs\frontend.err.log') `
        -Port $FrontendPort `
        -Environment $frontendEnvironment
    Wait-OwnedListener -Identity $frontendIdentity -TimeoutSeconds 60
    Wait-HttpReady -Url "http://127.0.0.1:$FrontendPort/" `
        -Identity $frontendIdentity -TimeoutSeconds 60

    $state = [ordered]@{
        schemaVersion = 1
        status = 'running'
        repositoryRoot = $RepositoryRoot
        startedAtUtc = [DateTime]::UtcNow.ToString('o')
        processes = [ordered]@{
            mysql = $mysqlIdentity
            redis = $redisIdentity
            backend = $backendIdentity
            frontend = $frontendIdentity
        }
    }
    Write-PrivateJson -Path $StatePath -Value $state
    Write-LaunchSummary
}
catch {
    $cleanupErrors = [System.Collections.Generic.List[string]]::new()
    foreach ($identity in @($StartedProcesses | Sort-Object { [int]$_.processId } -Descending)) {
        try {
            Stop-ExactStartedProcess -Identity $identity
        }
        catch {
            [void]$cleanupErrors.Add($_.Exception.Message)
        }
    }
    if ($cleanupErrors.Count -gt 0) {
        Write-Warning ('Some newly started processes required manual review: ' + ($cleanupErrors -join ' | '))
    }
    throw
}
