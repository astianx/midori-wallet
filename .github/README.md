# Build and signing process

The `Midori builds` workflow validates every push and pull request. It can also be started from **Actions → Midori builds → Run workflow**.

| Trigger | Validation artifacts | Signed Android release |
| --- | --- | --- |
| Push to a branch other than `main` | Web, desktop, Android debug, iOS Simulator | Skipped, with a reason in the `plan` summary |
| Pull request | Same validation artifacts | Skipped |
| Push to `main` | Same validation artifacts | APK and AAB, after Android debug succeeds and environment protection permits it |
| Manual run, `android_release` unchecked | Same validation artifacts | Skipped |
| Manual run on `main`, `android_release` checked | Same validation artifacts | APK and AAB |
| Manual signing request on another branch | Policy error: select `main` | Rejected |

Signing uses the existing `release-signing` GitHub environment. Its required reviewers and deployment branch restrictions are repository settings; naming an environment in YAML does not configure those rules. Keep its deployment branch restricted to `main` and retain the existing approval rules. Signing is not available to pull requests. Validation never receives the Android keystore.

To request a signed build manually:

```sh
gh workflow run ci.yml --ref main -f android_release=true
```

The workflow and manual input must first be present on the default branch. Re-running an old workflow execution uses its original revision and cannot pick up a workflow correction from a later commit.

## Shared preparation

Both native Android jobs use `.github/actions/setup-android`: JDK 17, Android SDK 36, Build Tools 36.0.0, and Gradle 8.14.2. Every build uses `.github/actions/setup-build`, Node from `.nvmrc`, the committed npm lockfiles, and an empty user npm configuration. Install scripts are disabled; Electron's binary installation is explicit.

New pushes cancel obsolete validation runs on feature branches. A running workflow on `main` is not automatically cancelled by a newer push, so signing and cleanup can finish.

## Signing checks

Configure these secrets for the signing job:

- `ANDROID_KEYSTORE_BASE64`: the existing PKCS12 keystore, encoded as base64.
- `ANDROID_KEYSTORE_PASSWORD`: its password.
- `ANDROID_KEY_ALIAS`: the existing release key alias.
- `ANDROID_KEY_PASSWORD`: that key's password.

The preflight validates the secrets and alias before compilation. The build requires signing configuration instead of silently producing an unsigned APK. APK verification uses `apksigner`; AAB verification uses `jarsigner -verify -strict` against the same keystore and alias. The APK certificate must match the configured keystore. Temporary keystore and Cordova signing files are removed even after a failed step.

## Artifacts and diagnosis

Each uploaded artifact contains `manifest.json`: target, profile, version, commit, workflow run and attempt, completeness, file sizes and SHA-256 hashes. The Android release manifest also records the verified signing certificate. APK/AAB hashes must still match their signature-verification result when collected.

macOS produces ZIP and DMG. DMGs use APFS, are verified with `hdiutil verify`, and retry only `Resource busy`, at most three attempts. Invalid partial images are removed. If DMG creation still fails, the job remains failed and its completed ZIP is preserved with an incomplete manifest. A partial artifact set is not a successful release.

Linux produces ZIP and DEB; Windows produces its Squirrel installer and update files. `MIDORI_DESKTOP_FORMAT=zip` supports local ZIP-only Linux builds. iOS Simulator apps remain inside a tarball to preserve executable permissions. None of these jobs publishes a GitHub release or deploys the website.

Run `npm run test:build` and `actionlint .github/workflows/ci.yml` when changing this process. Native toolchains still require validation on their corresponding operating systems.
