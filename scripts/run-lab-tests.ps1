[CmdletBinding(DefaultParameterSetName = 'Tests')]
param(
    [Parameter(Mandatory = $true)]
    [ValidatePattern('^lab_test_[A-Za-z0-9_]+$')]
    [string]$DatabaseName,

    [Parameter(Mandatory = $true, ParameterSetName = 'Tests')]
    [ValidatePattern('^[A-Za-z0-9_.*,#-]+$')]
    [string]$Tests,

    [Parameter(Mandatory = $true, ParameterSetName = 'CleanVerify')]
    [switch]$CleanVerify
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function Get-PhysicalPath
{
    param(
        [Parameter(Mandatory = $true)][string]$Path,
        [Parameter(Mandatory = $true)][string]$Description,
        [switch]$Directory
    )
    $fullPath = [System.IO.Path]::GetFullPath($Path)
    $pathRoot = [System.IO.Path]::GetPathRoot($fullPath)
    if ([string]::IsNullOrWhiteSpace($pathRoot))
    {
        throw "Cannot resolve $Description."
    }
    $currentPath = $pathRoot
    $separators = [char[]]@(
        [System.IO.Path]::DirectorySeparatorChar,
        [System.IO.Path]::AltDirectorySeparatorChar)
    foreach ($segment in $fullPath.Substring($pathRoot.Length).Split(
            $separators,
            [System.StringSplitOptions]::RemoveEmptyEntries))
    {
        $currentPath = Join-Path $currentPath $segment
        if (-not (Test-Path -LiteralPath $currentPath))
        {
            throw "$Description does not exist."
        }
        $item = Get-Item -LiteralPath $currentPath -Force
        if (($item.Attributes -band [System.IO.FileAttributes]::ReparsePoint) -ne 0)
        {
            throw "Reparse points are not allowed in $Description."
        }
    }
    $requiredPathType = if ($Directory) { 'Container' } else { 'Leaf' }
    if (-not (Test-Path -LiteralPath $fullPath -PathType $requiredPathType))
    {
        throw "$Description has the wrong filesystem type."
    }
    return $fullPath
}

function Test-IsolatedEnvironmentName
{
    param([Parameter(Mandatory = $true)][string]$Name)
    $canonicalName = $Name.ToUpperInvariant() -replace '[.\-]', '_'
    foreach ($prefix in @(
            'SPRING_',
            'LAB_',
            'RUOYI_',
            'TOKEN_',
            'SERVER_',
            'LOGGING_',
            'MAVEN_',
            'MYSQL_'))
    {
        if ($canonicalName.StartsWith($prefix, [System.StringComparison]::Ordinal))
        {
            return $true
        }
    }
    return $canonicalName -in @(
        'JAVA_HOME',
        'JAVA_TOOL_OPTIONS',
        '_JAVA_OPTIONS',
        'JDK_JAVA_OPTIONS',
        'JVM_CONFIG_MAVEN_PROPS')
}

function Get-Sha256Hex
{
    param([Parameter(Mandatory = $true)][string]$Path)

    $stream = [System.IO.File]::Open(
        $Path,
        [System.IO.FileMode]::Open,
        [System.IO.FileAccess]::Read,
        [System.IO.FileShare]::Read)
    $algorithm = $null
    try
    {
        $algorithm = [System.Security.Cryptography.SHA256]::Create()
        $hash = $algorithm.ComputeHash($stream)
        return ([System.BitConverter]::ToString($hash) -replace '-', '')
    }
    finally
    {
        if ($null -ne $algorithm)
        {
            $algorithm.Dispose()
        }
        $stream.Dispose()
    }
}

if ([string]::IsNullOrWhiteSpace($DatabaseName) -or
    $DatabaseName -notmatch '^lab_test_[A-Za-z0-9_]+$')
{
    throw 'Refusing to use an unsafe database name.'
}
$mode = $PSCmdlet.ParameterSetName
if ($mode -eq 'CleanVerify' -and -not $CleanVerify)
{
    throw 'CleanVerify cannot be explicitly disabled.'
}
if ($mode -eq 'Tests' -and [string]::IsNullOrWhiteSpace($Tests))
{
    throw 'Tests must not be empty.'
}

$scriptsRoot = Get-PhysicalPath -Path $PSScriptRoot -Description 'scripts root' -Directory
$repoRoot = Get-PhysicalPath `
    -Path (Join-Path $scriptsRoot '..') `
    -Description 'repository root' `
    -Directory
$workerPath = Get-PhysicalPath `
    -Path (Join-Path $scriptsRoot 'run-lab-tests-worker.ps1') `
    -Description 'lab test worker'
$repoPrefix = $repoRoot.TrimEnd(
    [System.IO.Path]::DirectorySeparatorChar,
    [System.IO.Path]::AltDirectorySeparatorChar) +
    [System.IO.Path]::DirectorySeparatorChar
if (-not $workerPath.StartsWith($repoPrefix, [System.StringComparison]::OrdinalIgnoreCase))
{
    throw 'The lab test worker is outside the repository.'
}
$workerHash = Get-Sha256Hex -Path $workerPath
if ($workerHash -notmatch '^[0-9A-F]{64}$')
{
    throw 'The lab test worker hash is invalid.'
}

$hostExecutableName = if ($PSVersionTable.PSEdition -eq 'Core') { 'pwsh.exe' } else { 'powershell.exe' }
$hostExecutablePath = Get-PhysicalPath `
    -Path (Join-Path $PSHOME $hostExecutableName) `
    -Description 'PowerShell host'

$adminHost = [System.Environment]::GetEnvironmentVariable('LAB_TEST_ADMIN_HOST')
$adminPort = [System.Environment]::GetEnvironmentVariable('LAB_TEST_ADMIN_PORT')
$adminUser = [System.Environment]::GetEnvironmentVariable('LAB_TEST_ADMIN_USERNAME')
$adminPassword = [System.Environment]::GetEnvironmentVariable('LAB_TEST_ADMIN_PASSWORD')
$databaseUser = [System.Environment]::GetEnvironmentVariable('LAB_TEST_DB_USERNAME')
$databasePassword = [System.Environment]::GetEnvironmentVariable('LAB_TEST_DB_PASSWORD')
$javaHome = [System.Environment]::GetEnvironmentVariable('JAVA_HOME')
if ($adminHost -notin @('localhost', '127.0.0.1') -or
    [string]::IsNullOrWhiteSpace($adminPort) -or
    [string]::IsNullOrWhiteSpace($adminUser) -or
    [string]::IsNullOrWhiteSpace($adminPassword) -or
    [string]::IsNullOrWhiteSpace($databaseUser) -or
    [string]::IsNullOrWhiteSpace($databasePassword) -or
    [string]::IsNullOrWhiteSpace($javaHome))
{
    throw 'Required lab-test environment input is missing.'
}

$startInfo = New-Object System.Diagnostics.ProcessStartInfo
$startInfo.FileName = $hostExecutablePath
$startInfo.Arguments = '-NoLogo -NoProfile -NonInteractive -ExecutionPolicy Bypass -File "' +
    $workerPath + '"'
$startInfo.WorkingDirectory = $repoRoot
$startInfo.UseShellExecute = $false
$startInfo.CreateNoWindow = $true
$startInfo.WindowStyle = [System.Diagnostics.ProcessWindowStyle]::Hidden
$startInfo.RedirectStandardOutput = $true
$startInfo.RedirectStandardError = $true
foreach ($name in @($startInfo.EnvironmentVariables.Keys))
{
    $actualName = [string]$name
    if (Test-IsolatedEnvironmentName -Name $actualName)
    {
        $startInfo.EnvironmentVariables.Remove($actualName)
    }
}
$startInfo.EnvironmentVariables['LAB_WRAPPER_INPUT_REPO_ROOT'] = $repoRoot
$startInfo.EnvironmentVariables['LAB_WRAPPER_INPUT_WORKER_HASH'] = $workerHash
$startInfo.EnvironmentVariables['LAB_WRAPPER_INPUT_DATABASE_NAME'] = $DatabaseName
$startInfo.EnvironmentVariables['LAB_WRAPPER_INPUT_MODE'] = $mode
$startInfo.EnvironmentVariables['LAB_WRAPPER_INPUT_TESTS'] = $(if ($mode -eq 'Tests') { $Tests } else { '' })
$startInfo.EnvironmentVariables['LAB_WRAPPER_INPUT_ADMIN_HOST'] = $adminHost
$startInfo.EnvironmentVariables['LAB_WRAPPER_INPUT_ADMIN_PORT'] = $adminPort
$startInfo.EnvironmentVariables['LAB_WRAPPER_INPUT_ADMIN_USER'] = $adminUser
$startInfo.EnvironmentVariables['LAB_WRAPPER_INPUT_ADMIN_PASSWORD'] = $adminPassword
$startInfo.EnvironmentVariables['LAB_WRAPPER_INPUT_DATABASE_USER'] = $databaseUser
$startInfo.EnvironmentVariables['LAB_WRAPPER_INPUT_DATABASE_PASSWORD'] = $databasePassword
$startInfo.EnvironmentVariables['LAB_WRAPPER_INPUT_JAVA_HOME'] = $javaHome

$process = New-Object System.Diagnostics.Process
$process.StartInfo = $startInfo
$workerExitCode = $null
try
{
    if (-not $process.Start())
    {
        throw 'The isolated lab-test worker could not be started.'
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
    $workerExitCode = $process.ExitCode
}
finally
{
    $process.Dispose()
}
if ($workerExitCode -ne 0)
{
    exit $workerExitCode
}
$global:LASTEXITCODE = 0
