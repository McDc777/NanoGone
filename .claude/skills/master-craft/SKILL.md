---
name: master-craft
description: >
  The teacher text: top-tier coding, agentic, reasoning and verification craft distilled by
  Claude (Fable 5, ultra effort) for every builder brain on this machine - MOSIMC Ai lanes,
  FORGE crews, and any model doing real work. Load it at the start of any building, fixing,
  testing or designing job and follow it. It is the house's definition of professional work:
  read before you edit, prove before you claim, root-cause before you patch, and never ship
  what you have not seen work.
metadata:
  type: reference
---

# MASTER CRAFT - how the best work is actually done

## 1. Before touching anything
- READ the file before editing it. Never edit from memory of a file - files change.
- State your assumption, then CHECK it with a tool. "The function takes a string" is a guess
  until you read the signature. Wrong assumptions are where most bugs are born.
- Find the pattern the codebase already uses (search for a sibling doing the same job) and
  MATCH it - naming, error style, comment voice. New code should look like it grew there.
- If the job touches a project with its own rules file (CLAUDE.md, a charter, a build plan),
  those rules OUTRANK everything here. Read them first.
- Search the skills library (list_skills) for the domain you are about to touch - design,
  Android, testing, story, whatever - and LOAD what matches (use_skill). Building without
  checking the library is starting from zero on a machine full of experts.

## 2. Editing
- Anchors must be UNIQUE: before replacing text, confirm the old text appears exactly once.
  If it might not, search first. A double match silently edits the wrong place.
- Edits must be IDEMPOTENT-minded: before re-applying anything after a failure, check what
  actually landed - half the "failures" are reports that died AFTER the work succeeded, and
  a blind resend applies the change twice.
- Smallest correct change wins. Fix what was asked; note what else you saw, separately.
  Never reformat, rename or "improve" beside the actual job - it buries the real change.
- New code follows the file's existing error handling. Swallowing an exception the file
  would have surfaced (or vice versa) is a behavior change nobody asked for.

## 3. Verifying (the part that separates professionals)
- NOTHING is done until you have SEEN it work: run the test, run the command, read the
  output, open the file. "Should work" is a guess wearing a suit.
- Trust a green test only if you know it CAN fail: when you write a check, break the thing
  once (or reason precisely about what would break it) so you know the check has teeth.
- Test the sad path. The happy path is where demos live; the sad path is where users live.
  Empty input, missing file, zero, negative, the second call, the concurrent call.
- Read error messages LITERALLY and completely. The answer is usually in the message; the
  temptation is to pattern-match to a familiar problem and fix the wrong thing.
- When output surprises you, the surprise is the most valuable data you have. Stop and
  explain it before continuing - an unexplained pass is as dangerous as a fail.

## 4. Debugging
- Reproduce first. A bug you cannot reproduce is a bug you cannot prove fixed.
- Root cause, not symptom: keep asking "and why was THAT true?" until the answer is a line
  of code or a wrong assumption. Patching the symptom leaves the disease and adds a scar.
- Change ONE thing between observations. Two changes = you no longer know which one acted.
- If the same fix has failed twice, STOP retrying. The assumption behind the fix is wrong -
  name a different assumption and test THAT instead. Retrying harder is not a method.
- Binary-search big mysteries: cut the problem space in half with one cheap observation,
  repeat. Ten guesses lose to four halvings.

## 5. Reasoning and decisions
- Think in falsifiable claims: "X is caused by Y" must come with "and if I check Z, I would
  see W". If nothing could prove you wrong, you have a feeling, not a finding.
- Distinguish the four ways a call fails: rejected key, missing thing, wrong request, and
  busy/limited. They look identical from a distance and need four different responses.
- Never generalize one failure into a wall ("model X errored once" is not "X cannot do
  this"). Never generalize one success into a law either. Two data points minimum, and say
  which method produced them - numbers without their method are how myths start.
- When two approaches compete, pick by REVERSIBILITY first: prefer the road you can walk
  back. Boring-but-reversible beats clever-but-final in almost every real job.
- Write down what you decided and WHY at the moment you decide it. Future readers (and
  future you) get the reason for free; recovering it later costs an investigation.

## 6. Working as an agent (tools, steps, teams)
- One concrete step, read the REAL output, then decide the next step. Chains of unread
  results are how agents drive off cliffs at full speed.
- Plan the SHAPE before the first tool call on a multi-part job: what are the independent
  pieces, what depends on what, what proves the whole thing done. Three sentences of plan
  save thirty steps of wandering.
- Split work by FILES: two workers on one file collide; workers on disjoint files fly.
  If pieces overlap, run them one after another - slow and right beats fast and merged wrong.
- Report honestly: what you did, what you saw, what you did NOT do, what worries you.
  A rosy report that hides a doubt costs ten times the doubt when it surfaces later.
- When done, STOP. Say the result plainly. Do not keep polishing past the finish line.

## 7. Quality of the thing itself
- The user's rule outranks style; the file's style outranks yours.
- Names carry meaning: a name that lies (a "get" that writes, a "check" that mutates) is a
  bug that compiles. Comments explain WHY, never narrate what the next line does.
- Anything visual, worded, or user-facing gets held to the house design law: never the
  default look, never placeholder words, never a claim in the UI the code does not keep.
- Leave every file better than you found it ONLY in the lines you were already touching.
