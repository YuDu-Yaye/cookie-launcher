using System.Windows;
using System.Windows.Controls;
using System.Windows.Markup;
using System.Windows.Media;
using CookieLauncher.Game.Contracts;
using CookieLauncher.Game.Install;
using CookieLauncher.Game.Meta;

namespace CookieLauncher.UI.Pages;

/// <summary>
/// Versions page: browse Mojang's catalogue, choose a loader and install.
/// </summary>
/// <remarks>
/// <para>
/// The list is a virtualising <see cref="ListBox"/>. Mojang publishes well over nine hundred
/// versions, and materialising a visual for each row makes the page take seconds to open.
/// </para>
/// <para>
/// Row and container templates are parsed from XAML strings rather than assembled with
/// <see cref="FrameworkElementFactory"/>. That API is deprecated, verbose, and its mistakes
/// stay invisible until runtime; a XAML string goes through the same parser as the rest of
/// the app.
/// </para>
/// </remarks>
public sealed class VersionsPage : PageBase
{
    private const string ContainerTemplateXaml = """
        <ControlTemplate xmlns="http://schemas.microsoft.com/winfx/2006/xaml/presentation"
                         xmlns:x="http://schemas.microsoft.com/winfx/2006/xaml"
                         TargetType="ListBoxItem">
            <Border x:Name="Row" CornerRadius="8" Background="Transparent" Padding="14,6,14,6">
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
                    <ColumnDefinition Width="*" />
                    <ColumnDefinition Width="Auto" />
                </Grid.ColumnDefinitions>
                <StackPanel Grid.Column="0">
                    <TextBlock Text="{Binding Id}"
                               FontSize="13"
                               FontWeight="SemiBold"
                               Foreground="{DynamicResource OnSurfaceBrush}" />
                    <TextBlock Text="{Binding Meta}"
                               FontSize="11"
                               Margin="0,1,0,0"
                               Foreground="{DynamicResource OnSurfaceVariantBrush}" />
                </StackPanel>
                <Border Grid.Column="1"
                        Height="24"
                        CornerRadius="12"
                        Padding="10,0"
                        VerticalAlignment="Center"
                        Background="{DynamicResource SecondaryContainerBrush}"
                        Visibility="{Binding Installed, Converter={StaticResource BoolToVis}}">
                    <TextBlock Text="Installed"
                               FontSize="11"
                               VerticalAlignment="Center"
                               Foreground="{DynamicResource OnSecondaryContainerBrush}" />
                </Border>
            </Grid>
        </DataTemplate>
        """;

    private readonly TextBox _search;
    private readonly ListBox _list;
    private readonly ComboBox _typeFilter;
    private readonly ComboBox _loaderFilter;
    private readonly TextBlock _statusLine;
    private readonly Button _installButton;
    private IReadOnlyList<VersionEntry> _all = [];

    public VersionsPage()
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
            Text = "Versions",
            Style = (Style)FindResource("MdHeadlineSmall"),
        });
        headline.Children.Add(new TextBlock
        {
            Text = "Install a Minecraft version, optionally with a mod loader.",
            Style = (Style)FindResource("MdBodyMedium"),
            Margin = new Thickness(0, 3, 0, 0),
        });
        Grid.SetRow(headline, 0);
        root.Children.Add(headline);

        // ---- Toolbar -------------------------------------------------------
        var toolbar = new Grid { Margin = new Thickness(0, 0, 0, 10) };
        toolbar.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
        toolbar.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
        toolbar.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });

        _search = new TextBox
        {
            Style = (Style)FindResource("MdTextField"),
            Tag = "Search versions",
            Margin = new Thickness(0, 0, 8, 0),
        };
        _search.TextChanged += (_, _) => ApplyFilter();
        Grid.SetColumn(_search, 0);
        toolbar.Children.Add(_search);

        _typeFilter = MakeCombo(["Releases", "Snapshots", "All"], 0, 124);
        _typeFilter.SelectionChanged += (_, _) => ApplyFilter();
        Grid.SetColumn(_typeFilter, 1);
        toolbar.Children.Add(_typeFilter);

        _loaderFilter = MakeCombo(["Vanilla", "Fabric", "Quilt", "Forge", "NeoForge"], 0, 118);
        _loaderFilter.Margin = new Thickness(8, 0, 0, 0);
        Grid.SetColumn(_loaderFilter, 2);
        toolbar.Children.Add(_loaderFilter);

        Grid.SetRow(toolbar, 1);
        root.Children.Add(toolbar);

        // ---- List ----------------------------------------------------------
        var containerStyle = new Style(typeof(ListBoxItem));
        containerStyle.Setters.Add(new Setter(BackgroundProperty, Brushes.Transparent));
        containerStyle.Setters.Add(new Setter(BorderThicknessProperty, new Thickness(0)));
        containerStyle.Setters.Add(new Setter(PaddingProperty, new Thickness(0)));
        containerStyle.Setters.Add(new Setter(MarginProperty, new Thickness(0, 0, 0, 2)));
        containerStyle.Setters.Add(new Setter(HorizontalContentAlignmentProperty,
            HorizontalAlignment.Stretch));
        containerStyle.Setters.Add(new Setter(TemplateProperty,
            Parse<ControlTemplate>(ContainerTemplateXaml)));

        _list = new ListBox
        {
            Background = Brushes.Transparent,
            BorderThickness = new Thickness(0),
            ItemContainerStyle = containerStyle,
            ItemTemplate = Parse<DataTemplate>(RowTemplateXaml),
        };
        // Attached properties cannot be set in an object initialiser.
        ScrollViewer.SetHorizontalScrollBarVisibility(_list, ScrollBarVisibility.Disabled);
        ScrollViewer.SetVerticalScrollBarVisibility(_list, ScrollBarVisibility.Auto);
        VirtualizingPanel.SetIsVirtualizing(_list, true);
        VirtualizingPanel.SetVirtualizationMode(_list, VirtualizationMode.Recycling);
        _list.SelectionChanged += (_, _) => UpdateSelection();
        Grid.SetRow(_list, 2);
        root.Children.Add(_list);

        // ---- Footer --------------------------------------------------------
        var footer = new Grid { Margin = new Thickness(0, 10, 0, 0) };
        footer.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
        footer.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });

        _statusLine = new TextBlock
        {
            Text = "Fetching the version catalogue...",
            Style = (Style)FindResource("MdLabelSmall"),
            VerticalAlignment = VerticalAlignment.Center,
        };
        Grid.SetColumn(_statusLine, 0);
        footer.Children.Add(_statusLine);

        _installButton = new Button
        {
            Content = "Install",
            Style = (Style)FindResource("MdFilledButton"),
            MinWidth = 116,
            IsEnabled = false,
        };
        _installButton.Click += (_, _) => InstallSelected();
        Grid.SetColumn(_installButton, 1);
        footer.Children.Add(_installButton);

        Grid.SetRow(footer, 3);
        root.Children.Add(footer);

        Content = root;
    }

    private static T Parse<T>(string xaml) where T : class => (T)XamlReader.Parse(xaml);

    private static ComboBox MakeCombo(string[] options, int selected, double width)
    {
        ComboBox combo = new()
        {
            Width = width,
            Style = (Style)Application.Current.Resources["MdComboBox"],
            SelectedIndex = selected,
        };
        foreach (string option in options)
        {
            _ = combo.Items.Add(option);
        }
        return combo;
    }

    public override void OnShown()
    {
        SetStatus("Ready");
        if (_all.Count == 0)
        {
            _ = LoadCatalogueAsync();
        }
    }

    private async Task LoadCatalogueAsync()
    {
        _statusLine.Text = "Fetching the version catalogue...";
        VersionManifest? manifest = await MetaService
            .LoadManifestAsync(App.Settings.Mirror)
            .ConfigureAwait(true);

        if (manifest is null)
        {
            _statusLine.Text = "Could not reach the catalogue. Check the network or the mirror setting.";
            return;
        }

        // A set, not a list: checking 900 versions against a list would be quadratic.
        HashSet<string> installed = [.. GameInstaller.InstalledVersions()];
        _all = manifest.Versions
            .Select(entry => new VersionEntry(entry.Id, entry.Type, entry.ReleaseTime)
            {
                Installed = installed.Contains(entry.Id),
            })
            .ToList();
        ApplyFilter();
    }

    private void ApplyFilter()
    {
        if (_all.Count == 0)
        {
            return;
        }

        bool snapshots = _typeFilter.SelectedIndex == 1;
        bool everything = _typeFilter.SelectedIndex == 2;
        string query = _search.Text.Trim();

        IEnumerable<VersionEntry> filtered = _all;
        if (!everything)
        {
            filtered = filtered.Where(entry => snapshots
                ? entry.Type == "snapshot"
                : entry.Type == "release");
        }
        if (!string.IsNullOrWhiteSpace(query))
        {
            filtered = filtered.Where(entry =>
                entry.Id.Contains(query, StringComparison.OrdinalIgnoreCase));
        }

        List<VersionEntry> rows = filtered.Take(400).ToList();
        _list.ItemsSource = rows;
        _statusLine.Text = rows.Count == 0
            ? "No version matches that filter."
            : $"{rows.Count} shown  ·  {_all.Count} in the catalogue";
    }

    private void UpdateSelection()
    {
        if (_list.SelectedItem is not VersionEntry entry)
        {
            _installButton.IsEnabled = false;
            return;
        }
        _installButton.IsEnabled = true;
        _installButton.Content = GameInstaller.IsInstalled(entry.Id) ? "Repair" : "Install";
    }

    private void InstallSelected()
    {
        if (_list.SelectedItem is not VersionEntry entry)
        {
            return;
        }
        string loader = _loaderFilter.SelectedItem as string ?? "Vanilla";
        SetStatus($"Selected {entry.Id} ({loader}). Installer wiring lands in the next step.");
    }
}
