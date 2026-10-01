# NanoGone engine research (2026-10-01)

**Plain summary**
1. One AI engine runner, Google's **LiteRT**, can use the AI chip (NPU) on ALL five of Admin's devices: S23, S24, S25, S26 Ultra (Snapdragon) and Tab A11+ (MediaTek Dimensity 7300).
2. A proven repair model (LaMa) runs in about **35 to 45 thousandths of a second** on the newest Galaxy AI chips. That is so fast we can spend the spare power on extra quality.
3. A proven selection model (SAM 2.1 tiny) is already tuned for Snapdragon phones.
4. The newest research (2026) has "one-step" removers that also wipe the **shadow and reflection** of the removed thing. They are big (SDXL size), so they suit the Ultras best.
5. Tiny objects get lost when the photo is shrunk for the AI. Fix: run the AI on the part of the photo you are zoomed into, at full detail.
6. Blocked here for now: `huggingface.co` (model files) and `dl.google.com` (Android build kit). Facts below come from web search summaries.

---

## 1. Admin's devices

| Device | Chip | AI chip path in LiteRT | RAM |
|---|---|---|---|
| Galaxy S23 Ultra | Snapdragon 8 Gen 2 for Galaxy (SM8550) | Qualcomm QNN (supported) | 8 or 12 GB |
| Galaxy S24 Ultra | Snapdragon 8 Gen 3 for Galaxy (SM8650) | Qualcomm QNN (supported) | 12 GB |
| Galaxy S25 Ultra | Snapdragon 8 Elite for Galaxy (SM8750) | Qualcomm QNN (supported) | 12 or 16 GB |
| Galaxy S26 Ultra | Snapdragon 8 Elite Gen 5 for Galaxy (SM8850), all regions | Qualcomm QNN (supported) | 12 or 16 GB |
| Galaxy Tab A11+ 5G | MediaTek Dimensity 7300 (MT6878), Mali-G615 MC2 GPU | MediaTek NeuroPilot (supported) | 6 or 8 GB |

- The Tab A11+ is the weakest device and sets the floor. Everything must still work there.
- The Ultras shoot up to 200 MP (16320 x 12240). Fully opened, that is about 800 MB of memory, so photos must be opened and edited **in tiles**, never all at once.

## 2. Runner (runtime)

- **LiteRT** (formerly TensorFlow Lite), `CompiledModel` API: GPU and NPU acceleration, production NPU support for Qualcomm (QNN) and MediaTek (NeuroPilot), both ahead-of-time and on-device compilation.
- Qualcomm list includes SM8550, SM8650, SM8750, SM8850 (all four Ultras). MediaTek list includes Dimensity 7300 (MT6878).
- Fallback order on every device: NPU, then GPU, then CPU.
- Open item for planning: how the vendor NPU runtime libraries get into the APK (bundled vs on-device compile), and whether a known LiteRT issue (MediaTek AOT missing optimisation flags, LiteRT issue #6462) affects us.

## 3. Selection (what you tap)

| Model | Notes |
|---|---|
| SAM 2.1 Hiera-Tiny (Qualcomm AI Hub build) | 720p input, encoder 33.5M params (128 MB float), decoder 6.2M params. Point and box prompts. Best general choice. |
| EdgeTAM | SAM 2 quality, built for phones (RepViT backbone), 16 FPS on a 2023 phone. On Qualcomm AI Hub. Lighter option for the Tab. |
| Light HQ-SAM | Sharper mask edges than MobileSAM, about 41 FPS on desktop GPU. Good for hair-thin borders. |
| EdgeSAM, MobileSAM, EfficientSAM | Older light options. |

Design ideas:
- **Zoom-aware selection:** run the selection model on the zoomed-in view at full detail. A 5-pixel speck becomes a big target. This beats "cannot select small objects".
- **No drift:** the mask only changes where you tap (plus or minus taps, or a box). Nothing grows by itself.
- For removal, grow the final mask by a few pixels so no outline or halo is left behind.

## 4. Repair (filling the hole)

| Model | Size | Speed / notes |
|---|---|---|
| LaMa-Dilated (Qualcomm AI Hub) | 45.6M params, 174 MB float, 512 x 512 | 34 ms on 8 Elite Gen 5, 43 ms on 8 Elite (NPU). Proven for object removal. Weak on very big holes. |
| AOT-GAN (Qualcomm AI Hub) | 15.2M params, 58 MB, 512 x 512 | Lighter alternative. |
| MI-GAN (Picsart, ICCV 2023) | 5.95M params | Built for phones, beats LaMa on some tests with far less work. |
| RETHINED (WACV 2025) | Small CNN + patch copy | Real-time ultra-high-res inpainting on phones: fix at low res, then copy real high-res texture from the photo. Idea worth copying for sharpness. |
| SD 1.5 inpainting on NPU | about 1 GB | 5 to 10 s per 512 x 512 on 8 Gen 3 NPU at 20 steps (apps: Local Dream, Nightmare Mobile). Can invent unwanted things. |
| One-step effect-aware removers: TurboClear (2026, weights on Hugging Face), OSOR (2026), FlashClear (2026) | SDXL size (about 2.6B params UNet) | Remove the object AND its shadow or reflection in one pass. Under 1 s on a server GPU. Big for phones; the Ultras are the realistic target. |

Design ideas:
- **Full-detail crop repair:** cut a box around the hole from the original photo at full resolution, repair that box, paste back only the hole. Every other pixel stays the same.
- **Sharp-detail pass:** after repair, copy real fine texture and camera grain from around the hole, so the fill is not smoother than the rest of the photo.
- **"Don't copy from here":** hide the protected area from the model while it repairs, then put the real pixels back. Stops colours bleeding in.

## 5. Saving

- Same width, height and aspect ratio as the original. Photo details (date, place, camera) kept. No stamp, no watermark.
- JPEG originals: replace only the 8 x 8 pixel blocks that changed, keep every other block bit-for-bit (libjpeg coefficient copy, the same idea as `jpegtran -drop`). The rest of the photo is not re-compressed at all.
- Other formats (HEIC, PNG, WebP): to be decided in the design (lossless by default).
- Saved as a copy in a "NanoGone" folder (Admin's Question 3).

## 6. Engine approaches to offer Admin (after the feature question)

- **A. One fast engine:** SAM 2.1 + LaMa on the AI chip. Instant everywhere. Weak on big objects and shadows.
- **B. One deep engine:** a one-step diffusion remover for every job. Best on big objects and shadows, but each fix takes seconds, the model is several GB, and the Tab may not cope.
- **C. Both (likely recommendation):** fast engine for live previews and small or medium jobs; deep engine for big objects and shadows, on the Ultras (and on the Tab if it fits). All the shared tricks above in both.

## 7. More findings (after Admin chose "both brains")

Deep brain candidates (one step, removes shadows and reflections, SDXL size):
- **OSOR-SDXL** (ECCV 2026): built on `diffusers/stable-diffusion-xl-1.0-inpainting-0.1`. Code on GitHub (Zhouqm-Git/osor), weights on Hugging Face (QinmingZhou/OSOR, mirror eerie-road/osor-sdxl). Has an "alpha head" that copes with rough masks.
- **TurboClear** (2026): one SDXL UNet pass. Code Apache 2.0, weights on Hugging Face (JGuo666/TurboClear); needs the ObjectClear base model too (check its licence). Peak about 8 GB on a server GPU before any phone tuning.
- **FlashClear** (2026): few-step version of ObjectClear (SDXL + LoRA). Code status unclear.
- Plan: a bake-off on our test photos picks the winner. Memory need on the phone after tuning: roughly 3 GB, so the 12 and 16 GB Ultras are the main target. The Tab A11+ gets it only if tests show it fits.

Helpers for the version 1 extras:
- **Find distractions:** RF-DETR-Seg Nano to Large (Apache 2.0, Jan 2026), 33.6M to 36.2M params, instance masks for people and objects. Wires: Adobe "Automatic High Resolution Wire Segmentation and Removal" (CVPR 2023, WireSegHR dataset, code adobe-research/auto-wire-removal; check licence).
- **Shadow catcher:** instance shadow detection pairs each object with its shadow. FastInstShadow (2025, light FIS-D1 variant, fastest and most accurate on SOAP) or SSISv2 (TPAMI 2023). The deep brain also removes shadows by itself.
- **Text and logo eraser:** an offline text finder (PaddleOCR detection, Apache 2.0, or Google ML Kit bundled text recognition, works offline). Logos: tap with the magnet.

## 8. Enhance models (Admin added Enhance in design Part 2)

- **Bigger (fast):** Real-ESRGAN-x4plus (BSD-3). Qualcomm build: 54 ms per tile on Galaxy S24 NPU (TFLite, FP16), 49 ms with QNN. Works in tiles for any photo size.
- **Bigger and Sharper (deep, Ultras):** one-step diffusion super-resolution (OSEDiff, NeurIPS 2024, SD 2.1 base plus 8.5M LoRA). It was the official baseline of the NTIRE 2026 Mobile Real-World Super-Resolution challenge on a MediaTek Dimensity 8400. Newer: FiDeSR (CVPR 2026).
- **Sharper and cleaner (fast):** a real-world restoration network at the same size (candidates: Real-ESRGAN x1 style, SCUNet, NAFNet); bake-off picks.
- **Light and colour:** image-adaptive 3D LUT (under 600K params, about 2 ms for 4K on a desktop GPU), trained on MIT-Adobe FiveK expert retouches. Newer: SepLUT (ECCV 2022), SVDLUT (ICCV 2025).
- **Face fix:** GFPGAN v1.4 (Apache 2.0), with a strength slider so faces stay true to life. Not CodeFormer (non-commercial licence). Faces found first by a small face finder.

## Sources (web search, 2026-10-01)

- LiteRT Qualcomm NPU: https://developers.google.com/edge/litert/next/qualcomm
- LiteRT MediaTek NPU: https://developers.google.com/edge/litert/next/mediatek
- LiteRT issue #6462: https://github.com/google-ai-edge/LiteRT/issues/6462
- Tab A11+ specs: https://www.samsung.com/us/tablets/galaxy-tab-a11-plus/ , https://www.gsmarena.com (Dimensity 7300)
- S26 Ultra chip: https://www.sammobile.com/news/galaxy-s26-plus-ultra-snapdragon-exynos-region-split-explained/
- LaMa-Dilated: https://huggingface.co/qualcomm/LaMa-Dilated
- AOT-GAN: https://huggingface.co/qualcomm/AOT-GAN
- SAM 2 (Qualcomm): https://huggingface.co/qualcomm/Segment-Anything-Model-2
- EdgeTAM: https://aihub.qualcomm.com/models/edgetam , https://arxiv.org/html/2501.07256v1
- Light HQ-SAM: https://github.com/SysCV/sam-hq
- MI-GAN: https://openaccess.thecvf.com/content/ICCV2023/papers/Sargsyan_MI-GAN_A_Simple_Baseline_for_Image_Inpainting_on_Mobile_Devices_ICCV_2023_paper.pdf
- RETHINED: https://arxiv.org/abs/2503.14757
- SD on Snapdragon NPU: https://github.com/xororz/local-dream
- TurboClear: https://arxiv.org/abs/2608.01288 , https://huggingface.co/JGuo666/TurboClear
- OSOR: https://arxiv.org/html/2606.28094
- FlashClear: https://arxiv.org/html/2605.09003
- OSOR: https://arxiv.org/abs/2606.28094 , https://huggingface.co/eerie-road/osor-sdxl
- TurboClear code: https://github.com/GuoCalix/TurboClear
- RF-DETR: https://github.com/roboflow/rf-detr , https://blog.roboflow.com/rf-detr-segmentation/
- Wire removal: https://arxiv.org/abs/2304.00221
- FastInstShadow: https://www.researchgate.net/publication/389748319_FastInstShadow_A_Simple_Query-Based_Model_for_Instance_Shadow_Detection
- Real-ESRGAN Qualcomm: https://huggingface.co/qualcomm/Real-ESRGAN-x4plus
- NTIRE 2026 mobile SR: https://arxiv.org/abs/2604.17306
- OSEDiff: https://arxiv.org/abs/2406.08177
- GFPGAN: https://github.com/TencentARC/GFPGAN
- 3D LUT: https://arxiv.org/pdf/2508.16121 , https://huggingface.co/WeiChen80percent/image-adaptive-3dlut
- Magic Eraser small-object complaints: https://www.androidpolice.com/users-claim-magic-eraser-has-gotten-worse/
