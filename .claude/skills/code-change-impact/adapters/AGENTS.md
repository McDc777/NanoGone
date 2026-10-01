# AGENTS.md — Code Change Impact procedure (Codex CLI & AGENTS.md-aware agents)

> Drop this into your repo-root `AGENTS.md` (or `~/.codex/AGENTS.md`). It stays
> dormant during normal work and activates only when you're checking the impact
> of a change. The full per-language detail is in `references/recipes.md` of the
> code-change-impact bundle — consult it if available.

When the user asks to **check the impact / blast radius / side effects of a
change**, asks **"did this break anything"** or **"is this safe to merge/push"**,
or **before finalizing any change that touches shared/core code, a public/
exported symbol, a type/interface, a serialized contract (REST/GraphQL/DTO/
protobuf), a DB schema/migration, a route/DI/plugin registry, a feature flag, or
global config/styles** — run this procedure instead of eyeballing the diff. The
goal: find what the change touched *besides* the target, then prove nothing else
broke.

## Procedure

**0. Discover the project.** Don't guess commands. Establish: VCS root + the diff;
languages/ecosystems (from manifests — `package.json`, `pyproject.toml`, `go.mod`,
`*.csproj`, `pom.xml`/`build.gradle`, `Gemfile`, `Cargo.toml`, `composer.json`);
the project's own **typecheck / build / test / lint** commands (read them from the
manifest scripts, `Makefile`, or CI config); and the module/import style + any
path aliases. A repo may mix several stacks.

**1. Pin the epicenter.** `git diff HEAD` (or `git diff <base>...HEAD` for a
branch). Read the exact hunks. Classify each changed file: shared/core · public/
exported symbol · type/interface/schema · serialized contract · config/registry ·
DB schema/migration · global config/styles · build/deps · generated/duplicated
twin · or a local leaf (small verify, done).

**2. Trace reverse dependencies.** For each changed symbol, grep who imports the
module and who calls the symbol, using this language's import form. Build
**directly impacted** (import/call it) and **transitively impacted** (depend on
those) buckets, then map them to runnable surfaces (route, endpoint, CLI command,
job).

**3. Reason about behavioral impact.** Per impacted site: did a shape, signature,
default, side effect, or invariant change — or is it internal/safe? Separate
**build-caught ripples** (typed languages) from **silent ripples** (cache/memo
key changes, changed default/sort/comparator, serialization or enum drift, global
mutable/persisted state, concurrency & transaction boundaries, locale/time/number
formatting, regex/validation predicates, feature-flag defaults, theme/i18n). In
dynamic languages there's no compiler net — weight this step heavier. Check
**mirror/twin files**: regenerate generated code whose source changed; sync any
constant/enum duplicated across a language boundary. One side edited without the
other is an impact finding even if it compiles.

**4. Verify.** Run the discovered commands, cheapest first, scaled to reach:
typecheck/compile → build → tests (prefer a targeted suite over the full one) →
lint. Then **exercise the impacted surfaces** (start the app/service, hit the
impacted route/endpoint, run the affected command/job) and watch logs/output for
new errors. A green local build is not the same as deployed.

**5. Report** using the template below.

## Report template (always use this)

```
# Code Change Impact: <one-line description>

## Verdict: SAFE | SAFE WITH CAVEATS | IMPACT FOUND

## Project (discovered)
- languages/ecosystems · verify cmds: typecheck/build/test

## Epicenter
- <file:line> — <coupling class> — <what changed>

## Blast radius
### Directly impacted
- <file / surface> — <route/endpoint/command> — <why> — risk: High|Med|Low
### Transitively impacted
- <…> (or "none beyond build-checked usages")

## Mirror / twin files
- <generated/duplicated file to sync> — in sync? yes/NO (or "none")

## Silent-risk callouts (build won't catch)
- <…> (or "none identified")

## Verification
- typecheck/build/tests: PASS/FAIL <summary>
- surfaces exercised: <list> → clean? errors?

## Residual risk & manual checklist
- [ ] <not auto-verifiable — external surface, behavioral edge, deploy step>
```

**Verdict rule** — SAFE: traced, twins in sync, checks pass, surfaces clean, no
silent risk. SAFE WITH CAVEATS: passes but residual manual checks remain (list
them). IMPACT FOUND: a twin is out of sync, a consumer breaks, or a silent ripple
is confirmed — state it plainly with file + fix.

**Scope discipline.** Analyze; don't silently fix unrelated things. Match effort
to reach — a leaf one-liner needs a typecheck and a glance; a shared/contract
change earns the whole pipeline. When unsure a ripple is real, say so and put it
on the checklist rather than declaring SAFE.
