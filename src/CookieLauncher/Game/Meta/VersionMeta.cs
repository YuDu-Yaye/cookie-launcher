using System.Text.Json;

namespace CookieLauncher.Game.Meta;

/// <summary>A downloadable artifact described by Mojang metadata.</summary>
public sealed record Artifact(string Path, string Url, string Sha1, long Size)
{
    public bool Usable => !string.IsNullOrWhiteSpace(Url);

    public static Artifact? Parse(JsonElement parent, string key)
    {
        if (!parent.TryGetProperty(key, out JsonElement node) || node.ValueKind != JsonValueKind.Object)
        {
            return null;
        }
        return new Artifact(
            Str(node, "path"),
            Str(node, "url"),
            Str(node, "sha1"),
            Num(node, "size"));
    }

    internal static string Str(JsonElement parent, string name) =>
        parent.TryGetProperty(name, out JsonElement value) && value.ValueKind == JsonValueKind.String
            ? value.GetString() ?? ""
            : "";

    internal static long Num(JsonElement parent, string name) =>
        parent.TryGetProperty(name, out JsonElement value) && value.ValueKind == JsonValueKind.Number
            && value.TryGetInt64(out long number) ? number : -1;
}

/// <summary>
/// A Maven-style library, including native classifiers and platform rules.
/// </summary>
/// <remarks>
/// Modern metadata lists explicit <c>downloads.artifact</c> entries; older metadata only
/// gives a base <c>url</c>, in which case the artifact path is derived from the Maven
/// coordinates. Both generations are supported because the catalogue reaches back to 2011.
/// </remarks>
public sealed class Library
{
    public string Name { get; init; } = "";
    public string BaseUrl { get; init; } = "";
    public IReadOnlyDictionary<string, Artifact> Downloads { get; init; } =
        new Dictionary<string, Artifact>();
    public IReadOnlyDictionary<string, string> Natives { get; init; } =
        new Dictionary<string, string>();
    /// <summary>
    /// Rules flattened at parse time, not stored as <c>JsonElement</c>.
    /// </summary>
    /// <remarks>
    /// A JsonElement is a view into its owning JsonDocument. Once that document is
    /// disposed, every access throws ObjectDisposedException - which is exactly what
    /// happened when the installer evaluated library rules after parsing completed.
    /// The values are therefore copied into plain records while the document is open.
    /// </remarks>
    public IReadOnlyList<RuleEntry> Rules { get; init; } = [];

    public string[] Coordinates()
    {
        string[] parts = Name.Split(':');
        return
        [
            parts.Length > 0 ? parts[0] : "",
            parts.Length > 1 ? parts[1] : "",
            parts.Length > 2 ? parts[2] : "",
            parts.Length > 3 ? parts[3] : "",
        ];
    }

    /// <summary>Relative path inside the libraries directory.</summary>
    public string Path
    {
        get
        {
            string[] coords = Coordinates();
            string name = coords[1] + "-" + coords[2]
                + (string.IsNullOrEmpty(coords[3]) ? "" : "-" + coords[3]) + ".jar";
            return coords[0].Replace('.', '/') + "/" + coords[1] + "/" + coords[2] + "/" + name;
        }
    }

    public Artifact? Artifact()
    {
        if (Downloads.TryGetValue("artifact", out Artifact? direct) && direct.Usable)
        {
            return direct;
        }
        if (string.IsNullOrWhiteSpace(BaseUrl))
        {
            return null;
        }
        string baseUrl = BaseUrl.EndsWith('/') ? BaseUrl : BaseUrl + "/";
        return new Artifact(Path, baseUrl + Path, "", -1);
    }

    public bool Allowed => Rule.Allows(Rules);
}

/// <summary>One rule, flattened out of JSON so it stays valid after the document closes.</summary>
public sealed record RuleEntry(
    bool Disallow,
    string OsName,
    string OsArch,
    IReadOnlyList<string> RequiredFeatures);

/// <summary>Evaluates Mojang's rule lists against the current platform.</summary>
public static class Rule
{
    /// <summary>Reads a rule array into plain records while the document is still open.</summary>
    public static IReadOnlyList<RuleEntry> Parse(JsonElement rulesArray)
    {
        List<RuleEntry> entries = [];
        if (rulesArray.ValueKind != JsonValueKind.Array)
        {
            return entries;
        }

        foreach (JsonElement rule in rulesArray.EnumerateArray())
        {
            bool disallow = Artifact.Str(rule, "action") == "disallow";
            string osName = "";
            string osArch = "";
            if (rule.TryGetProperty("os", out JsonElement os))
            {
                osName = Artifact.Str(os, "name");
                osArch = Artifact.Str(os, "arch");
            }

            List<string> features = [];
            if (rule.TryGetProperty("features", out JsonElement featureNode)
                && featureNode.ValueKind == JsonValueKind.Object)
            {
                foreach (JsonProperty feature in featureNode.EnumerateObject())
                {
                    if (feature.Value.ValueKind == JsonValueKind.True)
                    {
                        features.Add(feature.Name);
                    }
                }
            }

            entries.Add(new RuleEntry(disallow, osName, osArch, features));
        }
        return entries;
    }

    public static bool Allows(IReadOnlyList<RuleEntry> rules)
    {
        if (rules.Count == 0)
        {
            return true;
        }

        // Last matching rule wins, which is how Mojang's evaluator behaves.
        bool allowed = false;
        foreach (RuleEntry rule in rules)
        {
            if (Matches(rule))
            {
                allowed = !rule.Disallow;
            }
        }
        return allowed;
    }

    private static bool Matches(RuleEntry rule)
    {
        if (!string.IsNullOrEmpty(rule.OsName)
            && !rule.OsName.Equals(OperatingSystemName(), StringComparison.OrdinalIgnoreCase))
        {
            return false;
        }

        if (!string.IsNullOrEmpty(rule.OsArch)
            && !rule.OsArch.Equals(CurrentArch(), StringComparison.OrdinalIgnoreCase))
        {
            return false;
        }

        // Demo mode, quick play and custom resolution are all off during an install, so a
        // rule that requires a feature never matches.
        return rule.RequiredFeatures.Count == 0;
    }

    private static string OperatingSystemName() =>
        OperatingSystem.IsWindows() ? "windows"
        : OperatingSystem.IsMacOS() ? "osx"
        : "linux";

    private static string CurrentArch() =>
        System.Runtime.InteropServices.RuntimeInformation.ProcessArchitecture switch
        {
            System.Runtime.InteropServices.Architecture.X86 => "x86",
            System.Runtime.InteropServices.Architecture.Arm64 => "arm64",
            _ => "x86_64",
        };
}

/// <summary>A parsed version, with its <c>inheritsFrom</c> chain already resolved.</summary>
public sealed class VersionMeta
{
    public string Id { get; set; } = "";
    public string Type { get; set; } = "release";
    public string MainClass { get; set; } = "";
    public string Assets { get; set; } = "";
    public string InheritsFrom { get; set; } = "";
    public int JavaMajor { get; set; } = 8;
    public bool JavaMajorExplicit { get; set; }
    public IReadOnlyDictionary<string, Artifact> Downloads { get; set; } =
        new Dictionary<string, Artifact>();
    public List<Library> Libraries { get; set; } = [];
    public Artifact? AssetIndex { get; set; }
    public string AssetIndexId { get; set; } = "";
    public Artifact? LoggingFile { get; set; }
    public string LoggingArgument { get; set; } = "";
    public string LoggingName { get; set; } = "";

    public Artifact? Client => Downloads.TryGetValue("client", out Artifact? client) ? client : null;

    public static VersionMeta Parse(JsonElement root, string fallbackId)
    {
        string id = Artifact.Str(root, "id");
        List<Library> libraries = [];
        if (root.TryGetProperty("libraries", out JsonElement libs)
            && libs.ValueKind == JsonValueKind.Array)
        {
            foreach (JsonElement item in libs.EnumerateArray())
            {
                libraries.Add(ParseLibrary(item));
            }
        }

        Dictionary<string, Artifact> downloads = [];
        if (root.TryGetProperty("downloads", out JsonElement dl)
            && dl.ValueKind == JsonValueKind.Object)
        {
            foreach (JsonProperty entry in dl.EnumerateObject())
            {
                Artifact? artifact = Artifact.Parse(dl, entry.Name);
                if (artifact is not null)
                {
                    downloads[entry.Name] = artifact;
                }
            }
        }

        VersionMeta meta = new()
        {
            Id = string.IsNullOrEmpty(id) ? fallbackId : id,
            Type = Artifact.Str(root, "type") is { Length: > 0 } type ? type : "release",
            MainClass = Artifact.Str(root, "mainClass"),
            Assets = Artifact.Str(root, "assets"),
            InheritsFrom = Artifact.Str(root, "inheritsFrom"),
            Downloads = downloads,
            Libraries = libraries,
        };

        if (root.TryGetProperty("javaVersion", out JsonElement javaVersion))
        {
            meta.JavaMajor = (int)Artifact.Num(javaVersion, "majorVersion");
            meta.JavaMajorExplicit = meta.JavaMajor > 0;
            if (meta.JavaMajor <= 0)
            {
                meta.JavaMajor = 8;
            }
        }

        if (root.TryGetProperty("assetIndex", out JsonElement index))
        {
            meta.AssetIndex = new Artifact("", Artifact.Str(index, "url"),
                Artifact.Str(index, "sha1"), Artifact.Num(index, "size"));
            meta.AssetIndexId = Artifact.Str(index, "id");
        }

        if (root.TryGetProperty("logging", out JsonElement logging)
            && logging.TryGetProperty("client", out JsonElement client))
        {
            meta.LoggingArgument = Artifact.Str(client, "argument");
            meta.LoggingName = Artifact.Str(client, "id");
            meta.LoggingFile = Artifact.Parse(client, "file");
            if (string.IsNullOrEmpty(meta.LoggingName) && meta.LoggingFile is not null)
            {
                // Modern manifests omit "id" and give only url/sha1/size.
                int slash = meta.LoggingFile.Url.LastIndexOf('/');
                meta.LoggingName = slash >= 0 ? meta.LoggingFile.Url[(slash + 1)..] : meta.LoggingFile.Url;
            }
        }

        return meta;
    }

    private static Library ParseLibrary(JsonElement item)
    {
        Dictionary<string, Artifact> downloads = [];
        if (item.TryGetProperty("downloads", out JsonElement dl)
            && dl.ValueKind == JsonValueKind.Object)
        {
            foreach (JsonProperty entry in dl.EnumerateObject())
            {
                Artifact? artifact = Artifact.Parse(dl, entry.Name);
                if (artifact is not null)
                {
                    downloads[entry.Name] = artifact;
                }
            }
        }

        Dictionary<string, string> natives = [];
        if (item.TryGetProperty("natives", out JsonElement nat)
            && nat.ValueKind == JsonValueKind.Object)
        {
            foreach (JsonProperty entry in nat.EnumerateObject())
            {
                natives[entry.Name] = entry.Value.GetString() ?? "";
            }
        }

        IReadOnlyList<RuleEntry> rules = item.TryGetProperty("rules", out JsonElement ruleArray)
            ? Rule.Parse(ruleArray)
            : [];

        return new Library
        {
            Name = Artifact.Str(item, "name"),
            BaseUrl = Artifact.Str(item, "url"),
            Downloads = downloads,
            Natives = natives,
            Rules = rules,
        };
    }

    /// <summary>Fills this child's gaps from an already-resolved parent version.</summary>
    public void MergeFrom(VersionMeta parent)
    {
        if (string.IsNullOrEmpty(MainClass))
        {
            MainClass = parent.MainClass;
        }
        if (string.IsNullOrEmpty(Assets))
        {
            Assets = parent.Assets;
        }
        if (AssetIndex is null)
        {
            AssetIndex = parent.AssetIndex;
            AssetIndexId = parent.AssetIndexId;
        }
        if (!JavaMajorExplicit)
        {
            JavaMajor = parent.JavaMajor;
        }
        if (LoggingFile is null)
        {
            LoggingFile = parent.LoggingFile;
            LoggingArgument = parent.LoggingArgument;
            LoggingName = parent.LoggingName;
        }

        Dictionary<string, Artifact> merged = new(Downloads);
        foreach ((string key, Artifact value) in parent.Downloads)
        {
            _ = merged.TryAdd(key, value);
        }
        Downloads = merged;

        HashSet<string> seen = [.. Libraries.Select(l => l.Name)];
        foreach (Library library in parent.Libraries)
        {
            if (seen.Add(library.Name))
            {
                Libraries.Add(library);
            }
        }
    }
}
