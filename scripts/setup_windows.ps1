Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$RepoRoot = Resolve-Path (Join-Path $PSScriptRoot "..")
Set-Location $RepoRoot

function Write-Step {
    param([string]$Message)
    Write-Host "[setup] $Message" -ForegroundColor Cyan
}

function Write-Warn {
    param([string]$Message)
    Write-Host "[setup][warn] $Message" -ForegroundColor Yellow
}

function Fail {
    param([string]$Message)
    Write-Host "[setup][error] $Message" -ForegroundColor Red
    exit 1
}

function Test-CommandAvailable {
    param([string]$Name)
    return $null -ne (Get-Command $Name -ErrorAction SilentlyContinue)
}

function Invoke-WingetInstall {
    param(
        [string]$Id,
        [string]$DisplayName
    )

    Write-Step "Installing $DisplayName with winget..."
    & winget install --id $Id -e --accept-package-agreements --accept-source-agreements
    if ($LASTEXITCODE -ne 0) {
        Fail "Failed to install $DisplayName with winget. Exit code: $LASTEXITCODE"
    }
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

function Test-DockerDesktopInstalled {
    if (Test-CommandAvailable "docker") {
        return $true
    }
    $dockerPaths = @(
        "C:\Program Files\Docker\Docker\Docker Desktop.exe",
        "C:\Program Files\Docker\Docker\resources\bin\docker.exe"
    )
    foreach ($path in $dockerPaths) {
        if (Test-Path -LiteralPath $path) {
            return $true
        }
    }
    return $false
}

function Test-DockerDesktopRunning {
    $processNames = @("Docker Desktop", "com.docker.backend", "Docker Desktop Backend")
    $process = Get-Process -ErrorAction SilentlyContinue |
        Where-Object { $processNames -contains $_.ProcessName } |
        Select-Object -First 1
    if ($process) {
        return $true
    }
    $service = Get-Service -Name "com.docker.service" -ErrorAction SilentlyContinue
    return $service -and $service.Status -eq "Running"
}

function Ensure-DemoEnv {
    $envDemo = Join-Path $RepoRoot ".env.demo"
    $envExample = Join-Path $RepoRoot ".env.example"

    if (Test-Path -LiteralPath $envDemo) {
        Write-Step ".env.demo already exists; leaving it unchanged."
        return
    }

    if (-not (Test-Path -LiteralPath $envExample)) {
        Fail ".env.example was not found. Cannot create .env.demo."
    }

    Copy-Item -LiteralPath $envExample -Destination $envDemo

    $lines = [System.Collections.Generic.List[string]]::new()
    Get-Content -LiteralPath $envDemo | ForEach-Object { $lines.Add($_) }
    if (-not ($lines | Where-Object { $_ -match '^OPENAI_MODEL=' })) {
        $lines.Add("OPENAI_MODEL=replace_me")
    }
    if (-not ($lines | Where-Object { $_ -match '^EMBEDDING_MODEL=' })) {
        $lines.Add("EMBEDDING_MODEL=replace_me")
    }
    Set-Content -LiteralPath $envDemo -Value $lines -Encoding UTF8

    Write-Warn "Created .env.demo from .env.example."
    Write-Warn "Open .env.demo and fill OPENAI_BASE_URL, OPENAI_API_KEY, OPENAI_MODEL, and EMBEDDING_MODEL before starting."
}

Write-Step "Project root: $RepoRoot"

if (-not (Test-CommandAvailable "winget")) {
    Fail "winget is required for automatic installation. Install App Installer from Microsoft Store, then rerun this script."
}
Write-Step "winget is available."

if (-not (Test-DockerDesktopInstalled)) {
    Invoke-WingetInstall -Id "Docker.DockerDesktop" -DisplayName "Docker Desktop"
    Write-Warn "Docker Desktop was installed. Start Docker Desktop manually, and restart PowerShell or reboot if docker is not in PATH yet."
} else {
    Write-Step "Docker Desktop or Docker CLI is already present."
}

if (-not (Test-CommandAvailable "java")) {
    Invoke-WingetInstall -Id "EclipseAdoptium.Temurin.17.JDK" -DisplayName "Temurin JDK 17"
} else {
    Write-Step "Java is already available; skipping JDK installation."
}

if (-not (Test-CommandAvailable "mvn")) {
    Invoke-WingetInstall -Id "Apache.Maven" -DisplayName "Apache Maven"
} else {
    Write-Step "Maven is already available; skipping Maven installation."
}

if (-not (Test-CommandAvailable "git") -or -not (Test-CommandAvailable "bash")) {
    Invoke-WingetInstall -Id "Git.Git" -DisplayName "Git for Windows"
} else {
    Write-Step "Git and bash are already available; skipping Git for Windows installation."
}

if (-not (Test-CommandAvailable "make")) {
    Write-Warn "make is not installed. It is optional for this Windows workflow; use scripts\start_windows.ps1 instead of make demo."
    Write-Warn "Optional install choices include Chocolatey make, MSYS2, or Git Bash environments."
} else {
    Write-Step "make is available."
}

if (Test-CommandAvailable "node") {
    Write-Step "Node is already available; skipping Node installation."
} else {
    Write-Warn "Node is missing. This script does not install Node because the Docker build uses the project container setup."
}

if (Test-CommandAvailable "npm") {
    Write-Step "npm is already available; skipping npm installation."
} else {
    Write-Warn "npm is missing. This script does not install npm because the Docker build uses the project container setup."
}

if (Test-CommandAvailable "python" -or Test-CommandAvailable "python3") {
    Write-Step "Python is already available; skipping Python installation."
} else {
    Write-Warn "Python is missing. Install Python 3 if you want to run the optional smoke-test scripts."
}

Ensure-DemoEnv

if (Test-CommandAvailable "wsl") {
    try {
        & wsl --status
        if ($LASTEXITCODE -ne 0) {
            Write-Warn "WSL command exists, but WSL status returned exit code $LASTEXITCODE."
            Write-Warn "If Docker Desktop asks for WSL2, run 'wsl --install' from an elevated PowerShell and reboot."
        }
    } catch {
        Write-Warn "WSL status check failed: $($_.Exception.Message)"
        Write-Warn "If Docker Desktop asks for WSL2, enable WSL2 and the Virtual Machine Platform Windows feature."
    }
} else {
    Write-Warn "WSL command was not found. Docker Desktop may require WSL2. Install it with 'wsl --install' from an elevated PowerShell."
}

if (Test-CommandAvailable "docker") {
    if (Test-DockerDaemon) {
        Write-Step "Docker daemon is running."
    } else {
        Write-Warn "Docker Desktop is installed but not running, or the daemon is not ready."
        Write-Warn "Open Docker Desktop manually. If it reports WSL2 or virtualization errors, enable WSL2/Virtual Machine Platform and confirm CPU virtualization is enabled in BIOS/UEFI."
        Write-Warn "This script will not change BIOS settings and will not wait indefinitely for Docker startup."
    }
} elseif (Test-DockerDesktopRunning) {
    Write-Warn "Docker Desktop appears to be running, but docker is not in this PowerShell PATH yet. Restart PowerShell or reboot."
} else {
    Write-Warn "Docker Desktop is not running. Open Docker Desktop manually after installation."
}

Write-Host ""
Write-Host "Setup finished." -ForegroundColor Green
Write-Host "Restart PowerShell. If Docker Desktop was newly installed, reboot or open Docker Desktop manually before starting the project."
Write-Host "Then run: powershell -ExecutionPolicy Bypass -File .\scripts\start_windows.ps1"
