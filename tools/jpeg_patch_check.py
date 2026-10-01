"""Check a patched JPEG against the expected picture with Pillow.
Usage: jpeg_patch_check.py original.jpg patched.jpg expected.rgb
Prints PSNR vs expected and how many pixels differ from the original outside the edited squares."""
import sys, math
from PIL import Image
o = Image.open(sys.argv[1]).convert("RGB"); p = Image.open(sys.argv[2]).convert("RGB")
exp = open(sys.argv[3], "rb").read()
assert o.size == p.size, "size changed"
pb = p.tobytes(); ob = o.tobytes()
mse = sum((a - b) ** 2 for a, b in zip(pb, exp)) / len(exp)
psnr = 99.0 if mse == 0 else 10 * math.log10(255 * 255 / mse)
w, h = o.size
diff = [i // 3 for i in range(len(ob)) if ob[i] != pb[i]]
xs = sorted({d % w for d in diff}); ys = sorted({d // w for d in diff})
box = (xs[0], ys[0], xs[-1] + 1, ys[-1] + 1) if diff else None
print("size %dx%d  PSNR vs expected %.1f dB  changed pixels %d inside box %s" % (w, h, psnr, len(set(diff)), box))
