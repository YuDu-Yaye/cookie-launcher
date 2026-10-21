package dev.cookielauncher.game.meta;

import dev.cookielauncher.core.json.Json;
import dev.cookielauncher.core.json.JsonArray;
import dev.cookielauncher.core.json.JsonObject;
import dev.cookielauncher.core.util.Fs;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Parsed version metadata with {@code inheritsFrom} resolution.
 *
 * <p>Mod loaders publish a thin JSON that inherits a vanilla version. Resolution
 * is recursive and merge rules follow the loader conventions: the child wins for
 * scalars, libraries are unioned by coordinates (child first), and argument lists
 * are concatenated with the parent's entries first.</p>
 */
public final class VersionMeta {

    public String id = "";
    public String type = "release";
    public String mainClass = "";
    public String assets = "";
    public String inheritsFrom = "";
    public String releaseTime = "";
    public int javaMajor = 8;
    public boolean javaMajorExplicit;
    public String javaComponent = "";
    public JsonObject logging;
    public JsonObject arguments;
    public String minecraftArguments;
    public final Map<String, Artifact> downloads = new LinkedHashMap<>();
    public final List<Library> libraries = new ArrayList<>();
    public Artifact assetIndex;
    public String assetIndexId = "";

    public static VersionMeta parse(JsonObject object) {
        VersionMeta meta = new VersionMeta();
        meta.id = object.getString("id", "");
        meta.type = object.getString("type", "release");
        meta.mainClass = object.getString("mainClass", "");
        meta.assets = object.getString("assets", "");
        meta.inheritsFrom = object.getString("inheritsFrom", "");
        meta.releaseTime = object.getString("releaseTime", "");
        meta.logging = object.getObject("logging");
        meta.arguments = object.getObject("arguments");
        meta.minecraftArguments = object.getString("minecraftArguments");

        JsonObject javaVersion = object.getObject("javaVersion");
        if (javaVersion != null) {
            meta.javaMajor = javaVersion.getInt("majorVersion", 8);
            meta.javaMajorExplicit = true;
            meta.javaComponent = javaVersion.getString("component", "");
        }

        JsonObject downloadObject = object.getObject("downloads");
        if (downloadObject != null) {
            for (String key : downloadObject.keys()) {
                Artifact artifact = Artifact.parse(downloadObject.getObject(key));
                if (artifact != null) {
                    meta.downloads.put(key, artifact);
                }
            }
        }

        JsonArray libraryArray = object.getArray("libraries");
        if (libraryArray != null) {
            for (JsonObject libraryObject : libraryArray.objects()) {
                Library library = Library.parse(libraryObject);
                if (!library.name.isBlank()) {
                    meta.libraries.add(library);
                }
            }
        }

        JsonObject index = object.getObject("assetIndex");
        if (index != null) {
            meta.assetIndex = Artifact.parse(index);
            meta.assetIndexId = index.getString("id", "");
        }
        return meta;
    }

    public static VersionMeta load(Path file) throws IOException {
        return parse(Json.parseObject(file));
    }

    /** Loads a version and resolves its full inheritance chain. */
    public static VersionMeta resolve(Path versionsDir, String id) throws IOException {
        return resolve(versionsDir, id, new LinkedHashSet<>());
    }

    private static VersionMeta resolve(Path versionsDir, String id, Set<String> visited) throws IOException {
        if (!visited.add(id)) {
            throw new IOException("Circular inheritsFrom chain at " + id);
        }
        Path file = versionsDir.resolve(id).resolve(id + ".json");
        if (!Files.isRegularFile(file)) {
            throw new IOException("Version " + id + " is not installed (missing " + file + ")");
        }
        VersionMeta child = load(file);
        if (child.inheritsFrom != null && !child.inheritsFrom.isBlank()) {
            VersionMeta parent = resolve(versionsDir, child.inheritsFrom, visited);
            child.mergeFrom(parent);
        }
        child.id = id;
        return child;
    }

    /** Fills this child's gaps from an already-resolved parent. */
    public void mergeFrom(VersionMeta parent) {
        if (mainClass.isBlank()) {
            mainClass = parent.mainClass;
        }
        if (assets.isBlank()) {
            assets = parent.assets;
        }
        if (assetIndex == null) {
            assetIndex = parent.assetIndex;
            assetIndexId = parent.assetIndexId;
        }
        if (logging == null) {
            logging = parent.logging;
        }
        if (releaseTime.isBlank()) {
            releaseTime = parent.releaseTime;
        }
        if (!javaMajorExplicit) {
            javaMajor = parent.javaMajor;
            javaComponent = parent.javaComponent;
        }
        parent.downloads.forEach(downloads::putIfAbsent);

        Set<String> seen = new LinkedHashSet<>();
        for (Library library : libraries) {
            seen.add(key(library));
        }
        for (Library library : parent.libraries) {
            if (seen.add(key(library))) {
                libraries.add(library);
            }
        }

        arguments = mergeArguments(parent.arguments, arguments);
        if ((minecraftArguments == null || minecraftArguments.isBlank())
                && parent.minecraftArguments != null) {
            minecraftArguments = parent.minecraftArguments;
        }
    }

    private static String key(Library library) {
        return library.name;
    }

    private static JsonObject mergeArguments(JsonObject parent, JsonObject child) {
        if (parent == null) {
            return child;
        }
        if (child == null) {
            return parent;
        }
        JsonObject merged = new JsonObject();
        for (String section : new String[]{"game", "jvm"}) {
            JsonArray parentArray = parent.getArray(section);
            JsonArray childArray = child.getArray(section);
            JsonArray combined = new JsonArray();
            if (parentArray != null) {
                parentArray.forEach(combined::add);
            }
            if (childArray != null) {
                childArray.forEach(combined::add);
            }
            if (!combined.isEmpty()) {
                merged.put(section, combined);
            }
        }
        for (String key : child.keys()) {
            if (!merged.has(key)) {
                merged.put(key, child.get(key));
            }
        }
        return merged;
    }

    public Artifact client() {
        return downloads.get("client");
    }

    /**
     * The logging configuration file a version declares.
     *
     * <p>Mojang describes it as {@code {"id": "...", "file": {"url", "sha1", "size"}}}
     * — note there is no {@code path}, so the filename must be derived from the id.
     * Returning an empty path here previously made the installer write the file at
     * {@code cache/logging}, which then collided with the directory of the same name.</p>
     */
    public Artifact loggingConfig() {
        if (logging == null) {
            return null;
        }
        JsonObject clientConfig = logging.getObject("client");
        if (clientConfig == null) {
            return null;
        }
        Artifact file = Artifact.parse(clientConfig.getObject("file"));
        if (file == null || !file.usable()) {
            return null;
        }
        return file;
    }

    /** Filename for the logging configuration, e.g. {@code client-1.12.xml}. */
    public String loggingConfigName() {
        if (logging == null) {
            return "";
        }
        JsonObject clientConfig = logging.getObject("client");
        if (clientConfig == null) {
            return "";
        }
        String id = clientConfig.getString("id", "");
        if (!id.isBlank()) {
            return id;
        }
        // Modern manifests omit "id" entirely and give only url/sha1/size, so the
        // filename is derived from the download URL instead.
        String url = clientConfig.getObject("file") == null
                ? "" : clientConfig.getObject("file").getString("url", "");
        if (url.isBlank()) {
            return "";
        }
        int slash = url.lastIndexOf('/');
        return slash < 0 ? url : url.substring(slash + 1);
    }

    /** The JVM argument template that points at the logging configuration. */
    public String loggingArgument() {
        if (logging == null) {
            return "";
        }
        JsonObject clientConfig = logging.getObject("client");
        return clientConfig == null ? "" : clientConfig.getString("argument", "");
    }

    @Override
    public String toString() {
        return id + " (Java " + javaMajor + ", " + libraries.size() + " libraries)";
    }
}
