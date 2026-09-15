# Sumire development fork

Status: feature and Preview branches published; Android compilation and CI validation are in progress.
Do not treat these source changes as a verified daily build yet.

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

## Outstanding verification

Initial connector access returned GitHub 403; the owner restored access and both branches
were published. Local Gradle distribution fetch returned Network is unreachable.
Initial CI run 35031841016 failed workflow validation before allocating any jobs; runner
context use was moved from job env into a runner step. Android verification is pending.
All Android/Robolectric tests, manifest merge, release optimization, full native builds,
actual signed A -> B update and data retention remain unverified. Update this status
with exact commit/run IDs after access is restored and CI completes.
