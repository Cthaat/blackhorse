Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

& powershell -NoProfile -ExecutionPolicy Bypass -File `
    (Join-Path $PSScriptRoot 'verify-migrations.ps1')
if ($LASTEXITCODE -ne 0)
{
    exit $LASTEXITCODE
}

& powershell -NoProfile -ExecutionPolicy Bypass -File `
    (Join-Path $PSScriptRoot 'run-lab-tests.ps1') `
    -DatabaseName 'lab_test_verify' `
    -CleanVerify
if ($LASTEXITCODE -ne 0)
{
    exit $LASTEXITCODE
}

$requiredTests = @(
    'LabTestDiscoveryTest'
    'LabTestDiscoveryIT'
    'LabCompatibilityProbeMapperTest'
    'LabExceptionContractTest'
    'LabDemoAccountInitializerTest'
    'LabRoleSeedIT'
    'LabSystemOperatorLoginIT'
    'LabOpenApiNonProdIT'
    'LabOpenApiProdIT'
) -join ','
& powershell -NoProfile -ExecutionPolicy Bypass -File `
    (Join-Path $PSScriptRoot 'assert-surefire-tests.ps1') `
    -Module 'ruoyi-admin' `
    -RequiredTests $requiredTests
if ($LASTEXITCODE -ne 0)
{
    exit $LASTEXITCODE
}

$uiRoot = Join-Path $PSScriptRoot '..\ruoyi-ui'
& corepack yarn --cwd $uiRoot install --frozen-lockfile
if ($LASTEXITCODE -ne 0)
{
    exit $LASTEXITCODE
}

& corepack yarn --cwd $uiRoot test
if ($LASTEXITCODE -ne 0)
{
    exit $LASTEXITCODE
}

& corepack yarn --cwd $uiRoot build:prod
if ($LASTEXITCODE -ne 0)
{
    exit $LASTEXITCODE
}

exit 0
