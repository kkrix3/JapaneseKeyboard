#!/usr/bin/env bash
set -uo pipefail
mkdir -p emoji-search-device
export IME_EMULATOR_LOG_DIR="$PWD/emoji-search-device"
source .github/scripts/ime-emulator-common.sh
ime_emulator_prepare || exit 1
bash ./gradlew :app:connectedFullStandardDebugAndroidTest \
  -PpreviewDeviceTest=true \
  -Pandroid.testInstrumentationRunnerArguments.class=com.kazumaproject.markdownhelperkeyboard.EmojiSearchImeDeviceTest \
  --no-daemon --console=plain --max-workers=2
result=$?
probe_dir="/sdcard/Android/data/com.kazumaproject.markdownhelperkeyboard.preview.testbench/files"
for probe_image in emoji-search-results.png emoji-search-failure.png; do
  if adb shell "test -f $probe_dir/$probe_image"; then
    adb pull "$probe_dir/$probe_image" "$IME_EMULATOR_LOG_DIR/$probe_image" >/dev/null
  fi
done
adb logcat -d -v threadtime -s EmojiSearchProbe:I > emoji-search-device/performance.txt
if [[ "$result" -ne 0 ]]; then ime_emulator_capture_diagnostics emoji-search-failure; fi
exit "$result"
