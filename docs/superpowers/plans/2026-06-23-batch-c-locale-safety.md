# Plan — Batch C: i18n Locale-Safety (audit findings, 2026-06-23)

**Status:** REVIEWED (adversarial review gate passed 2026-06-23 — see "Review outcome" at end)
**Scope:** Locale-correctness fixes. **One real reachable bug (L88) + three consistency/latent-safety
fixes.** Near-zero behavior change on en/US devices; the changes only affect non-English-locale correctness
and consolidate number formatting.
**Source:** Batch C of the audit-tracker triage (verification workflow HEAD `617babd`; re-grounded at
`367fe6f`). **Findings closed:** #262 **L88, L89, L87, L91**.
**Deferred (developer decision):** L23 (gating-string externalization) + L2/L8/L27 (93 hardcoded literals) +
L90 (RTL review) → belong to a dedicated i18n/#34 effort; L24 (TimeProvider adoption) is DI/testability, not
i18n → its own item.

## Decisions (developer-approved, this session)
- **Batch C = locale-safety only** (L88/L89/L87/L91); defer the externalization/RTL/TimeProvider items.
- **L87 standardization: one shared helper pinned to `Locale.US`** (matches the existing
  `CurrencyDisplay.formatCurrency`), so grouping separators are deterministic everywhere and there is no
  per-locale output variance to test. (Device-default-locale formatting is deferred to the full #34 push.)

## ⚠️ Correctness note (changes the stakes of L88)
The triage's parenthetical "no current enum name contains 'I'" is **WRONG**. `BillingProduct.GEM_PACK_MEDIUM`
contains the letter **I**. Under a Turkish/Azeri locale, `"GEM_PACK_MEDIUM".lowercase()` →
`gem_pack_medıum` (dotless ı), which does NOT match the Play Console product id `gem_pack_medium`. Because
`skuId()` is the wire `productId` (Play Billing query + purchase + `BillingReceiptEntity.productId` column +
reconciliation reverse-lookup), this **breaks the medium gem-pack purchase AND receipt reconciliation on
Turkish-locale devices** — a real, reachable defect, not a latent/cosmetic one. L88 is the priority fix.

## Edit list (each grounded at HEAD `367fe6f`)

### L88 — `BillingProduct.skuId()` wire id (the real bug)
1. **`domain/model/BillingProduct.kt:23`**: `fun skuId(): String = name.lowercase()` →
   `name.lowercase(Locale.ROOT)`. Add `import java.util.Locale`. **`Locale.ROOT` (not `.US`)** is correct
   for a locale-independent wire identifier. **Domain-purity safe:** `DomainPurityTest` forbids only
   `android.`/`androidx.`/`com.android.`/`com.google.android.`/data-layer/`dagger.`/`javax.inject.` —
   `java.util.Locale` is allowed (verified `DomainPurityTest.kt` `forbiddenPrefixes`).
   - **Test (TDD):** add a `BillingProductTest` case asserting `skuId()` is locale-stable — set
     `Locale.setDefault(Locale("tr"))` in the test body (restore in `finally`), assert
     `GEM_PACK_MEDIUM.skuId() == "gem_pack_medium"` (would be `gem_pack_medıum` before the fix). This is the
     regression guard that makes the fix verifiable. **+1 JVM test.**

### L89 — display-side case conversions (3 sites)
2. **`presentation/ui/EnumDisplayName.kt:10`**, **`presentation/battle/ui/BiomeTransitionOverlay.kt:54`**,
   **`presentation/stats/StatsScreen.kt:77-78`**: pin ONLY the **`String.lowercase()`** calls to
   `Locale.ROOT`. **⚠️ REVIEW (MAJOR): the `.uppercase()` calls are `Char.uppercase()`** (inside
   `replaceFirstChar { c -> c.uppercase() }`) — **`Char.uppercase()` has NO `Locale` overload**, so
   `.uppercase(Locale.ROOT)` would NOT COMPILE. Leave the `Char.uppercase()` calls untouched (they are
   locale-independent for a single char in this title-casing path). Concretely:
   - `EnumDisplayName.kt:10`: `it.lowercase()` → `it.lowercase(Locale.ROOT)`; leave `c.uppercase()`.
   - `BiomeTransitionOverlay.kt:54`: `word.lowercase()` → `word.lowercase(Locale.ROOT)`; leave the inner
     `it.uppercase()`.
   - `StatsScreen.kt:77-78`: `.lowercase()` → `.lowercase(Locale.ROOT)`; leave `replaceFirstChar { it.uppercase() }`.
   Add `import java.util.Locale` where absent. (Whole-`main` sweep confirmed these are the only 3
   default-locale `String.lowercase()` display sites.)

### L87 — number formatting (consolidate onto one Locale.US helper)
3. **New file `presentation/ui/NumberFormatting.kt`** — a tiny **Compose-free** top-level helper:
   `fun formatCount(value: Long): String = NumberFormat.getNumberInstance(Locale.US).format(value)`
   (package `presentation.ui`). Compose-free so `service/StepWidgetProvider` can use it without pulling in
   Compose (the widget renders `RemoteViews`, not Compose).
4. **Migrate all sites to it:**
   - `presentation/home/HomeScreen.kt:256` — delete the local `private fun formatCount = "%,d".format(...)`,
     import the shared one (callers at :122, :250 unchanged).
   - `presentation/economy/CurrencyDashboardScreen.kt:114, :232` — `"%,d".format(x)` → `formatCount(x)`
     (the `:114` "%,d / 100,000 steps" becomes `"${formatCount(state.weeklySteps)} / 100,000 steps"` — keep
     the literal "100,000" as-is, it's a copy string; or use `formatCount(100_000)` for full consistency —
     reviewer's call, default to leaving the static target literal).
   - `presentation/stats/StatsScreen.kt:32` + `presentation/missions/MissionsScreen.kt:62` — replace
     `NumberFormat.getNumberInstance()` (default locale) with the shared `formatCount` (drop the threaded
     `fmt: NumberFormat` params, or seed them from `Locale.US` — prefer dropping the param and calling
     `formatCount` at each `fmt.format(...)` site for one mechanism).
   - `presentation/ui/CurrencyDisplay.kt:66` — `formatCurrency` already pins `Locale.US`; refactor it to
     delegate to `formatCount` so there is a single implementation (keeps the existing call sites :83/:97).
   - `service/StepWidgetProvider.kt:40` — replace `NumberFormat.getNumberInstance()` with `formatCount`.
   - **Test (TDD):** a small `NumberFormattingTest` asserting `formatCount(1234567) == "1,234,567"` under a
     non-US default locale (set `Locale.setDefault(Locale.GERMANY)` → still US-grouped because the helper
     pins `Locale.US`). **+1 JVM test.**

### L87b — default-locale `%.Nf` decimal formats (REVIEW: added — decimal-separator sibling of L87)
4b. **Two sites format decimals with the JVM default locale** (comma-decimal under de-DE/fr-FR → `1,5 dmg`),
    while every *other* `String.format` in `main` already pins `Locale.ROOT` (`UpgradeValueLabel.kt:18`,
    `UltimateWeaponScreen.kt:293-308`, `DescribeUpgradeEffect.kt:302`, `CardType.kt:101`) — so this is an
    inconsistency the codebase already has the right pattern for:
    - **`presentation/workshop/WorkshopViewModel.kt:198-211`** — the `upgradeEffectLabel` `when` arm uses
      `"%.1f dmg".format(s.damage)` etc. (14 arms). Convert each to `String.format(Locale.ROOT, "%.1f dmg", s.damage)`
      (or hoist to a small local `fun fmt(pattern, v) = String.format(Locale.ROOT, pattern, v)` to avoid
      repetition). Add `import java.util.Locale`.
    - **`presentation/labs/LabsScreen.kt:224`** — `String.format("%.1fh", info.timeToCompleteHours)` →
      `String.format(Locale.ROOT, "%.1fh", info.timeToCompleteHours)`. Add `java.util.Locale` import.
    These are **decimal-separator** fixes (NOT grouping) — the `formatCount` helper (edit 3, integer
    grouping) does NOT cover them; they need their own `Locale.ROOT` pin. en/US output is byte-identical
    (US already uses `.` decimal). **No new test required** (covered by the existing display assertions; the
    fix matches the established `Locale.ROOT` pattern these labels' siblings already use) — but a reviewer
    may add a de-locale assertion if desired.

### L91 — strings.xml currency-term casing
5. **`app/src/main/res/values/strings.xml:93`** `biome_steps_walked` = `"%1$d steps walked"`. **DECISION
   (REVISED after review — MINOR finding CONFIRMED): LEAVE AS-IS.** The review verified the cited precedents
   (`workshop_balance`, `upgrade_cost_steps`, `postround_stat_steps`) are all **noun/label** contexts, whereas
   line 93 is a **running verb phrase** where lowercase "steps walked" is grammatically correct English.
   Capitalizing to "Steps walked" would be awkward and is a user-facing copy change with no clear benefit, so
   the original "default: capitalize" was wrong and is **dropped**. L91 (currency-term glossary consistency)
   is effectively a non-issue for this string — **no edit.** (If a true glossary pass is ever wanted, it
   belongs in the deferred #34 i18n effort, with copywriting review.)

### Test hygiene (REVIEW: added)
6. **`test/.../presentation/ui/EnumDisplayNameTest.kt:27`** — the `matches the legacy formatName behaviour
   verbatim` guard reconstructs the transform inline as `it.lowercase().replaceFirstChar { c -> c.uppercase() }`
   (no Locale). After edit 2 pins the production `EnumDisplayName.kt:10` to `lowercase(Locale.ROOT)`, mirror
   the test's `legacy` lambda to `it.lowercase(Locale.ROOT)` so its stated "verbatim" intent stays honest
   (it passes either way on the ASCII samples, but the lambda should match production). Add `java.util.Locale`
   import to the test. No assertion/count change.

## Verification
- **TDD:** write the L88 + L87 tests FIRST (red), apply the fixes (green).
- `./run-gradle.sh testDebugUnitTest` — expect BUILD SUCCESSFUL, **1255 JVM** (1253 + 2 new), 0 failures.
- `./run-gradle.sh :app:assembleDebug` — confirms the new helper file + widget change compile.
- `./run-gradle.sh :app:detekt` + `./lint-kotlin.sh` — both exit 0 (new file must pass ktlint; run
  `--format` if needed before committing).
- **No schema change** (`git status app/schemas/` clean — no entity touched; `BillingProduct` is a domain
  enum, not a Room entity).
- **Behavior-equivalence proof (en/US):** the only en/US-visible change is consolidating number formatting
  (output identical: all paths already produced US-grouped digits except the default-locale ones, which on a
  US device WERE US-grouped). `skuId()`/display-case outputs are byte-identical on a Latin locale.
- **Grep proof post-edit:**
  - `rg '\.lowercase\(\)' app/src/main` returns no Locale-less `String.lowercase()` (the 4 sites now pass
    `Locale.ROOT`). NOTE: `Char.uppercase()` inside `replaceFirstChar` legitimately remains (no Locale
    overload exists) — that is expected, not a miss.
  - `rg 'getNumberInstance\(\)' app/src/main` returns none — **but `getNumberInstance(Locale.US)` survives
    once inside the new `NumberFormatting.kt` helper** (REVIEW nit: the empty-parens regex won't match it;
    a looser `rg getNumberInstance` will show exactly that one expected hit).
  - `rg 'String.format' app/src/main` — every hit pins `Locale.ROOT` (no bare `String.format("%.…"` left:
    `WorkshopViewModel` + `LabsScreen:224` now fixed).
  - `rg 'name.lowercase\(\)' .../BillingProduct.kt` returns none.

## PR Task-List (mandatory convention — sync current-state docs BEFORE STATE/RUN_LOG, then commit)
1. Write the 2 new tests (red); apply edits 1–5 (green).
2. Run `testDebugUnitTest` + `assembleDebug` + detekt/ktlint; confirm 1255 JVM / 0 failures, schema unchanged.
3. **Sync current-state docs:** CLAUDE.md headline test count 1253 → **1255**; README test-count mentions
   (:13, :48) → 1255; `docs/steering/source-files.md` — (a) add the new `NumberFormatting.kt` entry, and
   (b) **REVIEW NIT: update the literal substring at `source-files.md:168`** which currently reads
   `skuId() returning name.lowercase()` → `name.lowercase(Locale.ROOT)` (the signature is unchanged; only
   the hardening note). CHANGELOG `[Unreleased]` entry.
4. **Update `docs/agent/STATE.md`** (CURRENT objective + live headline `:18` test count 1253→1255; do NOT
   touch historical 1254/1253 occurrences) **+ append `RUN_LOG.md`.**
5. Commit on branch `fix/batch-c-locale-safety`; open PR; check off L88/L89/L87/L91 in #262 on merge.

## Risk
**Low–moderate.** L88 touches the billing wire path — but `Locale.ROOT` makes the en/US output
**byte-identical** (`gem_pack_medium` etc. unchanged) and only fixes the Turkish-locale corruption; the new
test pins it. L87 is a pure formatting consolidation (US output unchanged on US devices). The one judgment
call was L91 — now resolved to LEAVE AS-IS (review confirmed lowercase is correct in the verb phrase). No
schema, no economy/engine change.

## Review outcome (adversarial gate, 2026-06-23)

Reviewed via a multi-agent `Workflow` (3 dimensions — grounding-completeness / behavior-equivalence /
risk-discipline — each code-grounded then adversarially skeptic-verified at HEAD `367fe6f`). **6 findings,
all CONFIRMED (0 refuted).** The behavior-equivalence dimension returned **empty** — the en/US byte-identical
safety claim holds. Two findings were MAJOR and materially changed the implementation:

- **MAJOR — L89 would not compile:** the `.uppercase()` calls are `Char.uppercase()` (inside
  `replaceFirstChar`), which has **no `Locale` overload**. → Edit 2 rewritten: pin only the `String.lowercase()`
  calls to `Locale.ROOT`; leave the `Char.uppercase()` calls. *(This would have been a build break.)*
- **MAJOR — L87 sweep incomplete:** missed default-locale `%.Nf` `String.format` sites — `LabsScreen.kt:224`
  (review) + `WorkshopViewModel.kt:198-211` ×14 (my own whole-`main` sweep). These are decimal-separator
  fixes the grouping helper doesn't cover. → New **edit 4b** adds them with `Locale.ROOT` (matching the
  pattern the codebase's other `String.format` sites already use).
- **MINOR — L91:** capitalizing "steps"→"Steps" is grammatically wrong in the verb phrase "%1$d steps
  walked"; cited precedents are noun/label contexts. → Reversed to **LEAVE AS-IS** (edit 5 = no-op).
- **MINOR — `EnumDisplayNameTest`** legacy-verbatim guard reconstructs the transform without `Locale.ROOT`. →
  New **edit 6** mirrors the test lambda to production.
- **NIT — `source-files.md:168`** literally says `skuId() returning name.lowercase()`. → Task-list step 3
  updates the substring.
- **NIT — verification grep** `getNumberInstance()` won't match the surviving `getNumberInstance(Locale.US)`
  in the helper. → Grep-proof section now states the one expected surviving hit.

Independent pre-checks (corroborating the empty behavior-equivalence dimension): whole-`main` sweeps
confirmed the L88/L89 case sites (4) and L87 grouping sites (7) are complete; the `%.Nf` gap was found by my
own `String.format` sweep before the review independently flagged `LabsScreen:224`.

**Net: +2 JVM tests (1253 → 1255); the `%.Nf` edits add no test (covered by the established sibling
pattern).**
