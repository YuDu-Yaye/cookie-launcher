package dev.cookielauncher.core;

/** Application identity and version, read from one place. */
public final class AppInfo {

    public static final String NAME = "Cookie Launcher";
    public static final String VERSION = "1.0.0";
    public static final String REPOSITORY = "https://github.com/YuDu-Yaye/cookie-launcher";
    public static final String LICENSE = "GPL-3.0-only";
    public static final String USER_AGENT =
            "CookieLauncher/" + VERSION + " (+" + REPOSITORY + ")";

    private AppInfo() {
    }

    /** Bumped whenever the on-disk layout changes in a breaking way. */
    public static final int DATA_FORMAT = 1;
}
