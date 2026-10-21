package dev.cookielauncher.ui.theme;

import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.util.Arrays;
import java.util.List;

/**
 * Material Design 3 type scale plus shape, spacing and elevation tokens.
 *
 * <p>M3 defines five types: Display, Headline, Title, Body, Label — each with
 * Large/Medium/Small. Sizes below are the M3 baseline in logical pixels, mapped
 * to 96 DPI (Swing reports points, so the size we pass is points at 72 DPI; the
 * ratios still hold and stay readable on HiDPI because Swing scales fonts).</p>
 */
public final class TypeScale {

    private static final String FAMILY = pickFamily();
    private static final String MONO = pickMono();

    private TypeScale() {
    }

    /** Display: 57/45/36. */
    public static Font displayLarge() {
        return new Font(FAMILY, Font.PLAIN, 45);
    }

    public static Font displayMedium() {
        return new Font(FAMILY, Font.PLAIN, 36);
    }

    public static Font displaySmall() {
        return new Font(FAMILY, Font.PLAIN, 29);
    }

    /** Headline: 32/28/24. */
    public static Font headlineLarge() {
        return new Font(FAMILY, Font.PLAIN, 26);
    }

    public static Font headlineMedium() {
        return new Font(FAMILY, Font.PLAIN, 22);
    }

    public static Font headlineSmall() {
        return new Font(FAMILY, Font.PLAIN, 19);
    }

    /** Title: 22/16/14. */
    public static Font titleLarge() {
        return new Font(FAMILY, Font.PLAIN, 18);
    }

    public static Font titleMedium() {
        return new Font(FAMILY, Font.BOLD, 13);
    }

    public static Font titleSmall() {
        return new Font(FAMILY, Font.BOLD, 11);
    }

    /** Body: 16/14/12. */
    public static Font bodyLarge() {
        return new Font(FAMILY, Font.PLAIN, 13);
    }

    public static Font bodyMedium() {
        return new Font(FAMILY, Font.PLAIN, 12);
    }

    public static Font bodySmall() {
        return new Font(FAMILY, Font.PLAIN, 11);
    }

    /** Label: 14/12/11 with the medium weight M3 uses for buttons. */
    public static Font labelLarge() {
        return new Font(FAMILY, Font.BOLD, 12);
    }

    public static Font labelMedium() {
        return new Font(FAMILY, Font.BOLD, 11);
    }

    public static Font labelSmall() {
        return new Font(FAMILY, Font.BOLD, 10);
    }

    public static Font mono(int size) {
        return new Font(MONO, Font.PLAIN, size);
    }

    public static String family() {
        return FAMILY;
    }

    public static String monoFamily() {
        return MONO;
    }

    private static String pickFamily() {
        List<String> preferred = List.of(
                "Segoe UI Variable Text", "Segoe UI Variable", "Segoe UI",
                "Inter", "SF Pro Text", "SF Pro Display", "Helvetica Neue",
                "Noto Sans", "Roboto", "Ubuntu", "Cantarell", "DejaVu Sans");
        return firstAvailable(preferred, Font.SANS_SERIF);
    }

    private static String pickMono() {
        List<String> preferred = List.of(
                "Cascadia Mono", "Cascadia Code", "JetBrains Mono", "Consolas",
                "SF Mono", "Menlo", "DejaVu Sans Mono", "Liberation Mono");
        return firstAvailable(preferred, Font.MONOSPACED);
    }

    private static String firstAvailable(List<String> preferred, String fallback) {
        try {
            List<String> installed = Arrays.asList(
                    GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames());
            for (String candidate : preferred) {
                if (installed.contains(candidate)) {
                    return candidate;
                }
            }
        } catch (Throwable ignored) {
            // Headless or restricted environment: use the logical font.
        }
        return fallback;
    }

    // ------------------------------------------------------------------
    // M3 shape scale (corner radii, dp)
    // ------------------------------------------------------------------

    public static final int SHAPE_EXTRA_SMALL = 4;
    public static final int SHAPE_SMALL = 8;
    public static final int SHAPE_MEDIUM = 12;
    public static final int SHAPE_LARGE = 16;
    public static final int SHAPE_EXTRA_LARGE = 28;
    public static final int SHAPE_FULL = 999;

    // ------------------------------------------------------------------
    // 4dp grid spacing
    // ------------------------------------------------------------------

    public static final int SPACE_1 = 4;
    public static final int SPACE_2 = 8;
    public static final int SPACE_3 = 12;
    public static final int SPACE_4 = 16;
    public static final int SPACE_5 = 20;
    public static final int SPACE_6 = 24;
    public static final int SPACE_8 = 32;
    public static final int SPACE_10 = 40;

    /** M3 level 0..5 elevation, expressed as a surface tint overlay opacity. */
    public static float elevationOverlay(int level) {
        return switch (level) {
            case 0 -> 0f;
            case 1 -> 0.05f;
            case 2 -> 0.08f;
            case 3 -> 0.11f;
            case 4 -> 0.12f;
            default -> 0.14f;
        };
    }

    /** Touch target minimums from the M3 accessibility guidance. */
    public static final int MIN_TOUCH_TARGET = 48;
    public static final int BUTTON_HEIGHT = 40;
    public static final int SMALL_BUTTON_HEIGHT = 32;
}
