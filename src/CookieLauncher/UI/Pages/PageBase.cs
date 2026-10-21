using System.Windows.Controls;

namespace CookieLauncher.UI.Pages;

/// <summary>
/// Base class for the six top-level pages.
/// </summary>
/// <remarks>
/// A page is created once and reused, so <see cref="OnShown"/> is where dynamic content
/// is refreshed rather than the constructor. That keeps navigation instant: switching
/// back does not rebuild a visual tree, it only re-reads state that may have changed.
/// </remarks>
public abstract class PageBase : UserControl
{
    /// <summary>Called every time the page becomes visible.</summary>
    public virtual void OnShown()
    {
    }

    /// <summary>Reports a message to the shell's status strip.</summary>
    protected static void SetStatus(string message) =>
        Shell.MainWindow.ReportStatus(message);
}
