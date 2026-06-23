# Release Notes — v1.0.12 (versionCode 28)

**Track:** Play Console **internal**
**Tag:** `v1.0.12` · **Supersedes:** `v1.0.11` (versionCode 27, 2026-06-23)
**Release lane:** automated — `v*` tag → CI `release.yml` → signed AAB → Play internal track. See `docs/plans/plan-32-ci.md` + ADR-0018.

---

## What's new — Play Console "What's new in this version" (≤500 chars)

This is the message published to the Play internal track via the annotated tag (`release.yml`
reads `git tag -l --format='%(contents)'` into `distribution/whatsnew/whatsnew-en-US`, capped at
Play's 500-char limit). Developer-approved. Verbatim (~320 chars):

```
Another polish update — no new mechanics, just a calmer, more reliable game:

• Notifications now stay quiet overnight (10pm–8am), and supply-drop pings are capped so a long walk won't flood you
• Fixed a purchase bug that could affect some device languages (e.g. Turkish)
• Behind-the-scenes cleanup and stability work

Keep walking!
```

---

## What shipped (developer detail)

Everything accumulated since v1.0.11 — the audit-triage batches A–D plus two focused audit fixes.
**No new features, no schema change** (the `app/schemas` tree is byte-identical to v1.0.11). Full
per-change detail is in `CHANGELOG.md` under `[1.0.12]` and the entries beneath it. **1256 → 1277 JVM
tests** across the body.

### Player-facing

- **Notification quiet-hours + supply-drop cap (#216 / NOTIF-1).** Reminder and supply-drop
  notifications now respect a fixed local quiet-hours window (22:00–08:00), and supply-drop *pushes* are
  capped at 3/day — the drop itself is still generated and claimable in-app; only the off-hours/spammy
  notification is suppressed. Addresses the Play "disruptive notifications" policy concern + retention.
  Decision logic is a new pure-domain `NotificationPolicy` (JVM-tested); `SupplyDropNotificationManager`
  reads the clock via the injected `TimeProvider` and field-caches its prefs counter (it runs under the
  #120 credit mutex).
- **Locale purchase fix (Batch C / L88).** `BillingProduct.skuId()` used a default-locale `lowercase()`
  that corrupted `GEM_PACK_MEDIUM`'s `I` → dotless `ı` under the Turkish/`az` locale, breaking that
  purchase + its reconciliation. Fixed with `Locale.ROOT` + a Turkish-locale regression test.

### Not player-visible (cleanup / hardening / infra)

- **Dead-cosmetic removal (#221 / FEAT-1).** Removed the 4 seeded projectile/enemy-skin cosmetics
  (`proj_fire`/`proj_lightning`/`enemy_shadow`/`enemy_neon`) + the 2 unused `CosmeticCategory` values
  that had no render path — closing the audit "live trap" (a cosmetic that could be enabled for sale yet
  render nothing). Un-buyable today, so no player-visible change; existing-device data is cleaned up
  safely at next launch (a `CosmeticDao.deleteByIds` purge + a resilient `toDomainOrNull` mapping that
  can't crash on a legacy category). No schema migration (data-only delete).
- **Batch D2 — additive CI tooling (#262 L77 + #218).** Non-gating Kover coverage step + an OSV-Scanner
  supply-chain scan into Code Scanning. No app/schema change.
- **Batch D1 — release/CI config hardening (#262 L39/L68/L71/L73/L74/L75/L50).** release.yml
  concurrency/tag-guard/cert-identity/secret-cleanup; ci.yml `lintRelease` gate; gradle parallel+caching;
  a ktlint-job CI-speed split. No app/schema change.
- **Batch B — dead-code removal (#262 L15/L16/L17/L13/L18).** Unused code paths removed; no behavior change.
- **Batch A — audit docs/content-drift sweep (#262 L79/L81–L86/L93–L95).** Docs reconciled to ground
  truth; no production-code change. Also closed **#164** Bundle E (verify-and-close — shipped v1.0.8,
  never closed on the tracker).

> **1277 JVM tests** + 9 instrumented (up from 1256 at v1.0.11). `@Composable` visual pieces beyond the
> Robolectric coverage remain on-device-verified per the house norm.

---

## Provenance (review trail)

Both code-changing fixes (#216, #221) went through the full repo discipline — spec → Adversarial Review
Gate → plan → Adversarial Review Gate → TDD — under ultracode (multi-agent fan-out). The #216 spec review
surfaced 3 minor refinements (folded in); the #221 spec review surfaced 6 (folded in) and its **plan**
review caught a task-ordering bug that would have broken the green-at-every-commit invariant — the plan
was restructured into a behavior-preserving refactor + one atomic removal commit before any code was
written. The audit-triage batches A–D were each reviewed + merged in prior sessions (recorded per-PR in
`CHANGELOG.md`/`RUN_LOG.md`). This release collateral adds **no production code** — everything here already
landed on `main` in PRs #333–#341.

---

## Verification (release lane)

- CI `release.yml` builds the **committed** `versionCode` (no auto-bump — Play rejects reused codes).
  versionCode advanced 27 → 28; versionName 1.0.11 → 1.0.12 (the bump rides in with this release PR).
- `bundleRelease` runs R8 minify + lint-vital + signing; `jarsigner -verify` confirms the AAB is signed
  with the production upload keystore (Play App Signing enrolled).
- The release environment requires the `PLAY_LICENSE_KEY` secret — a release build with a blank key is
  hard-failed by the `app/build.gradle.kts` taskGraph guard so the billing fail-open can never ship.
- JVM unit suite green locally + in CI (**1277 tests, 0 failures**); CI PR gate + instrumented lane gate
  this release PR before merge. On-device feel/visual sign-off is the developer's call.
- **No schema change:** `app/schemas/` is byte-identical to v1.0.11 (confirmed via `git diff --stat
  v1.0.11..HEAD -- app/schemas/`, empty).

---

## Next

Promotion to the **closed** track remains judgment-gated on the Closed-Test Readiness Gate (see
`docs/plans/plan-FORWARD.md`). The remaining promotion prerequisite the repo cannot satisfy is the
**manual Play Console Data-Safety action** (#192, `docs/release/data-safety-form.md`). Post-this-release
work is the audit backlog's non-batchable items (#217 service tests, A24 anti-cheat clock-tamper, L12
BattleViewModel decomposition / #306 ADR-0012 follow-up) and the #34 i18n-externalization push.
