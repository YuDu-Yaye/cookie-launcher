package dev.cookielauncher.game.install;

import dev.cookielauncher.core.AppPaths;
import dev.cookielauncher.core.json.Json;
import dev.cookielauncher.core.json.JsonObject;
import dev.cookielauncher.core.log.Log;
import dev.cookielauncher.core.net.Downloader;
import dev.cookielauncher.core.net.Mirror;
import dev.cookielauncher.core.net.RemoteFile;
import dev.cookielauncher.core.util.Fmt;
import dev.cookielauncher.core.util.Fs;
import dev.cookielauncher.core.util.Progress;
import dev.cookielauncher.core.util.Zip;
import dev.cookielauncher.game.meta.Artifact;
import dev.cookielauncher.game.meta.Features;
import dev.cookielauncher.game.meta.Library;
import dev.cookielauncher.game.meta.MetaService;
import dev.cookielauncher.game.meta.VersionManifest;
import dev.cookielauncher.game.meta.VersionMeta;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Installs and repairs vanilla versions.
 *
 * <p>The whole file plan is built before any byte moves, so the progress bar is
 * accurate from the first frame and already-verified files cost nothing. Every
 * entry carries its published SHA-1 and an ordered mirror list.</p>
 */
public final class GameInstaller {

    private final Mirror.Policy policy;
    private final int workers;

    public GameInstaller(Mirror.Policy policy, int workers) {
        this.policy = policy == null ? Mirror.Policy.AUTO : policy;
        this.workers = Math.max(1, Math.min(32, workers));
    }

    public GameInstaller() {
        this(Mirror.Policy.AUTO, Downloader.DEFAULT_WORKERS);
    }

    public MetaService metaService() {
        return new MetaService(AppPaths.cache(), policy);
    }

    /** Installs a version and returns its id. */
    public String install(String versionId, Progress progress) throws IOException {
        Progress sink = progress == null ? Progress.noop() : progress;
        sink.status("Looking up " + versionId);
        MetaService meta = metaService();
        VersionManifest manifest = meta.manifest(0L);
        VersionManifest.Entry entry = manifest.find(versionId);
        if (entry == null) {
            throw new IOException("Minecraft " + versionId + " does not exist");
        }
        sink.status("Fetching metadata for " + versionId);
        meta.fetchVersionJson(entry, AppPaths.versions());
        VersionMeta versionMeta = VersionMeta.load(
                AppPaths.version(versionId).resolve(versionId + ".json"));
        installFiles(versionMeta, sink);
        return versionId;
    }

    /** Ensures every file a version needs is present and hash-correct (Repair uses this). */
    public void installFiles(VersionMeta meta, Progress progress) throws IOException {
        Progress sink = progress == null ? Progress.noop() : progress;
        Features features = Features.defaults();
        List<RemoteFile> plan = new ArrayList<>();
        List<Library> nativeLibraries = new ArrayList<>();

        Artifact client = meta.client();
        if (client != null && client.usable()) {
            plan.add(remote(AppPaths.version(meta.id).resolve(meta.id + ".jar"), client));
        }

        for (Library library : meta.libraries) {
            if (!library.allowed(features)) {
                continue;
            }
            Artifact artifact = library.artifact();
            if (artifact != null && artifact.usable()) {
                plan.add(remote(AppPaths.libraries().resolve(
                        artifact.path.isBlank() ? library.path() : artifact.path), artifact));
            }
            if (library.hasNatives()) {
                Artifact natives = library.nativeArtifact();
                if (natives != null && natives.usable()) {
                    plan.add(remote(AppPaths.downloads().resolve("natives").resolve(
                            natives.path.isBlank() ? library.path() : natives.path), natives));
                    nativeLibraries.add(library);
                }
            }
        }

        Artifact loggingConfig = meta.loggingConfig();
        if (loggingConfig != null && loggingConfig.usable() && !meta.loggingConfigName().isBlank()) {
            plan.add(remote(AppPaths.logging().resolve(meta.loggingConfigName()), loggingConfig));
        }

        Path indexFile = null;
        if (meta.assetIndex != null && meta.assetIndex.usable() && !meta.assetIndexId.isBlank()) {
            indexFile = AppPaths.assetIndexes().resolve(meta.assetIndexId + ".json");
            plan.add(remote(indexFile, meta.assetIndex));
        }

        sink.status("Planning " + meta.id + ": " + plan.size() + " files ("
                + Fmt.bytes(estimatedSize(meta)) + " of libraries and client)");
        int failures;
        try (Downloader downloader = new Downloader(workers)) {
            failures = downloader.run(plan, sink);
            if (failures > 0) {
                throw new IOException(failures + " file(s) failed; run install again to resume");
            }
        }

        // Assets need a second pass: the index file itself must exist first.
        if (indexFile != null && Files.isRegularFile(indexFile)) {
            List<RemoteFile> assets = assetPlan(indexFile);
            if (!assets.isEmpty()) {
                sink.status("Downloading " + assets.size() + " assets");
                try (Downloader downloader = new Downloader(workers)) {
                    int assetFailures = downloader.run(assets, sink);
                    if (assetFailures > 0) {
                        Log.warn(assetFailures + " asset(s) missing; the game usually still starts");
                    }
                }
            }
        }
        extractNatives(meta, nativeLibraries, sink);
    }

    private RemoteFile remote(Path target, Artifact artifact) {
        return new RemoteFile(target, artifact.sha1, artifact.size,
                Mirror.candidates(artifact.url, policy));
    }

    private List<RemoteFile> assetPlan(Path indexFile) throws IOException {
        List<RemoteFile> plan = new ArrayList<>();
        JsonObject index = Json.parseObject(indexFile);
        JsonObject objects = index.getObject("objects");
        if (objects == null) {
            return plan;
        }
        for (String name : objects.keys()) {
            JsonObject object = objects.getObject(name);
            if (object == null) {
                continue;
            }
            String hash = object.getString("hash", "");
            long size = object.getLong("size", -1L);
            if (hash.length() < 2) {
                continue;
            }
            Path target = AppPaths.assetObjects().resolve(hash.substring(0, 2)).resolve(hash);
            String url = "https://resources.download.minecraft.net/"
                    + hash.substring(0, 2) + "/" + hash;
            plan.add(new RemoteFile(target, hash, size, Mirror.candidates(url, policy)));
        }
        return plan;
    }

    private void extractNatives(VersionMeta meta, List<Library> libraries, Progress progress) throws IOException {
        if (libraries.isEmpty()) {
            return;
        }
        Path nativesDirectory = AppPaths.natives(meta.id);
        Fs.mkdirs(nativesDirectory);
        for (Library library : libraries) {
            Artifact natives = library.nativeArtifact();
            if (natives == null) {
                continue;
            }
            Path jar = AppPaths.downloads().resolve("natives")
                    .resolve(natives.path.isBlank() ? library.path() : natives.path);
            if (!Files.isRegularFile(jar)) {
                continue;
            }
            List<String> excludes = library.extractExcludes();
            progress.status("Extracting natives: " + library.name);
            try {
                Zip.extract(jar, nativesDirectory, name -> {
                    String lower = name.toLowerCase();
                    if (lower.startsWith("meta-inf/")) {
                        return false;
                    }
                    if (!(lower.endsWith(".dll") || lower.endsWith(".so")
                            || lower.endsWith(".dylib") || lower.endsWith(".jnilib"))) {
                        return false;
                    }
                    for (String exclude : excludes) {
                        if (lower.startsWith(exclude.toLowerCase())) {
                            return false;
                        }
                    }
                    return true;
                });
            } catch (IOException e) {
                Log.warn("Cannot extract natives from " + jar.getFileName() + ": " + e.getMessage());
            }
        }
    }

    public static List<String> installedVersions() {
        List<String> ids = new ArrayList<>();
        for (Path directory : Fs.list(AppPaths.versions())) {
            String id = directory.getFileName().toString();
            if (Files.isRegularFile(directory.resolve(id + ".json"))) {
                ids.add(id);
            }
        }
        return ids;
    }

    public static boolean isInstalled(String versionId) {
        return versionId != null && !versionId.isBlank()
                && Files.isRegularFile(AppPaths.version(versionId).resolve(versionId + ".json"));
    }

    /** Approximate download size of client plus libraries. */
    public static long estimatedSize(VersionMeta meta) {
        long total = 0L;
        Artifact client = meta.client();
        if (client != null) {
            total += Math.max(0L, client.size);
        }
        for (Library library : meta.libraries) {
            Artifact artifact = library.artifact();
            if (artifact != null) {
                total += Math.max(0L, artifact.size);
            }
        }
        return total;
    }
}
