# Dot-source this file from PowerShell: . ./scripts/Use-LocalToolchain.ps1
$toolchainPath = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../work/toolchain'))
$localJdk = Get-ChildItem (Join-Path $toolchainPath 'java') -Directory -ErrorAction SilentlyContinue | Select-Object -First 1
if (-not $localJdk -or -not (Test-Path (Join-Path $toolchainPath 'sdk/platform-tools/adb.exe'))) {
    throw "Local toolchain is missing from $toolchainPath. See README.md for standard Android SDK setup."
}
$env:JAVA_HOME = $localJdk.FullName
$env:ANDROID_HOME = Join-Path $toolchainPath 'sdk'
$env:ANDROID_USER_HOME = Join-Path $toolchainPath 'android-user'
# Legacy SDK_HOME conflicts with USER_HOME when they resolve to different folders.
$env:ANDROID_SDK_HOME = $null
$env:GRADLE_USER_HOME = Join-Path $toolchainPath 'gradle-cache'
# Keep Robolectric paths below Windows path limits in this deeply nested local checkout.
$env:WINDOWS_LEARNING_TEST_ROOT = [IO.Path]::GetFullPath((Join-Path $toolchainPath '../r'))
$env:Path = "$env:JAVA_HOME/bin;$env:ANDROID_HOME/platform-tools;$env:Path"
Write-Host "JDK: $env:JAVA_HOME"
Write-Host "SDK: $env:ANDROID_HOME"
