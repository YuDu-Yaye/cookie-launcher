using System.Net.Http;
using System.IO;
using System.IO.Compression;
using System.Text.Json;
using CookieLauncher.Core;
using CookieLauncher.Game.Download;
using CookieLauncher.Game.Meta;

namespace CookieLauncher.Game.Install;

/// <summary>
/// Installs and repairs Minecraft versions.
/// </summary>
/// <remarks>
/// <para>
/// The whole file plan is built before a byte moves. That makes progress accurate from the
/// first frame instead of creeping upward as each stage discovers more work, and it lets
/// already-verified files be excluded before any request is issued.
/// </para>
/// <para>
/// Assets are fetched in a second pass because the asset index is itself a download: its
/// contents are unknown until the index file has landed.
/// </para>
/// </remarks>
public sealed class GameInstaller
{
    private readonly string _mirror;
    private readonly int _workers;

    public GameInstaller(string? mirror, int workers = Downloader.DefaultWorkers)
    {
        _mirror = mirror ?? "auto";
        _workers = workers;
    }

    /// <summary>Installs a version and returns the id that was installed.</summary>
    public async Task<string> InstallAsync(
        string versionId,
        DownloadProgress progress,
        CancellationToken token = default)
    {
        progress.SetStatus("Fetching version metadata");
        VersionManifest? manifest = await MetaService.LoadManifestAsync(_mirror, token)
            .ConfigureAwait(false)
            ?? throw new IOException("Cannot reach the version catalogue");

        ManifestEntry entry = manifest.Find(versionId)
            ?? throw new IOException($"Version {versionId} does not exist");

        string versionDir = AppPaths.Version(versionId);
        Directory.CreateDirectory(versionDir);
        string jsonPath = Path.Combine(versionDir, versionId + ".json");

        if (!File.Exists(jsonPath))
        {
            string body = await FetchTextAsync(Mirror.Candidates(entry.Url, _mirror), token)
                .ConfigureAwait(false);
            await File.WriteAllTextAsync(jsonPath, body, token).ConfigureAwait(false);
        }

        VersionMeta meta = await ResolveAsync(versionId, token).ConfigureAwait(false);
        await InstallFilesAsync(meta, progress, token).ConfigureAwait(false);
        return versionId;
    }

    /// <summary>Verifies every file a version needs, re-fetching only what is broken.</summary>
    public async Task RepairAsync(
        string versionId,
        DownloadProgress progress,
        CancellationToken token = default)
    {
        VersionMeta meta = await ResolveAsync(versionId, token).ConfigureAwait(false);
        await InstallFilesAsync(meta, progress, token).ConfigureAwait(false);
    }

    private async Task InstallFilesAsync(
        VersionMeta meta, DownloadProgress progress, CancellationToken token)
    {
        List<RemoteFile> plan = [];
        List<Library> nativeLibraries = [];

        Artifact? client = meta.Client;
        if (client is not null && client.Usable)
        {
            plan.Add(RemoteFile.From(
                Path.Combine(AppPaths.Version(meta.Id), meta.Id + ".jar"),
                client.Sha1, client.Size, Mirror.Candidates(client.Url, _mirror)));
        }

        foreach (Library library in meta.Libraries)
        {
            if (!library.Allowed)
            {
                continue;
            }

            Artifact? artifact = library.Artifact();
            if (artifact is not null && artifact.Usable)
            {
                string relative = string.IsNullOrEmpty(artifact.Path) ? library.Path : artifact.Path;
                plan.Add(RemoteFile.From(
                    Path.Combine(AppPaths.Libraries, relative),
                    artifact.Sha1, artifact.Size, Mirror.Candidates(artifact.Url, _mirror)));
            }

            string? classifier = NativeClassifier(library);
            if (classifier is not null)
            {
                Artifact? natives = NativeArtifact(library, classifier);
                if (natives is not null && natives.Usable)
                {
                    string relative = string.IsNullOrEmpty(natives.Path)
                        ? library.Path
                        : natives.Path;
                    plan.Add(RemoteFile.From(
                        Path.Combine(AppPaths.Downloads, "natives", relative),
                        natives.Sha1, natives.Size, Mirror.Candidates(natives.Url, _mirror)));
                    nativeLibraries.Add(library);
                }
            }
        }

        if (meta.LoggingFile is not null && meta.LoggingFile.Usable
            && !string.IsNullOrEmpty(meta.LoggingName))
        {
            plan.Add(RemoteFile.From(
                Path.Combine(AppPaths.Logging, meta.LoggingName),
                meta.LoggingFile.Sha1, meta.LoggingFile.Size,
                Mirror.Candidates(meta.LoggingFile.Url, _mirror)));
        }

        string? indexPath = null;
        if (meta.AssetIndex is not null && meta.AssetIndex.Usable
            && !string.IsNullOrEmpty(meta.AssetIndexId))
        {
            indexPath = Path.Combine(AppPaths.AssetIndexes, meta.AssetIndexId + ".json");
            plan.Add(RemoteFile.From(indexPath, meta.AssetIndex.Sha1, meta.AssetIndex.Size,
                Mirror.Candidates(meta.AssetIndex.Url, _mirror)));
        }

        progress.SetStatus($"Preparing {meta.Id}: {plan.Count} files");
        using (Downloader downloader = new(_workers))
        {
            int failures = await downloader.RunAsync(plan, progress, token).ConfigureAwait(false);
            if (failures > 0)
            {
                throw new IOException($"{failures} file(s) failed; run install again to resume");
            }
        }

        // Assets need a second pass: the index file itself is one of the downloads above.
        if (indexPath is not null && File.Exists(indexPath))
        {
            List<RemoteFile> assets = BuildAssetPlan(indexPath);
            if (assets.Count > 0)
            {
                progress.SetStatus($"Downloading {assets.Count} assets");
                using Downloader downloader = new(_workers);
                int assetFailures = await downloader.RunAsync(assets, progress, token)
                    .ConfigureAwait(false);
                if (assetFailures > 0)
                {
                    Log.Warn($"{assetFailures} asset(s) missing; the game usually still starts");
                }
            }
        }

        ExtractNatives(meta, nativeLibraries, progress);
    }

    private List<RemoteFile> BuildAssetPlan(string indexPath)
    {
        List<RemoteFile> plan = [];
        using JsonDocument document = JsonDocument.Parse(File.ReadAllText(indexPath));
        if (!document.RootElement.TryGetProperty("objects", out JsonElement objects))
        {
            return plan;
        }

        foreach (JsonProperty entry in objects.EnumerateObject())
        {
            if (entry.Value.ValueKind != JsonValueKind.Object)
            {
                continue;
            }
            string hash = Artifact.Str(entry.Value, "hash");
            long size = Artifact.Num(entry.Value, "size");
            if (hash.Length < 2)
            {
                continue;
            }
            string url = "https://resources.download.minecraft.net/" + hash[..2] + "/" + hash;
            plan.Add(RemoteFile.From(
                Path.Combine(AppPaths.AssetObjects, hash[..2], hash),
                hash, size, Mirror.Candidates(url, _mirror)));
        }
        return plan;
    }

    private static void ExtractNatives(
        VersionMeta meta, List<Library> libraries, DownloadProgress progress)
    {
        if (libraries.Count == 0)
        {
            return;
        }

        string nativesDir = AppPaths.Natives(meta.Id);
        Directory.CreateDirectory(nativesDir);

        foreach (Library library in libraries)
        {
            string? classifier = NativeClassifier(library);
            Artifact? artifact = classifier is null ? null : NativeArtifact(library, classifier);
            if (artifact is null)
            {
                continue;
            }

            string jar = Path.Combine(AppPaths.Downloads, "natives",
                string.IsNullOrEmpty(artifact.Path) ? library.Path : artifact.Path);
            if (!File.Exists(jar))
            {
                continue;
            }

            progress.SetStatus("Extracting natives: " + library.Name);
            try
            {
                using ZipArchive archive = ZipFile.OpenRead(jar);
                foreach (ZipArchiveEntry item in archive.Entries)
                {
                    string lower = item.FullName.ToLowerInvariant();
                    if (lower.StartsWith("meta-inf/", StringComparison.Ordinal))
                    {
                        continue;
                    }
                    if (!(lower.EndsWith(".dll") || lower.EndsWith(".so")
                          || lower.EndsWith(".dylib") || lower.EndsWith(".jnilib")))
                    {
                        continue;
                    }
                    string target = Path.Combine(nativesDir, Path.GetFileName(item.FullName));
                    item.ExtractToFile(target, overwrite: true);
                }
            }
            catch (IOException error)
            {
                Log.Warn($"Cannot extract natives from {library.Name}: {error.Message}");
            }
        }
    }

    /// <summary>The native classifier for this platform, or null when there is none.</summary>
    private static string? NativeClassifier(Library library)
    {
        if (library.Natives.Count == 0)
        {
            return null;
        }
        string os = OperatingSystem.IsWindows() ? "windows"
            : OperatingSystem.IsMacOS() ? "osx"
            : "linux";
        if (!library.Natives.TryGetValue(os, out string? raw) || string.IsNullOrEmpty(raw))
        {
            return null;
        }
        return raw.Replace("${arch}", ProcessArch());
    }

    private static string ProcessArch() =>
        System.Runtime.InteropServices.RuntimeInformation.ProcessArchitecture switch
        {
            System.Runtime.InteropServices.Architecture.X86 => "32",
            System.Runtime.InteropServices.Architecture.Arm64 => "arm64",
            _ => "64",
        };

    private static Artifact? NativeArtifact(Library library, string classifier)
    {
        if (library.Downloads.TryGetValue(classifier, out Artifact? direct) && direct.Usable)
        {
            return direct;
        }
        if (library.Downloads.TryGetValue("classifiers." + classifier, out Artifact? keyed)
            && keyed.Usable)
        {
            return keyed;
        }
        if (string.IsNullOrWhiteSpace(library.BaseUrl))
        {
            return null;
        }
        string[] coords = library.Coordinates();
        string path = coords[0].Replace('.', '/') + "/" + coords[1] + "/" + coords[2] + "/"
            + coords[1] + "-" + coords[2] + "-" + classifier + ".jar";
        string baseUrl = library.BaseUrl.EndsWith('/') ? library.BaseUrl : library.BaseUrl + "/";
        return new Artifact(path, baseUrl + path, "", -1);
    }

    /// <summary>Loads a version and resolves its full inheritance chain.</summary>
    public static async Task<VersionMeta> ResolveAsync(
        string versionId, CancellationToken token = default)
    {
        return await ResolveAsync(versionId, [], token).ConfigureAwait(false);
    }

    private static async Task<VersionMeta> ResolveAsync(
        string versionId, HashSet<string> visited, CancellationToken token)
    {
        if (!visited.Add(versionId))
        {
            throw new IOException("Circular inheritsFrom chain at " + versionId);
        }

        string path = Path.Combine(AppPaths.Version(versionId), versionId + ".json");
        if (!File.Exists(path))
        {
            throw new IOException($"Version {versionId} is not installed");
        }

        string json = await File.ReadAllTextAsync(path, token).ConfigureAwait(false);
        using JsonDocument document = JsonDocument.Parse(json);
        VersionMeta meta = VersionMeta.Parse(document.RootElement, versionId);

        if (!string.IsNullOrEmpty(meta.InheritsFrom))
        {
            VersionMeta parent = await ResolveAsync(meta.InheritsFrom, visited, token)
                .ConfigureAwait(false);
            meta.MergeFrom(parent);
        }

        meta.Id = versionId;
        return meta;
    }

    private static async Task<string> FetchTextAsync(
        IReadOnlyList<string> urls, CancellationToken token)
    {
        Exception? last = null;
        using HttpClient client = new() { Timeout = TimeSpan.FromMinutes(2) };
        client.DefaultRequestHeaders.UserAgent.ParseAdd(AppInfo.UserAgent);
        foreach (string url in urls)
        {
            try
            {
                return await client.GetStringAsync(url, token).ConfigureAwait(false);
            }
            catch (Exception error) when (error is HttpRequestException or TaskCanceledException)
            {
                last = error;
            }
        }
        throw new IOException("Cannot fetch version metadata", last);
    }

    public static bool IsInstalled(string? versionId) =>
        !string.IsNullOrWhiteSpace(versionId)
        && File.Exists(Path.Combine(AppPaths.Version(versionId), versionId + ".json"));

    public static IReadOnlyList<string> InstalledVersions()
    {
        if (!Directory.Exists(AppPaths.Versions))
        {
            return [];
        }
        try
        {
            return Directory.EnumerateDirectories(AppPaths.Versions)
                .Select(Path.GetFileName)
                .Where(name => !string.IsNullOrEmpty(name)
                    && File.Exists(Path.Combine(AppPaths.Versions, name, name + ".json")))
                .Select(name => name!)
                .OrderBy(name => name, StringComparer.OrdinalIgnoreCase)
                .ToList();
        }
        catch (IOException)
        {
            return [];
        }
    }
}
