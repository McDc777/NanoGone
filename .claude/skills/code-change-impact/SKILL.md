---
name: code-change-impact
description: >-
  Trace the impact (blast radius) of a code change in ANY codebase and prove it
  didn't break anything else — language- and framework-agnostic. Use RIGHT AFTER
  a fix to a feature, function, module, type, route, API endpoint, schema, or
  config, before committing, opening a PR, or pushing. Triggers: "did this break
  anything", "what else does this affect", "check the impact / blast radius",
  "side effects of this change", "is this safe to merge/push", "regression
  check", "what depends on this" — or any change in shared/core code, a public/
  exported symbol, a type/interface, a serialized contract (REST/GraphQL/DTO), a
  DB schema/migration, a route/DI/plugin registry, a feature flag, or global
  config/styles. A one-line fix in shared or contract code routinely ripples into
  files you never opened, so prefer this over eyeballing the diff unless the
  change is contained in one leaf file. Works on JS/TS, Python, Go, Java, C#,
  Ruby, Rust, PHP and more by first discovering the project's own conventions.
---

# Code Change Impact — blast-radius / regression analysis for any codebase

A fix is "done" only when you know what it touched *besides* the thing you were
fixing. The dangerous ripples are rarely in the file you edited — they're in the
callers of a function whose behavior you changed, the other modules that import a
shared helper, the client that decodes a response whose shape you altered, the
generated/duplicated twin file you forgot to regenerate, or a consumer that now
fails to compile. This skill finds those, then **proves** the answer by running
the project's own build/test/lint and exercising the impacted surfaces.

Run it after a change, against the **VCS diff** (the diff is the source of truth
for "what changed"). It adapts to the language and tooling of whatever repo it's
in — so Phase 0 (discovery) comes first and the rest builds on it.

## Phase 0 — Discover the project (do this before analyzing)

You can't trace dependencies or verify a build until you know the project's
conventions. Spend a minute learning them; cache the findings for the rest of the
run. See `references/recipes.md` §1–2 for per-ecosystem detection commands.

Establish:
1. **Repo root + diff** — `git rev-parse --show-toplevel`; then the change set
   (working tree, staged, or branch-vs-base). Use the VCS in play (git/hg/jj).
2. **Languages + ecosystems** — from manifests and file extensions
   (`package.json`, `pyproject.toml`/`requirements.txt`, `go.mod`, `pom.xml`/
   `build.gradle`, `*.csproj`/`*.sln`, `Gemfile`, `Cargo.toml`, `composer.json`).
   A repo may have several (e.g. a JS frontend + a Go backend).
3. **Verification commands** — the project's own typecheck, build, test, and lint
   commands. Read them from the manifest's script section / Makefile / CI config
   rather than guessing (`references/recipes.md` §2). Note which test scripts are
   *targeted* (named per area) — those let you verify a specific blast radius
   cheaply.
4. **Module + import style** — how this language names and imports modules, and
   any path aliases (e.g. a TS `tsconfig.json` `paths`, a Python namespace, a Go
   module path). This is what your reverse-dependency greps will key on.

If discovery is ambiguous or the repo is unfamiliar, state what you found and
ask the user to confirm the build/test commands before relying on them.

## The five phases

### Phase 1 — Pin the epicenter (what actually changed)

```bash
git -C <root> status --short
git -C <root> diff --stat HEAD
git -C <root> diff HEAD            # full hunks — the exact lines decide the ripple
```
For a whole branch of fixes: `git diff <base>...HEAD`. If the user named a
feature/file, scope to it but still read the surrounding hunks.

**Classify each changed file** by coupling class — this sets how far the blast
radius reaches. Full taxonomy in `references/recipes.md` §3.

| Changed file is… | Reach | Where it ripples |
|---|---|---|
| shared/core/common/util module | **wide** | every importer |
| a public/exported symbol, package public API, barrel/index | callers in + out of the module | every caller |
| a type / interface / schema / `.proto` | wide | usages (compiler-caught in typed langs; **silent** in dynamic langs) |
| a serialized contract (REST/GraphQL/DTO/protobuf/event) | cross-service | the other side of the wire |
| a config/registry (route table, DI container, plugin list, feature flags) | fan-out | everything derived from it |
| a DB schema / migration / ORM model | data layer | queries, models, other services |
| global config / styles / theme / i18n strings | **global, silent** | every screen/string, no compiler signal |
| build / deps / lockfile / Dockerfile / CI | build & runtime | the whole app |
| a generated, duplicated, or cross-language **twin** file | paired | its twin (see §4 mirror files) |
| an internal change in a single leaf file | **local** | itself — small verify, done |

### Phase 2 — Trace the reverse dependencies

For each changed symbol, find who depends on it, using this language's import
style. Recipes per ecosystem are in `references/recipes.md` §1. The shape is
always the same:

```bash
# who imports the changed module / package / file?
rg -n "<import-form-for-this-language targeting the changed module>" <root> -l
# who calls the changed symbol by name?
rg -n "\b<SymbolName>\b" <root> -l
# for a contract change: who is on the other side of the wire?
rg -n "<endpoint path | message name | field name>" <root>
```

Build two buckets — **directly impacted** (import/call the changed symbol) and
**transitively impacted** (depend on the directly-impacted). One hop is usually
enough; go deeper for shared/core and public-API changes. Then map impacted code
→ **user-facing or external surfaces** (which route, command, job, or endpoint
exercises it) so the report names things a tester can actually run.

### Phase 3 — Reason about *behavioral* impact (what the compiler can't see)

For each directly-impacted site, ask: did the change alter a **shape, a
signature, a default, a side effect, or an invariant** — or is it internal and
safe? Split the risk:

- **Type-safe / build-caught ripples** — signature/shape changes in typed
  languages. The build will surface these; note them, they're cheap.
- **Silent ripples** — same types, different behavior. These are what ship bugs.
  Hunt them. The recurring ones (cache/memo key changes, changed default/sort/
  comparator, serialization/enum drift, global mutable state, concurrency &
  transaction boundaries, locale/time/number formatting, regex/validation
  predicates, feature-flag defaults, theme/i18n) are catalogued in
  `references/recipes.md` §4. Dynamic languages (Python/Ruby/JS) have *no*
  compiler net, so even signature changes are silent — weight Phase 3 heavier.

**Check for mirror/twin files** (`references/recipes.md` §4): generated code
whose source you edited, a constant/enum duplicated across a language boundary,
an OpenAPI/proto schema and its generated client. A diff that changes one side
and not the other is an impact finding even when everything compiles.

### Phase 4 — Verify (prove it, don't assert it)

Run the commands you discovered in Phase 0, cheapest signal first, scaled to the
blast radius:

1. **Typecheck / compile** — for any change in a typed language; catches the
   build-caught ripples fast.
2. **Build** — if shared/entry-point/build-path code was touched.
3. **Tests** — prefer the *targeted* suite matching the impacted area; fall back
   to the full suite for shared/core changes. Run the language's test runner.
4. **Lint / static analysis** — if the project gates on it.
5. **Exercise the impacted surfaces** — actually run the affected entry points
   (start the app/service and hit the impacted route or endpoint, run the
   affected CLI command or job) and watch logs/output/errors. Don't just verify
   the file you fixed — verify the *blast radius*. Use whatever run/preview
   tooling the environment provides.

A green local build is not the same as deployed/released — never claim a change
is live in an environment you didn't check.

### Phase 5 — Report

Always emit the report in the template below. The verdict is the headline; the
blast radius and residual checklist are what the user acts on.

## Report template

ALWAYS use this exact structure:

```
# Code Change Impact: <one-line description of the change>

## Verdict: SAFE | SAFE WITH CAVEATS | IMPACT FOUND

## Project (discovered)
- languages/ecosystems: <…>   verify cmds: typecheck=<…> build=<…> test=<…>

## Epicenter
- <file:line> — <coupling class> — <what changed (shape/default/internal/…)>

## Blast radius
### Directly impacted
- <file / surface> — <route/endpoint/command> — <why> — risk: High|Med|Low
### Transitively impacted
- <…>  (or "none beyond build-checked usages")

## Mirror / twin files
- <generated or duplicated file that must change in lockstep> — in sync? yes/NO
  (or "none identified")

## Silent-risk callouts (build won't catch)
- <…>  (or "none identified")

## Verification
- typecheck/compile: PASS/FAIL <summary>
- build: PASS/FAIL/skipped
- tests: <which ran> → PASS/FAIL
- surfaces exercised: <list> → clean? errors?

## Residual risk & manual checklist
- [ ] <thing not auto-verifiable — an external surface, a behavioral edge, a
      deploy/release step, a flaky-to-reproduce path>
```

### Verdict rule

- **SAFE** — blast radius traced; mirror/twin files in sync; typecheck +
  warranted build/tests pass; impacted surfaces exercised clean; no silent risk
  identified.
- **SAFE WITH CAVEATS** — passes, but residual checks remain that you couldn't
  auto-verify. List them as checkboxes; don't paper over them.
- **IMPACT FOUND** — a twin is out of sync, a consumer breaks at compile/runtime,
  or a silent ripple is confirmed. List each breakage with file + fix. This is
  the outcome the skill exists to catch — state it plainly, don't soften it.

## Scope discipline

- Analyze; don't silently "fix while you're in there". If you find collateral
  damage, report it and propose the fix — let the user decide whether the change
  should grow.
- Match effort to reach. An internal one-liner in a leaf file needs a typecheck
  and a glance. A shared/core, public-API, schema, or contract change earns the
  whole pipeline.
- When unsure whether a ripple is real, say so and put it on the manual checklist
  rather than declaring SAFE.
