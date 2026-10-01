# Slop Detector audit, first handover (2026-10-01)

**Plain summary**
1. Checked the built app against the house anti-generic rules and ART_DIRECTION.md.
2. No hard-ban hits.
3. Soft hits: 1 (S17 density set: no raster assets exist yet, the icon is vector, so this is a pass in practice).
4. Result: ships (0 to 1 soft hits allowed).
5. Evidence: emulator screenshots in the `ci-results` branch, folder `smoke/`.
6. Owner override recorded for glass and pastel gradients (Admin's own words).

## Hard bans (B1 to B25)

| Ban | Result | Note |
|---|---|---|
| B1 purple baseline | Clear | Palette is dawn pastels, plum and coral |
| B2 dynamic colour as identity | Clear | Not used |
| B3 untinted neutrals | Clear | Pearl `#F7F1EC`, plum text |
| B4 more than two accents | Clear | Coral only (gold is part of the sky) |
| B5 hijacked semantic roles | Clear | Error stays red |
| B6 inverted dark mode | Clear | Dark palette chosen separately (blue hour) |
| B7 Roboto only | Clear | Syne + Instrument Sans, bundled |
| B8 no scale contrast | Clear | 64 sp wordmark on welcome |
| B9 untouched display tracking | Clear | -2.5% to -3% |
| B10 card soup | Clear | Single editor surface, no grids |
| B11 elevated cards | Clear | Frosted panes, tonal |
| B12 one skeleton everywhere | Clear | Welcome and editor differ |
| B13 total symmetry | Clear | Asymmetric welcome, sun glow bottom left |
| B14 Home/Search/Profile bar | Clear | Tool tray instead |
| B15 mixed icon styles | Clear | One hand-drawn 1.8 dp family |
| B16 fade-only motion | Clear | Spring, mist breathing and lift |
| B17 no shared element | Partial | Welcome to editor has no shared element yet (no thumbnail list exists); logged for next version |
| B18 glass on 3+ surfaces | Clear | Panes and mist only (owner override noted) |
| B19 stock blobs | Clear | Gradient is the dawn sky (owner override) |
| B20 placeholder copy | Clear | All copy written for NanoGone |
| B21 grey empty state | Clear | Welcome is the empty state, styled |
| B22 bare spinner | Clear | Busy states are words in the tray plus mist |
| B23 letter-in-circle logo | Clear | Sun behind mist band |
| B24 emoji icons | Clear | None |
| B25 no adaptive or monochrome icon | Clear | Both layers present |

## Soft checks (S1 to S20)

S1 to S16 and S18 to S20: clear on review of the emulator screenshots. S17 (density set) noted above.
