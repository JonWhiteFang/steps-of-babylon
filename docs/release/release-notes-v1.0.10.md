# Release Notes — v1.0.10 (versionCode 26)

**Track:** Play Console **internal**
**Tag:** `v1.0.10` · **Supersedes:** `v1.0.9` (versionCode 25, 2026-06-16)
**Release lane:** automated — `v*` tag → CI `release.yml` → signed AAB → Play internal track. See `docs/plans/plan-32-ci.md` + ADR-0018.

---

## What's new — Play Console "What's new in this version" (≤500 chars)

This is the message published to the Play internal track via the annotated tag (`release.yml`
reads `git tag -l --format='%(contents)'` into `distribution/whatsnew/whatsnew-en-US`, capped at
Play's 500-char limit). Verbatim (293 chars):

```
A reliability-focused update so your steps always count:

• Optional "allow background activity" prompt keeps step counting alive on phones that aggressively sleep apps
• Screens now offer a Retry when something fails to load
• Steadier handling of purchases made offline
• Safer data handling under the hood

Keep walking!
```

---

## What shipped (developer detail)

Everything accumulated since v1.0.9 — **4 fix waves, 4 squash-merged PRs** (#270/#272/#274/#276). No new
features, **no schema change**. Full per-change detail is in `CHANGELOG.md` under `[1.0.10]` and the
entries beneath it. **1081 → 1110 JVM tests** across the four waves.

### Player-facing

- **#261 — battery-optimization whitelist primer.** The GDD's top-rated risk: on aggressive-OEM devices,
  Doze / App-Standby kills the foreground step service and steps silently stop. Added a contextual,
  dismissible onboarding primer (granted-branch) + a durable Settings "Background activity" re-offer that
  fires `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`. Never blocks the flow. (ADR-0029.)
- **#194 — load-error retry states.** The 10 data-backed menu screens now surface a shared error state
  with a **Retry** when their data flow throws, instead of hanging on a spinner. (ADR-0028.)
- **#250 — offline purchase reconcile.** Pending Play purchases are reconciled time-bounded + best-effort
  on resume (foreground) and via the 15-min worker (background), so an offline-completed purchase isn't
  stranded past Play's 3-day auto-refund window. (ADR-0028.)
- **#233 — battle is portrait-locked.** Rotating mid-round previously destroyed the in-flight round
  (fresh engine vs. surviving ViewModel). Battle now locks to portrait on entry and restores on exit, so
  the desync is unreachable. (ADR-0029.)
- **#195 — daily missions roll over at midnight** without needing an app restart; **#193 — no-sensor
  signposting**: a device without a step counter now gets steered toward Health Connect on the final
  onboarding slide instead of a silent dead-end.

### Not player-visible (data-integrity / reliability hardening)

- **#236 — atomic premium spend.** Card-pack opens and UW unlocks now deduct + grant in one transaction
  (commit/roll-back together), extending the guarded-deduct pattern to the premium paths. (ADR-0027.)
- **#237 — migration-chain guard.** A pure `AppMigrations.validateChain` + `MigrationChainTest` fail the
  build if a future version bump forgets to register a `Migration` object (the worst migration failure
  mode — a guaranteed launch crash for upgrading users — was previously uncaught by CI). (ADR-0030.)
- **#238 — scoped decrypt-fail recovery.** `DatabaseKeyManager` now wipes the local DB **only** when the
  Keystore alias is provably absent (true device-restore); a transient Keystore fault rethrows instead of
  destroying non-regenerable progress. (ADR-0030.)
- **#248 — safe data wipe.** "Delete All Data" awaits WorkManager cancellation before closing the DB, so
  an in-flight step-sync worker can't write to a closed database. (ADR-0030.)

> **1110 JVM tests** + 9 instrumented (up from 1081 JVM at v1.0.9). Added coverage is concentrated in the
> reliability wave (#236/#195/#193, +12), graceful degradation (#194/#250, +5), background reliability
> (#261/#233, +2), and the data-integrity wave (#237/#238/#248, +10). `@Composable` visual pieces remain
> untested per the house norm — verified by on-device sign-off.

---

## Provenance (review trail)

Each wave's spec/plan went through the repo's review discipline before implementation, then TDD where
there's a seam. With **ultracode off** for these sessions, the Adversarial Review Gate ran in its lighter
single-agent form (flagged + chosen each time), which caught real pre-code defects:

- **Reliability wave (#236/#195/#193)** — single-agent review; its scope-changing finding (drop an
  unnecessary interface for #193 — mockito-core 5.x mocks final classes) applied.
- **Graceful degradation (#194/#250)** — caught 2 real defects pre-code: `.catch` must live *inside*
  `flatMapLatest` (else `retry()` is a no-op), and `reconcilePendingPurchases()` needs `withTimeoutOrNull`
  (no internal connect timeout). ADR-0028.
- **Background reliability (#261/#233)** — caught a re-show bug: the construction-time
  `shouldOfferBatteryExemption` is stale after the grant, so both primer buttons must set the handled
  flag. ADR-0029.
- **Data-integrity wave (#237/#238/#248)** — caught a critical pre-code defect: the #238 catch-branch
  isn't testable through `getPassphrase` under Robolectric (`KeyStore.getInstance` throws before any
  decrypt), so the wipe-vs-rethrow decision was extracted as a pure seam. ADR-0030.

---

## Verification (release lane)

- CI `release.yml` builds the **committed** `versionCode` (no auto-bump — Play rejects reused codes).
  versionCode advanced 25 → 26; versionName 1.0.9 → 1.0.10 (the bump rides in with this release PR).
- `bundleRelease` runs R8 minify + lint-vital + signing; `jarsigner -verify` confirms the AAB is signed
  with the production upload keystore (Play App Signing enrolled).
- The release environment requires the `PLAY_LICENSE_KEY` secret — a release build with a blank key is
  hard-failed by the `app/build.gradle.kts` taskGraph guard so the billing fail-open can never ship.
- JVM unit suite green locally + in CI (1110 tests, 0 failures); CI PR gate + instrumented lane gate this
  release PR before merge. On-device feel/visual sign-off is the developer's call.

---

## Next

Promotion to the **closed** track remains judgment-gated on the Closed-Test Readiness Gate (see
`docs/plans/plan-FORWARD.md`). With this release, **all 4 net-new HIGHs from the 2026-06-18 audit
(#233/#236/#250/#261) and the last Gate-H `severity:major` soak items (#193/#194/#195) are shipped.** The
remaining promotion prerequisite that the repo cannot satisfy is the **manual Play Console Data-Safety
action** (#192, `docs/release/data-safety-form.md`). The 2026-06-18 audit verdict is **7/10 — keep
shipping internal, not public-ready**; the med/low backlog (#262 tracker + #251/#249 + the larger #233
Simulation-hoist) is post-this-release work.
