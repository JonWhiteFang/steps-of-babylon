# Labs "Coming Soon" Cleanup (#44) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the Labs code honestly reflect that `AUTO_UPGRADE_AI` (the one `isComingSoon` research type) is *hidden*, not badged — extract the list filter into a pure, JVM-testable `ResearchType.surfacedInLabs()` helper, add a regression guard, delete the now-dead `COMING SOON` UI branches, and correct the false code comments. Closes #44 and ticks Gate B.1.

**Architecture:** `AUTO_UPGRADE_AI` has been filtered out of the Labs list since V1X-15 (`LabsViewModel.kt:76` does `entries.filterNot { it.isComingSoon }`). This refactors that inline filter into a named pure-domain companion function, guards it with two pure JVM tests in `ResearchTypeTest`, removes the unreachable `info.type.isComingSoon ->` branches in `LabsScreen.ResearchCard`, and fixes the stale comment on the kept `startResearch` defensive guard. Presentation + one pure-domain helper only.

**Tech Stack:** Kotlin, JUnit Jupiter (JVM unit tests), Jetpack Compose (presentation). No schema/engine/economy/DI change.

**Spec:** `docs/superpowers/specs/2026-06-17-labs-coming-soon-cleanup-design.md`

---

## File Structure

| File | Change | Responsibility |
|---|---|---|
| `app/src/main/java/com/whitefang/stepsofbabylon/domain/model/ResearchType.kt` | Modify (+companion object) | Add `surfacedInLabs()` — the single source of truth for which research types the Labs UI shows. |
| `app/src/test/java/com/whitefang/stepsofbabylon/domain/model/ResearchTypeTest.kt` | Modify (+2 tests) | Pin the `surfacedInLabs()` helper-body semantics (excludes coming-soon; exactly the 11 wired types). |
| `app/src/main/java/com/whitefang/stepsofbabylon/presentation/labs/LabsViewModel.kt` | Modify (call-site + comment) | Build `researchList` via `surfacedInLabs()`; correct the stale `startResearch` guard comment. |
| `app/src/main/java/com/whitefang/stepsofbabylon/presentation/labs/LabsScreen.kt` | Modify (−2 dead branches) | Remove the unreachable `COMING SOON` title chip + empty action branch. |
| `docs/StepsOfBabylon_GDD.md` | Modify (line 256, doc-sync) | Fix the stale "research disabled in the Labs UI" wording → "hidden / filtered out". |
| `docs/plans/plan-FORWARD.md` | Modify (line 41, doc-sync) | Tick Gate B.1; leave B.2 unchecked. |

**Task order rationale:** Task 1 (helper + tests) is the foundation — it must land before the call-site switches to it (Task 2), so the test red→green sequence is clean. Task 3 (dead UI) is independent but ordered after so the "filter exists" precondition the dead-code argument relies on is already in place. Task 4 is doc-sync.

---

### Task 1: Pure `surfacedInLabs()` helper + regression tests

**Files:**
- Modify: `app/src/main/java/com/whitefang/stepsofbabylon/domain/model/ResearchType.kt` (add companion object before the enum's closing brace at line 73)
- Test: `app/src/test/java/com/whitefang/stepsofbabylon/domain/model/ResearchTypeTest.kt`

- [ ] **Step 1: Write the failing tests**

Add these two `@Test` methods to `ResearchTypeTest` (inside the class, after the existing `ENEMY_INTEL has full balance values populated` test at line 55, before the class closing brace):

```kotlin
    @Test
    fun `surfacedInLabs excludes coming-soon research`() {
        // The Labs UI must never surface a deferred (isComingSoon) research type — that is
        // exactly the half-built stub #44 / Gate B.1 is about. surfacedInLabs() is the single
        // source of truth LabsViewModel consumes; this pins its body so the exclusion can't be
        // silently dropped (see also `surfacedInLabs is exactly the wired types`).
        val surfaced = ResearchType.surfacedInLabs()
        assertTrue(
            surfaced.none { it.isComingSoon },
            "surfacedInLabs() must exclude every isComingSoon entry",
        )
        assertFalse(
            ResearchType.AUTO_UPGRADE_AI in surfaced,
            "AUTO_UPGRADE_AI (the deferred type) must not be surfaced in Labs",
        )
    }

    @Test
    fun `surfacedInLabs is exactly the wired types`() {
        // Set-equality both directions: surfaced == all entries minus the single deferred one.
        // Fails red if surfacedInLabs() stops filtering (would re-include AUTO_UPGRADE_AI) OR
        // over-filters (drops a wired type). AUTO_UPGRADE_AI is the sole isComingSoon entry
        // (guarded by `only AUTO_UPGRADE_AI is flagged isComingSoon` above).
        assertEquals(
            ResearchType.entries.toSet() - ResearchType.AUTO_UPGRADE_AI,
            ResearchType.surfacedInLabs().toSet(),
            "surfacedInLabs() must be exactly the 11 wired types (all entries minus AUTO_UPGRADE_AI)",
        )
    }
```

Add the missing imports to the top of `ResearchTypeTest.kt` (it currently imports only `assertEquals` + `Test`):

```kotlin
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
```

- [ ] **Step 2: Run the tests to verify they fail (compile error)**

Run: `./run-gradle.sh :app:testDebugUnitTest --tests "com.whitefang.stepsofbabylon.domain.model.ResearchTypeTest" > /tmp/t1.log 2>&1; tail -n 25 /tmp/t1.log`
Expected: **compile failure** — `unresolved reference: surfacedInLabs` (the companion fun does not exist yet). This is the red state.

- [ ] **Step 3: Add the `surfacedInLabs()` companion helper**

In `ResearchType.kt`, insert a companion object immediately before the enum's closing brace (currently line 73, after the `BOUNCE_RESEARCH(...)` constant on line 72). Note the comma after the last enum constant is already present (line 72 ends `..., costScaling = 1.5),`) — a companion object after the last constant is valid; ensure a `;` terminates the constant list before the companion:

```kotlin
    BOUNCE_RESEARCH(8_000, 6.0, 10, 1.0, "+1 projectile bounce", costScaling = 1.5);

    companion object {
        /**
         * Research types surfaced in the Labs UI — excludes [isComingSoon] (deferred-to-v1.x)
         * entries so testers never see a half-built stub (#44, Gate B.1). The single source of
         * truth for the Labs list filter: [com.whitefang.stepsofbabylon.presentation.labs.LabsViewModel]
         * calls this rather than re-deriving `entries.filterNot { it.isComingSoon }` inline. Pure
         * (no Android), so the UI-list contract is JVM-testable without instantiating the VM
         * (whose `while(true)` ticker complicates direct construction). Preserves `entries` order.
         */
        fun surfacedInLabs(): List<ResearchType> = entries.filterNot { it.isComingSoon }
    }
```

> Note: the last enum constant `BOUNCE_RESEARCH(...)` currently ends with a trailing comma (`),`). Replace that trailing comma with a semicolon (`);`) so the enum body can carry the companion object. Do not alter `BOUNCE_RESEARCH`'s constructor arguments.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./run-gradle.sh :app:testDebugUnitTest --tests "com.whitefang.stepsofbabylon.domain.model.ResearchTypeTest" > /tmp/t1.log 2>&1; tail -n 25 /tmp/t1.log`
Expected: **PASS** — all 4 tests in `ResearchTypeTest` green (the 2 existing + 2 new).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/whitefang/stepsofbabylon/domain/model/ResearchType.kt \
        app/src/test/java/com/whitefang/stepsofbabylon/domain/model/ResearchTypeTest.kt
git commit -m "feat(#44): add ResearchType.surfacedInLabs() helper + regression guard

Extracts the Labs list filter (entries.filterNot { isComingSoon }) into a named
pure-domain companion fun so it is JVM-testable without the LabsViewModel
while(true) ticker. Two new ResearchTypeTest cases pin the helper body: it
excludes every isComingSoon entry and equals exactly the 11 wired types."
```

---

### Task 2: Switch `LabsViewModel` to the helper + fix the stale guard comment

**Files:**
- Modify: `app/src/main/java/com/whitefang/stepsofbabylon/presentation/labs/LabsViewModel.kt:76` (call-site) and `:111-116` (comment)

- [ ] **Step 1: Switch the list-building call-site to the helper**

In `LabsViewModel.kt`, line 76 currently reads:

```kotlin
            researchList = ResearchType.entries.filterNot { it.isComingSoon }.map { type ->
```

Change it to:

```kotlin
            researchList = ResearchType.surfacedInLabs().map { type ->
```

Behaviour is identical (same elements, same order). Do not touch the `.map { type -> … }` body or anything else in the `combine` block.

- [ ] **Step 2: Correct the stale comment on the kept defensive guard**

In `LabsViewModel.startResearch`, the comment at lines 111-116 currently reads:

```kotlin
        // RO-11 #B.2 / V1X-15b: defensive belt-and-braces guard. The Labs UI already suppresses
        // the Start Research button when [ResearchType.isComingSoon] is true, but this block
        // protects against any future entry point (e.g. quick-research flow, deep-link)
        // accidentally bypassing the UI gate while AUTO_UPGRADE_AI is still deferred
        // (ENEMY_INTEL was wired in V1X-15b). Both layers read the same content-as-code flag
        // so they cannot drift.
```

Replace it with (the accurate mechanism — coming-soon types are *filtered out of the list*, not button-suppressed):

```kotlin
        // #44 / RO-11 #B.2 / V1X-15b: defensive belt-and-braces guard. Coming-soon research is
        // filtered out of the surfaced list entirely (ResearchType.surfacedInLabs()), so the UI
        // never renders a Start button for it; this block is the reachable second layer that
        // protects against any future caller (quick-research flow, deep-link) reaching
        // startResearch with a deferred type and spending Steps. Both layers read the same
        // content-as-code isComingSoon flag (only AUTO_UPGRADE_AI today), so they cannot drift.
```

Keep the guard body itself (`if (type.isComingSoon) { _userMessage.value = …; return }`) exactly as-is.

- [ ] **Step 3: Verify it compiles + the full JVM suite is green**

Run: `./run-gradle.sh :app:testDebugUnitTest > /tmp/t2.log 2>&1; tail -n 20 /tmp/t2.log`
Expected: **BUILD SUCCESSFUL**, all tests pass (behaviour unchanged; `LabsViewModelTest` untouched and still green).

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/whitefang/stepsofbabylon/presentation/labs/LabsViewModel.kt
git commit -m "refactor(#44): LabsViewModel builds list via surfacedInLabs(); fix guard comment

Call-site now uses ResearchType.surfacedInLabs() instead of the inline
filterNot. Corrects the startResearch guard comment: coming-soon research is
filtered out of the list (not Start-button-suppressed); the guard is the
reachable second layer blocking a Step spend. Behaviour unchanged."
```

---

### Task 3: Delete the dead `COMING SOON` UI branches in `LabsScreen`

**Files:**
- Modify: `app/src/main/java/com/whitefang/stepsofbabylon/presentation/labs/LabsScreen.kt` (remove ~lines 122-130 title-chip branch and ~143-149 action branch)

- [ ] **Step 1: Remove the dead title-chip branch**

In `ResearchCard`, the title-row `when` (opens at line 121) currently has this first arm:

```kotlin
                when {
                    // RO-11 #B.2 / V1X-15b: AUTO_UPGRADE_AI gated as Coming Soon while its
                    // real implementation is deferred to v1.x (ENEMY_INTEL was wired in V1X-15b).
                    // Badge takes priority over the MAX / level chip so testers see the deferral
                    // state immediately.
                    info.type.isComingSoon -> Text(
                        "COMING SOON",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                    info.isMaxed -> Text(
```

Delete the comment block and the `info.type.isComingSoon -> Text("COMING SOON", …)` arm so the `when` begins directly with the `info.isMaxed -> Text("MAX", …)` arm:

```kotlin
                when {
                    info.isMaxed -> Text(
```

This is safe: no `info` with `info.type.isComingSoon == true` ever reaches `ResearchCard` (the VM filters them out via `surfacedInLabs()` before the `items(state.researchList)` loop at line 86). For every surfaced type the `when` now resolves to `isMaxed` or the `else -> Text("Lv …")` arm — exactly as it already did.

- [ ] **Step 2: Remove the dead empty action branch**

In the action-area `when` (opens at line 142), remove this first arm and its comment:

```kotlin
            when {
                info.type.isComingSoon -> {
                    // RO-11 #B.2: no Start / Rush / progress UI for deferred research types.
                    // Existing levels (if any landed before the deferral gate) display via the
                    // "Lv N" chip path is suppressed in favour of the COMING SOON badge above,
                    // but the underlying level value is preserved in LabRepository so the v1.x
                    // implementation picks up where the player left off.
                }
                info.isMaxed -> {} // no actions
```

So the `when` begins directly with the `info.isMaxed -> {}` arm:

```kotlin
            when {
                info.isMaxed -> {} // no actions
```

The remaining arms (`isMaxed`, `isActive`, `!slotAvailable`, `else ->` Start) are unaffected.

- [ ] **Step 2b: Confirm no import became orphaned**

`MaterialTheme.colorScheme.tertiary` (the deleted badge's color) is a member access, not an import — nothing to remove. `Text` and `MaterialTheme` remain used pervasively. `lintDebug` in Step 3 will catch any genuine orphan; do not pre-emptively delete imports.

- [ ] **Step 3: Verify compile + lint + the full JVM suite**

Run: `./run-gradle.sh :app:testDebugUnitTest lintDebug > /tmp/t3.log 2>&1; tail -n 25 /tmp/t3.log`
Expected: **BUILD SUCCESSFUL**, no lint errors (specifically no unused-import warning), all tests green.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/whitefang/stepsofbabylon/presentation/labs/LabsScreen.kt
git commit -m "refactor(#44): delete dead COMING SOON branches in LabsScreen.ResearchCard

Both info.type.isComingSoon arms (the title 'COMING SOON' chip and the empty
action branch) are unreachable: the VM filters coming-soon types out of
researchList via surfacedInLabs() before any card is built. Removing dead code +
its now-false comments; no behaviour change (no coming-soon type reaches here)."
```

---

### Task 4: Doc-sync — GDD wording + Gate B.1 tick

**Files:**
- Modify: `docs/StepsOfBabylon_GDD.md:256`
- Modify: `docs/plans/plan-FORWARD.md:41`

- [ ] **Step 1: Fix the stale GDD wording**

`docs/StepsOfBabylon_GDD.md` line 256 currently reads:

```
| Auto-Upgrade AI | *Coming soon (v1.x) — not yet implemented; research disabled in the Labs UI (`isComingSoon=true`).* Planned: auto-spends Cash on optimal upgrades during rounds | 8,000 Steps | 12 hours | 5 |
```

Change "research disabled in the Labs UI" → "hidden from the Labs UI (filtered out via `ResearchType.surfacedInLabs()`)":

```
| Auto-Upgrade AI | *Coming soon (v1.x) — not yet implemented; hidden from the Labs UI (filtered out via `ResearchType.surfacedInLabs()`, `isComingSoon=true`).* Planned: auto-spends Cash on optimal upgrades during rounds | 8,000 Steps | 12 hours | 5 |
```

- [ ] **Step 2: Tick Gate B.1 (and leave B.2 unchecked)**

`docs/plans/plan-FORWARD.md` line 41 currently reads:

```
- [ ] AUTO_UPGRADE_AI resolved (shipped or clearly framed as deferred) — *satisfied-by #44*
```

Change to (mark resolved, keep the satisfied-by ref, note the resolution):

```
- [x] AUTO_UPGRADE_AI resolved — *satisfied-by #44 (PR pending): the type has been hidden from Labs since V1X-15 (`surfacedInLabs()` filter); #44 made the code match reality (deleted dead UI branches) + added a regression guard. Implementation deferred to v1.x; nothing half-built is shown.*
```

Do **not** change line 42 (B.2 — `No misleading "Coming Soon" in core flows; remaining locked cosmetics…`); it is satisfied-by separate cosmetic debt and stays `[ ]`.

- [ ] **Step 3: Commit**

```bash
git add docs/StepsOfBabylon_GDD.md docs/plans/plan-FORWARD.md
git commit -m "docs(#44): sync GDD Labs wording + tick Gate B.1

GDD:256 'research disabled in the Labs UI' → 'hidden / filtered out via
surfacedInLabs()' (matches reality since V1X-15). plan-FORWARD Gate B.1
(AUTO_UPGRADE_AI resolved) ticked; B.2 left unchecked (separate cosmetic debt)."
```

---

### Task 5: Final verification + checkpoint + PR

**Files:**
- Modify: `CHANGELOG.md`, `docs/agent/STATE.md`, `docs/agent/RUN_LOG.md` (via `/checkpoint`)

- [ ] **Step 1: Full green build**

Run: `./run-gradle.sh :app:testDebugUnitTest lintDebug assembleDebug > /tmp/final.log 2>&1; tail -n 20 /tmp/final.log`
Expected: **BUILD SUCCESSFUL**; test count 1052 → 1054 (the 2 new `ResearchTypeTest` cases).

- [ ] **Step 2: Red-before-green sanity check (scratch, not committed)**

Temporarily change `surfacedInLabs()`'s body to `entries.toList()` (drop the `filterNot`), run `./run-gradle.sh :app:testDebugUnitTest --tests "*ResearchTypeTest"`, confirm both new tests **FAIL**, then revert. This proves the guard actually bites.

- [ ] **Step 3: Checkpoint (doc-sync + STATE/RUN_LOG)**

Run the `/checkpoint` skill. Per the PR Task-List Convention, sync these current-state docs explicitly (do not rely on the doc-drift sweep to catch them):
- **`CLAUDE.md:303`** — update the headline count `1052 JVM tests` → `1054 JVM tests` (the one live test-count number the repo keeps in CLAUDE.md; it must move with the +2).
- **`CHANGELOG.md`** — add an `[Unreleased]` entry (#44, content honesty / Gate B.1, +2 JVM tests 1052→1054, presentation + pure-domain helper, no schema/engine/economy change).
- **`STATE.md`** — update the headline count to 1054 (line ~6) and rotate the current objective (#44 done; surface the manual feel gates A/E + fresh-install D as what remains in Phase 1).
- **`RUN_LOG.md`** — append a new top entry.

All three coordinated copies of the live count (CLAUDE.md:303, STATE.md:~6, CHANGELOG.md) must read 1054. No ADR (no non-trivial architectural decision — reuses the pure-helper + content-as-code patterns).

- [ ] **Step 4: Push + open PR**

```bash
git push -u origin feat/44-labs-coming-soon-cleanup
gh pr create --title "feat(#44): Labs Coming Soon cleanup — hide-already-shipped, lock Gate B.1" --body "<summary: AUTO_UPGRADE_AI already hidden since V1X-15; this makes code match reality (surfacedInLabs() helper + regression guard, deleted dead UI branches, fixed false comments) + doc-sync. Ticks Gate B.1. No schema/engine/economy change. Spec + plan both passed the Adversarial Review Gate.>"
```

Stop here for developer review before merge (do not auto-merge).

---

## Self-Review

**Spec coverage:**
- §3.1 pure helper → Task 1. ✓
- §3.2 delete dead UI branches → Task 3. ✓
- §3.3 keep guard + fix comment → Task 2 (Step 2). ✓
- §4 two new tests (helper-body semantics) → Task 1 (Step 1). ✓
- §3.1 call-site invariant / §4 coverage boundary → respected: tests are pure (Task 1), call-site is a single named call (Task 2), guard is reachable second layer (Task 2). ✓
- §6 doc-sync (GDD:256 + plan-FORWARD:41 B.1 only) → Task 4. ✓
- §6 STATE/RUN_LOG/CHANGELOG → Task 5 (checkpoint). ✓
- Non-goals (no AUTO_UPGRADE_AI impl; CELESTIAL_GATE untouched; no schema/engine/economy/DI) → honored (no task touches them). ✓

**Placeholder scan:** No TBD/TODO/"handle edge cases". Every code step shows the actual before/after code and exact commands. ✓

**Type consistency:** `surfacedInLabs()` signature (`fun surfacedInLabs(): List<ResearchType>`) is identical across Task 1 (definition + tests) and Task 2 (call-site). `ResearchType.AUTO_UPGRADE_AI` / `isComingSoon` names match the real enum. Test assertion names (`assertTrue`/`assertFalse`/`assertEquals`) match the added imports. ✓
