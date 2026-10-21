package dev.cookielauncher.game.loader;

/** Mod loaders Cookie Launcher can install. */
public enum LoaderType {

    VANILLA("vanilla", "Vanilla", ""),
    FABRIC("fabric", "Fabric", "Fabric"),
    QUILT("quilt", "Quilt", "Quilt"),
    FORGE("forge", "Forge", "Forge"),
    NEOFORGE("neoforge", "NeoForge", "NeoForge");

    private final String id;
    private final String display;
    private final String label;

    LoaderType(String id, String display, String label) {
        this.id = id;
        this.display = display;
        this.label = label;
    }

    public String id() {
        return id;
    }

    public String display() {
        return display;
    }

    /** The label used in instance metadata; empty for vanilla. */
    public String label() {
        return label;
    }

    public boolean isModded() {
        return this != VANILLA;
    }

    public static LoaderType parse(String value) {
        if (value == null || value.isBlank()) {
            return VANILLA;
        }
        for (LoaderType type : values()) {
            if (type.id.equalsIgnoreCase(value) || type.name().equalsIgnoreCase(value)) {
                return type;
            }
        }
        return VANILLA;
    }

    /** Loaders that can be installed on top of a given game version. */
    public static LoaderType[] installable() {
        return new LoaderType[]{FABRIC, QUILT, NEOFORGE, FORGE};
    }
}
