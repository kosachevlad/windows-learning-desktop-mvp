Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$projectRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
Push-Location -LiteralPath $projectRoot
try {
    . (Join-Path $PSScriptRoot 'Use-LocalToolchain.ps1')
    if (-not (Test-Path -LiteralPath '.\play-upload-key.jks')) {
        throw 'Missing release upload key: play-upload-key.jks'
    }
    if (-not $env:PLAY_UPLOAD_STORE_PASSWORD) {
        $secureStorePassword = Read-Host 'Пароль сховища ключів' -AsSecureString
        $env:PLAY_UPLOAD_STORE_PASSWORD = [System.Net.NetworkCredential]::new('', $secureStorePassword).Password
    }
    if (-not $env:PLAY_UPLOAD_KEY_PASSWORD) {
        $secureKeyPassword = Read-Host 'Пароль ключа' -AsSecureString
        $env:PLAY_UPLOAD_KEY_PASSWORD = [System.Net.NetworkCredential]::new('', $secureKeyPassword).Password
    }
    & .\gradlew.bat --no-daemon bundleRelease
    if ($LASTEXITCODE -ne 0) { throw "Release bundle build failed with exit code $LASTEXITCODE." }
    $bundle = Join-Path $projectRoot 'app\build\outputs\bundle\release\app-release.aab'
    if (-not (Test-Path -LiteralPath $bundle)) { throw "App Bundle was not created: $bundle" }
    Write-Host "App Bundle: $bundle"
} finally {
    Remove-Item Env:PLAY_UPLOAD_STORE_PASSWORD -ErrorAction SilentlyContinue
    Remove-Item Env:PLAY_UPLOAD_KEY_PASSWORD -ErrorAction SilentlyContinue
    Pop-Location
}
