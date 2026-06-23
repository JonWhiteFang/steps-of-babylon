---
name: release
description: Cut a Steps of Babylon release — bump versionCode/versionName, promote the CHANGELOG, write release notes, sync version pointers, open the release PR, and (after merge) push the annotated v* tag that triggers the automated Play-internal release lane. Run when the developer says "cut a release", "ship v1.0.x", "do a release", "bump the version", or "release to Play".
disable-model-invocation: true
---

# /release — Cut a Steps of Babylon release

User-invoked only (it has outward-facing side effects: a PR, a pushed tag, and a Play upload via
`release.yml`). This packages the precise, repeatable release checklist that the last several
releases followed (v1.0.11 / versionCode 27 is the reference). It does **not** invent a process —
it executes the one documented in `docs/release/release-checklist.md`, `docs/plans/plan-32-ci.md`,
and ADR-0018.

## Mental model (how a release actually ships)

The release lane is automated (Plan 32 / ADR-0018):

1. A **collateral-only PR** to `main` bumps the version, promotes the CHANGELOG, adds the release
   notes doc, and syncs version pointers. It contains **no production-code change** of its own —
   everything being released already landed on `main` in prior PRs.
2. After that PR merges, an **annotated tag** `vX.Y.Z` is pushed. The tag's **message becomes the
   Play "What's new"** text — `release.yml` runs `git tag -l --format='%(contents)'`, caps it at
   Play's 500-char limit, and writes it to `distribution/whatsnew/whatsnew-en-US`.
3. `release.yml` then builds the **committed** versionCode (no auto-bump — Play rejects reused
   codes; see the v13 rejection note in the workflow), runs the unit-test guard, signs the AAB, and
   uploads to the **Play internal** track.

So the human/agent work is steps 1–2; CI does step 3. Steps are **never** purchasable and nothing
about monetization changes in a release PR — this is collateral only.

## Preconditions (verify before doing anything)

- [ ] Working tree clean and on `main` (or confirm with the developer if not).
- [ ] Everything intended for this release is already merged to `main`. The release PR adds no new
      production code — if a feature isn't on `main` yet, it's not in this release.
- [ ] Read the current `versionCode` / `versionName` from `app/build.gradle.kts` (lines ~48–49).
      **Never** hardcode or guess them — read them.
- [ ] Decide the new `versionName` with the developer if not given, and set `versionCode = old + 1`.

## Steps

Build a TodoWrite list from these, then work them in order.

### 1. Determine versions
- Read current `versionCode` / `versionName` from `app/build.gradle.kts`.
- New `versionCode` = current + 1 (Play rejects reused codes). Confirm the new `versionName` with
  the developer.

### 2. Draft the "What's new" text (developer-approved)
- Draft a player-facing "What's new" (≤500 chars — Play's hard cap; `release.yml` truncates at 500).
  Player-facing, no internal jargon, no PR numbers. Model it on the v1.0.11 block in
  `docs/release/release-notes-v1.0.11.md` ("A polish update — …").
- **Show the draft to the developer and get explicit approval** before using it — this is the public
  store text. This is the one mandatory human gate.

### 3. Cut the release branch
- `git switch -c release/vX.Y.Z` off up-to-date `main`.

### 4. Bump the version
- Edit `app/build.gradle.kts`: `versionCode` → new, `versionName` → new.
- (The PreToolUse guard hook will flag this edit — expected; this is the sanctioned release path.)

### 5. Promote the CHANGELOG
- In `CHANGELOG.md`: rename the `## [Unreleased]` heading to `## [X.Y.Z] — YYYY-MM-DD (versionCode N)`
  (use today's date), and add a fresh empty `## [Unreleased]` section above it. Keep all the
  accumulated entries under the now-versioned heading.

### 6. Write the release-notes doc
- Create `docs/release/release-notes-vX.Y.Z.md` following the structure of
  `release-notes-v1.0.11.md`: header (track / tag / supersedes / lane), the verbatim "What's new"
  block (the developer-approved text from step 2), "What shipped (developer detail)" split into
  player-facing vs not-player-visible, provenance/review-trail, verification, and "Next".
- Reconcile the developer-detail section against the actual commits since the last release
  (`git log vX.Y.Z_prev..HEAD --oneline`) — don't restate from memory.

### 7. Sync version pointers (sweep — touch only what's stale)
Search for the OLD version string and the OLD test count and update the current-state references:
- `README.md` (version + headline test count if it changed)
- `docs/StepsOfBabylon_GDD.md`, `docs/plans/master-plan.md` (current clause only),
  relevant `docs/plans/plan-3*.md`, `docs/agent/STATE.md` headline.
- Use `rg -n 'X\.Y\.Z_prev'` to find pointers; update the *current-state* ones, never historical
  artifacts (RUN_LOG prior entries, `docs/archive/**`, `docs/external-reviews/**`, old release-notes).
  See CLAUDE.md "Historical artifacts — NEVER modify".

### 8. Verify the build is releasable
- `./run-gradle.sh testDebugUnitTest` — the unit suite must be green (the release lane gates on it).
- Confirm `app/schemas/` is unchanged vs the prior release if this is a no-schema-change release
  (state that fact explicitly in the notes, as v1.0.11 did).

### 9. Checkpoint the docs
- Run `/checkpoint` (or do its doc-sweep + STATE/RUN_LOG update) per the PR Task-List Convention —
  current-state docs synced, then STATE.md updated + RUN_LOG appended.

### 10. Commit + open the release PR
- Commit on `release/vX.Y.Z` with a message summarizing the promotion (version bump, CHANGELOG
  promote, release notes, pointer sync).
- `gh pr create` targeting `main`. The PR runs the normal CI gate + instrumented lane.
- **Stop here and hand back to the developer to review/merge.** Do not self-merge a release.

### 11. After the PR merges — push the annotated tag (the release trigger)
This is the step that actually ships. Do it only once the PR is merged to `main`.
- Switch to `main`, pull.
- Create an **annotated** tag whose message IS the approved "What's new" text:
  `git tag -a vX.Y.Z -m "<the ≤500-char What's-new text>"`
  (Annotated, not lightweight — `release.yml` reads the tag *message* for Play. A lightweight tag
  falls back to a generic line.)
- `git push origin vX.Y.Z` → triggers `release.yml` → signed AAB → Play internal track.
- Watch the run: `gh run watch` (or `gh run list --workflow=release.yml`). Report success/failure
  back to the developer; the AAB lands on the Play **internal** track, not production.

## Guardrails
- The PR carries **no production-code change** — if you find yourself editing engine/domain/UI code,
  stop: that belongs in a separate feature PR merged before the release.
- Never bump straight to a track beyond **internal** — promotion to closed/production is a separate,
  judgment-gated manual step (`docs/plans/plan-FORWARD.md`).
- Never reuse a `versionCode`. Never hardcode the version/test-count into the checklist doc.
- The "What's new" text must be developer-approved before it goes into the tag/notes.
