# ダブルタップ促音・Feature共通枠の検証記録

## 完了範囲

機能実装、設定、GitHubへの保存、テスト、未署名Full Standard Feature APKの生成と検査。
2026-09-17の再開確認: ユーザーから `feature-signing` Environment 作成済みとの報告あり。
手動入口PR #1は本人がpreviewへマージ済み。本人起動のrun #11もテストと未署名APK検査に成功した。
signジョブはスキップで、承認待ちではない。署名済みartifactと承認履歴はない。
Environmentの保護設定・4 Secrets登録・有効化変数の状態は現在の接続で確認できていない。
署名とユーザー実機での3者共存・更新インストールは未完了。**未署名APKはインストールできません。**

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

[再開時のCI run #11](https://github.com/kkrix3/JapaneseKeyboard/actions/runs/35226212046) の verify・device は成功。sign はスキップ。
2026-09-17 13:18:08 UTC（22:18:08 JST）にkkrix3がworkflow_dispatchで起動、再実行者もkkrix3、attempt 1。
対象は `verify/double-tap-small-tsu` の上記検証SHA。前回run #10と同じソースを、新しいversionCodeで再検証している。
取得したXMLレポート、実IME logcat、CI実行ログ、APK実体検査レポートを照合した。
同時期の[Preview CI #10](https://github.com/kkrix3/JapaneseKeyboard/actions/runs/35225347168)も成功、Preview署名はスキップ。
Preview CI内の既存「Full feature」ジョブはcustom-hapticsのDebug検査用で、今回の促音Feature共通枠APKとは別。

| 検証 | 件数 | 結果 |
| --- | ---: | --- |
| core JVM | 62 | 成功 |
| custom_keyboard JVM / 実View | 239 | 成功 |
| app JVM 回帰（対象は下記filters） | 105 | 成功 |
| JVM合計（促音関連41件を含む） | 406 | 失敗・エラー・skip 0 |
| 配布基盤Python | 4 | 成功 |
| API 35 / x86_64 実IME instrumented test | 1メソッド・20条件 | 20条件すべて成功、skip 0 |

[単体・回帰・APK検査レポート](https://github.com/kkrix3/JapaneseKeyboard/actions/runs/35226212046/artifacts/10499579525) ／ [実IME結果・logcat](https://github.com/kkrix3/JapaneseKeyboard/actions/runs/35226212046/artifacts/10499274443)

実IMEの条件はTenKey／カスタムの通常・フローティング（各プレビューOFF/ONで計8条件）、
Sumireのdefault・circle・sumire・second-flick・third-flick・center-guide-flick（各OFF/ONで計12条件）。
各条件で機能OFFのカーソル基準、ONの単独入力・区切り後の組・重ならない組・タップ＋フリック・外部カーソル、
ライブ変換ONで読みを継続し遅い候補で戻らないことを確認した。
連続操作は起動中の実IMEのroot Viewから実Service・InputConnection・EditTextを通す。
単独入力等はOS注入も使う。連続操作のテストをOS InputDispatcher全体やユーザー実機の検証とは扱わない。
80組のイベント注入ログはUP→DOWN 80〜269ms、1回目押下 25〜71ms。
これはテストの入力条件であり、製品の入力遅延ベンチマークではない。

app filters: `*SmallTsu*`, `*FlickInputPreviewCoordinatorTest`, `*FlickTextMutationResolverTest`, `*ShortcutActiveStateResolverTest`, `*KeyboardLayout*Test`, `*KeyboardBackup*Test`, `*CandidateQueryPolicyTest`, `*ConversionLearningSessionTest`, `*ComposingTextArbiterTest`。
core/custom_keyboardは全件、appは明示した回帰対象。appの全テストを実行したという意味ではない。
ユーザー実機での操作、署名済みAPKの3者共存・A→B更新は未実施。

実行コマンド:

```sh
python3 -m unittest discover -s scripts/feature -p 'test_*.py' -v
python3 scripts/feature/run_tests.py
# core/custom_keyboardは全件、appはfeature-build.jsonで明記した回帰テスト
bash ./gradlew :app:connectedFullStandardDebugAndroidTest -PfeatureDeviceTest=true \
  -Pandroid.testInstrumentationRunnerArguments.class=com.kazumaproject.markdownhelperkeyboard.SmallTsuImeDeviceTest \
  --no-daemon --console=plain --max-workers=2
bash ./gradlew :app:assembleFullStandardFeature \
  -PfeatureVersionCode=1000000011 -PfeatureBuildTag=double-tap-small-tsu-165ba703cab4 \
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
このfixtureをwindow ID照合へ修正し、run #10で20条件すべてが成功し、同じソースのrun #11でも成功した。

基点は未確定範囲がある選択通知を早期returnするため、外部カーソル移動後も次の文字が
従来の未確定末尾へ入る場合がある。今回の促音予約は破棄し、カーソル処理そのものは変更しない。

## 未署名APK

[検査済み未署名Full Feature APK（ZIP artifact）](https://github.com/kkrix3/JapaneseKeyboard/actions/runs/35226212046/artifacts/10500290087)

| 項目 | 実体検査結果 |
| --- | --- |
| APK名 | `sumire-feature-double-tap-small-tsu-165ba703cab4-full-unsigned.apk` |
| applicationId | `com.kazumaproject.markdownhelperkeyboard.feature` |
| 表示名 | Sumire Feature |
| versionName | `1.7.115-feature-double-tap-small-tsu-165ba703cab4` |
| versionCode | `1000000011` |
| variant | Full Standard Feature |
| debuggable | false |
| 署名 | なし（未署名） |
| APK SHA-256 | `d99f605ab11459dc83e931de5d87f1e8a169c64248c3f88545ab6b4370d0c5ac` |
| 署名証明書SHA-256 | なし（未署名のため） |
| 整列 | zipalign 16KB検査成功 |
| ソース | 上表の機能・基盤・検証SHAと一致 |

provider authorityは `.feature.fileprovider` と `.feature.androidx-startup`、
アプリ固有permissionは `.feature.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`。
設定Activityはこのアプリに含まれる元namespaceのMainActivityを参照する。
arm64-v8a / x86_64 のZenz・LiteRT/Gemma・OpenMP native、Zenzモデル、辞書資産を保持。
artifactの保存期間は30日。**未署名APKは実機へインストールできない。**

APK内のManifest、IME設定先、provider authority、Full用ZenzモデルとARM64 native、
Gemma runtime、辞書資産、非debuggable、16KB整列、未署名であることを検査する。
本家／Previewと異なるIDとauthorityはAPK実体で確認するが、3アプリ同時インストールと
実機の各設定画面への遷移、固定証明書によるA→B更新・設定保持は署名準備後の確認事項。

## 署名の再開手順

run #11のsignは実行条件を満たさずスキップされており、鍵を読み出すステップは一度も実行されていない。
Environment名を作成しただけでは署名開始にならない。公開run APIから実行時のsign入力とRepository変数の値は確認できず、
「signがOFFだった」「変数が未設定だった」のどちらかを断定しない。
ジョブ全体がスキップされたため、Secretの誤りによる署名処理失敗ではないが、Secretが正しく登録済みとも判断できない。

1. Environment作成とPR #1のマージは完了済み。作り直し・マージのやり直しは不要。
2. 本人のPCで保管・バックアップしたFeature専用固定鍵を使用していることを確認する。
   Environment `feature-signing` に `FEATURE_KEYSTORE_BASE64`、`FEATURE_KEYSTORE_PASSWORD`、
   `FEATURE_KEY_ALIAS`、`FEATURE_KEY_PASSWORD` の4 Secretsを登録する。既に登録済みなら再登録不要。
   秘密値はチャットへ貼らない。
3. Environment画面でRequired reviewersに `kkrix3`、Prevent self-reviewがOFF、
   Deployment branchesがSelected branchesで `verify/*` を許可していることを本人が確認する。
   前回のブラウザー確認はアクセス承認が拒否された。今回はその操作を再試行していない。
   現在のGitHub接続はEnvironment保護設定を読めないため、利用可能性は本人の確認が必要。
   必要な保護機能が使えない場合は有効化せず、その状況を連絡する。
4. 保護・鍵の準備が揃ったら、Settings → Secrets and variables → Actions → Variables の
   **Repository variables** に `FEATURE_SIGNING_ENABLED` があり、値が小文字の `true` であることを確認する。
   この有効化値はSecretではなくRepository variable。Environment内だけに置く設定ではない。
5. 所有者本人が[Sumire Full Feature CI](https://github.com/kkrix3/JapaneseKeyboard/actions/workflows/feature-ci.yml) →
   Run workflowでブランチ `verify/double-tap-small-tsu` を選び、
   「確認済みのコミットを専用Feature鍵で署名する（Environment承認が必要）」をONにして**新規実行**する。
   前回のsign入力がOFFなら再実行ボタンでその入力は変更できない。新規runで番号を増やして作る。
   今回Workから署名起動・承認は代行していない。添付実装指示の「所有者が明示的に起動しEnvironment承認する」経路を維持する。
6. verifyとdevice成功後、検証SHA `165ba703cab49e637dc2d453cb950b93ad088896`、
   機能・基盤SHA、versionCode、APKハッシュを確認し、本人がReview deploymentsで `feature-signing` を承認する。
   承認前のrun URLを共有すればWork側でもテスト結果と出所を確認できる。秘密値や鍵ファイルは不要。
7. `sumire-feature-full-signed` artifactが生成された後、APKと署名検査を確認する。
   `certificate-verification.txt` が公開証明書SHA-256、`apk-sha256.txt` が署名後APKのSHA-256。
   `unsigned-provenance.json` は署名前の情報。署名後APKのハッシュとは区別する。

詳細は[署名手順](https://github.com/kkrix3/JapaneseKeyboard/blob/build/feature-apk-infrastructure/docs/feature/SIGNING-ja.md)。
同文書末尾の「Environment未作成」は前回の記録であり、今回の作成報告で更新された。本書の再開記録を最新状態とする。
Workによる長期鍵の生成、秘密値の受け取り、Environment承認代行は行っていない。

## ユーザー実機チェックリスト（署名済み版を取得後）

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
