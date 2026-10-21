package dev.cookielauncher.core.config;

import dev.cookielauncher.core.json.JsonObject;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** A game account: offline, authlib-injector (Yggdrasil) or Microsoft. */
public final class Account {

    public String id = "";
    /** {@code offline}, {@code authlib} or {@code microsoft}. */
    public String type = "offline";
    public String username = "";
    public String uuid = "";
    public String accessToken = "";
    public String refreshToken = "";
    /** Yggdrasil server root, empty for offline and Microsoft accounts. */
    public String serverUrl = "";
    public long expiresAt = 0L;

    public static Account offline(String username) {
        Account account = new Account();
        account.type = "offline";
        account.username = username == null || username.isBlank() ? "Player" : username.trim();
        account.id = "offline:" + account.username.toLowerCase();
        account.uuid = offlineUuid(account.username);
        return account;
    }

    /** Mojang's offline UUID rule: a v3 UUID derived from "OfflinePlayer:&lt;name&gt;". */
    public static String offlineUuid(String username) {
        return UUID.nameUUIDFromBytes(
                ("OfflinePlayer:" + username).getBytes(StandardCharsets.UTF_8))
                .toString().replace("-", "");
    }

    public boolean isOnline() {
        return "microsoft".equals(type) || "authlib".equals(type);
    }

    public boolean isExpired() {
        return expiresAt > 0 && System.currentTimeMillis() >= expiresAt;
    }

    public String label() {
        return switch (type) {
            case "microsoft" -> username + "  ·  Microsoft";
            case "authlib" -> username + "  ·  Authlib";
            default -> username + "  ·  Offline";
        };
    }

    public Account copy() {
        Account copy = new Account();
        copy.id = id;
        copy.type = type;
        copy.username = username;
        copy.uuid = uuid;
        copy.accessToken = accessToken;
        copy.refreshToken = refreshToken;
        copy.serverUrl = serverUrl;
        copy.expiresAt = expiresAt;
        return copy;
    }

    public JsonObject toJson() {
        return JsonObject.of(
                "id", id,
                "type", type,
                "username", username,
                "uuid", uuid,
                "accessToken", accessToken,
                "refreshToken", refreshToken,
                "serverUrl", serverUrl,
                "expiresAt", expiresAt);
    }

    public static Account fromJson(JsonObject object) {
        Account account = new Account();
        account.id = object.getString("id", "");
        account.type = object.getString("type", "offline");
        account.username = object.getString("username", "Player");
        account.uuid = object.getString("uuid", "");
        account.accessToken = object.getString("accessToken", "");
        account.refreshToken = object.getString("refreshToken", "");
        account.serverUrl = object.getString("serverUrl", "");
        account.expiresAt = object.getLong("expiresAt", 0L);
        return account;
    }

    /** Never log tokens; this is the only safe representation. */
    @Override
    public String toString() {
        return "Account[" + type + ", " + username + ", uuid=" + uuid + "]";
    }
}
