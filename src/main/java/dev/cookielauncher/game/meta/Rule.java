package dev.cookielauncher.game.meta;

import dev.cookielauncher.core.json.JsonArray;
import dev.cookielauncher.core.json.JsonObject;
import dev.cookielauncher.core.util.Os;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One Mojang rule entry. A rule list is evaluated in order: the last applicable
 * action wins, and an empty list means "allowed".
 */
public final class Rule {

    public final String action;
    public final String osName;
    public final String osArch;
    public final String osVersion;
    public final Map<String, Boolean> features;

    private Rule(String action, String osName, String osArch, String osVersion,
                 Map<String, Boolean> features) {
        this.action = action;
        this.osName = osName;
        this.osArch = osArch;
        this.osVersion = osVersion;
        this.features = features;
    }

    public static Rule parse(JsonObject object) {
        String action = object.getString("action", "allow");
        String osName = null;
        String osArch = null;
        String osVersion = null;
        JsonObject os = object.getObject("os");
        if (os != null) {
            osName = os.getString("name");
            osArch = os.getString("arch");
            osVersion = os.getString("version");
        }
        Map<String, Boolean> features = new LinkedHashMap<>();
        JsonObject featureObject = object.getObject("features");
        if (featureObject != null) {
            for (String key : featureObject.keys()) {
                features.put(key, featureObject.getBoolean(key, false));
            }
        }
        return new Rule(action, osName, osArch, osVersion, features);
    }

    public boolean applies(Features features) {
        if (osName != null && !osName.isBlank() && !osName.equalsIgnoreCase(Os.mojangOs())) {
            return false;
        }
        if (osArch != null && !osArch.isBlank() && !osArch.equalsIgnoreCase(Os.mojangArch())) {
            return false;
        }
        if (osVersion != null && !osVersion.isBlank()) {
            String current = System.getProperty("os.version", "");
            if (!current.startsWith(osVersion)) {
                return false;
            }
        }
        for (Map.Entry<String, Boolean> entry : this.features.entrySet()) {
            if (features.get(entry.getKey()) != entry.getValue()) {
                return false;
            }
        }
        return true;
    }

    /** Evaluates a rule list against the current platform and feature flags. */
    public static boolean allows(JsonArray rules, Features features) {
        if (rules == null || rules.isEmpty()) {
            return true;
        }
        boolean allowed = false;
        List<JsonObject> objects = rules.objects();
        for (JsonObject object : objects) {
            Rule rule = parse(object);
            if (rule.applies(features)) {
                allowed = "allow".equalsIgnoreCase(rule.action);
            }
        }
        return allowed;
    }
}
