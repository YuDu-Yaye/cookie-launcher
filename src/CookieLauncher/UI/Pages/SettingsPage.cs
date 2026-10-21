using System.Windows;
using System.Windows.Controls;
using System.Windows.Media;

namespace CookieLauncher.UI.Pages;

/// <summary>Settings page.</summary>
public sealed class SettingsPage : PageBase
{
    public SettingsPage()
    {
        Margin = new Thickness(24, 20, 24, 20);
        var stack = new StackPanel();
        stack.Children.Add(new TextBlock
        {
            Text = "Settings",
            FontSize = 30,
            Foreground = (Brush)FindResource("OnSurfaceBrush"),
        });
        stack.Children.Add(new TextBlock
        {
            Text = "Java, downloads, appearance and paths.",
            FontSize = 13,
            Margin = new Thickness(0, 4, 0, 0),
            Foreground = (Brush)FindResource("OnSurfaceVariantBrush"),
        });
        Content = stack;
    }
}
