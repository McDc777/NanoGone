# The Art Direction Engine

Raw material for **inventing** an art direction. Companion to `anti-generic-doctrine.md`, which is the
law; this file is the ammunition.

> **Read this first:** the tables below are **not a menu to pick from.** Copying an archetype wholesale
> is exactly the sameness the law bans. They exist to (a) show what a *finished* direction looks like at
> full specificity, and (b) be **recombined** — take the shape rule from one, the motion from another,
> the palette logic from a third, then push all of it somewhere neither table went. If the shipped app
> matches any single row below on more than two axes, invent further.

---

## 1. The invention method

Three inputs. Never start from "what UI would suit this app" — that question only ever returns the mean.

1. **The job.** What does the app actually do, in five words? ("Log what I ate." "Split a bill.")
2. **The state.** What is the user's emotional state the second they open it? Rushed? Guilty? Bored in
   a queue? Proud? Anxious about money? **This is the input most often skipped, and it decides more
   than anything else.** A rushed user needs high contrast and one huge target. A bored user wants to
   be rewarded for looking closely.
3. **The borrowed world.** A physical craft, object, place, trade, or era that already solved this
   emotional problem beautifully — *before software existed.* Kitchen scales. A tide table. A dive
   watch. A pharmacist's label. A rally timing board.

Then: **the borrowed world supplies palette, materials, type, shape and motion; the job supplies
structure; the state decides the intensity.**

### The four-way stretch

Once you have a candidate, push each axis to its limit and see what survives:

| Axis | Question |
|------|----------|
| **Chroma** | What if the whole app were two colours only? What if it were monochrome plus one? |
| **Scale** | What if one element were 6× everything else? What if the type were 96sp? |
| **Density** | What if it were packed like an instrument panel? What if there were one thing per screen? |
| **Weight** | What if nothing bounced at all? What if everything had mass and inertia? |

At least one of these four should end up unusually far from the middle. An app that is average on all
four axes is invisible.

---

## 2. Archetype library (28 fully-specified directions)

Every row is complete enough to build from: palette with real hex, a real type pair (all Google
Fonts–downloadable), a shape rule, a motion spec with numbers, and a texture/asset note.

| # | Direction | Borrowed world | Palette (hex) | Type pair | Shape rule | Motion | Texture / assets |
|---|-----------|----------------|---------------|-----------|-----------|--------|------------------|
| 1 | **Brass Chronometer** | 1968 dive watch, dense and precise | dark `#12100C` · brass `#C9962F` · enamel `#EDE3CF` · lume `#7FE0B0` (accent, ≤8%) | Instrument Serif 400 (−2% tracking) / Geist 400·500·600 | 0dp panels; circular motifs; exactly one 32dp pill (primary action) | Mechanical detent: `damping 0.9, stiffness 900` — no bounce. Tick stagger 40ms | Brushed-metal AGSL noise, engraved bevels, lume glow at low light |
| 2 | **Risograph Zine** | Stapled 1990s two-colour fanzine on newsprint | paper `#F4EFE2` · fluoro orange `#FF4F2E` · ink blue `#2B3AC9` · black `#1A1A18` | Archivo Black / Work Sans 400·500 | 0dp everywhere; hard 2dp rules; deliberate 3px misregistration | Snap cuts, `tween(90ms, LinearEasing)`. Paper-shuffle stagger 60ms | Halftone dots, ink misregistration, newsprint grain |
| 3 | **Botanical Field Guide** | Victorian pressed-plant folio | aged paper `#EFE8D8` · herbarium green `#3E5C43` · ink `#22201B` · label rust `#A9502C` | EB Garamond 400·600 / Newsreader 400 | 1dp hairline frames; thin rules; wide margins | Page-turn, `emphasized decelerate 520ms`. Nothing springs | Paper fibre, generated botanical line art, specimen labels |
| 4 | **Analogue Synth** | 1974 modular panel | panel cream `#E8E2D4` · walnut `#4A3524` · knob black `#1A1A1C` · patch red `#D63A2F` · LED amber `#FFB020` | Chivo 700 / IBM Plex Mono 400 | 4dp with visible screw heads; inset bezels; 2dp panel seams | Knob-turn drag with resistance `damping 0.85, stiffness 600` | Hairline-brushed aluminium, silkscreen labels, patch cables |
| 5 | **Deep Ocean Telemetry** | Submersible instrument feed at 4000 m | abyssal `#0A1A24` · biolum cyan `#2BE0D4` · sonar green `#7CF06A` · pressure amber `#FFA93B` | Space Grotesk 500·700 / IBM Plex Mono 400 | 0dp panels; 999dp readout pills | Sonar sweep 2.4s loop; data rows enter 24ms apart | Caustics, particulate drift, faint scanlines |
| 6 | **Kintsugi Ceramic** | A repaired tea bowl | charcoal glaze `#1E2226` · gold seam `#C9A227` · clay `#C8B49E` · celadon `#8FA99A` | Fraunces 400·700 (optical) / Manrope 400·600 | Irregular — **every** container has one differing corner | A gold seam draws along the changed boundary, 600ms path reveal | Crackle glaze, gold-leaf edge, unglazed foot |
| 7 | **Brutalist Concrete** | 1970s civic building | concrete `#8C8880` · shuttering `#5C5A55` · safety yellow `#F2C300` · oxide `#8A3B2A` | Archivo 700·900 / Public Sans 400 | 0dp; 3dp borders; deep 8dp insets | Heavy: slabs slide `280ms accelerate`. Nothing bounces, ever | Board-form concrete, exposed aggregate, stencil signage |
| 8 | **Tropical Nocturne** | Moonlit greenhouse | deep `#0F2620` · moon `#E8F0E2` · orchid `#E0518F` · jade `#2F8F6B` · glass `#B8D8CE` | Playfair Display 500·700 / Figtree 400·600 | 28dp on **top** corners only; arched headers | Leaf-sway parallax; breathing scale 1.00↔1.02 over 4s | Condensation on glass, monstera silhouettes, moon scrim |
| 9 | **Rally Timing** | 1980s Group B stage board | board white `#F5F2EC` · tarmac `#21242A` · stage red `#E01B22` · split blue `#0057B8` | Anton / Barlow Condensed 400·600 | 0dp; 6° skewed parallelograms | Hard cuts + 7-segment count-up. Stagger 30ms | Livery stripes, decal edges, chequer |
| 10 | **Sun-Bleached Adobe** | New Mexico desert wall at noon | plaster `#E9DCC7` · terracotta `#C97B4A` · shadow `#6B4A38` · turquoise `#3E9AA4` | Bricolage Grotesque 600·800 / DM Sans 400 | Soft 20dp squircles; thick rounded blocks | Slow warm fade 420ms; long hard shadow shifts with scroll | Plaster grain, hard noon shadow, hand-thrown edges |
| 11 | **Cold-Storage Archive** | Film vault at 4 °C | frost white `#DDE3E6` · steel `#7C8A91` · index red `#C0392B` · tape label `#E4D9A8` | IBM Plex Sans 400·600 / IBM Plex Mono 400 | 2dp; tab-shaped headers; card-index dividers | Drawer slide with mechanical stop, 220ms, no overshoot | Label tape, sprocket holes, dust, canister stencils |
| 12 | **Neon Night Market** | Kowloon alley, 11 pm, raining | wet black `#0B0710` · sign pink `#FF2E88` · jade `#00E5A0` · lantern `#FFB300` | Unbounded 600·800 / Sora 400·600 | 999dp pills **and** 0dp signage slabs, deliberately clashing | Slam entrances `damping 0.5, stiffness 700`; flicker; reflection ripple | Neon bloom, rain streaks, puddle reflections |
| 13 | **Letterpress Stationer** | 1930s print shop sample book | stock `#F7F3E9` · ink `#1B1A17` · deep green `#24483A` · foil copper `#B3714A` | Bodoni Moda 400·700 (−3% tracking) / Karla 400·500 | 0dp with 1dp keylines; very generous margins | Press impression: 60ms scale to 0.98 on press, then settle | Deboss/impression, ink spread, foil stamp |
| 14 | **Orbital Ops** | Crewed station checklist display | `#0D1117` · panel `#1B2430` · caution `#FFB300` · go `#2FD75F` · EVA white `#E9EEF2` | Geist 500·700 / Geist Mono 400 | 4dp; thick status bars; dot-matrix readouts | Precise, no bounce: 160ms `LinearOutSlowIn`; telemetry ticks 1Hz | Velcro, kapton gold, laminate checklist |
| 15 | **Woodblock Ukiyo-e** | A Hokusai printer's palette | paper `#EFE6D2` · prussian `#1B3A6B` · vermilion `#D6452E` · moss `#6E7F52` | Zilla Slab 500·700 / Lora 400 | Flowing 24dp; wave motifs on dividers | Wave-crest reveal, `emphasized 500ms` | Woodgrain, bokashi gradients, key-block outlines |
| 16 | **Cassette Dub** | 1982 mixtape J-card | card `#E8E4D9` · shell `#B9B4A6` · tape brown `#6B4A2E` · marker blue `#2C4BA0` · hot pink `#E8447F` | Wix Madefor Display 700 / Rubik 400·500 | 6dp with rounded window cut-outs | Reel spin; wow-and-flutter warble on transitions | Handwriting, label smudge, dot-matrix, tape window |
| 17 | **Alpine Topographic** | Swiss 1:25 000 sheet | sheet `#F2F0EA` · contour `#9A7B4F` · glacier `#BEE0EA` · forest `#4A6B48` · route `#D0342C` | Schibsted Grotesk 500·700 / Inter 400 | 0dp panels; 0.5dp contour hairlines; grid ticks | Elevation-profile draw-on, 400ms decelerate | Contour lines, hillshade, grid graticule |
| 18 | **Apothecary Amber** | A chemist's dispensary | amber glass `#7A3E1D` · label cream `#F3E9D2` · cork `#C29A6B` · tincture `#3F5E3A` · wax `#8E2B2B` | Cormorant 500·600 / Libre Franklin 400 | 12dp bottle forms; 999dp label pills | Liquid settle `damping 0.6, stiffness 260`; pour transitions | Glass refraction, gummed labels, wax seal |
| 19 | **Track & Field** | Synthetic track, 1976 meet | lane white `#F2F2EF` · track red `#C7452F` · infield `#2E6B3E` · lane blue `#1B4FA0` | Bebas Neue / Barlow 400·600 | 999dp lanes and pills, nothing square | Lane-sweep wipe; sprint stagger 25ms; finish-tape snap | Rubber granulate, painted lane numerals |
| 20 | **Nordic Wool** | Faroese knit in winter | slate `#2B3440` · undyed `#E6DFD1` · madder `#9C4B3F` · lichen `#7E8B5E` | Fraunces 500 / Karla 400·600 | 16dp with a knitted selvedge edge motif | Soft and heavy `damping 0.9, stiffness 300`. Nothing snaps | Wool loft, stitch texture, natural fibre |
| 21 | **Arcade Cabinet** | 1983 vector-graphics cab | `#06060A` · phosphor cyan `#34F5E1` · phosphor magenta `#FF3DCE` · coin gold `#F5C33B` | Pixelify Sans (display only) / Rubik 400·500 | 0dp; thick bezel borders; scanline overlay | Vector-trace reveal; CRT power-on warp; glow decay 300ms | Phosphor bloom, scanlines, bezel glare |
| 22 | **Wet Clay Studio** | A potter's wheel mid-throw | clay `#B08163` · slip `#E7DACB` · kiln shadow `#4A3A32` · cobalt `#2E4E8F` | Gabarito 600·800 / Outfit 400·500 | Fully organic — 40%-smoothed squircles, **zero** hard corners | Throwing: rotational drag with mass `damping 0.7, stiffness 200` | Thumbprints, wheel ridges, slip drips |
| 23 | **Signal Corps** | WWII field radio set | olive `#3B4232` · dial cream `#DFD6BC` · tuning red `#B3372C` · bakelite `#24211C` | Oswald 500·600 / Archivo Narrow 400 | 4dp with knurled edges; 2dp panel lines | Needle-swing overshoot `damping 0.4, stiffness 500`; dial detent | Canvas webbing, knurling, stencil paint |
| 24 | **Coral Reef Census** | Marine biologist's survey slate | slate `#F0F6F5` · coral `#FF6F5E` · anemone `#7C5CE0` · algae `#1F8A70` · deep `#0C3A44` | Funnel Display 600·800 / Plus Jakarta Sans 400 | 24dp irregular organic blobs | Current drift and sway `damping 0.65, stiffness 180` | Dappled light, generated coral illustration |
| 25 | **Blueprint Room** | 1950s drafting office | blueprint `#14335C` · cyanotype `#E8EEF5` · graphite `#4A4E55` · redline `#D93A2B` | Archivo 600 / IBM Plex Mono 400 | 0dp with dimension lines and corner ticks as decoration | Lines draw on 340ms, then dimension arrows snap into place | Cyanotype wash, drafting grid, eraser smudge |
| 26 | **Midnight Diner** | Tokyo counter, 2 am | `#16110E` · lantern amber `#E8A33D` · enamel `#EFE7DA` · scallion `#6E8F4C` | Instrument Serif 400 / Manrope 400·600 | 20dp with a noren-curtain top edge | Steam rise 700ms; warm cross-fades; nothing hurried | Steam, chipped enamel, condensation |
| 27 | **Marble Quarry** | Carrara at first light | marble `#EFEDE8` · vein `#A8A49B` · shadow `#3A3833` · chisel steel `#6E7378` · sanguine `#9E5B4A` | Cormorant Garamond 500·600 / Inter Tight 400·500 | 0dp slabs with one chiselled 40dp diagonal cut | Slab slide with stone weight `damping 1.0, stiffness 450` | Generated marble veining, dust motes, chisel marks |
| 28 | **Aurora Station** | Arctic observatory in polar night | `#071019` · aurora green `#55F0B4` · aurora violet `#9B6BFF` · ice `#CFE4EE` | Syne 600·800 / Manrope 400·500 | 32dp on one corner, 0dp on the other three | Aurora ribbon drift (Perlin AGSL), 3–6s loops, never repeating | Aurora shader, star field, frost crystals |

### Reading the table as a generator, not a catalogue

Pick coordinates, not rows:

- Palette logic from **#11** (frost + one index red) × shape from **#22** (all organic) × motion from
  **#7** (nothing bounces) = a cold, soft, heavy thing that exists nowhere above.
- Or invert a row: **#12** Neon Night Market at *noon*, dry, in daylight. Same hues, opposite mood.
- Or change the era: **#4** Analogue Synth built in 2005 injection-moulded plastic instead of 1974 wood.

---

## 3. Palette construction

### 3.1 The recipe

1. **Seed from the borrowed world**, not from a colour picker. If the world is a brass watch, the seed
   is *brass* — sample the real hue (≈`#C9962F`), don't approximate it with "gold-ish yellow".
2. **Generate the tonal palette** from that seed (Material Theme Builder, or
   `material-color-utilities`), then **override by hand** where the algorithm went generic.
3. **Tint every neutral toward the seed hue** — at least 3% chroma. `surface` in a brass app is
   `#F7F2E8`/`#12100C`, never `#FFFFFF`/`#000000`. This single step kills more slop than any other.
4. **Choose one accent** whose whole job is scarcity. It appears on the primary action and nothing
   else. Budget: ≤10% of any screen's pixels.
5. **Protect the semantic roles.** `error` stays red-family. Don't spend the brand hue on it.
6. **Design light and dark separately** (see §6).

### 3.2 The 60/30/10/1 split

| Share | Role | Typical token |
|-------|------|---------------|
| ~60% | Dominant surface | `surface`, `surfaceContainerLow` |
| ~30% | Secondary structure | `surfaceContainer`, `surfaceContainerHigh`, `outlineVariant` |
| ~10% | Brand presence | `primary`, `primaryContainer`, `secondaryContainer` |
| ~1% | The accent that earns attention | the single accent, `tertiary` |

If the brand colour is on 40% of the screen, it has stopped being a brand colour and become a
background. Pull it back.

### 3.3 Chroma budget

- **Light UI** tolerates less chroma than you expect. Above ~C 0.12 (OKLCH) large light surfaces start
  to look like a toy.
- **Dark UI** tolerates more chroma in *small* areas and less in large ones. Accents usually need
  desaturating 10–20% from their light-mode value or they buzz.
- **Never place two high-chroma hues adjacent at equal area.** One dominates, one accents.

### 3.4 Contrast floors (non-negotiable, they are also correctness)

| Content | Minimum ratio |
|---------|---------------|
| Body text | **4.5:1** against its actual background |
| Large text (≥18pt regular / ≥14pt bold) | **3:1** |
| Icons, borders, focus rings, meaningful graphics | **3:1** |
| Disabled text | no floor, but keep ≥2.5:1 or it reads as a rendering bug |

Check against the **real** background, including any scrim, blur or image behind it — not against
`surface` in the abstract.

---

## 4. Type pairing

### 4.1 The rule

**A display face with a personality + a workhorse text face with none.** The display face carries the
concept; the text face gets out of the way. One of the two may be Roboto — never both, and never
Roboto as the display face.

### 4.2 Pairs that work (all Google Fonts, downloadable in Compose)

| Display | Text | Reads as |
|---------|------|----------|
| Instrument Serif | Geist | Editorial, quiet confidence, expensive |
| Bricolage Grotesque | DM Sans | Contemporary, slightly odd, friendly |
| Fraunces (optical size) | Manrope | Warm, crafted, human |
| Archivo Black | Work Sans | Loud, printed, punk |
| Bodoni Moda | Karla | Fashion, high contrast, formal |
| Syne | Manrope | Art-gallery, future-facing |
| Unbounded | Sora | Nightlife, bold, urban |
| Playfair Display | Figtree | Romantic, soft luxury |
| EB Garamond | Newsreader | Scholarly, textual, long-form |
| Anton | Barlow Condensed | Sport, urgency, scoreboard |
| Cormorant Garamond | Inter Tight | Delicate, classical, spacious |
| Zilla Slab | Lora | Sturdy, printed, journalistic |
| Space Grotesk | IBM Plex Mono | Technical, instrument-like |
| Gabarito | Outfit | Rounded, cheerful, tactile |
| Funnel Display | Plus Jakarta Sans | Playful, modern, approachable |
| Bebas Neue | Barlow | Compressed, athletic |
| Oswald | Archivo Narrow | Utilitarian, stencilled |
| Geist | Geist Mono | Systems, precise, engineered |
| Wix Madefor Display | Rubik | Consumer, warm, 80s-adjacent |
| Schibsted Grotesk | Inter | Neutral-Swiss with a spine |

Variable faces worth knowing: **Fraunces** (optical size + softness + WONK axes), **Recursive**
(casual↔linear, mono↔sans), **Roboto Flex** (many axes — if you must use Roboto, at least *use* its
axes), **Bricolage Grotesque** (width + optical size).

Outside Google Fonts: Fontshare (Satoshi, General Sans, Cabinet Grotesk) is free for commercial use
but must be **bundled into `res/font`**, not fetched by the provider.

### 4.3 Tuning that separates professional from generated

| Size band | Tracking | Line height |
|-----------|----------|-------------|
| Display (48–96sp) | **−2% to −4%** | 1.0–1.1× |
| Headline (24–40sp) | −1% to −2% | 1.15–1.25× |
| Title (16–22sp) | 0% | 1.3× |
| Body (14–16sp) | 0% to +0.5% | **1.45–1.6×** |
| Label / caption (11–13sp) | **+2% to +6%** | 1.3× |

Also: cap measure at **~65–75 characters** per line on wide screens; use a real display *weight* (700+)
rather than scaling up a 400; and pick one place to use the display face at *huge* size — a single
96sp moment per app is worth more than ten 24sp headings.

### 4.4 Installing it in Compose

```kotlin
// build.gradle: androidx.compose.ui:ui-text-google-fonts
private val provider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage    = "com.google.android.gms",
    certificates       = R.array.com_google_android_gms_fonts_certs,
)

private val Display = FontFamily(
    Font(GoogleFont("Instrument Serif"), provider, FontWeight.Normal),
)
private val Text = FontFamily(
    Font(GoogleFont("Geist"), provider, FontWeight.Normal),
    Font(GoogleFont("Geist"), provider, FontWeight.Medium),
    Font(GoogleFont("Geist"), provider, FontWeight.SemiBold),
)

val AppTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = Display, fontSize = 64.sp, lineHeight = 66.sp,
        letterSpacing = (-1.6).sp, fontWeight = FontWeight.Normal,   // −2.5%
    ),
    bodyLarge = TextStyle(
        fontFamily = Text, fontSize = 16.sp, lineHeight = 25.sp,     // 1.56×
        letterSpacing = 0.sp, fontWeight = FontWeight.Normal,
    ),
    // …fill every role. An unfilled role silently falls back to Roboto (B7).
)
```

**Always provide a bundled fallback** for the downloadable provider (a `res/font` file or a system
family) so first launch offline doesn't silently render Roboto.

---

## 5. Shape language

One idea, everywhere. Options that are actually distinctive:

| Idea | Implementation |
|------|----------------|
| **Single asymmetric corner** | `RoundedCornerShape(topStart = 28.dp, topEnd = 4.dp, bottomEnd = 4.dp, bottomStart = 4.dp)` on every container, rotated per hierarchy level |
| **Hard with one exception** | Everything `0.dp`; the primary action alone at `32.dp` or fully round |
| **Squircle** | `RoundedPolygon` / `MaterialShapes` from `androidx.graphics:graphics-shapes` — smoothing ≈0.4 reads as an organic squircle, not a rounded rect |
| **Cut corners** | `CutCornerShape(12.dp)` — instantly non-default, suits industrial/technical worlds |
| **Directional radius** | Radius grows down the screen (4→12→28dp) so the page feels like it's settling |
| **Container-relative** | Corner = 8% of the container's shorter side, so small chips are crisp and big cards are soft |
| **Motif edge** | A custom `Shape` with a repeating notch/selvedge/noren edge on one side only |

Install as `MaterialTheme.shapes` so components inherit it rather than each one hardcoding a radius.

MD3 Expressive adds **shape morphing** (a shape that changes on press/state). If the BOM supports it,
one morph moment is a cheap, memorable signature — verify current API names via `context7` or
`android-docs` before writing it.

---

## 6. Dark mode as a design

Full rules in `anti-generic-doctrine.md` §4. The working method:

1. **Decide which mode is canonical** and design it fully first. Say which in `ART_DIRECTION.md`.
2. **Re-pick the dark seed tone.** Not the light seed inverted — often a *different* tone of the same
   hue, sometimes a different hue entirely (a brass app can go to deep walnut in dark, not grey).
3. **Tinted near-black, never `#000000`.** Pull 4–8% of the brand hue in.
4. **Climb with tonal steps**, `surface` → `surfaceContainerLowest…Highest`, never with shadow.
5. **Desaturate accents 10–20%.**
6. **Give imagery its own treatment** — scrim, brightness variant, or a different crop.
7. **Test both at 20% and 100% screen brightness.** Dark palettes fail at high brightness; light
   palettes fail at low.

---

## 7. Motion signature

### 7.1 Concrete specs

| Feel | Spring | Where |
|------|--------|-------|
| Mechanical, no overshoot | `damping 1.0, stiffness 700–900` | Instruments, industrial, brutalist |
| Crisp and confident | `damping 0.85, stiffness 500` | Default for premium consumer |
| Playful | `damping 0.55, stiffness 380` | Warm, tactile, friendly apps |
| Heavy mass | `damping 0.9, stiffness 200–300` | Stone, wool, clay, anything with weight |
| Needle overshoot | `damping 0.4, stiffness 500` | Gauges, dials, meters |
| Liquid settle | `damping 0.6, stiffness 260` | Fluid, glass, organic |

```kotlin
val AppSpring = spring<Float>(dampingRatio = 0.85f, stiffness = 500f)
```

MD3 Expressive ships `MotionScheme` (`standard()` / `expressive()`) which threads springs through
components; if your BOM has it, set it on `MaterialTheme` so the whole app inherits one feel instead of
each animation deciding for itself.

### 7.2 Non-negotiable motion elements

1. **One spring for the whole app.** A shared feel is what makes motion read as *identity* rather than
   decoration.
2. **A stagger interval** for lists: 20–60ms per item. Below 20 it's invisible; above 80 it drags.
3. **A shared-element transition** from list → detail (`SharedTransitionLayout`). This is the single
   biggest "made by professionals" signal in modern Android. Verify the current
   `Modifier.sharedElement(...)` signature against `android-docs`/`context7` — it has changed between
   releases.
4. **One delight moment** that fires rarely and is tied to the concept (A3). Rare is the point — a
   delight on every tap becomes noise within a day.
5. **Predictive back** wired up, so the gesture feels continuous rather than a hard cut.
6. **Reduced-motion respect**: if `Settings.Global.ANIMATOR_DURATION_SCALE` is 0, collapse to
   instant/opacity-only. Keep the signature *concept*, drop the travel.

### 7.3 Legacy easing (still correct for enter/exit transitions)

| Easing | Duration | Use |
|--------|----------|-----|
| Emphasized | 500ms | Begins and ends on screen |
| Emphasized decelerate | 400ms | Entering |
| Emphasized accelerate | 200ms | Exiting |
| Standard | 300ms | Utility, begins and ends on screen |

---

## 8. Depth and material

Layer count is a design decision. State it as a number.

| Layers | Feel |
|--------|------|
| **1** (flat, colour-blocked) | Print, poster, brutalist, riso |
| **2** (ground + floating) | Most apps. Ground surface + one raised action layer |
| **3** (ground + content + chrome) | Instrument panels, dense data |
| **4+** (parallax stack) | Cinematic, 3D hero, spatial |

Rules:
- **Depth via tonal `surfaceContainer*` steps**, not shadow (MD3, and shadow is invisible in dark).
- **Name the light source** and honour it in every gradient, bevel and shadow. Inconsistent light is
  what makes "3D-ish" UI look cheap.
- **Glass: exactly one library, never stacked.** Options and the AGSL→RenderEffect→gradient fallback
  chain are in the global Android directives; the `kmp-liquid-glass` skill has the idiomatic recipes.
- **Glass on at most two surface types**, with a stated reason. More than that is decoration (B18).
- **Real 3D is not UI chrome.** Filament / SceneView with a PolyHaven HDRI for image-based lighting,
  for a hero object — not for buttons.

---

## 9. Logo, app icon, brand mark

The launcher is the first screen of the app. `B23` and `B25` ban wasting it.

### 9.1 Constructing a mark that isn't a letter-in-a-circle

Pick one:

- **Geometric reduction of the borrowed world** — a dive-watch bezel becomes a notched ring; a tide
  table becomes two offset arcs.
- **A ligature or a cut letterform** from the display face, modified (a counter removed, a stroke
  extended) so it's a mark, not type.
- **A negative-space idea** — the shape is defined by what's absent.
- **A repeated unit** — three of the app's smallest visual atom, arranged.

Then apply the three hard tests:

| Test | Requirement |
|------|-------------|
| **Squint at 48dp** | Still identifiable as a distinct shape |
| **Monochrome** | Works as one flat colour (required for the themed icon anyway) |
| **Line-up** | Distinguishable in 2 seconds beside the last 10 ledger icons |

### 9.2 Exact Android geometry

| Asset | Spec |
|-------|------|
| Adaptive icon canvas | **108 × 108 dp**, both layers |
| Masked viewport | inner **72 × 72 dp** (outer 18dp per side is parallax bleed) |
| Safe zone for the mark | **66 dp diameter** circle, centred — anything outside can be clipped by an OEM mask |
| Foreground asset (xxxhdpi) | **432 × 432 px**, transparent PNG or (better) a vector drawable |
| Background layer | Solid or gradient **vector**, full 108dp bleed — never a photo |
| Monochrome layer | Required for Android 13+ themed icons; declare `<monochrome>` in the `<adaptive-icon>` |
| Play Store icon | **512 × 512 px**, 32-bit PNG, **no alpha** |
| Play feature graphic | **1024 × 500 px** |
| Splash icon (Android 12+) | with icon background: **240 dp** canvas, inner **160 dp** visible · without: **288 dp** canvas, inner **192 dp** visible |
| Raster density set | mdpi 1× · hdpi 1.5× · xhdpi 2× · xxhdpi 3× · xxxhdpi 4×. Author at 4× and downscale |

Prefer **vector drawables** for anything geometric, and **WebP lossless** for raster UI art. Test the
icon under all OEM masks (circle, squircle, rounded square, teardrop) before shipping.

### 9.3 Colour and type in the brand mark

- The mark uses **one** palette colour plus at most one neutral. Not the whole scheme.
- The wordmark uses the **display face**, with tracking tightened further than any UI text.
- The icon background is where the brand hue lives at full strength — often the only place in the whole
  product where it appears at 100% area.

---

## 10. Content design (copy is design)

`B20` bans placeholder copy. Replacements:

| Surface | Instead of | Do |
|---------|-----------|-----|
| Onboarding | "Welcome back!" | Say what just happened or what's next, in the app's voice |
| Primary CTA | "Get Started" | Name the actual action: "Wind the watch", "Log the meal", "Split it" |
| Empty state | Grey icon + "No items yet" | An illustrated moment from the borrowed world + one sentence of voice + the action that fills it |
| Loading | Spinner | Shaped skeletons that match the real layout, shimmering with the brand hue |
| Error | "Something went wrong" | What happened, what it means, and the one button that fixes it |
| Success | Toast | The delight moment (A3), earned |

Write **realistic seed data** — real-looking names, plausible values, believable dates. Fake data makes
a good design look like a mockup.

---

## 11. Where to get more raw material

| Source | Use |
|--------|-----|
| `ui-ux-pro-max` skill | `python …\ui-ux-pro-max\scripts\search.py "<type + industry + vibe>" --stack jetpack-compose` for direction and system-level guidance |
| `mobbin` MCP | Real shipped mobile screens and flows — **for what to avoid repeating** as much as for inspiration (needs sign-in) |
| `figma` MCP | When the owner supplies a Figma URL, extract the real tokens rather than eyeballing |
| `context7` · `android-docs` MCP | Current Compose / Material3 API shapes before writing any newer API |
| `godmode` skill | Escalate here when the brief demands maximum visual ambition; it supplies art direction, this skill implements it in Compose |
| `D:\AI\AssetVault` | 100 PolyHaven 4K HDRIs (image-based lighting), 200 ambientCG PBR materials — all CC0 |
| `asset-generation.md` | Producing the actual icons, illustration sets, textures and 3D |

**Never** take Android component code from `magic`, `21st`, or `shadcn`. They are web/React. Visual
reference only.
