# NanoGone: project rules (read this first, every session)

**TL;DR**
1. NanoGone is a free Android app that removes objects from photos. It must beat Google Photos and Samsung Gallery.
2. The owner is called **Admin**. Admin is non-technical and writes zero code. You build everything.
3. Talk to Admin like to a smart 10-year-old: short, plain words, no jargon, and **never an em dash**.
4. **Decided:** all the AI work runs ON THE PHONE (option A). Offline, private, no cloud AI inside the app.
5. Status: mid-brainstorm. Read `docs/BRAINSTORM-STATE.md`, then carry on with the `brainstorming` skill.
6. A finished section is never a stopping point. Run until the WHOLE job is done.

---

## 1. How to talk to Admin (always)

- Line 1 is the answer or the next action. No warm-up, no recap, no "Hope this helps".
- Short sentences, one idea each. Everyday words. If a hard word is unavoidable, explain it once in the next sentence.
- **No em dashes** anywhere: replies, files, commit messages. Use a comma, a full stop, a colon or brackets.
- Number multi-step work. Lists of at most 5 items.
- Give time in real units ("about 20 minutes"), never "some work".
- Show wins plainly: what now works and how to see it.
- Errors: cause + fix, matter-of-fact.
- Replies stay short (about 12 lines). Long detail goes in a file, and the file starts with a 6-line plain summary.
- At the very end of the whole job: a 5-line block "Done / Not done / Next".

## 2. The non-stop law

- Finishing a part means starting the next part in the same turn.
- Never end with "shall I continue?", "ready for the next part?" or any question whose answer is obviously yes.
- Only three real stops: (1) the whole job is done, (2) a decision only Admin can make (ask ONE question, after doing every other part first), (3) something destructive that needs a yes.
- Brainstorming is the exception by design: there you ask Admin one question at a time, because the answers are Admin's decisions.

## 3. Admin's three standing DON'Ts

1. Never talk about cost, tokens or spending. Never cut work short to save them.
2. Never shrink for size. Use full-quality models and lossless formats by default. If size truly matters, ask first.
3. Never lecture about security, privacy or risk. Just do the work well.

Also: **never build anything for Apple platforms** (no iOS, no macOS, no Swift). Android only. Apple *design ideas* are welcome.

## 4. The process (the disciplined mind)

The skills in `.claude/skills/` are the house toolkit. When a skill says `superpowers:X`, it means the project skill `X` here.

1. `brainstorming` first: no code before Admin approves a design.
2. Its only exit is `writing-plans`.
3. Build with `subagent-driven-development` (or `executing-plans`).
4. Inside every task: `test-driven-development`. On any bug: `systematic-debugging` before any fix.
5. Before saying "done", "fixed" or "passing": `verification-before-completion` (fresh proof, not memory).
6. Reviews: `requesting-code-review` / `receiving-code-review`. Close with `finishing-a-development-branch`.
7. After a fix: `code-change-impact` to check what else it touched.

Design docs go to `docs/superpowers/specs/`, plans to `docs/superpowers/plans/`.

## 5. Android rules (binding)

- Read `android-app-studio` (the builder's doctrine) before any app code.
- **The anti-generic law is binding:** `android-material3-design/references/anti-generic-doctrine.md`. Invent a design, never pick a default. Write `ART_DIRECTION.md` before the first `@Composable`. Run the Slop Detector before every APK handover. No purple Material defaults, no card soup, no Home/Search/Profile bar.
- Look and feel advisors: `ui-ux-pro-max` (use `--stack jetpack-compose`), `compose-design-vault`, `kmp-liquid-glass`.
- Code: Kotlin + Jetpack Compose + Material 3. Skills: `android-compose-expert`, `android-architecture`, `compose`, `android-dev`, `kotlin-coroutines`, `kotlin-flows`, `android-testing`, `android-debugging`.
- Creativity is the priority. Every screen should feel premium, alive and one of a kind.
- The deliverable is a **debug APK** Admin installs on their own phone. Admin tests it themselves.
- Agents in `.claude/agents/`: `android-ux-architect`, `android-ui-designer`, `engineering-mobile-app-builder`, `engineering-code-reviewer`.

## 6. This cloud computer

- You run on Linux in Anthropic's cloud. Some skills mention Windows paths (`C:\...`, `D:\...`). Those are on Admin's PC and do not exist here. Ignore them.
- **Before building, prove the tools work:** install the Android SDK, build a tiny APK, and download one model file (for example from Hugging Face). If the network blocks a site, tell Admin the exact website name to allow, in one plain line.
- Commit often with clear messages and push. A session on Admin's PC may take over later to check the work and hand over the APK.
- Keep `docs/STATE.md` up to date (newest entry at the end): what is done, what is not, exact next step.
