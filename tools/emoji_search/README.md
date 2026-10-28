# Offline Japanese emoji search

The Android app ships only `symbol_keyboard/src/main/assets/emoji_search/{ja.tsv,NOTICE.txt}`.
The XML snapshots, authoring JSON, evaluation inputs, and host tools in this directory
are not APK assets. No model, vector index or additional runtime is required.

Inputs:

- Unicode CLDR **48.2** `common/annotations/ja.xml` and
  `common/annotationsDerived/ja.xml`, copied from the `release-48-2` release.
  The upstream files and full Unicode-3.0 permission notice are in `upstream/`;
  their SHA-256 values are in `manifest.json`. The fixed release deliberately
  matches the existing Unicode Emoji 17.0 category metadata.
- The actual `tango_emoji.dat`, `token_emoji.dat`, and `yomi_emoji.dat` shipped by
  Sumire. The JDK-only exporter reads the same Java serialization, LOUDS rank/select
  and token ranges as production. The app regression test independently checks its
  inventory against the production reader. No readings from a different dictionary
  or an external morphological tokenizer are introduced.
- `usages.json`: 73 reviewed usage/emotion concepts authored for Sumire under the
  repository's MIT license. Each concept defines equal-weight terms and emoji;
  `related` carries weaker associations. Array position never determines rank.

Rebuild, without network:

```sh
java tools/emoji_search/ExportBundledEmoji.java app/src/main/assets/emoji tools/emoji_search/bundled-emoji.tsv
python3 tools/emoji_search/generate.py
git diff --exit-code -- tools/emoji_search/bundled-emoji.tsv tools/emoji_search/manifest.json symbol_keyboard/src/main/assets/emoji_search
```

Evaluate the **production Kotlin** implementation on a host with JDK 17+ and
`kotlinc` on PATH (or set `KOTLINC_BIN`):

```sh
bash tools/emoji_search/run-host.sh /tmp/emoji-search-results
bash ./gradlew :symbol_keyboard:testDebugUnitTest
bash ./gradlew :app:testFullStandardDebugUnitTest --tests '*EmojiSearch*'
```

`regression.tsv` reuses all 28 representative-gold lists from
`sumire_emoji_query_results.csv` (2026-10-28). `development.tsv` contains 24
additional author-written development probes used to find incidental substring
matches. **Neither is an independent benchmark.** After the dictionary and ranker
were fixed, `unseen.tsv` fixed 24 further phrases; their failures have not been
used to add aliases or adjust rank weights. This small author-written probe still
has selection bias and is not a user study. Host results and environment are in
`results/`; Android's actual inventory/order is evaluated separately by
`BundledEmojiSearchTest`.

No-result/irrelevant results must be reported, not silently replaced by common or
recent emoji. Metrics measure the first representative gold only; they do not
measure precision or all valid interpretations. `感激で胸がいっぱい` returning 🥲
is a gold miss even though it can be a reasonable choice.

Android 14 device check (disposable emulator only):

```sh
bash ./gradlew :app:connectedFullStandardDebugAndroidTest -PpreviewDeviceTest=true \
  -Pandroid.testInstrumentationRunnerArguments.class=com.kazumaproject.markdownhelperkeyboard.EmojiSearchImeDeviceTest
```

The dedicated `Emoji Search Android 14` workflow runs reproducibility, symbol
unit tests, actual-dictionary evaluation, editor-isolation tests (API 28 and 34),
and the API 34 real IME probe. The existing Preview workflow is unchanged and
continues to cover ordinary keyboard input, layouts, history infrastructure and
Full Preview APK inspection. Signing remains restricted to protected `preview`;
this feature does not change that trust boundary.

On a successful API 34 run, `emoji-search-debug-testbench` contains the exact
debug-signed APK tested by that run, with ARM64 and x86_64 native libraries and
inspected search assets. Its separate `.preview.testbench` application ID allows
review alongside normal Sumire. It uses an ephemeral CI debug certificate, not the
Preview distribution certificate. `metadata.json` records the source SHA and APK
SHA-256. The ordinary Full Preview unsigned APK remains available from the existing
Preview workflow; its production signing restrictions are unchanged.
