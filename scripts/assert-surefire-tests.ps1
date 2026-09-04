param(
    [Parameter(Mandatory = $true)]
    [ValidatePattern('^[A-Za-z0-9_-]+$')]
    [string]$Module,

    [Parameter(Mandatory = $true)]
    [string]$RequiredTests
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function Assert-SafeExistingPath
{
    param(
        [Parameter(Mandatory = $true)]
        [string]$Path,

        [Parameter(Mandatory = $true)]
        [string]$Description,

        [ValidateSet('Any', 'Container', 'Leaf')]
        [string]$PathType = 'Any'
    )

    $fullPath = [System.IO.Path]::GetFullPath($Path)
    $exists = if ($PathType -eq 'Any')
    {
        Test-Path -LiteralPath $fullPath
    }
    else
    {
        Test-Path -LiteralPath $fullPath -PathType $PathType
    }
    if (-not $exists)
    {
        throw "$Description not found: $fullPath"
    }

    $pathRoot = [System.IO.Path]::GetPathRoot($fullPath)
    if ([string]::IsNullOrEmpty($pathRoot))
    {
        throw "Unable to determine the filesystem root for ${Description}: $fullPath"
    }

    $rootItem = Get-Item -LiteralPath $pathRoot -Force
    if (($rootItem.Attributes -band [System.IO.FileAttributes]::ReparsePoint) -ne 0)
    {
        throw "Reparse points are not allowed in ${Description}: $pathRoot"
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

function Get-SafeChildDirectory
{
    param(
        [Parameter(Mandatory = $true)]
        [string]$Parent,

        [Parameter(Mandatory = $true)]
        [string]$ChildPath,

        [Parameter(Mandatory = $true)]
        [string]$Description
    )

    $directory = Assert-SafeExistingPath `
        -Path (Join-Path $Parent $ChildPath) `
        -Description $Description `
        -PathType Container
    Assert-PathInside -Path $directory -Root $Parent -Description $Description
    return $directory
}

function Find-UniqueTestSource
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
    $sourcePath = Assert-SafeExistingPath `
        -Path $matches[0] `
        -Description 'test source file' `
        -PathType Leaf
    Assert-PathInside -Path $sourcePath -Root $SourceRoot -Description 'test source file'
    return $sourcePath
}

function Get-JavaPackageName
{
    param(
        [Parameter(Mandatory = $true)]
        [string]$SourcePath,

        [Parameter(Mandatory = $true)]
        [string]$TestName
    )

    $sourceText = Get-Content -LiteralPath $SourcePath -Raw
    $identifier = '[A-Za-z_][A-Za-z0-9_]*'
    $packagePattern = "$identifier(?:\.$identifier)*"
    $headerPattern = "\A(?:\uFEFF)?(?:(?:[ \t\r\n]+)|(?://[^\r\n]*(?:\r?\n|\z))|(?:/\*[\s\S]*?\*/))*package[ \t\r\n]+(?<package>$packagePattern)[ \t\r\n]*;"
    $declaration = [regex]::Match($sourceText, $headerPattern)
    if (-not $declaration.Success -or
        [regex]::IsMatch($sourceText.Substring($declaration.Length), '(?m)^[ \t]*package\b'))
    {
        throw "Expected exactly one valid package declaration for $TestName."
    }

    $packageName = $declaration.Groups['package'].Value
    $reservedWords = @(
        'abstract', 'assert', 'boolean', 'break', 'byte', 'case', 'catch',
        'char', 'class', 'const', 'continue', 'default', 'do', 'double',
        'else', 'enum', 'extends', 'final', 'finally', 'float', 'for', 'goto',
        'if', 'implements', 'import', 'instanceof', 'int', 'interface', 'long',
        'native', 'new', 'package', 'private', 'protected', 'public', 'return',
        'short', 'static', 'strictfp', 'super', 'switch', 'synchronized', 'this',
        'throw', 'throws', 'transient', 'try', 'void', 'volatile', 'while', '_',
        'true', 'false', 'null')
    if (@($packageName.Split('.') | Where-Object { $reservedWords -ccontains $_ }).Count -gt 0)
    {
        throw "Invalid package declaration for $TestName."
    }
    return $packageName
}

function Get-RequiredCounter
{
    param(
        [Parameter(Mandatory = $true)]
        [System.Xml.XmlElement]$Suite,

        [Parameter(Mandatory = $true)]
        [string]$CounterName,

        [Parameter(Mandatory = $true)]
        [string]$TestName
    )

    $counterText = $Suite.GetAttribute($CounterName)
    $counter = 0
    if (-not $Suite.HasAttribute($CounterName) -or
        $counterText -notmatch '^(0|[1-9][0-9]*)$' -or
        -not [int]::TryParse($counterText, [ref]$counter))
    {
        throw "Surefire report has a missing or invalid $CounterName counter for ${TestName}: $counterText"
    }
    return $counter
}

function Get-DirectChildElements
{
    param(
        [Parameter(Mandatory = $true)]
        [System.Xml.XmlNode]$Parent,

        [Parameter(Mandatory = $true)]
        [string]$ElementName
    )

    $elements = @(
        $Parent.ChildNodes |
            Where-Object {
                $_.NodeType -eq [System.Xml.XmlNodeType]::Element -and
                [string]::Equals($_.LocalName, $ElementName, [System.StringComparison]::Ordinal)
            }
    )
    if (@($elements | Where-Object { -not [string]::IsNullOrEmpty($_.NamespaceURI) }).Count -gt 0)
    {
        throw "Namespaced $ElementName elements are not valid Surefire report nodes."
    }
    return $elements
}

$testNames = @($RequiredTests.Split(',') | ForEach-Object { $_.Trim() })
$seenTestNames = [System.Collections.Generic.HashSet[string]]::new(
    [System.StringComparer]::OrdinalIgnoreCase)
foreach ($testName in $testNames)
{
    if ([string]::IsNullOrEmpty($testName))
    {
        throw 'RequiredTests must contain only non-empty test class names.'
    }
    if ($testName -notmatch '^[A-Za-z][A-Za-z0-9_]*$')
    {
        throw "Unsafe test class name: $testName"
    }
    if (-not $seenTestNames.Add($testName))
    {
        throw "Duplicate test class name: $testName"
    }
}

$null = Assert-SafeExistingPath -Path $PSCommandPath -Description 'assertion script' -PathType Leaf
$repoRoot = Assert-SafeExistingPath `
    -Path (Join-Path $PSScriptRoot '..') `
    -Description 'repository root' `
    -PathType Container
$moduleRoot = Get-SafeChildDirectory -Parent $repoRoot -ChildPath $Module -Description 'module directory'
$targetRoot = Get-SafeChildDirectory -Parent $moduleRoot -ChildPath 'target' -Description 'module target directory'
$reportRoot = Get-SafeChildDirectory -Parent $targetRoot -ChildPath 'surefire-reports' -Description 'Surefire report directory'
$sourceRoot = Get-SafeChildDirectory -Parent $moduleRoot -ChildPath 'src\test\java' -Description 'test source root'

foreach ($testName in $testNames)
{
    $sourcePath = Find-UniqueTestSource -SourceRoot $sourceRoot -TestName $testName
    $packageName = Get-JavaPackageName -SourcePath $sourcePath -TestName $testName
    $expectedFqcn = "$packageName.$testName"
    $expectedReportName = "TEST-$expectedFqcn.xml"
    $reports = @(
        Get-ChildItem -LiteralPath $reportRoot -Force -File |
            Where-Object {
                [string]::Equals($_.Name, $expectedReportName, [System.StringComparison]::Ordinal)
            }
    )
    if ($reports.Count -ne 1)
    {
        throw "Expected one Surefire XML report named $expectedReportName for $testName, found $($reports.Count)."
    }

    $reportPath = Assert-SafeExistingPath `
        -Path $reports[0].FullName `
        -Description 'Surefire XML report' `
        -PathType Leaf
    Assert-PathInside -Path $reportPath -Root $reportRoot -Description 'Surefire XML report'

    $settings = New-Object System.Xml.XmlReaderSettings
    $settings.DtdProcessing = [System.Xml.DtdProcessing]::Prohibit
    $settings.XmlResolver = $null
    $reader = [System.Xml.XmlReader]::Create($reportPath, $settings)
    try
    {
        $document = New-Object System.Xml.XmlDocument
        $document.XmlResolver = $null
        $document.Load($reader)
    }
    finally
    {
        $reader.Dispose()
    }

    $suite = $document.DocumentElement
    if ($null -eq $suite -or
        -not [string]::Equals($suite.LocalName, 'testsuite', [System.StringComparison]::Ordinal) -or
        -not [string]::IsNullOrEmpty($suite.NamespaceURI))
    {
        throw "Surefire XML report has no unnamespaced testsuite root for $testName."
    }
    $suiteName = $suite.GetAttribute('name')
    if (-not $suite.HasAttribute('name') -or
        -not [string]::Equals($suiteName, $expectedFqcn, [System.StringComparison]::Ordinal))
    {
        throw "Surefire report for $testName belongs to a different test class: $suiteName"
    }

    $tests = Get-RequiredCounter -Suite $suite -CounterName 'tests' -TestName $testName
    $failures = Get-RequiredCounter -Suite $suite -CounterName 'failures' -TestName $testName
    $errors = Get-RequiredCounter -Suite $suite -CounterName 'errors' -TestName $testName
    $skipped = Get-RequiredCounter -Suite $suite -CounterName 'skipped' -TestName $testName
    $testCases = @(Get-DirectChildElements -Parent $suite -ElementName 'testcase')
    if ($tests -lt 1 -or $testCases.Count -ne $tests)
    {
        throw "Surefire testcase count mismatch for ${testName}: tests=$tests testcases=$($testCases.Count)"
    }

    $actualFailures = 0
    $actualErrors = 0
    $actualSkipped = 0
    foreach ($testCase in $testCases)
    {
        $className = $testCase.GetAttribute('classname')
        if (-not $testCase.HasAttribute('classname') -or
            -not [string]::Equals($className, $expectedFqcn, [System.StringComparison]::Ordinal))
        {
            throw "Surefire testcase for $testName belongs to a different test class: $className"
        }
        $actualFailures += @(Get-DirectChildElements -Parent $testCase -ElementName 'failure').Count
        $actualErrors += @(Get-DirectChildElements -Parent $testCase -ElementName 'error').Count
        $actualSkipped += @(Get-DirectChildElements -Parent $testCase -ElementName 'skipped').Count
    }

    if ($failures -ne $actualFailures -or
        $errors -ne $actualErrors -or
        $skipped -ne $actualSkipped)
    {
        throw "Surefire counters do not match testcase nodes for ${testName}: failures=$failures/$actualFailures errors=$errors/$actualErrors skipped=$skipped/$actualSkipped"
    }
    if ($failures -ne 0 -or $errors -ne 0 -or $skipped -ne 0)
    {
        throw "Invalid Surefire result for ${testName}: tests=$tests failures=$failures errors=$errors skipped=$skipped"
    }

    Write-Host "VERIFIED TEST: $testName tests=$tests"
}
