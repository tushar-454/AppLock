# Android releases

AppLock releases are standalone, signed APKs published on GitHub Releases. The workflow builds the existing Expo prebuild/Gradle Android app, including the native `app-lock` module; it does not use Expo Go, EAS, or Google Play credentials.

## Required GitHub secrets

Configure these repository secrets before enabling releases:

- `APPLOCK_KEYSTORE_BASE64`: Base64 encoding of the existing `.tooling/applock-personal.keystore` file. Encode the keystore bytes without changing or regenerating the key.
- `APPLOCK_STORE_PASSWORD`: The existing keystore password from `.tooling/signing-password`.

From the repository root, GitHub CLI can set both secrets without printing their contents:

```bash
base64 .tooling/applock-personal.keystore | gh secret set APPLOCK_KEYSTORE_BASE64 -R tushar-454/AppLock
gh secret set APPLOCK_STORE_PASSWORD -R tushar-454/AppLock < .tooling/signing-password
```

The keystore must contain alias `applock`. CI decodes it to a mode-600 file under the ephemeral runner temp directory, checks that the alias opens, passes it to the existing Gradle signing configuration, then deletes the temporary file. The APK signer certificate must match the pinned SHA-256 fingerprint `7adcb0de660b8b286f416e81b1fc4945600c5085b0c90181c3152fb0e4a09cda`.

Never put the keystore or password in the repository, an artifact, a workflow variable, or a log. `.tooling/` and both named signing files are ignored by Git. Keep encrypted, access-controlled backups of the keystore and password separately. Losing this exact key prevents users from installing updates over this AppLock installation.

## Automatic releases

A push to `main` starts a release when application or build files change. Documentation-only changes are ignored. Pushes of generated release tags do not match the `main` branch trigger. Releases are serialized so two builds cannot select a version code at the same time.

The workflow uses the `package-lock.json` via `npm ci`, Node.js 22.13.1 (the Expo SDK 58 minimum is 22.13.x), Temurin Java 17, Android platform/build tools 37, NDK 27.1.12297006, and the repository's Gradle wrapper (Gradle 9.4.1). It runs lint, TypeScript checks, native configuration checks, engine tests, and native Gradle tests, performs Expo prebuild, then assembles a standalone release APK.

The workflow validates the signing secrets before building and fails closed if the CI signing guard sees missing signing values. Before publication, it verifies the APK signature, exact signer fingerprint, package `com.tushar.applock`, calculated version name and version code. It uploads `applock-vX.Y.Z.apk` and a SHA-256 checksum file to a GitHub Release. The release notes contain changes since the previous version tag, package and version details, the certificate fingerprint, APK checksum, and update instructions.

## Version assignment

The installed baseline is version `1.0.0`, code `2`. The first release is `1.0.1`, code `3`. Each subsequent release increments the patch version after the highest stable `vMAJOR.MINOR.PATCH` tag. A higher minor/major tag becomes the new patch-version base.

Android version codes start at baseline code 2 and advance by one for every stable `vX.Y.Z` release tag, plus one for the release being built. Tags are the persistent release history; failed builds create no tag, while a partially completed/published release still consumes its tag/version code and is never overwritten. Do not delete release tags. The calculation has unit tests and refuses a tag collision. This keeps the code increasing even when version names skip or jump.

Both Expo's resolved app configuration and the generated Gradle project receive `APPLOCK_VERSION_NAME` and `APPLOCK_VERSION_CODE` from the same calculation. The committed `app.json` remains the local baseline.

## Triggering a manual release

Open **Actions → Android Release → Run workflow**, select `main`, and start it. The workflow refuses to build when dispatched from another branch. A successful run publishes the Release automatically; do not rerun a completed release to replace its APK.

## Downloading and updating

Open the repository's **Releases** page, select the newest AppLock version, and download its APK asset. Open the APK on the Android device and accept Android's install/update prompt. For a normal update, the package must remain `com.tushar.applock`, the signer must remain the pinned certificate, and the new version code must be higher. Those conditions let Android preserve application data, including AppLock settings and PIN data. Do not uninstall the existing app or clear its data before updating.

## Verifying a download

Download the `.apk.sha256` file alongside the APK and compare it locally:

```bash
sha256sum --check applock-v1.0.1.apk.sha256
```

With Android SDK Build Tools installed, inspect the embedded certificate:

```bash
apksigner verify --verbose --print-certs applock-v1.0.1.apk
```

The output must contain exactly one signer with certificate SHA-256 `7adcb0de660b8b286f416e81b1fc4945600c5085b0c90181c3152fb0e4a09cda`. CI also checks the package and release version before the APK can be published.

## Recovering from a failed run

- **Missing/invalid secret:** check that both repository secrets are populated, the base64 value decodes to the original keystore, and password/alias `applock` are correct. Never print or paste secret values into logs or issues.
- **Build/test failure:** read the failing Gradle or validation step and fix the source on `main`; no release is made before the publishing job.
- **Tag or release already exists:** the workflow refuses to overwrite it. Inspect the release and its uploaded checksum. A completed version is immutable; fix the cause and let the next release calculation select a newer version.
- **Failure during publication:** inspect GitHub Releases and tags first. If the intended tag or release exists, treat that version as consumed; do not delete/reuse it. Resolve any partial release manually, then publish a later version.

## Backing up signing credentials securely

Keep at least one encrypted offline backup of the original keystore and its password, stored separately with access limited to release maintainers. Verify that the backup can open alias `applock` and has the pinned certificate fingerprint. Do not regenerate a key as a recovery measure: a replacement would make Android reject updates for installed users.
