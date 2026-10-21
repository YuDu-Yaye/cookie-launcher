package dev.cookielauncher.game.loader;

/** One installable build of a mod loader. */
public record LoaderVersion(LoaderType type, String version, String gameVersion, boolean stable) {

    /** Version id written to {@code versions/}, e.g. {@code 1.21.1-fabric}. */
    public String versionId() {
        return gameVersion + "-" + type.id();
    }

    /** Human label, e.g. "Fabric 0.16.5". */
    public String label() {
        return type.display() + " " + version;
    }

    public static LoaderVersion of(LoaderType type, String version, String gameVersion, boolean stable) {
        return new LoaderVersion(type, version, gameVersion, stable);
    }
}
