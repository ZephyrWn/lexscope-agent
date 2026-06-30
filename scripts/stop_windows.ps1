Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$RepoRoot = Resolve-Path (Join-Path $PSScriptRoot "..")
Set-Location $RepoRoot

function Fail {
    param([string]$Message)
    Write-Host "[stop][error] $Message" -ForegroundColor Red
    exit 1
}

if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    Fail "docker command was not found."
}

if (-not (Test-Path -LiteralPath (Join-Path $RepoRoot ".env.demo"))) {
    Fail ".env.demo was not found. Nothing was removed; create .env.demo before using this project workflow."
}

Write-Host "[stop] Stopping Docker Compose services without deleting volumes..." -ForegroundColor Cyan
& docker compose --env-file ".env.demo" down
if ($LASTEXITCODE -ne 0) {
    Fail "Docker Compose down failed with exit code $LASTEXITCODE."
}

Write-Host "[stop] Services stopped. Volumes were kept." -ForegroundColor Green
