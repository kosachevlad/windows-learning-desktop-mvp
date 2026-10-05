# Run from any PowerShell location:
# & .\scripts\Build-Debug.ps1

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$projectRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
Push-Location -LiteralPath $projectRoot
try {
    . (Join-Path $PSScriptRoot 'Use-LocalToolchain.ps1')

    & .\gradlew.bat --no-daemon assembleDebug
    if ($LASTEXITCODE -ne 0) {
        throw "Debug build failed with exit code $LASTEXITCODE."
    }

    $apkPath = Join-Path $projectRoot 'app\build\outputs\apk\debug\app-debug.apk'
    if (-not (Test-Path -LiteralPath $apkPath)) {
        throw "Debug APK was not created: $apkPath"
    }

    $latestStage = Get-ChildItem -LiteralPath (Join-Path $projectRoot 'app\apk') -Filter 'stage*.apk' |
        Where-Object { $_.BaseName -match '^stage\d+$' } |
        Sort-Object { [int]$_.BaseName.Substring(5) } |
        Select-Object -Last 1

    Write-Host "APK: $apkPath"
    if ($latestStage) {
        Write-Host "Архівна копія: $($latestStage.FullName)"
    }
} finally {
    Pop-Location
}
