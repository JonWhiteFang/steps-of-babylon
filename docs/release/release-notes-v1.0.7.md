# Release Notes — v1.0.7 (versionCode 23)

**Track:** Play Console **internal**
**Tag:** `v1.0.7` · **Supersedes:** `v1.0.6` (versionCode 22, 2026-06-14)
**Release lane:** automated — `v*` tag → CI `release.yml` → signed AAB → Play internal track. See `docs/plans/plan-32-ci.md` + ADR-0018.

---

## What's new — Play Console "What's new in this version" (≤500 chars)

This is the message published to the Play internal track via the annotated tag (`release.yml`
reads `git tag -l --format='%(contents)'` into `distribution/whatsnew/whatsnew-en-US`, capped at
Play's 500-char limit). Verbatim (243 chars):

```
Your collection now shows off its rarity:

• Ultimate Weapons and Cards get colour-coded rarity borders and badges
• A bold EQUIPPED chip so you can see your loadout at a glance
• A clear "3/3 — unequip one" hint when a loadout is full

Thanks for playing — keep walking!
```

---

## What shipped (developer detail)

Look & Feel **Bundle D (#163)** — the *collectibles rarity* wave of the 2026-06-12 UX review (PR #174,
squash `d317fdc`). **Presentation-only** — zero engine / economy / domain / loadout / concurrency /
persistence / schema change (0 lines touched in `domain/**`, `service/**`, `di/**`,
`battle/{engine,entities,effects}`, the ViewModels, DAOs/entities/migrations, build logic). Full
detail is in `CHANGELOG.md` under `[1.0.7]`.

### Shared rarity helper (greenfield)

- New `presentation/ui/Rarity.kt`: a presentation-only `RarityTier` (3-tier palette) + pure
  `color()` / `cardRarityTier(CardRarity)` / `uwRarityTier(unlockCost)` / label functions, plus the
  Compose primitives `RarityBadge`, `EquippedChip`, and `Modifier.rarityBorder` (3dp border + left
  accent bar, clipped to the card shape). One new `RaritySand` theme token — tiers 1/2 reuse the
  existing `LapisLight` / `Gold` tokens.
- The shared 3-colour palette is identical on both screens; only the **label** shifts: Cards keep
  COMMON / RARE / EPIC; Ultimate Weapons derive **RARE / EPIC / LEGENDARY** from `unlockCost`
  (≤60 / 61–89 / ≥90) — no UW is ever "common", and there is no domain rarity field to change.

### Cards + Ultimate Weapons screens

- **Prominent rarity treatment** (3dp rarity border + left accent bar + filled pill badge) replaces
  the thin generic border / no-identity look the UX review flagged.
- **Explicit EQUIPPED chip** replaces the tiny green ✓ (UW) and the implicit `primaryContainer`
  background tint (Cards) — the chip is now the sole equipped signal.
- **Header loadout-cap hint** ("3/3 — unequip one to swap") on both screens at the 3-cap.
- **Locked Ultimate Weapons show dimmed rarity** (border + badge at alpha 0.5×) — aspirational, not
  hidden.
- Fixes a latent palette bug: Epic cards were the same colour as the Power-Stone currency glyph
  (`#B57EDC`); Epic now reads gold.

> **996 JVM tests** (up from 990 at v1.0.6 — +6 `RarityTest` covering the pure tier-mapping / label /
> colour-distinctness functions) + 9 instrumented. The `@Composable` rarity pieces are untested per the
> house norm (verified by the on-device feel sign-off).

---

## Provenance (review trail)

Spec → plan → implementation, each gate-reviewed per the repo's **Adversarial Review Gate**:
- Spec adversarial review: 14 findings → 8 applied → 6 refuted (root fix: `color()` is a plain fun,
  not `@Composable`, so it's JVM-testable and callable from the Modifier).
- Plan adversarial review: 6 findings → 5 applied → 1 refuted (caught the locked-UW dimming gap before
  any code was written).
- Built via subagent-driven-development (fresh implementer + spec-review + quality-review per task); the
  Task-2 quality review caught + fixed an accent-bar corner-bleed by clipping to the card shape.
- Final whole-branch review: READY TO MERGE, 0 critical/major; diff confirmed 100% presentation-only.

Spec: `docs/superpowers/specs/2026-06-14-look-and-feel-bundle-d-design.md` ·
Plan: `docs/superpowers/plans/2026-06-14-look-and-feel-bundle-d.md`

---

## Verification (release lane)

- CI `release.yml` builds the **committed** `versionCode` (no auto-bump — Play rejects reused codes).
  versionCode advanced 22 → 23; versionName 1.0.6 → 1.0.7.
- `bundleRelease` runs R8 minify + lint-vital + signing; `jarsigner -verify` confirms the AAB is signed
  with the production upload keystore (Play App Signing enrolled).
- The release environment requires the `PLAY_LICENSE_KEY` secret — a release build with a blank key is
  hard-failed by the `app/build.gradle.kts` taskGraph guard so the billing fail-open can never ship.
- JVM unit suite green locally + in CI (996 tests, 0 failures) + CI PR gate + instrumented lane were
  green on PR #174 before merge. On-device feel/visual sign-off (rarity reads at a glance; EQUIPPED chip
  unmistakable; cap hint at 3/3; locked UW dimmed; accent bar not clipped) done by the developer.

---

## Next

Promotion to the **closed** track remains judgment-gated on the Closed-Test Readiness Gate (see
`docs/plans/plan-FORWARD.md`). The remaining code-actionable gate items are **#29** (upgrade
decision-support, Gate F) and **#26** (performance/battery, Gate G); the manual play-feel gates
(A audio, E balance) are the developer's call. The look-&-feel bundle **#164** (custom font +
onboarding per-slide theming + real ziggurat asset) is the next feature work. The ≥14-day tester soak
and production access are Phase 2 — they begin *after* the developer decides to promote.
