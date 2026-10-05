#!/usr/bin/env bash
# Run both suites even when one fails; preserve both reports and the failure status.
set -uo pipefail
bash ./gradlew :custom_keyboard:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.kazumaproject.custom_keyboard.view.SumireIndependentMultiTouchInstrumentedTest \
  --no-daemon --console=plain --max-workers=2
sumire_status=$?
bash ./gradlew :qwerty_keyboard:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.kazumaproject.qwerty_keyboard.ui.QwertyMultiTouchInstrumentedTest \
  --no-daemon --console=plain --max-workers=2
qwerty_status=$?
mkdir -p device-input-artifacts
adb pull /sdcard/Android/data/com.kazumaproject.custom_keyboard.test/files device-input-artifacts/ || true
test "$sumire_status" -eq 0 && test "$qwerty_status" -eq 0
