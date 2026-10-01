# The No-Sameness Ledger

**Purpose.** The AndroidGo cardinal rule is *twenty apps must look like twenty different studios made
them.* Good intentions do not enforce that — memory of past apps doesn't survive between sessions. This
file does. It is the machine that makes the rule real.

---

## The rule

**Before** finalising A1–A9 in `ART_DIRECTION.md`:

1. Read the table below.
2. Compare the new direction against **every entry in the last ten rows** on three axes:
   **palette family** · **type pair** · **signature move**.
3. It must differ on **at least two of the three** from every one of those rows.

**Automatic rejects:**

| Condition | Result |
|-----------|--------|
| Matches any recent row on all three axes | Reject (this is Slop Detector check **S20**) |
| Reuses a **Concept Line** already in the table, at all | Reject regardless of score |
| Reuses a **Signature Move** from the last five rows | Reject (`anti-generic-doctrine.md` §2, A3 reject rule) |

**After** shipping, append one row. Appending is not optional — a skipped row silently disables the rule
for every future app.

Also run the **line-up test**: place the new app icon beside the last ten in the table. If a stranger
can't pick it out in two seconds, the mark is too close to something already shipped.

---

## Axis definitions (so comparison isn't a judgement call)

**Palette family** — the dominant hue region plus the surface temperature. Record as
`<hue family> / <light|dark canonical> / <warm|cool|neutral>`.
Families: red · orange · amber-brass · yellow · lime · green · teal · cyan · blue · indigo · violet ·
magenta · pink · brown-earth · achromatic-plus-one.

Two entries are the **same family** if the dominant hue family *and* the temperature match.

**Type pair** — the display face is what counts. Same display face = same axis, even with a different
text face.

**Signature move** — the A3 answer. Two moves are the same if a user would describe them with the same
sentence, regardless of implementation.

---

## The ledger

Newest at the top. Keep every row forever; the rule only reads the last ten, but the Concept Line ban
reads all of them.

| Date | App | Concept Line (A1) | Palette family (A4) | Display face (A5) | Signature move (A3) |
|------|-----|-------------------|---------------------|-------------------|---------------------|
| — | *(no apps shipped under this law yet)* | — | — | — | — |

---

## How to append

Copy this row template, fill it, and put it directly under the header:

```markdown
| 2026-MM-DD | <app name> | <A1 verbatim, one sentence> | <hue family> / <light|dark> / <warm|cool|neutral> | <display face> | <A3 in one clause> |
```

Rules for filling it:

- **Concept Line verbatim.** Don't paraphrase — the ban depends on exact comparison being possible.
- **One display face**, the one that carries the concept.
- **Signature move as one clause**, phrased the way a user would describe it, not the way it's coded.
- Append on the day the APK is delivered, not when the design is agreed. Designs change during build.

## If the rule blocks a good direction

That means the direction is not as distinctive as it felt. The fix is not to bend the rule — it is to
push one axis further. Cheapest axes to move, in order:

1. **Signature move.** The catalogue of possible moves is effectively infinite; pull a fresh one from a
   different borrowed world.
2. **Display face.** `art-direction-engine.md` §4.2 lists twenty pairs, and Google Fonts holds
   hundreds more.
3. **Palette temperature.** Same hue family, opposite temperature, is a genuine difference — a warm
   brass app and a cool steel-blue app read as different studios even sharing a "metal" concept.

Changing the palette *hue* is usually the most disruptive change late in a build. Change the move first.
| 2026-09-04 | PhotosTV (Chromecast) | A photographer's light table late at night: 35 mm slides in ivory card mounts on warm glowing glass, a round loupe pulled over the one you are looking at. | warm ivory / light / warm | Fraunces | The loupe: the remote slides a round magnifier over the slide, it lifts 8 dp, OK opens the loupe into the full-screen viewer (projector iris for video) |
| 2026-09-28 | MovieBox TV (Chromecast) | A 35 mm projection booth during the late show: steel reels in blue-black haze, one carbon-arc lamp throwing an amber beam through the port glass, changeover cue dots flicking in the corner of the frame. | blue / dark / cool (amber accent) | Instrument Serif | The beam: an amber projector cone swings to whatever has focus, two changeover cue dots blink, OK flickers a 3-blade shutter |
