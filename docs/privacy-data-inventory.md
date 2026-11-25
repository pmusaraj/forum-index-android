# Privacy and Data safety inventory

This is a source-code inventory for preparing disclosures, not a published privacy policy or a completed Data safety declaration. Backend operations and third-party websites require operator review.

| Data / behavior | Observed implementation | Disclosure follow-up |
| --- | --- | --- |
| Subject choices, opened topics, bookmarked topic details | Stored locally; feed cache is in memory; app backup disabled | Distinguish local storage from transmitted contribution actions |
| Feed requests | HTTPS requests to `https://forumindex.musaraj.com`; subject/page choices in URLs | Confirm server/CDN IP, request logging, retention and purposes |
| Generated contributor name and device name | Sent on automatic enrollment unless opted out; device name editable | Assess personal info/name and user-provided content categories; explain automatic enrollment and persistent opt-out |
| Installation identity and bearer token | Server-issued installation identity; bearer token encrypted locally with Android Keystore and used for authenticated requests | Assess user/device identifiers; this is pseudonymous, not guaranteed anonymous |
| Reads, bookmark/remove-bookmark actions and topic reports | Topic ID, action kind and active flag sent while enrolled | Assess app interactions and relevant browsing-history categories; used to improve index and personalize feed |
| Forum submissions | Trusted contributors can send a forum URL | Assess other user-generated content; confirm moderation and retention |
| Opt-out | Locally disables contributions and removes the credential, then attempts to revoke it with DELETE `/api/v2/installation`. Keeps local bookmarks and past server data. | Describe stopping contributions and deletion as distinct controls. |
| Contributor data deletion | With an enrolled profile, Settings calls DELETE `/api/v2/installation/data`. On HTTP 204 it turns contributions off and clears local credentials, bookmarks, reading history, and the feedback draft. The backend erases the live profile, linked activity and feedback. | Review backup, operational log and provider retention. Handle requests from opted-out or uninstalled users through the public deletion link and the support inbox. |
| Native topic previews | Credential-free HTTPS requests to forum topic URLs with `Accept: text/markdown`; additional reply pages, author avatars, and body images may be fetched | Disclose requests to original forum and image hosts; no contributor token is sent to these hosts |
| Bookmark reply tracking | Foreground credential-free requests to the newest 20 saved Discourse topics, at most every five minutes; activity timestamps stored locally | Original forums receive requests even when the saved topic is not currently being read; no contributor token is sent |
| Feedback | Draft and request ID stored locally; enrolled users explicitly send message, app/build/OS versions and Android platform to `/api/v2/feedback` | Feedback is linked to the contributor profile and sent to app admins; confirm server retention and access |
| Forum pages | HTTPS WebView with JavaScript, plus external browser opening | Third-party hosts receive requests and may set cookies or load trackers/content. Audit applicable WebView collection/sharing and forum privacy practices |
| Analytics, ads, crash reporting SDKs | None explicitly configured in direct dependencies | Check transitive dependencies, backend telemetry and displayed web content before final declarations |

All application API endpoints and allowed topic navigation use HTTPS; mixed content is disabled. Do not infer comprehensive third-party data practices from those settings.

The [live website policy](https://theforumindex.com/privacy/) describes Forum Index data practices. Deploy the Android disclosures and request link prepared in the website checkout before relying on the link in store forms. The [production backend privacy notice](https://forumindex.musaraj.com/api/v2/privacy) lists 60 days from publication for topic-linked activity, 180 days for feedback, and retention until deletion for installation records. Confirm operational log and backup schedules, provider arrangements, public profile visibility, and the support deletion process with the service operator.

Google's [Data safety guidance](https://support.google.com/googleplay/android-developer/answer/10787469) covers off-device collection, SDKs, and WebViews. Review that guidance against the complete service before completing the Console form.
