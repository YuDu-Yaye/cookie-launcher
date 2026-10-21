package dev.cookielauncher.game.auth;

import dev.cookielauncher.core.AppPaths;
import dev.cookielauncher.core.json.JsonObject;
import dev.cookielauncher.core.log.Log;
import dev.cookielauncher.core.net.Downloader;
import dev.cookielauncher.core.net.Http;
import dev.cookielauncher.core.net.RemoteFile;
import dev.cookielauncher.core.util.Fs;
import dev.cookielauncher.core.util.Progress;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Fetches and caches the authlib-injector agent used to mount Yggdrasil
 * authentication servers into the game.
 */
public final class AuthlibInjector {

    private static final String RELEASE_URL =
            "https://authlib-injector.yushi.moe/artifact/latest.json";
    private static final String FALLBACK =
            "https://authlib-injector.yushi.moe/artifact/53/authlib-injector-1.2.5.jar";

    private AuthlibInjector() {
    }

    /** Returns the cached agent, downloading it once when missing. */
    public static Path ensure() throws IOException {
        Path directory = AppPaths.cache().resolve("authlib");
        Fs.mkdirs(directory);
        Path cached = findCached(directory);
        if (cached != null) {
            return cached;
        }

        String url = FALLBACK;
        String fileName = "authlib-injector.jar";
        try {
            JsonObject object = Http.getJson(RELEASE_URL);
            String downloadUrl = object.getString("download_url", "");
            if (!downloadUrl.isBlank()) {
                url = downloadUrl;
            }
            String version = object.getString("version", "");
            if (!version.isBlank()) {
                fileName = "authlib-injector-" + version + ".jar";
            }
        } catch (IOException e) {
            Log.warn("Cannot query authlib-injector releases: " + e.getMessage());
        }

        Path target = directory.resolve(fileName);
        Log.info("Downloading authlib-injector from " + url);
        try (Downloader downloader = new Downloader(2, false)) {
            downloader.run(List.of(new RemoteFile(target, null, -1L, List.of(url))), Progress.noop());
        }
        if (!Files.isRegularFile(target) || Files.size(target) < 10_000L) {
            throw new IOException("The authlib-injector download looks incomplete");
        }
        return target;
    }

    private static Path findCached(Path directory) {
        for (Path file : Fs.list(directory)) {
            String name = file.getFileName().toString();
            if (!Files.isRegularFile(file) || !name.startsWith("authlib-injector")
                    || !name.endsWith(".jar")) {
                continue;
            }
            try {
                if (Files.size(file) > 10_000L) {
                    return file;
                }
            } catch (IOException ignored) {
                // Keep looking.
            }
        }
        return null;
    }

    /** The API root authlib-injector must be pointed at. */
    public static String apiRoot(String serverUrl) {
        if (serverUrl == null || serverUrl.isBlank()) {
            return "";
        }
        String value = serverUrl.trim();
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }
}
