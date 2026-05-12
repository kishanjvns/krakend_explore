$images = @(
  "mediq/user-service:latest",
  "mediq/doctor-service:latest",
  "mediq/appointment-service:latest",
  "mediq/notification-service:latest",
  "mediq/emr-service:latest",
  "mediq/analytics-service:latest",
  "mediq/payment-service:latest",
  "mediq/referral-service",
  "mediq/frontend:latest",
  "confluentinc/cp-zookeeper:7.6.0",
  "confluentinc/cp-kafka:7.6.0",
  "quay.io/keycloak/keycloak:24.0.3",
  "temporalio/auto-setup:1.24.2",
  "temporalio/ui:2.26.2"
)

foreach ($img in $images) {
  Write-Host "Building single-platform tar for $img..." -ForegroundColor Yellow

  $tempDir = New-Item -ItemType Directory -Path (Join-Path $env:TEMP ([System.Guid]::NewGuid().ToString()))
  $tarPath = Join-Path $tempDir.FullName "image.tar"
  $dfPath  = Join-Path $tempDir.FullName "Dockerfile"

  "FROM $img`nLABEL mediq.kind.amd64=1" | Out-File -FilePath $dfPath -Encoding UTF8

  docker buildx build --platform linux/amd64 --provenance=false `
    --output "type=docker,name=$img,dest=$tarPath" `
    $tempDir.FullName

  if ($LASTEXITCODE -eq 0) {
    Write-Host "Loading $img into Kind..." -ForegroundColor Cyan
    kind load image-archive $tarPath --name mediq
    if ($LASTEXITCODE -eq 0) {
      Write-Host "OK: $img" -ForegroundColor Green
    } else {
      Write-Host "FAILED to load: $img" -ForegroundColor Red
    }
  } else {
    Write-Host "FAILED to build: $img" -ForegroundColor Red
  }

  Remove-Item -Recurse -Force $tempDir
}

Write-Host "`nDone!" -ForegroundColor Green
