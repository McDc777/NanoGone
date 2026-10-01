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
