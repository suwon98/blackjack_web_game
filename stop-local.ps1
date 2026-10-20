$ErrorActionPreference = 'Stop'
$stateFile = Join-Path $PSScriptRoot '.run/processes.json'
if (!(Test-Path $stateFile)) { Write-Host 'No tracked local servers.'; return }
$state = Get-Content $stateFile -Raw | ConvertFrom-Json
foreach ($processId in @($state.backend, $state.frontend)) {
    $process = Get-CimInstance Win32_Process -Filter "ProcessId = $processId" -ErrorAction SilentlyContinue
    if ($process -and $process.CommandLine -and $process.CommandLine.Contains($PSScriptRoot)) {
        Stop-Process -Id $processId
        Write-Host "Stopped project process $processId"
    }
}
Remove-Item -LiteralPath $stateFile
