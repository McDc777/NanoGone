# NanoGone brainstorm: where we are

**TL;DR**
1. Goal: a free Android app that removes objects from photos, better than Google Photos and Samsung Gallery.
2. Decided: option **A**, all AI work runs on the phone (offline, private).
3. Phones: Galaxy S23 to S26 Ultra plus Galaxy Tab A11+ 5G, full power. Saves a copy in a "NanoGone" folder. Extras: find distractions, shadow catcher, text and logo eraser, plus an easy draw-around lasso with a smart magnet. Engine: **both brains** (fast + deep). Now presenting the design in sections.
4. Next: more questions one at a time, then 2-3 engine approaches, then the design in sections.
5. Then: write the spec, self-review it, Admin reviews it, then the `writing-plans` skill.
6. Started 2026-10-01 on Admin's PC, moved to a Claude Code cloud session the same day.

---

## Admin's request (their own words, kept exactly)

> I want a super intelligent, powerful and user-friendly android app with abilities to remove objects from images.
> currently, Google Photo app does that but it's not user-friendly at all! the final image size and aspect ratio is just smaller than the original, sometimes it pulls the colors from near by subjects into the area that was meant to remove the object from, it has lot's of limitations and very slow. It can't select very small objects! its magnet selection (which is designed to correctly identify the borders of the object) drift on random bases and select other close objects. and etc.
> The other app that can also do object removal, is the Samsung Gallery app, which usually do a good job but for example even to remove a piece of rubbish in the background of a Family photo, it add a large note "Ai Generated image" which basically makes the image useless in case we wanted to add it on our social media. It also has some of the problems that Google Photo object remover has like inability to remove small objects.
> Currently, there are so many other strong and very good Object removal apps on Google Play Store and Samsung Galaxy store, that can do what I want and ensure the image quality is even better than the original image, but they all are there for purchase or subscription, which I don't want to do.
> So, tell me the plan and how you can make the app and what features you have in mind and how you want to implement those features?

## The complaints we must beat

1. Output is smaller than the original and the aspect ratio changes.
2. Colours from nearby subjects bleed into the removed area.
3. Very slow, many limits.
4. Cannot select very small objects.
5. The "magnet" edge selection drifts and grabs nearby objects.
6. Samsung stamps "AI generated" on the photo, which ruins it for social media.

## First ideas shown to Admin (not yet approved as a design)

1. **Same size, same shape, same quality:** repaint only the pixels inside the selection; every other pixel stays byte-for-byte the same; keep the original size, aspect ratio and photo details (date, place).
2. **No "AI generated" stamp**, ever.
3. **Tiny objects:** deep zoom, a magnifier bubble under the finger, a fine brush, and a one-tap spot remover for dust and specks.
4. **Selection that does not drift:** tap the object and a cut-out model (Segment-Anything style) hugs its edge; tap to add, "minus" tap to remove; it only grows where you tap.
5. **No borrowed colours:** a stronger repair model plus a "don't copy from here" brush.
6. Opens straight from Samsung Gallery and Google Photos via Share.
7. Speed target: rough preview in about a second, full quality in a few seconds (to be measured on the real phone).

## Decisions

| # | Question | Admin's answer |
|---|---|---|
| 1 | Where does the heavy AI work run? A phone only / B phone + PC / C phone + cloud | **A, phone only** (2026-10-01) |
| 2 | Which phone runs the app? | **Galaxy S23 Ultra, S24 Ultra, S25 Ultra, S26 Ultra and Galaxy Tab A11+ 5G.** Use ALL the power the phone has (AI chip, graphics chip, all cores) for the best result. (2026-10-01) |
| 3 | Save as a copy, or replace the original? | **Save a copy.** The original stays untouched. The new photo goes into its own **"NanoGone"** folder (shows as a NanoGone album in Gallery). (2026-10-01) |
| 4 | Must-have extra features for version 1? | **Find distractions, Shadow catcher, Text and logo eraser.** UPDATE during design Part 2: Admin said "you forgot the image enhancement!", so **Enhance is IN version 1** too. Admin also wrote, word for word: "user-friendly, easy to draw a line around the object, enhanced and automatic magnet so it automatically identify and wrap around the object. the background of the removed object must match the surroundings." (2026-10-01) |
| 5 | Which engine approach (A fast, B deep, C both)? | **C, both brains.** Fast brain for live preview and most fixes; deep brain for big objects plus their shadows and reflections. Build the fast one first. (2026-10-01) |

## Brainstorm checklist (the `brainstorming` skill)

- [x] 1. Explore project context (new, empty project)
- [ ] 2. Visual companion: offer it only when a question is truly visual
- [x] 3. Clarifying questions, one at a time (phones, save-as-copy, version 1 extras answered)
- [x] 4. Propose 2-3 engine approaches (Admin chose C, both brains)
- [ ] 5. Present the design in sections, Admin approves each
- [ ] 6. Write the spec to `docs/superpowers/specs/2026-10-01-nanogone-design.md` and commit
- [ ] 7. Spec self-review
- [ ] 8. Admin reviews the written spec
- [ ] 9. `writing-plans`

## Engine notes from the phone answer (for the design)

- S23 Ultra: Snapdragon 8 Gen 2. S24 Ultra: 8 Gen 3. S25 Ultra: 8 Elite. S26 Ultra: newest Snapdragon. All have a strong AI chip (NPU).
- Tab A11+ 5G: a MediaTek mid-range chip. It is the weakest device, so it sets the floor; it must still work, maybe a bit slower.
- "Full power" means: run on the AI chip first, then the graphics chip, then the main chip; no speed caps; keep the screen awake while working; use the biggest model that fits in memory.

## What Admin's Question 4 words mean for the design

- **Smart lasso:** draw a rough loop around the object with one finger. The AI then hugs the object's real edge, but ONLY inside your loop, so it can never jump to a nearby object (this fixes the Google "magnet drift").
- **Automatic magnet:** a single tap also works: the AI finds the whole object and wraps it. Plus and minus taps fix any mistake.
- **Fill must match the surroundings:** colour, light, texture and camera grain of the fill must blend with the area around it. The "don't copy from here" brush stays for hard cases.

## Design approval log

- Part 1 (how it works): approved.
- Part 2 (picking what to remove): no changes to the tools; Admin added **image enhancement** to version 1.
- Enhance question: Admin picked ALL four: **Sharper and cleaner** (blur and grain, same size), **Bigger photo** (2x or 4x), **Light and colour fix**, **Face fix**. Enhance is a separate button and changes the whole photo on purpose.
- Design now has 7 parts: 1 journey, 2 picking, 3 filling the hole, 4 Enhance, 5 saving, 6 look, 7 safety nets and testing.
- Part 3 (filling the hole): approved.
- Part 4 (Enhance): approved.
- Part 5 (saving): approved, with one change from Admin: "can you please add both options to chose". So Save offers **Top-quality JPEG** and **Lossless PNG** side by side, each with its estimated file size, remembering the last pick. (For a JPEG original with only removals, the JPEG choice is the "only changed squares rewritten" file.)
- Part 6 (look): three directions shown on a try-it page (docs/design/nanogone-looks.html, https://claude.ai/artifact/7M7S7fUqtbHdvaQ6wmqstB). Admin did not pick A, B or C. Admin gave their own direction, word for word:
  > "Cloudy glass neomorphism, soft pastel dawn gradients, luminous frosted acrylic. A warm morning mist blurs over the selected area and evaporates smoothly into clear air when you remove it."
  This is an **owner override** under the anti-generic law (glass and pastel gradients are allowed because Admin asked, with a dawn story behind them). Building it as **Look D, "Dawn Mist"** on the same try-it page for Admin to confirm.

## Admin's standing order (2026-10-01)

> "can you please stop asking and pick all of the best options and just get to get it done?"

So: no more questions. Look D "Dawn Mist" (Admin's own words) is locked. Part 7 (safety nets and testing) is decided by me. The spec review gate is waived by Admin; go straight to the plan and the build.
