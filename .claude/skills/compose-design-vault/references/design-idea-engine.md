# Design-idea engine — several genuinely different directions per component

Use in **ideation mode** (feeding the Art Direction Gate) or **execution mode** (styling a
component inside an already-invented direction). Rules of use:

1. Offer **at least three distinct directions**, never one "best".
2. Every direction must survive the anti-generic Banned List (no baseline `#6750A4`
   identity, no Home/Search/Profile bar, no fade-only motion...).
3. A direction is a MECHANIC + MOOD, not a palette — the app's `ART_DIRECTION.md` supplies
  palette/type/shape.
4. Vault pointers are raw material: open the item, extract the mechanic via
   `css-to-compose.md`, re-skin completely.

Trend grounding for all families: `trends-2026.md` (M3 Expressive physics motion, mesh
gradients/WCG, bento, neubrutalism, glass revival, Zero-UI). One standing guard: any
mesh/aurora surface (Aurora pane, Mesh pulse, Aurora void below) must be tied to a
narrative reason drawn from the app's A1 concept line — an aurora with no story is
gradient slop (Banned List B19 territory).

---

## Cards

- **Living depth** — card tilts subtly toward touch (graphicsLayer rotationX/Y from
  pointer), content layers parallax at different rates; glow follows the finger.
  Vault: `uiverse/cards/*` 3d/neumorphism tags. Mechanic: cookbook §4.
- **Bento cluster** — screen = one bento board; cards are rooms, not rows. Hierarchy by
  span, not order; 1.12 Grid named areas place hero/support/utility semantically.
- **Torn edge / physical object** — card as paper/ticket/polaroid: irregular GenericShape
  edge, hard M3-Expressive-morphing corners, spring drop-in with slight overshoot.
- **Aurora pane** — MeshGradientPainter background drifting slowly (WCG colors), content
  on a scrim; premium hero cards only, one per screen.
- **Neubrutalist slab** — thick outline, hard offset shadow (no blur), saturated flat
  fill, instant snap motion (stiff springs, no easing softness).

## Buttons

- **Sheen sweep** — idle premium button with a periodic light-band sweep (cookbook §10);
  press = scale 0.96 spring + sheen burst.
- **Morphing state token** — button IS the state: pill → circle progress → checkmark burst
  (M3 Expressive shape morphing; updateTransition orchestrates shape+color+width).
- **Charged press** — hold-to-confirm: ring fills around the button while pressed
  (Animatable progress + sweep-gradient stroke), release early springs back.
- **Neon rim** — solid dark button, animated neon border streak (the owner's moving-neon
  mechanic, cookbook §10) that accelerates on press.
- **Gooey pair** — primary/secondary buttons that visually merge when adjacent and split
  on scroll (metaball mechanic, API-33 AGSL with graceful fallback).

## Toggles / switches

The vault's crown jewels (8 named CodePen toggles incl. Skillet, Neon, Night&&Day,
Sci-Fi Door Lock live in `codepen/toggles/`).
- **Scene toggle** — the thumb is a world: day↔night scene crossfading inside the track
  (sun→moon arc, stars fade in). Mechanic: layered Boxes + updateTransition.
- **Physical latch** — skeuomorphic bolt/lock that slides with spring overshoot + haptic;
  1.12 `SoundEffectOnInteraction` opt-out lets you ship a custom clack.
- **Merging letters** — ON/OFF glyphs morph/merge through the thumb travel
  (vault: `jkantner__gOZrOQm`); Compose: two Texts with graphicsLayer cross-morph.
- **Neon state** — off = dead grey tube, on = flickering neon ignition (2-3 quick alpha
  keyframes then steady glow; cookbook §2 glow).
- **Gooey drop** — thumb detaches as a liquid blob and re-forms (metaball mechanic).

## Loaders / progress

- **Narrative loader** — loader tells the app's story (a plant growing, a coconut
  rolling): Canvas + PathMeasure progress, never a bare spinner.
- **Type-driven** — the app's display font's letters assemble/weight-breathe (variable
  font weight animation, cookbook §8).
- **Mesh pulse** — MeshGradientPainter vertices breathing; doubles as an empty-state
  ambience layer.
- **Skeleton with personality** — shimmer bands in the app's accent, sweeping in the
  direction content will arrive from; canonical shimmer recipe re-skinned.

## Inputs / forms

- **Focus bloom** — border is dormant; on focus a gradient stroke draws itself around the
  field (PathMeasure stroke reveal) and the label floats with a spring.
- **Inline credential magic** — passkey pill inside the field via 1.12
  `credentialRequest` semantics (passwordless trend, Group 5).
- **Typed-for-purpose** — 1.12 KeyboardType.Date/Time/SignedDecimal instead of generic
  text + regex; the field's shape hints its type (date = ticket notches).
- **Neubrutalist form** — fields as labeled slabs, hard shadows, validation = the slab
  physically stamps (scale + rotation kick).

## Navigation / structure

- **Gesture-first canvas** — content is the nav: swipe planes with shared-element
  continuity (`sharedBounds`), predictive-back staged with `DeferredAnimatedContent`.
- **Dock of moments** — bottom dock of morphing shape-tokens (not icon+label rows); the
  active token morphs into the screen's hero shape on tap (shared element).
- **Spatial bento hub** — home = bento board; tapping a tile expands it into the full
  screen (sharedBounds container transform), back gesture shrinks it live.
- Banned-list reminder: a default Home/Search/Profile bottom bar with grey icons is an
  auto-reject.

## Empty states / errors

- **World-building vignette** — the app's mascot/motif in its idle world (generated via
  the asset pipeline), one spring-looped motion accent, action button with sheen.
- **Constructive brutalism** — big type states the fact ("NOTHING SAVED YET"), thick
  arrow to the create action; matter-of-fact, zero cuteness.
- **Aurora void** — dark screen, slow mesh aurora, single line of light type; premium
  quiet apps.

---

## Using the engine end-to-end (worked example)

Brief: "finance app, card list screen".
1. Gate first → suppose `ART_DIRECTION.md` lands on "engraved banknote meets terminal
   green" (palette: ink + guilloché green; type: engraved serif display + mono data).
2. Ideation here → cards: *Living depth* vs *Torn edge (banknote)* vs *Neubrutalist
   slab*; pick per the direction: banknote torn-edge wins.
3. Vault execution → open 2-3 `uiverse/cards` items tagged 3d/border for edge + layer
   mechanics; translate via cookbook §4/§6/§10 into the banknote identity (guilloché
   line-pattern drawBehind, engraved serif, green glow accents).
4. Slop Detector before handover, as always.
