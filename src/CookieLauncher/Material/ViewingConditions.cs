namespace CookieLauncher.Material;

/// <summary>
/// CAM16 viewing conditions: the environment a colour is observed in.
///
/// Port of material-color-utilities <c>hct/viewing_conditions.ts</c>. Only the default
/// conditions are needed here, but the full construction is kept so the numbers match
/// the reference implementation exactly.
/// </summary>
public sealed class ViewingConditions
{
    public double N { get; }
    public double Aw { get; }
    public double Nbb { get; }
    public double Ncb { get; }
    public double C { get; }
    public double Nc { get; }
    public double[] RgbD { get; }
    public double Fl { get; }
    public double FlRoot { get; }
    public double Z { get; }

    private ViewingConditions(
        double n, double aw, double nbb, double ncb, double c, double nc,
        double[] rgbD, double fl, double flRoot, double z)
    {
        N = n;
        Aw = aw;
        Nbb = nbb;
        Ncb = ncb;
        C = c;
        Nc = nc;
        RgbD = rgbD;
        Fl = fl;
        FlRoot = flRoot;
        Z = z;
    }

    /// <summary>The sRGB-like default conditions used by HCT.</summary>
    public static ViewingConditions Default { get; } = Make();

    public static ViewingConditions Make(
        double[]? whitePoint = null,
        double? adaptingLuminance = null,
        double backgroundLstar = 50.0,
        double surround = 2.0,
        bool discountingIlluminant = false)
    {
        double[] xyz = whitePoint ?? ColorUtils.WhitePointD65;
        double adapting = adaptingLuminance ?? (200.0 / Math.PI) * ColorUtils.YFromLstar(50.0) / 100.0;

        double rW = (xyz[0] * 0.401288) + (xyz[1] * 0.650173) + (xyz[2] * -0.051461);
        double gW = (xyz[0] * -0.250268) + (xyz[1] * 1.204414) + (xyz[2] * 0.045854);
        double bW = (xyz[0] * -0.002079) + (xyz[1] * 0.048952) + (xyz[2] * 0.953127);

        double f = 0.8 + (surround / 10.0);
        double c = f >= 0.9
            ? MathUtils.Lerp(0.59, 0.69, (f - 0.9) * 10.0)
            : MathUtils.Lerp(0.525, 0.59, (f - 0.8) * 10.0);

        double d = discountingIlluminant
            ? 1.0
            : f * (1.0 - ((1.0 / 3.6) * Math.Exp((-adapting - 42.0) / 92.0)));
        d = d > 1.0 ? 1.0 : d < 0.0 ? 0.0 : d;

        double nc = f;
        double[] rgbD =
        [
            (d * (100.0 / rW)) + 1.0 - d,
            (d * (100.0 / gW)) + 1.0 - d,
            (d * (100.0 / bW)) + 1.0 - d,
        ];

        double k = 1.0 / ((5.0 * adapting) + 1.0);
        double k4 = k * k * k * k;
        double k4F = 1.0 - k4;
        double fl = (k4 * adapting) + (0.1 * k4F * k4F * Math.Cbrt(5.0 * adapting));

        double n = ColorUtils.YFromLstar(backgroundLstar) / xyz[1];
        double z = 1.48 + Math.Sqrt(n);
        double nbb = 0.725 / Math.Pow(n, 0.2);
        double ncb = nbb;

        double[] rgbAFactors =
        [
            Math.Pow(fl * rgbD[0] * rW / 100.0, 0.42),
            Math.Pow(fl * rgbD[1] * gW / 100.0, 0.42),
            Math.Pow(fl * rgbD[2] * bW / 100.0, 0.42),
        ];

        double[] rgbA =
        [
            400.0 * rgbAFactors[0] / (rgbAFactors[0] + 27.13),
            400.0 * rgbAFactors[1] / (rgbAFactors[1] + 27.13),
            400.0 * rgbAFactors[2] / (rgbAFactors[2] + 27.13),
        ];

        double aw = ((2.0 * rgbA[0]) + rgbA[1] + (0.05 * rgbA[2])) * nbb;

        return new ViewingConditions(n, aw, nbb, ncb, c, nc, rgbD, fl, Math.Pow(fl, 0.25), z);
    }
}
