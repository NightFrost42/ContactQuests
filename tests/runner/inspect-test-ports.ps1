param([int[]]$Ports = @(46007))
$ErrorActionPreference = 'Stop'
$listeners = @(Get-NetTCPConnection -State Listen -ErrorAction SilentlyContinue | Where-Object { $_.LocalPort -in $Ports })
$owners = @($listeners | ForEach-Object {
    $process = Get-CimInstance Win32_Process -Filter "ProcessId = $($_.OwningProcess)" -ErrorAction SilentlyContinue
    [pscustomobject]@{
        port = $_.LocalPort
        address = $_.LocalAddress
        pid = $_.OwningProcess
        name = $process.Name
        parentPid = $process.ParentProcessId
        createdAt = $process.CreationDate
        testCommand = if ($process.CommandLine -match 'ContactQuests') { $process.CommandLine } else { $null }
    }
})
$testProcesses = @(Get-CimInstance Win32_Process -Filter "Name = 'java.exe' OR Name = 'javaw.exe'" |
    Where-Object { $_.CommandLine -match 'ContactQuests' } |
    Select-Object ProcessId, ParentProcessId, CreationDate, CommandLine)
[pscustomobject]@{ inspectedAt = (Get-Date).ToString('o'); ports = $Ports; listeners = $owners; testProcesses = $testProcesses } |
    ConvertTo-Json -Depth 6
