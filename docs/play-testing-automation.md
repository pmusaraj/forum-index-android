# Automated Google Play testing releases

The Google Cloud project is `forum-index-510609` (number `266130594295`). The release service account is `play-release@forum-index-510609.iam.gserviceaccount.com`. Play Console access is restricted to `com.musaraj.forumindex`, with app read access and permission to release to testing tracks.

GitHub authenticates through Workload Identity Federation. No service-account JSON key is needed. The `github-releases/github` provider accepts only repository ID `1364595140`, owner ID `368961`, and either the manual Play workflow on `main` or the tagged release workflow. The service account has a `roles/iam.workloadIdentityUser` binding for that repository's identity pool principal.

## Repository variables

| Variable | Value |
| --- | --- |
| `PLAY_WIF_PROVIDER` | `projects/266130594295/locations/global/workloadIdentityPools/github-releases/providers/github` |
| `PLAY_SERVICE_ACCOUNT` | `play-release@forum-index-510609.iam.gserviceaccount.com` |
| `PLAY_TEST_TRACKS` | `internal` (comma-separated allowlist of testing track IDs) |
| `PLAY_UPLOADS_ENABLED` | Set to `true` after verifying access to enable uploads on version tags |

The existing Android signing secrets also sign the Play bundle. Keep the upload key consistent with the first accepted Play upload. The Google Cloud service account authenticates API requests; it is unrelated to the Android signing certificate.

## Verify access

```sh
gh workflow run play-testing.yml --ref main \
  -f tag=v1.0.0 -f track=internal -f verify_access=true
```

This authenticates from GitHub, creates a temporary Play edit, reads track/version information, and deletes the edit without publishing changes. For this mode, the tag is only validated for syntax and does not require a downloadable bundle. Avoid concurrent manual Console edits while automation runs.

## Release

Once enabled, pushing a new version tag builds and verifies both an APK and AAB, publishes both with checksums on GitHub, then calls the Play testing workflow to upload the AAB to `internal`. The current version mapping is described in [the GitHub release guide](github-releases.md). Version codes must exceed previous uploads; the manually uploaded `v1.0.0` uses code `10000`.

```sh
git tag -a v1.0.1 -m 'Forum Index 1.0.1'
git push origin v1.0.1
```

The Play release is committed with status `completed`, making it available to the track's configured testers once Google finishes processing and any applicable review. The workflow does not configure testers, change store listings, or publish to production.

## Retry or promote to another testing track

Use **Actions → Publish Play testing → Run workflow**, selecting `main`, the existing GitHub release tag, and the testing track ID. Leave the access-only checkbox off. Or run:

```sh
gh workflow run play-testing.yml --ref main -f tag=v1.0.1 -f track=internal
```

This downloads the published AAB and verifies its release checksum. An existing upload is reused only if both its version code and SHA-256 match. An already completed release is a no-op. Downgrades and unrelated draft/staged/halted releases are rejected. Failed edits are discarded; rerun after resolving the error. The original `v1.0.0` GitHub release predates automated AAB assets, so normal upload runs require a newer release containing an AAB.

For a closed-testing track, first create/configure it in Play Console and add its exact API track ID to `PLAY_TEST_TRACKS`, then select it in the manual workflow. Production and form-factor production tracks are rejected even if accidentally allowlisted. Play uploads are serialized across tracks.

If Google rejects a release because Console setup or review is required, resolve that requirement there. The automation reports the error without silently switching to draft or bypassing review. Check the upload job's final result before assuming testers can install the version.

Run the offline publisher checks with `python3 -m unittest discover -s scripts -p 'test_publish_play_testing.py' -v`.
