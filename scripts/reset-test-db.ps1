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
$sql = 'DROP DATABASE IF EXISTS `{0}`; CREATE DATABASE `{0}` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;' -f $DatabaseName
Write-Host "RESET DATABASE: $DatabaseName ON ${adminHost}:$adminPort"

$hadMysqlPwd = Test-Path Env:MYSQL_PWD
$previousMysqlPwd = $env:MYSQL_PWD
try
{
    $env:MYSQL_PWD = $adminPassword
    & $mysqlPath `
        "--host=$adminHost" `
        "--port=$adminPort" `
        "--user=$adminUser" `
        '--protocol=TCP' `
        "--execute=$sql"
    $mysqlExitCode = $LASTEXITCODE
    if ($mysqlExitCode -ne 0)
    {
        throw "mysql reset failed with exit code $mysqlExitCode"
    }
}
finally
{
    if ($hadMysqlPwd)
    {
        $env:MYSQL_PWD = $previousMysqlPwd
    }
    else
    {
        Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue
    }
}
