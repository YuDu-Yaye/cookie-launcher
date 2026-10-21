package dev.cookielauncher.core.config;

import dev.cookielauncher.core.json.Json;
import dev.cookielauncher.core.json.JsonObject;
import dev.cookielauncher.core.net.Mirror;
import dev.cookielauncher.core.util.Os;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** User settings, persisted as JSON. Every field has a documented default. */
public final class Config {

    public String javaPath = "";
    public int minMemoryMb = 512;
    public int maxMemoryMb = 4096;
    public String jvmArgs = "-XX:+UseG1GC -XX:+ParallelRefProcEnabled -XX:MaxGCPauseMillis=200 "
            + "-XX:+UnlockExperimentalVMOptions -XX:+DisableExplicitGC -XX:G1NewSizePercent=20 "
            + "-XX:G1ReservePercent=20 -XX:G1HeapRegionSize=16M -XX:+AlwaysPreTouch";
    public String gameArgs = "";
    public int windowWidth = 1280;
    public int windowHeight = 720;
    public boolean fullscreen = false;
    public String mirror = Mirror.Policy.AUTO.name();
    public int downloadWorkers = 12;
    public String proxy = "";
    public String themeId = "caramel-dark";
    public boolean showSnapshots = false;
    public boolean showOldVersions = false;
    public String lastInstanceId = "";
    public String language = "en";
    public String msaClientId = "";
    public boolean reduceMotion = false;

    public static Config load(Path file) {
        Config config = new Config();
        if (file != null && Files.isRegularFile(file)) {
            try {
                config.read(Json.parseObject(file));
            } catch (Exception ignored) {
                // A corrupt config must never prevent startup; defaults are safe.
            }
        }
        config.normalize();
        return config;
    }

    public void save(Path file) throws IOException {
        dev.cookielauncher.core.util.Fs.writeAtomic(file, Json.pretty(toJson()));
    }

    private void read(JsonObject object) {
        javaPath = object.getString("javaPath", javaPath);
        minMemoryMb = object.getInt("minMemoryMb", minMemoryMb);
        maxMemoryMb = object.getInt("maxMemoryMb", maxMemoryMb);
        jvmArgs = object.getString("jvmArgs", jvmArgs);
        gameArgs = object.getString("gameArgs", gameArgs);
        windowWidth = object.getInt("windowWidth", windowWidth);
        windowHeight = object.getInt("windowHeight", windowHeight);
        fullscreen = object.getBoolean("fullscreen", fullscreen);
        mirror = object.getString("mirror", mirror);
        downloadWorkers = object.getInt("downloadWorkers", downloadWorkers);
        proxy = object.getString("proxy", proxy);
        themeId = object.getString("themeId", themeId);
        showSnapshots = object.getBoolean("showSnapshots", showSnapshots);
        showOldVersions = object.getBoolean("showOldVersions", showOldVersions);
        lastInstanceId = object.getString("lastInstanceId", lastInstanceId);
        language = object.getString("language", language);
        msaClientId = object.getString("msaClientId", msaClientId);
        reduceMotion = object.getBoolean("reduceMotion", reduceMotion);
    }

    public JsonObject toJson() {
        return JsonObject.of(
                "javaPath", javaPath,
                "minMemoryMb", minMemoryMb,
                "maxMemoryMb", maxMemoryMb,
                "jvmArgs", jvmArgs,
                "gameArgs", gameArgs,
                "windowWidth", windowWidth,
                "windowHeight", windowHeight,
                "fullscreen", fullscreen,
                "mirror", mirror,
                "downloadWorkers", downloadWorkers,
                "proxy", proxy,
                "themeId", themeId,
                "showSnapshots", showSnapshots,
                "showOldVersions", showOldVersions,
                "lastInstanceId", lastInstanceId,
                "language", language,
                "msaClientId", msaClientId,
                "reduceMotion", reduceMotion);
    }

    public Mirror.Policy mirrorPolicy() {
        return Mirror.parse(mirror);
    }

    /** Clamps values into supported ranges after loading or editing. */
    public void normalize() {
        maxMemoryMb = clamp(maxMemoryMb, 512, 65536);
        minMemoryMb = clamp(minMemoryMb, 256, maxMemoryMb);
        maxMemoryMb = Math.max(minMemoryMb, maxMemoryMb);
        windowWidth = clamp(windowWidth, 854, 7680);
        windowHeight = clamp(windowHeight, 480, 4320);
        downloadWorkers = clamp(downloadWorkers, 1, 32);
        if (jvmArgs == null) {
            jvmArgs = "";
        }
        if (javaPath == null) {
            javaPath = "";
        }
        if (proxy == null) {
            proxy = "";
        }
        if (themeId == null || themeId.isBlank()) {
            themeId = "caramel-dark";
        }
        if (mirror == null || mirror.isBlank()) {
            mirror = Mirror.Policy.AUTO.name();
        }
    }

    /** Default maximum memory for this machine, capped sanely for large hosts. */
    public static int defaultMaxMemoryMb() {
        int recommended = Os.recommendedMaxMemoryMb();
        return clamp(recommended, 1024, 16384);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
