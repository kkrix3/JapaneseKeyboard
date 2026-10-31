# Google絵文字だけを試すFeature署名ブランチ

## 固定したソース

`verify/google-emoji-rendering` は次の2コミットの通常mergeと、この配布設定・説明だけで構成する。
既存Previewの内容にGoogle絵文字表示切替を追加し、別作業の絵文字検索は含めない。

| 用途 | ref | SHA |
| --- | --- | --- |
| Preview基点 | preview | e3cc93537c1c0912cb451b39133c270d39a10b9b |
| Google絵文字 | feature/google-emoji-rendering | 948f2dbce85081b8ecbe3b08813af3c9c2ff8b42 |
| 既存Feature配布基盤 | build/feature-apk-v1.7.133 | 7534cd734cdb5af42be53c5ddde1b85fb787e109 |

`feature-build.json` がこれらのSHAを固定する。署名用workflow・scripts/feature・
app/build.gradle・app/src/featureは配布基盤と同一で、CIが差分なしを検証する。
検証コミットSHA、APKハッシュ、versionCodeは各runのartifactに記録される。
元の機能ブランチ、Draft PR #13、preview/devは変更しない。

## 既存署名経路

使用するのは **Sumire Full Feature CI** (`.github/workflows/feature-ci.yml`) と
`feature-signing` Environment。通常Preview用の `preview-signing` は使用しない。
既存の[run 40483089124](https://github.com/kkrix3/JapaneseKeyboard/actions/runs/40483089124)
でverify・device・signの成功と `sumire-feature-full-signed` の生成を確認した。
この実績は別機能のもので、今回のAPKが署名済みであることを意味しない。
現在のEnvironment保護設定や秘密値そのものは参照していない。

- pushでは未署名ビルド・回帰テスト・API 35の実IMEテストを実行し、署名はスキップする。
- 署名は所有者 `kkrix3` による `workflow_dispatch`、`sign=true`、`verify/` ブランチ、
  `FEATURE_SIGNING_ENABLED=true`、`feature-signing` の承認を必要とする。
- 署名jobは同じrunの検査済みAPKだけを受け取り、キーを渡す前に由来・ハッシュ・
  パッケージ・versionCodeを再検査する。checkout、Gradle、リポジトリスクリプトは実行しない。
- 既存の `FEATURE_KEYSTORE_BASE64` / `FEATURE_KEYSTORE_PASSWORD` / `FEATURE_KEY_ALIAS` /
  `FEATURE_KEY_PASSWORD` をそのEnvironment内で使用する。秘密値をチャットへ送らない。

## 所有者による署名実行

1. [ActionsのSumire Full Feature CI](https://github.com/kkrix3/JapaneseKeyboard/actions/workflows/feature-ci.yml)
   を開き、Run workflowで **verify/google-emoji-rendering** を選び、**signをON**にして新規実行する。
2. verify・deviceの成功、`feature-build-resolved.json` の上表のSHA、対象ブランチの最新コミットを確認する。
   Environmentが対象ブランチを許可していることも確認する。
3. `feature-signing` の承認を行う。鍵の作成・変更・承認の代行は行っていない。
4. 成功した同じrunの **sumire-feature-full-signed** artifactをダウンロードする。
   `signed.apk` で終わるAPK、`certificate-verification.txt`、`apk-sha256.txt`、
   `unsigned-provenance.json` が入る。未署名artifactはインストールできない。

新しい配布APKは新規workflow実行で生成する。signだけ失敗した場合はそのjobだけ再試行できる。
GitHub連携にはworkflow_dispatchの実行機能がないため、ここからの手動実行は所有者が行う。

## インストールと確認

表示名は **Sumire Feature**、applicationIdは
`com.kazumaproject.markdownhelperkeyboard.feature`。Full Standard Feature版で、
通常版・Previewとは共存する。既存のSumire Featureがある場合は同じ固定鍵と増加する
versionCodeでアプリ全体を更新するため、別の試験機能は累積しない。
必要な設定は更新前にバックアップする。

Sumire FeatureをIMEとして有効にし、設定 → クリップボードとショートカット → 記号キーボード →
「絵文字の表示」で「Google絵文字」を選ぶ。初期値は「端末標準」。
一覧・履歴・肌色選択、設定の即時反映・再起動後の保持・標準表示への復帰を確認する。
入力先アプリの表示はそのアプリのフォントによる。入力Unicodeは変更しない。

元のGoogle絵文字機能は[CI run 40893779784](https://github.com/kkrix3/JapaneseKeyboard/actions/runs/40893779784)
でAPI 24/34の描画・複合文字・新プロセスでの設定保持と、API 35の実IME回帰を検証済み。
この署名ブランチのFeature CIはcore/custom_keyboard全件と上記Previewと同じappフィルター
（絵文字設定・フォント・カテゴリを含む）、API 35 SmallTsu実IME回帰を実行する。
API 24/34の絵文字moduleテストをこのFeature workflowで再実行するものではない。

ドコモ実機のAndroid 14でGoogleのデザインに切り替わること、カラー・ZWJ・国旗・肌色、
通常/フローティング/分割、カスタムフォント、入力先への文字列を確認する。
署名APKの実機インストールとOEM端末上の描画は未検証。

フォント・ライセンス・制約は[実装説明](../google-emoji-rendering.md)を参照。
