using System.Windows;
using System.Windows.Media;
using CookieLauncher.Core;
using CookieLauncher.UI;

namespace CookieLauncher;

/// <summary>
/// Application entry point.
/// </summary>
/// <remarks>
/// The shell is WPF; the game engine underneath lives in <c>Game/</c> and <c>Core/</c>.
/// Keeping the two apart means the engine stays testable without a window, which is how
/// every install and download path is verified.
/// </remarks>
public partial class App : Application
{
    private void OnStartup(object sender, StartupEventArgs e)
    {
        Log.FilePath = AppPaths.LauncherLog;
        AppPaths.EnsureDirectories();

        // Tokens are written before the window loads, so there is no flash of the
        // placeholder palette from Theme.xaml.
        ThemeService.Apply(ThemeService.CurrentId);

        Log.Info($"{AppInfo.Name} {AppInfo.Version} starting");
        Log.Info($"Data directory: {AppPaths.Root}");
        Log.Info($"Portable mode: {AppPaths.IsPortable}");
    }
}
