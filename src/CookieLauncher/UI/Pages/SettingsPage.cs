using System.IO;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Media;
using CookieLauncher.Core;

namespace CookieLauncher.UI.Pages;

/// <summary>
/// Settings page: appearance, Java, memory, downloads and data location.
/// </summary>
/// <remarks>
/// <para>
/// Controls write straight into <see cref="App.Settings"/> and the page saves on change,
/// rather than requiring a Save button. A launcher's settings are small and independent;
/// making the user confirm each one adds a step without preventing any mistake.
/// </para>
/// <para>
/// The theme picker applies immediately, because a theme is the one setting where seeing the
/// result <em>is</em> the decision.
/// </para>
/// </remarks>
public sealed class SettingsPage : PageBase
{
    private readonly StackPanel _content;
    private readonly TextBlock _statusLine;
    private bool _loading;

    public SettingsPage()
    {
        Margin = new Thickness(28, 22, 28, 18);

        var root = new Grid();
        root.RowDefinitions.Add(new RowDefinition { Height = GridLength.Auto });
        root.RowDefinitions.Add(new RowDefinition { Height = new GridLength(1, GridUnitType.Star) });
        root.RowDefinitions.Add(new RowDefinition { Height = GridLength.Auto });

        var headline = new StackPanel { Margin = new Thickness(0, 0, 0, 14) };
        headline.Children.Add(new TextBlock
        {
            Text = "Settings",
            Style = (Style)FindResource("MdHeadlineSmall"),
        });
        headline.Children.Add(new TextBlock
        {
            Text = "Everything is stored in one portable data directory.",
            Style = (Style)FindResource("MdBodyMedium"),
            Margin = new Thickness(0, 3, 0, 0),
        });
        Grid.SetRow(headline, 0);
        root.Children.Add(headline);

        _content = new StackPanel { Margin = new Thickness(0, 0, 0, 0) };
        var scroll = new ScrollViewer
        {
            Content = _content,
            VerticalScrollBarVisibility = ScrollBarVisibility.Auto,
            HorizontalScrollBarVisibility = ScrollBarVisibility.Disabled,
            Padding = new Thickness(0, 0, 8, 0),
        };
        Grid.SetRow(scroll, 1);
        root.Children.Add(scroll);

        _statusLine = new TextBlock
        {
            Style = (Style)FindResource("MdLabelSmall"),
            Margin = new Thickness(0, 8, 0, 0),
        };
        Grid.SetRow(_statusLine, 2);
        root.Children.Add(_statusLine);

        Content = root;
    }

    public override void OnShown()
    {
        _loading = true;
        BuildSections();
        _loading = false;
        _statusLine.Text = "Data directory: " + AppPaths.Root;
        SetStatus("Ready");
    }

    private void BuildSections()
    {
        _content.Children.Clear();

        _content.Children.Add(AppearanceSection());
        _content.Children.Add(JavaSection());
        _content.Children.Add(MemorySection());
        _content.Children.Add(DownloadsSection());
        _content.Children.Add(LocationsSection());
        _content.Children.Add(AboutSection());
    }

    /// <summary>A titled card, the unit every section is built from.</summary>
    private Border Section(string title, string? description, UIElement body)
    {
        Border card = new()
        {
            Style = (Style)FindResource("MdCardOutlined"),
            Padding = new Thickness(20, 16, 20, 18),
            Margin = new Thickness(0, 0, 0, 12),
        };

        StackPanel stack = new();
        stack.Children.Add(new TextBlock
        {
            Text = title,
            Style = (Style)FindResource("MdTitleMedium"),
        });
        if (!string.IsNullOrWhiteSpace(description))
        {
            stack.Children.Add(new TextBlock
            {
                Text = description,
                Style = (Style)FindResource("MdBodySmall"),
                Margin = new Thickness(0, 4, 0, 0),
            });
        }
        stack.Children.Add(new Border { Height = 14 });
        stack.Children.Add(body);
        card.Child = stack;
        return card;
    }

    // ------------------------------------------------------------------
    // Appearance
    // ------------------------------------------------------------------

    private Border AppearanceSection()
    {
        WrapPanel themes = new() { Orientation = Orientation.Horizontal };

        foreach (SchemeDefinition definition in ThemeCatalogue.All)
        {
            if (!definition.Dark)
            {
                // One swatch per seed; the light/dark choice is a separate toggle.
                continue;
            }

            SchemeDefinition dark = definition;
            SchemeDefinition light = ThemeCatalogue.Get(definition.Id.Replace("-dark", "-light"));

            bool active = App.Settings.ThemeId is string current
                && (current == dark.Id || current == light.Id);

            StackPanel item = new() { Margin = new Thickness(0, 0, 10, 10), Width = 92 };
            Border swatch = new()
            {
                Width = 92,
                Height = 52,
                CornerRadius = new CornerRadius(10),
                BorderThickness = new Thickness(active ? 2 : 1),
                BorderBrush = (Brush)FindResource(active ? "PrimaryBrush" : "OutlineVariantBrush"),
                Background = new SolidColorBrush(
                    System.Windows.Media.Color.FromArgb(255,
                        (byte)((dark.SeedArgb >> 16) & 0xFF),
                        (byte)((dark.SeedArgb >> 8) & 0xFF),
                        (byte)(dark.SeedArgb & 0xFF))),
                Cursor = System.Windows.Input.Cursors.Hand,
                ToolTip = definition.Name,
            };
            swatch.MouseLeftButtonUp += (_, _) =>
            {
                string target = App.Settings.ThemeId.EndsWith("-light", StringComparison.Ordinal)
                    ? light.Id
                    : dark.Id;
                ApplyTheme(target);
            };
            item.Children.Add(swatch);
            item.Children.Add(new TextBlock
            {
                Text = definition.Name,
                Style = (Style)FindResource("MdLabelSmall"),
                HorizontalAlignment = HorizontalAlignment.Center,
                Margin = new Thickness(0, 4, 0, 0),
            });
            themes.Children.Add(item);
        }

        StackPanel body = new();
        body.Children.Add(themes);

        bool darkMode = !App.Settings.ThemeId.EndsWith("-light", StringComparison.Ordinal);
        CheckBox toggle = new()
        {
            Content = new TextBlock
            {
                Text = "Dark mode",
                Style = (Style)FindResource("MdBodyMedium"),
            },
            Style = (Style)FindResource("MdSwitch"),
            IsChecked = darkMode,
            Margin = new Thickness(0, 4, 0, 0),
        };
        toggle.Checked += (_, _) => SetDark(true);
        toggle.Unchecked += (_, _) => SetDark(false);
        body.Children.Add(toggle);

        return Section("Appearance", "Generated from a seed colour with the Material 3 algorithm.",
            body);
    }

    private void SetDark(bool dark)
    {
        if (_loading)
        {
            return;
        }
        string baseId = App.Settings.ThemeId.Replace("-light", "").Replace("-dark", "");
        ApplyTheme(baseId + (dark ? "-dark" : "-light"));
    }

    private void ApplyTheme(string themeId)
    {
        App.Settings.ThemeId = themeId;
        App.SaveSettings();
        _ = ThemeService.Apply(themeId);
        BuildSections();
        _statusLine.Text = "Theme: " + ThemeService.CurrentName;
        SetStatus("Theme applied");
    }

    // ------------------------------------------------------------------
    // Java
    // ------------------------------------------------------------------

    private Border JavaSection()
    {
        StackPanel body = new();

        TextBox javaPath = new()
        {
            Style = (Style)FindResource("MdTextField"),
            Tag = "Auto-detect",
            Text = App.Settings.JavaPath ?? "",
        };
        javaPath.TextChanged += (_, _) =>
        {
            if (_loading)
            {
                return;
            }
            App.Settings.JavaPath = javaPath.Text.Trim();
            App.SaveSettings();
        };
        body.Children.Add(javaPath);

        StackPanel buttons = new()
        {
            Orientation = Orientation.Horizontal,
            Margin = new Thickness(0, 10, 0, 0),
        };
        var rescan = new Button
        {
            Content = "Rescan runtimes",
            Style = (Style)FindResource("MdOutlinedButton"),
        };
        rescan.Click += (_, _) =>
        {
            Game.Java.JavaLocator locator = new();
            IReadOnlyList<Game.Java.JavaRuntime> runtimes = locator.All;
            _statusLine.Text = runtimes.Count == 0
                ? "No Java runtime found on this machine."
                : $"{runtimes.Count} runtime(s): "
                  + string.Join(", ", runtimes.Select(runtime => runtime.PlainLabel));
            SetStatus("Rescan complete");
        };
        buttons.Children.Add(rescan);

        var browse = new Button
        {
            Content = "Browse",
            Style = (Style)FindResource("MdTextButton"),
            Margin = new Thickness(8, 0, 0, 0),
        };
        browse.Click += (_, _) =>
        {
            Microsoft.Win32.OpenFileDialog dialog = new()
            {
                Title = "Select the java executable",
                Filter = "java|java.exe;java|All files|*.*",
            };
            if (dialog.ShowDialog() == true)
            {
                javaPath.Text = dialog.FileName;
            }
        };
        buttons.Children.Add(browse);
        body.Children.Add(buttons);

        return Section("Java", "Leave empty to detect automatically. The launcher downloads a "
            + "matching runtime when none is found.", body);
    }

    // ------------------------------------------------------------------
    // Memory
    // ------------------------------------------------------------------

    private Border MemorySection()
    {
        StackPanel body = new();

        Grid header = new();
        header.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
        header.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });

        var caption = new TextBlock
        {
            Text = "Maximum memory",
            Style = (Style)FindResource("MdBodyMedium"),
            VerticalAlignment = VerticalAlignment.Center,
        };
        Grid.SetColumn(caption, 0);
        header.Children.Add(caption);

        var value = new TextBlock
        {
            Text = App.Settings.MaxMemoryMb + " MB",
            Style = (Style)FindResource("MdLabelLarge"),
            VerticalAlignment = VerticalAlignment.Center,
        };
        Grid.SetColumn(value, 1);
        header.Children.Add(value);

        body.Children.Add(header);

        Slider slider = new()
        {
            Minimum = 512,
            Maximum = 16384,
            TickFrequency = 512,
            IsSnapToTickEnabled = true,
            Value = Math.Clamp(App.Settings.MaxMemoryMb, 512, 16384),
            Margin = new Thickness(0, 8, 0, 0),
        };
        slider.ValueChanged += (_, _) =>
        {
            int megabytes = (int)slider.Value;
            value.Text = megabytes + " MB";
            if (_loading)
            {
                return;
            }
            App.Settings.MaxMemoryMb = megabytes;
            App.SaveSettings();
        };
        body.Children.Add(slider);

        body.Children.Add(new TextBlock
        {
            Text = "Leave room for the operating system: the game allocates off-heap buffers "
                 + "on top of this heap.",
            Style = (Style)FindResource("MdBodySmall"),
            Margin = new Thickness(0, 8, 0, 0),
        });

        return Section("Memory", null, body);
    }

    // ------------------------------------------------------------------
    // Downloads
    // ------------------------------------------------------------------

    private Border DownloadsSection()
    {
        StackPanel body = new();

        ComboBox mirror = new()
        {
            Style = (Style)FindResource("MdComboBox"),
            Width = 260,
            HorizontalAlignment = HorizontalAlignment.Left,
        };
        _ = mirror.Items.Add("Automatic");
        _ = mirror.Items.Add("Official Mojang CDN");
        _ = mirror.Items.Add("BMCLAPI mirror");
        mirror.SelectedIndex = App.Settings.Mirror switch
        {
            "official" => 1,
            "mirror" => 2,
            _ => 0,
        };
        mirror.SelectionChanged += (_, _) =>
        {
            if (_loading)
            {
                return;
            }
            App.Settings.Mirror = mirror.SelectedIndex switch
            {
                1 => "official",
                2 => "mirror",
                _ => "auto",
            };
            App.SaveSettings();
        };
        body.Children.Add(mirror);

        body.Children.Add(new TextBlock
        {
            Text = "Automatic tries the official CDN first and falls back per file. Every "
                 + "download is verified against its published hash either way.",
            Style = (Style)FindResource("MdBodySmall"),
            Margin = new Thickness(0, 8, 0, 0),
        });

        return Section("Downloads", null, body);
    }

    // ------------------------------------------------------------------
    // Locations
    // ------------------------------------------------------------------

    private Border LocationsSection()
    {
        StackPanel body = new();

        body.Children.Add(new TextBlock
        {
            Text = AppPaths.Root,
            Style = (Style)FindResource("MdBodySmall"),
            TextWrapping = TextWrapping.Wrap,
        });

        StackPanel buttons = new()
        {
            Orientation = Orientation.Horizontal,
            Margin = new Thickness(0, 12, 0, 0),
        };

        var openData = new Button
        {
            Content = "Open data folder",
            Style = (Style)FindResource("MdOutlinedButton"),
        };
        openData.Click += (_, _) => Shell.ShellOperations.OpenDirectory(AppPaths.Root);
        buttons.Children.Add(openData);

        var openLog = new Button
        {
            Content = "Open log",
            Style = (Style)FindResource("MdTextButton"),
            Margin = new Thickness(8, 0, 0, 0),
        };
        openLog.Click += (_, _) =>
        {
            if (Log.FilePath is string path && File.Exists(path))
            {
                Shell.ShellOperations.RevealFile(path);
            }
            else
            {
                SetStatus("No log file yet.");
            }
        };
        buttons.Children.Add(openLog);
        body.Children.Add(buttons);

        body.Children.Add(new TextBlock
        {
            Text = AppPaths.IsPortable
                ? "Portable mode is active."
                : "Tip: put an empty file named .portable beside the executable to keep "
                  + "everything in a data folder next to it.",
            Style = (Style)FindResource("MdBodySmall"),
            Margin = new Thickness(0, 10, 0, 0),
        });

        return Section("Data directory", "Instances, versions, libraries and assets all live "
            + "here, so moving this folder moves your whole setup.", body);
    }

    private Border AboutSection()
    {
        StackPanel body = new();
        body.Children.Add(new TextBlock
        {
            Text = $"{AppInfo.Name} {AppInfo.Version}  ·  {AppInfo.License}",
            Style = (Style)FindResource("MdBodyMedium"),
        });
        body.Children.Add(new TextBlock
        {
            Text = "No telemetry, no ads, no bundled content. The launcher contacts Mojang and "
                 + "your chosen mirror, and nothing else.",
            Style = (Style)FindResource("MdBodySmall"),
            Margin = new Thickness(0, 6, 0, 0),
        });
        return Section("About", null, body);
    }
}
