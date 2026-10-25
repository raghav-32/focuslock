# Build verification report

| Item | Value |
|---|---|
| Application ID | `com.focuslock.app` |
| Version | 1.0.0 (versionCode 1) |
| minSdk / targetSdk / compileSdk | 26 (Android 8.0) / 35 / 35 |
| Toolchain pinned | Gradle 8.9 (wrapper), AGP 8.7.3, Kotlin 2.0.21, KSP 2.0.21-1.0.28, Compose BOM 2024.12.01 |
| Build date (source written) | 2026-10-23 → 2026-10-25 |
| **APK** | **Not built.** There is no APK to download. |

## Why there is no APK

The authoring sandbox has JDK 21 and Gradle 8.14, but **no Android SDK**, and its network policy returns
HTTP 403 for `dl.google.com`, `services.gradle.org`, Google's Maven and Maven Central. Only npm, PyPI and raw GitHub
were reachable. AGP, AndroidX, Compose, Room and `android.jar` therefore could not be obtained, and
`./gradlew assembleDebug` could not be run. No APK was produced and none is claimed.

## What was actually executed

| Check | Result |
|---|---|
| Kotlin compile of `domain/` (pure Kotlin, 10 files) with Kotlin 2.0.21 (bundled in Gradle) | **Passed**, 0 errors |
| JVM unit tests: 77 tests in 7 classes (`RuleEngineTest`, `ScheduleEvaluatorTest`, `SessionClockTest`, `BlockGateTest`, `FeedDetectorTest`, `PolicyAndStatsTest`, `BackupCodecTest`) | **77 / 77 passed** |
| Static check that every `com.focuslock.app.*` import resolves to a symbol defined in the project | Passed (script, 125 imports) |
| Re-run from a clean script (`scripts/verify-domain.sh`) | Passed |

Caveat on `BackupCodecTest`: Android's `org.json` is not available on a plain JVM, so these tests ran against a small
stand-in (`scripts/orgjson-shim/Json.kt`, built on kotlinx-serialization). Edge-case behaviour of the real `org.json` may
differ. The Gradle build uses the real `org.json:json` artifact for unit tests.

## What was NOT executed

- Compilation of anything outside `domain/`: Room entities/DAOs, repositories, DataStore, the accessibility service,
  receivers, workers, notifications, all Compose UI, ViewModels, navigation, manifest merging, resource compilation.
  The code was read through for errors and several were fixed, but **compile errors are likely to remain** until a real build.
- Room KSP processing, schema export, migrations (none exist yet at schema v1).
- Instrumentation tests (`DatabaseInstrumentedTest`, `BlockScreenUiTest`): written, never run.
- Installing on any emulator or device; therefore **none of the on-device behaviour has been observed**: the accessibility
  service, blocking screen display, feed detection against real YouTube/Instagram/Facebook, notifications, alarms, reboot
  recovery, usage-stats numbers, OEM battery behaviour, light/dark rendering.
- R8 / release build.
- The 25 test scenarios in the project brief: scenarios whose logic lives in `domain/` (app blocked/unblocked, multiple apps,
  session timing incl. reboot and clock change, schedules incl. overnight/DST/time-zone, duplicate events, rapid switching,
  blocked feed vs unsupported feed, emergency bypass, backup validation) have automated JVM coverage. Permission
  grant/revoke, force-stop recovery, service recovery, large lists, themes, no-network, DB migration and the on-device
  blocking itself are **manual-only and untested** (see `MANUAL_TESTS.md`).

## Known risks to check first on the first real build

1. Compile errors in the Android/Compose layer (most likely: Compose API signatures, Room/KSP specifics).
2. Shipped feed-rule view-id names for YouTube/Instagram/Facebook are unverified guesses.
3. Launching `BlockActivity` from the accessibility service relies on Android allowing it from that context; a Home-screen
   fallback is coded if the launch throws, but behaviour on every OEM is untested.
4. `Settings.Global.BOOT_COUNT` is used for reboot detection; if an OEM blocks it the code falls back to the wall clock.
5. Notification/alarm timing in Doze is approximate by design.

## How to produce the real report

```bash
./gradlew clean assembleDebug testDebugUnitTest 2>&1 | tee build.log
./gradlew connectedDebugAndroidTest         # with an emulator/device attached
# then walk through docs/MANUAL_TESTS.md
```
