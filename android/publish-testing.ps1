param(
    [string]$Testers = $env:FIREBASE_TESTERS
)

$ErrorActionPreference = "Stop"

$javaHome = "C:\Program Files\Android\Android Studio\jbr"
$androidHome = "C:\Users\Prasad\AppData\Local\Android\Sdk"
$gradle = "C:\Users\Prasad\.gradle\wrapper\dists\gradle-9.5.0-bin\bvnork1r7n8i6kp5cnkibsc9q\gradle-9.5.0\bin\gradle.bat"
$pnpm = "C:\Users\Prasad\.cache\codex-runtimes\codex-primary-runtime\dependencies\bin\fallback\pnpm.cmd"
$appId = "1:3540673696:android:a5090de7d45bdc40e055c5"
$apk = Join-Path $PSScriptRoot "app\build\outputs\apk\staging\debug\app-staging-debug.apk"
$notes = Join-Path $PSScriptRoot "firebase\release-notes.txt"
$localTesters = Join-Path $PSScriptRoot "firebase\testers.txt"

if (-not $Testers -and (Test-Path -LiteralPath $localTesters)) {
    $Testers = (Get-Content -Raw -LiteralPath $localTesters).Trim()
}

$env:JAVA_HOME = $javaHome
$env:ANDROID_HOME = $androidHome

& $gradle assembleStagingDebug
if ($LASTEXITCODE -ne 0) { throw "Android build failed." }

$arguments = @(
    "dlx", "firebase-tools", "appdistribution:distribute", $apk,
    "--app", $appId,
    "--release-notes-file", $notes
)

if ($Testers) {
    $arguments += @("--testers", $Testers)
}

& $pnpm @arguments
if ($LASTEXITCODE -ne 0) { throw "Firebase upload failed." }

Write-Host "Testing APK built and uploaded successfully."
