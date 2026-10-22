using System.Windows;
using System.Windows.Controls;
using System.Windows.Media;
using CookieLauncher.Core;

namespace CookieLauncher.UI.Pages;

/// <summary>
/// More page: the destinations that do not deserve a permanent pill in the top bar.
/// </summary>
/// <remarks>
/// PCL2 keeps "more" as one capsule destination and lists the extras behind it, which is why
/// mods, logs and accounts live here rather than competing for top-bar space.
/// </remarks>
public sealed class MorePage : PageBase
{
    private readonly StackPanel _links;

    public MorePage()
    {
        Margin = new Thickness(28, 22, 28, 18);

        var root = new Grid();
        root.RowDefinitions.Add(new RowDefinition { Height = GridLength.Auto });
        root.RowDefinitions.Add(new RowDefinition { Height = new GridLength(1, GridUnitType.Star) });

        var headline = new StackPanel { Margin = new Thickness(0, 0, 0, 16) };
        headline.Children.Add(new TextBlock
        {
            Text = "More",
            Style = (Style)FindResource("MdHeadlineSmall"),
        });
        headline.Children.Add(new TextBlock
        {
            Text = "Mods, accounts, logs and diagnostics.",
            Style = (Style)FindResource("MdBodyMedium"),
            Margin = new Thickness(0, 3, 0, 0),
        });
        Grid.SetRow(headline, 0);
        root.Children.Add(headline);

        _links = new StackPanel();
        var scroll = new ScrollViewer
        {
            Content = _links,
            VerticalScrollBarVisibility = ScrollBarVisibility.Auto,
            HorizontalScrollBarVisibility = ScrollBarVisibility.Disabled,
        };
        Grid.SetRow(scroll, 1);
        root.Children.Add(scroll);

        Content = root;
        Build();
    }

    private void Build()
    {
        _links.Children.Clear();

        _links.Children.Add(Link("Mods", "Enable, disable or remove mods in this instance.", "mods"));
        _links.Children.Add(Link("Accounts", "Offline profiles, authlib servers and Microsoft.",
            "accounts"));
        _links.Children.Add(Link("Logs", "Live output and the crash diagnosis.", "log"));
        _links.Children.Add(DiagnoseCard());
        _links.Children.Add(FolderCard());
    }

    /// <summary>One navigable entry, styled as an M3 list item on a card.</summary>
    private Border Link(string title, string description, string destination)
    {
        Border card = new()
        {
            Style = (Style)FindResource("MdCardFilled"),
            Padding = new Thickness(20, 16, 20, 16),
            Margin = new Thickness(0, 0, 0, 10),
            Cursor = System.Windows.Input.Cursors.Hand,
        };

        Grid content = new();
        content.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
        content.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });

        StackPanel text = new();
        text.Children.Add(new TextBlock
        {
            Text = title,
            Style = (Style)FindResource("MdTitleMedium"),
        });
        text.Children.Add(new TextBlock
        {
            Text = description,
            Style = (Style)FindResource("MdBodySmall"),
            Margin = new Thickness(0, 3, 0, 0),
        });
        Grid.SetColumn(text, 0);
        content.Children.Add(text);

        System.Windows.Shapes.Path chevron = new()
        {
            Data = Geometry.Parse("M0,0 L6,6 L0,12"),
            Stroke = (Brush)FindResource("OnSurfaceVariantBrush"),
            StrokeThickness = 1.6,
            Width = 8,
            Height = 14,
            Stretch = Stretch.Uniform,
            VerticalAlignment = VerticalAlignment.Center,
        };
        Grid.SetColumn(chevron, 1);
        content.Children.Add(chevron);

        card.Child = content;
        card.MouseLeftButtonUp += (_, _) =>
        {
            MainWindowBridge.Navigate(destination);
            SetStatus(title);
        };
        return card;
    }

    /// <summary>Runs the crash analyser over the selected instance and shows the result.</summary>
    private Border DiagnoseCard()
    {
        Border card = new()
        {
            Style = (Style)FindResource("MdCardOutlined"),
            Padding = new Thickness(20, 16, 20, 18),
            Margin = new Thickness(0, 0, 0, 10),
        };

        StackPanel body = new();
        body.Children.Add(new TextBlock
        {
            Text = "Diagnose a crash",
            Style = (Style)FindResource("MdTitleMedium"),
        });

        var result = new TextBlock
        {
            Text = "Reads the launcher log and, when present, the newest crash report. "
                 + "Reports a cause and a fix rather than a stack trace.",
            Style = (Style)FindResource("MdBodySmall"),
            Margin = new Thickness(0, 6, 0, 0),
            TextWrapping = TextWrapping.Wrap,
        };
        body.Children.Add(result);

        var analyse = new Button
        {
            Content = "Analyse now",
            Style = (Style)FindResource("MdTonalButton"),
            MinWidth = 136,
            Margin = new Thickness(0, 12, 0, 0),
            HorizontalAlignment = HorizontalAlignment.Left,
        };
        analyse.Click += (_, _) =>
        {
            Instance? instance = LauncherState.Current.Instance;
            if (instance is null)
            {
                result.Text = "Select an instance first.";
                return;
            }

            IReadOnlyList<Game.Diagnose.Diagnosis> diagnoses =
                Game.Diagnose.CrashAnalyzer.AnalyzeDirectory(instance.ResolvedGameDir);
            result.Text = diagnoses.Count == 0
                ? "No known problem signature found in the log."
                : string.Join(Environment.NewLine + Environment.NewLine,
                    diagnoses.Select(diagnosis => diagnosis.Render()));
            SetStatus($"{diagnoses.Count} possible cause(s) identified");
        };
        body.Children.Add(analyse);

        card.Child = body;
        return card;
    }

    /// <summary>Opens the data directory, the fallback every launcher needs.</summary>
    private Border FolderCard()
    {
        Border card = new()
        {
            Style = (Style)FindResource("MdCardFilled"),
            Padding = new Thickness(20, 16, 20, 16),
            Margin = new Thickness(0, 0, 0, 10),
        };

        StackPanel body = new();
        body.Children.Add(new TextBlock
        {
            Text = "Data directory",
            Style = (Style)FindResource("MdTitleMedium"),
        });
        body.Children.Add(new TextBlock
        {
            Text = AppPaths.Root,
            Style = (Style)FindResource("MdBodySmall"),
            Margin = new Thickness(0, 4, 0, 0),
            TextWrapping = TextWrapping.Wrap,
        });

        var open = new Button
        {
            Content = "Open folder",
            Style = (Style)FindResource("MdOutlinedButton"),
            MinWidth = 128,
            Margin = new Thickness(0, 12, 0, 0),
            HorizontalAlignment = HorizontalAlignment.Left,
        };
        open.Click += (_, _) => Shell.ShellOperations.OpenDirectory(AppPaths.Root);
        body.Children.Add(open);

        card.Child = body;
        return card;
    }

    public override void OnShown()
    {
        Build();
        SetStatus("Ready");
    }
}

/// <summary>
/// Lets a page ask the shell to navigate without a direct reference to the window type.
/// </summary>
/// <remarks>
/// Pages live in the same assembly as the shell, so this is not about visibility; it keeps
/// the navigation dependency in one place so a page cannot reach into window internals.
/// </remarks>
internal static class MainWindowBridge
{
    public static void Navigate(string destination) =>
        Shell.MainWindow.Instance?.Navigate(destination);
}
