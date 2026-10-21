package dev.cookielauncher.game.meta;

import dev.cookielauncher.core.json.JsonObject;

/** A downloadable artifact from Mojang metadata. */
public final class Artifact {

    public final String path;
    public final String url;
    public final String sha1;
    public final long size;

    public Artifact(String path, String url, String sha1, long size) {
        this.path = path == null ? "" : path;
        this.url = url == null ? "" : url;
        this.sha1 = sha1 == null ? "" : sha1;
        this.size = size;
    }

    public static Artifact parse(JsonObject object) {
        if (object == null) {
            return null;
        }
        return new Artifact(
                object.getString("path", ""),
                object.getString("url", ""),
                object.getString("sha1", ""),
                object.getLong("size", -1L));
    }

    public boolean usable() {
        return !url.isBlank();
    }
}
