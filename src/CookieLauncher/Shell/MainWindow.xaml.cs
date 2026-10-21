using System.ComponentModel;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Input;
using System.Windows.Media;
using CookieLauncher.Core;
using CookieLauncher.UI;
using CookieLauncher.UI.Pages;

namespace CookieLauncher.Shell;

/// <summary>
/// The application shell: app bar, navigation rail, page host and status strip.
/// </summary>
/// <remarks>
/// The window is undecorated, so the app bar is also the title bar. Resize is handled by
/// <c>WindowChrome.ResizeBorderThickness</c> rather than custom hit-testing, which keeps
/// the platform's own edge behaviour (including snap and double-click maximise) intact.
/// </remarks>
public partial class MainWindow : Window
{
    private readonly Dictionary<string, PageBase> _pages = [];
    private string _currentPage = "home";

    public MainWindow()
    {
        InitializeComponent();
        BuildPages();
        Navigate("home");

        ThemeService.ThemeChanged += OnThemeChanged;
        {
        }

        StatusRight.Text = $"Java {Environment.Version}   windows/{Environment.Is64BitProcess switch { true => "x64", false => "x86" }}";
        Log.Info("Shell ready");
    }

    /// <summary>Raised when the app wants the status strip updated.</summary>

    private void BuildPages()
    {
        _pages["home"] = new PlayPage();
        _pages["versions"] = new VersionsPage();
        _pages["mods"] = new ModsPage();
        _pages["accounts"] = new AccountsPage();
        _pages["log"] = new LogPage();
        _pages["settings"] = new SettingsPage();

    }

    /// <summary>Switches to a page and updates the app bar title and rail selection.</summary>
    public void Navigate(string id)
    {
        if (!_pages.TryGetValue(id, out PageBase? page))
        {
            return;
        }

        _currentPage = id;
        PageHost.Content = page;
        page.OnShown();
        DestinationTitle.Text = TitleFor(id);

        RadioButton? item = id switch
        {
            "versions" => NavVersions,
            "mods" => NavMods,
            "accounts" => NavAccounts,
            "log" => NavLogs,
            "settings" => NavSettings,
            _ => NavPlay,
        };
        if (item is not null && item.IsChecked != true)
        {
            item.IsChecked = true;
        }
    }

    private static string TitleFor(string id) => id switch
    {
        "versions" => "Versions",
        "mods" => "Mods",
        "accounts" => "Accounts",
        "log" => "Logs",
        "settings" => "Settings",
        _ => "Play",
    };

    private void OnNavChecked(object sender, RoutedEventArgs e)
    {
        if (sender is RadioButton { Tag: string id } button && button.IsChecked == true)
        {
            Navigate(id);
        }
    }

    private void OnThemeChanged(SchemeDefinition definition)
    {
        // The haze is tinted from the generated scheme, not from a hardcoded palette.
        MaterialScheme scheme = ThemeService.Scheme;
        Ambient.SetColors(
            ToColor(scheme.Primary),
            ToColor(scheme.Tertiary),
            ToColor(scheme.Secondary));
        Ambient.SetIntensity(definition.Dark ? 1.0 : 0.72);
        ResetBackground();
    }

    private void ResetBackground()
    {
        // The window background must stay transparent so the DWM glass shows through;
        // only the tint brush changes.
        Background = Brushes.Transparent;
        RootBorder.Background = (Brush)FindResource("GlassTintBrush");
        RootBorder.BorderBrush = (Brush)FindResource("GlassStrokeBrush");
    }

    private static Color ToColor(int argb) => Color.FromArgb(
        (byte)((argb >> 24) & 0xFF),
        (byte)((argb >> 16) & 0xFF),
        (byte)((argb >> 8) & 0xFF),
        (byte)(argb & 0xFF));

    private void OnSourceInitialized(object? sender, EventArgs e)
    {
        // Ask DWM for the acrylic backdrop. Failure is tolerated: the tint over a solid
        // surface still reads as glass.
        WindowEffects.TryEnableBackdrop(this);
    }

    private void OnWindowStateChanged(object? sender, EventArgs e)
    {
        MaximiseButton.Content = WindowState == WindowState.Maximized ? "\uE923" : "\uE922";
    }

    private void OnTitleBarMouseLeftButtonDown(object sender, MouseButtonEventArgs e)
    {
        if (e.ClickCount == 2)
        {
            ToggleMaximise();
            return;
        }
        if (e.ButtonState == MouseButtonState.Pressed)
        {
            DragMove();
        }
    }

    private void OnMinimise(object sender, RoutedEventArgs e) =>
        WindowState = WindowState.Minimized;

    private void OnMaximise(object sender, RoutedEventArgs e) => ToggleMaximise();

    private void ToggleMaximise() =>
        WindowState = WindowState == WindowState.Maximized
            ? WindowState.Normal
            : WindowState.Maximized;

    private void OnClose(object sender, RoutedEventArgs e) => Close();

    protected override void OnClosing(CancelEventArgs e)
    {
        Log.Info("Shell closing");
        base.OnClosing(e);
    }

    /// <summary>Shows a transient message near the bottom edge.</summary>
    public void ShowSnackbar(string message)
    {
        var snackbar = new Border
        {
            Background = (Brush)FindResource("InverseSurfaceBrush"),
            CornerRadius = new CornerRadius(8),
            Padding = new Thickness(20, 12, 20, 12),
            Child = new TextBlock
            {
                Text = message,
                Foreground = (Brush)FindResource("OnInverseSurfaceBrush"),
                FontSize = 13,
            },
        };
        SnackbarHost.Content = snackbar;

        var timer = new System.Windows.Threading.DispatcherTimer
        {
            Interval = TimeSpan.FromSeconds(3),
        };
        timer.Tick += (_, _) =>
        {
            timer.Stop();
            SnackbarHost.Content = null;
        };
        timer.Start();
    }

    /// <summary>Sets the left-hand status text.</summary>
    public void SetStatus(string message) => StatusText.Text = message;

    /// <summary>Reports a message to the shell status strip from any page.</summary>
    public static void ReportStatus(string message)
    {
        MainWindow? window = Instance;
        window?.Dispatcher.Invoke(() => window.SetStatus(message));
    }

    /// <summary>The single live window.</summary>
    public static MainWindow? Instance { get; private set; }

    protected override void OnActivated(EventArgs e)
    {
        base.OnActivated(e);
        Instance = this;
        Ambient.SetRunning(true);
    }

    protected override void OnDeactivated(EventArgs e)
    {
        base.OnDeactivated(e);
        // Stop the drift while the window is in the background: it is invisible, and the
        // animation would otherwise keep a render thread awake for nothing.
        Ambient.SetRunning(false);
    }
}
