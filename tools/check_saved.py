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


def gain_map(path):
    """The Ultra HDR gain map (second JPEG listed in the MPF index), or None."""
    import io, struct
    d = open(path, "rb").read()
    i = d.find(b"MPF\x00")
    if i < 0:
        return None
    t = i + 4
    e = "<" if d[t:t + 2] == b"II" else ">"
    ifd = struct.unpack(e + "I", d[t + 4:t + 8])[0]
    n = struct.unpack(e + "H", d[t + ifd:t + ifd + 2])[0]
    for k in range(n):
        tag, typ, cnt, val = struct.unpack(e + "HHII", d[t + ifd + 2 + 12 * k:t + ifd + 14 + 12 * k])
        if tag == 0xB002 and cnt >= 32:
            size, off = struct.unpack(e + "II", d[t + val + 16 + 4:t + val + 16 + 12])
            return Image.open(io.BytesIO(d[t + off:t + off + size])).convert("L")
    return None


go, gs = gain_map(sys.argv[1]), gain_map(sys.argv[2])
if go is None:
    print("HDR: the original has no gain map")
elif gs is None:
    print("HDR: gain map LOST in the saved copy")
else:
    def glow(g):  # mean gain over the bin's middle (gain map is 1/4 size)
        p = g.load()
        v = [p[x, y] for x in range(480, 520) for y in range(345, 405)]
        return sum(v) / len(v)
    print("HDR: gain map kept %s; glow where the bin was: original %.0f, saved %.0f (low means the glow went with the bin)"
          % (gs.size, glow(go), glow(gs)))
