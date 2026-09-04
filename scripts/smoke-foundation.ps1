[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$appPort = 18080
$databaseName = 'lab_test_m1_smoke'
$repoRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$jarPath = Join-Path $repoRoot 'ruoyi-admin\target\ruoyi-admin.jar'
$runRoot = Join-Path $repoRoot ('target\smoke-foundation\' + [System.Guid]::NewGuid().ToString('N'))
$fileRoot = Join-Path $runRoot 'files'
$stdoutPath = Join-Path $runRoot 'application.stdout.log'
$stderrPath = Join-Path $runRoot 'application.stderr.log'
$logbackPath = Join-Path $runRoot 'logback-smoke.xml'
$appProcess = $null
$appStartTicks = $null
$httpClient = $null
$tokens = New-Object System.Collections.Generic.List[string]
$mysqlRuntimeIdentity = $null
$memuraiRuntimeIdentity = $null
$redisIsolationVerified = $false
$cleanupFailures = New-Object System.Collections.Generic.List[string]

function Write-Pass
{
    param(
        [Parameter(Mandatory = $true)][string]$Name,
        [string]$TraceId = ''
    )

    if ([string]::IsNullOrWhiteSpace($TraceId))
    {
        Write-Output ('PASS: ' + $Name)
    }
    else
    {
        Write-Output ('PASS: ' + $Name + ' traceId=' + $TraceId)
    }
}

function Get-RequiredEnvironmentValue
{
    param([Parameter(Mandatory = $true)][string]$Name)

    $value = [System.Environment]::GetEnvironmentVariable($Name)
    if ([string]::IsNullOrWhiteSpace($value))
    {
        throw ('Required environment variable is missing or blank: ' + $Name)
    }
    return $value
}

function Get-RequiredPort
{
    param(
        [Parameter(Mandatory = $true)][string]$Name,
        [Parameter(Mandatory = $true)][string]$Value
    )

    $port = 0
    if (-not [int]::TryParse($Value, [ref]$port) -or $port -lt 1 -or $port -gt 65535)
    {
        throw ($Name + ' must be an integer from 1 through 65535.')
    }
    return $port
}

function Get-RequiredProcessId
{
    param(
        [Parameter(Mandatory = $true)][string]$Name,
        [Parameter(Mandatory = $true)][string]$Value
    )

    $processId = 0
    if (-not [int]::TryParse($Value, [ref]$processId) -or $processId -lt 1)
    {
        throw ($Name + ' must be a positive process identifier.')
    }
    return $processId
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
            'MYSQL_',
            'REDIS_'))
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
        'JVM_CONFIG_MAVEN_PROPS',
        'CLASSPATH')
}

function Assert-RegularFile
{
    param(
        [Parameter(Mandatory = $true)][string]$Path,
        [Parameter(Mandatory = $true)][string]$Description
    )

    $fullPath = [System.IO.Path]::GetFullPath($Path)
    if (-not (Test-Path -LiteralPath $fullPath -PathType Leaf))
    {
        throw ($Description + ' does not exist.')
    }

    $root = [System.IO.Path]::GetPathRoot($fullPath)
    $current = $root
    $separators = [char[]]@(
        [System.IO.Path]::DirectorySeparatorChar,
        [System.IO.Path]::AltDirectorySeparatorChar)
    foreach ($segment in $fullPath.Substring($root.Length).Split(
            $separators,
            [System.StringSplitOptions]::RemoveEmptyEntries))
    {
        $current = Join-Path $current $segment
        $item = Get-Item -LiteralPath $current -Force
        if (($item.Attributes -band [System.IO.FileAttributes]::ReparsePoint) -ne 0)
        {
            throw ($Description + ' must not be reached through a reparse point.')
        }
    }

    return $fullPath
}

function Get-IsolatedRuntimeIdentity
{
    param(
        [Parameter(Mandatory = $true)][int]$Port,
        [Parameter(Mandatory = $true)][int]$ExpectedProcessId,
        [Parameter(Mandatory = $true)][string]$ExpectedExecutablePath,
        [Parameter(Mandatory = $true)][string]$Description
    )

    $listeners = @(Get-NetTCPConnection -State Listen -LocalPort $Port -ErrorAction Stop)
    if ($listeners.Count -ne 1 -or
        [string]$listeners[0].LocalAddress -ne '127.0.0.1' -or
        [int]$listeners[0].OwningProcess -ne $ExpectedProcessId)
    {
        throw ($Description + ' is not an exclusively owned IPv4 loopback listener.')
    }

    $cimProcesses = @(Get-CimInstance Win32_Process -Filter (
            'ProcessId=' + $ExpectedProcessId) -ErrorAction Stop)
    if ($cimProcesses.Count -ne 1 -or
        [string]::IsNullOrWhiteSpace([string]$cimProcesses[0].ExecutablePath) -or
        [string]::IsNullOrWhiteSpace([string]$cimProcesses[0].CommandLine))
    {
        throw ($Description + ' process image is unavailable.')
    }
    $actualExecutablePath = [System.IO.Path]::GetFullPath(
        [string]$cimProcesses[0].ExecutablePath)
    if (-not [string]::Equals(
            $actualExecutablePath,
            $ExpectedExecutablePath,
            [System.StringComparison]::OrdinalIgnoreCase))
    {
        throw ($Description + ' process image does not match the approved executable.')
    }

    $process = [System.Diagnostics.Process]::GetProcessById($ExpectedProcessId)
    try
    {
        $process.Refresh()
        if ($process.HasExited -or
            -not [string]::Equals(
                [System.IO.Path]::GetFullPath($process.MainModule.FileName),
                $ExpectedExecutablePath,
                [System.StringComparison]::OrdinalIgnoreCase))
        {
            throw ($Description + ' process identity changed during validation.')
        }
        return [pscustomobject]@{
            ProcessId = $ExpectedProcessId
            Port = $Port
            ExecutablePath = $ExpectedExecutablePath
            StartTimeUtcTicks = $process.StartTime.ToUniversalTime().Ticks
            Description = $Description
            CommandLine = [string]$cimProcesses[0].CommandLine
        }
    }
    finally
    {
        $process.Dispose()
    }
}

function Initialize-NativeCommandLineParser
{
    if ($null -ne ('LabSmoke.NativeCommandLineParser' -as [type]))
    {
        return
    }

    Add-Type -TypeDefinition @'
using System;
using System.ComponentModel;
using System.Runtime.InteropServices;

namespace LabSmoke
{
    public static class NativeCommandLineParser
    {
        [DllImport("shell32.dll", SetLastError = true)]
        private static extern IntPtr CommandLineToArgvW(
            [MarshalAs(UnmanagedType.LPWStr)] string commandLine,
            out int argumentCount);

        [DllImport("kernel32.dll")]
        private static extern IntPtr LocalFree(IntPtr memory);

        public static string[] Split(string commandLine)
        {
            if (String.IsNullOrWhiteSpace(commandLine))
            {
                throw new ArgumentException("The native command line is blank.", "commandLine");
            }

            int argumentCount;
            IntPtr arguments = CommandLineToArgvW(commandLine, out argumentCount);
            if (arguments == IntPtr.Zero)
            {
                throw new Win32Exception(Marshal.GetLastWin32Error());
            }

            try
            {
                string[] result = new string[argumentCount];
                for (int index = 0; index < argumentCount; index++)
                {
                    IntPtr value = Marshal.ReadIntPtr(arguments, index * IntPtr.Size);
                    result[index] = Marshal.PtrToStringUni(value);
                }
                return result;
            }
            finally
            {
                LocalFree(arguments);
            }
        }
    }
}
'@
}

function ConvertFrom-NativeCommandLine
{
    param([Parameter(Mandatory = $true)][string]$CommandLine)

    Initialize-NativeCommandLineParser
    return [LabSmoke.NativeCommandLineParser]::Split($CommandLine)
}

function Get-RequiredNativeOptionValue
{
    param(
        [Parameter(Mandatory = $true)][string[]]$Arguments,
        [Parameter(Mandatory = $true)][string]$Name,
        [Parameter(Mandatory = $true)][string]$Description
    )

    $values = New-Object System.Collections.Generic.List[string]
    for ($index = 1; $index -lt $Arguments.Count; $index++)
    {
        $argument = [string]$Arguments[$index]
        if ($argument -ceq $Name)
        {
            if ($index + 1 -ge $Arguments.Count -or
                [string]::IsNullOrWhiteSpace([string]$Arguments[$index + 1]))
            {
                throw ($Description + ' is missing its value.')
            }
            $values.Add([string]$Arguments[$index + 1]) | Out-Null
            $index++
        }
        elseif ($argument.StartsWith(
                $Name + '=',
                [System.StringComparison]::Ordinal))
        {
            $value = $argument.Substring($Name.Length + 1)
            if ([string]::IsNullOrWhiteSpace($value))
            {
                throw ($Description + ' is missing its value.')
            }
            $values.Add($value) | Out-Null
        }
    }

    if ($values.Count -ne 1)
    {
        throw ($Description + ' must occur exactly once in the owned process command line.')
    }
    return $values[0]
}

function Assert-IsolatedRuntimeIdentity
{
    param([Parameter(Mandatory = $true)]$Identity)

    $current = Get-IsolatedRuntimeIdentity `
        -Port ([int]$Identity.Port) `
        -ExpectedProcessId ([int]$Identity.ProcessId) `
        -ExpectedExecutablePath ([string]$Identity.ExecutablePath) `
        -Description ([string]$Identity.Description)
    if ([long]$current.StartTimeUtcTicks -ne [long]$Identity.StartTimeUtcTicks)
    {
        throw ([string]$Identity.Description + ' process identifier was reused.')
    }
}

function Assert-PhysicalDirectory
{
    param(
        [Parameter(Mandatory = $true)][string]$Path,
        [Parameter(Mandatory = $true)][string]$Description
    )

    $fullPath = [System.IO.Path]::GetFullPath($Path).TrimEnd('\')
    if (-not (Test-Path -LiteralPath $fullPath -PathType Container))
    {
        throw ($Description + ' does not exist.')
    }
    $root = [System.IO.Path]::GetPathRoot($fullPath)
    $current = $root
    $separators = [char[]]@(
        [System.IO.Path]::DirectorySeparatorChar,
        [System.IO.Path]::AltDirectorySeparatorChar)
    foreach ($segment in $fullPath.Substring($root.Length).Split(
            $separators,
            [System.StringSplitOptions]::RemoveEmptyEntries))
    {
        $current = Join-Path $current $segment
        $item = Get-Item -LiteralPath $current -Force
        if (($item.Attributes -band [System.IO.FileAttributes]::ReparsePoint) -ne 0 -or
            -not $item.PSIsContainer)
        {
            throw ($Description + ' must not contain or traverse a reparse point.')
        }
    }
    return $fullPath
}

function Get-RequiredPhysicalDirectory
{
    param(
        [Parameter(Mandatory = $true)][string]$Name,
        [Parameter(Mandatory = $true)][string]$Description
    )

    $value = Get-RequiredEnvironmentValue -Name $Name
    if (-not [System.IO.Path]::IsPathRooted($value))
    {
        throw ($Name + ' must be an absolute path.')
    }
    return Assert-PhysicalDirectory -Path $value -Description $Description
}

function Get-RequiredExecutablePath
{
    param(
        [Parameter(Mandatory = $true)][string]$Name,
        [Parameter(Mandatory = $true)][string]$ExpectedFileName,
        [Parameter(Mandatory = $true)][string]$Description
    )

    $value = Get-RequiredEnvironmentValue -Name $Name
    if (-not [System.IO.Path]::IsPathRooted($value))
    {
        throw ($Name + ' must be an absolute path.')
    }
    $fullPath = Assert-RegularFile -Path $value -Description $Description
    if (-not [string]::Equals(
            [System.IO.Path]::GetFileName($fullPath),
            $ExpectedFileName,
            [System.StringComparison]::OrdinalIgnoreCase))
    {
        throw ($Name + ' does not identify the expected executable file name.')
    }
    return $fullPath
}

function Test-StrictPathWithinDirectory
{
    param(
        [Parameter(Mandatory = $true)][string]$Path,
        [Parameter(Mandatory = $true)][string]$Directory
    )

    $separator = [System.IO.Path]::DirectorySeparatorChar
    $parentPath = [System.IO.Path]::GetFullPath($Directory).TrimEnd('\', '/')
    $childPath = [System.IO.Path]::GetFullPath($Path).TrimEnd('\', '/')
    return $childPath.StartsWith(
        $parentPath + $separator,
        [System.StringComparison]::OrdinalIgnoreCase)
}

function Get-RequiredTemporaryRoot
{
    param([Parameter(Mandatory = $true)][string]$Name)

    $value = Get-RequiredEnvironmentValue -Name $Name
    if (-not [System.IO.Path]::IsPathRooted($value))
    {
        throw ($Name + ' must be an absolute path.')
    }
    $fullPath = [System.IO.Path]::GetFullPath($value).TrimEnd('\', '/')
    $userTemporaryRoot = [System.IO.Path]::GetFullPath(
        [System.IO.Path]::GetTempPath()).TrimEnd('\', '/')
    if (-not (Test-StrictPathWithinDirectory `
            -Path $fullPath `
            -Directory $userTemporaryRoot))
    {
        throw ($Name + ' must be a strict descendant of the current user temporary directory.')
    }
    return Assert-PhysicalDirectory -Path $fullPath -Description $Name
}

function Get-RequiredOwnedDirectory
{
    param(
        [Parameter(Mandatory = $true)][string]$Name,
        [Parameter(Mandatory = $true)][string]$Root
    )

    $value = Get-RequiredEnvironmentValue -Name $Name
    if (-not [System.IO.Path]::IsPathRooted($value))
    {
        throw ($Name + ' must be an absolute path.')
    }
    $fullPath = [System.IO.Path]::GetFullPath($value).TrimEnd('\', '/')
    if (-not (Test-StrictPathWithinDirectory -Path $fullPath -Directory $Root))
    {
        throw ($Name + ' must be a strict descendant of its approved temporary root.')
    }
    return Assert-PhysicalDirectory -Path $fullPath -Description $Name
}

function Get-RequiredOwnedFile
{
    param(
        [Parameter(Mandatory = $true)][string]$Name,
        [Parameter(Mandatory = $true)][string]$Root,
        [Parameter(Mandatory = $true)][string]$Description
    )

    $value = Get-RequiredEnvironmentValue -Name $Name
    if (-not [System.IO.Path]::IsPathRooted($value))
    {
        throw ($Name + ' must be an absolute path.')
    }
    $fullPath = [System.IO.Path]::GetFullPath($value)
    if (-not (Test-StrictPathWithinDirectory -Path $fullPath -Directory $Root))
    {
        throw ($Name + ' must be a strict descendant of its approved temporary root.')
    }
    return Assert-RegularFile -Path $fullPath -Description $Description
}

function Assert-WrapperNonceMarker
{
    param(
        [Parameter(Mandatory = $true)][string]$Root,
        [Parameter(Mandatory = $true)][string]$Nonce,
        [Parameter(Mandatory = $true)][string]$Description
    )

    $markerPath = Join-Path $Root '.lab-smoke-wrapper-owner'
    $markerPath = Assert-RegularFile -Path $markerPath -Description ($Description + ' nonce marker')
    $markerContent = [System.IO.File]::ReadAllText($markerPath)
    if (-not [string]::Equals(
            $markerContent,
            $Nonce,
            [System.StringComparison]::Ordinal))
    {
        throw ($Description + ' nonce marker does not match this wrapper invocation.')
    }
}

function Get-RequiredMySqlConfigValue
{
    param(
        [Parameter(Mandatory = $true)][string[]]$Lines,
        [Parameter(Mandatory = $true)][string]$Name
    )

    $section = ''
    $values = New-Object System.Collections.Generic.List[string]
    foreach ($line in $Lines)
    {
        $trimmed = $line.Trim()
        if ([string]::IsNullOrWhiteSpace($trimmed) -or
            $trimmed.StartsWith('#') -or $trimmed.StartsWith(';'))
        {
            continue
        }
        $sectionMatch = [regex]::Match($trimmed, '^\[(?<section>[^\]]+)\]\s*$')
        if ($sectionMatch.Success)
        {
            $section = $sectionMatch.Groups['section'].Value
            continue
        }
        if (-not [string]::Equals(
                $section,
                'mysqld',
                [System.StringComparison]::OrdinalIgnoreCase))
        {
            continue
        }
        $optionMatch = [regex]::Match(
            $trimmed,
            '^(?<name>[A-Za-z0-9_-]+)(?:\s*=\s*|\s+)(?<value>.*)$')
        if ($optionMatch.Success -and [string]::Equals(
                $optionMatch.Groups['name'].Value,
                $Name,
                [System.StringComparison]::OrdinalIgnoreCase))
        {
            $values.Add($optionMatch.Groups['value'].Value.Trim()) | Out-Null
        }
    }
    if ($values.Count -ne 1 -or [string]::IsNullOrWhiteSpace($values[0]))
    {
        throw ('The wrapper-owned MySQL config must contain exactly one mysqld ' + $Name + ' option.')
    }
    return $values[0]
}

function ConvertFrom-OptionalDoubleQuotedPath
{
    param(
        [Parameter(Mandatory = $true)][string]$Value,
        [Parameter(Mandatory = $true)][string]$Description
    )

    $result = $Value
    $startsWithQuote = $result.StartsWith('"', [System.StringComparison]::Ordinal)
    $endsWithQuote = $result.EndsWith('"', [System.StringComparison]::Ordinal)
    if ($startsWithQuote -or $endsWithQuote)
    {
        if (-not ($startsWithQuote -and $endsWithQuote) -or $result.Length -le 2)
        {
            throw ($Description + ' uses invalid quoting.')
        }
        $result = $result.Substring(1, $result.Length - 2)
    }
    if ($result.Contains('"') -or -not [System.IO.Path]::IsPathRooted($result))
    {
        throw ($Description + ' must be one absolute path.')
    }
    return [System.IO.Path]::GetFullPath($result).TrimEnd('\', '/')
}

function Assert-MySqlWrapperOwnership
{
    param(
        [Parameter(Mandatory = $true)]$Identity,
        [Parameter(Mandatory = $true)][string]$ExpectedDataDirectory,
        [Parameter(Mandatory = $true)][string]$ExpectedConfigPath,
        [Parameter(Mandatory = $true)][int]$ExpectedPort
    )

    $arguments = @(ConvertFrom-NativeCommandLine -CommandLine ([string]$Identity.CommandLine))
    $defaultsFileValue = Get-RequiredNativeOptionValue `
        -Arguments $arguments `
        -Name '--defaults-file' `
        -Description 'isolated MySQL --defaults-file'
    if (-not [System.IO.Path]::IsPathRooted($defaultsFileValue) -or
        -not [string]::Equals(
            [System.IO.Path]::GetFullPath($defaultsFileValue),
            [System.IO.Path]::GetFullPath($ExpectedConfigPath),
            [System.StringComparison]::OrdinalIgnoreCase))
    {
        throw 'The isolated MySQL command line does not use the wrapper-owned config file.'
    }
    $dataDirectoryValue = Get-RequiredNativeOptionValue `
        -Arguments $arguments `
        -Name '--datadir' `
        -Description 'isolated MySQL --datadir'
    if (-not [System.IO.Path]::IsPathRooted($dataDirectoryValue) -or
        -not [string]::Equals(
            [System.IO.Path]::GetFullPath($dataDirectoryValue).TrimEnd('\', '/'),
            [System.IO.Path]::GetFullPath($ExpectedDataDirectory).TrimEnd('\', '/'),
            [System.StringComparison]::OrdinalIgnoreCase))
    {
        throw 'The isolated MySQL command line does not use the wrapper-owned data directory.'
    }

    $portValue = Get-RequiredNativeOptionValue `
        -Arguments $arguments `
        -Name '--port' `
        -Description 'isolated MySQL --port'
    if ($portValue -cne $ExpectedPort.ToString(
            [System.Globalization.CultureInfo]::InvariantCulture))
    {
        throw 'The isolated MySQL command line does not use the approved dynamic port.'
    }

    $bindAddressValue = Get-RequiredNativeOptionValue `
        -Arguments $arguments `
        -Name '--bind-address' `
        -Description 'isolated MySQL --bind-address'
    if ($bindAddressValue -cne '127.0.0.1')
    {
        throw 'The isolated MySQL command line is not bound exclusively to IPv4 loopback.'
    }

    $configLines = [System.IO.File]::ReadAllLines($ExpectedConfigPath)
    $includeLines = @($configLines | Where-Object {
            $_.Trim() -match '^!(?:include|includedir)(?:\s|$)'
        })
    if ($includeLines.Count -ne 0)
    {
        throw 'The wrapper-owned MySQL config must not include external configuration files.'
    }
    $configDataDirectoryValue = ConvertFrom-OptionalDoubleQuotedPath `
        -Value (Get-RequiredMySqlConfigValue -Lines $configLines -Name 'datadir') `
        -Description 'wrapper-owned MySQL config datadir'
    if (-not [string]::Equals(
            $configDataDirectoryValue,
            [System.IO.Path]::GetFullPath($ExpectedDataDirectory).TrimEnd('\', '/'),
            [System.StringComparison]::OrdinalIgnoreCase))
    {
        throw 'The wrapper-owned MySQL config does not use the approved data directory.'
    }
    if ((Get-RequiredMySqlConfigValue -Lines $configLines -Name 'port') -cne
        $ExpectedPort.ToString([System.Globalization.CultureInfo]::InvariantCulture))
    {
        throw 'The wrapper-owned MySQL config does not use the approved dynamic port.'
    }
    if ((Get-RequiredMySqlConfigValue -Lines $configLines -Name 'bind-address') -cne
        '127.0.0.1')
    {
        throw 'The wrapper-owned MySQL config is not bound exclusively to IPv4 loopback.'
    }
    if ((Get-RequiredMySqlConfigValue -Lines $configLines -Name 'mysqlx') -cne '0')
    {
        throw 'The wrapper-owned MySQL config must disable MySQL X Protocol.'
    }

    $processListeners = @(Get-NetTCPConnection `
            -State Listen `
            -OwningProcess ([int]$Identity.ProcessId) `
            -ErrorAction Stop)
    if ($processListeners.Count -ne 1 -or
        [string]$processListeners[0].LocalAddress -ne '127.0.0.1' -or
        [int]$processListeners[0].LocalPort -ne $ExpectedPort)
    {
        throw 'The isolated MySQL process owns an unexpected TCP listener.'
    }
}

function Get-RequiredMemuraiDirective
{
    param(
        [Parameter(Mandatory = $true)][string[]]$Lines,
        [Parameter(Mandatory = $true)][string]$Name
    )

    $values = New-Object System.Collections.Generic.List[string]
    foreach ($line in $Lines)
    {
        $trimmed = $line.Trim()
        if ([string]::IsNullOrWhiteSpace($trimmed) -or $trimmed.StartsWith('#'))
        {
            continue
        }
        $match = [regex]::Match($trimmed, '^(?<name>\S+)(?:\s+(?<value>.*))?$')
        if ($match.Success -and [string]::Equals(
                $match.Groups['name'].Value,
                $Name,
                [System.StringComparison]::OrdinalIgnoreCase))
        {
            $values.Add($match.Groups['value'].Value.Trim()) | Out-Null
        }
    }
    if ($values.Count -ne 1 -or [string]::IsNullOrWhiteSpace($values[0]))
    {
        throw ('The wrapper-owned Memurai config must contain exactly one ' + $Name + ' directive.')
    }
    return $values[0]
}

function Assert-MemuraiWrapperOwnership
{
    param(
        [Parameter(Mandatory = $true)]$Identity,
        [Parameter(Mandatory = $true)][string]$ExpectedConfigPath,
        [Parameter(Mandatory = $true)][string]$ExpectedRoot,
        [Parameter(Mandatory = $true)][int]$ExpectedPort
    )

    $arguments = @(ConvertFrom-NativeCommandLine -CommandLine ([string]$Identity.CommandLine))
    if ($arguments.Count -ne 2 -or
        -not [System.IO.Path]::IsPathRooted([string]$arguments[1]) -or
        -not [string]::Equals(
            [System.IO.Path]::GetFullPath([string]$arguments[1]),
            [System.IO.Path]::GetFullPath($ExpectedConfigPath),
            [System.StringComparison]::OrdinalIgnoreCase))
    {
        throw 'The isolated Memurai command line must reference only the wrapper-owned config file.'
    }

    $configLines = [System.IO.File]::ReadAllLines($ExpectedConfigPath)
    $includeLines = @($configLines | Where-Object {
            $_.Trim() -match '^include(?:\s|$)'
        })
    if ($includeLines.Count -ne 0)
    {
        throw 'The wrapper-owned Memurai config must not include external configuration files.'
    }
    $portValue = Get-RequiredMemuraiDirective -Lines $configLines -Name 'port'
    if ($portValue -cne $ExpectedPort.ToString(
            [System.Globalization.CultureInfo]::InvariantCulture))
    {
        throw 'The wrapper-owned Memurai config does not use the approved dynamic port.'
    }
    $bindAddressValue = Get-RequiredMemuraiDirective -Lines $configLines -Name 'bind'
    if ($bindAddressValue -cne '127.0.0.1')
    {
        throw 'The wrapper-owned Memurai config is not bound exclusively to IPv4 loopback.'
    }

    $memuraiDataDirectoryValue = Get-RequiredMemuraiDirective -Lines $configLines -Name 'dir'
    $startsWithQuote = $memuraiDataDirectoryValue.StartsWith(
        '"',
        [System.StringComparison]::Ordinal)
    $endsWithQuote = $memuraiDataDirectoryValue.EndsWith(
        '"',
        [System.StringComparison]::Ordinal)
    if ($startsWithQuote -or $endsWithQuote)
    {
        if (-not ($startsWithQuote -and $endsWithQuote) -or
            $memuraiDataDirectoryValue.Length -le 2)
        {
            throw 'The wrapper-owned Memurai data directory uses invalid quoting.'
        }
        $memuraiDataDirectoryValue = $memuraiDataDirectoryValue.Substring(
            1,
            $memuraiDataDirectoryValue.Length - 2)
    }
    if ($memuraiDataDirectoryValue.Contains('"') -or
        -not [System.IO.Path]::IsPathRooted($memuraiDataDirectoryValue) -or
        -not (Test-StrictPathWithinDirectory `
            -Path $memuraiDataDirectoryValue `
            -Directory $ExpectedRoot))
    {
        throw 'The wrapper-owned Memurai data directory is outside its approved temporary root.'
    }
    $null = Assert-PhysicalDirectory `
        -Path $memuraiDataDirectoryValue `
        -Description 'wrapper-owned Memurai data directory'

    if ((Get-RequiredMemuraiDirective -Lines $configLines -Name 'appendonly') -cne 'no')
    {
        throw 'The wrapper-owned Memurai config must disable append-only persistence.'
    }
    if ((Get-RequiredMemuraiDirective -Lines $configLines -Name 'save') -cne '""')
    {
        throw 'The wrapper-owned Memurai config must disable snapshot persistence.'
    }
    if ((Get-RequiredMemuraiDirective -Lines $configLines -Name 'protected-mode') -cne 'yes')
    {
        throw 'The wrapper-owned Memurai config must enable protected mode.'
    }
    $requirePassValue = Get-RequiredMemuraiDirective -Lines $configLines -Name 'requirepass'
    if ($requirePassValue -ceq '""' -or $requirePassValue -ceq "''")
    {
        throw 'The wrapper-owned Memurai config must use a non-empty password.'
    }
}

function New-VerifiedRunDirectory
{
    param([Parameter(Mandatory = $true)][string]$Path)

    $fullPath = [System.IO.Path]::GetFullPath($Path).TrimEnd('\')
    $approvedParent = [System.IO.Path]::GetFullPath(
        (Join-Path $repoRoot 'target\smoke-foundation')).TrimEnd('\')
    if (-not [string]::Equals(
            [System.IO.Path]::GetDirectoryName($fullPath),
            $approvedParent,
            [System.StringComparison]::OrdinalIgnoreCase) -or
        [System.IO.Path]::GetFileName($fullPath) -notmatch '^[a-f0-9]{32}$')
    {
        throw 'The smoke run directory is outside its approved target root.'
    }
    if (Test-Path -LiteralPath $fullPath)
    {
        throw 'The generated smoke run directory unexpectedly exists.'
    }

    $root = [System.IO.Path]::GetPathRoot($fullPath)
    $current = $root
    $separators = [char[]]@(
        [System.IO.Path]::DirectorySeparatorChar,
        [System.IO.Path]::AltDirectorySeparatorChar)
    foreach ($segment in $fullPath.Substring($root.Length).Split(
            $separators,
            [System.StringSplitOptions]::RemoveEmptyEntries))
    {
        $current = Join-Path $current $segment
        if (-not (Test-Path -LiteralPath $current))
        {
            [System.IO.Directory]::CreateDirectory($current) | Out-Null
        }
        $item = Get-Item -LiteralPath $current -Force
        if (($item.Attributes -band [System.IO.FileAttributes]::ReparsePoint) -ne 0 -or
            -not $item.PSIsContainer)
        {
            throw 'The smoke run directory must not contain or traverse a reparse point.'
        }
    }
    return Assert-PhysicalDirectory -Path $fullPath -Description 'smoke run directory'
}

function Remove-VerifiedRunDirectory
{
    param([Parameter(Mandatory = $true)][string]$Path)

    $fullPath = [System.IO.Path]::GetFullPath($Path).TrimEnd('\')
    $approvedParent = [System.IO.Path]::GetFullPath(
        (Join-Path $repoRoot 'target\smoke-foundation')).TrimEnd('\')
    if (-not [string]::Equals(
            [System.IO.Path]::GetDirectoryName($fullPath),
            $approvedParent,
            [System.StringComparison]::OrdinalIgnoreCase) -or
        [System.IO.Path]::GetFileName($fullPath) -notmatch '^[a-f0-9]{32}$')
    {
        throw 'Refusing to remove an unapproved smoke run directory.'
    }
    if (-not (Test-Path -LiteralPath $fullPath))
    {
        return
    }
    $null = Assert-PhysicalDirectory -Path $fullPath -Description 'smoke cleanup directory'
    Remove-Item -LiteralPath $fullPath -Recurse -Force -ErrorAction Stop
    if (Test-Path -LiteralPath $fullPath)
    {
        throw 'The successful smoke run directory was not removed.'
    }
}

function Resolve-MySqlClient
{
    return Assert-RegularFile -Path $mysqlClientPath -Description 'mysql client'
}

function Invoke-NativeCapture
{
    param(
        [Parameter(Mandatory = $true)][string]$FilePath,
        [Parameter(Mandatory = $true)][string]$Arguments,
        [Parameter(Mandatory = $true)][hashtable]$Environment,
        [Parameter(Mandatory = $true)][string]$Description,
        [string[]]$EnvironmentPrefixes = @(),
        [switch]$IncludeStandardError,
        [ValidateRange(1000, 120000)][int]$TimeoutMilliseconds = 15000
    )

    $startInfo = New-Object System.Diagnostics.ProcessStartInfo
    $startInfo.FileName = $FilePath
    $startInfo.Arguments = $Arguments
    $startInfo.WorkingDirectory = $repoRoot
    $startInfo.UseShellExecute = $false
    $startInfo.CreateNoWindow = $true
    $startInfo.WindowStyle = [System.Diagnostics.ProcessWindowStyle]::Hidden
    $startInfo.RedirectStandardOutput = $true
    $startInfo.RedirectStandardError = $true

    foreach ($name in @($startInfo.EnvironmentVariables.Keys))
    {
        $actualName = [string]$name
        foreach ($prefix in $EnvironmentPrefixes)
        {
            if ($actualName.StartsWith($prefix, [System.StringComparison]::OrdinalIgnoreCase))
            {
                $startInfo.EnvironmentVariables.Remove($actualName)
                break
            }
        }
    }
    foreach ($entry in $Environment.GetEnumerator())
    {
        $startInfo.EnvironmentVariables[[string]$entry.Key] = [string]$entry.Value
    }

    $process = New-Object System.Diagnostics.Process
    $process.StartInfo = $startInfo
    try
    {
        if (-not $process.Start())
        {
            throw ($Description + ' could not be started.')
        }
        $stdoutTask = $process.StandardOutput.ReadToEndAsync()
        $stderrTask = $process.StandardError.ReadToEndAsync()
        if (-not $process.WaitForExit($TimeoutMilliseconds))
        {
            try
            {
                $process.Refresh()
                if (-not $process.HasExited)
                {
                    $process.Kill()
                }
            }
            catch
            {
                throw ($Description + ' timed out and its owned child could not be stopped.')
            }
            if (-not $process.WaitForExit(5000))
            {
                throw ($Description + ' timed out and its owned child did not exit.')
            }
            throw ($Description + ' timed out.')
        }
        $stdout = $stdoutTask.GetAwaiter().GetResult()
        $stderr = $stderrTask.GetAwaiter().GetResult()
        if ($process.ExitCode -ne 0)
        {
            throw ($Description + ' failed.')
        }
        if ($IncludeStandardError)
        {
            return (($stdout + [Environment]::NewLine + $stderr).Trim())
        }
        return $stdout.Trim()
    }
    finally
    {
        $process.Dispose()
    }
}

function Invoke-MySqlScalar
{
    param(
        [Parameter(Mandatory = $true)][string]$Sql,
        [switch]$ServerScope
    )

    if ($Sql.Contains('"'))
    {
        throw 'Internal smoke SQL must not contain a double quote.'
    }
    if ($null -eq $mysqlRuntimeIdentity)
    {
        throw 'The isolated MySQL runtime identity was not established.'
    }
    Assert-IsolatedRuntimeIdentity -Identity $mysqlRuntimeIdentity
    $arguments = '--no-defaults --protocol=TCP --host=' + $databaseHost + ' --port=' +
        $adminPort + ' --user=' + $adminUsername
    if (-not $ServerScope)
    {
        $arguments += ' --database=' + $databaseName
    }
    $arguments += ' --batch --skip-column-names --execute="' + $Sql + '"'
    return Invoke-NativeCapture -FilePath $mysqlPath -Arguments $arguments -Environment @{
        MYSQL_PWD = $adminPassword
    } -EnvironmentPrefixes @('MYSQL_') -Description 'isolated MySQL query'
}

function Invoke-RedisCommand
{
    param([Parameter(Mandatory = $true)][string]$Command)

    if ($Command -notmatch '^(PING|FLUSHDB|DEL sys_config:sys\.account\.captchaEnabled)$')
    {
        throw 'Refusing an unexpected Redis command.'
    }
    if ($null -eq $memuraiRuntimeIdentity)
    {
        throw 'The isolated Memurai runtime identity was not established.'
    }
    Assert-IsolatedRuntimeIdentity -Identity $memuraiRuntimeIdentity
    $arguments = '--no-auth-warning -h ' + $redisHost + ' -p ' + $redisPort +
        ' -n ' + $redisDatabase + ' ' + $Command
    return Invoke-NativeCapture -FilePath $redisCliPath -Arguments $arguments -Environment @{
        REDISCLI_AUTH = $redisPassword
    } -EnvironmentPrefixes @('REDISCLI_') -Description 'isolated Redis command'
}

function Convert-JsonBody
{
    param(
        [Parameter(Mandatory = $true)][string]$Body,
        [Parameter(Mandatory = $true)][string]$Description
    )

    if ([string]::IsNullOrWhiteSpace($Body))
    {
        throw ($Description + ' returned an empty response body.')
    }
    try
    {
        return $Body | ConvertFrom-Json -ErrorAction Stop
    }
    catch
    {
        throw ($Description + ' did not return valid JSON.')
    }
}

function Invoke-ApiRequest
{
    param(
        [Parameter(Mandatory = $true)][string]$Method,
        [Parameter(Mandatory = $true)][string]$Path,
        [object]$Body = $null,
        [string]$Token = '',
        [string]$TraceId = ''
    )

    $uri = [System.Uri]::new(('http://127.0.0.1:' + $appPort + $Path))
    $httpMethod = [System.Net.Http.HttpMethod]::new($Method)
    $request = [System.Net.Http.HttpRequestMessage]::new($httpMethod, $uri)
    try
    {
        if (-not [string]::IsNullOrWhiteSpace($Token))
        {
            $null = $request.Headers.TryAddWithoutValidation('Authorization', 'Bearer ' + $Token)
        }
        if (-not [string]::IsNullOrWhiteSpace($TraceId))
        {
            $null = $request.Headers.TryAddWithoutValidation('X-Trace-Id', $TraceId)
        }
        if ($null -ne $Body)
        {
            $json = $Body | ConvertTo-Json -Compress -Depth 10
            $request.Content = [System.Net.Http.StringContent]::new(
                $json,
                [System.Text.Encoding]::UTF8,
                'application/json')
        }

        $response = $httpClient.SendAsync($request).GetAwaiter().GetResult()
        try
        {
            $responseBody = $response.Content.ReadAsStringAsync().GetAwaiter().GetResult()
            $responseTraceId = ''
            $traceValues = $null
            if ($response.Headers.TryGetValues('X-Trace-Id', [ref]$traceValues))
            {
                $responseTraceId = [string](@($traceValues)[0])
            }
            return [pscustomobject]@{
                StatusCode = [int]$response.StatusCode
                Body = $responseBody
                TraceId = $responseTraceId
                ContentType = [string]$response.Content.Headers.ContentType
            }
        }
        finally
        {
            $response.Dispose()
        }
    }
    finally
    {
        $request.Dispose()
    }
}

function Assert-Status
{
    param(
        [Parameter(Mandatory = $true)]$Response,
        [Parameter(Mandatory = $true)][int]$Expected,
        [Parameter(Mandatory = $true)][string]$Description
    )

    if ($Response.StatusCode -ne $Expected)
    {
        throw ($Description + ' returned an unexpected HTTP status.')
    }
}

function Assert-Trace
{
    param(
        [Parameter(Mandatory = $true)]$Response,
        [Parameter(Mandatory = $true)][string]$Expected,
        [Parameter(Mandatory = $true)][string]$Description
    )

    if ($Response.TraceId -ne $Expected)
    {
        throw ($Description + ' did not preserve the canonical trace identifier.')
    }
}

function Assert-ExactStrings
{
    param(
        [object[]]$Actual,
        [string[]]$Expected,
        [Parameter(Mandatory = $true)][string]$Description
    )

    $actualStrings = @($Actual | ForEach-Object { [string]$_ } | Sort-Object)
    $expectedStrings = @($Expected | ForEach-Object { [string]$_ } | Sort-Object)
    if ($actualStrings.Count -ne $expectedStrings.Count -or
        @(Compare-Object `
                -ReferenceObject $expectedStrings `
                -DifferenceObject $actualStrings `
                -CaseSensitive).Count -ne 0)
    {
        throw ($Description + ' did not match the exact expected set.')
    }
}

function Start-IsolatedApplication
{
    param([Parameter(Mandatory = $true)][hashtable]$ChildEnvironment)

    $savedEnvironment = @{}
    foreach ($name in @([System.Environment]::GetEnvironmentVariables().Keys))
    {
        $actualName = [string]$name
        if (Test-IsolatedEnvironmentName -Name $actualName)
        {
            $savedEnvironment[$actualName] = [System.Environment]::GetEnvironmentVariable($actualName)
            Remove-Item -LiteralPath ('Env:' + $actualName) -ErrorAction SilentlyContinue
        }
    }

    try
    {
        foreach ($entry in $ChildEnvironment.GetEnumerator())
        {
            [System.Environment]::SetEnvironmentVariable(
                [string]$entry.Key,
                [string]$entry.Value,
                [System.EnvironmentVariableTarget]::Process)
        }

        $logbackUri = ([System.Uri]::new($logbackPath)).AbsoluteUri
        $arguments = @(
            '-jar',
            ('"' + $jarPath + '"'),
            '--server.address=127.0.0.1',
            ('--server.port=' + $appPort),
            '--spring.config.location=classpath:/',
            '--spring.profiles.active=druid',
            '--spring.devtools.restart.enabled=false',
            '--spring.quartz.auto-startup=false',
            '--spring.main.banner-mode=off',
            '--spring.output.ansi.enabled=never',
            ('"--logging.config=' + $logbackUri + '"'))
        return Start-Process -FilePath $javaPath -ArgumentList $arguments -WorkingDirectory $repoRoot -WindowStyle Hidden -PassThru -RedirectStandardOutput $stdoutPath -RedirectStandardError $stderrPath
    }
    finally
    {
        foreach ($name in @([System.Environment]::GetEnvironmentVariables().Keys))
        {
            $actualName = [string]$name
            if (Test-IsolatedEnvironmentName -Name $actualName)
            {
                Remove-Item -LiteralPath ('Env:' + $actualName) -ErrorAction SilentlyContinue
            }
        }
        foreach ($entry in $savedEnvironment.GetEnumerator())
        {
            [System.Environment]::SetEnvironmentVariable(
                [string]$entry.Key,
                [string]$entry.Value,
                [System.EnvironmentVariableTarget]::Process)
        }
    }
}

function Assert-ApplicationListenerOwnership
{
    param(
        [Parameter(Mandatory = $true)][System.Diagnostics.Process]$Process,
        [Parameter(Mandatory = $true)][long]$ExpectedStartTicks
    )

    $Process.Refresh()
    if ($Process.HasExited -or
        $Process.StartTime.ToUniversalTime().Ticks -ne $ExpectedStartTicks -or
        -not [string]::Equals(
            [System.IO.Path]::GetFullPath($Process.MainModule.FileName),
            $javaPath,
            [System.StringComparison]::OrdinalIgnoreCase))
    {
        throw 'The smoke application identity changed before listener validation.'
    }
    $listeners = @(Get-NetTCPConnection -State Listen -LocalPort $appPort -ErrorAction Stop)
    if ($listeners.Count -ne 1 -or
        [string]$listeners[0].LocalAddress -ne '127.0.0.1' -or
        [int]$listeners[0].OwningProcess -ne $Process.Id)
    {
        throw 'The smoke HTTP listener is not owned exclusively by the launched Java process.'
    }
}

function Stop-OwnedApplication
{
    param(
        [Parameter(Mandatory = $true)][System.Diagnostics.Process]$Process,
        [Parameter(Mandatory = $true)][long]$ExpectedStartTicks
    )

    $Process.Refresh()
    if ($Process.HasExited)
    {
        return
    }

    $currentPath = [System.IO.Path]::GetFullPath($Process.MainModule.FileName)
    $currentStartTicks = $Process.StartTime.ToUniversalTime().Ticks
    if (-not [string]::Equals(
            $currentPath,
            $javaPath,
            [System.StringComparison]::OrdinalIgnoreCase) -or
        $currentStartTicks -ne $ExpectedStartTicks)
    {
        throw 'Refusing to stop a process whose Java identity changed.'
    }

    $cimProcess = Get-CimInstance Win32_Process -Filter ('ProcessId=' + $Process.Id) -ErrorAction Stop
    if ($null -eq $cimProcess -or [string]::IsNullOrWhiteSpace($cimProcess.CommandLine) -or
        $cimProcess.CommandLine.IndexOf($jarPath, [System.StringComparison]::OrdinalIgnoreCase) -lt 0 -or
        $cimProcess.CommandLine.IndexOf(
            '--server.port=18080',
            [System.StringComparison]::OrdinalIgnoreCase) -lt 0)
    {
        throw 'Refusing to stop a process whose command line is not the owned smoke application.'
    }

    $Process.Kill()
    if (-not $Process.WaitForExit(15000))
    {
        throw 'The owned smoke application did not stop within the cleanup deadline.'
    }
}

$wrapperMarker = Get-RequiredEnvironmentValue -Name 'LAB_TEST_WRAPPER_ACTIVE'
if ($wrapperMarker -cne 'true')
{
    throw 'LAB_TEST_WRAPPER_ACTIVE must be exactly true.'
}
$wrapperNonce = Get-RequiredEnvironmentValue -Name 'LAB_TEST_WRAPPER_NONCE'
if ($wrapperNonce -cnotmatch '^[A-Za-z0-9_-]{32,128}$')
{
    throw 'LAB_TEST_WRAPPER_NONCE must be a 32 through 128 character opaque identifier.'
}
$mysqlTemporaryRoot = Get-RequiredTemporaryRoot -Name 'LAB_TEST_MYSQL_ROOT'
$mysqlDataDirectory = Get-RequiredOwnedDirectory `
    -Name 'LAB_TEST_MYSQL_DATADIR' `
    -Root $mysqlTemporaryRoot
$mysqlConfigPath = Get-RequiredOwnedFile `
    -Name 'LAB_TEST_MYSQL_CONFIG' `
    -Root $mysqlTemporaryRoot `
    -Description 'wrapper-owned MySQL config'
$memuraiTemporaryRoot = Get-RequiredTemporaryRoot -Name 'LAB_TEST_MEMURAI_ROOT'
$memuraiConfigPath = Get-RequiredOwnedFile `
    -Name 'LAB_TEST_MEMURAI_CONFIG' `
    -Root $memuraiTemporaryRoot `
    -Description 'wrapper-owned Memurai config'
Assert-WrapperNonceMarker `
    -Root $mysqlTemporaryRoot `
    -Nonce $wrapperNonce `
    -Description 'MySQL temporary root'
Assert-WrapperNonceMarker `
    -Root $memuraiTemporaryRoot `
    -Nonce $wrapperNonce `
    -Description 'Memurai temporary root'

$adminHost = Get-RequiredEnvironmentValue -Name 'LAB_TEST_ADMIN_HOST'
if ($adminHost -notin @('localhost', '127.0.0.1'))
{
    throw 'LAB_TEST_ADMIN_HOST must be localhost or 127.0.0.1.'
}
$databaseHost = if ($adminHost -eq 'localhost') { '127.0.0.1' } else { $adminHost }
$adminPort = Get-RequiredPort -Name 'LAB_TEST_ADMIN_PORT' -Value (
    Get-RequiredEnvironmentValue -Name 'LAB_TEST_ADMIN_PORT')
$mysqlProcessId = Get-RequiredProcessId -Name 'LAB_TEST_MYSQL_PID' -Value (
    Get-RequiredEnvironmentValue -Name 'LAB_TEST_MYSQL_PID')
if ($adminPort -eq 3306)
{
    throw 'LAB_TEST_ADMIN_PORT must use a dynamic non-3306 isolated port.'
}
$adminUsername = Get-RequiredEnvironmentValue -Name 'LAB_TEST_ADMIN_USERNAME'
$adminPassword = Get-RequiredEnvironmentValue -Name 'LAB_TEST_ADMIN_PASSWORD'
$databaseUsername = Get-RequiredEnvironmentValue -Name 'LAB_TEST_DB_USERNAME'
$databasePassword = Get-RequiredEnvironmentValue -Name 'LAB_TEST_DB_PASSWORD'
foreach ($username in @($adminUsername, $databaseUsername))
{
    if ($username -notmatch '^[A-Za-z0-9_.-]{1,64}$')
    {
        throw 'Database usernames must use only the approved portable character set.'
    }
}

$redisHost = Get-RequiredEnvironmentValue -Name 'LAB_REDIS_HOST'
if ($redisHost -notin @('localhost', '127.0.0.1'))
{
    throw 'LAB_REDIS_HOST must be localhost or 127.0.0.1.'
}
$redisPort = Get-RequiredPort -Name 'LAB_REDIS_PORT' -Value (
    Get-RequiredEnvironmentValue -Name 'LAB_REDIS_PORT')
$memuraiProcessId = Get-RequiredProcessId -Name 'LAB_TEST_MEMURAI_PID' -Value (
    Get-RequiredEnvironmentValue -Name 'LAB_TEST_MEMURAI_PID')
if ($redisPort -eq 6379)
{
    throw 'LAB_REDIS_PORT must use a dynamic non-6379 isolated port.'
}
if ($adminPort -eq $redisPort -or $adminPort -eq $appPort -or
    $redisPort -eq $appPort -or $mysqlProcessId -eq $memuraiProcessId)
{
    throw 'The isolated MySQL, Memurai, and application identities must be distinct.'
}
$redisPassword = Get-RequiredEnvironmentValue -Name 'LAB_REDIS_PASSWORD'
$redisDatabaseText = [System.Environment]::GetEnvironmentVariable('LAB_REDIS_DATABASE')
if ([string]::IsNullOrWhiteSpace($redisDatabaseText))
{
    $redisDatabaseText = '0'
}
$redisDatabase = 0
if (-not [int]::TryParse($redisDatabaseText, [ref]$redisDatabase) -or
    $redisDatabase -lt 0 -or $redisDatabase -gt 15)
{
    throw 'LAB_REDIS_DATABASE must be an integer from 0 through 15.'
}

$tokenSecret = Get-RequiredEnvironmentValue -Name 'LAB_TOKEN_SECRET'
if ($tokenSecret.Length -lt 64)
{
    throw 'LAB_TOKEN_SECRET must contain at least 64 characters for the smoke test.'
}

$accountDefinitions = @(
    [pscustomobject]@{
        Username = 'lab_student'
        Role = 'lab_student'
        PasswordName = 'LAB_DEMO_STUDENT_PASSWORD'
        ExpectedRoutes = @('/lab')
        RequireExactEmptyPermissions = $true
    },
    [pscustomobject]@{
        Username = 'lab_manager'
        Role = 'lab_manager'
        PasswordName = 'LAB_DEMO_MANAGER_PASSWORD'
        ExpectedRoutes = @('/lab')
        RequireExactEmptyPermissions = $true
    },
    [pscustomobject]@{
        Username = 'lab_safety_officer'
        Role = 'lab_safety_officer'
        PasswordName = 'LAB_DEMO_SAFETY_PASSWORD'
        ExpectedRoutes = @('/lab')
        RequireExactEmptyPermissions = $true
    },
    [pscustomobject]@{
        Username = 'lab_repair_worker'
        Role = 'lab_repair_worker'
        PasswordName = 'LAB_DEMO_REPAIR_PASSWORD'
        ExpectedRoutes = @('/lab')
        RequireExactEmptyPermissions = $true
    },
    [pscustomobject]@{
        Username = 'lab_system_admin'
        Role = 'lab_system_admin'
        PasswordName = 'LAB_DEMO_ADMIN_PASSWORD'
        ExpectedRoutes = @('/lab', '/monitor', '/system')
        RequireExactEmptyPermissions = $false
    })

$demoPasswords = @{}
foreach ($account in $accountDefinitions)
{
    $password = Get-RequiredEnvironmentValue -Name $account.PasswordName
    if ($password.Length -lt 5 -or $password.Length -gt 20)
    {
        throw ($account.PasswordName + ' must contain from 5 through 20 characters.')
    }
    $demoPasswords[$account.PasswordName] = $password
}

$javaHome = Get-RequiredPhysicalDirectory `
    -Name 'LAB_TEST_JAVA_HOME' `
    -Description 'wrapper-selected JDK 17 home'
$javaPath = Assert-RegularFile `
    -Path (Join-Path $javaHome 'bin\java.exe') `
    -Description 'JDK 17 java executable'
$jarPath = Assert-RegularFile -Path $jarPath -Description 'built application JAR'
$mysqlClientPath = Get-RequiredExecutablePath `
    -Name 'LAB_TEST_MYSQL_CLIENT_PATH' `
    -ExpectedFileName 'mysql.exe' `
    -Description 'wrapper-selected mysql client'
$mysqlPath = Resolve-MySqlClient
$mysqlServerPath = Get-RequiredExecutablePath `
    -Name 'LAB_TEST_MYSQL_SERVER_PATH' `
    -ExpectedFileName 'mysqld.exe' `
    -Description 'wrapper-selected isolated mysqld executable'
$memuraiServerPath = Get-RequiredExecutablePath `
    -Name 'LAB_TEST_MEMURAI_SERVER_PATH' `
    -ExpectedFileName 'memurai.exe' `
    -Description 'wrapper-selected isolated Memurai executable'
$redisCliPath = Get-RequiredExecutablePath `
    -Name 'LAB_TEST_MEMURAI_CLI_PATH' `
    -ExpectedFileName 'memurai-cli.exe' `
    -Description 'wrapper-selected Memurai CLI'

$mysqlRuntimeIdentity = Get-IsolatedRuntimeIdentity `
    -Port $adminPort `
    -ExpectedProcessId $mysqlProcessId `
    -ExpectedExecutablePath $mysqlServerPath `
    -Description 'isolated MySQL listener'
$memuraiRuntimeIdentity = Get-IsolatedRuntimeIdentity `
    -Port $redisPort `
    -ExpectedProcessId $memuraiProcessId `
    -ExpectedExecutablePath $memuraiServerPath `
    -Description 'isolated Memurai listener'
Assert-MySqlWrapperOwnership -Identity $mysqlRuntimeIdentity `
    -ExpectedDataDirectory $mysqlDataDirectory `
    -ExpectedConfigPath $mysqlConfigPath `
    -ExpectedPort $adminPort
Assert-MemuraiWrapperOwnership -Identity $memuraiRuntimeIdentity `
    -ExpectedConfigPath $memuraiConfigPath `
    -ExpectedRoot $memuraiTemporaryRoot `
    -ExpectedPort $redisPort

$javaVersionOutput = Invoke-NativeCapture `
    -FilePath $javaPath `
    -Arguments '-version' `
    -Environment @{} `
    -Description 'JDK 17 version check' `
    -IncludeStandardError `
    -TimeoutMilliseconds 15000
if ($javaVersionOutput -notmatch 'version "17\.')
{
    throw 'The smoke application must run on JDK 17.'
}

$occupied = @(Get-NetTCPConnection -State Listen -LocalPort $appPort -ErrorAction SilentlyContinue)
if ($occupied.Count -ne 0)
{
    throw ('Loopback smoke port ' + $appPort + ' is already occupied; no process was stopped.')
}

$redisPing = Invoke-RedisCommand -Command 'PING'
if ($redisPing -ne 'PONG')
{
    throw 'The configured loopback Redis service did not return PONG.'
}
Write-Pass -Name 'loopback Redis authentication and PING'

$resetResult = Invoke-MySqlScalar -ServerScope -Sql (
    'drop database if exists `lab_test_m1_smoke`; ' +
    'create database `lab_test_m1_smoke` character set utf8mb4 collate utf8mb4_unicode_ci; ' +
    'select 1;')
if ($resetResult -ne '1')
{
    throw 'The isolated smoke database reset failed.'
}
Write-Pass -Name 'fixed isolated smoke database reset'

$runRoot = New-VerifiedRunDirectory -Path $runRoot
[System.IO.Directory]::CreateDirectory($fileRoot) | Out-Null
$fileRoot = Assert-PhysicalDirectory -Path $fileRoot -Description 'smoke file root'
$logbackXml = @'
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
  <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
    <encoder>
      <pattern>%date{ISO8601} %-5level [%X{traceId:-}] %logger{36} - %msg%n</pattern>
    </encoder>
  </appender>
  <root level="INFO">
    <appender-ref ref="CONSOLE" />
  </root>
</configuration>
'@
[System.IO.File]::WriteAllText(
    $logbackPath,
    $logbackXml,
    [System.Text.UTF8Encoding]::new($false))

Add-Type -AssemblyName System.Net.Http
$handler = [System.Net.Http.HttpClientHandler]::new()
$handler.UseProxy = $false
$httpClient = [System.Net.Http.HttpClient]::new($handler, $true)
$httpClient.Timeout = [System.TimeSpan]::FromSeconds(10)

$childEnvironment = @{
    JAVA_HOME = $javaHome
    LAB_DB_URL = 'jdbc:mysql://' + $databaseHost + ':' + $adminPort + '/' + $databaseName +
        '?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai&allowMultiQueries=true'
    LAB_DB_USERNAME = $databaseUsername
    LAB_DB_PASSWORD = $databasePassword
    LAB_FILE_ROOT = $fileRoot
    LAB_REDIS_PASSWORD = $redisPassword
    LAB_TOKEN_SECRET = $tokenSecret
    LAB_DEMO_DATA_ENABLED = 'true'
    LAB_DEMO_STUDENT_PASSWORD = $demoPasswords['LAB_DEMO_STUDENT_PASSWORD']
    LAB_DEMO_MANAGER_PASSWORD = $demoPasswords['LAB_DEMO_MANAGER_PASSWORD']
    LAB_DEMO_SAFETY_PASSWORD = $demoPasswords['LAB_DEMO_SAFETY_PASSWORD']
    LAB_DEMO_REPAIR_PASSWORD = $demoPasswords['LAB_DEMO_REPAIR_PASSWORD']
    LAB_DEMO_ADMIN_PASSWORD = $demoPasswords['LAB_DEMO_ADMIN_PASSWORD']
    SPRING_CONFIG_LOCATION = 'classpath:/'
    SPRING_PROFILES_ACTIVE = 'druid'
    SPRING_DATA_REDIS_HOST = $redisHost
    SPRING_DATA_REDIS_PORT = [string]$redisPort
    SPRING_DATA_REDIS_DATABASE = [string]$redisDatabase
    SPRING_DATA_REDIS_PASSWORD = $redisPassword
    SPRING_DEVTOOLS_RESTART_ENABLED = 'false'
    SPRING_QUARTZ_AUTO_STARTUP = 'false'
    SERVER_ADDRESS = '127.0.0.1'
    SERVER_PORT = [string]$appPort
    LOGGING_LEVEL_COM_RUOYI = 'INFO'
    LOGGING_LEVEL_ORG_SPRINGFRAMEWORK = 'WARN'
}

$completed = $false
try
{
    $redisIsolationVerified = $true
    $initialRedisFlush = Invoke-RedisCommand -Command 'FLUSHDB'
    if ($initialRedisFlush -ne 'OK')
    {
        throw 'The isolated Redis database could not be cleared before smoke execution.'
    }
    Write-Pass -Name 'isolated Redis database initial cleanup'

    $appProcess = Start-IsolatedApplication -ChildEnvironment $childEnvironment
    $appProcess.Refresh()
    $appStartTicks = $appProcess.StartTime.ToUniversalTime().Ticks

    $deadline = [System.DateTime]::UtcNow.AddSeconds(120)
    $ready = $false
    while ([System.DateTime]::UtcNow -lt $deadline)
    {
        $appProcess.Refresh()
        if ($appProcess.HasExited)
        {
            throw 'The smoke application exited before it became ready.'
        }

        try
        {
            $history = Invoke-MySqlScalar -Sql (
                "select concat(count(*), ':', coalesce(sum(case when success = 1 then 1 else 0 end), 0), ':', coalesce(group_concat(concat(coalesce(version, 'NULL'), '=', success) order by installed_rank separator ','), '')) from flyway_schema_history;")
            $accounts = Invoke-MySqlScalar -Sql (
                "select count(*) from sys_user where user_name in ('lab_student','lab_manager','lab_safety_officer','lab_repair_worker','lab_system_admin') and status = '0' and del_flag = '0' and remark = 'LAB_DEMO_ACCOUNT_V1';")
            $document = Invoke-ApiRequest -Method 'GET' -Path '/doc.html'
            if ($history -eq '3:3:1=1,1.1=1,1.2=1' -and $accounts -eq '5' -and
                $document.StatusCode -eq 200)
            {
                $ready = $true
                break
            }
        }
        catch
        {
            $ready = $false
        }
        Start-Sleep -Milliseconds 500
    }
    if (-not $ready)
    {
        throw 'The smoke application did not reach the required Flyway/account/HTTP state in time.'
    }
    Assert-ApplicationListenerOwnership -Process $appProcess -ExpectedStartTicks $appStartTicks
    Write-Pass -Name 'application startup, Flyway 1/1.1/1.2, and five managed accounts'

    $captchaRows = Invoke-MySqlScalar -Sql (
        "update sys_config set config_value = 'false', update_by = 'smoke-foundation', update_time = now(3) where config_key = 'sys.account.captchaEnabled'; select row_count();")
    if ($captchaRows -ne '1')
    {
        throw 'The isolated captcha setting was not updated exactly once.'
    }
    $cacheDelete = Invoke-RedisCommand -Command 'DEL sys_config:sys.account.captchaEnabled'
    if ($cacheDelete -notmatch '^[01]$')
    {
        throw 'The isolated captcha cache key could not be invalidated.'
    }

    $docResponse = Invoke-ApiRequest -Method 'GET' -Path '/doc.html'
    Assert-Status -Response $docResponse -Expected 200 -Description 'Knife4j document'
    if ($docResponse.Body -notmatch '(?i)(knife4j|<html)')
    {
        throw 'Knife4j did not return an HTML document.'
    }
    $defaultOpenApi = Invoke-ApiRequest -Method 'GET' -Path '/v3/api-docs'
    Assert-Status -Response $defaultOpenApi -Expected 200 -Description 'default OpenAPI document'
    $null = Convert-JsonBody -Body $defaultOpenApi.Body -Description 'default OpenAPI document'
    $labOpenApi = Invoke-ApiRequest -Method 'GET' -Path '/v3/api-docs/lab'
    Assert-Status -Response $labOpenApi -Expected 200 -Description 'lab OpenAPI document'
    $labDocument = Convert-JsonBody -Body $labOpenApi.Body -Description 'lab OpenAPI document'
    $labPaths = @($labDocument.paths.PSObject.Properties.Name)
    Assert-ExactStrings -Actual $labPaths -Expected @('/lab/security-probe') -Description 'lab OpenAPI paths'
    Write-Pass -Name 'non-production Knife4j and lab OpenAPI publication'

    $anonymousTrace = [System.Guid]::NewGuid().ToString()
    $anonymousProbe = Invoke-ApiRequest -Method 'GET' -Path '/lab/security-probe' -TraceId $anonymousTrace
    Assert-Status -Response $anonymousProbe -Expected 401 -Description 'anonymous lab probe'
    Assert-Trace -Response $anonymousProbe -Expected $anonymousTrace -Description 'anonymous lab probe'
    $anonymousError = Convert-JsonBody -Body $anonymousProbe.Body -Description 'anonymous lab probe'
    if ($anonymousError.errorCode -ne 'UNAUTHENTICATED' -or
        $anonymousError.traceId -ne $anonymousTrace)
    {
        throw 'The anonymous lab probe did not use the unified unauthenticated contract.'
    }
    Write-Pass -Name 'anonymous lab endpoint rejection' -TraceId $anonymousTrace

    $approvedSystemAdminPermissions = @(
        'monitor:job:add',
        'monitor:job:changeStatus',
        'monitor:job:edit',
        'monitor:job:export',
        'monitor:job:list',
        'monitor:job:query',
        'monitor:job:remove',
        'monitor:logininfor:export',
        'monitor:logininfor:list',
        'monitor:logininfor:query',
        'monitor:logininfor:remove',
        'monitor:logininfor:unlock',
        'monitor:operlog:export',
        'monitor:operlog:list',
        'monitor:operlog:query',
        'monitor:operlog:remove',
        'system:config:add',
        'system:config:edit',
        'system:config:export',
        'system:config:list',
        'system:config:query',
        'system:config:remove',
        'system:dept:add',
        'system:dept:edit',
        'system:dept:list',
        'system:dept:query',
        'system:dept:remove',
        'system:dict:add',
        'system:dict:edit',
        'system:dict:export',
        'system:dict:list',
        'system:dict:query',
        'system:dict:remove',
        'system:menu:list',
        'system:menu:query',
        'system:role:export',
        'system:role:list',
        'system:role:query',
        'system:user:export',
        'system:user:list',
        'system:user:query')

    foreach ($account in $accountDefinitions)
    {
        $loginTrace = [System.Guid]::NewGuid().ToString()
        $login = Invoke-ApiRequest -Method 'POST' -Path '/login' -TraceId $loginTrace -Body @{
            username = $account.Username
            password = $demoPasswords[$account.PasswordName]
            code = ''
            uuid = ''
        }
        Assert-Status -Response $login -Expected 200 -Description ($account.Username + ' login')
        Assert-Trace -Response $login -Expected $loginTrace -Description ($account.Username + ' login')
        $loginBody = Convert-JsonBody -Body $login.Body -Description ($account.Username + ' login')
        if ([int]$loginBody.code -ne 200 -or [string]::IsNullOrWhiteSpace([string]$loginBody.token))
        {
            throw ($account.Username + ' did not receive a valid login token.')
        }
        $token = [string]$loginBody.token
        $tokens.Add($token) | Out-Null

        $infoTrace = [System.Guid]::NewGuid().ToString()
        $infoResponse = Invoke-ApiRequest -Method 'GET' -Path '/getInfo' -Token $token -TraceId $infoTrace
        Assert-Status -Response $infoResponse -Expected 200 -Description ($account.Username + ' getInfo')
        Assert-Trace -Response $infoResponse -Expected $infoTrace -Description ($account.Username + ' getInfo')
        $info = Convert-JsonBody -Body $infoResponse.Body -Description ($account.Username + ' getInfo')
        if ([int]$info.code -ne 200)
        {
            throw ($account.Username + ' getInfo did not succeed.')
        }
        Assert-ExactStrings -Actual @($info.roles) -Expected @($account.Role) -Description ($account.Username + ' roles')

        $actualPermissions = @($info.permissions | ForEach-Object { [string]$_ })
        if ([bool]$account.RequireExactEmptyPermissions)
        {
            Assert-ExactStrings `
                -Actual $actualPermissions `
                -Expected @() `
                -Description ($account.Username + ' permissions')
        }
        if ($account.Role -eq 'lab_system_admin')
        {
            Assert-ExactStrings `
                -Actual $actualPermissions `
                -Expected $approvedSystemAdminPermissions `
                -Description 'lab_system_admin permissions'
        }

        $routesTrace = [System.Guid]::NewGuid().ToString()
        $routesResponse = Invoke-ApiRequest -Method 'GET' -Path '/getRouters' -Token $token -TraceId $routesTrace
        Assert-Status -Response $routesResponse -Expected 200 -Description ($account.Username + ' getRouters')
        Assert-Trace -Response $routesResponse -Expected $routesTrace -Description ($account.Username + ' getRouters')
        $routes = Convert-JsonBody -Body $routesResponse.Body -Description ($account.Username + ' getRouters')
        if ([int]$routes.code -ne 200)
        {
            throw ($account.Username + ' getRouters did not succeed.')
        }
        $routePaths = @($routes.data | ForEach-Object { [string]$_.path })
        Assert-ExactStrings -Actual $routePaths -Expected $account.ExpectedRoutes -Description ($account.Username + ' routes')

        $probeTrace = [System.Guid]::NewGuid().ToString()
        $probeResponse = Invoke-ApiRequest -Method 'GET' -Path '/lab/security-probe' -Token $token -TraceId $probeTrace
        Assert-Status -Response $probeResponse -Expected 204 -Description ($account.Username + ' authenticated lab probe')
        Assert-Trace -Response $probeResponse -Expected $probeTrace -Description ($account.Username + ' authenticated lab probe')
        if (-not [string]::IsNullOrEmpty($probeResponse.Body))
        {
            throw ($account.Username + ' authenticated lab probe unexpectedly returned a body.')
        }

        $forbiddenTrace = [System.Guid]::NewGuid().ToString()
        $forbidden = Invoke-ApiRequest -Method 'GET' -Path '/system/post/list' -Token $token -TraceId $forbiddenTrace
        Assert-Status -Response $forbidden -Expected 403 -Description ($account.Username + ' forbidden system post request')
        Assert-Trace -Response $forbidden -Expected $forbiddenTrace -Description ($account.Username + ' forbidden system post request')
        $forbiddenError = Convert-JsonBody -Body $forbidden.Body -Description ($account.Username + ' forbidden system post request')
        if ($forbiddenError.errorCode -ne 'ACCESS_DENIED' -or
            $forbiddenError.traceId -ne $forbiddenTrace)
        {
            throw ($account.Username + ' did not receive the unified forbidden contract.')
        }

        Write-Pass -Name ($account.Username + ' exact role/routes, 204 probe, and 403 boundary') -TraceId $probeTrace
    }

    $operatorTrace = [System.Guid]::NewGuid().ToString()
    $operatorLogin = Invoke-ApiRequest -Method 'POST' -Path '/login' -TraceId $operatorTrace -Body @{
        username = '__lab_system_operator__'
        password = 'NotARealLogin1!'
        code = ''
        uuid = ''
    }
    Assert-Status -Response $operatorLogin -Expected 401 -Description 'system operator login rejection'
    Assert-Trace -Response $operatorLogin -Expected $operatorTrace -Description 'system operator login rejection'
    $operatorBody = Convert-JsonBody -Body $operatorLogin.Body -Description 'system operator login rejection'
    $operatorToken = $operatorBody.PSObject.Properties['token']
    $expectedOperatorMessage = [regex]::Unescape(
        '\u7528\u6237\u4E0D\u5B58\u5728/\u5BC6\u7801\u9519\u8BEF')
    if ($operatorBody.errorCode -ne 'UNAUTHENTICATED' -or
        $operatorBody.msg -cne $expectedOperatorMessage -or
        ($null -ne $operatorToken -and
            -not [string]::IsNullOrWhiteSpace([string]$operatorToken.Value)))
    {
        throw 'The disabled system operator did not return the fixed unauthenticated contract.'
    }
    Write-Pass -Name 'disabled system operator login rejection' -TraceId $operatorTrace

    $completed = $true
}
finally
{
    if ($tokens.Count -gt 0)
    {
        if ($null -eq $httpClient -or $null -eq $appProcess)
        {
            $cleanupFailures.Add('Issued login tokens could not be logged out because the application handle is unavailable.') | Out-Null
        }
        else
        {
            try
            {
                $appProcess.Refresh()
                if ($appProcess.HasExited)
                {
                    $cleanupFailures.Add('Issued login tokens could not be logged out because the application exited early.') | Out-Null
                }
                else
                {
                    foreach ($token in $tokens)
                    {
                        try
                        {
                            $logoutResponse = Invoke-ApiRequest `
                                -Method 'POST' `
                                -Path '/logout' `
                                -Token $token `
                                -TraceId ([System.Guid]::NewGuid().ToString())
                            if ($logoutResponse.StatusCode -ne 200)
                            {
                                throw 'logout returned an unexpected HTTP status'
                            }
                            $logoutBody = Convert-JsonBody `
                                -Body $logoutResponse.Body `
                                -Description 'logout cleanup'
                            if ([int]$logoutBody.code -ne 200)
                            {
                                throw 'logout returned an unsuccessful business code'
                            }
                        }
                        catch
                        {
                            $cleanupFailures.Add('An issued login token failed strict logout cleanup.') | Out-Null
                        }
                    }
                }
            }
            catch
            {
                $cleanupFailures.Add('The smoke application state could not be read for token cleanup.') | Out-Null
            }
        }
    }

    if ($null -ne $appProcess)
    {
        try
        {
            Stop-OwnedApplication -Process $appProcess -ExpectedStartTicks $appStartTicks
        }
        catch
        {
            $cleanupFailures.Add('The identity-checked smoke application cleanup failed.') | Out-Null
        }
        finally
        {
            $appProcess.Dispose()
        }
    }
    if ($redisIsolationVerified)
    {
        try
        {
            $finalRedisFlush = Invoke-RedisCommand -Command 'FLUSHDB'
            if ($finalRedisFlush -ne 'OK')
            {
                throw 'FLUSHDB returned an unexpected response'
            }
        }
        catch
        {
            $cleanupFailures.Add('The isolated Redis FLUSHDB cleanup failed.') | Out-Null
        }
    }
    if ($null -ne $httpClient)
    {
        try
        {
            $httpClient.Dispose()
        }
        catch
        {
            $cleanupFailures.Add('The smoke HTTP client cleanup failed.') | Out-Null
        }
    }
    if ($completed -and $cleanupFailures.Count -eq 0)
    {
        try
        {
            Remove-VerifiedRunDirectory -Path $runRoot
        }
        catch
        {
            $cleanupFailures.Add('The successful smoke run directory cleanup failed.') | Out-Null
        }
    }
    if ($cleanupFailures.Count -ne 0)
    {
        throw ('Foundation smoke cleanup failed: ' + ($cleanupFailures -join ' '))
    }
}

if (-not $completed)
{
    throw 'The foundation smoke test did not complete.'
}
Write-Pass -Name 'owned application logout and identity-checked cleanup'
exit 0
