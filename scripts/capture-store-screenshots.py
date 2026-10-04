#!/usr/bin/env python3
"""Capture real Android UI. Build/install the debug and test APKs first; see store/README.md."""
import argparse
import os
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[1]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("device", choices=["phone", "tablet"])
    parser.add_argument("serial", help="adb device serial")
    parser.add_argument("--only", choices=["01-discover", "02-reader", "03-community", "04-starred", "05-subjects"])
    args = parser.parse_args()
    sdk = Path(os.environ.get("ANDROID_HOME", Path.home() / "Library/Android/sdk"))
    adb = [str(sdk / "platform-tools/adb"), "-s", args.serial]

    def run(*command, **kwargs):
        return subprocess.run(adb + list(command), check=True, **kwargs)

    size = "1080x1920" if args.device == "phone" else "1600x2560"
    run("shell", "wm", "size", size)
    run("shell", "settings", "put", "global", "heads_up_notifications_enabled", "0")
    run("shell", "settings", "put", "global", "sysui_demo_allowed", "1")
    for values in [("clock", "hhmm", "0941"), ("battery", "level", "100"), ("notifications", "visible", "false")]:
        command, key, value = values
        run("shell", "am", "broadcast", "-a", "com.android.systemui.demo", "-e", "command", command, "-e", key, value)
    run("install", "-r", str(ROOT / "app/build/outputs/apk/debug/app-debug.apk"))
    run("install", "-r", str(ROOT / "app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"))
    result = run("shell", "am", "instrument", "-w", "-e", "class",
                 "com.musaraj.forumindex.StoreScreenshotTest", "-e", "captureStore", "true",
                 *(["-e", "captureOnly", args.only] if args.only else []),
                 "com.musaraj.forumindex.test/androidx.test.runner.AndroidJUnitRunner",
                 capture_output=True, text=True)
    print(result.stdout)
    if "OK (1 test)" not in result.stdout:
        raise SystemExit("Screenshot capture did not pass; existing exports were not replaced.")
    output = ROOT / "store/screenshots" / args.device
    output.mkdir(parents=True, exist_ok=True)
    run("pull", "/sdcard/Android/data/com.musaraj.forumindex/files/store-screenshots/.", str(output))
    for path in sorted(output.glob("*/*.png")):
        subprocess.run(["magick", str(path), "-alpha", "off", "-strip", "PNG24:" + str(path)], check=True)
    print("Saved", output)


if __name__ == "__main__":
    main()
