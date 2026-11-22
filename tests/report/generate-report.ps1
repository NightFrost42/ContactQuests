param(
    [string]$RunId = '',
    [switch]$Check
)

$ErrorActionPreference = 'Stop'
$Root = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$WslRoot = (& wsl.exe --exec wslpath -a $Root).Trim()
if ($LASTEXITCODE -ne 0) { throw 'Could not resolve the project in WSL.' }
$ReportArguments = @('tests/report/generate-report.py')
if ($RunId) { $ReportArguments += @('--run-id', $RunId) } else { $ReportArguments += '--latest' }
if ($Check) { $ReportArguments += '--check' }
& wsl.exe --cd $WslRoot --exec python3 @ReportArguments
exit $LASTEXITCODE
