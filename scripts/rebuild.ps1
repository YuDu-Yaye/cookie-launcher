# Stops a running launcher, cleans a partial test install, then rebuilds.
#
# A running CookieLauncher holds bin\Debug\net10.0-windows\CookieLauncher.exe open,
# so MSBuild fails to copy the new apphost with a sharing violation. Stopping first
# is not optional; doing it here keeps the edit-build-click loop short.
param(
    [switch]$CleanVersions,
    [switch]$Run
)

$ErrorActionPreference = 'Stop'

$running = Get-Process CookieLauncher -ErrorAction SilentlyContinue
if ($running) {
    $running | Stop-Process -Force
    Start-Sleep -Milliseconds 700
    Write-Host "stopped $($running.Count) instance(s)"
}

if ($CleanVersions) {
    $versions = Join-Path $env:APPDATA 'CookieLauncher\versions'
    if (Test-Path $versions) {
        Remove-Item -Recurse -Force $versions
        Write-Host "cleared $versions"
    }
}

Push-Location $PSScriptRoot\..\src\CookieLauncher
try {
    dotnet build CookieLauncher.csproj -v quiet --nologo 2>&1 |
        Select-String -Pattern 'error|warning|已成功|Build succeeded' |
        ForEach-Object { $_.Line }
}
finally {
    Pop-Location
}

if ($Run) {
    $exe = Join-Path $PSScriptRoot '..\src\CookieLauncher\bin\Debug\net10.0-windows\CookieLauncher.exe'
    Start-Process -FilePath $exe
    Write-Host "started"
}
