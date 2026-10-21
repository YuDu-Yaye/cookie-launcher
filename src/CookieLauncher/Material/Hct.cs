namespace CookieLauncher.Material;

/// <summary>
/// HCT: hue, chroma and tone. Combines CAM16's hue and chroma with L* tone, so that
/// a tone difference maps predictably onto contrast.
///
/// Port of material-color-utilities <c>hct/hct.ts</c>.
/// </summary>
public sealed class Hct
{
    public double Hue { get; }
    public double Chroma { get; }
    public double Tone { get; }

    /// <summary>The sRGB colour this HCT triple resolves to.</summary>
    public int Argb { get; }

    private Hct(int argb)
    {
        Argb = argb;
        Cam16 cam = Cam16.FromInt(argb);
        Hue = cam.Hue;
        Chroma = cam.Chroma;
        Tone = ColorUtils.LstarFromArgb(argb);
    }

    public static Hct FromInt(int argb) => new(argb);

    /// <summary>
    /// Builds the colour for an HCT triple. Chroma may be reduced when the requested
    /// value is outside the sRGB gamut at that hue and tone.
    /// </summary>
    public static Hct From(double hue, double chroma, double tone)
        => new(HctSolver.SolveToInt(hue, chroma, tone));

    public int ToInt() => Argb;
}
