package dev.cookielauncher.game.mod;

import java.nio.file.Path;

/** Metadata read from a mod jar without loading any mod code. */
public final class ModInfo {

    public Path file;
    public String fileName = "";
    public String id = "";
    public String name = "";
    public String version = "";
    public String description = "";
    public String authors = "";
    public String loader = "unknown";
    public boolean enabled = true;
    public long sizeBytes = 0L;

    public String displayName() {
        return name == null || name.isBlank() ? fileName : name;
    }

    public String displayVersion() {
        return version == null || version.isBlank() ? "" : version;
    }

    /** One-line summary for a list row. */
    public String summary() {
        StringBuilder builder = new StringBuilder();
        builder.append(fileName);
        if (sizeBytes > 0) {
            builder.append("  -  ").append(dev.cookielauncher.core.util.Fmt.bytes(sizeBytes));
        }
        if (authors != null && !authors.isBlank()) {
            builder.append("  -  ").append(authors);
        }
        return builder.toString();
    }

    @Override
    public String toString() {
        return displayName() + " " + displayVersion();
    }
}
