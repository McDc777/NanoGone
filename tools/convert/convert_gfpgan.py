"""GFPGAN v1.4 to LiteRT: NHWC 512x512x3 in 0..1, out 0..1. Usage: convert_gfpgan.py weights.pth out.tflite"""
import sys, torch, numpy as np, litert_torch
sys.path.insert(0, __file__.rsplit("/", 1)[0])
from gfpgan_arch.gfpganv1_clean_arch import GFPGANv1Clean

net = GFPGANv1Clean(out_size=512, num_style_feat=512, channel_multiplier=2, decoder_load_path=None,
                    fix_decoder=False, num_mlp=8, input_is_latent=True, different_w=True, narrow=1, sft_half=True)
sd = torch.load(sys.argv[1], map_location="cpu")
net.load_state_dict(sd.get("params_ema", sd), strict=True)
net.eval()


class Wrap(torch.nn.Module):
    def __init__(s, n):
        super().__init__()
        s.n = n

    def forward(s, x):
        x = (x.permute(0, 3, 1, 2) * 2 - 1).contiguous()
        y = s.n(x, return_rgb=False, randomize_noise=False)[0]
        return ((y.clamp(-1, 1) + 1) / 2).permute(0, 2, 3, 1)


w = Wrap(net).eval()
x = torch.rand(1, 512, 512, 3)
with torch.no_grad():
    ref = w(x)
m = litert_torch.convert(w, (x,))
m.export(sys.argv[2])
print("gfpgan max diff", float(np.abs(np.asarray(m(x.numpy())) - ref.numpy()).max()))
