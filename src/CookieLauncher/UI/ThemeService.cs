using System.Windows;
using System.Windows.Media;
using CookieLauncher.Material;

namespace CookieLauncher.UI;

/// <summary>
/// Writes the generated Material 3 scheme into the application resource dictionary.
/// </summary>
/// <remarks>
/// <para>
/// Styles and templates in <c>App.xaml</c> reference colours through
/// <c>DynamicResource</c>, so replacing these brushes repaints the whole shell without
/// reloading any style. That is the same mechanism SkyLite's theme manager uses, and it
/// is why switching schemes is instant rather than a window rebuild.
/// </para>
/// <para>
/// Both a <c>Color</c> and a <c>Brush</c> are published per role: templates that animate
/// a colour need the raw value, while everything else binds the brush.
/// </para>
/// </remarks>
public static class ThemeService
{
    private static MaterialScheme? _scheme;
    private static SchemeDefinition _definition = ThemeCatalogue.Get(null);

    public static event Action<SchemeDefinition>? ThemeChanged;

    public static string CurrentId => _definition.Id;

    public static bool IsDark => _definition.Dark;

    public static MaterialScheme Scheme =>
        _scheme ??= ThemeCatalogue.Generate(_definition.Id);

    /// <summary>Applies a scheme by catalogue id, rebuilding every themed resource.</summary>
    public static void Apply(string? id)
    {
        _definition = ThemeCatalogue.Get(id);
        _scheme = ThemeCatalogue.Generate(_definition.Id);

        ResourceDictionary resources = Application.Current.Resources;
        MaterialScheme scheme = _scheme;

        Publish(resources, "Primary", scheme.Primary);
        Publish(resources, "OnPrimary", scheme.OnPrimary);
        Publish(resources, "PrimaryContainer", scheme.PrimaryContainer);
        Publish(resources, "OnPrimaryContainer", scheme.OnPrimaryContainer);

        Publish(resources, "Secondary", scheme.Secondary);
        Publish(resources, "OnSecondary", scheme.OnSecondary);
        Publish(resources, "SecondaryContainer", scheme.SecondaryContainer);
        Publish(resources, "OnSecondaryContainer", scheme.OnSecondaryContainer);

        Publish(resources, "Tertiary", scheme.Tertiary);
        Publish(resources, "OnTertiary", scheme.OnTertiary);
        Publish(resources, "TertiaryContainer", scheme.TertiaryContainer);
        Publish(resources, "OnTertiaryContainer", scheme.OnTertiaryContainer);

        Publish(resources, "Error", scheme.Error);
        Publish(resources, "OnError", scheme.OnError);
        Publish(resources, "ErrorContainer", scheme.ErrorContainer);
        Publish(resources, "OnErrorContainer", scheme.OnErrorContainer);

        Publish(resources, "Surface", scheme.Surface);
        Publish(resources, "OnSurface", scheme.OnSurface);
        Publish(resources, "SurfaceVariant", scheme.SurfaceVariant);
        Publish(resources, "OnSurfaceVariant", scheme.OnSurfaceVariant);
        Publish(resources, "SurfaceContainerLowest", scheme.SurfaceContainerLowest);
        Publish(resources, "SurfaceContainerLow", scheme.SurfaceContainerLow);
        Publish(resources, "SurfaceContainer", scheme.SurfaceContainer);
        Publish(resources, "SurfaceContainerHigh", scheme.SurfaceContainerHigh);
        Publish(resources, "SurfaceContainerHighest", scheme.SurfaceContainerHighest);

        Publish(resources, "Outline", scheme.Outline);
        Publish(resources, "OutlineVariant", scheme.OutlineVariant);
        Publish(resources, "InverseSurface", scheme.InverseSurface);
        Publish(resources, "OnInverseSurface", scheme.OnInverseSurface);
        Publish(resources, "InversePrimary", scheme.InversePrimary);

        ApplyGlassTokens(resources, scheme);

        ThemeChanged?.Invoke(_definition);
    }

    /// <summary>
    /// Publishes the translucent surface brushes the glass shell paints with.
    /// </summary>
    /// <remarks>
    /// These are deliberately <em>not</em> the opaque M3 container roles: a launcher
    /// window sits on top of a desktop, and some transparency is what makes the ambient
    /// haze readable through the panels. The alpha values are tuned so a card still
    /// passes text contrast against the brightest part of the haze.
    /// </remarks>
    private static void ApplyGlassTokens(ResourceDictionary resources, MaterialScheme scheme)
    {
        bool dark = _definition.Dark;

        resources["GlassTintBrush"] = new LinearGradientBrush(
            Color.FromArgb(dark ? (byte)0x8A : (byte)0x9A, 0x0E, 0x0C, 0x11),
            Color.FromArgb(dark ? (byte)0x8A : (byte)0x9A, 0x07, 0x06, 0x0A),
            new Point(0, 0), new Point(0.35, 1));

        resources["GlassStrokeBrush"] = new LinearGradientBrush(
            WithAlpha(ColorOf(scheme.OnSurfaceVariant), 0x33),
            WithAlpha(ColorOf(scheme.OnSurfaceVariant), 0x33),
            new Point(0, 0), new Point(1, 1));

        resources["SurfaceContainerLowBrush"] =
            new SolidColorBrush(WithAlpha(ColorOf(scheme.SurfaceContainerLow), 0x9E));
        resources["SurfaceContainerBrush"] =
            new SolidColorBrush(WithAlpha(ColorOf(scheme.SurfaceContainer), 0xB4));
        resources["SurfaceContainerHighBrush"] =
            new SolidColorBrush(WithAlpha(ColorOf(scheme.SurfaceContainerHigh), 0xC8));
        resources["SurfaceContainerHighestBrush"] =
            new SolidColorBrush(WithAlpha(ColorOf(scheme.SurfaceContainerHighest), 0xD8));

        resources["DividerBrush"] =
            new SolidColorBrush(WithAlpha(ColorOf(scheme.OnSurfaceVariant), 0x1F));
        resources["StateHoverBrush"] =
            new SolidColorBrush(WithAlpha(ColorOf(scheme.OnSurface), 0x14));
        resources["StatePressedBrush"] =
            new SolidColorBrush(WithAlpha(ColorOf(scheme.OnSurface), 0x1F));
        resources["RippleBrush"] =
            new SolidColorBrush(WithAlpha(ColorOf(scheme.OnSurface), 0x29));
        resources["InputFillBrush"] =
            new SolidColorBrush(WithAlpha(ColorOf(scheme.OnSurface), 0x14));
        resources["InputHoverBrush"] =
            new SolidColorBrush(WithAlpha(ColorOf(scheme.OnSurface), 0x1F));

        resources["ScrollThumbBrush"] =
            new SolidColorBrush(WithAlpha(ColorOf(scheme.OnSurfaceVariant), 0x66));
        resources["ScrollThumbHoverBrush"] =
            new SolidColorBrush(WithAlpha(ColorOf(scheme.OnSurfaceVariant), 0x99));

        resources["DisabledContainerBrush"] =
            new SolidColorBrush(WithAlpha(ColorOf(scheme.OnSurface), 0x1F));
        resources["DisabledLabelBrush"] =
            new SolidColorBrush(WithAlpha(ColorOf(scheme.OnSurface), 0x61));
    }

    private static void Publish(ResourceDictionary resources, string role, int argb)
    {
        Color color = ColorOf(argb);
        resources[role + "Color"] = color;
        resources[role + "Brush"] = new SolidColorBrush(color);
    }

    private static Color ColorOf(int argb) => Color.FromArgb(
        (byte)((argb >> 24) & 0xFF),
        (byte)((argb >> 16) & 0xFF),
        (byte)((argb >> 8) & 0xFF),
        (byte)(argb & 0xFF));

    private static Color WithAlpha(Color color, byte alpha) =>
        Color.FromArgb(alpha, color.R, color.G, color.B);
}
