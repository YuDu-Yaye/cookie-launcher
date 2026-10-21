package dev.cookielauncher.ui.theme;

/**
 * OKLab / OKLCH color math.
 *
 * <p>Material Design 3 generates its tonal palettes in HCT (hue, chroma, tone).
 * HCT's tone axis is CIE L*, and its chroma is bounded by the sRGB gamut. OKLab
 * gives a perceptually uniform lightness with far simpler math, so we build the
 * palettes here: convert sRGB to OKLCH, then for each tonal step fix lightness
 * and reduce chroma until the colour fits in sRGB. The result behaves like an
 * M3 tonal palette (tone 0 is black, tone 100 is white, 40/80 are the light/dark
 * primary stops) without shipping a colour library.</p>
 */
public final class Oklch {

    private Oklch() {
    }

    /** A colour in OKLCH: lightness 0..1, chroma 0..0.4, hue degrees 0..360. */
    public record Color(double l, double c, double h) {
    }

    public static double[] srgbToLinear(int channel) {
        double value = channel / 255.0;
        return new double[]{value <= 0.04045 ? value / 12.92 : Math.pow((value + 0.055) / 1.055, 2.4)};
    }

    public static int linearToSrgb(double value) {
        double clamped = Math.max(0.0, Math.min(1.0, value));
        double encoded = clamped <= 0.0031308
                ? clamped * 12.92
                : 1.055 * Math.pow(clamped, 1.0 / 2.4) - 0.055;
        return (int) Math.round(encoded * 255.0);
    }

    /** Converts an sRGB colour to OKLCH. */
    public static Color fromRgb(int r, int g, int b) {
        double lr = srgbToLinear(r)[0];
        double lg = srgbToLinear(g)[0];
        double lb = srgbToLinear(b)[0];

        double l = 0.4122214708 * lr + 0.5363325363 * lg + 0.0514459929 * lb;
        double m = 0.2119034982 * lr + 0.6806995451 * lg + 0.1073969566 * lb;
        double s = 0.0883024619 * lr + 0.2817188376 * lg + 0.6299787005 * lb;

        double l_ = Math.cbrt(l);
        double m_ = Math.cbrt(m);
        double s_ = Math.cbrt(s);

        double bigL = 0.2104542553 * l_ + 0.7936177850 * m_ - 0.0040720468 * s_;
        double a = 1.9779984951 * l_ - 2.4285922050 * m_ + 0.4505937099 * s_;
        double bb = 0.0259040371 * l_ + 0.7827717662 * m_ - 0.8086757660 * s_;

        double chroma = Math.sqrt(a * a + bb * bb);
        double hue = Math.toDegrees(Math.atan2(bb, a));
        if (hue < 0) {
            hue += 360.0;
        }
        return new Color(bigL, chroma, hue);
    }

    /** Converts OKLCH to sRGB, returning null when the colour is out of gamut. */
    public static int[] toRgbOrNull(double lightness, double chroma, double hueDegrees) {
        double radians = Math.toRadians(hueDegrees);
        double a = chroma * Math.cos(radians);
        double b = chroma * Math.sin(radians);

        double l_ = lightness + 0.3963377774 * a + 0.2158037573 * b;
        double m_ = lightness - 0.1055613458 * a - 0.0638541728 * b;
        double s_ = lightness - 0.0894841775 * a - 1.2914855480 * b;

        double l = l_ * l_ * l_;
        double m = m_ * m_ * m_;
        double s = s_ * s_ * s_;

        double r = 4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s;
        double g = -1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s;
        double bl = -0.0041960863 * l - 0.7034186147 * m + 1.7076147010 * s;

        double limit = 0.0005;
        if (r < -limit || r > 1 + limit || g < -limit || g > 1 + limit || bl < -limit || bl > 1 + limit) {
            return null;
        }
        return new int[]{linearToSrgb(r), linearToSrgb(g), linearToSrgb(bl)};
    }

    /**
     * Builds an M3-like tonal palette: {@code tones[i]} corresponds to tone
     * {@code i * 10} (0, 10, 20 ... 100).
     */
    public static int[] tonalPalette(int seedRgb) {
        Color seed = fromRgb((seedRgb >> 16) & 0xFF, (seedRgb >> 8) & 0xFF, seedRgb & 0xFF);
        int[] palette = new int[11];
        for (int step = 0; step <= 10; step++) {
            palette[step] = tone(seed.h(), Math.min(seed.c(), 0.16), step * 10);
        }
        return palette;
    }

    /**
     * Converts a Material tone (CIE L*, 0..100) to the OKLab lightness that
     * produces the same relative luminance for a neutral colour.
     *
     * <p>The two scales are not the same thing: M3's "tone 6" means CIE L* 6
     * (relative luminance 0.00068), which is OKLab L 0.088 — not 0.06. Mapping
     * them directly makes every surface dramatically too dark.</p>
     */
    public static double toneToOklabLightness(int tone) {
        double cieLightness = Math.max(0.0, Math.min(100.0, tone));
        double relativeLuminance = cieLightness > 8.0
                ? Math.pow((cieLightness + 16.0) / 116.0, 3.0)
                : cieLightness / 903.3;
        return Math.cbrt(Math.max(0.0, Math.min(1.0, relativeLuminance)));
    }

    /** Returns the sRGB colour at a given tone for a fixed hue and target chroma. */
    public static int tone(double hue, double chroma, int toneStep) {
        double lightness = toneToOklabLightness(toneStep);
        double c = chroma;
        for (int attempt = 0; attempt < 12; attempt++) {
            int[] rgb = toRgbOrNull(lightness, c, hue);
            if (rgb != null) {
                return (rgb[0] << 16) | (rgb[1] << 8) | rgb[2];
            }
            c *= 0.88;
            if (c < 0.0015) {
                break;
            }
        }
        int[] gray = toRgbOrNull(lightness, 0, hue);
        if (gray == null) {
            gray = new int[]{linearToSrgb(lightness), linearToSrgb(lightness), linearToSrgb(lightness)};
        }
        return (gray[0] << 16) | (gray[1] << 8) | gray[2];
    }

    /** Mixes two sRGB colours in OKLab so the midpoint stays perceptually even. */
    public static int mix(int first, int second, double ratio) {
        Color a = fromRgb((first >> 16) & 0xFF, (first >> 8) & 0xFF, first & 0xFF);
        Color b = fromRgb((second >> 16) & 0xFF, (second >> 8) & 0xFF, second & 0xFF);
        double t = Math.max(0.0, Math.min(1.0, ratio));
        double l = a.l() + (b.l() - a.l()) * t;

        double hueA = Math.toRadians(a.h());
        double hueB = Math.toRadians(b.h());
        double x = Math.cos(hueA) * a.c() + (Math.cos(hueB) * b.c() - Math.cos(hueA) * a.c()) * t;
        double y = Math.sin(hueA) * a.c() + (Math.sin(hueB) * b.c() - Math.sin(hueA) * a.c()) * t;
        double c = Math.sqrt(x * x + y * y);
        double hue = Math.toDegrees(Math.atan2(y, x));
        if (hue < 0) {
            hue += 360.0;
        }
        int[] rgb = toRgbOrNull(l, c, hue);
        if (rgb == null) {
            int[] fallback = toRgbOrNull(l, 0, hue);
            rgb = fallback == null ? new int[]{0, 0, 0} : fallback;
        }
        return (rgb[0] << 16) | (rgb[1] << 8) | rgb[2];
    }

    /** Perceptual lightness of an sRGB colour, 0..1. */
    public static double lightness(int rgb) {
        return fromRgb((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF).l();
    }
}
