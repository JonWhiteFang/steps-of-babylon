# Plan — Batch B: Dead-Code Removal (audit findings, 2026-06-23)

**Status:** REVIEWED (adversarial review gate passed 2026-06-23 — see "Review outcome" at end)
**Scope:** Remove confirmed dead code. **Zero behavior change** — every target has zero production callers
and zero test references at HEAD `9e186bc`.
**Source:** Batch B of the audit-tracker triage (verification workflow, HEAD `617babd`; re-grounded at
`9e186bc` — Batch A touched no `.kt` files). **Findings closed:** #262 **L15, L16, L17, L13/L18**.
**Deferred (developer decision):** **L26** (`fortuneMultiplier` rename) — it is a *cosmetic rename of a
correct field*, not dead code, spanning the battle fragile zone + pure-domain `SimulationMath` + a
reflection test. Kept OUT of this removal PR (its own small PR or dropped).

## Decisions (developer-approved, this session)
- **L26 deferred** — Batch B is pure dead-code REMOVAL only.
- **Card Dust (L13/L18): API methods only.** Remove the repository methods + dead DAOs + fake overrides +
  the dedicated test case. **KEEP** the `cardDust` DB column, the domain `PlayerProfile.cardDust` field, and
  the `toDomain()` mapping — the column is schema-bound (v1–12 + `Migrations.kt:175` zeroes it); dropping it
  is a migration, explicitly out of scope. The retained domain field harmlessly mirrors persisted state.

## Non-goals / out of scope
- No `fortuneMultiplier` rename (L26 — deferred).
- No schema change: the `cardDust` column stays; `app/schemas/*.json` must be byte-identical after this PR.
- No behavior change anywhere. This is removal of unreachable/never-read code only.
- Not touching the other LIVE batches (C i18n, D CI, E VM/a11y, F sensor, G security).

## Edit list (each grounded at HEAD `9e186bc`)

### Battle engine — dead code (fragile zone: read carefully, but these are all unreferenced)
1. **L15 — `resetUWCooldowns` (zero callers, repo-wide).** Remove BOTH:
   - `GameEngine.kt:548` `fun resetUWCooldowns() = uwController.resetUWCooldowns()` (the façade wrapper).
   - `UWController.kt:136-138` `fun resetUWCooldowns() { uwStates.forEach { it.cooldownRemaining = 0f } }`.
   `rg 'resetUWCooldowns'` confirms only the wrapper + impl exist — no main, test, or androidTest caller.
2. **L16 — `cooldownText` write-only field.** Remove `GameEngine.kt:102`
   `private var cooldownText: WaveCooldownText? = null` and its two dead writes at `:559`
   (`cooldownText = null // Old one auto-finishes`) and `:571` (`cooldownText = ct`). The local `val ct`
   (`:563-570`) and `fx.addEffect(ct)` (`:572`) are what actually drive the overlay — **keep them**, and
   keep the `WaveCooldownText` import (the local `ct` still uses it). The `:559` line's intent ("old one
   auto-finishes" via the EffectEngine) is preserved because the field was never the mechanism — drop the
   line entirely (the comment described a no-op).
3. **L17 — `GameLoopThread.fps` never read.** Remove the field `var fps: Int = 0 / private set`
   (`:31-32`) and its bookkeeping: the `var frameCount = 0` + `var fpsTimer = System.nanoTime()` locals
   (`:42-43`) and the entire `// FPS counter` block (`:92-98`: `frameCount++` + the 1-second
   `if (... fpsTimer ...) { fps = frameCount; frameCount = 0; fpsTimer = currentTime }`). **Keep** the
   `currentTime` read (`:46`, load-bearing for `elapsed`/`frameTime`) and the yield block (`:100-108`:
   `frameTime`/`sleepMs`/`sleep` — independent of the FPS counter). `rg '\.fps\b'` confirms no external
   reader; no test references `fps`/`frameCount`/`fpsTimer`.

### Card Dust API removal (L13/L18) — methods only, column stays
4. **`domain/repository/PlayerRepository.kt:47,49`** — remove the `suspend fun addCardDust(amount: Long)`
   and `suspend fun spendCardDust(amount: Long)` interface declarations.
5. **`data/repository/PlayerRepositoryImpl.kt:63,65`** — remove the two `override` impls
   (`addCardDust = dao.adjustCardDust(amount)` / `spendCardDust = dao.adjustCardDust(-amount)`).
6. **`data/local/PlayerProfileDao.kt`** — remove the now-orphaned DAOs:
   - `:25-26` `updateCardDust` (`@Query UPDATE … SET cardDust = :cardDust`) — **zero callers repo-wide**.
   - `:65-66` `adjustCardDust` (`@Query UPDATE … SET cardDust = MAX(0, cardDust + :delta)`) — its only
     callers were the two impls removed in edit 5.
7. **`test/fakes/FakePlayerRepository.kt:88-94`** — remove the `addCardDust`/`spendCardDust` `override`s
   (they implement the interface methods being deleted; the fake won't compile otherwise).
8. **`test/.../presentation/ux/CurrencyGuardTest.kt:55-61`** — remove the
   `spending more card dust than balance clamps to zero` test (it calls `repo.spendCardDust(100)`, which is
   being removed). This is the ONLY test that exercises the removed methods. **Net JVM test count: −1**
   (1254 → 1253). Update the headline test count in CLAUDE.md (`Testing` section) + README + STATE accordingly.

> **KEEP (do NOT touch):** `data/local/PlayerProfileEntity.kt:14` `val cardDust: Long`,
> `domain/model/PlayerProfile.kt:9` `val cardDust: Long`, `PlayerRepositoryImpl.toDomain()` `cardDust =`
> mapping (`:121`), `Migrations.kt:174-175`, all `app/schemas/*.json`, and the test fixtures that merely
> *construct* `PlayerProfile(cardDust = …)` / `PlayerProfileEntity(… cardDust = …)` (BillingManagerImplTest,
> PlayerRepositoryImplTest, CardsViewModelTest, CurrencyGuardTest's OTHER cases, DataTransformMigrationsTest)
> — those read/write the surviving column/field and stay green.

## Verification
- `./run-gradle.sh testDebugUnitTest` — expect BUILD SUCCESSFUL, **1253 JVM, 0 failures** (−1 from the
  removed CurrencyGuardTest case; no other test referenced any removed symbol).
- `./run-gradle.sh :app:assembleDebug` — confirms the battle-engine + DAO removals compile (Room codegen
  must still generate `PlayerProfileDao_Impl` without the removed queries).
- **Schema unchanged:** `git status app/schemas/` shows no change (Room won't re-emit — no `@Entity`/version
  change). If a schema file changes, STOP — the column was accidentally touched.
- `./run-gradle.sh :app:detekt` + `./lint-kotlin.sh` — both exit 0 (removal may shrink a baseline entry;
  regen only if it fails on a NEW violation, which removal shouldn't cause).
- **Grep proof post-edit:** `rg 'resetUWCooldowns|cooldownText|adjustCardDust|updateCardDust|addCardDust|spendCardDust'`
  over `app/src/main` returns nothing; over `app/src/test` returns only surviving `cardDust`-column fixture
  constructions (no method calls).

## PR Task-List (mandatory convention — sync current-state docs BEFORE STATE/RUN_LOG, then commit)
1. Apply edits 1–8.
2. Run `testDebugUnitTest` + `assembleDebug` + `detekt`/ktlint; confirm 1253 JVM / 0 failures, schema unchanged.
3. **Sync current-state docs (exact targets, grounded):**
   - **CLAUDE.md:344** `Headline count: 1254 JVM` → **1253** (Testing section).
   - **README.md:13** (`1254 JVM + 9 instrumented`) AND **README.md:48** (`# Unit tests (1254 JVM tests)`)
     → **1253** (both are live current-state).
   - **`docs/steering/source-files.md`: NO edit needed** — verified the `PlayerProfileDao` (:27),
     `PlayerRepository` (:196), and `PlayerRepositoryImpl` (:57) entries describe currency adjustments
     generically and do NOT enumerate `adjustCardDust`/`updateCardDust`/`addCardDust`/`spendCardDust`.
   - CHANGELOG `[Unreleased]` — add the dead-code-removal entry.
4. **Update `docs/agent/STATE.md`:** CURRENT objective → Batch B; bump ONLY the **live headline at
   `STATE.md:18`** (`1254 JVM + 9 instrumented`) → **1253**. **Do NOT touch** the historical occurrences at
   `:19` (per-wave tally `1110→1254`), `:43` (`1126→1254` in the v1.0.11 record), `:48` (`1254 JVM, 0
   failures` in the v1.0.11 CURRENT block), or `:67` (Batch-A session snapshot) — those are point-in-time
   provenance, not the live count. Then append `RUN_LOG.md`.
5. Commit on branch `chore/batch-b-dead-code`; open PR; check off L15/L16/L17/L13/L18 in #262 on merge.

## Risk
**Low.** Pure removal of unreferenced code. The two real watch-points:
(a) **battle fragile zone** — but all three battle targets are verified unreferenced (no caller, no test, no
reflection), and the surrounding live logic (local `ct`/`fx.addEffect`, the loop yield) is explicitly kept;
(b) **Card Dust column accidentally dropped** — mitigated by the explicit KEEP list + the schema-unchanged
verification gate. Test count drops by exactly 1 (one dedicated test for a removed method).

## Review outcome (adversarial gate, 2026-06-23)

Reviewed via a multi-agent `Workflow` (3 dimensions — grounding-unreferenced / completeness-compile /
risk-column-fragile — each code-grounded then adversarially skeptic-verified at HEAD `9e186bc`).
**0 confirmed findings.** Two dimensions (grounding, completeness) returned **empty** — confirming every
removal target is genuinely unreferenced (no caller, no test, no reflection, no DI) and the build won't
break. The risk dimension raised 2 nits, **both REFUTED** by the skeptic as execution reminders rather than
plan defects (the plan's wording already scoped them correctly).

Independent pre-checks (done inline, corroborating the empty grounding/completeness dimensions):
- **Only 2 implementors of `PlayerRepository`** own the Card Dust overrides — `PlayerRepositoryImpl` (main)
  + `FakePlayerRepository` (test), both in the edit list. `ThrowingPlayerRepository` (StatsViewModelTest)
  **extends** `FakePlayerRepository` and overrides only `observeProfile` — it *inherits* the Card Dust
  methods, so it's automatically fine once the fake's overrides go (no extra edit).
- **No dangling siblings:** no `CARD_DUST` enum / `Currency.CARD_DUST` value / Card-Dust use case / UI
  string; `updateCardDust` has zero callers; `adjustCardDust`'s only callers are the two impls being removed.

Folded into the plan from the review's useful nuance: the doc-sync step now **pins the exact live-vs-
historical test-count occurrences** (STATE.md:18 live → 1253; :19/:43/:48/:67 historical, untouched;
both README hits live; source-files.md needs no edit). No other amendment was warranted.
