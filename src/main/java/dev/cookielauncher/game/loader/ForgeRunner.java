package dev.cookielauncher.game.loader;

import dev.cookielauncher.core.AppPaths;
import dev.cookielauncher.core.log.Log;
import dev.cookielauncher.core.util.Fs;
import dev.cookielauncher.core.util.Progress;
import dev.cookielauncher.game.install.GameInstaller;
import dev.cookielauncher.game.java.JavaInstaller;
import dev.cookielauncher.game.java.JavaLocator;
import dev.cookielauncher.game.java.JavaRuntime;
import dev.cookielauncher.game.meta.VersionMeta;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Runs the official Forge / NeoForge installer in headless client mode.
 *
 * <p>Delegating is deliberate: Forge's installer performs binary patching and SRG
 * remapping with version-specific processors, and every mature launcher shells
 * out to it rather than tracking those internals across a decade of releases.</p>
 */
final class ForgeRunner {

    private static final int TIMEOUT_MINUTES = 20;

    private ForgeRunner() {
    }

    static String run(Path installer, LoaderType type, String gameVersion,
                      String loaderVersion, Progress progress) throws IOException {
        Progress sink = progress == null ? Progress.noop() : progress;
        int requiredJava = requiredJava(gameVersion);
        Path java = resolveJava(requiredJava);
        if (java == null) {
            throw new IOException("No Java " + requiredJava + " runtime is available to run the "
                    + type.display() + " installer");
        }

        sink.status("Running the " + type.display() + " installer (Java " + requiredJava + ")");
        List<String> command = new ArrayList<>();
        command.add(java.toString());
        command.add("-jar");
        command.add(installer.toString());
        command.add("--installClient");
        command.add(AppPaths.root().toString());

        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(AppPaths.root().toFile());
        builder.redirectErrorStream(true);
        Process process = builder.start();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                Log.line("INSTALLER", line);
                String trimmed = line.strip();
                if (trimmed.contains("Downloading") || trimmed.contains("Installing")
                        || trimmed.contains("Extracting")) {
                    sink.status(trimmed.length() > 120 ? trimmed.substring(0, 120) : trimmed);
                }
            }
        }

        boolean finished;
        try {
            finished = process.waitFor(TIMEOUT_MINUTES, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            throw new IOException("The installer was interrupted", e);
        }
        if (!finished) {
            process.destroyForcibly();
            throw new IOException("The installer timed out after " + TIMEOUT_MINUTES + " minutes");
        }
        if (process.exitValue() != 0) {
            throw new IOException("The installer exited with code " + process.exitValue()
                    + "; see the log for details");
        }

        Path directory = findInstalledVersion(type, gameVersion, loaderVersion);
        if (directory == null) {
            throw new IOException("The installer finished but wrote no version JSON");
        }
        String versionId = directory.getFileName().toString();
        sink.status(type.display() + " " + loaderVersion + " installed as " + versionId);

        if (!GameInstaller.isInstalled(versionId)) {
            throw new IOException("Installed version " + versionId + " has no metadata");
        }
        VersionMeta.resolve(AppPaths.versions(), versionId);
        return versionId;
    }

    /**
     * Forge writes {@code forge-<game>-<loader>}; NeoForge writes
     * {@code neoforge-<loader>}. Match the family prefix rather than guessing.
     */
    private static Path findInstalledVersion(LoaderType type, String gameVersion,
                                             String loaderVersion) {
        String expected = type == LoaderType.NEOFORGE
                ? "neoforge-" + loaderVersion
                : "forge-" + gameVersion + "-" + loaderVersion;
        Path direct = AppPaths.version(expected);
        if (Files.isRegularFile(direct.resolve(expected + ".json"))) {
            return direct;
        }
        String prefix = type == LoaderType.NEOFORGE ? "neoforge-" : "forge-" + gameVersion + "-";
        for (Path directory : Fs.list(AppPaths.versions())) {
            String name = directory.getFileName().toString();
            if (!Files.isDirectory(directory)) {
                continue;
            }
            if (name.startsWith(prefix) && Files.isRegularFile(directory.resolve(name + ".json"))) {
                return directory;
            }
        }
        return null;
    }

    /** Java major Forge itself needs in order to run its installer. */
    static int requiredJava(String gameVersion) {
        String[] parts = gameVersion.split("[.\\-]");
        int minor = parts.length > 1 ? parseInt(parts[1]) : 0;
        int patch = parts.length > 2 ? parseInt(parts[2]) : 0;
        if (minor > 20 || (minor == 20 && patch >= 5)) {
            return 21;
        }
        if (minor >= 18) {
            return 17;
        }
        if (minor == 17) {
            return 16;
        }
        return 8;
    }

    private static int parseInt(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static Path resolveJava(int required) {
        JavaLocator locator = new JavaLocator();
        JavaRuntime runtime = locator.select(required, null);
        if (runtime != null) {
            return runtime.executable();
        }
        try {
            return JavaInstaller.ensure(required, Progress.noop());
        } catch (IOException e) {
            Log.warn("Cannot provision Java " + required + ": " + e.getMessage());
            return null;
        }
    }
}
