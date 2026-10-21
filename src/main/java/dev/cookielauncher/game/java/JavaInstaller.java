package dev.cookielauncher.game.java;

import dev.cookielauncher.core.AppPaths;
import dev.cookielauncher.core.json.JsonObject;
import dev.cookielauncher.core.log.Log;
import dev.cookielauncher.core.net.Downloader;
import dev.cookielauncher.core.net.Http;
import dev.cookielauncher.core.net.RemoteFile;
import dev.cookielauncher.core.util.Fmt;
import dev.cookielauncher.core.util.Fs;
import dev.cookielauncher.core.util.Os;
import dev.cookielauncher.core.util.Progress;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Provisions Mojang-published Java runtimes so users never install a JDK by hand.
 *
 * <p>Mojang ships per-platform JRE bundles through the java-runtime product
 * manifest. We pick the component matching the required major version, download
 * every file with its hash, and mark executables runnable on POSIX systems.</p>
 */
public final class JavaInstaller {

    public static final String PRODUCT_MANIFEST =
            "https://launchermeta.mojang.com/v1/products/java-runtime/2ec0cc96c44e5a76b9c8b7c39df7210883d12871/all.json";

    private JavaInstaller() {
    }

    /** Mojang component names to try, newest first, for a required major version. */
    public static List<String> components(int requiredMajor) {
        if (requiredMajor >= 21) {
            return List.of("java-runtime-delta", "java-runtime-gamma", "java-runtime-beta");
        }
        if (requiredMajor >= 17) {
            return List.of("java-runtime-gamma", "java-runtime-beta");
        }
        if (requiredMajor >= 16) {
            return List.of("java-runtime-beta", "java-runtime-gamma");
        }
        return List.of("jre-legacy");
    }

    /**
     * Ensures a runtime for {@code requiredMajor} exists and returns its java
     * executable, downloading it when necessary.
     */
    public static Path ensure(int requiredMajor, Progress progress) throws IOException {
        Progress sink = progress == null ? Progress.noop() : progress;
        Path existing = find(requiredMajor);
        if (existing != null) {
            sink.status("Using managed Java " + requiredMajor);
            return existing;
        }

        sink.status("Fetching the Java " + requiredMajor + " runtime manifest");
        JsonObject root = Http.getJson(PRODUCT_MANIFEST);
        JsonObject platform = root.getObject(Os.runtimePlatform());
        if (platform == null) {
            throw new IOException("Mojang publishes no runtime for " + Os.runtimePlatform());
        }

        JsonObject chosen = null;
        String chosenName = "";
        for (String component : components(requiredMajor)) {
            var array = platform.getArray(component);
            if (array != null && !array.isEmpty()) {
                chosen = array.getObject(0);
                chosenName = component;
                break;
            }
        }
        if (chosen == null) {
            throw new IOException("No Mojang runtime offers Java " + requiredMajor);
        }

        String componentName = chosen.getString("component", chosenName);
        JsonObject manifestInfo = chosen.getObject("manifest");
        String manifestUrl = manifestInfo == null ? "" : manifestInfo.getString("url", "");
        if (manifestUrl.isBlank()) {
            throw new IOException("The runtime manifest URL is missing");
        }
        JsonObject manifest = Http.getJson(manifestUrl);
        JsonObject files = manifest.getObject("files");
        if (files == null) {
            throw new IOException("The runtime manifest contains no files");
        }

        Path target = AppPaths.runtimes().resolve(componentName + "-" + Os.mojangArch());
        Fs.mkdirs(target);

        List<RemoteFile> plan = new ArrayList<>();
        List<Path> executables = new ArrayList<>();
        long totalBytes = 0L;
        for (String relative : files.keys()) {
            JsonObject entry = files.getObject(relative);
            if (entry == null || !"file".equals(entry.getString("type", "file"))) {
                continue;
            }
            JsonObject downloadsNode = entry.getObject("downloads");
            JsonObject raw = downloadsNode == null ? null : downloadsNode.getObject("raw");
            if (raw == null) {
                continue;
            }
            String url = raw.getString("url", "");
            String sha1 = raw.getString("sha1", "");
            long size = raw.getLong("size", -1L);
            if (url.isBlank()) {
                continue;
            }
            Path destination = Fs.resolveSafely(target, relative);
            plan.add(new RemoteFile(destination, sha1, size, List.of(url)));
            totalBytes += Math.max(0L, size);
            if (entry.getBoolean("executable", false)) {
                executables.add(destination);
            }
        }

        sink.status("Downloading Java " + requiredMajor + " (" + plan.size() + " files, "
                + Fmt.bytes(totalBytes) + ")");
        try (Downloader downloader = new Downloader(Downloader.DEFAULT_WORKERS)) {
            int failures = downloader.run(plan, sink);
            if (failures > 0) {
                throw new IOException(failures + " runtime file(s) failed to download");
            }
        }
        makeExecutable(executables);
        Path java = executableIn(target);
        if (java == null) {
            throw new IOException("The downloaded runtime has no java executable");
        }
        Log.info("Installed managed Java " + requiredMajor + " at " + target);
        return java;
    }

    /** Looks for an installed managed runtime satisfying {@code requiredMajor}. */
    public static Path find(int requiredMajor) {
        Path root = AppPaths.runtimes();
        if (!Files.isDirectory(root)) {
            return null;
        }
        for (Path directory : Fs.list(root)) {
            if (!Files.isDirectory(directory)) {
                continue;
            }
            Path java = executableIn(directory);
            if (java == null) {
                continue;
            }
            JavaRuntime runtime = JavaLocator.inspect(java);
            if (runtime != null && runtime.major() >= requiredMajor) {
                return java;
            }
        }
        return null;
    }

    /** Finds the {@code java} executable inside a runtime directory tree. */
    public static Path executableIn(Path root) {
        Path direct = root.resolve("bin").resolve(Os.isWindows() ? "java.exe" : "java");
        if (Files.isRegularFile(direct)) {
            return direct;
        }
        if (Os.isMac()) {
            Path macPath = root.resolve("Contents").resolve("Home").resolve("bin").resolve("java");
            if (Files.isRegularFile(macPath)) {
                return macPath;
            }
        }
        for (Path child : Fs.list(root)) {
            if (Files.isDirectory(child)) {
                Path nested = executableIn(child);
                if (nested != null) {
                    return nested;
                }
            }
        }
        return null;
    }

    private static void makeExecutable(List<Path> files) {
        if (Os.isWindows()) {
            return;
        }
        for (Path file : files) {
            try {
                Set<PosixFilePermission> permissions = Files.getPosixFilePermissions(file);
                permissions.add(PosixFilePermission.OWNER_EXECUTE);
                permissions.add(PosixFilePermission.GROUP_EXECUTE);
                permissions.add(PosixFilePermission.OTHERS_EXECUTE);
                Files.setPosixFilePermissions(file, permissions);
            } catch (IOException ignored) {
                // Non-POSIX filesystem: nothing to do.
            }
        }
    }
}
