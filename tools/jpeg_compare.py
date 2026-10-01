"""Compare two images pixel by pixel with Pillow. Usage: jpeg_compare.py a.jpg b.jpg"""
import sys, math
from PIL import Image
a = Image.open(sys.argv[1]); b = Image.open(sys.argv[2])
if a.size != b.size or a.mode != b.mode:
    print("different size or mode", a.size, b.size, a.mode, b.mode); sys.exit(1)
pa = a.tobytes(); pb = b.tobytes()
if pa == pb:
    print("identical pixels"); sys.exit(0)
diff = sum((x - y) ** 2 for x, y in zip(pa, pb)) / len(pa)
print("PSNR %.2f dB" % (10 * math.log10(255 * 255 / diff))); sys.exit(2)
