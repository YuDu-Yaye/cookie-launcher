package dev.cookielauncher.game.java;

import java.nio.file.Path;

/** A detected or installed Java runtime. */
public record JavaRuntime(Path executable, int major, String version, String vendor, String arch) {

    public boolean supports(int required) {
        return major >= required;
    }

    public boolean is64Bit() {
        return !"x86".equalsIgnoreCase(arch) && !"arm32".equalsIgnoreCase(arch);
    }

    /** Rich label for the GUI, e.g. "Java 21 · Eclipse Temurin · x64". */
    public String label() {
        return "Java " + major + "  \u00b7  " + vendor + "  \u00b7  " + arch;
    }

    /**
     * ASCII-only label for terminals. Windows consoles still default to a legacy
     * code page where the middle dot renders as mojibake, so the CLI never uses
     * {@link #label()}.
     */
    public String plainLabel() {
        return "Java " + major + " (" + vendor + ", " + arch + ")";
    }

    /** e.g. "21.0.2". */
    public String shortVersion() {
        int dash = version.indexOf('+');
        String base = dash > 0 ? version.substring(0, dash) : version;
        int dot = base.indexOf('.');
        return dot > 0 && base.startsWith("1.") ? base.substring(2) : base;
    }
}
