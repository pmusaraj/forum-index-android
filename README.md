# Forum Index for Android

Native Android companion for Forum Index, built with Kotlin and Jetpack Compose.

## Download

Download the signed APK from [GitHub Releases](https://github.com/pmusaraj/forum-index-android/releases/latest). Requires Android 9 or newer. Allow your browser or file manager to install apps when Android prompts you. Future releases can update an existing GitHub installation without losing local data.

See [the APK release guide](docs/github-releases.md) for publishing, signing, and checksum verification.

The Android app aims for feature and behavior parity with the [iOS application](https://github.com/pmusaraj/forum-index-app). See [the parity goal and comparison process](docs/ios-parity.md) and [implementation checklist](docs/parity-checklist.md).

## Requirements

- Android Studio Quail 4 or newer
- JDK 17 or newer
- Android SDK 37
- Android 9 (API 28) or newer device/emulator

## Build and test

```sh
./gradlew testDebugUnitTest
./gradlew connectedDebugAndroidTest
./gradlew lintRelease assembleDebug assembleRelease
```

Install the debug build on a connected device:

```sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Behavior

The app reads the existing Forum Index public API, keeps feed caches in memory, and stores subject choices, opened topics, and bookmarks locally. Topics from supported forums open in a native Markdown reader with paginated replies and a full-page toggle. Same-site links open another preview; Back restores the previous page and scroll position, while Share and Bookmark follow the displayed page. Swipe between topics, then use Back or Close to return to the selected feed row. Scroll upward to reveal the topic title. Feed actions include About with an excerpt and community information. Hacker News and Lobsters discussion signals appear in feed rows with browser links in supported topic previews. Settings offer Auto, Light, and Dark appearance. Tap the forum header for site information. Devices automatically enroll with a generated contributor name; opting out in Settings persists across launches and keeps local bookmarks. The contribution bearer token is encrypted with Android Keystore and excluded from backup. Android contributions are currently shown as device-unverified because server-side Android attestation is not implemented.

The public Trending feed is separate from For You, which appears when the server marks your contributor profile eligible. Bookmarks open inside the app, and the newest 20 saves are checked for new replies while the app is open. Supported previews offer collapsible AI summaries, author bylines, and an end-of-topic footer. Settings include feedback with a saved draft and retry support.

## Typography

The interface and native Markdown reader bundle Source Sans 3 from [Adobe's font repository](https://github.com/adobe-fonts/source-sans/tree/87b37a2daaed80fcb8e8ccb0085c4d72ddade12e), including regular, medium, semibold, bold, and their italics. The wordmark retains its original typeface. The SIL Open Font License is included in `app/src/main/assets/licenses/source-sans-3.txt`.

## Google Play release

See [the release guide](docs/play-store-release.md) for upload signing, versioning, bundle builds, store materials, testing, and remaining Console requirements. Store listing drafts are in `store/`, alongside the [icon, feature graphic, and Android screenshot gallery](store/index.html). The [asset guide](store/README.md) explains uploads and regeneration; the [data inventory](docs/privacy-data-inventory.md) supports privacy-policy and Data safety preparation.
