using System.Windows;
using System.Windows.Media;
using CookieLauncher.Material;

namespace CookieLauncher.UI;

/// <summary>
/// Writes the generated Material 3 scheme into the application resource dictionary.
/// </summary>
/// <remarks>
/// <para>
/// Styles and templates reference colours through <c>DynamicResource</c>, so replacing
/// these brushes repaints the whole shell without reloading any style. Switching schemes
/// is therefore instant rather than a window rebuild.
/// </para>
/// <para>
/// Both a <c>Color</c> and a <c>Brush</c> are published per role: templates that animate a
/// colour need the raw value, while everything else binds the brush.
/// </para>
/// </remarks>
public static class ThemeService
{
    private static MaterialScheme? _scheme;
    private static SchemeDefinition _definition = ThemeCatalogue.Get(null);

    public static event Action<SchemeDefinition>? ThemeChanged;

    public static string CurrentId => _definition.Id;

    public static string CurrentName => _definition.Name;

    public static bool IsDark => _definition.Dark;

    public static MaterialScheme Scheme => _scheme ??= ThemeCatalogue.Generate(_definition.Id);

    /// <summary>
    /// Applies a scheme by catalogue id.
    /// </summary>
    /// <remarks>
    /// An unknown id resolves to the default catalogue entry rather than throwing, and the
    /// caller is told which id was actually applied so a stale value in <c>config.json</c>
    /// can be corrected instead of silently ignored every launch.
    /// </remarks>
    public static string Apply(string? id)
    {
        SchemeDefinition requested = ThemeCatalogue.Get(id);
        _definition = requested;
        _scheme = ThemeCatalogue.Generate(requested.Id);

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

        ThemeChanged?.Invoke(requested);
        return requested.Id;
    }

    /// <summary>
    /// Publishes the translucent surface brushes the glass shell paints with.
    /// </summary>
    /// <remarks>
    /// These are deliberately not the opaque M3 container roles: some transparency is what
    /// makes the ambient haze readable through the panels. Alpha values are tuned so card
    /// text still passes contrast against the brightest part of the haze.
    /// </remarks>
    private static void ApplyGlassTokens(ResourceDictionary resources, MaterialScheme scheme)
    {
        bool dark = _definition.Dark;

        // Near-black tint in the dark scheme, near-white in the light one.
        Color tintStart = dark
            ? Color.FromArgb(0x8E, 0x0A, 0x0B, 0x10)
            : Color.FromArgb(0xA2, 0xFA, 0xFA, 0xFD);
        Color tintEnd = dark
            ? Color.FromArgb(0x8E, 0x05, 0x06, 0x0A)
            : Color.FromArgb(0xA2, 0xF2, 0xF2, 0xF7);

        resources["GlassTintBrush"] = new LinearGradientBrush(
            tintStart, tintEnd, new Point(0, 0), new Point(0.35, 1));

        resources["GlassStrokeBrush"] = new LinearGradientBrush(
            WithAlpha(ColorOf(scheme.OnSurfaceVariant), 0x38),
            WithAlpha(ColorOf(scheme.OnSurfaceVariant), 0x18),
            new Point(0, 0), new Point(1, 1));

        resources["SurfaceContainerLowBrush"] =
            new SolidColorBrush(WithAlpha(ColorOf(scheme.SurfaceContainerLow), dark ? (byte)0xA8 : (byte)0xB8));
        resources["SurfaceContainerBrush"] =
            new SolidColorBrush(WithAlpha(ColorOf(scheme.SurfaceContainer), dark ? (byte)0xBE : (byte)0xCC));
        resources["SurfaceContainerHighBrush"] =
            new SolidColorBrush(WithAlpha(ColorOf(scheme.SurfaceContainerHigh), dark ? (byte)0xD0 : (byte)0xDC));
        resources["SurfaceContainerHighestBrush"] =
            new SolidColorBrush(WithAlpha(ColorOf(scheme.SurfaceContainerHighest), dark ? (byte)0xDE : (byte)0xE8));

        resources["DividerBrush"] =
            new SolidColorBrush(WithAlpha(ColorOf(scheme.OnSurfaceVariant), 0x22));
        resources["StateHoverBrush"] =
            new SolidColorBrush(WithAlpha(ColorOf(scheme.OnSurface), 0x14));
        resources["StatePressedBrush"] =
            new SolidColorBrush(WithAlpha(ColorOf(scheme.OnSurface), 0x1F));
        resources["RippleBrush"] =
            new SolidColorBrush(WithAlpha(ColorOf(scheme.OnSurface), 0x29));
        resources["InputFillBrush"] =
            new SolidColorBrush(WithAlpha(ColorOf(scheme.OnSurface), 0x12));
        resources["InputHoverBrush"] =
            new SolidColorBrush(WithAlpha(ColorOf(scheme.OnSurface), 0x1C));

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
