#!/usr/bin/env bash
# Run real IME ownership tests and existing multi-pointer suites on a disposable AVD.
set -uo pipefail
mkdir -p device-input-artifacts
adb logcat -c
adb logcat -v threadtime > device-input-artifacts/logcat.txt &
logcat_pid=$!
trap 'kill "$logcat_pid" 2>/dev/null || true' EXIT
bash ./gradlew :app:connectedFullStandardDebugAndroidTest \
  -PpreviewDeviceTest=true \
  -Pandroid.testInstrumentationRunnerArguments.class=com.kazumaproject.markdownhelperkeyboard.SmallTsuImeDeviceTest \
  --no-daemon --console=plain --max-workers=2
ime_status=$?
bash .github/scripts/run-multi-pointer-regression.sh
pointer_status=$?
test "$ime_status" -eq 0 && test "$pointer_status" -eq 0
