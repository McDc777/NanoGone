"""Make a 4000x3000 test photo with an obvious red bin in the middle and EXIF details."""
import random, sys
from PIL import Image, ImageDraw

random.seed(11)
W, H = 4000, 3000
img = Image.new("RGB", (W, H))
px = img.load()
for y in range(H):
    for x in range(0, W):
        if y < H * 0.45:   # sky
            base = (120 + y * 60 // H, 170 + y * 40 // H, 220)
        elif y < H * 0.6:  # sea
            base = (50, 110 + (x // 40) % 3 * 6, 150)
        else:              # sand
            base = (215, 192, 150)
        n = random.randint(-6, 6)
        px[x, y] = (base[0] + n, base[1] + n, base[2] + n)
d = ImageDraw.Draw(img)
d.rectangle([1900, 1350, 2100, 1650], fill=(200, 40, 30))   # the bin
d.rectangle([1880, 1320, 2120, 1350], fill=(40, 50, 45))    # lid
exif = Image.Exif()
exif[0x0110] = "NanoGone Test Cam"   # Model
exif[0x0132] = "2026:10:01 09:30:00" # DateTime
img.save(sys.argv[1], "JPEG", quality=92, subsampling=2, exif=exif)
print("made", sys.argv[1])
