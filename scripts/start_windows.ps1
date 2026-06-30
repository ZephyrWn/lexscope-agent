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

function Ensure-DockerImage {
    param(
        [string]$LocalImage,
        [string]$MirrorImage
    )

    & docker image inspect $LocalImage *> $null
    if ($LASTEXITCODE -eq 0) {
        return
    }

    Write-Host "[start] Pulling missing image: $LocalImage" -ForegroundColor Cyan
    & docker pull $LocalImage
    if ($LASTEXITCODE -eq 0) {
        return
    }

    Write-Host "[start] Docker Hub pull failed; trying mirror: $MirrorImage" -ForegroundColor Yellow
    & docker pull $MirrorImage
    if ($LASTEXITCODE -ne 0) {
        Fail "Could not pull required image '$LocalImage' from Docker Hub or mirror '$MirrorImage'. Check Docker Desktop network/proxy settings."
    }

    & docker tag $MirrorImage $LocalImage
    if ($LASTEXITCODE -ne 0) {
        Fail "Pulled mirror image '$MirrorImage' but failed to tag it as '$LocalImage'."
    }
}

function Ensure-RequiredImages {
    $requiredImages = @(
        @{ Local = "node:20-alpine"; Mirror = "docker.m.daocloud.io/library/node:20-alpine" },
        @{ Local = "nginx:1.27-alpine"; Mirror = "docker.m.daocloud.io/library/nginx:1.27-alpine" },
        @{ Local = "maven:3.9.9-eclipse-temurin-17"; Mirror = "docker.m.daocloud.io/library/maven:3.9.9-eclipse-temurin-17" },
        @{ Local = "eclipse-temurin:17-jre-alpine"; Mirror = "docker.m.daocloud.io/library/eclipse-temurin:17-jre-alpine" },
        @{ Local = "mysql:8.0.36"; Mirror = "docker.m.daocloud.io/library/mysql:8.0.36" },
        @{ Local = "redis:7.4-alpine"; Mirror = "docker.m.daocloud.io/library/redis:7.4-alpine" },
        @{ Local = "rabbitmq:3.13-management"; Mirror = "docker.m.daocloud.io/library/rabbitmq:3.13-management" },
        @{ Local = "grafana/tempo:2.6.1"; Mirror = "docker.m.daocloud.io/grafana/tempo:2.6.1" }
    )

    foreach ($image in $requiredImages) {
        Ensure-DockerImage -LocalImage $image.Local -MirrorImage $image.Mirror
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

function Show-DockerHubHint {
    Write-Host ""
    Write-Host "If the error mentions auth.docker.io, registry-1.docker.io, oauth token, timeout, or i/o timeout:" -ForegroundColor Yellow
    Write-Host "- Docker Desktop can start, but Docker Hub is not reachable from this network."
    Write-Host "- Try opening Docker Desktop, signing in again, changing network/proxy, or retrying after the network is stable."
    Write-Host "- The project files and .env.demo are usually not the cause of that error."
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

Ensure-RequiredImages

Write-Host "[start] Building and starting Docker Compose services..." -ForegroundColor Cyan
& docker compose --env-file ".env.demo" up --build -d
if ($LASTEXITCODE -ne 0) {
    $composeExitCode = $LASTEXITCODE
    Show-RecentLogs
    Show-DockerHubHint
    Fail "Docker Compose startup failed with exit code $composeExitCode."
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
