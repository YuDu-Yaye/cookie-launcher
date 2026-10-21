using System.Windows;
using System.Windows.Controls;
using System.Windows.Media;

namespace CookieLauncher.UI.Pages;

/// <summary>Accounts page.</summary>
public sealed class AccountsPage : PageBase
{
    public AccountsPage()
    {
        Margin = new Thickness(24, 20, 24, 20);
        var stack = new StackPanel();
        stack.Children.Add(new TextBlock
        {
            Text = "Accounts",
            FontSize = 30,
            Foreground = (Brush)FindResource("OnSurfaceBrush"),
        });
        stack.Children.Add(new TextBlock
        {
            Text = "Offline, authlib and Microsoft accounts.",
            FontSize = 13,
            Margin = new Thickness(0, 4, 0, 0),
            Foreground = (Brush)FindResource("OnSurfaceVariantBrush"),
        });
        Content = stack;
    }
}
