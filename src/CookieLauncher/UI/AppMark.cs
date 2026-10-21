using System.Windows;
using System.Windows.Media;

namespace CookieLauncher.UI;

/// <summary>
/// The appliqu&#233; mark: a cookie with chip marks, drawn as vectors so it stays crisp at
/// every DPI and needs no image asset.
/// </summary>
public sealed class AppMark : FrameworkElement
{
    protected override void OnRender(DrawingContext dc)
    {
        double size = Math.Min(ActualWidth, ActualHeight);
        if (size <= 1)
        {
            return;
        }

        var primary = (Brush)FindResource("PrimaryBrush");
        var surface = (Brush)FindResource("SurfaceBrush");

        double inset = size * 0.06;
        double diameter = size - inset * 2;

        dc.DrawEllipse(primary, null, new Point(size / 2, size / 2), diameter / 2, diameter / 2);

        // Four chips, deliberately unequal: a symmetric pattern reads as a dial, not a
        // cookie.
        double chip = size * 0.13;
        var chips = new (double X, double Y)[]
        {
            (0.36, 0.30),
            (0.60, 0.42),
            (0.32, 0.58),
            (0.58, 0.66),
        };
        foreach ((double x, double y) in chips)
        {
            dc.DrawEllipse(surface, null,
                new Point(size * x, size * y), chip / 2, chip / 2);
        }
    }
}
