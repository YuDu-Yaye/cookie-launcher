# Captures every page of the running launcher by driving its navigation rail.
#
# The rail's six items are RadioButtons in document order, so selecting index N is
# equivalent to clicking the Nth destination. UI Automation is used rather than
# synthetic clicks because the window may not own the foreground, and a click at a
# screen coordinate would land on whatever is actually on top.
param(
    [string]$OutputDirectory = 'E:\cookie-lunacher\docs\screens'
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName UIAutomationClient, UIAutomationTypes, System.Drawing

Add-Type @'
using System;
using System.Runtime.InteropServices;
public static class Capture {
    [DllImport("user32.dll")] public static extern bool PrintWindow(IntPtr h, IntPtr hdc, uint flags);
    [DllImport("user32.dll")] public static extern bool SetProcessDPIAware();
    [DllImport("dwmapi.dll")] public static extern int DwmGetWindowAttribute(
        IntPtr h, int attr, out RECT value, int size);
    [StructLayout(LayoutKind.Sequential)]
    public struct RECT { public int Left, Top, Right, Bottom; }
}
'@

[void][Capture]::SetProcessDPIAware()
New-Item -ItemType Directory -Force -Path $OutputDirectory | Out-Null

$process = Get-Process CookieLauncher -ErrorAction SilentlyContinue |
           Where-Object { $_.MainWindowHandle -ne 0 } |
           Select-Object -First 1
if (-not $process) { throw 'Cookie Launcher is not running.' }

$handle = $process.MainWindowHandle
$root = [System.Windows.Automation.AutomationElement]::FromHandle($handle)

$radioCondition = New-Object System.Windows.Automation.PropertyCondition(
    [System.Windows.Automation.AutomationElement]::ControlTypeProperty,
    [System.Windows.Automation.ControlType]::RadioButton)
$rail = $root.FindAll([System.Windows.Automation.TreeScope]::Descendants, $radioCondition)
if ($rail.Count -lt 7) { throw "expected 7 destinations, found $($rail.Count)" }

# Order must match the rail in MainWindow.xaml exactly.
$pages = @('play', 'instances', 'versions', 'mods', 'accounts', 'logs', 'settings')

for ($i = 0; $i -lt $pages.Count; $i++) {
    $item = $rail.Item($i)
    $pattern = $item.GetCurrentPattern([System.Windows.Automation.SelectionItemPattern]::Pattern)
    $pattern.Select()
    Start-Sleep -Milliseconds 1200

    $rect = New-Object Capture+RECT
    [void][Capture]::DwmGetWindowAttribute($handle, 9, [ref]$rect, 16)
    $width = $rect.Right - $rect.Left
    $height = $rect.Bottom - $rect.Top

    $bitmap = New-Object System.Drawing.Bitmap($width, $height)
    $graphics = [System.Drawing.Graphics]::FromImage($bitmap)
    $dc = $graphics.GetHdc()
    # PW_RENDERFULLCONTENT captures DirectComposition content that a plain BitBlt misses.
    [void][Capture]::PrintWindow($handle, $dc, 2)
    $graphics.ReleaseHdc($dc)
    $graphics.Dispose()

    $target = Join-Path $OutputDirectory ("{0}.png" -f $pages[$i])
    $bitmap.Save($target, [System.Drawing.Imaging.ImageFormat]::Png)
    $bitmap.Dispose()
    Write-Host "captured $($pages[$i]) -> $target"
}

Write-Host 'done'
