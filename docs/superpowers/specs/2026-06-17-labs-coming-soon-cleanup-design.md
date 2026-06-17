# Design — #44: Labs "Coming Soon" cleanup + lock Gate B (content honesty)

**Issue:** #44 ([Content] AUTO_UPGRADE_AI research type is permanently 'Coming Soon')
**Gate:** Closed-Test Readiness Gate **B — Content honesty** (`plan-FORWARD.md`)
**Date:** 2026-06-17
**Scope class:** small, presentation + one pure-domain helper; no schema / engine / economy change.

---

## 1. Problem — and why the issue's premise is stale

Issue #44 (filed 2026-06-03) states that `AUTO_UPGRADE_AI`
*"occupies a visible slot in the Labs screen with a 'Coming Soon' badge"* and argues that
showing half-built features to testers is worse than not showing them.

**Verified against `main`, that premise no longer holds:**

- `LabsViewModel.kt:76` already builds the Labs list as
  `ResearchType.entries.filterNot { it.isComingSoon }.map { … }`. The only `isComingSoon = true`
  research entry — `AUTO_UPGRADE_AI` — is therefore **filtered out and never reaches the screen**.
- That filter landed **2026-05-28** (`1b6cd01`, V1X-15) — *before* the issue was filed. The Labs
  list surfaces **11 of 12** research types; the deferred one is invisible, not badged.
- `ENEMY_INTEL`, the other historically-deferred type, was fully wired in V1X-15b (no longer a stub).

So **Gate B's actual requirement — no misleading half-built features shown to testers — is already
satisfied in shipped behaviour.** `AUTO_UPGRADE_AI` is hidden.

### The real gaps (what this work fixes)

1. **Code lies about reality.** Because no coming-soon type ever reaches `ResearchCard`, the
   `info.type.isComingSoon ->` branches in `LabsScreen.kt` (the `"COMING SOON"` title badge,
   ~lines 126–130; the empty action branch, ~lines 143–149) are **dead, unreachable code**. Their
   comments still describe a live "Coming Soon badge … disables the Start Research button" flow —
   almost certainly what misled the issue author into thinking the stub is still visible.
2. **No test pins the filter.** `ResearchTypeTest` asserts the *flag*
   (`AUTO_UPGRADE_AI.isComingSoon == true`) but nothing asserts the *Labs list excludes coming-soon
   entries*. Deleting the `filterNot` at `LabsViewModel.kt:76` would re-expose the stub **with every
   existing test still green** — a latent content-honesty regression.
3. **Stale comment** in `LabsViewModel.startResearch` claims the UI "suppresses the Start Research
   button when isComingSoon"; the truth is the item is filtered from the list entirely.

This work makes the code match reality and locks the win with a regression guard. It does **not**
implement the auto-upgrade feature (not required for Gate B; remains a v1.x backlog item).

## 2. Goals / non-goals

**Goals**
- Make the Labs code honestly reflect that coming-soon research is hidden, not badged.
- Add a regression guard so the filter cannot be silently removed (re-exposing the stub).
- Correct the false code comments.
- Satisfy and tick Gate B; close #44.

**Non-goals (explicitly out of scope)**
- Implementing `AUTO_UPGRADE_AI`'s actual gameplay (auto-purchase / auto-rush). Heavier than
  ENEMY_INTEL was, ambiguous by name, and **not required for Gate B**. Stays a v1.x item.
- `Biome.CELESTIAL_GATE.isComingSoon` — that is a biome gate, a different concern from #44 (which
  is about the Labs research screen). Untouched.
- Any schema, migration, engine, economy, or DI change.

## 3. Changes

### 3.1 Pure-domain helper — authoritative "what surfaces in Labs" gate

Add a companion to `ResearchType` (`domain/model/ResearchType.kt`):

```kotlin
companion object {
    /**
     * Research types surfaced in the Labs UI — excludes [isComingSoon] (deferred-to-v1.x)
     * entries so testers never see a half-built stub. The single source of truth for the
     * Labs list filter; [com.whitefang.stepsofbabylon.presentation.labs.LabsViewModel]
     * calls this rather than re-deriving the filter inline. Pure (no Android), so the
     * UI-list contract is JVM-testable without instantiating the VM (whose `while(true)`
     * ticker complicates direct construction).
     */
    fun surfacedInLabs(): List<ResearchType> = entries.filterNot { it.isComingSoon }
}
```

Then `LabsViewModel.kt:76` changes from:
```kotlin
researchList = ResearchType.entries.filterNot { it.isComingSoon }.map { type -> … }
```
to:
```kotlin
researchList = ResearchType.surfacedInLabs().map { type -> … }
```

Behaviour is identical (same elements, same order — `entries` order is preserved by `filterNot`).
The win is that the list semantics are now a named, pure, directly-testable function.

### 3.2 Delete dead UI branches + fix comments (`presentation/labs/LabsScreen.kt`)

Remove the two unreachable `info.type.isComingSoon ->` branches in `ResearchCard`:
- the `"COMING SOON"` title-chip branch in the title-row `when` (~126–130), and
- the empty action-area branch (~143–149) plus its multi-line comment.

These can never execute because no coming-soon type reaches `ResearchCard` (§3.1 filters them out
before the `items(state.researchList)` loop). The remaining `when` arms — `isMaxed`, `isActive`,
`!slotAvailable`, and the default Start arm — are unaffected; the title-row `when` falls through to
its `isMaxed` / `Lv N` arms exactly as before for every surfaced type.

No imports become unused as a result that aren't already used elsewhere — verify during
implementation (the badge branch uses `Text` + `MaterialTheme`, both still used pervasively).

### 3.3 Keep the VM defensive guard; fix its comment (`presentation/labs/LabsViewModel.kt`)

`startResearch`'s `if (type.isComingSoon) { _userMessage.value = …; return }` guard **stays** — it
is cheap defense-in-depth against any *future* caller (deep-link, quick-research flow) that might
bypass the list filter. Only its comment is corrected: it currently says the Labs UI "suppresses the
Start Research button when isComingSoon"; the accurate statement is that coming-soon types are
**filtered out of the surfaced list** (`ResearchType.surfacedInLabs()`), so the UI never offers a
Start button for them, and this guard is the belt-and-braces second layer.

## 4. Tests

All pure JVM, in `domain/model/ResearchTypeTest.kt` (no VM instantiation — avoids the `while(true)`
ticker entirely, per the Pure-helper decision):

1. **Keep** the existing `only AUTO_UPGRADE_AI is flagged isComingSoon` set-equality test
   (the flag contract) and the `ENEMY_INTEL has full balance values populated` test — unchanged.
2. **Add** `surfacedInLabs excludes coming-soon research`:
   `ResearchType.surfacedInLabs()` contains **no** `isComingSoon` entry
   (`assertTrue(surfacedInLabs().none { it.isComingSoon })`) and specifically does **not** contain
   `AUTO_UPGRADE_AI`.
3. **Add** `surfacedInLabs is exactly the wired types`:
   `surfacedInLabs().toSet() == ResearchType.entries.toSet() - ResearchType.AUTO_UPGRADE_AI`
   (i.e. all 12 minus the one deferred = the 11 wired types), pinning that the helper neither
   over- nor under-filters. This is the guard that fails red if someone removes the filter.

The existing `LabsViewModelTest` is **not** modified (it deliberately avoids constructing the VM;
the pure helper carries the filter contract instead).

## 5. Risk & fragile-zone check

- **No fragile zone touched.** Not in `domain/model/`'s balance constants (this adds a pure helper,
  changes no cost/effect values — `ResearchTypeTest`'s balance assertions stay green), not the
  battle engine, not economy spend/claim, not concurrency. The `LabsViewModel` ticker is left alone.
- **Behaviour-preserving:** §3.1 is a refactor to a same-result helper; §3.2 deletes only
  provably-unreachable branches; §3.3 is comment-only on a guard that already exists. No
  player-visible change (the stub was already hidden).
- **Verification:** `./run-gradle.sh testDebugUnitTest lintDebug assembleDebug` green; new tests
  red-before-green confirmed by temporarily reverting the `filterNot` in a scratch check.

## 6. Outcome

- ~3 production files (`ResearchType.kt` +helper, `LabsScreen.kt` −dead branches,
  `LabsViewModel.kt` call-site + comment), +2 JVM tests.
- No schema / engine / economy / DI change. Test count 1052 → ~1054.
- Gate B (content honesty) ticked; #44 closed with a note that the visible-stub complaint was
  resolved by the V1X-15 filter and this PR makes the code match + guards it.
