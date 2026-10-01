# The Anti-Generic Law

**Status: BINDING.** This file outranks every aesthetic default in the rest of this skill, in Material's
own documentation, and in the assistant's own habits. Material 3 supplies the *grammar* (tokens, roles,
components, accessibility). It does **not** supply the *voice*. A screen that is 100% MD3-compliant and
still looks like every other MD3 app has **failed this skill**, and compliance is not a defence.

Read this before writing a single line of Compose.

---

## 0. The one law

> **The assistant is forbidden from choosing a design.**
> It must *invent* one, write it down, prove it is not generic, and only then write code.

"Choosing" means reaching for a default, a baseline scheme, a docs sample, a house layout, or the
statistically most likely arrangement of Material components. That is the failure mode this file exists
to kill. Left unchecked, a language model regresses to the mean of its training data, and the mean of
Android UI is a purple card grid with a three-tab bottom bar. **That output is the enemy.**

Three consequences, all non-negotiable:

1. **No generic fallback is available.** There is no "safe" or "clean" default to retreat to when the
   brief is thin. A thin brief is a licence to invent harder, not to reach for the baseline.
2. **Effort is spent before code, not after.** The art direction is a written artefact that exists
   before the first `@Composable`. Retro-fitting personality onto a finished grey app never works.
3. **The gate has teeth.** Failing the audit in §3 means the design is **redone**, not patched.

---

## 1. The Banned List (hard bans — any single hit is an automatic reject)

These are the fingerprints of machine-generated Android UI. They are banned outright. Not
"discouraged" — banned. If the owner explicitly asks for one of them, that is the one exception, and it
gets recorded in `ART_DIRECTION.md` as an owner override with their words quoted.

### 1.1 Colour

| # | Banned | Why it reads as slop |
|---|--------|---------------------|
| B1 | **`#6750A4`** and its baseline family — `#EADDFF`, `#625B71`, `#E8DEF8`, `#7D5260`, `#FFD8E4`, `#FFFBFE`, `#1C1B1F` | The literal Material Theme Builder default. Instantly recognisable as "nobody made a decision here". |
| B2 | Dynamic colour (`dynamicLightColorScheme`) as the app's **only** identity | Wallpaper-derived colour is a *user setting to offer*, never the brand. An app whose look is decided by the phone has no look. |
| B3 | Untinted neutrals — `Color.White`, `Color.Black`, `#FFFFFF`, `#000000`, pure greys (`#F5F5F5`, `#EEEEEE`, `#9E9E9E`, `#212121`) as surfaces | Real design tints every neutral toward the brand hue. Pure grey is the absence of a decision. |
| B4 | More than **two** accent hues competing for attention | Everything shouting equals nothing landing. |
| B5 | Hijacked semantic roles — a green `error`, a brand-coloured `error`, red used decoratively | Breaks the user's learned meaning; also a real usability bug. |
| B6 | Dark mode produced by inverting the light scheme | See §4. Dark mode is a design, not a filter. |

### 1.2 Type

| # | Banned | Why |
|---|--------|-----|
| B7 | Roboto (or Roboto Flex) as the **only** typeface, at default weights, with no display face | Roboto is the correct MD3 *default* and therefore the loudest possible signal that no typographic choice was made. Use it as a workhorse if you like, but never alone. |
| B8 | No scale contrast — nothing on the screen larger than ~3× the body size | Flat type hierarchy is the visual signature of a generated layout. |
| B9 | Untouched tracking and line-height on display/headline sizes | Large type needs negative tracking. Leaving it default looks amateur at 40sp+. |

### 1.3 Layout

| # | Banned | Why |
|---|--------|-----|
| B10 | **Card soup** — a uniform grid or column where every item is the same size, same 12dp corner, same 16dp gap, same elevation | The single most common AI-Android tell. |
| B11 | `ElevatedCard` everywhere with shadow as the depth cue | MD3 communicates depth through *tonal surface colour*. Shadow-first is an MD2 habit. |
| B12 | Every screen sharing one skeleton (top bar → `LazyColumn` of cards) | Real apps have differently-shaped screens. |
| B13 | Total symmetry — everything centred, uniform padding, no full-bleed, no overlap, no deliberate asymmetry | Timid. Nothing to look at. |
| B14 | The **Home / Search / Profile** bottom bar using `Icons.Filled.Home` · `Search` · `Person` | The default of defaults. |
| B15 | Mixed icon styles in one screen (Filled + Outlined + Rounded + custom) | Reads as assembled, not designed. |

### 1.4 Motion and depth

| # | Banned | Why |
|---|--------|-----|
| B16 | `AnimatedVisibility` fade as the app's **only** motion | Fade is the absence of motion design. |
| B17 | Zero shared-element or continuity transition between a list and its detail screen | Modern Android has `SharedTransitionLayout`. Not using it is leaving the app feeling 2016. |
| B18 | Glass/blur applied to 3+ surface types, or with no consistent light source | Decoration without logic. Pick where the light is and honour it. |
| B19 | Stock pastel/rainbow mesh-gradient blobs with no narrative reason | The "AI app" background. Every one of them. |

### 1.5 Content and brand

| # | Banned | Why |
|---|--------|-----|
| B20 | Placeholder copy: *"Welcome back!"*, *"Get Started"*, *"Discover amazing content"*, *"No items yet"*, *"Item 1/2/3"*, *"John Doe"*, *lorem ipsum* | Copy is design. Generic copy makes a beautiful layout feel fake. |
| B21 | Centred grey outline icon + grey sentence + filled button as the empty state | Empty states are the highest-leverage personality moment in an app and are almost always wasted. |
| B22 | No designed loading state (a bare spinner instead of shaped skeletons) | Spinners say "we didn't think about waiting". |
| B23 | A logo that is a circle or rounded square containing one letter | Not a mark. A placeholder. |
| B24 | Emoji used as UI iconography | Renders differently per OEM, breaks tinting, reads as unfinished. |
| B25 | No adaptive icon layers / no monochrome themed icon (Android 13+) | The launcher is the first screen. Shipping a flat PNG there wastes it. |

---

## 2. The Art Direction Gate (pre-code, mandatory, written)

**No `@Composable` may be written until all nine answers below exist in the project's
`ART_DIRECTION.md` and have been shown to the owner in plain language.**

Each answer carries a **reject rule**. If an answer trips its reject rule, it is not an answer — write a
different one. Waffle is a reject. "Modern and clean" is a reject.

---

### A1 · The Concept Line
One sentence. It must name a **thing that exists in the physical world** — an object, a place, a craft,
a material, a era, a piece of equipment.

> *Example:* "A brass-and-enamel dive watch from 1968 — dense, precise, glowing faintly in the dark."

**Reject if:** it names an app, a website, or a competitor · it contains any of *modern, clean, sleek,
minimal, minimalist, intuitive, user-friendly, beautiful, premium, elegant, seamless, cutting-edge* ·
it could describe more than one app.

### A2 · The Anti-Reference
Name, specifically, what this app must **not** look like.

> *Example:* "Not a Material Theme Builder demo. Not a crypto dashboard. No purple, no glassmorphism."

**Reject if:** vague ("not boring") · doesn't name at least one concrete look being refused.

### A3 · The Signature Move
The **one** thing a user would describe to a friend. One interaction or one visual, not a list.

> *Example:* "Pulling down doesn't refresh — it physically winds the watch, and the crown resists."

**Reject if:** it's a standard transition · it's "smooth animations" or "nice haptics" · it appears in
the last five entries of the no-sameness ledger (§5).

### A4 · The Palette
Seed hex + the role hexes derived from it + one accent used **sparingly** + tinted neutrals. Every hex
written out. One line of *why* per colour.

**Reject if:** any hex on the Banned List (B1, B3) · the accent covers more than ~10% of any screen ·
neutrals are untinted (they need ≥3% chroma pulled toward the brand hue) · more than two accents (B4) ·
`error` is not red-family (B5) · light and dark were not chosen independently (B6).

### A5 · The Type Pair
A named **display face** + a named **text face**, with weights, and tracking for the display sizes.

> *Example:* Display **Instrument Serif** 400, tracking −2%; Text **Geist** 400/500/600.

**Reject if:** Roboto alone (B7) · no display face · the faces are not actually obtainable (Google Fonts
downloadable in Compose, or bundled in `res/font`) · no tracking decision on display sizes (B9).

### A6 · The Shape Language
**One** shape idea, carried through the whole app.

> *Examples:* a single 28dp corner on one side of every container, others at 4dp · squircle at 40%
> smoothing · hard 0dp everywhere with exactly one 32dp exception (the primary action).

**Reject if:** "12dp everywhere" · uses MD3's default shape scale unmodified · no rule that a future
component could be checked against.

### A7 · The Motion Signature
Concrete numbers. Spring spec, stagger interval, the one delight moment, and what the shared-element
transition is.

> *Example:* `spring(dampingRatio = 0.55f, stiffness = 380f)` on every entrance; list items stagger at
> 40ms; the delight moment is the crown-wind on pull; list→detail uses `SharedTransitionLayout` on the
> dial image.

**Reject if:** fade-only (B16) · no shared-element plan (B17) · uses only `tween` defaults · no numbers.

### A8 · The Depth Story
Where the light comes from, how many z-layers exist, and what casts onto what. Then: which one of the
glass/blur libraries (if any) — **exactly one, never stacked** — and the API-33 fallback chain.

**Reject if:** "elevation 1–3, default" · glass on 3+ surface types (B18) · no fallback for pre-API-33.

### A9 · The Asset Manifest
Exactly which visual assets get **generated**, from which lane, with the actual prompt seed for each.
Covers: app icon (adaptive layers + monochrome), splash, illustration set, empty-state art, textures,
any 3D hero, and the density set.

**Reject if:** empty · relies on stock/clip-art · no icon plan (B25) · doesn't name the lane per asset.
See **`asset-generation.md`** for how to produce each one — **cloud APIs and MCP servers are tried first**
(Google Stitch for screens and design systems; for images **NVIDIA `flux.1-dev` → Cloudflare Workers AI →
HuggingFace**, all three verified working; Meshy for 3D; Penpot/Figma for design files; NVIDIA VLMs and
Mistral for vision-reading and OCR), with the local ComfyUI stack as the fallback.

**A generator never chooses the direction.** Stitch, FLUX and Meshy are told what A1–A8 already decided.
A generic Stitch screen is exactly as much a reject as a generic Compose screen — run §3 on generated
output too.

---

### The gate's output

`ART_DIRECTION.md`, written into the project root, containing A1–A9 verbatim plus a one-paragraph
plain-language summary for the owner. It is a **contract**: every later component decision is checked
against it, and any component that can't be justified by A1–A9 does not ship.

---

## 3. The Slop Detector (post-build audit — run before every delivery)

Score each row **HIT** or **CLEAR** against the built app (screenshots, or the Compose source).

**Pass bar, both conditions required:**
- **Any hard-ban hit (B1–B25) = automatic reject.** No score needed.
- **2 or more soft hits below = reject.** 0–1 is the only ship state.

**Reject means redo the direction, not patch the symptom.** Recolouring a card grid does not fix a card
grid.

| # | Check | HIT if… |
|---|-------|---------|
| S1 | Concept legibility | A stranger shown 3 screenshots can't guess A1's real-world reference |
| S2 | Signature present | A3 doesn't actually appear in the build |
| S3 | Scale contrast | No element ≥3× the body type size on any primary screen |
| S4 | Shape rule held | Any container violates A6 without a written reason |
| S5 | Accent discipline | The accent exceeds ~10% coverage, or appears on every screen equally |
| S6 | Neutral tinting | Any surface is a pure/untinted grey |
| S7 | Dark mode identity | Dark is recognisably the same design, not an inversion, and not just darker greys |
| S8 | Motion identity | Entrances share no spring; nothing staggers; no delight moment fires |
| S9 | Continuity | List→detail has no shared element |
| S10 | Screen variety | Two or more screens share the same skeleton with only content swapped |
| S11 | Empty state | Any empty state is the centred-icon-plus-grey-text pattern |
| S12 | Loading state | Any load shows a bare spinner instead of a shaped skeleton |
| S13 | Copy quality | Any string is placeholder-grade, or could belong to any app |
| S14 | Icon coherence | Two icon styles coexist in one screen |
| S15 | Launcher | Adaptive foreground/background + monochrome layers not all present |
| S16 | Asset originality | Any shipped image is stock, clip-art, or an un-restyled vault asset |
| S17 | Density set | Raster assets missing xxhdpi/xxxhdpi, or not WebP/vector where they could be |
| S18 | Contrast | Body text below 4.5:1, or UI borders/large text below 3:1 |
| S19 | Full-bleed | Nothing on any screen breaks the padding — no edge-to-edge image, no overlap |
| S20 | Ledger distinctness | Palette **and** type **and** signature all match a ledger entry from the last 10 apps |

### The three tests worth running by hand

1. **The squint test.** Blur every screenshot to unreadability. Can you still tell the screens apart,
   and still name the brand colour? If every screen becomes the same grey rectangle, S3/S10 are hits.
2. **The stranger test.** Show three screens, no app name, and ask what world it's from. If nobody
   reaches A1's reference, the direction didn't land (S1).
3. **The line-up test.** Put this app's icon beside the last ten from the ledger. If a stranger can't
   pick it out in 2 seconds, S20 is a hit.

---

## 4. Dark mode is a design, not a filter

`B6` bans inversion. What replaces it:

- **Choose the dark palette independently.** Different seed *tone*, not the same one flipped. Dark UI
  can carry more chroma before it screams; light UI needs more.
- **Never `#000000`.** Use a tinted near-black — pull 4–8% of the brand hue in (e.g. a warm brass app
  goes to `#12100C`, not `#000000`). OLED true-black is a deliberate choice for one surface at most.
- **Elevation inverts direction.** In light, higher = lighter. In dark, higher = *lighter too*, via
  tonal `surfaceContainer*` steps — not via shadow, which is invisible on dark.
- **Desaturate the accent 10–20%** in dark, or it vibrates against the dark surface.
- **Imagery needs its own treatment** — a scrim, a reduced-brightness variant, or a different crop.
  The same photo rarely works in both modes.
- **Decide which mode is canonical.** One of them is the app's "real" look and gets designed first. Say
  which in A4.

## 5. The no-sameness ledger

**Cardinal rule (inherited from AndroidGo): twenty apps must look like twenty different studios made
them.** Enforcement is mechanical, not aspirational.

Before finalising A1–A9, read **`no-sameness-ledger.md`** in this folder. After shipping, append one
row: date · app · concept line · palette seed · type pair · signature move.

**The rule:** the new app must differ from **every** entry in the last ten on at least **two** of these
three axes — palette family, type pair, signature move. Matching on all three is S20, an automatic
reject. Matching a *concept line* at all is a reject regardless of score.

## 6. When the brief is thin

The most dangerous input is "make me a notes app". Thin briefs are where regression to the mean
happens. The procedure is **not** to ask twenty questions and stall, and **not** to fall back on
defaults. It is:

1. **Invent three directions**, fully distinct — different A1, A4, A5, A6, A7 for each. Use the
   archetype tables in `art-direction-engine.md` as raw material, never as a copy-paste answer.
2. **Show the owner all three** in plain language — one sentence and one colour-and-type description
   each. No jargon.
3. **Let them pick, or pick the boldest yourself and say so.** Per the AndroidGo doctrine, when
   unsure, take the bolder option.
4. Then run the gate on the winner.

Inventing three costs minutes and is the single highest-leverage thing in this file.

## 7. What Material 3 still owns

This law does not license breaking Material. MD3 keeps authority over:

- **Token architecture.** Colour comes from a real scheme with proper `onX` pairing; never hardcode
  hex into a component. A bespoke palette is *installed as* `MaterialTheme.colorScheme`.
- **Component structure and behaviour.** A `Switch` is a `Switch`. Restyle it; don't reinvent its
  semantics.
- **Navigation selection.** The bar/rail/drawer rules in `navigation-patterns.md` hold regardless of
  art direction.
- **Accessibility floors.** Contrast (S18), ~48dp touch targets, TalkBack semantics, focus order.
- **Adaptive layout.** Window size classes, readable max width on wide screens, hinge avoidance.

The split is simple: **MD3 decides *what* a thing is. This law decides *how it looks and feels*.**

## 8. Companion files

| File | Role |
|------|------|
| `art-direction-engine.md` | The invention library — archetypes, palettes, type pairs, signature moves, colour and logo science |
| `asset-generation.md` | How to actually produce icons, illustration, textures, 3D and UI screens. **Cloud APIs + MCP first** (Stitch; images via **NVIDIA `flux.1-dev` → Cloudflare Workers AI → HuggingFace**; Meshy, Penpot, NVIDIA VLMs, Mistral), local ComfyUI as fallback |
| `no-sameness-ledger.md` | The running record that makes §5 enforceable |
| `component-catalog.md` | MD3 components (the grammar) |
| `navigation-patterns.md` | Navigation selection rules (still binding) |
| `color-system.md` · `typography-and-shape.md` | MD3 token mechanics |
