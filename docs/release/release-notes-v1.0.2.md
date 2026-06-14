# Release Notes — v1.0.2 (versionCode 18)

**Track:** Play Console **internal** (live)
**Tag:** `v1.0.2` (2026-06-11) · **Supersedes:** `v1.0.1` (versionCode 17, 2026-06-04)
**Release lane:** automated — `v*` tag → CI `release.yml` → signed AAB → Play internal track. See `docs/plans/plan-32-ci.md` + ADR-0018.

> These are the first release notes for a **production** versionName. The earlier
> `release-notes-v5.md` / `release-notes-v6.md` documented *internal build* iterations
> (versionCode 5 / 6) during the v1.0.0 closed-test prep and are historical. v1.0.0 itself
> was never tagged — the versionName advanced to 1.0.1 → 1.0.2 to avoid a versionCode
> collision (vc17 was already consumed). `v1.0.1` and `v1.0.2` git tags both exist.

---

## What's new — Play Console "What's new in this version" (≤500 chars)

This is the message published to the Play internal track via the annotated tag (`release.yml`
reads the annotated-tag message via `git tag -l --format='%(contents)'` into
`distribution/whatsnew/whatsnew-en-US`, capped at Play's 500-char limit). Verbatim:

```
Stability & fairness update:
• Fixed the enemy counter showing negative numbers in long runs
• Fixed daily missions occasionally appearing twice
• Stronger in-app purchase verification
• Fixed several rare crashes and save-data edge cases
• Smoother battle performance

Thanks for playing — keep walking!
```

---

## What shipped (developer detail)

### v1.0.2 (versionCode 18, 2026-06-11) — Closed-Test Readiness Gate (Gate B + D) hardening

- **#127 — duplicate daily missions (schema v11→v12).** A check-then-insert with no DB-level
  uniqueness let two concurrent ViewModel inits each insert a full batch → 6 independently-claimable
  daily missions/day, inflating Gem/Power-Stone payouts. Fixed with a `(date, missionType)` unique
  index + `@Insert(onConflict = IGNORE)` + a recreate-table dedup migration (`MIGRATION_11_12`).
- **#146 — enemy counter drifts negative mid/late run.** The HUD enemy count is now derived live from
  the entity list under `entitiesLock`; the desync-prone `WaveSpawner.enemiesAlive` tally was removed.
  `EnemyEntity.takeDamage` gained an `if (!isAlive) return 0.0` guard, which also fixes a kill-reward
  double-credit on the projectile path.
- **#124 — client-side Play purchase signature verification.** Every wallet grant now passes a
  `PurchaseVerifier.isValidPurchase(originalJson, signature, expectedProductId, expectedPurchaseToken)`
  RSA check before the atomic grant, on both the purchase and reconciliation paths. The product+token
  binding blocks replaying a signed cheap receipt for an expensive product. (Client-side only — the
  server-side round-trip remains out of scope per `CONSTRAINTS.md`.) ADR-0005 amendment.
- **Quick-clear audit-Low wave (Gate B + D)** — 8 low-severity audit findings plus a latent card-pack
  crash (#35) cleared.
- **#121 — `daily_step_record` lost-update** closed via column-targeted `ON CONFLICT … DO UPDATE SET`
  upserts (disjoint-column writers no longer clobber each other).
- **#126 / #125 — battle performance.** Game-loop catch-up bounded (`clampAccumulator`,
  `MAX_CATCHUP_TICKS = 8`); `getAliveEnemies` allocation halved.

### v1.0.1 (versionCode 17, 2026-06-04) — first CI-driven release

- First release shipped end-to-end by the CI release lane (Plan 32 / ADR-0018): `v*` tag →
  `bundleRelease` → `jarsigner -verify` → upload to the Play internal track + GitHub Release.
- Automated Play "What's new" notes sourced from the annotated tag message.
- Plus the work merged between v1.0.0 and the tag: V1X-13 i18n phase 1 (notification + battle/workshop
  string extraction, `HardcodedText` lint-as-error guard), V1X-09 simulation extraction completion,
  the `#27` domain-purity guard, and a Dependabot dependency wave.

> Per-PR detail and the full entry list live in `CHANGELOG.md` (`[1.0.2]` and `[1.0.1]` sections).
> This doc is the release-collateral summary; the CHANGELOG is the authoritative change record.

---

## Verification (release lane)

- CI `release.yml` builds the **committed** `versionCode` (no auto-bump — Play rejects reused codes).
- `bundleRelease` runs R8 minify + lint-vital + signing; `jarsigner -verify` confirms the AAB is signed
  with the production upload keystore (Play App Signing enrolled).
- The release environment requires the `PLAY_LICENSE_KEY` secret — a release build with a blank key is
  hard-failed by the `app/build.gradle.kts` taskGraph guard so the billing fail-open can never ship.

---

## Next

Promotion to the **closed** track is judgment-gated on the Closed-Test Readiness Gate (see
`docs/plans/plan-FORWARD.md`). The ≥14-day tester soak and production access are Phase 2 — they begin
*after* the developer decides to promote, not now.
