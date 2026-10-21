package dev.cookielauncher.core.net;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * One file to fetch: a destination, an optional integrity hash, a size hint and
 * an ordered list of source URLs (mirror fallbacks).
 */
public final class RemoteFile {

    public final Path target;
    public final String sha1;
    public final long size;
    private final List<String> urls;

    public RemoteFile(Path target, String sha1, long size, List<String> urls) {
        this.target = target;
        this.sha1 = sha1 == null || sha1.isBlank() ? null : sha1.trim().toLowerCase();
        this.size = size;
        this.urls = urls == null ? List.of() : new ArrayList<>(urls);
    }

    public static RemoteFile of(Path target, String url) {
        return new RemoteFile(target, null, -1L, List.of(url));
    }

    public static RemoteFile verified(Path target, String sha1, long size, List<String> urls) {
        return new RemoteFile(target, sha1, size, urls);
    }

    public List<String> urls() {
        return urls;
    }

    public String primaryUrl() {
        return urls.isEmpty() ? "" : urls.get(0);
    }

    public String fileName() {
        return target.getFileName().toString();
    }

    public boolean usable() {
        return !urls.isEmpty() && target != null;
    }

    @Override
    public String toString() {
        return fileName() + " <- " + primaryUrl();
    }
}
