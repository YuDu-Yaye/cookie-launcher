using System.Windows;
using System.Windows.Controls;
using System.Windows.Media;

namespace CookieLauncher.UI.Pages;

/// <summary>Logs page.</summary>
public sealed class LogPage : PageBase
{
    public LogPage()
    {
        Margin = new Thickness(24, 20, 24, 20);
        var stack = new StackPanel();
        stack.Children.Add(new TextBlock
        {
            Text = "Logs",
            FontSize = 30,
            Foreground = (Brush)FindResource("OnSurfaceBrush"),
        });
        stack.Children.Add(new TextBlock
        {
            Text = "Live launcher and game output with crash analysis.",
            FontSize = 13,
            Margin = new Thickness(0, 4, 0, 0),
            Foreground = (Brush)FindResource("OnSurfaceVariantBrush"),
        });
        Content = stack;
    }
}
