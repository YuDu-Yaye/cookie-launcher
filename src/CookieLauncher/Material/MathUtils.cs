namespace CookieLauncher.Material;

/// <summary>
/// Mathematical helpers used by the Material colour algorithms.
///
/// A direct port of material-color-utilities <c>utils/math_utils.ts</c>, keeping the
/// same function semantics so results match Google's official implementation exactly.
/// </summary>
public static class MathUtils
{
    public static double Signum(double num)
        => num < 0 ? -1 : num == 0 ? 0 : 1;

    public static double Lerp(double start, double stop, double amount)
        => (1.0 - amount) * start + amount * stop;

    public static double ClampDouble(double min, double max, double input)
        => input < min ? min : input > max ? max : input;

    public static int ClampInt(int min, int max, int input)
        => input < min ? min : input > max ? max : input;

    /// <summary>Normalises degrees into [0, 360).</summary>
    public static double SanitizeDegreesDouble(double degrees)
    {
        degrees %= 360.0;
        if (degrees < 0)
        {
            degrees += 360.0;
        }

        return degrees;
    }

    /// <summary>
    /// Direction of shortest travel between two angles.
    /// Returns 1 when increasing, -1 when decreasing.
    /// </summary>
    public static double RotationDirection(double from, double to)
    {
        double increasingDifference = SanitizeDegreesDouble(to - from);
        return increasingDifference <= 180.0 ? 1.0 : -1.0;
    }

    /// <summary>Distance between two angles on a circle, in degrees.</summary>
    public static double DifferenceDegrees(double a, double b)
        => 180.0 - Math.Abs(Math.Abs(a - b) - 180.0);

    /// <summary>Multiplies a 1x3 row vector by a 3x3 matrix.</summary>
    public static double[] MatrixMultiply(double[] row, double[][] matrix) =>
    [
        (row[0] * matrix[0][0]) + (row[1] * matrix[0][1]) + (row[2] * matrix[0][2]),
        (row[0] * matrix[1][0]) + (row[1] * matrix[1][1]) + (row[2] * matrix[1][2]),
        (row[0] * matrix[2][0]) + (row[1] * matrix[2][1]) + (row[2] * matrix[2][2]),
    ];
}
