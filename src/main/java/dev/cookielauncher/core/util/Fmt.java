package dev.cookielauncher.core.util;

import java.util.Locale;

/** Byte, percentage and duration formatting. */
public final class Fmt {

    private Fmt() {
    }

    public static String bytes(long value) {
        if (value < 0) {
            return "?";
        }
        if (value < 1024) {
            return value + " B";
        }
        String[] units = {"KB", "MB", "GB", "TB"};
        double scaled = value;
        int unit = -1;
        do {
            scaled /= 1024d;
            unit++;
        } while (scaled >= 1024d && unit < units.length - 1);
        return String.format(Locale.ROOT, scaled >= 100 ? "%.0f %s" : "%.1f %s", scaled, units[unit]);
    }

    public static String bytesPerSecond(long value) {
        return bytes(value) + "/s";
    }

    public static String percent(long completed, long total) {
        if (total <= 0) {
            return "0%";
        }
        return Math.min(100, Math.max(0, (int) (completed * 100L / total))) + "%";
    }

    public static String percent(double fraction) {
        return Math.round(Math.max(0, Math.min(1, fraction)) * 100) + "%";
    }

    public static String duration(long millis) {
        if (millis < 1000) {
            return millis + " ms";
        }
        long seconds = millis / 1000;
        if (seconds < 60) {
            return seconds + " s";
        }
        long minutes = seconds / 60;
        if (minutes < 60) {
            return minutes + "m " + (seconds % 60) + "s";
        }
        long hours = minutes / 60;
        if (hours < 24) {
            return hours + "h " + (minutes % 60) + "m";
        }
        return (hours / 24) + "d " + (hours % 24) + "h";
    }

    public static String eta(long remainingBytes, long bytesPerSecond) {
        if (bytesPerSecond <= 0 || remainingBytes <= 0) {
            return "";
        }
        return duration(remainingBytes * 1000L / bytesPerSecond);
    }

    public static String truncate(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, Math.max(0, max - 1)) + "\u2026";
    }

    /** Escapes text for safe use inside a Swing HTML label. */
    public static String html(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
