package dev.cookielauncher.test;

import dev.cookielauncher.core.json.Json;
import dev.cookielauncher.core.json.JsonObject;
import dev.cookielauncher.game.meta.Library;

/** Maven coordinate handling, which decides where every library file lands. */
public final class LibraryTest {

    private static Library library(String name) {
        return Library.parse(JsonObject.of("name", name));
    }

    public static void testPathForSimpleCoordinate() {
        Assert.equal("com/google/guava/guava/32.1.2-jre/guava-32.1.2-jre.jar",
                library("com.google.guava:guava:32.1.2-jre").path(),
                "group dots become directories");
    }

    public static void testPathWithClassifier() {
        Assert.equal("org/lwjgl/lwjgl/3.3.1/lwjgl-3.3.1-natives-windows.jar",
                library("org.lwjgl:lwjgl:3.3.1:natives-windows").path(),
                "classifier is appended before .jar");
    }

    public static void testCoordinatesSplit() {
        String[] coords = library("net.minecraftforge:forge:1.20.1-47.2.0").coordinates();
        Assert.equal("net.minecraftforge", coords[0], "group");
        Assert.equal("forge", coords[1], "artifact");
        Assert.equal("1.20.1-47.2.0", coords[2], "version");
        Assert.equal("", coords[3], "no classifier");
    }

    public static void testArtifactFallsBackToBaseUrl() {
        String json = "{\"name\":\"com.example:thing:1.0\","
                + "\"url\":\"https://libraries.minecraft.net/\"}";
        Library lib = Library.parse(Json.parseObject(json));
        var artifact = lib.artifact();
        Assert.notNull(artifact, "artifact is synthesised from the base url");
        Assert.equal("https://libraries.minecraft.net/com/example/thing/1.0/thing-1.0.jar",
                artifact.url, "url is joined from base plus maven path");
    }

    public static void testExplicitDownloadsWin() {
        String json = "{\"name\":\"com.example:thing:1.0\","
                + "\"downloads\":{\"artifact\":{\"path\":\"custom/path.jar\","
                + "\"url\":\"https://cdn.example/path.jar\",\"sha1\":\"abc\",\"size\":42}}}";
        Library lib = Library.parse(Json.parseObject(json));
        var artifact = lib.artifact();
        Assert.equal("custom/path.jar", artifact.path, "explicit path is preserved");
        Assert.equal("https://cdn.example/path.jar", artifact.url, "explicit url wins");
        Assert.equal(42L, artifact.size, "declared size is preserved");
    }

    public static void testNoUrlMeansNoArtifact() {
        Library lib = library("com.example:thing:1.0");
        var artifact = lib.artifact();
        Assert.isTrue(artifact == null || !artifact.usable(),
                "without a url there is nothing to download");
    }

    public static void testRulesAreParsed() {
        String json = "{\"name\":\"com.example:windows-only:1.0\",\"url\":\"https://x/\","
                + "\"rules\":[{\"action\":\"allow\",\"os\":{\"name\":\"windows\"}}]}";
        Library lib = Library.parse(Json.parseObject(json));
        Assert.equal(1, lib.rules.size(), "one rule is recorded");
    }

    public static void testExtractExcludesAreParsed() {
        String json = "{\"name\":\"com.example:natives:1.0\",\"url\":\"https://x/\","
                + "\"extract\":{\"exclude\":[\"META-INF/\"]}}";
        Library lib = Library.parse(Json.parseObject(json));
        Assert.equal(1, lib.extractExcludes().size(), "one exclusion is recorded");
        Assert.equal("META-INF/", lib.extractExcludes().get(0), "exclusion value is preserved");
    }

    public static void testToStringIsTheCoordinate() {
        Assert.equal("com.example:thing:1.0", library("com.example:thing:1.0").toString(),
                "toString returns the maven coordinate");
    }
}
