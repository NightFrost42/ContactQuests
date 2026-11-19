param(
    [ValidateSet('compile', 'server', 'client', 'suite', 'multiplayer', 'no-kubejs')]
    [string]$Mode = 'suite',
    [ValidateSet('client-smoke.json', 'client-parcel-autofill.json', 'client-red-packet-autofill.json', 'client-postcard-autofill.json', 'client-jade-mailbox.json', 'client-autofill-lifecycle.json')]
    [string]$Scenario = 'client-smoke.json',
    [string]$RunId = '',
    [switch]$NativeWindows
)

$ErrorActionPreference = 'Stop'
$Root = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$RunnerArguments = @('tests/runner/run-tests.py', $Mode, '--client-platform', 'windows', '--scenario', $Scenario)
if ($RunId) { $RunnerArguments += @('--run-id', $RunId) }

$UseNative = $NativeWindows.IsPresent
if (-not $UseNative) {
    $WslCmd = Get-Command wsl.exe -ErrorAction SilentlyContinue
    if (-not $WslCmd) {
        $UseNative = $true
    } else {
        $WslRoot = (& wsl.exe --exec wslpath -a $Root 2>$null)
        if ($LASTEXITCODE -ne 0 -or -not $WslRoot) {
            $UseNative = $true
        } else {
            $WslRoot = $WslRoot.Trim()
        }
    }
}

if ($UseNative) {
    $Python = if ($env:CQ_PYTHON) { $env:CQ_PYTHON } else { 'python' }
    Push-Location $Root
    try {
        & $Python @RunnerArguments
        exit $LASTEXITCODE
    } finally {
        Pop-Location
    }
}

# All staging, build and report operations are performed by the same WSL runner.
# Each invocation uses new paths and retains previous worlds/reports.
& wsl.exe --cd $WslRoot --exec python3 @RunnerArguments
exit $LASTEXITCODE
