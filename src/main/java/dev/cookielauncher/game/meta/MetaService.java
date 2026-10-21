package dev.cookielauncher.game.meta;

import dev.cookielauncher.core.json.JsonObject;
import dev.cookielauncher.core.net.Http;
import dev.cookielauncher.core.net.Mirror;
import dev.cookielauncher.core.util.Fs;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Fetches Mojang metadata with mirror failover and an on-disk cache, so opening
 * the version list twice does not hit the network twice.
 */
public final class MetaService {

    private final Path cacheDirectory;
    private final Mirror.Policy policy;

    public MetaService(Path cacheDirectory, Mirror.Policy policy) {
        this.cacheDirectory = cacheDirectory;
        this.policy = policy == null ? Mirror.Policy.AUTO : policy;
    }

    /** Returns the version manifest, preferring a cached copy newer than {@code maxAgeMs}. */
    public VersionManifest manifest(long maxAgeMs) throws IOException {
        Path cached = cacheDirectory.resolve("version_manifest_v2.json");
        if (isFresh(cached, maxAgeMs)) {
            try {
                return VersionManifest.load(cached);
            } catch (Exception ignored) {
                // Fall through to the network.
            }
        }
        IOException last = null;
        for (String url : Mirror.candidates(VersionManifest.OFFICIAL_URL, policy)) {
            try {
                String body = Http.getString(url);
                Fs.writeAtomic(cached, body);
                return VersionManifest.parse(dev.cookielauncher.core.json.Json.parseObject(body));
            } catch (IOException e) {
                last = e;
            }
        }
        if (Files.isRegularFile(cached)) {
            return VersionManifest.load(cached);
        }
        throw last == null ? new IOException("Cannot fetch the version manifest") : last;
    }

    /** Downloads one version JSON into {@code versions/<id>/<id>.json}. */
    public JsonObject fetchVersionJson(VersionManifest.Entry entry, Path versionsDirectory) throws IOException {
        Path target = versionsDirectory.resolve(entry.id()).resolve(entry.id() + ".json");
        if (Files.isRegularFile(target)) {
            try {
                return dev.cookielauncher.core.json.Json.parseObject(target);
            } catch (Exception ignored) {
                // Corrupt cache: re-download below.
            }
        }
        IOException last = null;
        for (String url : Mirror.candidates(entry.url(), policy)) {
            try {
                String body = Http.getString(url);
                Fs.writeAtomic(target, body);
                return dev.cookielauncher.core.json.Json.parseObject(body);
            } catch (IOException e) {
                last = e;
            }
        }
        throw last == null ? new IOException("Cannot fetch metadata for " + entry.id()) : last;
    }

    public List<String> candidates(String officialUrl) {
        return Mirror.candidates(officialUrl, policy);
    }

    private static boolean isFresh(Path file, long maxAgeMs) {
        if (maxAgeMs <= 0 || !Files.isRegularFile(file)) {
            return false;
        }
        try {
            return System.currentTimeMillis() - Files.getLastModifiedTime(file).toMillis() < maxAgeMs;
        } catch (IOException e) {
            return false;
        }
    }
}
