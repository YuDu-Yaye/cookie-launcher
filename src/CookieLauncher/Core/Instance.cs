using System.IO;

namespace CookieLauncher.Core;

/// <summary>One game instance: an installed version plus its own directory and overrides.</summary>
public sealed class Instance
{
    public string Id { get; set; } = "";
    public string Name { get; set; } = "";

    /// <summary>Installed version id, for example <c>1.21.1</c> or <c>1.21.1-fabric</c>.</summary>
    public string Version { get; set; } = "";

    public string Loader { get; set; } = "vanilla";
    public string LoaderVersion { get; set; } = "";
    public string GameDir { get; set; } = "";
    public string JavaPath { get; set; } = "";

    /// <summary>Zero means "inherit the global setting".</summary>
    public int MemoryMb { get; set; }

    public string JvmArgs { get; set; } = "";
    public long CreatedAt { get; set; } = DateTimeOffset.UtcNow.ToUnixTimeMilliseconds();
    public long LastPlayedAt { get; set; }
    public long PlayTimeMs { get; set; }
    public string Notes { get; set; } = "";
    public string LastExit { get; set; } = "";

    /// <summary>True when the loader is anything other than vanilla.</summary>
    public bool IsModded => !string.Equals(Loader, "vanilla", StringComparison.OrdinalIgnoreCase);

    /// <summary>Human label for the loader, for example "Fabric 0.16.5".</summary>
    public string LoaderLabel => IsModded
        ? (string.IsNullOrWhiteSpace(LoaderVersion) ? Loader : Loader + " " + LoaderVersion)
        : "Vanilla";

    /// <summary>Short description shown under the instance name.</summary>
    public string Subtitle()
    {
        string version = string.IsNullOrWhiteSpace(Version) ? "No version" : Version;
        return IsModded ? version + "  ·  " + LoaderLabel : version;
    }

    /// <summary>Directory this instance's game data lives in.</summary>
    public string ResolvedGameDir => string.IsNullOrWhiteSpace(GameDir)
        ? AppPaths.Instance(Id)
        : GameDir;

    public string ModsDirectory => Path.Combine(ResolvedGameDir, "mods");
}
