#!/usr/bin/env python3
"""Validate Play upload PNGs and generate an asset manifest and local review gallery."""
import hashlib
import json
from pathlib import Path
from PIL import Image

ROOT = Path(__file__).resolve().parents[1] / "store"
names = ["01-discover", "02-reader", "03-community", "04-starred", "05-subjects"]
expected = {"graphics/icon.png": (512, 512), "graphics/feature-graphic.png": (1024, 500)}
for device, size in [("phone", (1080, 1920)), ("tablet", (1600, 2560))]:
    for appearance in ["light", "dark"]:
        for name in names:
            expected[f"screenshots/{device}/{appearance}/{name}.png"] = size
assets = []
for name, size in expected.items():
    path = ROOT / name
    with Image.open(path) as image:
        assert image.size == size, (name, image.size, size)
        assert image.format == "PNG", name
        if name == "graphics/icon.png":
            assert path.stat().st_size <= 1024 * 1024
        else:
            assert image.mode == "RGB", (name, image.mode)
        image.verify()
    assets.append(dict(path=name, width=size[0], height=size[1], bytes=path.stat().st_size,
                       sha256=hashlib.sha256(path.read_bytes()).hexdigest()))
(ROOT / "asset-manifest.json").write_text(json.dumps(assets, indent=2) + "\n")
html = ['''<!doctype html><html lang="en"><meta charset="utf-8"><meta name="viewport" content="width=device-width">
<title>Forum Index · Play Store assets</title><style>
*{box-sizing:border-box}body{margin:0;background:#faf6ee;color:#101010;font:16px system-ui,sans-serif}
main{max-width:1440px;margin:auto;padding:48px 28px}h1{font-size:40px;letter-spacing:-1.5px;margin:12px 0}
h2{margin-top:48px}p{color:#555;line-height:1.7}a{color:#008c95}img{display:block;max-width:100%;height:auto}
.brand{display:grid;grid-template-columns:180px 1fr;gap:24px;align-items:center;max-width:1000px}
.grid{display:grid;grid-template-columns:repeat(5,minmax(0,1fr));gap:18px}.grid a{text-decoration:none}
.grid img{border:1px solid #dedbd5;border-radius:8px}.grid span{display:block;margin-top:10px;font-size:14px}
@media(max-width:800px){.grid{grid-template-columns:repeat(2,minmax(0,1fr))}.brand{grid-template-columns:100px 1fr}}
</style><main><p>GOOGLE PLAY · ENGLISH</p><h1>Forum Index</h1>
<p>Original brand artwork and genuine Android captures. Click any image for the full-resolution upload file.<br>
Each screenshot set contains discovery, reading, community information, saved topics, and subject selection.</p>
<div class="brand"><a href="graphics/icon.png"><img src="graphics/icon.png" alt="512px Play icon"></a>
<a href="graphics/feature-graphic.png"><img src="graphics/feature-graphic.png" alt="1024 by 500 feature graphic"></a></div>
<p><a href="README.md">Upload and regeneration notes</a> · <a href="asset-manifest.json">Dimensions and checksums</a> ·
<a href="graphics/logo-light.png">Light wordmark</a> · <a href="graphics/logo-dark.png">Dark wordmark</a></p>''']
for device in ["phone", "tablet"]:
    for appearance in ["light", "dark"]:
        html.append(f'<h2>{device.title()} · {appearance.title()}</h2><div class="grid">')
        for name in names:
            src = f"screenshots/{device}/{appearance}/{name}.png"
            label = name.replace("-", " · ").title()
            html.append(f'<a href="{src}"><img loading="lazy" src="{src}" alt="{label}"><span>{label}</span></a>')
        html.append('</div>')
html.append('</main></html>')
(ROOT / "index.html").write_text("\n".join(html) + "\n")
print(f"Validated {len(assets)} upload assets; wrote store/index.html and store/asset-manifest.json")
