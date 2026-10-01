# Plan 2: Android app shell (open, Dawn Mist editor, save a perfect copy)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Plain summary**
1. This plan makes the first real NanoGone APK you can install.
2. You can open a photo from Gallery (Share or Edit) or from inside the app.
3. The editor screen has the Dawn Mist look: dawn colours, frosted panes, soft buttons.
4. You can brush over something; it gets the morning mist; Save makes a perfect copy.
5. In this plan "Remove" fills with a simple placeholder fill (the AI brains come in Plan 3).
6. Needs `dl.google.com` (Android build kit) to be allowed.

**Goal:** Installable debug APK: open a photo, select with a brush, see the mist, save a copy to `Pictures/NanoGone` through `:core:imaging` (block-patched JPEG or lossless PNG), same size, EXIF kept, preview rebuilt.

**Architecture:** Single-activity Compose app. `:app` (activity, intents, navigation, theme install), `:core:design` (Dawn Mist tokens, glass pane, neumorphic button, mist effect), `:feature:editor` (editor state holder, gestures, brush, history), `:core:storage` (MediaStore read and save, uses `:core:imaging`). `:core:imaging` from Plan 1 is reused unchanged.

**Tech Stack:** AGP (latest stable from Google Maven), Kotlin 2.4.x, Compose BOM (latest stable), Material 3, Hilt, Coroutines/Flow, `androidx.exifinterface`, JUnit 5 for JVM tests, Compose UI tests, Roborazzi screenshot tests.

## Global Constraints

- minSdk 31, targetSdk 36, compileSdk 36. Android only.
- Package `app.nanogone`. App name "NanoGone".
- Look and copy follow `ART_DIRECTION.md` exactly (Syne + Instrument Sans bundled, palette hexes, spring 0.78/320, glass on two surface types only).
- The original photo is opened read-only and never written.
- Saved copy: same width, height, orientation; EXIF kept; no AI label; file in `Pictures/NanoGone`, name `<original>_NanoGone.<jpg|png>`.
- Never decode a full 200 MP bitmap: display uses subsampled tiles (`BitmapRegionDecoder`), edits use crops.
- No em dashes anywhere.

---

### Task 1: Tool proof and Android skeleton
- Install the SDK command-line tools, `platform-tools`, `platforms;android-36`, `build-tools;36.0.0` to `/opt/android-sdk`; write `local.properties` (`sdk.dir=/opt/android-sdk`, not committed).
- Add `google()` back to `settings.gradle.kts`; add AGP, Compose, Hilt to `gradle/libs.versions.toml`.
- Create `:app` with `MainActivity` showing one Dawn Mist screen; `./gradlew :app:assembleDebug` must produce `app/build/outputs/apk/debug/app-debug.apk`.
- Test: `./gradlew :app:testDebugUnitTest` runs (one trivial JVM test) and the APK file exists.
- Commit `build: Android app skeleton`.

### Task 2: Dawn Mist design system (`:core:design`)
- `DawnColors` (light and dark from `ART_DIRECTION.md`), `DawnType` (Syne, Instrument Sans, tracking per size band), `DawnShapes` (28/18/20 dp squircles, pill), `DawnMotion` (the one spring, 30 ms stagger).
- `FrostedPane` (RenderEffect blur of what is behind, inner highlight, soft shadow), `SoftButton` (neumorphic raised and pressed states), `DawnBackground` (lake blue to first gold gradient with a faint sun glow).
- `MistOverlay(mask, state)` drawn with a `Canvas`: drifting soft puffs over the mask plus a blur of the photo under the mask; `evaporate()` animates rise, grow and fade over 1.2 s.
- Tests: Roborazzi screenshots of pane, buttons and mist in light and dark; a JVM test that every text colour pair meets 4.5:1.
- Commit `feat(design): Dawn Mist design system`.

### Task 3: Opening photos (`:core:storage` read side, `:app` intents)
- Intent filters: `ACTION_SEND` and `ACTION_EDIT` for `image/*`; in-app Android Photo Picker.
- `PhotoSource.open(uri)`: reads size, orientation and format, keeps a read-only handle; `TileDecoder` with `BitmapRegionDecoder` and sample sizes for display.
- Tests: instrumented test opening a 12 MP and a 200 MP-sized synthetic JPEG without OOM; JVM tests for sample-size math.
- Commit `feat(storage): open photos from Share, Edit and the picker`.

### Task 4: Editor screen and brush (`:feature:editor`)
- Zoomable, pannable photo (tiles), top frosted bar (back, undo, redo, Save), bottom frosted tray (Tap, Loop, Brush, Spot, Find, Enhance; only Brush active in this plan), Remove pill.
- Brush paints a `Mask` (`:core:imaging`) in image coordinates; width scales with zoom; magnifier bubble above the finger.
- Undo and redo of mask edits and removals; press and hold the photo to see the original.
- Remove (placeholder fill until Plan 3): fill the masked area by diffusion from its border (simple, deterministic) inside a `CropPlanner.contextBox`, pasted with `Paste.feathered`; mist evaporates.
- Tests: JVM tests for the screen state holder (brush to mask, undo/redo); Compose UI test that Remove enables only when something is selected.
- Commit `feat(editor): Dawn Mist editor with brush, undo and remove`.

### Task 5: Saving a perfect copy (`:core:storage` write side)
- Save sheet with two choices showing estimated sizes: Top-quality JPEG, Lossless PNG; remembers the last pick (DataStore).
- JPEG original + removals only: `CropPlanner.alignOut` to the MCU grid from `JpegProbe.info`, `BlockPatcher.patch(..., CopyCleaner.forCopy(thumb))` where `thumb` is a 160 px `JpegEncoder.encode` of the result. Unsupported JPEG or other formats: `JpegEncoder.encode` with kept EXIF, or PNG.
- Write through MediaStore to `Pictures/NanoGone` with `IS_PENDING`; done screen with Share.
- Tests: instrumented round trip (open, edit, save, reopen): size equal, EXIF date kept, untouched area identical, file in the album.
- Commit `feat(storage): save perfect copies to the NanoGone album`.

### Task 6: Handover
- Slop Detector audit against `ART_DIRECTION.md`; fix any hit.
- Build `app-debug.apk`, record its path and how to install in `docs/STATE.md`.
- Commit `chore: first APK handover`.
