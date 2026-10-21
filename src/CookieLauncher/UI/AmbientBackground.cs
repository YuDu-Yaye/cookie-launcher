using System.Windows;
using System.Windows.Media;
using System.Windows.Media.Animation;

namespace CookieLauncher.UI;

/// <summary>
/// A slowly drifting colour haze painted behind the glass.
/// </summary>
/// <remarks>
/// <para>
/// Three soft radial blobs in the scheme's primary, secondary and tertiary hues drift on
/// independent, long, non-integer periods so the pattern never visibly repeats. Only
/// render transforms move, so the drift costs no layout and no re-rasterisation beyond
/// the cached blobs themselves.
/// </para>
/// <para>
/// This is the component that gives the shell its depth. Combined with translucent glass
/// surfaces on top, the window reads as layered rather than flat. The periods are
/// deliberately long (37s, 53s, 71s): a fast cycle looks like a screen saver, and a
/// launcher is a window people leave open for hours.
/// </para>
/// </remarks>
public sealed class AmbientBackground : FrameworkElement
{
    private readonly DrawingVisual _visual = new();

    private readonly TranslateTransform _driftA = new();
    private readonly TranslateTransform _driftB = new();
    private readonly TranslateTransform _driftC = new();

    /// <summary>
    /// One breathe transform per blob. They are separate instances because a Freezable
    /// cannot be shared by two parents, and each has to be animated individually.
    /// </summary>
    private readonly ScaleTransform[] _breathe = [new(1, 1), new(1, 1), new(1, 1)];

    private Color _colorA = Color.FromRgb(0xC9, 0x7F, 0x3E);
    private Color _colorB = Color.FromRgb(0x8A, 0x4F, 0x76);
    private Color _colorC = Color.FromRgb(0x3F, 0x51, 0x8A);

    private bool _running;

    public AmbientBackground()
    {
        AddVisualChild(_visual);
        AddLogicalChild(_visual);
        IsHitTestVisible = false;
        ClipToBounds = true;
        Loaded += (_, _) => Redraw();
    }

    protected override int VisualChildrenCount => 1;

    protected override Visual GetVisualChild(int index) => _visual;

    /// <summary>Retints the haze to follow the active scheme.</summary>
    public void SetColors(Color primary, Color secondary, Color tertiary)
    {
        _colorA = primary;
        _colorB = secondary;
        _colorC = tertiary;
        Redraw();
    }

    /// <summary>Sets the haze opacity, so the light scheme can be calmer than the dark one.</summary>
    public void SetIntensity(double value)
    {
        Opacity = Math.Clamp(value, 0, 1);
    }

    /// <summary>Starts or stops the drift. Stopping freezes it where it is.</summary>
    public void SetRunning(bool running)
    {
        if (running == _running)
        {
            return;
        }

        _running = running;
        if (!running)
        {
            _driftA.BeginAnimation(TranslateTransform.XProperty, null);
            _driftA.BeginAnimation(TranslateTransform.YProperty, null);
            _driftB.BeginAnimation(TranslateTransform.XProperty, null);
            _driftB.BeginAnimation(TranslateTransform.YProperty, null);
            _driftC.BeginAnimation(TranslateTransform.XProperty, null);
            _driftC.BeginAnimation(TranslateTransform.YProperty, null);
            foreach (ScaleTransform breathe in _breathe)
            {
                breathe.BeginAnimation(ScaleTransform.ScaleXProperty, null);
                breathe.BeginAnimation(ScaleTransform.ScaleYProperty, null);
            }
            return;
        }

        // Long, prime-ish periods, so the three blobs only line up again after minutes.
        Drift(_driftA, -70, 70, -40, 40, 37);
        Drift(_driftB, 60, -60, 50, -30, 53);
        Drift(_driftC, -40, 40, 60, -50, 71);

        var ease = new SineEase { EasingMode = EasingMode.EaseInOut };
        foreach (ScaleTransform breathe in _breathe)
        {
            breathe.BeginAnimation(
                ScaleTransform.ScaleXProperty,
                new DoubleAnimation(1, 1.08, TimeSpan.FromSeconds(23))
                {
                    AutoReverse = true,
                    RepeatBehavior = RepeatBehavior.Forever,
                    EasingFunction = ease,
                });
            breathe.BeginAnimation(
                ScaleTransform.ScaleYProperty,
                new DoubleAnimation(1, 1.08, TimeSpan.FromSeconds(23))
                {
                    AutoReverse = true,
                    RepeatBehavior = RepeatBehavior.Forever,
                    EasingFunction = ease,
                });
        }
    }

    private static void Drift(
        TranslateTransform transform,
        double x1, double x2, double y1, double y2, double seconds)
    {
        var ease = new SineEase { EasingMode = EasingMode.EaseInOut };
        transform.BeginAnimation(
            TranslateTransform.XProperty,
            new DoubleAnimation(x1, x2, TimeSpan.FromSeconds(seconds))
            {
                AutoReverse = true,
                RepeatBehavior = RepeatBehavior.Forever,
                EasingFunction = ease,
            });
        transform.BeginAnimation(
            TranslateTransform.YProperty,
            new DoubleAnimation(y1, y2, TimeSpan.FromSeconds(seconds * 1.37))
            {
                AutoReverse = true,
                RepeatBehavior = RepeatBehavior.Forever,
                EasingFunction = ease,
            });
    }

    protected override void OnRenderSizeChanged(SizeChangedInfo info)
    {
        base.OnRenderSizeChanged(info);
        Redraw();
    }

    private void Redraw()
    {
        double w = ActualWidth;
        double h = ActualHeight;
        if (w <= 1 || h <= 1)
        {
            return;
        }

        // The breathe scales about the element centre, so the haze expands in place rather
        // than creeping toward a corner.
        foreach (ScaleTransform breathe in _breathe)
        {
            breathe.CenterX = w / 2;
            breathe.CenterY = h / 2;
        }

        using DrawingContext dc = _visual.RenderOpen();

        // Three overlapping soft blobs; each fades to transparent so they blend by eye
        // without needing a real blend mode.
        dc.DrawRectangle(
            Blob(_colorA, new Point(0.26, 0.32), Math.Max(w, h) * 0.52, _driftA, 0),
            null,
            new Rect(0, 0, w, h));
        dc.DrawRectangle(
            Blob(_colorB, new Point(0.76, 0.24), Math.Max(w, h) * 0.46, _driftB, 1),
            null,
            new Rect(0, 0, w, h));
        dc.DrawRectangle(
            Blob(_colorC, new Point(0.55, 0.78), Math.Max(w, h) * 0.50, _driftC, 2),
            null,
            new Rect(0, 0, w, h));
    }

    private Brush Blob(
        Color color, Point centre, double radius, TranslateTransform drift, int blobIndex)
    {
        double w = ActualWidth;
        double h = ActualHeight;

        // Absolute mapping: centre and radius are in pixels, and so is the drift transform.
        // RelativeToBoundingBox would make the drift's "70" mean 70 element widths.
        var brush = new RadialGradientBrush
        {
            MappingMode = BrushMappingMode.Absolute,
            Center = new Point(centre.X * w, centre.Y * h),
            GradientOrigin = new Point(centre.X * w, centre.Y * h),
            RadiusX = radius,
            RadiusY = radius,
        };

        brush.GradientStops.Add(new GradientStop(WithAlpha(color, 0x4A), 0.0));
        brush.GradientStops.Add(new GradientStop(WithAlpha(color, 0x22), 0.45));
        brush.GradientStops.Add(new GradientStop(WithAlpha(color, 0x00), 1.0));

        // The drift transform is animated, so this brush must NOT be frozen: a frozen
        // Freezable cannot host an animated sub-object.
        brush.Transform = new TransformGroup { Children = { drift, _breathe[blobIndex] } };

        return brush;
    }

    private static Color WithAlpha(Color color, byte alpha)
        => Color.FromArgb(alpha, color.R, color.G, color.B);
}
