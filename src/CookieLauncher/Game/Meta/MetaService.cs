using System.IO;
using System.Net.Http;
using System.Text.Json;
using CookieLauncher.Core;

namespace CookieLauncher.Game.Meta;

/// <summary>One published version and where its metadata lives.</summary>
public sealed record ManifestEntry(string Id, string Type, string Url, string Sha1, string ReleaseTime);

/// <summary>Mojang's version catalogue.</summary>
public sealed class VersionManifest
{
    public string LatestRelease { get; init; } = "";
    public string LatestSnapshot { get; init; } = "";
    public IReadOnlyList<ManifestEntry> Versions { get; init; } = [];

    public ManifestEntry? Find(string? id) =>
        id is null ? null : Versions.FirstOrDefault(v => v.Id == id);
}

/// <summary>
/// Fetches Mojang metadata with mirror failover and an on-disk cache.
/// </summary>
/// <remarks>
/// The manifest is cached for an hour. Opening the version page twice should not cost two
/// round trips, and the cache is what makes a cold page open feel instant on a slow link.
/// </remarks>
public static class MetaService
{
    public const string OfficialManifestUrl =
        "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";

    private static readonly TimeSpan CacheLifetime = TimeSpan.FromHours(1);
    private static readonly HttpClient Client = CreateClient();

    private static HttpClient CreateClient()
    {
        HttpClient client = new() { Timeout = TimeSpan.FromMinutes(2) };
        client.DefaultRequestHeaders.UserAgent.ParseAdd(AppInfo.UserAgent);
        client.DefaultRequestHeaders.AcceptEncoding.ParseAdd("identity");
        return client;
    }

    /// <summary>Returns the manifest, preferring a cache younger than an hour.</summary>
    public static async Task<VersionManifest?> LoadManifestAsync(string? mirror, CancellationToken token = default)
    {
        string cachePath = Path.Combine(AppPaths.Cache, "version_manifest_v2.json");
        if (IsFresh(cachePath))
        {
            VersionManifest? cached = TryParse(File.ReadAllText(cachePath));
            if (cached is not null)
            {
                return cached;
            }
        }

        foreach (string url in Mirror.Candidates(OfficialManifestUrl, mirror))
        {
            try
            {
                string body = await Client.GetStringAsync(url, token).ConfigureAwait(false);
                Directory.CreateDirectory(AppPaths.Cache);
                await File.WriteAllTextAsync(cachePath, body, token).ConfigureAwait(false);
                VersionManifest? manifest = TryParse(body);
                if (manifest is not null)
                {
                    return manifest;
                }
            }
            catch (Exception error) when (error is HttpRequestException or TaskCanceledException)
            {
                Log.Warn("Manifest fetch failed from " + url + ": " + error.Message);
            }
        }

        // Last resort: an expired cache is better than an empty page.
        if (File.Exists(cachePath))
        {
            return TryParse(File.ReadAllText(cachePath));
        }
        return null;
    }

    private static bool IsFresh(string path)
    {
        if (!File.Exists(path))
        {
            return false;
        }
        return DateTime.UtcNow - File.GetLastWriteTimeUtc(path) < CacheLifetime;
    }

    private static VersionManifest? TryParse(string json)
    {
        try
        {
            using JsonDocument document = JsonDocument.Parse(json);
            JsonElement root = document.RootElement;

            string release = "";
            string snapshot = "";
            if (root.TryGetProperty("latest", out JsonElement latest))
            {
                release = latest.TryGetProperty("release", out JsonElement r) ? r.GetString() ?? "" : "";
                snapshot = latest.TryGetProperty("snapshot", out JsonElement s) ? s.GetString() ?? "" : "";
            }

            List<ManifestEntry> versions = [];
            if (root.TryGetProperty("versions", out JsonElement array))
            {
                foreach (JsonElement item in array.EnumerateArray())
                {
                    string id = item.TryGetProperty("id", out JsonElement i) ? i.GetString() ?? "" : "";
                    string type = item.TryGetProperty("type", out JsonElement t) ? t.GetString() ?? "" : "";
                    string url = item.TryGetProperty("url", out JsonElement u) ? u.GetString() ?? "" : "";
                    string sha1 = item.TryGetProperty("sha1", out JsonElement h) ? h.GetString() ?? "" : "";
                    string time = item.TryGetProperty("releaseTime", out JsonElement rt)
                        ? rt.GetString() ?? "" : "";
                    if (!string.IsNullOrWhiteSpace(id) && !string.IsNullOrWhiteSpace(url))
                    {
                        versions.Add(new ManifestEntry(id, type, url, sha1, time));
                    }
                }
            }

            return new VersionManifest
            {
                LatestRelease = release,
                LatestSnapshot = snapshot,
                Versions = versions,
            };
        }
        catch (JsonException error)
        {
            Log.Warn("Manifest is not valid JSON: " + error.Message);
            return null;
        }
    }
}

/// <summary>Mirror policy: official CDN, BMCLAPI, or official-then-mirror.</summary>
public static class Mirror
{
    private static readonly (string Official, string Mirror)[] Prefixes =
    [
        ("https://piston-meta.mojang.com", "https://bmclapi2.bangbang93.com"),
        ("https://launchermeta.mojang.com", "https://bmclapi2.bangbang93.com"),
        ("https://launcher.mojang.com", "https://bmclapi2.bangbang93.com"),
        ("https://piston-data.mojang.com", "https://bmclapi2.bangbang93.com"),
        ("https://resources.download.minecraft.net", "https://bmclapi2.bangbang93.com/assets"),
        ("https://libraries.minecraft.net", "https://bmclapi2.bangbang93.com/maven"),
        ("https://maven.minecraftforge.net", "https://bmclapi2.bangbang93.com/maven"),
        ("https://files.minecraftforge.net/maven", "https://bmclapi2.bangbang93.com/maven"),
        ("https://maven.neoforged.net/releases", "https://bmclapi2.bangbang93.com/maven"),
        ("https://maven.fabricmc.net", "https://bmclapi2.bangbang93.com/maven"),
        ("https://meta.fabricmc.net", "https://bmclapi2.bangbang93.com/fabric-meta"),
        ("https://meta.quiltmc.org", "https://bmclapi2.bangbang93.com/quilt-meta"),
    ];

    /// <summary>Rewrites an official URL onto the mirror when it is a known host.</summary>
    public static string ToMirror(string url)
    {
        if (string.IsNullOrWhiteSpace(url))
        {
            return url;
        }
        foreach ((string official, string mirror) in Prefixes)
        {
            if (url.StartsWith(official, StringComparison.OrdinalIgnoreCase))
            {
                return mirror + url[official.Length..];
            }
        }
        return url;
    }

    /// <summary>Ordered candidate URLs for one file under the given policy.</summary>
    public static IReadOnlyList<string> Candidates(string? officialUrl, string? policy)
    {
        if (string.IsNullOrWhiteSpace(officialUrl))
        {
            return [];
        }
        string normalized = (policy ?? "auto").Trim().ToLowerInvariant();
        string mirrored = ToMirror(officialUrl);
        bool mirrorDiffers = !string.Equals(mirrored, officialUrl, StringComparison.Ordinal);

        return normalized switch
        {
            "official" or "mojang" => [officialUrl],
            "mirror" or "bmclapi" => mirrorDiffers ? [mirrored, officialUrl] : [officialUrl],
            _ => mirrorDiffers ? [officialUrl, mirrored] : [officialUrl],
        };
    }
}
