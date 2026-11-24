#!/usr/bin/env bash
set -euo pipefail
export IME_EMULATOR_LOG_DIR="$PWD/feature-device-reports"
source .github/scripts/ime-emulator-common.sh
ime_emulator_prepare
trap 'adb logcat -d > "$IME_EMULATOR_LOG_DIR/logcat.txt"; ime_emulator_capture_diagnostics after-tests' EXIT
python3 scripts/feature/run_device_tests.py
