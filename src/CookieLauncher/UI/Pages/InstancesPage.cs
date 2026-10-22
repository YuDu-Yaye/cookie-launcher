using System.IO;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Markup;
using System.Windows.Media;
using CookieLauncher.Core;

namespace CookieLauncher.UI.Pages;

/// <summary>
/// Instances page: one card per game instance, the picker PCL2 keeps on its left rail.
/// </summary>
/// <remarks>
/// <para>
/// An instance is a directory plus a small record, so the card shows the three things that
/// actually differ between instances: the version, the loader, and how much has been played.
/// Everything else is identical by construction.
/// </para>
/// <para>
/// Selection is the primary action here. Every other page reads the selected instance, which
/// is why choosing one navigates back to Play rather than staying on this page.
/// </para>
/// </remarks>
public sealed class InstancesPage : PageBase
{
    private readonly StackPanel _cards;
    private readonly TextBlock _summary;
    private readonly ScrollViewer _scroll;

    public InstancesPage()
    {
        Margin = new Thickness(28, 22, 28, 18);

        var root = new Grid();
        root.RowDefinitions.Add(new RowDefinition { Height = GridLength.Auto });
        root.RowDefinitions.Add(new RowDefinition { Height = new GridLength(1, GridUnitType.Star) });
        root.RowDefinitions.Add(new RowDefinition { Height = GridLength.Auto });

        // ---- Headline with the create action -------------------------------
        var header = new Grid { Margin = new Thickness(0, 0, 0, 16) };
        header.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
        header.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });

        var titles = new StackPanel();
        titles.Children.Add(new TextBlock
        {
            Text = "Instances",
            Style = (Style)FindResource("MdHeadlineSmall"),
        });
        titles.Children.Add(new TextBlock
        {
            Text = "Each instance keeps its own world, mods and settings.",
            Style = (Style)FindResource("MdBodyMedium"),
            Margin = new Thickness(0, 3, 0, 0),
        });
        Grid.SetColumn(titles, 0);
        header.Children.Add(titles);

        var create = new Button
        {
            Content = "New instance",
            Style = (Style)FindResource("MdFilledButton"),
            MinWidth = 136,
            VerticalAlignment = VerticalAlignment.Top,
        };
        create.Click += (_, _) => CreateInstance();
        Grid.SetColumn(create, 1);
        header.Children.Add(create);

        Grid.SetRow(header, 0);
        root.Children.Add(header);

        // ---- Card list -----------------------------------------------------
        _cards = new StackPanel();
        _scroll = new ScrollViewer
        {
            Content = _cards,
            VerticalScrollBarVisibility = ScrollBarVisibility.Auto,
            HorizontalScrollBarVisibility = ScrollBarVisibility.Disabled,
            Padding = new Thickness(0, 0, 8, 0),
        };
        Grid.SetRow(_scroll, 1);
        root.Children.Add(_scroll);

        // ---- Footer --------------------------------------------------------
        _summary = new TextBlock
        {
            Style = (Style)FindResource("MdLabelSmall"),
            Margin = new Thickness(0, 10, 0, 0),
        };
        Grid.SetRow(_summary, 2);
        root.Children.Add(_summary);

        Content = root;
    }

    public override void OnShown()
    {
        Rebuild();
        SetStatus("Ready");
    }

    private void Rebuild()
    {
        _cards.Children.Clear();
        InstanceStore store = LauncherState.Current.Instances;
        IReadOnlyList<Instance> instances = store.All;
        Instance? selected = store.Selected;

        if (instances.Count == 0)
        {
            _cards.Children.Add(EmptyState());
            _summary.Text = "No instances yet.";
            return;
        }

        // Most recently played first, which is the order a returning player expects.
        IEnumerable<Instance> ordered = instances
            .OrderByDescending(instance => instance.LastPlayedAt)
            .ThenBy(instance => instance.Name, StringComparer.OrdinalIgnoreCase);

        foreach (Instance instance in ordered)
        {
            _cards.Children.Add(InstanceCard(instance,
                selected is not null && selected.Id == instance.Id));
        }

        _summary.Text = $"{instances.Count} instance{(instances.Count == 1 ? "" : "s")}"
            + (selected is null ? "" : $"  ·  selected: {selected.Name}");
    }

    private Border EmptyState()
    {
        StackPanel stack = new()
        {
            HorizontalAlignment = HorizontalAlignment.Center,
            Margin = new Thickness(0, 40, 0, 0),
        };
        stack.Children.Add(new TextBlock
        {
            Text = "No instances yet",
            Style = (Style)FindResource("MdTitleMedium"),
            HorizontalAlignment = HorizontalAlignment.Center,
        });
        stack.Children.Add(new TextBlock
        {
            Text = "Create one, then pick a version on the Versions page.",
            Style = (Style)FindResource("MdBodySmall"),
            HorizontalAlignment = HorizontalAlignment.Center,
            Margin = new Thickness(0, 6, 0, 0),
        });
        return new Border { Child = stack };
    }

    private Border InstanceCard(Instance instance, bool isSelected)
    {
        Border card = new()
        {
            Style = (Style)FindResource("MdCardFilled"),
            Padding = new Thickness(20, 16, 20, 16),
            Margin = new Thickness(0, 0, 0, 10),
            BorderBrush = (Brush)FindResource(isSelected ? "PrimaryBrush" : "DividerBrush"),
            BorderThickness = new Thickness(isSelected ? 2 : 1),
            Cursor = System.Windows.Input.Cursors.Hand,
        };

        Grid content = new();
        content.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
        content.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });

        StackPanel text = new() { VerticalAlignment = VerticalAlignment.Center };
        text.Children.Add(new TextBlock
        {
            Text = instance.Name,
            Style = (Style)FindResource("MdTitleMedium"),
        });

        string meta = instance.Subtitle();
        text.Children.Add(new TextBlock
        {
            Text = meta,
            Style = (Style)FindResource("MdBodySmall"),
            Margin = new Thickness(0, 4, 0, 0),
        });

        string played = instance.LastPlayedAt <= 0
            ? "Never played"
            : "Last played " + DateTimeOffset.FromUnixTimeMilliseconds(instance.LastPlayedAt)
                .LocalDateTime.ToString("yyyy-MM-dd HH:mm");
        text.Children.Add(new TextBlock
        {
            Text = played + (instance.PlayTimeMs > 0
                ? "  ·  " + FormatPlayTime(instance.PlayTimeMs)
                : ""),
            Style = (Style)FindResource("MdLabelSmall"),
            Margin = new Thickness(0, 6, 0, 0),
            Opacity = 0.75,
        });

        Grid.SetColumn(text, 0);
        content.Children.Add(text);

        StackPanel actions = new()
        {
            Orientation = Orientation.Horizontal,
            VerticalAlignment = VerticalAlignment.Center,
        };

        if (!isSelected)
        {
            var use = new Button
            {
                Content = "Select",
                Style = (Style)FindResource("MdTonalButton"),
                MinWidth = 92,
                Margin = new Thickness(0, 0, 8, 0),
            };
            use.Click += (_, _) =>
            {
                LauncherState.Current.Instances.Select(instance.Id);
                App.SaveSettings();
                SetStatus("Selected " + instance.Name);
                Rebuild();
            };
            actions.Children.Add(use);
        }
        else
        {
            var active = new Border
            {
                Height = 24,
                CornerRadius = new CornerRadius(12),
                Padding = new Thickness(12, 0, 12, 0),
                Background = (Brush)FindResource("SecondaryContainerBrush"),
                VerticalAlignment = VerticalAlignment.Center,
                Margin = new Thickness(0, 0, 8, 0),
                Child = new TextBlock
                {
                    Text = "Selected",
                    FontSize = 11,
                    VerticalAlignment = VerticalAlignment.Center,
                    Foreground = (Brush)FindResource("OnSecondaryContainerBrush"),
                },
            };
            actions.Children.Add(active);
        }

        var folder = new Button
        {
            Content = "Folder",
            Style = (Style)FindResource("MdTextButton"),
            MinWidth = 84,
        };
        folder.Click += (_, _) =>
        {
            Directory.CreateDirectory(instance.ResolvedGameDir);
            Shell.ShellOperations.Open(instance.ResolvedGameDir);
        };
        actions.Children.Add(folder);

        Grid.SetColumn(actions, 1);
        content.Children.Add(actions);

        card.Child = content;
        card.MouseLeftButtonUp += (_, _) =>
        {
            if (!isSelected)
            {
                LauncherState.Current.Instances.Select(instance.Id);
                SetStatus("Selected " + instance.Name);
                Rebuild();
            }
        };
        return card;
    }

    private static string FormatPlayTime(long milliseconds)
    {
        TimeSpan span = TimeSpan.FromMilliseconds(milliseconds);
        return span.TotalHours >= 1
            ? $"{(int)span.TotalHours}h {span.Minutes}m played"
            : $"{Math.Max(1, span.Minutes)}m played";
    }

    private void CreateInstance()
    {
        Instance created = LauncherState.Current.Instances.Create(null, "");
        SetStatus("Created " + created.Name + ". Pick a version on the Versions page.");
        Rebuild();
    }
}
