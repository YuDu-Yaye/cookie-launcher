namespace CookieLauncher.Material;

/// <summary>
/// Colour science helpers for the sRGB / XYZ / Lab conversions used by HCT.
///
/// A direct port of material-color-utilities <c>utils/color_utils.ts</c>. Constants and
/// thresholds are kept verbatim so results match the reference implementation.
/// </summary>
public static class ColorUtils
{
    private static readonly double[][] SrgbToXyz =
    [
        [0.41233895, 0.35762064, 0.18051042],
        [0.2126, 0.7152, 0.0722],
        [0.01932141, 0.11916382, 0.95034478],
    ];

    private static readonly double[][] XyzToSrgb =
    [
        [3.2413774792388685, -1.5376652402851851, -0.49885366846268053],
        [-0.9691452513005321, 1.8758853451067872, 0.04156585616912061],
        [0.05562093689691305, -0.20395524564742123, 1.0571799111220335],
    ];

    /// <summary>D65 white point, the standard "sunny day" reference.</summary>
    public static readonly double[] WhitePointD65 = [95.047, 100.0, 108.883];

    public static int AlphaFromArgb(int argb) => (argb >> 24) & 255;

    public static int RedFromArgb(int argb) => (argb >> 16) & 255;

    public static int GreenFromArgb(int argb) => (argb >> 8) & 255;

    public static int BlueFromArgb(int argb) => argb & 255;

    public static bool IsOpaque(int argb) => AlphaFromArgb(argb) >= 255;

    public static int ArgbFromRgb(int red, int green, int blue)
        => (255 << 24) | ((red & 255) << 16) | ((green & 255) << 8) | (blue & 255);

    public static int ArgbFromLinrgb(double[] linrgb)
    {
        int r = Delinearized(linrgb[0]);
        int g = Delinearized(linrgb[1]);
        int b = Delinearized(linrgb[2]);
        return ArgbFromRgb(r, g, b);
    }

    public static int ArgbFromXyz(double x, double y, double z)
    {
        double linearR = (XyzToSrgb[0][0] * x) + (XyzToSrgb[0][1] * y) + (XyzToSrgb[0][2] * z);
        double linearG = (XyzToSrgb[1][0] * x) + (XyzToSrgb[1][1] * y) + (XyzToSrgb[1][2] * z);
        double linearB = (XyzToSrgb[2][0] * x) + (XyzToSrgb[2][1] * y) + (XyzToSrgb[2][2] * z);
        return ArgbFromRgb(Delinearized(linearR), Delinearized(linearG), Delinearized(linearB));
    }

    public static double[] XyzFromArgb(int argb)
    {
        double r = Linearized(RedFromArgb(argb));
        double g = Linearized(GreenFromArgb(argb));
        double b = Linearized(BlueFromArgb(argb));
        return MathUtils.MatrixMultiply([r, g, b], SrgbToXyz);
    }

    /// <summary>Converts a grayscale L* value (0-100) to an ARGB grey.</summary>
    public static int ArgbFromLstar(double lstar)
    {
        double y = YFromLstar(lstar);
        int component = Delinearized(y);
        return ArgbFromRgb(component, component, component);
    }

    public static double LstarFromArgb(int argb)
    {
        double y = XyzFromArgb(argb)[1];
        return (116.0 * LabF(y / 100.0)) - 16.0;
    }

    public static double YFromLstar(double lstar)
        => 100.0 * LabInvf((lstar + 16.0) / 116.0);

    public static double LstarFromY(double y) => (LabF(y / 100.0) * 116.0) - 16.0;

    /// <summary>Linearizes an sRGB channel (0-255) into linear RGB (0-100).</summary>
    public static double Linearized(int rgbComponent)
    {
        double normalized = rgbComponent / 255.0;
        return normalized <= 0.040449936
            ? normalized / 12.92 * 100.0
            : Math.Pow((normalized + 0.055) / 1.055, 2.4) * 100.0;
    }

    /// <summary>Delinearizes linear RGB (0-100) back into an sRGB channel (0-255).</summary>
    public static int Delinearized(double rgbComponent)
    {
        double normalized = rgbComponent / 100.0;
        double delinearized = normalized <= 0.0031308
            ? normalized * 12.92
            : (1.055 * Math.Pow(normalized, 1.0 / 2.4)) - 0.055;

        return MathUtils.ClampInt(0, 255, (int)Math.Round(delinearized * 255.0));
    }

    /// <summary>Formats an ARGB integer as an uppercase #RRGGBB string.</summary>
    public static string HexFromArgb(int argb)
        => $"#{RedFromArgb(argb):X2}{GreenFromArgb(argb):X2}{BlueFromArgb(argb):X2}";

    private static double LabF(double t)
    {
        const double e = 216.0 / 24389.0;
        const double kappa = 24389.0 / 27.0;
        return t > e ? Math.Pow(t, 1.0 / 3.0) : ((kappa * t) + 16) / 116;
    }

    private static double LabInvf(double ft)
    {
        const double e = 216.0 / 24389.0;
        const double kappa = 24389.0 / 27.0;
        double ft3 = ft * ft * ft;
        return ft3 > e ? ft3 : ((116 * ft) - 16) / kappa;
    }
}
