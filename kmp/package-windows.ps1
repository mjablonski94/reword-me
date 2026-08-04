[CmdletBinding()]
param()

$ErrorActionPreference = "Stop"
$ProjectRoot = $PSScriptRoot
$VersionFile = Join-Path $ProjectRoot "desktop\app\src\main\resources\app.properties"
$VersionText = Get-Content -Raw $VersionFile
if ($VersionText -notmatch '(?m)^version=(\d+\.\d+\.\d+)\s*$') {
    throw "app.properties must contain a three-component version such as version=1.0.1."
}
$ExpectedVersion = $Matches[1]
$BuildFile = Join-Path $ProjectRoot "desktop\app\build.gradle.kts"
$BuildText = Get-Content -Raw $BuildFile

if ($BuildText -notmatch 'packageVersion\s*=\s*appVersion') {
    throw "desktop/app/build.gradle.kts must source packageVersion from app.properties."
}

if (-not $env:JAVA_HOME) {
    throw "JAVA_HOME must point to a 64-bit JDK 21 installation."
}

$JPackage = Join-Path $env:JAVA_HOME "bin\jpackage.exe"
if (-not (Test-Path $JPackage)) {
    throw "jpackage.exe was not found under JAVA_HOME. Install a full 64-bit JDK 21."
}

if (-not (Get-Command candle.exe -ErrorAction SilentlyContinue) -or
    -not (Get-Command light.exe -ErrorAction SilentlyContinue)) {
    throw "WiX Toolset 3.x (candle.exe and light.exe) must be installed and on PATH."
}

Push-Location $ProjectRoot
try {
    & ".\gradlew.bat" --no-daemon clean build :desktop:app:packageExe
    if ($LASTEXITCODE -ne 0) {
        throw "Gradle failed with exit code $LASTEXITCODE."
    }

    $ExpectedName = "RewordMe-$ExpectedVersion.exe"
    $Binaries = Join-Path $ProjectRoot "desktop\app\build\compose\binaries"
    $Candidates = @(Get-ChildItem -Path $Binaries -Recurse -File -Filter $ExpectedName)
    if ($Candidates.Count -ne 1) {
        throw "Expected exactly one $ExpectedName under $Binaries; found $($Candidates.Count)."
    }

    $Destination = Join-Path $ProjectRoot $ExpectedName
    Copy-Item -Force $Candidates[0].FullName $Destination
    $Hash = (Get-FileHash -Algorithm SHA256 $Destination).Hash.ToLowerInvariant()
    Set-Content -Encoding ascii -Path "$Destination.sha256" -Value "$Hash  $ExpectedName"

    Write-Host ""
    Write-Host "Windows installer ready:" -ForegroundColor Green
    Write-Host "  $Destination"
    Write-Host "  SHA-256: $Hash"
} finally {
    Pop-Location
}
