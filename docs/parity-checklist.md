# Android parity checklist

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
