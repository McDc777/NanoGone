"""Build a tiny fake deep-brain pack plus expected answers, for the phone self-test.

Usage: OSOR_FAKE=1 python tools/convert/osor_stage.py merge|vae|part N|finish ...   (tiny random parts, see that file)
       python tools/make_deep_selftest.py OUT tools/testdata/deep_selftest
The phone runs the same chain (encoder, parts with the skip stack, decoder) and compares with
selftest_out.bin. This checks the plumbing (order of inputs and outputs, the stack), not quality.
"""
import json, os, shutil, sys
import numpy as np
from ai_edge_litert.interpreter import Interpreter

src, dst = sys.argv[1:3]
os.makedirs(dst, exist_ok=True)
m = json.load(open(os.path.join(src, "brains.json")))
S, L = m["size"], m["latent"]
rng = np.random.default_rng(3)
img = rng.random((1, S, S, 3), dtype=np.float32)
mask = np.zeros((1, S, S, 1), np.float32)
mask[:, S // 4:S // 2, S // 3:2 * S // 3] = 1
noise = rng.standard_normal((1, L, L, 4)).astype(np.float32)


def run(path, args):
    it = Interpreter(model_path=path)
    sr = it.get_signature_runner()
    keys = sorted(sr.get_input_details().keys(), key=lambda k: int(k.rsplit("_", 1)[-1]))
    out = sr(**dict(zip(keys, args)))
    return [np.array(out[k]) for k in sorted(out.keys(), key=lambda k: int(k.rsplit("_", 1)[-1]))]


z_lq = run(os.path.join(src, "osor_vae_enc.tflite"), [img])[0]
ml = mask.reshape(1, L, S // L, L, S // L, 1).max(axis=(2, 4))
h, stack, z, res = None, [], None, None
for p in m["parts"]:
    if p["first"]:
        args = [z_lq, ml, noise]
    else:
        popped = stack[len(stack) - p["pops"]:] if p["pops"] else []
        args = [h] + popped + ([z, z_lq] if p["last"] else [])
    outs = run(os.path.join(src, p["file"]), args)
    if p["last"]:
        res = outs
        break
    if p["first"]:
        z = outs.pop()
    if p["pops"]:
        del stack[len(stack) - p["pops"]:]
    new = outs if p["h_is_skip"] else outs[1:]
    h = new[-1] if p["h_is_skip"] else outs[0]
    stack.extend(new)
z_out, alpha = res
dec = run(os.path.join(src, "osor_vae_dec.tflite"), [z_out])[0]

for f in os.listdir(src):
    if f.endswith(".tflite") or f == "brains.json":
        shutil.copy(os.path.join(src, f), dst)
np.concatenate([img.ravel(), mask.ravel(), noise.ravel()]).astype("<f4").tofile(os.path.join(dst, "selftest_in.bin"))
np.concatenate([dec.ravel(), alpha.ravel()]).astype("<f4").tofile(os.path.join(dst, "selftest_out.bin"))
print("selftest pack:", sorted(os.listdir(dst)), "image", dec.shape, "alpha", alpha.shape)
