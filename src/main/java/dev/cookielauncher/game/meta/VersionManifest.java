package dev.cookielauncher.game.meta;

import dev.cookielauncher.core.json.Json;
import dev.cookielauncher.core.json.JsonArray;
import dev.cookielauncher.core.json.JsonObject;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Mojang's version manifest: every published version and its metadata URL. */
public final class VersionManifest {

    public static final String OFFICIAL_URL =
            "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";

    public String latestRelease = "";
    public String latestSnapshot = "";
    public final List<Entry> versions = new ArrayList<>();

    public static VersionManifest parse(JsonObject object) {
        VersionManifest manifest = new VersionManifest();
        JsonObject latest = object.getObject("latest");
        if (latest != null) {
            manifest.latestRelease = latest.getString("release", "");
            manifest.latestSnapshot = latest.getString("snapshot", "");
        }
        JsonArray array = object.getArray("versions");
        if (array != null) {
            for (JsonObject entryObject : array.objects()) {
                Entry entry = new Entry(
                        entryObject.getString("id", ""),
                        entryObject.getString("type", "release"),
                        entryObject.getString("url", ""),
                        entryObject.getString("sha1", ""),
                        entryObject.getString("releaseTime", ""));
                if (!entry.id().isBlank() && !entry.url().isBlank()) {
                    manifest.versions.add(entry);
                }
            }
        }
        return manifest;
    }

    public static VersionManifest load(Path file) throws IOException {
        return parse(Json.parseObject(file));
    }

    public Entry find(String id) {
        if (id == null) {
            return null;
        }
        for (Entry entry : versions) {
            if (entry.id().equals(id)) {
                return entry;
            }
        }
        return null;
    }

    /** Newest-first filtered list. */
    public List<Entry> filter(boolean snapshots, boolean oldVersions, String query) {
        String needle = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        List<Entry> result = new ArrayList<>();
        for (Entry entry : versions) {
            boolean isRelease = "release".equalsIgnoreCase(entry.type());
            boolean isOld = entry.type().startsWith("old_");
            if (!isRelease) {
                if (!snapshots && !isOld) {
                    continue;
                }
                if (isOld && !oldVersions) {
                    continue;
                }
            }
            if (!needle.isEmpty() && !entry.id().toLowerCase(Locale.ROOT).contains(needle)) {
                continue;
            }
            result.add(entry);
        }
        result.sort(Comparator.comparing((Entry entry) -> entry.releaseTime()).reversed());
        return result;
    }

    /** One published version. */
    public record Entry(String id, String type, String url, String sha1, String releaseTime) {

        public String displayType() {
            return switch (type == null ? "" : type) {
                case "release" -> "Release";
                case "snapshot" -> "Snapshot";
                case "old_beta" -> "Old Beta";
                case "old_alpha" -> "Old Alpha";
                default -> type == null ? "" : type;
            };
        }

        public String shortDate() {
            return releaseTime == null || releaseTime.length() < 10
                    ? (releaseTime == null ? "" : releaseTime)
                    : releaseTime.substring(0, 10);
        }
    }
}
