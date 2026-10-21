package dev.cookielauncher.core;

import dev.cookielauncher.core.util.Fs;
import dev.cookielauncher.core.util.Os;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Every on-disk location the launcher uses.
 *
 * <p>Resolution order: {@code -Dcookielauncher.home}, then a {@code .portable}
 * marker beside the executable (portable mode keeps data next to the app), then
 * the platform's standard application-data directory.</p>
 */
public final class AppPaths {

    private static volatile Path root;
    private static volatile Path executableDirectory;

    private AppPaths() {
    }

    public static Path root() {
        Path current = root;
        if (current != null) {
            return current;
        }
        synchronized (AppPaths.class) {
            if (root == null) {
                root = resolveRoot();
            }
            return root;
        }
    }

    public static void setRoot(Path value) {
        Path normalized = value.toAbsolutePath().normalize();
        Fs.mkdirs(normalized);
        root = normalized;
    }

    /** Directory containing the running application, used for portable mode. */
    public static Path executableDirectory() {
        Path current = executableDirectory;
        if (current != null) {
            return current;
        }
        Path detected = detectExecutableDirectory();
        executableDirectory = detected;
        return detected;
    }

    private static Path detectExecutableDirectory() {
        try {
            Path location = Paths.get(AppPaths.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI());
            if (Files.isRegularFile(location)) {
                // Running from a jar: jpackage places it in app/, the exe one level up.
                Path parent = location.getParent();
                if (parent != null && "app".equalsIgnoreCase(parent.getFileName().toString())) {
                    Path grandParent = parent.getParent();
                    if (grandParent != null) {
                        return grandParent;
                    }
                }
                return parent == null ? Paths.get("").toAbsolutePath() : parent;
            }
            return location;
        } catch (Exception e) {
            return Paths.get("").toAbsolutePath();
        }
    }

    private static Path resolveRoot() {
        String override = System.getProperty("cookielauncher.home");
        if (override != null && !override.isBlank()) {
            return Paths.get(override).toAbsolutePath().normalize();
        }
        Path beside = executableDirectory();
        if (Files.isRegularFile(beside.resolve(".portable"))
                || Files.isRegularFile(beside.resolve("data").resolve(".portable"))) {
            return beside.resolve("data");
        }
        Path home = Paths.get(System.getProperty("user.home", "."));
        return switch (Os.family()) {
            case WINDOWS -> {
                String appData = System.getenv("APPDATA");
                yield (appData == null || appData.isBlank()
                        ? home.resolve("AppData").resolve("Roaming")
                        : Paths.get(appData)).resolve("CookieLauncher");
            }
            case MACOS -> home.resolve("Library").resolve("Application Support").resolve("CookieLauncher");
            default -> {
                String xdg = System.getenv("XDG_DATA_HOME");
                yield (xdg == null || xdg.isBlank()
                        ? home.resolve(".local").resolve("share")
                        : Paths.get(xdg)).resolve("cookie-launcher");
            }
        };
    }

    public static boolean isPortable() {
        return System.getProperty("cookielauncher.home") != null
                || Files.isRegularFile(executableDirectory().resolve(".portable"));
    }

    // ------------------------------------------------------------------
    // Game data (shared, Mojang-compatible layout)
    // ------------------------------------------------------------------

    public static Path versions() {
        return root().resolve("versions");
    }

    public static Path version(String id) {
        return versions().resolve(id);
    }

    public static Path libraries() {
        return root().resolve("libraries");
    }

    public static Path assets() {
        return root().resolve("assets");
    }

    public static Path assetIndexes() {
        return assets().resolve("indexes");
    }

    public static Path assetObjects() {
        return assets().resolve("objects");
    }

    public static Path natives(String versionId) {
        return root().resolve("natives").resolve(versionId);
    }

    // ------------------------------------------------------------------
    // Launcher state
    // ------------------------------------------------------------------

    public static Path instances() {
        return root().resolve("instances");
    }

    public static Path instance(String instanceId) {
        return instances().resolve(instanceId);
    }

    public static Path configFile() {
        return root().resolve("config.json");
    }

    public static Path instancesFile() {
        return root().resolve("instances.json");
    }

    public static Path accountsFile() {
        return root().resolve("accounts.json");
    }

    public static Path cache() {
        return root().resolve("cache");
    }

    public static Path downloads() {
        return cache().resolve("downloads");
    }

    public static Path installers() {
        return cache().resolve("installers");
    }

    public static Path logging() {
        return cache().resolve("logging");
    }

    public static Path runtimes() {
        return root().resolve("runtimes");
    }

    public static Path logs() {
        return root().resolve("logs");
    }

    public static Path launcherLog() {
        return logs().resolve("launcher.log");
    }

    public static void ensureDirectories() {
        for (Path directory : new Path[]{
                versions(), libraries(), assets(), assetIndexes(), assetObjects(),
                instances(), cache(), downloads(), installers(), logging(),
                runtimes(), logs()}) {
            Fs.mkdirs(directory);
        }
    }
}
