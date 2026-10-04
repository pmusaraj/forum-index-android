#!/usr/bin/env python3
"""Publish an already signed release bundle to an explicitly allowed Play testing track."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import urllib.error
import urllib.parse
import urllib.request

PACKAGE = "com.musaraj.forumindex"
API = f"https://androidpublisher.googleapis.com/androidpublisher/v3/applications/{PACKAGE}/edits"
UPLOAD = f"https://androidpublisher.googleapis.com/upload/androidpublisher/v3/applications/{PACKAGE}/edits"


def version_code(tag):
    match = re.fullmatch(r"v(0|[1-9][0-9]{0,5})\.(0|[1-9][0-9]?)\.(0|[1-9][0-9]?)", tag)
    if not match:
        raise ValueError("Expected vMAJOR.MINOR.PATCH (minor and patch 0–99)")
    major, minor, patch = map(int, match.groups())
    code = major * 10000 + minor * 100 + patch
    if major > 209999 or code < 1:
        raise ValueError("Version exceeds the supported Android version code range")
    return code


def check_track(track, allowed):
    if not re.fullmatch(r"[A-Za-z0-9_-]+", track) or track.lower() == "production":
        raise ValueError("Only testing tracks are supported")
    if track not in {item.strip() for item in allowed.split(",")}:
        raise ValueError(f"Track {track!r} is not listed in PLAY_TEST_TRACKS")


def verified_bytes(bundle, checksums):
    data = bundle.read_bytes()
    digest = hashlib.sha256(data).hexdigest()
    entries = [line.split() for line in checksums.read_text().splitlines() if line.strip()]
    matches = [entry[0] for entry in entries if len(entry) == 2 and entry[1].lstrip("*") == bundle.name]
    if matches != [digest]:
        raise ValueError("Bundle checksum is missing, ambiguous, or does not match the GitHub release")
    return data, digest


class PlayAPI:
    def __init__(self, token):
        self.token = token

    def call(self, method, url, payload=None):
        binary = isinstance(payload, bytes)
        data = payload if binary else (json.dumps(payload).encode() if payload is not None else None)
        request = urllib.request.Request(url, data=data, method=method, headers={
            "Authorization": f"Bearer {self.token}",
            "Content-Type": "application/octet-stream" if binary else "application/json",
        })
        try:
            with urllib.request.urlopen(request, timeout=180) as response:
                body = response.read()
                return json.loads(body) if body else {}
        except urllib.error.HTTPError as error:
            # Error messages help diagnose missing permissions, signing mismatch, and Console setup.
            try:
                message = json.loads(error.read()).get("error", {}).get("message", "Request rejected")
            except (ValueError, AttributeError):
                message = "Request rejected"
            raise RuntimeError(f"Google Play HTTP {error.code}: {message}") from None


def publish(api, tag, track, code, data, digest):
    edit = api.call("POST", API, {})["id"]
    base = f"{API}/{urllib.parse.quote(edit, safe='')}"
    committed = False
    try:
        tracks = api.call("GET", base + "/tracks").get("tracks", [])
        current = next((item for item in tracks if item["track"] == track), None)
        if current is None:
            raise ValueError(f"Create the {track!r} testing track in Play Console first")
        releases = current.get("releases", [])
        current_codes = [int(value) for release in releases for value in release.get("versionCodes", [])]
        if any(value > code for value in current_codes):
            raise ValueError("Refusing to replace a newer testing release with an older version")
        if any(release.get("status") in {"draft", "inProgress", "halted"} and
               release.get("versionCodes") != [str(code)] for release in releases):
            raise ValueError("Track has another pending or halted release; resolve it in Play Console first")
        bundles = api.call("GET", base + "/bundles").get("bundles", [])
        existing = next((item for item in bundles if int(item["versionCode"]) == code), None)
        if existing is not None:
            if existing.get("sha256") != digest:
                raise ValueError("This version code already belongs to a different bundle; use a new tag")
        else:
            uploaded = api.call("POST", f"{UPLOAD}/{urllib.parse.quote(edit, safe='')}/bundles?uploadType=media", data)
            if int(uploaded["versionCode"]) != code or uploaded.get("sha256") != digest:
                raise ValueError("Uploaded bundle identity does not match the release tag and checksum")
        if any(release.get("status") == "completed" and str(code) in release.get("versionCodes", []) for release in releases):
            print(f"{tag} is already released to {track}; nothing changed.")
            return
        api.call("PUT", base + "/tracks/" + urllib.parse.quote(track, safe=""), {
            "track": track, "releases": [{"name": tag, "versionCodes": [str(code)], "status": "completed"}],
        })
        api.call("POST", base + ":validate", {})
        api.call("POST", base + ":commit", {})
        committed = True
        print(f"Committed {tag} (code {code}) to {track}. Play processing/review may still apply.")
    finally:
        if not committed:
            try:
                api.call("DELETE", base)
            except Exception:
                print("Could not discard the edit; it will expire automatically.")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--tag", required=True)
    parser.add_argument("--track", default="internal")
    parser.add_argument("--bundle", type=Path)
    parser.add_argument("--checksums", type=Path)
    parser.add_argument("--check-inputs", action="store_true")
    parser.add_argument("--check-access", action="store_true")
    args = parser.parse_args()
    code = version_code(args.tag)
    check_track(args.track, os.environ.get("PLAY_TEST_TRACKS", "internal"))
    if args.check_inputs:
        print(f"Validated {args.tag}: version code {code}, track {args.track}")
        return
    if args.check_access:
        token = os.environ.get("PLAY_ACCESS_TOKEN")
        if not token:
            raise ValueError("PLAY_ACCESS_TOKEN is required")
        api = PlayAPI(token)
        edit = api.call("POST", API, {})["id"]
        base = f"{API}/{urllib.parse.quote(edit, safe='')}"
        try:
            tracks = api.call("GET", base + "/tracks").get("tracks", [])
            print(json.dumps(tracks, indent=2))
            if not any(item["track"] == args.track for item in tracks):
                raise ValueError(f"Testing track {args.track!r} does not exist")
            print("Google Play access verified. No release was uploaded or changed.")
        finally:
            api.call("DELETE", base)
        return
    if not args.bundle or not args.checksums:
        parser.error("--bundle and --checksums are required")
    data, digest = verified_bytes(args.bundle, args.checksums)
    token = os.environ.get("PLAY_ACCESS_TOKEN")
    if not token:
        raise ValueError("PLAY_ACCESS_TOKEN is required")
    publish(PlayAPI(token), args.tag, args.track, code, data, digest)


if __name__ == "__main__":
    try:
        main()
    except (ValueError, RuntimeError, OSError) as error:
        raise SystemExit(str(error))
