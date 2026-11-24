# Build 芬兰语动词闯关岛: unit tests + signed release APK, copied to release\FinnishVerbIsland.apk
#   .\build.ps1             tests + APK
#   .\build.ps1 -SkipTests  APK only
param([switch]$SkipTests)
$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot

if (-not $env:JAVA_HOME) { $env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr' }
if (-not (Test-Path local.properties)) {
    # local.properties is machine-specific and not in git
    $sdk = (Join-Path $env:LOCALAPPDATA 'Android\Sdk') -replace '\\', '/'
    "sdk.dir=$sdk" | Out-File local.properties -Encoding ascii
}

$tasks = @('assembleRelease')
if (-not $SkipTests) { $tasks = @('testDebugUnitTest') + $tasks }
& .\gradlew.bat @tasks --console=plain
if ($LASTEXITCODE -ne 0) { throw "Gradle failed ($LASTEXITCODE)" }

New-Item -ItemType Directory -Force release | Out-Null
Copy-Item app\build\outputs\apk\release\app-release.apk release\FinnishVerbIsland.apk -Force
Get-Item release\FinnishVerbIsland.apk | Select-Object Name, Length, LastWriteTime
