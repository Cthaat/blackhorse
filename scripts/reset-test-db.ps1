param(
    [Parameter(Mandatory = $true)]
    [ValidatePattern('^lab_test_[A-Za-z0-9_]+$')]
    [string]$DatabaseName
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$safeDatabasePattern = '^lab_test_[A-Za-z0-9_]+$'
if ([string]::IsNullOrWhiteSpace($DatabaseName) -or $DatabaseName -notmatch $safeDatabasePattern)
{
    throw "Refusing to reset an unsafe database name: $DatabaseName"
}

$adminHost = $env:LAB_TEST_ADMIN_HOST
$adminPortText = $env:LAB_TEST_ADMIN_PORT
$adminUser = $env:LAB_TEST_ADMIN_USERNAME
$adminPassword = $env:LAB_TEST_ADMIN_PASSWORD

if ($adminHost -notin @('localhost', '127.0.0.1'))
{
    throw "LAB_TEST_ADMIN_HOST must be localhost or 127.0.0.1; actual host: $adminHost"
}

$adminPort = 0
if (-not [int]::TryParse($adminPortText, [ref]$adminPort) -or
    $adminPort -lt 1 -or $adminPort -gt 65535)
{
    throw 'LAB_TEST_ADMIN_PORT must be an integer from 1 through 65535.'
}
if ([string]::IsNullOrWhiteSpace($adminUser) -or
    [string]::IsNullOrWhiteSpace($adminPassword))
{
    throw 'LAB_TEST_ADMIN_USERNAME and LAB_TEST_ADMIN_PASSWORD are required.'
}

$mysqlCommand = Get-Command mysql -CommandType Application -ErrorAction Stop |
    Where-Object { [System.IO.Path]::GetExtension($_.Source) -ieq '.exe' } |
    Select-Object -First 1
if ($null -eq $mysqlCommand)
{
    throw 'mysql must resolve to a native mysql.exe executable.'
}
$mysqlPath = [System.IO.Path]::GetFullPath($mysqlCommand.Source)
$pathRoot = [System.IO.Path]::GetPathRoot($mysqlPath)
$currentPath = $pathRoot
$separators = [char[]]@(
    [System.IO.Path]::DirectorySeparatorChar,
    [System.IO.Path]::AltDirectorySeparatorChar)
foreach ($segment in $mysqlPath.Substring($pathRoot.Length).Split(
        $separators,
        [System.StringSplitOptions]::RemoveEmptyEntries))
{
    $currentPath = Join-Path $currentPath $segment
    $item = Get-Item -LiteralPath $currentPath -Force
    if (($item.Attributes -band [System.IO.FileAttributes]::ReparsePoint) -ne 0)
    {
        throw "mysql.exe must not be reached through a reparse point: $currentPath"
    }
}
if (-not (Test-Path -LiteralPath $mysqlPath -PathType Leaf))
{
    throw "mysql.exe is not a regular file: $mysqlPath"
}
$loginFileRoot = [System.IO.Path]::GetFullPath($PSScriptRoot)
$loginFileRootPathRoot = [System.IO.Path]::GetPathRoot($loginFileRoot)
$currentPath = $loginFileRootPathRoot
foreach ($segment in $loginFileRoot.Substring($loginFileRootPathRoot.Length).Split(
        $separators,
        [System.StringSplitOptions]::RemoveEmptyEntries))
{
    $currentPath = Join-Path $currentPath $segment
    $item = Get-Item -LiteralPath $currentPath -Force
    if (($item.Attributes -band [System.IO.FileAttributes]::ReparsePoint) -ne 0)
    {
        throw 'The trusted mysql login-file directory must not contain a reparse point.'
    }
}
if (-not (Test-Path -LiteralPath $loginFileRoot -PathType Container))
{
    throw 'The trusted mysql login-file directory is not a physical directory.'
}
$loginFilePath = Join-Path `
    $loginFileRoot `
    ('.lab-test-mysql-login-{0}.cnf' -f [System.Guid]::NewGuid().ToString('N'))
if (Test-Path -LiteralPath $loginFilePath)
{
    throw 'The isolated mysql login-file path unexpectedly exists.'
}
Write-Host "RESET DATABASE: $DatabaseName ON ${adminHost}:$adminPort"

$hostExecutableName = if ($PSVersionTable.PSEdition -eq 'Core') { 'pwsh.exe' } else { 'powershell.exe' }
$hostExecutablePath = [System.IO.Path]::GetFullPath((Join-Path $PSHOME $hostExecutableName))
$currentPath = [System.IO.Path]::GetPathRoot($hostExecutablePath)
foreach ($segment in $hostExecutablePath.Substring($currentPath.Length).Split(
        $separators,
        [System.StringSplitOptions]::RemoveEmptyEntries))
{
    $currentPath = Join-Path $currentPath $segment
    $item = Get-Item -LiteralPath $currentPath -Force
    if (($item.Attributes -band [System.IO.FileAttributes]::ReparsePoint) -ne 0)
    {
        throw 'The trusted PowerShell host must not be reached through a reparse point.'
    }
}
if (-not (Test-Path -LiteralPath $hostExecutablePath -PathType Leaf))
{
    throw 'The trusted PowerShell host is not a regular file.'
}

$childScript = @'
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
if (Test-Path Variable:PSNativeCommandUseErrorActionPreference)
{
    $PSNativeCommandUseErrorActionPreference = $false
}

$mysqlPath = $env:LAB_RESET_MYSQL_PATH
$adminHost = $env:LAB_RESET_ADMIN_HOST
$adminPortText = $env:LAB_RESET_ADMIN_PORT
$adminUser = $env:LAB_RESET_ADMIN_USER
$adminPassword = $env:LAB_RESET_ADMIN_PASSWORD
$loginFilePath = $env:LAB_RESET_LOGIN_FILE
$trustedRoot = $env:LAB_RESET_TRUSTED_ROOT
$databaseName = $env:LAB_RESET_DATABASE_NAME
foreach ($name in @([System.Environment]::GetEnvironmentVariables().Keys))
{
    $actualName = [string]$name
    $canonicalName = $actualName.ToUpperInvariant() -replace '[.\-]', '_'
    if ($canonicalName.StartsWith('MYSQL_', [System.StringComparison]::Ordinal) -or
        $canonicalName.StartsWith('LAB_RESET_', [System.StringComparison]::Ordinal))
    {
        Remove-Item -LiteralPath "Env:$actualName" -ErrorAction SilentlyContinue
    }
}
if ($adminHost -notin @('localhost', '127.0.0.1') -or
    [string]::IsNullOrWhiteSpace($adminUser) -or
    [string]::IsNullOrWhiteSpace($adminPassword) -or
    [string]::IsNullOrWhiteSpace($mysqlPath) -or
    [string]::IsNullOrWhiteSpace($loginFilePath) -or
    [string]::IsNullOrWhiteSpace($trustedRoot) -or
    $databaseName -notmatch '^lab_test_[A-Za-z0-9_]+$')
{
    throw 'The isolated mysql reset worker received invalid input.'
}
$adminPort = 0
if (-not [int]::TryParse($adminPortText, [ref]$adminPort) -or
    $adminPort -lt 1 -or $adminPort -gt 65535)
{
    throw 'The isolated mysql reset worker received an invalid port.'
}
$mysqlPath = [System.IO.Path]::GetFullPath($mysqlPath)
$pathRoot = [System.IO.Path]::GetPathRoot($mysqlPath)
$currentPath = $pathRoot
$separators = [char[]]@(
    [System.IO.Path]::DirectorySeparatorChar,
    [System.IO.Path]::AltDirectorySeparatorChar)
foreach ($segment in $mysqlPath.Substring($pathRoot.Length).Split(
        $separators,
        [System.StringSplitOptions]::RemoveEmptyEntries))
{
    $currentPath = Join-Path $currentPath $segment
    $item = Get-Item -LiteralPath $currentPath -Force
    if (($item.Attributes -band [System.IO.FileAttributes]::ReparsePoint) -ne 0)
    {
        throw 'The isolated mysql reset executable path contains a reparse point.'
    }
}
if (-not (Test-Path -LiteralPath $mysqlPath -PathType Leaf) -or
    [System.IO.Path]::GetExtension($mysqlPath) -ine '.exe')
{
    throw 'The isolated mysql reset executable is invalid.'
}
$loginFilePath = [System.IO.Path]::GetFullPath($loginFilePath)
$trustedRoot = [System.IO.Path]::GetFullPath($trustedRoot)
if (-not [string]::Equals(
        [System.IO.Path]::GetDirectoryName($loginFilePath),
        $trustedRoot,
        [System.StringComparison]::OrdinalIgnoreCase))
{
    throw 'The isolated mysql login-file path is outside its trusted root.'
}
$trustedPathRoot = [System.IO.Path]::GetPathRoot($trustedRoot)
$currentPath = $trustedPathRoot
foreach ($segment in $trustedRoot.Substring($trustedPathRoot.Length).Split(
        $separators,
        [System.StringSplitOptions]::RemoveEmptyEntries))
{
    $currentPath = Join-Path $currentPath $segment
    $item = Get-Item -LiteralPath $currentPath -Force
    if (($item.Attributes -band [System.IO.FileAttributes]::ReparsePoint) -ne 0)
    {
        throw 'The isolated mysql login-file root contains a reparse point.'
    }
}
if (-not (Test-Path -LiteralPath $trustedRoot -PathType Container))
{
    throw 'The isolated mysql login-file root is invalid.'
}
if (Test-Path -LiteralPath $loginFilePath)
{
    throw 'The isolated mysql login-file path unexpectedly exists.'
}
$env:MYSQL_PWD = $adminPassword
$env:MYSQL_TEST_LOGIN_FILE = $loginFilePath
$sql = 'DROP DATABASE IF EXISTS `{0}`; CREATE DATABASE `{0}` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;' -f $databaseName
& $mysqlPath `
    '--no-defaults' `
    "--host=$adminHost" `
    "--port=$adminPort" `
    "--user=$adminUser" `
    '--protocol=TCP' `
    "--execute=$sql"
exit $LASTEXITCODE
'@
$encodedCommand = [System.Convert]::ToBase64String(
    [System.Text.Encoding]::Unicode.GetBytes($childScript))
$startInfo = New-Object System.Diagnostics.ProcessStartInfo
$startInfo.FileName = $hostExecutablePath
$startInfo.Arguments = '-NoLogo -NoProfile -NonInteractive -EncodedCommand ' + $encodedCommand
$startInfo.WorkingDirectory = $loginFileRoot
$startInfo.UseShellExecute = $false
$startInfo.CreateNoWindow = $true
$startInfo.WindowStyle = [System.Diagnostics.ProcessWindowStyle]::Hidden
$startInfo.RedirectStandardOutput = $true
$startInfo.RedirectStandardError = $true
foreach ($name in @($startInfo.EnvironmentVariables.Keys))
{
    $actualName = [string]$name
    $canonicalName = $actualName.ToUpperInvariant() -replace '[.\-]', '_'
    $isIsolated = $false
    foreach ($prefix in @(
            'MYSQL_',
            'LAB_',
            'SPRING_',
            'RUOYI_',
            'TOKEN_',
            'SERVER_',
            'LOGGING_',
            'MAVEN_'))
    {
        if ($canonicalName.StartsWith($prefix, [System.StringComparison]::Ordinal))
        {
            $isIsolated = $true
            break
        }
    }
    if ($isIsolated -or $canonicalName -in @(
            'JAVA_TOOL_OPTIONS',
            '_JAVA_OPTIONS',
            'JDK_JAVA_OPTIONS',
            'JVM_CONFIG_MAVEN_PROPS'))
    {
        $startInfo.EnvironmentVariables.Remove($actualName)
    }
}
$startInfo.EnvironmentVariables['LAB_RESET_MYSQL_PATH'] = $mysqlPath
$startInfo.EnvironmentVariables['LAB_RESET_ADMIN_HOST'] = $adminHost
$startInfo.EnvironmentVariables['LAB_RESET_ADMIN_PORT'] = [string]$adminPort
$startInfo.EnvironmentVariables['LAB_RESET_ADMIN_USER'] = $adminUser
$startInfo.EnvironmentVariables['LAB_RESET_ADMIN_PASSWORD'] = $adminPassword
$startInfo.EnvironmentVariables['LAB_RESET_LOGIN_FILE'] = $loginFilePath
$startInfo.EnvironmentVariables['LAB_RESET_TRUSTED_ROOT'] = $loginFileRoot
$startInfo.EnvironmentVariables['LAB_RESET_DATABASE_NAME'] = $DatabaseName
$process = New-Object System.Diagnostics.Process
$process.StartInfo = $startInfo
try
{
    if (-not $process.Start())
    {
        throw 'The isolated mysql reset worker could not be started.'
    }
    $stdoutTask = $process.StandardOutput.ReadToEndAsync()
    $stderrTask = $process.StandardError.ReadToEndAsync()
    $process.WaitForExit()
    $stdout = $stdoutTask.GetAwaiter().GetResult()
    $stderr = $stderrTask.GetAwaiter().GetResult()
    if (-not [string]::IsNullOrEmpty($stdout))
    {
        [System.Console]::Out.Write($stdout)
    }
    if (-not [string]::IsNullOrEmpty($stderr))
    {
        [System.Console]::Error.Write($stderr)
    }
    $mysqlExitCode = $process.ExitCode
    if ($mysqlExitCode -ne 0)
    {
        throw "mysql reset failed with exit code $mysqlExitCode"
    }
}
finally
{
    $process.Dispose()
}
