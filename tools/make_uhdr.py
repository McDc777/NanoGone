"""Turn a normal JPEG into an Ultra HDR JPEG (gain map at 1/4 size: bright parts glow up to 4x).

Usage: make_uhdr.py ULTRAHDR_APP in.jpg out.jpg [--red-glows]
With --red-glows, strongly red things (the test bin) get the strongest glow, so a test can see
whether the glow went away with the object.
Uses Google's libultrahdr tool (encode scenario 4: picture + gain map + metadata).
"""
import os, subprocess, sys, tempfile
from PIL import Image, ImageFilter

app, src, dst = sys.argv[1:4]
red_glows = "--red-glows" in sys.argv
img = Image.open(src)
w, h = img.size
small = img.convert("RGB").resize((max(1, w // 4), max(1, h // 4)), Image.BILINEAR)
gm = small.convert("L").filter(ImageFilter.GaussianBlur(2))
# Gain map value: 0 for dark parts, rising to 255 for the brightest (log-encoded boost, gamma 1).
gm = gm.point(lambda v: max(0, min(255, (v - 110) * 2)))
if red_glows:
    px, sp = gm.load(), small.load()
    for y in range(gm.size[1]):
        for x in range(gm.size[0]):
            r, g, b = sp[x, y]
            if r - g > 80:
                px[x, y] = 255
with tempfile.TemporaryDirectory() as d:
    gpath = os.path.join(d, "gm.jpg")
    gm.save(gpath, "JPEG", quality=95)
    cfg = os.path.join(d, "meta.cfg")
    with open(cfg, "w") as f:
        f.write("--maxContentBoost 4.0\n--minContentBoost 1.0\n--gamma 1.0\n--offsetSdr 0.015625\n"
                "--offsetHdr 0.015625\n--hdrCapacityMin 1.0\n--hdrCapacityMax 4.0\n--useBaseColorSpace 1\n")
    r = subprocess.run([app, "-m", "0", "-i", src, "-g", gpath, "-f", cfg, "-z", dst], capture_output=True, text=True)
    if r.returncode != 0 or not os.path.exists(dst):
        sys.exit("ultrahdr_app failed: " + r.stdout + r.stderr)
print("made", dst, os.path.getsize(dst), "bytes")
