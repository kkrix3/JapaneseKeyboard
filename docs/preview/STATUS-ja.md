# Sumire開発fork 検証報告

署名・実機検証前の報告です。署名秘密鍵はWorkで生成・取得・保存していません。

## Repository / Remotes / Branches

- 正式fork: [kkrix3/JapaneseKeyboard](https://github.com/kkrix3/JapaneseKeyboard)
- origin: `https://github.com/kkrix3/JapaneseKeyboard.git`
- upstream: `https://github.com/KazumaProject/JapaneseKeyboard.git`
- upstream基点／fork dev: `a47715a453cee3ecb40e9757ffbd7d4790ce7ae9`
- feature/custom-haptics: `d0acb1ef3002c59adbe513548b9224b357eb65be`
- CI対象preview: `211feb89953a5f103b8757c8de744e93d388c5ab`
- devは上流追従専用。featureはハプティクス専用。previewは選択した機能＋固有配布構成。
- 本報告を含む後続コミットはCI対象コミットと区別する。preview HEADは本ファイルが属するブランチ先端。

## Migration

移植元は旧PoC feature/custom-hapticsの `fde1890dbd0c17322cf8f063e2557cd4ef7c68de`。
旧ブランチ全体をマージせず、波形モデル・保存・再生・設定UI・入力通知・テストを抽出した。
IMEServiceでは最新の分割キーボード、トグル入力、新しいキー操作を保ち、受理後の通知を追加。
トグル入力は通常のonActionを通らないため、最新側の専用callbackにも通知を追加した。
ダブルタップ処理の変更は遅延入力にハプティクス情報を保持するためで、促音機能の追加ではない。
参照用ファイルの改行差分、PoC専用CIは移植していない。既存の振動タイミング判定も維持した。

## Full / Preview

| 用途 | Task | applicationId |
| --- | --- | --- |
| 機能検証Full Debug | `:app:assembleFullStandardDebug` | `com.kazumaproject.markdownhelperkeyboard` |
| 統合版Full Preview | `:app:assembleFullStandardPreview` | `com.kazumaproject.markdownhelperkeyboard.preview` |

Previewの表示名は `Sumire Preview`。FullのZenz/Gemma構成を維持。
versionCodeは2020年1月1日からのUTC秒数をCI開始後に採番し、Preview実行を直列化する。
署名jobだけの再試行は同じAPKを再利用する。新しい更新APKにはworkflow全体を新規実行する。

## Signing / Update installation

固定署名を使う構成は用意済み。Gradleは未署名APKを作り、隔離した署名jobが同じ実行の
検証済みartifactだけを署名する。所有者がpreviewを指定した手動実行以外では署名しない。
Environment `preview-signing` の4 Secretsをユーザーが登録する:
`PREVIEW_KEYSTORE_BASE64`, `PREVIEW_KEYSTORE_PASSWORD`, `PREVIEW_KEY_ALIAS`, `PREVIEW_KEY_PASSWORD`。
秘密鍵は取り扱っておらず、新規コミットには秘密値を含めていない。
バックアップすべきものはkeystore本体、alias、両password。ChatGPTへ送らず本人が安全に保管する。

署名とA→B更新の実測は未実施。APK2本のID・証明書一致・versionCode増加を確認する
[verify_update_pair.py](../../scripts/preview/verify_update_pair.py) を準備した。
A導入後に設定・波形・キーボード・辞書を変更し、アンインストールせず `adb install -r B.apk`、
各データとIME動作を確認する。詳細は [移行手順](MIGRATION.md)。

## Settings compatibility / Other data

一般設定JSONはSharedPreferences全体をkey/type/valueで保存する既存仕様を維持した。
別アプリIDの保存領域を模した復元、既知の全データ型、未知キー、波形・MEDIAのround-tripを
Robolectricテストで両ブランチとも成功。実際の公式アプリから採取したバックアップの端末復元は未実施。
未知キーは保存し、未知typeは無視する。誤った型やキーごとの仕様差は既存実装の制約として残る。

カスタムキーボードは別形式。旧PoCは最大schemaVersion 3、最新upstreamは4。
新Previewには最新のv4 importerを残してあり、Full/Lite差とバージョン差を分けて扱う。

| データ | 移行方法 |
| --- | --- |
| 一般設定・カスタム波形・MEDIA選択 | 一般設定JSON |
| カスタムキーボード | 専用JSON export/import |
| ユーザー辞書・定型文 | 各画面のtxt export/import |
| 学習辞書・NGワード・カスタムローマ字 | 各画面のJSON export/import |
| マクロ・N-gram・ゼロクエリ・クリップボード | 各専用export/import |
| 外部モデル・画像・URI権限 | 再選択／権限付与／必要に応じ再取得 |
| その他のRoom表・内部ファイル・キャッシュ | 一般設定JSONで丸ごと移るとは保証しない |

公式設定を後から再インポートすると、replaceAllによりPreview固有の波形設定が消える場合がある。
公式からの初回移行を先に行い、Preview設定を作った後はPreview側もバックアップする。

## Tests / CI

Workflow: `Sumire Full Preview CI` (`.github/workflows/preview-ci.yml`)。
[CI run 35032069860](https://github.com/kkrix3/JapaneseKeyboard/actions/runs/35032069860)。
custom_keyboard既存＋追加テスト、および選択したFullアプリ回帰テストは両ジョブで成功。
Full Standard Debug／Full Standard Previewの両ビルド成功。PreviewのID・表示名・versionCode、
非debug、16KB alignment、未署名状態、ARM64ライブラリ、Zenzモデル同梱も検査成功。
このPreview artifactのversionCodeは `211675206`。

- [Full Debug artifact](https://github.com/kkrix3/JapaneseKeyboard/actions/runs/35032069860/artifacts/10421844424)
- [Full Preview unsigned artifact](https://github.com/kkrix3/JapaneseKeyboard/actions/runs/35032069860/artifacts/10422252884)
- [Featureテストレポート](https://github.com/kkrix3/JapaneseKeyboard/actions/runs/35032069860/artifacts/10422740045)
- [Previewテストレポート](https://github.com/kkrix3/JapaneseKeyboard/actions/runs/35032069860/artifacts/10422686025)

artifact保存期限は2026-09-29。未署名Previewはそのままインストールできない。
署名jobは設計どおりskip。署名済みartifactはまだ生成していない。
アプリ全テストの実行ではなく、custom_keyboard suiteと指定したFullアプリ回帰テストの結果。
後続コミットは報告書更新と署名専用のファイル除外regex修正のみで、APK生成コードは同一。
regexは秘密情報を使わず、拒否すべき6件・許可すべき3件をローカル確認した。
初回run 35031841016はworkflowのコンテキスト指定で失敗し、修正後に再実行した。

## Upstream readiness

feature差分にPreviewのpackage・表示名・署名・fork CIは含まれない。MITライセンスを維持。
提案文は [UPSTREAM-PROPOSAL.md](UPSTREAM-PROPOSAL.md)。Issue／PR／Releaseは送信していない。
実機結果を添えてから提案する。v1の文字入力通知対象はFlickKeyboardView（Sumire／カスタム）で、
独立したQWERTY等のすべての入力面まで対応したとはしていない。

## Remaining risks / 実機確認

1. 通常フリックが弱い1発、通常段階では2段波形にならないこと。
2. 2段フリックが明瞭な2発、途中や取消では鳴らないこと。
3. 特殊キーが強い1発、長押し反復とリリースで二重発火しないこと。
4. TOUCHモードと端末触覚OFF。
5. MEDIAモードと端末側の出力設定。
6. 高速連打、分割キーボード、トグル入力。
7. 強い振動後の弱い振動（自動tail補正は追加していない）。
8. 公式Sumireとの共存・IME/ランチャー上の区別。
9. 同じ署名のPreview A→Bの更新インストール。
10. 更新・再起動後の設定、波形、DBデータ保持。
11. 実際の公式Fullバックアップからの復元。
12. Zenz/Gemma等のFull固有機能。

旧PoCは変更していない。次の独立機能はdevから作り、previewへ必要なものだけ統合する。
標準の上流追従は共有履歴を壊さないmerge。未公開の提案用整理だけ必要に応じrebaseする。

## 次にユーザーが行う署名設定

追加セキュリティ要件に従い、鍵生成・バックアップ・Secret登録はユーザー本人が行う。
詳細は [SIGNING.md](SIGNING.md)。forkのdefault branchをpreviewに変更し、
Environment preview-signingにpreview限定と承認者を設定して4 Secretsを登録する。
Repository Actions variable PREVIEW_SIGNING_ENABLED=trueを設定後、previewで手動実行する。
秘密値やkeystoreをチャットへ送らない。devは引き続きupstreamと同一のまま保つ。
