# GitHub APK releases

Pushing a version tag runs `.github/workflows/release.yml`: unit tests, release lint, signed APK build, signature verification, and publication to GitHub Releases. Assets include the APK, `SHA256SUMS`, and public signing-certificate details. The release remains a draft until all assets upload successfully. Already published releases are never overwritten by the workflow.

## Publish

After Android CI passes on `main`:

```sh
git switch main
git pull --ff-only
git tag -a v1.0.0 -m 'Forum Index 1.0.0'
git push origin v1.0.0
```

For subsequent releases, use a new, increasing `vMAJOR.MINOR.PATCH` tag. Minor and patch must each be 0–99. Android's version code is `MAJOR * 10000 + MINOR * 100 + PATCH`, so `v1.0.0` has code `10000`, and `v1.0.1` has code `10001`. Prerelease suffixes are not supported. Tags supply the release version through Gradle properties; local build defaults are unchanged.

Watch the **Release APK** workflow in the Actions tab. If a run fails before publication, fix the underlying configuration and rerun it; an existing draft can be resumed. For code changes, use a new tag. Never move a published release tag.

The ordinary CI workflow also runs tests, lint, and unsigned build checks for pushes to `main` and pull requests. It does not receive signing credentials. Release actions are pinned to commit SHAs, Gradle wrapper validation is enabled, and signing builds do not save Gradle caches.

## Signing

The repository uses a dedicated GitHub APK signing key. Keep the same key for every update. Its private material lives in these GitHub Actions secrets:

| Secret | Value |
| --- | --- |
| `ANDROID_KEYSTORE_BASE64` | Base64-encoded PKCS12 keystore |
| `ANDROID_KEYSTORE_PASSWORD` | Keystore password |
| `ANDROID_KEY_ALIAS` | Signing alias |
| `ANDROID_KEY_PASSWORD` | Private key password |

The workflow maps these to the existing `FORUM_INDEX_UPLOAD_*` Gradle environment variables. It refuses to build without all credentials, verifies the APK signature, and removes the temporary keystore. Only trusted maintainers should push release tags or change release workflows.

Keep an independent secure backup of the keystore and passwords. GitHub secrets cannot be downloaded for recovery; losing the key prevents updating existing installations. During initial setup, a local backup was created outside the checkout under `~/.local/share/forum-index-android/signing/` with owner-only permissions. Copy that directory into your encrypted backup or password manager.

This key is separate from Google Play App Signing. GitHub APKs and Play builds can update each other only if their application ID and app signing certificate match. See [the Play release guide](play-store-release.md) before configuring Play distribution. A debug build also has a different certificate and must be uninstalled before installing the release APK; uninstalling removes its local app data.

## Verify a download

Download the APK and `SHA256SUMS` from the same release, then run:

```sh
sha256sum -c SHA256SUMS
# macOS:
shasum -a 256 -c SHA256SUMS
```

With Android SDK build tools installed, `apksigner verify --verbose --print-certs forum-index-v1.0.0.apk` verifies the APK signature. Compare its certificate SHA-256 digest with the first trusted release and `signing-certificate.txt`; it should remain the same across updates.

## Public-repository preparation

Before initial publication, Gitleaks 8.30.1 scanned all 17 existing commits across local and fetched remote refs and reported no secrets. Historical file names and credential-related code were also reviewed. There were no existing Actions runs, artifacts, or releases. No history rewrite was needed. Public API URLs, package identifiers, and normal commit author metadata remain public.

Keystores, private-key files, local environment files, and APK/AAB outputs are ignored. Do not commit signing backups or real enrollment tokens. The historical scan is a point-in-time check, not a guarantee about future commits.
