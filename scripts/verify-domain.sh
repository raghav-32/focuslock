#!/usr/bin/env bash
# Compiles the pure-Kotlin domain layer + its JUnit tests with the Kotlin compiler bundled in a
# Gradle distribution, and runs them on the JVM. No Android SDK needed. This does NOT compile the
# Android/Compose code: use ./gradlew assembleDebug for that.
#
# Usage: scripts/verify-domain.sh [/path/to/gradle/lib]   (default /opt/gradle/lib)
set -euo pipefail
L="${1:-/opt/gradle/lib}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$(mktemp -d)"
SHIM="$OUT/shim/org/json"; mkdir -p "$SHIM" "$OUT/classes"
cp "$ROOT/scripts/orgjson-shim/Json.kt" "$SHIM/Json.kt"

KC="$L/kotlin-compiler-embeddable-2.0.21.jar:$L/kotlin-stdlib-2.0.21.jar:$L/kotlin-script-runtime-2.0.21.jar:$L/kotlin-reflect-2.0.21.jar:$L/kotlin-daemon-embeddable-2.0.21.jar:$L/trove4j-1.0.20200330.jar:$L/annotations-24.0.1.jar:$L/kotlinx-coroutines-core-jvm-1.6.4.jar"
CP="$L/kotlin-stdlib-2.0.21.jar:$L/kotlinx-coroutines-core-jvm-1.6.4.jar:$L/kotlinx-serialization-core-jvm-1.6.2.jar:$L/kotlinx-serialization-json-jvm-1.6.2.jar:$L/junit-4.13.2.jar:$L/hamcrest-core-1.3.jar"

java -cp "$KC" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -no-stdlib -no-reflect -jvm-target 17 -cp "$CP" -d "$OUT/classes" \
  "$SHIM/Json.kt" "$ROOT"/app/src/main/java/com/focuslock/app/domain/*.kt "$ROOT"/app/src/test/java/com/focuslock/app/domain/*.kt

java -cp "$OUT/classes:$CP" org.junit.runner.JUnitCore \
  com.focuslock.app.domain.RuleEngineTest com.focuslock.app.domain.ScheduleEvaluatorTest \
  com.focuslock.app.domain.SessionClockTest com.focuslock.app.domain.BlockGateTest \
  com.focuslock.app.domain.FeedDetectorTest com.focuslock.app.domain.PolicyAndStatsTest \
  com.focuslock.app.domain.BackupCodecTest
