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
public static class ThemeCatalogue
{
    public static IReadOnlyList<SchemeDefinition> All { get; } =
    [
        new("caramel-dark", "Caramel", unchecked((int)0xFFC97F3E), true),
        new("caramel-light", "Caramel", unchecked((int)0xFFC97F3E), false),
        new("midnight-dark", "Midnight", unchecked((int)0xFF6C9FE8), true),
        new("midnight-light", "Midnight", unchecked((int)0xFF6C9FE8), false),
        new("sage-dark", "Sage", unchecked((int)0xFF7FB58A), true),
        new("sage-light", "Sage", unchecked((int)0xFF7FB58A), false),
        new("rose-dark", "Rose", unchecked((int)0xFFE08398), true),
        new("rose-light", "Rose", unchecked((int)0xFFE08398), false),
        new("material-dark", "Material", unchecked((int)0xFF6750A4), true),
        new("material-light", "Material", unchecked((int)0xFF6750A4), false),
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
