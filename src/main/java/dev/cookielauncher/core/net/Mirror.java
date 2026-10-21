package dev.cookielauncher.core.net;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Download mirror policy.
 *
 * <p>Mojang's CDN is authoritative and served by a global anycast network. The
 * BMCLAPI project mirrors the same bytes for mainland China. The launcher tries
 * the official host first and falls back per file, so a mirror outage degrades
 * speed instead of breaking installation.</p>
 */
public final class Mirror {

    public enum Policy {
        /** Official first, mirror as fallback. */
        AUTO,
        /** Official only. */
        OFFICIAL,
        /** Mirror first, official as fallback. */
        MIRROR
    }

    private static final String[][] PREFIXES = {
            {"https://piston-meta.mojang.com", "https://bmclapi2.bangbang93.com"},
            {"https://launchermeta.mojang.com", "https://bmclapi2.bangbang93.com"},
            {"https://launcher.mojang.com", "https://bmclapi2.bangbang93.com"},
            {"https://piston-data.mojang.com", "https://bmclapi2.bangbang93.com"},
            {"https://resources.download.minecraft.net", "https://bmclapi2.bangbang93.com/assets"},
            {"https://libraries.minecraft.net", "https://bmclapi2.bangbang93.com/maven"},
            {"https://files.minecraftforge.net/maven", "https://bmclapi2.bangbang93.com/maven"},
            {"https://maven.minecraftforge.net", "https://bmclapi2.bangbang93.com/maven"},
            {"https://maven.neoforged.net/releases", "https://bmclapi2.bangbang93.com/maven"},
            {"https://maven.fabricmc.net", "https://bmclapi2.bangbang93.com/maven"},
            {"https://maven.quiltmc.org/repository/release", "https://bmclapi2.bangbang93.com/maven"},
            {"https://meta.fabricmc.net", "https://bmclapi2.bangbang93.com/fabric-meta"},
            {"https://meta.quiltmc.org", "https://bmclapi2.bangbang93.com/quilt-meta"},
    };

    private Mirror() {
    }

    /** Rewrites an official URL onto the mirror when it is a known host. */
    public static String toMirror(String url) {
        if (url == null || url.isBlank()) {
            return url;
        }
        for (String[] pair : PREFIXES) {
            if (url.startsWith(pair[0])) {
                return pair[1] + url.substring(pair[0].length());
            }
        }
        return url;
    }

    /** Ordered candidate URLs for one file under the given policy. */
    public static List<String> candidates(String officialUrl, Policy policy) {
        List<String> result = new ArrayList<>(2);
        if (officialUrl == null || officialUrl.isBlank()) {
            return result;
        }
        String mirrored = toMirror(officialUrl);
        boolean mirrorAvailable = !mirrored.equals(officialUrl);
        switch (policy) {
            case OFFICIAL -> result.add(officialUrl);
            case MIRROR -> {
                result.add(mirrored);
                if (mirrorAvailable) {
                    result.add(officialUrl);
                }
            }
            case AUTO -> {
                result.add(officialUrl);
                if (mirrorAvailable) {
                    result.add(mirrored);
                }
            }
        }
        return result;
    }

    public static String label(Policy policy) {
        return switch (policy) {
            case AUTO -> "Automatic (official, then mirror)";
            case OFFICIAL -> "Official Mojang CDN";
            case MIRROR -> "BMCLAPI mirror";
        };
    }

    public static Policy parse(String value) {
        if (value == null) {
            return Policy.AUTO;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "official", "mojang" -> Policy.OFFICIAL;
            case "mirror", "bmclapi" -> Policy.MIRROR;
            default -> Policy.AUTO;
        };
    }
}
