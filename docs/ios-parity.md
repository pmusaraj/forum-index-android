# Android / iOS parity

## Goal

The goal of Forum Index for Android is feature and behavior parity with the iOS application, [pmusaraj/forum-index-app](https://github.com/pmusaraj/forum-index-app). The iOS app is the product reference: Android should offer the same reader capabilities, content, feed behavior, contribution controls, and privacy choices.

Parity means equivalent user outcomes, not identical platform code or pixel-for-pixel UI. Use native Android conventions for Back navigation, accessibility, sharing, lifecycle, and rendering. An iOS implementation detail or unfinished experiment is not automatically an Android requirement; record intentional differences and unresolved questions rather than silently diverging.

## Keeping parity current

For feature work and before releases:

1. Review iOS commits since the last recorded comparison, including API models and tests, not just screenshots or commit titles. Pin the reviewed commit.
2. Compare actual behavior with Android. Track additions, regressions, and intentional platform differences in [parity-checklist.md](parity-checklist.md).
3. Implement the relevant behavior with Android lifecycle/cancellation handling and tests. Preserve API contracts, composite forum/topic identity, persistent contribution opt-out, local bookmarks, and existing navigation state.
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
- The full-page toggle, share URL/title, and bookmark action follow the currently displayed page. Linked-page titles come from the generated Markdown envelope; source-topic reply counts and dates are not reused for linked content.
- A linked-page failure stays in the preview with Retry and View full page actions. The initial feed topic retains its automatic full-page fallback.
- Bookmarks for linked URLs have no Forum Index topic ID. They persist locally and do not send read/bookmark contribution actions for the source topic or guess an API ID from a forum URL. Existing ID-based bookmarks remain compatible.
- Other origins (including subdomains or different ports) still open externally. HTTP and unsupported URL schemes remain rejected.

The forum-overview popover introduced in iOS `d1442e60d5` was subsequently ported in the evening comparison below. Linked preview history currently belongs to a retained topic page; activity recreation or paging far enough to dispose that page resets the history.

## September 24 evening comparison

Reviewed iOS through [800c5bd](https://github.com/pmusaraj/forum-index-app/commit/800c5bd), including `deea313`, `a24986f`, `846abe7`, `d8dd68b`, and `cbec412`.

- Settings now offer persisted Auto/Light/Dark appearance, with matching reader, sheets, Markdown, controls, and native window colors. Auto follows Android system appearance; native content is independent of website header contrast.
- Feed rows use leading 36 dp forum icons, compact reply counts with a speech bubble, consistent spacing, separators, and matching loading placeholders.
- The forum name in the Markdown header opens site information, with a public forum-detail request, description, browser links, loading/error/retry, and HTTPS validation.
- Full web topics sample the visible site's header background and adjust the top inset and status-bar contrast. Web content extends to the bottom edge; floating actions remain above navigation controls. Sampling is cancelled for hidden/disposed pages and never exposes a JavaScript-to-native bridge. Android samples while visible rather than installing the iOS mutation observer.
- Contributions enroll automatically with a persistent generated Reader name and detected/editable device name. Explicit opt-out persists across launches, preserves local bookmarks, and can be reversed in Settings. Existing credentials are reused. Enrollment failure retains access to the public feed.
- Settings use the updated contribution disclosure, larger action buttons, and “Recommend a forum” section. Android verification behavior is unchanged, as requested.
- Markdown uses theme-aware code backgrounds, smaller code text, subtle rules, `@username` labels, and legacy post headings inside recognized generated envelopes. Ordinary Markdown and fenced examples remain intact.

App Store bundle identifiers, build numbers, TestFlight releases, screenshot tooling, and iOS verification are platform-specific and excluded. The earlier rendering and physical-device limitations still apply.

Validation: 76 JVM tests and 42 Android 15 emulator tests passed; debug/release APK assembly and release lint passed. See the evening validation in [parity-checklist.md](parity-checklist.md).

## September 25 subject defaults

Matched iOS [8abf4cd](https://github.com/pmusaraj/forum-index-app/commit/8abf4cd). Default tabs now follow Main, AI, Tech, Creative, Markets, Open Source, Jobs, Gaming, Audio, Community, Web Development, Sport, Lifestyle, and Science, when available in the eligible taxonomy. Saved subject selections and ordering remain authoritative. Technology and Web Development labels are selected by slug, matching iOS.

Also reviewed the adjacent working-tree changes: the iPad subject-picker popover sizing fix has no direct Android equivalent because the Android picker already uses a height-constrained modal bottom sheet. iOS screenshot fixtures/export tooling and TestFlight build metadata remain platform-specific. Verification remains excluded.

Validation: `testDebugUnitTest` passed (76 tests); `assembleDebug` passed. The existing cold-launch test now covers the expanded ordering and taxonomy filtering, and saved-selection coverage still passes. Emulator tests were not rerun for this default-list and label change.

## September 27 recent non-macOS changes

Compared the previous `8abf4cd` baseline with the adjacent repository's `origin/main` at [2b2c00d](https://github.com/pmusaraj/forum-index-app/commit/2b2c00d). Its checked-out `main` is older; this comparison uses committed remote-tracking history without changing that checkout.

- `2c276e4`: parse bold `**Author:**` and `**Post date:**` metadata as well as heading metadata. Preserve fenced examples and ordinary post Markdown.
- `0ad929d`: reveal a compact title after 12 dp of upward scrolling once the original title is offscreen; hide it on downward scrolling or returning to the top. Android tracks consumed nested-scroll distance, so layout changes, programmatic list jumps, and unconsumed overscroll do not reveal it. Reset visibility when changing the active page; use immediate transitions without motion effects.
- `b00a5d8`: add About to the feed's long-press actions, showing a selectable plain-text excerpt, community details with loading/retry, browser links, and an explicit Open topic action. Merely inspecting About does not mark a topic read.
- `dfe0eb5`: decode optional Hacker News/Lobsters signals, skipping unknown or invalid entries individually. Show source icons and accessible descriptions in feed rows, and browser discussion links in the root Markdown preview. Linked previews do not reuse the source topic's signals.
- Match the bookmark control's accent color and accessible Bookmarked/Not bookmarked state; retain native Android button behavior.
- `b3128cb`: Android already commits subject selection from `settledPage`, preserving per-subject list state. No additional pager change was needed.

macOS sidebar/layout, keyboard commands, desktop colors/fonts, and Apple signing/TestFlight build changes are excluded. Android verification remains deferred. Existing reader/rendering differences above remain applicable.

Validation: 80 JVM tests passed; debug APK, test APK, release APK, and release lint passed (0 errors, 30 warnings). On Pixel 6 / Android 13 (API 33), the initial full run passed 42 of 45 tests. After expanding the About sheet fully and using a bounded title-scroll gesture, all 14 recent-feature/reader tests passed on rerun, including the previously failing WebView gesture check and the added linked-signal isolation assertion. Thus all 45 distinct emulator scenarios passed across the two runs; this is not a claim of a single clean full-suite run. Physical-device/TalkBack checks and live external-discussion API/browser verification remain manual follow-up.

## October 15 comparison (iOS 552a2d0)

Pulled the adjacent iOS repository with `git pull --ff-only` and reviewed committed changes from `2b2c00d` through [552a2d0](https://github.com/pmusaraj/forum-index-app/commit/552a2d0). Both iOS and Android were already current with their upstream main branches.

- Main is now public Trending (`feed=trending`). For You is a separate authenticated destination, enabled by `for_you_eligible`. Eligibility refreshes at launch and after successful read reports. Saved subject ordering/hiding is retained, including temporarily unavailable For You, and personalized cache/state is removed on lost access.
- Stars are presented as bookmarks; existing storage and the server's `starred` action contract remain compatible. Newest saves appear first. New saves preserve forum capability and reader metadata; opening them uses the internal reader. Legacy saves without forum capability use the internal full-page view. URL-only saves never invent an index topic ID or submit a read for their source topic.
- While the app is foregrounded, the newest 20 bookmarks are checked at most every five minutes for Discourse `last_posted_at`, skipping unread/unsupported entries without admitting older saves. Requests go directly to the forum without contributor credentials. Unread state is persisted, indicated in the header and list, and cleared on opening the bookmark.
- Generated AI summaries can be expanded/collapsed above the first post. The parser only extracts summaries from the generated first-page envelope; ordinary user Markdown stays intact. No AI-generation request is made by the app.
- The first post's author/avatar and absolute date appear below the topic title. The end-of-topic footer includes a browser link, reply totals and case-insensitive participant totals only when trustworthy. Later-page/individual-post previews and broken/repeated pagination do not claim a complete topic.
- Settings include contributor feedback, a persistent draft, a 1,000 Unicode-code-point limit, app/OS metadata disclosure, and a stable request ID for retries. Success clears only the submitted draft; errors retain it. Android sends `platform=android`.

Apple release tooling, macOS layout changes, and the vendored Textual selection crash fix do not apply to Android's Compose/Markwon renderer. Android attestation remains deferred. Validation results are recorded in the checklist; production feedback submission and live bookmark polling are not exercised by automated tests.

## October 20 comparison (iOS/macOS e9f8254)

Reviewed the adjacent Apple repository through `e9f8254` (build 33), including `86e10cb` and `650138a`.

- New contributor nicknames use the same 64 verbs, 64 animal names, and two-digit suffix as Apple. Existing saved names remain intact.
- Settings shows the contributor nickname instead of an editable hardware name, with “Generate new name” available while enrolled or opted out. Enrolled updates PATCH only `display_name`; failed or mismatched responses retain the previous name. Local regeneration preserves contribution opt-out. Enrollment and status refresh persist the server-confirmed name.
- Settings includes “What is this app?” with the Apple description and a link to The Forum Index website. Hardware metadata, device verification, and installation identity remain separate from nicknames.
- Android startup now shows the website wordmark across the available width with a small progress indicator until the initial feed resolves. Both appearances are supported; errors expose Retry and later feed refreshes retain the reader UI.
- Empty linked-preview metadata rows are omitted, retaining the existing isolation of source-topic metadata.

Following screenshot review and user approval, version 1.0.4 (10004) was published to GitHub, internal testing, and open testing (`beta`) on October 20.

## October 26 comparison (Apple 2eb83af)

Reviewed committed Apple changes since `e9f8254`, especially `94a402e` (October 21,
image zoom and unread-reply resume), `720d71a` (reader footer), and `1e24359`
(support email).

- Markdown images open an accessible dark viewer with pinch zoom, pan, zoom buttons,
  Fit, Close, and loading/error/retry states. Image links open the viewer as well.
  Android retains its bounded, HTTPS-only bitmap loader and supported-format limits.
- Unread Markdown bookmarks resume at the first unread post. Public Discourse JSON
  locates the page using the post-ID stream, actual post numbers (including gaps),
  and the forum's chunk size. Legacy saves use their existing read timestamp until
  a new post establishes a cursor. No contributor credentials go to the forum.
- Opening a Markdown bookmark no longer clears unread status. Visible post bottoms
  advance persisted progress; hidden pages and the full webpage do not. Legacy
  bookmarks without Markdown capability retain their existing clear-on-open behavior.
  This supersedes the October 15 clear-on-open behavior for Markdown bookmarks.
- Resumed previews mark “New replies since last read,” offer earlier-page loading
  with retry, and provide Back to top. Earlier pages preserve existing post IDs and
  deduplicate numbered posts. Resume failures retain explicit Retry, Back to top,
  and View full page choices. Sharing and browser actions keep the original topic URL.
- The end-of-topic footer now centers the checkmark, heading, topic title, domain,
  totals, and browser action. README includes `hey@theforumindex.com` for support.

Apple keyboard shortcuts, the macOS subject picker, and signing/build metadata
remain platform-specific. Debug installation prefixes and bookmark terminology
were already present in Android.

Validation: 106 JVM tests and all 26 affected Pixel 6 / Android 13 emulator tests
passed on the final run. Release APK assembly and release lint passed. The initial
full emulator run exposed the Back to top state-reset issue and two obsolete
text selectors; these were fixed and covered by the final affected-suite run.
An existing WebView swipe check failed intermittently during an intermediate
rerun and passed in the final run. Store screenshot capture was skipped in the
initial full run because it requires its explicit capture flag. Live-forum and
physical-device/TalkBack checks remain manual follow-up.

## October 29 comparison (iOS build 43)

Reviewed iOS through [`2986179`](https://github.com/pmusaraj/forum-index-app/commit/29861798deb3875eed3fcd789d32fa7636c2cb1c), following the previous `2eb83af` baseline.

- Feed discovery uses `/api/v2/feeds`. New server-defined feeds appear once, can be reordered or hidden with the existing subject picker, and preserve saved choices. Discovery failure leaves existing feeds available. Reserved/duplicate IDs are ignored. Discovered requests stay under `/api/v2/`, preserve feed filters, set paging/language parameters, and send contribution credentials only for personalized feeds. API redirects are disabled.
- Reports include “Not Interesting for Me” using `not_interesting`.
- Exported oneboxes render as Markdown, with GitHub punctuation unescaped once and a 200 dp preview with Expand/Collapse controls. Ordinary quotes and fenced examples remain unchanged. Android uses text buttons and a clipped native text view instead of the SwiftUI gradient mask.
- `reader_markdown_url` controls reader availability, including explicit null/invalid opt-outs. Missing legacy fields fall back to forum Markdown support. Bookmarks persist the resolved URL while sharing/opening the original forum URL. Converted unread bookmarks open the original post in the full-page view; Show preview starts the converted reader at the beginning.
- Initial documents are cached for two minutes, bounded to 12 entries and 4 MiB of source text. Parsing runs off the main thread. Mutable readers and bookmark resume documents are not shared. Android keeps page-scoped cancellation; unlike the Apple cache, concurrent misses may fetch independently.
- Full web pages load only after selection and a request to view them (including automatic fallback), then remain available for preview/full-page toggling. Adjacent topics do not build native article views until selected; unread resume is limited to selected topics. Existing feed list-state and linked-preview history restoration remain in use.
- Markdown requests respect host-scoped Retry-After delays, including HTTP dates. Existing linked-image rendering already supports the iOS build 42 behavior and remains covered by the image-click test.

Apple signing, TestFlight version changes, UIKit scroll probes, and macOS changes are platform-specific and are not ported.

Validation: 115 JVM tests passed on the final revision. The full Pixel 9 / Android 15 suite passed 59 tests with the opt-in store screenshot exporter skipped. After the final personalized-feed opt-out and asynchronous image-layout refinements, all five affected recent-feature UI tests passed again. Release APK assembly and release lint passed (0 errors, 34 warnings, 2 hints). Coverage includes feed discovery and hidden choices, public versus personalized credentials, report submission, reader-source decoding and persistence, converted unread resume, onebox parsing and expansion, linked-image clicks, cache expiration/eviction, retry delays, and existing scrolling/history regressions. Production reports were not submitted; live discovery/converter, physical-device, and TalkBack checks remain unverified.

Physical-device follow-up: the user smoke-tested the separate Forum Index Test build on a Samsung SM-A145M and confirmed it looked good before authorizing release. The existing Google Play app and its data were preserved. This does not replace the outstanding TalkBack and targeted live-service checks.
