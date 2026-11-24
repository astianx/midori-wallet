# Build and signing process

The `Midori builds` workflow validates every push and pull request. It can also be started from **Actions → Midori builds → Run workflow**.

| Trigger | Validation artifacts | Signed Android release | Draft GitHub release |
| --- | --- | --- | --- |
| Push to a branch other than `main` | Web, desktop, Android debug, iOS Simulator | Skipped, with a reason in the `plan` summary | Skipped |
| Pull request | Same validation artifacts | Skipped | Skipped |
| Push to `main` | Same validation artifacts | APK and AAB, after Android debug succeeds and environment protection permits it | Skipped |
| Manual run, `android_release` unchecked | Same validation artifacts | Skipped | Skipped |
| Manual run on `main`, `android_release` checked | Same validation artifacts | APK and AAB | Skipped |
| Manual signing request on another branch | Policy error: select `main` | Rejected | Skipped |
| Push a tag `vX.Y.Z` or `vX.Y.Z-beta.N` | Same validation artifacts | APK and AAB | Release-profile desktop (Linux/macOS/Windows) built, Ed25519 update manifest signed, draft release published |

Release tags work from any ref the tag points to (normally a commit already on `main`); `scripts/ci/plan.mjs` treats them independently from the `main`-push policy above.

Signing uses the existing `release-signing` GitHub environment. Its required reviewers and deployment branch restrictions are repository settings; naming an environment in YAML does not configure those rules. Keep its deployment branch restricted to `main` and retain the existing approval rules. Signing is not available to pull requests. Validation never receives the Android keystore.

To request a signed build manually:

```sh
gh workflow run ci.yml --ref main -f android_release=true
```

The workflow and manual input must first be present on the default branch. Re-running an old workflow execution uses its original revision and cannot pick up a workflow correction from a later commit.

## Shared preparation

Both native Android jobs use `.github/actions/setup-android`: JDK 17, Android SDK 36, Build Tools 36.0.0, and Gradle 8.14.2. Every build uses `.github/actions/setup-build`, Node from `.nvmrc`, the committed npm lockfiles, and an empty user npm configuration. Install scripts are disabled; Electron's binary installation is explicit.

Generate and validate dependency lockfiles with the CI toolchain: Node 22.22.2 and its bundled npm 10.9.7. Run `npm ci --ignore-scripts --no-audit --no-fund` in `packages/wallet-core` from an empty `node_modules` directory before committing dependency changes. A development installation can hide missing optional peer dependencies; `npm ci` checks the complete lockfile even when those peers are not used at runtime.

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

macOS builds use `macos-15-intel` to avoid the unavailable ARM runner pool. Desktop packaging sets `MIDORI_DESKTOP_ARCH=arm64` explicitly, preserving the Apple Silicon ZIP and DMG target regardless of the build host. iOS validation produces a simulator bundle on the Intel host. Runner labels: [GitHub reference](https://docs.github.com/en/actions/reference/runners/github-hosted-runners); target architecture: [Electron Forge CLI](https://www.electronforge.io/cli).

macOS produces ZIP and DMG. DMGs use APFS, are verified with `hdiutil verify`, and retry only `Resource busy`, at most three attempts. Invalid partial images are removed. If DMG creation still fails, the job remains failed and its completed ZIP is preserved with an incomplete manifest. A partial artifact set is not a successful release.

Linux produces ZIP and DEB; Windows produces its Squirrel installer and update files. `MIDORI_DESKTOP_FORMAT=zip` supports local ZIP-only Linux builds. iOS Simulator apps remain inside a tarball to preserve executable permissions. None of these jobs deploys the website; only a pushed release tag publishes a (draft) GitHub release, described below.

## Cutting a release

Pushing a tag matching `vX.Y.Z` (stable channel) or `vX.Y.Z-beta.N` (beta channel) runs `desktop-release` (the same desktop job, with `MIDORI_BUILD_PROFILE=release`, so installers are branded "Midori Wallet" instead of "Midori Wallet Beta") and `android-release`, then the `release` job:

```sh
git tag v1.2.3   # or v1.2.3-beta.1 for the beta channel
git push origin v1.2.3
```

The `release` job (`release-signing` environment — same required reviewers as Android signing):

1. Downloads the `android-release` and three `desktop-release` artifacts.
2. Runs `scripts/ci/release.mjs` to pick exactly one installer per platform/arch (APK for `android`, the Squirrel `... Setup.exe` for `win32`, the `.dmg` for `darwin`/`arm64`, the `.deb` for `linux`/`x64`) and the full list of files to upload. It fails if an installer is missing or duplicated.
3. Runs `scripts/sign-update-manifest.mjs` to build and Ed25519-sign `midori-updates-<channel>.json`, using the `UPDATE_SIGNING_PRIVATE_KEY` secret (PKCS8 PEM, `release-signing` environment). The matching public key is the `MIDORI_UPDATE_PUBLIC_KEY` repository variable, read by `packages/update-core/index.js` on the client.
4. Publishes (or updates, with `--clobber`) a **draft** GitHub release named after the tag, uploading every installer plus the signed manifest and its `.sig`.

Drafts are intentional: `createGitHubUpdates` filters `!item.draft`, so clients never see the release — and it never backs `MIDORI_UPDATE_PUBLIC_KEY` verification — until someone reviews the draft on GitHub and publishes it. Re-pushing the same tag re-runs the job and overwrites the release assets (`gh release upload --clobber`) but does not move an already-published (non-draft) release back to draft.

Run `npm run test:build` and `actionlint .github/workflows/ci.yml` when changing this process. Native toolchains still require validation on their corresponding operating systems.
