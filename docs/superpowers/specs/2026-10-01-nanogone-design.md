# NanoGone design (spec)

**Plain summary**
1. NanoGone is a free Android app that removes things from photos, fully on the phone, offline.
2. You tap, loop or brush what you want gone. Helpers find distractions, text, and shadows for you.
3. A fast brain fills the hole in under a second; a deep brain upgrades big jobs and wipes shadows.
4. Enhance makes the whole photo sharper, bigger, better lit, with clearer faces.
5. Saves a copy in the NanoGone album: same size, no stamp, untouched pixels stay untouched.
6. The look is "Dawn Mist": frosted glass over soft dawn colours; a warm mist lifts away when you remove.

Status: approved by Admin in parts (journey, picking, filling, Enhance, saving) on 2026-10-01. Look D and all remaining choices decided under Admin's standing order "pick all of the best options and just get it done". Decision log: `docs/BRAINSTORM-STATE.md`. Model research: `docs/research/2026-10-01-engine-research.md`.

---

## 1. Goal and success criteria

Beat Google Photos (Magic Eraser) and Samsung Gallery (Object Eraser) on every complaint Admin listed:

| # | Complaint | NanoGone answer | How we prove it |
|---|---|---|---|
| 1 | Output smaller, aspect ratio changes | Output width, height and orientation always equal the original (Bigger is the only, deliberate exception) | Automated test on every save path |
| 2 | Colours bleed in from nearby subjects | Protect brush hides areas from the model; colour, light and grain matching at the seam | Bake-off photo set, visual review |
| 3 | Slow, many limits | Fast brain under 1 s on all devices; AI chip first; no size limits up to 200 MP | Speed log screen on device |
| 4 | Cannot select very small objects | Zoom-aware selection at full detail; spot tap; brush thins with zoom | Test photos with specks of 3 to 10 px |
| 5 | Magnet selection drifts | Each tap only changes the connected piece under it; smart loop is clipped to the drawn line | Unit tests on mask merge rules |
| 6 | "AI generated" stamp | No visible stamp and no AI label in metadata | Byte check of saved files |

Devices: Galaxy S23 Ultra (Snapdragon 8 Gen 2), S24 Ultra (8 Gen 3), S25 Ultra (8 Elite), S26 Ultra (8 Elite Gen 5), Galaxy Tab A11+ 5G (Dimensity 7300, 6 or 8 GB). Android 14 or newer on all; minSdk 31, targetSdk and compileSdk 36. Use all the power each device has.

Deliverable: a debug APK Admin installs and tests. Android only, never Apple platforms.

## 2. The journey

1. **Open:** Share or Edit from Samsung Gallery or Google Photos (ACTION_SEND, ACTION_EDIT for `image/*`), or the in-app picker (Android Photo Picker).
2. **Pick:** tap, loop, brush, spot, or a helper (Find distractions, Find text). Shadow catcher adds shadows and reflections automatically.
3. **Remove:** fast brain result appears in under 1 s; deep brain upgrades it in place when the job is big or has a shadow.
4. **Check:** press and hold the photo for the original; undo and redo every step; remove more things.
5. **Enhance (optional):** one panel, four switches, a strength slider, a split before/after.
6. **Save:** choose Top-quality JPEG or Lossless PNG (each shows its file size, last pick remembered). A copy lands in `Pictures/NanoGone`. Share straight from the done screen.

The session autosaves its edit history, so closing the app mid-edit reopens where you left off.

## 3. Picking what to remove (selection)

- **Magic tap:** SAM 2.1 Hiera-Tiny (point and box prompts). A plus tap adds only the connected region containing the tap; a minus tap removes only the connected region containing the tap. The rest of the mask never changes, so it cannot drift.
- **Smart loop:** draw around the object; the loop's box plus inside points prompt the model; the result is intersected with the loop. If the model is unsure (low score), the loop area itself is used.
- **Fine brush:** width follows zoom so it stays fine on screen; an eraser mode removes from the mask.
- **Spot tap:** one tap finds a small blob (dust, speck, blemish) around the finger by local contrast and selects it with a small margin.
- **Zoom-aware:** when zoomed in, the selection model runs on the visible area at full detail, not on a shrunken whole photo.
- **Magnifier:** a bubble above the finger while drawing.
- **Find distractions:** RF-DETR-Seg (Apache 2.0) finds people and objects; the main subject is the largest, sharpest, most central people group; everything else that is small, at the edge or out of focus is marked. A wire finder marks power lines. Each mark can be ticked or unticked; "Remove all marked".
- **Find text:** an offline text finder (PaddleOCR detection, Apache 2.0) marks writing, date stamps and watermarks. Logos use the magic tap.
- **Shadow catcher:** instance shadow detection (FastInstShadow class) adds the selected object's shadow and reflection, shown with a lighter mist so you can see and drop it.
- **Protect brush ("don't copy from here"):** painted areas are hidden from the repair models and then put back exactly.
- The final mask is grown by a few pixels (scaled to object size) so no outline or halo stays.

## 4. Filling the hole (repair)

- **Full-detail crop:** decode only a box around the mask (plus context margin) from the original at native resolution; repair that box; paste back only masked pixels with a soft seam. Pixels outside the mask are never changed.
- **Fast brain:** LaMa-Dilated class model (Qualcomm AI Hub build, 512 px input) on the AI chip; for bigger crops it runs on a downscaled crop, then the detail pass restores sharpness.
- **Deep brain:** one-step effect-aware SDXL-class remover (OSOR-SDXL or TurboClear; a bake-off on our test set picks one). Runs automatically when the mask covers more than about 4% of the crop's model input or a shadow or reflection is included; also on "Try deeper". Ultras only, unless tests show the Tab can run it.
- **Detail and grain pass:** after either brain, high-frequency detail and camera grain are copied from the area around the hole (patch matching guided by the fill), so the fill is never softer than the photo.
- **Seam blending:** colour and light are matched across the edge (gradient-domain blend).
- **Try again:** a fresh variation (different context box for the fast brain, different seed for the deep brain).
- **Huge photos:** everything works in tiles; up to 200 MP never needs the whole photo in memory.

## 5. Enhance

Runs on the whole photo, after removals, in tiles. Each switch has a fast version on every device; on the Ultras, Sharper and Bigger also get a deep version.

| Switch | Fast model | Deep model (Ultras) |
|---|---|---|
| Sharper and cleaner | Real-world restoration network at the same size (bake-off: SCUNet, NAFNet, Real-ESRGAN x1 style) | One-step diffusion restoration (OSEDiff class) |
| Bigger (2x, 4x) | Real-ESRGAN-x4plus (Qualcomm build) | One-step diffusion super-resolution (OSEDiff class) |
| Light and colour | Image-adaptive 3D LUT (MIT-Adobe FiveK expert style) | Same |
| Face fix | GFPGAN v1.4 (Apache 2.0) on faces found by a face finder, blended by the strength slider | Same |

Bigger is limited so the result stays at or under 200 MP.

## 6. Saving

- Always a new file in `Pictures/NanoGone` via MediaStore, named `<original name>_NanoGone.<ext>`. The original is opened read-only.
- **Top-quality JPEG:**
  - Original is JPEG and only removals were done: copy the original's DCT coefficients and re-encode only the 8x8 blocks (MCUs) that touch the mask, with the original's quantization tables and sampling (libjpeg-turbo, coefficient level). Every other block stays bit-identical.
  - Otherwise: quality 100, 4:4:4 chroma.
- **Lossless PNG:** the full result, lossless.
- **Metadata:** keep EXIF (date, place, camera, orientation) and XMP. Regenerate the EXIF thumbnail. Do not add AI or edit labels.
- **Samsung extras:** Ultra HDR gain map: inpaint the gain map in the same region and keep it. Motion-photo video and depth or other trailer data: dropped from the copy.
- Width, height and orientation equal the original unless Bigger was used.

## 7. Look: "Dawn Mist" (owner direction)

Admin's words (owner override, recorded verbatim): "Cloudy glass neomorphism, soft pastel dawn gradients, luminous frosted acrylic. A warm morning mist blurs over the selected area and evaporates smoothly into clear air when you remove it."

Art Direction Gate answers (copied into `ART_DIRECTION.md` before the first composable):
- **A1 Concept:** A still lake at sunrise: mist lifting off the water, frosted acrylic catching the first pink and gold light, and the haze burning off as the sun climbs.
- **A2 Anti-reference:** not a stock pastel blob background, not Material purple, not a neon-edged glass kit, not iOS Control Center. Glass never covers the photo.
- **A3 Signature move:** whatever you select is wrapped in warm, glowing morning mist that blurs it; tap Remove and the mist rises and evaporates into clear air, leaving the clean photo.
- **A4 Palette (light canonical):** lake blue `#D6E6F2`, rose haze `#F4D3DC`, peach mist `#FBE3D6`, first gold `#FFE6A6`, pearl surface `#F7F1EC`, dusk plum text `#2E2838`, secondary text `#6E6478`, sunrise coral accent `#FF8A5C` (primary action only), error `#D64545`. **Dark ("blue hour before dawn"):** night blue `#121726`, surface `#1A2032`, horizon rose `#3A2A3A`, text `#EEE8F0`, accent coral desaturated `#E98A66`. Dynamic colour offered as a setting, never the identity.
- **A5 Type:** display Syne 600 to 800 (tracking -2% to -3%), text Instrument Sans 400, 500, 600. Both bundled in `res/font` (offline).
- **A6 Shape:** soft continuous squircles; floating panes 28 dp; tool buttons 18 dp; the photo 20 dp. One exception: the Remove button is a full pill.
- **A7 Motion:** one app spring `dampingRatio 0.78, stiffness 320`; tool stagger 30 ms; the mist (drift loop, 6 s) and its evaporation (1.2 s rise and fade, blur 6 dp to 0); the photo opens from the gallery thumbnail with a shared-element transition; predictive back; reduced-motion collapses to a fade.
- **A8 Depth:** light comes from the sunrise, top left. Three layers: dawn gradient ground, the photo, floating frosted panes (top bar, tool tray, sheets) with soft neumorphic tool buttons. Glass on two surface types only: floating panes and the selection mist. RenderEffect blur (API 31+, all target devices).
- **A9 Assets:** adaptive icon (a sun disc half behind a mist band, monochrome layer), splash, empty-state art (a misty lake), all generated per `asset-generation.md` and checked by the Slop Detector.

Reference mockup: `docs/design/nanogone-looks.html`.

## 8. Under the hood

Kotlin, Jetpack Compose, Material 3 (tokens), coroutines and Flow, Hilt. Modules:

| Module | Job |
|---|---|
| `app` | Activity, navigation, intents, theme |
| `feature-editor` | Editor screen, tools, gestures, history (undo, redo, autosave) |
| `feature-enhance` | Enhance panel and pipeline |
| `core-imaging` (Kotlin + C++) | Tiled decode, crops, masks, seam blend, detail pass, JPEG block patcher (libjpeg-turbo), PNG, EXIF, Samsung trailer handling |
| `core-ai` | LiteRT `CompiledModel` runners with NPU, then GPU, then CPU fallback; model registry; warm-up |
| `core-models` | Model files and the brain pack loader |
| `core-design` | Dawn Mist theme, glass, mist effect, components |

Models: everything except the deep brains ships inside the APK. The deep brains (several GB) form a **brain pack** added once: the app downloads it over Wi-Fi from the NanoGone model page, or imports it from a file on the phone. After that, all work is offline.

Full power: AI chip first; image work on all CPU cores and the GPU; screen kept awake while working; Android performance hints on worker threads; brains loaded and warmed when the app opens; compiled NPU models cached so later launches start instantly. No self-imposed speed caps; the app only slows if Android itself throttles for heat.

## 9. Safety nets

- AI chip unavailable or a model fails: fall back to GPU, then CPU, silently.
- Deep brain missing or out of memory: use the fast brain plus detail pass and say so in one plain line.
- Huge photos: tiles everywhere; never decode a full 200 MP bitmap.
- The original is never written to.
- Unsupported file (RAW, video): a clear message and nothing else happens.

## 10. Testing

- Unit tests (JVM): mask merge rules (no drift), loop clipping, crop and paste math, seam blend, JPEG block patcher (all untouched blocks bit-identical), EXIF keep and thumbnail rebuild, file naming.
- Instrumented and screenshot tests for the editor screens.
- Bake-off set: real object removal pairs (with and without the object) plus Admin-style photos (family, beach, street, small specks, wires, text); used to pick models and to catch regressions.
- On-device speed log screen (Settings) showing per-step times and which chip ran them.
- Slop Detector audit before every APK handover.
- Admin installs the debug APK and tests on their devices.

## 11. Build order

1. Tool proof on the build machine (SDK, tiny APK, one model download).
2. App skeleton, Dawn Mist theme, open and save a copy (same size, EXIF kept).
3. Fast brain removal with magic tap, brush, spot, loop, undo, compare.
4. JPEG block patcher and full-detail crop pipeline, detail and grain pass.
5. Helpers: Find distractions, Find text, Shadow catcher, protect brush, zoom-aware selection.
6. Enhance (fast models).
7. Deep brains (removal, then Enhance deep modes) and the brain pack.
8. Polish, Slop Detector, APK handover.
