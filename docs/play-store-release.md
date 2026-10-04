# Google Play release

## Current status

Package: `com.musaraj.forumindex`, confirmed for the prepared Play Console app on October 3, 2026. Minimum Android version: 9 (API 28); compile/target SDK: 37. Check the latest [target API requirements](https://developer.android.com/google/play/requirements/target-sdk) before submission.

The first testing upload was completed in Play Console (confirmed October 4, 2026). The prepared first bundle is version `1.0.0`, code `10000`. Use a new version code for subsequent uploads and follow the [shared version numbering scheme](github-releases.md); local Gradle defaults (`1.0`, code `1`) are not the published release version. See [testing automation](play-testing-automation.md) for GitHub authentication, automatic internal releases, and manual retries.

## Upload signing

For the first Play upload, no build or upload certificate has been registered yet (confirmed October 3, 2026). The first bundle uses the existing GitHub release key, whose public certificate fingerprint is recorded in [the GitHub release guide](github-releases.md).

Before completing Play App Signing enrollment, choose the app signing identity deliberately: import the existing GitHub signing key using Play Console's PEPK instructions if Play builds should update GitHub installations. Letting Google generate a different app signing key prevents updates between those distributions, even when the bundle uses the GitHub key as its upload key. A public upload certificate alone does not transfer the app signing key. Keep the keystore, passwords, and encrypted private-key export out of Git and GitHub Release assets.

Use an existing upload key if this package is already registered. For a new app, create a dedicated upload key outside the repository. Let `keytool` prompt for passwords:

```sh
keytool -genkeypair -v -keystore /absolute/private/path/forum-index-upload.jks \
  -alias forum-index-upload -keyalg RSA -keysize 2048 -validity 10000
```

Back up the key and passwords securely. Configure these variables locally or as CI secrets (do not paste passwords into committed files or command history):

| Environment variable | Value |
| --- | --- |
| `FORUM_INDEX_UPLOAD_STORE_FILE` | Absolute path to the upload keystore |
| `FORUM_INDEX_UPLOAD_STORE_PASSWORD` | Keystore password |
| `FORUM_INDEX_UPLOAD_KEY_ALIAS` | Upload key alias |
| `FORUM_INDEX_UPLOAD_KEY_PASSWORD` | Private key password |

All four variables are required for `bundleRelease`. Debug builds and unsigned release APK validation work without them. Release builds never fall back to debug signing. Keep signing builds off shared machines with untrusted users; do not publish Gradle diagnostic dumps containing credentials.

```sh
./gradlew checkPlaySigning
./gradlew testDebugUnitTest lintRelease bundleRelease \
  -PreleaseVersionCode=10000 -PreleaseVersionName=1.0.0
jarsigner -verify app/build/outputs/bundle/release/app-release.aab
```

Upload `app/build/outputs/bundle/release/app-release.aab`. Increment the version code for every subsequent upload, including testing tracks. Check that `jarsigner` reports a verified signature, and compare the signing certificate with your expected upload certificate. A self-signed upload certificate is normal. Enroll in [Play App Signing](https://developer.android.com/studio/publish/app-signing); Google manages the app signing key separately from your upload key.

## Store materials

Draft English copy is in `store/listings/en-US/`; first-release notes are in `store/release-notes/en-US/1.txt`. These are plain text for copying into Console, not a configured automatic publishing integration.

- Supply a support email, public privacy-policy URL, and optional website.
- Store icon, feature graphic, and five Android screenshots per appearance for phone and tablet are prepared in `store/`. See the [asset gallery](../store/index.html) and [upload instructions](../store/README.md).
- Select the category, audience, countries, and pricing, and complete the content-rating questionnaire based on the forum content users can encounter. Do not assume the app is suitable for children.

See Google's [preview asset specifications](https://support.google.com/googleplay/android-developer/answer/9866151).

## Privacy and review blockers

- Publish a complete privacy policy naming Forum Index and its operator, with a contact address, collection/use/sharing practices, retention periods, and deletion process. Add an accessible link inside the app before submission. No policy URL is currently configured.
- Use [privacy-data-inventory.md](privacy-data-inventory.md) to complete Data safety after confirming backend logging, storage, retention, and third-party forum behavior. Do not declare “no data collected” based solely on the absence of an analytics SDK.
- Confirm whether contributor enrollment falls under Google's account-creation rules. The app sends a DELETE request when opting out, but backend deletion semantics have not been verified here. If applicable, provide clear in-app account/data deletion and a functional external deletion-request page. “Opt out” alone must not be assumed to satisfy the policy.
- Review user-generated-content obligations for indexed forums, moderation/report handling, and any account interaction available through the embedded forum pages.
- Complete Ads, App access, Target audience, Content rating, Data safety, and other applicable Console declarations. No advertising SDK appears in the direct app dependencies; verify third-party content before answering the Ads declaration.
- Reviewer instructions: new devices enroll automatically using a generated contributor name and detected device name. Tap Settings to opt out, re-enable contributions, rename the device, or choose Auto/Light/Dark appearance. Basic reading remains available after opting out. Trusted forum-submission tools require backend-granted status; arrange reviewer access/instructions if included in review scope. Android attestation is not implemented, so installs show as device-unverified.

References: [User data policy](https://support.google.com/googleplay/android-developer/answer/10144311), [account deletion](https://support.google.com/googleplay/android-developer/answer/13327111).

## Test and submit

1. Run unit tests, release lint, and the signed bundle build above. Run `./gradlew connectedDebugAndroidTest` on an emulator or device. Inspect `app/build/reports/` for failures.
2. Upload the signed AAB to internal testing. Install the Play-delivered build to validate signing and bundle delivery; inspect the pre-launch report and resolve crashes/accessibility issues.
3. Smoke-test Android 9 and a current Android device: loading and offline retry, paging, subject selection, saved stars after restart, WebView navigation, sharing, enrollment, read/star/report submissions, and opt-out. Include a 16 KB page-size device/emulator: Compose brings a native graphics library into the bundle. Confirm opting out stops future submissions and verify server-side deletion/retention behavior.
4. Complete account verification and Play Console setup. For personal accounts created after November 13, 2023, check the [closed-testing requirement](https://support.google.com/googleplay/android-developer/answer/14151465): at least 12 testers continuously opted in for 14 days before applying for production access. Internal testing does not substitute for this.
5. Finish listing assets and declarations, resolve Console warnings, then submit for review. Recheck current requirements at submission time.

## Local preparation validation (2026-09-21)

- All 47 JVM tests passed; `assembleRelease` succeeded.
- `lintRelease` completed with 0 errors and 26 warnings (dependency updates, launcher icon shape, and code-style/catalog suggestions).
- Missing upload credentials correctly fail `checkPlaySigning`.
- `bundleRelease` succeeded with a disposable test key and its JAR signature verified. The disposable test bundle is not a production upload artifact. Build again with the real upload key before submission.
- Store text fits the title, short-description, and full-description character limits.
- Device/UI tests were not run: no device or emulator was connected. Play-delivered and 16 KB device checks remain pending.
