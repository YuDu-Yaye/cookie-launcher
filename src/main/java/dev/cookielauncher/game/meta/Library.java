package dev.cookielauncher.game.meta;

import dev.cookielauncher.core.json.JsonObject;
import dev.cookielauncher.core.util.Os;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A Maven-style library dependency, including native classifiers and rules.
 *
 * <p>Modern metadata lists explicit {@code downloads.artifact} entries; older
 * metadata only gives a base {@code url}, in which case the artifact path is
 * derived from the Maven coordinates. Both are supported.</p>
 */
public final class Library {

    public final String name;
    public final String baseUrl;
    public final Map<String, Artifact> downloads;
    public final Map<String, String> natives;
    public final List<JsonObject> rules;
    public final JsonObject extract;

    private Library(String name, String baseUrl, Map<String, Artifact> downloads,
                    Map<String, String> natives, List<JsonObject> rules, JsonObject extract) {
        this.name = name;
        this.baseUrl = baseUrl;
        this.downloads = downloads;
        this.natives = natives;
        this.rules = rules;
        this.extract = extract;
    }

    public static Library parse(JsonObject object) {
        String name = object.getString("name", "");
        String baseUrl = object.getString("url", "");
        Map<String, Artifact> downloads = new LinkedHashMap<>();
        JsonObject downloadObject = object.getObject("downloads");
        if (downloadObject != null) {
            for (String key : downloadObject.keys()) {
                Artifact artifact = Artifact.parse(downloadObject.getObject(key));
                if (artifact != null) {
                    downloads.put(key, artifact);
                }
            }
        }
        Map<String, String> natives = new LinkedHashMap<>();
        JsonObject nativesObject = object.getObject("natives");
        if (nativesObject != null) {
            for (String key : nativesObject.keys()) {
                natives.put(key, nativesObject.getString(key, ""));
            }
        }
        List<JsonObject> rules = new ArrayList<>();
        var ruleArray = object.getArray("rules");
        if (ruleArray != null) {
            rules.addAll(ruleArray.objects());
        }
        return new Library(name, baseUrl, downloads, natives, rules, object.getObject("extract"));
    }

    public boolean allowed(Features features) {
        if (rules.isEmpty()) {
            return true;
        }
        boolean allowed = false;
        for (JsonObject object : rules) {
            Rule rule = Rule.parse(object);
            if (rule.applies(features)) {
                allowed = "allow".equalsIgnoreCase(rule.action);
            }
        }
        return allowed;
    }

    /** Maven coordinate parts: group, artifact, version, classifier (may be empty). */
    public String[] coordinates() {
        String[] parts = name.split(":");
        return new String[]{
                parts.length > 0 ? parts[0] : "",
                parts.length > 1 ? parts[1] : "",
                parts.length > 2 ? parts[2] : "",
                parts.length > 3 ? parts[3] : ""};
    }

    /** Relative path inside the libraries directory, e.g. {@code com/foo/bar/1.0/bar-1.0.jar}. */
    public String path() {
        String[] coords = coordinates();
        StringBuilder builder = new StringBuilder();
        builder.append(coords[0].replace('.', '/')).append('/')
                .append(coords[1]).append('/')
                .append(coords[2]).append('/')
                .append(coords[1]).append('-').append(coords[2]);
        if (!coords[3].isBlank()) {
            builder.append('-').append(coords[3]);
        }
        return builder.append(".jar").toString();
    }

    public Artifact artifact() {
        Artifact artifact = downloads.get("artifact");
        if (artifact != null && artifact.usable()) {
            return artifact;
        }
        if (baseUrl == null || baseUrl.isBlank()) {
            return null;
        }
        String base = baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
        return new Artifact(path(), base + path(), "", -1L);
    }

    /** The classifier token for this platform, or null when the library has no natives. */
    public String nativeClassifier() {
        String value = natives.get(Os.mojangOs());
        if (value == null || value.isBlank()) {
            return null;
        }
        String archToken = switch (Os.mojangArch()) {
            case "x86" -> "32";
            case "arm64" -> "arm64";
            default -> "64";
        };
        return value.replace("${arch}", archToken);
    }

    public boolean hasNatives() {
        return nativeClassifier() != null;
    }

    public Artifact nativeArtifact() {
        String classifier = nativeClassifier();
        if (classifier == null) {
            return null;
        }
        Artifact direct = downloads.get(classifier);
        if (direct == null) {
            direct = downloads.get("classifiers." + classifier);
        }
        if (direct != null && direct.usable()) {
            return direct;
        }
        if (baseUrl == null || baseUrl.isBlank()) {
            return null;
        }
        String[] coords = coordinates();
        String path = coords[0].replace('.', '/') + "/" + coords[1] + "/" + coords[2] + "/"
                + coords[1] + "-" + coords[2] + "-" + classifier + ".jar";
        String base = baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
        return new Artifact(path, base + path, "", -1L);
    }

    public List<String> extractExcludes() {
        if (extract == null) {
            return List.of();
        }
        var array = extract.getArray("exclude");
        return array == null ? List.of() : array.strings();
    }

    @Override
    public String toString() {
        return name;
    }
}
