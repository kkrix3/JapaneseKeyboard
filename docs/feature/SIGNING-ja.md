# ユーザー本人が行うFeature署名準備

Workは長期署名鍵を作成・取得しない。Previewの鍵・Secrets・Environmentは変更しない。
署名が未準備でも、未署名Full Feature APKとテスト結果までは利用できる。
**未署名APKは実機へインストールできません。** Debug APKは代用品として配布しない。

1. 自分のPCのAndroid Studio → Build → Generate Signed App Bundle / APK → APK → Create newで、
   **Feature専用**のkeystoreを作る。既存Previewの鍵は選択しない。
   keystore本体、alias、keystore password、key passwordをパスワード管理ツールと安全な別媒体に保管。
   名前・組織等の証明書情報はAPKから読める公開情報。実名・住所を入れる必要はない。
2. GitHubの対象fork → Settings → Environments → New environmentで `feature-signing` を作成。
   Required reviewers: `kkrix3`。Prevent self-reviewはOFF (単独所有者が自分の実行を承認するため)。
   管理者の保護迂回は可能なら禁止。Deployment branchesをSelected branchesにして `verify/*` を許可。
   この設定を本人が画面で確認する。利用できない保護機能がある場合は有効化変数をtrueにしない。
3. このEnvironment内にだけ、次の4 Secretsを登録する。

   | 名前 | 内容 |
   | --- | --- |
   | FEATURE_KEYSTORE_BASE64 | Feature専用keystoreのBase64 |
   | FEATURE_KEYSTORE_PASSWORD | keystoreのpassword |
   | FEATURE_KEY_ALIAS | alias |
   | FEATURE_KEY_PASSWORD | private keyのpassword |

   Base64はPC上だけで生成する。PowerShell例 (パスは自分の保存先に変更):
   `[Convert]::ToBase64String([IO.File]::ReadAllBytes('C:\keys\sumire-feature.jks')) | Set-Clipboard`
   GitHubのSecret欄に貼り付け、作業後にクリップボードを消去。チャット、Issue、ログへ貼らない。
4. 別途用意した手動入口のDraft PRを確認し、ユーザー自身でpreviewへ反映する。
   devと既定ブランチの選択は変更しない。Feature機能そのものをpreviewへ取り込む操作ではない。
5. Environmentの保護・鍵・バックアップを確認後、Repository Actions variable
   `FEATURE_SIGNING_ENABLED` を `true` にする (Secretではない)。
6. 所有者kkrix3としてActions → Sumire Full Feature CI → Run workflowを開き、
   検証対象の `verify/<name>` を選び、signをONにして新規実行する。
   テスト結果と機能SHA・基盤SHA・検証SHA・versionCode・APKハッシュを確認してEnvironmentを承認する。
   Workによる承認代行はしない。再実行者もkkrix3である必要がある。
7. `sumire-feature-full-signed` artifactのAPKを取得する。証明書SHA-256は同artifactの
   `certificate-verification.txt`、ファイルSHA-256は `apk-sha256.txt`。2つの値は別物。
   固定証明書SHA-256を記録し、次回以降も一致を確認する。
   署名済みAPK名にも機能名・短縮検証SHAが入る。`unsigned-provenance.json` は
   署名前のAPKハッシュとソースの組み合わせであり、署名後のAPKハッシュは `apk-sha256.txt` を使う。

署名ジョブは同じworkflow runの検査済みAPKだけを受け取り、ハッシュとAPK実体を再検査する。
署名工程だけ失敗した場合は、そのジョブだけ再試行できる。新しいAPKは新規workflow実行で作る。
秘密は署名ステップだけに渡し、一時ディレクトリを終了時に削除。公開artifactはAPKと公開検査情報のみ。

## インストールと実機チェック

初回はAndroid側で配布元のインストール許可が必要。OSの警告や保護を迂回しない。
本家・Preview・Sumire Featureが3つ並ぶこと、IME選択・設定画面もFeatureを開くことを確認。
別のFeatureへ更新するときは同一鍵・増加番号のAPKを使い、アンインストールしない。
PCでは `python3 scripts/feature/verify_update_pair.py A.apk B.apk` の後 `adb install -r B.apk`。
導入済みAで一般設定、カスタムキーボード、辞書を変更し、B更新後も保持されることを確認。
署名していない状態でこのA→B試験を実施済みとは扱わない。

2026-09-17時点でユーザーからEnvironmentは未作成との連絡あり。作成完了の報告まで署名は待機。
設定画面のブラウザー確認はアクセス承認が拒否され実行できなかった。
Required reviewers／自己承認／branch制限の利用可能性は未確認。上の確認を完了するまで署名を有効化しない。
