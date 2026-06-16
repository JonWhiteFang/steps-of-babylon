# Release Notes — v1.0.8 (versionCode 24)

**Track:** Play Console **internal**
**Tag:** `v1.0.8` · **Supersedes:** `v1.0.7` (versionCode 23, 2026-06-15)
**Release lane:** automated — `v*` tag → CI `release.yml` → signed AAB → Play internal track. See `docs/plans/plan-32-ci.md` + ADR-0018.

---

## What's new — Play Console "What's new in this version" (≤500 chars)

This is the message published to the Play internal track via the annotated tag (`release.yml`
reads `git tag -l --format='%(contents)'` into `distribution/whatsnew/whatsnew-en-US`, capped at
Play's 500-char limit). Verbatim (267 chars):

```
Babylon gets a new look:

• A bold, carved-stone display font across titles and headers
• Onboarding now glows through the biomes — Hanging Gardens to the Celestial Gate — ending on a real ziggurat
• Battle controls moved to a tidy left-hand rail so nothing overlaps mid-fight

Thanks for playing — keep walking!
```

---

## What shipped (developer detail)

Two **presentation-only** changes since v1.0.7 — zero engine / economy / domain / loadout / concurrency /
persistence / schema change. Full detail is in `CHANGELOG.md` under `[1.0.8]`.

### Look & Feel Bundle E (#164) — identity / art (PR #178, squash `9fd40b9`)

The **last** of the five A–E look-&-feel waves off the 2026-06-12 UX review. The project's first bundled
font + art assets.

- **Custom Cinzel display font** (SIL OFL 1.1, bundled in `res/font/`; license at
  `licenses/OFL-Cinzel.txt`) applied to the **Display + Headline** type tiers + biome names on the battle
  transition; body/title/label stay Roboto for legibility. Added the previously-missing
  `displayMedium`/`displayLarge` tokens (closing a latent gap where onboarding silently fell back to the
  Material3 stock style), guarded by `SobTypographyTest`.
- **Onboarding per-slide biome theming** — a `BiomeTheme` sky-gradient behind the carousel (Hanging
  Gardens → Burning Sands → Frozen Ziggurats → Celestial Gate; Underworld skipped), cross-faded on the
  pager offset via the pure `lerpArgb` / `crossfadeNeighborIndex` helper (`presentation/ui/ColorLerp.kt`;
  static under reduced-motion), a legibility scrim behind the text, and a one-shot completion pulse
  (reuses Bundle C's `PurchasePulse`, sequenced **persist-first → pulse → navigate** so the onboarding
  gating/nav contract is preserved).
- **Vector ziggurat emblem** (`ic_ziggurat_emblem.xml`) replacing the slide-1 🏛️ temple emoji
  ("literally the wrong building").

### Fixed — battle bottom-chrome overlap (#171) (PR #177)

The battle screen's bottom controls (speed `1x`/`2x`/`4x`, pause, upgrade) moved from a bottom-center
row to a **left vertical rail**, so they no longer overlap or clip the Ultimate-Weapon cooldown bar or
the in-round upgrade menu. The UW cooldown bar owns the bottom-center strip alone; the in-round upgrade
menu spans the full screen width and clears the rail vertically. New shared
`presentation/battle/ui/BattleControlRail.kt`; button bodies extracted verbatim (no behaviour change).

> **1010 JVM tests** (up from 996 at v1.0.7 — +14 from Bundle E: `SobTypographyTest`,
> `ColorLerpTest`, `OnboardingContentTest`; #171 added no net JVM tests) + 9 instrumented. The
> `@Composable` visual pieces (font render, onboarding gradient/cross-fade/scrim/pulse/emblem, the
> battle rail layout) are untested per the house norm — verified by the on-device feel sign-off.

---

## Provenance (review trail)

Both changes went spec → plan → implementation, each gate-reviewed per the repo's **Adversarial Review
Gate**, then subagent-driven-development with per-task spec + quality review and a final whole-branch
review.

- **Bundle E:** spec adversarial review ~39 findings → 13 surviving (0 unaddressed critical/major; the
  major catch — the original "gold shimmer reusing Bundle C" finish beat was unbuildable — was redefined
  as a persist-first `PurchasePulse`); plan adversarial review ~25 → 9 surviving (0 critical/major; the
  transparent-`Surface` "black text" finding was refuted by a code-grounded trace). **Execution caught a
  build-breaker both reviews missed:** the OFL license `.txt` cannot live in `res/font/` (the AAPT
  resource merger rejects non-font files there) — relocated to `licenses/OFL-Cinzel.txt`. Final
  whole-branch review: READY TO MERGE, 0 critical/major. **ADR-0024.**
  Spec: `docs/superpowers/specs/2026-06-15-look-and-feel-bundle-e-design.md` ·
  Plan: `docs/superpowers/plans/2026-06-15-look-and-feel-bundle-e.md`
- **#171:** spec review 21 findings → 14 surviving / 7 refuted (0 critical/major); plan review 10 → 4
  surviving / 6 refuted. Spec: `docs/superpowers/specs/2026-06-15-battle-bottom-chrome-overlap-design.md`.

---

## Verification (release lane)

- CI `release.yml` builds the **committed** `versionCode` (no auto-bump — Play rejects reused codes).
  versionCode advanced 23 → 24; versionName 1.0.7 → 1.0.8 (the bump rode in with PR #178).
- `bundleRelease` runs R8 minify + lint-vital + signing; `jarsigner -verify` confirms the AAB is signed
  with the production upload keystore (Play App Signing enrolled).
- The release environment requires the `PLAY_LICENSE_KEY` secret — a release build with a blank key is
  hard-failed by the `app/build.gradle.kts` taskGraph guard so the billing fail-open can never ship.
- JVM unit suite green locally + in CI (1010 tests, 0 failures) + CI PR gate + instrumented lane were
  green on PR #178 before merge. On-device feel/visual sign-off (Cinzel renders + digit legibility;
  onboarding gradient cross-fade + scrim contrast + emblem + completion pulse; battle rail clears the
  HUD) done by the developer.

---

## Next

Promotion to the **closed** track remains judgment-gated on the Closed-Test Readiness Gate (see
`docs/plans/plan-FORWARD.md`). Bundle E was the last of the A–E look-&-feel review bundles, so the
remaining code-actionable gate items are **#29** (upgrade decision-support, Gate F) and **#26**
(performance/battery, Gate G); the manual play-feel gates (A audio, E balance) are the developer's
call. The ≥14-day tester soak and production access are Phase 2 — they begin *after* the developer
decides to promote.
