#!/usr/bin/env python3
"""Render the original brand assets with Chrome and ImageMagick (macOS, Avenir Next)."""
from pathlib import Path
import subprocess
import tempfile
import time

ROOT = Path(__file__).resolve().parents[1]
GRAPHICS = ROOT / "store/graphics"
CHROME = "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"

subprocess.run(["magick", str(GRAPHICS / "source/icon-1024.png"), "-resize", "512x512",
                "-strip", "PNG32:" + str(GRAPHICS / "icon.png")], check=True)
with tempfile.TemporaryDirectory(prefix="forum-index-graphics-") as profile:
    for source, output, size, transparent in [
        ("feature-graphic.svg", "feature-graphic.png", "1024,500", False),
        ("forum-index-full-logo.svg", "logo-light.png", "600,160", True),
        ("forum-index-full-logo-dark.svg", "logo-dark.png", "600,160", True),
    ]:
        command = [CHROME, "--headless", "--disable-gpu", "--no-first-run",
                   "--user-data-dir=" + profile, "--hide-scrollbars", "--force-device-scale-factor=1",
                   "--window-size=" + size, "--screenshot=" + str(GRAPHICS / output)]
        if transparent:
            command.append("--default-background-color=00000000")
        destination = GRAPHICS / output
        destination.unlink(missing_ok=True)
        process = subprocess.Popen(command + [(GRAPHICS / "source" / source).as_uri()],
                                   stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        try:
            for _ in range(150):
                if destination.exists() and destination.stat().st_size > 0:
                    time.sleep(.5)
                    break
                if process.poll() is not None:
                    raise RuntimeError(f"Chrome exited before rendering {source}")
                time.sleep(.2)
            else:
                raise TimeoutError(f"Chrome did not render {source}")
        finally:
            process.terminate()
            try:
                process.wait(timeout=5)
            except subprocess.TimeoutExpired:
                process.kill()
                process.wait()
        subprocess.run(["magick", str(GRAPHICS / output), "-strip",
                        ("PNG32:" if transparent else "PNG24:") + str(GRAPHICS / output)], check=True)
print("Saved graphics to", GRAPHICS)
