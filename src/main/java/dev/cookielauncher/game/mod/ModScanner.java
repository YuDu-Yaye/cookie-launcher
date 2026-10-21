package dev.cookielauncher.game.mod;

import dev.cookielauncher.core.json.Json;
import dev.cookielauncher.core.json.JsonArray;
import dev.cookielauncher.core.json.JsonObject;
import dev.cookielauncher.core.log.Log;
import dev.cookielauncher.core.util.Fs;
import dev.cookielauncher.core.util.Zip;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Reads mod metadata from {@code mods/} without loading any mod code.
 *
 * <p>Supported formats, in the order loaders check them: {@code quilt.mod.json},
 * {@code fabric.mod.json}, {@code META-INF/neoforge.mods.toml},
 * {@code META-INF/mods.toml} and the legacy {@code mcmod.info}. A jar that declares
 * no metadata still appears so users can see and disable it.</p>
 */
public final class ModScanner {

    /** TOML multi-line string delimiters, built from code points to stay readable. */
    private static final String QUOTE3 = new String(new char[]{34, 34, 34});
    private static final String APOS3 = new String(new char[]{39, 39, 39});

    private ModScanner() {
    }

    public static List<ModInfo> scan(Path modsDirectory) {
        List<ModInfo> result = new ArrayList<>();
        if (!Files.isDirectory(modsDirectory)) {
            return result;
        }
        for (Path file : Fs.list(modsDirectory)) {
            String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
            boolean enabled = name.endsWith(".jar");
            boolean disabled = name.endsWith(".jar.disabled");
            if (!enabled && !disabled) {
                continue;
            }
            if (!Files.isRegularFile(file)) {
                continue;
            }
            ModInfo info = read(file, !enabled);
            if (info != null) {
                result.add(info);
            }
        }
        result.sort(Comparator.comparing((ModInfo info) -> !info.enabled)
                .thenComparing(info -> info.displayName().toLowerCase(Locale.ROOT)));
        return result;
    }

    static ModInfo read(Path file, boolean disabled) {
        ModInfo info = new ModInfo();
        info.file = file;
        info.fileName = file.getFileName().toString();
        info.enabled = !disabled;
        info.sizeBytes = Fs.size(file);
        try {
            String quilt = Zip.readEntry(file, "quilt.mod.json");
            if (quilt != null) {
                parseQuilt(Json.parseObject(quilt), info);
                return info;
            }
            String fabric = Zip.readEntry(file, "fabric.mod.json");
            if (fabric != null) {
                parseFabric(Json.parseObject(fabric), info);
                return info;
            }
            String neoForge = Zip.readEntry(file, "META-INF/neoforge.mods.toml");
            if (neoForge != null) {
                parseToml(neoForge, info);
                info.loader = "neoforge";
                return info;
            }
            String forge = Zip.readEntry(file, "META-INF/mods.toml");
            if (forge != null) {
                parseToml(forge, info);
                info.loader = "forge";
                return info;
            }
            String mcmod = Zip.readEntry(file, "mcmod.info");
            if (mcmod != null) {
                parseMcmodInfo(mcmod, info);
                info.loader = "forge";
                return info;
            }
        } catch (IOException e) {
            Log.debug("Cannot read mod metadata from " + file.getFileName() + ": " + e.getMessage());
        }
        if (info.name == null || info.name.isBlank()) {
            info.name = defaultName(info.fileName);
        }
        if (info.id == null || info.id.isBlank()) {
            info.id = info.name.toLowerCase(Locale.ROOT).replace(' ', '_');
        }
        return info;
    }

    private static String defaultName(String fileName) {
        String stripped = fileName;
        String lower = stripped.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".disabled")) {
            stripped = stripped.substring(0, stripped.length() - ".disabled".length());
            lower = stripped.toLowerCase(Locale.ROOT);
        }
        if (lower.endsWith(".jar")) {
            stripped = stripped.substring(0, stripped.length() - ".jar".length());
        }
        return stripped;
    }

    private static void parseFabric(JsonObject root, ModInfo info) {
        info.loader = "fabric";
        info.id = root.getString("id", "");
        info.version = root.getString("version", "");
        info.name = root.getString("name", info.id);
        info.description = root.getString("description", "");
        info.authors = joinAuthors(root.getArray("authors"));
        finish(info);
    }

    private static void parseQuilt(JsonObject root, ModInfo info) {
        info.loader = "quilt";
        JsonObject loader = root.getObject("quilt_loader");
        if (loader != null) {
            info.id = loader.getString("id", "");
            info.version = loader.getString("version", "");
            JsonObject metadata = loader.getObject("metadata");
            if (metadata != null) {
                info.name = metadata.getString("name", info.id);
                info.description = metadata.getString("description", "");
                info.authors = joinAuthors(metadata.getArray("contributors"));
            }
        }
        finish(info);
    }

    private static String joinAuthors(JsonArray array) {
        if (array == null || array.isEmpty()) {
            return "";
        }
        List<String> names = new ArrayList<>();
        for (Object element : array) {
            if (element instanceof String text) {
                names.add(text);
            } else if (element instanceof JsonObject object) {
                String name = object.getString("name", "");
                if (!name.isBlank()) {
                    names.add(name);
                }
            }
        }
        return String.join(", ", names);
    }

    private static void parseToml(String text, ModInfo info) {
        // Only the first [[mods]] block matters for the list view.
        Map<String, String> values = new LinkedHashMap<>();
        boolean inModsBlock = false;
        for (String rawLine : text.split("\\R")) {
            String line = rawLine.strip();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            if (line.startsWith("[[mods]]")) {
                inModsBlock = true;
                continue;
            }
            if (line.startsWith("[[")) {
                inModsBlock = false;
                continue;
            }
            if (!inModsBlock) {
                continue;
            }
            int equals = line.indexOf('=');
            if (equals <= 0) {
                continue;
            }
            String key = line.substring(0, equals).strip();
            String value = tomlValue(line.substring(equals + 1).strip());
            values.putIfAbsent(key, value);
        }
        info.id = values.getOrDefault("modId", "");
        info.version = values.getOrDefault("version", "");
        info.name = values.getOrDefault("displayName", info.id);
        info.description = values.getOrDefault("description", "");
        info.authors = values.getOrDefault("authors", "");
        finish(info);
    }

    private static String tomlValue(String raw) {
        String value = raw;
        if (value.startsWith(QUOTE3)) {
            value = value.substring(3);
        } else if (value.startsWith(APOS3)) {
            value = value.substring(3);
        } else if (value.startsWith("\"") || value.startsWith("'")) {
            char quote = value.charAt(0);
            value = value.substring(1);
            int end = value.indexOf(quote);
            if (end >= 0) {
                value = value.substring(0, end);
            }
        }
        if (value.startsWith("[")) {
            value = value.replace("[", "").replace("]", "")
                    .replace("\"", "").replace("'", "");
        }
        return value.trim();
    }

    private static void parseMcmodInfo(String text, ModInfo info) {
        Object root = Json.parse(text);
        JsonObject first = null;
        if (root instanceof JsonArray array && !array.isEmpty()) {
            first = array.getObject(0);
        } else if (root instanceof JsonObject object) {
            JsonArray modList = object.getArray("modList");
            first = modList != null && !modList.isEmpty() ? modList.getObject(0) : object;
        }
        if (first == null) {
            return;
        }
        info.id = first.getString("modid", "");
        info.name = first.getString("name", info.id);
        info.version = first.getString("version", "");
        info.description = first.getString("description", "");
        info.authors = first.getString("authorList", "");
        finish(info);
    }

    private static void finish(ModInfo info) {
        if (info.name == null || info.name.isBlank()) {
            info.name = info.id == null || info.id.isBlank() ? defaultName(info.fileName) : info.id;
        }
        if (info.id == null) {
            info.id = "";
        }
    }

    /** Mod ids appearing more than once among enabled files. */
    public static List<String> duplicateIds(List<ModInfo> mods) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (ModInfo mod : mods) {
            if (!mod.enabled || mod.id == null || mod.id.isBlank()) {
                continue;
            }
            counts.merge(mod.id.toLowerCase(Locale.ROOT), 1, Integer::sum);
        }
        List<String> duplicates = new ArrayList<>();
        counts.forEach((id, count) -> {
            if (count > 1) {
                duplicates.add(id);
            }
        });
        return duplicates;
    }

    /** Loaders represented among enabled mods, for mismatch warnings. */
    public static List<String> loaders(List<ModInfo> mods) {
        List<String> result = new ArrayList<>();
        for (ModInfo mod : mods) {
            if (mod.enabled && !result.contains(mod.loader)) {
                result.add(mod.loader);
            }
        }
        return result;
    }

    /** Enables or disables a mod by renaming it, returning the new path. */
    public static Path toggle(ModInfo mod) throws IOException {
        Path source = mod.file;
        Path target = mod.enabled
                ? source.resolveSibling(source.getFileName() + ".disabled")
                : source.resolveSibling(mod.fileName.replaceFirst("(?i)\\.disabled$", ""));
        Fs.copy(source, target);
        Files.deleteIfExists(source);
        return target;
    }
}
