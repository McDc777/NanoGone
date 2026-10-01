---
name: compose-design-vault
description: >
  Design-intelligence layer for Android/Compose app production: a growing categorized UI
  design vault (D:\AI\DesignVault — true counts in its INDEX.md; buttons, cards, loaders,
  inputs, forms, switches, checkboxes, radio buttons, patterns, tooltips and toggles;
  uiverse.io + CodePen code with per-item MIT provenance, curated in part via the
  codemyui.com and cssbuttons.io catalogs; resumable HTTP harvesters can sweep all 7,418
  uiverse designs),
  2026 trend digests with sources (M3 Expressive, Compose 1.11/1.12 APIs, glassmorphism
  revival, bento, neubrutalism), per-component design-idea generators, and a CSS-to-Compose
  translation cookbook. Use WHENEVER inventing an app's art direction, brainstorming screen/component
  design ideas, needing "several design options", translating a web design or CodePen/CSS
  effect into Compose, or asking what's current in UI design. Composes with (never replaces)
  android-compose-expert and android-skills:compose (they teach HOW to code Compose; this
  skill supplies WHAT to design and the raw material). Feeds the Art Direction Gate — it
  never replaces it.
metadata:
  version: "1.0.0"
---

# Compose Design Vault

The design-intelligence layer of the AndroidGo pipeline. Three assets in one skill:

1. **The Vault** — `D:\AI\DesignVault\` — categorized, licensed, growing collection of real
   UI designs (HTML/CSS/JS) with true-count indexes. Raw material, never final answers.
2. **Trend intelligence** — what is current in 2026, every claim with a source URL.
3. **The translation cookbook** — how any web/CSS design concept becomes real Compose code.

## ⚖️ The one law that governs this skill (binding)

**The vault is raw material for INVENTION, never a menu to choose from.** The anti-generic
law (`android-material3-design/references/anti-generic-doctrine.md`) still decides every
app's direction: the Art Direction Gate (A1–A9, written to `ART_DIRECTION.md`) runs FIRST
and invents this app's own identity. Only then does this skill fire, in two lawful modes:

- **Ideation mode** (before/during the Gate): pull several *distinct directions* from
  `references/design-idea-engine.md` + trend digests to stretch the invention — three or
  more genuinely different candidate identities, never one "best pick".
- **Execution mode** (after the Gate): mine the vault for *mechanics* — how a gooey toggle
  moves, how a neon border animates, how a card layers its depth — and translate those
  mechanics through `references/css-to-compose.md` INTO the invented direction's own
  palette, type, and shape language.

Copying a vault design's look wholesale into an app is a Slop Detector failure (sameness
amplification). Twenty apps must still look like twenty studios.

## Quick routing

| Need | Read |
|------|------|
| What's new/current in 2026 (APIs + visual trends, sourced) | `references/trends-2026.md` |
| Several design ideas for a component or screen | `references/design-idea-engine.md` |
| Turn a CSS/web design mechanic into Compose code | `references/css-to-compose.md` |
| Find designs in the vault / grow the vault / provenance rules | `references/vault-guide.md` |

## The vault at a glance

- Location: `D:\AI\DesignVault\` — master `INDEX.md` (true on-disk counts, regenerate with
  `_tools/build_index.py`), then `<site>/<category>/<author>__<slug>/` items, each holding
  `markup.html` + `style.css` (+ `script.js`) + `meta.json` (source URL, author, license,
  license evidence).
- Sources so far: **uiverse.io** (site-wide MIT, stated in its footer — **7,418 designs
  across 11 element types**, all reachable; `sweep_all.sh` harvests the lot), **CodePen**
  public pens (MIT per CodePen's licensing docs), **codemyui.com** and **cssbuttons.io**
  (curation catalogs only — the code is harvested from the underlying pen / uiverse post),
  plus three **MIT component repos imported not scraped** — `markmead/hyperui` (560 Tailwind
  examples), `ekmas/neobrutalism-components` (92, the neobrutalism style) and
  `themesberg/flowbite` (631).
  ⚠ cssbuttons.io does **not** carry an MIT footer, whatever this skill said before —
  see the correction in `references/vault-guide.md`. All harvesters in
  `D:\AI\DesignVault\_tools\` are resumable and now run over **plain HTTP** (Remix payload,
  no browser, ~0.5 s/item); see `references/vault-guide.md` for the exact commands.
- The index never lies: counts are computed from disk at generation time, and a malformed
  item (missing license/source) fails the index build.

## How this composes with the other skills

- `android-material3-design` — owns the Art Direction Gate + Slop Detector. This skill
  feeds it ideas and mechanics.
- `android-compose-expert` / `android-skills:compose` — own deep Compose correctness
  (state, layout, performance, navigation). When translating vault mechanics, verify APIs
  against their references and live androidx sources exactly as they mandate.
- `kmp-liquid-glass` — owns native Compose glass/liquid-glass recipes; any glassmorphism
  mechanic found in the vault routes through it (house rule: pick ONE glass lib per
  project, never stack).
- Asset generation (images/icons) stays with `android-material3-design/references/`
  `asset-generation.md` cloud-first routing — this skill is about DESIGN, not image gen.
