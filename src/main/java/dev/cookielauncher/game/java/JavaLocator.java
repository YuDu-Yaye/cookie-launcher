package dev.cookielauncher.game.java;

import dev.cookielauncher.core.AppPaths;
import dev.cookielauncher.core.log.Log;
import dev.cookielauncher.core.util.Os;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Discovers Java runtimes: the JVM running the launcher, {@code JAVA_HOME}, every
 * {@code java} on {@code PATH}, the platform's install roots, and the launcher's
 * own managed runtimes.
 */
public final class JavaLocator {

    private static final Pattern VERSION = Pattern.compile("\"([0-9][0-9._]*)\"");

    private List<JavaRuntime> cache;

    public synchronized List<JavaRuntime> all() {
        if (cache == null) {
            cache = scan();
        }
        return new ArrayList<>(cache);
    }

    public synchronized void refresh() {
        cache = null;
    }

    /** Adds a runtime found outside the scan, e.g. a freshly downloaded one. */
    public synchronized void register(Path executable) {
        JavaRuntime runtime = inspect(executable);
        if (runtime == null) {
            return;
        }
        if (cache == null) {
            cache = scan();
        }
        if (cache.stream().noneMatch(existing -> existing.executable().equals(runtime.executable()))) {
            cache.add(runtime);
            cache.sort(Comparator.comparingInt(JavaRuntime::major)
                    .thenComparing(entry -> entry.executable().toString()));
        }
    }

    /**
     * Best runtime for a required major version: the lowest sufficient major, so
     * 1.16.5 does not get Java 21, with 64-bit preferred.
     */
    public JavaRuntime select(int required, String overridePath) {
        if (overridePath != null && !overridePath.isBlank()) {
            JavaRuntime override = inspect(Paths.get(overridePath));
            if (override != null && override.supports(required)) {
                return override;
            }
        }
        return all().stream()
                .filter(runtime -> runtime.supports(required))
                .filter(JavaRuntime::is64Bit)
                .min(Comparator.comparingInt(JavaRuntime::major))
                .orElse(null);
    }

    private List<JavaRuntime> scan() {
        Set<Path> candidates = new LinkedHashSet<>();

        String javaHome = System.getProperty("java.home", "");
        if (!javaHome.isBlank()) {
            addIfJava(candidates, Paths.get(javaHome, "bin"));
        }
        String envHome = System.getenv("JAVA_HOME");
        if (envHome != null && !envHome.isBlank()) {
            addIfJava(candidates, Paths.get(envHome, "bin"));
        }
        String pathVariable = System.getenv("PATH");
        if (pathVariable != null) {
            for (String entry : pathVariable.split(java.io.File.pathSeparator)) {
                if (!entry.isBlank()) {
                    addIfJava(candidates, Paths.get(entry));
                }
            }
        }
        for (Path root : searchRoots()) {
            collectBinDirectories(root, candidates);
        }

        List<JavaRuntime> runtimes = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (Path candidate : candidates) {
            JavaRuntime runtime = inspect(candidate);
            if (runtime == null) {
                continue;
            }
            if (seen.add(runtime.executable().toAbsolutePath().toString())) {
                runtimes.add(runtime);
            }
        }
        runtimes.sort(Comparator.comparingInt(JavaRuntime::major)
                .thenComparing(runtime -> runtime.executable().toString()));
        Log.debug("Detected " + runtimes.size() + " Java runtime(s)");
        return runtimes;
    }

    private List<Path> searchRoots() {
        List<Path> roots = new ArrayList<>();
        switch (Os.family()) {
            case WINDOWS -> {
                roots.add(Paths.get("C:\\Program Files\\Java"));
                roots.add(Paths.get("C:\\Program Files\\Eclipse Adoptium"));
                roots.add(Paths.get("C:\\Program Files\\Microsoft"));
                roots.add(Paths.get("C:\\Program Files\\Zulu"));
                roots.add(Paths.get("C:\\Program Files\\BellSoft"));
                roots.add(Paths.get("C:\\Program Files\\Amazon Corretto"));
                roots.add(Paths.get("C:\\Program Files (x86)\\Java"));
                String localAppData = System.getenv("LOCALAPPDATA");
                if (localAppData != null && !localAppData.isBlank()) {
                    roots.add(Paths.get(localAppData, "Programs", "Eclipse Adoptium"));
                }
                String programData = System.getenv("ProgramData");
                if (programData != null && !programData.isBlank()) {
                    roots.add(Paths.get(programData, "Oracle", "Java"));
                }
            }
            case MACOS -> {
                roots.add(Paths.get("/Library/Java/JavaVirtualMachines"));
                roots.add(Paths.get(System.getProperty("user.home", "."),
                        "Library", "Java", "JavaVirtualMachines"));
                roots.add(Paths.get("/opt/homebrew/opt"));
            }
            default -> {
                roots.add(Paths.get("/usr/lib/jvm"));
                roots.add(Paths.get("/usr/java"));
                roots.add(Paths.get("/opt/java"));
                roots.add(Paths.get(System.getProperty("user.home", "."),
                        ".sdkman", "candidates", "java"));
            }
        }
        roots.add(AppPaths.runtimes());
        return roots;
    }

    private void collectBinDirectories(Path root, Set<Path> out) {
        if (!Files.isDirectory(root)) {
            return;
        }
        try (var stream = Files.walk(root, 4)) {
            stream.filter(Files::isDirectory)
                    .filter(path -> "bin".equalsIgnoreCase(path.getFileName().toString()))
                    .forEach(bin -> addIfJava(out, bin));
        } catch (IOException ignored) {
            // Unreadable tree: skip it.
        }
    }

    private void addIfJava(Set<Path> out, Path binDirectory) {
        Path executable = binDirectory.resolve(Os.isWindows() ? "java.exe" : "java");
        if (Files.isRegularFile(executable)) {
            out.add(executable);
        }
    }

    /** Runs {@code java -version} and parses version, vendor and architecture. */
    public static JavaRuntime inspect(Path executable) {
        if (executable == null || !Files.isRegularFile(executable)) {
            return null;
        }
        try {
            Process process = new ProcessBuilder(executable.toString(), "-version")
                    .redirectErrorStream(true)
                    .start();
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append('\n');
                }
            }
            if (!process.waitFor(20, java.util.concurrent.TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return null;
            }
            String text = output.toString();
            int major = parseMajor(text);
            if (major <= 0) {
                return null;
            }
            return new JavaRuntime(executable, major, parseVersion(text),
                    parseVendor(text), parseArch(text));
        } catch (IOException e) {
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    static int parseMajor(String output) {
        Matcher matcher = VERSION.matcher(output);
        if (!matcher.find()) {
            return -1;
        }
        String[] parts = matcher.group(1).split("[._-]");
        if (parts.length == 0) {
            return -1;
        }
        try {
            int first = Integer.parseInt(parts[0]);
            if (first == 1 && parts.length > 1) {
                return Integer.parseInt(parts[1]);
            }
            return first;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    static String parseVersion(String output) {
        Matcher matcher = Pattern.compile("version \"([^\"]+)\"").matcher(output);
        return matcher.find() ? matcher.group(1) : "";
    }

    static String parseVendor(String output) {
        String lower = output.toLowerCase();
        if (lower.contains("temurin") || lower.contains("adoptium")) {
            return "Eclipse Temurin";
        }
        if (lower.contains("zulu")) {
            return "Azul Zulu";
        }
        if (lower.contains("graalvm")) {
            return "GraalVM";
        }
        if (lower.contains("corretto") || lower.contains("amazon")) {
            return "Amazon Corretto";
        }
        if (lower.contains("liberica") || lower.contains("bellsoft")) {
            return "BellSoft Liberica";
        }
        if (lower.contains("microsoft")) {
            return "Microsoft OpenJDK";
        }
        if (lower.contains("oracle")) {
            return "Oracle JDK";
        }
        if (lower.contains("openjdk")) {
            return "OpenJDK";
        }
        return "Unknown";
    }

    static String parseArch(String output) {
        String lower = output.toLowerCase();
        if (lower.contains("aarch64") || lower.contains("arm64")) {
            return "arm64";
        }
        if (lower.contains("64-bit") || lower.contains("x86_64") || lower.contains("amd64")) {
            return "x64";
        }
        if (lower.contains("32-bit") || lower.contains("i386") || lower.contains("x86")) {
            return "x86";
        }
        return Os.mojangArch();
    }

    public static int requiredFor(int declaredMajor) {
        return declaredMajor <= 0 ? 8 : declaredMajor;
    }
}
