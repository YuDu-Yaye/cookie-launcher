using System.IO;
using CookieLauncher.Core;

namespace CookieLauncher.Game.Diagnose;

/// <summary>One diagnosis: what happened, why, and the concrete next step.</summary>
/// <remarks>
/// Every diagnosis carries at least one fix. "Here is your stack trace" is not help, and the
/// absence of that distinction is the gap the market research found in every other launcher.
/// </remarks>
public sealed record Diagnosis(
    Diagnosis.Severity Level,
    string Title,
    string Explanation,
    IReadOnlyList<string> Fixes,
    string Evidence)
{
    public enum Severity
    {
        /// <summary>The game cannot start until this is fixed.</summary>
        Blocker,
        /// <summary>The game starts but will fail or run badly.</summary>
        Error,
        /// <summary>Worth knowing; may not be the cause.</summary>
        Warning,
    }

    public string Render()
    {
        List<string> lines =
        [
            $"[{Level.ToString().ToUpperInvariant()}] {Title}",
            "  " + Explanation,
        ];
        if (!string.IsNullOrWhiteSpace(Evidence))
        {
            lines.Add("  Evidence: " + Evidence);
        }
        foreach (string fix in Fixes)
        {
            lines.Add("  \u2192 " + fix);
        }
        return string.Join(Environment.NewLine, lines);
    }
}

/// <summary>A log signature and the advice it triggers.</summary>
/// <remarks>
/// Rules are data, not code: adding a failure class means adding an entry and a fixture,
/// which is what keeps the table extensible by people who do not want to read the engine.
/// </remarks>
public sealed record CrashRule(
    string Id,
    Diagnosis.Severity Level,
    string Title,
    IReadOnlyList<string> Needles,
    string Explanation,
    IReadOnlyList<string> Fixes)
{
    /// <summary>Returns the first matching line, or null when the rule does not apply.</summary>
    public string? Match(IReadOnlyList<string> lines)
    {
        foreach (string line in lines)
        {
            foreach (string needle in Needles)
            {
                if (line.Contains(needle, StringComparison.OrdinalIgnoreCase))
                {
                    return line;
                }
            }
        }
        return null;
    }
}

/// <summary>
/// Rule-based crash analysis over the launcher log, the game log and crash reports.
/// </summary>
public static class CrashAnalyzer
{
    private const int MaxLines = 6000;
    private const int MaxReportChars = 200_000;

    public static IReadOnlyList<CrashRule> Rules { get; } =
    [
        new("java-too-old", Diagnosis.Severity.Blocker,
            "The Java version is too old for this Minecraft version",
            ["UnsupportedClassVersionError", "class file version"],
            "The game or a mod was compiled for a newer Java than the one selected, so the JVM "
            + "refuses to load its classes.",
            ["Pick a newer runtime in Settings.",
             "Minecraft 1.18 to 1.20.4 needs Java 17; 1.20.5 and newer need Java 21.",
             "Let the launcher download a managed runtime instead of pointing at an old JDK."]),

        new("java-too-new", Diagnosis.Severity.Error,
            "The selected Java may be too new",
            ["IllegalAccessError", "InaccessibleObjectException", "Unrecognized VM option",
             "module java.base does not"],
            "Newer JDKs removed internals that older Minecraft versions and mods rely on.",
            ["Use Java 8 for 1.7.10 to 1.16.5, Java 17 for 1.17 to 1.20.4, Java 21 for 1.20.5+.",
             "Remove custom JVM flags that no longer exist."]),

        new("out-of-memory", Diagnosis.Severity.Error,
            "The game ran out of memory",
            ["java.lang.OutOfMemoryError", "GC overhead limit exceeded", "Java heap space",
             "Could not reserve enough space"],
            "The JVM heap was exhausted. Large modpacks commonly need 6 to 8 GB, and a 32-bit "
            + "JVM cannot exceed roughly 1.5 GB at all.",
            ["Raise the maximum memory in Settings (start at 4096 MB).",
             "Confirm the runtime is 64-bit.",
             "Lower render distance or remove heavy mods and resource packs."]),

        new("missing-dependency", Diagnosis.Severity.Blocker,
            "A mod is missing a dependency",
            ["Missing or unsupported mandatory dependencies", "requires any version of",
             "NoClassDefFoundError: net/minecraftforge", "ModResolutionException",
             "MissingDependencyException"],
            "The loader found a mod that declares a dependency which is not installed, so it "
            + "aborted startup before Minecraft could begin.",
            ["Open Mods and check the dependency column for red entries.",
             "Install the missing mod at a matching version.",
             "Alternatively disable the mod that requires it."]),

        new("duplicate-mod", Diagnosis.Severity.Error,
            "Two copies of the same mod are installed",
            ["Duplicate mods", "duplicate mod id", "DuplicateModsFoundException",
             "already registered"],
            "Two jars declare the same mod id, which usually happens after copying a mod into "
            + "an instance twice or updating without deleting the old file.",
            ["Open Mods, sort by name, and remove the older duplicate.",
             "A .disabled file still counts if two enabled copies exist."]),

        new("loader-mismatch", Diagnosis.Severity.Blocker,
            "Mods or the loader do not match this Minecraft version",
            ["Incompatible mod set", "This mod requires Minecraft", "incompatible with current version",
             "Unsupported mod", "Mod file is for a different"],
            "A mod was built for another Minecraft or loader version, and the loader refused to "
            + "start rather than risk corrupting the world.",
            ["Check the mod page for a build matching this exact game version.",
             "Do not mix Forge and Fabric mods in one instance.",
             "Create a matching instance and install the right loader."]),

        new("graphics-driver", Diagnosis.Severity.Error,
            "The graphics driver rejected the OpenGL context",
            ["GLFW error", "Failed to create window", "Pixel format not accelerated",
             "Couldn't set pixel format", "EXCEPTION_ACCESS_VIOLATION"],
            "The GPU driver could not provide the OpenGL context this version requires. This is "
            + "a driver problem, not a launcher bug.",
            ["Update the driver from the vendor, not through Windows Update.",
             "On a laptop with two GPUs, force the game onto the dedicated one.",
             "Remote desktop sessions often cannot create a hardware context."]),

        new("natives-missing", Diagnosis.Severity.Error,
            "A native library is missing",
            ["UnsatisfiedLinkError", "error while loading shared libraries",
             "NoClassDefFoundError: Could not initialize", "libGL.so", "libX11"],
            "The game could not load a native library, typically because the file was not "
            + "extracted or a system package is absent.",
            ["Run Repair for this version so natives are extracted again.",
             "On Linux install the runtime packages the game needs.",
             "Confirm the architecture of the library matches your Java."]),

        new("assets-corrupt", Diagnosis.Severity.Error,
            "Game assets are incomplete or corrupt",
            ["Failed to download asset", "Unable to load asset", "Missing resource"],
            "One or more files under assets are missing or fail their hash check.",
            ["Run Repair for this version.",
             "If downloads keep failing, switch the download source in Settings."]),

        new("world-corrupt", Diagnosis.Severity.Error,
            "The world could not be loaded",
            ["Failed to load world", "Level is corrupt", "Chunk file", "EOFException",
             "region file"],
            "A region or level file is damaged, often after a crash during saving.",
            ["Restore the world from a backup.",
             "Try a repair tool before deleting anything.",
             "Never force-kill the game while it is saving."]),

        new("port-in-use", Diagnosis.Severity.Warning,
            "A local port is already in use",
            ["Address already in use", "Failed to bind", "BindException"],
            "Another process owns the port the game wants, often a second instance or a "
            + "leftover java process.",
            ["Close other Minecraft instances.",
             "End stray java processes, then relaunch."]),

        new("auth-failed", Diagnosis.Severity.Error,
            "Authentication was rejected",
            ["Invalid credentials", "Failed to verify username", "Invalid session",
             "InvalidCredentialsException", "401 Unauthorized"],
            "The session token was rejected by Mojang or by the third-party auth server.",
            ["Sign out and sign in again from Accounts.",
             "For a third-party server, confirm the authlib address is correct.",
             "Check whether the authentication service is having an outage."]),

        new("mixin-failed", Diagnosis.Severity.Warning,
            "A mixin could not be applied",
            ["mixin apply failed", "MixinApplyError", "InvalidInjectionException",
             "org.spongepowered.asm"],
            "A mod patches a class in a way the loader could not reconcile, usually because two "
            + "mods patch the same target.",
            ["Update the loader and the mods involved to matching builds.",
             "Remove recently added mods one at a time to find the conflict."]),

        new("jvm-flag", Diagnosis.Severity.Warning,
            "A custom JVM flag was rejected",
            ["Unrecognized option", "Could not create the Java Virtual Machine",
             "Error: Could not create"],
            "The JVM refused to start because a configured flag does not exist in the selected "
            + "Java version.",
            ["Reset the JVM arguments to the defaults in Settings.",
             "Remove flags added for a different Java version."]),

        new("mod-crash", Diagnosis.Severity.Error,
            "A mod threw an exception during startup",
            ["A mod crashed", "Exception in thread \"main\"", "Caused by: java.lang"],
            "A mod failed while initialising; the stack trace names its package in the lines "
            + "immediately above the exception.",
            ["Find the package name in the trace and disable that mod.",
             "Update the mod; if it still fails, report it with this log."]),
    ];

    /// <summary>Analyses raw log text and returns diagnoses ordered by severity.</summary>
    public static IReadOnlyList<Diagnosis> Analyze(string? logText)
    {
        List<string> lines = [];
        if (!string.IsNullOrWhiteSpace(logText))
        {
            foreach (string line in logText.Split('\n'))
            {
                string trimmed = line.TrimEnd('\r');
                if (!string.IsNullOrWhiteSpace(trimmed))
                {
                    lines.Add(trimmed);
                }
            }
        }

        if (lines.Count > MaxLines)
        {
            lines = lines[^MaxLines..];
        }

        List<Diagnosis> diagnoses = [];
        foreach (CrashRule rule in Rules)
        {
            string? evidence = rule.Match(lines);
            if (evidence is not null)
            {
                diagnoses.Add(new Diagnosis(rule.Level, rule.Title, rule.Explanation,
                    rule.Fixes, Trim(evidence)));
            }
        }

        diagnoses.Sort((left, right) => left.Level.CompareTo(right.Level));
        return diagnoses;
    }

    /// <summary>Analyses the launcher log plus the newest crash reports for an instance.</summary>
    public static IReadOnlyList<Diagnosis> AnalyzeDirectory(string gameDirectory)
    {
        System.Text.StringBuilder builder = new();
        builder.AppendLine(Log.Recent(600));

        string crashReports = Path.Combine(gameDirectory, "crash-reports");
        if (Directory.Exists(crashReports))
        {
            try
            {
                List<string> files = [.. Directory.EnumerateFiles(crashReports, "*.txt")
                    .OrderByDescending(File.GetLastWriteTimeUtc)
                    .Take(3)];
                foreach (string file in files)
                {
                    builder.AppendLine(SafeRead(file));
                }
            }
            catch (IOException)
            {
                // Unreadable crash folder: the launcher log is still worth analysing.
            }
        }

        string latest = Path.Combine(gameDirectory, "logs", "latest.log");
        if (File.Exists(latest))
        {
            string text = SafeRead(latest);
            builder.AppendLine(text.Length > MaxReportChars ? text[^MaxReportChars..] : text);
        }

        return Analyze(builder.ToString());
    }

    private static string SafeRead(string path)
    {
        try
        {
            return File.ReadAllText(path);
        }
        catch (IOException)
        {
            return "";
        }
    }

    private static string Trim(string line)
    {
        string value = line.Trim();
        return value.Length <= 220 ? value : value[..217] + "\u2026";
    }
}
