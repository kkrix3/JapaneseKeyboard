# Input character pop animation (prototype)

Enable **Keyboard display → Touch effects → Input character pop animation**
(日本語: キーボード表示 → タッチエフェクト → 入力文字の浮き上がりアニメーション).
The switch defaults to OFF, is independent of the selected touch effect and vibration,
and participates in the existing preferences backup. It affects Sumire and custom
keyboards rendered by `FlickKeyboardView`.

## Motion and rendering

- Duration: 280 ms. Scale: 1.0 → 1.8, ease-out quadratic `1 - (1 - t)^2`.
- Alpha: 1.0 for the first 15% (42 ms), then fades with smoothstep
  `1 - u²(3 - 2u)`, where `u = (t - .15) / .85`, clamped to 0…1.
- The center of the glyph's ink bounds remains fixed in both X and Y.
- A single non-interactive window-overlay Drawable per keyboard renders independent
  glyph copies. It does not replace keys, alter popup lifecycle, or consume touches.
- At most 12 active copies; the oldest is removed only when this limit is reached.
  Each copy retains its own font/paint and start time. No per-key View/Animator tree.
- OFF bypasses snapshot preparation and allocates no renderer on a new keyboard.
  Callbacks and copied Paints are released at completion; hide, detach, layout,
  configuration, font, theme, disable and keyboard replacement clear the layer.
- Tuning constants/curves: `custom_keyboard/.../feedback/InputCharacterPop.kt`.

## Input boundary

`FlickTextPreviewEmitter.commit` sends `CommitPending` before dispatch and `Finished`
from a `finally` block. These events do **not** prove that the IME accepted input.
Neither is used to start this effect, and neither emitter nor event types changed.

The common `dispatchCommittedKeyAction` captures just the selected text appearance
before input can change labels or dismiss the popup. For eligible text it calls the
new opt-in listener method `onCommittedTextAction`. IMEService acknowledges only
after the existing `performCommittedAction` / `CommittedInputHaptics.dispatch`
path accepts it with an input connection. Deferred cursor operations carry the same
callback; a generation check prevents stale callbacks after layout/lifecycle changes.
There is no animation wait, input debounce, composing mutation, extra InputConnection
call, or change to the recognizers. IME acceptance is not proof that a remote editor
has rendered the characters; this follows the app's existing acceptance contract.

Candidates and live conversion do not enter this view dispatch path. Small-tsu
replacement keeps displaying the original selected character. ACTION_CANCEL and
unassigned directions cannot start an effect. Empty, whitespace-only, control-containing
or >32-code-point text is skipped to bound work.

## Coverage / fallback

The common commit path covers normal text taps, standard/cross/petal/center-guide
flicks, two-step, sticky two-step, hierarchical/three-step and circular (donut) input.
Character commits after a flick guide's long press also use this path.

Cross/petal directional popups and their long-press grid expose the selected text
position and font before dismissal. Other popup implementations deliberately use a
shared key-label/center fallback. This avoids adding separate animation lifecycles
to all popup controllers. The key center also applies when a popup has a custom icon
or label that does not match the selected text. Clipping is at the window edge, not
at individual key bounds. PopupWindows for other simultaneous gestures remain above
the underlying window overlay. Rotated view hierarchies are not supported; existing
keyboard scale/translation and floating-window origins are accounted for.

Not covered: legacy Kana `TenKey` view, QWERTY, special/action keys, toggle cycling,
explicit special-key double-tap bindings, normal-key long-press action callbacks,
and candidate/clipboard/voice input. The prototype intentionally leaves these paths
alone. Animation ON/OFF does not disable other touch effects or haptics.

## Device review before merging

Compare OFF/ON using the same layout, theme and input destination:

1. Tap, all directions, 2nd/3rd stage and donut; verify exact input and selected glyph.
2. Release after long-press guide display, then cancel a gesture. Only accepted
   character input should pop. Inspect both directional and grid popup origins.
3. Type rapidly while previous glyphs remain visible, including two fingers, with
   existing touch effects and custom/stage haptics enabled. Check recognition and latency.
4. Switch keyboard, input mode, theme/font, orientation and floating/split layout
   during an animation; ensure no old glyphs remain or jump to another surface.
5. Check dark/light/custom skins and edge keys; no key-sized clipping, unwanted XY
   movement, background scaling or popup flicker. Judge 280 ms / 180% on the device.
6. With live conversion, predictions and small-tsu enabled, check that only physical
   key-selected text pops, with no additional effects for conversion/replacement.

Robolectric tests exercise curves, bounding, independent expiry, input-before-effect,
rejection/cancel, real touch routing, lifecycle invalidation and small-tsu/haptics.
They cannot certify perceived smoothness, OLED contrast, GPU frame time, or latency
on a physical device. Full Preview CI builds/inspects an unsigned APK for the PR.
The existing trusted Preview signing job permits only owner-dispatched `preview`;
this feature does not alter that policy or signing secrets.
