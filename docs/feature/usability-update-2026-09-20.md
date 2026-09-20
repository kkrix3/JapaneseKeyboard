# 2026-09-20 ダブルタップ促音・使い方改善版

## 変更内容

- 旧設定の「共通」タブ → キーボードの「入力方式」から、既存のダブルタップ促音設定を開ける。
- 判定時間を短くする効果と、「かく」のつもりでも時間内なら「っく」になる競合を設定画面で説明する。
- 対象行・除外条件は2回目の実際の出力で判定すること、1回目の未確定追加範囲全体を置換することを説明する。
- 実機確認HTMLを2026-09-20.1へ更新。C05の期待値は「っき」。TenKeyとカスタムの区別、取消し・二本指・直接入力・特殊キーの明示ダブルタップの手順を補足する。

入力処理、通常入力の即時性、初期OFF・200ms、保存キー／形式、署名基盤は#13から変更なし。
現在本人が設定している100ms等を更新で初期値に戻す変更はない。
旧設定の検索は画面へのリンクを除外する既存実装なので、旧設定では「共通」タブの入口を使う。
検索で開けるとした一時的な説明は、機能ブランチの後続文書コミットで訂正済み。
2回目DOWNでの早期表示・選択置換直後の促音化は含めない。
S06の入力先切替時の遅さは、促音OFF・本家でも再現するとの本人報告を前提に、共通の別件として扱う。

## ソースと保存先

| 役割 | ブランチ | SHA |
| --- | --- | --- |
| 固定dev基点 | dev | `a47715a453cee3ecb40e9757ffbd7d4790ce7ae9` |
| 機能・設定・説明 | feature/double-tap-small-tsu | `dcbe3c86ea2f8e20fcd5b70deb5f6e3934025684` |
| Feature共通配布基盤 | build/feature-apk-infrastructure | `e2adc19425c7842dc0ecbd43c20537463c1130c4` |
| 通常mergeと参照SHA固定 | verify/double-tap-small-tsu | `e7ba809ff91a53fec4e72d1a31b5eaa4d5fd214d` |
| この検証・更新手順 | docs/feature-signing-status | この文書の履歴を参照 |

設定の実装コミットは `bb7436dc1ee72117254a6a480bae57c549aa9133`。
`dcbe3c8` は旧検索に関する説明1行の訂正のみ。
機能と基盤は従来どおり分離し、merge commitで組み合わせた。
dev・preview・feature/custom-hapticsは変更せず、mainの新設、上流同期、上流への投稿、Release公開、自動マージは行わない。

## 検証

最終ソースのCI: [#15](https://github.com/kkrix3/JapaneseKeyboard/actions/runs/35491809548)。
先行する[#14](https://github.com/kkrix3/JapaneseKeyboard/actions/runs/35491454438)との差は文書と参照SHAのみ。
app/core/custom_keyboard、workflow、配布スクリプトに差がないことを確認した。

#15はverify・deviceとも成功、signはpush起動のため予定どおりスキップ。
先行#14もverify・device成功、signスキップ。追加のコード修正は不要だった。

| 検証 | 件数 | 結果 |
| --- | ---: | --- |
| core JVM | 62 | 成功 |
| custom_keyboard JVM / View | 239 | 成功 |
| app JVM（指定した回帰対象） | 105 | 成功 |
| JVM合計（促音関連41件を含む） | 406 | 失敗・エラー・skip 0 |
| 配布基盤Python | 4 | 成功 |
| API 35 x86_64 実IME | 1メソッド・20条件 | 成功、skip 0 |

[単体・APK検査レポート](https://github.com/kkrix3/JapaneseKeyboard/actions/runs/35491809548/artifacts/10599283366) ／
[実IMEレポート](https://github.com/kkrix3/JapaneseKeyboard/actions/runs/35491809548/artifacts/10599382472)。
XMLレポートの件数・結果を集計し、logcatの20条件・80組の注入記録と照合した。
core/custom_keyboardは全件、appは指定した回帰対象で、app全件を実行したという意味ではない。

ローカルではXML構文・既存ナビゲーションとの接続・設定既定値の維持、`git diff --check`、
配布基盤のPythonテスト4件を確認した。入力処理と既存テストが#13と同一であることも差分で確認した。
Android SDKを備えたローカル検証環境はないため、Androidビルド／テストは上記GitHub Actionsで行う。
XMLの実体験としての遷移、文字サイズ別の読みやすさ、署名済みAPKの実機更新は利用者確認が必要。

CIの主要コマンド（appの単体試験は `feature-build.json` の指定範囲）:

```sh
python3 -m unittest discover -s scripts/feature -p 'test_*.py' -v
python3 scripts/feature/run_tests.py
bash scripts/feature/device_tests.sh
bash ./gradlew :app:assembleFullStandardFeature \
  -PfeatureVersionCode=1000000015 -PfeatureBuildTag=double-tap-small-tsu-e7ba809ff91a \
  --no-daemon --console=plain --max-workers=2
```

実IME試験の時間条件のある操作は、起動中のIME root ViewからService・InputConnection・EditTextを通す。
OSのInputDispatcher全体を通す試験やユーザー実機での確認とは区別する。
単独入力・カーソル操作にはOS注入も使い、時間の厳密な境界は単体／View試験で確認する。
実IMEレポートは1メソッド・20条件すべて成功、skip 0。80組の注入ログは
UP→DOWNが80〜252ms、1回目の押下が25〜116msで、試験設定500ms／長押し閾値300msの範囲内。
これらは注入した入力条件であり、製品の入力遅延ベンチマークではない。

## 未署名Full Feature APK

[検査済み未署名APK（ZIP artifact）](https://github.com/kkrix3/JapaneseKeyboard/actions/runs/35491809548/artifacts/10599597124)。
**このAPKは未署名で、実機へインストールできない。署名済み版は次の所有者実行で作る。**

| 項目 | #15の検査結果 |
| --- | --- |
| applicationId | `com.kazumaproject.markdownhelperkeyboard.feature` |
| 表示名 | Sumire Feature |
| versionName | `1.7.115-feature-double-tap-small-tsu-e7ba809ff91a` |
| versionCode | `1000000015` |
| variant | Full Standard Feature |
| debuggable | false |
| 署名／証明書 | 未署名／なし |
| APKバイト数 | 175559911 |
| APK SHA-256 | `b2ea233bfaecc6c73e18b4fa1037e371b45ca425df94961d03b5de7d2444ae6c` |
| ZIP SHA-256 | `e7b801f9866c345837a93235284479601386b5745c0d2907d53bad67d7dc9a31` |
| APK名 | `sumire-feature-double-tap-small-tsu-e7ba809ff91a-full-unsigned.apk` |
| artifact保存期限 | 2026-10-20 06:01:18 UTC |

CIでAPK実体のID・表示名・非debuggable・16KB整列・未署名状態・authority・IME設定画面を検査した。
authorityは `.feature.fileprovider` と `.feature.androidx-startup`、固有permissionも `.feature`。
設定Activityはこのアプリ内の元namespaceのMainActivity。Preview IDへの参照はない。
arm64-v8a / x86_64のZenz・LiteRT/Gemma・OpenMP native、Zenzモデル、辞書を保持する。
取得したZIPのSHA-256をGitHub artifact digestと照合し、ZIP内APKのSHA-256も再計算してmetadataと一致した。
APKのZIP実体でも両ABIのnative、モデル・辞書資産と鍵ファイル非混入を確認した。
ローカルでaapt／apksignerを再実行したという意味ではなく、その検査はCIの結果に基づく。
同じIDと#13より大きい番号は確認済みだが、次版の署名証明書の一致は署名版完成後に確認する。
本家・Previewとの共存の実機結果は#13の利用者報告であり、次版の実機確認はまだ行っていない。

## 実機確認HTML

確認項目版は `2026-09-20.1`、仕様参照SHAは上記機能SHA。全64項目・基本12項目を維持する。
入力・判定・保存処理は版／参照SHA定数以外変更していない。
記録コードのJavaScript構文、64件のID重複なし、旧版JSON拒否、新版JSONの手動判定保持、
訂正した期待値での報告生成、保存キーの版分離を確認した。
期待文字列と一致しても手動NGを自動OKへ変更しない。
今回のブラウザ再描画確認は未実施（ローカルChromium取得がネットワークエラー）。
Androidでの表示・保存・IME操作の確認とは区別する。

配布HTMLのSHA-256: `8770fa0d384ec85e467749ec60fdbaa6d9cd2a5e00cdf926fe5f7302e2e9baa8`。

旧版2026-09-18.1のJSONは書き出した旧HTMLで参照する。新版へ過去の判定を自動移行しない。
[9/18実機報告](manual-check-2026-09-18.md)の原本集計（OK41／NG1／保留2／対象外1／未実施19）は維持する。
C05の期待値訂正を理由に過去のNGをOKへ集計し直さない。元のTXTも変更していない。

## 次の署名操作（所有者本人）

署名鍵・Environmentは#13で準備と実行が確認済み。新しく鍵を作ったり変数を移し直したりする必要はない。
元の実装指示にある「所有者が明示的に起動し、Environment承認を行う」経路を引き続き使う。

1. 上記#15のverify/device成功と検証SHAを確認する。
2. [Sumire Full Feature CI](https://github.com/kkrix3/JapaneseKeyboard/actions/workflows/feature-ci.yml)で **Run workflow** を選ぶ。
3. Branchを `verify/double-tap-small-tsu` にし、対象が `e7ba809ff91a53fec4e72d1a31b5eaa4d5fd214d` であることを確認。署名のチェックをONにして新規実行する。
4. 同じ新規runのverify/deviceが成功したら、対象コミットを確認して `feature-signing` を本人承認する。
5. sign成功後の `sumire-feature-full-signed` を使用する。新規runでは新しいversionCodeが割り当てられるので、#15の番号を署名版の番号として転記しない。

push実行の#15は署名OFF。既存runの「再実行」で署名ONへ変更するのではなく、新しい全体実行が必要。
署名ジョブだけの再試行は同じrunの同じAPK・番号を使う場合に限る。
実行・承認の代行、ガードの緩和、鍵の受領は行わない。

## #13からの更新確認

先に設定保持を確認してから、HTMLの入力試験に合わせて設定を変える。更新前に200msへ戻す必要はない。

更新元は署名済み[#13](https://github.com/kkrix3/JapaneseKeyboard/actions/runs/35236398155)。

| 確認項目 | #13 | 次の署名済み版で必要な条件 |
| --- | --- | --- |
| applicationId | `com.kazumaproject.markdownhelperkeyboard.feature` | 同一 |
| versionCode | `1000000013` | これより大きい値 |
| 証明書SHA-256 | `7d1811b55b29acf2c227ed348ec0b77a9a300e62931ec7c0d02f02aa897e6a76` | 同一 |
| APK SHA-256 | `497d4012cc9683c09b2cf271d5231e5e6264810e13f6c441d0126ff2d861474b` | 次のAPK固有値を別記する |

署名版完成後は公開APKを `python3 scripts/feature/verify_update_pair.py A.apk B.apk` で検査できる
（Android SDK Build Tools 36.0.0と `ANDROID_HOME` が必要、Aが#13・Bが次版）。
ID・証明書・番号の一致条件はAPK検査で確認し、データ保持は下記の端末操作で別に確認する。

1. Featureの一般設定、カスタム配列、ユーザー辞書を、利用可能な各エクスポート方法で保存する。
   一般設定JSONだけで配列や辞書の全データを保存したとは扱わない。
2. 更新前の促音ON/OFF・判定時間・対象行、配列名、辞書に登録済みの架空の語を控える。
3. 次版の署名済みAPKをAndroidで開いて更新する。PC利用時は `adb install -r B.apk`。
   アンインストール、データ消去、強制ダウングレードで代用しない。
4. 版番号が増え、控えた各設定・配列・辞書が保持されたことを確認する。
   新ホームの「入力方式」と旧「共通」→「入力方式」から、同じ促音設定が見えることを確認する。
5. 通常の1回入力が即座に出ること、タップ＋同じキーのフリックが成立することを試す。
   #13で確認済みの全64項目を一律にやり直す必要はない。今回の重点はG06・X06・C05と、未確認だった経路。
6. HTMLのX06等へ更新前後の版、設定値、操作・観察を記録する。試験で変更した値は最後に元へ戻す。

次の署名、#13との署名比較、実機でのA→B更新・設定保持は未完了。完了したこととして報告しない。
