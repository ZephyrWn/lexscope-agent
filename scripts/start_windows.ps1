Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$RepoRoot = Resolve-Path (Join-Path $PSScriptRoot "..")
Set-Location $RepoRoot

function Fail {
    param([string]$Message)
    Write-Host "[start][error] $Message" -ForegroundColor Red
    exit 1
}

function Test-CommandAvailable {
    param([string]$Name)
    return $null -ne (Get-Command $Name -ErrorAction SilentlyContinue)
}

function Test-DockerDaemon {
    if (-not (Test-CommandAvailable "docker")) {
        return $false
    }
    try {
        & docker info *> $null
        return $LASTEXITCODE -eq 0
    } catch {
        return $false
    }
}

function Show-RecentLogs {
    Write-Host ""
    Write-Host "Recent Docker Compose logs (last 100 lines):" -ForegroundColor Yellow
    try {
        & docker compose --env-file ".env.demo" logs --tail=100
    } catch {
        Write-Host "Could not read Docker Compose logs: $($_.Exception.Message)" -ForegroundColor Yellow
    }
}

Write-Host "[start] Project root: $RepoRoot" -ForegroundColor Cyan

if (-not (Test-Path -LiteralPath (Join-Path $RepoRoot ".env.demo"))) {
    Fail ".env.demo was not found. Run scripts\setup_windows.ps1 first, then fill the model provider values."
}

if (-not (Test-CommandAvailable "docker")) {
    Fail "docker command was not found. Run scripts\setup_windows.ps1 and restart PowerShell."
}

try {
    & docker compose version *> $null
    if ($LASTEXITCODE -ne 0) {
        Fail "Docker Compose is not available through 'docker compose'."
    }
} catch {
    Fail "Docker Compose check failed: $($_.Exception.Message)"
}

if (-not (Test-DockerDaemon)) {
    Fail "Docker Desktop is installed but not running. Open Docker Desktop and wait until it is ready, then rerun this script."
}

Write-Host "[start] Building and starting Docker Compose services..." -ForegroundColor Cyan
& docker compose --env-file ".env.demo" up --build -d
if ($LASTEXITCODE -ne 0) {
    Show-RecentLogs
    Fail "Docker Compose startup failed with exit code $LASTEXITCODE."
}

Write-Host ""
Write-Host "LexScope Agent URLs:" -ForegroundColor Green
Write-Host "Frontend: http://localhost:8088/"
Write-Host "Backend:  http://localhost:8080/"
Write-Host "Swagger:  http://localhost:8080/swagger-ui/index.html"
Write-Host ""

Write-Host "Docker Compose status:" -ForegroundColor Cyan
& docker compose --env-file ".env.demo" ps
if ($LASTEXITCODE -ne 0) {
    Show-RecentLogs
    Fail "Docker Compose status check failed with exit code $LASTEXITCODE."
}
