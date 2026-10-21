package dev.cookielauncher.core.config;

import dev.cookielauncher.core.json.JsonObject;

/** One game instance: an installed version plus its own directory and overrides. */
public final class Instance {

    public String id = "";
    public String name = "";
    /** Installed version id, e.g. {@code 1.21.1} or {@code 1.21.1-fabric}. */
    public String version = "";
    public String loader = "vanilla";
    public String loaderVersion = "";
    public String gameDir = "";
    public String javaPath = "";
    /** 0 means "inherit the global setting". */
    public int memoryMb = 0;
    public String jvmArgs = "";
    public long createdAt = System.currentTimeMillis();
    public long lastPlayedAt = 0L;
    public long playTimeMs = 0L;
    public String notes = "";
    public String lastExit = "";

    public Instance copy() {
        Instance copy = new Instance();
        copy.id = id;
        copy.name = name;
        copy.version = version;
        copy.loader = loader;
        copy.loaderVersion = loaderVersion;
        copy.gameDir = gameDir;
        copy.javaPath = javaPath;
        copy.memoryMb = memoryMb;
        copy.jvmArgs = jvmArgs;
        copy.createdAt = createdAt;
        copy.lastPlayedAt = lastPlayedAt;
        copy.playTimeMs = playTimeMs;
        copy.notes = notes;
        copy.lastExit = lastExit;
        return copy;
    }

    public JsonObject toJson() {
        return JsonObject.of(
                "id", id,
                "name", name,
                "version", version,
                "loader", loader,
                "loaderVersion", loaderVersion,
                "gameDir", gameDir,
                "javaPath", javaPath,
                "memoryMb", memoryMb,
                "jvmArgs", jvmArgs,
                "createdAt", createdAt,
                "lastPlayedAt", lastPlayedAt,
                "playTimeMs", playTimeMs,
                "notes", notes,
                "lastExit", lastExit);
    }

    public static Instance fromJson(JsonObject object) {
        Instance instance = new Instance();
        instance.id = object.getString("id", "");
        instance.name = object.getString("name", "Instance");
        instance.version = object.getString("version", "");
        instance.loader = object.getString("loader", "vanilla");
        instance.loaderVersion = object.getString("loaderVersion", "");
        instance.gameDir = object.getString("gameDir", "");
        instance.javaPath = object.getString("javaPath", "");
        instance.memoryMb = object.getInt("memoryMb", 0);
        instance.jvmArgs = object.getString("jvmArgs", "");
        instance.createdAt = object.getLong("createdAt", System.currentTimeMillis());
        instance.lastPlayedAt = object.getLong("lastPlayedAt", 0L);
        instance.playTimeMs = object.getLong("playTimeMs", 0L);
        instance.notes = object.getString("notes", "");
        instance.lastExit = object.getString("lastExit", "");
        return instance;
    }

    public boolean isVanilla() {
        return loader == null || loader.isBlank() || "vanilla".equalsIgnoreCase(loader);
    }

    /** Human label for the loader, e.g. "Fabric 0.16.5". */
    public String loaderLabel() {
        if (isVanilla()) {
            return "Vanilla";
        }
        return loaderVersion == null || loaderVersion.isBlank()
                ? loader
                : loader + " " + loaderVersion;
    }

    /** Short description shown under the instance name in the UI. */
    public String subtitle() {
        String versionLabel = version == null || version.isBlank() ? "No version" : version;
        return isVanilla() ? versionLabel : versionLabel + "  ·  " + loaderLabel();
    }
}
