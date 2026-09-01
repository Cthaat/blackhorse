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
if (Test-Path Variable:PSNativeCommandUseErrorActionPreference)
{
    # Keep native exit codes under the wrapper's explicit LASTEXITCODE handling.
    # Assignment is local to this script invocation and does not change callers.
    $PSNativeCommandUseErrorActionPreference = $false
}

function Assert-NoReparsePoint
{
    param(
        [Parameter(Mandatory = $true)]
        [string]$Path,

        [Parameter(Mandatory = $true)]
        [string]$Description
    )

    $fullPath = [System.IO.Path]::GetFullPath($Path)
    $pathRoot = [System.IO.Path]::GetPathRoot($fullPath)
    if ([string]::IsNullOrWhiteSpace($pathRoot))
    {
        throw "Cannot determine the filesystem root for ${Description}: $fullPath"
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
            break
        }
        $item = Get-Item -LiteralPath $currentPath -Force
        if (($item.Attributes -band [System.IO.FileAttributes]::ReparsePoint) -ne 0)
        {
            throw "Reparse points are not allowed in ${Description}: $currentPath"
        }
    }
    return $fullPath
}

function Assert-PathInside
{
    param(
        [Parameter(Mandatory = $true)]
        [string]$Path,

        [Parameter(Mandatory = $true)]
        [string]$Root,

        [Parameter(Mandatory = $true)]
        [string]$Description
    )

    $rootPrefix = $Root.TrimEnd(
        [System.IO.Path]::DirectorySeparatorChar,
        [System.IO.Path]::AltDirectorySeparatorChar) +
        [System.IO.Path]::DirectorySeparatorChar
    if (-not $Path.StartsWith($rootPrefix, [System.StringComparison]::OrdinalIgnoreCase))
    {
        throw "$Description is outside its trusted root: $Path"
    }
}

function Get-SafeDirectory
{
    param(
        [Parameter(Mandatory = $true)]
        [string]$Path,

        [Parameter(Mandatory = $true)]
        [string]$Root,

        [Parameter(Mandatory = $true)]
        [string]$Description
    )

    $fullPath = Assert-NoReparsePoint -Path $Path -Description $Description
    Assert-PathInside -Path $fullPath -Root $Root -Description $Description
    if (Test-Path -LiteralPath $fullPath)
    {
        if (-not (Test-Path -LiteralPath $fullPath -PathType Container))
        {
            throw "$Description is not a directory: $fullPath"
        }
    }
    else
    {
        $null = New-Item -ItemType Directory -Path $fullPath
    }

    $verifiedPath = Assert-NoReparsePoint -Path $fullPath -Description $Description
    if (-not (Test-Path -LiteralPath $verifiedPath -PathType Container))
    {
        throw "$Description could not be created: $verifiedPath"
    }
    Assert-PathInside -Path $verifiedPath -Root $Root -Description $Description
    return $verifiedPath
}

function Get-SafeExistingFile
{
    param(
        [Parameter(Mandatory = $true)]
        [string]$Path,

        [Parameter(Mandatory = $true)]
        [string]$Description,

        [string]$TrustedRoot
    )

    $fullPath = Assert-NoReparsePoint -Path $Path -Description $Description
    if (-not [string]::IsNullOrEmpty($TrustedRoot))
    {
        Assert-PathInside -Path $fullPath -Root $TrustedRoot -Description $Description
    }
    if (-not (Test-Path -LiteralPath $fullPath -PathType Leaf))
    {
        throw "$Description not found: $fullPath"
    }
    return Assert-NoReparsePoint -Path $fullPath -Description $Description
}

function Find-UniqueSafeTestSource
{
    param(
        [Parameter(Mandatory = $true)]
        [string]$SourceRoot,

        [Parameter(Mandatory = $true)]
        [string]$TestName
    )

    $fileName = "$TestName.java"
    $pending = [System.Collections.Generic.Queue[string]]::new()
    $matches = [System.Collections.Generic.List[string]]::new()
    $pending.Enqueue($SourceRoot)
    while ($pending.Count -gt 0)
    {
        $directory = $pending.Dequeue()
        foreach ($entry in @(Get-ChildItem -LiteralPath $directory -Force))
        {
            if (($entry.Attributes -band [System.IO.FileAttributes]::ReparsePoint) -ne 0)
            {
                throw "Reparse points are not allowed in test sources: $($entry.FullName)"
            }
            if ($entry.PSIsContainer)
            {
                $pending.Enqueue($entry.FullName)
            }
            elseif ([string]::Equals($entry.Name, $fileName, [System.StringComparison]::Ordinal))
            {
                $matches.Add($entry.FullName)
            }
        }
    }
    if ($matches.Count -ne 1)
    {
        throw "Expected one test source file for $TestName, found $($matches.Count)."
    }
    return Get-SafeExistingFile `
        -Path $matches[0] `
        -Description "test source for $TestName" `
        -TrustedRoot $SourceRoot
}

$safeDatabasePattern = '^lab_test_[A-Za-z0-9_]+$'
if ([string]::IsNullOrWhiteSpace($DatabaseName) -or $DatabaseName -notmatch $safeDatabasePattern)
{
    throw "Refusing to use an unsafe database name: $DatabaseName"
}
$requiredTestNames = @()
if ($PSCmdlet.ParameterSetName -eq 'Tests')
{
    $seenTestNames = [System.Collections.Generic.HashSet[string]]::new(
        [System.StringComparer]::OrdinalIgnoreCase)
    foreach ($testName in @($Tests.Split(
                [char[]]@(','),
                [System.StringSplitOptions]::None)))
    {
        if ($testName -notmatch '^[A-Za-z][A-Za-z0-9_]*$')
        {
            throw "Tests must contain only comma-separated simple class names: $testName"
        }
        if (-not $seenTestNames.Add($testName))
        {
            throw "Duplicate test class name: $testName"
        }
        $requiredTestNames += $testName
    }
}
else
{
    if (-not $CleanVerify)
    {
        throw 'CleanVerify cannot be explicitly disabled.'
    }
    $requiredTestNames = @('LabCompatibilityProbeMapperTest')
}
$normalizedRequiredTests = [string]::Join(',', $requiredTestNames)

# Complete every non-database preflight before resetting the test database.
$resolvedRepoRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path
$repoRoot = Assert-NoReparsePoint -Path $resolvedRepoRoot -Description 'repository root'
$resetScript = Get-SafeExistingFile `
    -Path (Join-Path $PSScriptRoot 'reset-test-db.ps1') `
    -Description 'database reset script' `
    -TrustedRoot $repoRoot
$assertScript = Get-SafeExistingFile `
    -Path (Join-Path $PSScriptRoot 'assert-surefire-tests.ps1') `
    -Description 'Surefire assertion script' `
    -TrustedRoot $repoRoot

$windowsPowerShellCommand = Get-Command powershell -CommandType Application -ErrorAction Stop |
    Where-Object { [System.IO.Path]::GetExtension($_.Source) -ieq '.exe' } |
    Select-Object -First 1
if ($null -eq $windowsPowerShellCommand)
{
    throw 'powershell must resolve to a native powershell.exe executable.'
}
$windowsPowerShellPath = Get-SafeExistingFile `
    -Path $windowsPowerShellCommand.Source `
    -Description 'native powershell.exe'
$mavenCommand = Get-Command mvn -CommandType Application -ErrorAction Stop |
    Select-Object -First 1
if ($null -eq $mavenCommand)
{
    throw 'mvn must resolve to a Maven application.'
}
$mavenPath = Get-SafeExistingFile -Path $mavenCommand.Source -Description 'Maven application'

$adminHost = $env:LAB_TEST_ADMIN_HOST
$adminPort = 0
if ($adminHost -notin @('localhost', '127.0.0.1') -or
    -not [int]::TryParse($env:LAB_TEST_ADMIN_PORT, [ref]$adminPort) -or
    $adminPort -lt 1 -or $adminPort -gt 65535)
{
    throw 'LAB_TEST_ADMIN_HOST/PORT did not pass the local test database check.'
}
$databaseUser = $env:LAB_TEST_DB_USERNAME
$databasePassword = $env:LAB_TEST_DB_PASSWORD
if ([string]::IsNullOrWhiteSpace($databaseUser) -or
    [string]::IsNullOrWhiteSpace($databasePassword))
{
    throw 'LAB_TEST_DB_USERNAME and LAB_TEST_DB_PASSWORD are required.'
}

$moduleRoot = Assert-NoReparsePoint `
    -Path (Join-Path $repoRoot 'ruoyi-admin') `
    -Description 'admin module root'
Assert-PathInside -Path $moduleRoot -Root $repoRoot -Description 'admin module root'
if (-not (Test-Path -LiteralPath $moduleRoot -PathType Container))
{
    throw "Admin module root not found: $moduleRoot"
}
$sourceRoot = Assert-NoReparsePoint `
    -Path (Join-Path $moduleRoot 'src\test\java') `
    -Description 'admin test source root'
Assert-PathInside -Path $sourceRoot -Root $repoRoot -Description 'admin test source root'
if (-not (Test-Path -LiteralPath $sourceRoot -PathType Container))
{
    throw "Admin test source root not found: $sourceRoot"
}
foreach ($testName in $requiredTestNames)
{
    $null = Find-UniqueSafeTestSource -SourceRoot $sourceRoot -TestName $testName
}

$targetRoot = Get-SafeDirectory `
    -Path (Join-Path $repoRoot 'target') `
    -Root $repoRoot `
    -Description 'test target root'
$testFilesRoot = Get-SafeDirectory `
    -Path (Join-Path $targetRoot 'test-files') `
    -Root $repoRoot `
    -Description 'test files root'
$fileRoot = Get-SafeDirectory `
    -Path (Join-Path $testFilesRoot $DatabaseName) `
    -Root $repoRoot `
    -Description 'database test file root'
$adminTargetRoot = Get-SafeDirectory `
    -Path (Join-Path $moduleRoot 'target') `
    -Root $repoRoot `
    -Description 'admin target root'
$reportRoot = Get-SafeDirectory `
    -Path (Join-Path $adminTargetRoot 'surefire-reports') `
    -Root $repoRoot `
    -Description 'admin Surefire report directory'

foreach ($report in @(
        Get-ChildItem -LiteralPath $reportRoot -Force |
            Where-Object { $_.Name -like 'TEST-*.xml' }))
{
    if ($report.PSIsContainer -or
        ($report.Attributes -band [System.IO.FileAttributes]::ReparsePoint) -ne 0)
    {
        throw "Unsafe stale Surefire report entry: $($report.FullName)"
    }
    $reportPath = Get-SafeExistingFile `
        -Path $report.FullName `
        -Description 'stale Surefire XML report' `
        -TrustedRoot $reportRoot
    Remove-Item -LiteralPath $reportPath -Force
}

$safeJdbcUrl = "jdbc:mysql://${adminHost}:$adminPort/${DatabaseName}?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai"
$pinnedEnvironmentNames = @(
    'LAB_TEST_WRAPPER_ACTIVE',
    'LAB_TEST_DB_URL',
    'LAB_TEST_DB_USERNAME',
    'LAB_TEST_DB_PASSWORD',
    'LAB_TEST_FLYWAY_ENABLED',
    'LAB_DB_URL',
    'LAB_DB_USERNAME',
    'LAB_DB_PASSWORD',
    'LAB_FILE_ROOT',
    'SPRING_DATASOURCE_DRUID_MASTER_URL',
    'SPRING_DATASOURCE_DRUID_MASTER_USERNAME',
    'SPRING_DATASOURCE_DRUID_MASTER_PASSWORD',
    'SPRING_DATASOURCE_DRUID_SLAVE_ENABLED',
    'SPRING_DATASOURCE_DRUID_SLAVE_URL',
    'SPRING_DATASOURCE_DRUID_SLAVE_USERNAME',
    'SPRING_DATASOURCE_DRUID_SLAVE_PASSWORD',
    'SPRING_FLYWAY_ENABLED',
    'SPRING_PROFILES_ACTIVE',
    'RUOYI_PROFILE',
    'SPRING_APPLICATION_JSON',
    'JAVA_TOOL_OPTIONS',
    '_JAVA_OPTIONS',
    'JDK_JAVA_OPTIONS',
    'MAVEN_OPTS',
    'MAVEN_ARGS')
$previousEnvironment = @{}
foreach ($name in $pinnedEnvironmentNames)
{
    $previousEnvironment[$name] = @{
        Exists = Test-Path -LiteralPath "Env:$name"
        Value = [System.Environment]::GetEnvironmentVariable(
            $name,
            [System.EnvironmentVariableTarget]::Process)
    }
}

$mavenExitCode = $null
try
{
    $env:LAB_TEST_WRAPPER_ACTIVE = 'true'
    $env:LAB_TEST_DB_URL = $safeJdbcUrl
    $env:LAB_TEST_DB_USERNAME = $databaseUser
    $env:LAB_TEST_DB_PASSWORD = $databasePassword
    $env:LAB_TEST_FLYWAY_ENABLED = 'true'
    $env:LAB_DB_URL = $safeJdbcUrl
    $env:LAB_DB_USERNAME = $databaseUser
    $env:LAB_DB_PASSWORD = $databasePassword
    $env:LAB_FILE_ROOT = $fileRoot

    # Pin every supported datasource override and remove inherited JVM/Maven
    # channels that could inject higher-priority properties.
    $env:SPRING_DATASOURCE_DRUID_MASTER_URL = $safeJdbcUrl
    $env:SPRING_DATASOURCE_DRUID_MASTER_USERNAME = $databaseUser
    $env:SPRING_DATASOURCE_DRUID_MASTER_PASSWORD = $databasePassword
    $env:SPRING_DATASOURCE_DRUID_SLAVE_ENABLED = 'false'
    $env:SPRING_DATASOURCE_DRUID_SLAVE_URL = $safeJdbcUrl
    $env:SPRING_DATASOURCE_DRUID_SLAVE_USERNAME = $databaseUser
    $env:SPRING_DATASOURCE_DRUID_SLAVE_PASSWORD = $databasePassword
    $env:SPRING_FLYWAY_ENABLED = 'true'
    $env:SPRING_PROFILES_ACTIVE = 'test'
    $env:RUOYI_PROFILE = $fileRoot
    foreach ($name in @(
            'SPRING_APPLICATION_JSON',
            'JAVA_TOOL_OPTIONS',
            '_JAVA_OPTIONS',
            'JDK_JAVA_OPTIONS',
            'MAVEN_OPTS',
            'MAVEN_ARGS'))
    {
        Remove-Item -LiteralPath "Env:$name" -ErrorAction SilentlyContinue
    }

    $safeSpringArguments = @(
        ('"-Dspring.datasource.druid.master.url={0}"' -f $safeJdbcUrl),
        '-Dspring.datasource.druid.slave.enabled=false',
        '-Dspring.flyway.enabled=true',
        '-Dspring.profiles.active=test',
        ('"-Druoyi.profile={0}"' -f $fileRoot))
    Push-Location -LiteralPath $repoRoot
    try
    {
        # This is the final operation after every non-database preflight and
        # before Maven can execute tests against the freshly rebuilt database.
        & $windowsPowerShellPath `
            -NoProfile `
            -ExecutionPolicy Bypass `
            -File $resetScript `
            -DatabaseName $DatabaseName
        $resetExitCode = $LASTEXITCODE
        if ($resetExitCode -ne 0)
        {
            throw "Test database reset failed with exit code $resetExitCode"
        }

        if ($PSCmdlet.ParameterSetName -eq 'CleanVerify')
        {
            & $mavenPath `
                @safeSpringArguments `
                '-DskipTests=false' `
                '-Dmaven.test.skip=false' `
                '-Dmaven.clean.skip=false' `
                clean `
                verify
        }
        else
        {
            & $mavenPath `
                @safeSpringArguments `
                -pl ruoyi-admin `
                -am `
                '-DskipTests=false' `
                '-Dsurefire.failIfNoSpecifiedTests=false' `
                "-Dtest=$normalizedRequiredTests" `
                test
        }
        $mavenExitCode = $LASTEXITCODE
        if ($mavenExitCode -eq 0)
        {
            & $assertScript `
                -Module 'ruoyi-admin' `
                -RequiredTests $normalizedRequiredTests
        }
    }
    finally
    {
        Pop-Location
    }
}
finally
{
    foreach ($name in $pinnedEnvironmentNames)
    {
        if ($previousEnvironment[$name].Exists)
        {
            [System.Environment]::SetEnvironmentVariable(
                $name,
                $previousEnvironment[$name].Value,
                [System.EnvironmentVariableTarget]::Process)
        }
        else
        {
            Remove-Item -LiteralPath "Env:$name" -ErrorAction SilentlyContinue
        }
    }
}

if ($mavenExitCode -ne 0)
{
    exit $mavenExitCode
}
