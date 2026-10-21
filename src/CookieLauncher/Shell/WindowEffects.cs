using System.Runtime.InteropServices;
using System.Windows;
using System.Windows.Interop;

namespace CookieLauncher.Shell;

/// <summary>
/// Asks the Desktop Window Manager for a translucent backdrop behind the window.
/// </summary>
/// <remarks>
/// <para>
/// Windows 11 exposes <c>DwmSetWindowAttribute</c> with a system-backdrop attribute that
/// gives the window real acrylic/mica blur. Windows 10 has no such attribute, so there
/// the shell falls back to its own tinted gradient, which still reads as glass because
/// the ambient haze paints behind it.
/// </para>
/// <para>
/// Every call is best-effort: a failure changes nothing about correctness, so it is
/// swallowed rather than surfaced to the user.
/// </para>
/// </remarks>
internal static class WindowEffects
{
    // Undocumented but stable attribute ids used by Windows 11 22H2 and later.
    private const int DwmwaUseImmersiveDarkMode = 20;
    private const int DwmwaWindowCornerPreference = 33;
    private const int DwmwaSystemBackdropType = 38;

    private const int BackdropMica = 2;
    private const int BackdropAcrylic = 3;

    private const int CornerDefault = 0;

    [DllImport("dwmapi.dll", PreserveSig = true)]
    private static extern int DwmSetWindowAttribute(IntPtr hwnd, int attribute, ref int value, int size);

    /// <summary>Enables the darkest available backdrop for the window.</summary>
    public static void TryEnableBackdrop(Window window)
    {
        try
        {
            IntPtr handle = new WindowInteropHelper(window).Handle;
            if (handle == IntPtr.Zero)
            {
                return;
            }

            int dark = window.Background is null ? 1 : 1;
            _ = DwmSetWindowAttribute(handle, DwmwaUseImmersiveDarkMode, ref dark, sizeof(int));

            // Acrylic is preferred over mica: mica samples the desktop wallpaper, which
            // fights the ambient haze, while acrylic blurs whatever is behind the window.
            int backdrop = BackdropAcrylic;
            if (DwmSetWindowAttribute(handle, DwmwaSystemBackdropType, ref backdrop, sizeof(int)) != 0)
            {
                backdrop = BackdropMica;
                _ = DwmSetWindowAttribute(handle, DwmwaSystemBackdropType, ref backdrop, sizeof(int));
            }

            int corner = CornerDefault;
            _ = DwmSetWindowAttribute(handle, DwmwaWindowCornerPreference, ref corner, sizeof(int));
        }
        catch (DllNotFoundException)
        {
            // Older Windows without dwmapi: the composed gradient still applies.
        }
        catch (EntryPointNotFoundException)
        {
            // dwmapi present but the attribute is unsupported.
        }
    }
}
