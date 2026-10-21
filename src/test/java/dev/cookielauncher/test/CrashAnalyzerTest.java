package dev.cookielauncher.test;

import dev.cookielauncher.game.diagnose.CrashAnalyzer;
import dev.cookielauncher.game.diagnose.Diagnosis;
import dev.cookielauncher.game.diagnose.Rule;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The crash rule engine. Every rule must fire on its own signature, and a healthy
 * log must produce nothing. This is the feature no competing launcher ships, so it
 * is held to a strict test.
 */
public final class CrashAnalyzerTest {

    public static void testRuleIdsAreUnique() {
        Set<String> ids = new HashSet<>();
        for (Rule rule : CrashAnalyzer.rules()) {
            Assert.isTrue(ids.add(rule.id()), "duplicate rule id: " + rule.id());
        }
    }

    public static void testEveryRuleHasExplanationAndFix() {
        for (Rule rule : CrashAnalyzer.rules()) {
            Assert.isFalse(rule.title().isBlank(), "rule " + rule.id() + " has a title");
            Assert.isFalse(rule.explanation().isBlank(), "rule " + rule.id() + " explains the cause");
            Assert.atLeast(1, rule.fixes().size(), "rule " + rule.id() + " offers a fix");
            Assert.atLeast(1, rule.needles().size(), "rule " + rule.id() + " has a signature");
        }
    }

    public static void testEveryRuleFiresOnItsOwnSignature() {
        for (Rule rule : CrashAnalyzer.rules()) {
            String fixture = "[12:00:00] [main/ERROR] " + rule.needles().get(0);
            List<Diagnosis> diagnoses = CrashAnalyzer.analyze(fixture);
            boolean found = diagnoses.stream().anyMatch(d -> d.title().equals(rule.title()));
            Assert.isTrue(found, "rule " + rule.id() + " fires on its own signature");
        }
    }

    public static void testCleanLogProducesNoDiagnosis() {
        String clean = """
                [12:00:00] [main/INFO] Setting user: Steve
                [12:00:01] [main/INFO] Backend library: LWJGL version 3.3.3
                [12:00:02] [Render thread/INFO] OpenGL debug message
                [12:00:03] [Render thread/INFO] Narrator library for x64 loaded
                [12:00:04] [Server thread/INFO] Preparing spawn area: 100%
                [12:00:05] [Render thread/INFO] Sound engine started
                """;
        Assert.equal(0, CrashAnalyzer.analyze(clean).size(), "a healthy log yields no diagnosis");
    }

    public static void testJavaTooOldIsBlocker() {
        String log = "java.lang.UnsupportedClassVersionError: net/minecraft/client/main/Main "
                + "has been compiled by a more recent version of the Java Runtime "
                + "(class file version 65.0), this version only recognizes 52.0";
        List<Diagnosis> diagnoses = CrashAnalyzer.analyze(log);
        Assert.atLeast(1, diagnoses.size(), "an old JVM is detected");
        Assert.isTrue(diagnoses.get(0).isBlocker(), "wrong Java is a blocker");
        Assert.contains(diagnoses.get(0).render().toLowerCase(), "java",
                "the explanation mentions Java");
    }

    public static void testOutOfMemoryIsDetected() {
        List<Diagnosis> diagnoses = CrashAnalyzer.analyze(
                "java.lang.OutOfMemoryError: Java heap space");
        Assert.atLeast(1, diagnoses.size(), "heap exhaustion is detected");
        boolean mentionsMemory = diagnoses.stream()
                .anyMatch(d -> d.render().toLowerCase().contains("memory"));
        Assert.isTrue(mentionsMemory, "the fix mentions memory settings");
    }

    public static void testMissingDependencyIsBlocker() {
        List<Diagnosis> diagnoses = CrashAnalyzer.analyze(
                "Missing or unsupported mandatory dependencies: Mod 'create' requires 'flywheel'");
        Assert.atLeast(1, diagnoses.size(), "a missing dependency is detected");
        Assert.isTrue(diagnoses.stream().anyMatch(Diagnosis::isBlocker),
                "a missing dependency blocks startup");
    }

    public static void testGraphicsDriverRule() {
        List<Diagnosis> diagnoses = CrashAnalyzer.analyze("GLFW error 65542: Failed to create window");
        Assert.atLeast(1, diagnoses.size(), "a GLFW failure is detected");
        Assert.contains(diagnoses.get(0).render().toLowerCase(), "driver",
                "the advice points at the graphics driver");
    }

    public static void testDiagnosesAreOrderedBySeverity() {
        String log = """
                java.lang.OutOfMemoryError: Java heap space
                java.lang.UnsupportedClassVersionError: bad class file version
                GLFW error 65542: Failed to create window
                """;
        List<Diagnosis> diagnoses = CrashAnalyzer.analyze(log);
        Assert.atLeast(2, diagnoses.size(), "multiple problems are reported");
        for (int i = 1; i < diagnoses.size(); i++) {
            Assert.isTrue(
                    diagnoses.get(i - 1).severity().ordinal() <= diagnoses.get(i).severity().ordinal(),
                    "severity is non-decreasing, so blockers come first");
        }
    }

    public static void testMatchingIsCaseInsensitive() {
        List<Diagnosis> diagnoses = CrashAnalyzer.analyze(
                "JAVA.LANG.OUTOFMEMORYERROR: java heap space");
        Assert.atLeast(1, diagnoses.size(), "signatures match regardless of case");
    }

    public static void testBlankInputIsSafe() {
        Assert.equal(0, CrashAnalyzer.analyze("").size(), "empty log is safe");
        Assert.equal(0, CrashAnalyzer.analyze(null).size(), "null log is safe");
        Assert.equal(0, CrashAnalyzer.analyze("   \n\n  ").size(), "whitespace log is safe");
    }

    public static void testRenderIncludesCauseAndFix() {
        List<Diagnosis> diagnoses = CrashAnalyzer.analyze(
                "java.lang.OutOfMemoryError: Java heap space");
        String rendered = CrashAnalyzer.render(diagnoses);
        Assert.contains(rendered, "->", "rendered output lists concrete fixes");
        Assert.isFalse(rendered.isBlank(), "rendered output is not empty");
    }

    public static void testRenderOfEmptyListExplainsItself() {
        Assert.isFalse(CrashAnalyzer.render(List.of()).isBlank(),
                "an empty result still produces guidance");
    }

    public static void testHasBlockerHelper() {
        Assert.isTrue(CrashAnalyzer.hasBlocker(CrashAnalyzer.analyze(
                        "java.lang.UnsupportedClassVersionError: class file version")),
                "blocker detected");
        Assert.isFalse(CrashAnalyzer.hasBlocker(List.of()), "empty list has no blocker");
    }

    public static void testEvidenceIsTruncated() {
        String longLine = "java.lang.OutOfMemoryError: Java heap space " + "x".repeat(500);
        List<Diagnosis> diagnoses = CrashAnalyzer.analyze(longLine);
        Assert.atLeast(1, diagnoses.size(), "the rule fires");
        Assert.atMost(260, diagnoses.get(0).evidence().length(),
                "evidence is trimmed so the UI cannot be blown out");
    }

    public static void testMixinRule() {
        List<Diagnosis> diagnoses = CrashAnalyzer.analyze(
                "org.spongepowered.asm.mixin.injection.throwables.InvalidInjectionException: "
                        + "mixin apply failed");
        Assert.atLeast(1, diagnoses.size(), "a mixin failure is detected");
    }

    public static void testRuleCountIsSubstantial() {
        Assert.atLeast(12, CrashAnalyzer.rules().size(),
                "the rule table covers the common failure classes");
    }
}
