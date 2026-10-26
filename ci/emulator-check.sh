#!/usr/bin/env bash
# Runs inside an Android emulator (stock Android, not Xiaomi). Verifies the blocking path end to end.
set -u
APP=com.focuslock.app
SVC=$APP/com.focuslock.app.system.FocusLockAccessibilityService
OUT=ci-out; mkdir -p $OUT; : > $OUT/summary.txt
fail=0
say() { echo "=== $*"; }
exec > >(tee -a $OUT/summary.txt) 2>&1
report() {
  sleep 1
  # Publish the summary as a run annotation (readable without downloading artifacts).
  msg=$(tail -c 3500 $OUT/summary.txt | sed ':a;N;$!ba;s/%/%25/g;s/\r/%0D/g;s/\n/%0A/g')
  echo "::notice title=emulator-summary::$msg"
  for f in diagnostics.txt logcat.txt; do
    [ -f $OUT/$f ] && { m=$(tail -c 3000 $OUT/$f | sed ':a;N;$!ba;s/%/%25/g;s/\r/%0D/g;s/\n/%0A/g'); echo "::notice title=$f::$m"; }
  done
}
trap report EXIT

adb install -r -t app/build/outputs/apk/debug/app-debug.apk || exit 1
adb install -r -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk || exit 1

PKG=""
for p in com.android.camera2 com.android.chrome com.google.android.apps.maps com.android.documentsui com.android.gallery3d com.android.calendar com.android.contacts; do
  if adb shell pm path $p | grep -q package; then
    if adb shell cmd package resolve-activity --brief -c android.intent.category.LAUNCHER $p | grep -q "/"; then PKG=$p; break; fi
  fi
done
say "target app: ${PKG:-none}"
[ -z "$PKG" ] && { echo "no launchable test app found"; exit 1; }

say "seed rule (block $PKG)"
adb shell am instrument -w -e class com.focuslock.app.SeedBlockRuleTest -e pkg "$PKG" \
  com.focuslock.app.test/androidx.test.runner.AndroidJUnitRunner | tee $OUT/seed.txt
grep -q "OK (1 test)" $OUT/seed.txt || { echo "seeding failed"; fail=1; }

say "enable accessibility service"
adb shell settings put secure enabled_accessibility_services $SVC
adb shell settings put secure accessibility_enabled 1
sleep 6
adb shell dumpsys accessibility | grep -i focuslock | head -5

top() { adb shell dumpsys activity activities | grep -E "topResumedActivity|mResumedActivity" | head -2; }

say "open blocked app (1st time)"
adb shell input keyevent KEYCODE_HOME; sleep 2
adb shell monkey -p "$PKG" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1
sleep 5
adb exec-out screencap -p > $OUT/after-open-1.png
T=$(top); echo "$T"
echo "$T" | grep -q "BlockActivity" && echo "PASS: block screen shown (1st open)" || { echo "FAIL: block screen NOT shown (1st open)"; fail=1; }

say "go home, open again (re-open must be blocked too)"
adb shell input keyevent KEYCODE_HOME; sleep 2
adb shell monkey -p "$PKG" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1
sleep 5
adb exec-out screencap -p > $OUT/after-open-2.png
T=$(top); echo "$T"
echo "$T" | grep -q "BlockActivity" && echo "PASS: block screen shown (re-open)" || { echo "FAIL: block screen NOT shown (re-open)"; fail=1; }

say "diagnostics log from app"
adb shell run-as $APP cat files/diagnostics.log | tee $OUT/diagnostics.txt
adb logcat -d -s FocusLockA11y:* AndroidRuntime:E > $OUT/logcat.txt
exit $fail
