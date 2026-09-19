# ダブルタップ促音・Feature共通枠の検証記録

## 完了範囲

機能実装、設定、GitHub保存、テスト、Full Standard Feature APKの生成・実体検査・**専用鍵による署名まで完了**。
[run #13](https://github.com/kkrix3/JapaneseKeyboard/actions/runs/35236398155) はverify・device・signがすべて成功。
所有者kkrix3による `feature-signing` Environmentの承認履歴を確認した。
署名完了は2026-09-17 22:08 UTC（2026-09-18 07:08 JST）。
署名済みFull Feature APKを実機検証用に配布できる状態。
2026-09-18の[利用者実機報告と開発側評価](manual-check-2026-09-18.md)を受領した。
原本はOK 41・NG 1・保留2・対象外1・未実施19、基本12項目は全件OK、3者共存は利用者判定OK。
唯一のNG C05はHTML期待値と仕様の不一致。
2026-09-19に本人から、試験APKはrun #13、試験は基本200ms、現在は100msで調整中と追記あり。
S06の入力先切替直後の現象は促音OFF・本家でも再現するため、今回の促音機能固有の不具合から切り分けた。
端末・Android・ブラウザの詳細は未記録。
A→B更新は未実施。項目ごとの確認範囲や入力欄切替の観察はリンク先を参照。

有効化変数をEnvironment variablesに入れていたことが、前回署名スキップの原因だったと本人から報告あり。
Repository variablesへ移して新規run #13を実行し、Environment本人承認を経て署名成功。
機能・基盤・検証SHAや署名ガードの変更で解決したものではない。

## 設定と入力

設定 → 入力方法 → ダブルタップ促音。初期OFF、初期200ms、変更範囲50〜500ms（1ms単位）。
既存ショートカットバーの編集から「ダブルタップ促音」を追加でき、チェック表示が現在のON状態。
行の判定は2回目の出力先頭。初期対象は、か・が・さ・ざ・た・だ・は・ば・ぱ。
旧バックアップでは既定値に戻り、一般設定の新バックアップはON/OFF・時間・対象行を保持する。

1回目は通常の入力タイミングで出力する。1回目UPから2回目DOWNが時間内なら、
2回目終了時に1回目が追加した自分の未確定読みだけを「っ／ッ＋2回目の出力」に置換。
通常入力を保留するタイマー、sleep、同期の文字取得IPCは追加していない。
機能OFFと通常の1回目では文字列の正規化・行判定も行わない。

| 操作 | 結果 |
| --- | --- |
| たタップ → 同じキー左フリック | っち |
| あ入力済み → たタップ → 左フリック | あっち |
| ば入力済み → さタップ → 2段目でじ | ばっじ |
| 2回目の出力がこう／シャ | っこう／ッシャ |
| かを1・2・3・4連打 | か → っか → っかか → っかっか |
| か → 判定時間を超えて区切る → かを2回 | かっか |
| な行OFFでなを2回 | なな |

対象はTenKeyのフリック専用、Sumireの通常・円形・ドーナツ・2段・3段、カスタムかな。
共通Viewの通常／フローティング／分割bindingへ接続。
トグル入力、明示ダブルタップ割当て、長押し、直接確定、複数文字即時確定は従来動作を優先。
物理、QWERTYローマ字、独立Gojuon、手書き、記号面、IME内の辞書編集欄は対象外。
半角カナ、拡張かな、小書きヵヶ、踊り字、ん／ン始まり、既存促音始まり、
漢字・英数字・空白・一般記号・絵文字を含む出力は対象外。正規化は判定だけに使用する。
詳細は[機能仕様](https://github.com/kkrix3/JapaneseKeyboard/blob/feature/double-tap-small-tsu/docs/features/double-tap-small-tsu.md)を参照。

## 保存先とソース

| 役割 | ブランチ | SHA |
| --- | --- | --- |
| 作業開始時のdev | dev | a47715a453cee3ecb40e9757ffbd7d4790ce7ae9 |
| 機能 | feature/double-tap-small-tsu | 99c9f4d649376c23cdb7b9b3205d2d49eb6cb58a |
| 共通配布基盤 | build/feature-apk-infrastructure | e2adc19425c7842dc0ecbd43c20537463c1130c4 |
| 通常mergeした検証ソース | verify/double-tap-small-tsu | 165ba703cab49e637dc2d453cb950b93ad088896 |

開始時点ではfork devとupstream/devに差分なし。同期は実施していない。
mainは開始時に存在せず、再開時も存在しない。dev・feature/custom-haptics・機能・基盤・検証ブランチは前回のSHAを維持。
ユーザーkkrix3が2026-09-17 13:09:37 UTC（22:09:37 JST）に[PR #1](https://github.com/kkrix3/JapaneseKeyboard/pull/1)をマージし、
previewは `25a9ce7cf678c06eece74c94f17e0f453e1ef5d5` へ進んだ。
マージ差分は `.github/workflows/feature-ci.yml` と本書の追加2ファイルだけ。
workflow blob `44ac1ed78d121c0ddcc73c71c6e9e4ca9915771f` は基盤と同一。
Feature本体、Gradle、Preview署名設定はこのマージに含まれない。既定ブランチはpreviewのまま。
Workから既存dev・preview・feature/custom-hapticsへの書込み、force-push、削除、上流PR・コメント、Release公開はしていない。
今回の状態更新は文書専用ブランチ `docs/feature-signing-status` に保存し、previewへ自動マージしない。

## 実行結果

[CI run #13](https://github.com/kkrix3/JapaneseKeyboard/actions/runs/35236398155) のverify・device・signはすべて成功。
2026-09-17 14:51:39 UTC（23:51:39 JST）にkkrix3がworkflow_dispatchで起動、再実行者もkkrix3、attempt 1。
対象は `verify/double-tap-small-tsu` の上記検証SHA。前回と同じソースを新しいversionCodeでビルドした。
取得したXMLレポート、実IME logcat、CI実行ログ、APK実体検査レポートを照合。
署名済みZIPのSHA-256はGitHub artifact digestと一致し、ZIP内APKを再計算したSHA-256も同梱記録と一致した。
署名検証はCIのapksigner結果（v2・v3成功）と公開証明書レポートを照合した。ローカルでapksignerを再実行したという意味ではない。
最初の実IMEレポート取得は途中で切れたZIPだったため再取得し、GitHub artifact digestと一致する完全なZIPを使って確認した。

[Preview CI #10](https://github.com/kkrix3/JapaneseKeyboard/actions/runs/35225347168)も成功、Preview署名はスキップ。
Preview CI内の既存「Full feature」ジョブはcustom-hapticsのDebug検査用で、今回の促音Feature共通枠APKとは別。

| 検証 | 件数 | 結果 |
| --- | ---: | --- |
| core JVM | 62 | 成功 |
| custom_keyboard JVM / 実View | 239 | 成功 |
| app JVM 回帰（対象は下記filters） | 105 | 成功 |
| JVM合計（促音関連41件を含む） | 406 | 失敗・エラー・skip 0 |
| 配布基盤Python | 4 | 成功 |
| API 35 / x86_64 実IME instrumented test | 1メソッド・20条件 | 20条件すべて成功、skip 0 |

[単体・回帰・APK検査レポート](https://github.com/kkrix3/JapaneseKeyboard/actions/runs/35236398155/artifacts/10503867334) ／ [実IME結果・logcat](https://github.com/kkrix3/JapaneseKeyboard/actions/runs/35236398155/artifacts/10503618924)

実IMEの条件はTenKey／カスタムの通常・フローティング（各プレビューOFF/ONで計8条件）、
Sumireのdefault・circle・sumire・second-flick・third-flick・center-guide-flick（各OFF/ONで計12条件）。
各条件で機能OFFのカーソル基準、ONの単独入力・区切り後の組・重ならない組・タップ＋フリック・外部カーソル、
ライブ変換ONで読みを継続し遅い候補で戻らないことを確認した。
連続操作は起動中の実IMEのroot Viewから実Service・InputConnection・EditTextを通す。
単独入力等はOS注入も使う。連続操作のテストをOS InputDispatcher全体やユーザー実機の検証とは扱わない。
80組のイベント注入ログはUP→DOWN 80〜249ms、1回目押下 25〜75ms。
これはテストの入力条件であり、製品の入力遅延ベンチマークではない。

app filters: `*SmallTsu*`, `*FlickInputPreviewCoordinatorTest`, `*FlickTextMutationResolverTest`, `*ShortcutActiveStateResolverTest`, `*KeyboardLayout*Test`, `*KeyboardBackup*Test`, `*CandidateQueryPolicyTest`, `*ConversionLearningSessionTest`, `*ComposingTextArbiterTest`。
core/custom_keyboardは全件、appは明示した回帰対象。appの全テストを実行したという意味ではない。
この表はCIの結果。ユーザー実機の報告は上記の別記録に分け、CIの20条件と合算しない。A→B更新は未実施。

実行コマンド:

```sh
python3 -m unittest discover -s scripts/feature -p 'test_*.py' -v
python3 scripts/feature/run_tests.py
# core/custom_keyboardは全件、appはfeature-build.jsonで明記した回帰テスト
bash ./gradlew :app:connectedFullStandardDebugAndroidTest -PfeatureDeviceTest=true \
  -Pandroid.testInstrumentationRunnerArguments.class=com.kazumaproject.markdownhelperkeyboard.SmallTsuImeDeviceTest \
  --no-daemon --console=plain --max-workers=2
bash ./gradlew :app:assembleFullStandardFeature \
  -PfeatureVersionCode=1000000013 -PfeatureBuildTag=double-tap-small-tsu-165ba703cab4 \
  --no-daemon --console=plain --max-workers=2
```

実IMEテストのDebugは使い捨ての `.feature.testbench`、Full版。配布用APKには使用しない。
時間の境界199/200/201msと遅い2回目UPは、イベント時刻を注入する単体／Viewテストで検証。
実IMEの自動操作は注入処理の時間を考慮して500ms設定で実行する。
ローカルGradleはwrapper ZIP取得時のNetwork is unreachableで実行できず、Android検証はGitHub Actionsで実行。

修正履歴: 初回380件は成功しFull APKビルドも成功したが、検査が最適化後のXMLパスを解決できず失敗。
検査をリソース表から解決する方式に修正した。追加Viewテストでは未接続Viewの非表示試験を
実際の画面に接続する形へ修正し、円形タップの通知不足を実装修正した。
外部カーソルの初期期待値は基点の既存処理と相違していたため、機能OFFの実操作との比較で
既存文字と1回目の保持を検証する。判定のテストを削除・無効化して通したものではない。
ライブ変換の試験は、内部読みの更新と非同期表示の完了を区別して待機する。
通常入力の直後の表示検証は維持し、アプリの通常入力へ待機処理を追加していない。
20通りを通した追加実IME試験では、UiAutomationの各イベント後の描画同期によって
UP→DOWNが500msを超える場合があった。
Android 15の実装ではsync=falseでもwindow transaction同期が残るため、
時間条件のある組は公開WindowInspector経由で実IMEのルートViewへ実時刻イベントを送る。
実Service・InputConnection・EditText・候補処理を通す。単独入力とカーソルケースではOS注入も残す。
連続操作の試験はOS InputDispatcher全体を通す操作とは区別する。
UP→DOWN間隔と1回目の押下時間をログとassertで確認し、単独入力の即時表示試験も残す。
フローティング時は、表示キーのアクセシビリティwindow IDと一致するrootを選ぶ。
イベントはテストスレッドで実時刻を採取してメインスレッドへ順にpostし、最後のUPだけ処理完了を待つ。
Viewの探索や各イベントの処理待ちを、テストの押下時間へ混ぜない。
run #9は通常表示4条件の成功後、フローティングで複数rootの選別に停止した。
このfixtureをwindow ID照合へ修正し、run #10で20条件すべてが成功し、同じソースのrun #11・#12・#13でもdeviceジョブは成功した。

基点は未確定範囲がある選択通知を早期returnするため、外部カーソル移動後も次の文字が
従来の未確定末尾へ入る場合がある。今回の促音予約は破棄し、カーソル処理そのものは変更しない。

## 署名済みFull Feature APK

[実機用・署名済みFull Feature APK（ZIP artifact）](https://github.com/kkrix3/JapaneseKeyboard/actions/runs/35236398155/artifacts/10521872603)

ZIPを展開して `sumire-feature-double-tap-small-tsu-165ba703cab4-full-signed.apk` を使用する。
artifact名は `sumire-feature-full-signed`。未署名版やPreview CIのDebug artifactと取り違えない。

| 項目 | 検査結果 |
| --- | --- |
| applicationId | `com.kazumaproject.markdownhelperkeyboard.feature` |
| 表示名 | Sumire Feature |
| versionName | `1.7.115-feature-double-tap-small-tsu-165ba703cab4` |
| versionCode | `1000000013` |
| variant | Full Standard Feature |
| debuggable | false |
| 署名 | Feature専用Environmentの鍵で署名、apksigner v2・v3検証成功 |
| APKバイト数 | 175707570 |
| 署名済みAPK SHA-256 | `497d4012cc9683c09b2cf271d5231e5e6264810e13f6c441d0126ff2d861474b` |
| 署名証明書SHA-256 | `7d1811b55b29acf2c227ed348ec0b77a9a300e62931ec7c0d02f02aa897e6a76` |
| ZIP artifact SHA-256 | `27df8684ff762ff8bb1fd7cba7bad10249f1379399fb3f5e267b19d042af25a2` |
| 整列 | 署名後もzipalign 16KB検査成功 |
| ソース | 上表の機能・基盤・検証SHAと一致 |

同梱ファイルはAPK、`apk-sha256.txt`、`certificate-verification.txt`、`unsigned-provenance.json` の4点。
秘密鍵・keystoreは配布物に含めない。`unsigned-provenance.json` は署名前の情報なので `unsigned: true` で正しい。
そこにある `sha256` は署名前APKのハッシュで、署名済みAPKのハッシュとは異なる。

[同じrunの未署名APK](https://github.com/kkrix3/JapaneseKeyboard/actions/runs/35236398155/artifacts/10503907236)のSHA-256:
`7d1f131a315f7cfd9950d0943d6c3c2d416453f387a2fa868dff3b71080254b1`。
**未署名版は実機へインストールできない。実機用は上のsigned artifactを使う。**

provider authorityは `.feature.fileprovider` と `.feature.androidx-startup`、
アプリ固有permissionは `.feature.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`。
設定Activityはこのアプリに含まれる元namespaceのMainActivityを参照する。
arm64-v8a / x86_64のZenz・LiteRT/Gemma・OpenMP native、Zenzモデル、辞書資産を保持。
署名済みartifactの保存期限は2026-10-17 22:08:50 UTC。必要なら期限前にAPKと検査情報を手元へ保存する。

既存[Preview署名run](https://github.com/kkrix3/JapaneseKeyboard/actions/runs/35120527517)の公開証明書SHA-256は
`dcbf5b630a85788cb56960b51edf120440ace81d564652ecb7435add2d31f48f`。
署名ジョブログで照合し、今回のFeature証明書とは異なることを確認した。
APK実体の異なるID・authorityと別証明書は確認済み。3アプリ同時インストール、
実機の各設定画面への遷移、固定証明書によるA→B更新と設定保持はユーザー実機の確認事項。3者共存は上記利用者報告にOKの記録あり。

## 署名運用と今回の確認

手動入口PR #1は本人がpreviewへマージ済み。Environmentと鍵の作成・再登録やPRの再マージは不要。
Environment内に置いていた `FEATURE_SIGNING_ENABLED` をRepository variablesへ移すことで、
今回run #13の署名条件を満たした。コード・ガードは変更していない。

今回の承認APIから `feature-signing` をkkrix3がapprovedにしたこと、
Environmentの `can_admins_bypass: false` を確認した。
起動者と承認者がともにkkrix3であり、単独所有者による起動・承認経路が実際に成立している。
Environment全体の設定画面は取得していないため、Deployment branchesの全許可パターン等は独立には確認していない。
Secretの値や長期鍵のバックアップは取得せず、署名成功を通じて今回の署名に必要な設定が使えたことを確認した。

次回の新しいビルドでも、次の経路を使う。

1. [Sumire Full Feature CI](https://github.com/kkrix3/JapaneseKeyboard/actions/workflows/feature-ci.yml) →
   Run workflowで対象 `verify/<name>` を選び、署名のチェックをONにして所有者本人が新規実行する。
2. verify・deviceの成功後、機能・基盤・検証SHA、versionCode、APKハッシュを確認し、
   Review deploymentsで `feature-signing` を本人が承認する。
3. `sumire-feature-full-signed` artifactを取得し、
   `certificate-verification.txt` の公開証明書SHA-256が上記Feature指紋と一致することを確認する。
   `apk-sha256.txt` はその版の署名済みAPKハッシュ。APKハッシュは版ごとに変わる。
4. 初回導入はZIPを展開してsigned APKをインストールし、Androidの入力方法でSumire Featureを有効化・選択する。
   Androidが配布元の許可を求める場合は本人が確認して操作する。
   アプリ内の「入力方法 → ダブルタップ促音」は初期OFFなので、実機検証時にONにする。
5. 更新時は同じFeature鍵と増加したversionCodeを使い、既存Featureをアンインストールせず更新する。
   署名ジョブだけ失敗した場合はそのジョブだけ再試行し、新しい更新版は新規workflow実行で作る。

`FEATURE_SIGNING_ENABLED=true` はSettings → Secrets and variables → Actions → Variables の
**Repository variables**。SecretでもEnvironment variableでもない。
4つの鍵関連Secretsは `feature-signing` Environment内に保持する。
詳細は[署名手順](https://github.com/kkrix3/JapaneseKeyboard/blob/build/feature-apk-infrastructure/docs/feature/SIGNING-ja.md)。
同文書末尾の「Environment未作成」は初回時点の記録であり、本書の署名完了記録が最新状態。
Workによる長期鍵の生成、秘密値の受け取り、Environment承認代行は行っていない。

## ユーザー実機チェックリスト

- 本家・Preview・Sumire Featureが並び、IME選択と設定画面がそれぞれ対応するアプリを開く。
- 機能OFFで従来入力を確認後ONにする。1回タップが待たされず表示されることを確認する。
- 上の操作表を試す。2回目は時間内に触れれば、移動を続けて200msより後に離しても成立する。
- 2段・3段・ドーナツ・カスタム複数文字、通常／フローティング／分割で試す。
- 2回目の取消、別キー、削除、確定、候補選択、カーソル・範囲選択、入力先変更、
  画面回転・再接続、設定変更の後で古い1回目が置換されないことを確認する。
- 長押し、多指操作、トグル、明示ダブルタップ割当て、直接確定、機能OFFの従来動作を確認する。
- ライブ変換ONとOFF、候補選択、高速連続入力で読みが戻らず、取消した読みが誤学習されないことを確認する。
- 時間と対象行、ショートカット状態、新旧バックアップの読み込み結果を確認する。
- 同じFeature ID・固定鍵・増加番号のA→Bを `adb install -r` で更新し、設定・レイアウト・辞書を確認する。
  `python3 scripts/feature/verify_update_pair.py A.apk B.apk` で公開情報を先に検査する。
  機能は累積せずアプリ全体が入れ替わる。アンインストールや強制ダウングレードで確認を省略しない。

問題があれば、端末機種・Android版、APKのversionName/Code、対象アプリと入力欄の種類、
キーボード／フリック方式、フローティング・プレビュー・ライブ変換、機能ON/OFF・判定時間・
対象行、操作順と期待結果／実際の結果を記録する。実機未確認の項目を実施済みとは扱わない。
