# Privacy and Data safety inventory

This is a source-code inventory for preparing disclosures, not a published privacy policy or a completed Data safety declaration. Backend operations and third-party websites require operator review.

| Data / behavior | Observed implementation | Disclosure follow-up |
| --- | --- | --- |
| Subject choices, opened topics, starred topic details | Stored locally; feed cache is in memory; app backup disabled | Distinguish local storage from transmitted contribution actions |
| Feed requests | HTTPS requests to `https://do3.musaraj.com`; subject/page choices in URLs | Confirm server/CDN IP, request logging, retention and purposes |
| Display name and device name | Sent on optional contributor enrollment; device name editable | Assess personal info/name and user-provided content categories; explain optional collection |
| Installation identity and bearer token | Server-issued installation identity; bearer token encrypted locally with Android Keystore and used for authenticated requests | Assess user/device identifiers; this is pseudonymous, not guaranteed anonymous |
| Reads, star/unstar actions and topic reports | Topic ID, action kind and active flag sent while enrolled | Assess app interactions and relevant browsing-history categories; used to improve index and personalize feed |
| Forum submissions | Trusted contributors can send a forum URL | Assess other user-generated content; confirm moderation and retention |
| Opt-out | Local credential removal first, then best-effort DELETE `/api/v2/installation`; local stars retained | Verify deletion of installation and associated actions, backups and retention exceptions before promising deletion |
| Native topic previews | Credential-free HTTPS requests to forum topic URLs with `Accept: text/markdown`; additional reply pages, author avatars, and body images may be fetched | Disclose requests to original forum and image hosts; no contributor token is sent to these hosts |
| Forum pages | HTTPS WebView with JavaScript, plus external browser opening | Third-party hosts receive requests and may set cookies or load trackers/content. Audit applicable WebView collection/sharing and forum privacy practices |
| Analytics, ads, crash reporting SDKs | None explicitly configured in direct dependencies | Check transitive dependencies, backend telemetry and displayed web content before final declarations |

All application API endpoints and allowed topic navigation use HTTPS; mixed content is disabled. Do not infer comprehensive third-party data practices from those settings.

Before publishing a policy, supply the operator's legal/public name and contact email, effective date, hosting URL, data recipients/processors, geographic processing details where relevant, retention periods, and a supported deletion/request process. Confirm whether display names or contribution statistics are public. Explain that browsing is available without contribution enrollment and that contribution activity is associated with an installation.

Google's [Data safety guidance](https://support.google.com/googleplay/android-developer/answer/10787469) covers off-device collection, SDKs, and WebViews. Review that guidance against the complete service before completing the Console form.
