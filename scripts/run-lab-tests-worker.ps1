Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
if (Test-Path Variable:PSNativeCommandUseErrorActionPreference)
{
    # Keep native exit codes under the wrapper's explicit LASTEXITCODE handling.
    # Assignment is local to this script invocation and does not change callers.
    $PSNativeCommandUseErrorActionPreference = $false
}

$inputRepoRoot = $env:LAB_WRAPPER_INPUT_REPO_ROOT
$inputWorkerHash = $env:LAB_WRAPPER_INPUT_WORKER_HASH
$DatabaseName = $env:LAB_WRAPPER_INPUT_DATABASE_NAME
$workerMode = $env:LAB_WRAPPER_INPUT_MODE
$Tests = $env:LAB_WRAPPER_INPUT_TESTS
$adminHost = $env:LAB_WRAPPER_INPUT_ADMIN_HOST
$adminPortText = $env:LAB_WRAPPER_INPUT_ADMIN_PORT
$adminUser = $env:LAB_WRAPPER_INPUT_ADMIN_USER
$adminPassword = $env:LAB_WRAPPER_INPUT_ADMIN_PASSWORD
$databaseUser = $env:LAB_WRAPPER_INPUT_DATABASE_USER
$databasePassword = $env:LAB_WRAPPER_INPUT_DATABASE_PASSWORD
$inputJavaHome = $env:LAB_WRAPPER_INPUT_JAVA_HOME

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

foreach ($name in @([System.Environment]::GetEnvironmentVariables().Keys))
{
    $actualName = [string]$name
    $canonicalName = $actualName.ToUpperInvariant() -replace '[.\-]', '_'
    $isolated = $false
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
            $isolated = $true
            break
        }
    }
    if ($isolated -or $canonicalName -in @(
            'JAVA_HOME',
            'JAVA_TOOL_OPTIONS',
            '_JAVA_OPTIONS',
            'JDK_JAVA_OPTIONS',
            'JVM_CONFIG_MAVEN_PROPS'))
    {
        Remove-Item -LiteralPath "Env:$actualName" -ErrorAction SilentlyContinue
    }
}

if ([string]::IsNullOrWhiteSpace($inputRepoRoot) -or
    $inputWorkerHash -notmatch '^[0-9A-Fa-f]{64}$' -or
    $DatabaseName -notmatch '^lab_test_[A-Za-z0-9_]+$' -or
    $workerMode -notin @('Tests', 'CleanVerify') -or
    ($workerMode -eq 'Tests' -and [string]::IsNullOrWhiteSpace($Tests)) -or
    $adminHost -notin @('localhost', '127.0.0.1') -or
    [string]::IsNullOrWhiteSpace($adminPortText) -or
    [string]::IsNullOrWhiteSpace($adminUser) -or
    [string]::IsNullOrWhiteSpace($adminPassword) -or
    [string]::IsNullOrWhiteSpace($databaseUser) -or
    [string]::IsNullOrWhiteSpace($databasePassword) -or
    [string]::IsNullOrWhiteSpace($inputJavaHome))
{
    throw 'The isolated lab-test worker received invalid input.'
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

function Assert-NoMavenStartupConfiguration
{
    param(
        [Parameter(Mandatory = $true)]
        [string]$RepositoryRoot
    )

    $currentDirectory = [System.IO.Path]::GetFullPath($RepositoryRoot)
    while (-not [string]::IsNullOrEmpty($currentDirectory))
    {
        if (Test-Path -LiteralPath (Join-Path $currentDirectory '.mvn'))
        {
            throw 'Repository and ancestor .mvn startup configuration is not allowed.'
        }
        $parent = [System.IO.Directory]::GetParent($currentDirectory)
        if ($null -eq $parent)
        {
            break
        }
        $currentDirectory = $parent.FullName
    }
}

function New-RandomHex
{
    param([int]$ByteCount = 32)
    $bytes = New-Object byte[] $ByteCount
    $random = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try
    {
        $random.GetBytes($bytes)
    }
    finally
    {
        $random.Dispose()
    }
    return ([System.BitConverter]::ToString($bytes) -replace '-', '').ToLowerInvariant()
}

function New-TrustedMavenSettings
{
    param(
        [Parameter(Mandatory = $true)]
        [string]$Path,

        [Parameter(Mandatory = $true)]
        [string]$TrustedRoot
    )

    $fullPath = Assert-NoReparsePoint -Path $Path -Description 'trusted Maven settings file'
    Assert-PathInside -Path $fullPath -Root $TrustedRoot -Description 'trusted Maven settings file'
    if (Test-Path -LiteralPath $fullPath)
    {
        throw 'Trusted Maven settings target already exists.'
    }

    $settingsText = @'
<?xml version="1.0" encoding="UTF-8"?>
<settings xmlns="http://maven.apache.org/SETTINGS/1.2.0"
          xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
          xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.2.0 https://maven.apache.org/xsd/settings-1.2.0.xsd" />
'@
    $bytes = [System.Text.UTF8Encoding]::new($false).GetBytes($settingsText)
    $stream = $null
    try
    {
        $stream = [System.IO.File]::Open(
            $fullPath,
            [System.IO.FileMode]::CreateNew,
            [System.IO.FileAccess]::Write,
            [System.IO.FileShare]::None)
        $stream.Write($bytes, 0, $bytes.Length)
        $stream.Flush($true)
    }
    catch
    {
        if ($null -ne $stream)
        {
            $stream.Dispose()
            $stream = $null
        }
        if (Test-Path -LiteralPath $fullPath -PathType Leaf)
        {
            $createdFile = Get-SafeExistingFile `
                -Path $fullPath `
                -Description 'incomplete trusted Maven settings file' `
                -TrustedRoot $TrustedRoot
            Remove-Item -LiteralPath $createdFile -Force
        }
        throw
    }
    finally
    {
        if ($null -ne $stream)
        {
            $stream.Dispose()
        }
    }
    return Get-SafeExistingFile `
        -Path $fullPath `
        -Description 'trusted Maven settings file' `
        -TrustedRoot $TrustedRoot
}

function Remove-TrustedMavenSettings
{
    param(
        [Parameter(Mandatory = $true)]
        [string]$Path,

        [Parameter(Mandatory = $true)]
        [string]$TrustedRoot
    )

    if (-not (Test-Path -LiteralPath $Path))
    {
        return
    }
    $safePath = Get-SafeExistingFile `
        -Path $Path `
        -Description 'trusted Maven settings file cleanup target' `
        -TrustedRoot $TrustedRoot
    Remove-Item -LiteralPath $safePath -Force
    if (Test-Path -LiteralPath $safePath)
    {
        throw 'Trusted Maven settings cleanup failed.'
    }
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
if ($workerMode -eq 'Tests')
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
    $requiredTestNames = @('LabCompatibilityProbeMapperTest')
}
$normalizedRequiredTests = [string]::Join(',', $requiredTestNames)

# Complete every non-database preflight before resetting the test database.
$resolvedRepoRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path
$repoRoot = Assert-NoReparsePoint -Path $resolvedRepoRoot -Description 'repository root'
if (-not [string]::Equals(
        $repoRoot,
        [System.IO.Path]::GetFullPath($inputRepoRoot),
        [System.StringComparison]::OrdinalIgnoreCase))
{
    throw 'The isolated lab-test worker repository root changed.'
}
$workerPath = Get-SafeExistingFile `
    -Path $MyInvocation.MyCommand.Path `
    -Description 'lab-test worker script' `
    -TrustedRoot $repoRoot
$actualWorkerHash = Get-Sha256Hex -Path $workerPath
if ($actualWorkerHash -cne $inputWorkerHash.ToUpperInvariant())
{
    throw 'The isolated lab-test worker hash changed before execution.'
}
$resetScript = Get-SafeExistingFile `
    -Path (Join-Path $PSScriptRoot 'reset-test-db.ps1') `
    -Description 'database reset script' `
    -TrustedRoot $repoRoot
$assertScript = Get-SafeExistingFile `
    -Path (Join-Path $PSScriptRoot 'assert-surefire-tests.ps1') `
    -Description 'Surefire assertion script' `
    -TrustedRoot $repoRoot

$windowsPowerShellPath = Get-SafeExistingFile `
    -Path (Join-Path $env:SystemRoot 'System32\WindowsPowerShell\v1.0\powershell.exe') `
    -Description 'native powershell.exe'
$mavenCommand = Get-Command mvn -CommandType Application -ErrorAction Stop |
    Select-Object -First 1
if ($null -eq $mavenCommand)
{
    throw 'mvn must resolve to a Maven application.'
}
$mavenPath = Get-SafeExistingFile -Path $mavenCommand.Source -Description 'Maven application'

if ([string]::IsNullOrWhiteSpace($inputJavaHome))
{
    throw 'JAVA_HOME must identify a JDK 17 installation.'
}
$validatedJavaHome = Assert-NoReparsePoint -Path $inputJavaHome -Description 'JDK 17 home'
if (-not (Test-Path -LiteralPath $validatedJavaHome -PathType Container))
{
    throw 'JAVA_HOME must identify a JDK 17 installation.'
}
$null = Get-SafeExistingFile `
    -Path (Join-Path $validatedJavaHome 'bin\java.exe') `
    -Description 'JDK 17 Java executable' `
    -TrustedRoot $validatedJavaHome
$javaReleasePath = Get-SafeExistingFile `
    -Path (Join-Path $validatedJavaHome 'release') `
    -Description 'JDK 17 release metadata' `
    -TrustedRoot $validatedJavaHome
$javaReleaseText = [System.IO.File]::ReadAllText($javaReleasePath)
if ($javaReleaseText -notmatch '(?m)^JAVA_VERSION="17(?:\.[^"]*)?"\s*$')
{
    throw 'JAVA_HOME must identify a JDK 17 installation.'
}
Assert-NoMavenStartupConfiguration -RepositoryRoot $repoRoot

$adminPort = 0
if ($adminHost -notin @('localhost', '127.0.0.1') -or
    -not [int]::TryParse($adminPortText, [ref]$adminPort) -or
    $adminPort -lt 1 -or $adminPort -gt 65535)
{
    throw 'LAB_TEST_ADMIN_HOST/PORT did not pass the local test database check.'
}
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
$trustedMavenSettingsPath = [System.IO.Path]::GetFullPath(
    (Join-Path $targetRoot 'lab-test-maven-settings.xml'))
Assert-PathInside `
    -Path $trustedMavenSettingsPath `
    -Root $targetRoot `
    -Description 'trusted Maven settings file'
$null = Assert-NoReparsePoint `
    -Path $trustedMavenSettingsPath `
    -Description 'trusted Maven settings file'
if (Test-Path -LiteralPath $trustedMavenSettingsPath)
{
    throw 'Trusted Maven settings target already exists.'
}

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
$mavenExitCode = $null
$trustedMavenSettingsCreated = $false
$testTokenSecret = New-RandomHex
$testRedisPassword = New-RandomHex
try
{
    $env:LAB_TEST_ADMIN_HOST = $adminHost
    $env:LAB_TEST_ADMIN_PORT = [string]$adminPort
    $env:LAB_TEST_ADMIN_USERNAME = $adminUser
    $env:LAB_TEST_ADMIN_PASSWORD = $adminPassword
    $env:LAB_TEST_WRAPPER_ACTIVE = 'true'
    $env:LAB_TEST_DB_URL = $safeJdbcUrl
    $env:LAB_TEST_DB_USERNAME = $databaseUser
    $env:LAB_TEST_DB_PASSWORD = $databasePassword
    $env:LAB_TEST_FLYWAY_ENABLED = 'true'
    $env:LAB_DB_URL = $safeJdbcUrl
    $env:LAB_DB_USERNAME = $databaseUser
    $env:LAB_DB_PASSWORD = $databasePassword
    $env:LAB_FILE_ROOT = $fileRoot
    $env:LAB_TOKEN_SECRET = $testTokenSecret
    $env:LAB_REDIS_PASSWORD = $testRedisPassword

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
    $env:SPRING_FLYWAY_URL = $safeJdbcUrl
    $env:SPRING_FLYWAY_USER = $databaseUser
    $env:SPRING_FLYWAY_PASSWORD = $databasePassword
    $env:SPRING_FLYWAY_LOCATIONS = 'classpath:db/migration'
    $env:SPRING_FLYWAY_BASELINE_ON_MIGRATE = 'false'
    $env:SPRING_FLYWAY_CLEAN_DISABLED = 'true'
    $env:SPRING_FLYWAY_VALIDATE_ON_MIGRATE = 'true'
    $env:SPRING_FLYWAY_PLACEHOLDERREPLACEMENT = 'false'
    $env:SPRING_FLYWAY_PLACEHOLDER_REPLACEMENT = 'false'
    $env:SPRING_FLYWAY_SCHEMAS = $DatabaseName
    $env:SPRING_FLYWAY_DEFAULT_SCHEMA = $DatabaseName
    $env:SPRING_FLYWAY_TABLE = 'flyway_schema_history'
    $env:SPRING_PROFILES_ACTIVE = 'test'
    $env:SPRING_SQL_INIT_MODE = 'never'
    $env:SPRING_QUARTZ_JDBC_INITIALIZE_SCHEMA = 'never'
    # Replace Spring Boot's default current-directory ConfigData search with
    # the packaged classpath configuration only. Every inherited ConfigData
    # and profile variant was removed above; only this whitelist is restored
    # for the isolated test invocation.
    $env:SPRING_CONFIG_LOCATION = 'classpath:/'
    $env:SPRING_CONFIG_NAME = 'application'
    $env:RUOYI_PROFILE = $fileRoot
    $env:JAVA_HOME = $validatedJavaHome
    $env:MAVEN_BASEDIR = $repoRoot
    $env:MAVEN_SKIP_RC = 'true'

    $trustedMavenSettingsPath = New-TrustedMavenSettings `
        -Path $trustedMavenSettingsPath `
        -TrustedRoot $targetRoot
    $trustedMavenSettingsCreated = $true
    $safeMavenArguments = @(
        '-s',
        $trustedMavenSettingsPath,
        '-gs',
        $trustedMavenSettingsPath)
    $safeSpringArguments = @(
        ('"-Dspring.datasource.druid.master.url={0}"' -f $safeJdbcUrl),
        '-Dspring.datasource.druid.slave.enabled=false',
        '-Dspring.flyway.enabled=true',
        '-Dspring.flyway.placeholder-replacement=false',
        '-Dspring.sql.init.mode=never',
        '-Dspring.quartz.jdbc.initialize-schema=never',
        '-Dspring.profiles.active=test',
        ('"-Druoyi.profile={0}"' -f $fileRoot))
    Push-Location -LiteralPath $repoRoot
    try
    {
        # This is the final operation after every non-database preflight and
        # before Maven can execute tests against the freshly rebuilt database.
        & $windowsPowerShellPath `
            -NoProfile `
            -OutputFormat Text `
            -ExecutionPolicy Bypass `
            -File $resetScript `
            -DatabaseName $DatabaseName
        $resetExitCode = $LASTEXITCODE
        if ($resetExitCode -ne 0)
        {
            throw "Test database reset failed with exit code $resetExitCode"
        }
        Assert-NoMavenStartupConfiguration -RepositoryRoot $repoRoot
        foreach ($name in @(
                'LAB_TEST_ADMIN_HOST',
                'LAB_TEST_ADMIN_PORT',
                'LAB_TEST_ADMIN_USERNAME',
                'LAB_TEST_ADMIN_PASSWORD',
                'MYSQL_PWD',
                'MYSQL_TEST_LOGIN_FILE'))
        {
            Remove-Item -LiteralPath "Env:$name" -ErrorAction SilentlyContinue
        }

        if ($workerMode -eq 'CleanVerify')
        {
            & $mavenPath `
                @safeMavenArguments `
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
                @safeMavenArguments `
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
    if ($trustedMavenSettingsCreated)
    {
        Remove-TrustedMavenSettings `
            -Path $trustedMavenSettingsPath `
            -TrustedRoot $targetRoot
    }
}

if ($mavenExitCode -ne 0)
{
    exit $mavenExitCode
}
