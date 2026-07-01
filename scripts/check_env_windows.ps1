Set-StrictMode -Version Latest
$ErrorActionPreference = "Continue"

$RepoRoot = Resolve-Path (Join-Path $PSScriptRoot "..")
$Ports = @(8080, 8088, 3306, 6379, 5672, 15672, 4319)

function Write-Section {
    param([string]$Title)
    Write-Host ""
    Write-Host "== $Title ==" -ForegroundColor Cyan
}

function Test-CommandAvailable {
    param([string]$Name)
    return $null -ne (Get-Command $Name -ErrorAction SilentlyContinue)
}

function Get-NativeVersion {
    param(
        [string]$Command,
        [string[]]$Arguments
    )
    if (-not (Test-CommandAvailable $Command)) {
        return $null
    }
    try {
        $output = & $Command @Arguments 2>&1
        if ($LASTEXITCODE -ne 0 -and -not $output) {
            return "command returned exit code $LASTEXITCODE"
        }
        return ($output | Select-Object -First 1).ToString()
    } catch {
        return "error: $($_.Exception.Message)"
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

function Test-DockerCompose {
    if (-not (Test-CommandAvailable "docker")) {
        return $false
    }
    try {
        & docker compose version *> $null
        return $LASTEXITCODE -eq 0
    } catch {
        return $false
    }
}

function Test-DockerDesktopProcess {
    $processNames = @("Docker Desktop", "com.docker.backend", "Docker Desktop Backend")
    $process = Get-Process -ErrorAction SilentlyContinue |
        Where-Object { $processNames -contains $_.ProcessName } |
        Select-Object -First 1
    if ($null -ne $process) {
        return $true
    }

    $service = Get-Service -Name "com.docker.service" -ErrorAction SilentlyContinue
    return $null -ne $service -and $service.Status -eq "Running"
}

function Get-WslStatus {
    if (-not (Test-CommandAvailable "wsl")) {
        return [pscustomobject]@{ Available = $false; Healthy = $false; Detail = "wsl command not found" }
    }
    try {
        $output = & wsl --status 2>&1
        if ($LASTEXITCODE -eq 0) {
            return [pscustomobject]@{ Available = $true; Healthy = $true; Detail = (($output | Select-Object -First 1).ToString()) }
        }
        return [pscustomobject]@{ Available = $true; Healthy = $false; Detail = "wsl command exists, but status check returned exit code $LASTEXITCODE" }
    } catch {
        return [pscustomobject]@{ Available = $true; Healthy = $false; Detail = "wsl command exists, but status check failed: $($_.Exception.Message)" }
    }
}

function Write-Check {
    param(
        [string]$Name,
        [bool]$Ok,
        [string]$Detail,
        [bool]$WarningOnly = $false
    )
    if ($Ok) {
        Write-Host ("[OK]   {0,-24} {1}" -f $Name, $Detail) -ForegroundColor Green
    } elseif ($WarningOnly) {
        Write-Host ("[WARN] {0,-24} {1}" -f $Name, $Detail) -ForegroundColor Yellow
    } else {
        Write-Host ("[MISS] {0,-24} {1}" -f $Name, $Detail) -ForegroundColor Red
    }
}

Set-Location $RepoRoot
Write-Host "LexScope Agent Windows environment check"
Write-Host "Project root: $RepoRoot"

Write-Section "Toolchain"
$dockerVersion = Get-NativeVersion "docker" @("--version")
Write-Check "Docker CLI" ($null -ne $dockerVersion) ($(if ($dockerVersion) { $dockerVersion } else { "docker command not found" }))

$dockerDesktopRunning = Test-DockerDesktopProcess
Write-Check "Docker Desktop" $dockerDesktopRunning ($(if ($dockerDesktopRunning) { "process or service is running" } else { "not running" })) -WarningOnly:$true

$dockerDaemonRunning = Test-DockerDaemon
Write-Check "Docker daemon" $dockerDaemonRunning ($(if ($dockerDaemonRunning) { "docker info succeeded" } else { "Docker Desktop is installed but not running, or Docker is missing" }))

$composeOk = Test-DockerCompose
$composeVersion = if ($composeOk) { Get-NativeVersion "docker" @("compose", "version") } else { "docker compose is not available" }
Write-Check "Docker Compose" $composeOk $composeVersion

$wsl = Get-WslStatus
Write-Check "WSL" $wsl.Healthy $wsl.Detail -WarningOnly:($wsl.Available)

$javaVersion = Get-NativeVersion "java" @("-version")
Write-Check "Java" ($null -ne $javaVersion) ($(if ($javaVersion) { $javaVersion } else { "java command not found" }))

$mavenVersion = Get-NativeVersion "mvn" @("-version")
Write-Check "Maven" ($null -ne $mavenVersion) ($(if ($mavenVersion) { $mavenVersion } else { "mvn command not found" }))

$gitVersion = Get-NativeVersion "git" @("--version")
Write-Check "Git" ($null -ne $gitVersion) ($(if ($gitVersion) { $gitVersion } else { "git command not found" }))

$bashVersion = Get-NativeVersion "bash" @("--version")
Write-Check "bash" ($null -ne $bashVersion) ($(if ($bashVersion) { $bashVersion } else { "bash command not found" })) -WarningOnly:$true

$makeVersion = Get-NativeVersion "make" @("--version")
Write-Check "make" ($null -ne $makeVersion) ($(if ($makeVersion) { $makeVersion } else { "make command not found; optional for Windows workflow" })) -WarningOnly:$true

$nodeVersion = Get-NativeVersion "node" @("--version")
Write-Check "Node" ($null -ne $nodeVersion) ($(if ($nodeVersion) { $nodeVersion } else { "node command not found" }))

$npmVersion = Get-NativeVersion "npm" @("--version")
Write-Check "npm" ($null -ne $npmVersion) ($(if ($npmVersion) { $npmVersion } else { "npm command not found" }))

$pythonVersion = Get-NativeVersion "python" @("--version")
if ($null -eq $pythonVersion) {
    $pythonVersion = Get-NativeVersion "python3" @("--version")
}
Write-Check "Python" ($null -ne $pythonVersion) ($(if ($pythonVersion) { $pythonVersion } else { "python/python3 command not found" }))

Write-Section "Ports"
foreach ($port in $Ports) {
    try {
        $listeners = Get-NetTCPConnection -State Listen -LocalPort $port -ErrorAction SilentlyContinue
        if ($listeners) {
            foreach ($listener in $listeners) {
                $process = Get-Process -Id $listener.OwningProcess -ErrorAction SilentlyContinue
                $processName = if ($process) { $process.ProcessName } else { "unknown" }
                Write-Host ("[USED] Port {0,-5} PID={1,-8} Process={2}" -f $port, $listener.OwningProcess, $processName) -ForegroundColor Yellow
            }
        } else {
            Write-Host ("[FREE] Port {0}" -f $port) -ForegroundColor Green
        }
    } catch {
        Write-Host ("[WARN] Port {0,-5} could not be checked: {1}" -f $port, $_.Exception.Message) -ForegroundColor Yellow
    }
}

Write-Host ""
Write-Host "Check complete."
