package dev.cookielauncher.core.util;

import java.util.Locale;

/** Platform and hardware facts the launcher needs to pick files and defaults. */
public final class Os {

    public enum Family {
        WINDOWS, MACOS, LINUX, UNKNOWN
    }

    private Os() {
    }

    public static Family family() {
        String name = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (name.contains("win")) {
            return Family.WINDOWS;
        }
        if (name.contains("mac") || name.contains("darwin")) {
            return Family.MACOS;
        }
        if (name.contains("nux") || name.contains("nix") || name.contains("aix") || name.contains("freebsd")) {
            return Family.LINUX;
        }
        return Family.UNKNOWN;
    }

    public static boolean isWindows() {
        return family() == Family.WINDOWS;
    }

    public static boolean isMac() {
        return family() == Family.MACOS;
    }

    public static boolean isLinux() {
        return family() == Family.LINUX;
    }

    /** The {@code os.name} value Mojang rules compare against. */
    public static String mojangOs() {
        return switch (family()) {
            case WINDOWS -> "windows";
            case MACOS -> "osx";
            case LINUX -> "linux";
            default -> "unknown";
        };
    }

    /** The architecture token Mojang rules and native classifiers use. */
    public static String mojangArch() {
        String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        if (arch.equals("aarch64") || arch.equals("arm64")) {
            return "arm64";
        }
        if (arch.contains("64")) {
            return "x64";
        }
        if (arch.contains("86") || arch.equals("i386") || arch.equals("i686")) {
            return "x86";
        }
        if (arch.startsWith("arm") || arch.startsWith("aarch32")) {
            return "arm32";
        }
        return arch.isBlank() ? "x64" : arch;
    }

    public static boolean is64Bit() {
        String arch = mojangArch();
        return "x64".equals(arch) || "arm64".equals(arch);
    }

    /** Mojang's runtime platform key, e.g. {@code windows-x64}. */
    public static String runtimePlatform() {
        String arch = mojangArch();
        String suffix = switch (arch) {
            case "arm64" -> "arm64";
            case "x86" -> "x86";
            default -> "x64";
        };
        return switch (family()) {
            case WINDOWS -> "windows-" + suffix;
            case MACOS -> "arm64".equals(suffix) ? "mac-os-arm64" : "mac-os";
            case LINUX -> "linux-" + (suffix.equals("arm64") ? "arm64" : "x64");
            default -> "linux-x64";
        };
    }

    public static String displayName() {
        return (System.getProperty("os.name", "Unknown") + " "
                + System.getProperty("os.version", "")).trim();
    }

    public static int cpuCount() {
        return Math.max(1, Runtime.getRuntime().availableProcessors());
    }

    /** Physical memory in MiB, or -1 when the platform does not expose it. */
    public static long totalMemoryMb() {
        try {
            java.lang.management.OperatingSystemMXBean bean =
                    java.lang.management.ManagementFactory.getOperatingSystemMXBean();
            if (bean instanceof com.sun.management.OperatingSystemMXBean extended) {
                long bytes = extended.getTotalMemorySize();
                if (bytes > 0) {
                    return bytes / (1024L * 1024L);
                }
            }
        } catch (Throwable ignored) {
            // Fall through: the JVM heap cap is a safe lower bound.
        }
        return -1L;
    }

    /**
     * Heap ceiling this launcher recommends, leaving room for the OS and the game's
     * off-heap buffers. Mirrors the "half of RAM, capped" heuristic that avoids the
     * classic 16 GB allocation on an 8 GB machine.
     */
    public static int recommendedMaxMemoryMb() {
        long total = totalMemoryMb();
        if (total <= 0) {
            return 4096;
        }
        long usable = total - 2048;
        long half = total / 2;
        long target = Math.min(usable, Math.max(half, 2048));
        long capped = Math.min(target, 16384);
        // Round down to a 512 MB step so the number looks intentional.
        return (int) Math.max(1024, (capped / 512) * 512);
    }
}
