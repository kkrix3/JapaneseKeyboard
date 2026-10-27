# 入力文字の浮き上がり: 既存Full Feature署名経路

## 対象とソース

検証ブランチ: `verify/input-character-pop-animation`。
既存の `.github/workflows/feature-ci.yml` (Sumire Full Feature CI) を使用する。
ワークフロー、署名ジョブ、Environment、Secrets、共通採番方式は変更しない。

| 役割 | ref | SHA |
| --- | --- | --- |
| 基点 | preview | a83b20478880662cb52e2ba0fa48910a9eeca012 |
| 機能 | feature/input-character-pop-animation | 6bf4874f773eae53c5a26d03b7519537d2287bdd |
| 配布基盤 | build/feature-apk-v1.7.133 | 7534cd734cdb5af42be53c5ddde1b85fb787e109 |

機能と配布基盤を通常mergeした検証専用ブランチで、previewへのマージではない。
基盤は既存 `build/feature-apk-v1.7.132` のFeature資源・検査スクリプトを再利用し、
v1.7.133のGradleへFeature variantを追加したもの。Preview variantとアプリ版数を維持する。
機能のPR #11へ配布基盤は混ぜない。

`feature-build.json` が参照元とSHA、回帰テスト範囲を固定する。
CIは両親の祖先関係、forkの参照、基盤とビルド/署名コードの一致を検査し、
実行HEADを `verificationSha` として `feature-build-resolved.json` とAPK metadataへ記録する。
Android端末テストのパッケージ検査2か所だけ、既存のPreview testbenchに加えて
Feature testbenchも許可した。入力・表示・キャンセル・連続入力のアサーションは同じ。

## 署名実行

1. [Sumire Full Feature CI](https://github.com/kkrix3/JapaneseKeyboard/actions/workflows/feature-ci.yml) を開く。
2. 所有者 `kkrix3` として **Run workflow** を選び、branchを
   **verify/input-character-pop-animation**、**signをON** にして新規実行する。
3. verify/deviceの成功、ソースSHA、APK情報を確認し、`feature-signing` の承認を行う。
4. 完了後に **sumire-feature-full-signed** artifactを取得する。

既存の `FEATURE_SIGNING_ENABLED=true`、`feature-signing` の既存鍵・保護設定、
対象verifyブランチの許可が前提。これらの設定は今回変更しない。
通常pushでは署名をスキップする。新しいAPKの生成には新規Run workflowを使い、
過去run全体のRe-runは使わない（共通versionCodeの逆行防止）。
この作業で署名承認を代行したり、秘密値を取得したりはしない。

既存署名の成功例: [run 38894196608](https://github.com/kkrix3/JapaneseKeyboard/actions/runs/38894196608)。
その公開証明書SHA-256は
`7d1811b55b29acf2c227ed348ec0b77a9a300e62931ec7c0d02f02aa897e6a76`。
新しい署名済みAPKの `certificate-verification.txt` と照合する。

## 出力と試用

- Full Standard Feature、表示名 **Sumire Feature**、非debuggable。
- applicationId: `com.kazumaproject.markdownhelperkeyboard.feature`。
- versionCode: 既存共通workflowの `1000000000 + run_number`。
- versionName/APK名: 機能名と検証コミットの短縮SHAを含む。
- `sumire-feature-full-unsigned`: 未署名APKとソース・ハッシュ情報。
- `feature-verification-reports`: Python/単体/回帰テストとAPK検査情報。
- `feature-ime-device-reports`: API 35の実IMEテスト。
- `sumire-feature-full-signed`: 署名済みAPK、証明書検査、APK SHA-256、署名前の出所情報。

未署名APKはそのままインストールできない。署名済みFeatureは既存Featureの更新用で、
通常Previewや使い捨て `.preview.testbench` とは別アプリ。同じFeature鍵と増加する
versionCodeで更新し、設定保持・各IMEの共存は実機で確認する。

設定 → キーボード表示 → タッチエフェクト → 入力文字の浮き上がりアニメーションをONにする。
初期OFF、280ms、100%→180%、XY固定、最初の20%（56ms）後から透明化。
時間・拡大率・不透明度・補間・基準距離を同じ設定画面で変更できる。
機能の対応範囲と見た目の確認項目は [機能仕様](../input-character-pop-animation.md) を参照。

## 実機フィードバック後の確認

未確定一文字目で候補欄が現れても文字が消えないこと、二段フリックの3×3/上部ガイドで
設定した基準距離と選択セルに応じたキー基準位置から拡大することを確認する。
距離0・1段斜め・2段斜め・軸上配置と、各パラメータの調整も確認する。入力経路の仕様は変更していない。
端末テストは入力直後の状態だけでなく、一文字目の描画時まで演出が残ることも検査する。
旧コミットの署名済みAPKにはこの修正が含まれないため、このブランチで新規Run workflowを行う。
