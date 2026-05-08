# Run Log

## 2026-05-08 — Phase C.2 PR 3b + 3c: seed remaining milestone cosmetics (closes milestone-cosmetic gap)

- **Goal:** Batch the last two milestone-cosmetic content PRs (PR 3b for MARATHON_WALKER's `garden_ziggurat_skin`, PR 3c for GLOBE_TROTTER's `sandals_of_gilgamesh`) into a single PR since they share the same file, test-update pattern, and risk profile. After this lands, all 6 Milestone entries have `Success` end-to-end on `ClaimMilestone` — the RO-07 "shipped but disabled" monetization gap tracked since Plan R2-11 is fully resolved.
- **Preflight:** read `START_HERE`, `STATE`, `CONSTRAINTS`, `RUN_LOG` head (C.2 PR 3 + ensureSeedData fix + C.4 + C.2 PR 2 entries). `git status` clean on `main`, up to date with origin (last commit `8c907c1 feat(cosmetics): seed lapis_lazuli_skin (C.2 PR 3, IRON_SOLES reward)`). Confirmed the PR 3 pattern: one seed row + one palette entry + one ClaimMilestoneTest rewire (remove UnknownCosmetic, add end-to-end Success) + one CosmeticRepositoryImplTest palette test. Batching doubles the content but keeps the pattern identical.

### Design

**Why batch 3b + 3c.** Evaluated three options:
1. **Option A (chosen):** Single PR with both seed rows, both palettes, both test rewires. Same file (`CosmeticRepositoryImpl.kt`), same test files. Two narratively-distinct PRs would produce mechanically identical diffs + sync-doc churn for effectively zero risk difference. Merged PR size stays small (~120 insertions) — well under a reasonable review threshold.
2. **Option B:** Two sequential PRs. Strictly cleaner git history but doubles doc-sync work (two CHANGELOG sections, two STATE.md edits, two RUN_LOG entries) for no functional benefit.
3. **Option C:** Land PR 3b separately + defer PR 3c pending a category decision. The category decision is small enough to fold into this PR.

**Palette choices.**
- **`garden_ziggurat_skin` (MARATHON_WALKER):** Hanging Gardens biome-themed. Matches the Tier 1-2 Hanging Gardens biome in the GDD. `[0xFF8B4726, 0xFFAD7B4C, 0xFF5E7F47, 0xFF7BA85A, 0xFFE0C890]` — terracotta ziggurat base → sun-bleached sandstone → mossy vines begin → lush foliage → pale bloom canopy. The progression from stone at the base to greenery at the top captures the "ziggurat overtaken by gardens" vibe.
- **`sandals_of_gilgamesh` (GLOBE_TROTTER):** Heroic bronze / ancient Sumerian theme. `[0xFF3B2A1A, 0xFF6B4A2A, 0xFF8B6B42, 0xFFB89152, 0xFFE8C068]` — dark weathered bronze → aged bronze → warm bronze → polished brass → gold crown. The gold crown echoes `lapis_lazuli_skin` and signals "legendary" status.

**Category decision for `sandals_of_gilgamesh`.** The id literally means footwear but the cosmetic is implemented as `ZIGGURAT_SKIN`. Three options:
1. **Option A (chosen):** Reframe via description. "Bronze ziggurat in honour of Gilgamesh, whose sandals walked the edges of the world." Keeps existing `CosmeticCategory` enum + pipeline intact. No schema change. The name stays `Sandals of Gilgamesh` because that's the milestone-reward name from `Milestone.GLOBE_TROTTER.rewards`.
2. **Option B:** Add a `PLAYER_AVATAR` category + new rendering path. Architecturally cleaner but requires pipeline extension, new lookup table, new `BattleViewModel` hydration. Not justified for one cosmetic.
3. **Option C:** Rename the milestone reward id. Would require a matching change in `Milestone.kt` enum. Breaks ADR-0003 / content-as-code contract; C.4 detection explicitly says "do not rename the 3 mismatched IDs" (content decision, deferred to this PR — now resolved via reframe).

Option A wins because it's the smallest diff that works. If future milestones introduce *multiple* player-avatar cosmetics, revisit Option B as a RO-07 follow-up.

**Synthetic rejection-before-atomic guard.** Post-PR, no prod Milestone returns `UnknownCosmetic` — all 3 milestone ids are seeded. The `UnknownCosmetic rejects claim before the atomic DAO call with no credit` test still uses MARATHON_WALKER against the empty `FakeCosmeticRepository`, but its narrative now says "synthetic mechanism-level regression guard against future content work that introduces a new Milestone with an unseeded Cosmetic reward." Kept because removing it loses mechanism coverage; the mechanism is non-trivial (iterate rewards, filter Cosmetic, call idExists, short-circuit return) and worth a dedicated test even in the absence of a prod trigger.

**End-to-end success tests for all 3 milestone cosmetics.** Added `MARATHON_WALKER claim succeeds end-to-end via real CosmeticRepositoryImpl` + `GLOBE_TROTTER claim succeeds end-to-end via real CosmeticRepositoryImpl`, mirroring the `IRON_SOLES` test from PR 3. Each uses `CosmeticRepositoryImpl(FakeCosmeticDao())` directly so the whole chain (`SEED_COSMETICS → ensureSeedData → idExists → ClaimMilestone atomic credit → wallet`) is exercised, not just the `ClaimMilestone` layer. Every Milestone with a Cosmetic reward now has a dedicated end-to-end test — symmetric coverage.

### Files touched

- `app/src/main/java/.../data/repository/CosmeticRepositoryImpl.kt`:
  - `ZIGGURAT_COLOR_LOOKUP` +2 entries (garden, sandals). KDoc expanded to document all 4 current palettes + the GLOBE_TROTTER reframe rationale.
  - `SEED_COSMETICS` +2 rows (positioned directly after `lapis_lazuli_skin` so all 4 palette-shipping cosmetics cluster at the top). Inline comments explain each row's milestone reward link and the Store visibility policy (not in ENABLED_COSMETIC_ID — milestone-only acquisition for now).
- `app/src/test/java/.../domain/usecase/ClaimMilestoneTest.kt`:
  - Removed `UnknownCosmetic surfaces offending cosmetic id for MARATHON_WALKER` + `... for GLOBE_TROTTER`.
  - Rewrote the rejection-before-atomic regression guard's comment to explain the synthetic-mechanism-level semantics.
  - Added `MARATHON_WALKER claim succeeds end-to-end via real CosmeticRepositoryImpl` (600 Gems assertion) + `GLOBE_TROTTER claim succeeds end-to-end via real CosmeticRepositoryImpl` (500 Gems assertion).
  - Updated the setup comment to reflect "no more prod mismatches."
- `app/src/test/java/.../data/repository/CosmeticRepositoryImplTest.kt`:
  - +2 palette tests (`C2PR3b - garden_ziggurat_skin propagates hanging-gardens palette`, `C2PR3c - sandals_of_gilgamesh propagates bronze-ziggurat palette`), each with exact 5-int assertions matching the `zig_jade` / `lapis_lazuli_skin` pattern.
  - Updated 3 count assertions (9 → 11): idempotency, partial-catalogue upgrade, existing-row preservation.
  - Partial-catalogue upgrade test now asserts all 4 palette-shipping cosmetics land correctly (was asserting 2).
  - "Other seeded ziggurat cosmetics null overrideColors" comment updated to list all 4 palette cosmetics.

### Test changes (+2 net: 486 → 488)

- **ClaimMilestoneTest:** -2 (UnknownCosmetic MARATHON_WALKER + GLOBE_TROTTER) + 2 (end-to-end Success for both) = **net 0**.
- **CosmeticRepositoryImplTest:** +2 new palette tests = **+2**.
- Net: **+2** (486 → 488).

### Verification

- `./run-gradle.sh test` — BUILD SUCCESSFUL in 19s. 0 failures, 0 errors, 0 skipped.
- Test count: **486 → 488** (matches net-+2 expectation).
- Grep sanity checks:
  - `grep -c "garden_ziggurat_skin\|sandals_of_gilgamesh" app/src/main/java/com/whitefang/stepsofbabylon/data/repository/CosmeticRepositoryImpl.kt` — 6 (each id appears 3 times: lookup entry + seed row + KDoc).
  - `grep "UnknownCosmetic surfaces offending cosmetic id" app/src/test` — 0 (both stale tests removed).
  - `grep -c "claim succeeds end-to-end" app/src/test/java/com/whitefang/stepsofbabylon/domain/usecase/ClaimMilestoneTest.kt` — 3 (one per milestone with Cosmetic reward: IRON_SOLES, MARATHON_WALKER, GLOBE_TROTTER).
- All 4 palette-shipping cosmetics render correctly via the existing C.2 PR 1 pipeline (no renderer changes needed; pipeline is additive and data-driven).

### Surface changes

- 2 new `SEED_COSMETICS` rows + 2 new `ZIGGURAT_COLOR_LOOKUP` entries. Both land on any install via the `ensureSeedData` per-cosmeticId filter.
- No public API changes. No DB schema changes. No Room migration. Still on v8.
- No new production dependencies.
- No ADR — content + narrative-reframe decision documented in the SEED_COSMETICS inline comments + the ZIGGURAT_COLOR_LOOKUP KDoc at point-of-use.

### Milestone

**RO-07 milestone-cosmetic gap fully closed.** All 6 Milestone entries claim cleanly end-to-end:
| Milestone | Steps | Cosmetic | Result |
|---|---|---|---|
| FIRST_STEPS | 1K | *(none)* | ✅ Success |
| MORNING_JOGGER | 10K | *(none)* | ✅ Success |
| TRAIL_BLAZER | 100K | *(none)* | ✅ Success |
| MARATHON_WALKER | 500K | `garden_ziggurat_skin` | ✅ Success (this PR) |
| IRON_SOLES | 1M | `lapis_lazuli_skin` | ✅ Success (C.2 PR 3) |
| GLOBE_TROTTER | 5M | `sandals_of_gilgamesh` | ✅ Success (this PR) |

### Open questions / blockers

- **None.** All cosmetic-related debt tracked since Plan R2-11 is closed. No prod Milestone currently returns `UnknownCosmetic`.
- **Store visibility of milestone-reward cosmetics.** Currently all 3 milestone cosmetics show "Coming Soon" in the Store. Product decision deferred: enable them in `ENABLED_COSMETIC_ID` at higher store prices (500-600 Gems matches the current values), or keep milestone-only. Not blocking — game works either way.

### Follow-ups

- **Open ADR-0005 (Billing SDK) and ADR-0006 (Ad SDK) stubs.** Prerequisite for C.5 / C.6. These are now the top release-critical items.
- **C.5 — Real Google Play Billing Library v7 swap.** High risk: real SDK failure paths have never run against prod code. A.4 fake tests provide the unit-test safety net; internal-track testing + Firebase pre-launch report complete the verification.
- **C.6 — Real AdMob swap.** Same shape as C.5.
- **B.4 FollowOnPipeline + B.5 UpdateMissionProgress.** Pure debt; land opportunistically.
- **Phase D (Plan 31).** Play Console setup, AAB upload, Firebase pre-launch. Depends on C.5 + C.6 + public privacy policy URL.

### Memory updated

- `STATE.md` ✅ — current objective now "Phase C.2 PR 3b + 3c landed"; new bullet in "what works"; Known-issues list updated to reflect 4 plumbed palettes; priorities / next-actions rotated (ADR stubs + C.5/C.6 now top); test count 486 → 488; critical path marks PRs 1+2+3+3b+3c done; last-run updated.
- `RUN_LOG.md` ✅ — this entry.
- ADR: not warranted — content + narrative-reframe decision captured in KDoc at point-of-use.

## 2026-05-08 — Phase C.2 PR 3: seed lapis_lazuli_skin (resolves IRON_SOLES UnknownCosmetic)

- **Goal:** Land the first of three milestone-cosmetic content PRs per STATE.md next-actions #1. Seeds `lapis_lazuli_skin` in `SEED_COSMETICS` + its palette in `ZIGGURAT_COLOR_LOOKUP`, which flips `ClaimMilestone(IRON_SOLES)` from returning `UnknownCosmetic("lapis_lazuli_skin")` (C.4 detection behaviour) to returning `Success` with a 200 Gems + 50 Power Stones atomic credit. Two more milestone cosmetics (`garden_ziggurat_skin` for MARATHON_WALKER, `sandals_of_gilgamesh` for GLOBE_TROTTER) remain for PR 3b/3c.
- **Preflight:** read `START_HERE`, `STATE`, `CONSTRAINTS`, `RUN_LOG` head (ensureSeedData fix + C.4 + C.2 PR 2 + C.2 PR 1 entries). `git status` clean on `main`, up to date with origin (last commit `a510350 fix(cosmetics): ensureSeedData per-cosmeticId filter`, pushed). Re-read `Milestone.IRON_SOLES` (lapis_lazuli_skin reward id confirmed), `CosmeticRepositoryImpl` (SEED_COSMETICS after PR 2 + ensureSeedData fix), `ClaimMilestoneTest` (12 cases from C.4), `CosmeticRepositoryImplTest` (7 cases from C.2 PR 2 + ensureSeedData fix). Confirmed `FakeCosmeticDao` already exists (C.2 PR 2).

### Design

**Palette choice.** Traditional lapis lazuli is a near-pure-blue semi-precious stone with gold-yellow pyrite flecks. Went for 4 lapis-blue gradient layers + 1 pyrite-gold crown layer (layer 4, top) to evoke that classic "lapis with gold" visual: `[0xFF1A1F5C, 0xFF2A3880, 0xFF3B4FAB, 0xFF4F68C8, 0xFFD4A84A]`. The gold crown at the top is the distinguishing visual note \u2014 pure blue would read as generic sapphire or cobalt. Same 5-ints / bottom-to-top contract as `zig_jade` in PR 2.

**Store pricing and visibility.** Set `priceGems = 500` \u2014 highest in the ziggurat-skin catalogue (above `zig_golden` at 300). Signals "elite" status. Intentionally NOT added to `StoreScreen.ENABLED_COSMETIC_ID` because the primary acquisition path is the IRON_SOLES milestone (1M lifetime steps). The Store still shows it as "Coming Soon" (R2-11 guard), which is fine \u2014 the milestone reward is the canonical way to get it. Whether to eventually enable Store purchase is a future UX decision.

**Seed-row placement.** Added as the second entry in `SEED_COSMETICS`, directly after `zig_jade`. Two reasons: (a) both have shipping palettes, so grouping them at the top of the catalogue is visually coherent, and (b) the Store cosmetics section renders in `SEED_COSMETICS` order, so the two "real" cosmetics surface at the top.

**ClaimMilestoneTest migration: remove the stale IRON_SOLES UnknownCosmetic test.** The C.4 test suite had three `UnknownCosmetic surfaces offending cosmetic id for <milestone>` tests, one per mismatched id. Post-PR 3, `lapis_lazuli_skin` IS in SEED_COSMETICS, so `useCase(IRON_SOLES)` against the real impl now returns `Success`, not `UnknownCosmetic`. The ClaimMilestoneTest uses a `FakeCosmeticRepository` with an empty items list by default, so the synthetic test would still pass (fake's `idExists` would still return false), but the narrative meaning has changed \u2014 it no longer reflects prod. Removing it and keeping the MARATHON_WALKER + GLOBE_TROTTER tests ensures the UnknownCosmetic test suite genuinely tracks the remaining mismatched ids. Each future PR (3b, 3c) will remove one more.

**Switch the rejection-before-atomic regression guard target.** The `UnknownCosmetic rejects claim before the atomic DAO call with no credit` test was using IRON_SOLES. Same reasoning: post-PR 3, IRON_SOLES is no longer the right exemplar \u2014 lapis is seeded, so the rejection wouldn't happen against the real impl. Switched to MARATHON_WALKER (`garden_ziggurat_skin` still unknown). This keeps the test's narrative \u2014 "rejection happens before atomic" \u2014 aligned with prod.

**Rewrite the positive-path test as end-to-end with the real impl.** The C.4 positive-path test `milestone with matching cosmetic id credits rewards via atomic path` explicitly seeded a `lapis_lazuli_skin` fixture in the `FakeCosmeticRepository` and asserted Success. Post-PR 3, the fixture is redundant \u2014 the real `CosmeticRepositoryImpl + FakeCosmeticDao` also has the id. Rewrote as `IRON_SOLES claim succeeds end-to-end via real CosmeticRepositoryImpl`: constructs the real impl with a fresh `FakeCosmeticDao()` and calls `useCase(IRON_SOLES)`, proving the whole chain. This is a stronger test than the old fixture-based one \u2014 it verifies the SEED_COSMETICS catalogue itself contains the right entry, not just that "if the id were known, the flow would work."

**CosmeticRepositoryImplTest row-count updates.** Three tests had `assertEquals(8, ...)` assertions for seed-row count (idempotency, partial-catalogue upgrade, existing-row preservation). All updated to 9. The partial-catalogue upgrade test also gained a palette assertion for the new `lapis_lazuli_skin` row to prove both palettes land on the same upgrade path \u2014 previously it only checked `zig_jade`. New `C2PR3 - lapis_lazuli_skin propagates lapis palette via overrideColors from ZIGGURAT_COLOR_LOOKUP` test mirrors the C.2 PR 2 `zig_jade` palette assertion shape with the exact 5-int lapis gradient.

### Files touched

- `app/src/main/java/.../data/repository/CosmeticRepositoryImpl.kt` \u2014 +1 `ZIGGURAT_COLOR_LOOKUP` entry (lapis palette), +1 `SEED_COSMETICS` row (`lapis_lazuli_skin`), expanded KDoc on the lookup table to document the PR 3 entry + pending PR 3b/3c entries. Also cleaned up a stale comment in `idExists` that referenced the old `dao.count() > 0` gate (post-ensureSeedData-fix mismatch).
- `app/src/test/java/.../domain/usecase/ClaimMilestoneTest.kt` \u2014 removed `CosmeticCategory` + `CosmeticItem` imports (no longer needed after the positive-path test switched to the real impl); added `CosmeticRepositoryImpl` + `FakeCosmeticDao` imports. Removed the IRON_SOLES UnknownCosmetic test. Repointed the rejection-before-atomic regression guard at MARATHON_WALKER. Rewrote the positive-path test as the real-impl end-to-end case. Updated setup comment to reflect 2 still-mismatched ids (post-PR-3 state).
- `app/src/test/java/.../data/repository/CosmeticRepositoryImplTest.kt` \u2014 +1 new `C2PR3 - lapis_lazuli_skin propagates lapis palette` test with exact-value assertion. Updated 3 count assertions (8 \u2192 9). Updated partial-catalogue upgrade test to check both `zig_jade` and `lapis_lazuli_skin` palettes. Updated comment on `other seeded ziggurat cosmetics have null overrideColors` to mention both PR 2 + PR 3 palettes.

### Tests rewired (0 net change, 486 \u2192 486)

**ClaimMilestoneTest: 12 \u2192 11 cases.**
- Removed: `UnknownCosmetic surfaces offending cosmetic id for IRON_SOLES` (prod semantics flipped \u2014 lapis_lazuli_skin is now seeded).
- Repointed: `UnknownCosmetic rejects claim before the atomic DAO call with no credit` now targets MARATHON_WALKER (garden_ziggurat_skin still unknown).
- Rewritten: `milestone with matching cosmetic id credits rewards via atomic path` \u2192 `IRON_SOLES claim succeeds end-to-end via real CosmeticRepositoryImpl`. Uses `CosmeticRepositoryImpl(FakeCosmeticDao())` directly; proves the full chain from SEED_COSMETICS through to wallet credit without any fixture intermediaries.
- Preserved unchanged: 9 other tests (step-threshold guard, Gems credit on Success, marks claimed, AlreadyClaimed, atomic path, concurrent claims, pre-existing claimed entity, MARATHON_WALKER UnknownCosmetic, GLOBE_TROTTER UnknownCosmetic).

**CosmeticRepositoryImplTest: 7 \u2192 8 cases.**
- Added: `C2PR3 - lapis_lazuli_skin propagates lapis palette via overrideColors from ZIGGURAT_COLOR_LOOKUP` \u2014 exact-value assertion matching the `zig_jade` pattern.
- Updated 3 count assertions: idempotency (8 \u2192 9), partial-catalogue upgrade (expected post-count 8 \u2192 9, now checks both palettes), existing-row preservation (expected post-count 8 \u2192 9).

**Net total tests: 486 \u2192 486 (-1 + 1 = 0).**

### Verification

- `./run-gradle.sh test` \u2014 BUILD SUCCESSFUL in 19s, 36 actionable tasks. 0 failures, 0 errors, 0 skipped.
- Test count: 486 (unchanged, matches net-zero expectation).
- Grep sanity checks:
  - `grep -c "lapis_lazuli_skin" app/src/main/java/com/whitefang/stepsofbabylon/data/repository/CosmeticRepositoryImpl.kt` \u2014 4 (seed row + lookup entry + 2 KDoc mentions).
  - `grep "UnknownCosmetic surfaces offending cosmetic id for IRON_SOLES" app/src/test` \u2014 0 (stale test removed).
  - `grep -c "IRON_SOLES" app/src/test/java/com/whitefang/stepsofbabylon/domain/usecase/ClaimMilestoneTest.kt` \u2014 2 (just the new end-to-end test).
- Behaviour preservation: all 9 pre-existing ClaimMilestone tests pass unchanged; the concurrent-claims test still uses MORNING_JOGGER (which has no Cosmetic reward), so C.4 pre-flight doesn't interfere with the atomicity test.

### Surface changes

- No public API changes.
- 1 new `SEED_COSMETICS` row + 1 new `ZIGGURAT_COLOR_LOOKUP` entry. Both land on any install via the post-fix `ensureSeedData` (the ensureSeedData fix from the previous PR directly unblocks this one).
- No DB schema changes. No Room migration. Still on v8.
- No new production dependencies.
- No ADR \u2014 this is content work; design rationale (palette choice, pricing, store visibility) is captured in the `ZIGGURAT_COLOR_LOOKUP` + `SEED_COSMETICS` KDoc at point-of-use.

### Open questions / blockers

- **None.** C.2 PR 3b (MARATHON_WALKER) and PR 3c (GLOBE_TROTTER) are mechanically identical and ready to land on the same pattern.
- **Palette-design note for PR 3b:** MARATHON_WALKER's reward is `garden_ziggurat_skin`. Hanging Gardens is the first biome (Tier 1\u20132), so a green + terracotta palette is the obvious choice \u2014 lush greens at the base fading to terracotta / sandstone at the top.
- **Category decision deferred for PR 3c:** GLOBE_TROTTER's reward is `sandals_of_gilgamesh` \u2014 semantically footwear, not a ziggurat skin. The current `CosmeticCategory` enum has `ZIGGURAT_SKIN`, `PROJECTILE_EFFECT`, `ENEMY_SKIN`. Options: (a) add a `PLAYER_AVATAR` category (new enum value + new category of ZIGGURAT_COLOR_LOOKUP-equivalent lookup), (b) repurpose as "Gilgamesh Ziggurat" ZIGGURAT_SKIN, keeping the palette system consistent. Option (b) is simpler and ships in PR 3c; option (a) is better if future milestones introduce more player-avatar cosmetics. Document the decision in PR 3c's notes.

### Follow-ups

- **C.2 PR 3b** (next): add `garden_ziggurat_skin` with Hanging Gardens palette. Remove MARATHON_WALKER UnknownCosmetic test. Add `MARATHON_WALKER claim succeeds end-to-end` real-impl test. Update count assertions 9 \u2192 10.
- **C.2 PR 3c** (next): add `sandals_of_gilgamesh`. Decide category. Remove GLOBE_TROTTER UnknownCosmetic test. Add `GLOBE_TROTTER claim succeeds end-to-end` real-impl test. Update count assertions 10 \u2192 11. After this lands, all 6 Milestone entries are fully claimable end-to-end \u2014 closes the "shipped but disabled" monetization gap tracked since Plan R2-11.
- **C.5 + C.6:** real Billing + Ad SDK swaps (gated on ADR-0005/0006). Independent.
- **B.4 / B.5:** pure debt; can land opportunistically.

### Memory updated

- `STATE.md` \u2705 \u2014 current objective now "Phase C.2 PR 3 landed"; new bullet in "what works" for PR 3; test count stays 486 with net-0 note; priorities/next-actions reshuffled (PR 3b \u2192 #1, PR 3c \u2192 #2, C.5/C.6 \u2192 #3); critical path marks PRs 1+2+3 done; last-run updated.
- `RUN_LOG.md` \u2705 \u2014 this entry.
- ADR: not warranted \u2014 content work; palette / pricing / store-visibility decisions captured in KDoc at point-of-use.

## 2026-05-08 — Fix: ensureSeedData per-cosmeticId filter (unblocks C.2 PR 3+)

- **Goal:** Close the known-debt item called out in STATE.md and the C.2 PR 2 CHANGELOG entry \u2014 `CosmeticRepositoryImpl.ensureSeedData` short-circuited on `dao.count() > 0`, which meant any new `SEED_COSMETICS` row added after a device's first install would never land without a data clear. That blocked the C.2 PR 3+ rolling content cadence (the 3 milestone cosmetic seed rows `lapis_lazuli_skin` / `garden_ziggurat_skin` / `sandals_of_gilgamesh` that flip the C.4 UnknownCosmetic detections to Success). One-line gate \u2192 5-line per-cosmeticId filter. Small, low-risk, prerequisite.
- **Preflight:** read `START_HERE`, `STATE`, `CONSTRAINTS`, `RUN_LOG` head (C.4 + C.2 PR 2 + doc-sweep entries). `git status` clean on `main`, up to date with origin (last commit `c9e6033 feat(milestones): detect UnknownCosmetic in ClaimMilestone (C.4)`, pushed). Re-read `CosmeticEntity` (confirmed primary key is `id` auto-gen, not `cosmeticId`), `CosmeticDao` (upsert semantics from Room), `CosmeticRepositoryImpl` (current ensureSeedData), existing `CosmeticRepositoryImplTest` (5 C.2 PR 2 cases including the idempotency test that asserts "count gate holds"). Grep-confirmed `idExists` from C.4 already lazy-calls `ensureSeedData()` \u2014 so fixing the gate here also makes the C.4 pre-flight check behave correctly on partial-catalogue devices.

### Design

**Shape: filter + conditional upsertAll, not universal upsert.** Evaluated three options:
1. **Option A (chosen):** Read existing ids once via `observeAll().first().mapTo(HashSet())`, compute `missing = SEED_COSMETICS.filter { it.cosmeticId !in existingIds }`, `upsertAll(missing)` only when non-empty.
2. **Option B (rejected):** Drop the gate entirely and `upsertAll(SEED_COSMETICS)` on every call. Naive; `CosmeticEntity`'s `@PrimaryKey(autoGenerate = true)` means a seed row with `id = 0` would insert as a brand-new auto-gen row every time, creating duplicates by the second call. Would need either a schema change (make `cosmeticId` the PK) or a conflict-resolution strategy (Room's `@Upsert` is conflict-by-PK only).
3. **Option C (rejected):** Schema migration to make `cosmeticId` the primary key. Bigger change; requires DB version bump (v8 \u2192 v9) + a migration that rekeys the table; not justified when Option A achieves the same end-state without touching schema.

Option A wins because it's scoped, additive, and sidesteps the duplicate-row risk by never passing already-present rows to the DAO. Player state on existing rows (`isOwned`, `isEquipped`) is preserved simply because the filter skips those rows entirely \u2014 there's no re-upsert to overwrite.

**Three behaviours, one contract.** Called out explicitly in the inline KDoc so future readers don't need to reason through it:
- **Fresh install** (count == 0, existingIds empty): `missing == SEED_COSMETICS`, all 8 rows inserted. Identical to pre-fix behaviour.
- **Partial-catalogue upgrade** (count == 7, the pre-`zig_jade` state): `missing == [zig_jade]`, one row inserted. Previously broken (the gate short-circuited because count > 0).
- **Steady state** (count == 8, all ids present): `missing == emptyList()`, no DAO write. Same end-state as the old gate, arrived via a different mechanism.

**HashSet + mapTo over List + contains.** Using `mapTo(HashSet()) { it.cosmeticId }` instead of `map { it.cosmeticId }` so the `in` check is O(1). Minor, but matters if the catalogue ever grows to dozens of items.

**Chose not to touch `ensureSeedData` call-site contract.** Still returns `Unit`, still suspend, still idempotent. Callers (StoreViewModel init, CosmeticRepositoryImpl.idExists) don't need updates.

### Files touched

- `app/src/main/java/.../data/repository/CosmeticRepositoryImpl.kt` \u2014 rewrote `ensureSeedData` body (3 lines \u2192 3 lines of logic + extensive inline KDoc explaining the 3 behaviours and the auto-gen-PK rationale). No other changes in the file.
- `app/src/test/java/.../data/repository/CosmeticRepositoryImplTest.kt` \u2014 +`CosmeticEntity` import (for direct test-side seeding); renamed the existing idempotency test (removed "count gate holds" phrase; updated comment to describe the filter-based mechanism \u2014 same end-state assertion); +2 new regression-guard cases.

### Tests added (2 new cases in `CosmeticRepositoryImplTest`)

1. **`ensureSeedData inserts newly-added rows on partial catalogue upgrade`** \u2014 pre-seeds 7 legacy rows manually (`zig_obsidian`, `zig_crystal`, `zig_golden`, `proj_fire`, `proj_lightning`, `enemy_shadow`, `enemy_neon`) via direct DAO upsert, asserts baseline count == 7, calls `repo.ensureSeedData()`, asserts count == 8 (only `zig_jade` added). Additionally asserts:
   - `zig_jade.overrideColors` is non-null with 5 entries (proves ZIGGURAT_COLOR_LOOKUP still plumbs through for freshly-seeded rows).
   - Every one of the 7 legacy ids still present (proves the upgrade is additive, not replacive).
   
   This case would have failed pre-fix: the count > 0 gate would short-circuit and `zig_jade` never inserted.

2. **`ensureSeedData preserves player state on existing rows (isOwned, isEquipped)`** \u2014 the most player-visible risk of a naive re-upsert approach. Pre-seeds `zig_jade` with `isOwned = true, isEquipped = true`, calls `ensureSeedData`, asserts the jade row's player state survives and all 8 rows are present. Proves the filter skips already-present ids entirely so no overwrite occurs.

### Test name renamed

- `C2PR2 - ensureSeedData is idempotent on repeat call (count gate holds)` \u2192 `C2PR2 - ensureSeedData is idempotent on repeat call`. The `(count gate holds)` parenthetical was directly tied to the old `dao.count() > 0` short-circuit implementation \u2014 stale now. Updated the test's explanatory comment to say "filter produces `missing == emptyList()` on the second call" so future readers understand the new mechanism.

### Verification

- `./run-gradle.sh test` \u2014 BUILD SUCCESSFUL in 18s, 36 actionable tasks, 11 executed. Test count: **484 \u2192 486 JVM tests** (+2, matches the 2 new regression-guard cases exactly). 0 failures, 0 errors, 0 skipped.
- Sanity check: `grep -c "dao.count()" app/src/main/java/com/whitefang/stepsofbabylon/data/repository/CosmeticRepositoryImpl.kt` \u2014 0 (the old gate is gone). `grep -c "missing" app/src/main/java/com/whitefang/stepsofbabylon/data/repository/CosmeticRepositoryImpl.kt` \u2014 3 (matches the variable + 2 references in the code + KDoc mentions).
- No lint changes, no new warnings, no behaviour changes for fresh installs or steady-state.

### Surface changes

- No public API changes. `CosmeticRepository.ensureSeedData` signature unchanged. All callers (StoreViewModel init, the new C.4 `idExists` lazy-call) benefit automatically.
- No DB schema changes. No Room migration. Still on v8.
- No new production dependencies.
- No ADR \u2014 this is bounded bug-fix work; the design decision (Option A vs B vs C) is documented in the inline KDoc at point-of-use.

### Open questions / blockers

- **None.** The count-gate debt flagged in STATE.md after C.2 PR 2 is closed. C.2 PR 3+ can now proceed as a rolling content cadence.
- **Follow-up for C.2 PR 3+ authors:** when adding a new SEED_COSMETICS row, just add it. No ensureSeedData edits needed. The per-cosmeticId filter will pick it up on the next launch for every install, fresh or existing.

### Follow-ups

- **C.2 PR 3 is the natural next PR.** Proposed: `lapis_lazuli_skin` (IRON_SOLES reward). Why first: the existing C.4 positive-path test `milestone with matching cosmetic id credits rewards via atomic path` already uses `lapis_lazuli_skin` as a fixture; replacing the fixture with a real seed row is the smallest possible diff that converts a fixture-based test into a real end-to-end coverage. Side effect: the C.4 `UnknownCosmetic surfaces offending cosmetic id for IRON_SOLES` test will need to be updated to expect Success (the id becomes known).
- Remaining milestone cosmetics (`garden_ziggurat_skin`, `sandals_of_gilgamesh`) land in PR 3b / PR 3c.
- After all 3 milestone cosmetics land, all 6 Milestone enum entries are fully claimable end-to-end \u2014 closes the "shipped but disabled" monetization gap that has been tracked since Plan R2-11.

### Memory updated

- `STATE.md` \u2705 \u2014 current objective now "`ensureSeedData` count-gate fix landed"; new bullet in "what works"; the debt line about `ensureSeedData is all-or-nothing` removed; priorities/next-actions reshuffled (C.2 PR 3 now #1, 3b/3c split out, ADR-0005/0006 + C.5/C.6 shift to #4); test count 484 \u2192 486; critical path marks the fix done; last-run updated.
- `RUN_LOG.md` \u2705 \u2014 this entry.
- ADR: not warranted \u2014 this is a scoped bug-fix; design rationale (Option A vs B vs C) is captured in the inline KDoc at point-of-use, the most discoverable location for future readers.

## 2026-05-08 — Phase C.4: ClaimMilestone UnknownCosmetic detection (RO-07 follow-up)

- **Goal:** Land the detection half of the `ClaimMilestone.Cosmetic` gap per `devdocs/evolution/implementation_roadmap.md` §C.4. Before this PR, `ClaimMilestone` silently dropped `MilestoneReward.Cosmetic` rewards whose ids didn't exist in `SEED_COSMETICS` — the 3 currently-mismatched milestone cosmetic ids (`garden_ziggurat_skin` on MARATHON_WALKER, `lapis_lazuli_skin` on IRON_SOLES, `sandals_of_gilgamesh` on GLOBE_TROTTER) would credit the Gems/PS rewards but never grant the cosmetic, with no observable error. This PR makes the mismatch surface loudly through a sealed-Result return type; resolution (seeding matching rows) stays as C.2 PR 3+ content work per the roadmap's explicit non-goal.
- **Preflight:** read `START_HERE`, `STATE`, `CONSTRAINTS`, `RUN_LOG` head (C.2 PR 2 + doc-sweep + C.2 PR 1 + B.3 PR 2 + B.2 PRs 4-5 entries). `git status` clean on `main`, up to date with origin (last commit `f01d54c feat(cosmetics): seed zig_jade as first end-to-end cosmetic (C.2 PR 2)`, just pushed). Read `ClaimMilestone`, `Milestone` enum (6 entries; 3 with Cosmetic rewards), `MilestoneReward` (sealed: Gems / PowerStones / Cosmetic), `CosmeticRepository` interface, `CosmeticRepositoryImpl` (ensureSeedData + toDomain), `FakeCosmeticRepository`, `MissionsViewModel` (uses ClaimMilestone; existing snackbar infrastructure: none, unlike StoreScreen/WorkshopScreen which already have Scaffold+SnackbarHost), `MissionsScreen`, `MissionsUiState`, `MilestoneDao.claimMilestoneAtomic` (to confirm atomic invariant is preserved), `ClaimMilestoneTest` (8 cases including 3 atomicity tests from B.2 PR 4), `MissionsViewModelTest`. Grep-confirmed ClaimMilestone has exactly 3 construction sites: `MissionsViewModel` (prod), `ClaimMilestoneTest` sut, `MissionsViewModelTest` one direct construction.

### Design

**Result shape: sealed class with `data object` + one `data class`.** Four variants match the 4 distinct rejection/success paths. `Success`, `InsufficientSteps`, `AlreadyClaimed` are singletons (no data, rendered as `data object` per Kotlin 2.3 best practice). `UnknownCosmetic` carries the offending `cosmeticId: String` so consumers can surface the specific id — matters because a player hitting MARATHON_WALKER's cosmetic issue should see a different message than IRON_SOLES's. Named the class `ClaimMilestoneResult` (not `Result`) to avoid any confusion with `kotlin.Result` elsewhere in the codebase. Placed in the same file as `ClaimMilestone` so the two are read together — same pattern as `OpenCardPack.PackTier` / `ActivateOverdrive.Result`.

**Pre-flight cosmetic-id check, not post-atomic recovery.** Two options evaluated:
1. **Option A (chosen):** Before calling `claimMilestoneAtomic`, iterate `milestone.rewards`, and for each `MilestoneReward.Cosmetic` call `cosmeticRepository.idExists(id)`. First unknown wins; return `UnknownCosmetic(id)` immediately with zero wallet movement. Clean: no partial credit, claim stays atomic in the "transition" sense.
2. **Option B (rejected):** Run `claimMilestoneAtomic` (credit Gems/PS + mark claimed), then check cosmetic ids afterwards, return `UnknownCosmetic` on miss. Would still credit the non-cosmetic rewards. Player-friendlier in a sense ("at least they got the Gems"), but contradicts "detection only" — marking the milestone claimed with unknown-cosmetic state couples detection to partial fulfilment and makes post-C.2-PR-3 resolution harder (the row is already `claimed=true` so re-granting the cosmetic when seed lands requires a separate mechanism).

Option A wins because the roadmap is explicit: "Do not silently drop." The strictest reading is "reject the whole claim so nothing silent happens." It also means the test can assert "no wallet movement" as a regression guard — a cleaner invariant than "partial movement" which is hard to test without enumerating exactly what was credited.

**Trade-off acknowledged:** Option A means the 3 affected milestones (MARATHON_WALKER, IRON_SOLES, GLOBE_TROTTER) are **currently un-claimable** until C.2 PR 3+ lands their cosmetic seed rows. A real player today who walks 500k steps and taps "Claim" on MARATHON_WALKER sees a snackbar, not a 600-Gem payout. This is a deliberate C.4 non-goal per the roadmap: "do not rename the 3 mismatched IDs in this PR (that is content work coupled to C.2 PR 3)." The alternative (silent drop) was worse — the player would never learn that they never got their cosmetic. An in-between state where they get the gems but not the cosmetic, and the milestone is marked claimed, would lock us out of fixing it later. So Option A is correct even if user-facing.

**`idExists` on the repo, not on the use case.** Added `suspend fun idExists(cosmeticId: String): Boolean` to the `CosmeticRepository` interface. Real impl lazy-seeds via `ensureSeedData()` then queries `observeAll().first().any { it.cosmeticId == cosmeticId }`. The lazy seed is important: the cosmetic catalogue is otherwise seeded only when `StoreViewModel.init` runs, so a player claiming a milestone from the Missions screen without ever opening the Store would see false-negatives. `FakeCosmeticRepository` checks its `items` StateFlow directly (tests configure items explicitly; no seed behaviour to emulate).

Considered exposing `SEED_COSMETICS` statically (e.g. a top-level constant or companion-object member) instead of going through the DAO round-trip, but that:
- breaks the domain/data boundary (use case would import data-layer contents).
- bypasses any runtime-added cosmetics (if future content PRs source cosmetics from a server or a pack, the static view is wrong).

Going through the repo keeps the contract clean: "the catalogue is whatever the repo says it is, at the moment of the check." The `ensureSeedData` idempotency makes this cheap on steady-state.

**First-unknown-wins semantics.** If a milestone has multiple Cosmetic rewards (none currently do, but the data model allows it), only the first unknown id is reported. Exhaustive reporting would require a `List<String>` on the result variant and complicate the common case. Since the roadmap's resolution plan is "one content PR per cosmetic," reporting the first unknown and iterating is enough — after the first seed lands, the same claim returns `UnknownCosmetic(next_id)` instead of the first one.

**MissionsViewModel: pattern-match + surface via snackbar.** The consumer now pattern-matches the result:
```kotlin
when (val result = claimMilestoneUseCase.invoke(milestone)) {
    ClaimMilestoneResult.Success -> Unit
    ClaimMilestoneResult.InsufficientSteps -> userMessage.value = "You haven't walked enough steps yet."
    ClaimMilestoneResult.AlreadyClaimed -> userMessage.value = "Milestone already claimed."
    is ClaimMilestoneResult.UnknownCosmetic -> userMessage.value = "Reward temporarily unavailable (cosmetic \"${result.cosmeticId}\" is being finalised). Try again after the next update."
}
```
The `userMessage: StateFlow<String?>` is nullable — non-null triggers the snackbar on the next render. `clearMessage()` resets after the snackbar dismisses. Matches the existing Store/Workshop/Cards/Labs pattern established in R10/R2-09.

`MissionsScreen` previously had no `Scaffold` wrapper; wrapped the `LazyColumn` in `Scaffold(snackbarHost = { SnackbarHost(\u2026) })` and added a `LaunchedEffect(state.userMessage)` that shows + clears. First time the Missions screen has user-feedback plumbing. `@OptIn(ExperimentalMaterial3Api::class)` was already present on the composable (left unchanged).

### Files touched

- `app/src/main/java/.../domain/repository/CosmeticRepository.kt` — +`suspend fun idExists(cosmeticId: String): Boolean` with KDoc explaining C.4 rationale and flagging resolution as C.2 PR 3+ content work.
- `app/src/main/java/.../data/repository/CosmeticRepositoryImpl.kt` — +`override suspend fun idExists(...)` that lazy-seeds via `ensureSeedData()` then queries `observeAll().first().any { it.cosmeticId == cosmeticId }`. Inline comment notes the amortised cost (one table-count query on steady-state).
- `app/src/main/java/.../domain/usecase/ClaimMilestone.kt` — rewrite: +`ClaimMilestoneResult` sealed class (4 variants) in same file; constructor grew from 3 to 4 params (+`CosmeticRepository`); body now does step-threshold check \u2192 pre-flight cosmetic-id check \u2192 atomic DAO call \u2192 Success|AlreadyClaimed. KDoc expanded to document C.4 detection-only contract. `MilestoneReward.Cosmetic` import added for the pre-flight check.
- `app/src/main/java/.../presentation/missions/MissionsUiState.kt` — +`userMessage: String? = null` field with KDoc.
- `app/src/main/java/.../presentation/missions/MissionsViewModel.kt` — +`CosmeticRepository` injection (6 \u2192 7 constructor params); +`userMessage: MutableStateFlow<String?>` + `clearMessage()`; `claimMilestone(milestone)` now pattern-matches `ClaimMilestoneResult`; `combine()` grew from 4 to 5 flows (+userMessage).
- `app/src/main/java/.../presentation/missions/MissionsScreen.kt` — wrapped `LazyColumn` in `Scaffold(snackbarHost = { SnackbarHost(\u2026) })`; added `LaunchedEffect(state.userMessage)` that shows + clears. New imports: `LaunchedEffect`, `remember`.
- `app/src/test/java/.../fakes/FakeCosmeticRepository.kt` — +`override suspend fun idExists` that checks `items.value.any { it.cosmeticId == cosmeticId }`.
- `app/src/test/java/.../domain/usecase/ClaimMilestoneTest.kt` — rewrite: 8 cases \u2192 12 cases (-1 merged into positive-path + 5 new C.4 cases). Constructor updated to 4-arg; sut now takes a `FakeCosmeticRepository` (empty by default to match prod-today state where the 3 ids don't exist). Test-name renames for Result-type clarity.
- `app/src/test/java/.../presentation/missions/MissionsViewModelTest.kt` — direct `ClaimMilestone` construction updated to 4-arg (+`FakeCosmeticRepository()`). Asserts `ClaimMilestoneResult.Success` instead of the old Boolean.

### Tests added (5 new cases in `ClaimMilestoneTest`, 1 existing case merged)

New C.4 cases:
1. **`UnknownCosmetic surfaces offending cosmetic id for MARATHON_WALKER`** \u2014 empty cosmetic catalogue; `useCase(MARATHON_WALKER)` returns `UnknownCosmetic("garden_ziggurat_skin")`. Asserts the exact id so renaming the reward in future content work surfaces as a test failure.
2. **`UnknownCosmetic surfaces offending cosmetic id for IRON_SOLES`** \u2014 same, for `"lapis_lazuli_skin"`.
3. **`UnknownCosmetic surfaces offending cosmetic id for GLOBE_TROTTER`** \u2014 same, for `"sandals_of_gilgamesh"`.
4. **`UnknownCosmetic rejects claim before the atomic DAO call with no credit`** \u2014 regression guard on pre-flight ordering: uses IRON_SOLES, asserts zero wallet movement + `claimMilestoneAtomicCallCount == 0` + no milestone row written. Proves the check runs BEFORE the atomic, not as a post-atomic cleanup.
5. **`milestone with matching cosmetic id credits rewards via atomic path`** \u2014 positive path emulating post-C.2-PR-3 state: seeds `cosmeticRepo.items` with a `lapis_lazuli_skin` CosmeticItem, then `useCase(IRON_SOLES)` succeeds atomically (200 Gems + 50 PS credited; atomic call count 1). Shows the check is selective: when the id is present, the rest of the flow runs normally. This test is the forward-looking compass for when C.2 PR 3 eventually seeds `lapis_lazuli_skin` \u2014 the test flips from "fixture-based" to "real seed row" without code changes.

Existing test merged away: `credits Gems and Power Stones for IRON_SOLES` was a Boolean-success case against IRON_SOLES, which now returns `UnknownCosmetic` by default. Coverage preserved by (a) the new UnknownCosmetic IRON_SOLES test (confirms the rejection shape) + (b) the new positive-path test (confirms the credit runs when the id resolves). Net: 8 \u2192 12 cases (+4).

Existing test adjusted in place:
- `two concurrent claims on the same milestone - only one credits` \u2014 target switched from IRON_SOLES (unknown cosmetic) to MORNING_JOGGER (Gems-only, no Cosmetic reward). The atomicity invariant being tested is independent of the cosmetic-id pre-flight check, so the target change keeps the coverage focused. Assertions updated to `ClaimMilestoneResult.Success` / `ClaimMilestoneResult.AlreadyClaimed` counts.
- `credits Gems correctly` \u2192 `credits Gems correctly on Success`, `marks milestone as claimed` \u2192 `marks milestone as claimed on Success`, `claiming twice is no-op` \u2192 `claiming twice returns AlreadyClaimed on second call`, `claiming milestone without reaching step threshold returns false` \u2192 `\u2026 returns InsufficientSteps`, `already-claimed entity pre-existing in DAO causes invoke to short-circuit` \u2192 `\u2026 causes invoke to return AlreadyClaimed`. Rename for Result-type clarity.

### Mid-edit bugs caught

**Em-dash in backtick test name.** Initially wrote `UnknownCosmetic rejects claim before the atomic DAO call \u2014 no credit` (em-dash separator). Kotlin compiler rejected with "Name contains illegal characters: ." \u2014 the em-dash character trips the Kotlin 2.3 backtick-identifier validator. Replaced with "with no credit" prose. Lesson: keep test names to ASCII.

### Verification

- `./run-gradle.sh test` \u2014 first run: compile error on em-dash test name (above). After fix, **BUILD SUCCESSFUL in 14s**, 36 actionable tasks. 0 failures, 0 errors, 0 skipped.
- Test count: **480 \u2192 484 JVM tests** (+4 net). Breakdown: -1 case merged (old IRON_SOLES success-path) + 5 new cases = +4. Matches expectations exactly.
- Grep sanity checks:
  - `grep -rn "ClaimMilestoneResult" app/src` \u2014 6 hits across the file pair + 3 test files.
  - `grep -rn "idExists" app/src` \u2014 7 hits (interface + 2 impls + use case + 3 tests).
  - `grep -rn "cosmeticId silently" app/src` \u2014 0 hits (no stale comments referring to the pre-C.4 silent-drop behaviour).
- Behaviour preservation: milestones without Cosmetic rewards (FIRST_STEPS, MORNING_JOGGER, TRAIL_BLAZER) claim identically to pre-C.4 \u2014 verified by the 7 preserved test cases.

### Surface changes

- `CosmeticRepository` gained one `suspend fun`. All implementations (real + fake) updated.
- `ClaimMilestone` constructor grew from 3 to 4 params; return type changed from `Boolean` to `ClaimMilestoneResult`. 3 call sites touched (MissionsViewModel, ClaimMilestoneTest sut, MissionsViewModelTest direct construction) \u2014 all updated in this PR.
- `MissionsViewModel` constructor grew from 6 to 7 params. Hilt graph picks up `CosmeticRepository` via the existing `@Binds` in `RepositoryModule`.
- `MissionsScreen` wrapped in `Scaffold` \u2014 backwards-compatible (no Hilt / nav changes). First user-feedback surface on the Missions screen.
- No new production dependencies. No ADR \u2014 C.4 roadmap section fully covers the decision with alternatives and non-goals.

### Open questions / blockers

- **3 milestones currently un-claimable by design.** MARATHON_WALKER / IRON_SOLES / GLOBE_TROTTER each carry an unknown-cosmetic reward; until C.2 PR 3+ seeds the matching rows, the claim snackbar is the only outcome. This is a known trade-off, documented in CHANGELOG, STATE.md, and the use case's KDoc.
- **Known seed-data debt** \u2014 `ensureSeedData` still short-circuits on `dao.count() > 0`. If a dev installs the app before C.2 PR 2 (no `zig_jade`) and before C.2 PR 3+ (no milestone cosmetics), subsequent upgrades won't seed the new rows. Fix is still queued as #1 in next-actions; must precede any C.2 PR 3+ content PR.

### Follow-ups

- **Immediate next (priority #1):** fix `ensureSeedData` count-gate. One-line change (per-`cosmeticId` filter) in `CosmeticRepositoryImpl.kt`. Small, additive, low risk.
- **C.2 PR 3:** ship the first milestone cosmetic seed row. Proposed: `lapis_lazuli_skin` because the positive-path test in C.4 already uses it as a fixture \u2014 replacing the fixture with real data is the smallest diff. The C.4 UnknownCosmetic IRON_SOLES test will break when this lands (the id will be known), and the replacement assertion \u2014 that IRON_SOLES now claims successfully \u2014 will be trivial.
- **C.5 + C.6:** Billing + Ad SDK swaps, each gated on ADR-0005 / ADR-0006. Independent of C.2 PR 3+ cadence.

### Memory updated

- `STATE.md` \u2705 \u2014 current objective now "Phase C.4 landed"; C.4 added to "what works"; test count 480 \u2192 484; priorities/next-actions reshuffled (ensureSeedData fix #1, C.2 PR 3 #2, C.5/C.6 #3); C.4 removed from debt list; critical path marks PRs 1+2 + C.4 done; last-run date updated.
- `RUN_LOG.md` \u2705 \u2014 this entry.
- ADR: not warranted \u2014 C.4 roadmap section fully covers the decision. The pre-flight-vs-post-atomic design choice is documented in the use case's KDoc (Option A vs Option B rationale) and in this entry, discoverable at the point of use.

## 2026-05-08 — Phase C.2 PR 2 (RO-07): seed zig_jade as first end-to-end cosmetic

- **Goal:** Land the first content slice of the C.2 cosmetic pipeline per `devdocs/evolution/implementation_roadmap.md` §C.2 PR 2. PR 1 shipped the renderer plumbing (dormant — `ZIGGURAT_COLOR_LOOKUP` empty). PR 2 seeds the first cosmetic (`zig_jade` — jade ziggurat recolour per gap_analysis §5.2), populates its palette, and lifts the R2-11 "Coming Soon" guard for that single ID so it's purchasable in the Store. Closes the "shipped but disabled" monetization gap for one end-to-end slice, unblocks the remaining 6 seeded + 3 milestone cosmetics as pure content work for PR 3+.
- **Preflight:** read `START_HERE`, `STATE`, `CONSTRAINTS`, `RUN_LOG` head (doc-sweep + C.2 PR 1 + B.3 PR 2 + B.2 PRs 4-5 entries). `git status` clean on `main`, up to date with origin (last commit `d50cf9f docs(agent): mandate current-state doc sync before STATE/RUN_LOG in every PR task list`). Read `CosmeticRepositoryImpl` (SEED_COSMETICS + ZIGGURAT_COLOR_LOOKUP + toDomain), `CosmeticItem`, `StoreScreen`, `StoreViewModel` (purchaseCosmetic path), `CosmeticDao`, `CosmeticEntity`, `ZigguratEntity.DEFAULT_COLORS` (5 ints → content contract matches). Checked existing PR 1 `BattleViewModelTest` cosmetic fixtures for palette conventions — synthetic fixture uses `"ZIG_JADE"` uppercase, but existing seed rows (`zig_obsidian`, etc.) are snake_case lowercase. Chose `zig_jade` for consistency. Grep-confirmed no `CosmeticRepositoryImplTest` or `FakeCosmeticDao` existed.

### Design

**Cosmetic ID choice: `zig_jade` (lowercase).** The roadmap and gap_analysis §5.2 both write `ZIG_JADE` in prose, but existing `SEED_COSMETICS` rows (`zig_obsidian`, `zig_crystal`, `zig_golden`, `proj_fire`, etc.) all use snake_case. Treating the doc's uppercase as formatting emphasis not literal; lowercase matches the established ID convention. The PR 1 synthetic VM test fixture `"ZIG_JADE"` is a test-only string that injects through a fake repo — it doesn't collide with the real seed row.

**Palette choice.** Reused the exact 5-color jade gradient from the PR 1 test fixture: `[0xFF104E3C, 0xFF1A6B52, 0xFF2A8F6E, 0xFF3CAB82, 0xFF54C79A]` (bottom layer → top highlight, deep jade to pale highlight). Tests lock in the exact values so any accidental palette mutation surfaces as a test failure (content-as-code contract). Matches the `ZigguratEntity.DEFAULT_COLORS` cardinality contract (exactly 5 Ints, one per layer).

**StoreScreen allow-list idiom.** Introduced a file-level `private const val ENABLED_COSMETIC_ID = "zig_jade"` and gated the enable-branch on `cosmetic.cosmeticId == ENABLED_COSMETIC_ID`. Three alternatives rejected:
1. Remove the guard entirely for all owned-but-not-equipped — wrong, purchase is still guarded by affordability only.
2. Carry the allow-list in `CosmeticCategory` or as a list — premature abstraction; PR 3+ adds one ID at a time.
3. Read `CosmeticItem.overrideColors != null` as the enable signal — couples the UI to the renderer contract, breaks the moment a category adds non-color overrides.

The file-level const is the smallest step that scales monotonically with PR 3+ (add one ID to a list when the next palette lands).

**Price point: 150 💎.** Between `zig_obsidian` (100) and `zig_crystal` (200). Matches the roadmap's implicit mid-tier positioning for the first cosmetic (150 is also the `proj_fire` / `proj_lightning` price — no collision, jade is a new category).

**Disclaimer line update.** Was "Cosmetic visuals are being finalized. Purchases are disabled until ready." — now "Most cosmetic visuals are still being finalized. Jade Ziggurat is available now." Accurate signal to the player; doesn't overpromise.

**Known debt explicitly not fixed in this PR.** `ensureSeedData` short-circuits when `dao.count() > 0`, so `zig_jade` only lands on fresh installs — existing dev installs need a data clear. Considered fixing in the same PR (one-line per-cosmeticId filter) but held scope tight per STATE.md's narrow phrasing ("seed ZIG_JADE + remove guard"). Flagged in the Known-issues section of STATE.md + CHANGELOG.md so it surfaces as explicit follow-up work before any further content PR. Low risk: pre-release app has no shipped installs; devs can clear data.

### Files touched

- `app/src/main/java/.../data/repository/CosmeticRepositoryImpl.kt` — `ZIGGURAT_COLOR_LOOKUP` gained the first entry (`"zig_jade" to [5-color jade palette]`); KDoc expanded to document the content-as-code contract and point at PR 3+ as the extension vehicle. `SEED_COSMETICS` gained a `zig_jade` row (ZIGGURAT_SKIN, 150 💎, placed first in the list so it surfaces at the top of the Store cosmetics section). Total seed count: 7 → 8.
- `app/src/main/java/.../presentation/store/StoreScreen.kt` — file-level `private const val ENABLED_COSMETIC_ID = "zig_jade"` + KDoc explaining the allow-list contract + which other files must co-update when expanding (`CosmeticRepositoryImpl.SEED_COSMETICS` + `ZIGGURAT_COLOR_LOOKUP`). Enable-branch added to the `when` at the unowned-path of the cosmetic card: shows `💎 {priceGems}` on an enabled Button wired to `viewModel.purchaseCosmetic(cosmetic.cosmeticId)`, `enabled = !state.isPurchasing` (the existing double-tap guard). All non-`zig_jade` unowned cosmetics fall through to the pre-existing "Coming Soon" disabled Button. Disclaimer text updated.
- `app/src/test/java/.../fakes/FakeCosmeticDao.kt` (new, 75 LOC) — in-memory `CosmeticDao` fake. Monotonic `nextId: Int` counter simulates Room's `@PrimaryKey(autoGenerate = true)`. Upsert resolves conflicts by `cosmeticId` (preserves id on update). KDoc explains the constraint: does NOT enforce cosmeticId uniqueness, caller's responsibility to drive via `ensureSeedData`.
- `app/src/test/java/.../data/repository/CosmeticRepositoryImplTest.kt` (new, 134 LOC, 5 cases) — new test directory `app/src/test/java/.../data/repository/` matching the main-sources layout.

### Tests added (5 new cases in new `CosmeticRepositoryImplTest`)

1. **`C2PR2 - ensureSeedData inserts zig_jade as first end-to-end cosmetic`** — proves `ensureSeedData` on a fresh fake DAO creates the `zig_jade` row with the expected metadata (name = "Jade Ziggurat", category = ZIGGURAT_SKIN, priceGems = 150, isOwned = false, isEquipped = false).
2. **`C2PR2 - zig_jade propagates jade palette via overrideColors from ZIGGURAT_COLOR_LOOKUP`** — proves the lookup table → `toDomain` chain: observed `zig_jade` has `overrideColors.size == 5` and matches the exact palette. Content-as-code contract; any accidental palette mutation fails this test.
3. **`C2PR2 - other seeded ziggurat cosmetics have null overrideColors pending content PRs`** — regression guard: all 7 non-`zig_jade` seeds (`zig_obsidian`, `zig_crystal`, `zig_golden`, `proj_fire`, `proj_lightning`, `enemy_shadow`, `enemy_neon`) return `null` overrideColors. Proves the lookup is selective, not blanket.
4. **`C2PR2 - equipped zig_jade surfaces via observeEquipped with overrideColors intact`** — repo-layer mirror of the PR 1 VM→engine test. Exercises the full equip path: `purchase("zig_jade")` → `equip("zig_jade")` → `observeEquipped().first()` returns jade with `isOwned = true`, `isEquipped = true`, `overrideColors.size == 5`. Together with PR 1's VM test, proves the end-to-end chain `CosmeticRepo → VM → engine.cosmeticOverrides → layer colors`.
5. **`C2PR2 - ensureSeedData is idempotent on repeat call (count gate holds)`** — documents the current all-or-nothing contract. First call seeds 8 rows; second call returns early via `dao.count() > 0` gate. Locks in the known-debt behaviour so future content PRs that change `ensureSeedData` semantics surface as a test failure rather than silent double-seed.

### Mid-edit bugs caught

None. The fake construction, StoreScreen edit, and test suite landed on first try. The palette sync with the PR 1 test fixture was deliberate (reused the exact values to avoid a mismatch between the synthetic VM fixture and the real seed row).

### Verification

- `./run-gradle.sh test` — BUILD SUCCESSFUL in 20s, 36 actionable tasks. Test count: **475 → 480 JVM tests** (+5, matches exactly). 0 failures, 0 errors, 0 skipped.
- Lint: clean (pre-existing warnings unchanged).
- Grep sanity checks:
  - `grep "zig_jade" app/src/main` — 3 hits (SEED_COSMETICS row + ZIGGURAT_COLOR_LOOKUP entry + StoreScreen ENABLED_COSMETIC_ID).
  - `grep "Coming Soon" app/src/main` — 1 hit (the disabled Button text for all non-`zig_jade` unowned cosmetics; intentional).
  - `grep "ENABLED_COSMETIC_ID" app/src/main` — 2 hits (declaration + one usage in the when-branch).
- Behaviour preservation: the empty-`ZIGGURAT_COLOR_LOOKUP` default branch from PR 1 still short-circuits correctly for any cosmeticId not in the map (verified by test #3).

### Surface changes

- New content in `CosmeticRepositoryImpl` (1 seed row + 1 lookup entry + expanded KDoc). Additive — no other files need updating.
- `StoreScreen` gained a file-level const + one additional branch in the `when` expression. No new imports. No API changes.
- New test fake (`FakeCosmeticDao`) + new test file (`CosmeticRepositoryImplTest`). No existing tests modified.
- No new production dependencies. No ADR — C.2 roadmap section fully covers this PR with alternatives/non-goals/rollback; ADR would duplicate content.

### Open questions / blockers

- **Known debt:** `ensureSeedData` count-gate prevents `zig_jade` (and all future content PR seed rows) from landing on existing dev installs. Fix is a one-line change (`val missing = SEED_COSMETICS.filter { it.cosmeticId !in existingIds }`) but I held it out of this PR to keep scope tight. Should land BEFORE C.2 PR 3 so content PRs don't each have to ship a data-clear workaround.
- **Product decision for PR 3+:** which cosmetic is second? Roadmap / gap_analysis don't prescribe order. Proposed default: pick any one of the remaining 6 seeded ziggurat/projectile/enemy rows (my vote: `zig_obsidian` at 100 💎 — cheaper entry point, signals "affordable starter").

### Follow-ups

- **Immediate next PR (C.4):** `ClaimMilestone.Cosmetic` detection fix. Small, independent, surfaces the 3 mismatched milestone IDs as `Result.UnknownCosmetic` instead of silent drop. Promoted from #2 to #1 in STATE.md next-actions.
- **Before C.2 PR 3:** fix `ensureSeedData` count-gate. Same file, one-line change, additive semantics (existing installs just gain the new row; no data loss).
- **C.5 + C.6:** real Billing + Ad SDK swaps, each gated on ADR-0005 / ADR-0006 stubs. Independent of Phase C.2 progress.
- **B.4 / B.5:** FollowOnPipeline + UpdateMissionProgress debt cleanup. Not blockers.

### Memory updated

- `STATE.md` ✅ — current objective now "Phase C.2 PR 2 landed"; C.2 PR 2 added to "what works"; known-debt line updated (`ensureSeedData` count-gate called out explicitly); priorities/next-actions reshuffled (C.4 top, seed-migration fix second, C.5/C.6 third); test count 475 → 480; critical-path updated to mark C.2 PRs 1+2 complete; last-run date 2026-05-08.
- `RUN_LOG.md` ✅ — this entry.
- ADR: not warranted — C.2 roadmap section fully covers the PR. The lowercase `zig_jade` vs roadmap's `ZIG_JADE` is a naming consistency choice, not an architectural decision.

## 2026-05-08 — Doc sweep: current-state sync after B.2 PRs 4-5 + B.3 PR 2 + C.2 PR 1

- **Goal:** Close accumulated current-state doc drift. Last A.1-style sweep (2026-05-06) synced through Phase A; since then B.2 PRs 4-5, B.3 PR 2, and C.2 PR 1 have landed — 4 current-state docs were stale. Preflight grep confirmed 4 targets needed updates: `AGENTS.md`, `CHANGELOG.md`, `.kiro/steering/source-files.md`, `.kiro/steering/structure.md`. Historical artifacts (RUN_LOG, plan-R*, external-reviews, devdocs/archaeology, devdocs/evolution) intentionally left untouched per the A.1 precedent.
- **Preflight:** read `START_HERE`, `STATE`, `CONSTRAINTS`, `RUN_LOG` head (C.2 PR 1 + B.3 PR 2 + B.2 PRs 4-5 entries). `git status` clean on `main`, up to date with origin (last commit `ff5c414 feat: cosmetic renderer override pipeline (C.2 PR 1)`). Grep-enumerated stale references: `465 JVM tests` (2 hits in AGENTS/CHANGELOG), missing `CoroutineScopeModule` / `cosmeticOverrides` / `claimMilestoneAtomic` / `hasWaveProgress` / `ZIGGURAT_COLOR_LOOKUP` entries across the 4 targets.

### Changes

- **`AGENTS.md`** (1 line): test count 465 → 475; coverage list extended to note RO-02 `5/5 sites landed` (added `ClaimMilestone.claimMilestoneAtomic`, `BattleViewModel.runEndRoundPersistence` tx wrap), RO-03 `2/2 sites landed` (added `onCleared` guard), RO-07 `PR 1` cosmetic pipeline plumbing.
- **`CHANGELOG.md`** (4 new sections + updated Current state): added "Phase B.2 PR 4 — Atomic @Transaction for ClaimMilestone", "Phase B.2 PR 5 — Room @Transaction around runEndRoundPersistence (FINAL RO-02 site)", "Phase B.3 PR 2 — onCleared guard preserves mid-nav round progress (FINAL RO-03 site)", "Phase C.2 PR 1 — Cosmetic renderer override pipeline". Current state test progression 465 → 475, RO-02 complete (5/5), RO-03 complete (2/2), RO-07 in flight, noted B.4/B.5/C.4 as remaining debt.
- **`.kiro/steering/source-files.md`**: added `di/CoroutineScopeModule.kt` entry with KDoc-style one-liner; updated 6 existing entries — `MilestoneDao` (+`@Transaction claimMilestoneAtomic`), `ClaimMilestone` (+atomic delegation note), `CosmeticItem` (+`overrideColors`), `CosmeticRepositoryImpl` (+`ZIGGURAT_COLOR_LOOKUP`), `BattleViewModel` (composite summary: 14-param constructor; RO-02 tx wrap + RO-03 resilience + onCleared guard + C.2 cosmetic hydration), `GameEngine` (+`hasWaveProgress` + `cosmeticOverrides`).
- **`.kiro/steering/structure.md`**: added `CoroutineScopeModule` to the `di/` module list (line 39) and a new row in the Key Files table directly below `di/TimeModule.kt`.

### Verification

- `./run-gradle.sh :app:testDebugUnitTest` — BUILD SUCCESSFUL in 1s, all 36 actionable tasks UP-TO-DATE (no code changed — nothing to recompile, test task cached). Test suite stays at **475 JVM tests**, all green.
- Post-sweep grep `'465 JVM tests'` — 0 matches in non-historical docs. Post-sweep grep `'CoroutineScopeModule'` — now referenced from source-files.md + structure.md + CHANGELOG.md as expected.
- No code or test changes; the RO-02/RO-03/RO-07 behaviour locked in earlier is unaffected.

### Files touched

- `AGENTS.md` (1-line test count + coverage update)
- `CHANGELOG.md` (+4 PR sections; updated Current state)
- `.kiro/steering/source-files.md` (+1 entry; 6 updated entries)
- `.kiro/steering/structure.md` (+2 mentions of `CoroutineScopeModule`)
- `docs/agent/STATE.md` (objective line + last-run line)
- `docs/agent/RUN_LOG.md` (this entry)

### Intentionally NOT touched

- `docs/agent/RUN_LOG.md` (historical per-session entries below this one)
- `docs/plans/plan-R*.md`, `docs/plans/plan-R2*.md` (historical)
- `docs/external-reviews/*` (historical at review date)
- `devdocs/archaeology/*`, `devdocs/evolution/*`, `smoke_tests/*` (historical per their HEAD pin)

### Open questions / blockers

- **None.** Doc drift closed. Next substantive work: C.2 PR 2 (seed `ZIG_JADE` + remove R2-11 guard for that single ID), then C.4 (ClaimMilestone.Cosmetic detection fix), then C.5/C.6 (real SDK swaps gated on ADR stubs).

### Memory updated

- `STATE.md` ✅ — current objective now "Doc sweep landed"; last-run date reflects doc-sweep.
- `RUN_LOG.md` ✅ — this entry.
- ADR: not warranted — doc-only sweep, no architectural decisions.

## 2026-05-08 — Phase C.2 PR 1 (RO-07): cosmetic renderer override pipeline (plumbing only)

- **Goal:** Land PR 1 of the cosmetic rendering pipeline per `devdocs/evolution/refactoring_opportunities.md` §RO-07 and `implementation_roadmap.md` §C.2. The cosmetic system has three disconnected parts: **data** (`CosmeticEntity` / `CosmeticDao` / 7 seeded rows), **UI** (`StoreScreen` + `StoreViewModel` with the R2-11 "Coming Soon" guard disabling purchases), and **renderer** (`GameEngine` / `ZigguratEntity` with zero cosmetic awareness). This PR closes the *renderer* gap with pure-additive plumbing so PR 2 can seed one cosmetic (`ZIG_JADE`) end-to-end and remove the R2-11 guard for it.
- **Preflight:** read `START_HERE`, `STATE`, `CONSTRAINTS`, `RUN_LOG` head (B.2/B.3 entries), `devdocs/evolution/refactoring_opportunities.md` §RO-07, `implementation_roadmap.md` §C.2. `git status` clean on `main`, up to date with origin (last commit `c083cb8 refactor: onCleared guard preserves mid-nav round progress (B.3 PR 2)`). Read `CosmeticItem`, `CosmeticEntity`, `CosmeticRepositoryImpl` (for SEED_COSMETICS and toDomain), `CosmeticCategory` enum, `CosmeticDao`, `domain/repository/CosmeticRepository` interface, `GameEngine.init` (ZigguratEntity construction site at line 142), `ZigguratEntity` (`layerColors: List<Int>` already a constructor param with `DEFAULT_COLORS` fallback), `BattleScreen` / `GameSurfaceView` / `BattleViewModel` for the engine lifecycle, `FakeCosmeticRepository`. Grep-confirmed `lifecycle-process` still not on classpath (N/A for C.2).

### Design

**Field placement on `CosmeticItem`.** The spec quote "BattleViewModel selects override.colors if ZIGGURAT category is present" suggests colors live on the cosmetic. Added `overrideColors: List<Int>? = null` as a nullable data-class field. `List<Int>` is pure Kotlin — no Android imports leak into `domain/`. Nullable default keeps every existing CosmeticItem construction site (tests + internal creations) source-compatible.

**Color lookup table.** `CosmeticRepositoryImpl.companion object` gained `private val ZIGGURAT_COLOR_LOOKUP: Map<String, List<Int>> = emptyMap()`, consulted in `toDomain()` as `overrideColors = ZIGGURAT_COLOR_LOOKUP[cosmeticId]`. Empty in PR 1 — the first entry (`ZIG_JADE`) ships in PR 2. **No DB schema change.** Colors are content, stored in code; changing a palette is a one-line patch not a migration. KDoc calls out the contract: each entry MUST be exactly 5 Ints to match `ZigguratEntity.DEFAULT_COLORS`.

**GameEngine contract.** New `@Volatile var cosmeticOverrides: Map<CosmeticCategory, CosmeticItem> = emptyMap()` public property on `GameEngine`. Default `emptyMap()` preserves today's rendering exactly. In `init()`, replaced the direct `layerColors = biomeTheme.zigguratColors` with:
```kotlin
val zigColors = cosmeticOverrides[CosmeticCategory.ZIGGURAT_SKIN]?.overrideColors
    ?: biomeTheme.zigguratColors
```
Null-coalescing fallback — a player with no ziggurat cosmetic equipped, or an equipped cosmetic whose ID isn't in `ZIGGURAT_COLOR_LOOKUP`, gets the biome default. `@Volatile` is defensive — reads happen on the game-loop thread, writes happen on the UI/VM thread via Hilt-scoped coroutines.

**BattleViewModel hydration.** `CosmeticRepository` added as a constructor dep (13 → 14 params). In the init-launch, after loading cards:
```kotlin
equippedCosmetics = cosmeticRepository.observeEquipped().first().associateBy { it.category }
engine?.cosmeticOverrides = equippedCosmetics
```
**Two push sites, intentional.** The engine can be attached either *before* the init-launch completes (normal case — `startPollingEngine` fires from `BattleScreen`'s first composition, VM init launches a coroutine that completes later) or *after* (rare race). The init-launch pushes *if engine already attached*, and `startPollingEngine` also pushes `engine.cosmeticOverrides = equippedCosmetics` in case the init launch finishes first. Whichever fires last wins; both are idempotent writes to the same `@Volatile` field. The subsequent `engine.init()` (triggered by `surfaceView.configure()` when `isLoading=false`) reads the up-to-date map.

**Non-goals in PR 1.** Per RO-07 non-goals: (a) no animated cosmetics; (b) only `ZIGGURAT_SKIN` category plumbed — `PROJECTILE_EFFECT` and `ENEMY_SKIN` follow in PR 3+ when content ships; (c) no R2-11 guard removal (that's PR 2, gated on content); (d) no `ClaimMilestone.Cosmetic` detection fix (that's C.4, independent).

### Files touched

- `app/src/main/java/.../domain/model/CosmeticItem.kt` (+`overrideColors: List<Int>? = null` field; class KDoc explaining the lookup-table relationship)
- `app/src/main/java/.../data/repository/CosmeticRepositoryImpl.kt` (+`ZIGGURAT_COLOR_LOOKUP` empty map + KDoc; `toDomain` reads from it)
- `app/src/main/java/.../presentation/battle/engine/GameEngine.kt` (+imports: `CosmeticCategory`, `CosmeticItem`; +`@Volatile var cosmeticOverrides` public property + KDoc; `init()` swaps `biomeTheme.zigguratColors` for null-coalesced lookup)
- `app/src/main/java/.../presentation/battle/BattleViewModel.kt` (+imports: `CosmeticCategory`, `CosmeticItem`, `CosmeticRepository`; +`cosmeticRepository` constructor param (13 → 14); +`private var equippedCosmetics: Map<CosmeticCategory, CosmeticItem>` field; init-launch loads + pushes to `engine?.cosmeticOverrides`; `startPollingEngine` also pushes as defence against the load-vs-attach race)
- `app/src/test/java/.../presentation/battle/BattleViewModelTest.kt` (+`cosmeticRepo` fixture; threaded through createVm + both RO-03 direct constructions; +2 new C.2 PR 1 tests)

### Tests added (2 new cases in `BattleViewModelTest`, bringing it to 26 total)

1. **`C2PR1 - no equipped cosmetics keeps engine cosmeticOverrides empty`** — regression guard. Empty `cosmeticRepo.items.value`, construct VM, install engine via `installEngineForEndRound` BEFORE `advanceUntilIdle` (so the VM's init-launch `engine?.cosmeticOverrides = equippedCosmetics` push lands on the test engine), advance. Assert `engine.cosmeticOverrides.isEmpty()`. Proves: players without equipped cosmetics see no change in engine state — the null-coalescing fallback in `engine.init()` returns the biome default.
2. **`C2PR1 - equipped ziggurat cosmetic propagates to engine cosmeticOverrides`** — happy path. Seed an in-memory `CosmeticItem("ZIG_JADE", ZIGGURAT_SKIN, ..., overrideColors = jadeColors)` with `isEquipped = true`, install engine before advance, assert `engine.cosmeticOverrides[ZIGGURAT_SKIN]` matches the equipped item including `overrideColors`. Proves the end-to-end pipeline: `CosmeticRepository → VM → engine.cosmeticOverrides` is wired.

### Mid-edit bugs caught

1. **First build hung on test execution.** Gradle Test Executor at 100% CPU for several minutes; compilation and lint had already succeeded. Root cause: my initial tests called `vm.startPollingEngine(engine, mock())` to trigger the cosmetic push, but `startPollingEngine` launches an infinite `while(true) { delay(200); ziggurat ?: continue }` polling loop inside `viewModelScope`. Since `engine.init()` was never called in the test, `eng.ziggurat` stayed null, the `continue` branch fired every tick, and `advanceUntilIdle()` never returned — the test dispatcher kept seeing scheduled delays. Detected by checking `ps aux | grep gradle` and `tail /tmp/gradle_out.txt`. Fix: removed the `startPollingEngine` calls and installed the engine via `installEngineForEndRound(vm)` *before* the first `advanceUntilIdle`, so the VM's init-launch push (`engine?.cosmeticOverrides = equippedCosmetics`) lands on the engine when it runs. No polling loop, no hang. Stuck Gradle processes (test executor 24346, wrapper 24260, shell 24257) killed with `kill -9` before retry.
2. **Import-line corruption from a two-edit batch.** My batch `edit_file` call inserted tests at the end *and* tried to normalise the imports block simultaneously; the imports got concatenated onto a single line (`fakes.*import com.whitefang...MilestoneNotificationManager`). Caught by the immediate follow-up diff and fixed with a one-line split. KSP didn't notice because the edit happened between compile and test runs.

### Verification

- First build: hung at `Task :app:testDebugUnitTest` (see Mid-edit bugs above). Compilation + lint succeeded.
- Second build after fixing the hang and import corruption: `./run-gradle.sh :app:compileDebugKotlin :app:testDebugUnitTest :app:lintDebug` — BUILD SUCCESSFUL, zero warnings.
- Test suite: **473 → 475 JVM tests** (+2, matches the 2 new pipeline tests exactly), 0 failures, 0 errors, 0 skipped. `BattleViewModelTest`: 24 → 26 cases.
- Lint: clean.
- Behavior preservation: existing tests unchanged; the null-coalescing `cosmeticOverrides[ZIGGURAT_SKIN]?.overrideColors ?: biomeTheme.zigguratColors` guarantees identical rendering when no cosmetic is equipped (the regression-guard test locks this in).

### Surface changes

- `CosmeticItem` gained a nullable field; all existing construction sites (test fakes, internal) stay source-compatible via default. 20 file hits, 0 required updates.
- `GameEngine` gained a public `cosmeticOverrides` property — additive, no existing code consumes it yet.
- `BattleViewModel` constructor grew 13 → 14 params. Hilt graph unaffected (`CosmeticRepository` already `@Binds`-ed in `RepositoryModule`). 3 test construction sites updated.
- No new production dependencies. No ADR — RO-07 spec fully covers this PR with alternatives/non-goals/rollback; ADR would duplicate content.

### Open questions / blockers

- **None for PR 1.** The pipeline is live and dormant — waiting for PR 2's `ZIG_JADE` seed row + palette + R2-11 guard removal.
- **Product decision for PR 2** is noted in the roadmap (gap_analysis §5.2 proposes jade ziggurat; any one-color-swap cosmetic works). Any other single-cosmetic choice just changes the ID string.

### Follow-ups

- **C.2 PR 2** is the next natural unit: +1 row in `SEED_COSMETICS`, +1 entry in `ZIGGURAT_COLOR_LOOKUP`, minus the R2-11 guard for that single ID in `StoreScreen` (existing logic at line 129). Zero changes needed in the pipeline itself.
- **C.4** (`ClaimMilestone.Cosmetic` detection fix) is independent and can land in parallel; surfaces the 3 mismatched milestone cosmetic IDs as `Result.UnknownCosmetic` instead of silent drop.
- **PR 3+** (remaining 6 seeded + 3 milestone cosmetics) is content work; each is +1 row / +1 palette / verify R2-11 removal. No pipeline changes.
- Doc drift: `AGENTS.md` still says "455 JVM tests" — now stale by seven PRs (+20 total). Continue bundling into the next A.1-style sweep; post-Phase-C is the natural checkpoint.
- `.kiro/steering/source-files.md` should add `di/CoroutineScopeModule.kt` (B.3 PR 2) on the next doc sweep; it's the only new file in `app/src/main/` since the last sweep that the index hasn't caught yet.

### Memory updated

- `STATE.md` ✅ — current objective now "C.2 PR 1 (plumbing only)"; C.2 PR 1 added to "what works"; priorities/next-actions reshuffled (C.2 PR 2 top, C.4 second); test count 473 → 475; critical-path line updated to mark C.2 PR 1 complete.
- `RUN_LOG.md` ✅ — this entry.
- ADR: not warranted — RO-07 spec + C.2 roadmap section already cover the PR with alternatives, risk, verification, rollback, non-goals. One-line deviation (color table location: code not DB) is documented in the `ZIGGURAT_COLOR_LOOKUP` KDoc, discoverable at the point of use.

## 2026-05-08 — Phase B.3 PR 2 (RO-03, FINAL): onCleared guard via @ApplicationScope CoroutineScope

- **Goal:** Land the final RO-03 unit per `devdocs/evolution/refactoring_opportunities.md` §RO-03. `BattleViewModel.onCleared` currently nulls the step-reward callback and calls `super.onCleared()` — which cancels `viewModelScope`. If a deep-link navigation teardown fires mid-round (e.g. a supply-drop notification replaces the Battle route), any in-flight round-persistence work is silently discarded. The spec calls for a scope that outlives VM cancellation; we fill in that gap.
- **Preflight:** read `START_HERE`, `STATE`, `CONSTRAINTS`, `RUN_LOG` head (B.2 PR 1–5 and B.3 PR 1 entries). `git status` clean on `main`, up to date with origin (last commit `a95ea00 refactor: Room @Transaction around runEndRoundPersistence (B.2 PR 5)`). Read `BattleViewModel`, `BattleViewModelTest` (21 cases), `StepCounterService` (existing `CoroutineScope(SupervisorJob() + Dispatchers.Default)` precedent), `StepsOfBabylonApp` (no app-level scope yet), `libs.versions.toml` (confirmed `lifecycle-process` NOT on classpath), existing Hilt modules for the @Qualifier + @Module precedent. Grep-confirmed zero current `ProcessLifecycleOwner` usage anywhere in the codebase.

### Design

**Scope-idiom choice: deviated from the RO-03 spec.** The spec's "First safe step" suggested `ProcessLifecycleOwner.lifecycleScope`, claiming `androidx.lifecycle:lifecycle-process` is "transitively available". That claim is *wrong* — the dep is not on the classpath, `libs.versions.toml` only declares `lifecycle-viewmodel-compose` and `lifecycle-runtime-compose`, and `grep -r lifecycle-process` returns zero matches. Rather than pull in a new dep, I used a Hilt-injected `@ApplicationScope` `CoroutineScope(SupervisorJob() + Dispatchers.Default)`. Reasons:
1. **No new dependency.** `kotlinx.coroutines` is already on the classpath; the scope is pure Kotlin.
2. **Right dispatcher.** `ProcessLifecycleOwner.lifecycleScope` defaults to `Dispatchers.Main` — wrong for DB writes. `Dispatchers.Default` matches the `StepCounterService` precedent in the same project.
3. **More testable.** The scope is DI-injected, so tests inject a `CoroutineScope(SupervisorJob() + dispatcher)` bound to `StandardTestDispatcher` — `advanceUntilIdle()` deterministically drains the launched work. A `ProcessLifecycleOwner.get()` singleton would need mocking.
4. **Matches project conventions.** Every other cross-cutting infrastructure piece (DB, Hilt Work, TimeProvider) is Hilt-injected. Adding a new `ProcessLifecycleOwner.get()` call site would introduce a second paradigm.

**`@ApplicationScope` qualifier.** New file `di/CoroutineScopeModule.kt` with a `@Qualifier` annotation and a `@Singleton @Provides` method. KDoc explains the scope semantics (lifetime = JVM process, SupervisorJob isolation, Default dispatcher) and the spec-deviation rationale. Follows the same shape as the existing `BillingModule`, `AdModule`, `TimeModule` precedent — adding a new cross-cutting Hilt provider is a well-trodden path.

**`GameEngine.hasWaveProgress()`.** The RO-03 spec's code snippet references `engine.hasWaveProgress()` as the guard that prevents persisting a zero-progress round (user opens Battle then immediately backs out). The method didn't exist; added as a pure-read boolean over two `@Volatile` fields (`elapsedTimeSeconds > 0f || totalEnemiesKilled > 0`). Thread-safe, no state mutation, no dispatcher concerns.

**`markEndedAndLaunchPersistence(scope, engine)` helper.** Rather than duplicate the "claim `roundEnded` guard + mark engine.roundOver + compute wave + launch persistence" sequence between the existing `endRound()` path and the new `onCleared` path, extracted a shared helper. Takes the scope as a parameter. `endRound()` passes `viewModelScope` (normal teardown), `onCleared` passes `applicationScope` (survives VM cancellation). Centralises future changes and guarantees both paths stay in sync.

**`onCleared` guard.** New override:
```kotlin
override fun onCleared() {
    val eng = engine
    if (eng != null && !roundEnded && eng.hasWaveProgress()) {
        markEndedAndLaunchPersistence(applicationScope, eng)
    }
    eng?.onStepReward = null
    super.onCleared()
}
```
Three-way guard: engine must exist, round must not have already ended (`roundEnded` guard in `markEndedAndLaunchPersistence` prevents double-persist), AND wave must have made observable progress (`hasWaveProgress` prevents bounce-through phantoms).

**Order of operations.** Check → launch — then null the callback — then super.onCleared(). The `applicationScope.launch` captures `eng` by value before `super.onCleared()` cancels `viewModelScope`, so the work is already queued on a scope that survives. The callback-nulling stays *after* the launch decision because a mid-launch kill of onStepReward is fine (the persistence coroutine owns its own engine ref).

**Annotation target.** `@ApplicationScope` on the constructor parameter produced a Kotlin KT-73255 forward-compat warning about future application to both parameter and property. Fixed with explicit `@param:ApplicationScope` to future-proof the annotation target. The `@param:` qualifier is the sanctioned migration path.

### Files touched

- `app/src/main/java/.../di/CoroutineScopeModule.kt` (new file: `@ApplicationScope` qualifier + `@Singleton @Provides fun provideApplicationScope()` returning `CoroutineScope(SupervisorJob() + Dispatchers.Default)`; KDoc explains scope semantics and the ProcessLifecycleOwner deviation rationale)
- `app/src/main/java/.../presentation/battle/engine/GameEngine.kt` (+`hasWaveProgress(): Boolean` with KDoc explaining the mid-nav persistence-guard semantics; reads existing `@Volatile` fields only)
- `app/src/main/java/.../presentation/battle/BattleViewModel.kt` (+imports: `ApplicationScope`, `CoroutineScope`; +constructor param `@param:ApplicationScope applicationScope: CoroutineScope` (12 → 13 params); +`markEndedAndLaunchPersistence(scope, eng)` helper; `endRound()` delegates to helper via `viewModelScope`; new `onCleared()` override checks guard trio and launches via `applicationScope` when applicable)
- `app/src/test/java/.../presentation/battle/BattleViewModelTest.kt` (+imports: `CoroutineScope`, `SupervisorJob`; +`applicationScope: CoroutineScope` fixture rebuilt in @BeforeEach as `CoroutineScope(SupervisorJob() + dispatcher)` so test-dispatcher advancement drains launches; threaded through `createVm` + both direct `BattleViewModel(...)` constructions in the RO-03 failure tests; +`invokeOnCleared(vm)` reflection helper; +`installEngineWithProgress(vm, elapsedSeconds, kills)` reflection helper that sets the `@Volatile` fields directly; +3 B.3 PR 2 tests)

### Tests added (3 new cases in `BattleViewModelTest`, bringing it to 24 total)

1. **`B3PR2 - onCleared mid-round launches persistence on the application scope`** — installs an engine with `elapsedSeconds=30f, kills=7`, calls `invokeOnCleared(vm)`, advances the test dispatcher. Asserts `playerRepo.profile.value.totalRoundsPlayed` advanced by 1 and `totalEnemiesKilled` = 7. Before B.3 PR 2 this would fail because `viewModelScope` is cancelled before `runEndRoundPersistence` runs.
2. **`B3PR2 - onCleared with no wave progress is a no-op`** — installs a fresh engine (no reflection into `@Volatile` fields, so `elapsedTimeSeconds=0f`, `totalEnemiesKilled=0`), calls `invokeOnCleared(vm)`. Asserts `totalRoundsPlayed` unchanged. Proves the `hasWaveProgress()` guard short-circuits the bounce-through case (user opens Battle then backs out before any wave ticks).
3. **`B3PR2 - onCleared after quitRound is a no-op (roundEnded guard holds)`** — installs engine with progress, calls `quitRound()` (persists once), then `invokeOnCleared(vm)`. Asserts `totalRoundsPlayed` stays at 1 after both calls. Proves the `roundEnded` guard holds across both teardown paths — prevents a normal quit-and-nav sequence from double-persisting.

### Verification

- First build: `./run-gradle.sh :app:compileDebugKotlin :app:testDebugUnitTest :app:lintDebug` — BUILD SUCCESSFUL with 2 Kotlin warnings about `@ApplicationScope` annotation target (KT-73255 forward-compat).
- Second build after adding `@param:ApplicationScope` explicit target — BUILD SUCCESSFUL, **zero warnings**.
- Test suite: **470 → 473 JVM tests** (+3, matches the 3 new atomicity cases exactly), 0 failures, 0 errors, 0 skipped. `BattleViewModelTest`: 21 → 24 cases.
- Lint: clean.
- Hilt KSP compiled the new qualifier + provider cleanly — `BattleViewModel`'s Hilt graph now includes the `@ApplicationScope CoroutineScope` binding without any manual wiring changes outside the new module.
- **RO-03 site count: 2/2 complete.** The R03 family (B.3 PR 1 resilient extraction + B.3 PR 2 mid-nav scope guard) is closed.

### Surface changes

- `BattleViewModel` constructor grew 12 → 13 params. Hilt graph picks up the new `@ApplicationScope CoroutineScope` via `CoroutineScopeModule`. 3 test construction sites (createVm + 2 RO-03 failure tests) updated.
- New public module `di/CoroutineScopeModule.kt` with `@ApplicationScope` qualifier; first use of this annotation in the codebase. Any future long-lived background work that should outlive a VM (e.g. fire-and-forget notification posting, background analytics) can reuse this qualifier.
- New public method `GameEngine.hasWaveProgress(): Boolean`. Small, pure read; safe surface addition.
- No changes to any existing public API. No new production dependencies. No ADR — RO-03 spec already covers the site with alternatives; the ProcessLifecycleOwner deviation is documented in the module's KDoc.

### Open questions / blockers

- **None. RO-03 is complete.** The mid-nav round-loss gap is closed at the VM level. Process-kill between `launch` and first write still loses the round (same as the spec's mitigation statement); RO-02 PR 5's transaction wrap closes the only remaining observability gap (partial-commit on crash).
- B.4 (`FollowOnPipeline` extraction) and B.5 (`UpdateMissionProgress` use case) remain as Phase B debt. Both are maintainability refactors, not blockers; can land any time before or during Phase C.

### Follow-ups

- **RO-03 milestone:** 2/2 sites complete. The resilience family (B.3 PR 1 extraction + PR 2 guard) is closed.
- **Phase C can proceed.** C.2 (cosmetic rendering pipeline) is the next release-critical path item. C.5 and C.6 (real Billing + Ad SDK swaps) are gated on their respective ADR stubs.
- **B.4/B.5** are pure debt; schedule opportunistically. Both share a common theme (removing forbidden-direction imports from presentation → data.local) and compose with each other.
- Doc drift: `AGENTS.md` still says "455 JVM tests" — now stale by six PRs (+3+3+2+3+2+3 = +16). Continue bundling into the next A.1-style sweep; a single doc-sync PR at the end of Phase B will handle it.
- `.kiro/steering/source-files.md` should add `di/CoroutineScopeModule.kt` on next doc sweep.

### Memory updated

- `STATE.md` ✅ — current objective now "RO-03 is COMPLETE"; B.3 PR 2 added to "what works"; known-issues/debt line updated (both RO-02 and RO-03 complete); priorities/next-actions reshuffled (Phase C.2 top); test count 470 → 473; critical-path line updated to mark B.3 complete; last-run date 2026-05-08.
- `RUN_LOG.md` ✅ — this entry.
- ADR: not warranted — RO-03 spec already covers this site with alternatives and rollback. The one genuine deviation (Hilt scope vs ProcessLifecycleOwner) is documented in the `CoroutineScopeModule` KDoc, which is discoverable at the point of use.

## 2026-05-08 — Phase B.2 PR 5 (RO-02 site #5, FINAL): AppDatabase.withTransaction for BattleViewModel.runEndRoundPersistence

- **Goal:** Land the final RO-02 site per `devdocs/evolution/refactoring_opportunities.md` §RO-02. `BattleViewModel.runEndRoundPersistence` (extracted in B.3 PR 1 specifically to enable this wrap) has 5 SQLite writes in the end-of-round fan-out: `updateBestWave`, `awardWaveMilestone`, `updateHighestUnlockedTier` (behind a profile-read), `incrementBattleStats`, `dailyMissionDao.updateProgress`. Without a transaction boundary, external readers (other ViewModels observing reactive Flows) can observe a partially-applied end-of-round state; e.g. `totalRoundsPlayed` advances but `bestWavePerTier` hasn't yet, or vice versa. Wrapping the writes in a single Room transaction closes this window.
- **Preflight:** read `START_HERE`, `STATE`, `CONSTRAINTS`, `RUN_LOG` head (B.2 PR 1–4, B.3 PR 1 entries). `git status` clean on `main`, up to date with origin (last commit `a9ebcde refactor: atomic @Transaction for ClaimMilestone (B.2 PR 4)`). Read `BattleViewModel`, `BattleViewModelTest` (19 cases including 3 RO-03 + 7 other A.7 / step-reward), `StepCrossValidator` (the B.2 PR 3 `withTransaction` precedent), `StepCrossValidatorTest` (seam test pattern). Grep-confirmed `BattleViewModel` has 3 construction sites (createVm + 2 RO-03 direct constructions); `runInTransaction =` appears only in StepCrossValidator / EscrowLifecycleTest tests (pattern-safe to reuse).

### Design

**Idiom choice.** Used `AppDatabase.withTransaction { }` (repo-level, same as B.2 PR 3) rather than a DAO-level `@Transaction` default method (B.2 PRs 1/2/4). Reasons:
1. The writes span three layers — `PlayerRepository` (Room-backed, composite methods), direct `DailyMissionDao` calls, and a profile read. A DAO-level `@Transaction` default method would force either a giant composite DAO method or repo-level orchestration; neither fits cleanly.
2. `BattleViewModel` already injects three DAOs (`DailyMissionDao`, `DailyStepDao`, `PlayerProfileDao`) as the B.2 PR 2 precedent. Adding `AppDatabase` is a marginal additional layering concession of the same flavour.
3. RO-02 explicitly licenses this form: "different pattern but same spirit" per B.2 PR 3's RUN_LOG. `TransactionRunner` abstraction is an RO-02 non-goal; a `@VisibleForTesting internal var runInTransaction` seam is the sanctioned middle ground.

**Transaction boundary decisions.** Only the 5 SQLite writes go inside the `runInTransaction { }` block. Two kinds of work are deliberately *outside*:
1. **Milestone notification** (`MilestoneNotificationManager.notifyNewBestWave`) — posts through the Android notification system, not SQLite. Holding a DB lock across a system-service IPC is wasteful and risks ANR if the NotificationManager is slow to respond.
2. **UI state push** (`_uiState.update`) — in-memory MutableStateFlow update. Observers are Compose collectors; they should see the post-round overlay ASAP once the transaction commits, not be blocked on DB work.

This means the UI push moved from "between writes 3 and 4" (pre-PR 5) to "strictly after all 5 writes commit" (post-PR 5). Semantically equivalent for the user (the whole sequence still completes in a single coroutine tick from the poll loop), but guarantees the DB lock is released before any UI painting.

**RO-02 + RO-03 composition.** The RO-03 per-write `runCatching { }.onFailure { Log.w }` pattern (B.3 PR 1) is *preserved inside* the transaction block. This doesn't give classical ACID rollback-on-failure — a caught exception doesn't propagate out of the transaction, so Room commits whatever was written before the throw. What the transaction *does* give:
- **External-reader atomicity**: other connections (Flow-based reactive reads) see either the pre-PR state or the post-PR state, never a partial fan-out. SQLite's SERIALIZABLE isolation on commit provides this.
- **Concurrent-writer serialization**: if another ViewModel / Worker tries to write while the tx is open, SQLite queues it — prevents interleaving.
- **Reduced lock acquisition**: one `BEGIN TRANSACTION` instead of 5. Material on mobile where DB contention matters.

The outer `runCatching { runInTransaction { ... } }.onFailure { Log.w }` guards against Room infrastructure failures (disk full, SQLCipher decrypt failure, Room throwing from `withTransaction` itself). RO-03's "UI must always appear" takes priority here — if the whole tx fails, we log and still fire the UI push with safe defaults (`isNewRecord = false`, `previousBest = 0`, etc. captured via the `var` locals).

**Captured locals.** Because `isNewRecord`, `previousBest`, `psAwarded`, and `newTier` are computed inside the tx but read by the notification + UI push *after* the tx, they're hoisted to `var`s outside the `runInTransaction` call. The Kotlin closure captures `var`s by reference wrapper — safe here because we're in a sequential suspend context, not racing coroutines.

**Test seam.** Matches `StepCrossValidator` (B.2 PR 3) verbatim: `@VisibleForTesting internal var runInTransaction: suspend (block: suspend () -> Unit) -> Unit = { block -> appDatabase.withTransaction { block() } }`. Tests construct with `mock<AppDatabase>()` and override the seam with a direct-invocation pass-through via `.apply { runInTransaction = { block -> block() } }`. Justified: Mockito can't mock Room's `withTransaction` extension on a bare mock, and instrumented tests (out of scope for JVM unit tests) validate the real transaction behaviour.

### Files touched

- `app/src/main/java/.../presentation/battle/BattleViewModel.kt` (+`androidx.room.withTransaction` + `androidx.annotation.VisibleForTesting` + `data.local.AppDatabase` imports; +`appDatabase: AppDatabase` constructor param (11 → 12); +`@VisibleForTesting internal var runInTransaction` seam; `runEndRoundPersistence` body restructured: 5 SQLite writes wrapped in `runInTransaction { }`, notification + UI push moved to after the tx block, outer `runCatching` preserves RO-03 resilience; KDoc rewritten to explain RO-02 + RO-03 composition and the outside-tx rationale)
- `app/src/test/java/.../presentation/battle/BattleViewModelTest.kt` (+`appDatabase = mock<AppDatabase>()` fixture; `createVm()` now passes 12 args AND chains `.apply { runInTransaction = { block -> block() } }` to install the pass-through seam; both direct `BattleViewModel(...)` constructions in the RO-03 failure tests updated the same way; +2 new B.2 PR 5 atomicity tests)

### Tests added (2 new cases in `BattleViewModelTest`, bringing it to 21 total)

1. **`RO-02 B2PR5 - runEndRoundPersistence opens the transaction seam exactly once per round`** — counting wrapper replaces the default pass-through; `vm.quitRound() + advanceUntilIdle()` x 2; asserts `transactionCalls == 1` after the first call (exactly one tx) and still `== 1` after the second call (roundEnded guard short-circuits before reaching the tx). Mirrors the B.2 PR 3 counting-wrapper pattern for `StepCrossValidator`.
2. **`RO-02 B2PR5 - UI push runs AFTER the transaction commits`** — captures `vm.uiState.value.roundEndState` *inside* the seam lambda immediately after `block()` returns; asserts it's still `null` there (UI push has NOT yet happened), then asserts it's non-null after the whole `quitRound()` call completes. Uses `lateinit var vm` so the seam lambda can reference the VM it's installed on. Proves the post-round overlay waits for the DB lock to release before appearing.

All 19 existing cases preserved verbatim via the `createVm()` change — the default `runInTransaction` override is a pass-through, so the behaviour under test is identical to pre-PR 5.

### Verification

- `./run-gradle.sh :app:compileDebugKotlin :app:testDebugUnitTest :app:lintDebug` — BUILD SUCCESSFUL.
- Test suite: **468 → 470 JVM tests** (+2, matches the 2 new atomicity cases exactly), 0 failures, 0 errors, 0 skipped. `BattleViewModelTest`: 19 → 21 cases.
- Lint: clean, no new warnings.
- RO-02 site count: **5/5 landed**. `grep -c "@Transaction" app/src/main/java/com/whitefang/stepsofbabylon/data/local/*.kt` — 3 matches (WorkshopDao, DailyStepDao, MilestoneDao); `grep -rn "withTransaction" app/src/main` — matches in StepCrossValidator + BattleViewModel for a total of 5 atomic sites.
- No mid-edit bugs this PR.

### Surface changes

- `BattleViewModel` constructor grew 11 → 12 params. Hilt graph unaffected — `AppDatabase` is already `@Provides`-d by `DatabaseModule`. 3 test construction sites (createVm + 2 RO-03 direct) updated.
- New public-ish API surface: `@VisibleForTesting internal var runInTransaction`. Test-only, not called from production except through the default lambda.
- No changes to `BattleUiState`, `RoundEndState`, or any domain types.
- No new production dependencies. room-ktx's `withTransaction` already on the classpath (used by `StepCrossValidator`).
- No ADR — RO-02 spec already covers this site; same rationale as B.2 PR 3 for the repo-level idiom vs DAO-level, and same rationale as B.2 PRs 1–4 for not writing a PR-specific ADR.

### Open questions / blockers

- None. **RO-02 is complete.** Real Room transaction behaviour at runtime is validated on-device / via instrumented tests (explicitly out of scope for JVM unit tests per the RO-02 verification strategy).
- B.3 PR 2 (`onCleared` guard via `ProcessLifecycleOwner.lifecycleScope`) remains as the last item in the B.3 family. Independent of RO-02; closes the mid-nav round-loss gap.

### Follow-ups

- **RO-02 milestone:** 5/5 atomic sites landed. The atomic-transaction PR family that started with B.2 PR 1 (2026-05-07) is now closed.
- **B.3 PR 2** is the next natural unit in Phase B. Small scope: `onCleared()` currently just nulls `engine.onStepReward`; the fix moves the round-persistence launch to a scope that outlives VM cleanup, so mid-battle nav doesn't drop in-flight writes.
- **Phase C** can begin in parallel with B.3 PR 2 now that RO-02 has closed its debt. C.2 (cosmetic rendering pipeline) is the release-critical path.
- Doc drift: `AGENTS.md` still says "455 JVM tests" — now stale by five PRs. Continue bundling into the next A.1-style sweep.

### Memory updated

- `STATE.md` ✅ — current objective now "RO-02 is COMPLETE"; B.2 PR 5 added to "what works"; known-issues/debt line updated (RO-02 done); priorities/next-actions reshuffled (B.3 PR 2 top); test count 468 → 470; critical-path line updated to mark B.2 complete; last-run date 2026-05-08.
- `RUN_LOG.md` ✅ — this entry.
- ADR: not warranted — RO-02 spec already covers this site; no net-new decisions required a standalone record.

## 2026-05-08 — Phase B.2 PR 4 (RO-02 site #4): atomic @Transaction for ClaimMilestone

- **Goal:** Apply the B.2 PR 1–2 pattern to the fourth RO-02 multi-write site named in `devdocs/evolution/refactoring_opportunities.md` §RO-02: `ClaimMilestone`. The use case currently (a) reads `totalStepsEarned` for a step-threshold guard, (b) reads the existing `MilestoneEntity` for an already-claimed guard, (c) iterates rewards calling `playerRepository.addGems` / `addPowerStones`, and (d) finally `milestoneDao.upsert(... claimed = true)`. A crash between (c) and (d) credits the player but leaves the milestone unclaimed — enabling double-credit on retry. Two concurrent claim clicks can also both pass (b) and both run (c). Both windows close with a single SQLite transaction wrapping the check + mark-claimed + reward credits.
- **Preflight:** read `START_HERE`, `STATE`, `CONSTRAINTS`, `RUN_LOG` head (B.2 PR 1, B.3 PR 1, B.2 PR 2, B.2 PR 3 entries). `git status` clean on `main`, up to date with origin (last commit `fd4e282 docs: sync current-state docs after B.2 PRs 1-3 + B.3 PR 1`). Read `ClaimMilestone`, `MilestoneDao`, `MilestoneEntity`, `Milestone`, `MilestoneReward`, `PlayerProfileDao`, `PlayerRepositoryImpl` (to learn `addGems` = `adjustGems` + `incrementGemsEarned` composite), `ClaimMilestoneTest` (5 cases), `FakeMilestoneDao`, `MissionsViewModel`, `MissionsViewModelTest`, `CheckMilestonesTest` (to confirm it won't be affected), `HomeViewModelTest` (to confirm `FakeMilestoneDao()` no-arg stays source-compatible). Grep-confirmed ClaimMilestone has exactly two construction sites: `MissionsViewModel` (prod) and `MissionsViewModelTest` (one test case) plus `ClaimMilestoneTest`'s sut.

### Design

Mirrored B.2 PR 2 exactly: cross-DAO `@Transaction` default method on a Room DAO interface. Chose read-modify-write (read `getByIdOnce` → bail if `claimed == true` → `upsert` → credit) over SQL-guarded single-statement (`INSERT ... ON CONFLICT DO UPDATE WHERE`) because the read-modify-write pattern matches `DailyStepDao.creditBattleStepsAtomic` identically (same idiom, same KDoc shape, same Mutex emulation in the fake) and leans on SQLite's SERIALIZABLE isolation inside `@Transaction` instead of a Room-specific return-type-of-INSERT edge case. Both approaches are correct; consistency with the established pattern won.

- **`MilestoneDao`:** added `claimMilestoneAtomic(milestoneId, gems, powerStones, claimedAt, playerDao: PlayerProfileDao): Boolean` as a suspend `@Transaction` default method. Body does `getByIdOnce` → bail if `existing?.claimed == true` → `upsert(MilestoneEntity(id, claimed = true, claimedAt))` → if `gems > 0` then `playerDao.adjustGems(gems)` + `playerDao.incrementGemsEarned(gems)` → same for Power Stones → return `true`. Cross-DAO calls are safe inside the `@Transaction` because Room's transaction tracker is scoped to the underlying `RoomDatabase`. The wallet composite (`adjustGems` + `incrementGemsEarned`) matches `PlayerRepositoryImpl.addGems` exactly — dropping either would fail the `totalGemsEarned` lifetime-counter invariant that the Economy dashboard depends on.
- **`ClaimMilestone` use case:** dep shape changed from `(milestoneDao, playerRepository)` to `(milestoneDao, playerRepository, playerProfileDao)`. The use case still reads `totalStepsEarned` through `PlayerRepository` for the step-threshold guard — this is intentional: `totalStepsEarned` is monotonic, so a stale read can only fail-closed (false-negative → user retries) and there is no correctness window to close. Body shrank from ~15 lines (profile read + claimed check + reward iteration loop + upsert) to a step-threshold guard + single `milestoneDao.claimMilestoneAtomic(...)` call. `MilestoneReward.Cosmetic` remains a no-op pending Phase C.2's cosmetic-rendering pipeline (documented in the new KDoc).
- **`MissionsViewModel`:** gained a Hilt-injected `PlayerProfileDao` constructor param (6 params now); updated the internal `ClaimMilestone(...)` construction. DI graph unaffected — `DatabaseModule` already provides `PlayerProfileDao`.
- **Fake emulation (`FakeMilestoneDao`):** added optional `linkedPlayer: FakePlayerRepository? = null` constructor arg matching `FakeDailyStepDao`'s pattern. Overrides `claimMilestoneAtomic` to emulate the SQL atomic contract under a `Mutex` — read-check-write-credit serialised so concurrent callers observe each other's mutations. The override takes `playerDao: PlayerProfileDao` for type satisfaction but ignores it; credits (gems + totalGemsEarned; powerStones + totalPowerStonesEarned) go through `linkedPlayer.profile` so existing tests can keep asserting on `FakePlayerRepository`. Added `claimMilestoneAtomicCallCount` counter for tests to prove the atomic path is live.

### Files touched

- `app/src/main/java/.../data/local/MilestoneDao.kt` (+`@Transaction claimMilestoneAtomic` default method, +`androidx.room.Transaction` import, comprehensive KDoc)
- `app/src/main/java/.../domain/usecase/ClaimMilestone.kt` (+`PlayerProfileDao` constructor dep, body rewrite to delegation, class-level KDoc explaining the monotonic-read rationale for keeping `PlayerRepository`)
- `app/src/main/java/.../presentation/missions/MissionsViewModel.kt` (+`PlayerProfileDao` import + constructor param, updated `ClaimMilestone` construction)
- `app/src/test/java/.../fakes/FakeMilestoneDao.kt` (+`linkedPlayer` param, +Mutex-guarded `claimMilestoneAtomic` override, +`claimMilestoneAtomicCallCount`, KDoc)
- `app/src/test/java/.../domain/usecase/ClaimMilestoneTest.kt` (sut helper rewritten: `FakeMilestoneDao(linkedPlayer = playerRepo)` + `mock<PlayerProfileDao>()`; 5 existing cases preserved; +3 new atomicity cases; existing `claiming milestone without reaching step threshold` strengthened with `claimMilestoneAtomicCallCount == 0` assertion to prove the fast-fail bypasses the atomic call)
- `app/src/test/java/.../presentation/missions/MissionsViewModelTest.kt` (`FakeMilestoneDao(linkedPlayer = playerRepo)`; `ClaimMilestone(milestoneDao, playerRepo, mock<PlayerProfileDao>())`; all 4 existing test cases preserved)

### Tests added (3 new cases in `ClaimMilestoneTest`, bringing it to 8 total)

1. **`successful claim goes through atomic DAO method exactly once`** — asserts `dao.claimMilestoneAtomicCallCount == 1` after a successful claim and that the wallet was credited correctly (60 Gems for FIRST_STEPS). Regression-guard: if someone reintroduces the split `addGems` + `upsert` flow this test fails immediately.
2. **`two concurrent claims on the same milestone - only one credits`** — `kotlinx.coroutines.async` + `awaitAll` pair racing on the atomic path against `Milestone.IRON_SOLES` (200 Gems + 50 Power Stones + Cosmetic no-op). Asserts exactly one `true` and one `false`, wallet credited exactly once (200 Gems + 50 PS — not 400/100), milestone marked claimed exactly once, and both callers reached the atomic method. Proves the Mutex-guarded fake models the SQL atomic guard correctly.
3. **`already-claimed entity pre-existing in DAO causes invoke to short-circuit`** — seeds the DAO with a pre-claimed entity (emulates "claim committed in a previous process lifecycle") and calls `useCase(MORNING_JOGGER)`. Asserts result is `false`, no gems credited, and `claimMilestoneAtomicCallCount == 1` — proves the already-claimed guard lives *inside* the atomic method (where the race would matter), not outside. This is one more test than the two PR 1–2 predecessors added; kept it because it covers a semantically distinct path (persisted vs in-memory race loss) and follows B.2 PR 1's precedent of strengthening existing cases where it's cheap.

All 5 existing cases preserved verbatim, with one strengthened: `claiming milestone without reaching step threshold returns false` gained `assertEquals(0, claimMilestoneAtomicCallCount)` to prove the step-threshold guard short-circuits *before* the atomic DAO call, avoiding an unnecessary DB round-trip for obviously-unqualified callers.

### Verification

- `./run-gradle.sh :app:compileDebugKotlin :app:testDebugUnitTest :app:lintDebug` — BUILD SUCCESSFUL. Room KSP compiled the third `@Transaction` default method with a cross-DAO parameter cleanly.
- Test suite: **465 → 468 JVM tests** (+3, matches the 3 new atomicity cases exactly), 0 failures, 0 errors, 0 skipped. `ClaimMilestoneTest`: 5 → 8 cases. `MissionsViewModelTest`: 4 → 4 (only construction-arg update, no test changes).
- Lint: clean, no new warnings.
- `grep -c "@Transaction" app/src/main/java/com/whitefang/stepsofbabylon/data/local/*.kt` — **3 matches** (`WorkshopDao.kt`, `DailyStepDao.kt`, `MilestoneDao.kt`). RO-02 target is ≥5 atomic sites after all 5 PRs in the family land; 4/5 now (3 DAO-level `@Transaction` + 1 repo-level `withTransaction` in `StepCrossValidator`).
- No mid-edit bugs this PR. The read-modify-write pattern from B.2 PR 2 translated cleanly to the gem/PS credit shape.

### Surface changes

- `ClaimMilestone` constructor: `(milestoneDao, playerRepository)` → `(milestoneDao, playerRepository, playerProfileDao)`. 3 call sites touched (`MissionsViewModel`, `ClaimMilestoneTest` sut + one direct construction, `MissionsViewModelTest` one direct construction); all updated in this PR.
- `MissionsViewModel` constructor grew from 5 to 6 params. Hilt graph unaffected (PlayerProfileDao already `@Provides`-d). Test code that manually constructs `MissionsViewModel` — none; the test uses use-case-level assertions (matches existing precedent in the file header comment).
- `FakeMilestoneDao` constructor: no-arg → optional `linkedPlayer` param. All 5 call sites (`ClaimMilestoneTest`, `CheckMilestonesTest`, `MissionsViewModelTest`, `HomeViewModelTest`, `FakeMilestoneDao` itself) remain source-compatible because the default is `null`. Only `ClaimMilestoneTest` and `MissionsViewModelTest` actively pass `linkedPlayer` — the other two don't credit through the fake and don't need the forwarding.
- No new production dependencies. No ADR — RO-02 spec already covers this site; same rationale as B.2 PRs 1–3.

### Open questions / blockers

- None. Double-claim atomicity is proven at the fake/Mutex level; real Room transaction behaviour is a separate instrumented-test concern per RO-02 verification strategy.
- 1 RO-02 site remains (B.2 PR 5 — wrap `runEndRoundPersistence` in a Room `@Transaction`). This is the smallest remaining unit: `runEndRoundPersistence` was extracted in B.3 PR 1 specifically to enable a single-call-site transaction wrap. Should be trivial.

### Follow-ups

- **B.2 PR 5 is the final RO-02 unit.** Should land next; completes the 5-site atomic-transaction family.
- B.3 PR 2 (`onCleared` guard via `ProcessLifecycleOwner.lifecycleScope`) remains independent and can land any time after PR 5 to keep the B.2/B.3 ordering clean.
- Doc drift: `AGENTS.md` still says "455 JVM tests" — now stale by four PRs (+3, +3, +2, +3). Bundle into the next A.1-style sweep rather than a one-line PR per change.
- Phase C can begin in parallel with B.3 PR 2 once RO-02 closes.

### Memory updated

- `STATE.md` ✅ — current objective now "B.2 PR 4 complete"; B.2 PR 4 added to "what works"; priorities/next-actions reshuffled (B.2 PR 5 top); test count 465 → 468; critical-path line updated; last-run date 2026-05-08.
- `RUN_LOG.md` ✅ — this entry.
- ADR: not warranted — RO-02 spec already covers this site with full alternatives/rollback.

## 2026-05-07 — Phase B.2 PR 3 (RO-02 site #2): AppDatabase.withTransaction for StepCrossValidator

- **Goal:** Apply RO-02 site #2 per `devdocs/evolution/refactoring_opportunities.md` §RO-02. `StepCrossValidator.validate` has multiple graduated-response branches that each pair a `playerRepository.spendSteps` / `addSteps` call with a `stepRepository.updateEscrow` / `releaseEscrow` call. A crash between the two writes leaves the wallet and escrow counter out of sync — either the player was charged without the escrow recording it (allowing double-spend on retry) or the reverse. RO-02 explicitly licenses the cross-layer `AppDatabase` import at this site (unlike PRs 1–2 where the transaction lives on the DAO) because the validator lives in `data/healthconnect/` and the graduated-response branches need parallel transaction scopes.
- **Preflight:** read `START_HERE`, `STATE`, `CONSTRAINTS`, `RUN_LOG` head (B.2 PR 1 + 2, B.3 PR 1 entries). `git status` clean on `main`, 4 commits ahead of origin pushed. Read `StepCrossValidator`, `StepRepository` interface, `StepRepositoryImpl`, `AntiCheatPreferences`, `StepCrossValidatorTest` (10 cases, all Mockito-based), `EscrowLifecycleTest` (2 cases, uses `FakePlayerRepository` + `FakeStepRepository`). Grep-confirmed 3 construction sites total.

### Design

The spec says "3 parallel branches" but the validator actually has **5 multi-write pairs** (cap-excess branches share a shape; each gets its own wrapper):

1. **Level 3 cap-excess:** `spendSteps(excess) + updateEscrow(..., MAX_ESCROW_SYNCS_DEFAULT)`
2. **Level 2 cap-excess:** `spendSteps(excess) + updateEscrow(..., MAX_ESCROW_SYNCS_DEFAULT)`
3. **Level 1 first-escrow:** `spendSteps(excess) + updateEscrow(..., newSyncCount)` — the subsequent-sync metadata-only update and the discard path are single writes (not wrapped)
4. **Level 0 first-escrow:** same shape as #3
5. **Reconciliation:** `addSteps(record.escrowSteps) + releaseEscrow(date)`

All 5 wrapped in `runInTransaction { … }`. The `antiCheatPrefs.recordCvOffense(date)` write (at the top of the if-branch) and `antiCheatPrefs.decayCvOffenses()` (after reconciliation) deliberately stay **outside** the transaction — they are SharedPreferences writes, not SQLite-backed, and cannot participate in a Room transaction. Recording the offense before the transaction is also the safer ordering: a transaction failure must not hide the fact that a validation attempt detected a discrepancy.

**Test seam.** `StepCrossValidator` gains an `@VisibleForTesting internal var runInTransaction` field with a default that delegates to `appDatabase.withTransaction { block() }`. Tests construct with `mock<AppDatabase>()` (Mockito can't mock Room's `withTransaction` extension on a bare mock) and override the seam with a direct-invocation pass-through `{ block -> block() }`. The branch-logic assertions remain unchanged; real Room transaction behaviour is an instrumented-test concern out of JVM scope, per the RO-02 verification strategy.

### Why the seam + internal var, not a full abstraction

RO-02 non-goal: "Do not introduce a global `TransactionRunner` abstraction." The seam is a single `internal var` on one class, not a project-wide interface — it exists only because `mock<AppDatabase>()` doesn't support Kotlin extension functions. Every production call goes through the default `appDatabase.withTransaction { block() }`; the override path is test-only and annotated with `@VisibleForTesting`.

### Files touched

- `app/src/main/java/.../data/healthconnect/StepCrossValidator.kt` (+`AppDatabase` + `androidx.annotation.VisibleForTesting` + `androidx.room.withTransaction` imports; +`appDatabase` constructor param; +`runInTransaction` seam; 5 multi-write branches wrapped; SharedPreferences writes explicitly documented as outside-transaction; comprehensive KDoc on the class header explaining the scope split and RO-02 license)
- `app/src/test/java/.../data/healthconnect/StepCrossValidatorTest.kt` (+`AppDatabase` mock; `runInTransaction = { block -> block() }` on validator construction; all 10 existing cases preserved verbatim; +2 new atomicity cases)
- `app/src/test/java/.../data/integration/EscrowLifecycleTest.kt` (+`AppDatabase` mock; new `makeValidator` helper that wires the pass-through seam; both existing integration cases preserved)

### Tests added (2 new cases in `StepCrossValidatorTest`, bringing it to 12 total)

1. **`RO-02 site 2 - multi-write branch invokes the transaction seam exactly once per write pair`** — constructs a validator with a counting wrapper around `runInTransaction`; exercises the Level 0 first-escrow path; asserts `transactionCalls == 1` and that both writes still happened. Proves the seam is live for the multi-write branches.
2. **`RO-02 site 2 - single-write branches bypass the transaction seam`** — same counting wrapper; exercises the Level 0 subsequent-sync path (metadata-only `updateEscrow`, no `spendSteps`); asserts `transactionCalls == 0`. Proves the seam is not dead weight on single-write branches — the wrapping is surgical, not indiscriminate.

All 10 existing `StepCrossValidatorTest` cases continue to pass verbatim because the seam's default is a direct-invocation pass-through from the test's perspective. `EscrowLifecycleTest`'s two full-lifecycle cases (escrow + release, escrow + discard) also pass — same seam wiring via the new `makeValidator` helper.

### Verification

- `./run-gradle.sh :app:compileDebugKotlin :app:testDebugUnitTest :app:lintDebug` — BUILD SUCCESSFUL.
- Test suite: **463 → 465 JVM tests** (+2, matches the 2 new atomicity cases exactly), 0 failures, 0 errors, 0 skipped. `StepCrossValidatorTest`: 10 → 12 cases. `EscrowLifecycleTest`: 2 → 2 (preserved, now routing through the seam).
- Lint: clean, no new warnings.
- `grep -c "@Transaction" app/src/main/java/com/whitefang/stepsofbabylon/data/local/*.kt` still 2 (WorkshopDao, DailyStepDao). `grep -c "withTransaction" app/src/main/java/com/whitefang/stepsofbabylon/data/healthconnect/StepCrossValidator.kt` — 1 match (the default lambda body). RO-02 progress: 3/5 atomic sites landed.

### Surface changes

- `StepCrossValidator` constructor grew from 4 to 5 params (added `AppDatabase`). Hilt graph unaffected — `AppDatabase` is provided by `DatabaseModule`.
- Tests that construct `StepCrossValidator` manually (3 sites total) all updated.
- No public API changes to `StepRepository`, `PlayerRepository`, or `AntiCheatPreferences`.
- No new production dependencies.
- No ADR — same rationale as B.2 PRs 1–2.

### Open questions / blockers

- None. The `runInTransaction` seam is a deliberate, narrowly-scoped test hook. The real transaction behaviour is exercised at app runtime via Room's generated impl of `withTransaction` on the SQLCipher-wrapped `AppDatabase`.
- 2 RO-02 sites remain. B.2 PR 4 (`ClaimMilestone`) is the same pattern as PRs 1–2 — a composite `@Transaction` method on `MilestoneDao` taking `PlayerProfileDao`. B.2 PR 5 wraps `runEndRoundPersistence` in `withTransaction { }` — a single-call-site change thanks to B.3 PR 1.

### Follow-ups

- B.2 PR 4 next: `ClaimMilestone` atomic. Same mechanical pattern as PRs 1–2. Expect a clean copy-paste of the shape.
- B.2 PR 5 is the smallest remaining RO-02 unit (single `withTransaction { }` wrap around an existing function).
- B.3 PR 2 (`onCleared` guard) remains independent and can land any time.
- Doc drift: `AGENTS.md` still says "455 JVM tests" (now 465). Bundle into a future A.1-style sweep.

### Memory updated

- `STATE.md` ✅ — current objective now "B.2 PR 3 complete"; B.2 PR 3 added to "what works"; priorities/next-actions reshuffled (B.2 PR 4 top); test count 463 → 465; critical-path line updated.
- `RUN_LOG.md` ✅ — this entry.
- ADR: not warranted — RO-02 spec already covers this site with full alternatives/rollback.

## 2026-05-07 — Phase B.2 PR 2 (RO-02 site #1): atomic @Transaction for AwardBattleSteps

- **Goal:** Apply the B.2 PR 1 pattern to the first multi-write site RO-02 names: `AwardBattleSteps`. Wrap the cap check + `incrementBattleSteps` + wallet-credit chain in a single Room `@Transaction` so a crash between the two writes can no longer leave the wallet credited without the cap counter moving, and two concurrent kills with 1 battle-step of headroom can no longer both credit and overflow the cap by 1.
- **Preflight:** read `START_HERE`, `STATE`, `CONSTRAINTS`, `RUN_LOG` head (B.2 PR 1 + B.3 PR 1 entries). `git status` clean at `main`, 2 commits ahead pushed to origin. Read `AwardBattleSteps`, `DailyStepDao`, `DailyStepRecordEntity`, existing `AwardBattleStepsTest` (7 cases), `FakeDailyStepDao`. Grep-confirmed 3 callers: `AwardBattleStepsTest` (sut helper), `BattleViewModel` (init), `BattleViewModelTest` (dead `awardBattleSteps` field declared but never read).

### Design

Mirrored B.2 PR 1 exactly: cross-DAO `@Transaction` default method on a Room interface.

- **`DailyStepDao`:** added `creditBattleStepsAtomic(date, requested, dailyCap, playerDao: PlayerProfileDao): Long` as a suspend `@Transaction` default method. Body does cap check → `min(requested, remaining)` → `incrementBattleSteps(date, credited)` → `playerDao.adjustStepBalance(credited)` → returns credited. Cross-DAO call is safe inside the `@Transaction` because Room's transaction tracker is scoped to the underlying `RoomDatabase`.
- **`AwardBattleSteps` use case:** dep shape changed from `(playerRepository, dailyStepDao, timeProvider)` to `(dailyStepDao, playerProfileDao, timeProvider)`. Body shrank from ~7 lines of read/compute/write to a single delegation `dailyStepDao.creditBattleStepsAtomic(today, amount, DAILY_BATTLE_STEP_CAP, playerProfileDao)`. Drops the `PlayerRepository` dep — wallet write now happens inside the transaction via `PlayerProfileDao` directly.
- **`BattleViewModel`:** added `PlayerProfileDao` as a Hilt-injected constructor param (11 params now); updated the internal `AwardBattleSteps` construction. DI graph unaffected — `DatabaseModule` already provides `PlayerProfileDao`.
- **Fake emulation (`FakeDailyStepDao`):** added optional `linkedPlayer: FakePlayerRepository? = null` constructor arg. Overrides `creditBattleStepsAtomic` to emulate the SQL atomic contract under a `Mutex` — read-check-write-credit serialised so concurrent callers observe each other's mutations. The override takes `playerDao: PlayerProfileDao` for type satisfaction but ignores it; wallet side-effects go through `linkedPlayer` so existing tests can keep asserting on `FakePlayerRepository.profile`. Added `creditBattleStepsAtomicCallCount` for tests to prove the atomic path is live.

The `playerDao` decoy is a deliberate test-only abstraction. The real impl (Room's generated default-method delegate) exercises the actual `PlayerProfileDao.adjustStepBalance` path at runtime. The JVM tests can't meaningfully test the real cross-DAO call path without an in-memory Room DB; the fake's Mutex-guarded override models the SQL-level atomicity, and callers pass `mock<PlayerProfileDao>()` for type satisfaction.

### Files touched

- `app/src/main/java/.../data/local/DailyStepDao.kt` (+`@Transaction creditBattleStepsAtomic` default method, +`androidx.room.Transaction` + `kotlin.math.min` imports, KDoc)
- `app/src/main/java/.../domain/usecase/AwardBattleSteps.kt` (–`PlayerRepository` dep, +`PlayerProfileDao` dep, body rewrite to single delegation, KDoc update)
- `app/src/main/java/.../presentation/battle/BattleViewModel.kt` (+`PlayerProfileDao` import + constructor param, updated `AwardBattleSteps` construction)
- `app/src/test/java/.../fakes/FakeDailyStepDao.kt` (+`linkedPlayer` param, +Mutex-guarded `creditBattleStepsAtomic` override, +`creditBattleStepsAtomicCallCount`, KDoc)
- `app/src/test/java/.../domain/usecase/AwardBattleStepsTest.kt` (sut helper rewritten: `FakeDailyStepDao(linkedPlayer = playerRepo)` + `mock<PlayerProfileDao>()`; 7 existing cases preserved; +2 new atomicity cases)
- `app/src/test/java/.../presentation/battle/BattleViewModelTest.kt` (removed dead `awardBattleSteps` field; `dailyStepDao = FakeDailyStepDao(linkedPlayer = playerRepo)`; +`playerProfileDao = mock<PlayerProfileDao>()`; wired into `createVm` + 2 B.3 PR 1 failure-injection tests)

### Tests added (2 new cases in `AwardBattleStepsTest`, bringing it to 9 total)

1. **`successful credit goes through atomic DAO method and bypasses the legacy split path`** — asserts `dao.creditBattleStepsAtomicCallCount == 1` and `player.spendStepsCallCount == 0` after a successful credit. Proves the use case no longer uses the split `playerRepository.addSteps` + `dao.incrementBattleSteps` path. Regression-guard: if someone reintroduces the split flow this test fails immediately.
2. **`two concurrent kills on exactly one headroom - only one credits`** — `kotlinx.coroutines.async` pair racing on the atomic path with exactly 1 battle-step of headroom. Asserts `results.sum() == 1L` (total credited = 1 unit, no overflow), cap counter advances by exactly 1, wallet advances by exactly 1, and both calls reached the atomic method. Proves the Mutex-guarded fake models the SQL atomic guard correctly; real Room-level atomicity is a separate instrumented-test concern (out of scope).

All 7 existing cases preserved verbatim: first call / cap exhausted / partial credit / date rollover / zero-or-negative no-op / dao-incremented-by-credited-not-requested / FakeTimeProvider drives default today.

BattleViewModelTest dead-code removal: the `private lateinit var awardBattleSteps: AwardBattleSteps` field at test line 34 was declared and initialised at line 49 but never read by any test case. Removed as a drive-by cleanup (the imports reference resolves via the companion-object constant access at lines 182/202, unchanged).

### Verification

- `./run-gradle.sh :app:compileDebugKotlin :app:testDebugUnitTest :app:lintDebug` — BUILD SUCCESSFUL. Room KSP compiled the second `@Transaction` default method with a cross-DAO parameter cleanly (same shape as B.2 PR 1's `WorkshopDao.purchaseUpgradeAtomic`).
- Test suite: **461 → 463 JVM tests** (+2, matches the 2 new atomicity cases exactly), 0 failures, 0 errors, 0 skipped. `AwardBattleStepsTest`: 7 → 9 cases. `BattleViewModelTest`: 19 → 19 (dead field removal doesn't change test count — never a `@Test`).
- Lint: clean, no new warnings.
- `grep -c "@Transaction" app/src/main/java/com/whitefang/stepsofbabylon/data/local/*.kt` — **2 matches** (`WorkshopDao.kt`, `DailyStepDao.kt`). RO-02 target is ≥5 after all 5 PRs land; 2/5 now.
- One mid-edit bug fixed: the first `BattleViewModelTest` edit had a trailing-newline mismatch that concatenated `MilestoneNotificationManager` + `kotlinx.coroutines.Dispatchers` imports onto one line. Caught by the next immediate edit+diff review, fixed with a dedicated edit.

### Surface changes

- `AwardBattleSteps` constructor: `(playerRepository, dailyStepDao, timeProvider?)` → `(dailyStepDao, playerProfileDao, timeProvider?)`. 3 call sites updated in this PR (VM init, use case test, and the removed dead-field init in `BattleViewModelTest`).
- `BattleViewModel` constructor grew from 10 to 11 params. Hilt graph unaffected (PlayerProfileDao already `@Provides`-d). Manual constructions in `BattleViewModelTest` (`createVm` and 2 B.3 PR 1 failure-injection tests) all updated.
- `FakeDailyStepDao` constructor: no-arg → optional `linkedPlayer` param. All 5 other call sites (`DailyStepManagerTest` x2, `TrackWeeklyChallengeTest`, plus the 2 in updated tests) remain source-compatible because the default is `null`.
- No new production dependencies. No ADR — RO-02 spec already covers this site; same rationale as B.2 PR 1 / B.3 PR 1.

### Open questions / blockers

- None. Concurrent-kill atomicity is proven at the fake/Mutex level; real Room transaction behaviour is a separate instrumented-test concern per RO-02 verification strategy.
- 3 RO-02 sites remain. B.2 PR 3 (`StepCrossValidator`) uses a different idiom (`AppDatabase.withTransaction { }` at repo level) because the validator lives in `data/healthconnect/` and can legally import `RoomDatabase`. B.2 PR 4 (`ClaimMilestone`) applies the same pattern as PR 1 and PR 2. B.2 PR 5 wraps `runEndRoundPersistence` — a single-call-site change thanks to B.3 PR 1.

### Follow-ups

- B.2 PR 3 (`StepCrossValidator`) next. Different pattern but same goal; expect higher touch count due to the three graduated-response branches.
- B.2 PR 4 (`ClaimMilestone`) is mechanically identical to this PR once `MilestoneDao` gets its own `@Transaction` default method.
- B.3 PR 2 (`onCleared` guard via `ProcessLifecycleOwner.lifecycleScope`) is independent and can slot in any time.
- Doc drift: `AGENTS.md` says "455 JVM tests" — now stale by three PRs (+3, +3, +2). Bundle into a future A.1-style sweep.

### Memory updated

- `STATE.md` ✅ — current objective now "B.2 PR 2 complete"; B.2 PR 2 added to "what works"; priorities/next-actions reshuffled (B.2 PR 3 top); test count 461 → 463; critical-path line updated.
- `RUN_LOG.md` ✅ — this entry.
- ADR: not warranted — RO-02 spec already fully covers this site.

## 2026-05-07 — Phase B.3 PR 1 (RO-03 pattern-proving): resilient `runEndRoundPersistence`

- **Goal:** Execute the RO-03 first PR per `devdocs/evolution/refactoring_opportunities.md` §RO-03 — extract `runEndRoundPersistence` from `BattleViewModel.endRound` and isolate every write / notification in a `runCatching { }.onFailure { Log.w }` block so a single Room or notification-manager exception can no longer leave the player on a frozen battle screen. Spec explicitly splits RO-03 into two PRs; **no `onCleared` change in this PR** (that is PR 2, which uses `ProcessLifecycleOwner.lifecycleScope` to outlive VM cleanup). PR 1 is deliberately small and composable with a future B.2 PR 5 that wraps the whole body in a Room `@Transaction`.
- **Preflight:** read `START_HERE`, `STATE`, `CONSTRAINTS`, `RUN_LOG` head (B.2 PR 1 entry and below). `git status` showed the B.2 PR 1 working tree uncommitted — flagged to user. B.3 touches a disjoint file set (`BattleViewModel.kt`, `BattleViewModelTest.kt`, `FakePlayerRepository.kt`), so the diffs stack cleanly and can be staged as two commits at review time. Read RO-03 spec in full, `BattleViewModel`, `BattleViewModelTest`, `BattleUiState` (for `RoundEndState` field names).

### Design

The spec names 3 writes but the current `endRound` actually has **5** best-effort writes plus 1 notification:

1. `updateBestWave(tier, wave)` — produces `result.isNewRecord` + `result.previousBest` used by the UI push.
2. `awardWaveMilestone(wave)` — produces `psAwarded` used by the UI push.
3. `milestoneNotificationManager.notifyNewBestWave(...)` — not a DB write but still best-effort.
4. `playerRepository.updateHighestUnlockedTier(newTier)` — gated on a profile read + `checkTierUnlock`; result used by the UI push.
5. `playerRepository.incrementBattleStats(...)` — previously wrapped in ad-hoc `try / catch (_: Exception) {}` swallow.
6. `dailyMissionDao.updateProgress(...)` — previously wrapped in ad-hoc `try / catch (_: Exception) {}` swallow.

All six are now normalised to `runCatching { ... }.onFailure { Log.w(TAG, "endRound: <writeName> failed", it) }`. Writes whose results feed `RoundEndState` (1, 2, 4) use `.getOrNull()` / `.getOrDefault(0)` with safe fallbacks (`isNewBestWave = false`, `previousBest = 0`, `psAwarded = 0`, `tierUnlocked = null`) so the `_uiState.update` push below is guaranteed to run.

`endRound()` shrank from ~35 lines to 6 (guard + null-check + `viewModelScope.launch { runEndRoundPersistence(eng, wave) }`). `quitRound()` and the polling-loop call site (`startPollingEngine`) are unchanged — both go through the same slimmed-down `endRound()`, so the `roundEnded` guard still dedupes.

Added `private companion object { private const val TAG = "BattleViewModel" }` + `import android.util.Log`. This is the first `Log.*` call in the Battle presentation layer; it matches the R2-07 precedent for `StepSyncWorker`'s resilient-worker catches.

### Design decision: no `onCleared` change

Per the RO-03 spec's "First safe step": *"PR 1 — extract `runEndRoundPersistence` and wrap each of the 3 writes in `runCatching { }.onFailure { Log.w }`. Both `endRound()` and `quitRound()` now call the extracted function. **No `onCleared` change yet.**"* Deliberately keeping this PR to a single-axis change (error handling) means a clean revert and lets reviewers verify the behaviour-preservation without having to reason about lifecycle scoping at the same time. The `onCleared` fix needs `ProcessLifecycleOwner.lifecycleScope` to survive VM cleanup, which is a different risk profile (process-kill still loses data; transaction wrapping in RO-02 PR 5 is the longer-term fix).

### Files touched

- `app/src/main/java/.../presentation/battle/BattleViewModel.kt` (+`android.util.Log` import; extracted `runEndRoundPersistence(eng, wave)`; normalised 5 writes + 1 notification to `runCatching + Log.w`; `endRound()` shrank from ~35 lines to 6; +`private companion object { TAG }`)
- `app/src/test/java/.../fakes/FakePlayerRepository.kt` (`class` → `open class`; marked `updateBestWave`, `addPowerStones`, `updateHighestUnlockedTier`, `incrementBattleStats` as `open override` for per-method throwing overrides)
- `app/src/test/java/.../presentation/battle/BattleViewModelTest.kt` (+ `installEngineForEndRound(vm)` helper using reflection on the private `engine` field, mirroring the existing A.7 pattern; +3 RO-03 tests)

### Tests added (3 new cases in `BattleViewModelTest`, bringing it to 19 total)

1. **`RO-03 - updateBestWave failure does not block later writes or UI push`** — anonymous `FakePlayerRepository` subclass throws from `updateBestWave`. Asserts (a) `vm.uiState.value.roundEndState` is non-null (UI push ran despite the earlier throw) and (b) `totalRoundsPlayed == 1L` (a later write, `incrementBattleStats`, still ran). Before RO-03 this test would fail because the thrown exception propagates out of the `viewModelScope.launch` and short-circuits the remaining writes + UI push.
2. **`RO-03 - all persistence failures still produce RoundEndState`** — throws from all 4 player-repository writes (`updateBestWave`, `addPowerStones`, `updateHighestUnlockedTier`, `incrementBattleStats`). Asserts the `RoundEndState` is still set, with safe-default fields (`isNewBestWave = false`, `previousBest = 0`, `powerStonesAwarded = 0`, `tierUnlocked = null`). Proves the `.getOrNull() / .getOrDefault(0)` fallback contract.
3. **`RO-03 - roundEnded guard prevents double persistence on repeated quitRound`** — calls `quitRound()` twice in sequence. Asserts `totalRoundsPlayed` is exactly 1 after both calls — the `roundEnded` boolean guard at the top of `endRound()` gates the second call. Protects against a regression where a future change (e.g. making `endRound` idempotent differently) breaks the single-run invariant the polling loop + `quitRound` depend on.

Both failure-isolation tests use reflection to reach the private `engine` field (`installEngineForEndRound` helper), then call the public `quitRound()` to drive `endRound` without needing the full polling-loop setup. The pattern follows the A.7 tests, which already use reflection for `effectEngine` / `pendingEffects` / `effects` access.

### Verification

- `./run-gradle.sh :app:compileDebugKotlin` — BUILD SUCCESSFUL. The `runCatching { ... }.getOrNull() ` / `.getOrDefault(0)` chains type-check against `UpdateBestWave.Result?`, `Int?`, and `Int` targets cleanly.
- `./run-gradle.sh :app:testDebugUnitTest :app:lintDebug` — BUILD SUCCESSFUL. `BattleViewModelTest`: 16 → 19 cases, 0 failures. Total suite: **458 → 461 JVM tests** (+3, matches the 3 new RO-03 cases exactly), 0 failures, 0 errors, 0 skipped. Lint clean.
- Field-name correction: initial test draft referenced `state.isNewRecord` / `state.newTierUnlocked`, which don't exist — actual field names are `state.isNewBestWave` / `state.tierUnlocked`. Caught by a pre-test grep on `RoundEndState`'s declaration in `BattleUiState.kt` before running the suite.

### Surface changes

- No public `BattleViewModel` API change. `endRound()` is private; `quitRound()` and `startPollingEngine()` behaviour preserved.
- `FakePlayerRepository` is now `open class` with 4 `open override` methods. Subclassing is the only behaviour change — the unqualified `FakePlayerRepository(...)` constructor call still works unchanged across all 15+ existing test sites.
- No new production dependencies. `android.util.Log` was already available (presentation layer is allowed Android imports).

### Open questions / blockers

- None for this PR. `onCleared` mid-nav gap is explicitly deferred to B.3 PR 2 per the RO-03 spec.
- No ADR written. RO-03 is fully documented in `devdocs/evolution/refactoring_opportunities.md §RO-03` with alternatives, first-safe-step, verification, rollback, and non-goals. Same rationale as B.2 PR 1 — no duplication with the evolution doc, matches Phase A precedent of only writing ADRs for genuinely new decisions.

### Follow-ups

- **B.2 PR 5 is now trivial.** The extraction means wrapping the `runEndRoundPersistence` body in a Room `@Transaction` (or `AppDatabase.withTransaction { }`) is a single-call-site change. Was gated on this PR per the RO-02 spec's dependency graph; now unblocked.
- B.3 PR 2 remains: `onCleared` guard using `ProcessLifecycleOwner.lifecycleScope`. Would outlive the VM but still not the process — process-kill loss is a separate issue that RO-02 PR 5 (this-PR-dependent transaction wrapping) partially addresses.
- B.2 PR 2 (`AwardBattleSteps` atomic) and B.2 PR 4 (`ClaimMilestone` atomic) can now proceed in parallel with each other since the pattern is proven.
- Doc drift: `AGENTS.md` still says "455 JVM tests" — now stale by two PRs (+3 each). Bundle into a future A.1-style sweep rather than a one-line doc PR per change.

### Memory updated

- `STATE.md` ✅ — current objective now "Phase B.3 PR 1 complete"; B.3 PR 1 moved into "what works"; priorities/next-actions reshuffled (B.2 PRs 2–4 now top; B.3 PR 2 dropped to priority 3); test count 458 → 461; critical-path line updated.
- `RUN_LOG.md` ✅ — this entry.
- ADR: not warranted — see "Open questions".

## 2026-05-07 — Phase B.2 PR 1 (RO-02 pattern-proving): atomic @Transaction for PurchaseUpgrade

- **Goal:** Execute the RO-02 first PR per `devdocs/evolution/refactoring_opportunities.md` §RO-02 — replace the two-step `spendSteps` + `setUpgradeLevel` sequence in `PurchaseUpgrade` with a single atomic call backed by a Room `@Transaction` DAO method. Proves the pattern so PRs 2–5 (AwardBattleSteps, StepCrossValidator, ClaimMilestone, endRound) can follow without re-litigating the design.
- **Preflight:** read `START_HERE`, `STATE`, `CONSTRAINTS`, `RUN_LOG` head (Phase A + B.1 entries). Verified `@Transaction` count in `app/src/main` was 0 before this PR (Phase 4 §2 baseline still held post-Phase-A). room-ktx 2.8.4 confirmed on classpath; chose DAO-level `@Transaction` default method over repo-level `AppDatabase.withTransaction { }` to match RO-02 sketch literally and keep the pattern per-site rather than introducing a transaction idiom to be repeated at every future repo. Read `PurchaseUpgrade` use case, both repos + impls, both DAOs, existing `PurchaseUpgradeTest`, both fakes, `WorkshopViewModelTest`, `UserFeedbackTest`, DI modules. Grep-confirmed `PurchaseUpgrade` has exactly two callers: the test and `WorkshopViewModel`.

### Design

- **Authoritative guard** lives in SQL: `PlayerProfileDao.adjustStepBalanceIfSufficient(cost: Long): Int` runs `UPDATE player_profile SET currentStepBalance = currentStepBalance - :cost WHERE id = 1 AND currentStepBalance >= :cost`. Returns rows affected (1 = deducted, 0 = insufficient). The `WHERE ... >= :cost` clause atomically closes both the partial-failure gap and the double-tap race (two concurrent purchases can't both pass and double-spend).
- **Transaction boundary** lives on `WorkshopDao` as a suspend `@Transaction` default interface method: `purchaseUpgradeAtomic(type, newLevel, cost, playerDao: PlayerProfileDao): Boolean`. Body calls `playerDao.adjustStepBalanceIfSufficient(cost)`; if it returns 0 the transaction short-circuits to `false` (no upsert); otherwise it calls `upsert(WorkshopUpgradeEntity(type, newLevel))` and returns `true`. Cross-DAO call inside the transaction is safe because Room's transaction tracker is scoped to the underlying `RoomDatabase`, not to a specific DAO instance — both DAO calls share the same SQLite transaction.
- **Domain interface** gained `WorkshopRepository.purchaseUpgradeAtomic(type, newLevel, cost): Boolean`. Implemented in `WorkshopRepositoryImpl` after adding a `PlayerProfileDao` constructor dependency (DI graph unaffected — `DatabaseModule` already `@Provides`-es both DAOs).
- **Use case shrink:** `PurchaseUpgrade` dropped its `PlayerRepository` dependency entirely. Body now does `maxLevel` check → `calculateCost` → wallet fast-fail (UI-side hint) → `workshopRepository.purchaseUpgradeAtomic(type, currentLevel + 1, cost)`. Public signature `(type, currentLevel, wallet): Boolean` unchanged, so `WorkshopViewModel`'s call sites were untouched aside from the one-line constructor update from 3-arg to 2-arg.
- **Fake emulation.** `FakeWorkshopRepository` gained an optional `linkedPlayer: FakePlayerRepository? = null` constructor param. When supplied, `purchaseUpgradeAtomic` uses a `kotlinx.coroutines.sync.Mutex` to faithfully emulate the SQL atomic guard — read-check-deduct-write under the mutex so a concurrent call observes the deducted balance. When null, it acts as a purchase recorder (no affordability check) so the 5 existing `FakeWorkshopRepository()` call sites stayed source-compatible without every test being forced to supply a player. `FakePlayerRepository` gained a `spendStepsCallCount` counter (increments on direct `spendSteps`) so tests can prove the use case does NOT call the legacy path.

### Files touched

- `app/src/main/java/.../data/local/PlayerProfileDao.kt` (+1 `@Query` method, KDoc)
- `app/src/main/java/.../data/local/WorkshopDao.kt` (+`@Transaction` default method, +import `androidx.room.Transaction`, KDoc)
- `app/src/main/java/.../domain/repository/WorkshopRepository.kt` (+1 interface method, KDoc)
- `app/src/main/java/.../data/repository/WorkshopRepositoryImpl.kt` (+`PlayerProfileDao` constructor dep, +impl)
- `app/src/main/java/.../domain/usecase/PurchaseUpgrade.kt` (–`PlayerRepository` dep, body rewrite, KDoc)
- `app/src/main/java/.../presentation/workshop/WorkshopViewModel.kt` (1-line constructor call update)
- `app/src/test/java/.../fakes/FakePlayerRepository.kt` (+`spendStepsCallCount`)
- `app/src/test/java/.../fakes/FakeWorkshopRepository.kt` (rewritten: +`linkedPlayer` param, +Mutex, +`purchaseUpgradeAtomic` impl, +`purchaseUpgradeAtomicCallCount`)
- `app/src/test/java/.../domain/usecase/PurchaseUpgradeTest.kt` (rewrite: 4 existing cases strengthened with workshop-side asserts, +3 new RO-02 atomicity cases)
- `app/src/test/java/.../presentation/workshop/WorkshopViewModelTest.kt` (setup order swap + link fakes)

### Tests added (3 new cases in `PurchaseUpgradeTest`)

1. **`successful purchase uses atomic repo method and does not call spendSteps directly`** — asserts `playerRepo.spendStepsCallCount == 0` and `workshopRepo.purchaseUpgradeAtomicCallCount == 1` after one successful purchase. If someone re-introduces the two-step flow this test fails immediately.
2. **`purchase skips atomic call when wallet fast-fail trips`** — wallet balance 0 means the use case returns false before hitting the repo. Asserts `purchaseUpgradeAtomicCallCount == 0`. Verifies the fast-fail path exists and avoids an unnecessary DB round-trip for obviously-broke callers.
3. **`two concurrent purchases on exactly sufficient balance - only one succeeds`** — `kotlinx.coroutines.async` pair racing on the atomic path with exactly one purchase worth of Steps. Asserts exactly one `true` and one `false` result, balance = 0 after, upgrade level = 1 (not 2), and `purchaseUpgradeAtomicCallCount == 2`. Proves the Mutex-guarded fake models the SQL atomic guard correctly; the real Room-level atomicity is a separate instrumented-test concern (out of scope for this PR; documented in RO-02 verification strategy).

The 4 existing cases were all preserved. `insufficient steps returns false without mutation` was strengthened: previously only asserted on step balance; now also asserts `workshop.upgrades.value[DAMAGE] == null` (no partial workshop-side write). `at max level returns false` gained a balance-unchanged assertion. `level 0 purchase costs exactly baseCost` unchanged.

### Verification

- `./run-gradle.sh :app:compileDebugKotlin` — BUILD SUCCESSFUL. Room KSP compiled the `@Transaction` default method including the cross-DAO call signature cleanly; had the pattern been malformed (e.g. interface-vs-abstract-class, transaction-boundary-across-DAOs), KSP would have failed at this step.
- `./run-gradle.sh :app:testDebugUnitTest` — BUILD SUCCESSFUL. `PurchaseUpgradeTest`: 4 → 7 cases, 0 failures. Total suite: **455 → 458 JVM tests** (+3, matches the 3 new atomicity cases exactly), 0 failures, 0 errors, 0 skipped.
- `./run-gradle.sh :app:lintDebug` — BUILD SUCCESSFUL. No new warnings introduced.
- `grep -rn "@Transaction\|withTransaction" app/src/main --include='*.kt'` — **1 match** (`WorkshopDao.kt:48`). This is the first `@Transaction` marker in `app/src/main`; RO-02 target is ≥5 after all 5 PRs in the family land.

### Surface changes (breaking-but-internal)

- `PurchaseUpgrade` constructor now takes `(workshopRepository, calculateCost?)` instead of `(workshopRepository, playerRepository, calculateCost?)`. Only two call sites touched (`WorkshopViewModel` + `PurchaseUpgradeTest`); both updated in this PR.
- `WorkshopRepositoryImpl` constructor gained a second `PlayerProfileDao` param. Hilt graph unaffected — the DAO is already provided by `DatabaseModule`. Test code that manually constructs `WorkshopRepositoryImpl` would need updating; a grep confirmed there is no such code (all test sites use `FakeWorkshopRepository`).

### Open questions / blockers

- None for this PR. Concurrent-purchase atomicity is proven at the fake/Mutex level; a future on-device smoke test or instrumented test against a real Room DB would be the only stronger guarantee but is not in RO-02 PR 1 scope (the verification strategy in the spec explicitly calls these two levels adequate for each PR).
- No ADR written. The decision was already fully documented in `devdocs/evolution/refactoring_opportunities.md §RO-02` with alternatives, first-safe-step, rollback, and non-goals. Writing an ADR would duplicate content; subsequent RO-02 PRs can reference the same section. Matches the Phase A precedent (9 PRs, 0 ADRs, all pre-documented in Phase-14 roadmap).

### Follow-ups

- B.2 PR 2 (`AwardBattleSteps` — `addSteps` + `incrementBattleSteps` cross-DAO) is the next natural unit; add a composite `@Transaction` method on `DailyStepDao` that takes `PlayerProfileDao`. Uses the pattern established here.
- B.2 PR 3 (`StepCrossValidator`) is the one place RO-02 licenses a repo-level `AppDatabase.withTransaction { }` (validator lives in `data/healthconnect/` so it can import `RoomDatabase`); different pattern but same spirit.
- B.2 PR 4 (`ClaimMilestone`) — composite method on `MilestoneDao` taking `PlayerProfileDao`. Same pattern as this PR.
- B.2 PR 5 (`endRound` `@Transaction` wrapper) is gated on B.3 PR 1 landing first (extraction of `runEndRoundPersistence`).
- Doc drift: `AGENTS.md` says "455 JVM tests" — stale after this PR. Bundle into the next A.1-style doc sweep alongside any subsequent test-count changes (no value in a one-line PR for each ±3).

### Memory updated

- `STATE.md` ✅ — current objective now "Phase B.2 PR 1 complete"; B.2 PR 1 moved into "what works"; priorities/next-actions reshuffled with B.3 PR 1 as the new top priority; test count 455 → 458; critical-path line updated.
- `RUN_LOG.md` ✅ — this entry.
- ADR: not warranted — see "Open questions".

## 2026-05-07 — Phase A (Foundation): land 9 tactical PRs from the Phase-14 implementation roadmap

- **Goal:** Execute Phase A of `devdocs/evolution/implementation_roadmap.md` — 9 tactical PRs (A.1–A.9) covering doc drift, test-classpath recovery, DB-decrypt recovery, Season Pass background fix, deep-link coverage expansion, configurable fake failure modes, capped-kill FloatingText suppression, dead-code removal, and an orphan-enum decision. Low risk, high velocity; nothing blocks a later phase but several enable Phase B/C/D to land safely.
- **Preflight:** read `START_HERE`, `STATE`, `CONSTRAINTS`, RUN_LOG tail (Phase 14 entry); `git status` clean on `main`, up to date with origin. `git log -n 10 --oneline` confirmed HEAD at `1609680 docs: add archaeology + evolution deliverables and smoke-test baseline` prior to starting.
- **Execution order (payback-per-day per roadmap §A.10, modified to put A.2 first because A.3/A.5 tests depend on it):** A.2 → A.3 → A.6 → A.5 → A.4 → A.7 → A.8 → A.1 → A.9. Six commits landed on `main`; three had been pushed mid-phase after A.2/A.3/A.6 per a checkpoint request. Final push brought the whole phase up.

### Per-item summary

- **A.2 — junit-vintage-engine on test classpath.** Added `junit-vintage-engine` via `gradle/libs.versions.toml` + `app/build.gradle.kts`. 3 silently-skipped JUnit 4 + Robolectric test classes (`RoomSchemaTest`, `DeepLinkRoutingTest`, `StepWidgetProviderTest`) now discovered; each needed `@Config(sdk = [34], application = android.app.Application::class)` because Robolectric 4.14.1 does not support `compileSdk 36` and the default Hilt-generated Application tries to initialise `DatabaseModule` → SQLCipher native lib (UnsatisfiedLinkError on JVM). Commit `a336bce`. Tests: 412 → 421.
- **A.3 — DB-file wipe on decrypt failure.** `DatabaseKeyManager.kt` now wipes `steps_of_babylon.db` + `-shm`/`-wal` siblings when the encrypted passphrase blob fails to decrypt (e.g. backup-restore to a new device). Prevents crash-on-launch loop. Extracted `wipeDatabaseFile(context)` as `internal` for test visibility because Robolectric's `AndroidKeyStore` shadow throws `NoSuchAlgorithmException` — the keystore-to-wipe coupling is single call-site and covered by code inspection plus on-device smoke. Added `DatabaseKeyManagerTest` (3 Robolectric cases). Commit `51636c0`. Tests: 421 → 424.
- **A.6 — Season Pass flag in background pipeline.** `DailyStepManager.runFollowOnPipeline` now reads `seasonPassActive` + `seasonPassExpiry` from `PlayerRepository` and forwards them to `TrackDailyLogin.checkAndAward`, mirroring `HomeViewModel.init`. Season Pass owners now receive the +10 Gems/day streak bonus when step ingestion happens from `StepSyncWorker` or `StepCounterService` (previously only the foreground path paid out). Added 3 `DailyStepManagerTest` cases: active pass = 11 Gems (1 streak + 10 bonus); no pass = 1 Gem; expired pass falls back to 1 Gem. Commit `35529e8`. Tests: 424 → 427. Tactical patch per roadmap B.4 non-goal — the cleaner home for this logic is the planned FollowOnPipeline extraction (RO-04).
- **A.5 — Deep-link coverage for all argument-free routes.** Added `Screen.fromRoute(name): Screen?` and `val argumentFreeRoutes: Set<String>` in `presentation/navigation/Screen.kt`. Both guarded by `by lazy` to preserve the sealed-class init-order NPE workaround (commit 1872af9). `MainActivity`'s 4-route `when` replaced with `Screen.fromRoute(route)?.takeIf { it.route in Screen.argumentFreeRoutes }?.let { navController.navigate(it.route) }`. Deep-links now reach all 12 argument-free routes (home, workshop, battle, labs, stats, weapons, cards, supplies, economy, missions, settings, store); unknown routes fall through silently (preserves prior behaviour). `DeepLinkRoutingTest` extended from 3 to 17 cases (per-route resolution, null/unknown/case-sensitivity, whitelist contents, round-trip). Commit `5266623`. Tests: 427 → 444.
- **A.4 — Configurable failure modes in Fake billing/ad managers.** `FakeBillingManager` + `FakeRewardAdManager` enhanced with `resultQueue: ArrayDeque<...>` (per-call scripted results, falls back to `nextResult` when empty), configurable `isAdRemoved`/`isSeasonPassActive`/`isAdAvailable`, and append-only `purchases`/`shown` call logs. Added 4 `StoreViewModelTest` cases (Success + Error variants for GemPack and AdRemoval, plus sequential queue replay) and 3 `CardsViewModelTest` cases (AdResult.Rewarded records day-stamp + opens pack, Cancelled and Error leave state unchanged). Commit `dae4fa7`. Tests: 444 → 451. Dropped 2 initially-drafted concurrent-call tests because `StandardTestDispatcher` doesn't exercise in-flight guards cleanly — guards work in practice but aren't deterministically testable without more orchestration (out of scope for A.4).
- **A.7 — Suppress Battle Step FloatingText when daily cap hit.** `GameEngine.onStepReward` callback signature changed from `((Long) -> Unit)?` to `((amount: Long, x: Float, y: Float) -> Unit)?`. `GameEngine.killEnemy` no longer spawns the `+N Step` FloatingText unconditionally; `BattleViewModel.wireStepRewardCallback` now spawns it on the engine's `EffectEngine` only when `AwardBattleSteps` returns credited > 0. Capped kills (2k/day cap hit) silently drop the indicator; frozen HUD counter at `DAILY_BATTLE_STEP_CAP` communicates the gate. Updated 3 existing `BattleViewModelTest` cases for the new signature; added 2 A.7 cases asserting (a) fully capped kill spawns zero FloatingText effects, (b) partial-credit kill still spawns exactly one. Tests use reflection on `EffectEngine.pendingEffects + effects` because `addEffect` is deferred until the next `update()` tick. Commit `61bdd33`. Tests: 451 → 453.
- **A.8 — Delete `PlaceholderScreen` dead code.** Removed `private fun PlaceholderScreen(name: String)` at `MainActivity.kt` (dead since Plan 06 replaced every placeholder route). Removed 4 orphaned imports it was the only consumer of: `androidx.compose.foundation.layout.{Box, fillMaxSize}`, `androidx.compose.material3.Text`, `androidx.compose.ui.Alignment`. `grep PlaceholderScreen app/src` returns empty. Not touched: `Screen.items by lazy` — per cleanup_inventory §A7 it is a documented NPE workaround (commit 1872af9) and must stay. Commit `7e9ea81`. Tests: 453 (unchanged).
- **A.1 — Doc drift sync (schema v8, test count, Battle Step Rewards).** Resolved current-state doc drift without touching historical docs (RUN_LOG, plan-R*, plan-26, external-reviews stay). `docs/database-schema.md`: added `battleStepsEarned` column row + v7→v8 migration entry + "Current schema version: 8". `.kiro/steering/source-files.md`: version 7 → 8, added missing `Migrations.kt` entry. `.kiro/steering/structure.md`: version 7 → 8. `.kiro/steering/lib-room.md`: schema version 2 → 8 (stale since v3), entity/DAO count 9 → 12. `AGENTS.md`: test count 401 → 453, added A.3/A.4/A.6/A.7/A.5 coverage items. Verification: `grep -rn 'version 7|schema v7|Current schema version: 2|Current schema version: 7' docs/ .kiro/ AGENTS.md README.md` (excluding historical files) returns zero matches. Commit `337643a`. Tests: 453 (unchanged — pure doc sweep).
- **A.9 — Delete `SupplyDropTrigger.STEP_BURST`.** Decision: delete (confirmed by @jpawhite). The enum entry was declared with notification copy ("Your pace is impressive! An energy surge flows into your ziggurat.") but never produced by `GenerateSupplyDrop`; zero Room rows ever carried this value as a `.name` string. No test, no doc, no GDD line promised the feature. Commit body preserves the original copy per project rule #2 (historical intent). `grep -r STEP_BURST app/src` returns zero matches. Commit `9f7f1d2`. Tests: 453 (unchanged). Unblocks Phase B.4 `FollowOnPipeline` extraction by simplifying `GenerateSupplyDrop`'s surface.

### Test results
- Start of phase: **412 JVM tests**, all green.
- End of phase: **453 JVM tests**, all green. Delta **+41 tests**. Breakdown: +9 recovered (A.2 Robolectric discovery) + +3 (A.3 DB wipe) + +3 (A.6 Season Pass) + +14 (A.5 new deep-link cases) + +7 (A.4 failure modes) + +2 (A.7 capped-kill) + +0 (A.8/A.1/A.9 no test changes) + +3 net from existing DeepLinkRoutingTest migration.
- Full suite runs: `./run-gradle.sh :app:testDebugUnitTest` green after each of the 9 items.
- Build: `./run-gradle.sh :app:compileDebugKotlin` green after A.8's import cleanup (zero warnings introduced).

### Commits landed on `main`
```
9f7f1d2 chore: delete unused SupplyDropTrigger.STEP_BURST enum entry (A.9)
337643a docs: sync schema to v8 and refresh test-count / coverage (A.1)
7e9ea81 chore: delete PlaceholderScreen dead code (A.8)
61bdd33 fix: suppress Battle Step floating text when daily cap is hit (A.7)
dae4fa7 test: configurable failure modes in Fake billing and ad managers (A.4)
5266623 feat: extend deep-link coverage to all argument-free routes (A.5)
35529e8 fix: pass Season Pass flags from background ingestion pipeline (A.6)
51636c0 fix: wipe SQLCipher DB file on decrypt failure (A.3)
a336bce test: add junit-vintage-engine to discover Robolectric tests (A.2)
```

### Files touched (summary)
- Build: `gradle/libs.versions.toml`, `app/build.gradle.kts`
- Code: `DatabaseKeyManager.kt`, `DailyStepManager.kt`, `Screen.kt`, `MainActivity.kt`, `GameEngine.kt`, `BattleViewModel.kt`, `SupplyDropTrigger.kt`
- Test: `RoomSchemaTest.kt`, `DeepLinkRoutingTest.kt`, `StepWidgetProviderTest.kt`, `DatabaseKeyManagerTest.kt` (new), `DailyStepManagerTest.kt`, `FakeBillingManager.kt`, `FakeRewardAdManager.kt`, `StoreViewModelTest.kt`, `CardsViewModelTest.kt`, `BattleViewModelTest.kt`
- Docs: `docs/database-schema.md`, `.kiro/steering/source-files.md`, `.kiro/steering/structure.md`, `.kiro/steering/lib-room.md`, `AGENTS.md`

### Open questions / blockers
- None blocking. Phase A exit criteria (A.10 in roadmap) all met: 9/9 tactical PRs merged, test count ≥ 418 (actual 453), `grep STEP_BURST app/src` empty (delete route), schema/test-count drift in current-state docs aligned to HEAD.
- Two decisions already consumed by Phase A and not carried forward: (a) A.9 delete-vs-wire STEP_BURST → delete; (b) the concurrent-call test coverage gap flagged in A.4 → deferred as out-of-scope.

### Follow-ups
- **Next phase entry point is a product choice**: Phase B.1 (`TimeProvider` narrow migration, lowest risk, unblocks B.4) **or** Phase C.2 (cosmetic rendering pipeline, most user-facing, ships one cosmetic end-to-end). Both paths end at Plan 31. Phase B is optional for v1.0 per roadmap §1; the release-critical subset is A (done) + C.2 PR 1-2 + C.5 + C.6 + D.
- Three ADRs scheduled by the roadmap remain to be written before their corresponding PRs: ADR-0004 `FollowOnPipeline` (prerequisite of B.4), ADR-0005 Billing SDK (prerequisite of C.5), ADR-0006 Ad SDK (prerequisite of C.6).
- Phase 12 smoke report flagged 6 hidden Robolectric tests; A.2 recovered 9 (all 3 files had 3 tests each, consistent with the sweep grep done in Phase 13 §C1). `cleanup_inventory.md` §C1 can be updated if/when next touched.

### Memory updated
- `STATE.md` ✅ — current objective now "Phase A complete; entry point for Phase B/C/D pending product choice"; priorities, next-actions, and references refreshed.
- `RUN_LOG.md` ✅ — this entry.
- ADR: not warranted for Phase A itself — every item executed a decision already documented in the Phase-14 roadmap. ADR-0004/0005/0006 still owed before their corresponding Phase B/C PRs.

## 2026-05-06 — Standard Analysis Phase 14: refactoring opportunities + implementation roadmap

- Goal: Produce two deliverables per the Phase 14 prompt. (a) `devdocs/evolution/refactoring_opportunities.md` — highest-ROI refactors with current pattern + file paths, proposed abstraction, benefits, effort, risk+mitigation, ROI, first safe step, verification, rollback. (b) `devdocs/evolution/implementation_roadmap.md` — phased plan (A Foundation, B Core Refactoring, C Gap Filling, D Integration & Polish) combining critical cleanup from cleanup inventory, essential unblocking refactors, smoke-report fixes, gap closure, and doc sync; each item carries files / dependencies / success criteria / risk / verification / PR size / rollback / owner role.
- Preflight: read `START_HERE`, `STATE`, `CONSTRAINTS`, head of `RUN_LOG`; `git status` showed only modified STATE/RUN_LOG + untracked `devdocs/` + `smoke_tests/` (expected). Confirmed no prior `refactoring_opportunities.md` or `implementation_roadmap.md` existed via directory listing of `devdocs/evolution/`.
- Inputs used (all cited inline in the deliverables; no new findings introduced per global rule #3): Phase 4 `5_things_or_not.md` (5 PR-sized proposals with full risk/rollback/verify); Phase 8 `architecture_analysis.md` + `module_discovery.md` (structural critique + module boundaries); Phase 10 `gap_analysis.md` (release-gate split, architecture changes §2, tech debt §3, rewrite-rejection §5); Phase 11 `gap_closure_plan.md` (Q1–Q8 quick wins, I1–I7 incremental, M1–M4 major, MR1 cosmetic pipeline, §5 non-goals); Phase 12 `smoke_tests/check_what_is_working/report.md` (412 tests green, junit-vintage gap); Phase 13 `devdocs/archaeology/cleanup_inventory.md` (§A removals, §B quarantines, §C test gaps, §D config, §E docs, §F dynamic-risk register). Cross-checked against `docs/plans/plan-31-play-console.md` and `docs/agent/DECISIONS/ADR-0003-battle-step-rewards.md`.
- Changes made:
  - Created `devdocs/evolution/refactoring_opportunities.md` (~1296 lines, 10 ROI-ranked refactors + deferred appendix + meta cross-refs): TL;DR table; RO-01 TimeProvider narrow migration; RO-02 @Transaction for 5 multi-write sites; RO-03 Resilient BattleViewModel.endRound; RO-04 FollowOnPipeline extraction; RO-05 UpdateMissionProgress use case; RO-06 Screen.fromRoute deep-link coverage; RO-07 Cosmetic rendering pipeline contract; RO-08 Configurable fake failure modes; RO-09 junit-vintage-engine on classpath; RO-10 PreferencesStore consolidation. Each entry has: current pattern with file:line citations, proposed abstraction (with code sketch), benefits, effort (XS/S/M/L scale), risk+mitigation, ROI justification, first safe step, verification strategy, rollback plan, non-goals. Deferred appendix lists 10 lower-ROI items (GameEngine snapshot stack, StepCrossValidator dedup, release/discardEscrow merge, PlayerWallet.cardDust, typed-loadout collapse, Reward sealed unification, multi-module split, typed routes, HealthConnectModule delete, DataStore migration) with source citations + defer rationale.
  - Created `devdocs/evolution/implementation_roadmap.md` (~1319 lines, 4 phases × avg 7 items each): Phase A Foundation (A.1 doc drift, A.2 junit-vintage, A.3 DB decrypt recovery, A.4 fake failure modes, A.5 deep-link coverage, A.6 Season Pass leak, A.7 float-text guard, A.8 PlaceholderScreen, A.9 STEP_BURST decision, plus A.10 rollout order + exit criteria); Phase B Core Refactoring (B.1–B.5 map 1:1 to RO-01 through RO-05, plus B.6 rollout order + exit criteria); Phase C Gap Filling (C.1 anti-cheat visibility, C.2 cosmetic pipeline multi-PR, C.3 Settings rename + privacy link, C.4 ClaimMilestone.Cosmetic fix, C.5 real Billing SDK, C.6 real Ad SDK, C.7 PreferencesStore, C.8 rollout + exit criteria); Phase D Integration & Polish (D.1 privacy URL hosting, D.2 Play Console setup, D.3 store listing + icon, D.4 audio assets, D.5 AAB track promotion, D.6 Firebase pre-launch, D.7 rollout + exit criteria). Each item carries files / dependencies / success criteria / risk label / verification commands / PR size / rollback / suggested owner role. Added aggregate critical path diagram, mermaid dependency graph for release-blocking subset, doc-updates table (11 entries), 17-item Non-goals list lifted from Phase 11 §5 + Phase 14 Part 1 deferred appendix, memory-update checklist, and source-phase cross-reference table.
  - Updated `docs/agent/STATE.md` References list (added two bullets for Phase 14 Part 1 and Part 2) + last-run line.
- Code changes: **none** (evolution/documentation only).
- Commands/tests run: filesystem reads + directory listings only — no build, no tests. Per Phase 10/11 convention, evolution deliverables do not require a green test run because they do not change behaviour.
- Open questions / blockers: none for the deliverables. The two unknowns explicitly surfaced for future decision-making are (a) which cosmetic ships first in C.2 PR 2 (proposed default: jade ziggurat recolour per gap_analysis §5.2); and (b) delete-vs-wire for `SupplyDropTrigger.STEP_BURST` in A.9 (proposed default: delete with documented intent per Phase 11 Q6). Both are flagged as prerequisites inside the roadmap, not new findings.
- Follow-ups created: none new. The roadmap schedules every scheduled follow-up from Phases 4, 10, 11, 13 into A/B/C/D; rejected candidates from Phase 10 §5 and Phase 11 §4 stay in §Non-goals.
- Memory updated: STATE ✅ / RUN_LOG ✅
- ADR: not warranted — no architectural decision was made; this phase ranks already-proposed refactors by ROI and sequences already-scheduled Phase 11 items into release-criticality buckets. New ADRs are named as prerequisites inside the roadmap itself (ADR-0004 FollowOnPipeline, ADR-0005 Billing SDK, ADR-0006 Ad SDK) to be written *before* the corresponding code PRs.

## 2026-05-05 — Standard Analysis Phase 12: baseline smoke tests
- Goal: Establish a baseline smoke-test suite per the Phase 12 prompt. Deliverables: `smoke_tests/check_what_is_working/README.md` (strategy + prerequisites + commands), `smoke_tests/check_what_is_working/test_plan.md` (5 areas × 5 cases = 25 total), `smoke_tests/check_what_is_working/report.md` (results of running the easiest subset). Constraint: reuse the existing JUnit 5 harness — no new framework, no new top-level architecture, no mocks beyond existing fakes.
- Preflight: read `START_HERE`, `STATE`, `CONSTRAINTS`, and RUN_LOG head; `git status` clean apart from modified STATE/RUN_LOG + untracked `devdocs/` (normal per recent archaeology phases). Confirmed no prior `smoke_tests/` directory via `glob '**/smoke*'` and `glob '**/*SmokeTest*'` — both empty.
- Survey: Framework is JUnit 5 Jupiter via `testOptions { unitTests.all { it.useJUnitPlatform() } }` in `app/build.gradle.kts`, complemented by kotlinx-coroutines-test, Mockito-Kotlin, Robolectric, room-testing, androidx.test.core. Existing test tree: 94 Kotlin files across `fakes/` (15), `balance/` (8), `data/sensor/` (5), `data/healthconnect/` (2), `data/local/` (RoomSchemaTest), `data/integration/` (EscrowLifecycleTest), `domain/model/` (9), `domain/usecase/` (~33), `presentation/*/` (≥13 VMs + ux + DeepLinkRoutingTest), `service/` (StepWidgetProviderTest).
- Deliverables:
  - Created `smoke_tests/check_what_is_working/README.md` (152 lines): strategy (reuse/real-components/offline/removable), how the existing harness is organised, prerequisites (JDK 17 / AndroidSDK 36 / no env vars or emulators), commands (full suite, compile-only, lintDebug, assembleDebug, per-area targeted runs), outputs (JUnit HTML/XML, lint HTML/XML, APK path), non-goals (no connectedAndroidTest, no Play Billing/AdMob, no prod creds).
  - Created `smoke_tests/check_what_is_working/test_plan.md` (143 lines): 5 areas × 5 cases = 25 total. Area 1 Build & Packaging (compile Kotlin main/test, KSP, schema export v8 present, assembleDebug APK). Area 2 Domain Formulas (CalculateUpgradeCost, CalculateDamage with seeded Random, CalculateDefense, CheckTierUnlock, AwardBattleSteps). Area 3 Anti-Cheat & Ingestion (StepRateLimiter, StepVelocityAnalyzer, StepCrossValidator, StepIngestion worker/service coord, DailyStepManager). Area 4 Persistence Round-Trip (RoomSchemaTest player profile, RoomSchemaTest daily step record escrow fields, EscrowLifecycleTest, StepWidgetProviderTest, StepIngestionPreferencesTest). Area 5 Presentation (HomeViewModel, WorkshopViewModel, BattleViewModel, MissionsViewModel, DeepLinkRouting). Each case mapped to an existing test file plus a targeted command.
- Checks executed (easiest subset, using `./run-gradle.sh` per project convention):
  1. `./run-gradle.sh testDebugUnitTest --rerun-tasks` → BUILD SUCCESSFUL in 55s, 36 tasks executed. 77 JUnit XML reports, **412 tests, 0 failures, 0 errors, 0 skipped** (confirmed via XML aggregation).
  2. `./run-gradle.sh lintDebug` → BUILD SUCCESSFUL in 51s. Lint XML: **0 errors, 47 Warning entries** (pre-existing advisory warnings; no regressions).
  3. `./run-gradle.sh assembleDebug` → BUILD SUCCESSFUL in 5s (mostly cached from step 1). **`app-debug.apk` 61 MB** at `app/build/outputs/apk/debug/`.
  4. Classpath audit via `./run-gradle.sh :app:dependencies --configuration debugUnitTestRuntimeClasspath | grep -iE 'junit-vintage|junit.*4\.1[0-9]|launcher'` → confirmed `junit-platform-launcher:1.11.4` present and `junit:junit:4.13.2` transitively via `org.robolectric:junit:4.14.1`, but **no `junit-vintage-engine` entry anywhere in the tree**.
- Key finding (documented in report.md as "broken but acceptable"): Under `useJUnitPlatform()` with only the Jupiter engine on the classpath, JUnit 4-style tests annotated with `@RunWith(RobolectricTestRunner::class)` are silently not discovered. Affects `RoomSchemaTest.kt` (3 @Test methods) and `StepWidgetProviderTest.kt` (3 @Test methods) — total 6 tests never run. `EscrowLifecycleTest` is unaffected because it uses `org.junit.jupiter.api.Test`. Per-package test counts confirm this: `data.local` yields 0 test cases despite having 3 in source; `service` yields 0 despite having 3 in source; `data.integration` yields 2 correctly. Sum across all packages = 412, matches STATE.md claim — the claim was always based on what runs, not what exists.
- Why "acceptable" not "blocker": schema correctness is re-validated at build time (`copyRoomSchemas` task + v8 JSON in `app/schemas/`) and at app startup (Room throws `IllegalStateException` on mismatch); widget SharedPreferences is a thin key/value surface exercised in practice; the JUnit 5 `EscrowLifecycleTest` independently covers the more complex escrow lifecycle; expansion of the gap is bounded by the two existing files.
- Non-destructive fix path documented in report.md for a future PR (not this run): add `testRuntimeOnly("org.junit.vintage:junit-vintage-engine:5.11.4")` to `app/build.gradle.kts` — one-line additive change, rollback is a trivial revert, verification is a rerun that should yield 412 → 418 tests all green. Alternative long-term cleanup: port the two files to JUnit 5 + `@ExtendWith(RobolectricExtension::class)` so the suite is stylistically uniform.
- Code changes: **none**. Smoke-test directory is documentation only.
- Git state at end of run: `smoke_tests/check_what_is_working/` untracked (3 new files, 152 + 143 + 186 lines), STATE.md + RUN_LOG.md modified. No other tree changes.
- Open questions / blockers: none. The junit-vintage-engine gap is documented with a fix path; decision deferred to the next code-change PR.
- Follow-ups created: none new. If adopted, the fix path in report.md §"What is broken but acceptable" is one trivial PR.
- Memory updated: STATE ✅ / RUN_LOG ✅
- ADR: not warranted — no architectural decision was made; this phase documents and runs existing smoke surfaces and records one classpath-configuration finding.

## 2026-05-05 — Evolution Phase 10: gap analysis
- Goal: Produce `devdocs/evolution/gap_analysis.md` per Standard Analysis Phase 10 prompt. Compare current state (Phases 1–9 archaeology) to desired state implied by docs, roadmap, ADRs, tests, STATE.md known issues, and direction ("next is Plan 31"). Must include: concepts needing implementation, architecture changes required, tech debt blocking progress, incremental improvements, rewrite justification if any. Must separate known/inferred gaps, avoid inventing requirements not supported by artifacts, propose smallest next step where desired state is unclear.
- Preflight: read `START_HERE`, `STATE`, `CONSTRAINTS`, head of `RUN_LOG`; `git status` modified STATE/RUN_LOG + untracked `devdocs/` (expected). Reviewed existing Phase 1–9 outputs; confirmed no `devdocs/evolution/` directory existed yet.
- Inputs used (all cited inline in the deliverable): Phase 9 concept_mappings (25-concept coverage %, divergence rationale, Appendix A cross-concept risks, Appendix B coverage roll-up); Phase 4 5_things_or_not (5 PR-sized improvement bets with risk/rollback/verify); Phase 5 missing_concepts_list (intentionally deferred vs unintended vs compliance/privacy); Phase 8 architecture_analysis + module_discovery (12 forbidden-direction imports, fat modules, overlapping reward vocabularies); Phase 7 doc-inferred known_requirements (R-STEP/R-AC/R-ECO/R-BAT numbered shalls); master-plan.md current status + Plan 31 task list; STATE.md known issues/priorities; CONSTRAINTS.md invariants; grep of `app/src/main` for TODO/FIXME/XXX/HACK markers (0 matches — no in-code tracking).
- Changes made:
  - Created `devdocs/evolution/gap_analysis.md` (993 lines, 44 KB, 8 top-level sections + appendix): TL;DR (no rewrite needed; Plan 31 is the only release-blocker; cosmetic rendering pipeline is the one structural refactor blocking a shipped-but-disabled feature); §1 Concepts needing implementation (12 entries, known vs inferred labels, smallest next step for each ambiguity); §2 Architecture changes required (6 items: @Transaction, TimeProvider, deep-link coverage, MissionProgressTracker extraction, FollowOnPipeline extraction, plus explicit "non-changes" list); §3 Technical debt blocking progress (12 items ordered by leverage); §4 Incremental improvements (Phase 4 5-item cross-reference + 7 additional PR-sized items including DB-file wipe on decrypt failure, configurable fake failure modes, ClaimMilestone.Cosmetic drop bug); §5 What requires a rewrite (nothing — argues each candidate explicitly; names cosmetic pipeline as "required change short of rewrite" with smallest-step ship-one-cosmetic proposal); §6 Risks and unknowns (7 known risks, 8 explicit unknowns where desired state is ambiguous with smallest clarifying step per item, 4 explicit non-unknowns); §7 Aggregated posture (coverage x release-gate table + critical path to v1.0); appendix relating deliverable to prior phases.
  - Updated `docs/agent/STATE.md` References list + last-run line.
- Code changes: none (archaeology/evolution only).
- Commands/tests run: filesystem reads + grep only — no build. Confirmed 0 TODO/FIXME/XXX/HACK markers in `app/src/main`.
- Open questions / blockers: none for the deliverable. The 8 unknowns enumerated in §6.2 are surfaced with proposed smallest next steps; none require a decision before the next planning session.
- Follow-ups created: none new. The deliverable synthesises existing Phase 4/5/8/9 proposals and aligns them to release-gate / quality-improvement / out-of-scope categories without scheduling new work. If adopted, the critical-path order in §7 is (1) ship one cosmetic end-to-end, (2) Plan 31, (3) optional post-release Phase 4 five-item list.
- Memory updated: STATE ✅ / RUN_LOG ✅
- ADR: not warranted — no architectural decision was made; this phase synthesises already-documented decisions and gaps into a single release-gated view.

## 2026-05-05 — Archaeology Phase 8: architecture reconstruction + module discovery
- Goal: Reconstruct architecture from code per Standard Analysis Phase 8 prompt. Two deliverables: (a) `devdocs/archaeology/architecture_analysis.md` — entry points, data-model inventory, duplicated/overlapping models, contracts, architectural patterns, what doesn't make sense, implied-but-not-enforced invariants; (b) `devdocs/archaeology/module_discovery.md` — natural module boundaries, coupling/cohesion, dependency relationships, shared utilities, cross-cutting concerns, violated boundaries, missing boundaries.
- Preflight: read `START_HERE`, `STATE`, `CONSTRAINTS`, `RUN_LOG` head; `git status` clean except modified STATE/RUN_LOG + untracked `devdocs/` (expected). Reviewed existing Phase 1–7 outputs to avoid duplication.
- Code read (no docs used as primary source per global rule #1): `AndroidManifest.xml`, `StepsOfBabylonApp.kt`, `MainActivity.kt`, all 6 `di/*` modules, `AppDatabase.kt` + 12 entities + 12 DAOs (sampled), `Converters.kt`, `DatabaseKeyManager.kt`, `Migrations.kt`, all 8 `data/repository/*Impl.kt`, `data/sensor/` (5 files), `data/healthconnect/` (4 files), `data/anticheat/AntiCheatPreferences.kt`, 5 SharedPreferences wrapper files in `data/`, both billing/ads stubs, all 36 `domain/model/*.kt` (reviewed en bloc for overlap), key `domain/usecase/*.kt` (AwardBattleSteps, GenerateSupplyDrop, ResolveStats, CalculateDamage, PurchaseUpgrade, TrackDailyLogin, TrackWeeklyChallenge, OpenCardPack, GenerateDailyMissions, StartResearch, PurchaseGemPack, ClaimMilestone, CalculateUpgradeCost), `domain/repository/PlayerRepository.kt` + StepRepository + WorkshopRepository + BillingManager + RewardAdManager, all 12 VMs under `presentation/*/`, `Screen.kt` + `BottomNavBar.kt`, `BattleScreen.kt` + `BattleUiState.kt` + `GameSurfaceView.kt` + `GameLoopThread.kt` + `GameEngine.kt` + `Entity.kt` + `WaveSpawner.kt` + `EnemyScaler.kt` + `CollisionSystem.kt`, all 9 `service/*` files.
- Quantitative validation via grep: 53 `System.currentTimeMillis|LocalDate.now|Instant.now` in 33 files (matches Phase 4); 83 `Random` references in 15 files of which only 7 use cases are seamed; 6 `domain/` + 6 `presentation/` files import `data.local.*Dao` (12 architectural violations); 10 distinct SharedPreferences files with 4 different access patterns; 0 `@Transaction`/`withTransaction` calls in `app/src/main` (no multi-statement atomicity anywhere); daily-mission progress-update logic duplicated across 5 sites (BattleVM, LabsVM, WorkshopVM, MissionsVM, DailyStepManager).
- Key findings documented with file:line citations: `SupplyDropTrigger.STEP_BURST` declared (`SupplyDropTrigger.kt:5`) but never produced; `ClaimMilestone.kt:25` silently drops `MilestoneReward.Cosmetic` despite cosmetics system being wired (3 declared milestone cosmetics never minted, IDs don't match `SEED_COSMETICS`); `MainActivity.PlaceholderScreen:237` is dead code; `Screen.items by lazy` is a documented workaround for sealed-class init-order NPE (commit `1872af9`); `StepRepositoryImpl.releaseEscrow`/`discardEscrow` are line-for-line identical delegations to `clearEscrow`, semantic difference only in caller; `CosmeticEntity` has double key (`id` autoGenerate + `cosmeticId` String); `GameSurfaceView.kt:26` bypasses `SoundPreferences` to read `sound_prefs` inline; `Currency`/`SupplyDropReward`/`MilestoneReward` are three overlapping reward vocabularies; `PlayerWallet` omits `cardDust`; `CardLoadout` and `UltimateWeaponLoadout` are near-identical (neither exercised at runtime); `GameEngine` has two pre-stat snapshots (`preOverdriveStats`, `preGoldenStats`) with implicit restore order; `StepCrossValidator` Level 0/1 branches duplicate ~20 lines (only `MAX_ESCROW_SYNCS` differs); loadout max-3 enforced only in VMs (`CardsViewModel.kt:114`, `UltimateWeaponViewModel.kt:82`), no DAO guard; currency non-negative clamp lives in SQL (`MAX(0, col + :delta)`) not in Kotlin interface; `DailyStepManager.runFollowOnPipeline` has 4 pokemon-catch blocks; `DailyStepManager` has 12 constructor deps and constructs use cases inline; `TrackDailyLogin` call path from `DailyStepManager` never passes Season Pass flags, so walking-streak Gems lose +10 Gems bonus; `HealthConnectModule` is an empty organisational placeholder.
- Changes made:
  - Created `devdocs/archaeology/architecture_analysis.md` (∼650 lines, 8 top-level sections): TL;DR + sources, entry points & flows, data models (persistence/domain/UI state/commands & events), duplicated/overlapping models (6 categories with file pointers), contracts (repository interfaces, use cases, Hilt modules, Android framework contracts, battle-layer callback contracts, notification managers), architectural patterns (11 patterns: Clean Architecture partial, MVVM with StateFlow, Repository with Flow, Enum-as-balance-sheet, Seeded randomness partial, Default-parameter time sparse, Fixed-timestep game loop, Offline-first, Read-modify-write via `first()`, Stub-then-swap, Plain-Kotlin use cases), 13 "what doesn't make sense" items, 9 implied-but-not-enforced invariants, prioritised summary.
  - Created `devdocs/archaeology/module_discovery.md` (∼800 lines, 8 top-level sections): 16 natural module boundaries (M1 core-domain, M2 core-usecases, M3 persistence, M4 repositories, M5 sensor, M6 healthconnect, M7 anticheat, M8 prefs — virtual, M9 billing-ads, M10 service, M11 navigation, M12 screens, M13 battle-engine, M14 audio, M15 theme, M16 di), coupling/cohesion summary table, "fat" modules (DailyStepManager, GameEngine, HomeViewModel), "thin" modules (HealthConnectModule, theme), cross-screen coupling, fan-out of PlayerRepository (23 methods, universal dependency), ASCII dependency graph, zero package-import cycles confirmed, table of 12 forbidden-direction imports with file:line, shared utilities inventory, 10 cross-cutting concerns (time, randomness, prefs, notifications, anti-cheat, currency clamp, logging, coroutine scoping, error handling, HC availability), 8 boundary violations with file pointers, 10 missing boundaries that would help, prioritised summary (6 payback items ordered by effort/payback).
  - Updated `docs/agent/STATE.md` References list + last-run line.
- Code changes: none (archaeology only).
- Commands/tests run: filesystem reads + grep only — no build.
- Open questions: none. Phase 8 deliverables are strictly documentation. The 12 "forbidden-direction imports" are enumerated for future refactoring; none are newly introduced and all are already documented in Phase 4 + the foundations docs as tolerated gaps. The proposed `TimeProvider`, `RandomSource`, `MissionProgressTracker`, `FollowOnPipeline`, `Reward` sealed hierarchy, and `PreferencesStore` consolidations remain unscheduled — they are cross-references to Phase 4 item 1, 4, 5 and to new proposals here.
- Follow-ups created: none new. Phase 8 synthesises existing findings into two distinct views (architectural critique + module boundaries); it does not schedule work. If adopted, the prioritised list in `module_discovery.md` §8 gives a natural ordering.
- Memory updated: STATE ✅ / RUN_LOG ✅
- ADR: not warranted — no architectural decision was made; this phase describes decisions already evident in the code plus the gaps around them.

## 2026-05-05 — Archaeology Phase 6: code-inferred foundations docs
- Goal: Extract `project_description`, `philosophy`, and `known_requirements` foundations docs under `devdocs/archaeology/foundations/`, grounded in the actual codebase, synthesising (not duplicating) Phases 1–5.
- Input review: read `small_summary.md`, `intro2codebase.md`, `intro2deployment.md`, all 4 Phase 5 concept docs (`technical`, `design`, `business`, `missing`), plus memory spine. Spot-checked code: `AndroidManifest.xml`, `StepsOfBabylonApp.kt`, `AppDatabase.kt`, `DatabaseModule.kt`, `DatabaseKeyManager.kt`, `Migrations.kt`, `AwardBattleSteps.kt`, `StepCrossValidator.kt`, `DailyStepManager.kt`, `StepCounterService.kt`, `GameLoopThread.kt`, `StubBillingManager.kt`, `StubRewardAdManager.kt`, `Currency.kt`, `UpgradeType.kt`, `PlayerProfileEntity.kt`, `BillingProduct.kt`, `app/build.gradle.kts`, `app/proguard-rules.pro`, `network_security_config.xml`.
- Changes made:
  - Created `devdocs/archaeology/foundations/project_description.md` (334 lines): what the system actually does (5 core behaviours), current use cases, actor types (player + platform services + dev/tester; no operator/SRE/account role), actual problems solved (walk-gated economy, reliable background counting, client-only anti-cheat, inclusive-fitness via exercise minutes, encrypted offline persistence, 60 UPS SurfaceView game loop embedded in Compose), runtime/delivery model (single AAB to Play Store, user-initiated updates, no backend, no CI, v7→v8 migrations), and explicit unknowns.
  - Created `devdocs/archaeology/foundations/philosophy.md` (523 lines): observed design principles (Steps-are-sacred, enum-as-balance-sheet, offline-first, Room-as-truth, encrypted-by-default, domain-is-pure-Kotlin, geometric cost curves, fail-fast-schema vs fail-soft-pipeline, seamed randomness, default-param time, one Activity + one SurfaceView, stub-then-swap SDKs, `@Volatile` polling across threads); consistent patterns (coding, architectural, testing, operational, deployment); architectural decisions evident in structure; deliberate tradeoffs (privacy > observability, offline fidelity > portability, client-side anti-cheat, build-time balance > live tuning, stub SDKs > feature flags); what philosophy doesn't commit to; PR-heuristic checklist.
  - Created `devdocs/archaeology/foundations/known_requirements.md` (685 lines): 18 sections covering platform, runtime/reliability, privacy/security, anti-cheat (with numeric thresholds: 200/min rate, 50k/day ceiling, 2k/day battle-step cap, 20% HC discrepancy), offline, latency budgets (16.67ms frame, 200ms UI poll, 30s/60s throttles, 15min worker), scalability (single-user/device/process), reproducibility/testability, compatibility, security consolidation, observability (deliberately minimal), deployment, integration (HC optional with graceful degrade; sensor implicit), privacy-by-default, concurrency, compliance/legal, explicit non-requirements (no accounts/server/multiplayer/leaderboards/CI/i18n/a11y-pass), and explicit unknowns (battery budget, real SDKs, final audio, cosmetic visual pipeline, localisation, retention policy, PlaceholderScreen intent).
  - Updated `docs/agent/STATE.md` reference list + last-run line.
- Code changes: none (archaeology only).
- Commands/tests run: filesystem reads + grep only — no build.
- Open questions: none. Phase 6 deliverables are documentation; they do not introduce code changes. Several explicit unknowns are enumerated inside `known_requirements.md` §18 rather than left implicit.
- Follow-ups created: none new. The foundations docs cross-reference Phase 4 proposals (`5_things_or_not.md`) and Phase 5 concept inventories without scheduling new work.
- Memory updated: STATE ✅ / RUN_LOG ✅
- ADR: not warranted — no architectural decision was made; this phase describes decisions already in code.

## 2026-05-05 — Archaeology Phase 4: "5 Things" improvement list
- Goal: Synthesise the 13 Phase 3 traces into a prioritised list of 5 impactful improvements, with code citations, historical rationale, and PR-sized first steps.
- Input review: read `devdocs/archaeology/small_summary.md`, `intro2codebase.md`, `intro2deployment.md`, and all 13 `traces/trace_*.md` "Feels Incomplete / Vulnerable / Bad Design" sections.
- Cross-cutting findings: zero `@Transaction` or `withTransaction` uses in `app/src/main`; 53 direct `System.currentTimeMillis()`/`LocalDate.now()` calls across 33 files; `DailyStepManager` has 11 constructor parameters; `BattleViewModel.endRound` not invoked on `onCleared` so mid-battle deep-links lose the round.
- Changes made:
  - Created `devdocs/archaeology/5_things_or_not.md` (683 lines): TimeProvider abstraction, Room @Transaction for multi-writes, robust round-end cascade against navigation, extract FollowOnPipeline from DailyStepManager, surface anti-cheat effects on Stats screen. Each item has file+line citations, risk assessment, rollback, and verification steps per global rule #5.
- Code changes: none (archaeology only).
- Commands/tests run: grep/code-search only — no build.
- Open questions: none; this is a proposal document. Each item is independently actionable; items 1 and 2 compose cleanly (a `TimeProvider` + `@Transaction` PR together would cover items 1 and 2 in a single small PR).
- Follow-ups created: 5 proposals documented; none scheduled. If adopted, each is a separate PR; item 1 blocks nothing; item 2 is a dependency for item 3's rollback plan.
- Memory updated: STATE ✅ / RUN_LOG ✅

## 2026-03-04 — Project Memory System Setup
- Goal: Implement repo-backed project memory system for Kiro CLI default agent.
- Plan: Create steering files (10-project-memory.md, 11-agent-protocol.md), living memory docs (START_HERE, STATE, CONSTRAINTS, RUN_LOG, ADR template), update AGENTS.md.
- Changes made:
  - Created `.kiro/steering/10-project-memory.md` (always-on memory source declarations)
  - Created `.kiro/steering/11-agent-protocol.md` (preflight + end-of-run protocol)
  - Created `docs/agent/START_HERE.md` (agent contract)
  - Created `docs/agent/STATE.md` (current project snapshot)
  - Created `docs/agent/CONSTRAINTS.md` (invariants and rules)
  - Created `docs/agent/RUN_LOG.md` (this file)
  - Created `docs/agent/DECISIONS/ADR-0001-template.md`
  - Created `docs/agent/state.json`
  - Updated `AGENTS.md` with memory spine section
- Commands/tests run: N/A (documentation-only change)
- Open questions / blockers: None.
- Follow-ups created: None.
- Memory updated: STATE ✅ / RUN_LOG ✅

## 2026-03-04 — Plan 04: Step Counter Service
- Goal: Implement background step counting with foreground service, anti-cheat, and WorkManager sync.
- Changes made:
  - Added `hilt-work:1.3.0` and `hilt-androidx-compiler:1.3.0` to version catalog + build.gradle.kts
  - Created `data/sensor/StepRateLimiter.kt` — rolling 1-min window, 200/min cap (250 burst)
  - Created `data/sensor/DailyStepManager.kt` — orchestrates rate limit → 50k ceiling → Room persist
  - Created `data/sensor/StepSensorDataSource.kt` — TYPE_STEP_COUNTER wrapper, emits deltas via callbackFlow
  - Created `service/StepNotificationManager.kt` — notification channel + builder, 30s throttle
  - Created `service/StepCounterService.kt` — foreground service (health type), START_STICKY
  - Created `service/BootReceiver.kt` — BOOT_COMPLETED → restart service
  - Created `service/StepSyncWorker.kt` — @HiltWorker CoroutineWorker, 15-min periodic catch-up
  - Created `service/StepSyncScheduler.kt` — enqueues periodic work request
  - Created `di/StepModule.kt` — provides SensorManager via Hilt
  - Updated `StepsOfBabylonApp.kt` — implements Configuration.Provider, injects HiltWorkerFactory
  - Updated `AndroidManifest.xml` — 5 permissions, service + receiver declarations, disabled default WorkManager init
  - Updated `MainActivity.kt` — runtime permission requests for ACTIVITY_RECOGNITION + POST_NOTIFICATIONS
  - Added `getDailyRecord()` to StepRepository interface + StepRepositoryImpl
- Commands/tests run: `./run-gradle.sh assembleDebug` — BUILD SUCCESSFUL, zero warnings
- Open questions / blockers: None.
- Follow-ups created:
  - Replace placeholder notification icon with custom app icon (when assets exist)
  - Notification balance could show live wallet balance via Flow observation
- Memory updated: STATE ✅ / RUN_LOG ✅

## 2026-03-04 — Plan 05: Health Connect Integration
- Goal: Implement Health Connect (replacing deprecated Google Fit) for step cross-validation, gap-filling, and Activity Minute Parity.
- Key decision: ADR-worthy — used Health Connect instead of Google Fit (Google Fit APIs deprecated, shutting down 2026). See docs/agent/DECISIONS/ for ADR.
- Changes made:
  - Added `health-connect-client:1.2.0-alpha02` to version catalog + build.gradle.kts
  - Created `data/healthconnect/HealthConnectClientWrapper.kt` — client setup, availability, permissions
  - Created `data/healthconnect/HealthConnectStepReader.kt` — aggregated step reading
  - Created `data/healthconnect/StepCrossValidator.kt` — escrow system (>20% discrepancy, 3-sync lifecycle)
  - Created `data/healthconnect/StepGapFiller.kt` — recovers missed steps from HC
  - Created `data/healthconnect/ExerciseSessionReader.kt` — reads exercise sessions
  - Created `data/healthconnect/ActivityMinuteConverter.kt` — conversion table with per-activity caps + double-counting prevention
  - Created `di/HealthConnectModule.kt` — organizational Hilt module
  - Created `presentation/HealthConnectPermissionActivity.kt` — privacy policy stub
  - Updated `DailyStepRecordEntity.kt` — renamed googleFitSteps→healthConnectSteps, added escrowSteps + escrowSyncCount
  - Updated `DailyStepSummary.kt` — matching field changes
  - Updated `StepRepository.kt` — renamed method, added escrow methods
  - Updated `StepRepositoryImpl.kt` — implemented escrow methods
  - Updated `DailyStepDao.kt` — added clearEscrow query
  - Updated `DailyStepManager.kt` — added recordActivityMinutes()
  - Updated `StepSyncWorker.kt` — integrated HC gap-fill, cross-validation, activity minutes
  - Updated `MainActivity.kt` — HC permission request via PermissionController
  - Updated `AndroidManifest.xml` — HC permissions, privacy policy activity + activity-alias
- Commands/tests run: `./run-gradle.sh assembleDebug` — BUILD SUCCESSFUL, zero warnings
- Open questions / blockers:
  - StepSyncWorker passes empty sensorStepsPerMinute map to ActivityMinuteConverter (full per-minute tracking deferred)
- Follow-ups created:
  - Update GDD/step-tracking docs to reference Health Connect instead of Google Fit
  - Create ADR for Google Fit → Health Connect decision
- Memory updated: STATE ✅ / RUN_LOG ✅

## 2026-03-04 — Plan 06: Home Screen & Navigation
- Goal: Build Compose navigation graph, bottom nav bar, and real Home dashboard with live data.
- Changes made:
  - Added `hilt-navigation-compose:1.3.0` and `compose-material-icons-core` to version catalog + build.gradle.kts
  - Created `presentation/navigation/Screen.kt` — sealed class with 5 routes (Home, Workshop, Battle, Labs, Stats)
  - Created `presentation/navigation/BottomNavBar.kt` — NavigationBar with 5 items, route highlighting
  - Created `presentation/home/HomeUiState.kt` — UI state data class
  - Created `presentation/home/HomeViewModel.kt` — @HiltViewModel combining PlayerRepository + StepRepository flows
  - Rewrote `presentation/home/HomeScreen.kt` — real dashboard (tier/biome header, step card, currency row, best wave, battle button)
  - Updated `presentation/MainActivity.kt` — Scaffold + NavHost + BottomNavBar, preserved permission logic
  - HomeViewModel calls `ensureProfileExists()` in init to seed default profile
- Commands/tests run: `./run-gradle.sh assembleDebug` — BUILD SUCCESSFUL, zero warnings
- Open questions / blockers: None.
- Follow-ups created: None.
- Memory updated: STATE ✅ / RUN_LOG ✅

## 2026-03-04 — Plan 07: Workshop Screen & Upgrades
- Goal: Build Workshop screen with 3-tab layout, 23 upgrades, tap-to-buy, Quick Invest.
- Changes made:
  - Created `domain/usecase/PurchaseUpgrade.kt` — checks affordability, deducts Steps, increments level
  - Created `domain/usecase/QuickInvest.kt` — recommends cheapest affordable upgrade
  - Created `presentation/workshop/WorkshopUiState.kt` — UpgradeDisplayInfo + WorkshopUiState
  - Created `presentation/workshop/WorkshopViewModel.kt` — @HiltViewModel, combines upgrades + wallet flows
  - Created `presentation/workshop/UpgradeCard.kt` — reusable card with 3 visual states
  - Created `presentation/workshop/WorkshopScreen.kt` — PrimaryTabRow, LazyColumn, Quick Invest FAB
  - Updated `presentation/home/HomeViewModel.kt` — added workshopRepository.ensureUpgradesExist() in init
  - Updated `presentation/MainActivity.kt` — replaced Workshop placeholder with WorkshopScreen()
- Commands/tests run: `./run-gradle.sh assembleDebug` — BUILD SUCCESSFUL, zero warnings
- Open questions / blockers: None.
- Follow-ups created: None.
- Memory updated: STATE ✅ / RUN_LOG ✅

## 2026-03-04 — Plan 08: Battle Renderer — Game Loop & Ziggurat
- Goal: Build custom SurfaceView battle renderer with game loop, ziggurat entity, projectiles, health bar, and Compose overlay.
- Decisions made:
  - (b) ZigguratBaseStats as domain/model object — proper constants for Plan 10's ResolveStats to consume.
  - (a) Simple geometric ziggurat — 5 stacked rectangles in sandstone tones.
  - (a) Hidden bottom nav during battle — full-screen immersive.
- Changes made:
  - Created `domain/model/ZigguratBaseStats.kt` — base stat constants (HP, damage, attack speed, range, regen, knockback, projectile speed)
  - Created `presentation/battle/engine/Entity.kt` — abstract base class (x, y, width, height, isAlive, update, render)
  - Created `presentation/battle/engine/GameEngine.kt` — entity list, update/render dispatch, HealthBarRenderer integration
  - Created `presentation/battle/entities/ZigguratEntity.kt` — 5-layer ziggurat, auto-fire via callback, HP tracking
  - Created `presentation/battle/entities/ProjectileEntity.kt` — moves toward target, self-destructs on arrival
  - Created `presentation/battle/ui/HealthBarRenderer.kt` — green/yellow/red HP bar with numeric text
  - Created `presentation/battle/GameLoopThread.kt` — fixed timestep (60 UPS), accumulator pattern, speed multiplier, FPS counter
  - Created `presentation/battle/GameSurfaceView.kt` — SurfaceHolder.Callback, manages game loop thread lifecycle
  - Created `presentation/battle/BattleUiState.kt` — UI state for Compose overlay
  - Created `presentation/battle/BattleViewModel.kt` — @HiltViewModel, loads tier, exposes state + BattleEvent
  - Created `presentation/battle/BattleScreen.kt` — Compose wrapper (AndroidView + overlay: wave counter, speed controls, pause, exit)
  - Updated `presentation/MainActivity.kt` — BattleScreen replaces placeholder, bottom nav hidden on Battle route
- Commands/tests run: `./run-gradle.sh assembleDebug` — BUILD SUCCESSFUL, zero warnings
- Open questions / blockers:
  - Ziggurat fires at fixed test target (top-center) — Plan 09 replaces with nearest enemy
  - Workshop bonuses not applied to base stats yet — Plan 10 adds ResolveStats
- Follow-ups created: None.
- Memory updated: STATE ✅ / RUN_LOG ✅

## 2026-03-05 — Plan 09: Battle System — Enemies & Waves
- Goal: Add 6 enemy types, wave spawning, enemy scaling, collision, cash, nearest-enemy targeting, round end.
- Decisions made:
  - (b) Enemies spawn from top + left + right edges (converging on ziggurat)
  - (b) Fix EnemyType enum to match battle-formulas.md (FAST dmg 0.5→0.7, RANGED spd 1.0→0.8 + dmg 1.5→1.2, BOSS hp 10→20)
  - (b) Wave scaling: 1.05^wave (gentler curve, tunable in Plan 28)
- Changes made:
  - Updated `domain/model/EnemyType.kt` — corrected multipliers to match balance spec
  - Created `presentation/battle/engine/EnemyScaler.kt` — wave-based stat scaling (1.05^wave), cash rewards per type
  - Created `presentation/battle/entities/EnemyEntity.kt` — 6 types, movement, melee/ranged attack, distinct shapes/colors, mini HP bar
  - Created `presentation/battle/entities/EnemyProjectileEntity.kt` — red projectiles for Ranged enemies
  - Created `presentation/battle/engine/WaveSpawner.kt` — 26s spawn + 9s cooldown, enemy composition by wave, boss every 10 waves
  - Created `presentation/battle/engine/CollisionSystem.kt` — projectile↔enemy and enemy projectile↔ziggurat collision
  - Updated `presentation/battle/engine/GameEngine.kt` — integrated WaveSpawner, CollisionSystem, cash tracking, Scatter splitting, round end detection, findNearestEnemy()
  - Updated `presentation/battle/entities/ZigguratEntity.kt` — targets nearest enemy via lambda, only fires when enemy in range
  - Updated `presentation/battle/BattleUiState.kt` — added enemyCount, wavePhase
  - Updated `presentation/battle/BattleViewModel.kt` — polls engine state every 200ms, detects roundOver
  - Updated `presentation/battle/BattleScreen.kt` — shows enemy count, wave phase, cash in overlay
- Commands/tests run: `./run-gradle.sh assembleDebug` — BUILD SUCCESSFUL, zero warnings
- Open questions / blockers:
  - Cash economy simplified (base per type) — Plan 11 adds full formula
  - Workshop bonuses not applied to stats — Plan 10 adds ResolveStats
- Follow-ups created: None.
- Memory updated: STATE ✅ / RUN_LOG ✅

## 2026-03-05 — Plan 10: Battle System — Stats & Combat
- Goal: Stats resolution engine + core combat mechanics (crit, knockback, lifesteal, thorn, regen, death defy, defense).
- Decisions made:
  - (b) Core stats + simple mechanics now; Orbs/Multishot/Bounce deferred
  - (a) GameEngine accepts ResolvedStats in init() — ViewModel resolves on round start
  - (a) Centralized applyDamageToZiggurat() for all damage sources
- Changes made:
  - Created `domain/model/ResolvedStats.kt` — all computed combat stats data class
  - Created `domain/usecase/ResolveStats.kt` — workshop + in-round levels → ResolvedStats
  - Created `domain/usecase/CalculateDamage.kt` — raw damage + crit roll + damage/meter bonus
  - Created `domain/usecase/CalculateDefense.kt` — damage reduction (cap 75%) + flat block
  - Updated `presentation/battle/entities/ZigguratEntity.kt` — uses ResolvedStats for HP, attack speed, range, health regen
  - Updated `presentation/battle/entities/EnemyEntity.kt` — added applyKnockback()
  - Updated `presentation/battle/engine/CollisionSystem.kt` — delegates to engine callbacks
  - Updated `presentation/battle/engine/GameEngine.kt` — centralized damage pipeline (defense → death defy → thorn), knockback, lifesteal
  - Updated `presentation/battle/GameSurfaceView.kt` — accepts ResolvedStats, re-inits engine
  - Updated `presentation/battle/BattleViewModel.kt` — resolves stats from workshop on init
  - Updated `presentation/battle/BattleScreen.kt` — passes resolved stats to surface view
- Commands/tests run: `./run-gradle.sh assembleDebug` — BUILD SUCCESSFUL, zero warnings
- Open questions / blockers:
  - Orbs, Multishot, Bounce Shot computed in ResolvedStats but not wired to gameplay
  - In-round upgrades (Plan 11) will re-resolve stats on purchase
- Memory updated: STATE ✅ / RUN_LOG ✅

## 2026-03-06 — Plan 11: In-Round Upgrades & Cash Economy
- Goal: Full cash economy + in-round upgrade menu with purchase flow.
- Decisions made:
  - (b) Cash economy + upgrade menu only; Orbs/Multishot/Bounce deferred to mini-plan 10b
  - (a) Upgrade menu always accessible via toggle button
  - (a) onWaveComplete callback added to WaveSpawner
- Changes made:
  - Updated `presentation/battle/engine/WaveSpawner.kt` — added onWaveComplete callback, fires on SPAWNING→COOLDOWN
  - Updated `presentation/battle/engine/GameEngine.kt` — full cash formula (tier × cashBonus), wave cash + interest, spendCash(), updateZigguratStats()
  - Updated `presentation/battle/BattleUiState.kt` — added showUpgradeMenu, inRoundLevels, lastPurchaseFree
  - Updated `presentation/battle/BattleViewModel.kt` — purchase flow, in-round levels, re-resolve stats, free upgrade chance, tier tracking
  - Updated `presentation/battle/GameSurfaceView.kt` — configure() accepts stats + tier + workshopLevels
  - Created `presentation/battle/ui/InRoundUpgradeMenu.kt` — 3-tab Compose overlay, upgrade list, purchase buttons
  - Updated `presentation/battle/BattleScreen.kt` — upgrade toggle button, InRoundUpgradeMenu overlay
  - Created `docs/plans/plan-10b-advanced-combat.md` — mini-plan for Orbs, Multishot, Bounce Shot
  - Updated `docs/plans/plan-11-in-round-upgrades.md` — removed deferred section
  - Updated `docs/plans/master-plan.md` — added Plan 10b entry
- Commands/tests run: `./run-gradle.sh assembleDebug` — BUILD SUCCESSFUL, zero warnings
- Open questions / blockers:
  - Orbs/Multishot/Bounce in Plan 10b (ready to implement anytime)
- Memory updated: STATE ✅ / RUN_LOG ✅

## 2026-03-06 — Plan 10b: Advanced Combat (Orbs, Multishot, Bounce Shot)
- Goal: Wire the three deferred combat mechanics to gameplay.
- Decisions made:
  - (a) Orbs: damage on contact with 0.5s per-enemy cooldown, 50% resolved damage
  - (a) Bounce: spawn new ProjectileEntity with bouncesRemaining, reuse collision pipeline
  - (a) Multishot: findNearestEnemies(n) lambda, fire one projectile per target
- Changes made:
  - Updated `presentation/battle/entities/ProjectileEntity.kt` — added bouncesRemaining + hitEnemies
  - Created `presentation/battle/entities/OrbEntity.kt` — orbiting entity, per-enemy cooldown, cyan rendering
  - Updated `presentation/battle/entities/ZigguratEntity.kt` — multishot via findNearestEnemies(n) lambda
  - Updated `presentation/battle/engine/GameEngine.kt` — findNearestEnemies(), bounce logic in onProjectileHitEnemy, orb spawn/despawn, onOrbHitEnemy
- Commands/tests run: `./run-gradle.sh assembleDebug` — BUILD SUCCESSFUL, zero warnings
- Open questions / blockers: None.
- Memory updated: STATE ✅ / RUN_LOG ✅

## 2026-03-06 — Documentation Sweep
- Goal: Full project documentation audit — find and fix stale/incorrect references.
- Changes made:
  - Updated `docs/StepsOfBabylon_GDD.md` — replaced all Google Fit references with Health Connect (§2.1, §11.1–§11.4, §15.1, §17, §19). Fixed anti-cheat rate limit from ">500 steps/min" to "200/min (250 burst)".
  - Updated `docs/database-schema.md` — DailyStepRecord: `googleFitSteps` → `healthConnectSteps`, added `escrowSteps` and `escrowSyncCount` columns.
  - Updated `docs/architecture.md` — layer diagram "Google Fit" → "Health Connect", DI section now lists actual modules (StepModule, HealthConnectModule) instead of "Future modules".
  - Rewrote `docs/plans/plan-05-google-fit.md` — body now reflects actual Health Connect implementation with correct file paths and class names.
  - Updated `docs/plans/plan-25-anti-cheat.md` — all Google Fit references → Health Connect, corrected package paths (`data/healthconnect/` not `data/googlefit/`).
  - Updated `docs/plans/plan-30-release.md` — ProGuard keep rules, privacy policy, and checklist updated for Health Connect.
  - Updated `docs/plans/master-plan.md` — Plan 10 description corrected (orbs/bounce were deferred to 10b).
  - Updated `docs/agent/STATE.md` — removed stale "Google Fit references" known issue.
- Remaining cosmetic issues (not fixed — completed plans, code is correct):
  - `docs/plans/plan-02-database.md` and `plan-03-repositories.md` still reference `googleFitSteps` column name (these are historical plan docs; actual code uses `healthConnectSteps`)
  - `docs/agent/RUN_LOG.md` references are historical records (correct to leave as-is)
  - `docs/agent/DECISIONS/ADR-0002-health-connect.md` references are contextual (explaining the decision)
  - `docs/agent/state.json` is an orphaned file from earlier approach (harmless)
  - `docs/temp/` contains a reference playbook from setup (harmless)
- Commands/tests run: N/A (documentation-only changes)
- Memory updated: STATE ✅ / RUN_LOG ✅

## 2026-03-06 — Plan 12: Round Lifecycle & Post-Round
- Goal: Full round lifecycle with post-round summary, best wave persistence, pause overlay, auto-pause.
- Decisions made:
  - (b) Post-round as overlay within Battle route (avoids ViewModel re-creation)
  - (a) Engine owns totalEnemiesKilled + elapsedTimeSeconds (single source of truth)
  - (a) Quit Round shows summary and saves best wave (player earned that progress)
- Changes made:
  - Updated `presentation/battle/engine/GameEngine.kt` — added totalEnemiesKilled, elapsedTimeSeconds, totalCashEarned tracking; made roundOver publicly settable for quit flow
  - Created `domain/usecase/UpdateBestWave.kt` — compares wave to stored best, persists if new record, returns Result(isNewRecord, previousBest)
  - Updated `presentation/battle/BattleUiState.kt` — added RoundEndState data class and roundEndState field
  - Rewrote `presentation/battle/BattleViewModel.kt` — endRound(), quitRound(), playAgain(), pause(); removed BattleEvent; tracks surfaceView reference for play-again re-init
  - Created `presentation/battle/ui/PostRoundOverlay.kt` — wave reached, enemies killed, cash earned, time survived, new record banner, Play Again / Return to Workshop buttons
  - Created `presentation/battle/ui/PauseOverlay.kt` — Resume / Quit Round buttons
  - Rewrote `presentation/battle/BattleScreen.kt` — integrated overlays, auto-pause via LifecycleEventObserver, exit button calls quitRound(), controls hidden when round over
- Commands/tests run: `./run-gradle.sh assembleDebug` — BUILD SUCCESSFUL, zero warnings
- Open questions / blockers: None.
- Memory updated: STATE ✅ / RUN_LOG ✅

## 2026-03-06 — Domain Layer Unit Testing (Regression Safety Net)
- Goal: Add pure JVM unit tests covering all domain use cases, key domain models, and critical pure-Kotlin logic outside domain.
- Decisions made:
  - JVM-only tests (no instrumented/emulator tests) for speed and simplicity
  - JUnit 5 + kotlinx-coroutines-test as test framework (no Turbine needed yet)
  - Injected `Random` into `CalculateDamage` for deterministic crit testing (default param, zero caller impact)
  - Created fake repositories (FakePlayerRepository, FakeWorkshopRepository) for use case tests
- Changes made:
  - Updated `gradle/libs.versions.toml` — added junit5=5.11.4, coroutinesTest=1.10.1, test library entries
  - Updated `app/build.gradle.kts` — added testImplementation deps, JUnit Platform config, platform launcher
  - Refactored `domain/usecase/CalculateDamage.kt` — injectable Random parameter
  - Created `test/fakes/FakePlayerRepository.kt` — in-memory MutableStateFlow-backed fake
  - Created `test/fakes/FakeWorkshopRepository.kt` — in-memory MutableStateFlow-backed fake
  - Created 15 test classes (80 tests total):
    - `domain/usecase/`: CalculateUpgradeCostTest, CanAffordUpgradeTest, QuickInvestTest, PurchaseUpgradeTest, UpdateBestWaveTest, ResolveStatsTest, CalculateDamageTest, CalculateDefenseTest
    - `domain/model/`: TierConfigTest, BiomeTest, CardLoadoutTest, UltimateWeaponLoadoutTest, UpgradeTypeTest, EnemyTypeTest
    - `presentation/battle/engine/`: EnemyScalerTest
    - `data/sensor/`: StepRateLimiterTest
- Commands/tests run: `./run-gradle.sh testDebugUnitTest` — BUILD SUCCESSFUL, 80 tests, 0 failures
- Open questions / blockers: None. ViewModel tests and instrumented tests deferred to Plan 29.
- Memory updated: STATE ✅ / RUN_LOG ✅

## 2026-03-06 — Plan 13: Tier System & Progression
- Goal: Tier unlock logic, tier selector UI, battle conditions at Tier 6+, post-round tier unlock notification.
- Decisions made:
  - (a) Armor as hit counter — enemies block first N hits, then take full damage. Punishes fast-attack/low-damage builds.
  - (a) Minimal tier selector — horizontal chip row on home screen, not a dedicated screen.
  - (b) Notify only on unlock — player stays on current tier, chooses when to advance via selector.
  - Added `highestUnlockedTier` as separate field from `currentTier` (play tier) to support tier selection.
  - DB version bumped to 2 with destructive fallback (dev phase — proper migration before release).
- Changes made:
  - Created `domain/usecase/CheckTierUnlock.kt` — iterates tiers, checks wave milestones against bestWavePerTier
  - Created `domain/model/BattleConditionEffects.kt` — pre-computes numeric modifiers from tier battle conditions
  - Created `presentation/home/TierSelector.kt` — horizontal tier chip row with lock/unlock states, condition summary
  - Updated `data/local/PlayerProfileEntity.kt` — added `highestUnlockedTier` column (default 1)
  - Updated `data/local/PlayerProfileDao.kt` — added `updateHighestUnlockedTier()` query
  - Updated `data/local/AppDatabase.kt` — bumped version to 2
  - Updated `domain/model/PlayerProfile.kt` — added `highestUnlockedTier` field
  - Updated `domain/repository/PlayerRepository.kt` — added `updateHighestUnlockedTier()` method
  - Updated `data/repository/PlayerRepositoryImpl.kt` — implemented new method + entity→domain mapping
  - Updated `presentation/battle/entities/EnemyEntity.kt` — added `armorHits` (blocks first N hits), `attackInterval` param, armor ring visual
  - Updated `presentation/battle/engine/WaveSpawner.kt` — accepts `BattleConditionEffects`, applies speed/attack/armor/boss interval
  - Updated `presentation/battle/engine/GameEngine.kt` — computes conditions from tier, applies orb/knockback/thorn multipliers
  - Updated `presentation/battle/BattleUiState.kt` — added `tierUnlocked` to `RoundEndState`
  - Updated `presentation/battle/BattleViewModel.kt` — checks tier unlock after round end, persists new highest tier
  - Updated `presentation/battle/ui/PostRoundOverlay.kt` — shows "🔓 Tier X Unlocked!" banner with cash multiplier teaser
  - Updated `presentation/home/HomeUiState.kt` — added `highestUnlockedTier`, `bestWavePerTier`
  - Updated `presentation/home/HomeViewModel.kt` — loads unlock data, exposes `selectTier()`
  - Updated `presentation/home/HomeScreen.kt` — replaced static header with TierSelector
  - Updated `test/fakes/FakePlayerRepository.kt` — added `updateHighestUnlockedTier`
  - Created `test/.../CheckTierUnlockTest.kt` — 7 tests for tier unlock logic
  - Created `test/.../BattleConditionEffectsTest.kt` — 6 tests for all tier condition values
- Commands/tests run: `./run-gradle.sh testDebugUnitTest` — BUILD SUCCESSFUL, 93 tests, 0 failures. `./run-gradle.sh assembleDebug` — BUILD SUCCESSFUL.
- Open questions / blockers: None.
- Memory updated: STATE ✅ / RUN_LOG ✅

## 2026-03-06 — Plan 18: Narrative Biome Progression
- Goal: 5 biome visual identities, ambient particles, biome transition overlay, home screen theming.
- Decisions made:
  - (a) Simple overlay for biome transition — styled Compose screen, animation deferred to Plan 27.
  - (a) Simple particles — lightweight spawn-drift-recycle, 30-50 per biome, no physics.
  - (a) Derive biome unlock from highestUnlockedTier — no DB change, first-seen via SharedPreferences.
  - Enemy tinting via 30% color blend with base type color (not color filter).
  - Ziggurat colors passed as constructor parameter, paints built dynamically.
- Changes made:
  - Created `presentation/battle/biome/BiomeTheme.kt` — 5 biome palettes (sky, ground, ziggurat, enemy tint, particles)
  - Created `presentation/battle/biome/BackgroundRenderer.kt` — gradient sky + ambient particle system
  - Created `presentation/battle/ui/BiomeTransitionOverlay.kt` — full-screen biome reveal with step count
  - Created `data/BiomePreferences.kt` — SharedPreferences wrapper for first-seen tracking
  - Updated `presentation/battle/engine/GameEngine.kt` — creates BackgroundRenderer, passes biome colors/tint
  - Updated `presentation/battle/entities/ZigguratEntity.kt` — accepts layerColors parameter
  - Updated `presentation/battle/entities/EnemyEntity.kt` — accepts enemyTint, blends with base color
  - Updated `presentation/battle/engine/WaveSpawner.kt` — accepts and passes enemyTint
  - Updated `presentation/battle/BattleUiState.kt` — added biomeTransition field
  - Updated `presentation/battle/BattleViewModel.kt` — injects BiomePreferences, checks first-seen, dismissBiomeTransition()
  - Updated `presentation/battle/BattleScreen.kt` — shows BiomeTransitionOverlay
  - Updated `presentation/home/HomeScreen.kt` — biome gradient background
  - Created `test/.../BiomeThemeTest.kt` — 4 tests
- Commands/tests run: `./run-gradle.sh testDebugUnitTest` — 97 tests, 0 failures. `./run-gradle.sh assembleDebug` — BUILD SUCCESSFUL.
- Open questions / blockers: None.
- Memory updated: STATE ✅ / RUN_LOG ✅

## 2026-03-06 — Plan 14: Step Overdrive
- Goal: Mid-battle mechanic to sacrifice Steps for 60s combat buff, once per round.
- Decisions made:
  - (a) Stub SURGE — shows in UI, deducts cost, but UW cooldown reset is no-op until Plan 15.
  - (a) Skip free charges — deferred to Plan 19 (Walking Encounters).
  - (a) Engine-side aura — pulsing circle + timer bar rendered on Canvas, respects game speed.
- Changes made:
  - Created `domain/usecase/ActivateOverdrive.kt` — sealed Result, checks balance + once-per-round
  - Created `presentation/battle/ui/OverdriveMenu.kt` — 4-option selection with cost/affordability
  - Created `test/.../ActivateOverdriveTest.kt` — 4 tests
  - Updated `GameEngine.kt` — overdrive state (timer, fortune multiplier, stat modification), activateOverdrive(), expireOverdrive()
  - Updated `ZigguratEntity.kt` — pulsing aura circle + timer bar, overdriveColor/overdriveProgress fields
  - Updated `BattleUiState.kt` — added overdriveUsed, activeOverdriveType, overdriveTimeRemaining, stepBalance, showOverdriveMenu
  - Updated `BattleViewModel.kt` — activateOverdrive(), toggleOverdriveMenu(), polls engine overdrive state
  - Updated `BattleScreen.kt` — ⚡ button in control bar, OverdriveMenu overlay, active overdrive HUD indicator
- Commands/tests run: `./run-gradle.sh testDebugUnitTest` — 101 tests, 0 failures. `./run-gradle.sh assembleDebug` — BUILD SUCCESSFUL.
- Open questions / blockers: None.
- Memory updated: STATE ✅ / RUN_LOG ✅

## 2026-03-06 — Plan 15: Ultimate Weapons
- Goal: 6 UW types with unlock/upgrade/equip, battle activation with cooldowns, visual effects, management screen.
- Decisions made:
  - (a) Simple geometric effects — expanding circles, lines, tints. Polish in Plan 27.
  - (a) Sub-screen of Workshop — "Ultimate Weapons" button navigates to UW management.
  - (a) Simple scaling — upgradeCost = unlockCost * 2 * level, cooldown -5%/level, max level 10.
- Changes made:
  - Updated `domain/model/UltimateWeaponType.kt` — added baseCooldownSeconds, effectDurationSeconds, upgradeCost(), cooldownAtLevel(), MAX_LEVEL
  - Created `domain/usecase/UnlockUltimateWeapon.kt` — checks balance + not owned, deducts Power Stones
  - Created `domain/usecase/UpgradeUltimateWeapon.kt` — cost scaling, max level 10
  - Created `presentation/weapons/UltimateWeaponViewModel.kt` — observes weapons + wallet
  - Created `presentation/weapons/UltimateWeaponScreen.kt` — 6 UW cards with lock/unlock/equip/upgrade
  - Created `presentation/battle/ui/UltimateWeaponBar.kt` — row of 3 UW activation buttons
  - Updated `GameEngine.kt` — UW state management, 6 effect implementations, visual rendering, SURGE wired
  - Updated `BattleUiState.kt` — added UWSlotInfo, uwSlots
  - Updated `BattleViewModel.kt` — injects UltimateWeaponRepository, loads equipped, polls UW state
  - Updated `BattleScreen.kt` — shows UltimateWeaponBar
  - Updated `Screen.kt` — added Weapons route
  - Updated `MainActivity.kt` — added Weapons composable route
  - Updated `WorkshopScreen.kt` — added "Ultimate Weapons" navigation button
  - Created `test/fakes/FakeUltimateWeaponRepository.kt`
  - Created `test/.../UnlockUltimateWeaponTest.kt` — 3 tests
  - Created `test/.../UpgradeUltimateWeaponTest.kt` — 4 tests
- Commands/tests run: `./run-gradle.sh testDebugUnitTest` — 108 tests, 0 failures. `./run-gradle.sh assembleDebug` — BUILD SUCCESSFUL.
- Open questions / blockers: None.
- Memory updated: STATE ✅ / RUN_LOG ✅

## 2026-03-06 — Plan 16: Labs System
- Goal: Implement Labs research system — 10 time-gated research projects, lab slots, Gem rush, auto-completion.
- Decisions made:
  - (a) Cost scaling 1.15, time scaling 1.10 — moderate ramp matching Workshop feel.
  - (a) Gem rush: linear interpolation `50 + fraction × 150` (range 50–200 Gems).
  - (a) Per-type scaling fields on ResearchType enum (tunable in Plan 28).
- Changes made:
  - Updated `domain/model/ResearchType.kt` — added `costScaling: Double = 1.15` and `timeScaling: Double = 1.10`
  - Created `domain/usecase/CalculateResearchCost.kt` — `baseCostSteps × costScaling^level`
  - Created `domain/usecase/CalculateResearchTime.kt` — `baseTimeHours × timeScaling^level`
  - Created `domain/usecase/StartResearch.kt` — validates slots, affordability, max level, deducts Steps
  - Created `domain/usecase/CompleteResearch.kt` — gates on timer, increments level
  - Created `domain/usecase/RushResearch.kt` — linear Gem cost, companion `calculateRushCost()`
  - Created `domain/usecase/UnlockLabSlot.kt` — 200 Gems per slot, max 4
  - Created `domain/usecase/CheckResearchCompletion.kt` — auto-completes expired research
  - Updated `data/local/PlayerProfileEntity.kt` — added `labSlotCount` with `@ColumnInfo(defaultValue = "1")`
  - Updated `data/local/PlayerProfileDao.kt` — added `updateLabSlotCount()`
  - Updated `data/local/AppDatabase.kt` — bumped version to 3
  - Updated `domain/model/PlayerProfile.kt` — added `labSlotCount`
  - Updated `domain/repository/PlayerRepository.kt` — added `updateLabSlotCount()`
  - Updated `data/repository/PlayerRepositoryImpl.kt` — implemented + toDomain mapping
  - Updated `domain/repository/LabRepository.kt` — added `getResearchLevel()`, `getActiveResearchCount()`, updated `startResearch()` signature
  - Updated `data/repository/LabRepositoryImpl.kt` — implemented new methods
  - Created `presentation/labs/LabsUiState.kt` — ResearchDisplayInfo + LabsUiState
  - Created `presentation/labs/LabsViewModel.kt` — combines research/wallet/tick flows, 1s countdown
  - Created `presentation/labs/LabsScreen.kt` — full UI with slot indicator, research cards, start/rush/unlock
  - Updated `presentation/MainActivity.kt` — replaced Labs placeholder with LabsScreen
  - Updated `presentation/home/HomeViewModel.kt` — added labRepository.ensureResearchExists() + CheckResearchCompletion
  - Created `test/fakes/FakeLabRepository.kt` — in-memory StateFlow-backed fake
  - Updated `test/fakes/FakePlayerRepository.kt` — added updateLabSlotCount
  - Created 7 test classes (25 new tests):
    - CalculateResearchCostTest (4), CalculateResearchTimeTest (3), StartResearchTest (5), CompleteResearchTest (3), RushResearchTest (4), UnlockLabSlotTest (3), CheckResearchCompletionTest (3)
- Commands/tests run: `./run-gradle.sh testDebugUnitTest` — 133 tests, 0 failures. `./run-gradle.sh assembleDebug` — BUILD SUCCESSFUL.
- Open questions / blockers: None.
- Memory updated: STATE ✅ / RUN_LOG ✅

## 2026-03-06 — Plan 17: Cards System
- Goal: Implement Cards system — 9 card types, 3 rarities, pack opening, Card Dust upgrades, loadout, battle integration.
- Decisions made:
  - (a) Pack distributions: Common 80/18/2, Rare 50/40/10, Epic 20/40/40. Dust from dupes: 5/15/50.
  - (a) Numeric fields on CardType enum with linear interpolation for level scaling.
  - (b) Post-process pattern: ApplyCardEffects modifies ResolvedStats copy, ResolveStats untouched.
- Changes made:
  - Updated `domain/model/CardType.kt` — added valueLv1/valueLv5/secondaryLv1/secondaryLv5, effectAtLevel(), secondaryAtLevel()
  - Updated `domain/model/CardRarity.kt` — added dustValue (5/15/50) and upgradeDustPerLevel (10/25/50)
  - Created `domain/usecase/OpenCardPack.kt` — PackTier enum, CardResult, rarity rolling, duplicate→dust
  - Created `domain/usecase/UpgradeCard.kt` — Card Dust cost scaling by rarity and level
  - Created `domain/usecase/ApplyCardEffects.kt` — CardEffectResult, 9 card effects as post-process on ResolvedStats
  - Created `domain/usecase/ManageCardLoadout.kt` — equip/unequip with max 3 validation
  - Created `presentation/cards/CardsUiState.kt` — CardDisplayInfo, PackOption, CardsUiState
  - Created `presentation/cards/CardsViewModel.kt` — combines cards + wallet, all actions
  - Created `presentation/cards/CardsScreen.kt` — pack buttons, card collection, equip/upgrade, rarity colors
  - Updated `presentation/battle/BattleViewModel.kt` — inject CardRepository, apply card effects at round start + playAgain
  - Updated `presentation/battle/engine/GameEngine.kt` — Second Wind revive, cashBonusPercent in kill rewards
  - Updated `presentation/navigation/Screen.kt` — added Cards route
  - Updated `presentation/MainActivity.kt` — added Cards composable
  - Updated `presentation/workshop/WorkshopScreen.kt` — added "🃏 Cards" navigation button
  - Created `test/fakes/FakeCardRepository.kt` — in-memory StateFlow-backed fake
  - Updated `test/fakes/FakePlayerRepository.kt` — implemented addCardDust/spendCardDust
  - Created 4 test classes (22 new tests):
    - OpenCardPackTest (4), UpgradeCardTest (4), ApplyCardEffectsTest (11), ManageCardLoadoutTest (3)
- Commands/tests run: `./run-gradle.sh testDebugUnitTest` — 155 tests, 0 failures. `./run-gradle.sh assembleDebug` — BUILD SUCCESSFUL.
- Open questions / blockers: Step Surge gemMultiplier tracked but not consumed (no Gem earning in battle — deferred to Plan 20).
- Memory updated: STATE ✅ / RUN_LOG ✅

## Run — 2026-03-06 — Plan 19: Walking Encounters & Supply Drops

### Objective
Implement Plan 19: Supply drop generation during walks, push notifications, claim system, and inbox UI.

### What was done
1. **Task 1 — Enums & type safety**: Created `SupplyDropTrigger` (4 entries with notification messages) and `SupplyDropReward` (4 entries). Updated `SupplyDrop` domain model from raw `String` fields to type-safe enums. Updated `WalkingEncounterRepository` interface and `WalkingEncounterRepositoryImpl` to use enums (stored as `.name` strings in Room).

2. **Task 2 — GenerateSupplyDrop use case**: Seeded random drop generation with 3 active triggers (milestone at 10k, threshold at 2k boundaries with 5% per 100 steps, random at 1% per 500 steps). Step burst deferred. Created `DropGeneratorState` for tracking. 9 unit tests, all green.

3. **Task 3 — ClaimSupplyDrop use case**: Credits reward to correct `PlayerRepository` method based on `SupplyDropReward` type, marks drop claimed. Created `FakeWalkingEncounterRepository`. 6 unit tests, all green.

4. **Task 4 — Inbox cap enforcement**: Added `deleteOldestUnclaimed()` and `countUnclaimedOnce()` to `WalkingEncounterDao`. Added `enforceInboxCap(maxSize)` and `getUnclaimedCount()` to repository interface/impl.

5. **Task 5 — SupplyDropNotificationManager**: Dedicated `supply_drops` notification channel (IMPORTANCE_DEFAULT), unique notification IDs per drop, deep-link intent to supplies screen.

6. **Task 6 — DailyStepManager integration**: Added `WalkingEncounterRepository` and `SupplyDropNotificationManager` as dependencies. After step crediting, calls `GenerateSupplyDrop`, enforces inbox cap, creates drop, and sends notification. Tracks `DropGeneratorState` with day rollover reset.

7. **Task 7 — UnclaimedSuppliesScreen**: Added `Screen.Supplies` route. Created `UnclaimedSuppliesViewModel` (observes unclaimed drops, claim/claimAll), `SuppliesUiState`, and `UnclaimedSuppliesScreen` (LazyColumn with claim buttons, empty state, relative timestamps). Added route to `NavHost` in `MainActivity` with notification deep-link handling.

8. **Task 8 — Home screen inbox badge**: Added `unclaimedDropCount` to `HomeUiState`. Injected `WalkingEncounterRepository` into `HomeViewModel`, added to `combine()`. Added `BadgedBox` button on `HomeScreen` that shows when count > 0, navigates to supplies. Added `onSuppliesClick` callback wired in `MainActivity`.

### Decisions
- No GPS triggers — step-based only, defer to future plan.
- No free Overdrive charges — burst trigger deferred, avoids Room migration.
- Inbox overflow discards oldest unclaimed drop silently.
- No Card Pack reward — Card Dust instead, avoids coupling to OpenCardPack flow.
- 10k milestone gives 5 Gems (single drop); Power Stones deferred to combined reward enhancement.
- No notification action button — tap opens inbox screen (avoids BroadcastReceiver complexity).

### Test results
- 170 total JVM tests (155 existing + 15 new), all green, 0 failures.
- New: GenerateSupplyDropTest (9), ClaimSupplyDropTest (6).

### What remains
- Step burst trigger (needs step velocity tracking in DailyStepManager).
- 10k milestone second reward (Power Stones) — could be two drops or combined.
- Custom notification icons (currently using system placeholders).
- Supply drop notification preferences (on/off toggle — Plan 23).
- Claim animation in UnclaimedSuppliesScreen (polish — Plan 27).

## Run — 2026-03-06 — Plan 20: Power Stone & Gem Economy

### Objective
Implement premium currency earning systems: weekly step challenges, daily login rewards, and wave milestone bonuses.

### What was done
1. **Task 1 — Database**: Created `WeeklyChallengeEntity` + `WeeklyChallengeDao`, `DailyLoginEntity` + `DailyLoginDao`. Added `currentStreak`/`lastLoginDate` to `PlayerProfileEntity`/`PlayerProfile`. Added `updateStreak()` to `PlayerProfileDao`/`PlayerRepository`. Added `sumCreditedSteps()` to `DailyStepDao`. Bumped DB to version 4 (9 entities). Updated `DatabaseModule` with 2 new DAO providers. Updated `FakePlayerRepository` with streak support.

2. **Task 2 — Weekly Step Challenge**: Created `TrackWeeklyChallenge` use case. Queries weekly step sum from `DailyStepDao`, awards PS at 50k (10), 75k (20 total), 100k (35 total) thresholds. Only awards delta PS for newly crossed tiers.

3. **Task 3 — Daily Login & Streak**: Created `TrackDailyLogin` use case. Awards 1 PS when 1k+ steps walked (once/day). Manages 7-day Gem streak: consecutive days increment streak, missed day resets to 1, awards min(streak, 5) Gems. Streak cycles after day 7.

4. **Task 4 — Wave Milestone PS**: Created `AwardWaveMilestone` use case. Awards 1 PS (base), 2 PS (wave % 10 == 0), or 5 PS (wave % 25 == 0) on new personal bests. Integrated into `BattleViewModel.endRound()`. Added `powerStonesAwarded` to `RoundEndState`. Updated `PostRoundOverlay` to display PS earned.

5. **Task 5 — Currency Dashboard**: Created `Screen.Economy` route. Created `CurrencyDashboardViewModel` + `CurrencyDashboardScreen` with weekly progress bar, 3 threshold markers, login streak dots (7-day), daily PS status, and currency balances.

6. **Task 6 — Integration**: Updated `DailyStepManager` with `DailyLoginDao`, `WeeklyChallengeDao`, `DailyStepDao` dependencies. Calls `TrackDailyLogin` and `TrackWeeklyChallenge` after step crediting. Updated `HomeViewModel` to trigger daily login on app open. Made currency row on `HomeScreen` tappable to navigate to economy dashboard.

### Decisions
- Streak fields on PlayerProfileEntity (no separate LoginStreakEntity) — avoids extra table/DAO/repo.
- Long-distance Gem bonuses deferred to Plan 21 (milestones).
- Wave milestone: 1 PS base, 2 PS at multiples of 10, 5 PS at multiples of 25.
- TrackWeeklyChallenge/TrackDailyLogin use DAOs directly (data-layer integration, not pure domain).

### Test results
- 179 total JVM tests (170 existing + 9 new AwardWaveMilestone), all green, 0 failures.

### What remains
- TrackWeeklyChallenge and TrackDailyLogin unit tests (need DAO fakes — deferred to Plan 29).
- Long-distance walking Gem bonuses (Plan 21).
- Weekly challenge reset notification.

## Run — 2026-03-09 — Plan 21: Milestones & Daily Missions

### Objective
Implement lifetime walking milestones and daily missions with progress tracking and claim rewards.

### Design decisions
- Card Pack milestone rewards → equivalent Gems (Tutorial=50, Rare=150, Epic=500). Keeps OpenCardPack untouched.
- Cosmetic milestone rewards → stored as claimed but no-op visually until cosmetics system exists.
- Walking mission progress → DAO query approach (steps already tracked).
- Battle mission progress → accumulated in BattleViewModel.endRound().
- Workshop/Lab mission progress → updated at call sites.
- DB version 5 with destructive fallback (still in dev).

### What was done
1. **Task 1 — Domain models**: Created `MilestoneReward` (sealed class: Gems/PowerStones/Cosmetic), `Milestone` (6 entries matching GDD §16.1 with card pack→Gem equivalents), `DailyMissionType` (6 entries: 2 walking, 2 battle, 2 upgrade), `MissionCategory` enum.

2. **Task 2 — Milestone DB layer**: Created `MilestoneEntity` + `MilestoneDao`. Updated `AppDatabase` (version 5, 11 entities). Updated `DatabaseModule` with 2 new DAO providers.

3. **Task 3 — Mission DB layer**: Created `DailyMissionEntity` + `DailyMissionDao` (with `countClaimable` Flow query).

4. **Task 4 — Use cases**: Created `CheckMilestones` (queries DAO, filters by threshold + unclaimed) and `ClaimMilestone` (credits Gems/PS, marks claimed, cosmetics no-op).

5. **Task 5 — GenerateDailyMissions**: Date-seeded Random, 1 per category, idempotent (skips if missions exist for today).

6. **Task 6 — Progress hooks**: 
   - `BattleViewModel.endRound()` → updates REACH_WAVE and KILL_ENEMIES missions.
   - `WorkshopViewModel.purchase()` → updates SPEND_WORKSHOP_STEPS mission.
   - `LabsViewModel` → updates COMPLETE_RESEARCH mission after rush/completion.

7. **Task 7 — Missions screen**: Created `MissionsUiState`, `MissionsViewModel` (combines missions + milestones + profile + tick), `MissionsScreen` (daily missions with progress bars + claim buttons, milestones with progress + claim, midnight countdown).

8. **Task 8 — Home integration**: Added `Screen.Missions` route, `claimableMissionCount` to `HomeUiState`, missions badge button on `HomeScreen`, `GenerateDailyMissions` call in `HomeViewModel.init`, 5-flow `combine()` with milestone/mission counts.

### Test results
- 206 total JVM tests (179 existing + 27 new), all green, 0 failures.
- New: MilestoneTest (6), DailyMissionTypeTest (7), CheckMilestonesTest (4), ClaimMilestoneTest (4), GenerateDailyMissionsTest (6).
- New fakes: FakeMilestoneDao, FakeDailyMissionDao.

### What remains
- Milestone cosmetic rewards are no-op (needs cosmetics system — Plan 26/27).
- Walking mission auto-progress runs once on MissionsScreen open (not continuously from DailyStepManager) — sufficient since steps flow updates the ViewModel.
- Daily mission notification on completion (deferred to Plan 23).

## Run — 2026-03-09 — Plan 22: Stats & History Screen

### Objective
Build the Stats & History screen with walking history charts, battle stats, and all-time aggregates.

### Design decisions
- Canvas-drawn bar chart (no third-party library, matches existing Canvas patterns).
- Lifetime currency counters (totalGemsEarned/Spent, totalPowerStonesEarned/Spent) on PlayerProfileEntity — tracked at DAO/repository level, zero caller changes.
- Battle stats (totalRoundsPlayed, totalEnemiesKilled, totalCashEarned) on PlayerProfileEntity — no separate entity.
- DB version 6 with destructive fallback.

### What was done
1. **Task 1 — Data layer**: Added 7 new columns to `PlayerProfileEntity` (totalGemsEarned/Spent, totalPowerStonesEarned/Spent, totalRoundsPlayed, totalEnemiesKilled, totalCashEarned). Updated `PlayerProfile` domain model, `PlayerProfileDao` (6 new queries), `PlayerRepositoryImpl` (lifetime tracking in add/spend methods + incrementBattleStats), `PlayerRepository` interface, `FakePlayerRepository`. Bumped DB to version 6.

2. **Task 2 — Battle stats wiring**: Added `playerRepository.incrementBattleStats()` call in `BattleViewModel.endRound()`.

3. **Task 3 — StatsViewModel**: Created `StatsUiState` (DailyBarData, StatsPeriod enum) and `StatsViewModel` (4-flow combine: profile + history + upgrades + period). Builds bar data for 7-day/30-day/12-week views. Computes daysActive, averageDailySteps, totalWorkshopLevels.

4. **Task 4 — Walking history chart**: Created `WalkingHistoryChart` Canvas composable — vertical bars with primary/secondary color split (sensor steps vs step-equivalents), 50k ceiling dashed line, date labels, y-axis scale, FilterChip period toggle, legend.

5. **Task 5 — Stats screen**: Created `StatsScreen` with 4 Card sections (Walking History, Today's Activity, Battle Stats, All-Time Stats). Replaced placeholder in `MainActivity`.

### Test results
- 206 total JVM tests, all green, 0 failures. No new tests (presentation-only plan).

### What remains
- Lifetime currency counters start from 0 (no retroactive backfill).
- Chart tap-for-detail tooltip deferred to Plan 27 polish.
- Pull-to-refresh deferred (data is already reactive via Flows).

## Run — 2026-03-09 — Plan 23: Notifications & Widget

### Objective
Enhanced notifications, home screen widget, smart reminders, milestone alerts, and notification preferences.

### Design decisions
- Traditional AppWidgetProvider + RemoteViews (no Glance dependency).
- Smart reminders piggyback on existing StepSyncWorker (no separate WorkManager job).
- SharedPreferences for notification preferences (consistent with BiomePreferences pattern).

### What was done
1. **Task 1 — NotificationPreferences**: Created `data/NotificationPreferences.kt` — 4 boolean toggles (persistent, supply drops, smart reminders, milestone alerts).

2. **Task 2 — Enhanced persistent notification**: Updated `StepNotificationManager` with Workshop/Battle action buttons via PendingIntents. Updated `StepCounterService` to pass actual step balance from PlayerRepository. Added preference gate. Extended `MainActivity` deep-link handling for workshop/battle/missions routes.

3. **Task 3 — Home screen widget**: Created `widget_step_counter.xml` layout, `step_widget_info.xml` metadata, `StepWidgetProvider` (AppWidgetProvider with SharedPreferences-backed data), `WidgetUpdateHelper` (60s throttle). Integrated into `DailyStepManager`. Registered in AndroidManifest.

4. **Task 4 — Smart reminders**: Created `SmartReminderManager` — checks prefs enabled, not sent today, lastActiveAt > 4h, finds cheapest upgrade within 10k step gap. Uses `reminders` notification channel. Integrated into `StepSyncWorker.doWork()`.

5. **Task 5 — Milestone alerts**: Created `MilestoneNotificationManager` — notifyNewBestWave() and notifyMilestoneAchieved(). Uses `milestones` channel. Integrated into `BattleViewModel.endRound()` (new best wave) and `HomeViewModel.init` (achievable milestones).

6. **Task 6 — Supply drop preference gate**: Updated `SupplyDropNotificationManager` to inject NotificationPreferences and skip if disabled.

7. **Task 7 — Settings UI**: Created `NotificationSettingsViewModel` + `NotificationSettingsScreen` (4 Switch toggles). Added `Screen.Settings` route, wired in NavHost, added settings button on HomeScreen.

### Test results
- 206 total JVM tests, all green, 0 failures. No new tests (Android notification/widget APIs).

### What remains
- Custom notification icons (all channels use system placeholders).
- Widget balance shows 0 (DailyStepManager doesn't query PlayerRepository for balance).
- Widget preview image for widget picker.

## Run — 2026-03-09 — Plan 25: Anti-Cheat & Validation

### Objective
Harden anti-cheat beyond basic rate limiting + daily ceiling + HC escrow. Add velocity analysis, graduated cross-validation, activity minute gaming prevention, and per-minute overlap deduction.

### Design decisions
- No accelerometer sensor — step velocity analysis detects shakers via statistical patterns (zero battery cost).
- No Room entity for logging — SharedPreferences counters + Logcat (no DB migration needed).
- Cross-validation offense count in SharedPreferences (survives DB wipes, matches existing prefs pattern).
- Added mockito-kotlin 5.4.0 as test dependency for mocking Android classes in JVM tests.
- Enabled `unitTests.isReturnDefaultValues = true` in build.gradle.kts for android.util.Log in tests.

### What was done
1. **Task 1 — AntiCheatPreferences**: Created `data/anticheat/AntiCheatPreferences.kt` — SharedPreferences wrapper with daily counters (rate rejected, velocity penalized, activity minutes rejected), cross-validation offense tracking (count + last date), and 7-day offense decay.

2. **Task 2 — StepVelocityAnalyzer**: Created `data/sensor/StepVelocityAnalyzer.kt` — rolling 15-min window, two heuristics: instant jump detection (idle→spike in last 3 pairs) and constant rate detection (CV < 0.05 over 10-min window). Returns penalty multiplier (1.0/0.5/0.0).

3. **Task 3 — DailyStepManager wiring**: Added `StepVelocityAnalyzer` and `AntiCheatPreferences` as constructor dependencies. Pipeline: rate limit → velocity analysis → ceiling → persist. Logs rate-rejected and velocity-penalized steps. Added `stepsPerMinute` map for overlap deduction. Resets on day rollover.

4. **Task 4 — Enhanced StepCrossValidator**: Rewrote with graduated response based on offense count: Level 0 (escrow, 3 syncs), Level 1 (escrow, 2 syncs), Level 2 (cap at HC value), Level 3 (cap at HC minus 10%). Records offenses on discrepancy, decays on reconciliation.

5. **Task 5 — ActivityMinuteValidator**: Created `data/healthconnect/ActivityMinuteValidator.kt` — filters sessions: discards <2min micro-sessions, truncates >4hr sessions to 240min, rejects sessions beyond 5 distinct activity types per day.

6. **Task 6 — StepSyncWorker wiring**: Added `ActivityMinuteValidator` to constructor. Sessions filtered through validator before conversion. Passes `dailyStepManager.getSensorStepsPerMinute()` instead of `emptyMap()`.

7. **Task 7 — Per-minute overlap deduction**: Added `stepsPerMinute` accumulator to `DailyStepManager` (epoch-minute → credited steps). Capped at 1440 entries. Exposed via `getSensorStepsPerMinute()`. `ActivityMinuteConverter` now receives real per-minute data for double-counting prevention.

### Test results
- 222 total JVM tests (206 existing + 16 new), all green, 0 failures.
- New: StepVelocityAnalyzerTest (6), ActivityMinuteValidatorTest (5), StepCrossValidatorTest (5).
- Build: assembleDebug successful.

### What remains
- StepCrossValidator Level 2/3 could also adjust `creditedSteps` in Room (currently only escrows excess).
- AntiCheatPreferences counters not surfaced in any UI (debug screen could be added).
- Step burst trigger for supply drops still deferred.

## Run — 2026-03-09 — Plan 26: Monetization & Ads

### Objective
Implement monetization layer with stub billing/ads, cosmetic store, Season Pass, and reward ads.

### Design decisions
- Stub-first architecture: `BillingManager` and `RewardAdManager` interfaces in domain (pure Kotlin), stub impls in data. Swap via DI bindings when real SDKs integrated.
- Season Pass daily Gem bonus piggybacks on existing `TrackDailyLogin` (automatic, not manual claim).
- Cosmetic store uses placeholder items — visual application deferred to Plan 27.
- `OpenCardPack` gets `isFree: Boolean = false` default param — backward-compatible, zero caller impact.
- No new test dependencies needed — stubs are simple enough to not warrant dedicated tests.
- DB version 7 with destructive fallback (still in dev).

### What was done
1. **Task 1 — Database & Profile**: Added 5 monetization fields to `PlayerProfileEntity` (`adRemoved`, `seasonPassActive`, `seasonPassExpiry`, `freeLabRushUsedToday`, `freeCardPackAdUsedToday`). Created `CosmeticEntity` + `CosmeticDao`. Bumped DB to version 7 (12 entities). Updated `PlayerProfileDao` (4 new queries), `PlayerRepository` interface (4 new methods), `PlayerRepositoryImpl`, `FakePlayerRepository`.

2. **Task 2 — Billing Manager Stub**: Created `BillingProduct` enum (5 products), `PurchaseResult` sealed class, `BillingManager` interface, `StubBillingManager` (500ms delay, always succeeds), `BillingModule` DI binding.

3. **Task 3 — Gem Pack Purchase + Store UI**: Created `PurchaseGemPack` use case, `StoreScreen` (Gem packs, Ad Removal, Season Pass, Cosmetics sections), `StoreViewModel`, `StoreUiState`. Added `Screen.Store` route, wired in `MainActivity` NavHost.

4. **Task 4 — Ad Removal**: Ad Removal card in StoreScreen, `StoreViewModel.purchaseAdRemoval()`, "Already Purchased" state.

5. **Task 5 — Season Pass**: Updated `TrackDailyLogin` with `seasonPassActive`/`seasonPassExpiry` params (+10 Gems/day). Updated `LabsViewModel` with `freeRush()` method and `seasonPassFreeRushAvailable` state. Updated `LabsScreen` with "Free ⭐" button. Season Pass card in StoreScreen.

6. **Task 6 — Reward Ad Stub**: Created `AdPlacement` enum (3 placements), `AdResult` sealed class, `RewardAdManager` interface, `StubRewardAdManager` (1s delay, always rewards), `AdModule` DI binding.

7. **Task 7 — Post-Round Ads**: Added `adRemoved`/`gemAdWatched`/`psAdWatched` to `RoundEndState`. Injected `RewardAdManager` into `BattleViewModel`, added `watchGemAd()`/`watchPsAd()`. Updated `PostRoundOverlay` with ad buttons (hidden if adRemoved, disabled after use).

8. **Task 8 — Free Card Pack Ad**: Added `isFree` param to `OpenCardPack` (backward-compatible default). Injected `RewardAdManager` into `CardsViewModel`, added `watchFreePackAd()`. Updated `CardsScreen` with "🎬 Free Pack (Ad)" button (hidden if adRemoved, disabled if used today).

9. **Task 9 — Cosmetic Store**: Created `CosmeticCategory` enum, `CosmeticItem` domain model, `CosmeticRepository` interface, `CosmeticRepositoryImpl` (7 placeholder items, seed on first access). Added cosmetics section to StoreScreen with buy/equip/unequip.

10. **Task 10 — Integration**: Added Store button to HomeScreen and Economy screen. Season Pass badge on HomeScreen. All ad UI gated on `adRemoved` flag.

### Test results
- 222 total JVM tests, all green, 0 failures. No new tests (stub implementations, presentation-only changes).
- Build: assembleDebug successful.

### What remains (deferred)
- Google Play Billing Library v7 integration (replace StubBillingManager).
- AdMob SDK integration (replace StubRewardAdManager).
- Real purchase verification and receipt validation.
- Subscription renewal handling and grace periods.
- Real cosmetic content and visual application (Plan 27).
- Play Console product configuration and test tracks.
- Ad mediation for fill rate optimization.
- ADR for stub billing decision (documented in plan-26-monetization.md instead).

---

## Run: 2026-03-09 — Plan 27: Polish & Visual Effects

**Objective:** Add visual polish and audio to the battle renderer and UI.

**Decisions:**
- (a) Pooled particle system (200 pre-allocated) over lightweight ad-hoc allocation — avoids GC pressure during combat.
- (a) Minimal sound set (~7 reusable sounds) over full per-type set — sufficient for v1.0, easy to expand later.
- (a) Floating cash text on Canvas (game thread) over Compose overlay — same coordinate space, no latency.
- (a) System ANIMATOR_DURATION_SCALE for reduced motion — no in-app toggle needed.
- (a) Placeholder WAV files as sine wave tones — real audio assets to be sourced separately.

**Created files:**
- `presentation/battle/effects/ParticlePool.kt` — Particle class + ParticlePool (200 capacity, acquire/release/recycle)
- `presentation/battle/effects/ReducedMotionCheck.kt` — Reads system ANIMATOR_DURATION_SCALE
- `presentation/battle/effects/EffectEngine.kt` — Effect interface + EffectEngine (manages effects, owns pool + screen shake)
- `presentation/battle/effects/ScreenShake.kt` — Canvas translate oscillation with decay
- `presentation/battle/effects/ProjectileTrailEffect.kt` — Spawns fading trail particles at projectile positions
- `presentation/battle/effects/DeathEffect.kt` — Per-enemy-type death burst (6 types, 6-20 particles each)
- `presentation/battle/effects/FloatingText.kt` — "+X" cash text that drifts up and fades
- `presentation/battle/effects/UWVisualEffect.kt` — 6 particle-based UW spectacles (replaces old geometric rendering)
- `presentation/battle/effects/OverdriveAuraEffect.kt` — 4 overdrive aura particle emitters
- `presentation/battle/effects/WaveAnnouncement.kt` — Wave number + boss warning text overlay + cooldown countdown
- `presentation/audio/SoundManager.kt` — SoundPool wrapper, 7 sound effects, volume/mute, shoot throttling
- `data/SoundPreferences.kt` — SharedPreferences for sound mute/volume
- `res/raw/sfx_*.ogg` — 7 placeholder WAV audio files (sine wave tones)

**Created tests:**
- `presentation/battle/effects/ParticlePoolTest.kt` — 9 tests (acquire, release, recycle, expire, clear, reset)
- `presentation/battle/effects/ScreenShakeTest.kt` — 6 tests (trigger, decay, override, reset, offset)
- `presentation/battle/effects/DeathEffectTest.kt` — 7 tests (particle count per enemy type)

**Modified files:**
- `presentation/battle/engine/GameEngine.kt` — Full rewrite: integrated EffectEngine, removed old UW rendering (uwEffects list, uwPaint, inline render code), added all trigger points (trail, death, floating text, UW spectacle, overdrive aura, wave announcement, screen shake, sound), added reducedMotion parameter to init()
- `presentation/battle/engine/WaveSpawner.kt` — Made phaseTimer publicly readable (for cooldown text)
- `presentation/battle/entities/ZigguratEntity.kt` — Removed old aura circle rendering (auraPulse, auraPaint), added centerY property, kept overdrive timer bar
- `presentation/battle/GameSurfaceView.kt` — Added SoundManager init, reduced motion check, passes isReducedMotion to engine.init()
- `presentation/battle/BattleViewModel.kt` — Added upgrade purchase sound trigger
- `presentation/settings/NotificationSettingsViewModel.kt` — Added SoundPreferences injection, soundMuted state
- `presentation/settings/NotificationSettingsScreen.kt` — Added Sound section with mute toggle
- `presentation/workshop/UpgradeCard.kt` — Added purchase pulse animation (1.05x scale, 100ms, reduced motion aware)
- `presentation/home/HomeScreen.kt` — Added animateContentSize() to step counter
- `presentation/MainActivity.kt` — Added screen transition animations (fadeIn + slideInHorizontally, reduced motion aware)

**Test results:** 244 JVM tests — all green (was 222, +22 new).
**Build:** assembleDebug successful, 2 minor warnings (redundant conversion, hiltViewModel deprecation).

**What remains:**
- Plan 28: Balancing & Tuning (next on critical path)
- Replace placeholder audio with real royalty-free sound effects
- Plan 29: Testing & QA
- Plan 30: Release Prep

---

## Run: 2026-03-09 — Plan 28: Balancing & Tuning

**Objective:** Validate all game constants against GDD player profiles and progression timeline.

**Approach:** Test-based validation — 39 JUnit tests that compute progression math and assert GDD milestones. Conservative tuning — only adjust constants where tests reveal actual problems.

**Findings:**
- Step economy is more generous than GDD predicted in week 1 (intentional — hooks players). Settles toward GDD rates by week 4-8.
- Enemy scaling (1.05^wave) is correct — outpaces raw Workshop DPS but is balanced by crits, multishot, orbs, cards, and in-round upgrades.
- Tier progression timeline is within tolerance when accounting for full combat system (5x combat multiplier).
- Cash economy supports meaningful in-round decisions. Interest at max level is 59% of kill income (borderline but requires 20 levels of investment).
- All 9 card types are balanced with meaningful tradeoffs. No card exceeds 2.5x effective power.
- UW cooldowns allow 2-3+ activations per 20-minute round. No UW dominates.
- First UW unlock takes ~3 weeks (not 2) — acceptable for mid-game reward.
- Supply drop rates produce 1-5 drops per 10k steps.

**Constants changed:** None. All existing values validated as appropriate.

**Created files:**
- `app/src/test/java/com/whitefang/stepsofbabylon/balance/StepEconomyTest.kt` — 5 tests
- `app/src/test/java/com/whitefang/stepsofbabylon/balance/CostCurveTest.kt` — 5 tests
- `app/src/test/java/com/whitefang/stepsofbabylon/balance/EnemyScalingTest.kt` — 6 tests
- `app/src/test/java/com/whitefang/stepsofbabylon/balance/TierProgressionTest.kt` — 5 tests
- `app/src/test/java/com/whitefang/stepsofbabylon/balance/CashEconomyTest.kt` — 4 tests
- `app/src/test/java/com/whitefang/stepsofbabylon/balance/CardBalanceTest.kt` — 4 tests
- `app/src/test/java/com/whitefang/stepsofbabylon/balance/UWOverdriveBalanceTest.kt` — 5 tests
- `app/src/test/java/com/whitefang/stepsofbabylon/balance/SupplyDropEconomyTest.kt` — 5 tests
- `docs/balance/balance-report.md` — comprehensive balance validation report

**Test results:** 283 JVM tests — all green (was 244, +39 new balance tests).
**Build:** No compilation changes needed.

**What remains:**
- Plan 29: Testing & QA (next on critical path)
- Plan 30: Release Prep

## Run: 2026-03-10 — Plan 29: Testing & QA

**Objective:** Add ViewModel tests and deferred use case tests. JVM-only, no instrumented tests.

**Approach:** StandardTestDispatcher + backgroundScope collector for StateFlow-based ViewModels. advanceTimeBy for VMs with ticker loops. Use-case-level testing for LabsViewModel/MissionsViewModel (infinite ticker loops prevent direct VM testing).

**Created fakes:**
- `FakeStepRepository` — in-memory StepRepository
- `FakeBillingManager` — tracks purchases, configurable result
- `FakeRewardAdManager` — configurable AdResult
- `FakeCosmeticRepository` — in-memory cosmetic store
- `FakeDailyLoginDao` — in-memory daily login
- `FakeWeeklyChallengeDao` — in-memory weekly challenge
- `FakeDailyStepDao` — in-memory daily step records with Flow support

**Created test files (64 new tests):**
- `presentation/stats/StatsViewModelTest.kt` — 6 tests
- `presentation/weapons/UltimateWeaponViewModelTest.kt` — 4 tests
- `presentation/supplies/UnclaimedSuppliesViewModelTest.kt` — 3 tests
- `presentation/workshop/WorkshopViewModelTest.kt` — 6 tests
- `presentation/cards/CardsViewModelTest.kt` — 5 tests
- `presentation/labs/LabsViewModelTest.kt` — 4 tests (use-case level)
- `presentation/home/HomeViewModelTest.kt` — 5 tests
- `presentation/battle/BattleViewModelTest.kt` — 10 tests
- `presentation/missions/MissionsViewModelTest.kt` — 4 tests (use-case level)
- `presentation/economy/CurrencyDashboardViewModelTest.kt` — 3 tests
- `presentation/store/StoreViewModelTest.kt` — 3 tests
- `domain/usecase/TrackDailyLoginTest.kt` — 6 tests
- `domain/usecase/TrackWeeklyChallengeTest.kt` — 5 tests

**Key decisions:**
- StandardTestDispatcher over UnconfinedTestDispatcher — prevents infinite loops from ticker coroutines.
- `backgroundScope.launch { vm.uiState.collect {} }` required for WhileSubscribed StateFlows.
- LabsViewModel/MissionsViewModel tested at use-case level (not VM level) due to `while(true) { delay(1000) }` ticker loops that hang even with advanceTimeBy.
- HomeViewModel init modifies profile (TrackDailyLogin) — assertions check structural correctness, not exact currency values.
- No instrumented tests — deferred to post-release.

**Test results:** 347 JVM tests — all green (was 283, +64 new).
**Build:** testDebugUnitTest successful in 44s.

**What remains:**
- Plan 30: Release Prep (next on critical path)
- Instrumented tests (Room DAOs, Compose UI) — post-release
- LabsViewModel/MissionsViewModel direct VM tests (needs ticker refactoring or injectable clock)

## 2026-03-10 — Plan 30: Release Prep

### What was done
- **Task 1: ProGuard/R8 hardening** — Added keep rules for Health Connect SDK, SensorEventListener callbacks, WorkManager ListenableWorker subclasses, Room entity fields, org.json. Restructured rules file with section headers.
- **Task 2: Remove fallbackToDestructiveMigration** — Removed from DatabaseModule.kt. Added comment about future migration requirements.
- **Task 3: Signing config** — Added `import java.util.Properties`, keystore.properties loader with graceful fallback, signingConfigs block, release build type wiring. Added keystore entries to .gitignore. Created docs/release/signing-guide.md.
- **Task 4: Version bump** — Updated versionName from 0.1.0 to 1.0.0. Updated CHANGELOG.md with comprehensive v1.0.0 release notes covering all features.
- **Task 5: Privacy policy** — Created docs/release/privacy-policy.md covering step data, Health Connect, local storage, third-party SDKs. Updated HealthConnectPermissionActivity with scrollable structured privacy content.
- **Task 6: Play Store listing** — Created docs/release/play-store-listing.md (short/full descriptions, category, content rating notes). Created docs/release/release-checklist.md.
- **Task 7: Build verification** — All 347 tests pass. Release APK builds successfully (26MB unsigned, R8 minification clean). Fixed Gradle DSL issue with java.util.Properties import.

### Build verification results
- `testDebugUnitTest`: BUILD SUCCESSFUL (347 tests, all green)
- `assembleRelease`: BUILD SUCCESSFUL (26MB unsigned APK, R8 clean)
- Only warnings: 4 redundant conversion calls, 6 hiltViewModel() deprecations (pre-existing)

### Files created
- `docs/release/privacy-policy.md`
- `docs/release/play-store-listing.md`
- `docs/release/signing-guide.md`
- `docs/release/release-checklist.md`

### Files modified
- `app/proguard-rules.pro` — hardened R8 rules
- `app/build.gradle.kts` — signing config, version 1.0.0
- `app/src/main/java/.../di/DatabaseModule.kt` — removed fallbackToDestructiveMigration
- `app/src/main/java/.../presentation/HealthConnectPermissionActivity.kt` — expanded privacy content
- `CHANGELOG.md` — v1.0.0 release notes
- `.gitignore` — keystore entries

### What remains
- Plan 31: Play Console & Store Publication
- Generate upload keystore (manual step)
- Host privacy policy at public URL
- Create visual assets (icon, screenshots, feature graphic)
- Replace contact email placeholders

---

## 2026-03-11 — Remediation Plan Creation

### Context
- External code review completed (`docs/external-reviews/REPO_ANALYSIS_BUGS_AND_UX.md`) identifying 12 high-priority findings across step integrity, battle wiring, database safety, widget, missions, notifications, deep-links, premium state, UX feedback, accessibility, and test coverage.
- Plan 30 was complete; Plan 31 was next on the critical path.

### What was done
- Created `docs/plans/plan-R-remediation.md` — 12 sub-plans (R01–R12) organized into 3 priority tiers.
- Updated `docs/plans/master-plan.md`:
  - Added Plan R to plan index table.
  - Updated dependency graph: Plan 30 → Plan R → Plan 31.
  - Updated critical path to include Plan R (Tier 1) before Plan 31.
  - Added Plan R to status tracker.
- Updated `docs/agent/STATE.md` — current objective is now Plan R; priorities and next actions reflect remediation order.

### Key decisions
- Plan R Tier 1 (R01–R05) blocks production release (Plan 31). These are data-integrity and progression-correctness issues.
- Plan R Tier 2 (R06–R09) should complete before release but are user-trust issues, not data corruption risks.
- Plan R Tier 3 (R10–R12) can follow shortly after release.
- R01 → R02 is the only sequential dependency within remediation. All other sub-plans are parallelizable.

### What remains
- Execute R01–R12 per priority tiers.
- Plan 31 after R Tier 1 complete.

---

## 2026-03-11 — R01: Step Ingestion Unification

### What was done
- Created `data/sensor/StepIngestionPreferences.kt` — SharedPreferences wrapper with service heartbeat (2-min threshold) and date-scoped day-start counter.
- Refactored `service/StepSyncWorker.kt` — removed private `last_counter_value` baseline. Worker now checks heartbeat (skips if service alive), uses Room `sensorSteps` as authoritative baseline, and only credits the uncredited gap.
- Updated `service/StepCounterService.kt` — writes heartbeat on every step credit, sets day-start counter on startup via one-shot sensor read.
- Created `StepIngestionPreferencesTest.kt` (11 tests) — heartbeat read/write, isServiceAlive, day-start counter, day rollover.
- Created `StepIngestionTest.kt` (10 tests) — service-active skip, gap recovery, day rollover, no double-credit, counter reboot safety.
- All 368 tests pass. Debug build compiles clean.

### Key design decisions
- Two-mechanism approach: heartbeat (optimization) + Room baseline (correctness). Heartbeat prevents unnecessary sensor reads; Room baseline guarantees no double-credit even under race conditions.
- Day-start counter set by whichever path (service or worker) reads the sensor first today. Service sets it on startup; worker sets it if service never ran.
- Worker's old private `last_counter_value` replaced entirely — no migration needed since it was only used for catch-up delta computation.

### What remains
- R02: Escrow Redesign (next — depends on R01 ✓)
- R03–R12: remaining remediation sub-plans

---

## 2026-03-11 — R02: Escrow Redesign

### What was done
- Modified `PlayerProfileDao.adjustStepBalance` — added `MAX(0, ...)` clamp to prevent negative balances on any spend operation.
- Rewrote `StepCrossValidator.validate()` — escrow now deducts excess from player balance via `spendSteps()`. Release restores via `addSteps()`. Discard leaves deduction in place. Level 0/1 branches track whether escrow was already deducted to avoid double-deduction on subsequent syncs.
- Rewrote `StepCrossValidatorTest` — 10 tests (was 5): added balance deduction verification on all escrow branches, no-double-deduction on subsequent syncs, escrow→release net-zero test, escrow→discard keeps-deduction test.
- All 373 tests pass. Build clean.

### Key design decisions
- Deduct-on-escrow approach: simplest correct fix, no schema changes, no new domain concepts.
- Balance clamped to zero: prevents negative balances if player spent suspicious steps before reconciliation.
- Level 0/1 branches check `record.escrowSteps == 0L` to distinguish first escrow (deduct) from subsequent syncs (metadata only).

### What remains
- R03–R12: remaining remediation sub-plans (all Tier 1 blockers now independent)

---

## 2026-03-11 — R03+R04: Battle Workshop Wiring + Dead Upgrade Cleanup

### What was done
- R03: Exposed `workshopLevels` from BattleViewModel (was private). Replaced `emptyMap()` with real workshop levels in both `BattleScreen.LaunchedEffect` and `BattleViewModel.playAgain()`. CASH_BONUS, CASH_PER_WAVE, and INTEREST now reach the GameEngine.
- R04: Added `hiddenUpgrades` set in WorkshopViewModel filtering out STEP_MULTIPLIER and RECOVERY_PACKAGES from the workshop UI. Enum entries preserved for future implementation.
- All 373 tests pass. Build clean.

### What remains
- R05: Database Safety (last Tier 1 blocker)
- R06–R12: Tier 2 and 3 remediation

---

## 2026-03-11 — R05: Database Safety

### What was done
- Disabled backup in AndroidManifest (`allowBackup="false"`). No valuable state to restore in a local-only game.
- Added `fallbackToDestructiveMigration()` in DatabaseModule for pre-release schema mismatch safety.
- Added try/catch recovery in `DatabaseKeyManager.getPassphrase()` — on decryption failure (keystore mismatch after restore), wipes stale passphrase blob and generates fresh key.
- All 373 tests pass. Build clean.

### Key decisions
- Backup disabled entirely rather than selective exclusion — simpler, eliminates the whole class of restore bugs.
- Destructive migration is pre-release only. CONSTRAINTS.md already mandates explicit migrations post-release.

### Milestone
- **Tier 1 remediation complete** (R01–R05). Plan 31 is now unblocked.

### What remains
- R06–R12: Tier 2 and 3 remediation
- Plan 31: Play Console & Store Publication

---

## 2026-03-11 — Documentation Sweep (Post-R05)

### Objective
Full codebase documentation audit after R01–R05 remediation. Find and fix stale references.

### Issues found and fixed (8 files)

1. **CHANGELOG.md** — Test count 347→373. Added R01–R05 remediation section.
2. **docs/release/release-checklist.md** — Unchecked `fallbackToDestructiveMigration` (R05 re-added it for pre-release safety). Updated test count 347→373.
3. **docs/step-tracking.md** — Added R01 service↔worker coordination section (heartbeat, Room baseline, day-start counter). Updated escrow table for R02 balance deduction behavior. Updated data flow diagram with heartbeat and gap recovery steps.
4. **docs/database-schema.md** — Added R05 key recovery mechanism and backup-disabled note to Security section.
5. **docs/architecture.md** — Added backup-disabled row and key auto-recovery note to Security table.
6. **.kiro/steering/source-files.md** — Added 7 missing test fakes from Plan 29 (FakeStepRepository, FakeCosmeticRepository, FakeBillingManager, FakeRewardAdManager, FakeDailyLoginDao, FakeWeeklyChallengeDao, FakeDailyStepDao).
7. **.kiro/steering/structure.md** — Same 7 missing fakes added to fakes directory listing.
8. **AGENTS.md** — Same 7 missing fakes added. Updated test coverage description with StepIngestionPreferences and StepIngestion test areas.

### Verified as correct (no changes needed)
- Google Fit references in RUN_LOG, ADR-0002, plan-02, plan-03, plan-05 — all historical/contextual.
- AGENTS.md test count (373), use case count (32), route count (12), repository count (8) — all accurate.
- database-schema.md entity schemas — all match actual code.
- monetization.md — accurate, reflects stub implementation status.
- master-plan.md — status tracker correct (Plan R unchecked, all others accurate).
- step-tracking.md anti-cheat rules — all thresholds match code.

### Commands/tests run: N/A (documentation-only changes)
### Memory updated: STATE ✅ / RUN_LOG ✅

## 2026-03-12 — R06 Widget Fix + R07 Live Mission Progress

**Objective:** Fix two Tier 2 High-severity bugs from external code review.

**R06 — Widget Fix (3 changes):**
- `DailyStepManager.recordSteps()`: replaced hardcoded `0` balance with `playerRepository.getStepBalance()` call.
- `widget_step_counter.xml`: added `android:id="@+id/widget_root"` to root LinearLayout.
- `StepWidgetProvider.updateAllWidgets()`: changed click PendingIntent target from `android.R.id.background` to `R.id.widget_root`.
- Added `getStepBalance()` to `PlayerRepository` interface, `PlayerRepositoryImpl`, and `FakePlayerRepository`.

**R07 — Live Mission Progress (2 changes):**
- Added `DailyMissionDao` as constructor dependency to `DailyStepManager`.
- Added `updateWalkingMissions()` private method called after economy rewards in `recordSteps()`. Queries today's missions, filters to unclaimed/incomplete WALKING missions, updates progress based on `dailyCreditedTotal`.
- Hilt auto-resolves the new dependency (DailyMissionDao already provided by DatabaseModule).

**Tests added:** 6 new tests in `DailyStepManagerTest.kt`:
- Widget receives real step balance after crediting
- Widget balance accumulates across multiple credits
- Walking mission progress updates on step credit
- Walking mission completes when target reached
- Battle mission is not updated by step credits
- Already completed mission is not re-updated

**Test count:** 373 → 379 (all green).

**Files changed:**
- `domain/repository/PlayerRepository.kt` — added `getStepBalance()`
- `data/repository/PlayerRepositoryImpl.kt` — implemented `getStepBalance()`
- `data/sensor/DailyStepManager.kt` — real widget balance, DailyMissionDao dep, walking mission updates
- `service/StepWidgetProvider.kt` — fixed click target to `R.id.widget_root`
- `res/layout/widget_step_counter.xml` — added `android:id` to root
- `test/fakes/FakePlayerRepository.kt` — added `getStepBalance()`
- `test/data/sensor/DailyStepManagerTest.kt` — new test file (6 tests)

**What's next:** R08 (Notification & Reminder Fixes) + R09 (Deep-link & Premium State), parallelizable.

## 2026-03-12 — R08 Notification & Reminder Fixes + R09 Deep-link & Premium State

**Objective:** Fix two Tier 2 Medium-severity issues from external code review.

**R08 — Notification & Reminder Fixes (2 changes):**
- `NotificationSettingsScreen.kt`: Renamed "Step Counter" / "Persistent notification with daily steps" to "Step Count Updates" / "Show step count and balance in the notification" — accurately describes what the toggle controls.
- Added `updateLastActiveAt(timestamp)` to `PlayerRepository` interface, `PlayerRepositoryImpl`, and `FakePlayerRepository`. Called from `MainActivity.onResume()` so `SmartReminderManager` has a fresh timestamp.

**R09 — Deep-link & Premium State (3 changes):**
- `MainActivity`: Added `pendingNavigation: MutableStateFlow<String?>`, `onNewIntent()` override, and a `LaunchedEffect` that collects the flow. Consolidates cold-start and warm-start deep-link handling. Supply drop notifications now navigate correctly when app is already open.
- `StoreViewModel`: Added expiry check — `seasonPassActive = profile.seasonPassActive && profile.seasonPassExpiry > System.currentTimeMillis()` — matching HomeViewModel's logic.
- `BattleViewModel.playAgain()`: Added `adRemoved = it.adRemoved` to the new `BattleUiState` constructor, preserving ad-free state across replays.

**Tests added:** 2 new tests in `StoreViewModelSeasonPassTest`:
- Expired season pass shows as inactive
- Active season pass with future expiry shows as active

**Test count:** 379 → 381 (all green).

**Files changed:**
- `domain/repository/PlayerRepository.kt` — added `updateLastActiveAt()`
- `data/repository/PlayerRepositoryImpl.kt` — implemented `updateLastActiveAt()`
- `presentation/MainActivity.kt` — onResume, onNewIntent, pendingNavigation flow, onDestroy
- `presentation/settings/NotificationSettingsScreen.kt` — renamed toggle label
- `presentation/store/StoreViewModel.kt` — season pass expiry check
- `presentation/battle/BattleViewModel.kt` — preserve adRemoved on playAgain
- `test/fakes/FakePlayerRepository.kt` — added `updateLastActiveAt()`
- `test/presentation/store/StoreViewModelTest.kt` — 2 new season pass tests

**What's next:** R10 (UX Feedback & Guards) + R11 (Accessibility & Docs), parallelizable.

## 2026-03-12 — R10 UX Feedback & Guards + R11 Accessibility & Docs

**Objective:** Fix three UX issues (silent failures, double-tap races, midnight staleness) and three polish issues (symbol-only labels, placeholder emails, README inaccuracies).

**R10 — UX Feedback & Guards (7 changes):**
- `PlayerProfileDao`: Added `MAX(0, ...)` guards to `adjustGems`, `adjustPowerStones`, `adjustCardDust` — matching existing `adjustStepBalance` pattern.
- `WorkshopUiState`, `CardsUiState`, `LabsUiState`, `StoreUiState`: Added `userMessage: String?` and `isProcessing: Boolean` fields.
- `WorkshopViewModel`, `CardsViewModel`, `LabsViewModel`, `StoreViewModel`: Added `clearMessage()`, processing guards on all purchase/action methods (early return if `_processing.value`), feedback messages on failures (insufficient funds, max level, no slots).
- `BattleViewModel`: Added VM-level guards to `watchGemAd`/`watchPsAd` — early return if already watched.
- `WorkshopScreen`, `CardsScreen`, `LabsScreen`, `StoreScreen`: Wrapped content in `Scaffold` with `SnackbarHost`. Added `LaunchedEffect(state.userMessage)` to show snackbar and clear.
- `MissionsViewModel`: Changed `today` from `val` to `var`. Added day-change detection in existing 1s ticker — regenerates missions and updates walking progress on midnight crossing.
- `HomeViewModel`: Changed hardcoded `LocalDate.now()` to `MutableStateFlow<String>` with `flatMapLatest`. Added `refreshDate()` called from `HomeScreen` via lifecycle resume observer.
- `StatsViewModel`: Changed `today` from `val` to `MutableStateFlow<LocalDate>` with `flatMapLatest`. Added `refreshDate()` called from `StatsScreen` via lifecycle resume observer.
- `FakePlayerRepository`: Updated spend methods to clamp at 0, matching DAO guards.

**R11 — Accessibility & Docs (4 changes):**
- `BattleScreen`: Added `contentDescription` via `semantics` to speed buttons ("Speed 1x/2x/4x"), pause/resume button, upgrades button, overdrive button.
- `UltimateWeaponBar`: Added `semantics { contentDescription }` to weapon slots — "Activate {name}" when ready, "{name} on cooldown, N seconds" when not.
- `HomeScreen`: Added `contentDescription` to supplies badge button.
- Replaced `<contact-email>` with `support@whitefanggames.com` in `privacy-policy.md`, `play-store-listing.md`, `HealthConnectPermissionActivity.kt`.
- `README.md`: Replaced instrumented test section with note that they're planned but not yet implemented.

**Tests added:** 7 new tests:
- `CurrencyGuardTest` (4): gems/PS/dust/steps spend-beyond-balance clamps to 0.
- `UserFeedbackTest` (3): workshop purchase failure sets userMessage, clearMessage resets, quickInvest failure sets message.

**Test count:** 381 → 388 (all green).

**Files changed:**
- `data/local/PlayerProfileDao.kt` — MAX(0) guards on 3 currency queries
- `presentation/workshop/WorkshopUiState.kt` — added isProcessing, userMessage
- `presentation/cards/CardsUiState.kt` — added isProcessing, userMessage
- `presentation/labs/LabsUiState.kt` — added isProcessing, userMessage
- `presentation/store/StoreUiState.kt` — added userMessage
- `presentation/workshop/WorkshopViewModel.kt` — rewritten with guards + feedback
- `presentation/cards/CardsViewModel.kt` — rewritten with guards + feedback
- `presentation/labs/LabsViewModel.kt` — rewritten with guards + feedback
- `presentation/store/StoreViewModel.kt` — rewritten with guards + feedback
- `presentation/battle/BattleViewModel.kt` — ad watch guards
- `presentation/workshop/WorkshopScreen.kt` — Scaffold + SnackbarHost
- `presentation/cards/CardsScreen.kt` — Scaffold + SnackbarHost
- `presentation/labs/LabsScreen.kt` — Scaffold + SnackbarHost
- `presentation/store/StoreScreen.kt` — Scaffold + SnackbarHost
- `presentation/missions/MissionsViewModel.kt` — midnight day-change detection
- `presentation/home/HomeViewModel.kt` — currentDate flow + refreshDate
- `presentation/home/HomeScreen.kt` — lifecycle resume observer
- `presentation/stats/StatsViewModel.kt` — today flow + refreshDate
- `presentation/stats/StatsScreen.kt` — lifecycle resume observer
- `presentation/battle/BattleScreen.kt` — contentDescription on all controls
- `presentation/battle/ui/UltimateWeaponBar.kt` — semantics on weapon slots
- `presentation/HealthConnectPermissionActivity.kt` — real email
- `docs/release/privacy-policy.md` — real email
- `docs/release/play-store-listing.md` — real email
- `README.md` — fixed instrumented test reference
- `test/fakes/FakePlayerRepository.kt` — spend clamps at 0
- `test/presentation/ux/CurrencyGuardTest.kt` — new (4 tests)
- `test/presentation/ux/UserFeedbackTest.kt` — new (3 tests)

**What's next:** R12 (Integration Test Coverage), then Plan 31 (Play Console & Store Publication).

## 2026-03-12 — R12 Integration Test Coverage

**Objective:** Add integration-level tests for widget, deep-links, Room schema, and escrow lifecycle.

**What was done:**
1. **Task 1 — Robolectric setup**: Added `robolectric:4.14.1`, `androidx.test:core:1.6.1`, and `room-testing` to version catalog + build.gradle.kts. Enabled `unitTests.isIncludeAndroidResources = true`.

2. **Task 2 — Widget tests** (`service/StepWidgetProviderTest.kt`, 3 tests): Robolectric-based tests verifying `saveData()` persists to SharedPreferences, overwrites work, and defaults are zero.

3. **Task 3 — Deep-link tests** (`presentation/DeepLinkRoutingTest.kt`, 3 tests): Verify `navigate_to` intent extra extraction for supplies, workshop, and null case.

4. **Task 4 — Room schema tests** (`data/local/RoomSchemaTest.kt`, 3 tests): In-memory Room DB round-trip for PlayerProfileEntity (gems/PS/tier), DailyStepRecordEntity (escrow fields), WorkshopUpgradeEntity (level).

5. **Task 5 — Escrow lifecycle tests** (`data/integration/EscrowLifecycleTest.kt`, 2 tests): Full lifecycle using FakePlayerRepository + FakeStepRepository + mocked HealthConnectStepReader. Test 1: escrow deducts → release restores (net zero). Test 2: escrow deducts → 3 syncs → discard keeps deduction.

**Decisions:**
- No instrumented tests (androidTest) — all tests run on JVM via Robolectric.
- No Room migration objects or migration tests — pre-release app, `fallbackToDestructiveMigration` handles dev/QA installs. Post-release migrations documented in CONSTRAINTS.md.
- Skipped Hilt-injected service lifecycle tests — StepCounterService is a thin shell around already-tested components.

**Test count:** 388 → 399 (all green).

**Files changed:**
- `gradle/libs.versions.toml` — added robolectric, androidx-test-core, room-testing
- `app/build.gradle.kts` — added 3 test dependencies, isIncludeAndroidResources
- `test/service/StepWidgetProviderTest.kt` — new (3 tests)
- `test/presentation/DeepLinkRoutingTest.kt` — new (3 tests)
- `test/data/local/RoomSchemaTest.kt` — new (3 tests)
- `test/data/integration/EscrowLifecycleTest.kt` — new (2 tests)

**Milestone:** Plan R (Remediation) fully complete. All 12 sub-plans done.

**What's next:** Plan 31: Play Console & Store Publication.

## 2026-03-12 — Documentation Sweep & Corrections

**Objective:** Full codebase sweep for outdated/incorrect documentation.

**What was done:**

1. **AGENTS.md — Plan count fixed**: "31-plan master plan" → "33 entries (Plans 01–31, 10b, and R)". Key documents table: "30 plans" → "33 entries".

2. **AGENTS.md — Missing use case**: Added `PurchaseGemPack` to architecture tree use case list (was 31, now 32 — matches codebase).

3. **README.md — Plan count fixed**: "30-plan development roadmap" → "33-entry development roadmap".

4. **structure.md — Test tree updated**: Added 16 missing test directories (data/healthconnect, data/local, data/integration, presentation/home, presentation/workshop, presentation/labs, presentation/cards, presentation/weapons, presentation/supplies, presentation/economy, presentation/missions, presentation/stats, presentation/store, presentation/ux, DeepLinkRoutingTest, service). Updated domain model/usecase descriptions.

5. **tech.md — Missing libraries added**: mockito-kotlin 5.4.0, robolectric 4.14.1, androidx-test-core 1.6.1, hilt-work 1.3.0, compose-material-icons.

6. **CHANGELOG.md — Structure fixed**: Moved [Unreleased] to top (for Plan 31 tracking). Folded historical scaffold/Plan 01 entries into v1.0.0 section.

7. **battle-formulas.md — Step Multiplier note**: Added note that STEP_MULTIPLIER is currently hidden from Workshop UI (R04 remediation).

8. **plan-05 filename renamed**: `plan-05-google-fit.md` → `plan-05-health-connect.md`. Updated master-plan.md link.

9. **Version catalog cleanup**: Removed unused `kotlin-android` plugin from `libs.versions.toml`.

10. **PurchaseGemPackTest added**: 2 tests (success delegation, error forwarding). Closes the only use case without test coverage. Updated source-files.md.

11. **Test count updated**: 399 → 401 across AGENTS.md, CHANGELOG.md, STATE.md, release-checklist.md.

**Test count:** 399 → 401 (all green).

**Files changed:**
- `AGENTS.md` — plan count (×2), use case list, test count
- `README.md` — plan count
- `.kiro/steering/structure.md` — test tree
- `.kiro/steering/tech.md` — library table
- `.kiro/steering/source-files.md` — added PurchaseGemPackTest
- `CHANGELOG.md` — section order, test count
- `docs/battle-formulas.md` — Step Multiplier note
- `docs/plans/master-plan.md` — plan-05 link
- `docs/plans/plan-05-health-connect.md` — renamed from plan-05-google-fit.md
- `docs/release/release-checklist.md` — test count
- `docs/agent/STATE.md` — test count
- `gradle/libs.versions.toml` — removed unused kotlin-android plugin
- `test/domain/usecase/PurchaseGemPackTest.kt` — new (2 tests)

**What's next:** Plan 31: Play Console & Store Publication.

---

## 2026-03-13 — R2 Remediation Plan Creation

**Trigger:** Second external code review (`docs/external-reviews/REPO_ANALYSIS_BUGS_AND_UX_2.md`).

**What I did:**
1. Read the full second external review (10 sections, 8 high-priority findings, ~30 total findings).
2. Cross-referenced every finding against Plan R (first remediation, R01–R12, all complete) to identify what's genuinely new vs. already addressed.
3. Verified current code state for all key findings: `DailyStepManager.recordActivityMinutes()`, `ClaimMilestone`, `stateIn(viewModelScope).value` pattern (12 occurrences), `PostRoundOverlay` button text, `NotificationSettingsScreen` wording, `DatabaseModule` migration config, `LabsViewModel.freeRush()`, `StoreScreen` cosmetics, `CurrencyDashboardViewModel`.
4. Created `docs/plans/plan-R2-remediation.md` with 12 sub-plans (R2-01 through R2-12), organized by severity and dependency.
5. Updated `docs/agent/STATE.md` with new objective, priorities, and references.

**Key findings confirmed as genuinely new/unresolved:**
- Activity-minute crediting is non-idempotent (Critical) — `recordActivityMinutes()` re-credits on process restart because `dailyCreditedTotal` initializes from `creditedSteps` (sensor-only), not including prior `stepEquivalents`.
- Activity-minute pipeline bypasses widget/mission/drop/economy updates (High).
- 12 `stateIn(viewModelScope).value` occurrences still present across 4 ViewModels (High).
- "Return to Workshop" label still present (High).
- Notification setting wording unchanged (High).
- `.fallbackToDestructiveMigration()` still in DatabaseModule (High).
- `freeRush()` still has silent returns (Medium).
- `ClaimMilestone` still lacks step-threshold check (Medium).
- Cosmetics still purchasable with "coming soon" label (Medium).
- CurrencyDashboard still snapshot-based (Medium).

**Files created:**
- `docs/plans/plan-R2-remediation.md`

**Files updated:**
- `docs/agent/STATE.md`
- `docs/agent/RUN_LOG.md`

**What's next:** Begin R2-01 (Activity-Minute Idempotency), then R2-02, R2-06, R2-03 in priority order.

## 2026-03-13 — R2-01: Activity-Minute Idempotency

**Objective:** Fix double-crediting of activity-minute step-equivalents on process restart.

**Root cause:** `recordActivityMinutes()` initialized `dailyCreditedTotal` from `existing.creditedSteps` (sensor-only), ignoring previously credited `stepEquivalents`. The worker passes cumulative `stepEquivalents` from `ActivityMinuteConverter`, and the manager called `playerRepository.addSteps(credited)` with the full amount each time instead of just the delta.

**What was done:**
1. Extracted shared `ensureInitialized()` method from duplicated init blocks in `recordSteps()` and `recordActivityMinutes()`. Initialization now sets `dailyCreditedTotal = creditedSteps + stepEquivalents` (combined ceiling).
2. Added `dailySensorCredited` field to track sensor-only credits for Room's `creditedSteps` field (prevents writing combined total into sensor-only column).
3. Added `dailyActivityMinuteTotal` field initialized from `existing.stepEquivalents` during init.
4. Made `recordActivityMinutes()` delta-based: computes `delta = stepEquivalents - dailyActivityMinuteTotal`, only credits positive delta. Stores `dailyActivityMinuteTotal` (actual credited, respecting ceiling) to Room, not raw input.

**Bug caught during implementation:** Initial version wrote `dailyCreditedTotal` (now combined sensor + activity) to Room's `creditedSteps` field via `updateDailySteps()`. This would have caused double-counting on next init since `ensureInitialized()` reads `creditedSteps + stepEquivalents`. Fixed by adding `dailySensorCredited` to track sensor credits separately for the Room write.

**Tests added (5):**
- Activity minutes credit correct step-equivalents (baseline)
- Duplicate call produces zero additional credits (idempotency)
- Incremental call credits only delta
- Combined sensor + activity-minute credits respect 50k ceiling
- Process restart does not re-credit activity minutes (new manager instance, same repos)

**Test count:** 397 JVM tests — all green, 0 failures.

**Files changed:**
- `data/sensor/DailyStepManager.kt` — extracted `ensureInitialized()`, added `dailySensorCredited` + `dailyActivityMinuteTotal`, delta-based `recordActivityMinutes()`
- `test/data/sensor/DailyStepManagerTest.kt` — 5 new tests

**What's next:** R2-02 (Activity-Minute Pipeline Unification), then R2-06, R2-03.

## 2026-03-13 — R2-02: Activity-Minute Pipeline Unification

**Objective:** Route activity-minute credits through the same follow-on pipeline as sensor steps (widget, supply drops, economy, missions).

**Root cause:** `recordActivityMinutes()` only called `stepRepository.updateActivityMinutes()` and `playerRepository.addSteps()`. It skipped widget updates, supply drop generation, economy rewards (daily login, weekly challenge), and walking mission progress that `recordSteps()` performs.

**What was done:**
1. Extracted the follow-on pipeline (widget update, supply drop generation, economy rewards, walking mission progress) from `recordSteps()` into `private suspend fun runFollowOnPipeline(timestampMs: Long)`.
2. `recordSteps()` now calls `runFollowOnPipeline(timestampMs)` instead of inlining the pipeline.
3. `recordActivityMinutes()` now accepts `timestampMs: Long = System.currentTimeMillis()` and calls `runFollowOnPipeline(timestampMs)` after crediting steps.
4. Each pipeline section wrapped in try/catch for best-effort consistency (supply drop generation was previously unwrapped — now consistent).
5. No changes needed to `StepSyncWorker.kt` — the new `timestampMs` parameter has a default value.

**Files changed:**
- `data/sensor/DailyStepManager.kt` — extracted `runFollowOnPipeline()`, called from both methods

**Test count:** 397 JVM tests — all green, 0 failures. No new tests (R2-12 adds coverage).

**What's next:** R2-06 (Destructive Migration Removal), then R2-03, R2-04/05/07, R2-12.

## 2026-03-13 — R2-03: Hot Flow Cleanup

**Objective:** Replace 12 `observeX().stateIn(viewModelScope).value` calls in ViewModel action handlers with `first()` or `uiState.value` reads. Each leaked call created a hot StateFlow tied to the ViewModel scope that was never cancelled.

**What was done:**
1. **WorkshopViewModel** (2 occurrences): Replaced `observeWallet().stateIn(viewModelScope).value` with `observeWallet().first()` in `purchase()` and `quickInvest()`. Use cases require full `PlayerWallet` not available in `uiState`. Removed unused `import kotlinx.coroutines.flow.update`.
2. **CardsViewModel** (3 occurrences): Replaced `observeProfile().stateIn(viewModelScope).value` with `uiState.value` reads in `openPack()` (`.gems`), `upgradeCard()` (`.cardDust`), and `watchFreePackAd()` (`.gems`). All values already materialized in UI state.
3. **LabsViewModel** (6 occurrences): 5 replaced with `first()` — `startResearch()`, `rushResearch()` (profile + activeList), `freeRush()` (profile + activeList) — needed full domain objects (`profile.toWallet()`, season pass fields). 1 replaced with `uiState.value` — `unlockSlot()` only needed `totalSlots` and `gems`.
4. **StoreViewModel** (1 occurrence): Replaced `observeProfile().stateIn(viewModelScope).value` with `uiState.value.gems` in `purchaseCosmetic()`.

**Verification:**
- `grep stateIn(viewModelScope).value` across presentation/ returns 0 matches
- All 397 JVM tests pass, 0 failures

**Files changed:**
- `presentation/workshop/WorkshopViewModel.kt` — 2 fixes + 1 unused import removed
- `presentation/cards/CardsViewModel.kt` — 3 fixes
- `presentation/labs/LabsViewModel.kt` — 6 fixes + `first` import added
- `presentation/store/StoreViewModel.kt` — 1 fix

**What's next:** R2-06 (Destructive Migration Removal), then R2-04/05/07, R2-12.

## 2026-03-13 — R2-04: Battle Exit Navigation

**Objective:** Fix "Return to Workshop" button label/behavior mismatch in PostRoundOverlay. The button calls `navController.popBackStack()` which returns to whatever screen preceded battle, not necessarily Workshop.

**What was done:**
1. Renamed parameter `onReturnToWorkshop` → `onExitBattle` in `PostRoundOverlay.kt` (matches `BattleScreen`'s existing `onExitBattle` naming).
2. Changed button text from "Return to Workshop" → "Leave Battle".
3. Updated named argument at call site in `BattleScreen.kt` from `onReturnToWorkshop =` → `onExitBattle =`.

**Verification:**
- `grep onReturnToWorkshop|Return to Workshop` across `app/src/` returns 0 matches
- Build successful, all 397 JVM tests pass

**Files changed:**
- `presentation/battle/ui/PostRoundOverlay.kt` — parameter rename + button text
- `presentation/battle/BattleScreen.kt` — call site named argument

**What's next:** R2-06 (Destructive Migration Removal), then R2-05/07, R2-12.

## 2026-03-13 — R2-05: Notification Setting Alignment

**Objective:** Fix misleading "Step Count Updates" toggle that implies users can hide the foreground notification entirely. Android requires a visible notification for foreground services.

**What was done:**
1. Renamed toggle title "Step Count Updates" → "Live Step Updates" in `NotificationSettingsScreen.kt`.
2. Updated description to: "Update notification with live step count and balance. A minimal tracking notification is always shown while step counting is active."
3. Added `buildMinimalNotification()` to `StepNotificationManager.kt` — shows "Step tracking active" with no counts/balance/action buttons.
4. Injected `NotificationPreferences` into `StepCounterService.kt` and added preference check in `onCreate()` to choose full vs minimal notification at startup.

**Verification:**
- Build successful, all 397 JVM tests pass
- Toggle ON → full notification with live counts + Workshop/Battle buttons
- Toggle OFF → clean "Step tracking active" notification, no frozen zeroes

**Files changed:**
- `presentation/settings/NotificationSettingsScreen.kt` — toggle title + description text
- `service/StepNotificationManager.kt` — added `buildMinimalNotification()`
- `service/StepCounterService.kt` — injected NotificationPreferences, preference-aware initial notification

**What's next:** R2-06 (Destructive Migration Removal), then R2-07, R2-12.

## 2026-03-13 — R2-06 through R2-12 (Final Remediation)

**Objective:** Complete all remaining R2 sub-plans.

**What was done:**
- R2-06: Replaced `.fallbackToDestructiveMigration()` with `.fallbackToDestructiveMigrationOnDowngrade(dropAllTables = true)` in `DatabaseModule.kt`. Schema upgrades without explicit Migration now crash (fail-fast) instead of silently wiping data.
- R2-07: Added `Log.w("StepSyncWorker", ...)` to both silent catch blocks in `StepSyncWorker.kt` (HC sync and smart reminders).
- R2-09: Added user messages to all 3 silent early-return paths in `LabsViewModel.freeRush()`: "Season Pass required", "Free rush already used today", "No active research to rush".
- R2-11: Disabled cosmetic purchase buttons in `StoreScreen.kt` — unowned cosmetics show disabled "Coming Soon" button. Updated description text. Equip/Unequip still works for owned items.
- R2-08: (a) Added step-threshold validation to `ClaimMilestone` — reads `totalStepsEarned` and returns `false` if below `milestone.requiredSteps`. (b) Created `MilestoneNotificationPreferences` (SharedPreferences wrapper) for notification dedup. Wired into `HomeViewModel` — milestone notifications now fire at most once per milestone.
- R2-10: Rewrote `CurrencyDashboardViewModel` with hybrid reactive approach — `combine()` of live `observeProfile()` flow + `MutableStateFlow<SnapshotData>` for weekly/login data. Added `refresh()` method. Added `LaunchedEffect(Unit)` in `CurrencyDashboardScreen` for refresh on entry.
- R2-12: Added 2 remaining activity-minute tests (walking mission progress + widget updates). 4 of 6 tests already existed from R2-01.

**Tests:** 401 JVM tests, all green (was 397). Added: 1 ClaimMilestone threshold test, 1 CurrencyDashboard reactive test, 2 activity-minute pipeline tests.

**Decisions:**
- Used SharedPreferences (not Room column) for milestone notification dedup — it's a UI concern, not game state. Avoids schema v8 migration.
- Used `dropAllTables = true` parameter on `fallbackToDestructiveMigrationOnDowngrade()` to avoid Room deprecation warning.
- Hybrid reactive approach for economy dashboard: live profile flow for balances, one-shot refresh for weekly/login data.

**What remains:** Plan R2 fully complete. Plan 31 (Play Console & Store Publication) is unblocked.

## 2026-05-03 — Feature: Battle Step Rewards (ADR-0003)

**Trigger:** Player-facing feature request. "Killing enemies in a round gives steps as a reward, to add incentive to playing."

**Scope:** Add Steps as an enemy-kill reward separate from the walking pipeline, with a per-day cap, running HUD counter, floating +N Step text on kill, and a Round End summary line item.

**Design decisions:** See ADR-0003.
- Small supplement (BASIC/FAST/SCATTER=1, RANGED=2, TANK=3, BOSS=10). ~350–550 Steps per typical round.
- 2,000 battle-Steps/day cap, tracked on `DailyStepRecordEntity.battleStepsEarned`. Separate from the 50k walking ceiling (never additive).
- Flat per-enemy-type rewards — NOT multiplied by Fortune overdrive, Cash Bonus upgrade, or Golden Ziggurat UW. Anti-cheat-predictable.
- Credit immediately on each kill via callback → coroutine → use case (game loop must not suspend).
- Room v7 → v8 migration: first explicit `Migration` object in the project (stored in new `data/local/Migrations.kt`).

**What was done (9 tasks):**
1. Added `EnemyScaler.stepReward(type)` with agreed constants. `EnemyScalerTest` extended with per-type assertions + positive-for-all-types regression.
2. Added `battleStepsEarned: Long = 0` to `DailyStepRecordEntity`. Bumped `@Database(version = 7)` → `8`. Created `data/local/Migrations.kt` with `MIGRATION_7_8`. Wired `.addMigrations(*AppMigrations.ALL)` in `DatabaseModule`. Added DAO methods `getBattleStepsEarned(date)` (COALESCE→0) and `incrementBattleSteps(date, delta)` (UPSERT via `INSERT ... ON CONFLICT(date) DO UPDATE`). Updated `FakeDailyStepDao`.
3. Created `domain/usecase/AwardBattleSteps.kt` with `DAILY_BATTLE_STEP_CAP = 2_000L`. Logic: skip if amount≤0; compute remaining from DAO; credit `min(amount, remaining)` via `addSteps` + `incrementBattleSteps`. `AwardBattleStepsTest` — 6 tests covering full/partial/exhausted/rollover/negative/dao-amount.
4. Wired `GameEngine`: `@Volatile totalStepsEarned: Long = 0`, `@Volatile onStepReward: ((Long) -> Unit)? = null`. Reset in `init()`. In `handleEnemyDeath`, compute `EnemyScaler.stepReward(enemy.enemyType)`, invoke callback, spawn green `FloatingText` at `y + 24f`. Extended `FloatingText` with `color` parameter (default unchanged yellow-gold, new `STEP_COLOR = 0xFF4CAF50`).
5. Injected `AwardBattleSteps` into `BattleViewModel`. Added `stepsEarnedThisRound: Long = 0` to `BattleUiState`, `stepsEarned: Long = 0` to `RoundEndState`. Extracted callback wiring into `@VisibleForTesting internal fun wireStepRewardCallback(engine)` — prevents test deadlock with the polling loop. Override `onCleared()` nulls the callback on the engine. `BattleViewModelTest` extended with 3 new tests.
6. Added HUD Step counter (green `👟 +N Steps`) in `BattleScreen.kt`'s top-left column, shown only when `stepsEarnedThisRound > 0`. Includes `contentDescription` for accessibility.
7. Added green "Steps" banner + "Steps Earned" StatRow in `PostRoundOverlay.kt`, shown when `stepsEarned > 0`. `BattleViewModel.endRound()` populates `RoundEndState.stepsEarned` from `_uiState.value.stepsEarnedThisRound` (capped credited amount).
8. Created ADR-0003. Updated `STATE.md` (feature status, DB v8), `CONSTRAINTS.md` (new anti-cheat invariant), appended this RUN_LOG entry.
9. Integration — see test/build results below.

**Test results:** `./run-gradle.sh test` — BUILD SUCCESSFUL, **412 JVM tests, 0 failures** (was 401, +11 new). `./run-gradle.sh assembleDebug` — BUILD SUCCESSFUL. Room schema v8 exported at `app/schemas/com.whitefang.stepsofbabylon.data.local.AppDatabase/8.json` with `battleStepsEarned INTEGER NOT NULL` column.

**Bug caught during verification:** Initial build failed with Hilt `MissingBinding` error because I had added `AwardBattleSteps` to `BattleViewModel`'s constructor. Project convention (verified across all 32 existing use cases) is that domain use cases are **instantiated inline inside ViewModels**, not injected via Hilt. Fixed by:
1. Removed `AwardBattleSteps` from constructor; added `DailyStepDao` instead (already provided by `DatabaseModule`).
2. Construct `private val awardBattleSteps = AwardBattleSteps(playerRepository, dailyStepDao)` inline, matching the pattern used by `UpdateBestWave`, `AwardWaveMilestone`, `ApplyCardEffects`, etc.
3. Updated `BattleViewModelTest.createVm()` to pass `dailyStepDao` instead of `awardBattleSteps`.

After the fix, tests pass on first try and assembleDebug is clean.

**Files changed:**
- `app/src/main/java/com/whitefang/stepsofbabylon/data/local/DailyStepRecordEntity.kt`
- `app/src/main/java/com/whitefang/stepsofbabylon/data/local/AppDatabase.kt`
- `app/src/main/java/com/whitefang/stepsofbabylon/data/local/Migrations.kt` (new)
- `app/src/main/java/com/whitefang/stepsofbabylon/data/local/DailyStepDao.kt`
- `app/src/main/java/com/whitefang/stepsofbabylon/di/DatabaseModule.kt`
- `app/src/main/java/com/whitefang/stepsofbabylon/domain/usecase/AwardBattleSteps.kt` (new)
- `app/src/main/java/com/whitefang/stepsofbabylon/presentation/battle/engine/EnemyScaler.kt`
- `app/src/main/java/com/whitefang/stepsofbabylon/presentation/battle/engine/GameEngine.kt`
- `app/src/main/java/com/whitefang/stepsofbabylon/presentation/battle/effects/FloatingText.kt`
- `app/src/main/java/com/whitefang/stepsofbabylon/presentation/battle/BattleUiState.kt`
- `app/src/main/java/com/whitefang/stepsofbabylon/presentation/battle/BattleViewModel.kt`
- `app/src/main/java/com/whitefang/stepsofbabylon/presentation/battle/BattleScreen.kt`
- `app/src/main/java/com/whitefang/stepsofbabylon/presentation/battle/ui/PostRoundOverlay.kt`
- `app/src/test/java/com/whitefang/stepsofbabylon/presentation/battle/engine/EnemyScalerTest.kt`
- `app/src/test/java/com/whitefang/stepsofbabylon/domain/usecase/AwardBattleStepsTest.kt` (new)
- `app/src/test/java/com/whitefang/stepsofbabylon/fakes/FakeDailyStepDao.kt`
- `app/src/test/java/com/whitefang/stepsofbabylon/presentation/battle/BattleViewModelTest.kt`
- `docs/agent/DECISIONS/ADR-0003-battle-step-rewards.md` (new)
- `docs/agent/STATE.md`
- `docs/agent/CONSTRAINTS.md`
- `docs/agent/RUN_LOG.md`

**What remains:** Plan 31 (Play Console & Store Publication).

---

## 2026-05-05 — Archaeology Phase 1: Non-Technical Summary

**Objective:** External prompt asked for a source-grounded, non-technical summary of the codebase.

**What was done:** Created `devdocs/archaeology/small_summary.md` (283 lines). Written directly from source (AndroidManifest, StepsOfBabylonApp, MainActivity, StepCounterService, DailyStepManager, AppDatabase, 23-entry UpgradeType, BattleViewModel, GameEngine, WaveSpawner, AwardBattleSteps, StoreScreen, SoundManager, StubBillingManager, StubRewardAdManager, build.gradle.kts, settings.gradle.kts, libs.versions.toml, res/ tree). Not derived from existing docs. Identifies primary deliverable (single `:app` module, v1.0.0), walks through user experience, core loop, complete-vs-evolving areas, and five uncertainties.

**Discrepancy logged (code is authoritative):** `.kiro/steering/source-files.md` still claims `AppDatabase` is version 7; code is version 8 with a registered v7→v8 `Migration` object. Summary flags this rather than silently correcting the steering file.

**No code or production docs modified.** No new dependencies. No build/test runs needed (documentation-only).

**Files created:**
- `devdocs/archaeology/small_summary.md`

**Memory updated:** STATE ✅ (no substantive status change — still Plan 31) / RUN_LOG ✅.

---

## 2026-05-05 — Archaeology Phase 2: Architecture + Deployment Intros

**Objective:** External prompt asked for two source-grounded intros for new engineers: architecture intro (data flow paths, abstractions, patterns, time/random/ID/config/cache/env) and deployment intro (build, run, test, package, ship).

**What was done:**
1. Searched for prior equivalents — none (`docs/architecture.md` is a short reference card, not an intro; `docs/database-schema.md` is a per-table reference). Proceeded with new files.
2. Read the full DI module set (`DatabaseModule`, `RepositoryModule`, `StepModule`, `HealthConnectModule`, `BillingModule`, `AdModule`), `DatabaseKeyManager`, `Migrations.kt`, `PlayerRepositoryImpl`, `PlayerProfileDao`, `StepSensorDataSource`, `StepCounterService`, `StepSyncWorker`, `StepSyncScheduler`, `StepIngestionPreferences`, `StepRateLimiter`, `AntiCheatPreferences`, `StepCrossValidator`, `HealthConnectClientWrapper`, `GameLoopThread`, `GameSurfaceView`, `BootReceiver`, `StepNotificationManager`, `WidgetUpdateHelper`, `StepWidgetProvider`, `Converters`, `run-gradle.sh`, `proguard-rules.pro`, `.gitignore`, `gradle.properties`, `local.properties`, `app/schemas/` tree, `network_security_config.xml`, `step_widget_info.xml`, signing-guide.md, release-checklist.md, the three stub use cases with injectable `Random` (`CalculateDamage`, `GenerateSupplyDrop`, `OpenCardPack`), and `GenerateDailyMissions` (date-seeded Random).
3. Confirmed no CI configuration of any kind exists in the repo (no `.github/`, `.circleci/`, `.gitlab-ci.yml`, Jenkinsfile, Dockerfile, fastlane, etc.).
4. Grep-verified: zero `BuildConfig` reads, zero `DataStore` references, zero `UUID.randomUUID()` calls, single `Room.databaseBuilder` (in `DatabaseModule`). 8 SharedPreferences files in use.
5. Wrote `devdocs/archaeology/intro2codebase.md` (480 lines):
   - 10-second mental model, six concrete entry points
   - Two main data flows diagrammed: step ingestion pipeline (with follow-on fan-out) and battle game loop (SurfaceView + dedicated thread + callbacks into ViewModel)
   - The four-layer trace used by every non-battle screen
   - Main abstractions: 6 Hilt modules, 8 repositories, services & adapters, presentation conventions
   - Explicit section on time / randomness / IDs / config / caching / persistence / environment access locations — flags that `Clock` is **not** abstracted, `Random` **is** injected in 3 use cases with the documented default-parameter pattern, and `GenerateDailyMissions` uses `Random(todayDate.hashCode())` deterministically
   - Design patterns, module boundaries (single `:app`), entry-point quick lookup
6. Wrote `devdocs/archaeology/intro2deployment.md` (464 lines):
   - Build system at a glance (version matrix, catalog-only policy)
   - Local build commands + `run-gradle.sh` non-TTY wrapper
   - CI status (none; release gates are manual)
   - Build types + R8 keep-rule inventory
   - Bundled assets (code, .ogg sound effects, schemas (reference only), network_security_config, widget XML) and known missing assets (no launcher icon, no localisation)
   - Release signing: opt-in `keystore.properties`, Play App Signing guidance, output paths
   - Testing pipeline (JVM-only, JUnit5 + Robolectric, ~412 tests; no instrumented tests yet)
   - Runtime deployment mechanisms (WorkManager 15-min periodic, foreground service, boot receiver, widget refresh, Health Connect sync)
   - Database migration process (`AppMigrations.ALL`, `fallbackToDestructiveMigrationOnDowngrade(dropAllTables=true)`)
   - Permissions and secrets, one-page quick reference

**Discrepancies logged (code is authoritative):**
   - `docs/database-schema.md` still says "Current schema version: 7"; code is v8 (`AppDatabase.kt`, `app/schemas/8.json`).
   - `.kiro/steering/source-files.md` describes `AppDatabase` as version 7.
   - `docs/architecture.md` doesn't mention `CosmeticEntity` and doesn't note `HealthConnectModule` is an empty placeholder — both verified in code.

**No code or production docs modified.** No new dependencies. No build/test runs needed (documentation-only).

**Files created:**
- `devdocs/archaeology/intro2codebase.md`
- `devdocs/archaeology/intro2deployment.md`

**Memory updated:** STATE ✅ (no substantive status change — still Plan 31) / RUN_LOG ✅.


## 2026-05-05 — Archaeology Phase 3: Deep Trace Analysis

**Objective:** External prompt asked for per-trace files under `devdocs/archaeology/traces/` covering every internal interface boundary and submodule-level interaction, end-to-end, using a fixed 10-section schema (Entry Point, Execution Path, Resource Management, Error Path, Performance Characteristics, Observable Effects, Why This Design, Feels Incomplete, Feels Vulnerable, Feels Like Bad Design).

**What was done:**
1. Context preflight: read `START_HERE.md`, `STATE.md`, `CONSTRAINTS.md`, RUN_LOG tail, ADRs 0001–0003. Confirmed repo is on `main`, working tree clean except for prior RUN_LOG edit and untracked `devdocs/` from Phase 2.
2. Surveyed existing archaeology output (`small_summary.md`, `intro2codebase.md`, `intro2deployment.md`) so Phase 3 deepens rather than duplicates. Confirmed Phase 2's entry-point quick-lookup table was a reasonable seed list for traces.
3. Enumerated the highest-value internal boundaries across: sensor HAL ↔ Flow ↔ service ↔ DailyStepManager ↔ Room; service ↔ WorkManager heartbeat; HC cross-validation state machine; runFollowOnPipeline fan-out; Compose ↔ SurfaceView; game-loop thread ↔ GameEngine (`@Volatile`); engine kill callback ↔ VM viewModelScope ↔ use case ↔ DAO; round-end cascade; generic VM-VM-DAO pattern (Workshop); notification PendingIntent ↔ MainActivity deep-link; `AppWidgetManager` IPC; `System.loadLibrary("sqlcipher")` + Keystore passphrase; `BootReceiver` restart.
4. Read all code on each trace path before writing — `StepSensorDataSource`, `StepCounterService`, `DailyStepManager`, `StepRateLimiter`, `StepVelocityAnalyzer`, `StepIngestionPreferences`, `StepSyncWorker`, `StepSyncScheduler`, `StepCrossValidator`, `StepGapFiller`, `HealthConnectClientWrapper`, `HealthConnectStepReader`, `ActivityMinuteConverter`, `ActivityMinuteValidator`, `ExerciseSessionReader`, `AntiCheatPreferences`, `GenerateSupplyDrop`, `WalkingEncounterRepositoryImpl`, `StepRepositoryImpl`, `DailyStepDao`, `DailyStepRecordEntity`, `Migrations.kt`, `PlayerRepositoryImpl`, `PlayerProfileDao`, `AppDatabase`, `DatabaseKeyManager`, all six DI modules, `MainActivity`, `BattleScreen`, `BattleViewModel`, `GameSurfaceView`, `GameLoopThread`, `GameEngine`, `WaveSpawner`, `CollisionSystem`, `EnemyEntity`, `ZigguratEntity`, `EnemyScaler`, `AwardBattleSteps`, `UpdateBestWave`, `AwardWaveMilestone`, `CheckTierUnlock`, `PurchaseUpgrade`, `WorkshopViewModel`, `HomeViewModel`, `StoreViewModel`, `StubBillingManager`, `StubRewardAdManager`, `SupplyDropNotificationManager`, `StepNotificationManager`, `SmartReminderManager`, `MilestoneNotificationManager`, `StepWidgetProvider`, `WidgetUpdateHelper`, `BootReceiver`, AndroidManifest.
5. Wrote 13 trace files + 1 README index under `devdocs/archaeology/traces/`:
   - `trace_01_step_sensor_to_room.md`
   - `trace_02_step_sync_worker_and_heartbeat_handoff.md`
   - `trace_03_hc_cross_validation_escrow.md`
   - `trace_04_follow_on_pipeline_fanout.md`
   - `trace_05_compose_to_surfaceview_boot.md`
   - `trace_06_game_loop_single_frame.md`
   - `trace_07_enemy_kill_and_battle_step_reward.md`
   - `trace_08_round_end_cascade.md`
   - `trace_09_workshop_purchase_flow.md`
   - `trace_10_supply_drop_to_deep_link.md`
   - `trace_11_widget_update.md`
   - `trace_12_db_bootstrap_and_keystore.md`
   - `trace_13_boot_recovery.md`
   - `README.md` (index with per-trace table, usage guide, and what Phase 3 deliberately omits)

**Key code facts verified during writing (no code changes):**
- `GameEngine.onStepReward: ((Long) -> Unit)?` is `@Volatile`; game thread invokes it; `BattleViewModel.wireStepRewardCallback` hops to `viewModelScope.launch` so the loop never blocks on Room.
- `AwardBattleSteps` does 3 statements (read cap, add steps, increment counter) with NO transaction wrapping — observed a partial-failure vulnerability.
- `DailyStepManager.runFollowOnPipeline` wraps each of its 5 stages in `try/catch (_: Exception) {}` individually.
- `StepCrossValidator` calls `playerRepository.spendSteps(excess)` destructively at Levels 0/1 *before* `updateEscrow`; separated by two different Room writes — partial-failure vulnerability.
- `DatabaseKeyManager.getPassphrase` wipes SharedPreferences on decrypt failure but does NOT wipe the DB file — existing SQLCipher DB becomes unreadable with the new passphrase on device-restore. The `Room.databaseBuilder` has `fallbackToDestructiveMigrationOnDowngrade(true)` but no `fallbackToDestructiveMigration()`, so this is a latent crash-on-launch.
- `GameLoopThread` uses `System.nanoTime()` directly (acceptable per `intro2codebase.md` §5); `TICK_NS = 16_666_667L`; speed multiplier scales the accumulator, not `dt`, so physics stays deterministic.
- `StepSyncWorker.sensorCatchUp` uses `StepIngestionPreferences.isServiceAlive(now)` with `HEARTBEAT_THRESHOLD_MS=120_000`; HC gap-filling reuses `dailyStepManager.recordSteps` so anti-cheat cannot be bypassed via HC.
- `MainActivity.pendingNavigation: MutableStateFlow<String?>` is the cold/warm deep-link carrier; `onNewIntent` + first `LaunchedEffect(Unit)` both write; collector `LaunchedEffect(Unit)` reads and nulls.

**No code or production docs modified.** No new dependencies. No build/test runs needed — pure documentation output grounded in code reading.

**Files created:**
- `devdocs/archaeology/traces/` (new directory)
- `devdocs/archaeology/traces/trace_01_step_sensor_to_room.md`
- `devdocs/archaeology/traces/trace_02_step_sync_worker_and_heartbeat_handoff.md`
- `devdocs/archaeology/traces/trace_03_hc_cross_validation_escrow.md`
- `devdocs/archaeology/traces/trace_04_follow_on_pipeline_fanout.md`
- `devdocs/archaeology/traces/trace_05_compose_to_surfaceview_boot.md`
- `devdocs/archaeology/traces/trace_06_game_loop_single_frame.md`
- `devdocs/archaeology/traces/trace_07_enemy_kill_and_battle_step_reward.md`
- `devdocs/archaeology/traces/trace_08_round_end_cascade.md`
- `devdocs/archaeology/traces/trace_09_workshop_purchase_flow.md`
- `devdocs/archaeology/traces/trace_10_supply_drop_to_deep_link.md`
- `devdocs/archaeology/traces/trace_11_widget_update.md`
- `devdocs/archaeology/traces/trace_12_db_bootstrap_and_keystore.md`
- `devdocs/archaeology/traces/trace_13_boot_recovery.md`
- `devdocs/archaeology/traces/README.md`

**Memory updated:** STATE ✅ (no substantive project-status change — still Plan 31; added pointer to new archaeology output) / RUN_LOG ✅. No ADR needed — the traces describe existing code behaviour; no new decisions were made.

**Follow-ups worth considering (for a future, distinct session — not implemented here):**
- Wrap `AwardBattleSteps` in a `@Transaction` suspend function to prevent the cap/wallet divergence observed in trace 07.
- Wrap `StepCrossValidator` Level-0/1 escrow writes in a transaction so `spendSteps` + `updateEscrow` commit atomically (trace 03).
- `DatabaseKeyManager` should wipe the DB file alongside the encrypted-passphrase reset, to avoid crash-on-launch after device restore (trace 12).
- Missing deep-link routes in `MainActivity.pendingNavigation` collector: Store / Stats / etc. not handled; some notification paths already target Missions, Workshop, Battle, Supplies (trace 10).
- `PlaceholderScreen` dead composable in `MainActivity.kt` — documented but not yet removed (noted across Phase 1 and trace 05).

## 2026-05-05 — Archaeology Phase 5: Concept Inventory

**Objective:** External prompt asked for a source-grounded concept inventory across four lists: technical, design, business, and missing concepts. Each entry ≤3 sentences with implementation status (Fully / Partial / Missing) and file pointers.

**What was done:**
1. Context preflight: re-read `START_HERE.md`, `STATE.md`, `CONSTRAINTS.md`, the full RUN_LOG tail, and the three ADRs. Confirmed repo on `main`, working tree clean except for prior STATE/RUN_LOG edits and untracked `devdocs/`.
2. Surveyed existing archaeology output so Phase 5 complements Phases 1–4 instead of duplicating them. Phase 1 gives user-flow overview; Phase 2 gives architecture + deployment intros; Phase 3 gives 13 per-boundary traces; Phase 4 gives 5 improvement proposals. Phase 5 fills the inventory slot.
3. Read 40+ definitional source files to ground the lists: all 23 `UpgradeType` configs, 10 `ResearchType`, 9 `CardType` (3 rarities), 6 `UltimateWeaponType`, 4 `OverdriveType`, 5 `Biome` ranges, 6 `Milestone`, 6 `DailyMissionType`, 4 × 4 `SupplyDropTrigger`/`SupplyDropReward`, 5 `BillingProduct`, 3 `AdPlacement`, 6 `EnemyType`, 7 `BattleCondition`, 12 `Screen` routes. Verified `AppDatabase` v8 + `MIGRATION_7_8` + 12 entities, `PlayerProfileEntity`'s 27 columns, `DailyStepRecordEntity.battleStepsEarned`, 7 manifest permissions, 4 notification channels, `StubBillingManager` + `StubRewardAdManager`, 8 SharedPreferences wrappers, injected `Random` in the 3 stochastic use cases, date-seeded RNG in `GenerateDailyMissions`, `@Volatile` + callback-hop step-reward wiring in `GameEngine` / `BattleViewModel`, the 5-stage `runFollowOnPipeline` fan-out in `DailyStepManager`, and the 4-level `StepCrossValidator` that deducts via `spendSteps` on first escrow.
4. Wrote four concept-inventory docs under `devdocs/archaeology/concepts/`:
   - `technical_concepts_list.md` — 9 sections, 40+ concepts (platform choices, persistence, step ingestion, anti-cheat, battle renderer, UI/navigation/notifications, security, reproducibility/testing, cross-cutting patterns).
   - `design_concepts_list.md` — 7 sections, 35+ concepts (product design, domain data model shape, contract/interface shape, data-flow, UX/feedback, monetization, operational contracts).
   - `business_lvl_concepts_list.md` — 7 sections, 30+ concepts (positioning, hard invariants, currency economy, progression systems, engagement/retention, player-trust contracts, release/distribution).
   - `missing_concepts_list.md` — 4 sections, 30+ concepts split between intentionally deferred (Plan 24 accessibility, Plan 31 real SDKs, app icon, audio assets, i18n, instrumented tests, CI, step burst, onboarding, store assets, public privacy URL) and unintended gaps (TimeProvider abstraction, `@Transaction` wrapping, DB-file wipe on decrypt failure, round-end cascade on nav interrupt, deep-link coverage for 7 missing routes, FollowOnPipeline extraction, anti-cheat UI surfacing, boss/threat targeting, sound-settings surface, `PlaceholderScreen` dead code) plus compliance/operational assumptions and integration contracts worth naming.
5. Each entry holds to the ≤3-sentence limit, carries an explicit `Implementation status:` line (Fully / Partial / Missing), and cites the primary file(s) it refers to. Central concepts first, then branching; implicit concepts (invariants, privacy assumptions, operational handoffs, integration protocols) surfaced where they exist only as conventions.

**Discrepancies logged (code authoritative):** None new. Known drift points (DB v7 references in some docs, `CosmeticEntity` missing from `architecture.md`, `HealthConnectModule` placeholder) remain as called out in Phase 2. Phase 5 did not edit any production doc.

**No code or production docs modified.** No new dependencies. No build/test runs needed — pure inventory output grounded in code reading.

**Files created:**
- `devdocs/archaeology/concepts/` (new directory)
- `devdocs/archaeology/concepts/technical_concepts_list.md`
- `devdocs/archaeology/concepts/design_concepts_list.md`
- `devdocs/archaeology/concepts/business_lvl_concepts_list.md`
- `devdocs/archaeology/concepts/missing_concepts_list.md`

**Memory updated:** STATE ✅ (added Phase 5 pointer, no substantive status change — still Plan 31) / RUN_LOG ✅. No ADR needed — inventory describes existing code and decisions; no new decisions were made.

## 2026-05-05 — Standard Analysis Phase 7: Doc-Inferred Foundations

**Objective:** External prompt asked for "doc-inferred" foundation documents — `project_description.md`, `philosophy.md`, and `known_requirements.md` — extracted **only** from non-code documentation, with a "Docs vs Code" delta section at the end of each file. This is the twin of Phase 6 (which built the same three docs from code reading) but sourced from docs alone.

**What was done:**
1. Context preflight: read `START_HERE.md`, `STATE.md`, `CONSTRAINTS.md`, the tail of `RUN_LOG.md`, and all three ADRs. Confirmed still pointed at Plan 31 as the next substantive work.
2. Gathered the full non-code doc surface: `README.md`, `AGENTS.md`, `CHANGELOG.md`, the GDD, `docs/architecture.md`, `docs/database-schema.md`, `docs/battle-formulas.md`, `docs/step-tracking.md`, `docs/monetization.md`, `docs/plans/master-plan.md`, `plan-R-remediation.md`, `plan-R2-remediation.md`, sampled plan files (01, 24, 31), `docs/release/*`, `docs/balance/balance-report.md`, all `.kiro/steering/*` including agent-protocol and project-memory rules, and every `docs/agent/` file including the three ADRs.
3. Deliberately did **not** open code, gradle files, manifests, or the `devdocs/archaeology/` Phase 6 output (which is code-based) while drafting. The Phase 6 foundations at `devdocs/archaeology/foundations/` stay untouched; Phase 7 output is written to a fresh `devdocs/foundations/` directory so the two provenances can be compared side by side.
4. Wrote `devdocs/foundations/project_description.md` — product description organised as: tagline / platform facts, core loop, major systems table (with doc citation per row), currencies, architecture snapshot, explicit out-of-scope list, delivery status, and a Docs vs Code delta.
5. Wrote `devdocs/foundations/philosophy.md` — the product's stated belief system: the one immovable axiom ("Walk to Power"), design pillars, hard game rules, fair-play stance, monetization philosophy, accessibility/inclusivity stance, privacy posture, architectural philosophy, testing philosophy, the doc-first project-memory process philosophy, game-feel philosophy, and a negative philosophy list ("what the product refuses to be"), plus a delta.
6. Wrote `devdocs/foundations/known_requirements.md` — a numbered requirements catalogue (R-STEP-*, R-AC-*, R-ECO-*, R-WS-*, R-BAT-*, R-IR-*, R-TIER-*, R-BIO-*, R-UW-*, R-OD-*, R-LAB-*, R-CARD-*, R-SUP-*, R-NOTIF-*, R-STAT-*, R-MON-*, R-NFR-*, R-REL-*, R-PROC-*) with every requirement tied to a specific doc citation. Doc-vague points are captured as vague rather than promoted to invented precision.
7. Phase 7 delta sections identify a consistent set of doc drifts that are worth escalating eventually: schema v7 vs v8 (ADR-0003) in `docs/database-schema.md`, three simultaneous test counts (397/401/412), the Battle Step Rewards feature missing from every user-facing doc (GDD, README, CHANGELOG 1.0.0, battle-formulas, Play Store listing), the SharedPreferences state stores contradicting "Room is the single source of truth", the "33-entry roadmap" README phrasing, unchecked items in the release checklist that R2 already closed, the GDD pillars self-inconsistency (4 vs 5), and multiple vague areas (Supply-Drop seeding, Activity-Minute 100-step-eq rationale, inbox eviction policy, daily-mission rewards, battery acceptance methodology). These are only called out; nothing was fixed.

**Discrepancies logged:** None edited — Phase 7 is observational. The delta sections inside each file are the catalogue.

**No code, gradle, manifest, or production feature docs were modified.** No new dependencies. No build/test runs needed — this phase is pure doc synthesis.

**Files created:**
- `devdocs/foundations/` (new directory, distinct from the existing `devdocs/archaeology/foundations/`)
- `devdocs/foundations/project_description.md`
- `devdocs/foundations/philosophy.md`
- `devdocs/foundations/known_requirements.md`

**Memory updated:** STATE ✅ (added Phase 7 pointer; status still Plan 31) / RUN_LOG ✅. No ADR needed — this phase produces analysis, not decisions.

## 2026-05-05 — Archaeology Phase 9: Concept Mappings

**Objective:** External prompt asked for a per-concept mapping at `devdocs/archaeology/concept_mappings.md` with 7 parts per concept: files/modules, coverage % (Fully/Partial/Missing), divergence from an "ideal" architecture, alternatives likely considered, edge cases that shaped the design, related tests/fixtures/migrations/config/docs, and risks caused by the current shape.

**What was done:**
1. Context preflight: re-read `START_HERE.md`, `STATE.md`, `CONSTRAINTS.md`, RUN_LOG tail, all three ADRs. Confirmed still on `main`, working tree clean except modified STATE/RUN_LOG and untracked `devdocs/` (expected from prior phases).
2. Surveyed existing Phase 1–8 output so Phase 9 complements, not duplicates. Phase 5 (concepts/) gives file pointers per concept; Phase 9's unique contribution is coverage %, divergence, alternatives, edge cases, tests, risks. Different lens on the same concept domain.
3. Curated 25 major concepts (cut from an initial ~34 to keep signal high): step pipeline, HC cross-validation, battle step rewards (ADR-0003), currency model, persistence (Room/SQLCipher/migrations), battle renderer, combat formulas, wave system, biomes, Workshop upgrades, Labs research, Cards system, Ultimate Weapons, Step Overdrive, Tiers, supply drops, milestones/missions, weekly/login economy, monetization (billing/ads/cosmetics), notifications/widget, navigation/deep-link/UX feedback, DI/Hilt layering, reproducibility contracts, testing strategy, release/security.
4. Wrote `devdocs/archaeology/concept_mappings.md` in five incremental chunks (header+TOC+5, then 5, 5, 5, 5+appendices) to manage output budget. Total 2 342 lines, 25 concepts × 7-part entries, plus Appendix A (12 cross-concept risks) and Appendix B (coverage roll-up table with Key Gap column).
5. Every coverage figure and risk grounded in existing archaeology findings: Phase 4 items (TimeProvider, @Transaction, round-end cascade, FollowOnPipeline, anti-cheat surfacing), Phase 8 findings (12 forbidden imports, duplicated mission-progress updates, fat modules, thin modules, 3 reward vocabularies, 2 stat-snapshot chains), Phase 5 missing concepts (STEP_BURST orphan, cosmetic visual gap, deep-link partial, decrypt-failure zombie DB). No fresh code claims — Phase 9 synthesises what's already known in a mapping lens.

**Key calibration:** Coverage labels = Fully (85–100%), Partial (50–84%), Skeleton (20–49%), Missing (0–19%). Per-concept % is qualitative (feature completeness + edge cases + tests + docs + production-readiness), not line-counted.

**Highlights from the coverage roll-up (Appendix B):**
- 18 of 25 concepts at ≥88% coverage (core gameplay loop solid).
- 4 concepts at Partial (75–80%): supply drops (STEP_BURST orphan), milestones/missions (cosmetic no-op + 5-site duplication), navigation (5 of 12 deep-link routes), release (manual gate, Play Console pending).
- 2 concepts at Partial (55–70%): reproducibility contracts (53 direct wall-clock calls), testing strategy (no instrumented tests, no CI).
- 1 concept at Skeleton (45%): monetization (real SDKs deferred to Plan 31, cosmetic visuals missing).

**Changes made:**
- Created `devdocs/archaeology/concept_mappings.md` (2 342 lines).
- Updated `docs/agent/STATE.md` references + last-run line.
- This RUN_LOG entry.

**Code changes:** none (archaeology only).

**Commands/tests run:** filesystem reads + grep only — no build. No tests run (documentation-only).

**Open questions:** none. Phase 9 deliverable is strictly documentation. Cross-concept risks in Appendix A are cross-references to Phase 4 (item 1 TimeProvider, item 2 @Transaction, item 3 round-end cascade, item 4 FollowOnPipeline, item 5 anti-cheat UX) and Phase 8 (module-discovery prioritised list §8). No new proposals.

**Follow-ups created:** none new. Phase 9 synthesises existing findings; it does not schedule work. If the project wants to act on the map, the Key Gap column of Appendix B gives a natural triage order.

**Memory updated:** STATE ✅ (added Phase 9 reference + last-run line) / RUN_LOG ✅.

**ADR:** not warranted — no architectural decision was made; this phase maps decisions already evident in code plus the gaps around them.

## 2026-05-05 — Standard Analysis Phase 11: Gap Closure Plan

**Objective:** External prompt (Phase 11 "Towards First Implementation Pass — Gap Closure Strategy") asked for `devdocs/evolution/gap_closure_plan.md` — an executable, phased plan built from Phase 10's gap analysis. Required four phases (Quick Wins, Incremental Improvements, Major Refactoring, Complete Rewrites), each with dependencies, risk assessment, testing/verification, rollback, developer workflow, PR boundaries, and explicit non-goals.

**What was done:**
1. Context preflight: read `START_HERE.md`, `STATE.md`, `CONSTRAINTS.md`, RUN_LOG tail (Phase 10 entry), all three ADRs. Clean tree apart from modified STATE/RUN_LOG and untracked `devdocs/` (expected). Next substantive work is still Plan 31.
2. Primary input: `devdocs/evolution/gap_analysis.md` (993 lines from Phase 10). Re-read §1 (12 missing concepts), §2 (6 architecture changes), §3 (12 tech-debt items), §4 (Phase 4 cross-ref + 7 additional incrementals), §5 (rewrite rejections), §6 (7 known risks + 8 unknowns + 4 non-unknowns), §7 (aggregated posture + critical path).
3. Secondary input: `devdocs/archaeology/5_things_or_not.md` (Phase 4) which already costs out the five highest-leverage items with file:line citations and rollback plans. Phase 11 cross-references these rather than duplicating (global rule #3).
4. Wrote `devdocs/evolution/gap_closure_plan.md` (1 032 lines): §0 read-first checklist; §1 quick wins (Q1–Q8: doc drift, DB-file wipe on decrypt failure, fake failure modes, FloatingText suppression on cap, deep-link all-routes, STEP_BURST decision, PlaceholderScreen deletion, Season Pass leak tactical patch); §2 incremental improvements (I1–I7: anti-cheat visibility, atomic writes first site + remaining sites, resilient endRound, TimeProvider narrow migration, ClaimMilestone.Cosmetic fix, Settings rename + privacy link); §3 major refactoring (M1 FollowOnPipeline + UpdateMissionProgress extractions, M2 real Billing SDK, M3 real Ad SDK, M4 Plan 31 external tasks) + MR1 cosmetic rendering pipeline promoted out of §4 per Phase 10 §5.2 "not a rewrite, a new narrow contract"; §4 rewrite rejections with explicit revisit triggers (multi-module Gradle split, Reward sealed hierarchy unification); §5 sixteen explicit non-goals matched to Phase 10 citations (accessibility, onboarding, i18n, analytics, server-side anti-cheat, etc.); §6 aggregate critical path with release-critical sequence (Q3 → M2+M3 → MR1 PR1-2 → M4); §7 memory-update checklist aligned to §11-agent-protocol.
5. Each phase entry holds the prompt's required shape: dependencies/prerequisites, risk assessment + mitigation, testing/verification strategy, rollback plan, expected developer workflow, suggested commit/PR boundaries, explicit non-goals. No new findings introduced — every item cited back to its Phase 10 section, Phase 4 item number, or a roadmap/plan-31 task (global rules #1, #3).
6. Updated `docs/agent/STATE.md` references list + last-run line.

**Key design choices in the plan (explicit so future readers can audit):**
- **Q6 (STEP_BURST) defaults to delete-with-documented-intent** because no doc demands wiring and Phase 10 §1.4 lists both options as equally valid. The commit-body documentation satisfies rule #2.
- **Q8 is a tactical patch** for the Season Pass leak, flagged as duplicate-logic until M1 PR 4 removes it — prevents blocking the fix on the larger extraction.
- **I2 lands the first @Transaction site before I3 fans it out** to prove the pattern on the most user-visible purchase before migrating four more.
- **I5 explicitly migrates only 3 of 53 TimeProvider call sites** per Phase 4 §1's scope-creep mitigation. Not a sweep.
- **MR1 moved from §4 to §3** — Phase 10 §5.2 was categorical: "Not a rewrite — a new narrow contract." Putting it in §4 would have contradicted the source.
- **M4 (Plan 31 external) stays in the plan even though it's not code** — Phase 10 §1.1 and the master-plan both treat it as a release gate; omitting it would leave the critical path incomplete.
- **Two rewrites explicitly rejected with revisit triggers**: multi-module Gradle split (revisit when a second engineer joins) and `Reward` sealed unification (revisit when a fourth reward type is specified). Satisfies prompt's §4 "only if necessary" framing.

**Changes made:**
- Created `devdocs/evolution/gap_closure_plan.md` (1 032 lines).
- Updated `docs/agent/STATE.md` references list + last-run line.
- This RUN_LOG entry.

**Code changes:** none (evolution/planning only).

**Commands/tests run:** filesystem reads only — no build, no tests.

**Open questions:** two unknowns from Phase 10 §6.2 now carried forward into specific plan entries rather than left implicit —
- Q6 resolution (delete vs wire `STEP_BURST`) is named as the first action of that PR.
- MR1 PR 2's single-cosmetic choice (proposed: jade ziggurat recolour) needs a product decision before coding; the plan surfaces the decision up-front rather than guessing.

**Follow-ups created:** none new work items. The plan aggregates, schedules, and risk-scopes already-discovered items. If adopted, the rollout orders inside §1, §2, §3 and the critical path in §6 give the sequence.

**Memory updated:** STATE ✅ / RUN_LOG ✅.

**ADR:** not warranted — no architectural decision made. The plan is a schedule of already-documented decisions; actual architecture choices (e.g. ADR-0004 FollowOnPipeline, ADR-0005 billing SDK, ADR-0006 ad SDK) are called out **as prerequisites** of M1/M2/M3 respectively, to be written before the first code PR of each.

## 2026-05-06 — Standard Analysis Phase 13: Codebase Cleanup Inventory

- Goal: Produce `devdocs/archaeology/cleanup_inventory.md` per the Phase 13 prompt — identify candidates for removal / consolidation / quarantine with evidence / confidence / risk / verification / action per item. No deletions, no code changes.
- Preflight: read `START_HERE`, `STATE`, `CONSTRAINTS`, RUN_LOG tail (Phase 12 entry); `git status` clean apart from modified STATE/RUN_LOG and untracked `devdocs/` + `smoke_tests/` (expected). `git log -n 5 --oneline` shows HEAD at `a9d0386 feat: award Steps for enemy kills with 2k/day cap (ADR-0003)`.
- Survey: confirmed no prior `devdocs/archaeology/cleanup_inventory.md` existed. Re-used Phases 1–12 findings (small_summary, intro2codebase, intro2deployment, traces, 5_things_or_not, concepts/, foundations/ x2, architecture_analysis, module_discovery, concept_mappings, gap_analysis, gap_closure_plan, smoke_tests/report.md) as the primary evidence base — cited inline per global rule #3 to avoid duplication.
- Code verification (no builds, only filesystem + grep):
  - `grep PlaceholderScreen` → 1 match, declaration only (MainActivity.kt:237). Dead composable confirmed.
  - `grep UltimateWeaponLoadout app/src/main` → declaration file only, **0 non-self references in main**. `ManageCardLoadout.kt:15` uses `CardLoadout.MAX_SIZE` constant → CardLoadout is alive; UltimateWeaponLoadout is dead runtime-wise.
  - `grep STEP_BURST` → 1 match, declaration at SupplyDropTrigger.kt:5. No producer. Orphan enum value.
  - `grep 'STEP_MULTIPLIER|RECOVERY_PACKAGES' app/src` → 4 matches main (2 enum entries + 2 config entries) + 2 matches test (balance/CostCurveTest.kt:17,45) + 1 match WorkshopViewModel.kt:42 hiddenUpgrades. Not pure orphan — still exercised by balance tests.
  - `grep 'MilestoneReward\.(Gems|PowerStones|Cosmetic)' app/src` → Milestone.kt declares 3 cosmetic rewards (garden_ziggurat_skin, lapis_lazuli_skin, sandals_of_gilgamesh); ClaimMilestone.kt:25 is `is Cosmetic -> { /* no-op */ }`. Grep of `cosmeticId\s*=` in CosmeticRepositoryImpl.kt shows SEED_COSMETICS uses ids `zig_obsidian, zig_crystal, zig_golden, proj_fire, proj_lightning, enemy_shadow, enemy_neon` — **zero overlap with the milestone cosmetic ids**. Confirmed.
  - `grep 'releaseEscrow|discardEscrow|clearEscrow'` → StepRepositoryImpl.kt:44 = `dao.clearEscrow(date)`; line 46 = `dao.clearEscrow(date)` — byte-identical delegations. Semantic split lives only in StepCrossValidator (discard at 76,92 for offense; release at 106 for reconciliation).
  - `grep '@RunWith|RobolectricTestRunner' app/src/test` → 3 files: RoomSchemaTest.kt (3 @Test), StepWidgetProviderTest.kt (3 @Test), DeepLinkRoutingTest.kt (3 @Test). All use `org.junit.Test` (JUnit 4). Classpath confirmed in Phase 12 has no `junit-vintage-engine`. Phase 12 report said only RoomSchemaTest + StepWidgetProviderTest were affected (6 tests); grep says DeepLinkRoutingTest is also JUnit 4 + Robolectric — so likely 9 tests silently skipped, not 6. Flagged in §C1 as a discrepancy worth verifying against the `testDebugUnitTest` XML on a future run.
  - `grep 'TODO|FIXME|XXX|HACK' app/src/main` → 0 matches. Confirmed in Phase 10 gap_analysis too.
  - `ls docs/agent/state.json docs/temp/` → both absent. Already-cleaned orphans; §E1 confirms.
  - `ls app/schemas/.../AppDatabase/` → 1.json through 8.json present. `Migrations.kt` registers only `MIGRATION_7_8`. §D1 flags 1–6.json as possibly historical artefacts.
  - `grep '@InstallIn|@Inject|@HiltViewModel|@HiltWorker|@HiltAndroidApp|@AndroidEntryPoint|@Provides|@Binds|@Module' app/src/main` → 239 matches across 79 files. §F dynamic-risk register pins all these.
  - `grep 'BiomePreferences|NotificationPreferences|SoundPreferences|MilestoneNotificationPreferences|AntiCheatPreferences|StepIngestionPreferences'` → 8 preference wrappers total, each wired; §A8 names them as a virtual `prefs` module and proposes consolidation.
  - Read HealthConnectModule.kt in full: lines 10–13 are `@Module / @InstallIn(SingletonComponent::class) / object HealthConnectModule` with **no body**. Empty organisational placeholder. §A6.
  - Read Screen.kt: `val items by lazy { … }` — workaround for sealed-class init-order NPE (commit 1872af9). §A7 proposes a one-line comment.
- Changes made:
  - Created `devdocs/archaeology/cleanup_inventory.md` (565 lines, ~43 KB, 8 top-level sections + TL;DR + caution box): TL;DR of 18 candidates grouped by effort/confidence; Dynamic-Risk Caution Box up front listing 11 framework mechanisms; §A source tree (11 entries A1–A11); §B abandoned features / orphan enum entries (7 entries B1–B7); §C tests, fakes, fixtures (5 entries C1–C5); §D config, build, scripts, schema, migrations (13 entries D1–D13); §E docs orphans / redundancy (6 entries E1–E6); §F dynamic-risk register with 17-row table of classes invisible to grep plus a pre-removal audit checklist; §G retention / compatibility / legal; §H summary + cross-references to prior archaeology and Phase 11 schedule.
  - Updated `docs/agent/STATE.md` references list + last-run line.
  - This RUN_LOG entry.
- Code changes: none (archaeology/inventory only).
- Commands/tests run: filesystem reads + grep + `git status`/`git log` only — no build, no tests.
- Open questions:
  - §C1 flags possible discrepancy with Phase 12 report (DeepLinkRoutingTest may be silently skipped — Phase 12 claimed it passed). Worth verifying against `app/build/test-results/testDebugUnitTest/*.xml` per-package counts in a future session. Not blocking the inventory.
  - Three product decisions called out in §B (STEP_BURST wire-or-delete, STEP_MULTIPLIER/RECOVERY_PACKAGES wire-or-delete, MilestoneReward.Cosmetic ID alignment) are **inputs** to any future cleanup; they are not decisions this phase makes.
- Follow-ups created: none new work items. The inventory cross-references Phase 4 five-item list, Phase 8 module-discovery prioritised list, Phase 10 gap analysis, and Phase 11 gap closure plan as the existing schedules.
- Memory updated: STATE ✅ / RUN_LOG ✅
- ADR: not warranted — no architectural decision made; inventory surfaces existing code state plus gaps already documented elsewhere.

