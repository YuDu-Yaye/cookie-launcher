using System.Windows;
using System.Windows.Controls;
using System.Windows.Media;
using CookieLauncher.Core;

namespace CookieLauncher.UI.Pages;

/// <summary>
/// Play page: the instance hero, quick stats and the crash diagnosis panel.
/// </summary>
/// <remarks>
/// <para>
/// The window is a fixed 1040x680, so the content column is about 960 wide. The page is
/// laid out for exactly that width rather than flowing: a launcher's home screen is
/// scanned, not read, and fixed proportions keep the primary action in the same place
/// every launch.
/// </para>
/// <para>
/// Structure follows M3: a headline block, one hero card carrying the single primary
/// action, a row of supporting stat cards, and a lower-emphasis diagnosis panel.
/// </para>
/// </remarks>
public sealed class PlayPage : PageBase
{
    private readonly TextBlock _instanceName;
    private readonly TextBlock _instanceMeta;
    private readonly TextBlock _diagnosis;
    private readonly TextBlock _memoryValue;
    private readonly TextBlock _versionValue;
    private readonly TextBlock _modsValue;
    private readonly TextBlock _accountValue;
    private readonly Button _playButton;

    public PlayPage()
    {
        Margin = new Thickness(28, 24, 28, 20);

        var root = new Grid();
        root.RowDefinitions.Add(new RowDefinition { Height = GridLength.Auto });   // headline
        root.RowDefinitions.Add(new RowDefinition { Height = GridLength.Auto });   // hero
        root.RowDefinitions.Add(new RowDefinition { Height = GridLength.Auto });   // stats
        root.RowDefinitions.Add(new RowDefinition { Height = new GridLength(1, GridUnitType.Star) }); // diagnosis
        root.RowDefinitions.Add(new RowDefinition { Height = GridLength.Auto });   // footer note

        // ---- Headline ------------------------------------------------------
        var headline = new StackPanel { Margin = new Thickness(0, 0, 0, 18) };
        headline.Children.Add(new TextBlock
        {
            Text = "Ready to play",
            Style = (Style)FindResource("MdHeadlineSmall"),
        });
        headline.Children.Add(new TextBlock
        {
            Text = "Each instance keeps its own world, mods and settings.",
            Style = (Style)FindResource("MdBodyMedium"),
            Margin = new Thickness(0, 4, 0, 0),
        });
        Grid.SetRow(headline, 0);
        root.Children.Add(headline);

        // ---- Hero card -----------------------------------------------------
        var hero = new Border
        {
            Style = (Style)FindResource("MdHeroCard"),
            Margin = new Thickness(0, 0, 0, 14),
        };
        Grid.SetRow(hero, 1);
        root.Children.Add(hero);

        var heroGrid = new Grid();
        heroGrid.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
        heroGrid.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });

        var heroText = new StackPanel { VerticalAlignment = VerticalAlignment.Center };
        _instanceName = new TextBlock
        {
            Text = "No instance yet",
            Style = (Style)FindResource("MdHeadlineMedium"),
            TextTrimming = TextTrimming.CharacterEllipsis,
        };
        _instanceMeta = new TextBlock
        {
            Text = "Create one to get started.",
            Style = (Style)FindResource("MdBodyMedium"),
            Margin = new Thickness(0, 6, 0, 0),
        };

        var heroButtons = new StackPanel
        {
            Orientation = Orientation.Horizontal,
            Margin = new Thickness(0, 20, 0, 0),
        };
        _playButton = new Button
        {
            Content = "Play",
            Style = (Style)FindResource("MdFilledButton"),
            MinWidth = 128,
        };
        _playButton.Click += (_, _) => SetStatus("Launching...");
        var create = new Button
        {
            Content = "New instance",
            Style = (Style)FindResource("MdOutlinedButton"),
            Margin = new Thickness(10, 0, 0, 0),
        };
        create.Click += (_, _) => SetStatus("Create an instance from the Versions page.");
        heroButtons.Children.Add(_playButton);
        heroButtons.Children.Add(create);

        heroText.Children.Add(_instanceName);
        heroText.Children.Add(_instanceMeta);
        heroText.Children.Add(heroButtons);
        Grid.SetColumn(heroText, 0);
        heroGrid.Children.Add(heroText);

        var mark = new AppMark
        {
            Width = 88,
            Height = 88,
            VerticalAlignment = VerticalAlignment.Center,
            Margin = new Thickness(24, 0, 0, 0),
        };
        Grid.SetColumn(mark, 1);
        heroGrid.Children.Add(mark);

        hero.Child = heroGrid;

        // ---- Stat cards ----------------------------------------------------
        var stats = new Grid { Margin = new Thickness(0, 0, 0, 14) };
        for (int i = 0; i < 4; i++)
        {
            stats.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
        }
        _memoryValue = AddStat(stats, 0, "Memory", "4096 MB");
        _versionValue = AddStat(stats, 1, "Version", "-");
        _modsValue = AddStat(stats, 2, "Mods", "0");
        _accountValue = AddStat(stats, 3, "Account", "None");
        Grid.SetRow(stats, 2);
        root.Children.Add(stats);

        // ---- Diagnosis -----------------------------------------------------
        var diagnosisCard = new Border
        {
            Style = (Style)FindResource("MdCardOutlined"),
            Padding = new Thickness(20, 16, 20, 16),
            VerticalAlignment = VerticalAlignment.Top,
        };
        var diagnosisStack = new StackPanel();
        var diagnosisHeader = new Grid();
        diagnosisHeader.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
        diagnosisHeader.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });

        var diagnosisTitle = new TextBlock
        {
            Text = "Last crash diagnosis",
            Style = (Style)FindResource("MdTitleSmall"),
            VerticalAlignment = VerticalAlignment.Center,
        };
        Grid.SetColumn(diagnosisTitle, 0);
        diagnosisHeader.Children.Add(diagnosisTitle);

        var analyse = new Button
        {
            Content = "Analyse",
            Style = (Style)FindResource("MdTextButton"),
            Height = 32,
            Padding = new Thickness(12, 0, 12, 0),
        };
        analyse.Click += (_, _) => SetStatus("Reading the game log...");
        Grid.SetColumn(analyse, 1);
        diagnosisHeader.Children.Add(analyse);

        diagnosisStack.Children.Add(diagnosisHeader);
        _diagnosis = new TextBlock
        {
            Text = "No crash recorded yet. When a launch fails, this panel names the cause "
                 + "and the fix instead of showing a stack trace.",
            Style = (Style)FindResource("MdBodySmall"),
            Margin = new Thickness(0, 8, 0, 0),
        };
        diagnosisStack.Children.Add(_diagnosis);
        diagnosisCard.Child = diagnosisStack;
        Grid.SetRow(diagnosisCard, 3);
        root.Children.Add(diagnosisCard);

        // ---- Footer note ---------------------------------------------------
        var footer = new TextBlock
        {
            Text = "Data directory: " + AppPaths.Root,
            Style = (Style)FindResource("MdLabelSmall"),
            Margin = new Thickness(0, 12, 0, 0),
            TextTrimming = TextTrimming.CharacterEllipsis,
            Opacity = 0.7,
        };
        Grid.SetRow(footer, 4);
        root.Children.Add(footer);

        Content = root;

        // Reflect whatever the stores already hold, so the page is correct on first show.
        Refresh();
    }

    /// <summary>Creates one stat card and returns its value label.</summary>
    private TextBlock AddStat(Grid grid, int column, string caption, string value)
    {
        var card = new Border
        {
            Style = (Style)FindResource("MdCardFilled"),
            Padding = new Thickness(16, 12, 16, 12),
            Margin = new Thickness(column == 0 ? 0 : 10, 0, 0, 0),
        };
        var stack = new StackPanel();
        stack.Children.Add(new TextBlock
        {
            Text = caption,
            Style = (Style)FindResource("MdLabelSmall"),
        });
        var valueLabel = new TextBlock
        {
            Text = value,
            Style = (Style)FindResource("MdTitleMedium"),
            Margin = new Thickness(0, 3, 0, 0),
            TextTrimming = TextTrimming.CharacterEllipsis,
        };
        stack.Children.Add(valueLabel);
        card.Child = stack;
        Grid.SetColumn(card, column);
        grid.Children.Add(card);
        return valueLabel;
    }

    /// <summary>Re-reads the instance and account stores into the page.</summary>
    public void Refresh()
    {
        Config settings = App.Settings;
        _memoryValue.Text = settings.MaxMemoryMb + " MB";

        Instance? instance = LauncherState.Current.Instance;
        if (instance is null)
        {
            _instanceName.Text = "No instance yet";
            _instanceMeta.Text = "Pick a version to install, then play.";
            _versionValue.Text = "-";
            _modsValue.Text = "0";
            _playButton.Content = "Install a version";
        }
        else
        {
            _instanceName.Text = instance.Name;
            _instanceMeta.Text = instance.Subtitle();
            _versionValue.Text = string.IsNullOrWhiteSpace(instance.Version) ? "-" : instance.Version;
            _modsValue.Text = LauncherState.Current.ModCount(instance).ToString();
            _playButton.Content = "Play";
        }

        Account? account = LauncherState.Current.SelectedAccount;
        _accountValue.Text = account is null ? "None" : account.Username;
    }

    public override void OnShown()
    {
        Refresh();
        SetStatus("Ready");
    }
}
