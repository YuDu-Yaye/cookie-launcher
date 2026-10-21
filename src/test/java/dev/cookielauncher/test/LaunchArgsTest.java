package dev.cookielauncher.test;

import dev.cookielauncher.core.config.Account;
import dev.cookielauncher.core.json.JsonObject;
import dev.cookielauncher.game.launch.LaunchBuilder;
import dev.cookielauncher.game.launch.LaunchCommand;
import dev.cookielauncher.game.launch.LaunchRequest;
import dev.cookielauncher.game.meta.VersionMeta;

import java.nio.file.Path;
import java.util.List;

/**
 * Command line assembly: placeholder substitution, conditional arguments, quoting
 * and classpath ordering. This is the code path that decides whether the game
 * starts at all, so it is tested without touching the network.
 */
public final class LaunchArgsTest {

    private static LaunchRequest requestFor(String versionJson) {
        LaunchRequest request = new LaunchRequest();
        request.meta = VersionMeta.parse(
                dev.cookielauncher.core.json.Json.parseObject(versionJson));
        request.account = Account.offline("Steve");
        request.javaExecutable = Path.of("/opt/java/bin/java");
        request.gameDirectory = Path.of("/data/.minecraft");
        request.assetsDirectory = Path.of("/data/.minecraft/assets");
        request.librariesDirectory = Path.of("/data/.minecraft/libraries");
        request.nativesDirectory = Path.of("/data/natives");
        request.clientJar = null;
        request.minMemoryMb = 512;
        request.maxMemoryMb = 4096;
        return request;
    }

    private static String modernJson(String extraArguments) {
        return "{\"id\":\"1.21.1\",\"type\":\"release\",\"mainClass\":\"net.minecraft.client.main.Main\","
                + "\"assets\":\"17\",\"javaVersion\":{\"majorVersion\":21},"
                + "\"arguments\":{\"jvm\":[\"-Djava.library.path=${natives_directory}\",\"-cp\","
                + "\"${classpath}\"],\"game\":[\"--username\",\"${auth_player_name}\","
                + "\"--version\",\"${version_name}\",\"--uuid\",\"${auth_uuid}\","
                + "\"--accessToken\",\"${auth_access_token}\""
                + (extraArguments == null ? "" : "," + extraArguments) + "]}}";
    }

    public static void testPlaceholdersAreSubstituted() {
        LaunchCommand command = LaunchBuilder.build(requestFor(modernJson(null)));
        String joined = String.join(" ", command.parts());
        Assert.contains(joined, "Steve", "player name is substituted");
        Assert.contains(joined, "1.21.1", "version name is substituted");
        Assert.contains(joined, Account.offlineUuid("Steve"), "uuid is substituted");
    }

    public static void testJvmArgumentsComeBeforeMainClass() {
        LaunchCommand command = LaunchBuilder.build(requestFor(modernJson(null)));
        List<String> arguments = command.parts();
        int mainClass = arguments.indexOf("net.minecraft.client.main.Main");
        Assert.atLeast(0, mainClass, "main class is present");
        Assert.isTrue(arguments.indexOf("-cp") < mainClass, "-cp appears before the main class");
        Assert.isTrue(arguments.indexOf("--username") > mainClass,
                "game arguments come after the main class");
    }

    public static void testClasspathTokenIsNotLeftUnresolved() {
        LaunchCommand command = LaunchBuilder.build(requestFor(modernJson(null)));
        String joined = String.join(" ", command.parts());
        Assert.notContains(joined, "${classpath}", "classpath placeholder is resolved");
        Assert.notContains(joined, "${natives_directory}", "natives placeholder is resolved");
    }

    public static void testLegacyMinecraftArgumentsAreSplit() {
        String legacy = "{\"id\":\"1.8.9\",\"mainClass\":\"net.minecraft.client.main.Main\","
                + "\"minecraftArguments\":\"--username ${auth_player_name} --gameDir "
                + "${game_directory} --version ${version_name}\"}";
        LaunchCommand command = LaunchBuilder.build(requestFor(legacy));
        List<String> arguments = command.parts();
        Assert.contains(String.join(" ", arguments), "--username Steve",
                "legacy string is split into separate tokens");
        Assert.isTrue(arguments.contains("--gameDir"), "legacy tokens keep their flags");
    }

    public static void testMemoryArgumentsAreAppliedForLegacyMetadata() {
        LaunchCommand command = LaunchBuilder.build(requestFor(
                "{\"id\":\"1.8.9\",\"mainClass\":\"a\",\"minecraftArguments\":\"--username x\"}"));
        String joined = String.join(" ", command.parts());
        Assert.contains(joined, "-Xmx4096M", "max memory is applied");
        Assert.contains(joined, "-Xms512M", "min memory is applied");
    }

    public static void testConditionalArgumentsWithoutRulesAreIncluded() {
        String json = modernJson("\"--demo\"");
        LaunchCommand command = LaunchBuilder.build(requestFor(json));
        Assert.isTrue(command.parts().contains("--demo"), "plain string arguments are kept");
    }

    public static void testWindowsOnlyRuleIsEvaluated() {
        String json = modernJson("{\"rules\":[{\"action\":\"allow\",\"os\":{\"name\":\"windows\"}}],"
                + "\"value\":\"--win-only\"}");
        LaunchCommand command = LaunchBuilder.build(requestFor(json));
        boolean windows = dev.cookielauncher.core.util.Os.isWindows();
        Assert.equal(windows, command.parts().contains("--win-only"),
                "a windows-only argument is present exactly on Windows");
    }

    public static void testFeatureGatedArgumentRespectsFlags() {
        // has_custom_resolution is true when both window dimensions are positive,
        // which LaunchRequest defaults to, so this entry must be included.
        String json = modernJson("{\"rules\":[{\"action\":\"allow\","
                + "\"features\":{\"has_custom_resolution\":true}}],"
                + "\"value\":[\"--width\",\"1280\",\"--height\",\"720\"]}");
        LaunchCommand command = LaunchBuilder.build(requestFor(json));
        Assert.isTrue(command.parts().contains("--width"),
                "feature-gated argument appears when the feature is on");
        Assert.isTrue(command.parts().contains("720"), "both values are expanded");
    }

    public static void testFullscreenAddsFlagOnce() {
        LaunchRequest request = requestFor(modernJson(null));
        request.fullscreen = true;
        request.extraGameArgs = "--fullscreen";
        LaunchCommand command = LaunchBuilder.build(request);
        long count = command.parts().stream().filter("--fullscreen"::equals).count();
        Assert.equal(1L, count, "fullscreen is not duplicated");
    }

    public static void testQuotedExtraArgumentsSurvive() {
        LaunchRequest request = requestFor(modernJson(null));
        request.extraJvmArgs = "-Dcustom=\"a b\" -Dother=1";
        LaunchCommand command = LaunchBuilder.build(request);
        Assert.isTrue(command.parts().contains("-Dcustom=a b"),
                "quoted value stays one token");
        Assert.isTrue(command.parts().contains("-Dother=1"), "second flag is separated");
    }

    public static void testSplitHandlesQuotes() {
        List<String> tokens = LaunchBuilder.split("a \"b c\" d");
        Assert.equal(3, tokens.size(), "quoted token is not split");
        Assert.equal("b c", tokens.get(1), "quotes are removed from the value");
    }

    public static void testBlankSplitYieldsNothing() {
        Assert.equal(0, LaunchBuilder.split("").size(), "empty string has no tokens");
        Assert.equal(0, LaunchBuilder.split(null).size(), "null has no tokens");
        Assert.equal(0, LaunchBuilder.split("   ").size(), "whitespace only has no tokens");
    }

    public static void testServerBecomesQuickPlayOnSupportingVersions() {
        LaunchRequest request = requestFor(modernJson(
                "{\"rules\":[{\"action\":\"allow\",\"features\":{\"has_quick_plays_support\":true}}],"
                        + "\"value\":[\"--quickPlayMultiplayer\",\"${quickPlayMultiplayer}\"]}"));
        request.server = "mc.example.com";
        String joined = String.join(" ", LaunchBuilder.build(request).parts());
        Assert.contains(joined, "--quickPlayMultiplayer", "the flag is passed");
        Assert.contains(joined, "mc.example.com:25565", "a bare host gets the default port");
    }

    public static void testQuickPlayIsNotForcedOnOldVersions() {
        // 1.19 and older reject unknown flags, so the launcher must not invent one.
        LaunchRequest request = requestFor(modernJson(null));
        request.server = "mc.example.com";
        String joined = String.join(" ", LaunchBuilder.build(request).parts());
        Assert.notContains(joined, "--quickPlayMultiplayer",
                "a version without quick play support never receives the flag");
    }

    public static void testQuickPlayAddressKeepsExplicitPort() {
        Assert.equal("mc.example.com:25565", LaunchBuilder.quickPlayAddress("mc.example.com"),
                "a bare host gains the default port");
        Assert.equal("mc.example.com:1234", LaunchBuilder.quickPlayAddress("mc.example.com:1234"),
                "an explicit port is preserved");
        Assert.equal("", LaunchBuilder.quickPlayAddress(""), "an empty address stays empty");
    }

    public static void testMissingMainClassIsRejected() {
        Assert.fails(IllegalStateException.class,
                () -> LaunchBuilder.build(requestFor("{\"id\":\"x\",\"mainClass\":\"\"}")),
                "a version without a main class cannot launch");
    }

    public static void testMissingMetadataIsRejected() {
        LaunchRequest request = new LaunchRequest();
        request.javaExecutable = Path.of("/java");
        request.gameDirectory = Path.of("/g");
        Assert.fails(IllegalArgumentException.class, () -> LaunchBuilder.build(request),
                "launch requires version metadata");
    }

    public static void testAccessTokenPlaceholderUsesZeroForOffline() {
        LaunchCommand command = LaunchBuilder.build(requestFor(modernJson(null)));
        List<String> arguments = command.parts();
        int index = arguments.indexOf("--accessToken");
        Assert.atLeast(0, index, "access token flag exists");
        Assert.equal("0", arguments.get(index + 1),
                "offline accounts pass 0 instead of an empty token");
    }

    public static void testVersionJsonWithJavaVersionIsParsed() {
        VersionMeta meta = VersionMeta.parse(
                dev.cookielauncher.core.json.Json.parseObject(modernJson(null)));
        Assert.equal(21, meta.javaMajor, "javaVersion.majorVersion is read");
        Assert.equal("17", meta.assets, "asset index id is read");
    }

    public static void testInheritedVersionMergesParent() {
        VersionMeta parent = VersionMeta.parse(dev.cookielauncher.core.json.Json.parseObject(
                "{\"id\":\"1.21.1\",\"mainClass\":\"net.minecraft.client.main.Main\","
                        + "\"assets\":\"17\",\"javaVersion\":{\"majorVersion\":21},"
                        + "\"libraries\":[{\"name\":\"a:b:1\"}]}"));
        VersionMeta child = VersionMeta.parse(dev.cookielauncher.core.json.Json.parseObject(
                "{\"id\":\"1.21.1-fabric\",\"inheritsFrom\":\"1.21.1\","
                        + "\"libraries\":[{\"name\":\"c:d:2\"}]}"));
        child.mergeFrom(parent);
        Assert.equal("net.minecraft.client.main.Main", child.mainClass,
                "child inherits the main class");
        Assert.equal(21, child.javaMajor, "child inherits the java version");
        Assert.equal(2, child.libraries.size(), "libraries are unioned");
        Assert.equal("c:d:2", child.libraries.get(0).name, "child libraries come first");
    }

    public static void testJsonObjectRoundTripInRequest() {
        JsonObject object = JsonObject.of("id", "1.20.1");
        Assert.equal("1.20.1", object.getString("id"), "sanity check on the json helper");
    }
}
