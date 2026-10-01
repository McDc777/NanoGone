# 2026 trend intelligence — five groups, every claim sourced

Researched 2026-08-15 by five separate web sweeps (one per group). **Rule inherited from the
Compose expert skills: never state an API or trend as fact without its source; re-verify
volatile claims against the live source at use time.** Each claim below carries the URL it
was taken from.

---

## Group 1 — Screen basics: state, layout, structure

**Compose 1.12 (August 2026 release)** — source:
<https://android-developers.googleblog.com/2026/08/jetpack-compose-august-2026-release.html>
- `SideEffect` gained a **keyed overload** — side-effect execution with key arguments,
  up to 90% faster than `LaunchedEffect` for that use.
- **Grid named areas** — define semantic regions in `GridConfigurationScope`, place items
  via `gridItem(areaId = "name")` (CSS-grid-template-areas, natively).
- `credentialRequest` **semantics property** — integrates Android Credential Manager for
  passkey prompts straight from a text field.
- New `KeyboardType`s: `Date`, `Time`, `DateTime`, `SignedDecimal`.
- Rich `BasicTextField` formatting: `addStyle()`, `getSpanStyles()`, `getParagraphStyles()`;
  programmatic selection via the new `SelectionState` API (`selectAll()`, `select(TextRange)`,
  `extendSelectionByWord()`).
- Requires compileSdk 37 + AGP 9.1.1.

**Compose 1.11 (April 2026 release)** — sources:
<https://android-developers.googleblog.com/2026/04/jetpack-compose-april-2026-updates.html>,
<https://developer.android.com/blog/posts/whats-new-in-the-jetpack-compose-april-26-release>
- **New APIs: Styles, MediaQuery, Grid, FlexBox** — web-grade layout primitives natively.
- v2 testing framework became the default (v1 deprecated); first-class trackpad support.
- New `SlotTable` implementation — faster random edits of the composition (ships disabled
  by default behind `ComposeRuntimeFlags.isLinkBufferComposerEnabled`).

**The `retain{}` API (in development, AOSP)** — source:
<https://proandroiddev.com/exploring-retain-api-a-new-way-to-persist-state-in-jetpack-compose-bfb2fe2eae43>
- Lets values outlive recomposition AND transient removal from the hierarchy —
  ViewModel-like retention in a purely composable, lifecycle-independent way. Watch it;
  do not ship claims about its final shape until it lands in a release.

**Design consequence for apps we build:** grid named-areas + FlexBox make bento layouts
(Group 5) natural in pure Compose; the keyed SideEffect trims effect boilerplate in
animation-heavy screens.

---

## Group 2 — Movement: animation, navigation, transitions

**Compose 1.12 animation** — source:
<https://android-developers.googleblog.com/2026/08/jetpack-compose-august-2026-release.html>
- **`DeferredAnimatedContent` / `DeferredAnimatedVisibility`** — two-stage transitions:
  gesture-driven phase hands off to an automatic phase (built for predictive back).
- **`DeferredTargetAnimation`** graduated from experimental — manual real-time control of
  animated properties (scale, offset) during a transition's deferred phase.
- `permitTransformDuringDeferredTransition` flag in `SharedContentConfig` — controls
  whether shared elements transform with their parent containers in the deferred phase.

**Shared element transitions (stable canon)** — source:
<https://developer.android.com/develop/ui/compose/animation/shared-elements>
- `SharedTransitionScope` + `Modifier.sharedElement()` for individual elements and
  `Modifier.sharedBounds()` for containers; one configuration animates both directions.
- Newer additions (compose-animation 1.10.0-alpha01, August 2025): dynamically
  enable/disable shared elements; alternative target bounds when the target is disposed
  mid-transition — source:
  <https://developer.android.com/jetpack/androidx/releases/compose-animation>

**What "premium motion" means in 2026:** physics springs by default (see Group 3's M3
Expressive), gesture-driven interruptible transitions, and shared-element continuity on
every navigation. A fade-only app reads as template work (the anti-generic Banned List
already bans it).

---

## Group 3 — Looks: theming, typography, shape, color

**Material 3 Expressive** — the current Google design language. Sources:
<https://blog.google/products-and-platforms/platforms/android/material-3-expressive-android-wearos-launch/>,
<https://www.androidauthority.com/google-material-3-expressive-features-changes-availability-supported-devices-3556392/>,
<https://supercharge.design/blog/material-3-expressive>,
<https://developer.android.com/develop/ui/compose/designsystems/material3>
- Research-backed expansion of M3: bolder motion, typography, shape usage, "emotional
  clarity"; an evolution of Material You, not a replacement.
- **Motion:** springier physics-based system; morphing shapes communicate state changes.
- **Type:** upgraded scale (Display/Headline/Title/Body/Label, each small/medium/large)
  with new attention-grabbing styles.
- **Shape:** five roundedness levels (extra-small → extra-large) + shape morphing.
- **Color:** richer dynamic-color palettes, clearer primary/secondary/tertiary separation.
- Debuted on Pixel with Android 16 QPR1 (Sept 2025); the 2026 baseline for "current".

**Compose 1.12 visual APIs** — source:
<https://android-developers.googleblog.com/2026/08/jetpack-compose-august-2026-release.html>
- **`MeshGradientPainter`** — multi-point organic gradients (vertex positions + colors):
  the aurora/mesh look natively, no shader hand-rolling.
- **Wide Color Gamut (P3) + HDR rendering** — saturated beyond-sRGB color as identity.
- **`LayerOutsets`** — expand a `GraphicsLayer`'s visual bounds beyond measured size
  (glow/shadow effects without implicit clipping).
- Variable-font settings now work with downloadable fonts.
- `SoundEffectOnInteraction` — composable controlling (incl. opting out of) automatic
  interaction sounds, so custom audio feedback can replace the system defaults.

**House integration:** these feed the Art Direction Gate's palette/type/shape answers —
M3 Expressive is the floor, the invented identity is the point (anti-generic law).

---

## Group 4 — Not breaking: performance, a11y, crash-resistance

**Performance canon** — sources:
<https://developer.android.com/develop/ui/compose/performance/bestpractices>,
<https://developer.android.com/develop/ui/compose/performance/stability/strongskipping>,
<https://github.com/skydoves/compose-performance>
- Strong skipping mode is enabled by default since Kotlin / Compose-compiler 2.0.20:
  stable-parameter functions skip automatically.
- The living rules: defer state reads (lambda-based `Modifier.graphicsLayer { }` over
  argument-based when animating), `derivedStateOf` for computed state, lazy-list `key`s,
  no backwards writes, `remember` expensive work, keep state low and scoped.
- Layout Inspector's recomposition counters remain the first profiling stop.
- 1.12 note: `Modifier.onFirstVisible()` is deprecated → migrate to
  `Modifier.onVisibilityChanged()` — source:
  <https://android-developers.googleblog.com/2026/08/jetpack-compose-august-2026-release.html>

**2026 sensibility:** accessibility-first design and sustainable/ethical/inclusive UI are
trend-listed this year, alongside Zero-UI, agentic UX, and contextual/adaptive interfaces
(source: <https://uxpilot.ai/blogs/mobile-app-design-trends>) — but per the owner's
standing doctrine, creativity leads and a11y is applied where it doesn't fight the vision.

---

## Group 5 — Design→code: visual languages + workflows of 2026

**The visual languages in play** — sources:
<https://www.designstudiouiux.com/blog/mobile-app-ui-ux-design-trends/>,
<https://uxpilot.ai/blogs/mobile-app-design-trends>,
<https://midrocket.com/en/guides/ui-design-trends-2026/>,
<https://www.wearetenet.com/blog/ui-ux-design-trends>
- **Glassmorphism revival** — frosted translucent layers with real depth; mainstreamed by
  the Liquid Glass wave; 2026 flavor = subtle layers, not noise. (Native Compose route:
  the `kmp-liquid-glass` skill + the house fallback chain AGSL → RenderEffect → gradient.)
- **Bento grids** — modular rounded cards of varied sizes, hierarchy by card size
  (Compose 1.11 Grid/FlexBox + 1.12 named areas make this first-class).
- **Neubrutalism** — "deliberate harshness: rigid grids, mismatched fonts, thick
  outlines, and saturated colors"; it "doesn't whisper — it shouts". Source:
  <https://www.cccreative.design/blogs/differences-in-ui-design-trends-neumorphism-glassmorphism-and-neubrutalism>
- **AI-native interfaces, Zero-UI, and passwordless auth** — Zero-UI = the interface
  receding behind voice, gesture, and environmental cues (uxpilot's trend list); the 1.12
  `credentialRequest` semantics is the Compose hook for passwordless.
- Vault cross-reference: uiverse categories carry live specimens of all of these —
  neumorphism/3d/gradient tags on cards, gooey toggles, neon borders, skeuomorphic
  switches. Mine mechanics, re-skin into the invented direction.

**Design-to-code workflows** — sources:
<https://www.banani.co/blog/ai-design-to-code-tools>,
<https://www.managed-code.com/blog-post/design-to-code-ai-tools-2026>
- 2026 reality: AI design-to-code tools accelerate (30–60% initial build time cut) but
  none ship production-ready code alone — translation judgment stays with the builder.
- Machine fact (not a sourced trend claim): the `figma` MCP is already wired on this
  machine — when the owner supplies a Figma file, designs import directly.

---

*Currency rule: this digest was true on 2026-08-15. Any session using a claim in shipped
work re-verifies it against the linked source (the android-docs MCP for API claims).*
