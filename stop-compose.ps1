$ErrorActionPreference = 'Stop'
$previousDomain = $env:DOMAIN
Push-Location $PSScriptRoot
try {
    $env:DOMAIN = 'localhost'
    & docker compose -p blackjack-verify -f compose.yml -f compose.local.yml stop
    if ($LASTEXITCODE -ne 0) { throw 'Container shutdown failed.' }
} finally {
    $env:DOMAIN = $previousDomain
    Pop-Location
}
