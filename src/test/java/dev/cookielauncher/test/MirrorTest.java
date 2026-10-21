package dev.cookielauncher.test;

import dev.cookielauncher.core.net.Mirror;

import java.util.List;

/** Mirror rewriting and candidate ordering: the fallback contract downloads rely on. */
public final class MirrorTest {

    public static void testRewritesMojangHosts() {
        Assert.equal("https://bmclapi2.bangbang93.com/mc/game/version_manifest_v2.json",
                Mirror.toMirror("https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"),
                "piston-meta maps to the mirror root");
        Assert.equal("https://bmclapi2.bangbang93.com/assets/ab/cdef",
                Mirror.toMirror("https://resources.download.minecraft.net/ab/cdef"),
                "asset host maps under /assets");
        Assert.equal("https://bmclapi2.bangbang93.com/maven/com/example/a.jar",
                Mirror.toMirror("https://libraries.minecraft.net/com/example/a.jar"),
                "libraries host maps under /maven");
    }

    public static void testUnknownHostIsUntouched() {
        String url = "https://example.com/some/file.jar";
        Assert.equal(url, Mirror.toMirror(url), "an unknown host is returned unchanged");
    }

    public static void testAutoTriesOfficialFirst() {
        List<String> candidates = Mirror.candidates(
                "https://piston-data.mojang.com/v1/objects/abc/client.jar", Mirror.Policy.AUTO);
        Assert.equal(2, candidates.size(), "two candidates for a known host");
        Assert.equal("https://piston-data.mojang.com/v1/objects/abc/client.jar",
                candidates.get(0), "official URL is tried first in auto mode");
        Assert.contains(candidates.get(1), "bmclapi2.bangbang93.com", "mirror is the fallback");
    }

    public static void testOfficialPolicyHasOneCandidate() {
        List<String> candidates = Mirror.candidates(
                "https://piston-data.mojang.com/v1/objects/abc/client.jar", Mirror.Policy.OFFICIAL);
        Assert.equal(1, candidates.size(), "official policy does not add a mirror");
        Assert.contains(candidates.get(0), "mojang.com", "the only candidate is official");
    }

    public static void testMirrorPolicyTriesMirrorFirst() {
        List<String> candidates = Mirror.candidates(
                "https://piston-data.mojang.com/v1/objects/abc/client.jar", Mirror.Policy.MIRROR);
        Assert.equal(2, candidates.size(), "mirror policy keeps an official fallback");
        Assert.contains(candidates.get(0), "bmclapi2.bangbang93.com", "mirror is tried first");
        Assert.contains(candidates.get(1), "mojang.com", "official is the fallback");
    }

    public static void testUnknownHostYieldsSingleCandidateUnderMirrorPolicy() {
        List<String> candidates = Mirror.candidates(
                "https://example.com/a.jar", Mirror.Policy.MIRROR);
        Assert.equal(1, candidates.size(), "no duplicate when there is nothing to rewrite");
        Assert.equal("https://example.com/a.jar", candidates.get(0), "the original URL is kept");
    }

    public static void testBlankUrlYieldsNothing() {
        Assert.equal(0, Mirror.candidates("", Mirror.Policy.AUTO).size(), "empty url has no candidates");
        Assert.equal(0, Mirror.candidates(null, Mirror.Policy.AUTO).size(), "null url has no candidates");
    }

    public static void testParseAcceptsKnownSpellings() {
        Assert.equal(Mirror.Policy.OFFICIAL, Mirror.parse("mojang"), "mojang maps to official");
        Assert.equal(Mirror.Policy.OFFICIAL, Mirror.parse("OFFICIAL"), "case insensitive");
        Assert.equal(Mirror.Policy.MIRROR, Mirror.parse("bmclapi"), "bmclapi maps to mirror");
        Assert.equal(Mirror.Policy.AUTO, Mirror.parse("nonsense"), "unknown values default to auto");
        Assert.equal(Mirror.Policy.AUTO, Mirror.parse(null), "null defaults to auto");
    }

    public static void testLabelsAreHuman() {
        Assert.contains(Mirror.label(Mirror.Policy.AUTO), "Automatic", "auto label");
        Assert.contains(Mirror.label(Mirror.Policy.OFFICIAL), "Official", "official label");
        Assert.contains(Mirror.label(Mirror.Policy.MIRROR), "BMCLAPI", "mirror label");
    }
}
