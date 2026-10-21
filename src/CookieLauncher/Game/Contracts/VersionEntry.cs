namespace CookieLauncher.Game.Contracts;

/// <summary>
/// A row in the version catalogue, shaped for binding rather than for parsing.
/// </summary>
/// <remarks>
/// The metadata model is deliberately separate from the UI shape: the parser deals with
/// Mojang's field names and rule objects, this type deals with what a row displays and with
/// state the parser has no opinion about, such as whether the version is already installed.
/// </remarks>
public sealed record VersionEntry(string Id, string Type, string ReleaseTime)
{
    /// <summary>Set by the page after checking the disk, drives the "Installed" badge.</summary>
    public bool Installed { get; init; }

    /// <summary>Secondary line, for example "Release · 2024-08-08".</summary>
    public string Meta => $"{DisplayType}  ·  {ShortDate}";

    public string DisplayType => Type switch
    {
        "release" => "Release",
        "snapshot" => "Snapshot",
        "old_beta" => "Old Beta",
        "old_alpha" => "Old Alpha",
        _ => Type,
    };

    public string ShortDate => ReleaseTime.Length >= 10 ? ReleaseTime[..10] : ReleaseTime;
}
