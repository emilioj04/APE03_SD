Write-Host "Iniciando instalacion de requerimientos para APE03_SD..." -ForegroundColor Cyan

Write-Host "1. Instalando Java JDK 17 (Eclipse Temurin)..." -ForegroundColor Yellow
winget install --id EclipseAdoptium.Temurin.17.JDK -e --accept-source-agreements --accept-package-agreements

Write-Host "2. Instalando Apache Maven..." -ForegroundColor Yellow
winget install --id Apache.Maven -e --accept-source-agreements --accept-package-agreements

Write-Host "3. Instalando Docker Desktop..." -ForegroundColor Yellow
winget install --id Docker.DockerDesktop -e --accept-source-agreements --accept-package-agreements

Write-Host "Instalacion finalizada. Por favor reinicia la terminal para actualizar las variables de entorno." -ForegroundColor Green
