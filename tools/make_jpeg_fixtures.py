"""Make small JPEG test files for core/imaging tests (run once, files are committed)."""
import os, random
from PIL import Image

OUT = os.path.join(os.path.dirname(__file__), "..", "core", "imaging", "src", "test", "resources", "jpeg")
os.makedirs(OUT, exist_ok=True)
random.seed(7)

def picture(w, h, mode="RGB"):
    img = Image.new("RGB", (w, h))
    px = img.load()
    for y in range(h):
        for x in range(w):
            n = random.randint(-12, 12)
            px[x, y] = (
                max(0, min(255, int(255 * x / w) + n)),
                max(0, min(255, int(255 * y / h) + n)),
                max(0, min(255, 128 + int(60 * ((x // 9 + y // 7) % 2)) + n)),
            )
    return img.convert(mode)

cases = [
    ("rgb444_q95.jpg", 64, 48, "RGB", dict(quality=95, subsampling=0)),
    ("rgb420_q75.jpg", 203, 157, "RGB", dict(quality=75, subsampling=2)),
    ("rgb422_q90.jpg", 203, 157, "RGB", dict(quality=90, subsampling=1)),
    ("gray_q85.jpg", 77, 51, "L", dict(quality=85)),
    ("rgb420_restart.jpg", 203, 157, "RGB", dict(quality=92, subsampling=2, restart_marker_blocks=4)),
    ("progressive.jpg", 64, 48, "RGB", dict(quality=90, progressive=True)),
]
for name, w, h, mode, opts in cases:
    picture(w, h, mode).save(os.path.join(OUT, name), "JPEG", **opts)
    print(name)
