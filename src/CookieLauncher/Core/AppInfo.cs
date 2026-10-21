using System.Diagnostics;
using System.IO;
using System.Text.Json;
using System.Text.Json.Serialization;

namespace CookieLauncher.Core;

/// <summary>Application identity, kept in one place so nothing hardcodes a version.</summary>
public static class AppInfo
{
    public const string Name = "Cookie Launcher";
    public const string Version = "1.0.0";
    public const string Repository = "https://github.com/YuDu-Yaye/cookie-launcher";
    public const string License = "GPL-3.0-only";

    public const string UserAgent =
        "CookieLauncher/" + Version + " (+" + Repository + ")";
}

/// <summary>Every on-disk location the launcher uses.</summary>
/// <remarks>
/// <para>
/// Layout mirrors Mojang's own (<c>versions/</c>, <c>libraries/</c>, <c>assets/</c>) so a
/// shared <c>.minecraft</c> directory can be adopted without duplicating gigabytes.
/// </para>
/// <para>
/// Resolution order: an explicit <c>--home</c> override, a <c>.portable</c> marker next
/// to the executable, then the per-user application data folder. Portable mode is a
/// first-class feature because users move installations between drives.
/// </para>
/// </remarks>
public static class AppPaths
{
    private static string? _root;

    /// <summary>Directory that contains the running executable.</summary>
    public static string ExecutableDirectory
    {
        get
        {
            string? path = Environment.ProcessPath;
            if (string.IsNullOrEmpty(path))
            {
                return AppContext.BaseDirectory;
            }
            return Path.GetDirectoryName(path) ?? AppContext.BaseDirectory;
        }
    }

    public static string Root
    {
        get
        {
            _root ??= ResolveRoot();
            return _root;
        }
    }

    /// <summary>Overrides the data directory, used by <c>--home</c> and by tests.</summary>
    public static void SetRoot(string path)
    {
        _root = Path.GetFullPath(path);
        Directory.CreateDirectory(_root);
    }

    private static string ResolveRoot()
    {
        string beside = ExecutableDirectory;
        if (File.Exists(Path.Combine(beside, ".portable")))
        {
            return Path.Combine(beside, "data");
        }
        return Path.Combine(
            Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData),
            "CookieLauncher");
    }

    public static bool IsPortable =>
        File.Exists(Path.Combine(ExecutableDirectory, ".portable"));

    // Game data, Mojang-compatible.
    public static string Versions => Path.Combine(Root, "versions");
    public static string Version(string id) => Path.Combine(Versions, id);
    public static string Libraries => Path.Combine(Root, "libraries");
    public static string Assets => Path.Combine(Root, "assets");
    public static string AssetIndexes => Path.Combine(Assets, "indexes");
    public static string AssetObjects => Path.Combine(Assets, "objects");
    public static string Natives(string versionId) => Path.Combine(Root, "natives", versionId);

    // Launcher state.
    public static string Instances => Path.Combine(Root, "instances");
    public static string Instance(string id) => Path.Combine(Instances, id);
    public static string ConfigFile => Path.Combine(Root, "config.json");
    public static string InstancesFile => Path.Combine(Root, "instances.json");
    public static string AccountsFile => Path.Combine(Root, "accounts.json");
    public static string Cache => Path.Combine(Root, "cache");
    public static string Downloads => Path.Combine(Cache, "downloads");
    public static string Installers => Path.Combine(Cache, "installers");
    public static string Logging => Path.Combine(Cache, "logging");
    public static string Runtimes => Path.Combine(Root, "runtimes");
    public static string Logs => Path.Combine(Root, "logs");
    public static string LauncherLog => Path.Combine(Logs, "launcher.log");

    /// <summary>Creates every directory the launcher writes into.</summary>
    public static void EnsureDirectories()
    {
        foreach (string directory in new[]
                 {
                     Versions, Libraries, Assets, AssetIndexes, AssetObjects,
                     Instances, Cache, Downloads, Installers, Logging, Runtimes, Logs,
                 })
        {
            Directory.CreateDirectory(directory);
        }
    }
}

/// <summary>Shared JSON options so every file is written identically.</summary>
public static class Json
{
    public static readonly JsonSerializerOptions Pretty = new()
    {
        WriteIndented = true,
        DefaultIgnoreCondition = JsonIgnoreCondition.Never,
        PropertyNamingPolicy = JsonNamingPolicy.CamelCase,
    };

    public static readonly JsonSerializerOptions Compact = new()
    {
        WriteIndented = false,
        PropertyNamingPolicy = JsonNamingPolicy.CamelCase,
    };

    public static T? Read<T>(string path) where T : class
    {
        try
        {
            if (!File.Exists(path))
            {
                return null;
            }
            return JsonSerializer.Deserialize<T>(File.ReadAllText(path), Pretty);
        }
        catch (Exception error) when (error is IOException or JsonException)
        {
            Log.Warn($"Cannot read {Path.GetFileName(path)}: {error.Message}");
            return null;
        }
    }

    /// <summary>
    /// Writes through a temporary file, then replaces the target. A crash during the write
    /// can therefore never leave a half-written config behind.
    /// </summary>
    public static void Write<T>(string path, T value)
    {
        string? directory = Path.GetDirectoryName(path);
        if (!string.IsNullOrEmpty(directory))
        {
            Directory.CreateDirectory(directory);
        }
        string temp = path + ".tmp";
        File.WriteAllText(temp, JsonSerializer.Serialize(value, Pretty));
        File.Move(temp, path, overwrite: true);
    }
}

/// <summary>
/// Process-wide logger with a bounded in-memory history.
/// </summary>
/// <remarks>
/// The log page renders the history; the launch pipeline appends to it. The ring buffer
/// keeps memory flat even when a modded game spews tens of thousands of lines.
/// </remarks>
public static class Log
{
    private const int Capacity = 5000;
    private static readonly Lock Gate = new();
    private static readonly Queue<string> History = new(Capacity);
    private static readonly List<Action<string>> Listeners = [];

    public static string? FilePath { get; set; }
    public static bool DebugEnabled { get; set; }

    public static void AddListener(Action<string> listener)
    {
        lock (Gate)
        {
            Listeners.Add(listener);
        }
    }

    public static void RemoveListener(Action<string> listener)
    {
        lock (Gate)
        {
            Listeners.Remove(listener);
        }
    }

    public static void Info(string message) => Write("INFO", message);
    public static void Warn(string message) => Write("WARN", message);
    public static void Error(string message) => Write("ERROR", message);

    public static void Error(string message, Exception error) =>
        Write("ERROR", message + Environment.NewLine + error);

    public static void Debug(string message)
    {
        if (DebugEnabled)
        {
            Write("DEBUG", message);
        }
    }

    /// <summary>Appends a line the game produced, already classified.</summary>
    public static void GameLine(string line) => Write("GAME", line);

    private static void Write(string level, string message)
    {
        string line = $"[{DateTime.Now:HH:mm:ss.fff}] [{level}] {message}";
        List<Action<string>> listeners;
        lock (Gate)
        {
            History.Enqueue(line);
            while (History.Count > Capacity)
            {
                History.Dequeue();
            }
            listeners = [.. Listeners];
        }

        foreach (Action<string> listener in listeners)
        {
            try
            {
                listener(line);
            }
            catch
            {
                // A broken consumer must never break logging.
            }
        }

        string? path = FilePath;
        if (path is null)
        {
            return;
        }
        try
        {
            Directory.CreateDirectory(Path.GetDirectoryName(path)!);
            File.AppendAllText(path, line + Environment.NewLine);
        }
        catch (IOException)
        {
            // Disk full or locked: the in-memory history still works.
        }
    }

    public static IReadOnlyList<string> Snapshot()
    {
        lock (Gate)
        {
            return [.. History];
        }
    }

    public static string Recent(int count)
    {
        IReadOnlyList<string> all = Snapshot();
        int from = Math.Max(0, all.Count - count);
        return string.Join(Environment.NewLine, all.Skip(from));
    }
}

/// <summary>Handy shell operations that fail softly.</summary>
public static class Shell
{
    /// <summary>Opens a folder or file in the platform file manager.</summary>
    public static void Open(string path)
    {
        try
        {
            Process.Start(new ProcessStartInfo(path) { UseShellExecute = true });
        }
        catch (Exception error)
        {
            Log.Warn($"Cannot open {path}: {error.Message}");
        }
    }
}
