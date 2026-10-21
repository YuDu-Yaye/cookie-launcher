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

    /** e.g. "Java 21 · Eclipse Temurin · x64". */
    public String label() {
        return "Java " + major + "  ·  " + vendor + "  ·  " + arch;
    }

    /** e.g. "21.0.2". */
    public String shortVersion() {
        int dash = version.indexOf('+');
        String base = dash > 0 ? version.substring(0, dash) : version;
        int dot = base.indexOf('.');
        return dot > 0 && base.startsWith("1.") ? base.substring(2) : base;
    }
}
