$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $root

if (-not $env:JAVA_HOME) {
    Write-Host "JAVA_HOME nao definido. Configure para um JDK 17+ (com jpackage)." -ForegroundColor Yellow
    exit 1
}

Write-Host "Build do projeto..." -ForegroundColor Cyan
& .\mvnw -DskipTests package

Write-Host "Copiando dependencias..." -ForegroundColor Cyan
& .\mvnw -DincludeScope=runtime dependency:copy-dependencies -DoutputDirectory=target\app-libs
& .\mvnw "-DincludeArtifactIds=javafx-controls,javafx-fxml,javafx-graphics,javafx-base" -Dclassifier=win `
    dependency:copy-dependencies -DoutputDirectory=target\javafx

$jarName = "PromoPingPainel-1.0-SNAPSHOT.jar"
$appDir = "target\app"
New-Item -ItemType Directory -Force -Path $appDir | Out-Null
Copy-Item -Force -Path ("target\" + $jarName) -Destination $appDir
Copy-Item -Force -Path "target\app-libs\*" -Destination $appDir

Write-Host "Gerando app-image com jpackage..." -ForegroundColor Cyan
$dest = "dist"
New-Item -ItemType Directory -Force -Path $dest | Out-Null

$jpackage = Join-Path $env:JAVA_HOME "bin\jpackage.exe"
if (-not (Test-Path $jpackage)) {
    Write-Host "jpackage nao encontrado em $jpackage" -ForegroundColor Yellow
    exit 1
}

& $jpackage `
    --type app-image `
    --dest $dest `
    --input $appDir `
    --name "PromoPingCodePad" `
    --main-jar $jarName `
    --main-class "org.example.Main" `
    --module-path "target\javafx" `
    --add-modules "javafx.controls,javafx.fxml,javafx.graphics,javafx.base"

Write-Host "Pronto. App em dist\PromoPingCodePad" -ForegroundColor Green
