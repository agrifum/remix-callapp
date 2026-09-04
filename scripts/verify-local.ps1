$ErrorActionPreference = "Stop"

$RepoRoot = (Resolve-Path "$PSScriptRoot/..").Path
Set-Location $RepoRoot

# Auto-detect JAVA_HOME if not set in environment
if (-not $env:JAVA_HOME) {
    if (Test-Path "C:/Program Files/Android/Android Studio/jbr") {
        $env:JAVA_HOME = "C:/Program Files/Android/Android Studio/jbr"
    }
}

# Auto-detect ANDROID_HOME if not set in environment
if (-not $env:ANDROID_HOME -and (Test-Path "$HOME/AppData/Local/Android/Sdk")) {
    $env:ANDROID_HOME = "$HOME/AppData/Local/Android/Sdk"
}

if (-not (Test-Path "debug.keystore")) {
    Write-Host "debug.keystore not found. Generating disposable debug keystore..."
    $keytoolCmd = Get-Command keytool -ErrorAction SilentlyContinue
    $keytoolPath = if ($keytoolCmd) {
        $keytoolCmd.Source
    } elseif ($env:JAVA_HOME -and (Test-Path "$env:JAVA_HOME/bin/keytool.exe")) {
        "$env:JAVA_HOME/bin/keytool.exe"
    } else {
        $null
    }

    if (-not $keytoolPath) {
        throw "keytool not found. Please ensure JDK 17 is installed or JAVA_HOME is set."
    }

    & $keytoolPath -genkeypair -v `
        -keystore debug.keystore `
        -alias androiddebugkey `
        -keyalg RSA `
        -keysize 2048 `
        -validity 10000 `
        -storepass android `
        -keypass android `
        -dname "CN=Android Debug,O=Android,C=US" `
        -storetype JKS
    if ($LASTEXITCODE -ne 0) {
        throw "keytool failed with exit code $LASTEXITCODE"
    }
    Write-Host "debug.keystore generated successfully."
}

Write-Host "Executing Gradle verification: assembleDebug, testDebugUnitTest, lintDebug..."
& .\gradlew.bat --no-daemon :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
if ($LASTEXITCODE -ne 0) {
    exit $LASTEXITCODE
}
