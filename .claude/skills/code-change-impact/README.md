# code-change-impact

Trace the **impact / blast radius** of a code change in any codebase and prove it
didn't break anything else. Run it right after a fix — before committing, opening
a PR, or pushing. It discovers the project's own conventions (languages, build/
test commands, import style), traces what depends on the change, hunts the silent
ripples a compiler won't catch, runs the verification, and reports a verdict:
**SAFE / SAFE WITH CAVEATS / IMPACT FOUND**.

Language- and framework-agnostic: JS/TS, Python, Go, Java/Kotlin, C#, Ruby, Rust,
PHP, and more.

## What's in here

| File | For |
|---|---|
| `SKILL.md` | **Claude Code / Claude agents** — the source-of-truth method |
| `references/recipes.md` | Shared deep detail: per-language grep recipes, verify-command detection, coupling taxonomy, mirror-file finder, silent-risk catalog |
| `adapters/AGENTS.md` | **OpenAI Codex CLI** & any `AGENTS.md`-aware agent |
| `adapters/code-change-impact.mdc` | **Cursor** rule |
| `adapters/code-change-impact.prompt.md` | **VS Code + GitHub Copilot** prompt file |

The `SKILL.md` format is Claude-native — the other tools don't read it. Each
adapter carries the same procedure in that tool's own format. Keep them in sync if
you edit the method; `SKILL.md` is the source of truth.

## Install per tool

### Claude Code (and the Claude Code VS Code / JetBrains extensions)
Copy the folder into your skills dir:
```bash
# global (all projects)
cp -r code-change-impact ~/.claude/skills/
# or per-repo (commit it, the team gets it)
cp -r code-change-impact <repo>/.claude/skills/
```
**Trigger:** auto — the model invokes it by description when you ask things like
"check the impact of this change". Or invoke explicitly: `/code-change-impact`.

### OpenAI Codex CLI
Codex reads `AGENTS.md`. Append the adapter into your repo-root `AGENTS.md` (or
`~/.codex/AGENTS.md` for all repos), and keep `references/recipes.md` nearby:
```bash
cat code-change-impact/adapters/AGENTS.md >> AGENTS.md
mkdir -p code-change-impact && cp code-change-impact/references/recipes.md code-change-impact/
```
**Trigger:** it stays dormant until you ask Codex to "check the impact / blast
radius of this change" (or before finalizing a shared/contract change).

### Cursor
Cursor reads rules from `.cursor/rules/`:
```bash
mkdir -p <repo>/.cursor/rules
cp code-change-impact/adapters/code-change-impact.mdc <repo>/.cursor/rules/
```
**Trigger:** "Agent Requested" — Cursor's agent pulls the rule in when your task
matches its `description`. You can also `@code-change-impact` to force it.

### VS Code + GitHub Copilot
Copilot reads prompt files from `.github/prompts/` (enable
`chat.promptFiles` in settings):
```bash
mkdir -p <repo>/.github/prompts
cp code-change-impact/adapters/code-change-impact.prompt.md <repo>/.github/prompts/
```
**Trigger:** type `/code-change-impact` in Copilot Chat (agent mode).

> "VS Code" by itself isn't an agent — it runs one via an extension. If you use
> the **Claude Code** extension, follow the Claude Code steps (SKILL.md). If you
> use **Copilot**, use the prompt file above. Cline/Roo users can paste the
> `.mdc` body into their rules.

## Note on triggering

Auto-trigger behavior differs per tool — that's a property of each tool's agent,
not the skill. The *method and report are identical everywhere*. When in doubt,
invoke it explicitly (the slash command or an @-mention) rather than relying on
auto-trigger.
