# Feature共通の実機検証枠

本家Full `com.kazumaproject.markdownhelperkeyboard`、日常Preview `.preview`、
実験用Feature `.feature` を分ける。表示名は Sumire Feature。namespace／クラス名は維持。
機能はdevから `feature/<name>`、配布基盤は同じdevから `build/feature-apk-infrastructure`。
両者を `verify/<name>` へ通常mergeし、`feature-build.json` にbase／機能／基盤SHAとrefを固定。
検証SHAはCI実行の実際のHEADを記録する (自己参照するSHAを設定ファイルへ書かない)。
機能SHAと基盤SHAの祖先関係、fork内の参照元、ビルド・署名コードが基盤と一致することをCIで検査。

## ビルド

`bash ./gradlew :app:assembleFullStandardFeature -PfeatureVersionCode=N -PfeatureBuildTag=name-shortsha`

Full Standardのみ。Zenzモデル・ARM64/x86_64 native・Gemma runtimeを維持。
Gradleは鍵を扱わない非debuggable unsignedビルド。未署名APKはそのままインストール不可。
APK名とversionNameに機能名と検証SHAを含める。method.xml、ランチャー・設定・IMEの表示名を分離。
provider authorityはapplicationIdから展開。設定Activityは同じクラス名だがFeatureパッケージの
登録コンポーネントとして起動する。本家／Previewの設定やデータを自動移行しない。

verify/**のpushで秘密なしCIが起動する。元の公開・同期workflowはこの基盤内でのみ
`.github/upstream-workflows` に退避。元ブランチには変更しない。
単体テスト、Viewテスト、選択したFullアプリ回帰テストのレポートを保存し、APKのID・表示名・
非debug・16KB整列・未署名・モデル・native・authority・設定先を実体検査する。
通常push/PRで署名しない。署名ジョブにはcheckout／Gradle／リポジトリスクリプト／キャッシュなし。

## 共通versionCode

同じ `.github/workflows/feature-ci.yml` のGitHub共通 `run_number` + 1,000,000,000。
全Featureで同じworkflowパス・concurrency `sumire-feature-distribution` を使う。
1..2,100,000,000の上限、過去成功runに対する逆行を検査。ブランチ別にworkflowを複製しない。
採番方式とworkflowの同一性は固定する。rename／別workflowへの移行時は番号管理を明示的に移行する。
新しい更新APKは新規workflow実行で作る。古いソースの再検証も新規実行。
全体／ビルドジョブのre-runは拒否し、署名ジョブだけの再試行は同じrunの同じAPKを再使用。
`verify_update_pair.py A.apk B.apk` でID・固定証明書一致・番号増加をAPK2本から確認する。

## Feature入れ替え

同じID・同じ専用固定鍵・増加するversionCodeで更新する。機能は累積せずアプリ全体が入れ替わる。
未知の設定を一括削除しない。DBやバックアップ形式を戻す場合の互換性を確認し、破壊的な実験は
将来の個別applicationIdで行う。アンインストールや強制ダウングレードで問題を隠さない。

## 手動実行の入口

既定ブランチはpreview。新workflowは初回pushでunsigned CIを動かせるが、手動入口の登録には
既定ブランチへの配置が必要。この基盤はpreviewへ勝手にマージしない。
別の `build/feature-dispatch-entry` Draft PRをpreview向けに作り、workflow入口だけをレビュー可能にする。
ユーザーがそのPRを反映後、Actions → Sumire Full Feature CI → Run workflowでverifyブランチを指定する。
入口ファイルを手動追加する場合も、レビューした基盤のworkflowと同じものを使う。
この文書の時点では手動入口・Environment保護は未確認。コードの存在と承認設定の完了は別。
