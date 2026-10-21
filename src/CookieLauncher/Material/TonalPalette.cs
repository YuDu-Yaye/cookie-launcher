using System.Collections.Concurrent;

namespace CookieLauncher.Material;

/// <summary>
/// A palette of colours sharing one hue and chroma, indexed by tone.
///
/// Port of material-color-utilities <c>palettes/tonal_palette.ts</c>. Results are
/// cached because the solver runs a numeric search per tone, and a scheme asks for the
/// same handful of tones repeatedly.
/// </summary>
public sealed class TonalPalette
{
    private readonly ConcurrentDictionary<double, int> _cache = new();

    public double Hue { get; }
    public double Chroma { get; }

    private TonalPalette(double hue, double chroma)
    {
        Hue = hue;
        Chroma = chroma;
    }

    public static TonalPalette FromHueAndChroma(double hue, double chroma)
        => new(hue, chroma);

    /// <summary>The sRGB colour at the given tone (0 = black, 100 = white).</summary>
    public int Tone(double tone)
        => _cache.GetOrAdd(tone, t => Hct.From(Hue, Chroma, t).ToInt());
}
