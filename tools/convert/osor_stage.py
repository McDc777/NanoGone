"""OSOR-SDXL-Inpainting to LiteRT for NanoGone's deep brain, in steps that each fit a 7 GB machine.

  osor_stage.py merge  BASE OSOR_WEIGHTS WORK   fixed prompt + LoRA merged into the UNet (fp16), one layer at a time
  osor_stage.py prep   WORK VAE                 test photos encoded: the first inputs of the chain
  osor_stage.py part I WORK OUT                 part I only: loads just its weights, runs it, converts and checks it
  osor_stage.py vae    VAE OUT                  the picture encoder and decoder
  osor_stage.py finish WORK VAE PARTS OUT       converted chain vs PyTorch on the test photos, brains.json

Run each step in its own process (memory is given back in between).
BASE  diffusers/stable-diffusion-xl-1.0-inpainting-0.1     VAE  madebyollin/sdxl-vae-fp16-fix
OSOR_FAKE=1 swaps in tiny random models of the same shape, to test this script in minutes.
"""
import gc, hashlib, json, os, sys, time
import numpy as np
import torch

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from osor_parts import Part, constants, nchw, nhwc, plan, split  # noqa: E402

FAKE = os.environ.get("OSOR_FAKE") == "1"
T = 400
SIZE, LAT, MAX_PARAMS = (64, 8, 400_000) if FAKE else (512, 64, 300_000_000)
PROMPT = "Remove the instance of object"
FAKE_CFG = dict(sample_size=8, in_channels=9, out_channels=4, down_block_types=["DownBlock2D", "CrossAttnDownBlock2D", "CrossAttnDownBlock2D"],
                up_block_types=["CrossAttnUpBlock2D", "CrossAttnUpBlock2D", "UpBlock2D"], block_out_channels=[32, 64, 128], layers_per_block=2,
                transformer_layers_per_block=[1, 2, 3], cross_attention_dim=64, attention_head_dim=[2, 4, 8], use_linear_projection=True,
                addition_embed_type="text_time", addition_time_embed_dim=8, projection_class_embeddings_input_dim=32 + 48)
t0 = time.time()


def log(*a):
    print(f"[{time.time() - t0:7.0f}s]", *a, flush=True)


def rss():
    try:
        with open("/proc/self/status") as f:
            for line in f:
                if line.startswith("VmRSS"):
                    return line.split()[1] + " kB"
    except OSError:
        pass
    return "?"


# ---------------------------------------------------------------- merge
def merge(base, weights, work):
    from diffusers import DDPMScheduler, UNet2DConditionModel
    from safetensors.torch import save_file
    os.makedirs(work, exist_ok=True)
    if FAKE:
        pe, pooled = torch.randn(1, 77, 64), torch.randn(1, 32)
    else:
        from transformers import CLIPTextModel, CLIPTextModelWithProjection, CLIPTokenizer
        with torch.no_grad():
            tok1 = CLIPTokenizer.from_pretrained(base, subfolder="tokenizer")
            enc1 = CLIPTextModel.from_pretrained(base, subfolder="text_encoder", variant="fp16", torch_dtype=torch.float32)
            ids = tok1(PROMPT, padding="max_length", max_length=tok1.model_max_length, truncation=True, return_tensors="pt").input_ids
            pe1 = enc1(ids, output_hidden_states=True).hidden_states[-2]
            del enc1
            tok2 = CLIPTokenizer.from_pretrained(base, subfolder="tokenizer_2")
            enc2 = CLIPTextModelWithProjection.from_pretrained(base, subfolder="text_encoder_2", variant="fp16", torch_dtype=torch.float32)
            ids = tok2(PROMPT, padding="max_length", max_length=tok2.model_max_length, truncation=True, return_tensors="pt").input_ids
            o2 = enc2(ids, output_hidden_states=True)
            pe = torch.cat([pe1, o2.hidden_states[-2]], dim=-1)
            pooled = o2.text_embeds
            del enc2, o2
        gc.collect()
    log("prompt", tuple(pe.shape), tuple(pooled.shape), "rss", rss())

    if FAKE:
        torch.manual_seed(0)
        unet = UNet2DConditionModel(**FAKE_CFG).to(torch.float16)
    else:
        unet = UNet2DConditionModel.from_pretrained(base, subfolder="unet", variant="fp16", torch_dtype=torch.float16)
    cfg = dict(unet.config)
    old = unet.conv_out
    unet.conv_out = torch.nn.Conv2d(old.in_channels, 5, old.kernel_size, old.stride, old.padding).to(torch.float16)
    log("unet loaded", "rss", rss())

    if FAKE:
        raw = fake_checkpoint(unet)
    else:
        try:
            raw = torch.load(weights, map_location="cpu", mmap=True, weights_only=True)
        except Exception as e:  # older checkpoint formats cannot be memory-mapped
            log("mmap load failed, full load:", e)
            raw = torch.load(weights, map_location="cpu")
    if "state_dict" in raw and isinstance(raw["state_dict"], dict):
        raw = raw["state_dict"]
    keys = list(raw.keys())
    dtypes = {}
    for k in keys:
        dtypes[str(raw[k].dtype)] = dtypes.get(str(raw[k].dtype), 0) + 1
    log("checkpoint:", len(keys), "tensors", dtypes, "sample", keys[:4], "rss", rss())

    def clean(k):
        for p in ("module.", "_orig_mod."):
            k = k.replace(p, "")
        return k[len("unet."):] if k.startswith("unet.") else k

    by = {clean(k): k for k in keys}
    merged, used = 0, set()
    with torch.no_grad():
        for ck, rk in by.items():
            for tag in (".lora_A.default.weight", ".lora_A.weight"):
                if not ck.endswith(tag):
                    continue
                prefix = ck[: -len(tag)]
                bk = prefix + tag.replace("lora_A", "lora_B")
                if bk not in by:
                    raise SystemExit(f"no lora_B for {ck}")
                a = raw[rk].float()
                b = raw[by[bk]].float()
                mod = unet.get_submodule(prefix)
                if a.dim() == 2:
                    delta = b @ a
                else:  # conv: A is the real kernel (r, cin, kh, kw), B a 1x1 (cout, r, 1, 1)
                    delta = torch.einsum("or,rikl->oikl", b[:, :, 0, 0], a)
                if delta.shape != mod.weight.shape:
                    raise SystemExit(f"shape mismatch at {prefix}: {tuple(delta.shape)} vs {tuple(mod.weight.shape)}")
                mod.weight.data = (mod.weight.data.float() + delta).to(torch.float16)  # lora_alpha == r, so scale 1
                used.update([ck, bk])
                merged += 1
        for name in ("conv_out.weight", "conv_out.bias"):
            for ck in (name, name.replace("conv_out.", "conv_out.base_layer.")):
                if ck in by:
                    getattr(unet.conv_out, name.split(".")[1]).data = raw[by[ck]].to(torch.float16)
                    used.add(ck)
        left = [k for k in by if k not in used]
    log("merged", merged, "LoRA layers; unused checkpoint keys:", len(left), left[:5], "rss", rss())
    if merged == 0 or "conv_out.weight" not in used and "conv_out.base_layer.weight" not in used:
        raise SystemExit("checkpoint did not match the model")
    if left:
        raise SystemExit(f"{len(left)} checkpoint tensors were not used, for example {left[:3]}")
    del raw
    gc.collect()

    sched = DDPMScheduler(beta_schedule="scaled_linear", beta_start=0.00085, beta_end=0.012) if FAKE else DDPMScheduler.from_pretrained(base, subfolder="scheduler")
    assert sched.config.prediction_type == "epsilon", sched.config.prediction_type
    acp = float(sched.alphas_cumprod[T])
    unet.time_embedding.float()
    unet.add_embedding.float()
    emb, ehs = constants(unet, T, pe.float(), pooled.float(), SIZE)
    sd = {k: v.contiguous() for k, v in unet.state_dict().items()}
    save_file(sd, os.path.join(work, "unet.safetensors"))
    json.dump({k: (list(v) if isinstance(v, tuple) else v) for k, v in cfg.items() if not k.startswith("_")},
              open(os.path.join(work, "unet_config.json"), "w"), default=str)
    torch.save({"emb": emb, "ehs": ehs, "acp": acp}, os.path.join(work, "constants.pt"))
    unet.eval().requires_grad_(False)
    count = len(make_parts(unet, emb.float(), ehs.float(), acp))
    json.dump({"count": count}, open(os.path.join(work, "count.json"), "w"))
    log("saved merged UNet", os.path.getsize(os.path.join(work, "unet.safetensors")) // 1_000_000, "MB; acp", acp, "; parts", count)


def fake_checkpoint(unet):
    """A checkpoint shaped like OSOR's (peft names, rank 4) for the tiny test model."""
    raw = {}
    targets = ("to_k", "to_q", "to_v", "to_out.0", "conv", "conv1", "conv2", "conv_shortcut", "proj_in", "proj_out", "ff.net.2", "ff.net.0.proj")
    for name, m in unet.named_modules():
        if not any(name == t or name.endswith("." + t) for t in targets) or name.startswith("conv_out"):
            continue
        r = 4
        if isinstance(m, torch.nn.Linear):
            raw[f"unet.{name}.lora_A.default.weight"] = torch.randn(r, m.in_features) * 0.05
            raw[f"unet.{name}.lora_B.default.weight"] = torch.randn(m.out_features, r) * 0.05
        elif isinstance(m, torch.nn.Conv2d):
            raw[f"unet.{name}.lora_A.default.weight"] = torch.randn(r, m.in_channels, *m.kernel_size) * 0.05
            raw[f"unet.{name}.lora_B.default.weight"] = torch.randn(m.out_channels, r, 1, 1) * 0.05
    raw["unet.conv_out.weight"] = torch.randn(5, unet.conv_out.in_channels, 3, 3) * 0.02
    raw["unet.conv_out.bias"] = torch.zeros(5)
    return raw


# ---------------------------------------------------------------- shared helpers
def meta_unet(work):
    """The merged UNet's structure with no weights loaded (weights come per part)."""
    from diffusers import UNet2DConditionModel
    cfg = json.load(open(os.path.join(work, "unet_config.json")))
    with torch.device("meta"):
        unet = UNet2DConditionModel.from_config(cfg)
        old = unet.conv_out
        unet.conv_out = torch.nn.Conv2d(old.in_channels, 5, old.kernel_size, old.stride, old.padding)
    unet.eval().requires_grad_(False)
    c = torch.load(os.path.join(work, "constants.pt"))
    return unet, c["emb"].float(), c["ehs"].float(), c["acp"]


def load_part(work, i):
    """Part i with only its own weights read from the merged file (fp16), plus how many parts there are."""
    from safetensors import safe_open
    unet, emb, ehs, acp = meta_unet(work)
    parts = make_parts(unet, emb, ehs, acp)
    p = parts[i]
    names = {id(m): n for n, m in unet.named_modules()}
    owned = [n for n in (names.get(id(m)) for m in p.modules()) if n]
    # Keep only the outermost owned modules (children are loaded with them).
    tops = [n for n in owned if not any(n != o and n.startswith(o + ".") for o in owned)]
    with safe_open(os.path.join(work, "unet.safetensors"), framework="pt") as f:
        keys = list(f.keys())
        for n in tops:
            sub = unet.get_submodule(n)
            sd = {k[len(n) + 1:]: f.get_tensor(k) for k in keys if k.startswith(n + ".")}
            sub.load_state_dict(sd, assign=True, strict=True)
    left = [n for n, t in list(p.named_parameters()) + list(p.named_buffers()) if t.is_meta]
    if left:
        raise SystemExit(f"part {i}: weights not loaded for {left[:3]}")
    p.to(torch.float16)
    return p, len(parts)


def load_vae(vae_dir):
    from diffusers import AutoencoderKL
    if FAKE:
        torch.manual_seed(1)
        return AutoencoderKL(block_out_channels=[32, 32, 32, 32], down_block_types=["DownEncoderBlock2D"] * 4,
                             up_block_types=["UpDecoderBlock2D"] * 4, latent_channels=4, norm_num_groups=32).eval().requires_grad_(False)
    return AutoencoderKL.from_pretrained(vae_dir, torch_dtype=torch.float32).eval().requires_grad_(False)


class VaeEnc(torch.nn.Module):
    def __init__(s, v):
        super().__init__()
        s.v = v

    def forward(s, x):  # NHWC 0..1 -> NHWC latent
        return nhwc(s.v.encode(nchw(x) * 2 - 1).latent_dist.mean * s.v.config.scaling_factor)


class VaeDec(torch.nn.Module):
    def __init__(s, v):
        super().__init__()
        s.v = v

    def forward(s, z):  # NHWC latent -> NHWC 0..1
        return nhwc(((s.v.decode(nchw(z) / s.v.config.scaling_factor).sample + 1) / 2).clamp(0, 1))


def test_inputs():
    from PIL import Image
    from skimage import data
    cases = []
    for name, im, box in [("astronaut", data.astronaut(), (330, 40, 470, 250)), ("coffee", data.coffee(), (150, 120, 260, 230))]:
        img = np.asarray(Image.fromarray(im).resize((SIZE, SIZE), Image.BICUBIC)).astype(np.float32) / 255
        sx, sy = SIZE / im.shape[1], SIZE / im.shape[0]
        m = np.zeros((SIZE, SIZE), np.float32)
        m[int(box[1] * sy):int(box[3] * sy), int(box[0] * sx):int(box[2] * sx)] = 1
        cases.append((name, torch.from_numpy(img)[None], torch.from_numpy(m)[None, :, :, None]))
    return cases


def mask_latent(m):
    return nhwc(torch.nn.functional.max_pool2d(nchw(m), 8, 8))


def noise():
    return torch.randn(1, LAT, LAT, 4, generator=torch.Generator().manual_seed(7))


def make_parts(unet, emb, ehs, acp):
    a, b = acp ** 0.5, (1 - acp) ** 0.5
    groups = split(plan(unet, emb, ehs), MAX_PARAMS)
    return [Part(g, (a, b) if i == 0 else None, (a, b) if i == len(groups) - 1 else None).eval() for i, g in enumerate(groups)]


def part_args(p, s):
    if p.first is not None:
        return s["first"]
    popped = s["stack"][len(s["stack"]) - p.pops:]
    return (s["h"], *popped, *((s["z"], s["z_lq"]) if p.last is not None else ()))


def part_done(p, s, outs):
    outs = list(outs) if isinstance(outs, (tuple, list)) else [outs]
    if p.last is not None:
        return outs
    if p.first is not None:
        s["z"] = outs.pop()
    if p.pops:
        del s["stack"][len(s["stack"]) - p.pops:]
    new = outs if p.h_is_skip else outs[1:]
    s["h"] = new[-1] if p.h_is_skip else outs[0]
    s["stack"].extend(new)
    return None


def run_torch(p, args):
    p.to(torch.float32)
    with torch.no_grad():
        out = p(*args)
    p.to(torch.float16)
    gc.collect()
    return out


def run_tfl(path, args):
    from ai_edge_litert.interpreter import Interpreter
    it = Interpreter(model_path=path, num_threads=os.cpu_count())
    sr = it.get_signature_runner()
    order = sorted(sr.get_input_details().keys(), key=lambda k: int(k.rsplit("_", 1)[-1]))
    out = sr(**{k: a.numpy() for k, a in zip(order, args)})
    outs = [torch.from_numpy(np.array(out[k])) for k in sorted(out.keys(), key=lambda k: int(k.rsplit("_", 1)[-1]))]
    info = {"input_order": order, "inputs": [(d["name"], [int(v) for v in d["shape"]]) for d in it.get_input_details()],
            "outputs": [(d["name"], [int(v) for v in d["shape"]]) for d in it.get_output_details()]}
    del sr, it
    gc.collect()
    return outs, info


def export(module, args, path, fp16):
    import litert_torch
    from litert_torch.generative.quantize import quant_recipes
    litert_torch.convert(module, args, quant_config=quant_recipes.full_fp16_recipe() if fp16 else None).export(path)


# ---------------------------------------------------------------- prep and part
def prep(work, vae_dir):
    vae = load_vae(vae_dir)
    for name, img, m in test_inputs():
        with torch.no_grad():
            z_lq = VaeEnc(vae)(img)
        s = {"first": (z_lq, mask_latent(m), noise()), "h": None, "stack": [], "z_lq": z_lq, "z": None, "final": None}
        torch.save(s, os.path.join(work, f"state_{name}_0.pt"))
    log("test photos encoded")


def part(i, work, out):
    os.makedirs(out, exist_ok=True)
    p, count = load_part(work, i)
    log(f"part {i} of {count} loaded ({sum(x.numel() for x in p.parameters()) / 1e6:.0f}M params), rss {rss()}")
    want0, args0 = None, None
    for name, _, _ in test_inputs():
        s = torch.load(os.path.join(work, f"state_{name}_{i}.pt"))
        args = part_args(p, s)
        want = run_torch(p, args)
        want = list(want) if isinstance(want, tuple) else [want]
        if want0 is None:
            want0, args0 = want, args
        r = part_done(p, s, want)
        if r is not None:
            s["final"] = r
        torch.save(s, os.path.join(work, f"state_{name}_{i + 1}.pt"))
        os.remove(os.path.join(work, f"state_{name}_{i}.pt")) if i > 0 else None
    p.to(torch.float32)
    path = os.path.join(out, f"osor_unet_{i}.tflite")
    export(p, args0, path, fp16=True)
    p.to(torch.float16)
    gc.collect()
    log(f"part {i} converted: {os.path.getsize(path) / 1e9:.2f} GB, rss {rss()}")
    got, info = run_tfl(path, args0)
    assert len(got) == len(want0), (len(got), len(want0))
    worst = max(float((g - w).abs().max()) for g, w in zip(got, want0))
    spec = {"file": f"osor_unet_{i}.tflite", "pops": p.pops, "pushes": p.pushes, "h_is_skip": p.h_is_skip,
            "first": p.first is not None, "last": p.last is not None, "params": sum(x.numel() for x in p.parameters()),
            "worst_diff": worst, **info}
    json.dump(spec, open(os.path.join(out, f"osor_unet_{i}.json"), "w"), indent=1)
    log(f"part {i}: worst difference vs PyTorch {worst:.4f}")


# ---------------------------------------------------------------- vae
def vae_stage(vae_dir, out):
    os.makedirs(out, exist_ok=True)
    vae = load_vae(vae_dir)
    img = test_inputs()[0][1]
    export(VaeEnc(vae), (img,), os.path.join(out, "osor_vae_enc.tflite"), fp16=False)
    export(VaeDec(vae), (torch.zeros(1, LAT, LAT, 4),), os.path.join(out, "osor_vae_dec.tflite"), fp16=False)
    log("vae converted")


# ---------------------------------------------------------------- finish
def finish(work, vae_dir, parts_dir, out):
    from PIL import Image
    os.makedirs(out, exist_ok=True)
    count = json.load(open(os.path.join(work, "count.json")))["count"]
    specs = [json.load(open(os.path.join(parts_dir, f"osor_unet_{i}.json"))) for i in range(count)]
    stubs = [type("P", (), {"first": 1 if sp["first"] else None, "last": 1 if sp["last"] else None,
                            "pops": sp["pops"], "h_is_skip": sp["h_is_skip"]}) for sp in specs]
    vae = load_vae(vae_dir)
    worst_img = 0.0
    for name, img, m in test_inputs():
        ref = torch.load(os.path.join(work, f"state_{name}_{count}.pt"))["final"]
        with torch.no_grad():
            ref_img = VaeDec(vae)(ref[0])[0].numpy()
        z_lq = run_tfl(os.path.join(parts_dir, "osor_vae_enc.tflite"), (img,))[0][0]
        s = {"first": (z_lq, mask_latent(m), noise()), "h": None, "stack": [], "z_lq": z_lq, "z": None}
        for p, sp in zip(stubs, specs):
            r = part_done(p, s, run_tfl(os.path.join(parts_dir, sp["file"]), part_args(p, s))[0])
        z_out, alpha = r
        tfl_img = run_tfl(os.path.join(parts_dir, "osor_vae_dec.tflite"), (z_out,))[0][0][0].numpy()
        src = img[0].numpy()
        row = np.concatenate([src * (1 - 0.5 * m[0].numpy()), ref_img, tfl_img], 1)
        Image.fromarray((row * 255).clip(0, 255).astype(np.uint8)).save(os.path.join(out, f"check_{name}.png"))
        Image.fromarray((alpha[0, :, :, 0].numpy() * 255).astype(np.uint8)).resize((SIZE, SIZE)).save(os.path.join(out, f"alpha_{name}.png"))
        d = float(np.abs(ref_img - tfl_img).mean() * 255)
        worst_img = max(worst_img, d)
        log(f"check {name}: phone files vs PyTorch mean difference {d:.2f} of 255")
    files = []
    for f in ["osor_vae_enc.tflite"] + [sp["file"] for sp in specs] + ["osor_vae_dec.tflite"]:
        h = hashlib.sha256()
        with open(os.path.join(parts_dir, f), "rb") as fh:
            for chunk in iter(lambda: fh.read(1 << 24), b""):
                h.update(chunk)
        files.append({"name": f, "size": os.path.getsize(os.path.join(parts_dir, f)), "sha256": h.hexdigest()})
    acp = torch.load(os.path.join(work, "constants.pt"))["acp"]
    manifest = {"version": 1, "size": SIZE, "latent": LAT, "t": T, "alphas_cumprod": acp, "prompt": PROMPT,
                "parts": specs, "files": files, "check_mean_diff": worst_img}
    json.dump(manifest, open(os.path.join(out, "brains.json"), "w"), indent=1)
    log("brains.json written:", len(files), "files,", sum(f["size"] for f in files) // 1_000_000, "MB")
    if worst_img > 8:
        raise SystemExit(f"converted chain differs too much from PyTorch ({worst_img:.1f} of 255)")


if __name__ == "__main__":
    cmd = sys.argv[1]
    if cmd == "merge":
        merge(*sys.argv[2:5])
    elif cmd == "prep":
        prep(*sys.argv[2:4])
    elif cmd == "part":
        part(int(sys.argv[2]), *sys.argv[3:5])
    elif cmd == "vae":
        vae_stage(*sys.argv[2:4])
    elif cmd == "finish":
        finish(*sys.argv[2:6])
    else:
        raise SystemExit(__doc__)
