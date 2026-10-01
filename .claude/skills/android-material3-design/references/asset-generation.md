# Asset Generation — Icons, Illustration, Texture, 3D, UI

How to actually produce the assets promised in **A9 · The Asset Manifest**
(`anti-generic-doctrine.md` §2). Stock art, clip-art and un-restyled vault assets are banned by **S16**.
Every shipped image is either generated for this app or restyled into the app's palette.

## Lane order — CLOUD AND MCP FIRST (owner directive, 2026-08-12)

> **Try Lane 1 (cloud APIs + MCP servers) before Lane 2 (local ComfyUI), always.**
> Lane 2 is the fallback: use it when a Lane 1 service is unavailable, out of credit, refuses the job,
> or when the machine is offline. Both lanes are fully installed and working — this is a priority
> ordering, not a capability gap.

All credentials live in `~/.claude/settings.json` → `env`, so they are present as environment variables
in every session. **Never inline a key into a script, a workflow JSON, or a committed file — read the
env var.**

---

# Lane 1 — Cloud APIs and MCP servers (default)

Every row below was **re-verified on 2026-08-13** by a real artifact-producing call. Status is what the
account actually does today, not what the vendor's marketing page claims.

| Capability | Service | Env var / transport | Verified |
|-----------|---------|--------------------|----------|
| **UI screens + design systems** | **Google Stitch** (MCP, 15 tools) | `STITCH_API_KEY` → `https://stitch.googleapis.com/mcp` | ✅ 15 tools listed |
| **Raster image — 1st choice** | **NVIDIA `flux.1-dev`** | `NVIDIA_API_KEY` / `_2` → `ai.api.nvidia.com/v1/genai/black-forest-labs/flux.1-dev` | ✅ real 1024² PNG — see 1.5 |
| **Raster image — 2nd choice** | **Cloudflare Workers AI** (3 models) | `CLOUDFLARE_API_TOKEN` + `CLOUDFLARE_ACCOUNT_ID` | ✅ 3/3 returned images |
| **Raster image — 3rd choice** | **HuggingFace → fal-ai → FLUX.1-schnell** | `HF_TOKEN` | ✅ 200, image returned |
| **Faithful image editing** | **FLUX.1-Kontext** via HF | `HF_TOKEN` → `router.huggingface.co/fal-ai/fal-ai/flux-kontext/dev` | ✅ preserves composition, **does NOT upscale** |
| **3D models** | **Meshy.ai** (text→3D, image→3D) | `MESHY_API_KEY` | ✅ **4,980 credits** |
| **Design files / tokens** | **Penpot** (MCP) | `PENPOT_USER_TOKEN` | ✅ Connected |
| **Design files / tokens** | **Figma** (MCP) | existing token | ⚠ needs OAuth sign-in |
| **Text + vision-reading models** | **NVIDIA NIM** (OpenAI-compatible) | `NVIDIA_API_KEY` (+ `_2`) | ✅ — see 1.5 |
| **Text — fastest (~0.6 s)** | **Cerebras** (3 models) | `CEREBRAS_API_KEY` → `api.cerebras.ai/v1` | ✅ 200 |
| **Speaking voice (TTS)** | **Google AI Studio** `gemini-2.5-flash-preview-tts` | `GOOGLE_AI_STUDIO_KEY` | ✅ real PCM audio |
| **Asset hosting / file storage** | **Cloudflare R2** (S3 API) | `R2_ACCESS_KEY_ID`, `R2_SECRET_ACCESS_KEY`, `R2_S3_ENDPOINT` | ✅ read/write/delete |
| **OCR, doc/spec reading, text** | **Mistral** (55 models) | `MISTRAL_API_KEY` | ✅ 200 |
| **Gated model downloads** | HuggingFace Hub | `HF_TOKEN` (user `Mosimc`) | ✅ |
| **Real shipped-app reference** | **Mobbin** (MCP) | — | ⚠ needs sign-in |
| **Live API docs** | **context7**, **android-docs** (MCP) | — | ✅ |

⚠ **No cloud service here does faithful upscaling / detail enhancement** (the Enhancor-class job: raise
quality without altering a single element). Cloudflare has no super-resolution model at all; NVIDIA has
none; Google's editors are `limit: 0`; HF's `Unblur-Upscale` and `qwen-edit-skin` are listed but the
router returns *"Model not supported by provider."* **Upscaling is a Lane 2 job** — see §2.

⚠ **Music and video have NO working cloud source.** Google's `lyria-3` (music) and `veo-3.1` (video) are
both `limit: 0` on the free tier. Music falls back to local AUDIO_GOD; video is unavailable anywhere.

---

## 1.1 Google Stitch — the UI and design-system engine

**This is the highest-value tool in the lane for this skill.** It generates real app screens and real
design systems from text, and it accepts a `DESIGN.md` file as the source of a design system — which is
exactly what the Art Direction Gate produces.

The 15 verified tools:

| Group | Tools |
|-------|-------|
| Projects | `create_project` · `get_project` · `list_projects` · `delete_project` |
| Screens | `list_screens` · `get_screen` · **`generate_screen_from_text`** · `edit_screens` · **`generate_variants`** |
| Design systems | **`create_design_system`** · `update_design_system` · `list_design_systems` · **`apply_design_system`** · **`upload_design_md`** · `create_design_system_from_design_md` |

### The intended workflow — Stitch as the gate's execution arm

1. Run the **Art Direction Gate** (A1–A9) and write `ART_DIRECTION.md` as the doctrine requires.
2. `create_project` for the app.
3. **Feed the direction in**: copy `ART_DIRECTION.md` to `DESIGN.md` and call `upload_design_md` +
   `create_design_system_from_design_md`, or pass A4–A6 (palette, type pair, shape language) straight
   into `create_design_system`. The bespoke direction becomes Stitch's house style — **not** the other
   way round.
4. **Thin brief? Use `generate_variants`** to render the doctrine's three-distinct-directions
   requirement (§6) as real screens the owner can look at instead of describing in words. This is the
   single best use of Stitch in the whole pipeline.
5. `generate_screen_from_text` per screen, then `apply_design_system` so the whole set is coherent.
6. `get_screen` to pull the result down as reference for the Compose build.

### Hard rules

- **Stitch output is a design reference, never shipped code.** AndroidGo owns all Kotlin/Compose
  (Tier-2 code owner). Read Stitch's layout and visual decisions, then write idiomatic Compose.
- **Stitch does not get to choose the art direction.** The gate runs first, and Stitch is told what the
  direction is. Letting a generator decide the look is exactly the failure the doctrine bans — a
  generic Stitch default is as much a reject as a generic Compose default.
- Run the **Slop Detector** on Stitch output too. It is a generator, and generators regress to the mean.

> ⚠ **Known quirk:** `claude mcp list` reports stitch as `! Connected · tools fetch failed`. The server
> is a stateless MCP implementation and doesn't satisfy the CLI's probe handshake. A direct JSON-RPC
> `initialize` and `tools/list` both return HTTP 200 with all 15 tools, so the server is healthy. If the
> in-session tools don't appear, restart Claude Code; if they still don't, drive the endpoint over HTTP
> with the `X-Goog-Api-Key` header.

## 1.2 Image generation — three independent sources, in order

### 1.2a NVIDIA `flux.1-dev` — DEFAULT (best quality, native 1024²)

```
POST https://ai.api.nvidia.com/v1/genai/black-forest-labs/flux.1-dev
Authorization: Bearer $NVIDIA_API_KEY        # or $NVIDIA_API_KEY_2
Content-Type: application/json
Accept: application/json

{ "prompt": "<the art-directed prompt>",
  "width": 1024, "height": 1024,
  "steps": 25, "cfg_scale": 3.5, "seed": 7, "mode": "base" }
```

Returns `{ "artifacts": [ { "base64": "…" } ] }` — **base64, not a URL.** Decode and save to `art/`.

- ⚠ **`steps` must be ~25.** Sending `steps: 4` (the `schnell` value) returns **422** and looks broken.
- **Fixing `seed` gives byte-identical reruns** (sha256-verified) — this is the strongest style-lock for
  a set of assets that must look like one artist made them.
- `flux.1-schnell` on this account hangs — do not use it. See 1.5 for the full per-model map.

### 1.2b Cloudflare Workers AI — 2nd choice (3 models, fastest)

```
POST https://api.cloudflare.com/client/v4/accounts/$CLOUDFLARE_ACCOUNT_ID/ai/run/<model>
Authorization: Bearer $CLOUDFLARE_API_TOKEN
{ "prompt": "<prompt>" }
```

⚠ **Three different response shapes — sniff the magic bytes before JSON-parsing:**

| Model | Response |
|---|---|
| `@cf/black-forest-labs/flux-1-schnell` | JSON, base64 in `result.image` |
| `@cf/stabilityai/stable-diffusion-xl-base-1.0` | **raw PNG bytes** |
| `@cf/bytedance/stable-diffusion-xl-lightning` | **raw JPEG bytes** |

Also available there: `flux-2-dev`, `flux-2-klein-9b/4b`, `leonardo/phoenix-1.0`, `leonardo/lucid-origin`,
`dreamshaper-8-lcm`, plus `stable-diffusion-v1-5-inpainting` and `-img2img`.

### 1.2c HuggingFace → fal-ai → FLUX.1-schnell — 3rd choice

```
POST https://router.huggingface.co/fal-ai/fal-ai/flux/schnell
Authorization: Bearer $HF_TOKEN
{ "prompt": "<prompt>", "image_size": "square_hd", "num_inference_steps": 4 }
```

Returns `{ "images": [ { "url": "https://…" } ] }` — **a URL, not base64.** Download it into `art/`.

Notes from verification:
- Field is **`prompt`**, not `inputs`. Sending `inputs` returns HTTP 422.
- The older `hf-inference` provider route returns **410 deprecated** for FLUX — use the `fal-ai` route.

### 1.2d Faithful editing — FLUX.1-Kontext (keeps composition)

```
POST https://router.huggingface.co/fal-ai/fal-ai/flux-kontext/dev
Authorization: Bearer $HF_TOKEN
{ "image_url": "data:image/jpeg;base64,<…>", "prompt": "<what to change>" }
```

Verified: it preserves subject, angle, background and shadow faithfully. ⚠ **It returns the SAME
resolution it was given** — it re-renders, it does not enlarge. It is an editor, **not an upscaler**.
Only this exact route works; the `wavespeed` / `replicate` / LoRA paths all return
*"Model not supported by provider."*
- The HF account is on the free inference allowance (`canPay=False`). If a call ever returns 402 or a
  quota error, either add credits, regenerate the token with inference permission, or fall to Lane 2 —
  local FLUX produces the same model's output.
- Record the returned **`seed`** in `ART_DIRECTION.md` so a set can be extended later and still match.

## 1.3 3D models — Meshy.ai

**Verified: 4,980 credits available.** This is the default 3D path, ahead of local TRELLIS.

```
Authorization: Bearer $MESHY_API_KEY

POST https://api.meshy.ai/openapi/v2/text-to-3d      # text → mesh
POST https://api.meshy.ai/openapi/v1/image-to-3d     # image → mesh  (verified reachable)
GET  https://api.meshy.ai/openapi/v1/balance         # credits
GET  https://api.meshy.ai/openapi/v2/text-to-3d/<id> # poll a task
```

Meshy is **asynchronous**: the POST returns a task id, then poll until the task reports success and
exposes model URLs. Request **`glb`** output — SceneView loads `.glb` directly.

Best chain for a hero object: art-direct a concept image via 1.2, then **image→3D** through Meshy so the
mesh inherits the app's actual look, rather than text→3D which invents its own.

Verify the current request body against Meshy's docs (`context7` or their API reference) before the
first call of a session — parameter names change between their API versions.

## 1.4 Design files — Penpot and Figma

- **Penpot** (MCP, ✅ connected) — the owner's design workspace. Pull real files, boards and tokens
  instead of eyeballing a screenshot; push a direction in for review.
- **Figma** (MCP) — same role, ⚠ requires an OAuth sign-in before use. When the owner supplies a Figma
  URL, extract the actual tokens rather than guessing hex values.

Either way: **extracted tokens still pass the gate.** An imported palette full of baseline purple is
still a `B1` reject.

## 1.5 NVIDIA NIM — images ✅, text ✅, vision-reading ✅

🔴 **CORRECTION (2026-08-13).** This section previously read *"NVIDIA is not an image generator on this
account — route images to HuggingFace."* **That was wrong**, and a later session inherited it and used
HuggingFace for art it should have made here. The error: four picture models were tested, all four
genuinely fail, and the fifth — the one that works — was never tried.

Per-model status, each proven by a real call:

| Endpoint | Result |
|----------|--------|
| `…/genai/black-forest-labs/flux.1-dev` | ✅ **200, real 1024² PNG** (needs `steps: 25`) |
| `…/genai/black-forest-labs/flux.1-schnell` | ⚠ path valid on key 1, **hangs** on key `_2` |
| `…/genai/stabilityai/stable-diffusion-xl`, `…/stable-diffusion-3-medium` | ❌ 404 not on this account |
| `…/genai/shutterstock/edify-360-hdri` | ❌ 404 |
| `POST …/v1/chat/completions` `meta/llama-3.1-8b-instruct` | ✅ 200 |
| `nvidia/nemotron-3-ultra-550b-a55b` (550B) | ✅ 200, correct reasoning |
| `nvidia/nemotron-nano-12b-v2-vl`, `nvidia/llama-3.1-nemotron-nano-vl-8b-v1` | ✅ read an image correctly |
| `microsoft/phi-3-vision-128k-instruct`, `nvidia/vila` | ❌ 404 |
| `POST …/v1/embeddings` `nvidia/nv-embedqa-e5-v5` | ✅ 1024 dims |

**Read the status code as a map — this is the lesson that caused the error:**

| Code | Meaning | Action |
|---|---|---|
| `403` | key rejected | key is dead or wrong |
| `404` | model not on this account | genuinely unavailable, try another model |
| `422` | **model IS available, your body is wrong** | fix params — this is a green light |
| `429` + `limit: 0` | allowed but zero free quota | billing, not availability |

**Conclusions:**

- **NVIDIA IS the default image generator** (`flux.1-dev`, §1.2a) — best quality of the three sources.
- The catalogue lists more than the account can call. **Probe a model with a cheap 8-token call before
  building on it**, and read `404` vs `422` correctly before writing any model off.
- Base URL is OpenAI-compatible: `https://integrate.api.nvidia.com/v1`, `Authorization: Bearer
  $NVIDIA_API_KEY`. **Two keys are wired** (`NVIDIA_API_KEY`, `_2`) — both fully working; rotate to `_2`
  on a rate limit. ⚠ **`NVIDIA_API_KEY_3` was DELETED — it was dead** (403 on every real call).
- ⚠ **`GET /v1/models` returns 200 + all 102 models for a completely INVENTED key.** That false green is
  exactly how the dead key 3 got recorded as "valid". **Only a real inference call proves a key is alive.**
- Vision models here **read** images (describe a screenshot, check a layout). They don't make them.
  That is genuinely useful for the Slop Detector: hand a screenshot to a VLM and ask it to guess the
  concept line as an automated **stranger test** (`S1`).

## 1.6 Mistral — OCR, spec reading, text

**Verified: 200, 55 models.** `mistral-medium-latest` confirmed working.

Highest-value use here is the **`mistral-ocr-*` family** (`mistral-ocr-latest`, `-4-1`, `-3`…): read a
PDF brand guideline, a scanned spec, or a screenshot of a reference design and pull out the real
structure and text. Also has vision-capable chat models (`mistral-medium`, `magistral-small`) and
`pixtral`-class multimodal.

```
POST https://api.mistral.ai/v1/chat/completions
Authorization: Bearer $MISTRAL_API_KEY
```

## 1.7 Cloud lane limits

| Limit | Fallback |
|-------|----------|
| Legible text inside a generated image | Render text in Compose. Always better — translatable, scalable, accessible |
| True vector (SVG) output | Hand-author vector drawables (correct practice for marks anyway) |
| HF free inference allowance exhausted | Lane 2 local FLUX — same model |
| Meshy credits exhausted | Lane 2 TRELLIS 2 / Hunyuan3D-2.1 |
| Offline / no network | Lane 2 entirely, which needs neither |

---

# Lane 2 — Local generation (fallback; free, offline, no key)

Fully installed and boot-verified on this machine. Nothing here needs an account or a network.

## 2.0 Starting the engine

```bash
D:\AI\ComfyUI\Launch_ComfyUI.bat
```

Opens **http://127.0.0.1:8188**. The launcher already passes `--lowvram --reserve-vram 0.6`, which
offloads into the 48 GB of system RAM so full-precision models fit the 8 GB RTX 3070. Don't remove those
flags.

| Path | What's there |
|------|--------------|
| `D:\AI\ComfyUI\models\checkpoints\` | FLUX.1-schnell fp8 (16.1 GB), SDXL base, Juggernaut-XL v9, ACE-Step |
| `D:\AI\ComfyUI\workflows_godotgod\` | 22 ready workflows (+ README). **UI format** — convert to API format with the converter at `C:\Users\Admin\SuperCoconutIndentures\tools\art_factory\` if driving ComfyUI over HTTP |
| `D:\AI\AssetVault\` | ~15 GB all-CC0: 100 PolyHaven 4K HDRIs, 200 ambientCG 2K PBR materials, 19 Kenney packs, 9 Quaternius packs, 19 KayKit packs |
| `D:\AI\UniRig\` | Local auto-rigger (own venv) |
| `D:\AI\GodotDocs\` | Offline manual |

## 2.1 The local roster

| Model | Job | Settings that matter |
|-------|-----|---------------------|
| **FLUX.1-schnell** fp8 (Apache-2.0) | Fast text→image — icons, illustration, empty-state art, textures | Distilled: **steps 4, cfg 1.0**. Higher does nothing but burn time |
| **SDXL base** + **ControlNet Union ProMax** | Composition control — pose, depth, canny, layout | steps 25–30, cfg 6–8 |
| **Juggernaut-XL v9** (RunDiffusion Photo v2) | SDXL photo-realism finetune — **photographic** material (product shots, real textures) | steps 30–35, cfg 4–6, DPM++ 2M Karras |
| **IPAdapter Plus** + CLIP-ViT-H | **Style locking across a whole set** — see 2.3 | weight 0.6–0.85 |
| **4x-UltraSharp** | Upscale to xxxhdpi without softening edges | Run last, after background removal |
| **TRELLIS 2** (`microsoft/TRELLIS.2-4B`) | Image→3D, clean geometry | **`backend=sdpa`, `sparse_backend=xformers`** — flash-attn is not installed and will fail |
| **Hunyuan3D-2.1** | Image→3D with real **PBR textures** | Slower; hero assets only |
| **UniRig** | Auto-rig a generated character | Separate venv — full-path its `python.exe` |
| **rembg / u2net** (cached) | Background removal | Before upscaling |

**HF_TOKEN is now in the environment**, so gated Hub downloads work from the local venvs too — no more
license-gated model blocks. (Keep `HF_HUB_ENABLE_HF_TRANSFER=0` in venvs lacking `hf_transfer`.)

## 2.2 Prompt craft — beating *generated-image* slop (applies to BOTH lanes)

AI images have their own sameness tells, just as fatal as UI slop. **Banned in generated assets:**

| Banned look | Why |
|-------------|-----|
| Corporate-Memphis flat vector people (long limbs, no faces) | The most recognisable "AI/startup" image on earth |
| Glowing blue circuitry, hexagon grids, "digital brain", nodes-and-lines | The AI-app cliché |
| Pastel mesh-gradient blobs | Banned as backgrounds (B19) and as assets |
| Over-rendered glossy plastic 3D icons | The 2022 3D-icon-pack trend, instantly dated |
| Lens flares, bokeh sparkles, symmetric mandalas | Filler, no information |
| Human hands or faces at small size | Still mangled; a bad hand destroys the "professional team" read instantly |
| Any text rendered *inside* the image | Garbled by image models. Render text in Compose, always |

**Prompt like an art director, not a keyword stuffer.** The junk suffix — *"trending on artstation, 8k,
hyperdetailed, masterpiece, award-winning"* — produces the same homogenised look every time. Delete it.

Name four real things, mirroring the Concept Line method:

1. **A real medium** — riso print, cyanotype, gouache on cold-press, silkscreen, woodblock, technical pen
2. **A real process or era** — "two-colour offset, 1974", "1930s botanical plate", "Bauhaus poster"
3. **A real material** — brushed brass, unglazed stoneware, waxed canvas, cast concrete
4. **The app's actual palette**, as hex or as named colours from A4

```text
✅  A single brass dive-watch bezel, isolated on flat cream #EDE3CF, rendered as a
    1968 technical illustration in fine technical pen with flat spot-colour fills,
    two inks only: brass #C9962F and near-black #12100C. Orthographic, dead-centre,
    no perspective, no text, no background objects.

❌  beautiful modern watch icon, 3d render, glossy, professional, trending on
    artstation, 8k, hyperdetailed
```

Add **structural constraints**: `isolated on flat <hex>`, `orthographic`, `centred`, `no text`,
`two inks only`, `flat fills, no gradients`. Constraints are what make a *set* usable in a UI.

## 2.3 Style locking — the professional-team move

A set of 12 illustrations that each look slightly different is the loudest amateur signal in an app.

**Cloud method (Lane 1):** fix the **`seed`** returned by the FLUX call and keep one prompt template,
varying only the subject clause. Record seed + template in `ART_DIRECTION.md`.

**Local method (Lane 2), stronger:**

1. **Generate one style anchor.** Iterate a *single* image until it is exactly the app's look. Save as
   `_anchor.png` in the project's `art/`. This is the app's visual constitution.
2. **Feed the anchor to IPAdapter Plus** (weight 0.6–0.85) for every later image. Subject changes; style
   reference does not.
3. **Freeze the seed family**; vary only the subject text.
4. **Lock composition with ControlNet** where the frame matters.
5. **Colour-clamp** every output to the A4 palette so no stray hue enters the app.
6. **Audit the set side by side.** If one image looks like a guest, regenerate it.

Record anchor filename · IPAdapter weight · seed · prompt template in `ART_DIRECTION.md`, so a future
session extends the set instead of restarting it.

---

# Recipes (lane-agnostic)

## R1 · App icon (adaptive layers)

Geometry in `art-direction-engine.md` §9.2. Production:

1. **Design the mark, don't generate it.** Marks are geometric and must survive 48dp — hand-author the
   foreground as a **vector drawable** when the idea is geometric. Generation is for *exploring* twenty
   mark ideas fast, not for the final file.
2. If generating: `a single flat symbol, isolated on pure white, no text, orthographic, centred, two
   colours only` at 1024², then rembg → upscale → redraw as vector.
3. **Foreground:** 432 × 432 px transparent PNG (xxxhdpi) or vector; mark inside the **66 dp** safe
   circle of the 108 dp canvas.
4. **Background:** solid or gradient **vector**, full 108 dp bleed. Never a photo.
5. **Monochrome:** one-colour foreground, declared `<monochrome>` in `<adaptive-icon>` (Android 13+).
6. **Play Store:** 512 × 512 px, 32-bit PNG, **no alpha**.
7. **Test under all four OEM masks** (circle, squircle, rounded square, teardrop).

## R2 · Illustration set

1. List every slot up front: onboarding ×3, empty states ×N, error, success, achievement.
2. Lock the style (2.3), then generate the whole set **in one session** — never across sessions, or the
   style drifts.
3. Empty states get the most care. `B21` bans the grey-icon default: each one is a *scene from the
   borrowed world* plus one sentence of voice.
4. Export at 4×, downscale, convert to WebP lossless (R4).

## R3 · Texture and shader source

- Generate **seamless tiles** at 1024² (`seamless tileable texture, orthographic, flat lighting, no
  shadows, no objects`) for paper grain, brushed metal, plaster, wool, concrete, woodgrain.
- Or recolour a **CC0 PBR material** from `D:\AI\AssetVault\ambientCG_*` into the A4 palette — that
  counts as restyled, not stock.
- Feed the tile to an **AGSL `RuntimeShader`** (API 33+), or use it as a low-opacity overlay on
  `surface` for grain that reads as material rather than noise.
- Always ship the fallback chain: AGSL (33+) → `RenderEffect` blur (31+) → static gradient.

## R4 · Density export (ffmpeg 8.1.2, on PATH)

```bash
ffmpeg -i art_4x.png -vf scale=iw/2:ih/2       -sws_flags lanczos art_2x.png   # xhdpi
ffmpeg -i art_4x.png -vf scale=iw/1.333:ih/1.333 -sws_flags lanczos art_3x.png # xxhdpi
ffmpeg -i art_4x.png -c:v libwebp -lossless 1 -q:v 100 art_4x.webp             # ship WebP
```

Buckets: mdpi 1× · hdpi 1.5× · xhdpi 2× · xxhdpi 3× · xxxhdpi 4×. Ship **WebP lossless** for raster UI
art, **vector drawables** for anything geometric. `S17` fails a build missing xxhdpi/xxxhdpi.

## R5 · 3D hero object

1. **Concept image** — one object, isolated, orthographic, clean silhouette. Silhouette quality decides
   mesh quality more than anything else.
2. **Mesh:** Meshy **image→3D** (Lane 1, default) so the mesh inherits the app's look. Fallback:
   TRELLIS 2 for clean geometry, Hunyuan3D-2.1 when you need real PBR on the hero.
3. **Rig** with UniRig only if it animates.
4. **Light it** with a PolyHaven 4K HDRI from `D:\AI\AssetVault` as an IBL environment — this is what
   makes real-time 3D look expensive rather than plasticky.
5. **Load in Android** via **SceneView** (`io.github.sceneview:sceneview`), which takes `.glb` directly.
   Use raw Filament when you need custom material passes (transmission, clearcoat, iridescence).
6. **Budget it.** One hero object, one HDRI, one screen. 3D everywhere reads as a tech demo.
7. Verify shader/3D cost on a **physical device** — emulator GPU misrepresents it badly.

## R6 · Automated stranger test (S1)

Send a built screenshot to a vision model (NVIDIA VLM or Mistral vision) and ask: *"What real-world
object, craft or era does this interface evoke? Answer in one sentence."* If the answer doesn't reach
A1's reference, `S1` is a hit and the direction didn't land. Cheap, repeatable, and catches the failure
the doctrine cares most about.

---

## Colour, logo and palette intelligence

Design-side rules live in `art-direction-engine.md`: palette construction and the 60/30/10/1 split (§3),
chroma budget and contrast floors (§3.3–3.4), dark mode as a design (§6), logo construction and exact
Android icon geometry (§9). Tools:

| Task | Tool |
|------|------|
| Extract a palette from a reference or anchor | **Palette** (`androidx.palette:palette-ktx`), or sample by hand and record hex in A4 |
| Read a PDF/scanned brand guideline | **Mistral OCR** (1.6) |
| Pull real tokens from a design file | **Penpot** or **Figma** MCP (1.4) |
| Build a tonal scheme from a seed | `material-color-utilities` / Material Theme Builder — then **hand-override** where it went generic |
| Verify contrast | Compute against the *real* composited background, scrims and blurs included. Floors in §3.4 |
| Check the icon at every OEM mask | Android Studio's adaptive-icon preview + a 48dp squint test |

---

## Deliverables checklist

Before any build is handed over, the project's `art/` folder contains:

- [ ] `ART_DIRECTION.md` with A1–A9, plus the seed / anchor / prompt template so the set is extensible
- [ ] Adaptive icon: foreground (vector or 432² PNG) · background vector · **monochrome** layer
- [ ] Play Store icon 512² (no alpha); feature graphic 1024 × 500 if shipping
- [ ] Splash asset at the correct canvas (240 dp with background / 288 dp without)
- [ ] The complete illustration set, style-locked, palette-clamped
- [ ] Bespoke empty-state art for **every** empty state
- [ ] Texture tiles, seamless, plus the AGSL fallback chain implemented
- [ ] The 3D hero as `.glb` + its HDRI, if the manifest promised one
- [ ] Every raster asset as WebP lossless across xhdpi → xxxhdpi
- [ ] Which lane produced each asset, recorded — so a re-run is reproducible
