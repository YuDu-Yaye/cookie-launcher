package dev.cookielauncher.ui.theme;

import java.awt.Color;

/**
 * One complete Material Design 3 theme: a colour scheme, a dark/light flag and a
 * display name. Seeds come from a small curated set so every generated palette is
 * harmonious; users pick a seed, not a hex code.
 */
public final class Theme {

    public final String id;
    public final String name;
    public final ColorScheme colors;
    public final boolean dark;

    private Theme(String id, String name, int seed, boolean dark, int tertiaryShift) {
        this.id = id;
        this.name = name;
        this.dark = dark;
        this.colors = new ColorScheme(seed, dark, tertiaryShift);
    }

    public static Theme caramelDark() {
        return new Theme("caramel-dark", "Caramel (dark)", 0xC97F3E, true, 150);
    }

    public static Theme caramelLight() {
        return new Theme("caramel-light", "Caramel (light)", 0xC97F3E, false, 150);
    }

    public static Theme midnightDark() {
        return new Theme("midnight-dark", "Midnight (dark)", 0x6C9FE8, true, -120);
    }

    public static Theme midnightLight() {
        return new Theme("midnight-light", "Midnight (light)", 0x6C9FE8, false, -120);
    }

    public static Theme sageDark() {
        return new Theme("sage-dark", "Sage (dark)", 0x7FB58A, true, 90);
    }

    public static Theme sageLight() {
        return new Theme("sage-light", "Sage (light)", 0x7FB58A, false, 90);
    }

    public static Theme roseDark() {
        return new Theme("rose-dark", "Rose (dark)", 0xE08398, true, -75);
    }

    public static Theme roseLight() {
        return new Theme("rose-light", "Rose (light)", 0xE08398, false, -75);
    }

    public static java.util.List<Theme> all() {
        return java.util.List.of(
                caramelDark(), caramelLight(),
                midnightDark(), midnightLight(),
                sageDark(), sageLight(),
                roseDark(), roseLight());
    }

    public static Theme byId(String id) {
        if (id != null) {
            for (Theme theme : all()) {
                if (theme.id.equalsIgnoreCase(id)) {
                    return theme;
                }
            }
        }
        return caramelDark();
    }

    public boolean isDark() {
        return dark;
    }

    @Override
    public String toString() {
        return name;
    }

    /** M3 surface tint: elevated containers blend toward the primary hue. */
    public Color tintedSurface(int level) {
        float overlay = TypeScale.elevationOverlay(level);
        if (overlay <= 0f) {
            return colors.surface;
        }
        return new Color(Oklch.mix(colors.surface.getRGB() & 0xFFFFFF,
                colors.primary.getRGB() & 0xFFFFFF, overlay));
    }
}
