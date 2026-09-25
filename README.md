# Forum Index for Android

Native Android companion for Forum Index, built with Kotlin and Jetpack Compose.

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

The app reads the existing Forum Index public API, keeps feed caches in memory, and stores subject choices, opened topics, and stars locally. Topics from supported forums open in a native Markdown reader with paginated replies and a full-page toggle. Same-site links open another preview; Back restores the previous page and scroll position, while Share and Star follow the displayed page. Swipe between topics, then use Back or Close to return to the selected feed row. Settings offer Auto, Light, and Dark appearance. Tap the forum header for site information. Devices automatically enroll with a generated contributor name; opting out in Settings persists across launches and keeps local stars. The contribution bearer token is encrypted with Android Keystore and excluded from backup. Android contributions are currently shown as device-unverified because server-side Android attestation is not implemented.

## Google Play release

See [the release guide](docs/play-store-release.md) for upload signing, versioning, bundle builds, store materials, testing, and remaining Console requirements. Store listing drafts are in `store/`; the [data inventory](docs/privacy-data-inventory.md) supports privacy-policy and Data safety preparation.
