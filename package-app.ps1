$ErrorActionPreference = "Stop"
$JavaHome = "C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot"
$JPackage = "$JavaHome\bin\jpackage.exe"

Write-Host "==============================================" -ForegroundColor Cyan
Write-Host "F1 Telemetry - Desktop EXE Builder (PowerShell)" -ForegroundColor Cyan
Write-Host "Backend: http://140.245.219.62:8080" -ForegroundColor Cyan
Write-Host "==============================================" -ForegroundColor Cyan

# 1. Kill old processes
Write-Host "`n[1/5] Stopping any running instances..." -ForegroundColor Yellow
Get-Process -Name "F1Telemetry*", "jcef_helper*" -ErrorAction SilentlyContinue | Stop-Process -Force -ErrorAction SilentlyContinue
Start-Sleep -Milliseconds 500

# 2. Clean output folders
Write-Host "`n[2/5] Cleaning old build directories..." -ForegroundColor Yellow
if (Test-Path "target\dist") { Remove-Item -Path "target\dist" -Recurse -Force -ErrorAction SilentlyContinue }
if (Test-Path "target\input") { Remove-Item -Path "target\input" -Recurse -Force -ErrorAction SilentlyContinue }

# 3. Build Fat JAR
Write-Host "`n[3/5] Building Spring Boot fat JAR (Maven)..." -ForegroundColor Yellow
cmd.exe /c "mvn package -DskipTests -q"
if ($LASTEXITCODE -ne 0) {
    Write-Error "Maven build failed!"
    exit 1
}

# 4. Prepare input
Write-Host "`n[4/5] Staging packaging input..." -ForegroundColor Yellow
New-Item -ItemType Directory -Path "target\input" -Force | Out-Null
Copy-Item "target\f1-telemetry-0.0.1-SNAPSHOT.jar" "target\input\f1-telemetry-0.0.1-SNAPSHOT.jar" -Force

# 5. Run JPackage
Write-Host "`n[5/5] Packaging into standalone EXE (jpackage)..." -ForegroundColor Yellow
& $JPackage `
  --type app-image `
  --dest target\dist `
  --name F1Telemetry `
  --input target\input `
  --main-jar f1-telemetry-0.0.1-SNAPSHOT.jar `
  --main-class org.springframework.boot.loader.launch.JarLauncher `
  --runtime-image $JavaHome `
  --java-options "-Djava.awt.headless=false" `
  --java-options "-Df1.desktop.mode=true" `
  --java-options "--add-exports=java.base/java.lang=ALL-UNNAMED" `
  --java-options "--add-exports=java.desktop/sun.awt=ALL-UNNAMED" `
  --java-options "--add-exports=java.desktop/sun.java2d=ALL-UNNAMED" `
  --icon logo.ico

if ($LASTEXITCODE -ne 0) {
    Write-Error "jpackage failed!"
    exit 1
}

Write-Host "`n==============================================" -ForegroundColor Green
Write-Host "SUCCESS! Standalone app at: target\dist\F1Telemetry\" -ForegroundColor Green
Write-Host "Executable: target\dist\F1Telemetry\F1Telemetry.exe" -ForegroundColor Green
Write-Host "==============================================" -ForegroundColor Green
