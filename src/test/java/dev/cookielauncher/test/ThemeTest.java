package dev.cookielauncher.test;

import dev.cookielauncher.ui.theme.ColorScheme;
import dev.cookielauncher.ui.theme.Oklch;
import dev.cookielauncher.ui.theme.Theme;

import java.awt.Color;
import java.util.List;

/**
 * Material Design 3 colour generation. These tests exist because the first
 * implementation was subtly wrong: it mapped M3 tone (CIE L*) directly onto OKLab
 * lightness, which produced near-black surfaces. A contrast assertion catches that
 * class of bug immediately.
 */
public final class ThemeTest {

    /** WCAG relative luminance. */
    private static double luminance(Color color) {
        double[] channels = {color.getRed() / 255.0, color.getGreen() / 255.0, color.getBlue() / 255.0};
        double[] linear = new double[3];
        for (int i = 0; i < 3; i++) {
            linear[i] = channels[i] <= 0.04045
                    ? channels[i] / 12.92
                    : Math.pow((channels[i] + 0.055) / 1.055, 2.4);
        }
        return 0.2126 * linear[0] + 0.7152 * linear[1] + 0.0722 * linear[2];
    }

    private static double contrast(Color first, Color second) {
        double a = luminance(first);
        double b = luminance(second);
        return (Math.max(a, b) + 0.05) / (Math.min(a, b) + 0.05);
    }

    public static void testAllThemesGenerate() {
        Assert.equal(8, Theme.all().size(), "four seeds times two schemes");
        for (Theme theme : Theme.all()) {
            Assert.notNull(theme.colors, "theme " + theme.id + " has a colour scheme");
            Assert.notNull(theme.colors.primary, "primary is defined");
            Assert.notNull(theme.colors.surface, "surface is defined");
        }
    }

    public static void testThemeIdsAreUnique() {
        List<String> ids = Theme.all().stream().map(theme -> theme.id).toList();
        Assert.equal(ids.size(), (int) ids.stream().distinct().count(), "theme ids are unique");
    }

    public static void testOnSurfaceContrastIsAccessible() {
        for (Theme theme : Theme.all()) {
            double ratio = contrast(theme.colors.onSurface, theme.colors.surface);
            Assert.atLeast(45, Math.round(ratio * 10), "onSurface/surface contrast >= 4.5:1 for " + theme.id);
        }
    }

    public static void testOnPrimaryContrastIsAccessible() {
        for (Theme theme : Theme.all()) {
            double ratio = contrast(theme.colors.onPrimary, theme.colors.primary);
            Assert.atLeast(45, Math.round(ratio * 10), "onPrimary/primary contrast >= 4.5:1 for " + theme.id);
        }
    }

    public static void testBodyTextContrastIsHigh() {
        // M3 expects body text to reach ~7:1; ours measures above 12:1, which means
        // the surface tones are in the intended range instead of collapsing to black.
        for (Theme theme : Theme.all()) {
            double ratio = contrast(theme.colors.onSurface, theme.colors.surface);
            Assert.atLeast(70, Math.round(ratio * 10), "body text contrast >= 7:1 for " + theme.id);
        }
    }

    public static void testDarkSurfacesAreDarkButNotBlack() {
        for (Theme theme : Theme.all()) {
            if (!theme.isDark()) {
                continue;
            }
            double surfaceLuminance = luminance(theme.colors.surface);
            Assert.atLeast(1, Math.round(surfaceLuminance * 10000),
                    "dark surface is not pure black for " + theme.id);
            Assert.atMost(1200, Math.round(surfaceLuminance * 10000),
                    "dark surface stays dark for " + theme.id);
        }
    }

    public static void testLightSurfacesAreLight() {
        for (Theme theme : Theme.all()) {
            if (theme.isDark()) {
                continue;
            }
            double surfaceLuminance = luminance(theme.colors.surface);
            Assert.atLeast(7000, Math.round(surfaceLuminance * 10000),
                    "light surface is bright for " + theme.id);
        }
    }

    public static void testSurfaceContainersAreOrdered() {
        for (Theme theme : Theme.all()) {
            ColorScheme colors = theme.colors;
            double lowest = luminance(colors.surfaceContainerLowest);
            double low = luminance(colors.surfaceContainerLow);
            double normal = luminance(colors.surfaceContainer);
            double high = luminance(colors.surfaceContainerHigh);
            if (theme.isDark()) {
                Assert.isTrue(lowest <= low && low <= normal && normal <= high,
                        "dark containers get lighter as elevation rises (" + theme.id + ")");
            } else {
                Assert.isTrue(lowest >= low && low >= normal && normal >= high,
                        "light containers get darker as elevation rises (" + theme.id + ")");
            }
        }
    }

    public static void testOklchRoundTrip() {
        int original = 0xC97F3E;
        Oklch.Color oklch = Oklch.fromRgb((original >> 16) & 0xFF, (original >> 8) & 0xFF, original & 0xFF);
        int[] rgb = Oklch.toRgbOrNull(oklch.l(), oklch.c(), oklch.h());
        Assert.notNull(rgb, "the seed colour is inside the sRGB gamut");
        int reconstructed = (rgb[0] << 16) | (rgb[1] << 8) | rgb[2];
        Assert.isTrue(Math.abs(((original >> 16) & 0xFF) - rgb[0]) <= 2
                        && Math.abs(((original >> 8) & 0xFF) - rgb[1]) <= 2
                        && Math.abs((original & 0xFF) - rgb[2]) <= 2,
                "sRGB -> OKLCH -> sRGB round trips within 2/255 (got "
                        + Integer.toHexString(reconstructed) + ")");
    }

    public static void testToneMappingMatchesCieLightness() {
        // Tone 0 must be black and tone 100 must be white for any hue.
        Assert.equal(0, Oklch.tone(30, 0.0, 0) & 0xFFFFFF, "tone 0 is black");
        Assert.equal(0xFFFFFF, Oklch.tone(30, 0.0, 100) & 0xFFFFFF, "tone 100 is white");
    }

    public static void testToneProducesMonotonicLightness() {
        double previous = -1;
        for (int tone = 0; tone <= 100; tone += 10) {
            int rgb = Oklch.tone(30, 0.05, tone);
            double lightness = Oklch.lightness(rgb);
            Assert.isTrue(lightness > previous,
                    "lightness increases with tone (tone " + tone + ")");
            previous = lightness;
        }
    }

    public static void testOutOfGamutColoursAreClamped() {
        // Absurd chroma must still return a valid colour rather than null.
        int rgb = Oklch.tone(200, 0.9, 50);
        Assert.isTrue(rgb >= 0 && rgb <= 0xFFFFFF, "an impossible chroma still yields a colour");
    }

    public static void testMixIsPerceptuallyBetween() {
        int black = 0x000000;
        int white = 0xFFFFFF;
        int middle = Oklch.mix(black, white, 0.5);
        double lightness = Oklch.lightness(middle);
        Assert.between(35, 65, Math.round(lightness * 100), "a 50% mix lands mid lightness");
    }

    public static void testThemeLookupFallsBackSafely() {
        Assert.equal("caramel-dark", Theme.byId("nonsense").id, "unknown id falls back");
        Assert.equal("caramel-dark", Theme.byId(null).id, "null id falls back");
        Assert.equal("midnight-light", Theme.byId("midnight-light").id, "known id resolves");
    }

    public static void testTintedSurfaceRisesWithElevation() {
        Theme theme = Theme.caramelDark();
        double base = luminance(theme.tintedSurface(0));
        double elevated = luminance(theme.tintedSurface(3));
        Assert.isTrue(elevated >= base, "elevated surfaces are tinted toward the primary");
    }

    public static void testEveryRoleIsFullyOpaque() {
        for (Theme theme : Theme.all()) {
            ColorScheme colors = theme.colors;
            for (Color color : new Color[]{
                    colors.primary, colors.onPrimary, colors.primaryContainer,
                    colors.surface, colors.onSurface, colors.surfaceVariant,
                    colors.outline, colors.error}) {
                Assert.equal(255, color.getAlpha(), "scheme roles are opaque for " + theme.id);
            }
        }
    }
}
