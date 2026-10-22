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
/// The launcher shell, arranged like PCL2 and rendered in Material 3.
/// </summary>
/// <remarks>
/// <para>
/// Left column: login switch, avatar, account name, and the large launch block pinned to
/// the bottom with the two version buttons under it. Top: a compact strip whose centre is a
/// pill navigation group. Right: the page area.
/// </para>
/// <para>
/// The window is undecorated, so the top strip doubles as the title bar.
/// </para>
/// </remarks>
public partial class MainWindow : Window
{
    private readonly Dictionary<string, PageBase> _pages = [];
    private string _currentPage = "home";

    public MainWindow()
    {
        InitializeComponent();
        BuildPages();

        ThemeService.ThemeChanged += OnThemeChanged;
        OnThemeChanged(ThemeCatalogue.Get(ThemeService.CurrentId));

        string arch = Environment.Is64BitProcess ? "x64" : "x86";
        StatusRight.Text = $".NET {Environment.Version.ToString(2)}   windows/{arch}";

        RefreshAccount();
        RefreshLaunchBlock();
        Navigate("home");
        LaunchSurface.MouseLeftButtonUp += (_, _) => LaunchGame();

        Log.Info("Shell ready");
    }

    /// <summary>The live window.</summary>
    public static MainWindow? Instance { get; private set; }

    private void BuildPages()
    {
        _pages["home"] = new PlayPage();
        _pages["instances"] = new InstancesPage();
        _pages["versions"] = new VersionsPage();
        _pages["mods"] = new ModsPage();
        _pages["accounts"] = new AccountsPage();
        _pages["log"] = new LogPage();
        _pages["settings"] = new SettingsPage();
        _pages["more"] = new MorePage();
    }

    /// <summary>Switches page and syncs the pill selection.</summary>
    public void Navigate(string id)
    {
        if (!_pages.TryGetValue(id, out PageBase? page))
        {
            return;
        }

        _currentPage = id;
        PageHost.Content = page;
        page.OnShown();

        RadioButton? tab = id switch
        {
            "instances" => NavInstances,
            "versions" => NavVersions,
            "settings" => NavSettings,
            "more" => NavMore,
            _ => NavPlay,
        };
        if (tab is not null && tab.IsChecked != true)
        {
            tab.IsChecked = true;
        }

        RefreshLaunchBlock();
    }

    private void OnNavChecked(object sender, RoutedEventArgs e)
    {
        if (sender is not RadioButton { Tag: string id } button || button.IsChecked != true)
        {
            return;
        }
        // The first Checked fires while XAML is still loading the tree.
        if (PageHost is null)
        {
            return;
        }
        Navigate(id);
    }

    // ------------------------------------------------------------------
    // Left column
    // ------------------------------------------------------------------

    /// <summary>Repaints the account card from the store.</summary>
    public void RefreshAccount()
    {
        Account? account = LauncherState.Current.SelectedAccount;
        if (account is null)
        {
            AvatarInitials.Text = "?";
            AccountName.Text = "No account";
            AccountKind.Text = "Add one to play";
            SwitchAccountButton.Content = "Add account";
            return;
        }

        AvatarInitials.Text = Initials(account.Username);
        AccountName.Text = account.Username;
        AccountKind.Text = account.Type switch
        {
            "authlib" => "Authlib account",
            "microsoft" => "Microsoft account",
            _ => "Offline profile",
        };
        SwitchAccountButton.Content = "Switch account";
        LoginOffline.IsChecked = account.Type == "offline";
        LoginOnline.IsChecked = account.Type != "offline";
    }

    private static string Initials(string name)
    {
        if (string.IsNullOrWhiteSpace(name))
        {
            return "?";
        }
        string trimmed = name.Trim();
        return trimmed.Length == 1
            ? trimmed.ToUpperInvariant()
            : (trimmed[0].ToString() + trimmed[^1]).ToUpperInvariant();
    }

    /// <summary>Repaints the bottom launch block from the selected instance.</summary>
    public void RefreshLaunchBlock()
    {
        Instance? instance = LauncherState.Current.Instance;
        if (instance is null)
        {
            LaunchVersion.Text = "No instance selected";
            LaunchLabel.Text = "Launch game";
            return;
        }

        bool installed = Game.Install.GameInstaller.IsInstalled(instance.Version);
        LaunchVersion.Text = string.IsNullOrWhiteSpace(instance.Version)
            ? "Pick a version first"
            : instance.Subtitle() + (installed ? "" : "  ·  not installed");
        LaunchLabel.Text = installed ? "Launch game" : "Install first";
    }

    private void OnLoginTypeChanged(object sender, RoutedEventArgs e)
    {
        // XAML raises Checked during InitializeComponent, before the named elements are
        // assigned; guard so the window can finish loading.
        if (StatusText is null || LoginOnline is null)
        {
            return;
        }

        if (LoginOnline.IsChecked == true)
        {
            Account? account = LauncherState.Current.SelectedAccount;
            if (account is null || !account.IsOnline)
            {
                SetStatus("Add a Microsoft or authlib account to play online.");
            }
        }
    }

    private void OnSwitchAccount(object sender, RoutedEventArgs e)
    {
        Navigate("accounts");
        SetStatus("Choose or add an account.");
    }

    private void OnVersionSelect(object sender, RoutedEventArgs e)
    {
        Navigate("versions");
        SetStatus("Pick a version to install.");
    }

    private void OnVersionSettings(object sender, RoutedEventArgs e)
    {
        Navigate("instances");
        SetStatus("Select an instance to configure it.");
    }

    private void LaunchGame()
    {
        Instance? instance = LauncherState.Current.Instance;
        if (instance is null)
        {
            Navigate("versions");
            SetStatus("Create an instance and install a version first.");
            return;
        }
        if (!Game.Install.GameInstaller.IsInstalled(instance.Version))
        {
            Navigate("versions");
            SetStatus("That version is not installed yet.");
            return;
        }
        SetStatus("Launch pipeline wiring is the next step.");
    }

    // ------------------------------------------------------------------
    // Theme and shell plumbing
    // ------------------------------------------------------------------

    private void OnThemeChanged(SchemeDefinition definition)
    {
        MaterialScheme scheme = ThemeService.Scheme;
        Ambient.SetColors(
            ToColor(scheme.Primary),
            ToColor(scheme.Tertiary),
            ToColor(scheme.Secondary));
        Ambient.SetIntensity(definition.Dark ? 1.0 : 0.72);

        Background = Brushes.Transparent;
        RootBorder.Background = (Brush)FindResource("GlassTintBrush");
        RootBorder.BorderBrush = (Brush)FindResource("GlassStrokeBrush");
    }

    private static Color ToColor(int argb) => Color.FromArgb(
        (byte)((argb >> 24) & 0xFF),
        (byte)((argb >> 16) & 0xFF),
        (byte)((argb >> 8) & 0xFF),
        (byte)(argb & 0xFF));

    private void OnSourceInitialized(object? sender, EventArgs e) =>
        WindowEffects.TryEnableBackdrop(this);

    private void OnWindowStateChanged(object? sender, EventArgs e) =>
        MaximiseButton.Content = WindowState == WindowState.Maximized ? "\uE923" : "\uE922";

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

    private void ToggleMaximise() => WindowState =
        WindowState == WindowState.Maximized ? WindowState.Normal : WindowState.Maximized;

    private void OnClose(object sender, RoutedEventArgs e) => Close();

    /// <summary>Reports a message to the status strip from any page.</summary>
    public static void ReportStatus(string message)
    {
        MainWindow? window = Instance;
        window?.Dispatcher.Invoke(() => window.SetStatus(message));
    }

    public void SetStatus(string message)
    {
        if (StatusText is not null)
        {
            StatusText.Text = message;
        }
    }

    /// <summary>Shows a transient message near the bottom edge.</summary>
    public void ShowSnackbar(string message)
    {
        SnackbarHost.Content = new Border
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

    protected override void OnActivated(EventArgs e)
    {
        base.OnActivated(e);
        Instance = this;
        Ambient.SetRunning(true);
    }

    protected override void OnDeactivated(EventArgs e)
    {
        base.OnDeactivated(e);
        // The haze is invisible in the background; stop it rather than burn a render thread.
        Ambient.SetRunning(false);
    }

    protected override void OnClosing(CancelEventArgs e)
    {
        Log.Info("Shell closing");
        base.OnClosing(e);
    }
}
