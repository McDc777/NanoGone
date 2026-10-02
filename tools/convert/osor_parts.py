"""Split the OSOR (SDXL-Inpainting + LoRA, alpha head) one-step remover into phone-sized parts.

Every part is a plain module with NHWC float inputs and outputs, so the phone code stays simple.
The time step (400), prompt and size conditioning are fixed, so they are baked in as constants.
"""
import torch
import torch.nn as nn


def nchw(x):
    return x.permute(0, 3, 1, 2).contiguous()


def nhwc(x):
    return x.permute(0, 2, 3, 1).contiguous()


class Step(nn.Module):
    """One resnet (and its attention, if any) of a UNet block."""

    def __init__(self, resnet, attn, emb, ehs):
        super().__init__()
        self.resnet, self.attn = resnet, attn
        self.register_buffer("emb", emb)
        self.register_buffer("ehs", ehs)

    def forward(self, h):
        h = self.resnet(h, self.emb)
        if self.attn is not None:
            h = self.attn(h, encoder_hidden_states=self.ehs, return_dict=False)[0]
        return h


def plan(unet, emb, ehs):
    """The UNet as a flat list of ops. Each op: (kind, module). Kinds:
    'in' conv_in (pushes skip), 'down' step (pushes skip), 'ds' downsampler (pushes skip),
    'mid' mid block, 'up' step (pops skip first), 'us' upsampler, 'out' norm, act, conv."""
    ops = [("in", unet.conv_in)]
    for b in unet.down_blocks:
        attns = getattr(b, "attentions", None) or [None] * len(b.resnets)
        for r, a in zip(b.resnets, attns):
            ops.append(("down", Step(r, a, emb, ehs)))
        for d in b.downsamplers or []:
            ops.append(("ds", d))
    ops.append(("mid", MidStep(unet.mid_block, emb, ehs)))
    for b in unet.up_blocks:
        attns = getattr(b, "attentions", None) or [None] * len(b.resnets)
        for r, a in zip(b.resnets, attns):
            ops.append(("up", Step(r, a, emb, ehs)))
        for u in b.upsamplers or []:
            ops.append(("us", u))
    ops.append(("out", nn.Sequential(unet.conv_norm_out, unet.conv_act, unet.conv_out)))
    return ops


class MidStep(nn.Module):
    def __init__(self, mid, emb, ehs):
        super().__init__()
        self.mid = mid
        self.register_buffer("emb", emb)
        self.register_buffer("ehs", ehs)

    def forward(self, h):
        return self.mid(h, self.emb, encoder_hidden_states=self.ehs)


def run_ops(ops, h, skips):
    """Runs ops on h with a skip stack (list, changed in place). Used by the parts and the check."""
    for kind, m in ops:
        if kind == "up":
            h = torch.cat([h, skips.pop()], dim=1)
            h = m(h)
        elif kind in ("in", "down", "ds"):
            h = m(h)
            skips.append(h)
        else:
            h = m(h)
    return h


def stack_use(kinds):
    """How a run of ops uses the skip stack: (pops from earlier parts, new skips left, h is also the last new skip)."""
    local, need = 0, 0
    for k in kinds:
        if k in ("in", "down", "ds"):
            local += 1
        elif k == "up":
            if local:
                local -= 1
            else:
                need += 1
    return need, local, bool(kinds) and kinds[-1] in ("in", "down", "ds")


class Part(nn.Module):
    """A slice of the op list, NHWC in and out. Inputs: h, then the skips it pops (oldest first).
    Outputs: h (left out when it is the same tensor as the last new skip), then the new skips
    (oldest first). The first part takes (z_lq, mask, noise), packs them and also returns the
    noisy latent z last. The last part also takes (z, z_lq) and returns (z_out, alpha)."""

    def __init__(self, ops, first=None, last=None):
        super().__init__()
        self.kinds = [k for k, _ in ops]
        self.mods = nn.ModuleList([m for _, m in ops])
        self.pops, self.pushes, self.h_is_skip = stack_use(self.kinds)
        self.first = first  # (sqrt_acp, sqrt_1m_acp) or None
        self.last = last

    def forward(self, *args):
        if self.first is not None:
            z_lq, mask, noise = (nchw(a) for a in args[:3])
            a, b = self.first
            z = a * z_lq + b * noise
            h = torch.cat([z, mask, z_lq], dim=1)
            skips = []
        else:
            h = nchw(args[0])
            skips = [nchw(s) for s in args[1:1 + self.pops]]
            if self.last is not None:
                z, z_lq = nchw(args[1 + self.pops]), nchw(args[2 + self.pops])
        h = run_ops(list(zip(self.kinds, self.mods)), h, skips)
        if self.last is not None:
            a, b = self.last
            eps, logit = h[:, :4], h[:, 4:5]
            x0 = (z - b * eps) / a
            alpha = torch.sigmoid(logit)
            out = x0 * alpha + z_lq * (1 - alpha)
            return nhwc(out), nhwc(alpha)
        outs = ([] if self.h_is_skip else [nhwc(h)]) + [nhwc(s) for s in skips]
        if self.first is not None:
            outs.append(nhwc(z))  # the noisy latent, needed by the last part
        return tuple(outs)


def constants(unet, t, prompt_embeds, pooled, size):
    """Time and text conditioning, computed once (they never change)."""
    with torch.no_grad():
        tt = torch.tensor([t])
        emb = unet.time_embedding(unet.time_proj(tt).to(prompt_embeds.dtype))
        time_ids = torch.tensor([[size, size, 0, 0, size, size]], dtype=prompt_embeds.dtype)
        te = unet.add_time_proj(time_ids.flatten()).reshape(1, -1).to(prompt_embeds.dtype)
        emb = emb + unet.add_embedding(torch.cat([pooled, te], dim=-1))
    return emb, prompt_embeds


def split(ops, max_params):
    """Cut the op list into parts of at most max_params parameters (never splitting an op)."""
    groups, cur, n = [], [], 0
    for op in ops:
        p = sum(x.numel() for x in op[1].parameters())
        if cur and n + p > max_params:
            groups.append(cur)
            cur, n = [], 0
        cur.append(op)
        n += p
    groups.append(cur)
    return groups
