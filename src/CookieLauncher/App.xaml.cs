using System.Windows;
using CookieLauncher.Core;
using CookieLauncher.UI;

namespace CookieLauncher;

/// <summary>
/// Application entry point.
/// </summary>
/// <remarks>
/// The shell is WPF; the game engine lives in <c>Game/</c> and <c>Core/</c>. Keeping the
/// two apart means the engine stays testable without a window, which is how every install
/// and download path is verified.
/// </remarks>
public partial class App : Application
{
    /// <summary>The loaded settings, shared by every page.</summary>
    public static Config Settings { get; private set; } = new();

    /// <summary>
    /// Loads settings and applies the colour scheme before the first window appears.
    /// </summary>
    /// <remarks>
    /// This has to be the <c>override</c>, not a <c>private void OnStartup(object, ...)</c>
    /// method. WPF only calls the override; a method with the event-handler shape is never
    /// wired up unless it is attached explicitly, which leaves every DynamicResource
    /// pointing at the placeholder palette in <c>Theme.xaml</c> instead of the generated
    /// scheme. That failure is silent: the window opens, the layout is correct, and only
    /// the colours are wrong.
    /// </remarks>
    protected override void OnStartup(StartupEventArgs e)
    {
        base.OnStartup(e);

        Log.FilePath = AppPaths.LauncherLog;
        AppPaths.EnsureDirectories();

        Settings = Config.Load(AppPaths.ConfigFile);
        LauncherState.Bootstrap();

        string applied = ThemeService.Apply(Settings.ThemeId);
        if (!string.Equals(applied, Settings.ThemeId, StringComparison.OrdinalIgnoreCase))
        {
            // The saved id no longer exists (renamed or removed scheme); adopt the fallback.
            Log.Warn($"Theme '{Settings.ThemeId}' is unknown; using '{applied}'");
            Settings.ThemeId = applied;
            Settings.Save();
        }

        Log.Info($"{AppInfo.Name} {AppInfo.Version} starting");
        Log.Info($"Data directory: {AppPaths.Root}");
        Log.Info($"Portable mode: {AppPaths.IsPortable}");
        Log.Info($"Theme: {ThemeService.CurrentId} ({ThemeService.CurrentName})");
    }

    /// <summary>Persists the settings after a page changes them.</summary>
    public static void SaveSettings()
    {
        Settings.Save();
        Log.Debug("Settings saved");
    }
}
