---
mode: agent
description: Trace the impact / blast radius of a code change and prove it didn't break other features. Run after a fix, before commit/PR/push. Language-agnostic.
---

# Code Change Impact (GitHub Copilot / VS Code prompt file)

Invoke with `/code-change-impact` in Copilot Chat (agent mode) after making a
change. A fix is "done" only when you know what it touched *besides* the target.
Find the ripples, then prove nothing else broke. Work against the VCS diff.

## Procedure

**0. Discover the project.** Establish the repo root + diff; languages (from
`package.json`, `pyproject.toml`, `go.mod`, `*.csproj`, `pom.xml`/`build.gradle`,
`Gemfile`, `Cargo.toml`, `composer.json`); the project's own **typecheck / build /
test / lint** commands (read them from manifest scripts, `Makefile`, or CI —
don't guess); and the import style + path aliases. A repo may mix stacks.

**1. Pin the epicenter.** `git diff HEAD` (or `git diff <base>...HEAD`). Read the
hunks. Classify each changed file: shared/core · public/exported symbol · type/
interface/schema · serialized contract (REST/GraphQL/DTO/proto) · config/registry ·
DB schema/migration · global config/styles · build/deps · generated/duplicated
twin · or a local leaf.

**2. Trace reverse dependencies.** For each changed symbol, search who imports the
module and who calls the symbol. Build **directly impacted** + **transitively
impacted**, then map to runnable surfaces (route/endpoint/command/job).

**3. Reason about behavioral impact.** Per site: did a shape/signature/default/
side-effect/invariant change, or is it internal/safe? Split build-caught ripples
(typed languages) from **silent ripples** — cache/memo keys, changed default/sort,
serialization or enum drift, global/persisted state, concurrency & transactions,
locale/time/number formatting, regex/validation, feature-flag defaults, theme/
i18n. Check **mirror/twin files**: regenerate generated code, sync cross-language
duplicated constants. One side edited without the other is an impact finding even
if it compiles.

**4. Verify.** Run the discovered commands cheapest-first, scaled to reach:
typecheck → build → tests (prefer targeted) → lint. Then **exercise the impacted
surfaces** (run the app/service/command and hit the affected route/endpoint) and
watch the output for new errors. A green local build is not the same as deployed.

**5. Report** with the template below.

## Report template (always use this)

```
# Code Change Impact: <one-line description>
## Verdict: SAFE | SAFE WITH CAVEATS | IMPACT FOUND
## Epicenter
- <file:line> — <coupling class> — <what changed>
## Blast radius
### Directly impacted
- <file / surface> — <route/endpoint/command> — <why> — risk: High|Med|Low
### Transitively impacted — <…> (or none)
## Mirror / twin files — <file to sync, in sync? yes/NO> (or none)
## Silent-risk callouts (build won't catch) — <…> (or none)
## Verification
- typecheck/build/tests: PASS/FAIL
- surfaces exercised: <list> → clean? errors?
## Residual risk & manual checklist
- [ ] <not auto-verifiable — external surface, behavioral edge, deploy step>
```

**Verdict rule** — SAFE: traced, twins synced, checks pass, surfaces clean, no
silent risk. SAFE WITH CAVEATS: passes but residual manual checks remain (list
them). IMPACT FOUND: a twin out of sync, a consumer breaks, or a silent ripple
confirmed — state it plainly with file + fix.

**Scope discipline.** Analyze; don't silently fix unrelated code. Match effort to
reach — a leaf one-liner needs a typecheck and a glance; a shared/contract change
earns the whole pipeline. When unsure a ripple is real, put it on the checklist
rather than declaring SAFE.
