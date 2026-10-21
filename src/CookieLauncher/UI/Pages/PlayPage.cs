using System.Windows;
using System.Windows.Controls;
using System.Windows.Media;

namespace CookieLauncher.UI.Pages;

/// <summary>
/// Play page: the instance card, its launch action and the crash diagnosis panel.
/// </summary>
/// <remarks>
/// <para>
/// This is the page shown on every start, so it carries exactly one primary action and one
/// piece of secondary information: whether the last launch failed and what to do about it.
/// </para>
/// <para>
/// Content is capped at 880dp and centred. Stretching a card across a 1440-wide window
/// leaves a metre of dead space between its title and its button, which is what made the
/// first layout look unfinished.
/// </para>
/// </remarks>
public sealed class PlayPage : PageBase
{
    private readonly TextBlock _instanceName;
    private readonly TextBlock _instanceMeta;
    private readonly TextBlock _diagnosis;

    public PlayPage()
    {
        var scroll = new ScrollViewer
        {
            VerticalScrollBarVisibility = ScrollBarVisibility.Auto,
            HorizontalScrollBarVisibility = ScrollBarVisibility.Disabled,
        };

        var outer = new Grid { Margin = new Thickness(36, 30, 36, 30) };
        outer.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
        outer.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(880, GridUnitType.Pixel) });
        outer.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });

        var column = new StackPanel();
        Grid.SetColumn(column, 1);
        outer.Children.Add(column);

        // ---- Header -------------------------------------------------------
        column.Children.Add(new TextBlock
        {
            Text = "Ready to play",
            FontSize = 32,
            FontWeight = FontWeights.Normal,
            Foreground = (Brush)FindResource("OnSurfaceBrush"),
        });
        column.Children.Add(new TextBlock
        {
            Text = "Each instance keeps its own world, mods and settings.",
            FontSize = 13.5,
            Margin = new Thickness(0, 6, 0, 0),
            Foreground = (Brush)FindResource("OnSurfaceVariantBrush"),
        });

        // ---- Hero card -----------------------------------------------------
        var hero = new Border
        {
            CornerRadius = new CornerRadius(24),
            Background = (Brush)FindResource("SurfaceContainerHighBrush"),
            BorderBrush = (Brush)FindResource("DividerBrush"),
            BorderThickness = new Thickness(1),
            Padding = new Thickness(32, 28, 32, 28),
            Margin = new Thickness(0, 26, 0, 0),
        };

        var heroGrid = new Grid();
        heroGrid.ColumnDefinitions.Add(
            new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
        heroGrid.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });

        var left = new StackPanel { VerticalAlignment = VerticalAlignment.Center };
        _instanceName = new TextBlock
        {
            Text = "No instance yet",
            FontSize = 26,
            Foreground = (Brush)FindResource("OnSurfaceBrush"),
            TextTrimming = TextTrimming.CharacterEllipsis,
        };
        _instanceMeta = new TextBlock
        {
            FontSize = 13.5,
            Margin = new Thickness(0, 8, 0, 0),
            Foreground = (Brush)FindResource("OnSurfaceVariantBrush"),
            TextTrimming = TextTrimming.CharacterEllipsis,
        };

        var buttons = new StackPanel
        {
            Orientation = Orientation.Horizontal,
            Margin = new Thickness(0, 26, 0, 0),
        };
        var play = new Button
        {
            Content = "Play",
            Style = (Style)FindResource("MdFilledButton"),
            MinWidth = 148,
            Height = 48,
            FontSize = 14,
        };
        play.Click += (_, _) => SetStatus("Launching...");
        var create = new Button
        {
            Content = "New instance",
            Style = (Style)FindResource("MdOutlinedButton"),
            Margin = new Thickness(12, 0, 0, 0),
            Height = 48,
        };
        buttons.Children.Add(play);
        buttons.Children.Add(create);

        left.Children.Add(_instanceName);
        left.Children.Add(_instanceMeta);
        left.Children.Add(buttons);
        Grid.SetColumn(left, 0);
        heroGrid.Children.Add(left);

        var mark = new AppMark
        {
            Width = 96,
            Height = 96,
            VerticalAlignment = VerticalAlignment.Center,
            Margin = new Thickness(28, 0, 0, 0),
        };
        Grid.SetColumn(mark, 1);
        heroGrid.Children.Add(mark);

        hero.Child = heroGrid;
        column.Children.Add(hero);

        // ---- Quick stats ---------------------------------------------------
        var stats = new Grid { Margin = new Thickness(0, 16, 0, 0) };
        for (int i = 0; i < 4; i++)
        {
            stats.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
        }
        AddStat(stats, 0, "Memory", "4096 MB");
        AddStat(stats, 1, "Version", "-");
        AddStat(stats, 2, "Mods", "0");
        AddStat(stats, 3, "Account", "None");
        column.Children.Add(stats);

        // ---- Diagnosis card ------------------------------------------------
        var diagnosisCard = new Border
        {
            CornerRadius = new CornerRadius(20),
            Background = (Brush)FindResource("SurfaceContainerBrush"),
            BorderBrush = (Brush)FindResource("DividerBrush"),
            BorderThickness = new Thickness(1),
            Padding = new Thickness(24, 20, 24, 20),
            Margin = new Thickness(0, 16, 0, 0),
        };
        var diagnosisStack = new StackPanel();
        diagnosisStack.Children.Add(new TextBlock
        {
            Text = "Last crash diagnosis",
            FontSize = 14,
            FontWeight = FontWeights.Medium,
            Foreground = (Brush)FindResource("OnSurfaceBrush"),
        });
        _diagnosis = new TextBlock
        {
            Text = "No crash recorded yet. If the game fails to start, the Logs page "
                 + "turns the reason into a concrete fix.",
            FontSize = 12.5,
            Margin = new Thickness(0, 10, 0, 0),
            TextWrapping = TextWrapping.Wrap,
            Foreground = (Brush)FindResource("OnSurfaceVariantBrush"),
        };
        diagnosisStack.Children.Add(_diagnosis);
        diagnosisCard.Child = diagnosisStack;
        column.Children.Add(diagnosisCard);

        scroll.Content = outer;
        Content = scroll;
    }

    private void AddStat(Grid grid, int column, string caption, string value)
    {
        var card = new Border
        {
            CornerRadius = new CornerRadius(14),
            Background = (Brush)FindResource("SurfaceContainerBrush"),
            BorderBrush = (Brush)FindResource("DividerBrush"),
            BorderThickness = new Thickness(1),
            Padding = new Thickness(18, 14, 18, 14),
            Margin = new Thickness(column == 0 ? 0 : 8, 0, 0, 0),
        };
        var stack = new StackPanel();
        stack.Children.Add(new TextBlock
        {
            Text = caption,
            FontSize = 11,
            Foreground = (Brush)FindResource("OnSurfaceVariantBrush"),
        });
        stack.Children.Add(new TextBlock
        {
            Text = value,
            FontSize = 17,
            FontWeight = FontWeights.Medium,
            Margin = new Thickness(0, 4, 0, 0),
            Foreground = (Brush)FindResource("OnSurfaceBrush"),
        });
        card.Child = stack;
        Grid.SetColumn(card, column);
        grid.Children.Add(card);
    }

    public override void OnShown()
    {
        SetStatus("Ready");
    }
}
