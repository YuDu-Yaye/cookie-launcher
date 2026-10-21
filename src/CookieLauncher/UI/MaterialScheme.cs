using CookieLauncher.Material;

namespace CookieLauncher.UI;

/// <summary>
/// The complete Material 3 colour role set, generated from a single seed colour.
///
/// <para>
/// The roles and tone assignments follow material-color-utilities
/// (<c>scheme/scheme_tonal_spot.ts</c> plus the tone table in
/// <c>dynamiccolor/material_dynamic_colors.ts</c>), so the output matches Google's own
/// Material Theme Builder rather than approximating it. That is the difference between
/// a palette that looks "about right" and one where every surface container, outline
/// and container role sits at the tone the design system expects.
/// </para>
///
/// <para>
/// SkyLite only needed eleven roles for its injector page. A launcher needs the full
/// set: lists, dialogs, snackbars, progress indicators and disabled states all read
/// distinct roles, and collapsing them is how a UI starts to look muddy.
/// </para>
/// </summary>
public sealed record MaterialScheme
{
    public required int Primary { get; init; }
    public required int OnPrimary { get; init; }
    public required int PrimaryContainer { get; init; }
    public required int OnPrimaryContainer { get; init; }

    public required int Secondary { get; init; }
    public required int OnSecondary { get; init; }
    public required int SecondaryContainer { get; init; }
    public required int OnSecondaryContainer { get; init; }

    public required int Tertiary { get; init; }
    public required int OnTertiary { get; init; }
    public required int TertiaryContainer { get; init; }
    public required int OnTertiaryContainer { get; init; }

    public required int Error { get; init; }
    public required int OnError { get; init; }
    public required int ErrorContainer { get; init; }
    public required int OnErrorContainer { get; init; }

    public required int Surface { get; init; }
    public required int OnSurface { get; init; }
    public required int SurfaceVariant { get; init; }
    public required int OnSurfaceVariant { get; init; }
    public required int SurfaceContainerLowest { get; init; }
    public required int SurfaceContainerLow { get; init; }
    public required int SurfaceContainer { get; init; }
    public required int SurfaceContainerHigh { get; init; }
    public required int SurfaceContainerHighest { get; init; }

    public required int Outline { get; init; }
    public required int OutlineVariant { get; init; }
    public required int InverseSurface { get; init; }
    public required int OnInverseSurface { get; init; }
    public required int InversePrimary { get; init; }

    /// <summary>Convenience: the seeded palette, for generating supplementary tones.</summary>
    public required TonalPalette PrimaryPalette { get; init; }

    public required TonalPalette NeutralPalette { get; init; }
}

/// <summary>Generates the M3 tonal-spot scheme for a seed colour.</summary>
public static class SchemeTonalSpot
{
    // Chromas from the reference tonal-spot implementation.
    private const double PrimaryChroma = 48.0;
    private const double SecondaryChroma = 20.0;
    private const double TertiaryChroma = 30.0;
    private const double NeutralChroma = 8.0;
    private const double NeutralVariantChroma = 10.0;
    private const double ErrorChroma = 84.0;

    /// <summary>Builds the light or dark scheme for a seed colour.</summary>
    public static MaterialScheme FromSeed(int seedArgb, bool dark)
    {
        Hct source = Hct.FromInt(seedArgb);
        double hue = source.Hue;

        // The tertiary hue is rotated 60 degrees, which is what makes an M3 scheme feel
        // three-dimensional instead of monochrome. The reference uses the same shift.
        double tertiaryHue = (hue + 60.0) % 360.0;

        TonalPalette primary = TonalPalette.FromHueAndChroma(hue, PrimaryChroma);
        TonalPalette secondary = TonalPalette.FromHueAndChroma(hue, SecondaryChroma);
        TonalPalette tertiary = TonalPalette.FromHueAndChroma(tertiaryHue, TertiaryChroma);
        TonalPalette neutral = TonalPalette.FromHueAndChroma(hue, NeutralChroma);
        TonalPalette neutralVariant = TonalPalette.FromHueAndChroma(hue, NeutralVariantChroma);
        TonalPalette error = TonalPalette.FromHueAndChroma(25.0, ErrorChroma);

        return dark
            ? new MaterialScheme
            {
                Primary = primary.Tone(80),
                OnPrimary = primary.Tone(20),
                PrimaryContainer = primary.Tone(30),
                OnPrimaryContainer = primary.Tone(90),

                Secondary = secondary.Tone(80),
                OnSecondary = secondary.Tone(20),
                SecondaryContainer = secondary.Tone(30),
                OnSecondaryContainer = secondary.Tone(90),

                Tertiary = tertiary.Tone(80),
                OnTertiary = tertiary.Tone(20),
                TertiaryContainer = tertiary.Tone(30),
                OnTertiaryContainer = tertiary.Tone(90),

                Error = error.Tone(80),
                OnError = error.Tone(20),
                ErrorContainer = error.Tone(30),
                OnErrorContainer = error.Tone(90),

                Surface = neutral.Tone(6),
                OnSurface = neutral.Tone(90),
                SurfaceVariant = neutralVariant.Tone(30),
                OnSurfaceVariant = neutralVariant.Tone(80),
                SurfaceContainerLowest = neutral.Tone(4),
                SurfaceContainerLow = neutral.Tone(10),
                SurfaceContainer = neutral.Tone(12),
                SurfaceContainerHigh = neutral.Tone(17),
                SurfaceContainerHighest = neutral.Tone(22),

                Outline = neutralVariant.Tone(60),
                OutlineVariant = neutralVariant.Tone(30),
                InverseSurface = neutral.Tone(90),
                OnInverseSurface = neutral.Tone(20),
                InversePrimary = primary.Tone(40),

                PrimaryPalette = primary,
                NeutralPalette = neutral,
            }
            : new MaterialScheme
            {
                Primary = primary.Tone(40),
                OnPrimary = primary.Tone(100),
                PrimaryContainer = primary.Tone(90),
                OnPrimaryContainer = primary.Tone(10),

                Secondary = secondary.Tone(40),
                OnSecondary = secondary.Tone(100),
                SecondaryContainer = secondary.Tone(90),
                OnSecondaryContainer = secondary.Tone(10),

                Tertiary = tertiary.Tone(40),
                OnTertiary = tertiary.Tone(100),
                TertiaryContainer = tertiary.Tone(90),
                OnTertiaryContainer = tertiary.Tone(10),

                Error = error.Tone(40),
                OnError = error.Tone(100),
                ErrorContainer = error.Tone(90),
                OnErrorContainer = error.Tone(10),

                Surface = neutral.Tone(98),
                OnSurface = neutral.Tone(10),
                SurfaceVariant = neutralVariant.Tone(90),
                OnSurfaceVariant = neutralVariant.Tone(30),
                SurfaceContainerLowest = neutral.Tone(100),
                SurfaceContainerLow = neutral.Tone(96),
                SurfaceContainer = neutral.Tone(94),
                SurfaceContainerHigh = neutral.Tone(92),
                SurfaceContainerHighest = neutral.Tone(90),

                Outline = neutralVariant.Tone(50),
                OutlineVariant = neutralVariant.Tone(80),
                InverseSurface = neutral.Tone(20),
                OnInverseSurface = neutral.Tone(95),
                InversePrimary = primary.Tone(80),

                PrimaryPalette = primary,
                NeutralPalette = neutral,
            };
    }
}
