param([switch]$SkipBuild)
$ErrorActionPreference = 'Stop'
$previousDomain = $env:DOMAIN
Push-Location $PSScriptRoot
try {
    $systemDrive = Get-PSDrive -Name ($env:SystemDrive.TrimEnd(':'))
    if ($systemDrive.Free -lt 5GB) {
        throw 'The system drive needs at least 5 GiB available before starting Docker. Check the Docker disk location and host storage.'
    }
    & docker info --format '{{.ServerVersion}}'
    if ($LASTEXITCODE -ne 0) { throw 'Docker engine is unavailable. Start Docker Desktop and check docker info.' }
    & python deploy/generate-secrets.py
    if ($LASTEXITCODE -ne 0) { throw 'Secret file preparation failed.' }
    $env:DOMAIN = 'localhost'
    $composeArgs = @('compose', '-p', 'blackjack-verify', '-f', 'compose.yml', '-f', 'compose.local.yml')
    & docker @composeArgs config --quiet
    if ($LASTEXITCODE -ne 0) { throw 'Compose configuration is invalid.' }
    $upArgs = @('up', '-d')
    if (!$SkipBuild) { $upArgs += '--build' }
    & docker @composeArgs @upArgs
    if ($LASTEXITCODE -ne 0) { throw 'Container startup failed. Inspect docker compose logs and host disk space.' }
    $ready = $false
    for ($attempt = 0; $attempt -lt 60; $attempt++) {
        try {
            if ((Invoke-RestMethod 'http://localhost:18081/actuator/health').status -eq 'UP') { $ready = $true; break }
        } catch {}
        Start-Sleep -Seconds 1
    }
    if (!$ready) { throw 'Application readiness timed out. Inspect backend and db logs.' }
    Write-Host 'Blackjack containers are ready: http://localhost:18081'
} finally {
    $env:DOMAIN = $previousDomain
    Pop-Location
}
