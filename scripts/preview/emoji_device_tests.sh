#!/usr/bin/env bash
set -euo pipefail
mkdir -p emoji-rendering-artifacts
# The emulator action shuts the device down before the following workflow step.
# Capture files here while it is still online, including on a failed test.
trap 'adb pull /sdcard/Android/data/com.kazumaproject.symbol_keyboard.test/files/emoji-rendering emoji-rendering-artifacts/ || true' EXIT
bash ./gradlew :symbol_keyboard:connectedDebugAndroidTest --no-daemon --console=plain --max-workers=2
# Exercise saved preferences after the instrumentation process exits and is force-stopped.
runner="$(adb shell pm list instrumentation | tr -d '\r' | sed -n 's/^instrumentation:\(com.kazumaproject.symbol_keyboard.test\/[^ ]*\).*/\1/p')"
test -n "$runner"
for phase in save verify; do
  adb shell am instrument -w -r \
    -e class com.kazumaproject.symbol_keyboard.GoogleEmojiRenderingDeviceTest#persistedGoogleSelectionSurvivesNewProcess \
    -e emojiRestartPhase "$phase" "$runner" | tee "emoji-rendering-artifacts/restart-$phase.txt"
  grep -F 'OK (1 test)' "emoji-rendering-artifacts/restart-$phase.txt"
  if [[ "$phase" = save ]]; then adb shell am force-stop com.kazumaproject.symbol_keyboard.test; fi
done
