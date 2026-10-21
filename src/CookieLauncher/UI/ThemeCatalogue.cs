namespace CookieLauncher.UI;

/// <summary>A selectable colour scheme, defined only by its seed colour.</summary>
/// <remarks>
/// Every role is generated from the seed by <see cref="SchemeTonalSpot"/>, which is how
/// the Material Theme Builder produces a palette from a single colour. Keeping the
/// catalogue to seeds rather than hex lists means adding a scheme is one line, and every
/// scheme is guaranteed internally consistent.
/// </remarks>
public sealed record SchemeDefinition(string Id, string Name, int SeedArgb, bool Dark);

/// <summary>The palette catalogue shown in Settings.</summary>
/// <remarks>
/// Blue is first and is the default: it is the reference Material 3 blue (the same seed
/// Google's own tooling opens with), so the generated scheme has the tone relationships
/// the design system was tuned against rather than an arbitrary hue.
/// </remarks>
public static class ThemeCatalogue
{
    public static IReadOnlyList<SchemeDefinition> All { get; } =
    [
        new("blue-dark", "Blue", unchecked((int)0xFF1B6EF3), true),
        new("blue-light", "Blue", unchecked((int)0xFF1B6EF3), false),
        new("indigo-dark", "Indigo", unchecked((int)0xFF4F5BD5), true),
        new("indigo-light", "Indigo", unchecked((int)0xFF4F5BD5), false),
        new("teal-dark", "Teal", unchecked((int)0xFF00897B), true),
        new("teal-light", "Teal", unchecked((int)0xFF00897B), false),
        new("violet-dark", "Violet", unchecked((int)0xFF7C4DFF), true),
        new("violet-light", "Violet", unchecked((int)0xFF7C4DFF), false),
        new("rose-dark", "Rose", unchecked((int)0xFFE0457B), true),
        new("rose-light", "Rose", unchecked((int)0xFFE0457B), false),
        new("amber-dark", "Amber", unchecked((int)0xFFD98200), true),
        new("amber-light", "Amber", unchecked((int)0xFFD98200), false),
        new("graphite-dark", "Graphite", unchecked((int)0xFF6E7B8B), true),
        new("graphite-light", "Graphite", unchecked((int)0xFF6E7B8B), false),
    ];

    public static SchemeDefinition Get(string? id)
        => All.FirstOrDefault(s => string.Equals(s.Id, id, StringComparison.OrdinalIgnoreCase))
           ?? All[0];

    /// <summary>Generates the full scheme for a catalogue entry.</summary>
    public static MaterialScheme Generate(string? id)
    {
        SchemeDefinition definition = Get(id);
        return SchemeTonalSpot.FromSeed(definition.SeedArgb, definition.Dark);
    }
}
