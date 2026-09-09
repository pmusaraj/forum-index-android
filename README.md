# Forum Index for Android

Native Android companion for Forum Index, built with Kotlin and Jetpack Compose.

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

The app reads the existing Forum Index public API, keeps feed caches in memory, and stores subject choices, opened topics, and stars locally. Contribution enrollment is optional; its bearer token is encrypted with Android Keystore and excluded from backup. Android contributions are currently shown as device-unverified because server-side Android attestation is not implemented.
