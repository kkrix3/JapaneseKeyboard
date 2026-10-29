# Google emoji display in Sumire

Settings → Clipboard and shortcuts → Symbol keyboard → Emoji appearance:
Device default (initial value), or Google emoji (日本語: 端末標準 / Google絵文字).
Only Sumire's emoji grid, history and skin-tone popup change. Normal conversion
candidates, kaomoji, symbols, clipboard text, OS and destination apps retain their
existing display policies. Category icons remain the existing vector icons.

## Rendering

Noto Color Emoji 2.057 is an unmodified CBDT/CBLC bitmap color font with GSUB
sequence shaping. Android shapes the original Unicode, including VS16, ZWJ,
modifiers, regional indicators and tag sequences. No normalization, variation
selector insertion or modified strings are sent to the input connection.

`GoogleEmojiFont` lazily loads one process-cached Typeface from the APK.
`SymbolAdapter` uses the existing KeyboardFontApplicator with a local appearance
revision so recycled cells can switch between device, Google and custom keyboard
fonts even when the keyboard font's process revision has not changed.
AppCompat emoji processing is temporarily disabled for Google cells, and each
cell is rebound from its original String; automatic EmojiSpans cannot override
the chosen font. Other modes keep their original AppCompat policy.

CustomSymbolKeyboardView registers a listener to the existing default preferences
while attached, synchronizes on attach, and unregisters on detach. This covers
main, floating and split views without changing IMEService or candidate rendering.
Style changes dismiss open skin-tone popups and rebind visible cells. Popup
choices use the same cached font and retain existing selection/input callbacks.

## Alternatives

- EmojiCompat bundled with forced replacement can replace OEM glyphs, but needs
  care around its process singleton and AppCompat auto processing. Its default
  policy replaces only missing system glyphs and would not solve OEM substitution.
- Downloadable fonts depend on a provider and connection at runtime.
- Explicit bundled Typeface is local to these emoji views, offline at runtime,
  and adds no new emoji runtime library or global font configuration.
- CBDT/CBLC supports the existing Android 24 minimum. COLRv1 is smaller but has
  narrower compatibility with older Android versions.

Sources: [Android emoji2 guide](https://developer.android.com/develop/ui/views/text-and-emoji/emoji2),
[Noto Emoji](https://github.com/googlefonts/noto-emoji),
[TextRunShaper](https://developer.android.com/reference/android/graphics/text/TextRunShaper).

## Font provenance, build and license

`symbol_keyboard/google-emoji.gradle` downloads the font at **build time**, verifies
SHA-256 and generates a font resource before Android's resource compilation. The
APK includes the font and works without network/provider access. Builds need
access to the pinned URL on the first run; Gradle reuses the generated output on
subsequent unchanged builds. The task supports configuration caching and refuses
to package a checksum mismatch. No font binary is sent through the GitHub connector.

- Repository commit: `99701e07d3d1fe1bdbcdb14964547f0fd592966f`
- Path: `2D/fonts/NotoColorEmoji.ttf`
- Font version record: `Version 2.057;GOOG;noto-emoji:20260911:fc4ca365e7c20e78278ae702aa20434bfe704c8f`
- SHA-256: `15671215ab769fdc7162a045d56fd7d7e477c51b04e6b3c761d914d8fdd6cc44`
- Font size: 10,730,124 bytes (10.23 MiB), approximately 9.50 MiB compressed.
  Full Preview APK inspection reports its actual stored contribution.
- Copyright 2013 Google LLC, SIL Open Font License 1.1. Full upstream font license
  and copyright are packaged in `assets/licenses/noto-color-emoji.html`, accessible
  from Settings → Open source licenses → Noto Color Emoji (Google). The font remains
  OFL; no separate font sale or modification is involved.

Future sequences absent from this pinned font may still fall back to the platform.
Updating the font requires updating the checksum and rerunning native tests.

## Verification

`EmojiRenderingSettingTest` covers defaults, preference reinitialization, device
restoration, backup, unknown/wrongly typed values, and settings choices/defaults.

`GoogleEmojiRenderingDeviceTest` runs on Android 24 and Android 14/API 34. It checks
visible output for ten simple/VS16/ZWJ/tone/flag/keycap/tag sequences, and colored
output for the nine whose upstream glyphs are colored (the family silhouette
is intentionally grayscale in Noto 2.057). On API 34,
TextRunShaper must return one glyph, and the actual shaped glyph font buffer must
hash to the bundled font, distinguishing it from OEM/system fallback. Native UI
tests cover live preference updates, activity recreation with saved Google mode,
history/category strings, all six skin-tone options, selected strings, standard
restoration, popup dismissal, and custom-font/emoji precedence after mode recycling.
The CI artifacts include device/Google render pairs and history/popup bitmaps.
A separate save/force-stop/verify instrumentation sequence checks that Google
selection survives a fresh Android process (including a distinct PID) and is used
by a newly attached keyboard.

The existing Full Preview CI retains app/core/keyboard regression tests, API 35
real IME input, release APK generation and package/native/model/alignment checks.
The new APK checks require the pinned Noto font and full license. Final run results
are recorded in [Draft PR #13](https://github.com/kkrix3/JapaneseKeyboard/pull/13).

## Limits and manual checks

No docomo physical device is available. Confirm on Android 14 docomo hardware that
Google selection actually changes docomo designs, remains colored and fits cells
and popups. Check light/dark themes, landscape, fast scrolling, floating/split mode,
switching back, and full-IME process restart on the physical device. Confirm kana/kanji candidates and custom
fonts in kaomoji/symbol modes remain unaffected. Commit family/tone/flag sequences
into different apps and confirm the same Unicode; each destination controls its
own glyph display.

Existing Preview signing accepts only owner-dispatched runs of `preview`, with
the signing enable variable and Environment approval. The feature branch cannot
use that job as it stands. This PR keeps the trust guard and remains Draft without
merging. The unsigned Full Preview artifact is for inspection, not installation.
