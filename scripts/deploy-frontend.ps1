# deploy-frontend.ps1
# Script to build and deploy the Angular frontend using Docker, Kind (via WSL), and Helmfile.

$ErrorActionPreference = "Stop"

Write-Host "=========================================" -ForegroundColor Cyan
Write-Host " 1. Building Angular Frontend Docker Image" -ForegroundColor Cyan
Write-Host "=========================================" -ForegroundColor Cyan
docker build -t mediq/frontend:latest ./frontend

if ($LASTEXITCODE -ne 0) {
    Write-Host "❌ Docker build failed." -ForegroundColor Red
    exit $LASTEXITCODE
}

Write-Host "`n=========================================" -ForegroundColor Cyan
Write-Host " 2. Loading Image into Kind (via WSL)" -ForegroundColor Cyan
Write-Host "=========================================" -ForegroundColor Cyan
# User specified that `kind` is installed in WSL but not native PowerShell
wsl kind load docker-image mediq/frontend:latest --name mediq

if ($LASTEXITCODE -ne 0) {
    Write-Host "❌ Failed to load image into Kind." -ForegroundColor Red
    exit $LASTEXITCODE
}

Write-Host "`n=========================================" -ForegroundColor Cyan
Write-Host " 3. Deploying via Helmfile" -ForegroundColor Cyan
Write-Host "=========================================" -ForegroundColor Cyan
# Use the selector to only apply the frontend, saving time.
helmfile -f helmfile.yaml apply --selector name=mediq-frontend

if ($LASTEXITCODE -ne 0) {
    Write-Host "❌ Helmfile deployment failed." -ForegroundColor Red
    exit $LASTEXITCODE
}

Write-Host "`n✅ Frontend deployed successfully!" -ForegroundColor Green
