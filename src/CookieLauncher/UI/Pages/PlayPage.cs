using System.Windows;
using System.Windows.Controls;
using System.Windows.Media;

namespace CookieLauncher.UI.Pages;

/// <summary>
/// Play page: the instance card, its launch button and the crash diagnosis panel.
/// </summary>
/// <remarks>
/// This is the page the user sees on every start, so it carries the primary action and
/// exactly one piece of secondary information: whether the last launch failed and why.
/// </remarks>
public sealed class PlayPage : PageBase
{
    private readonly TextBlock _instanceName;
    private readonly TextBlock _instanceMeta;
    private readonly Button _playButton;
    private readonly TextBlock _diagnosis;

    public PlayPage()
    {
        Margin = new Thickness(24, 20, 24, 20);

        var root = new Grid();
        root.RowDefinitions.Add(new RowDefinition { Height = GridLength.Auto });
        root.RowDefinitions.Add(new RowDefinition { Height = new GridLength(16) });
        root.RowDefinitions.Add(new RowDefinition { Height = new GridLength(1, GridUnitType.Star) });

        // ---- Header -------------------------------------------------------
        var header = new StackPanel();
        var title = new TextBlock
        {
            Text = "Ready to play",
            FontSize = 30,
            FontWeight = FontWeights.Normal,
            Foreground = (Brush)FindResource("OnSurfaceBrush"),
        };
        var subtitle = new TextBlock
        {
            Text = "Each instance keeps its own world, mods and settings.",
            FontSize = 13,
            Margin = new Thickness(0, 4, 0, 0),
            Foreground = (Brush)FindResource("OnSurfaceVariantBrush"),
        };
        header.Children.Add(title);
        header.Children.Add(subtitle);
        Grid.SetRow(header, 0);
        root.Children.Add(header);

        // ---- Hero card -----------------------------------------------------
        var hero = new Border
        {
            CornerRadius = new CornerRadius(16),
            Background = (Brush)FindResource("SurfaceContainerHighBrush"),
            BorderBrush = (Brush)FindResource("DividerBrush"),
            BorderThickness = new Thickness(1),
            Padding = new Thickness(24),
        };

        var heroGrid = new Grid();
        heroGrid.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
        heroGrid.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });

        var left = new StackPanel();
        _instanceName = new TextBlock
        {
            Text = "No instance yet",
            FontSize = 26,
            Foreground = (Brush)FindResource("OnSurfaceBrush"),
        };
        _instanceMeta = new TextBlock
        {
            FontSize = 13,
            Margin = new Thickness(0, 8, 0, 0),
            Foreground = (Brush)FindResource("OnSurfaceVariantBrush"),
        };

        var buttons = new StackPanel
        {
            Orientation = Orientation.Horizontal,
            Margin = new Thickness(0, 22, 0, 0),
        };
        _playButton = new Button
        {
            Content = "Play",
            Style = (Style)FindResource("MdFilledButton"),
            MinWidth = 140,
            Height = 48,
        };
        var newButton = new Button
        {
            Content = "New instance",
            Style = (Style)FindResource("MdOutlinedButton"),
            Margin = new Thickness(12, 0, 0, 0),
        };
        buttons.Children.Add(_playButton);
        buttons.Children.Add(newButton);

        left.Children.Add(_instanceName);
        left.Children.Add(_instanceMeta);
        left.Children.Add(buttons);
        Grid.SetColumn(left, 0);
        heroGrid.Children.Add(left);

        var mark = new UI.AppMark
        {
            Width = 92,
            Height = 92,
            VerticalAlignment = VerticalAlignment.Center,
            Margin = new Thickness(24, 0, 0, 0),
        };
        Grid.SetColumn(mark, 1);
        heroGrid.Children.Add(mark);


        // ---- Diagnosis card ------------------------------------------------
        var diagnosisCard = new Border
        {
            CornerRadius = new CornerRadius(16),
            Background = (Brush)FindResource("SurfaceContainerBrush"),
            BorderBrush = (Brush)FindResource("DividerBrush"),
            BorderThickness = new Thickness(1),
            Padding = new Thickness(22),
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
            Text = "No crash recorded yet. If the game fails to start, the log page will "
                 + "turn the reason into a concrete fix.",
            FontSize = 12,
            Margin = new Thickness(0, 10, 0, 0),
            TextWrapping = TextWrapping.Wrap,
            Foreground = (Brush)FindResource("OnSurfaceVariantBrush"),
        };
        diagnosisStack.Children.Add(_diagnosis);
        diagnosisCard.Child = diagnosisStack;

        var body = new Grid();
        body.RowDefinitions.Add(new RowDefinition { Height = GridLength.Auto });
        body.RowDefinitions.Add(new RowDefinition { Height = new GridLength(16) });
        body.RowDefinitions.Add(new RowDefinition { Height = new GridLength(1, GridUnitType.Star) });
        Grid.SetRow(hero, 0);
        body.Children.Add(hero);
        Grid.SetRow(diagnosisCard, 2);
        body.Children.Add(diagnosisCard);

        root.Children.Add(body);

        Content = root;
    }

    public override void OnShown()
    {
        SetStatus("Ready");
    }
}
