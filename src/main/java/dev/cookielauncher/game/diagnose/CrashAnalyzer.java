package dev.cookielauncher.game.diagnose;

import dev.cookielauncher.core.log.Log;
import dev.cookielauncher.core.util.Fs;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Rule-based crash analysis.
 *
 * <p>Scans the tail of the launcher log, the game's {@code latest.log} and up to
 * three newest crash reports, then matches them against an ordered rule table.
 * Results are ranked by severity so the first line a user reads is the one most
 * likely to fix their launch.</p>
 *
 * <p>Every rule has a fixture-based unit test; adding a rule means adding an entry
 * to {@link #RULES} and a matching case in {@code CrashAnalyzerTest}.</p>
 */
public final class CrashAnalyzer {

    private static final int MAX_LINES = 6000;
    private static final int MAX_REPORT_CHARS = 200_000;

    private static final List<Rule> RULES = List.of(
            new Rule("java-too-old", Diagnosis.Severity.BLOCKER,
                    "The Java version is too old for this Minecraft version",
                    List.of("UnsupportedClassVersionError", "class file version"),
                    "The game or a mod was compiled for a newer Java than the one selected, "
                            + "so the JVM refuses to load its classes.",
                    List.of("Set a newer runtime in Settings -> Java.",
                            "Minecraft 1.18-1.20.4 needs Java 17; 1.20.5 and newer need Java 21.",
                            "Let the launcher download a managed runtime instead of pointing at an old JDK.")),

            new Rule("java-too-new", Diagnosis.Severity.ERROR,
                    "The selected Java may be too new",
                    List.of("IllegalAccessError", "InaccessibleObjectException",
                            "Unrecognized VM option", "module java.base does not"),
                    "Newer JDKs removed internals that older Minecraft versions and mods rely on.",
                    List.of("Use Java 8 for 1.7.10-1.16.5, Java 17 for 1.17-1.20.4, "
                                    + "Java 21 for 1.20.5 and newer.",
                            "Remove custom JVM flags that no longer exist.")),

            new Rule("out-of-memory", Diagnosis.Severity.ERROR,
                    "The game ran out of memory",
                    List.of("java.lang.OutOfMemoryError", "GC overhead limit exceeded",
                            "Java heap space", "Could not reserve enough space"),
                    "The JVM heap was exhausted. Large modpacks commonly need 6-8 GB, and a "
                            + "32-bit JVM cannot exceed roughly 1.5 GB at all.",
                    List.of("Raise the maximum memory in Settings -> Memory (start at 4096 MB).",
                            "Confirm the runtime is 64-bit.",
                            "Lower render distance or remove heavy mods and resource packs.")),

            new Rule("missing-dependency", Diagnosis.Severity.BLOCKER,
                    "A mod is missing a dependency",
                    List.of("Missing or unsupported mandatory dependencies",
                            "requires any version of", "ModResolutionException",
                            "MissingDependencyException", "java.lang.NoClassDefFoundError: net/minecraftforge"),
                    "The loader found a mod that declares a dependency which is not installed, "
                            + "so it stopped before Minecraft could start.",
                    List.of("Open Mods and look for entries flagged as missing a dependency.",
                            "Install the missing mod at a matching version.",
                            "Or disable the mod that requires it.")),

            new Rule("duplicate-mod", Diagnosis.Severity.ERROR,
                    "Two copies of the same mod are installed",
                    List.of("Duplicate mods", "duplicate mod id", "DuplicateModsFoundException",
                            "already registered"),
                    "Two jars declare the same mod id, which usually happens after copying a mod "
                            + "in twice or updating without deleting the old file.",
                    List.of("Open Mods and remove the older duplicate.",
                            "Disabled files ending in .disabled are ignored, but two enabled copies are not.")),

            new Rule("loader-mismatch", Diagnosis.Severity.BLOCKER,
                    "A mod or the loader does not match this Minecraft version",
                    List.of("Incompatible mod set", "This mod requires Minecraft",
                            "incompatible with current version", "Unsupported mod",
                            "Mod file is for a different"),
                    "Something was built for another Minecraft or loader version, and the loader "
                            + "refused to start rather than risk a corrupt world.",
                    List.of("Check the mod page for a build matching this exact game version.",
                            "Never mix Forge and Fabric mods in one instance.",
                            "Create an instance with the matching loader.")),

            new Rule("graphics-driver", Diagnosis.Severity.ERROR,
                    "The graphics driver could not provide an OpenGL context",
                    List.of("GLFW error", "Failed to create window", "Pixel format not accelerated",
                            "Couldn't set pixel format", "EXCEPTION_ACCESS_VIOLATION"),
                    "This is almost always a GPU driver problem rather than a launcher problem.",
                    List.of("Update the GPU driver from NVIDIA, AMD or Intel directly.",
                            "On a laptop, force the launcher to use the dedicated GPU.",
                            "Remote desktop sessions often cannot create the required context; "
                                    + "run the game locally.")),

            new Rule("missing-native", Diagnosis.Severity.ERROR,
                    "A native library or system package is missing",
                    List.of("UnsatisfiedLinkError", "error while loading shared libraries",
                            "libGL.so", "libX11", "libgtk", "NoClassDefFoundError: Could not initialize"),
                    "The game could not load a native library, typically because system packages "
                            + "required by LWJGL are not installed.",
                    List.of("On Linux, install the runtime packages listed in docs/TROUBLESHOOTING.md.",
                            "Make sure the Java architecture matches the library architecture.",
                            "Repair the installation so natives are extracted again.")),

            new Rule("bad-assets", Diagnosis.Severity.ERROR,
                    "Game assets or libraries are incomplete",
                    List.of("Failed to download asset", "Unable to load asset",
                            "java.io.FileNotFoundException", "Missing resource"),
                    "A file under assets/ or libraries/ is missing or failed its hash check.",
                    List.of("Run Repair for this version.",
                            "If downloads keep failing, switch the download source in Settings -> Downloads.")),

            new Rule("world-corrupt", Diagnosis.Severity.ERROR,
                    "The world could not be loaded",
                    List.of("Failed to load world", "Level is corrupt", "Chunk file",
                            "java.io.EOFException", "region file"),
                    "A region or level file is damaged, often after a crash while saving.",
                    List.of("Restore the world from a backup.",
                            "Try a repair tool before deleting anything.",
                            "Avoid force-killing the game while it is saving.")),

            new Rule("port-conflict", Diagnosis.Severity.WARNING,
                    "A local port is already in use",
                    List.of("Address already in use", "Failed to bind", "BindException"),
                    "Another process owns the port the game wants, often a second Minecraft "
                            + "instance or a leftover java process.",
                    List.of("Close other Minecraft instances.",
                            "End stray java processes, then launch again.")),

            new Rule("auth-rejected", Diagnosis.Severity.ERROR,
                    "Authentication was rejected",
                    List.of("Invalid credentials", "Failed to verify username", "Invalid session",
                            "401 Unauthorized", "403 Forbidden",
                            "authentication servers are down"),
                    "The session token was refused by Mojang or by the third-party auth server.",
                    List.of("Sign out and sign in again from Accounts.",
                            "For authlib servers, confirm the server URL is correct.",
                            "Check whether the authentication service is reporting an outage.")),

            new Rule("mixin-conflict", Diagnosis.Severity.WARNING,
                    "A mixin could not be applied",
                    List.of("MixinApplyError", "mixin apply failed", "org.spongepowered.asm",
                            "mixin.injection"),
                    "Two mods patch the same class, or a mod expects a different loader build.",
                    List.of("Update the loader and the mods involved.",
                            "Remove recently added mods one at a time to isolate the conflict.",
                            "Check the mod's issue tracker for known incompatibilities.")),

            new Rule("jvm-flag-rejected", Diagnosis.Severity.WARNING,
                    "A custom JVM flag was rejected",
                    List.of("Unrecognized option", "Unrecognized VM option",
                            "Could not create the Java Virtual Machine"),
                    "The JVM refused to start because a configured flag does not exist in the "
                            + "selected Java version.",
                    List.of("Reset the JVM arguments to the defaults in Settings -> Memory.",
                            "Remove flags that were added for a different Java version.")),

            new Rule("mod-crash", Diagnosis.Severity.ERROR,
                    "A mod threw an exception while starting",
                    List.of("A mod crashed", "Exception in thread \"main\"",
                            "Caused by: java.lang.NullPointerException"),
                    "A mod failed during initialisation. The stack trace names its package in the "
                            + "lines immediately above the exception.",
                    List.of("Find the mod's package name in the trace.",
                            "Disable that mod and launch again to confirm.",
                            "Update the mod, and report it to its author with this log."))
    );

    private CrashAnalyzer() {
    }

    public static List<Rule> rules() {
        return RULES;
    }

    /** Analyzes raw log text and returns diagnoses ordered by severity. */
    public static List<Diagnosis> analyze(String logText) {
        List<String> lines = new ArrayList<>();
        if (logText != null) {
            for (String line : logText.split("\\R")) {
                if (!line.isBlank()) {
                    lines.add(line);
                }
            }
        }
        if (lines.size() > MAX_LINES) {
            lines = new ArrayList<>(lines.subList(lines.size() - MAX_LINES, lines.size()));
        }
        List<Diagnosis> diagnoses = new ArrayList<>();
        for (Rule rule : RULES) {
            String evidence = rule.match(lines);
            if (evidence != null) {
                diagnoses.add(new Diagnosis(rule.severity(), rule.title(), rule.explanation(),
                        rule.fixes(), trim(evidence)));
            }
        }
        diagnoses.sort(Comparator.comparingInt(diagnosis -> diagnosis.severity().ordinal()));
        return diagnoses;
    }

    /** Collects launcher log, game log and crash reports, then analyzes them. */
    public static List<Diagnosis> analyzeDirectory(Path gameDirectory) {
        StringBuilder builder = new StringBuilder();
        builder.append(Log.recent(800)).append('\n');

        Path latestLog = gameDirectory.resolve("logs").resolve("latest.log");
        if (Files.isRegularFile(latestLog)) {
            builder.append(tail(latestLog, MAX_REPORT_CHARS)).append('\n');
        }
        Path crashReports = gameDirectory.resolve("crash-reports");
        if (Files.isDirectory(crashReports)) {
            List<Path> reports = new ArrayList<>(Fs.list(crashReports));
            reports.sort(Comparator.comparingLong(path -> path.toFile().lastModified()));
            int from = Math.max(0, reports.size() - 3);
            for (Path report : reports.subList(from, reports.size())) {
                builder.append(tail(report, MAX_REPORT_CHARS)).append('\n');
            }
        }
        return analyze(builder.toString());
    }

    private static String tail(Path file, int chars) {
        try {
            String text = Files.readString(file);
            return text.length() <= chars ? text : text.substring(text.length() - chars);
        } catch (IOException e) {
            return "";
        }
    }

    /** Renders diagnoses as readable text for the log page and the CLI. */
    public static String render(List<Diagnosis> diagnoses) {
        if (diagnoses.isEmpty()) {
            return "No known problem signature was found.\n"
                    + "Share the log with the community if the game still fails to start.";
        }
        StringBuilder builder = new StringBuilder();
        for (Diagnosis diagnosis : diagnoses) {
            builder.append(diagnosis.render()).append('\n');
        }
        return builder.toString();
    }

    /** True when any diagnosis reports a blocking condition. */
    public static boolean hasBlocker(List<Diagnosis> diagnoses) {
        return diagnoses.stream().anyMatch(Diagnosis::isBlocker);
    }

    private static String trim(String line) {
        String value = line.strip();
        return value.length() <= 220 ? value : value.substring(0, 217) + "...";
    }
}
