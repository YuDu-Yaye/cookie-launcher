using System.Windows;
using System.Windows.Controls;
using System.Windows.Media;

namespace CookieLauncher.UI.Pages;

/// <summary>Mods page.</summary>
public sealed class ModsPage : PageBase
{
    public ModsPage()
    {
        Margin = new Thickness(24, 20, 24, 20);
        var stack = new StackPanel();
        stack.Children.Add(new TextBlock
        {
            Text = "Mods",
            FontSize = 30,
            Foreground = (Brush)FindResource("OnSurfaceBrush"),
        });
        stack.Children.Add(new TextBlock
        {
            Text = "Enable, disable and remove mods for this instance.",
            FontSize = 13,
            Margin = new Thickness(0, 4, 0, 0),
            Foreground = (Brush)FindResource("OnSurfaceVariantBrush"),
        });
        Content = stack;
    }
}
