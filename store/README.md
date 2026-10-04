# Google Play listing assets

Open [the review gallery](index.html) to inspect the artwork and all screenshot sets. These assets are prepared locally; they have not been uploaded to Play Console.

| Play Console field | File or folder | Dimensions |
| --- | --- | --- |
| App icon | `graphics/icon.png` | 512 × 512 |
| Feature graphic | `graphics/feature-graphic.png` | 1024 × 500 |
| Phone screenshots | `screenshots/phone/light/` or `dark/` | 1080 × 1920 |
| Tablet screenshots | `screenshots/tablet/light/` or `dark/` | 1600 × 2560 |

Upload the five numbered screenshots in order: discover, reader, community, starred, subjects. Choose one appearance per device category, or mix up to eight images. The tablet set was captured on a Pixel Tablet emulator and is intended for the large-tablet listing field. No separate 7-inch-device capture is included.

The icon preserves the original edge-cropped typographic design, without adding rounded corners or a shadow. The feature graphic uses the original wordmark, cream background, and teal accent. Standalone transparent wordmarks are in `graphics/logo-light.png` and `graphics/logo-dark.png`; editable originals are in `graphics/source/`.

Screenshot and feature-graphic upload files are opaque RGB PNGs. `asset-manifest.json` records checked dimensions, file sizes, and SHA-256 hashes. Specifications: [Google Play preview assets](https://support.google.com/googleplay/android-developer/answer/9866151).

## Original artwork and screenshot content

Brand files were copied from the adjacent `forum-index-app` checkout (`71883af`):

- `Sources/ForumIndexApp/Assets.xcassets/AppIcon.appiconset/AppIcon-1024.png`
- `Sources/ForumIndexApp/Assets.xcassets/ForumIndexFullLogo.imageset/` light and dark SVGs
- `Tests/ForumIndexAppUITests/Fixtures/AppStore/` public feed, taxonomy, WaniKani discussion, and forum metadata

Android screenshots were captured on October 3, 2026 using production Android composables on Android 15 emulators. Screens and content correspond to the iOS store sequence; the images are Android captures. The fixture content is unchanged. Forum icons and author avatars load from their original public URLs; the app displays its normal letter fallback when an image is unavailable. Relative dates reflect capture time.

The opt-in `StoreScreenshotTest` renders the app screens with fixture state, selects a representative subject order, and seeds six saved topics. It does not create a contributor or submit activity. Test code and fixtures live exclusively under `app/src/androidTest/` and are excluded from release packages. Screenshots receive no retouching, framing, or promotional overlays; export only removes alpha and metadata.

## Regenerate

Requires the Android SDK, JDK 17, Python 3, Pillow, and ImageMagick. Artwork rendering also requires Chrome and the original Avenir Next fonts on macOS.

```sh
export ANDROID_HOME="$HOME/Library/Android/sdk"
export JAVA_HOME=$(/usr/libexec/java_home)
./gradlew assembleDebug assembleDebugAndroidTest
# Boot Pixel_9_API_35 and Pixel_Tablet_API_35 (or equivalent dedicated emulators).
adb devices
python3 scripts/capture-store-screenshots.py phone emulator-5554
python3 scripts/capture-store-screenshots.py tablet emulator-5556
python3 scripts/export-store-graphics.py
python3 scripts/validate-store-assets.py
```

The capture scripts install debug/test APKs, set the emulator display size, disable heads-up notification banners, and enable a 9:41 demo status bar. Use dedicated test emulators, wait for initial system/WebView updates to finish, and set the tablet to portrait orientation before capturing. To refresh only one screen, append e.g. `--only 05-subjects` after a complete capture. Review images after every run; remote image availability can vary.

Restore a test emulator afterward with `adb -s SERIAL shell wm size reset`, `adb -s SERIAL shell settings put global heads_up_notifications_enabled 1`, and `adb -s SERIAL shell am broadcast -a com.android.systemui.demo -e command exit`.

English listing text remains in `listings/en-US/`, and release notes are in `release-notes/en-US/`.
