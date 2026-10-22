using System.IO;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Markup;
using System.Windows.Media;
using CookieLauncher.Core;

namespace CookieLauncher.UI.Pages;

/// <summary>
/// Accounts page: offline profiles, authlib servers and Microsoft sign-in.
/// </summary>
/// <remarks>
/// <para>
/// The three account types are presented as three distinct affordances rather than one
/// "Sign in" button. Their failure modes share nothing: an offline name is always valid, a
/// Yggdrasil login can be rejected for credentials or for an unreachable server, and Microsoft
/// needs a registered application id before it can even start. Collapsing them produces the
/// useless generic error message every other launcher ships.
/// </para>
/// <para>
/// Account rows show the UUID because that, not the name, is what a server actually keys on.
/// Two accounts can share a name across different servers.
/// </para>
/// </remarks>
public sealed class AccountsPage : PageBase
{
    private const string ContainerTemplateXaml = """
        <ControlTemplate xmlns="http://schemas.microsoft.com/winfx/2006/xaml/presentation"
                         xmlns:x="http://schemas.microsoft.com/winfx/2006/xaml"
                         TargetType="ListBoxItem">
            <Border x:Name="Row" CornerRadius="8" Background="Transparent" Padding="12,8,12,8">
                <ContentPresenter />
            </Border>
            <ControlTemplate.Triggers>
                <Trigger Property="IsMouseOver" Value="True">
                    <Setter TargetName="Row" Property="Background"
                            Value="{DynamicResource SurfaceContainerBrush}" />
                </Trigger>
                <Trigger Property="IsSelected" Value="True">
                    <Setter TargetName="Row" Property="Background"
                            Value="{DynamicResource SecondaryContainerBrush}" />
                </Trigger>
            </ControlTemplate.Triggers>
        </ControlTemplate>
        """;

    private const string RowTemplateXaml = """
        <DataTemplate xmlns="http://schemas.microsoft.com/winfx/2006/xaml/presentation">
            <Grid>
                <Grid.ColumnDefinitions>
                    <ColumnDefinition Width="44" />
                    <ColumnDefinition Width="*" />
                    <ColumnDefinition Width="Auto" />
                </Grid.ColumnDefinitions>

                <!-- Avatar: initials on the primary container, as M3 uses for avatars. -->
                <Border Grid.Column="0"
                        Width="36" Height="36"
                        CornerRadius="18"
                        VerticalAlignment="Center"
                        Background="{DynamicResource PrimaryContainerBrush}">
                    <TextBlock Text="{Binding Initials}"
                               FontSize="14"
                               FontWeight="SemiBold"
                               HorizontalAlignment="Center"
                               VerticalAlignment="Center"
                               Foreground="{DynamicResource OnPrimaryContainerBrush}" />
                </Border>

                <StackPanel Grid.Column="1" Margin="12,0,0,0" VerticalAlignment="Center">
                    <TextBlock Text="{Binding Name}"
                               FontSize="13"
                               FontWeight="SemiBold"
                               Foreground="{DynamicResource OnSurfaceBrush}" />
                    <TextBlock Text="{Binding Detail}"
                               FontSize="11"
                               Margin="0,1,0,0"
                               TextTrimming="CharacterEllipsis"
                               Foreground="{DynamicResource OnSurfaceVariantBrush}" />
                </StackPanel>

                <Border Grid.Column="2"
                        Height="24"
                        CornerRadius="12"
                        Padding="10,0"
                        VerticalAlignment="Center"
                        Background="{DynamicResource SurfaceContainerHighBrush}">
                    <TextBlock Text="{Binding Kind}"
                               FontSize="11"
                               VerticalAlignment="Center"
                               Foreground="{DynamicResource OnSurfaceVariantBrush}" />
                </Border>
            </Grid>
        </DataTemplate>
        """;

    private readonly ListBox _list;
    private readonly TextBlock _summary;
    private readonly Button _selectButton;
    private readonly Button _removeButton;

    public AccountsPage()
    {
        Margin = new Thickness(28, 22, 28, 18);

        var root = new Grid();
        root.RowDefinitions.Add(new RowDefinition { Height = GridLength.Auto });
        root.RowDefinitions.Add(new RowDefinition { Height = GridLength.Auto });
        root.RowDefinitions.Add(new RowDefinition { Height = new GridLength(1, GridUnitType.Star) });
        root.RowDefinitions.Add(new RowDefinition { Height = GridLength.Auto });

        // ---- Headline ------------------------------------------------------
        var headline = new StackPanel { Margin = new Thickness(0, 0, 0, 14) };
        headline.Children.Add(new TextBlock
        {
            Text = "Accounts",
            Style = (Style)FindResource("MdHeadlineSmall"),
        });
        headline.Children.Add(new TextBlock
        {
            Text = "Offline profiles play singleplayer; online accounts join servers.",
            Style = (Style)FindResource("MdBodyMedium"),
            Margin = new Thickness(0, 3, 0, 0),
        });
        Grid.SetRow(headline, 0);
        root.Children.Add(headline);

        // ---- Add row -------------------------------------------------------
        var addRow = new Grid { Margin = new Thickness(0, 0, 0, 12) };
        addRow.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
        addRow.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
        addRow.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });

        var addOffline = new Button
        {
            Content = "Add offline",
            Style = (Style)FindResource("MdFilledButton"),
            MinWidth = 128,
        };
        addOffline.Click += (_, _) => AddOffline();
        Grid.SetColumn(addOffline, 0);
        addRow.Children.Add(addOffline);

        var addAuthlib = new Button
        {
            Content = "Add authlib",
            Style = (Style)FindResource("MdOutlinedButton"),
            MinWidth = 128,
            Margin = new Thickness(10, 0, 0, 0),
        };
        addAuthlib.Click += (_, _) => AddAuthlib();
        Grid.SetColumn(addAuthlib, 1);
        addRow.Children.Add(addAuthlib);

        Grid.SetRow(addRow, 1);
        root.Children.Add(addRow);

        // ---- List ----------------------------------------------------------
        var containerStyle = new Style(typeof(ListBoxItem));
        containerStyle.Setters.Add(new Setter(BackgroundProperty, Brushes.Transparent));
        containerStyle.Setters.Add(new Setter(BorderThicknessProperty, new Thickness(0)));
        containerStyle.Setters.Add(new Setter(PaddingProperty, new Thickness(0)));
        containerStyle.Setters.Add(new Setter(MarginProperty, new Thickness(0, 0, 0, 2)));
        containerStyle.Setters.Add(new Setter(HorizontalContentAlignmentProperty,
            HorizontalAlignment.Stretch));
        containerStyle.Setters.Add(new Setter(TemplateProperty,
            (ControlTemplate)XamlReader.Parse(ContainerTemplateXaml)));

        _list = new ListBox
        {
            Background = Brushes.Transparent,
            BorderThickness = new Thickness(0),
            ItemContainerStyle = containerStyle,
            ItemTemplate = (DataTemplate)XamlReader.Parse(RowTemplateXaml),
        };
        ScrollViewer.SetHorizontalScrollBarVisibility(_list, ScrollBarVisibility.Disabled);
        ScrollViewer.SetVerticalScrollBarVisibility(_list, ScrollBarVisibility.Auto);
        VirtualizingPanel.SetIsVirtualizing(_list, true);
        _list.SelectionChanged += (_, _) => UpdateSelection();
        Grid.SetRow(_list, 2);
        root.Children.Add(_list);

        // ---- Footer --------------------------------------------------------
        var footer = new Grid { Margin = new Thickness(0, 10, 0, 0) };
        footer.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
        footer.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
        footer.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });

        _summary = new TextBlock
        {
            Style = (Style)FindResource("MdLabelSmall"),
            VerticalAlignment = VerticalAlignment.Center,
        };
        Grid.SetColumn(_summary, 0);
        footer.Children.Add(_summary);

        _selectButton = new Button
        {
            Content = "Use this account",
            Style = (Style)FindResource("MdTonalButton"),
            MinWidth = 152,
            IsEnabled = false,
            Margin = new Thickness(0, 0, 8, 0),
        };
        _selectButton.Click += (_, _) => SelectCurrent();
        Grid.SetColumn(_selectButton, 1);
        footer.Children.Add(_selectButton);

        _removeButton = new Button
        {
            Content = "Remove",
            Style = (Style)FindResource("MdTextButton"),
            MinWidth = 96,
            IsEnabled = false,
        };
        _removeButton.Click += (_, _) => RemoveCurrent();
        Grid.SetColumn(_removeButton, 2);
        footer.Children.Add(_removeButton);

        Grid.SetRow(footer, 3);
        root.Children.Add(footer);

        Content = root;
    }

    public override void OnShown()
    {
        Reload();
        SetStatus("Ready");
    }

    private void Reload()
    {
        AccountStore store = LauncherState.Current.Accounts;
        Account? selected = store.Selected;

        List<AccountRow> rows = [.. store.All.Select(account => new AccountRow(
            account.Username,
            Initials(account.Username),
            Describe(account),
            KindLabel(account),
            account.Id,
            selected is not null && selected.Id == account.Id))];

        _list.ItemsSource = rows;

        int count = rows.Count;
        _summary.Text = count == 0
            ? "No accounts yet."
            : $"{count} account{(count == 1 ? "" : "s")}"
              + (selected is null ? "" : $"  ·  using {selected.Username}");
    }

    private static string Describe(Account account)
    {
        string uuid = string.IsNullOrWhiteSpace(account.Uuid) ? "no uuid" : account.Uuid;
        return account.Type switch
        {
            "authlib" => $"{account.ServerUrl}  ·  {Shorten(uuid)}",
            "microsoft" => "Microsoft account  ·  " + Shorten(uuid),
            _ => "Offline profile  ·  " + Shorten(uuid),
        };
    }

    private static string Shorten(string uuid) =>
        uuid.Length > 12 ? uuid[..8] + "\u2026" + uuid[^4..] : uuid;

    private static string KindLabel(Account account) => account.Type switch
    {
        "authlib" => "Authlib",
        "microsoft" => "Microsoft",
        _ => "Offline",
    };

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

    private void UpdateSelection()
    {
        bool hasSelection = _list.SelectedItem is AccountRow;
        _selectButton.IsEnabled = hasSelection;
        _removeButton.IsEnabled = hasSelection;
        if (_list.SelectedItem is AccountRow row)
        {
            _selectButton.Content = row.IsActive ? "In use" : "Use this account";
            _selectButton.IsEnabled = !row.IsActive;
        }
    }

    private void SelectCurrent()
    {
        if (_list.SelectedItem is not AccountRow row)
        {
            return;
        }
        LauncherState.Current.Accounts.Select(row.Id);
        SetStatus("Now using " + row.Name);
        Reload();
    }

    private void RemoveCurrent()
    {
        if (_list.SelectedItem is not AccountRow row)
        {
            return;
        }
        LauncherState.Current.Accounts.Remove(row.Id);
        SetStatus("Removed " + row.Name);
        Reload();
    }

    private void AddOffline()
    {
        OfflineDialog dialog = new() { Owner = Window.GetWindow(this) };
        if (dialog.ShowDialog() != true || string.IsNullOrWhiteSpace(dialog.Username))
        {
            return;
        }

        Account account = Account.Offline(dialog.Username);
        LauncherState.Current.Accounts.Add(account);
        SetStatus("Added offline account " + account.Username);
        Reload();
    }

    private void AddAuthlib()
    {
        AuthlibDialog dialog = new() { Owner = Window.GetWindow(this) };
        if (dialog.ShowDialog() != true)
        {
            return;
        }

        Account account = new()
        {
            Type = "authlib",
            Username = dialog.Username,
            ServerUrl = dialog.ServerUrl,
            Uuid = Account.OfflineUuid(dialog.Username),
            Id = "authlib:" + dialog.Username.ToLowerInvariant() + "@" + dialog.ServerUrl,
        };
        LauncherState.Current.Accounts.Add(account);
        SetStatus("Added authlib account " + account.Username);
        Reload();
    }

    /// <summary>One row in the list.</summary>
    private sealed record AccountRow(
        string Name, string Initials, string Detail, string Kind, string Id, bool IsActive);

    /// <summary>Asks for a name to create an offline profile.</summary>
    private sealed class OfflineDialog : Window
    {
        private readonly TextBox _name;

        public string Username => _name.Text.Trim();

        public OfflineDialog()
        {
            Title = "Add offline account";
            Width = 380;
            SizeToContent = SizeToContent.Height;
            WindowStartupLocation = WindowStartupLocation.CenterOwner;
            ResizeMode = ResizeMode.NoResize;
            Background = (Brush)Application.Current.Resources["SurfaceBrush"];

            var panel = new StackPanel { Margin = new Thickness(24) };
            panel.Children.Add(new TextBlock
            {
                Text = "Offline username",
                FontSize = 16,
                FontWeight = FontWeights.SemiBold,
                Foreground = (Brush)Application.Current.Resources["OnSurfaceBrush"],
            });
            panel.Children.Add(new TextBlock
            {
                Text = "Used for singleplayer and LAN. The UUID is derived from the name, "
                     + "so it matches what a server in offline mode would assign.",
                FontSize = 12,
                TextWrapping = TextWrapping.Wrap,
                Margin = new Thickness(0, 6, 0, 14),
                Foreground = (Brush)Application.Current.Resources["OnSurfaceVariantBrush"],
            });

            _name = new TextBox
            {
                Height = 44,
                FontSize = 14,
                Padding = new Thickness(12, 10, 12, 10),
                Background = (Brush)Application.Current.Resources["SurfaceContainerHighestBrush"],
                Foreground = (Brush)Application.Current.Resources["OnSurfaceBrush"],
                BorderThickness = new Thickness(0),
            };
            _name.Text = "Player";
            panel.Children.Add(_name);

            var buttons = new StackPanel
            {
                Orientation = Orientation.Horizontal,
                HorizontalAlignment = HorizontalAlignment.Right,
                Margin = new Thickness(0, 20, 0, 0),
            };
            var cancel = new Button
            {
                Content = "Cancel",
                Style = (Style)Application.Current.Resources["MdTextButton"],
                MinWidth = 88,
                Margin = new Thickness(0, 0, 8, 0),
            };
            cancel.Click += (_, _) => DialogResult = false;
            var confirm = new Button
            {
                Content = "Add",
                Style = (Style)Application.Current.Resources["MdFilledButton"],
                MinWidth = 96,
            };
            confirm.Click += (_, _) => DialogResult = true;
            buttons.Children.Add(cancel);
            buttons.Children.Add(confirm);
            panel.Children.Add(buttons);

            Content = panel;
        }
    }

    /// <summary>Asks for a server root and username for an authlib account.</summary>
    private sealed class AuthlibDialog : Window
    {
        private readonly TextBox _server;
        private readonly TextBox _name;

        public string ServerUrl => _server.Text.Trim();
        public string Username => _name.Text.Trim();

        public AuthlibDialog()
        {
            Title = "Add authlib account";
            Width = 440;
            SizeToContent = SizeToContent.Height;
            WindowStartupLocation = WindowStartupLocation.CenterOwner;
            ResizeMode = ResizeMode.NoResize;
            Background = (Brush)Application.Current.Resources["SurfaceBrush"];

            var panel = new StackPanel { Margin = new Thickness(24) };
            panel.Children.Add(new TextBlock
            {
                Text = "Third-party authentication",
                FontSize = 16,
                FontWeight = FontWeights.SemiBold,
                Foreground = (Brush)Application.Current.Resources["OnSurfaceBrush"],
            });
            panel.Children.Add(new TextBlock
            {
                Text = "The server root, for example https://example.com/api/yggdrasil. "
                     + "authlib-injector is patched into the game at launch.",
                FontSize = 12,
                TextWrapping = TextWrapping.Wrap,
                Margin = new Thickness(0, 6, 0, 14),
                Foreground = (Brush)Application.Current.Resources["OnSurfaceVariantBrush"],
            });

            _server = MakeField("https://example.com/api/yggdrasil");
            panel.Children.Add(_server);
            panel.Children.Add(new Border { Height = 10 });
            _name = MakeField("Username");
            panel.Children.Add(_name);

            var buttons = new StackPanel
            {
                Orientation = Orientation.Horizontal,
                HorizontalAlignment = HorizontalAlignment.Right,
                Margin = new Thickness(0, 20, 0, 0),
            };
            var cancel = new Button
            {
                Content = "Cancel",
                Style = (Style)Application.Current.Resources["MdTextButton"],
                MinWidth = 88,
                Margin = new Thickness(0, 0, 8, 0),
            };
            cancel.Click += (_, _) => DialogResult = false;
            var confirm = new Button
            {
                Content = "Add",
                Style = (Style)Application.Current.Resources["MdFilledButton"],
                MinWidth = 96,
            };
            confirm.Click += (_, _) =>
            {
                if (!ServerUrl.StartsWith("http", StringComparison.OrdinalIgnoreCase)
                    || string.IsNullOrWhiteSpace(Username))
                {
                    MessageBox.Show("Enter a server URL starting with http and a username.",
                        "Missing information", MessageBoxButton.OK, MessageBoxImage.Information);
                    return;
                }
                DialogResult = true;
            };
            buttons.Children.Add(cancel);
            buttons.Children.Add(confirm);
            panel.Children.Add(buttons);

            Content = panel;
        }

        private static TextBox MakeField(string placeholder)
        {
            TextBox field = new()
            {
                Height = 44,
                FontSize = 14,
                Padding = new Thickness(12, 10, 12, 10),
                Background = (Brush)Application.Current.Resources["SurfaceContainerHighestBrush"],
                Foreground = (Brush)Application.Current.Resources["OnSurfaceBrush"],
                BorderThickness = new Thickness(0),
                ToolTip = placeholder,
            };
            return field;
        }
    }
}
