# Android parity checklist

The ongoing goal is feature and behavior parity with [the iOS app](https://github.com/pmusaraj/forum-index-app). See [ios-parity.md](ios-parity.md) for the comparison process, pinned upstream baseline, and intentional platform differences.

## Reader

- [x] Fixed Forum Index header and light beige/turquoise styling
- [x] Main feed plus ordered, customizable subject tabs
- [x] Native horizontal paging with independent vertical list positions
- [x] Pull to refresh, retry states, stale rows, and three bounded pages
- [x] Topic title, forum icon/fallback, source, reply count, and opened state

## Topics

- [x] HTTPS-only in-app WebView
- [x] Share, Star/Unstar, and Close controls
- [x] Local persistent stars and opened-topic state
- [x] Starred-topic list and external opening
- [x] Long-press Star/Unstar and report actions

## September 24 iOS reader changes

- [x] Decode optional forum Markdown capability, defaulting to false
- [x] Request negotiated Markdown without contributor credentials; preserve the final HTTPS URL
- [x] Parse generated topic envelopes, post authors/avatars/dates, and reply navigation
- [x] Native selectable Markdown with links, images, tables, quotes, code, and task lists
- [x] Persistent forum header, preview/full-page toggle, background web loading, and fallback/retry
- [x] Forward reply pagination with explicit retry, cancellation handling, and loop prevention
- [x] Swipe through a stable snapshot of feed topics using composite identity
- [x] Mark only selected topic pages read and return to the selected feed row
- [x] Android system Back/Close navigation and HTTPS forum migration handling
- [x] Android 15 emulator verification: preview/fallback, header toggle, scroll preservation, reply retry, paging, Back, and feed return

## Internal links (iOS 52298db736)

- [x] Same-origin links open another Markdown preview, including individual replies
- [x] Header/system Back restore the prior reader and scroll position
- [x] Full-page, Share, and Star target the displayed linked page
- [x] Linked bookmarks persist by URL without sending source-topic contribution actions
- [x] Parse linked-page titles and remove generated “Showing post” metadata
- [x] Linked loading errors offer Retry and View full page
- [ ] Forum overview popover from the separate iOS d1442e60d5 change

## Contributions

- [x] Optional public enrollment and encrypted bearer token
- [x] Contributor status and editable device name
- [x] Read/star/report action submission while enabled
- [x] Confirmed opt-out preserving local stars
- [x] Android-neutral device-verification copy

## Verification

- [x] JVM API, state, race, and URL-policy tests
- [x] API 33 Compose UI and secure-storage tests
- [x] Live production feed smoke test
- [x] Live topic WebView smoke test
- [x] Reader, WebView, and contribution-sheet screenshots inspected
- [x] Play Store upload-signing configuration and release documentation
- [ ] Signed Play bundle, store assets, privacy disclosures, and Console submission (see play-store-release.md)
- [ ] Play Integrity/backend attestation (deferred until trusted Android devices are required)

## September 24 validation

- `testDebugUnitTest`: 66 tests passed.
- `connectedDebugAndroidTest`: 33 tests passed on the Pixel 9 / Android 15 (API 35) emulator.
- `lintRelease`: 0 errors, 26 existing warnings.
- `assembleRelease`: passed (unsigned APK; production signing remains a release task).
- Live production feed, native Markdown posts, swiping over selectable text, and the full-page header/toggle were visually inspected.
- Still pending: physical-device/TalkBack checks, minimum-API validation of the new reader, and a broader sample of forum-specific rich embeds. See the documented rendering differences in [ios-parity.md](ios-parity.md).

## Internal-link validation

- 71 JVM tests and 35 Android 15 emulator tests passed.
- Rendered-link navigation, exact linked URL/bookmark targeting, parent scroll restoration without refetch, linked-page error/retry, system Back, and mixed old/new bookmark persistence are covered.
- Release APK assembly passed; release lint reports 0 errors and the same 26 existing warnings.
