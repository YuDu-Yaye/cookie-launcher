package dev.cookielauncher.game.launch;

import dev.cookielauncher.core.config.Account;
import dev.cookielauncher.core.json.JsonArray;
import dev.cookielauncher.core.json.JsonObject;
import dev.cookielauncher.game.meta.Features;
import dev.cookielauncher.game.meta.Library;
import dev.cookielauncher.game.meta.Rule;
import dev.cookielauncher.game.meta.VersionMeta;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Assembles the JVM command line from version metadata.
 *
 * <p>Covers both metadata generations: the modern {@code arguments} object with
 * per-entry rules and conditional values, and the legacy single
 * {@code minecraftArguments} string. Placeholder substitution implements the full
 * documented set, including quick-play server arguments.</p>
 */
public final class LaunchBuilder {

    private LaunchBuilder() {
    }

    public static LaunchCommand build(LaunchRequest request) {
        require(request != null, "request");
        require(request.meta != null, "version metadata");
        require(request.javaExecutable != null, "java executable");
        require(request.gameDirectory != null, "game directory");

        VersionMeta meta = request.meta;
        if (meta.mainClass == null || meta.mainClass.isBlank()) {
            throw new IllegalStateException("Version " + meta.id + " has no mainClass");
        }

        Features features = Features.forRequest(request);
        Map<String, String> placeholders = placeholders(request, meta, features);
        List<String> command = new ArrayList<>();
        command.add(request.javaExecutable.toString());

        List<String> jvmArguments = expand(meta.arguments == null ? null
                : meta.arguments.getArray("jvm"), features, placeholders);
        if (jvmArguments.isEmpty()) {
            jvmArguments = legacyJvmArguments(request, placeholders);
        }
        // The logging argument is defined once per version in logging.client.argument
        // and is NOT part of arguments.jvm, so every modern version needs it added
        // here. Without it log4j falls back to its default format.
        if (request.loggingConfig != null && request.loggingArgument != null
                && !request.loggingArgument.isBlank()
                && jvmArguments.stream().noneMatch(part -> part.startsWith("-Dlog4j"))) {
            jvmArguments.add(substitute(request.loggingArgument, placeholders));
        }
        command.addAll(jvmArguments);
        // User-supplied JVM flags come after the metadata flags so a user override
        // (for example a bigger heap) wins over the template.
        command.addAll(split(request.extraJvmArgs));

        Set<String> seen = new LinkedHashSet<>(command);
        if (seen.stream().noneMatch(part -> part.startsWith("-Djava.library.path"))) {
            command.add("-Djava.library.path=" + request.nativesDirectory);
        }
        if (seen.stream().noneMatch(part -> part.equals("-cp") || part.equals("-classpath"))) {
            command.add("-cp");
            command.add(classpath(request, meta, features));
        }
        command.add(meta.mainClass);

        List<String> gameArguments = new ArrayList<>();
        if (meta.arguments != null && meta.arguments.getArray("game") != null) {
            gameArguments.addAll(expand(meta.arguments.getArray("game"), features, placeholders));
        } else if (meta.minecraftArguments != null && !meta.minecraftArguments.isBlank()) {
            for (String token : split(meta.minecraftArguments)) {
                gameArguments.add(substitute(token, placeholders));
            }
        }
        if (gameArguments.isEmpty()) {
            gameArguments.addAll(fallbackGameArguments(request, placeholders));
        }
        for (String extra : split(request.extraGameArgs)) {
            if (!gameArguments.contains(extra)) {
                gameArguments.add(extra);
            }
        }
        // Quick play is only safe on versions whose metadata declares the flag.
        // Passing it to 1.19 or older makes the game reject the command line, so
        // the metadata decides rather than the launcher guessing from a version id.
        if (request.server != null && !request.server.isBlank()
                && supportsQuickPlay(meta)
                && gameArguments.stream().noneMatch("--quickPlayMultiplayer"::equals)) {
            gameArguments.add("--quickPlayMultiplayer");
            gameArguments.add(quickPlayAddress(request.server));
        }
        if (request.fullscreen && gameArguments.stream().noneMatch("--fullscreen"::equals)) {
            gameArguments.add("--fullscreen");
        }
        command.addAll(gameArguments);

        return new LaunchCommand(command, request.gameDirectory, Map.of());
    }

    /** True when the version's game arguments mention the quick play placeholder. */
    private static boolean supportsQuickPlay(VersionMeta meta) {
        if (meta.arguments == null) {
            return false;
        }
        var gameArray = meta.arguments.getArray("game");
        return gameArray != null && gameArray.toString().contains("quickPlayMultiplayer");
    }

    /** Appends the default port when the user typed a bare host. */
    public static String quickPlayAddress(String server) {
        if (server == null || server.isBlank()) {
            return "";
        }
        String value = server.trim();
        int colon = value.lastIndexOf(':');
        boolean hasPort = colon > 0 && colon < value.length() - 1
                && value.substring(colon + 1).chars().allMatch(Character::isDigit);
        return hasPort ? value : value + ":25565";
    }

    private static Map<String, String> placeholders(LaunchRequest request, VersionMeta meta,
                                                    Features features) {
        Map<String, String> map = new LinkedHashMap<>();
        Account account = request.account;
        String name = account == null ? "Player" : account.username;
        String uuid = account == null ? Account.offlineUuid("Player") : account.uuid;
        String token = account == null || account.accessToken == null || account.accessToken.isBlank()
                ? "0" : account.accessToken;

        map.put("auth_player_name", name);
        map.put("auth_uuid", uuid);
        map.put("auth_access_token", token);
        map.put("auth_session", token);
        map.put("auth_xuid", "0");
        map.put("user_type", account != null && account.isOnline() ? "msa" : "legacy");
        map.put("user_properties", "{}");

        map.put("version_name", meta.id);
        map.put("version_type", meta.type == null ? "release" : meta.type);
        map.put("game_directory", request.gameDirectory.toString());
        map.put("assets_root", request.assetsDirectory.toString());
        map.put("assets_index_name",
                meta.assets == null || meta.assets.isBlank() ? "legacy" : meta.assets);
        map.put("game_assets", request.assetsDirectory.toString());
        map.put("natives_directory", request.nativesDirectory.toString());
        map.put("library_directory", request.librariesDirectory.toString());
        map.put("classpath_separator", java.io.File.pathSeparator);
        map.put("classpath", classpath(request, meta, features));
        map.put("launcher_name", "CookieLauncher");
        map.put("launcher_version", dev.cookielauncher.core.AppInfo.VERSION);
        map.put("resolution_width", String.valueOf(request.windowWidth));
        map.put("resolution_height", String.valueOf(request.windowHeight));

        if (request.server != null && !request.server.isBlank()) {
            String normalized = request.server.contains(":")
                    ? request.server : request.server + ":25565";
            map.put("quickPlayMultiplayer", normalized);
        }
        return map;
    }

    private static List<String> expand(JsonArray array, Features features,
                                       Map<String, String> placeholders) {
        List<String> result = new ArrayList<>();
        if (array == null) {
            return result;
        }
        for (Object element : array) {
            if (element instanceof String text) {
                result.add(substitute(text, placeholders));
            } else if (element instanceof JsonObject object) {
                if (!Rule.allows(object.getArray("rules"), features)) {
                    continue;
                }
                JsonArray values = object.getArray("value");
                if (values != null) {
                    for (String value : values.strings()) {
                        result.add(substitute(value, placeholders));
                    }
                } else {
                    String single = object.getString("value");
                    if (single != null) {
                        result.add(substitute(single, placeholders));
                    }
                }
            }
        }
        return result;
    }

    private static List<String> legacyJvmArguments(LaunchRequest request,
                                                   Map<String, String> placeholders) {
        List<String> arguments = new ArrayList<>();
        arguments.add("-Xms" + request.minMemoryMb + "M");
        arguments.add("-Xmx" + request.maxMemoryMb + "M");
        arguments.add("-Djava.library.path=" + request.nativesDirectory);
        if (request.loggingConfig != null && request.loggingArgument != null
                && !request.loggingArgument.isBlank()) {
            arguments.add(substitute(request.loggingArgument, placeholders));
        }
        return arguments;
    }

    /** Client jar first, then every applicable non-native library that exists. */
    public static String classpath(LaunchRequest request, VersionMeta meta, Features features) {
        StringBuilder builder = new StringBuilder();
        Path clientJar = request.clientJar;
        if (clientJar != null && Files.isRegularFile(clientJar)) {
            builder.append(clientJar);
        }
        for (Library library : meta.libraries) {
            if (!library.allowed(features) || library.hasNatives()) {
                continue;
            }
            Path path = request.librariesDirectory.resolve(library.path());
            if (!Files.isRegularFile(path)) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(java.io.File.pathSeparator);
            }
            builder.append(path);
        }
        return builder.toString();
    }

    private static List<String> fallbackGameArguments(LaunchRequest request,
                                                      Map<String, String> placeholders) {
        List<String> arguments = new ArrayList<>();
        arguments.add("--username");
        arguments.add(placeholders.get("auth_player_name"));
        arguments.add("--version");
        arguments.add(placeholders.get("version_name"));
        arguments.add("--gameDir");
        arguments.add(request.gameDirectory.toString());
        arguments.add("--assetsDir");
        arguments.add(request.assetsDirectory.toString());
        arguments.add("--assetIndex");
        arguments.add(placeholders.get("assets_index_name"));
        arguments.add("--uuid");
        arguments.add(placeholders.get("auth_uuid"));
        arguments.add("--accessToken");
        arguments.add(placeholders.get("auth_access_token"));
        arguments.add("--userType");
        arguments.add(placeholders.get("user_type"));
        arguments.add("--versionType");
        arguments.add(placeholders.get("version_type"));
        return arguments;
    }

    static String substitute(String template, Map<String, String> placeholders) {
        if (template == null || template.indexOf("${") < 0) {
            return template;
        }
        String result = template;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            result = result.replace("${" + entry.getKey() + "}", entry.getValue());
        }
        return result;
    }

    /** Splits an argument string on whitespace, honouring double quotes. */
    public static List<String> split(String raw) {
        List<String> result = new ArrayList<>();
        if (raw == null || raw.isBlank()) {
            return result;
        }
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == '"') {
                quoted = !quoted;
            } else if (Character.isWhitespace(c) && !quoted) {
                if (current.length() > 0) {
                    result.add(current.toString());
                    current.setLength(0);
                }
            } else {
                current.append(c);
            }
        }
        if (current.length() > 0) {
            result.add(current.toString());
        }
        return result;
    }

    private static void require(boolean condition, String what) {
        if (!condition) {
            throw new IllegalArgumentException("Missing " + what);
        }
    }
}
