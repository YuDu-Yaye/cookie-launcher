namespace CookieLauncher.Material;

/// <summary>
/// CAM16 colour appearance model, ported from material-color-utilities
/// <c>hct/cam16.ts</c>.
///
/// Only the conversions HCT needs are implemented: from ARGB, from J/C/H, and back to
/// ARGB. The formulas are kept in the same order as the reference so floating-point
/// results line up.
/// </summary>
public sealed class Cam16
{
    public double Hue { get; }
    public double Chroma { get; }
    public double J { get; }
    public double Q { get; }
    public double M { get; }
    public double S { get; }
    public double Jstar { get; }
    public double Astar { get; }
    public double Bstar { get; }

    private Cam16(
        double hue, double chroma, double j, double q, double m, double s,
        double jstar, double astar, double bstar)
    {
        Hue = hue;
        Chroma = chroma;
        J = j;
        Q = q;
        M = m;
        S = s;
        Jstar = jstar;
        Astar = astar;
        Bstar = bstar;
    }

    public static Cam16 FromInt(int argb)
        => FromIntInViewingConditions(argb, ViewingConditions.Default);

    public static Cam16 FromIntInViewingConditions(int argb, ViewingConditions vc)
    {
        int red = (argb & 0x00FF0000) >> 16;
        int green = (argb & 0x0000FF00) >> 8;
        int blue = argb & 0x000000FF;

        double redL = ColorUtils.Linearized(red);
        double greenL = ColorUtils.Linearized(green);
        double blueL = ColorUtils.Linearized(blue);

        double x = (0.41233895 * redL) + (0.35762064 * greenL) + (0.18051042 * blueL);
        double y = (0.2126 * redL) + (0.7152 * greenL) + (0.0722 * blueL);
        double z = (0.01932141 * redL) + (0.11916382 * greenL) + (0.95034478 * blueL);

        double rC = (0.401288 * x) + (0.650173 * y) - (0.051461 * z);
        double gC = (-0.250268 * x) + (1.204414 * y) + (0.045854 * z);
        double bC = (-0.002079 * x) + (0.048952 * y) + (0.953127 * z);

        double rD = vc.RgbD[0] * rC;
        double gD = vc.RgbD[1] * gC;
        double bD = vc.RgbD[2] * bC;

        double rAF = Math.Pow(vc.Fl * Math.Abs(rD) / 100.0, 0.42);
        double gAF = Math.Pow(vc.Fl * Math.Abs(gD) / 100.0, 0.42);
        double bAF = Math.Pow(vc.Fl * Math.Abs(bD) / 100.0, 0.42);

        double rA = MathUtils.Signum(rD) * 400.0 * rAF / (rAF + 27.13);
        double gA = MathUtils.Signum(gD) * 400.0 * gAF / (gAF + 27.13);
        double bA = MathUtils.Signum(bD) * 400.0 * bAF / (bAF + 27.13);

        double a = ((11.0 * rA) + (-12.0 * gA) + bA) / 11.0;
        double b = (rA + gA - (2.0 * bA)) / 9.0;
        double u = ((20.0 * rA) + (20.0 * gA) + (21.0 * bA)) / 20.0;
        double p2 = ((40.0 * rA) + (20.0 * gA) + bA) / 20.0;

        double atan2 = Math.Atan2(b, a);
        double atanDegrees = atan2 * 180.0 / Math.PI;
        double hue = atanDegrees < 0 ? atanDegrees + 360.0
            : atanDegrees >= 360 ? atanDegrees - 360.0
            : atanDegrees;
        double hueRadians = hue * Math.PI / 180.0;

        double ac = p2 * vc.Nbb;
        double j = 100.0 * Math.Pow(ac / vc.Aw, vc.C * vc.Z);
        double q = (4.0 / vc.C) * Math.Sqrt(j / 100.0) * (vc.Aw + 4.0) * vc.FlRoot;

        double huePrime = hue < 20.14 ? hue + 360 : hue;
        double eHue = 0.25 * (Math.Cos((huePrime * Math.PI / 180.0) + 2.0) + 3.8);
        double p1 = (50000.0 / 13.0) * eHue * vc.Nc * vc.Ncb;
        double t = p1 * Math.Sqrt((a * a) + (b * b)) / (u + 0.305);
        double alpha = Math.Pow(t, 0.9) * Math.Pow(1.64 - Math.Pow(0.29, vc.N), 0.73);

        double c = alpha * Math.Sqrt(j / 100.0);
        double m = c * vc.FlRoot;
        double s = 50.0 * Math.Sqrt(alpha * vc.C / (vc.Aw + 4.0));

        double jstar = (1.0 + (100.0 * 0.007)) * j / (1.0 + (0.007 * j));
        double mstar = (1.0 / 0.0228) * Math.Log(1.0 + (0.0228 * m));
        double astar = mstar * Math.Cos(hueRadians);
        double bstar = mstar * Math.Sin(hueRadians);

        return new Cam16(hue, c, j, q, m, s, jstar, astar, bstar);
    }

    public static Cam16 FromJch(double j, double c, double h)
        => FromJchInViewingConditions(j, c, h, ViewingConditions.Default);

    public static Cam16 FromJchInViewingConditions(double j, double c, double h, ViewingConditions vc)
    {
        double q = (4.0 / vc.C) * Math.Sqrt(j / 100.0) * (vc.Aw + 4.0) * vc.FlRoot;
        double m = c * vc.FlRoot;
        double alpha = c / Math.Sqrt(j / 100.0);
        double s = 50.0 * Math.Sqrt(alpha * vc.C / (vc.Aw + 4.0));
        double hueRadians = h * Math.PI / 180.0;
        double jstar = (1.0 + (100.0 * 0.007)) * j / (1.0 + (0.007 * j));
        double mstar = (1.0 / 0.0228) * Math.Log(1.0 + (0.0228 * m));
        double astar = mstar * Math.Cos(hueRadians);
        double bstar = mstar * Math.Sin(hueRadians);

        return new Cam16(h, c, j, q, m, s, jstar, astar, bstar);
    }

    /// <summary>Converts this CAM16 colour back to ARGB under the given conditions.</summary>
    public int Viewed(ViewingConditions vc)
    {
        double alpha = Chroma == 0.0 || J == 0.0
            ? 0.0
            : Chroma / Math.Sqrt(J / 100.0);

        double t = Math.Pow(
            alpha / Math.Pow(1.64 - Math.Pow(0.29, vc.N), 0.73), 1.0 / 0.9);

        double hRad = Hue * Math.PI / 180.0;
        double eHue = 0.25 * (Math.Cos(hRad + 2.0) + 3.8);
        double ac = vc.Aw * Math.Pow(J / 100.0, 1.0 / vc.C / vc.Z);
        double p1 = eHue * (50000.0 / 13.0) * vc.Nc * vc.Ncb;
        double p2 = ac / vc.Nbb;

        double hSin = Math.Sin(hRad);
        double hCos = Math.Cos(hRad);
        double gamma = 23.0 * (p2 + 0.305) * t
            / ((23.0 * p1) + (11.0 * t * hCos) + (108.0 * t * hSin));

        double a = gamma * hCos;
        double b = gamma * hSin;

        double rA = ((460.0 * p2) + (451.0 * a) + (288.0 * b)) / 1403.0;
        double gA = ((460.0 * p2) - (891.0 * a) - (261.0 * b)) / 1403.0;
        double bA = ((460.0 * p2) - (220.0 * a) - (6300.0 * b)) / 1403.0;

        double rCBase = Math.Max(0, 27.13 * Math.Abs(rA) / (400.0 - Math.Abs(rA)));
        double rC = MathUtils.Signum(rA) * (100.0 / vc.Fl) * Math.Pow(rCBase, 1.0 / 0.42);
        double gCBase = Math.Max(0, 27.13 * Math.Abs(gA) / (400.0 - Math.Abs(gA)));
        double gC = MathUtils.Signum(gA) * (100.0 / vc.Fl) * Math.Pow(gCBase, 1.0 / 0.42);
        double bCBase = Math.Max(0, 27.13 * Math.Abs(bA) / (400.0 - Math.Abs(bA)));
        double bC = MathUtils.Signum(bA) * (100.0 / vc.Fl) * Math.Pow(bCBase, 1.0 / 0.42);

        double rF = rC / vc.RgbD[0];
        double gF = gC / vc.RgbD[1];
        double bF = bC / vc.RgbD[2];

        double x = (1.86206786 * rF) - (1.01125463 * gF) + (0.14918677 * bF);
        double y = (0.38752654 * rF) + (0.62144744 * gF) - (0.00897398 * bF);
        double z = (-0.01584150 * rF) - (0.03412294 * gF) + (1.04996444 * bF);

        return ColorUtils.ArgbFromXyz(x, y, z);
    }

    public int ToInt() => Viewed(ViewingConditions.Default);
}
