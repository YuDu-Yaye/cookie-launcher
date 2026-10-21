package dev.cookielauncher.game.auth;

import dev.cookielauncher.core.config.Account;
import dev.cookielauncher.core.json.Json;
import dev.cookielauncher.core.json.JsonArray;
import dev.cookielauncher.core.json.JsonObject;
import dev.cookielauncher.core.net.Http;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.StringJoiner;

/**
 * Microsoft account sign-in through the Entra ID device-code flow, then the Xbox
 * Live, XSTS and Minecraft services chain.
 *
 * <p>Cookie Launcher deliberately does not embed a third-party application id.
 * A distributor registers a public client under "Personal Microsoft accounts
 * only", enables public client flows, and supplies the id via {@code config.json}
 * as {@code msaClientId}. See {@code docs/AUTH.md}.</p>
 *
 * <p>This class is written and tested for the protocol but ships dormant while
 * the id is blank, so v1.0 works with offline and authlib accounts only.</p>
 */
public final class MicrosoftAuth {

    public static final String DEVICE_CODE_URL =
            "https://login.microsoftonline.com/consumers/oauth2/v2.0/devicecode";
    public static final String TOKEN_URL =
            "https://login.microsoftonline.com/consumers/oauth2/v2.0/token";
    public static final String XBL_URL = "https://user.auth.xboxlive.com/user/authenticate";
    public static final String XSTS_URL = "https://xsts.auth.xboxlive.com/xsts/authorize";
    public static final String MC_LOGIN_URL =
            "https://api.minecraftservices.com/authentication/login_with_xbox";
    public static final String MC_PROFILE_URL =
            "https://api.minecraftservices.com/minecraft/profile";
    public static final String SCOPE = "XboxLive.signin offline_access";

    private final String clientId;

    public MicrosoftAuth(String clientId) {
        this.clientId = clientId == null ? "" : clientId.trim();
    }

    public boolean isConfigured() {
        return !clientId.isBlank();
    }

    public String clientId() {
        return clientId;
    }

    /** One pending device-code challenge shown to the user. */
    public record DeviceCode(String userCode, String verificationUri, String message,
                             String deviceCode, int intervalSeconds, long expiresAt) {
    }

    /** Raw identity-platform token response. */
    public record Tokens(String accessToken, String refreshToken, int expiresInSeconds) {
    }

    public DeviceCode startDeviceCode() throws IOException {
        requireConfigured();
        Map<String, String> form = new LinkedHashMap<>();
        form.put("client_id", clientId);
        form.put("scope", SCOPE);
        JsonObject response = postForm(DEVICE_CODE_URL, form);
        String error = response.getString("error");
        if (error != null) {
            throw new IOException(error + ": " + response.getString("error_description", ""));
        }
        String userCode = response.getString("user_code", "");
        String deviceCode = response.getString("device_code", "");
        if (userCode.isBlank() || deviceCode.isBlank()) {
            throw new IOException("Microsoft returned no device code");
        }
        int interval = Math.max(1, response.getInt("interval", 5));
        long expiresAt = System.currentTimeMillis()
                + Math.max(60, response.getInt("expires_in", 900)) * 1000L;
        return new DeviceCode(userCode,
                response.getString("verification_uri", "https://microsoft.com/link"),
                response.getString("message", ""), deviceCode, interval, expiresAt);
    }

    /**
     * Polls until the user approves, the code expires or the deadline passes.
     * Blocks the calling thread, so run it off the UI thread.
     */
    public Tokens poll(DeviceCode code) throws IOException {
        requireConfigured();
        long deadline = Math.min(code.expiresAt(), System.currentTimeMillis() + 15 * 60 * 1000L);
        while (System.currentTimeMillis() < deadline) {
            sleep(code.intervalSeconds() * 1000L);
            Map<String, String> form = new LinkedHashMap<>();
            form.put("client_id", clientId);
            form.put("grant_type", "urn:ietf:params:oauth:grant-type:device_code");
            form.put("device_code", code.deviceCode());
            JsonObject response = postForm(TOKEN_URL, form);
            String error = response.getString("error");
            if (error == null) {
                return tokens(response);
            }
            switch (error) {
                case "authorization_pending" -> {
                    // Keep polling; this is the normal path.
                }
                case "slow_down" -> sleep(5000L);
                case "authorization_declined" -> throw new IOException("Sign-in was declined");
                case "expired_token" -> throw new IOException("The sign-in code expired");
                default -> throw new IOException(error + ": "
                        + response.getString("error_description", ""));
            }
        }
        throw new IOException("Timed out waiting for Microsoft sign-in");
    }

    public Tokens refresh(String refreshToken) throws IOException {
        requireConfigured();
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new IOException("No refresh token; sign in again");
        }
        Map<String, String> form = new LinkedHashMap<>();
        form.put("client_id", clientId);
        form.put("grant_type", "refresh_token");
        form.put("refresh_token", refreshToken);
        form.put("scope", SCOPE);
        JsonObject response = postForm(TOKEN_URL, form);
        String error = response.getString("error");
        if (error != null) {
            throw new IOException(error + ": " + response.getString("error_description", ""));
        }
        return tokens(response);
    }

    /** Completes the Xbox Live to Minecraft chain and returns a ready account. */
    public Account toAccount(Tokens tokens) throws IOException {
        String xblToken = authenticateXboxLive(tokens.accessToken());
        Xsts xsts = authorizeXsts(xblToken);
        MinecraftToken minecraft = loginWithXbox(xsts);
        Profile profile = fetchProfile(minecraft.accessToken());

        Account account = new Account();
        account.type = "microsoft";
        account.username = profile.name();
        account.uuid = profile.id();
        account.accessToken = minecraft.accessToken();
        account.refreshToken = tokens.refreshToken();
        account.expiresAt = minecraft.expiresAt();
        account.id = "microsoft:" + profile.id();
        return account;
    }

    private Tokens tokens(JsonObject response) {
        return new Tokens(response.getString("access_token", ""),
                response.getString("refresh_token", ""),
                response.getInt("expires_in", 3600));
    }

    private String authenticateXboxLive(String accessToken) throws IOException {
        JsonObject properties = JsonObject.of(
                "AuthMethod", "RPS",
                "SiteName", "user.auth.xboxlive.com",
                "RpsTicket", "d=" + accessToken);
        JsonObject body = JsonObject.of(
                "Properties", properties,
                "RelyingParty", "http://auth.xboxlive.com",
                "TokenType", "JWT");
        JsonObject response = postJson(XBL_URL, body);
        String token = response.getString("Token", "");
        if (token.isBlank()) {
            throw new IOException("Xbox Live rejected the Microsoft token");
        }
        return token;
    }

    private record Xsts(String token, String userHash) {
    }

    private Xsts authorizeXsts(String xblToken) throws IOException {
        JsonObject body = JsonObject.of(
                "Properties", JsonObject.of("SandboxId", "RETAIL"),
                "RelyingParty", "rp://api.minecraftservices.com/",
                "TokenType", "JWT");
        JsonObject response = postJson(XSTS_URL, body);
        String token = response.getString("Token", "");
        String userHash = "";
        JsonObject claims = response.getObject("DisplayClaims");
        JsonArray xui = claims == null ? null : claims.getArray("xui");
        if (xui != null && !xui.isEmpty()) {
            JsonObject first = xui.getObject(0);
            if (first != null) {
                userHash = first.getString("uhs", "");
            }
        }
        if (token.isBlank()) {
            throw new IOException("XSTS refused to authorise this account; "
                    + "it may have no Xbox profile");
        }
        return new Xsts(token, userHash);
    }

    private record MinecraftToken(String accessToken, long expiresAt) {
    }

    private MinecraftToken loginWithXbox(Xsts xsts) throws IOException {
        JsonObject body = JsonObject.of("identityToken",
                "XBL3.0 x=" + xsts.userHash() + ";" + xsts.token());
        JsonObject response = postJson(MC_LOGIN_URL, body);
        String token = response.getString("accessToken", "");
        if (token.isBlank()) {
            token = response.getString("access_token", "");
        }
        if (token.isBlank()) {
            throw new IOException("Minecraft services refused the Xbox token; "
                    + "this account may not own Java Edition");
        }
        long expiresAt = System.currentTimeMillis()
                + response.getInt("expires_in", 86400) * 1000L;
        return new MinecraftToken(token, expiresAt);
    }

    private record Profile(String id, String name) {
    }

    private Profile fetchProfile(String accessToken) throws IOException {
        java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder(
                        java.net.URI.create(MC_PROFILE_URL))
                .timeout(java.time.Duration.ofSeconds(30))
                .header("Authorization", "Bearer " + accessToken)
                .header("Accept", "application/json")
                .header("User-Agent", dev.cookielauncher.core.AppInfo.USER_AGENT)
                .GET()
                .build();
        try {
            java.net.http.HttpResponse<String> response = Http.client().send(request,
                    java.net.http.HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 401 || response.statusCode() == 403) {
                throw new IOException("Minecraft services rejected the token");
            }
            JsonObject root = Json.parseObject(response.body());
            String id = root.getString("id", "");
            String name = root.getString("name", "");
            if (id.isBlank() || name.isBlank()) {
                throw new IOException("This account does not own Minecraft: Java Edition");
            }
            return new Profile(id, name);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while reading the Minecraft profile", e);
        }
    }

    private void requireConfigured() throws IOException {
        if (!isConfigured()) {
            throw new IOException("Microsoft sign-in needs an application id in config.json "
                    + "(msaClientId). See docs/AUTH.md.");
        }
    }

    private JsonObject postForm(String url, Map<String, String> form) throws IOException {
        StringJoiner joiner = new StringJoiner("&");
        form.forEach((key, value) -> joiner.add(encode(key) + "=" + encode(value)));
        return postRaw(url, joiner.toString(), "application/x-www-form-urlencoded");
    }

    private JsonObject postJson(String url, JsonObject body) throws IOException {
        return postRaw(url, Json.write(body), "application/json");
    }

    private JsonObject postRaw(String url, String body, String contentType) throws IOException {
        java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder(
                        java.net.URI.create(url))
                .timeout(java.time.Duration.ofSeconds(45))
                .header("Content-Type", contentType)
                .header("Accept", "application/json")
                .header("User-Agent", dev.cookielauncher.core.AppInfo.USER_AGENT)
                .POST(java.net.http.HttpRequest.BodyPublishers.ofString(body))
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
            throw new IOException("Interrupted while contacting Microsoft", e);
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private static void sleep(long millis) throws IOException {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Sign-in was cancelled", e);
        }
    }
}
