# NanoGone art direction: Dawn Mist

**Plain summary (for Admin)**
1. NanoGone looks like a still lake at sunrise.
2. Soft pastel dawn colours (lake blue, rose, peach, first gold) sit behind frosted glass panels.
3. Buttons are soft and pillowy, lit from the top left like the rising sun.
4. Whatever you select gets wrapped in a warm, glowing morning mist.
5. Tap Remove and the mist rises and fades into clear air, leaving the clean photo.
6. At night (dark mode) it becomes the blue hour before dawn.

## Owner override (anti-generic law, B18 and B19)

Admin's words, verbatim: "Cloudy glass neomorphism, soft pastel dawn gradients, luminous frosted acrylic. A warm morning mist blurs over the selected area and evaporates smoothly into clear air when you remove it."

Glass and pastel gradients are used because the owner asked for them, and they carry a story (dawn mist). Limits that still hold: glass on two surface types only (floating control panes, the selection mist); the photo itself is never tinted or covered by glass; no random blobs, every gradient is the dawn sky.

## A1 Concept line
A still lake at sunrise: mist lifting off the water, frosted acrylic catching the first pink and gold light, and the haze burning off as the sun climbs.

## A2 Anti-reference
Not a stock pastel blob "AI app" background. Not Material's default purple. Not a neon-edged glass kit. Not a copy of iOS Control Center. No card grids, no Home/Search/Profile bar.

## A3 Signature move
The morning mist: whatever you select is wrapped in warm, glowing mist that blurs it. Tap Remove and the mist rises and evaporates into clear air (about 1.2 s), leaving the clean photo.

## A4 Palette (light is canonical)
| Role | Hex | Why |
|---|---|---|
| Lake blue (sky top) | `#D6E6F2` | the cool sky above the lake |
| Rose haze | `#F4D3DC` | first pink light |
| Peach mist | `#FBE3D6` | warm mist near the water |
| First gold (horizon) | `#FFE6A6` | the sun about to rise |
| Pearl surface | `#F7F1EC` | tinted neutral, never pure white |
| Dusk plum (text) | `#2E2838` | night leaving the sky; about 13:1 on pearl |
| Secondary text | `#6E6478` | softer plum; about 5:1 on pearl |
| Sunrise coral (accent) | `#FF8A5C` | primary action only, under 10% of any screen |
| Error | `#D64545` | stays red |

Dark ("blue hour before dawn"), chosen separately: night blue `#121726`, surface `#1A2032`, horizon rose `#3A2A3A`, text `#EEE8F0`, accent `#E98A66` (desaturated). Dynamic colour is an optional setting, never the identity.

## A5 Type
Display **Syne** 600 to 800, tracking -2% to -3% at display sizes. Text **Instrument Sans** 400, 500, 600. Both bundled in `res/font` so they work offline. One huge moment: the 64 sp "NanoGone" on the welcome screen.

## A6 Shape
Soft continuous squircles. Floating panes 28 dp, tool buttons 18 dp, the photo 20 dp. The one exception: the Remove button is a full pill.

## A7 Motion
One app spring: `dampingRatio = 0.78f, stiffness = 320f`. Tool buttons stagger in at 30 ms. Mist drift loops every 6 s; evaporation is 1.2 s (rise, grow, fade; blur 6 dp to 0). Gallery thumbnail to editor uses a shared-element transition. Predictive back. Reduced motion collapses everything to a short fade.

## A8 Depth
Light comes from the sunrise, top left. Three layers: dawn gradient ground, the photo, floating frosted panes (top bar, tool tray, sheets) with soft neumorphic buttons (light shadow top left, plum shadow bottom right). Blur via `RenderEffect` (API 31+, all target devices). No glass stacking.

## A9 Assets
Adaptive icon: a sun disc half hidden behind a soft mist band over still water, plus a monochrome layer. Splash: the same mark rising. Empty state: a misty lake illustration. Generated per `.claude/skills/android-material3-design/references/asset-generation.md`, checked by the Slop Detector before every APK.
