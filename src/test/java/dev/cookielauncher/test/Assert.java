package dev.cookielauncher.test;

import java.util.Objects;

/** Minimal assertion helpers, so tests read as intent rather than boolean noise. */
public final class Assert {

    private Assert() {
    }

    public static void isTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    public static void isFalse(boolean condition, String message) {
        if (condition) {
            throw new AssertionError(message);
        }
    }

    public static void equal(Object expected, Object actual, String message) {
        if (!Objects.equals(expected, actual)) {
            throw new AssertionError(message + " (expected <" + expected + ">, was <" + actual + ">)");
        }
    }

    public static void notEqual(Object unexpected, Object actual, String message) {
        if (Objects.equals(unexpected, actual)) {
            throw new AssertionError(message + " (both <" + actual + ">)");
        }
    }

    public static void notNull(Object value, String message) {
        if (value == null) {
            throw new AssertionError(message + " (was null)");
        }
    }

    public static void contains(String haystack, String needle, String message) {
        if (haystack == null || !haystack.contains(needle)) {
            throw new AssertionError(message + " (expected <" + needle + "> in <"
                    + abbreviate(haystack) + ">)");
        }
    }

    public static void notContains(String haystack, String needle, String message) {
        if (haystack != null && haystack.contains(needle)) {
            throw new AssertionError(message + " (did not expect <" + needle + ">)");
        }
    }

    public static void atLeast(long minimum, long actual, String message) {
        if (actual < minimum) {
            throw new AssertionError(message + " (expected >= " + minimum + ", was " + actual + ")");
        }
    }

    public static void atMost(long maximum, long actual, String message) {
        if (actual > maximum) {
            throw new AssertionError(message + " (expected <= " + maximum + ", was " + actual + ")");
        }
    }

    public static void between(long min, long max, long actual, String message) {
        atLeast(min, actual, message);
        atMost(max, actual, message);
    }

    public static void fails(Class<? extends Throwable> type, Runnable action, String message) {
        try {
            action.run();
        } catch (Throwable error) {
            if (type.isInstance(error)) {
                return;
            }
            throw new AssertionError(message + " (expected " + type.getSimpleName()
                    + ", got " + error.getClass().getSimpleName() + ": " + error.getMessage() + ")");
        }
        throw new AssertionError(message + " (expected " + type.getSimpleName()
                + ", nothing was thrown)");
    }

    private static String abbreviate(String value) {
        if (value == null) {
            return "null";
        }
        return value.length() <= 140 ? value : value.substring(0, 137) + "...";
    }
}
