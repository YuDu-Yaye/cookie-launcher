// One-off source rewrite: flatten JSON rules into plain records.
//
// The JsonElement-based rule engine crashed at runtime because a JsonElement is a
// view into its owning JsonDocument, and that document is disposed as soon as
// parsing finishes. Rule evaluation then throws ObjectDisposedException.
//
// This script replaces the element-based engine with a record-based one.
"use strict";

const fs = require("fs");

const target = "Game/Meta/VersionMeta.cs";
let source = fs.readFileSync(target, "utf8");

// 1. The Library field type.
source = source.replace(
  "    public IReadOnlyList<JsonElement> Rules { get; init; } = [];",
  `    /// <summary>
    /// Rules flattened at parse time, not stored as <c>JsonElement</c>.
    /// </summary>
    /// <remarks>
    /// A JsonElement is a view into its owning JsonDocument. Once that document is
    /// disposed, every access throws ObjectDisposedException - which is exactly what
    /// happened when the installer evaluated library rules after parsing completed.
    /// The values are therefore copied into plain records while the document is open.
    /// </remarks>
    public IReadOnlyList<RuleEntry> Rules { get; init; } = [];`,
);

// 2. Replace the whole rule engine block.
const startMarker = "/// <summary>Evaluates Mojang's rule lists against the current platform.</summary>";
const endMarker = "/// <summary>A parsed version, with its <c>inheritsFrom</c> chain already resolved.</summary>";
const start = source.indexOf(startMarker);
const end = source.indexOf(endMarker);
if (start < 0 || end < 0 || end <= start) {
  throw new Error("rule engine markers not found");
}

const replacement = `/// <summary>One rule, flattened out of JSON so it stays valid after the document closes.</summary>
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

`;

source = source.slice(0, start) + replacement + source.slice(end);

// 3. Library parsing must use the new entry point.
source = source.replace(
  `        List<JsonElement> rules = [];
        if (item.TryGetProperty("rules", out JsonElement r) && r.ValueKind == JsonValueKind.Array)
        {
            rules.AddRange(r.EnumerateArray());
        }`,
  `        IReadOnlyList<RuleEntry> rules = item.TryGetProperty("rules", out JsonElement ruleArray)
            ? Rule.Parse(ruleArray)
            : [];`,
);

fs.writeFileSync(target, source);
console.log("rule engine flattened");
