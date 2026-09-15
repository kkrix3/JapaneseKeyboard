# Draft: configurable haptic waveforms at committed input boundaries

Status: reusable proposal draft only. Not submitted as an Issue/PR. The migrated feature
has passed the Full Debug build and targeted regression tests; device validation is pending.

Target: KazumaProject/JapaneseKeyboard:dev
Proposed source: [kkrix3/JapaneseKeyboard:feature/custom-haptics](https://github.com/kkrix3/JapaneseKeyboard/tree/feature/custom-haptics)

[Feature diff](https://github.com/kkrix3/JapaneseKeyboard/compare/dev...feature/custom-haptics)

## Why

Different accepted gestures can benefit from distinct tactile signals without looking at
the keyboard. A short single pulse for ordinary input, two pulses for a two-step flick and
a stronger pulse for a special action help distinguish what the IME accepted. Existing
press/release/both options describe timing, but do not expose these patterns and amplitudes.
Predefined/primitive support and system routing vary between devices.

## Proposal and UX

Add an opt-in custom waveform vibration mode, retaining the three existing modes. Three
patterns are editable: NORMAL_FLICK [(5,32)], TWO_STEP_FLICK [(5,32),(20,0),(5,32)] and
SPECIAL_KEY [(5,96)]. Steps are raw durationMs/amplitude, with amplitude 0 as a gap and a
maximum of ten steps. There is no distinct gap type or stored preset name. Tick-like,
Click-like and Heavy-like append (5,32), (5,96) and (5,255); Custom accepts raw values.
The editor supports add, edit, reorder, delete, reset, preview and explicit save/cancel.

Normal TOUCH routing respects system touch feedback. An explicit MEDIA option uses the
media route; accessibility attributes are not repurposed. No automatic tail correction,
queueing or intensity reinterpretation is introduced. Empty/OFF-only patterns are silent.

## Input and compatibility design

The view sends gesture context at the accepted-action boundary. Two-step classification
uses controller directions/path, not character count. The retained hierarchical path
ignores cancelled stages. Deferred double-tap actions retain their original callback's
context; the existing dispatcher is extended only for this purpose, not to add a new
double-tap feature. The IME plays only after acknowledging the existing input handler.
Legacy vibrate calls are suppressed only in custom mode; press/release/both retain their
existing policy. Long-press repetition plays at the actual repeat loop, not timer setup.

The port preserves current upstream split-pane activation, toggle-input finalization and
new action branches. Upstream's new toggle callback bypasses onAction, so it gets its own
normal-input notification. The old IMEService file was not substituted or merged wholesale.
Unrelated snapshot whitespace changes and all PoC CI/branding files were excluded.
The already-correct upstream VibrationTimingPolicy truth table was retained, with a test
that custom leaves its legacy path disabled. Library models/player/preferences and editor
were reusable; contextual input wiring was adapted to the latest upstream handlers.

This v1 wires FlickKeyboardView (Sumire/custom layouts). Other independent keyboard views
that do not supply committed gesture metadata are not claimed to have full custom feedback
coverage. Acceptance here means the IME handled the action, not that an external app honors
every InputConnection call. This boundary should be reviewed before an upstream proposal.

## Device fallback

API 33+ uses VibrationAttributes TOUCH/MEDIA. Earlier APIs use the corresponding
AudioAttributes. Amplitude-control absence maps nonzero amplitudes to default intensity
while retaining ON/OFF timing. Before API 26, adjacent ON segments are combined into the
legacy alternating OFF/ON representation. No vibrator, disabled master feedback or disabled
system touch mode is silent. The media option remains subject to device policy.

## Prior PoC observations (user-reported, not measurements of this migrated build)

The user reports a working PoC tested on a physical device. Very short strong-to-weak
sequences showed history dependence on a tested device; appending raw (1ms,32) after
the strong pulse sometimes stabilized the next weak pulse. That observation motivates
user control, not automatic correction. This port has no newly collected device measurements.
Before submission, record the device/Android version, pulse duration, amplitude, gap,
two-pulse distinguishability, amplitude-control capability and predefined/primitive support.
Do not infer universal device behavior or measured timing thresholds from the defaults.

## Tests and evidence still needed

Included tests cover model bounds/defaults, malformed/empty persistence, playback gates,
amplitude fallback, accepted/cancelled input ordering/count, actual DOWN/UP/CANCEL view
events, first-stage/two-stage classification, delayed callbacks, editor operations and
existing settings backup round-trip. Existing custom_keyboard tests run alongside them.
New backup tests exercise the upstream exporter/importer across independent preference
namespaces and all supported types. No backup production format or DB schema is changed.

CI: [run 35032069860](https://github.com/kkrix3/JapaneseKeyboard/actions/runs/35032069860),
feature commit d0acb1ef3002c59adbe513548b9224b357eb65be. Full Standard Debug,
custom_keyboard tests and selected Full app regression/backup tests passed.
Actual hardware validation: pending; see MIGRATION.md.
Before opening a draft PR, attach successful Full build/test run links and device results.
The feature branch must contain no applicationId, Preview label, signing, fork workflow or
release changes. Those are isolated on preview and must not be included in the proposed diff.
