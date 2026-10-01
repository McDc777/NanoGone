# Recipes — per-ecosystem detection, reverse-deps, coupling, silent risks

Companion to `SKILL.md`. Read the section for the language(s) the change touches.
All `rg` (ripgrep) commands fall back to `grep -rn` if `rg` is absent.

## Table of contents
1. Reverse-dependency recipes by ecosystem
2. Detecting verify commands (typecheck / build / test / lint)
3. Generic coupling taxonomy
4. Mirror/twin files + silent-risk catalog
5. Surface-mapping cheats

---

## 1. Reverse-dependency recipes by ecosystem

The universal move: take the changed file's **module identifier** (its import
path / package / namespace) and the **changed symbol name**, then grep for who
imports the module and who calls the symbol. Per language:

### JavaScript / TypeScript
- Imports: `rg -n "from ['\"].*<modulePathOrPkg>['\"]" <root> -l` and
  `rg -n "require\(['\"].*<module>" <root> -l`.
- Path aliases: read `tsconfig.json` / `jsconfig.json` `compilerOptions.paths`
  (and bundler config). Translate an alias like `@/x` → `src/x` so a file change
  maps to the alias importers grep.
- Barrel files (`index.ts`) re-export — a symbol is usually imported via the
  nearest barrel, so grep the **symbol name**, not just the file path:
  `rg -n "\b<Symbol>\b" <root> --type ts -l`.

### Python
- Imports: `rg -n "^\s*(from|import)\s+<dotted.module>" <root>`.
- A package `__init__.py` re-exports; grep the symbol too:
  `rg -n "\b<symbol>\b" <root> -t py -l`.
- No compiler — signature changes are silent. If `mypy`/`pyright` is configured,
  run it; otherwise lean on tests + grep.

### Go
- Imports: grep the module's import path `rg -n "\"<module/import/path>\"" <root>`.
- Exported identifiers are Capitalized; `rg -n "\b<pkg>\.<Symbol>\b" <root>`.
- `go build ./...` and `go vet ./...` catch most ripples.

### Java / Kotlin
- Imports: `rg -n "import\s+<package>\.<Class>" <root>` (and wildcard
  `import <package>.*`).
- Symbol use: `rg -n "\b<Class>\b" <root> -l`.

### C# / .NET
- `using <Namespace>;` + symbol grep `rg -n "\b<Type>\b" <root> -l`.
- Solutions/projects: `*.sln` / `*.slnx` / `*.csproj`.

### Ruby
- `rg -n "require(_relative)?\s+['\"].*<file>" <root>` + constant grep
  `rg -n "\b<ClassOrModule>\b" <root> -l`.

### Rust
- `rg -n "use\s+(crate|super|<crate_name>)::.*<item>" <root>` + symbol grep.
- `cargo build` / `cargo clippy` catch ripples.

### PHP
- `rg -n "use\s+<Namespace>\\\\<Class>" <root>` + `new <Class>` / `<Class>::` grep.

### Cross-cutting (any language)
- Config/contract by string, not import: an endpoint path, env var, feature-flag
  key, DB table/column, or message-type name — grep the literal string repo-wide:
  `rg -n "<literal>" <root>`.

---

## 2. Detecting verify commands

Read the project's own commands; don't invent them.

| Ecosystem | Where commands live | Typical typecheck / build / test / lint |
|---|---|---|
| JS/TS | `package.json` `scripts` | `tsc --noEmit` · `build` · `test`/`vitest`/`jest` · `eslint` |
| Python | `pyproject.toml`, `tox.ini`, `noxfile`, `Makefile` | `mypy`/`pyright` · — · `pytest` · `ruff`/`flake8` |
| Go | `go.mod`, `Makefile` | `go build ./...` · same · `go test ./...` · `go vet` |
| Java/Kotlin | `pom.xml`, `build.gradle` | `mvn compile` / `gradle build` · same · `mvn test` · checkstyle |
| C#/.NET | `*.sln`/`*.csproj` | `dotnet build` · same · `dotnet test` · analyzers |
| Ruby | `Rakefile`, `Gemfile` | — · — · `rspec`/`rake test` · `rubocop` |
| Rust | `Cargo.toml` | `cargo check` · `cargo build` · `cargo test` · `cargo clippy` |
| PHP | `composer.json` `scripts` | `phpstan`/`psalm` · — · `phpunit`/`pest` · `php-cs-fixer` |

Also check `Makefile`, `justfile`, and the CI config (`.github/workflows/*`,
`.gitlab-ci.yml`) — CI is the canonical list of "what must pass". Note any
**targeted** test scripts (named per feature/area); they verify a specific blast
radius far cheaper than the full suite.

---

## 3. Generic coupling taxonomy

How reach scales by what kind of thing changed:

- **Shared / core / common / util** — imported widely; wide reach. Internal-only
  edits (no signature/behavior change) are safe — confirm that's all it is.
- **Public API surface** — exported function/class, package public symbol,
  barrel/index re-export, a library's published interface. Every caller (and, for
  a library, downstream repos) is in scope. Behavior changes without a version
  bump are silent breakage for consumers.
- **Type / interface / schema** — wide. Typed languages get a compiler net;
  dynamic languages do not — weight behavioral reasoning heavier there. Widening
  or narrowing an enum/union silently breaks exhaustive switches.
- **Serialized contract** — REST/GraphQL endpoint, DTO, protobuf/Avro message,
  event payload, queue message. The other side of the wire must agree; FE and BE
  (or producer and consumer) compile independently, so a shape drift is a runtime
  mismatch, not a build error.
- **Config / registry** — route table, DI container, plugin/extension registry,
  feature-flag set, middleware chain. Downstream behavior is *derived* from these,
  so an add/remove/reorder fans out.
- **DB schema / migration / model** — touches every query and ORM model over that
  table, plus any other service reading it. Migrations have an ordering/rollback
  dimension and an environment-apply dimension.
- **Global config / styles / theme / i18n** — no compiler signal; global reach. A
  token/string/locale change can shift things far from the edit.
- **Build / deps / lockfile / container / CI** — affects the whole app's build and
  runtime; a transitive dependency bump can change behavior with no source diff.

---

## 4. Mirror/twin files + silent-risk catalog

### Finding mirror/twin files (must change in lockstep)
A diff that edits one side and not the other is an impact finding even when it
compiles. Look for:
- **Generated code** — a `// Code generated` / `@generated` / "DO NOT EDIT"
  header, or a codegen config (OpenAPI/Swagger, protobuf, GraphQL codegen, ORM
  scaffolds). If the **source** schema changed, the generated client/types must be
  regenerated; if someone hand-edited generated output, that's a smell.
- **Cross-language duplicated constants** — the same enum, status code, error
  code, or validation rule defined in both a frontend and a backend (or two
  services). Grep the literal values across the repo to find the twin.
- **Paired fixtures / golden files / snapshots** — a change in logic often
  requires updating recorded expectations.
- **Docs/specs that encode behavior** — an OpenAPI spec, a `.proto`, a
  CODEOWNERS-flagged contract file.

### Silent-risk catalog (build won't catch)
Hunt these when the change is in shared/contract/registry/global code:
- **Cache / memoization key change** — invalidations that targeted the old key
  stop matching (stale data); a key that now collides serves the wrong cached
  value.
- **Changed default value / sort order / comparator / rounding** — same types,
  every caller inherits new behavior.
- **Serialization drift** — a field made nullable/optional, an enum value added,
  a field renamed, a default changed: the decoder on the other side may break or
  silently mis-map. (Watch the empty/null → "no content" case in HTTP clients.)
- **Global mutable state / singleton shape** — config object, service locator,
  context provider, persisted client state (localStorage/session) holding an old
  shape after an upgrade.
- **Concurrency & transactions** — a changed lock scope, transaction boundary,
  retry policy, or async ordering can corrupt or deadlock without a type error.
- **Locale / time zone / number & date formatting** — output changes by
  environment, not by type.
- **Regex / validation predicate** — a tweak that accepts/rejects different
  inputs than before.
- **Feature flag / env var default** — flipping a default changes behavior for
  everyone not explicitly overriding it.
- **Error-handling control flow** — turning a thrown error into a swallowed one
  (or vice versa) reroutes callers silently.
- **Theme / token / i18n string** — global visual or copy change with no compiler
  signal.

---

## 5. Surface-mapping cheats

The report is most useful when it names runnable surfaces, not just files. To map
impacted code → a thing a tester can exercise:
- **Web app** — find the route/page that renders the impacted component (route
  table / router config), and the API endpoint the impacted service calls.
- **Service/API** — find the controller/handler exposing the impacted logic and
  its path + method; note the consumers (clients, other services, jobs).
- **CLI** — find the command/subcommand wired to the impacted function.
- **Library** — there's no app surface; the "surface" is the public API + its
  tests, and downstream consumers if in the same monorepo.
- **Background job / queue** — find the worker/handler bound to the impacted code
  and the trigger (schedule, message type).
