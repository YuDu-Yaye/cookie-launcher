using System.IO;
using CookieLauncher.Core;

namespace CookieLauncher.Game.Install;

/// <summary>
/// Queries and verifies installed versions.
/// </summary>
/// <remarks>
/// A version counts as installed when its metadata JSON exists, not when its jar does: the
/// JSON is the first thing written and the last thing that should ever be missing, so it is
/// the honest marker for "this version was installed here".
/// </remarks>
public static class GameInstaller
{
    public static bool IsInstalled(string? versionId) =>
        !string.IsNullOrWhiteSpace(versionId)
        && File.Exists(Path.Combine(AppPaths.Version(versionId), versionId + ".json"));

    /// <summary>Installed version ids, sorted by name.</summary>
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
                .Where(name => name is not null
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
