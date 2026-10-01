"""Compare the saved NanoGone copy with the original test photo."""
import sys
from PIL import Image
o = Image.open(sys.argv[1]); s = Image.open(sys.argv[2])
print("original", o.size, "saved", s.size, "same size:", o.size == s.size)
print("saved EXIF model:", s.getexif().get(0x0110), "date:", s.getexif().get(0x0132))
o = o.convert("RGB"); s = s.convert("RGB")
W, H = o.size
op = o.load(); sp = s.load()
diff = 0; box = [W, H, 0, 0]
for y in range(0, H):
    for x in range(0, W):
        if op[x, y] != sp[x, y]:
            diff += 1
            box = [min(box[0], x), min(box[1], y), max(box[2], x), max(box[3], y)]
print("pixels changed: %d (%.2f%% of photo) inside box %s" % (diff, 100.0 * diff / (W * H), box if diff else None))
r = sum(sp[x, y][0] - sp[x, y][1] for x in range(1920, 2080, 8) for y in range(1380, 1620, 8)) / (20 * 30)
print("redness left where the bin was (0 means gone, about 160 means still there): %.0f" % r)
