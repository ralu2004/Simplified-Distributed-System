param(
    [int]$ChaosRequests = 40,
    [int]$RecoveryRequests = 10,
    [string]$MovieId = "123"
)

$ErrorActionPreference = "Stop"

function Wait-ForHttp200 {
    param(
        [string]$Name,
        [string]$Url,
        [int]$TimeoutSec = 120
    )

    $deadline = (Get-Date).AddSeconds($TimeoutSec)
    while ((Get-Date) -lt $deadline) {
        try {
            $response = Invoke-WebRequest -Uri $Url -UseBasicParsing -TimeoutSec 5
            if ($response.StatusCode -eq 200) {
                Write-Host "OK: $Name is ready ($Url)"
                return
            }
        } catch {
            Start-Sleep -Seconds 2
        }
    }

    throw "Timeout waiting for $Name at $Url"
}

function Write-Step {
    param([string]$Message)
    Write-Host ""
    Write-Host "== $Message ==" -ForegroundColor Cyan
}

function Get-BreakerState {
    param([int]$TimeoutSec = 10)
    $cb = Invoke-RestMethod -Uri "http://localhost:8081/actuator/circuitbreakers" -TimeoutSec $TimeoutSec
    return $cb.circuitBreakers.recommendationCB.state
}

$root = Split-Path -Parent $PSScriptRoot
$stamp = Get-Date -Format "yyyyMMdd-HHmmss"
$outDir = Join-Path $root ("demo-artifacts\" + $stamp)
New-Item -ItemType Directory -Path $outDir -Force | Out-Null

Write-Step "Starting full stack (chaos off)"
docker compose -f "$root\docker-compose.yml" up -d --build | Out-Null

Wait-ForHttp200 -Name "recommendation-service" -Url "http://localhost:8082/actuator/health"
Wait-ForHttp200 -Name "movie-service" -Url "http://localhost:8081/actuator/health"
Wait-ForHttp200 -Name "gateway-service route" -Url "http://localhost:8090/movies/$MovieId"

Write-Step "Happy-path checks"
$happy = Invoke-RestMethod -Uri "http://localhost:8090/movies/$MovieId" -TimeoutSec 10
$happy | ConvertTo-Json -Depth 8 | Set-Content -Path (Join-Path $outDir "01-happy-response.json")

$cbBefore = Invoke-RestMethod -Uri "http://localhost:8081/actuator/circuitbreakers" -TimeoutSec 10
$cbBefore | ConvertTo-Json -Depth 10 | Set-Content -Path (Join-Path $outDir "02-circuitbreakers-before-chaos.json")

Write-Step "Turning chaos ON (recommendation only)"
$oldChaos = $env:CHAOS_MODE
$env:CHAOS_MODE = "true"
docker compose -f "$root\docker-compose.yml" up -d --build --force-recreate recommendation-service | Out-Null
Wait-ForHttp200 -Name "recommendation-service (chaos on)" -Url "http://localhost:8082/actuator/health"
docker compose -f "$root\docker-compose.yml" exec recommendation-service printenv CHAOS_MODE |
    Set-Content -Path (Join-Path $outDir "02b-chaos-mode-on.txt")

Write-Step "Generating chaos traffic through gateway"
$chaosResponses = New-Object System.Collections.Generic.List[string]
for ($i = 1; $i -le $ChaosRequests; $i++) {
    try {
        $resp = Invoke-RestMethod -Uri "http://localhost:8090/movies/$MovieId" -TimeoutSec 10
        $chaosResponses.Add(($resp | ConvertTo-Json -Compress -Depth 8))
    } catch {
        $safeMsg = ($_.Exception.Message | ConvertTo-Json -Compress)
        $chaosResponses.Add("{""request"":$i,""error"":$safeMsg}")
    }
}
$chaosResponses | Set-Content -Path (Join-Path $outDir "03-chaos-responses.ndjson")

docker compose -f "$root\docker-compose.yml" logs movie-service > (Join-Path $outDir "04-movie-service.log")
docker compose -f "$root\docker-compose.yml" logs recommendation-service > (Join-Path $outDir "05-recommendation-service.log")

Get-Content (Join-Path $outDir "04-movie-service.log") |
    Select-String "Circuit state changed" |
    Set-Content (Join-Path $outDir "06-circuit-transitions.log")

Get-Content (Join-Path $outDir "05-recommendation-service.log") |
    Select-String "Chaos:" |
    Set-Content (Join-Path $outDir "07-chaos-events.log")

$fallbackPattern = '"recommendations":\["1","2","3","4","5"\]'
$fallbackCount = ($chaosResponses | Select-String -Pattern $fallbackPattern).Count
$liveCount = $ChaosRequests - $fallbackCount

@(
    "chaos_requests=$ChaosRequests"
    "fallback_count=$fallbackCount"
    "live_count=$liveCount"
) | Set-Content -Path (Join-Path $outDir "08-chaos-summary.txt")

Write-Step "Turning chaos OFF and validating recovery"
if ($null -eq $oldChaos) {
    Remove-Item Env:CHAOS_MODE -ErrorAction SilentlyContinue
} else {
    $env:CHAOS_MODE = $oldChaos
}
docker compose -f "$root\docker-compose.yml" up -d --build --force-recreate recommendation-service | Out-Null
Wait-ForHttp200 -Name "recommendation-service (chaos off)" -Url "http://localhost:8082/actuator/health"
docker compose -f "$root\docker-compose.yml" exec recommendation-service printenv CHAOS_MODE |
    Set-Content -Path (Join-Path $outDir "08b-chaos-mode-off.txt")
Write-Host "Waiting 12s for breaker half-open probe window..."
Start-Sleep -Seconds 12

$recoveryResponses = New-Object System.Collections.Generic.List[string]
for ($i = 1; $i -le $RecoveryRequests; $i++) {
    try {
        $resp = Invoke-RestMethod -Uri "http://localhost:8090/movies/$MovieId" -TimeoutSec 10
        $recoveryResponses.Add(($resp | ConvertTo-Json -Compress -Depth 8))
    } catch {
        $safeMsg = ($_.Exception.Message | ConvertTo-Json -Compress)
        $recoveryResponses.Add("{""request"":$i,""error"":$safeMsg}")
    }
}
$recoveryResponses | Set-Content -Path (Join-Path $outDir "09-recovery-responses.ndjson")

# Keep probing until the breaker returns to CLOSED (or timeout).
$deadline = (Get-Date).AddSeconds(90)
$stateHistory = New-Object System.Collections.Generic.List[string]
while ((Get-Date) -lt $deadline) {
    try {
        $resp = Invoke-RestMethod -Uri "http://localhost:8090/movies/$MovieId" -TimeoutSec 10
        $recoveryResponses.Add(($resp | ConvertTo-Json -Compress -Depth 8))
        $state = Get-BreakerState
        $stateHistory.Add(("{0:o} state={1}" -f (Get-Date), $state))
        if ($state -eq "CLOSED") {
            break
        }
    } catch {
        $safeMsg = ($_.Exception.Message | ConvertTo-Json -Compress)
        $recoveryResponses.Add("{""request"":""recovery-loop"",""error"":$safeMsg}")
    }
    Start-Sleep -Seconds 2
}
$recoveryResponses | Set-Content -Path (Join-Path $outDir "09-recovery-responses.ndjson")
$stateHistory | Set-Content -Path (Join-Path $outDir "09b-recovery-state-poll.log")

docker compose -f "$root\docker-compose.yml" logs movie-service > (Join-Path $outDir "04-movie-service.log")
Get-Content (Join-Path $outDir "04-movie-service.log") |
    Select-String "Circuit state changed" |
    Set-Content (Join-Path $outDir "11-all-circuit-transitions.log")

$transitionBlob = (Get-Content (Join-Path $outDir "11-all-circuit-transitions.log")) -join "; "
@(
    "movie_id=$MovieId"
    "saw_closed_to_open=$($transitionBlob -match 'CLOSED to OPEN')"
    "saw_open_to_half_open=$($transitionBlob -match 'OPEN to HALF_OPEN')"
    "saw_half_open_to_closed=$($transitionBlob -match 'HALF_OPEN to CLOSED')"
    "final_state=$(Get-BreakerState)"
) | Set-Content -Path (Join-Path $outDir "12-recovery-summary.txt")

$cbAfter = Invoke-RestMethod -Uri "http://localhost:8081/actuator/circuitbreakerevents" -TimeoutSec 10
$cbAfter | ConvertTo-Json -Depth 12 | Set-Content -Path (Join-Path $outDir "10-circuitbreakerevents-after-recovery.json")

Write-Step "Demo artifacts ready"
Write-Host "Saved to: $outDir" -ForegroundColor Green
Write-Host "Key files:"
Write-Host " - 01-happy-response.json"
Write-Host " - 06-circuit-transitions.log"
Write-Host " - 07-chaos-events.log"
Write-Host " - 08-chaos-summary.txt"
Write-Host " - 12-recovery-summary.txt"
Write-Host " - 10-circuitbreakerevents-after-recovery.json"
