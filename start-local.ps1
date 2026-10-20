param([switch]$SkipBuild)
$ErrorActionPreference = 'Stop'
$projectRoot = $PSScriptRoot
$backend = Join-Path $projectRoot 'blackjack'
$frontend = Join-Path $projectRoot 'frontend/frontend-blackjack'
$runDir = Join-Path $projectRoot '.run'
New-Item -ItemType Directory -Force -Path $runDir | Out-Null
foreach ($port in @(8080, 5173)) {
    if (Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue) {
        throw "Port $port is already in use. Stop the previous server first."
    }
}
if (-not $SkipBuild) {
    Push-Location $backend
    try {
        & .\gradlew.bat test bootJar --no-daemon
        if ($LASTEXITCODE -ne 0) { throw 'Backend build/test failed.' }
    } finally { Pop-Location }
    Push-Location $frontend
    try {
        & npm.cmd ci --no-audit --no-fund
        if ($LASTEXITCODE -ne 0) { throw 'npm ci failed.' }
        & npm.cmd run build
        if ($LASTEXITCODE -ne 0) { throw 'Frontend build failed.' }
    } finally { Pop-Location }
}
$jar = Join-Path $backend 'build/libs/blackjack-0.0.1-SNAPSHOT.jar'
$vite = Join-Path $frontend 'node_modules/vite/bin/vite.js'
if (!(Test-Path $jar) -or !(Test-Path $vite)) { throw 'Run without -SkipBuild to install and build first.' }
$runtimeJar = Join-Path $runDir 'blackjack-runtime.jar'
Copy-Item -LiteralPath $jar -Destination $runtimeJar -Force
$javaProcess = Start-Process -FilePath (Get-Command java).Source -ArgumentList @('-jar', ('"' + $runtimeJar + '"'), '--spring.profiles.active=local') -WorkingDirectory $backend -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $runDir 'backend.log') -RedirectStandardError (Join-Path $runDir 'backend-error.log')
$nodeProcess = Start-Process -FilePath (Get-Command node).Source -ArgumentList @(('"' + $vite + '"')) -WorkingDirectory $frontend -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $runDir 'frontend.log') -RedirectStandardError (Join-Path $runDir 'frontend-error.log')
@{ backend = $javaProcess.Id; frontend = $nodeProcess.Id } | ConvertTo-Json | Set-Content (Join-Path $runDir 'processes.json')
$ready = $false
for ($i = 0; $i -lt 40; $i++) {
    Start-Sleep -Milliseconds 500
    if ($javaProcess.HasExited -or $nodeProcess.HasExited) { throw "Server exited. See $runDir logs." }
    try {
        $response = Invoke-WebRequest -Uri 'http://127.0.0.1:8080/api/rooms' -SkipHttpErrorCheck
        if ($response.StatusCode -eq 401) { $ready = $true; break }
    } catch {}
}
if (!$ready) { throw "Startup timed out. See $runDir logs." }
Write-Host 'Blackjack is ready: http://localhost:5173'
Write-Host 'Register an account to play. Stop with .\stop-local.ps1'
