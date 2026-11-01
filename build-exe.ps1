# HomeDesigner – EXE készítése (Windows)
# Használat:  powershell -ExecutionPolicy Bypass -File build-exe.ps1
$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot

$jdk = Get-ChildItem "$PSScriptRoot\.tools" -Directory -Filter "jdk-21*" -ErrorAction SilentlyContinue | Select-Object -First 1
if ($jdk) { $env:JAVA_HOME = $jdk.FullName }
if (-not $env:JAVA_HOME) { throw "JDK 21 szükséges (JAVA_HOME vagy .tools\jdk-21*)." }
$jpackage = Join-Path $env:JAVA_HOME "bin\jpackage.exe"
$appDir = Join-Path $PSScriptRoot "dist\HomeDesigner"
$exe = Join-Path $appDir "HomeDesigner.exe"
$running = Get-Process -Name HomeDesigner -ErrorAction SilentlyContinue | Where-Object { $_.Path -eq $exe }
if ($running) { throw "A HomeDesigner meg fut. Mentsd a munkat es zard be az alkalmazast az EXE ujraepitese elott." }

Write-Host "1/3 Fordítás (Maven)..."
cmd /d /c "call mvn -q -B clean package"
if ($LASTEXITCODE -ne 0) { throw "Maven build hiba" }

Write-Host "Eredeti furdoszobamodellek generalasa..."
& (Join-Path $env:JAVA_HOME "bin\java.exe") -cp "target\dist\*" hu.helmetsoft.homedesigner.io.BuiltinModels "objects\HelmetSoft_Furdoszoba"
if ($LASTEXITCODE -ne 0) { throw "Modellgeneralasi hiba" }
& (Join-Path $env:JAVA_HOME "bin\java.exe") -cp "target\dist\*" hu.helmetsoft.homedesigner.io.HomeModels "objects\HelmetSoft_Otthon"
if ($LASTEXITCODE -ne 0) { throw "Otthonmodell-generalasi hiba" }

Write-Host "2/3 jpackage..."
if (Test-Path $appDir) { Remove-Item -LiteralPath $appDir -Recurse -Force }
& $jpackage --type app-image --input target\dist --main-jar homedesigner.jar `
    --main-class hu.helmetsoft.homedesigner.Launcher --name HomeDesigner --app-version 1.0.0 `
    --vendor helmetsoft.com --description "Belsoepiteszeti 3D tervezo" `
    --java-options "-XX:MaxRAMPercentage=75" --java-options "-Xss16m" --dest dist
if ($LASTEXITCODE -ne 0) { throw "jpackage hiba" }

Write-Host "3/3 Mintamodellek másolása..."
if (Test-Path objects) { Copy-Item objects dist\HomeDesigner\objects -Recurse }
Compress-Archive -Path "$PSScriptRoot\objects\HelmetSoft_Furdoszoba" -DestinationPath "$PSScriptRoot\dist\HelmetSoft_Furdoszoba.zip" -Force

Write-Host "Kész: dist\HomeDesigner\HomeDesigner.exe"
