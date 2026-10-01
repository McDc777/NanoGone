# Plan 1: Imaging core (pure Kotlin, testable without the Android SDK)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Plain summary**
1. This plan builds the photo-handling heart of NanoGone as plain Kotlin code with tests.
2. It covers masks (what you selected), crop boxes, soft pasting, and the JPEG saver.
3. The JPEG saver rewrites only the 8x8 squares you changed; every other square stays bit-identical.
4. It also cleans the copy: drops Samsung extras that would show the removed thing.
5. It runs on this cloud computer today, even while the Android build kit is blocked.
6. Later plans (Android app, AI brains, helpers, Enhance) plug into these pieces.

**Goal:** A JVM Kotlin library `:core:imaging` with masks, geometry, feathered paste, a baseline JPEG coefficient codec, a JPEG block patcher, a full JPEG encoder fallback, and copy-cleaning for Samsung files.

**Architecture:** Pure Kotlin (no Android types) so it is unit-tested on the JVM and later used unchanged by the Android app. Images are `IntArray` ARGB. JPEG work happens on quantized DCT coefficients, so untouched blocks keep identical coefficients and therefore identical pixels in every decoder.

**Tech Stack:** Kotlin 2.x JVM, Gradle 8.14 with version catalog, JUnit 5, Pillow (Python) only for one-off visual checks.

## Global Constraints

- Package root: `app.nanogone.imaging`.
- No Android or third-party runtime dependencies in `:core:imaging` (Kotlin stdlib only).
- JPEG support for patching: baseline or extended sequential Huffman (SOF0, SOF1), 8-bit, 1 or 3 components, one interleaved scan, any sampling factors, optional restart interval. Anything else throws `UnsupportedJpegException` so the caller falls back to a full encode.
- Untouched blocks must keep identical quantized coefficients (tested).
- Huffman tables are always re-optimized on write (Annex K.2), so new coefficients never miss a code.
- Output width and height always equal the input's.
- No em dashes in code, comments, docs or commit messages.

---

### Task 1: Project skeleton and masks

**Files:**
- Create: `settings.gradle.kts`, `build.gradle.kts`, `gradle/libs.versions.toml`, `gradle.properties`, Gradle wrapper
- Create: `core/imaging/build.gradle.kts`
- Create: `core/imaging/src/main/kotlin/app/nanogone/imaging/mask/Mask.kt`
- Create: `core/imaging/src/main/kotlin/app/nanogone/imaging/mask/MaskOps.kt`
- Create: `core/imaging/src/main/kotlin/app/nanogone/imaging/geom/IntRect.kt`
- Test: `core/imaging/src/test/kotlin/app/nanogone/imaging/mask/MaskOpsTest.kt`

**Interfaces (produces):**
- `class Mask(width: Int, height: Int, bits: BooleanArray)`, `get/set(x, y)`, `count()`, `isEmpty()`, `copy()`, `bounds(): IntRect?`
- `data class IntRect(left, top, right, bottom)` (right and bottom exclusive), `width`, `height`, `contains(x, y)`, `intersect`, `isEmpty`
- `object MaskOps`: `componentAt(m, x, y): Mask`, `union(a, b)`, `intersect(a, b)`, `subtract(a, b)`, `plusTap(current, proposal, x, y)`, `minusTap(current, proposal, x, y)`, `rasterizePolygon(w, h, xs: FloatArray, ys: FloatArray): Mask`, `clipToPolygon(m, xs, ys)`, `distanceToOff(m): FloatArray` (Euclidean distance from each on pixel to the nearest off pixel, 0 for off pixels), `grow(m, radius: Int): Mask`, `growRadiusFor(m): Int`

- [ ] Step 1: Write tests: plus tap adds only the touched piece of a two-blob proposal; minus tap removes only the touched piece; a tap outside the proposal changes nothing; polygon raster of a square; clip keeps only inside; grow by 2 turns a 1-pixel dot into a radius-2 disk (13 pixels); `growRadiusFor` clamps to 2..16.
- [ ] Step 2: Run `./gradlew :core:imaging:test`, expect compile failure.
- [ ] Step 3: Implement (4-connected flood fill; even-odd scanline polygon fill at pixel centres; exact squared EDT by Felzenszwalb for distance and grow; radius rule `clamp(round(0.03 * sqrt(count)), 2, 16)`).
- [ ] Step 4: Run tests, expect PASS.
- [ ] Step 5: Commit `feat(imaging): masks with no-drift tap rules`.

### Task 2: Crop planning and feathered paste

**Files:**
- Create: `core/imaging/src/main/kotlin/app/nanogone/imaging/geom/CropPlanner.kt`
- Create: `core/imaging/src/main/kotlin/app/nanogone/imaging/image/Argb.kt`
- Create: `core/imaging/src/main/kotlin/app/nanogone/imaging/image/Paste.kt`
- Test: `CropPlannerTest.kt`, `PasteTest.kt`

**Interfaces (produces):**
- `CropPlanner.contextBox(maskBounds: IntRect, imageW: Int, imageH: Int, minPad: Int = 32, contextScale: Float = 0.75f): IntRect`: grow each side by `max(minPad, contextScale * max(w, h))`, clamp to the image.
- `CropPlanner.alignOut(r: IntRect, gridW: Int, gridH: Int, imageW: Int, imageH: Int): IntRect`: snap outward to a grid, clamp.
- `class Argb(width, height, px: IntArray)` with `get/set`, `crop(r): Argb`.
- `Paste.feathered(dst: Argb, src: Argb, atX: Int, atY: Int, mask: Mask, feather: Float)`: for each masked pixel, alpha = `min(1, distanceToOff / feather)`; unmasked pixels of `dst` are never written.

- [ ] Step 1: Tests: context box clamps at image edges; alignOut snaps to 16; paste never changes a pixel outside the mask (checked over the whole image); a pixel deep inside the mask equals the source exactly.
- [ ] Step 2: Run, expect FAIL. Step 3: Implement. Step 4: Run, expect PASS.
- [ ] Step 5: Commit `feat(imaging): crop planning and feathered paste`.

### Task 3: JPEG reading to coefficients

**Files:**
- Create: `core/imaging/src/main/kotlin/app/nanogone/imaging/jpeg/JpegModel.kt` (segments, frame, scan, tables, `UnsupportedJpegException`)
- Create: `.../jpeg/JpegParser.kt` (markers, DQT, DHT, SOF0/1, DRI, SOS, entropy bytes, trailing bytes after EOI)
- Create: `.../jpeg/Huffman.kt` (decode tables, bit reader with byte stuffing and RST handling)
- Create: `.../jpeg/CoefficientDecoder.kt`
- Create: `tools/make_jpeg_fixtures.py`, fixtures in `core/imaging/src/test/resources/jpeg/`
- Test: `JpegParserTest.kt`, `CoefficientDecoderTest.kt`

**Interfaces (produces):**
- `JpegParser.parse(bytes: ByteArray): ParsedJpeg` with `frame: Frame(width, height, components: List<FrameComponent(id, h, v, tq)>)`, `qTables: Array<IntArray?>` (zigzag order), `restartInterval: Int`, `scan: ScanHeader`, `preFrame: List<Segment>`, `entropy: ByteArray`, `trailing: ByteArray`, `mcuWidth`, `mcuHeight`, `mcusX`, `mcusY`.
- `CoefficientDecoder.decode(p: ParsedJpeg): Coefficients` where `Coefficients(blocksX: IntArray, blocksY: IntArray, data: Array<ShortArray>)`, block `(bx, by)` of component `c` at offset `((by * blocksX[c]) + bx) * 64`, zigzag order, quantized values.

Fixtures (made once with Pillow and committed): 64x48 and 203x157 images, sampling 4:4:4, 4:2:0, 4:2:2, grayscale, quality 75 and 95, one with restart interval 4 (Pillow `restart_marker_blocks`), one progressive (must be rejected).

- [ ] Step 1: Tests: parse sizes and sampling of each fixture; progressive fixture throws `UnsupportedJpegException`; decoded DC of a flat grey 4:4:4 image is the same for every block.
- [ ] Step 2: Run, expect FAIL. Step 3: Implement. Step 4: Run, expect PASS.
- [ ] Step 5: Commit `feat(imaging): baseline JPEG coefficient decoder`.

### Task 4: JPEG writing from coefficients (optimized Huffman)

**Files:**
- Create: `.../jpeg/HuffmanOptimizer.kt` (Annex K.2 code lengths limited to 16)
- Create: `.../jpeg/CoefficientEncoder.kt` (symbol statistics pass, table build, entropy write with stuffing and RST markers)
- Create: `.../jpeg/JpegWriter.kt` (SOI, kept pre-frame segments minus DHT and DRI, SOF, new DHT, DRI, SOS, data, EOI)
- Test: `RoundTripTest.kt`

**Interfaces (produces):**
- `JpegWriter.write(p: ParsedJpeg, coeffs: Coefficients, keep: (Segment) -> Boolean = { true }): ByteArray`

- [ ] Step 1: Test: for every fixture, `decode(parse(write(parse(f), decode(parse(f)))))` equals the original coefficients exactly, and frame, quant tables and restart interval are unchanged.
- [ ] Step 2: Run, expect FAIL. Step 3: Implement. Step 4: Run, expect PASS.
- [ ] Step 5: Verify once with Pillow: `python3 tools/jpeg_compare.py original.jpg rewritten.jpg` prints `identical pixels`.
- [ ] Step 6: Commit `feat(imaging): JPEG writer with optimized Huffman tables`.

### Task 5: Block patcher and full encoder

**Files:**
- Create: `.../jpeg/ForwardDct.kt` (RGB to YCbCr JFIF, chroma box downsample by sampling factors, edge replication, float FDCT, quantize with rounding, zigzag)
- Create: `.../jpeg/BlockPatcher.kt`
- Create: `.../jpeg/JpegEncoder.kt` (full encode: quality 100 tables, 4:4:4, via the same writer)
- Test: `BlockPatcherTest.kt`, `JpegEncoderTest.kt`

**Interfaces (produces):**
- `JpegProbe.info(bytes): JpegInfo(width, height, mcuWidth, mcuHeight, patchable: Boolean)`
- `BlockPatcher.patch(original: ByteArray, region: IntRect, pixels: IntArray, changed: Mask): ByteArray`. `pixels` is ARGB of `region` (the edited result, original pixels where unchanged). `changed` is in region coordinates. Every MCU containing a changed pixel must lie inside `region` (clipped by the image edge) or `IllegalArgumentException`.
- `JpegEncoder.encode(width, height, argb: IntArray, preFrame: List<Segment> = emptyList()): ByteArray`

- [ ] Step 1: Tests: after patching a 16x16 square on each fixture, every block outside the touched MCUs has identical coefficients to the original; touched blocks differ; output dimensions equal input; Pillow-decoded patched area is close to the new pixels (PSNR above 30 dB at quality 95, checked by the compare tool once). `JpegEncoder` output re-parses and has the right size.
- [ ] Step 2: Run, expect FAIL. Step 3: Implement. Step 4: Run, expect PASS.
- [ ] Step 5: Commit `feat(imaging): JPEG block patcher and full encoder`.

### Task 6: Cleaning the copy (Samsung extras, EXIF thumbnail)

**Files:**
- Create: `.../jpeg/CopyCleaner.kt`
- Create: `.../jpeg/ExifThumbnail.kt` (TIFF walk of APP1 Exif, replace IFD1 JPEG thumbnail when it is the last thing in the segment, else drop IFD1 link)
- Test: `CopyCleanerTest.kt`, `ExifThumbnailTest.kt`

**Interfaces (produces):**
- `CopyCleaner.keepSegment(s: Segment): Boolean`: keeps APP1 Exif, APP1 XMP without `hdrgm`, APP2 ICC, APP0 JFIF, COM, DQT; drops APP2 `MPF`, APP1 XMP with `hdrgm`, Samsung APP segments that hold depth or motion data.
- Trailing bytes after EOI (Samsung motion photo and SEFT data, MPF secondary images) are never written.
- `ExifThumbnail.replace(exifApp1: ByteArray, newThumb: ByteArray): ByteArray`

- [ ] Step 1: Tests with synthetic segments: MPF dropped, ICC kept, `hdrgm` XMP dropped, trailing bytes dropped by the writer; thumbnail replaced and lengths updated in a little-endian and a big-endian TIFF.
- [ ] Step 2: Run, expect FAIL. Step 3: Implement. Step 4: Run, expect PASS.
- [ ] Step 5: Commit `feat(imaging): clean copies and rebuild EXIF thumbnail`.

---

## Later plans (written when this one is done)

- Plan 2: Android app shell, Dawn Mist theme, open and save (needs `dl.google.com`).
- Plan 3: AI runtime (LiteRT), fast brain, selection tools (needs `huggingface.co`).
- Plan 4: Helpers (distractions, text, shadows), zoom-aware selection, detail and grain pass.
- Plan 5: Enhance.
- Plan 6: Deep brains and the brain pack.
