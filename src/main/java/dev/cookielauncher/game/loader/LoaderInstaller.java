package dev.cookielauncher.game.loader;

import dev.cookielauncher.core.AppPaths;
import dev.cookielauncher.core.json.Json;
import dev.cookielauncher.core.json.JsonArray;
import dev.cookielauncher.core.json.JsonObject;
import dev.cookielauncher.core.log.Log;
import dev.cookielauncher.core.net.Downloader;
import dev.cookielauncher.core.net.Http;
import dev.cookielauncher.core.net.Mirror;
import dev.cookielauncher.core.net.RemoteFile;
import dev.cookielauncher.core.util.Fs;
import dev.cookielauncher.core.util.Progress;
import dev.cookielauncher.game.install.GameInstaller;
import dev.cookielauncher.game.meta.VersionMeta;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Installs mod loaders.
 *
 * <p>Fabric and Quilt publish a ready-made version JSON per game version, so
 * installation is one metadata fetch plus the inherited vanilla files. Forge and
 * NeoForge ship an installer jar whose processor pipeline (binary patching, SRG
 * remapping) is version specific and is delegated to the upstream installer
 * rather than reimplemented.</p>
 */
public final class LoaderInstaller {

    public static final String FABRIC_META = "https://meta.fabricmc.net/v2";
    public static final String QUILT_META = "https://meta.quiltmc.org/v3";
    public static final String FORGE_PROMOTIONS =
            "https://files.minecraftforge.net/net/minecraftforge/forge/promotions_slim.json";
    public static final String NEOFORGE_MAVEN = "https://maven.neoforged.net/releases";

    private final Mirror.Policy policy;
    private final int workers;

    public LoaderInstaller(Mirror.Policy policy, int workers) {
        this.policy = policy == null ? Mirror.Policy.AUTO : policy;
        this.workers = Math.max(1, Math.min(32, workers));
    }

    public LoaderInstaller() {
        this(Mirror.Policy.AUTO, Downloader.DEFAULT_WORKERS);
    }

    /** Lists installable builds for a game version, newest first. */
    public List<LoaderVersion> available(LoaderType type, String gameVersion) throws IOException {
        return switch (type) {
            case FABRIC -> fabricLike(FABRIC_META, LoaderType.FABRIC, gameVersion);
            case QUILT -> fabricLike(QUILT_META, LoaderType.QUILT, gameVersion);
            case NEOFORGE -> neoforgeVersions(gameVersion);
            case FORGE -> forgeVersions(gameVersion);
            case VANILLA -> List.of();
        };
    }

    /** Newest build, or null when the loader has no build for this version. */
    public LoaderVersion latest(LoaderType type, String gameVersion) {
        try {
            List<LoaderVersion> versions = available(type, gameVersion);
            return versions.isEmpty() ? null : versions.get(0);
        } catch (IOException e) {
            return null;
        }
    }

    private List<LoaderVersion> fabricLike(String base, LoaderType type, String gameVersion)
            throws IOException {
        String url = base + "/versions/loader/" + gameVersion;
        Object root = fetchJsonValue(url);
        JsonArray array = null;
        if (root instanceof JsonArray direct) {
            array = direct;
        } else if (root instanceof JsonObject object) {
            array = object.getArray("loader");
            if (array == null) {
                array = object.getArray("versions");
            }
        }
        List<LoaderVersion> result = new ArrayList<>();
        if (array == null) {
            return result;
        }
        for (JsonObject entry : array.objects()) {
            JsonObject loaderObject = entry.getObject("loader");
            String version = loaderObject != null
                    ? loaderObject.getString("version", "")
                    : entry.getString("version", "");
            if (version.isBlank()) {
                continue;
            }
            result.add(LoaderVersion.of(type, version, gameVersion,
                    entry.getBoolean("stable", true)));
        }
        return result;
    }

    /** Tries official then mirror, accepting either root shape. */
    private Object fetchJsonValue(String officialUrl) throws IOException {
        IOException last = null;
        for (String url : Mirror.candidates(officialUrl, policy)) {
            try {
                return Http.getJsonValue(url);
            } catch (IOException e) {
                last = e;
            }
        }
        throw last == null ? new IOException("Cannot reach " + officialUrl) : last;
    }

    private List<LoaderVersion> forgeVersions(String gameVersion) {
        List<LoaderVersion> result = new ArrayList<>();
        try {
            JsonObject promotions = Http.getJson(FORGE_PROMOTIONS);
            JsonObject promos = promotions.getObject("promos");
            if (promos != null) {
                String recommended = promos.getString(gameVersion + "-recommended");
                String latest = promos.getString(gameVersion + "-latest");
                if (recommended != null && !recommended.isBlank()) {
                    result.add(LoaderVersion.of(LoaderType.FORGE, recommended, gameVersion, true));
                }
                if (latest != null && !latest.isBlank() && !latest.equals(recommended)) {
                    result.add(LoaderVersion.of(LoaderType.FORGE, latest, gameVersion, false));
                }
            }
        } catch (IOException e) {
            Log.warn("Cannot query Forge promotions: " + e.getMessage());
        }
        return result;
    }

    private List<LoaderVersion> neoforgeVersions(String gameVersion) {
        List<LoaderVersion> result = new ArrayList<>();
        String[] parts = gameVersion.split("\\.");
        if (parts.length < 2) {
            return result;
        }
        // NeoForge versions follow the game version: 1.21.1 maps to 21.1.x.
        String prefix = parts[1] + "." + (parts.length > 2 ? parts[2] : "0") + ".";
        try {
            String xml = Http.getString(NEOFORGE_MAVEN
                    + "/net/neoforged/neoforge/maven-metadata.xml");
            List<String> versions = parseXmlVersions(xml);
            for (int i = versions.size() - 1; i >= 0; i--) {
                String version = versions.get(i);
                if (version.startsWith(prefix)) {
                    result.add(LoaderVersion.of(LoaderType.NEOFORGE, version, gameVersion, true));
                }
            }
        } catch (IOException e) {
            Log.warn("Cannot list NeoForge versions: " + e.getMessage());
        }
        return result;
    }

    static List<String> parseXmlVersions(String xml) {
        List<String> versions = new ArrayList<>();
        int index = 0;
        while (true) {
            int start = xml.indexOf("<version>", index);
            if (start < 0) {
                break;
            }
            int end = xml.indexOf("</version>", start);
            if (end < 0) {
                break;
            }
            versions.add(xml.substring(start + "<version>".length(), end).trim());
            index = end + 1;
        }
        return versions;
    }

    /**
     * Installs a loader on top of an installed vanilla version and returns the
     * resulting version id, which becomes the instance's target.
     */
    public String install(LoaderType type, String gameVersion, String loaderVersion,
                          Progress progress) throws IOException {
        Progress sink = progress == null ? Progress.noop() : progress;
        if (type == LoaderType.VANILLA) {
            throw new IOException("Vanilla has no loader to install");
        }
        if (!GameInstaller.isInstalled(gameVersion)) {
            sink.status("Installing " + gameVersion + " first");
            new GameInstaller(policy, workers).install(gameVersion, sink);
        }
        return switch (type) {
            case FABRIC -> installFabricLike(LoaderType.FABRIC, FABRIC_META, gameVersion,
                    loaderVersion, sink);
            case QUILT -> installFabricLike(LoaderType.QUILT, QUILT_META, gameVersion,
                    loaderVersion, sink);
            case FORGE, NEOFORGE -> installForgeLike(type, gameVersion, loaderVersion, sink);
            case VANILLA -> gameVersion;
        };
    }

    private String installFabricLike(LoaderType type, String base, String gameVersion,
                                     String loaderVersion, Progress sink) throws IOException {
        String versionId = gameVersion + "-" + type.id();
        String url = base + "/versions/loader/" + gameVersion + "/" + loaderVersion + "/profile/json";
        sink.status("Fetching the " + type.display() + " profile");
        JsonObject profile = null;
        IOException last = null;
        for (String candidate : Mirror.candidates(url, policy)) {
            try {
                profile = Http.getJson(candidate);
                break;
            } catch (IOException e) {
                last = e;
            }
        }
        if (profile == null) {
            throw last == null ? new IOException("Cannot fetch the loader profile") : last;
        }
        profile.put("id", versionId);
        profile.put("inheritsFrom", gameVersion);

        Path directory = AppPaths.version(versionId);
        Fs.mkdirs(directory);
        Fs.writeAtomic(directory.resolve(versionId + ".json"), Json.pretty(profile));

        sink.status("Resolving inherited files for " + versionId);
        VersionMeta merged = VersionMeta.resolve(AppPaths.versions(), versionId);
        new GameInstaller(policy, workers).installFiles(merged, sink);
        sink.status(type.display() + " " + loaderVersion + " installed");
        return versionId;
    }

    private String installForgeLike(LoaderType type, String gameVersion, String loaderVersion,
                                    Progress sink) throws IOException {
        String coordinator = type == LoaderType.NEOFORGE
                ? loaderVersion
                : gameVersion + "-" + loaderVersion;
        String group = type == LoaderType.NEOFORGE ? "net/neoforged" : "net/minecraftforge";
        String artifact = type == LoaderType.NEOFORGE ? "neoforge" : "forge";
        String base = type == LoaderType.NEOFORGE ? NEOFORGE_MAVEN
                : "https://maven.minecraftforge.net";
        String url = base + "/" + group + "/" + artifact + "/" + coordinator + "/"
                + artifact + "-" + coordinator + "-installer.jar";
        Path installer = AppPaths.installers()
                .resolve(artifact + "-" + coordinator + "-installer.jar");

        sink.status("Downloading the " + type.display() + " installer");
        try (Downloader downloader = new Downloader(workers)) {
            int failures = downloader.run(
                    List.of(new RemoteFile(installer, null, -1L, Mirror.candidates(url, policy))), sink);
            if (failures > 0 || !Files.isRegularFile(installer)) {
                throw new IOException("Cannot download the " + type.display() + " installer");
            }
        }
        return ForgeRunner.run(installer, type, gameVersion, loaderVersion, sink);
    }
}
