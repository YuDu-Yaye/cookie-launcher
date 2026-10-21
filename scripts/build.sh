#!/usr/bin/env bash
# Builds the launcher jar and a native app image, then prints checksums.
#
# Requirements: JDK 21 (javac, jar, jlink, jpackage) on PATH or in JAVA_HOME.
# Usage: scripts/build.sh [--package]
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

JDK_BIN="${JAVA_HOME:+$JAVA_HOME/bin/}"
JAVAC="${JDK_BIN}javac"
JAR="${JDK_BIN}jar"
JPACKAGE="${JDK_BIN}jpackage"

BUILD="$ROOT/build"
CLASSES="$BUILD/classes"
TEST_CLASSES="$BUILD/test-classes"
DIST="$BUILD/dist"

rm -rf "$BUILD"
mkdir -p "$CLASSES" "$TEST_CLASSES" "$DIST"

echo "==> Compiling main sources"
find src/main/java -name '*.java' > "$BUILD/sources.txt"
"$JAVAC" -encoding UTF-8 -d "$CLASSES" @"$BUILD/sources.txt"

echo "==> Compiling tests"
find src/test/java -name '*.java' > "$BUILD/test-sources.txt"
"$JAVAC" -encoding UTF-8 -cp "$CLASSES" -d "$TEST_CLASSES" @"$BUILD/test-sources.txt"

echo "==> Discovering test classes"
mkdir -p "$TEST_CLASSES/dev/cookielauncher/test"
grep -h 'public static void test' src/test/java/dev/cookielauncher/test/*.java >/dev/null 2>&1 || true
find src/test/java -name '*Test.java' | sed 's|src/test/java/||; s|\.java$||; s|/|.|g' \
  | sort > "$TEST_CLASSES/tests.list"

echo "==> Running tests"
TEST_STATUS=0
java -cp "$CLASSES:$TEST_CLASSES" dev.cookielauncher.test.TestRunner "$@" || TEST_STATUS=$?
if [ "$TEST_STATUS" -ne 0 ]; then
  echo "!! Tests failed; refusing to package."
  exit "$TEST_STATUS"
fi

echo "==> Building jar"
MAIN_CLASS="dev.cookielauncher.Main"
cat > "$BUILD/MANIFEST.MF" <<MANIFEST
Manifest-Version: 1.0
Main-Class: $MAIN_CLASS
Implementation-Title: Cookie Launcher
Implementation-Version: 1.0.0
MANIFEST
"$JAR" --create --file "$DIST/cookie-launcher.jar" --manifest "$BUILD/MANIFEST.MF" -C "$CLASSES" .
echo "    $DIST/cookie-launcher.jar ($(du -h "$DIST/cookie-launcher.jar" | cut -f1))"

if [ "${1:-}" = "--package" ]; then
  echo "==> Building native app image (this bundles a trimmed JRE)"
  "$JPACKAGE" \
    --type app-image \
    --name CookieLauncher \
    --input "$DIST" \
    --main-jar cookie-launcher.jar \
    --main-class "$MAIN_CLASS" \
    --dest "$BUILD/app" \
    --java-options "-XX:+UseG1GC" \
    --java-options "-Dfile.encoding=UTF-8" \
    --java-options "-Dsun.java2d.uiScale.enabled=true" \
    --app-version 1.0.0 \
    --vendor "Cookie Launcher contributors" \
    --description "Open source Minecraft: Java Edition launcher"
  echo "    $BUILD/app/CookieLauncher"
fi

echo "==> Checksums"
( cd "$DIST" && sha256sum ./*.jar > SHA256SUMS.txt && cat SHA256SUMS.txt )

echo "==> Done"
