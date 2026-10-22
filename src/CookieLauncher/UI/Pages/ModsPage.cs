using System.IO;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Markup;
using System.Windows.Media;
using CookieLauncher.Core;

namespace CookieLauncher.UI.Pages;

/// <summary>
/// Mods page: list, enable and remove the jars in the selected instance.
/// </summary>
/// <remarks>
/// <para>
/// Enable/disable uses the <c>.disabled</c> suffix rather than a database flag. Every loader
/// already ignores files that do not end in <c>.jar</c>, so renaming is the one mechanism
/// that behaves identically across Fabric, Quilt, Forge and NeoForge, and it survives the
/// user moving the instance folder.
/// </para>
/// <para>
/// The list virtualises: a large modpack carries several hundred jars, and building a visual
/// for each one makes the page stall when it opens.
/// </para>
/// </remarks>
public sealed class ModsPage : PageBase
{
    private const string ContainerTemplateXaml = """
        <ControlTemplate xmlns="http://schemas.microsoft.com/winfx/2006/xaml/presentation"
                         xmlns:x="http://schemas.microsoft.com/winfx/2006/xaml"
                         TargetType="ListBoxItem">
            <Border x:Name="Row" CornerRadius="8" Background="Transparent" Padding="12,6,12,6">
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
                    <TextBlock Text="{Binding Name}"
                               FontSize="13"
                               FontWeight="SemiBold"
                               TextTrimming="CharacterEllipsis"
                               Foreground="{DynamicResource OnSurfaceBrush}" />
                    <TextBlock Text="{Binding Detail}"
                               FontSize="11"
                               Margin="0,1,0,0"
                               TextTrimming="CharacterEllipsis"
                               Foreground="{DynamicResource OnSurfaceVariantBrush}" />
                </StackPanel>
                <Border Grid.Column="1"
                        Height="24"
                        CornerRadius="12"
                        Padding="10,0"
                        VerticalAlignment="Center"
                        Background="{DynamicResource SurfaceContainerHighBrush}">
                    <TextBlock Text="{Binding State}"
                               FontSize="11"
                               VerticalAlignment="Center"
                               Foreground="{DynamicResource OnSurfaceVariantBrush}" />
                </Border>
            </Grid>
        </DataTemplate>
        """;

    private readonly ListBox _list;
    private readonly TextBox _filter;
    private readonly TextBlock _summary;
    private readonly Button _toggleButton;
    private readonly Button _deleteButton;
    private IReadOnlyList<ModRow> _all = [];

    public ModsPage()
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
            Text = "Mods",
            Style = (Style)FindResource("MdHeadlineSmall"),
        });
        headline.Children.Add(new TextBlock
        {
            Text = "Enable, disable or remove the mods in this instance.",
            Style = (Style)FindResource("MdBodyMedium"),
            Margin = new Thickness(0, 3, 0, 0),
        });
        Grid.SetRow(headline, 0);
        root.Children.Add(headline);

        // ---- Toolbar -------------------------------------------------------
        var toolbar = new Grid { Margin = new Thickness(0, 0, 0, 10) };
        toolbar.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
        toolbar.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });

        _filter = new TextBox
        {
            Style = (Style)FindResource("MdTextField"),
            Tag = "Filter by file name",
            Margin = new Thickness(0, 0, 8, 0),
        };
        _filter.TextChanged += (_, _) => ApplyFilter();
        Grid.SetColumn(_filter, 0);
        toolbar.Children.Add(_filter);

        var openFolder = new Button
        {
            Content = "Open folder",
            Style = (Style)FindResource("MdOutlinedButton"),
        };
        openFolder.Click += (_, _) => OpenModsFolder();
        Grid.SetColumn(openFolder, 1);
        toolbar.Children.Add(openFolder);

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
        VirtualizingPanel.SetVirtualizationMode(_list, VirtualizationMode.Recycling);
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
            Text = "No instance selected.",
            Style = (Style)FindResource("MdLabelSmall"),
            VerticalAlignment = VerticalAlignment.Center,
        };
        Grid.SetColumn(_summary, 0);
        footer.Children.Add(_summary);

        _toggleButton = new Button
        {
            Content = "Disable",
            Style = (Style)FindResource("MdTonalButton"),
            MinWidth = 106,
            IsEnabled = false,
            Margin = new Thickness(0, 0, 8, 0),
        };
        _toggleButton.Click += (_, _) => ToggleSelected();
        Grid.SetColumn(_toggleButton, 1);
        footer.Children.Add(_toggleButton);

        _deleteButton = new Button
        {
            Content = "Delete",
            Style = (Style)FindResource("MdDangerButton"),
            MinWidth = 96,
            IsEnabled = false,
        };
        _deleteButton.Click += (_, _) => DeleteSelected();
        Grid.SetColumn(_deleteButton, 2);
        footer.Children.Add(_deleteButton);

        Grid.SetRow(footer, 3);
        root.Children.Add(footer);

        Content = root;
    }

    public override void OnShown()
    {
        Reload();
        SetStatus("Ready");
    }

    /// <summary>Re-reads the instance's mods directory.</summary>
    private void Reload()
    {
        Instance? instance = LauncherState.Current.Instance;
        if (instance is null)
        {
            _all = [];
            _summary.Text = "No instance selected.";
            _list.ItemsSource = null;
            return;
        }

        List<ModRow> rows = [];
        string directory = instance.ModsDirectory;

        if (Directory.Exists(directory))
        {
            try
            {
                foreach (string file in Directory.EnumerateFiles(directory))
                {
                    string fileName = Path.GetFileName(file);
                    bool enabled = fileName.EndsWith(".jar", StringComparison.OrdinalIgnoreCase);
                    bool disabled = fileName.EndsWith(".jar.disabled",
                        StringComparison.OrdinalIgnoreCase);
                    if (!enabled && !disabled)
                    {
                        continue;
                    }

                    long size = 0;
                    try
                    {
                        size = new FileInfo(file).Length;
                    }
                    catch (IOException)
                    {
                        // Being written by an external tool; show it with unknown size.
                    }

                    rows.Add(new ModRow(
                        DisplayName(fileName),
                        FormatSize(size) + "  ·  " + instance.Name,
                        enabled ? "Enabled" : "Disabled",
                        file,
                        enabled));
                }
            }
            catch (IOException error)
            {
                Log.Warn("Cannot read the mods directory: " + error.Message);
            }
        }

        _all = [.. rows.OrderBy(row => row.Name, StringComparer.OrdinalIgnoreCase)];
        ApplyFilter();
    }

    private static string DisplayName(string fileName)
    {
        string name = fileName;
        if (name.EndsWith(".disabled", StringComparison.OrdinalIgnoreCase))
        {
            name = name[..^".disabled".Length];
        }
        return name.EndsWith(".jar", StringComparison.OrdinalIgnoreCase)
            ? name[..^".jar".Length]
            : name;
    }

    private static string FormatSize(long bytes)
    {
        if (bytes <= 0)
        {
            return "unknown size";
        }
        if (bytes < 1024)
        {
            return bytes + " B";
        }
        string[] units = ["KB", "MB", "GB"];
        double scaled = bytes;
        int unit = -1;
        do
        {
            scaled /= 1024;
            unit++;
        }
        while (scaled >= 1024 && unit < units.Length - 1);
        return scaled.ToString(scaled >= 100 ? "0" : "0.0") + " " + units[unit];
    }

    private void ApplyFilter()
    {
        string query = _filter.Text.Trim();
        IReadOnlyList<ModRow> rows = string.IsNullOrWhiteSpace(query)
            ? _all
            : [.. _all.Where(row => row.Name.Contains(query, StringComparison.OrdinalIgnoreCase))];

        _list.ItemsSource = rows;

        int enabled = _all.Count(row => row.Enabled);
        _summary.Text = _all.Count == 0
            ? "No mods in this instance."
            : $"{rows.Count} shown  ·  {enabled} of {_all.Count} enabled";
    }

    private void UpdateSelection()
    {
        bool hasSelection = _list.SelectedItem is ModRow;
        _toggleButton.IsEnabled = hasSelection;
        _deleteButton.IsEnabled = hasSelection;
        if (_list.SelectedItem is ModRow row)
        {
            _toggleButton.Content = row.Enabled ? "Disable" : "Enable";
        }
    }

    private void ToggleSelected()
    {
        if (_list.SelectedItem is not ModRow row)
        {
            return;
        }

        string target = row.Enabled
            ? row.Path + ".disabled"
            : row.Path[..^".disabled".Length];
        try
        {
            File.Move(row.Path, target);
            SetStatus((row.Enabled ? "Disabled " : "Enabled ") + row.Name);
            Reload();
        }
        catch (Exception error) when (error is IOException or UnauthorizedAccessException)
        {
            SetStatus("Cannot rename the mod: " + error.Message);
        }
    }

    private void DeleteSelected()
    {
        if (_list.SelectedItem is not ModRow row)
        {
            return;
        }

        MessageBoxResult answer = MessageBox.Show(
            $"Delete {row.Name}?\n\nThis removes the file from disk and cannot be undone.",
            "Delete mod", MessageBoxButton.OKCancel, MessageBoxImage.Warning);
        if (answer != MessageBoxResult.OK)
        {
            return;
        }

        try
        {
            File.Delete(row.Path);
            SetStatus("Deleted " + row.Name);
            Reload();
        }
        catch (Exception error) when (error is IOException or UnauthorizedAccessException)
        {
            SetStatus("Cannot delete the mod: " + error.Message);
        }
    }

    private void OpenModsFolder()
    {
        Instance? instance = LauncherState.Current.Instance;
        if (instance is null)
        {
            SetStatus("No instance selected.");
            return;
        }
        Directory.CreateDirectory(instance.ModsDirectory);
        Shell.ShellOperations.Open(instance.ModsDirectory);
    }

    /// <summary>One row in the list.</summary>
    private sealed record ModRow(string Name, string Detail, string State, string Path, bool Enabled);
}
