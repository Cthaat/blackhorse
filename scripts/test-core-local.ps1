[CmdletBinding()]
param(
    [ValidatePattern('^lab_test_core_[A-Za-z0-9_]+$')]
    [string]$DatabaseName = 'lab_test_core_local',
    [string]$MySqlHome = 'C:\Program Files\MySQL\MySQL Server 8.0',
    [string]$JavaHome = 'C:\APP\JDK\jdk_17'
)

$ErrorActionPreference = 'Stop'
$repository = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$credentials = Get-Content -Raw -LiteralPath (Join-Path $repository 'target/local-runtime/credentials.json') | ConvertFrom-Json
$variables = @{
    PATH = (Join-Path $MySqlHome 'bin') + ';' + $env:PATH
    JAVA_HOME = $JavaHome
    LAB_TEST_ADMIN_HOST = '127.0.0.1'
    LAB_TEST_ADMIN_PORT = '33306'
    LAB_TEST_ADMIN_USERNAME = 'root'
    LAB_TEST_ADMIN_PASSWORD = $credentials.mysql.rootPassword
    LAB_TEST_DB_USERNAME = 'root'
    LAB_TEST_DB_PASSWORD = $credentials.mysql.rootPassword
}
$previous = @{}
try {
    foreach ($key in $variables.Keys) {
        $previous[$key] = [Environment]::GetEnvironmentVariable($key, 'Process')
        [Environment]::SetEnvironmentVariable($key, $variables[$key], 'Process')
    }
    & (Join-Path $PSScriptRoot 'run-lab-tests.ps1') -DatabaseName $DatabaseName -Tests LabCoreBusinessIT
    if ($LASTEXITCODE -ne 0) { throw 'Core business integration checks failed; inspect the test report.' }
}
finally {
    foreach ($key in $previous.Keys) {
        [Environment]::SetEnvironmentVariable($key, $previous[$key], 'Process')
    }
}
