package dev.cookielauncher.game.auth;

import dev.cookielauncher.core.config.Account;
import dev.cookielauncher.core.json.Json;
import dev.cookielauncher.core.json.JsonObject;
import dev.cookielauncher.core.log.Log;
import dev.cookielauncher.core.net.Http;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Yggdrasil (authlib) authentication for third-party servers.
 *
 * <p>Speaks the classic Yggdrasil protocol that authlib-injector implements:
 * {@code /authserver/authenticate}, {@code /authserver/refresh} and the public
 * {@code /api/yggdrasil} document used to read the server name.</p>
 */
public final class YggdrasilAuth {

    private final String serverUrl;

    public YggdrasilAuth(String serverUrl) {
        String normalized = serverUrl == null ? "" : serverUrl.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        this.serverUrl = normalized;
    }

    public String serverUrl() {
        return serverUrl;
    }

    public boolean isConfigured() {
        return serverUrl.startsWith("http://") || serverUrl.startsWith("https://");
    }

    /** Server display name from {@code /api/yggdrasil}, falling back to the host. */
    public String displayName() {
        try {
            JsonObject root = Http.getJson(serverUrl + "/api/yggdrasil");
            JsonObject meta = root.getObject("meta");
            if (meta != null) {
                String name = meta.getString("serverName", "");
                if (!name.isBlank()) {
                    return name;
                }
            }
        } catch (IOException e) {
            Log.debug("Cannot read Yggdrasil metadata: " + e.getMessage());
        }
        return serverUrl.replace("https://", "").replace("http://", "");
    }

    /** Authenticates a username/password pair and returns a ready account. */
    public Account login(String username, String password) throws IOException {
        if (!isConfigured()) {
            throw new IOException("Server URL must start with http:// or https://");
        }
        if (username == null || username.isBlank()) {
            throw new IOException("Username is required");
        }
        if (password == null || password.isEmpty()) {
            throw new IOException("Password is required");
        }
        JsonObject body = JsonObject.of(
                "agent", JsonObject.of("name", "Minecraft", "version", 1),
                "username", username.trim(),
                "password", password,
                "clientToken", java.util.UUID.randomUUID().toString().replace("-", ""),
                "requestUser", true);
        JsonObject response = post("/authserver/authenticate", body);
        return toAccount(response, username.trim());
    }

    /** Refreshes an existing session using its access token. */
    public Account refresh(Account account) throws IOException {
        if (account == null || account.accessToken == null || account.accessToken.isBlank()) {
            throw new IOException("This account has no session to refresh");
        }
        JsonObject body = JsonObject.of(
                "accessToken", account.accessToken,
                "clientToken", account.refreshToken == null ? "" : account.refreshToken,
                "requestUser", true);
        JsonObject response = post("/authserver/refresh", body);
        Account refreshed = toAccount(response, account.username);
        refreshed.refreshToken = account.refreshToken;
        return refreshed;
    }

    /** Invalidates a session server-side; failures are logged, never fatal. */
    public void invalidate(String accessToken, String clientToken) {
        try {
            post("/authserver/invalidate", JsonObject.of(
                    "accessToken", accessToken,
                    "clientToken", clientToken == null ? "" : clientToken));
        } catch (IOException e) {
            Log.debug("Session invalidation failed: " + e.getMessage());
        }
    }

    private Account toAccount(JsonObject response, String fallbackName) throws IOException {
        String error = response.getString("error");
        if (error != null && !error.isBlank()) {
            throw new IOException(explain(error, response.getString("errorMessage", "")));
        }
        JsonObject profile = response.getObject("selectedProfile");
        if (profile == null) {
            throw new IOException("This account owns no Minecraft profile on that server");
        }
        Account account = new Account();
        account.type = "authlib";
        account.username = profile.getString("name", fallbackName);
        account.uuid = profile.getString("id", "");
        account.accessToken = response.getString("accessToken", "");
        account.refreshToken = response.getString("clientToken", "");
        account.serverUrl = serverUrl;
        account.id = "authlib:" + account.uuid;
        if (account.accessToken.isBlank()) {
            throw new IOException("The server returned no access token");
        }
        return account;
    }

    private static String explain(String error, String detail) {
        String suffix = detail == null || detail.isBlank() ? "" : " (" + detail + ")";
        return switch (error) {
            case "ForbiddenOperationException" -> "Wrong username or password" + suffix;
            case "IllegalArgumentException" -> "The server rejected the request format" + suffix;
            case "UnsupportedMediaTypeException" -> "The server speaks a different protocol" + suffix;
            default -> error + suffix;
        };
    }

    private JsonObject post(String path, JsonObject body) throws IOException {
        java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder(
                        java.net.URI.create(serverUrl + path))
                .timeout(java.time.Duration.ofSeconds(30))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header("User-Agent", dev.cookielauncher.core.AppInfo.USER_AGENT)
                .POST(java.net.http.HttpRequest.BodyPublishers.ofString(Json.write(body)))
                .build();
        try {
            java.net.http.HttpResponse<String> response = Http.client().send(request,
                    java.net.http.HttpResponse.BodyHandlers.ofString());
            String text = response.body();
            if (text == null || text.isBlank()) {
                throw new IOException("Empty response (HTTP " + response.statusCode() + ")");
            }
            return Json.parseObject(text);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while contacting the auth server", e);
        }
    }

    /** Lists skin URLs the server reports for this account. */
    public List<String> skins(String accessToken) {
        List<String> result = new ArrayList<>();
        try {
            java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder(
                            java.net.URI.create(serverUrl + "/api/user/profile/" + accessToken))
                    .timeout(java.time.Duration.ofSeconds(15))
                    .header("Authorization", "Bearer " + accessToken)
                    .header("User-Agent", dev.cookielauncher.core.AppInfo.USER_AGENT)
                    .GET()
                    .build();
            java.net.http.HttpResponse<String> response = Http.client().send(request,
                    java.net.http.HttpResponse.BodyHandlers.ofString());
            JsonObject root = Json.parseObject(response.body());
            var skins = root.getArray("skins");
            if (skins != null) {
                for (JsonObject skin : skins.objects()) {
                    String url = skin.getString("url", "");
                    if (!url.isBlank()) {
                        result.add(url);
                    }
                }
            }
        } catch (IOException e) {
            Log.debug("Cannot list skins: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return result;
    }
}
