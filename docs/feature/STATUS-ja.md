# ダブルタップ促音・Feature共通枠の検証記録

## 完了範囲

機能実装、設定、GitHubへの保存、テスト、未署名Full Standard Feature APKの生成と検査。
署名はユーザーの `feature-signing` Environment 作成報告待ち。保護設定の確認、署名、
ユーザー実機での3者共存・更新インストールは未完了。**未署名APKはインストールできません。**

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
mainは開始時に存在しなかった。既存dev・preview・feature/custom-hapticsへの直接変更、
force-push、削除、上流PR・コメント、Release公開はしていない。

## 実行結果

[最終CI run #10](https://github.com/kkrix3/JapaneseKeyboard/actions/runs/35210714262) の verify・device は成功。sign は準備待ちのためスキップ。

| 検証 | 件数 | 結果 |
| --- | ---: | --- |
| core JVM | 62 | 成功 |
| custom_keyboard JVM / 実View | 239 | 成功 |
| app JVM 回帰（対象は下記filters） | 105 | 成功 |
| JVM合計（促音関連41件を含む） | 406 | 失敗・エラー・skip 0 |
| 配布基盤Python | 4 | 成功 |
| API 35 / x86_64 実IME instrumented test | 1メソッド・20条件 | 20条件すべて成功、skip 0 |

[単体・回帰・APK検査レポート](https://github.com/kkrix3/JapaneseKeyboard/actions/runs/35210714262/artifacts/10493210875) ／ [実IME結果・logcat](https://github.com/kkrix3/JapaneseKeyboard/actions/runs/35210714262/artifacts/10492487858)

実IMEの条件はTenKey／カスタムの通常・フローティング（各プレビューOFF/ONで計8条件）、
Sumireのdefault・circle・sumire・second-flick・third-flick・center-guide-flick（各OFF/ONで計12条件）。
各条件で機能OFFのカーソル基準、ONの単独入力・区切り後の組・重ならない組・タップ＋フリック・外部カーソル、
ライブ変換ONで読みを継続し遅い候補で戻らないことを確認した。
連続操作は起動中の実IMEのroot Viewから実Service・InputConnection・EditTextを通す。
単独入力等はOS注入も使う。連続操作のテストをOS InputDispatcher全体やユーザー実機の検証とは扱わない。
80組のイベント注入ログはUP→DOWN 80〜281ms、1回目押下 25〜119ms。
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
  -PfeatureVersionCode=1000000010 -PfeatureBuildTag=double-tap-small-tsu-165ba703cab4 \
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
このfixtureをwindow ID照合へ修正し、最終run #10で20条件すべてが成功した。

基点は未確定範囲がある選択通知を早期returnするため、外部カーソル移動後も次の文字が
従来の未確定末尾へ入る場合がある。今回の促音予約は破棄し、カーソル処理そのものは変更しない。

## 未署名APK

[検査済み未署名Full Feature APK（ZIP artifact）](https://github.com/kkrix3/JapaneseKeyboard/actions/runs/35210714262/artifacts/10493025958)

| 項目 | 実体検査結果 |
| --- | --- |
| APK名 | `sumire-feature-double-tap-small-tsu-165ba703cab4-full-unsigned.apk` |
| applicationId | `com.kazumaproject.markdownhelperkeyboard.feature` |
| 表示名 | Sumire Feature |
| versionName | `1.7.115-feature-double-tap-small-tsu-165ba703cab4` |
| versionCode | `1000000010` |
| variant | Full Standard Feature |
| debuggable | false |
| 署名 | なし（未署名） |
| APK SHA-256 | `054db5a84a7c4b8e4d1d760ff3f3dce2554222b18ecaad66dc82d0d499db63ea` |
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

1. ユーザー本人のPCでFeature専用の固定鍵を作成・保管・バックアップする。Preview鍵は流用しない。
2. `feature-signing` Environmentに専用の4 Secretsを登録する。秘密値をチャットに貼らない。
3. Required reviewers、自己承認可能な構成、対象verifyブランチの制限を本人が確認する。
   Environment画面のアクセスは拒否され、こちらから保護機能の利用可能性は確認できていない。
4. [手動入口のDraft PR #1](https://github.com/kkrix3/JapaneseKeyboard/pull/1)をレビューして本人が反映する。
   手動入口は既定ブランチへの配置待ち。pushによる未署名CIは実行済み。自動マージしない。
5. `FEATURE_SIGNING_ENABLED=true`（Repository Actions variable）を設定し、所有者として
   Sumire Full Feature CI → Run workflow → 検証ブランチ → sign ONで新規実行する。
6. テスト、各SHA、versionCode、APKハッシュを確認して本人がEnvironmentを承認する。
7. 署名済みartifactのAPKを取得し、公開証明書SHA-256とAPKファイルSHA-256を別々に確認する。

詳細は[署名手順](https://github.com/kkrix3/JapaneseKeyboard/blob/build/feature-apk-infrastructure/docs/feature/SIGNING-ja.md)。
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
