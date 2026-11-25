# Galaxy Store and AppGallery

Reuse the signed `forum-index-v1.0.9.apk` from the same [GitHub release workflow](github-releases.md) that produces the Play bundle. The next release adds in-app contributor data deletion. Use `com.musaraj.forumindex` and the same increasing version code across stores.

## Before creating store drafts

- Deploy the [Android privacy and deletion disclosures](play-store-release.md#prepare-the-update). Use the [listing drafts, icon, and screenshots](../store/README.md), the [release notes](../store/release-notes/en-US/10009.txt), and the [reviewer instructions](play-store-release.md#play-console-fields). Supply the public website, a monitored support address, and the privacy and deletion links where requested.
- Download the APK, checksums, and public certificate from the same GitHub release. [Verify the files](github-releases.md#verify-a-download) and install the APK on a test device. Verify that an installed GitHub release updates to the new APK with bookmarks intact. Compare the certificate delivered by each store and test any cross-store upgrade you plan to support.
- In Play Console, check **Android developer verification → Package names**. Confirm that the installed signing certificate used by your off-Play APK is registered for this package. If Play delivers a different certificate, verify the additional certificate with the official Console flow. Developer verification is already enforced in some markets for participating stores. [Android developer verification guide](https://developer.android.com/developer-verification/guides/google-play-console)

## Samsung Galaxy Store

1. Register or sign in to [Seller Portal](https://seller.samsungapps.com/) and complete seller verification and commercial seller approval as required for distribution. [Samsung registration guide](https://developer.samsung.com/galaxy-store/prepare.html)
2. Create an Android app. Set the English listing, category, age rating, contact and legal links. Start with `store/graphics/icon.png` and the five portrait phone screenshots. Those files meet Samsung’s published mobile image dimensions. [Samsung image specifications](https://developer.samsung.com/galaxy-store/galaxy-store-developer-api/content-publish-api/reference.html)
3. Choose the **APK** upload with the existing app signing identity, configure compatible Galaxy phones/tablets and the intended regions and price, then test a store-delivered build on a Galaxy device before submitting for review. Uploading an AAB requires Galaxy Store to manage signing. [Samsung app submission guide](https://developer.samsung.com/galaxy-store/launch.html)

## Huawei AppGallery

1. Verify your Huawei developer account and create the Android app in [AppGallery Connect](https://developer.huawei.com/consumer/en/appgallery/).
2. Select the APK submission format. Upload the signed APK for `com.musaraj.forumindex` and supply its existing SHA-256 signing certificate fingerprint if requested. Fill in the English listing, icon, screenshots, contact details, privacy policy, category, rating and distribution regions. Mainland China has additional listing and documentation requirements. [AppGallery APK submission guide](https://developer.huawei.com/consumer/en/doc/doccenter-distribution/agc-help-releaseapkrpk-0000001106463276/)
3. Test feeds, the reader and WebView fallback on a Huawei device that supports Android APKs, preferably without Google Play services. The app’s current Gradle dependencies have no Google Play services SDK requirement. Submit the tested build for review in AppGallery Connect.

Amazon ended its general Android phone store in 2025 and still serves Fire devices. A Fire Tablet release would need its own testing. F-Droid’s main repository requires an eligible free/open-source license and source build. This repository currently has no top-level application license. [Amazon announcement](https://developer.amazon.com/apps-and-games/blogs/2025/02/upcoming-changes-to-amazon-appstore-for-android-devices-and-coins-program), [F-Droid inclusion policy](https://f-droid.org/docs/Inclusion_Policy/)
