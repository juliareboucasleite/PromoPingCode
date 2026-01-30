$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $root

if (-not $env:JAVA_HOME) {
    Write-Host "JAVA_HOME nao definido. Configure para um JDK 17+ (com jpackage)." -ForegroundColor Yellow
    exit 1
}

Write-Host "Build do projeto..." -ForegroundColor Cyan
& .\mvnw -DskipTests package

Write-Host "Preparando icone..." -ForegroundColor Cyan
$png = "src\main\resources\org\example\nodecode.png"
$ico = "src\main\resources\org\example\nodecode.ico"

if (-not (Test-Path "dist")) {
    New-Item -ItemType Directory -Force -Path "dist" | Out-Null
}

if (-not (Test-Path $ico) -and (Test-Path $png)) {
    $icoOut = (Resolve-Path "dist") + "\nodecode.ico"
    $magick = Get-Command magick -ErrorAction SilentlyContinue
    if ($magick) {
        try {
            & $magick.Source $png -define icon:auto-resize=256,128,64,48,32,24,16 $icoOut
            if (Test-Path $icoOut) {
                $ico = "dist\nodecode.ico"
            }
        } catch {
            Write-Host "Falha ao gerar .ico com ImageMagick. Tentando conversao simples..." -ForegroundColor Yellow
        }
    }
    if (-not (Test-Path $ico)) {
        try {
            Add-Type -AssemblyName System.Drawing
            $img = [System.Drawing.Image]::FromFile((Resolve-Path $png))
            $icon = [System.Drawing.Icon]::FromHandle($img.GetHicon())
            $fs = New-Object System.IO.FileStream($icoOut, [System.IO.FileMode]::Create)
            $icon.Save($fs)
            $fs.Close()
            $icon.Dispose()
            $img.Dispose()
            $ico = "dist\nodecode.ico"
        } catch {
            Write-Host "Falha ao converter PNG para ICO. Use um .ico multi-tamanho (16-256)." -ForegroundColor Yellow
            $ico = $null
        }
    }
}

if (-not (Test-Path $ico)) {
    Write-Host "Icone ICO nao encontrado. Gere um .ico multi-tamanho (16-256)." -ForegroundColor Yellow
    exit 1
}

# Validate multi-size ICO using ImageMagick if available
$magick = Get-Command magick -ErrorAction SilentlyContinue
if (-not $magick) {
    Write-Host "ImageMagick nao encontrado. Instale para validar tamanhos do .ico." -ForegroundColor Yellow
    exit 1
}

$identify = & $magick.Source "identify" -format "%w " (Resolve-Path $ico)
if (-not $identify) {
    Write-Host "Falha ao ler tamanhos do .ico." -ForegroundColor Yellow
    exit 1
}

$sizes = $identify.Trim().Split(" ", [System.StringSplitOptions]::RemoveEmptyEntries) | Sort-Object -Unique
$required = @(16,24,32,48,64,128,256)
$missing = @()
foreach ($s in $required) {
    if ($sizes -notcontains $s.ToString()) { $missing += $s }
}
if ($missing.Count -gt 0) {
    Write-Host ("ICO sem tamanhos: " + ($missing -join ", ") + ". Recrie o .ico multi-tamanho.") -ForegroundColor Yellow
    exit 1
}

Write-Host "Copiando dependencias..." -ForegroundColor Cyan
& .\mvnw -DincludeScope=runtime dependency:copy-dependencies -DoutputDirectory=target\app-libs
& .\mvnw "-DincludeArtifactIds=javafx-controls,javafx-fxml,javafx-graphics,javafx-base" -Dclassifier=win `
    dependency:copy-dependencies -DoutputDirectory=target\javafx

$jarName = "PromoPingPainel-1.1.1.jar"
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

$iconArg = @()
if ($ico) {
    $iconArg = @("--icon", (Resolve-Path $ico))
}

& $jpackage `
    --type app-image `
    --dest $dest `
    --input $appDir `
    --name "PromoPingCodePad" `
    --main-jar $jarName `
    --main-class "org.example.Main" `
    --module-path "target\javafx" `
    --add-modules "javafx.controls,javafx.fxml,javafx.graphics,javafx.base" `
    @iconArg

Write-Host "Pronto. App em dist\PromoPingCodePad" -ForegroundColor Green
