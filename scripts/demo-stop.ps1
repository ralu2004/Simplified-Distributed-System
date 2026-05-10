$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
docker compose -f "$root\docker-compose.yml" down
Write-Host "Stack stopped."
