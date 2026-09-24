# Android / iOS parity

## Goal

The goal of Forum Index for Android is feature and behavior parity with the iOS application, [pmusaraj/forum-index-app](https://github.com/pmusaraj/forum-index-app). The iOS app is the product reference: Android should offer the same reader capabilities, content, feed behavior, contribution controls, and privacy choices.

Parity means equivalent user outcomes, not identical platform code or pixel-for-pixel UI. Use native Android conventions for Back navigation, accessibility, sharing, lifecycle, and rendering. An iOS implementation detail or unfinished experiment is not automatically an Android requirement; record intentional differences and unresolved questions rather than silently diverging.

## Keeping parity current

For feature work and before releases:

1. Review iOS commits since the last recorded comparison, including API models and tests, not just screenshots or commit titles. Pin the reviewed commit.
2. Compare actual behavior with Android. Track additions, regressions, and intentional platform differences in [parity-checklist.md](parity-checklist.md).
3. Implement the relevant behavior with Android lifecycle/cancellation handling and tests. Preserve API contracts, composite forum/topic identity, contribution opt-in, local stars, and existing navigation state.
4. Validate equivalent scenarios on a device/emulator and record what was tested and what remains unverified. Shared fixtures should exercise the same contract; platform-specific UI tests need not use identical gestures.
5. Update this document's comparison baseline and checklist. Do not describe an untested or partial implementation as complete parity.

## Comparison baseline: September 24, 2026

Reviewed iOS [905ff7d5ce](https://github.com/pmusaraj/forum-index-app/commit/905ff7d5ce), “WIP: add native Markdown reader and topic paging”, against Android's existing behavior through iOS's September 16 contribution-copy changes. The upstream commit is labeled WIP; this record describes the observed contract, not a claim that it is a finished iOS release.

| Recent iOS behavior | Android implementation |
| --- | --- |
| Forum-level `supports_markdown`, default false | Decode capability; existing forums remain on the full webpage |
| Negotiate `text/markdown` at the topic URL | Separate credential-free forum client with bounded response size, timeouts, and HTTPS redirects |
| Split Discourse-generated envelopes into posts | Native post model preserves body Markdown, author, avatar, timestamp, and final footer navigation |
| Persistent forum header and preview/full-page toggle | Header remains outside the scrolling content; loading/error fallback keeps full-page access available |
| Paginated replies | Append forward pages of the same topic; preserve posts on failure, require explicit retry, stop on empty/backward/repeated pages |
| Swipe between topics | Stable feed snapshot, invalid URLs/duplicates filtered by forum/topic identity; read actions only for selected pages |
| Return to the last paged topic in the feed | Scroll to and highlight the selected row; preserve the original feed offset when no paging occurred |
| Native edge Back | Android system Back and Close return to the feed; the pager does not claim system gesture edges |
| Migrated forum domains | Automatic HTTPS navigation stays in the embedded page; explicitly tapped external links open outside the app |

## Platform differences and follow-up

- iOS uses Textual/SwiftUI for Markdown. Android uses [Markwon](https://noties.io/Markwon/docs/v4/) with native Android text inside Compose; typography and selection behavior should follow Android conventions.
- Preview images support Android bitmap formats; SVG/video/interactive embeds are available through the full-page toggle. Syntax-colored code and custom Discourse HTML embeds are not rendered natively.
- Android retains the current topic session in its ViewModel across configuration changes. Preview/page loading belongs to the visible/adjacent page composition and is cancelled when disposed. Detail restoration after process death is not implemented.
- iOS centers the returned row; Android positions it at the top of the feed and highlights it. Both return to the last selected topic.
- Android system Back gestures replace UIKit's leading-edge pop gesture; do not add an app gesture that conflicts with system navigation.
- Do not equate contributor installations with anonymous data. Keep the clarified Android disclosure even where upstream copy still says “anonymous”.
- Android attestation remains deferred. Device-unverified status is an acknowledged platform difference, not a trusted-device claim.
- App Store archives/export files do not apply to Android. Track Google Play requirements in [play-store-release.md](play-store-release.md).

Validation for this comparison: 66 JVM tests and 33 Android 15 emulator tests passed; release APK assembly succeeded and release lint reported 0 errors. Live feed, native preview, swipe, and full-page layouts were visually checked. See the checklist for remaining rendering and device-test gaps.

## Internal-link follow-up

Matched iOS [52298db736](https://github.com/pmusaraj/forum-index-app/commit/52298db736), including the refinement after [a65a4baa30](https://github.com/pmusaraj/forum-index-app/commit/a65a4baa30):

- Same-origin HTTPS links open a new native Markdown preview in the current topic screen. Relative links resolve against the fetched page URL, including migrated domains. Post suffixes, queries, and fragments remain in the requested URL.
- Header Back and Android system Back return through the preview history before leaving the topic. Readers and saved list positions are retained; returning does not refetch a loaded page. Close still returns directly to the feed.
- The full-page toggle, share URL/title, and star action follow the currently displayed page. Linked-page titles come from the generated Markdown envelope; source-topic reply counts and dates are not reused for linked content.
- A linked-page failure stays in the preview with Retry and View full page actions. The initial feed topic retains its automatic full-page fallback.
- Bookmarks for linked URLs have no Forum Index topic ID. They persist locally and do not send read/star contribution actions for the source topic or guess an API ID from a forum URL. Existing ID-based bookmarks remain compatible.
- Other origins (including subdomains or different ports) still open externally. HTTP and unsupported URL schemes remain rejected.

The separate forum-overview popover introduced in iOS `d1442e60d5` is not included in this internal-link change and remains a tracked parity gap. Linked preview history currently belongs to a retained topic page; activity recreation or paging far enough to dispose that page resets the history.
