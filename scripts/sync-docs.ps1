# SPOT docs sync: backend/docs -> docs/shared + SPOT-front/docs
$ErrorActionPreference = "Stop"

$backendRoot = Split-Path $PSScriptRoot -Parent
$backendDocs = Join-Path $backendRoot "docs"
$sharedDest = Join-Path $backendDocs "shared"
$frontDocs = Join-Path (Split-Path $backendRoot -Parent) "SPOT-front\docs"

if (-not (Test-Path $backendDocs)) {
    throw "Backend docs not found: $backendDocs"
}
if (-not (Test-Path $frontDocs)) {
    throw "Front docs not found: $frontDocs"
}

New-Item -ItemType Directory -Force -Path $sharedDest | Out-Null

$sharedFiles = @(
    "basic.md",
    "PRD.md",
    "BUSINESS_RULES.md",
    "DATA_MODEL.md",
    "API_SPEC.md",
    "ARCHITECTURE.md",
    "ROADMAP.md",
    "DEPLOYMENT.md"
)

$backendToFront = @(
    "IMPLEMENTATION_STATUS.md",
    "FRONTEND_DEV.md",
    "todolist.md",
    "SCREENS.md",
    "README.md",
    "NAVER_OAUTH.md",
    "KEEPALIVE.md"
)

$copied = @()

foreach ($file in $sharedFiles) {
    $src = Join-Path $backendDocs $file
    if (-not (Test-Path $src)) {
        Write-Warning "Skip missing shared source: $file"
        continue
    }
    Copy-Item $src (Join-Path $sharedDest $file) -Force
    Copy-Item $src (Join-Path $frontDocs $file) -Force
    $copied += $file
}

foreach ($file in $backendToFront) {
    $src = Join-Path $backendDocs $file
    if (-not (Test-Path $src)) {
        Write-Warning "Skip missing backend doc: $file"
        continue
    }
    Copy-Item $src (Join-Path $frontDocs $file) -Force
    $copied += $file
}

$timestamp = Get-Date -Format "yyyy-MM-dd HH:mm:ss K"
$manifest = @"
# Sync manifest (auto-generated)

- **Source:** SPOT-backend/docs/ -> SPOT-front/docs/ + docs/shared/
- **Last sync:** $timestamp
- **Shared files:** $($sharedFiles -join ', ')
- **Also synced to front:** $($backendToFront -join ', ')

Run from backend repo:

``````powershell
.\scripts\sync-docs.ps1
``````
"@

Set-Content -Path (Join-Path $sharedDest "_SYNC.md") -Value $manifest -Encoding UTF8

Write-Host "Synced $($copied.Count) files to:"
Write-Host "  - $sharedDest"
Write-Host "  - $frontDocs"
