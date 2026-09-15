# User-managed Preview signing

No signing key has been generated, accessed or stored by Work. The following is setup
for the repository owner on their own trusted computer and GitHub account.

1. Create a long-lived Android signing keystore locally using Android Studio's signing
   tools or keytool. Keep a password-manager record of keystore password, key alias and
   key password. Back up the original keystore securely outside this repository and
   outside ChatGPT. Losing it prevents updates to the existing Preview installation.
2. In repository Settings -> Environments, create `preview-signing` before enabling
   signing. Allow only the `preview` branch. Add `kkrix3` as a required reviewer and
   disable administrator bypass where available. For a single-owner workflow, do not
   enable Prevent self-review: the same owner must be able to approve their manual run.
3. Add these four **Environment secrets**, not repository-wide secrets:

| Secret | User supplies |
| --- | --- |
| PREVIEW_KEYSTORE_BASE64 | Base64 of the keystore, transferred locally to GitHub without logging or a normal temporary file |
| PREVIEW_KEYSTORE_PASSWORD | Keystore password |
| PREVIEW_KEY_ALIAS | Alias of the long-lived signing key |
| PREVIEW_KEY_PASSWORD | Private-key password |

4. Set the non-secret **repository Actions variable** `PREVIEW_SIGNING_ENABLED` to `true`
   only after environment protections and all secrets have been set.
5. Publish Preview and set it as the fork default branch (dev stays unchanged); enable
   Actions if GitHub requires initial fork activation. Run `Sumire Full Preview CI`
   manually on `preview` as `kkrix3`. Review the commit and successful tests, then approve
   the environment gate. Download `sumire-preview-full-signed` after signature verification.

Do not paste keys/passwords/Base64 into ChatGPT, tickets, logs or repository files. Work
must not receive the keystore backup. Record the public certificate SHA-256 printed by
apksigner for future identity checks; this is public certificate metadata, not a key.

Without the opt-in variable, only unsigned/debug CI runs. If the signing job is enabled
but any of the four secrets is missing, it skips signing and keeps the unsigned artifact;
ordinary tests are not dependent on signing secrets. Do not replace a missing key with a
newly generated one after Preview has been installed.

The secret step runs only on owner-started workflow_dispatch of this repository's preview
branch, after both verification jobs succeed and the environment gate is approved.
PR, pull_request_target, foreign branch and foreign artifact signing are unsupported.
The sign job has no source checkout, Gradle build or cache. It downloads only this run's
named artifact, verifies SHA/commit/package/version and signs using an ephemeral directory
under RUNNER_TEMP. A trap removes it on normal/error exit; GitHub-hosted runners are
short-lived. Upload targets are exact APK paths, with v4 sidecar generation disabled.
All password arguments use apksigner's env: syntax, not inline command-line passwords.

Environment protection is a GitHub setting; writing `environment: preview-signing` into
YAML does not itself configure reviewers. See:
https://docs.github.com/en/actions/reference/workflows-and-actions/deployments-and-environments
https://developer.android.com/tools/apksigner

OS side-loading/Play Protect checks may still appear. This configuration addresses stable
application identity and signing for updates; it does not bypass Android security checks.
