# Captures the running Cookie Launcher window at native resolution.
#
# Windows reports window rectangles in logical units when a process is not
# DPI-aware; on a 150% display that crops the right and bottom of the capture.
# Declaring DPI awareness first makes GetWindowRect return physical pixels.
Add-Type -AssemblyName System.Drawing

Add-Type @'
using System;
using System.Runtime.InteropServices;
public static class NativeDpi {
    [DllImport("user32.dll")] public static extern bool SetProcessDPIAware();
    [DllImport("user32.dll")] public static extern bool SetForegroundWindow(IntPtr h);
    [DllImport("user32.dll")] public static extern bool GetWindowRect(IntPtr h, out RECT r);
    [DllImport("user32.dll")] public static extern bool ShowWindow(IntPtr h, int c);
    [DllImport("dwmapi.dll")] public static extern int DwmGetWindowAttribute(
        IntPtr hwnd, int attr, out RECT value, int size);

    [StructLayout(LayoutKind.Sequential)]
    public struct RECT { public int Left, Top, Right, Bottom; }
}
'@

# 1 = process DPI aware, which must run before any window query.
[void][NativeDpi]::SetProcessDPIAware()

$proc = Get-Process CookieLauncher -ErrorAction SilentlyContinue |
        Where-Object { $_.MainWindowHandle -ne 0 } |
        Select-Object -First 1
if (-not $proc) { throw 'Cookie Launcher is not running with a visible window.' }

$handle = $proc.MainWindowHandle
[void][NativeDpi]::ShowWindow($handle, 9)
[void][NativeDpi]::SetForegroundWindow($handle)
Start-Sleep -Milliseconds 1400

# DWMWA_EXTENDED_FRAME_BOUNDS (9) excludes the invisible resize border that
# GetWindowRect includes, so the capture matches what the user sees.
$rect = New-Object NativeDpi+RECT
$ok = [NativeDpi]::DwmGetWindowAttribute($handle, 9, [ref]$rect, 16)
if ($ok -ne 0) { [void][NativeDpi]::GetWindowRect($handle, [ref]$rect) | Out-Null }

$width = $rect.Right - $rect.Left
$height = $rect.Bottom - $rect.Top
Write-Host "capture ${width}x${height} at $($rect.Left),$($rect.Top)"

$bitmap = New-Object System.Drawing.Bitmap($width, $height)
$graphics = [System.Drawing.Graphics]::FromImage($bitmap)
$graphics.CopyFromScreen($rect.Left, $rect.Top, 0, 0, $bitmap.Size)

$target = 'E:\cookie-lunacher\docs\wpf-shell.png'
$bitmap.Save($target, [System.Drawing.Imaging.ImageFormat]::Png)
$graphics.Dispose()
$bitmap.Dispose()
Write-Host "saved $target"
