# FHIRLint Windows Installer
# Usage in PowerShell:
#   irm https://raw.githubusercontent.com/braeden512/fhir-lint/main/install.ps1 | iex

$ErrorActionPreference = 'Stop'

$Repo = "braeden512/fhir-lint"
$LatestUrl = "https://github.com/$Repo/releases/latest/download/fhir-lint-windows-x86_64.exe"

$InstallDir = Join-Path $env:USERPROFILE ".fhir-lint\bin"
if (-not (Test-Path $InstallDir)) {
    New-Item -ItemType Directory -Path $InstallDir -Force | Out-Null
}

$ExePath = Join-Path $InstallDir "fhir-lint.exe"

Write-Host "Downloading FHIRLint for Windows from $LatestUrl..." -ForegroundColor Cyan
Invoke-WebRequest -Uri $LatestUrl -OutFile $ExePath -UseBasicParsing

# Add to user PATH if not already present
$UserPath = [Environment]::GetEnvironmentVariable("Path", "User")
if ($UserPath -notlike "*$InstallDir*") {
    Write-Host "Adding $InstallDir to user PATH..." -ForegroundColor Cyan
    $NewUserPath = "$UserPath;$InstallDir"
    [Environment]::SetEnvironmentVariable("Path", $NewUserPath, "User")
    $env:Path = "$env:Path;$InstallDir"
}

Write-Host ""
Write-Host "FHIRLint installed successfully to $ExePath!" -ForegroundColor Green
Write-Host "Verifying installation:" -ForegroundColor Cyan
& $ExePath --version
