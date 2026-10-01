# NanoGone: work log (newest entry at the end)

## 2026-10-01, cloud session: tool check

**Summary**
1. Java 21 and Gradle are installed here. Good.
2. Google's Android library sites (maven.google.com) and Maven Central work. Good.
3. **Blocked:** `dl.google.com` (where the Android SDK comes from). No APK can be built until it is allowed.
4. **Blocked:** `huggingface.co` (where the AI model files come from). No model can be downloaded until it is allowed.
5. Admin was asked to add these to the cloud environment's allowed sites.
6. Brainstorm: still waiting on Question 2 (which phone).

Sites to allow (Network access in the environment settings):
- `dl.google.com`
- `huggingface.co`, `cdn-lfs.huggingface.co`, `cas-bridge.xethub.hf.co` (Hugging Face file downloads use these)

Next step: once Admin answers Question 2 and the sites are allowed, rerun the tool check (install SDK, build a tiny APK, download one model file), then continue the brainstorm.

## 2026-10-01, brainstorm Question 2 answered

- Phones: Galaxy S23, S24, S25, S26 Ultra and Galaxy Tab A11+ 5G. Admin wants the app to use all the phone's power.
- Sites `dl.google.com` and `huggingface.co` still blocked at this time.
- Next: Question 3 (save as a copy or replace the original).

## 2026-10-01, brainstorm Question 3 answered, engine research saved

- Save a copy. The original stays untouched. The copy goes into a "NanoGone" folder.
- Engine research saved in `docs/research/2026-10-01-engine-research.md` (LiteRT runs the AI chip on all five devices; LaMa repair in about 40 ms on new Galaxy chips; new one-step removers also wipe shadows).
- Sites still blocked: `dl.google.com`, `huggingface.co`.
- Next: Question 4 (extra features for version 1), then propose engine approaches A, B, C.

## 2026-10-01, brainstorm Question 4 answered

- Version 1 extras: Find distractions, Shadow catcher, Text and logo eraser. Not Enhance (later).
- Admin also asked for an easy "draw a line around it" lasso with an automatic magnet that wraps the object, and fills that match the surroundings.
- Next: Question 5, engine approach (A fast, B deep, C both; recommending C).

## 2026-10-01, brainstorm Question 5 answered

- Engine: both brains. Fast brain (LaMa class) for live preview and most fixes; deep brain (one-step SDXL-class remover, OSOR or TurboClear by bake-off) for big objects, shadows and reflections. Fast one gets built first.
- Research file extended with deep brain candidates and helper models.
- Next: present the design to Admin in 6 parts (journey, selecting, repairing, saving, look, safety nets and testing).

## 2026-10-01, design parts 1 to 5 approved, look options published

- Approved: Part 1 journey, Part 2 picking (plus Enhance added), Part 3 filling the hole, Part 4 Enhance (all four tools), Part 5 saving (Save offers JPEG and PNG).
- Part 6 (look): three art directions built as a try-it page: `docs/design/nanogone-looks.html`, published at https://claude.ai/artifact/7M7S7fUqtbHdvaQ6wmqstB . A: The Invisible Mender (weave). B: The Conservator's Lamp (UV torch compare). C: Fresh Powder (snowfall). Recommending B.
- Next: Admin picks a look, then Part 7 (safety nets and testing), then write the spec.

## 2026-10-01, Admin said "stop asking, pick the best, get it done"; spec and Plan 1 done

- Look locked: Admin's own "Dawn Mist" (frosted glass, pastel dawn, warm mist that lifts away on Remove).
- Spec written: `docs/superpowers/specs/2026-10-01-nanogone-design.md`.
- Plan 1 written and DONE: `docs/superpowers/plans/2026-10-01-plan-1-imaging-core.md`. Module `:core:imaging` (pure Kotlin, 27 tests passing, run `./gradlew :core:imaging:test`):
  - masks with no-drift tap rules, loop clipping, grow, distance transform
  - crop planning, feathered paste (never touches pixels outside the mask)
  - baseline JPEG coefficient decoder and writer (rewrite = identical pixels in Pillow)
  - block patcher: only edited 8x8/16x16 squares re-encoded; full quality-100 encoder fallback
  - copy cleaner (drops MPF, Ultra HDR and motion-photo XMP, Samsung trailer) and EXIF preview swap
- Note: Maven Central sometimes answers 429 (too many requests). Just rerun; a second mirror (repo1) is set.
- STILL BLOCKED: `dl.google.com` (Android SDK and Google's Maven, so no APK yet) and `huggingface.co` (AI model files).
- Next: Plan 2 (Android app shell, Dawn Mist theme, open and save a copy using :core:imaging) as soon as `dl.google.com` is allowed; Plan 3 (AI brains) needs `huggingface.co`.
- Also written: `ART_DIRECTION.md` (Dawn Mist, gate answers A1 to A9 with the owner override) and Plan 2 `docs/superpowers/plans/2026-10-01-plan-2-app-shell.md` (first installable APK). Plan 2 starts with the SDK install once `dl.google.com` is allowed.

## 2026-10-01, building on GitHub instead (Plan B)

- This cloud box still cannot reach `dl.google.com` or `huggingface.co`, so builds run on **GitHub Actions** (`.github/workflows/build.yml`), which has the Android SDK and open internet.
- Each push to `main` (outside docs) builds, runs all tests, starts an Android emulator, and drives the app (`tools/smoke.sh`): open a 12 MP test photo, brush the bin, Remove, Save as JPEG, then byte-check the saved copy (`tools/check_saved.py`).
- Results come back on the branch `ci-results` (status, errors, tests, screenshots in `smoke/`, timings). Read them with `git fetch origin ci-results && git show FETCH_HEAD:status.txt`.
- The APK is attached to the GitHub release **debug-latest** (download on the phone from the repo's Releases page).
- AI brains (Qualcomm builds, public download, shipped inside the APK, fetched at build time, not committed): LaMa-Dilated (fast brain, 512x512), MobileSAM (magic tap), Real-ESRGAN x4 (for Enhance). Run with LiteRT 2.2 CompiledModel: graphics chip first, main chip fallback.
- First emulator run (smooth fill, before the AI brains): same size kept, EXIF kept, bin gone, 2.8% of pixels changed (all around the bin). Removal took 32 s on the emulator; step timing logs added to find out why.
- Known limits right now: Find distractions, Shadow catcher, Text eraser, deep brain, Enhance not built yet. Huge photos (200 MP) can run out of memory when saving as PNG.
