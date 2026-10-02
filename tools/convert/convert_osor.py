"""OSOR-SDXL-Inpainting (phase 2, one step, alpha head) to LiteRT parts for NanoGone's deep brain.

Usage: convert_osor.py BASE_DIR VAE_DIR OSOR_WEIGHTS OUT_DIR

BASE_DIR  diffusers/stable-diffusion-xl-1.0-inpainting-0.1 (unet fp16, text encoders, tokenizers, scheduler)
VAE_DIR   madebyollin/sdxl-vae-fp16-fix (safe on phone graphics chips)
OUT_DIR   gets osor_vae_enc.tflite, osor_unet_N.tflite, osor_vae_dec.tflite, brains.json, check images

Steps: cache the fixed prompt, merge the LoRA into the UNet, split it into parts under the 2 GB
file limit (fp16 weights), convert the VAE, then run the converted chain on test photos and
compare with the PyTorch original.
"""
import gc, hashlib, json, os, sys, time
import numpy as np
import torch
import litert_torch
from litert_torch.generative.quantize import quant_recipes

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from osor_parts import Part, constants, nchw, nhwc, plan, split  # noqa: E402

BASE, VAE_DIR, WEIGHTS, OUT = sys.argv[1:5]
FAKE = os.environ.get("OSOR_FAKE") == "1"  # tiny random stand-ins, to test this script quickly
os.makedirs(OUT, exist_ok=True)
T = 400                       # OSOR's model timestep
SIZE = 512                    # working size on the phone
LAT = SIZE // 8
PROMPT = "Remove the instance of object"
MAX_PARAMS = 420_000_000      # keeps every part's file well under 2 GB
if FAKE:
    SIZE, LAT, MAX_PARAMS = 64, 8, 400_000
LORA_MODULES = ["to_k", "to_q", "to_v", "to_out.0", "conv", "conv1", "conv2", "conv_shortcut",
                "proj_in", "proj_out", "ff.net.2", "ff.net.0.proj"]
t0 = time.time()


def log(*a):
    print(f"[{time.time() - t0:7.0f}s]", *a, flush=True)


# 1. Fixed prompt (the text encoders are only needed here, never on the phone).
if FAKE:
    pe, pooled = torch.randn(1, 77, 64), torch.randn(1, 32)
else:
    from transformers import CLIPTextModel, CLIPTextModelWithProjection, CLIPTokenizer  # noqa: E402
    with torch.no_grad():
        tok1 = CLIPTokenizer.from_pretrained(BASE, subfolder="tokenizer")
        enc1 = CLIPTextModel.from_pretrained(BASE, subfolder="text_encoder", variant="fp16", torch_dtype=torch.float32)
        ids = tok1(PROMPT, padding="max_length", max_length=tok1.model_max_length, truncation=True, return_tensors="pt").input_ids
        pe1 = enc1(ids, output_hidden_states=True).hidden_states[-2]
        del enc1
        tok2 = CLIPTokenizer.from_pretrained(BASE, subfolder="tokenizer_2")
        enc2 = CLIPTextModelWithProjection.from_pretrained(BASE, subfolder="text_encoder_2", variant="fp16", torch_dtype=torch.float32)
        ids = tok2(PROMPT, padding="max_length", max_length=tok2.model_max_length, truncation=True, return_tensors="pt").input_ids
        o2 = enc2(ids, output_hidden_states=True)
        pe = torch.cat([pe1, o2.hidden_states[-2]], dim=-1)
        pooled = o2.text_embeds
        del enc2, o2
    gc.collect()
log("prompt", tuple(pe.shape), tuple(pooled.shape))

# 2. UNet + LoRA + 5-channel conv_out (alpha head), merged into plain weights.
from diffusers import AutoencoderKL, DDPMScheduler, UNet2DConditionModel  # noqa: E402
from peft import LoraConfig  # noqa: E402
from peft.tuners.tuners_utils import BaseTunerLayer  # noqa: E402

if FAKE:
    cfg = dict(sample_size=LAT, in_channels=9, out_channels=4, down_block_types=["DownBlock2D", "CrossAttnDownBlock2D", "CrossAttnDownBlock2D"],
               up_block_types=["CrossAttnUpBlock2D", "CrossAttnUpBlock2D", "UpBlock2D"], block_out_channels=[32, 64, 128], layers_per_block=2,
               transformer_layers_per_block=[1, 2, 3], cross_attention_dim=64, attention_head_dim=[2, 4, 8], use_linear_projection=True,
               addition_embed_type="text_time", addition_time_embed_dim=8, projection_class_embeddings_input_dim=32 + 48)
    unet = UNet2DConditionModel(**cfg).to(torch.float16)
else:
    unet = UNet2DConditionModel.from_pretrained(BASE, subfolder="unet", variant="fp16", torch_dtype=torch.float16)
unet.add_adapter(LoraConfig(r=256, lora_alpha=256, init_lora_weights="gaussian", target_modules=LORA_MODULES))
old = unet.conv_out
new = torch.nn.Conv2d(old.in_channels, 5, old.kernel_size, old.stride, old.padding).to(torch.float16)
unet.conv_out = new
if FAKE:  # a checkpoint shaped like OSOR's: "unet."-prefixed LoRA and conv_out weights
    raw = {"module.unet." + k: torch.randn_like(v.float()) * 0.01 for k, v in unet.state_dict().items() if "lora" in k or k.startswith("conv_out")}
else:
    raw = torch.load(WEIGHTS, map_location="cpu")
clean = {k.replace("module.", "").replace("_orig_mod.", ""): v for k, v in raw.items()}
clean = {(k[len("unet."):] if k.startswith("unet.") else k): v.to(torch.float16) for k, v in clean.items()}
del raw
res = unet.load_state_dict(clean, strict=False)
log("lora load: missing", len(res.missing_keys), "unexpected", len(res.unexpected_keys), res.unexpected_keys[:5])
assert not res.unexpected_keys, "checkpoint keys do not match"
assert any("lora" in k for k in clean) and "conv_out.weight" in clean
del clean
gc.collect()
n_merged = 0
for name, mod in list(unet.named_modules()):
    if isinstance(mod, BaseTunerLayer):
        base = mod.get_base_layer()
        with torch.no_grad():
            w = base.weight.data.float()
            mod.to(torch.float32)
            delta = mod.get_delta_weight("default").float()
            base.weight.data = (w + delta).to(torch.float16)
            mod.to(torch.float16)
        parent = unet.get_submodule(name.rsplit(".", 1)[0]) if "." in name else unet
        setattr(parent, name.rsplit(".", 1)[-1], base)
        n_merged += 1
unet.peft_config = {}
unet._hf_peft_config_loaded = False
gc.collect()
assert not any(isinstance(m, BaseTunerLayer) for m in unet.modules())
log("merged LoRA layers:", n_merged, "params %.0fM" % (sum(p.numel() for p in unet.parameters()) / 1e6))

sched = DDPMScheduler(beta_schedule="scaled_linear", beta_start=0.00085, beta_end=0.012) if FAKE else DDPMScheduler.from_pretrained(BASE, subfolder="scheduler")
assert sched.config.prediction_type == "epsilon", sched.config.prediction_type
acp = float(sched.alphas_cumprod[T])
A, B = acp ** 0.5, (1 - acp) ** 0.5
log("alphas_cumprod[400] =", acp)

unet.eval().requires_grad_(False)
unet.time_embedding.float()
unet.add_embedding.float()
emb, ehs = constants(unet, T, pe.float(), pooled.float(), SIZE)

ops = plan(unet, emb, ehs)
groups = split(ops, MAX_PARAMS)
log("parts:", len(groups), [len(g) for g in groups])

# 3. Convert parts one at a time (fp32 maths, fp16 weights).
if FAKE:
    vae = AutoencoderKL(block_out_channels=[32, 32, 32, 32], down_block_types=["DownEncoderBlock2D"] * 4, up_block_types=["UpDecoderBlock2D"] * 4, latent_channels=4, norm_num_groups=32).eval().requires_grad_(False)
else:
    vae = AutoencoderKL.from_pretrained(VAE_DIR, torch_dtype=torch.float32).eval().requires_grad_(False)
SCALE = vae.config.scaling_factor


class VaeEnc(torch.nn.Module):
    def __init__(s, v):
        super().__init__()
        s.v = v

    def forward(s, x):  # NHWC 0..1 -> NHWC latent
        return nhwc(s.v.encode(nchw(x) * 2 - 1).latent_dist.mean * SCALE)


class VaeDec(torch.nn.Module):
    def __init__(s, v):
        super().__init__()
        s.v = v

    def forward(s, z):  # NHWC latent -> NHWC 0..1
        return nhwc(((s.v.decode(nchw(z) / SCALE).sample + 1) / 2).clamp(0, 1))


def test_inputs():
    from PIL import Image
    from skimage import data
    cases = []
    for name, im, box in [("astronaut", data.astronaut(), (330, 40, 470, 250)),
                          ("coffee", data.coffee(), (150, 120, 260, 230))]:
        img = np.asarray(Image.fromarray(im).resize((SIZE, SIZE), Image.BICUBIC)).astype(np.float32) / 255
        sx, sy = SIZE / im.shape[1], SIZE / im.shape[0]
        m = np.zeros((SIZE, SIZE), np.float32)
        m[int(box[1] * sy):int(box[3] * sy), int(box[0] * sx):int(box[2] * sx)] = 1
        cases.append((name, torch.from_numpy(img)[None], torch.from_numpy(m)[None, :, :, None]))
    return cases


def mask_latent(m):  # max-pool by 8, as OSOR does
    return nhwc(torch.nn.functional.max_pool2d(nchw(m), 8, 8))


def export(module, args, path, fp16):
    q = quant_recipes.full_fp16_recipe() if fp16 else None
    litert_torch.convert(module, args, quant_config=q).export(path)
    return path


from ai_edge_litert.interpreter import Interpreter  # noqa: E402


def run_tfl(path, args):
    it = Interpreter(model_path=path, num_threads=os.cpu_count())
    sr = it.get_signature_runner()
    names = list(sr.get_input_details().keys())
    order = sorted(names, key=lambda k: int(k.rsplit("_", 1)[-1]))
    out = sr(**{k: a.numpy() for k, a in zip(order, args)})
    outs = [torch.from_numpy(np.array(out[k])) for k in sorted(out.keys(), key=lambda k: int(k.rsplit("_", 1)[-1]))]
    det_in = [(d["name"], [int(v) for v in d["shape"]]) for d in it.get_input_details()]
    det_out = [(d["name"], [int(v) for v in d["shape"]]) for d in it.get_output_details()]
    del sr, it
    gc.collect()
    return outs, order, det_in, det_out


manifest = {"version": 1, "size": SIZE, "latent": LAT, "t": T, "alphas_cumprod": acp, "prompt": PROMPT,
            "parts": [], "files": []}
cases = test_inputs()
noise = torch.randn(1, LAT, LAT, 4, generator=torch.Generator().manual_seed(7))

# Reference: the whole thing in PyTorch (fp16 weights cast to fp32 maths, op by op), saving every
# part's inputs so each converted part can be checked on its own.
with torch.no_grad():
    enc = VaeEnc(vae)
    path = export(enc, (cases[0][1],), f"{OUT}/osor_vae_enc.tflite", fp16=False)
    log("vae encoder converted", os.path.getsize(path))
    dec = VaeDec(vae)
    path = export(dec, (torch.zeros(1, LAT, LAT, 4),), f"{OUT}/osor_vae_dec.tflite", fp16=False)
    log("vae decoder converted", os.path.getsize(path))

state = {}
for name, img, m in cases:
    with torch.no_grad():
        z_lq = VaeEnc(vae)(img)
    # The PyTorch reference keeps h and the skip stack, exactly like the phone will.
    state[name] = {"first": (z_lq, mask_latent(m), noise), "h": None, "stack": [], "z_lq": z_lq, "z": None}


def part_args(part, s):
    if part.first is not None:
        return s["first"]
    popped = s["stack"][len(s["stack"]) - part.pops:]
    extra = (s["z"], s["z_lq"]) if part.last is not None else ()
    return (s["h"], *popped, *extra)


def part_done(part, s, outs):
    """Update h and the skip stack from a part's outputs. Returns the final result for the last part."""
    outs = list(outs) if isinstance(outs, (tuple, list)) else [outs]
    if part.last is not None:
        return outs
    if part.first is not None:
        s["z"] = outs.pop()
    if part.pops:
        del s["stack"][len(s["stack"]) - part.pops:]
    new = outs if part.h_is_skip else outs[1:]
    s["h"] = new[-1] if part.h_is_skip else outs[0]
    s["stack"].extend(new)
    return None


final = {}
for gi, g in enumerate(groups):
    first = (A, B) if gi == 0 else None
    last = (A, B) if gi == len(groups) - 1 else None
    part = Part(g, first, last).eval()
    part.to(torch.float32)
    path = export(part, part_args(part, state[cases[0][0]]), f"{OUT}/osor_unet_{gi}.tflite", fp16=True)
    n_par = sum(p.numel() for p in part.parameters())
    worst = 0.0
    for ci, (name, _, _) in enumerate(cases):
        s = state[name]
        args = part_args(part, s)
        with torch.no_grad():
            want = part(*args)
        want = list(want) if isinstance(want, tuple) else [want]
        if ci == 0:  # check each converted part on one photo; the full chain is checked on all below
            got, order, det_in, det_out = run_tfl(path, args)
            assert len(got) == len(want), (len(got), len(want))
            worst = max(worst, max(float((g_ - w_).abs().max()) for g_, w_ in zip(got, want)))
            del got
        r = part_done(part, s, want)
        if r is not None:
            final[name] = r
    log(f"part {gi}: {n_par / 1e6:.0f}M params, {os.path.getsize(path) / 1e9:.2f} GB, pops {part.pops}, "
        f"pushes {part.pushes}, h_is_skip {part.h_is_skip}, worst diff {worst:.4f}")
    manifest["parts"].append({"file": f"osor_unet_{gi}.tflite", "pops": part.pops, "pushes": part.pushes,
                              "h_is_skip": part.h_is_skip, "first": first is not None, "last": last is not None,
                              "inputs": det_in, "outputs": det_out, "input_order": order})
    part.to(torch.float16)
    del part
    gc.collect()

# 4. Full converted chain vs PyTorch, on the test photos, saved as pictures.
from PIL import Image  # noqa: E402
del unet, ops, groups
gc.collect()
for name, img, m in cases:
    ref = final[name][0]
    with torch.no_grad():
        ref_img = VaeDec(vae)(ref)[0].numpy()
    z_lq = run_tfl(f"{OUT}/osor_vae_enc.tflite", (img,))[0][0]
    s = {"first": (z_lq, mask_latent(m), noise), "h": None, "stack": [], "z_lq": z_lq, "z": None}
    for spec in manifest["parts"]:
        stub = type("P", (), {"first": 1 if spec["first"] else None, "last": 1 if spec["last"] else None,
                              "pops": spec["pops"], "h_is_skip": spec["h_is_skip"]})
        outs = run_tfl(f"{OUT}/{spec['file']}", part_args(stub, s))[0]
        r = part_done(stub, s, outs)
    z_out, alpha = r
    tfl_img = run_tfl(f"{OUT}/osor_vae_dec.tflite", (z_out,))[0][0][0].numpy()
    src = img[0].numpy()
    row = np.concatenate([src * (1 - 0.5 * m[0].numpy()), ref_img, tfl_img], 1)
    Image.fromarray((row * 255).clip(0, 255).astype(np.uint8)).save(f"{OUT}/check_{name}.png")
    a_img = alpha[0, :, :, 0].numpy()
    Image.fromarray((a_img * 255).astype(np.uint8)).resize((SIZE, SIZE)).save(f"{OUT}/alpha_{name}.png")
    log(f"check {name}: converted vs PyTorch mean abs diff {np.abs(ref_img - tfl_img).mean() * 255:.2f} /255")

for f in sorted(os.listdir(OUT)):
    if f.endswith(".tflite"):
        h = hashlib.sha256()
        with open(f"{OUT}/{f}", "rb") as fh:
            for chunk in iter(lambda: fh.read(1 << 24), b""):
                h.update(chunk)
        manifest["files"].append({"name": f, "size": os.path.getsize(f"{OUT}/{f}"), "sha256": h.hexdigest()})
manifest["files"] = [x for x in manifest["files"] if x["name"].startswith("osor_")]
with open(f"{OUT}/brains.json", "w") as fh:
    json.dump(manifest, fh, indent=1)
log("done")
