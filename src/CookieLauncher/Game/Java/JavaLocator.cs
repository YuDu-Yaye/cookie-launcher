using System.Diagnostics;
using System.IO;
using CookieLauncher.Core;

namespace CookieLauncher.Game.Java;

/// <summary>A detected Java runtime.</summary>
public sealed record JavaRuntime(string Executable, int Major, string Version, string Vendor, string Arch)
{
    public bool Supports(int required) => Major >= required;

    public bool Is64Bit => Arch is not ("x86" or "arm32");

    /// <summary>ASCII-only label, safe for a Windows console.</summary>
    public string PlainLabel => $"Java {Major} ({Vendor}, {Arch})";
}

/// <summary>
/// Finds Java runtimes: the one running the launcher, JAVA_HOME, PATH, the platform's
/// install roots, and the launcher's own managed runtimes.
/// </summary>
/// <remarks>
/// The lowest sufficient major version wins. Handing Java 21 to a 1.16.5 instance breaks
/// the old mods that expect removed JDK internals, so "newest" is the wrong policy for a
/// launcher that has to run a decade of versions.
/// </remarks>
public sealed class JavaLocator
{
    private const string RequiredProp = "java.home";

    private IReadOnlyList<JavaRuntime>? _cache;

    public IReadOnlyList<JavaRuntime> All => _cache ??= Scan();

    /// <summary>Forces a rescan on the next access.</summary>
    public void Refresh() => _cache = null;

    /// <summary>Best runtime for a required major version, or null when none qualifies.</summary>
    public JavaRuntime? Select(int required, string? preferredPath)
    {
        if (!string.IsNullOrWhiteSpace(preferredPath))
        {
            JavaRuntime? preferred = Inspect(preferredPath);
            if (preferred is not null && preferred.Supports(required))
            {
                return preferred;
            }
        }

        return All
            .Where(runtime => runtime.Supports(required) && runtime.Is64Bit)
            .OrderBy(runtime => runtime.Major)
            .FirstOrDefault();
    }

    private static IReadOnlyList<JavaRuntime> Scan()
    {
        HashSet<string> candidates = new(StringComparer.OrdinalIgnoreCase);
        string executableName = OperatingSystem.IsWindows() ? "java.exe" : "java";

        void TryAddBin(string? binDirectory)
        {
            if (string.IsNullOrWhiteSpace(binDirectory))
            {
                return;
            }
            string candidate = Path.Combine(binDirectory, executableName);
            if (File.Exists(candidate))
            {
                _ = candidates.Add(candidate);
            }
        }

        foreach (string root in SearchRoots())
        {
            CollectBinDirectories(root, candidates);
        }

        List<JavaRuntime> runtimes = [];
        HashSet<string> seen = new(StringComparer.OrdinalIgnoreCase);
        foreach (string candidate in candidates)
        {
            JavaRuntime? runtime = Inspect(candidate);
            if (runtime is not null && seen.Add(runtime.Executable))
            {
                runtimes.Add(runtime);
            }
        }

        // The runtime executing the launcher is always a candidate, and cheap to add.
        string? launcherHome = Environment.GetEnvironmentVariable("JAVA_HOME");
        TryAddBin(launcherHome is null ? null : Path.Combine(launcherHome, "bin"));

        runtimes.Sort((left, right) => left.Major != right.Major
            ? left.Major.CompareTo(right.Major)
            : string.CompareOrdinal(left.Executable, right.Executable));

        Log.Debug($"Detected {runtimes.Count} Java runtime(s)");
        return runtimes;
    }

    private static IEnumerable<string> SearchRoots()
    {
        yield return AppPaths.Runtimes;

        if (OperatingSystem.IsWindows())
        {
            yield return @"C:\Program Files\Java";
            yield return @"C:\Program Files\Eclipse Adoptium";
            yield return @"C:\Program Files\Microsoft";
            yield return @"C:\Program Files\Zulu";
            yield return @"C:\Program Files\BellSoft";
            yield return @"C:\Program Files\Amazon Corretto";
            yield return @"C:\Program Files (x86)\Java";

            string? localAppData = Environment.GetEnvironmentVariable("LOCALAPPDATA");
            if (!string.IsNullOrWhiteSpace(localAppData))
            {
                yield return Path.Combine(localAppData, "Programs", "Eclipse Adoptium");
            }
        }
        else if (OperatingSystem.IsMacOS())
        {
            yield return "/Library/Java/JavaVirtualMachines";
            yield return "/opt/homebrew/opt";
        }
        else
        {
            yield return "/usr/lib/jvm";
            yield return "/usr/java";
            yield return "/opt/java";
        }
    }

    private static void CollectBinDirectories(string root, HashSet<string> candidates)
    {
        if (!Directory.Exists(root))
        {
            return;
        }
        try
        {
            foreach (string bin in Directory.EnumerateDirectories(root, "bin",
                         SearchOption.AllDirectories))
            {
                string candidate = Path.Combine(bin,
                    OperatingSystem.IsWindows() ? "java.exe" : "java");
                if (File.Exists(candidate))
                {
                    _ = candidates.Add(candidate);
                }
            }
        }
        catch (IOException)
        {
            // Unreadable tree: skip it.
        }
        catch (UnauthorizedAccessException)
        {
            // Protected directory: skip it.
        }
    }

    /// <summary>Runs <c>java -version</c> and parses version, vendor and architecture.</summary>
    public static JavaRuntime? Inspect(string executable)
    {
        if (!File.Exists(executable))
        {
            return null;
        }

        try
        {
            using Process process = new()
            {
                StartInfo = new ProcessStartInfo(executable, "-version")
                {
                    RedirectStandardError = true,
                    RedirectStandardOutput = true,
                    UseShellExecute = false,
                    CreateNoWindow = true,
                },
            };
            process.Start();
            // java -version writes to stderr on every implementation.
            string output = process.StandardError.ReadToEnd() + process.StandardOutput.ReadToEnd();
            if (!process.WaitForExit(15000))
            {
                process.Kill(entireProcessTree: true);
                return null;
            }

            int major = ParseMajor(output);
            if (major <= 0)
            {
                return null;
            }

            return new JavaRuntime(executable, major, ParseVersion(output),
                ParseVendor(output), ParseArch(output));
        }
        catch (Exception error) when (error is IOException or UnauthorizedAccessException
                                          or InvalidOperationException)
        {
            return null;
        }
    }

    private static int ParseMajor(string output)
    {
        // Quoted version, e.g. "21.0.5" or "1.8.0_411".
        int firstQuote = output.IndexOf('"');
        if (firstQuote < 0)
        {
            return -1;
        }
        int secondQuote = output.IndexOf('"', firstQuote + 1);
        if (secondQuote < 0)
        {
            return -1;
        }

        string version = output[(firstQuote + 1)..secondQuote];
        string[] parts = version.Split('.', '_', '-', '+');
        if (parts.Length == 0 || !int.TryParse(parts[0], out int major))
        {
            return -1;
        }

        // Legacy scheme: 1.8.0 means Java 8.
        if (major == 1 && parts.Length > 1 && int.TryParse(parts[1], out int legacy))
        {
            return legacy;
        }
        return major;
    }

    private static string ParseVersion(string output)
    {
        int firstQuote = output.IndexOf('"');
        int secondQuote = firstQuote < 0 ? -1 : output.IndexOf('"', firstQuote + 1);
        return firstQuote >= 0 && secondQuote > firstQuote
            ? output[(firstQuote + 1)..secondQuote]
            : "";
    }

    private static string ParseVendor(string output)
    {
        string lower = output.ToLowerInvariant();
        if (lower.Contains("temurin") || lower.Contains("adoptium"))
        {
            return "Eclipse Temurin";
        }
        if (lower.Contains("zulu"))
        {
            return "Azul Zulu";
        }
        if (lower.Contains("graalvm"))
        {
            return "GraalVM";
        }
        if (lower.Contains("corretto") || lower.Contains("amazon"))
        {
            return "Amazon Corretto";
        }
        if (lower.Contains("liberica") || lower.Contains("bellsoft"))
        {
            return "BellSoft Liberica";
        }
        if (lower.Contains("microsoft"))
        {
            return "Microsoft";
        }
        if (lower.Contains("oracle"))
        {
            return "Oracle";
        }
        return lower.Contains("openjdk") ? "OpenJDK" : "Unknown";
    }

    private static string ParseArch(string output)
    {
        string lower = output.ToLowerInvariant();
        if (lower.Contains("aarch64") || lower.Contains("arm64"))
        {
            return "arm64";
        }
        if (lower.Contains("64-bit") || lower.Contains("x86_64") || lower.Contains("amd64"))
        {
            return "x64";
        }
        if (lower.Contains("32-bit") || lower.Contains("i386"))
        {
            return "x86";
        }
        return System.Runtime.InteropServices.RuntimeInformation.ProcessArchitecture switch
        {
            System.Runtime.InteropServices.Architecture.Arm64 => "arm64",
            System.Runtime.InteropServices.Architecture.X86 => "x86",
            _ => "x64",
        };
    }

    /// <summary>Unused placeholder kept so the property name is documented in one place.</summary>
    internal static string HomeProperty => RequiredProp;
}
