# Sumire development fork

Status: feature and Preview branches published. Full Debug and unsigned Full Preview builds,
custom_keyboard tests and selected Full app regressions passed in CI run 35032069860.
Signing and physical-device validation are pending; see STATUS-ja.md and SIGNING.md.

## Repositories and branches

- origin: https://github.com/kkrix3/JapaneseKeyboard.git (formal public fork)
- upstream: https://github.com/KazumaProject/JapaneseKeyboard.git
- PoC, read only: https://github.com/kkrix3/sumire-haptics
- Reviewed upstream baseline: `a47715a453cee3ecb40e9757ffbd7d4790ce7ae9` (1.7.115, code 808).
- PoC source: `fde1890dbd0c17322cf8f063e2557cd4ef7c68de`, feature/custom-haptics.

| Branch | Role |
| --- | --- |
| dev | Exact upstream/dev tracking branch; no fork changes |
| feature/custom-haptics | Raw waveform haptics and directly relevant tests only |
| preview | Selective integration plus package/name/version/build/CI changes |

New independent features start at dev, not preview. Future branches can be named
feature/double-tap-small-tsu, feature/custom-key-commit, feature/haptic-per-stroke.
They are not created as empty placeholders. Check current upstream behavior first.

Preview is a descendant of the haptics feature, representing its initial fast-forward
integration. Subsequent features are integrated with explicit merge commits. Avoid
squashing feature commits into preview, which makes subsequent integration harder.

The preferred routine update is a fast-forward of dev followed by an ordinary merge
of dev into shared feature branches. This preserves published history and preview
ancestry. Feature-to-upstream diffs still exclude unchanged upstream files. Rebase is
reserved for an unpublished branch, or a separate proposal branch prepared deliberately
before PR submission. Never rebase dev/preview or force-push them as a routine update.

```bash
git fetch upstream
git switch dev
git merge --ff-only upstream/dev
git push origin dev
git switch feature/custom-haptics
git merge dev
# Review changed input boundaries; run tests before integration.
git push origin feature/custom-haptics
git switch preview
git merge --no-ff feature/custom-haptics
git push origin preview
```

With multiple features, first merge dev into each feature and test; integrate the
selected feature heads into preview. Conflicts affecting more than one feature should
be resolved on preview unless the fix intrinsically belongs to a feature. Record why.

## Build variants

| Purpose | Task | Application ID | Artifact |
| --- | --- | --- | --- |
| Feature validation | :app:assembleFullStandardDebug | com.kazumaproject.markdownhelperkeyboard | sumire-feature-full-debug |
| Daily Preview input | :app:assembleFullStandardPreview -PpreviewVersionCode=N | com.kazumaproject.markdownhelperkeyboard.preview | sumire-preview-full-unsigned |
| Daily installable APK | isolated apksigner job after tests | same Preview ID | sumire-preview-full-signed |

The unsigned artifact cannot be installed. The feature Debug APK uses the official
package ID and is for build inspection/test devices, not installation over official
Sumire. Daily use starts with the signed Full Preview APK. It is named Sumire Preview
in the launcher, settings and IME chooser. Full retains HAS_ZENZ/HAS_GEMMA and upstream
native dependencies; no Lite substitution is made. ABI splitting is disabled for Preview.

Preview inherits releaseUnsigned/release optimization and uses release library variants.
Gradle signingConfig is explicitly null. Only the isolated Actions job handles the key.

## CI

The only active Preview workflow is `.github/workflows/preview-ci.yml`:
`Sumire Full Preview CI`. Inherited upstream workflows are retained for reference under
`.github/upstream-workflows`, so upstream tag-release/sync jobs do not run for Preview.
Dev and feature retain upstream files unchanged. No Release or upstream PR is created.

On a Preview push or PR, the feature and integration variants are tested without secrets:
custom_keyboard suite, selected Full app regression/backup tests, Full Debug and unsigned
Full Preview. SHA-pinned checkout/setup-java/upload/download actions use contents:read;
checkout does not persist credentials. Failures do not prevent uploading test reports.
Feature checkout is resolved by checkout to a specific commit and recorded in checkout logs.
Feature changes trigger this CI when integrated into Preview (or via manual run); the clean
feature branch does not contain the fork's pipeline.

For manual signed runs, make `preview` the fork's GitHub default branch after publication.
This does not change dev or its upstream-tracking role. GitHub requires workflow_dispatch
workflows to exist on the default branch:
https://docs.github.com/en/actions/how-tos/manage-workflow-runs/manually-run-a-workflow

After the owner enables Actions, push CI builds unsigned artifacts. After configuring
SIGNING.md, the owner uses Actions -> Sumire Full Preview CI -> Run workflow -> preview.
A new signed run builds and tests again; it never signs an externally supplied artifact.

## Version codes

CI assigns UTC seconds since 2020-01-01 after the serialized workflow starts. Bounds are
checked (1..2,100,000,000); the scheme remains within Android's limit until 2086. Preview
runs share a concurrency group, including time awaiting environment approval, so normal
new runs allocate later codes. PRs use separate groups and never produce signed APKs.
Local unsigned builds default to code 1; provide an explicit code for local experiments.
A signing-job-only retry reuses the same APK/code. To get a new update, start a new whole
workflow run. Preserve this epoch, package name and signer for the lifetime of Preview.
Before installation, verify_update_pair.py checks the actual pair of APKs; never force
a downgrade or uninstall to bypass a failed compatibility check.

## Verification and remaining work

[CI run 35032069860](https://github.com/kkrix3/JapaneseKeyboard/actions/runs/35032069860)
succeeded on Preview commit `211feb89953a5f103b8757c8de744e93d388c5ab` and feature commit
`d0acb1ef3002c59adbe513548b9224b357eb65be`. Both jobs passed custom_keyboard tests and the
selected Full app regression/backup tests. Full Debug and unsigned release-style Full
Preview builds succeeded. Preview package, versionCode 211675206, label, non-debuggable
flag, alignment, unsigned state, ARM64 libraries and bundled Zenz model passed inspection.
Test reports and both APK artifacts are available in that run for 14 days.

The initial run 35031841016 failed workflow validation before allocating jobs. Moving
runner context usage from job env into a runner step resolved it. Local Gradle fetching
was network-blocked; the Android results above are from GitHub-hosted runners.

A post-build commit updates documentation and corrects the signing-only ZIP filename
exclusion regex. Its six forbidden and three allowed filename checks passed locally;
no application or APK-producing build code changed after the successful run. Signing
was skipped by design. User-managed Environment setup, signed A -> B installation,
data retention, actual official Full backup restoration and hardware haptics/full-feature
behavior still require verification. No persistent signing secret was handled by Work.
