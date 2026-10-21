namespace CookieLauncher.Core;

/// <summary>A game account: offline, authlib-injector (Yggdrasil) or Microsoft.</summary>
public sealed class Account
{
    public string Id { get; set; } = "";

    /// <summary><c>offline</c>, <c>authlib</c> or <c>microsoft</c>.</summary>
    public string Type { get; set; } = "offline";

    public string Username { get; set; } = "";
    public string Uuid { get; set; } = "";
    public string AccessToken { get; set; } = "";
    public string RefreshToken { get; set; } = "";

    /// <summary>Yggdrasil server root, empty for offline and Microsoft accounts.</summary>
    public string ServerUrl { get; set; } = "";

    public long ExpiresAt { get; set; }

    public static Account Offline(string username)
    {
        string name = string.IsNullOrWhiteSpace(username) ? "Player" : username.Trim();
        return new Account
        {
            Type = "offline",
            Username = name,
            Id = "offline:" + name.ToLowerInvariant(),
            Uuid = OfflineUuid(name),
        };
    }

    /// <summary>
    /// Mojang's offline identity rule: a v3 UUID derived from the name.
    /// </summary>
    /// <remarks>
    /// Every server in offline mode assigns the same UUID for a given name, so worlds and
    /// inventories match what the official launcher would produce for the same account.
    /// The name is case sensitive, exactly as it is on a real server.
    /// </remarks>
    public static string OfflineUuid(string username)
    {
        // UUID v3 over the UTF-8 bytes of "OfflinePlayer:<name>", MD5 based, with the
        // version and variant bits set - the same derivation the vanilla server uses.
        byte[] input = System.Text.Encoding.UTF8.GetBytes("OfflinePlayer:" + username);
        byte[] hash = System.Security.Cryptography.MD5.HashData(input);
        hash[6] = (byte)((hash[6] & 0x0F) | 0x30); // version 3
        hash[8] = (byte)((hash[8] & 0x3F) | 0x80); // variant RFC 4122
        return Convert.ToHexString(hash).ToLowerInvariant();
    }

    public bool IsOnline => Type is "microsoft" or "authlib";

    public bool IsExpired => ExpiresAt > 0
        && DateTimeOffset.UtcNow.ToUnixTimeMilliseconds() >= ExpiresAt;

    public string Label => Type switch
    {
        "microsoft" => Username + "  ·  Microsoft",
        "authlib" => Username + "  ·  Authlib",
        _ => Username + "  ·  Offline",
    };

    public Account Copy() => new()
    {
        Id = Id,
        Type = Type,
        Username = Username,
        Uuid = Uuid,
        AccessToken = AccessToken,
        RefreshToken = RefreshToken,
        ServerUrl = ServerUrl,
        ExpiresAt = ExpiresAt,
    };

    /// <summary>Never logs tokens; this is the only safe representation.</summary>
    public override string ToString() => $"Account[{Type}, {Username}, uuid={Uuid}]";
}
