# FocusLock — App & Feed Blocker

Created by **NEPUS**.

Native Android app (Kotlin, Jetpack Compose, Material 3) that blocks distracting apps and, where the
app exposes enough information, short-video feeds such as YouTube Shorts and Instagram Reels.

> **Build status: source complete, APK NOT yet compiled.**
> The environment this project was written in has no Android SDK and no access to Google's Maven
> repository, so Gradle could not run. Only the pure-Kotlin logic layer (`domain/`) was compiled and
> unit-tested. The Android and Compose code has not been compiled by a real toolchain, so expect to
> fix a few compile errors on first build. See [docs/BUILD_VERIFICATION.md](docs/BUILD_VERIFICATION.md)
> for exactly what was and was not run.

> No Android Studio? Push this folder to GitHub and run the "Build APK" workflow (Actions tab); it uploads app-debug.apk.

- Application ID `com.focuslock.app` · version `1.0.0` (code 1) · minSdk 26 (Android 8.0) · targetSdk / compileSdk 35
- No internet permission. No analytics. All data stays on the phone.

## Build the APK

Requirements: Android Studio Ladybug (2024.2) or newer, **or** JDK 17+ and the Android SDK (platform 35,
build-tools 35.x) with `ANDROID_HOME` set.

```bash
# Android Studio: File > Open > select this folder > wait for Gradle sync > Run

# Command line
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

If sync fails on a dependency, check `gradle/libs.versions.toml`; every version is pinned there.

## Tests

```bash
./gradlew testDebugUnitTest            # JVM unit tests (app/src/test)
./gradlew connectedDebugAndroidTest    # instrumentation tests; needs an emulator or device
./scripts/verify-domain.sh             # SDK-free: compiles + runs domain tests with Gradle's bundled Kotlin
```

Manual device checklist: [docs/MANUAL_TESTS.md](docs/MANUAL_TESTS.md).

## First run on a phone

1. Install the APK and open FocusLock; the onboarding explains each permission.
2. **Accessibility** is required for blocking. Android 13+ may grey the switch out for apps installed from a
   file: open *App info → ⋮ → Allow restricted settings*, then enable FocusLock in Accessibility.
3. Usage access (optional) enables usage-time statistics. Notifications are optional.
4. Apps tab → toggle the apps to block. Focus tab → start a session.

## How it works

| Piece | What it does |
|---|---|
| `FocusLockAccessibilityService` | Receives window-change events, asks `RuleEngine` whether the foreground app is blocked, and shows `BlockActivity`. For apps with an enabled feed rule it also scans accessibility view ids to spot Shorts/Reels. |
| `domain/RuleEngine` | Pure function: rules + time → block decision. Re-evaluated on every event, so enforcement does **not** depend on alarms firing on time. |
| `domain/BlockGate` | Debounces duplicate events, latches feed blocks, throttles scans. Re-opening a blocked app after switching away is always blocked again. |
| `Scheduler` + `Maintenance` | One inexact alarm for the next session end / schedule boundary (notifications and closing sessions); also run after boot, time change, time-zone change, app update and by a 30-minute WorkManager job. |
| `SessionClock` | Uses the monotonic clock within a boot (immune to clock changes), and the wall clock after a reboot. |
| `UsageStatsReader` | Measures foreground time for reporting only. Usage access cannot block anything. |
| Room + DataStore | Rules, schedules, groups, sessions, block events, usage totals / settings. |

### Rule priority

1. Never-blocked apps (launcher, dialer, Settings, keyboards, FocusLock) and your emergency-allowed apps always win.
2. A running emergency bypass allows that app (ignored during a strict session).
3. Otherwise an app is blocked if **any** rule applies. The reason shown is: focus session → schedule → always-blocked list.
4. Whole-app blocks beat feed rules for the same app.

## Honest limitations

- **Blocking is not tamper-proof.** The user can turn the accessibility service off, revoke permissions, force-stop or uninstall the app. FocusLock warns visibly (home banner, notification) but cannot prevent it. "Strict" sessions only remove the easy exits inside FocusLock.
- **Feed detection is best-effort.** It matches view-id names exposed to accessibility. The shipped YouTube/Instagram/Facebook names are starting points that were **not verified** against current app releases; they ship switched off. Apps change these names, and some (notably Facebook) expose almost nothing. Each rule can fall back to blocking the whole app.
- Detection never uses screenshots, coordinates or keystrokes, and reads no message content.
- Alarms are inexact in Doze (may be minutes late). Blocking itself is unaffected; only notifications and session close-out can lag.
- Some manufacturers (Xiaomi, Huawei, Oppo, Vivo, some Samsung modes) kill background services; set battery to Unrestricted and enable autostart.
- Language: only English strings exist; the architecture (resources + stored language tag, applied on Android 13+) is in place.
- Dependency injection is a small hand-written container rather than Hilt (kept the build simple and offline-friendly).
- Release builds enable R8 with minimal keep rules (`app/proguard-rules.pro`) and have not been tested.

## Privacy

See the in-app Privacy screen. In short: no INTERNET permission, no analytics, `allowBackup=false`; screen
content is inspected in memory only for apps with an enabled feed rule and is never stored or logged.
Exports (backup JSON, statistics CSV) go only where the user saves them.

## Project layout

```
app/src/main/java/com/focuslock/app/
  domain/    pure Kotlin: models, RuleEngine, ScheduleEvaluator, SessionClock, BlockGate, FeedDetector, BackupCodec
  data/      Room entities/DAOs/database (+ migration notes), repository implementations, DataStore settings
  system/    AccessibilityService, permissions, app discovery, usage stats, notifications, alarms, workers, receiver
  ui/        Compose screens + ViewModels (home, apps, feeds, focus, schedules, stats, groups, settings, onboarding, block)
app/src/test/          JVM unit tests (77, run in this project's verification)
app/src/androidTest/   instrumentation tests (written, not run)
docs/                  manual test checklist, build verification report
scripts/               SDK-free domain verification
```

## Database migrations

Schema version is 1 (first release); `exportSchema = true` writes JSON to `app/schemas/` on build.
Never use destructive fallback: add a `Migration` in `FocusLockDatabase.kt`, commit the new schema, and add a
`MigrationTestHelper` test (`androidTest`). The migration test is **not** written yet because no migration exists.

## Credits

Designed and created by NEPUS. © 2026 NEPUS.
