Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

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
        throw "$Description is outside its trusted root."
    }
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
    if ([string]::IsNullOrEmpty($pathRoot))
    {
        throw "Unable to determine the filesystem root for $Description."
    }

    $rootItem = Get-Item -LiteralPath $pathRoot -Force
    if (($rootItem.Attributes -band [System.IO.FileAttributes]::ReparsePoint) -ne 0)
    {
        throw "Reparse points are not allowed in $Description."
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

    return $fullPath
}

function Add-SqlToken
{
    param(
        [Parameter(Mandatory = $true)]
        [AllowEmptyCollection()]
        [System.Collections.Generic.List[object]]$Tokens,

        [Parameter(Mandatory = $true)]
        [ValidateSet('Word', 'String', 'Symbol')]
        [string]$Type,

        [Parameter(Mandatory = $true)]
        [AllowEmptyString()]
        [string]$Value,

        [Parameter(Mandatory = $true)]
        [int]$Line,

        [Parameter(Mandatory = $true)]
        [bool]$AtStatementStart
    )

    $Tokens.Add([pscustomobject]@{
            Type = $Type
            Value = $Value
            Line = $Line
            AtStatementStart = $AtStatementStart
        })
}

function Get-SqlTokens
{
    param(
        [Parameter(Mandatory = $true)]
        [AllowEmptyString()]
        [string]$SqlText,

        [Parameter(Mandatory = $true)]
        [string]$FileName
    )

    $tokens = [System.Collections.Generic.List[object]]::new()
    $index = 0
    $line = 1
    $statementHasToken = $false
    while ($index -lt $SqlText.Length)
    {
        $character = $SqlText[$index]
        if ($character -eq [char]0xFEFF)
        {
            $index++
            continue
        }
        if ([char]::IsWhiteSpace($character))
        {
            if ($character -eq "`r")
            {
                if ($index + 1 -lt $SqlText.Length -and $SqlText[$index + 1] -eq "`n")
                {
                    $index++
                }
                $line++
            }
            elseif ($character -eq "`n")
            {
                $line++
            }
            $index++
            continue
        }

        $isDashComment = $false
        if ($character -eq '-' -and $index + 1 -lt $SqlText.Length -and
            $SqlText[$index + 1] -eq '-')
        {
            $nextCharacterValue = $(if ($index + 2 -lt $SqlText.Length) {
                    [int]$SqlText[$index + 2]
                }
                else {
                    -1
                })
            # MySQL applies its single-byte lexer classification here. UTF-8
            # encodings of Unicode whitespace (for example NBSP) do not make a
            # double dash into a comment.
            $isDashComment = $nextCharacterValue -eq -1 -or
                $nextCharacterValue -le 0x20 -or
                $nextCharacterValue -eq 0x7F
        }
        if ($character -eq '#' -or $isDashComment)
        {
            while ($index -lt $SqlText.Length -and
                $SqlText[$index] -ne "`r" -and $SqlText[$index] -ne "`n")
            {
                $index++
            }
            continue
        }

        if ($character -eq '/' -and $index + 1 -lt $SqlText.Length -and
            $SqlText[$index + 1] -eq '*')
        {
            $commentLine = $line
            $isMySqlExecutableComment = $index + 2 -lt $SqlText.Length -and
                $SqlText[$index + 2] -eq '!'
            $isMariaDbExecutableComment = $index + 3 -lt $SqlText.Length -and
                ($SqlText[$index + 2] -eq 'M' -or $SqlText[$index + 2] -eq 'm') -and
                $SqlText[$index + 3] -eq '!'
            if ($isMySqlExecutableComment -or $isMariaDbExecutableComment)
            {
                throw "Database executable comments are not allowed in $FileName at line $commentLine."
            }
            $index += 2
            $closed = $false
            while ($index -lt $SqlText.Length)
            {
                if ($SqlText[$index] -eq '*' -and $index + 1 -lt $SqlText.Length -and
                    $SqlText[$index + 1] -eq '/')
                {
                    $index += 2
                    $closed = $true
                    break
                }
                if ($SqlText[$index] -eq "`r")
                {
                    if ($index + 1 -lt $SqlText.Length -and $SqlText[$index + 1] -eq "`n")
                    {
                        $index++
                    }
                    $line++
                }
                elseif ($SqlText[$index] -eq "`n")
                {
                    $line++
                }
                $index++
            }
            if (-not $closed)
            {
                throw "Unterminated block comment in $FileName at line $commentLine."
            }
            continue
        }

        $tokenLine = $line
        $atStatementStart = -not $statementHasToken
        if ($character -eq "'" -or $character -eq '"')
        {
            $quote = $character
            $value = [System.Text.StringBuilder]::new()
            $index++
            $closed = $false
            while ($index -lt $SqlText.Length)
            {
                $current = $SqlText[$index]
                if ($current -eq '\' -and $index + 1 -lt $SqlText.Length)
                {
                    $null = $value.Append($SqlText[$index + 1])
                    $index += 2
                    continue
                }
                if ($current -eq $quote)
                {
                    if ($index + 1 -lt $SqlText.Length -and $SqlText[$index + 1] -eq $quote)
                    {
                        $null = $value.Append($quote)
                        $index += 2
                        continue
                    }
                    $index++
                    $closed = $true
                    break
                }
                if ($current -eq "`r")
                {
                    if ($index + 1 -lt $SqlText.Length -and $SqlText[$index + 1] -eq "`n")
                    {
                        $null = $value.Append("`r`n")
                        $index += 2
                    }
                    else
                    {
                        $null = $value.Append("`r")
                        $index++
                    }
                    $line++
                    continue
                }
                if ($current -eq "`n")
                {
                    $null = $value.Append("`n")
                    $line++
                    $index++
                    continue
                }
                $null = $value.Append($current)
                $index++
            }
            if (-not $closed)
            {
                throw "Unterminated SQL string in $FileName at line $tokenLine."
            }
            Add-SqlToken -Tokens $tokens -Type String -Value $value.ToString() `
                -Line $tokenLine -AtStatementStart $atStatementStart
            $statementHasToken = $true
            continue
        }

        if ($character -eq '`')
        {
            $value = [System.Text.StringBuilder]::new()
            $index++
            $closed = $false
            while ($index -lt $SqlText.Length)
            {
                $current = $SqlText[$index]
                if ($current -eq '`')
                {
                    if ($index + 1 -lt $SqlText.Length -and $SqlText[$index + 1] -eq '`')
                    {
                        $null = $value.Append('`')
                        $index += 2
                        continue
                    }
                    $index++
                    $closed = $true
                    break
                }
                if ($current -eq "`r" -or $current -eq "`n")
                {
                    throw "Newline in quoted identifier in $FileName at line $tokenLine."
                }
                $null = $value.Append($current)
                $index++
            }
            if (-not $closed)
            {
                throw "Unterminated quoted identifier in $FileName at line $tokenLine."
            }
            Add-SqlToken -Tokens $tokens -Type Word -Value $value.ToString() `
                -Line $tokenLine -AtStatementStart $atStatementStart
            $statementHasToken = $true
            continue
        }

        if ([char]::IsLetterOrDigit($character) -or $character -eq '_' -or $character -eq '$')
        {
            $start = $index
            while ($index -lt $SqlText.Length)
            {
                $current = $SqlText[$index]
                if (-not ([char]::IsLetterOrDigit($current) -or
                        $current -eq '_' -or $current -eq '$'))
                {
                    break
                }
                $index++
            }
            Add-SqlToken -Tokens $tokens -Type Word `
                -Value $SqlText.Substring($start, $index - $start) `
                -Line $tokenLine -AtStatementStart $atStatementStart
            $statementHasToken = $true
            continue
        }

        Add-SqlToken -Tokens $tokens -Type Symbol -Value ([string]$character) `
            -Line $tokenLine -AtStatementStart $atStatementStart
        $statementHasToken = $character -ne ';'
        $index++
    }

    return $tokens.ToArray()
}

function Test-ApprovedPasswordHash
{
    param(
        [Parameter(Mandatory = $true)]
        [AllowEmptyString()]
        [string]$Value
    )

    return $Value -match '^\$2[aby]\$[0-9]{2}\$[./A-Za-z0-9]{53}$' -or
        $Value -match '^\$argon2(?:id|i|d)\$v=[0-9]+\$[^\s]+$'
}

function Assert-SafeSql
{
    param(
        [Parameter(Mandatory = $true)]
        [object[]]$Tokens,

        [Parameter(Mandatory = $true)]
        [string]$FileName
    )

    for ($index = 0; $index -lt $Tokens.Count; $index++)
    {
        $token = $Tokens[$index]
        if ($token.Type -ne 'Word')
        {
            continue
        }

        if ([string]::Equals($token.Value, 'SET', [System.StringComparison]::OrdinalIgnoreCase))
        {
            $statementStart = $index
            while ($statementStart -gt 0 -and
                -not ($Tokens[$statementStart - 1].Type -eq 'Symbol' -and
                    $Tokens[$statementStart - 1].Value -eq ';'))
            {
                $statementStart--
            }
            $statementHasDataMutation = $false
            for ($statementIndex = $statementStart;
                $statementIndex -lt $Tokens.Count;
                $statementIndex++)
            {
                $statementToken = $Tokens[$statementIndex]
                if ($statementToken.Type -eq 'Symbol' -and $statementToken.Value -eq ';')
                {
                    break
                }
                if ($statementToken.Type -eq 'Word' -and
                    $statementToken.Value -in @('UPDATE', 'INSERT', 'REPLACE', 'LOAD'))
                {
                    $statementHasDataMutation = $true
                }
            }
            for ($setIndex = $index + 1; $setIndex -lt $Tokens.Count; $setIndex++)
            {
                $setToken = $Tokens[$setIndex]
                if ($setToken.Type -eq 'Symbol' -and $setToken.Value -eq ';')
                {
                    break
                }
                if ($setToken.Type -eq 'Word' -and
                    [string]::Equals(
                        $setToken.Value,
                        'sql_mode',
                        [System.StringComparison]::OrdinalIgnoreCase))
                {
                    throw "SQL mode changes are not allowed in $FileName at line $($token.Line)."
                }
                if (-not $statementHasDataMutation -and $setToken.Type -eq 'Word' -and
                    [string]::Equals(
                        $setToken.Value,
                        'PASSWORD',
                        [System.StringComparison]::OrdinalIgnoreCase))
                {
                    throw "Database password statements are not allowed in $FileName at line $($token.Line)."
                }
            }
        }

        if ($token.Value -in @('CREATE', 'ALTER', 'DROP', 'RENAME'))
        {
            $userObjectModifiers = @('OR', 'REPLACE', 'IF', 'NOT', 'EXISTS')
            for ($objectIndex = $index + 1;
                $objectIndex -lt $Tokens.Count;
                $objectIndex++)
            {
                $objectToken = $Tokens[$objectIndex]
                if ($objectToken.Type -eq 'Symbol' -and $objectToken.Value -eq ';')
                {
                    break
                }
                if ($objectToken.Type -ne 'Word')
                {
                    continue
                }
                if ($objectToken.Value -in $userObjectModifiers)
                {
                    continue
                }
                if ([string]::Equals(
                        $objectToken.Value,
                        'USER',
                        [System.StringComparison]::OrdinalIgnoreCase))
                {
                    throw "Database user-account statements are not allowed in $FileName at line $($token.Line)."
                }
                break
            }
        }

        if ([string]::Equals(
                $token.Value,
                'IDENTIFIED',
                [System.StringComparison]::OrdinalIgnoreCase))
        {
            for ($identifiedIndex = $index + 1;
                $identifiedIndex -lt $Tokens.Count;
                $identifiedIndex++)
            {
                $identifiedToken = $Tokens[$identifiedIndex]
                if ($identifiedToken.Type -eq 'Symbol' -and $identifiedToken.Value -eq ';')
                {
                    break
                }
                if ($identifiedToken.Type -eq 'Word' -and
                    [string]::Equals(
                        $identifiedToken.Value,
                        'BY',
                        [System.StringComparison]::OrdinalIgnoreCase))
                {
                    throw "Database password clauses are not allowed in $FileName at line $($token.Line)."
                }
            }
        }

        if ([string]::Equals($token.Value, 'DROP', [System.StringComparison]::OrdinalIgnoreCase))
        {
            $nextIndex = $index + 1
            if ($nextIndex -lt $Tokens.Count -and
                $Tokens[$nextIndex].Type -eq 'Word' -and
                [string]::Equals(
                    $Tokens[$nextIndex].Value,
                    'TEMPORARY',
                    [System.StringComparison]::OrdinalIgnoreCase))
            {
                $nextIndex++
            }
            if ($nextIndex -lt $Tokens.Count -and $Tokens[$nextIndex].Type -eq 'Word' -and
                ($Tokens[$nextIndex].Value -in @('DATABASE', 'SCHEMA', 'TABLE')))
            {
                throw "Forbidden DROP statement in $FileName at line $($token.Line)."
            }
        }

        if ([string]::Equals($token.Value, 'CREATE', [System.StringComparison]::OrdinalIgnoreCase) -and
            $index + 1 -lt $Tokens.Count -and $Tokens[$index + 1].Type -eq 'Word' -and
            ($Tokens[$index + 1].Value -in @('DATABASE', 'SCHEMA')))
        {
            $objectType = $Tokens[$index + 1].Value.ToUpperInvariant()
            throw "Forbidden CREATE $objectType statement in $FileName at line $($token.Line)."
        }

        if ($token.AtStatementStart -and
            [string]::Equals($token.Value, 'USE', [System.StringComparison]::OrdinalIgnoreCase))
        {
            throw "Forbidden USE statement in $FileName at line $($token.Line)."
        }

        if ($token.Value -in @('PREPARE', 'EXECUTE'))
        {
            throw "Forbidden dynamic SQL statement in $FileName at line $($token.Line)."
        }

        if ([string]::Equals($token.Value, 'DELIMITER', [System.StringComparison]::OrdinalIgnoreCase))
        {
            throw "DELIMITER directives are not allowed in $FileName at line $($token.Line)."
        }

        if ([string]::Equals($token.Value, 'password', [System.StringComparison]::OrdinalIgnoreCase) -and
            $index + 1 -lt $Tokens.Count -and $Tokens[$index + 1].Type -eq 'Symbol' -and
            $Tokens[$index + 1].Value -eq '=')
        {
            $valueIndex = $index + 2
            $approvedHash = $valueIndex -lt $Tokens.Count -and
                $Tokens[$valueIndex].Type -eq 'String' -and
                (Test-ApprovedPasswordHash -Value $Tokens[$valueIndex].Value)
            if (-not $approvedHash)
            {
                throw "Forbidden plaintext password assignment in $FileName at line $($token.Line)."
            }
        }
    }
}

function ConvertTo-NormalizedVersion
{
    param(
        [Parameter(Mandatory = $true)]
        [string]$Version
    )

    $segments = [System.Collections.Generic.List[string]]::new()
    foreach ($segment in ($Version -split '[._]'))
    {
        $segments.Add(($segment -replace '^0+(?=[0-9])', ''))
    }
    while ($segments.Count -gt 1 -and $segments[$segments.Count - 1] -eq '0')
    {
        $segments.RemoveAt($segments.Count - 1)
    }
    return $segments.ToArray() -join '.'
}

$repoRoot = Assert-NoReparsePoint `
    -Path (Join-Path $PSScriptRoot '..') `
    -Description 'repository root'
if (-not (Test-Path -LiteralPath $repoRoot -PathType Container))
{
    throw 'Repository root is not a directory.'
}

$migrationRoot = [System.IO.Path]::GetFullPath((Join-Path `
        $repoRoot `
        'ruoyi-admin\src\main\resources\db\migration'))
Assert-PathInside -Path $migrationRoot -Root $repoRoot -Description 'migration directory'
if (-not (Test-Path -LiteralPath $migrationRoot -PathType Container))
{
    throw 'No Flyway migrations found.'
}
$migrationRoot = Assert-NoReparsePoint -Path $migrationRoot -Description 'migration directory'

$entries = @(Get-ChildItem -LiteralPath $migrationRoot -Force)
if ($entries.Count -eq 0)
{
    throw 'No Flyway migrations found.'
}

$fileNamePattern = [regex]::new(
    '\AV(?<version>[0-9]+(?:[._][0-9]+)*)__(?<description>[A-Za-z0-9][A-Za-z0-9_]*)\.sql\z',
    [System.Text.RegularExpressions.RegexOptions]::CultureInvariant)
$versions = [System.Collections.Generic.Dictionary[string, string]]::new(
    [System.StringComparer]::OrdinalIgnoreCase)
$migrationFiles = [System.Collections.Generic.List[object]]::new()
foreach ($entry in $entries)
{
    if (($entry.Attributes -band [System.IO.FileAttributes]::ReparsePoint) -ne 0)
    {
        throw "Reparse points are not allowed in the Flyway migration directory: $($entry.Name)"
    }
    if ($entry.PSIsContainer)
    {
        throw "Subdirectories are not allowed in the Flyway migration directory: $($entry.Name)"
    }
    $entryPath = [System.IO.Path]::GetFullPath($entry.FullName)
    Assert-PathInside -Path $entryPath -Root $migrationRoot -Description 'migration file'
    if (-not (Test-Path -LiteralPath $entryPath -PathType Leaf))
    {
        throw "Unexpected Flyway migration entry: $($entry.Name)"
    }

    $nameMatch = $fileNamePattern.Match($entry.Name)
    if (-not $nameMatch.Success)
    {
        throw "Invalid Flyway migration filename: $($entry.Name)"
    }
    $normalizedVersion = ConvertTo-NormalizedVersion -Version $nameMatch.Groups['version'].Value
    if ($versions.ContainsKey($normalizedVersion))
    {
        throw "Duplicate Flyway versions found: $normalizedVersion"
    }
    $versions.Add($normalizedVersion, $entry.Name)
    $migrationFiles.Add([pscustomobject]@{
            Name = $entry.Name
            FullName = $entryPath
            Version = $normalizedVersion
        })
}

$utf8 = [System.Text.UTF8Encoding]::new($false, $true)
foreach ($migrationFile in @($migrationFiles | Sort-Object Version, Name))
{
    $sql = [System.IO.File]::ReadAllText($migrationFile.FullName, $utf8)
    if ($sql.Contains('${'))
    {
        throw "Flyway placeholders are not allowed in $($migrationFile.Name)."
    }
    $tokens = @(Get-SqlTokens -SqlText $sql -FileName $migrationFile.Name)
    Assert-SafeSql -Tokens $tokens -FileName $migrationFile.Name
}

Write-Host "Verified Flyway migrations: files=$($migrationFiles.Count) versions=$((@($versions.Keys) | Sort-Object) -join ',')"
