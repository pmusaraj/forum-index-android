# Google Play release

The [Forum Index Play listing](https://play.google.com/store/apps/details?id=com.musaraj.forumindex) is public. [GitHub v1.0.8](https://github.com/pmusaraj/forum-index-android/releases/tag/v1.0.8) includes a signed APK and AAB. The [release workflow](https://github.com/pmusaraj/forum-index-android/actions/runs/44663009628) uploaded code `10008` to Internal testing. Check Play Console for the version currently served to production and the highest uploaded version code.

## Prepare the update

1. Deploy the website privacy policy changes from the `forum-index-website` checkout. Open `https://theforumindex.com/privacy/` and `https://theforumindex.com/privacy/#delete-data` in a signed-out browser. Verify the deletion section and confirm that the support inbox can receive and resolve deletion requests for people who have opted out, uninstalled, or lost access to their device.
2. Run unit tests, release lint, and device tests. On a dedicated test installation, try deleting contributor data once offline and once online. Confirm an offline failure can be retried, and that successful deletion clears the local profile, history, bookmarks, and draft, with contributions still off after relaunch. Check browsing, Markdown/WebView, bookmarks, reporting, and feedback on current Android and Android 9. Verify startup and reader behavior on a [16 KB emulator or device](https://developer.android.com/guide/practices/page-sizes).
3. Use the [shared tag/version workflow](github-releases.md) to generate signed binaries. The prepared version is `1.0.9` with code `10009`. If that code is already uploaded, pick a higher code and update the release-note filename. CI publishes the signed APK and AAB on GitHub and can upload the same AAB to [Internal testing](play-testing-automation.md).
4. Install the Play-delivered test build and review the Console pre-launch report. In **Setup → App integrity**, compare the Play **app signing** certificate SHA-256 with the [GitHub APK signing certificate](github-releases.md#signing). Play uses the **upload** certificate to authorize the uploaded AAB. Cross-store Android updates depend on the installed app signing identity and version code. See [Play App Signing](https://developer.android.com/studio/publish/app-signing).

## Play Console fields

| Field | Value or action |
| --- | --- |
| Application ID | `com.musaraj.forumindex` |
| Binary | `forum-index-v1.0.9.aab` or the next available release, signed by the existing upload key |
| Minimum and target SDK | Android 9 (API 28) minimum. API 37 target meets the current API 36 submission minimum. [Google Play target requirements](https://support.google.com/googleplay/android-developer/answer/11926878) |
| Title, descriptions, screenshots and icon | [English listing draft and artwork](../store/README.md). Compare with the live listing before replacing images or text. |
| Release notes | [English notes for code 10009](../store/release-notes/en-US/10009.txt) |
| Website and privacy policy | `https://theforumindex.com/` and `https://theforumindex.com/privacy/` |
| Account/data deletion URL | `https://theforumindex.com/privacy/#delete-data` after deploy. [Play deletion requirements](https://support.google.com/googleplay/android-developer/answer/13327111) |
| Support email | The live Play listing shows `info@theforumindex.com`. The website uses `hey@theforumindex.com` for privacy requests. Verify both inboxes or use one monitored address consistently. |
| App content | Recheck Data safety, Ads, App access, Content rating, target audience, and applicable user-generated content requirements. Use the [privacy data inventory](privacy-data-inventory.md) and answer for the live service and content. |

**Reviewer notes.** Reading requires an internet connection and opens without a login. The app automatically creates a contributor profile. In Settings, reviewers can opt out, delete contributor data, regenerate the nickname, or open the privacy policy. Topic actions include reports for low-quality or inappropriate content and the wrong subject. Trusted forum submissions require backend-granted access. If the store requests access to this feature, provide steps and access in App access. Android installations appear as device-unverified.

Use the existing listing in Play Console. Review any tasks under **App content** and **Publishing overview**, then promote the tested bundle to Production or select it from the app bundle library. If production access is still restricted by the [new personal account testing rule](https://support.google.com/googleplay/android-developer/answer/14151465), use a closed test with 12 testers opted in continuously for at least 14 days before applying. Internal and open testing are distinct from the required closed test.

## Build a signed bundle locally

Use the upload key registered for this app. Load credentials from your local secure storage into these environment variables. The release workflow already supplies the equivalent values from GitHub Actions secrets.

| Environment variable | Value |
| --- | --- |
| `FORUM_INDEX_UPLOAD_STORE_FILE` | Absolute path to the existing upload keystore |
| `FORUM_INDEX_UPLOAD_STORE_PASSWORD` | Keystore password |
| `FORUM_INDEX_UPLOAD_KEY_ALIAS` | Upload key alias |
| `FORUM_INDEX_UPLOAD_KEY_PASSWORD` | Private key password |

```sh
./gradlew testDebugUnitTest lintRelease bundleRelease \
  -PreleaseVersionCode=10009 -PreleaseVersionName=1.0.9
jarsigner -verify app/build/outputs/bundle/release/app-release.aab
```

Verify the signature and compare the public certificate with the registered upload certificate. Keep signing credentials and private keys outside the checkout. `bundleRelease` requires upload signing credentials. `assembleRelease` without credentials is suitable for an unsigned build check.
