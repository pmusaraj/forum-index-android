"""Offline checks for release identity, retries, and track protection."""
import importlib.util
from pathlib import Path
import tempfile
import unittest

spec = importlib.util.spec_from_file_location("publisher", Path(__file__).with_name("publish-play-testing.py"))
publisher = importlib.util.module_from_spec(spec)
spec.loader.exec_module(publisher)


class FakeAPI:
    def __init__(self, bundles=None, releases=None, fail_commit=False, upload_code=10001):
        self.bundles = bundles or []
        self.releases = releases or []
        self.fail_commit = fail_commit
        self.upload_code = upload_code
        self.calls = []

    def call(self, method, url, payload=None):
        self.calls.append((method, url, payload))
        if url == publisher.API:
            return {"id": "edit-1"}
        if method == "GET" and url.endswith("/tracks"):
            return {"tracks": [{"track": "internal", "releases": self.releases}]}
        if method == "GET" and url.endswith("/bundles"):
            return {"bundles": self.bundles}
        if "uploadType=media" in url:
            return {"versionCode": self.upload_code, "sha256": "expected"}
        if url.endswith(":commit") and self.fail_commit:
            raise RuntimeError("Commit rejected")
        return {}


class PublisherTests(unittest.TestCase):
    def publish(self, api):
        publisher.publish(api, "v1.0.1", "internal", 10001, b"bundle", "expected")

    def test_rejects_production_and_unconfigured_tracks(self):
        for track in ["production", "wear:production", "../production", "alpha"]:
            with self.assertRaises(ValueError):
                publisher.check_track(track, "internal,production")
        publisher.check_track("team-testing", "internal,team-testing")

    def test_version_mapping_and_invalid_tags(self):
        self.assertEqual(publisher.version_code("v1.0.1"), 10001)
        for tag in ["v0.0.0", "v1.100.0", "v01.0.0", "v1.0.1-beta", "v210000.0.0"]:
            with self.assertRaises(ValueError):
                publisher.version_code(tag)

    def test_checksum_must_name_the_exact_bundle(self):
        with tempfile.TemporaryDirectory() as directory:
            bundle = Path(directory) / "release.aab"
            bundle.write_bytes(b"bundle")
            checksums = Path(directory) / "SHA256SUMS"
            digest = publisher.hashlib.sha256(b"bundle").hexdigest()
            checksums.write_text(f"{digest}  release.aab\n")
            self.assertEqual(publisher.verified_bytes(bundle, checksums), (b"bundle", digest))
            checksums.write_text(f"{digest}  another.aab\n")
            with self.assertRaises(ValueError):
                publisher.verified_bytes(bundle, checksums)

    def test_success_uploads_validates_and_commits(self):
        api = FakeAPI()
        self.publish(api)
        self.assertTrue(any("uploadType=media" in url for _, url, _ in api.calls))
        self.assertEqual(api.calls[-2][1].split(":")[-1], "validate")
        self.assertEqual(api.calls[-1][1].split(":")[-1], "commit")

    def test_completed_retry_is_noop_and_discards_edit(self):
        api = FakeAPI(bundles=[{"versionCode": 10001, "sha256": "expected"}],
                      releases=[{"versionCodes": ["10001"], "status": "completed"}])
        self.publish(api)
        self.assertFalse(any(method == "PUT" or "uploadType=media" in url for method, url, _ in api.calls))
        self.assertEqual(api.calls[-1][0], "DELETE")

    def test_existing_version_with_wrong_hash_is_rejected(self):
        api = FakeAPI(bundles=[{"versionCode": 10001, "sha256": "wrong"}])
        with self.assertRaisesRegex(ValueError, "different bundle"):
            self.publish(api)
        self.assertFalse(any(method == "PUT" for method, _, _ in api.calls))
        self.assertEqual(api.calls[-1][0], "DELETE")

    def test_wrong_uploaded_version_is_never_assigned_to_track(self):
        api = FakeAPI(upload_code=10000)
        with self.assertRaisesRegex(ValueError, "identity"):
            self.publish(api)
        self.assertFalse(any(method == "PUT" for method, _, _ in api.calls))

    def test_downgrades_and_other_pending_releases_are_rejected(self):
        for release in [{"versionCodes": ["10002"], "status": "completed"},
                        {"versionCodes": ["10000"], "status": "draft"}]:
            api = FakeAPI(releases=[release])
            with self.assertRaises(ValueError):
                self.publish(api)
            self.assertFalse(any(method == "PUT" for method, _, _ in api.calls))
            self.assertEqual(api.calls[-1][0], "DELETE")

    def test_failed_commit_discards_edit_and_reports_failure(self):
        api = FakeAPI(fail_commit=True)
        with self.assertRaisesRegex(RuntimeError, "Commit rejected"):
            self.publish(api)
        self.assertEqual(api.calls[-1][0], "DELETE")


if __name__ == "__main__":
    unittest.main()
