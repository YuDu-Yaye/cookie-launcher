using System.Text.Json.Serialization;

namespace CookieLauncher.Core;

/// <summary>User settings, persisted as JSON next to the instances.</summary>
/// <remarks>
/// Every property has a default that is safe on a machine the launcher has never seen.
/// Loading merges onto a fresh instance, so a config written by an older build keeps
/// working: unknown keys are ignored and missing keys keep their default.
/// </remarks>
public sealed class Config
{
    public string JavaPath { get; set; } = "";

    public int MinMemoryMb { get; set; } = 512;

    public int MaxMemoryMb { get; set; } = RecommendedMemoryMb();

    public string JvmArgs { get; set; } =
        "-XX:+UseG1GC -XX:+ParallelRefProcEnabled -XX:MaxGCPauseMillis=200 "
        + "-XX:+UnlockExperimentalVMOptions -XX:+DisableExplicitGC -XX:G1NewSizePercent=20 "
        + "-XX:G1ReservePercent=20 -XX:G1HeapRegionSize=16M";

    public string GameArgs { get; set; } = "";

    public int WindowWidth { get; set; } = 1280;

    public int WindowHeight { get; set; } = 720;

    public bool Fullscreen { get; set; }

    /// <summary>Mirror policy: auto, official or mirror.</summary>
    public string Mirror { get; set; } = "auto";

    public int DownloadWorkers { get; set; } = 12;

    public string Proxy { get; set; } = "";

    /// <summary>Catalogue id from <c>ThemeCatalogue</c>.</summary>
    public string ThemeId { get; set; } = "blue-dark";

    public bool ShowSnapshots { get; set; }

    public bool ShowOldVersions { get; set; }

    public string LastInstanceId { get; set; } = "";

    /// <summary>Microsoft Entra application id; empty means sign-in is unavailable.</summary>
    public string MsaClientId { get; set; } = "";

    [JsonIgnore]
    public string FilePath { get; set; } = "";

    public static Config Load(string path)
    {
        Config config = Json.Read<Config>(path) ?? new Config();
        config.FilePath = path;
        config.Normalize();
        return config;
    }

    public void Save()
    {
        Normalize();
        Json.Write(FilePath, this);
    }

    /// <summary>Clamps values into the ranges the rest of the launcher relies on.</summary>
    public void Normalize()
    {
        MaxMemoryMb = Math.Clamp(MaxMemoryMb, 512, 65536);
        MinMemoryMb = Math.Clamp(MinMemoryMb, 256, MaxMemoryMb);
        MaxMemoryMb = Math.Max(MinMemoryMb, MaxMemoryMb);
        WindowWidth = Math.Clamp(WindowWidth, 854, 7680);
        WindowHeight = Math.Clamp(WindowHeight, 480, 4320);
        DownloadWorkers = Math.Clamp(DownloadWorkers, 1, 32);

        JavaPath ??= "";
        JvmArgs ??= "";
        GameArgs ??= "";
        Proxy ??= "";
        LastInstanceId ??= "";
        MsaClientId ??= "";

        if (string.IsNullOrWhiteSpace(Mirror))
        {
            Mirror = "auto";
        }
        if (string.IsNullOrWhiteSpace(ThemeId))
        {
            ThemeId = "blue-dark";
        }
    }

    /// <summary>
    /// A memory ceiling that leaves the operating system room to breathe.
    /// </summary>
    /// <remarks>
    /// Half of physical memory, capped at 16 GB and floored at 2 GB, rounded down to a
    /// 512 MB step so the number looks deliberate. Setting the ceiling to total RAM is the
    /// classic mistake: the game allocates off-heap buffers on top of the heap.
    /// </remarks>
    public static int RecommendedMemoryMb()
    {
        long totalMb;
        try
        {
            totalMb = GC.GetGCMemoryInfo().TotalAvailableMemoryBytes / (1024L * 1024L);
        }
        catch
        {
            return 4096;
        }
        if (totalMb <= 0)
        {
            return 4096;
        }
        long half = totalMb / 2;
        long capped = Math.Min(half, 16384);
        long stepped = (capped / 512) * 512;
        return (int)Math.Max(2048, stepped);
    }
}
