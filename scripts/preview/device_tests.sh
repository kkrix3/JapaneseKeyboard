#!/usr/bin/env bash
# Run real IME ownership tests and existing multi-pointer suites on a disposable AVD.
set -uo pipefail
mkdir -p device-input-artifacts
export IME_EMULATOR_LOG_DIR="$PWD/device-input-artifacts"
source .github/scripts/ime-emulator-common.sh
ime_emulator_prepare || exit 1
adb shell setprop log.tag.FlickStageHaptics DEBUG
adb logcat -v threadtime > device-input-artifacts/logcat.txt &
logcat_pid=$!
trap 'kill "$logcat_pid" 2>/dev/null || true' EXIT
bash ./gradlew :app:connectedFullStandardDebugAndroidTest \
  -PpreviewDeviceTest=true \
  -Pandroid.testInstrumentationRunnerArguments.class=com.kazumaproject.markdownhelperkeyboard.SmallTsuImeDeviceTest \
  -Pandroid.testInstrumentationRunnerArguments.timeout_msec=600000 \
  --no-daemon --console=plain --max-workers=2
ime_status=$?
if [[ "$ime_status" -ne 0 ]]; then
  ime_emulator_capture_diagnostics "ime-test-failure"
fi
bash .github/scripts/run-multi-pointer-regression.sh
pointer_status=$?
test "$ime_status" -eq 0 && test "$pointer_status" -eq 0
