# ADR-0005: Real Google Play Billing Library integration

**Date:** 2026-05-08
**Status:** Proposed (stub — concrete decision deferred to C.5 PR 1 scoping)

## Context

All In-App Purchase functionality is currently stubbed via `StubBillingManager` (`app/src/main/java/.../data/billing/`). The stub satisfies the `BillingManager` interface (`domain/repository/`), credits Gems / flags instantly after a 500 ms simulated delay, and always returns `PurchaseResult.Success`. It was landed in Plan 26 as a deliberate architectural seam: the whole app — `StoreViewModel`, `CurrencyDashboardViewModel`, `TrackDailyLogin` (Season Pass bonus), `LabsViewModel` (free Lab rush), `BattleViewModel` (ad-removal visibility), `CardsViewModel` (free pack ad), and the test fakes — talks only to `BillingManager`, so swapping to a real implementation is a single DI-binding flip away.

Shipping v1.0 to the Play Store per Plan 31 requires replacing the stub with a real integration:

- **5 products** defined in `domain/model/BillingProduct.kt`: 3 consumable Gem packs (50 / 300 / 700 Gems at $0.99 / $4.99 / $9.99), 1 one-time Ad Removal ($3.99), 1 monthly Season Pass subscription ($4.99/mo).
- **2 enum constants** carry the SKU IDs as source-of-truth in code.
- **`PurchaseResult` sealed class** has two variants (`Success`, `Error(message)`) — both must have realistic failure-path coverage before C.5 PR 1 lands, since A.4 already exercised them on the stub fakes.
- **`CONSTRAINTS.md` forbids a server backend** for v1.0, so receipt validation is client-side only. Server-side validation is a known post-v1.0 hardening step.

Risk shape: **high.** Real Play Billing failure paths (network loss mid-purchase, user cancels in the Google sheet, pending purchase state after app kill, subscription renewal edge cases, SKU mismatch between Play Console and code) have never run against production code. A broken billing path directly affects revenue and player trust.

## Decision (stub)

**TBD.** The concrete design — Play Billing Library version pin, exact class shape of `BillingManagerImpl`, reconnection policy, pending-purchase handling — will be finalised when C.5 PR 1 is scoped. The decision recorded here is only the commitment to:

1. **Library: Google Play Billing Library v7** (or the most-recent stable when C.5 PR 1 lands; v7 is the current line as of 2026-05).
2. **Impl location:** `data/billing/BillingManagerImpl.kt` alongside `StubBillingManager`. Both coexist briefly under a `BuildConfig.USE_REAL_BILLING` flag so debug + internal-track builds can diverge from release builds while the real path is hardened.
3. **Listener-based async model.** Wrap `BillingClient` + `PurchasesUpdatedListener` as a `suspend fun purchase(product: BillingProduct): PurchaseResult`, adapting the callback to the existing interface via `suspendCancellableCoroutine`. No interface changes.
4. **Receipt-idempotency table in Room.** A new `billing_receipts` entity keyed by `orderId` with a `granted: Boolean` column. Every purchase writes the receipt + flips `granted = true` atomically in one `@Transaction`. Re-delivery of the same `orderId` (pending purchases resolved after app restart, multi-device edge cases) sees `granted = true` and short-circuits to `PurchaseResult.Success` without re-crediting. DB schema bump v8 → v9 with an explicit `Migration` object per `CONSTRAINTS.md`.
5. **`onResume` pending-purchase sweep.** On every app resume / `StoreScreen` enter, call `BillingClient.queryPurchasesAsync()` and reconcile any pending or unresolved purchases against the receipt table. Prevents the "user restarted the app mid-purchase" data-loss case.
6. **SKU drift mitigation.** `BillingProduct` enum stays the single source of truth. Add a startup sanity check that `queryProductDetailsAsync()` returns every product in the enum; log a warning + disable the affected Store card if any are missing. Play Console SKU IDs must match enum names byte-for-byte; this is documented in `docs/release/signing-guide.md` follow-up.
7. **3-PR rollout** (RO-07-style incremental swap):
   - **PR 1:** New `BillingManagerImpl` + receipt table + migration + unit tests. `@Binds` still points at `StubBillingManager`. Zero runtime behaviour change.
   - **PR 2:** Flip `@Binds` to `BillingManagerImpl` behind `BuildConfig.USE_REAL_BILLING`. Flag defaults to false in debug, true in release. Verify on internal test track + closed track.
   - **PR 3:** Delete `StubBillingManager` once internal + closed tracks confirm the real path for ~1 week. Rollback window closes.

## Rationale

- **PBL v7 is the current stable line.** Google deprecated v5 in 2025-Q3; v6 sunsets 2026-Q4. v7 is the only line with guaranteed Play Store acceptance windows for a 2026-2027 v1.0 launch. Pinning to v7 minimises forced upgrades.
- **No server = idempotency lives in Room.** `CONSTRAINTS.md` forbids a backend for v1.0. The risk of grant duplication (user gets charged twice, or user gets credited twice for one charge) is the single highest-impact billing failure. Receipt-idempotency in Room sidesteps the whole category by making "this orderId has been granted" an atomic, process-survivable predicate.
- **`BuildConfig` flag, not a runtime flag.** A runtime flag would need a remote-config dependency (Firebase / back-end) forbidden by `CONSTRAINTS.md`. A compile-time flag is good enough because the rollout strategy is internal-track → closed-track → release, each built separately.
- **Listener-to-suspend adaptation is well-trodden.** `suspendCancellableCoroutine` + `BillingClient` is a standard Android pattern; no custom abstraction needed. The fake `FakeBillingManager` already returns `suspend` on its `purchase` method, so the interface is pre-shaped for this.
- **3-PR rollout avoids a big-bang swap.** The stub stays deletable for ~1 week post-enablement so a regression found in early rollout can hotfix by flipping the flag, not by reverting a large PR chain.

## Consequences

- New `billing-play:<v7.x.y>` dependency in `gradle/libs.versions.toml`. Pinned version, never ranged.
- New `app/proguard-rules.pro` keep rules per Play Billing release notes (billing classes, listener interfaces, reflection-based response parsing).
- `BuildConfig.USE_REAL_BILLING` (boolean, product-flavour or buildType-specific) added via `app/build.gradle.kts`. Debug default: false. Release default: true. Override for internal-track testing via `keystore.properties` entry.
- Room DB v8 → v9 with `billing_receipts` entity + explicit `Migration`. `AppDatabase` entity list grows to 13.
- `StubBillingManager` eventually deleted — one call site (`di/BillingModule.kt` `@Binds`) and the fake stays on the test classpath. Existing `FakeBillingManager` continues to cover VM tests; it does not grow real-billing awareness.
- `StoreViewModel` contract **unchanged** — the swap is pure impl-side. Existing failure-path coverage from A.4 (`FakeBillingManager.resultQueue`) continues to be the unit-test safety net.
- `TrackDailyLogin` Season Pass bonus path unchanged — still reads `profile.seasonPassActive` + `profile.seasonPassExpiry`. The real `BillingManagerImpl` writes those fields from the Billing library's subscription state instead of from the stub's 30-day hardcode.
- Play Console setup (product IDs, subscription base plan, test accounts) is external and lives in `docs/release/signing-guide.md` follow-up.

## Non-goals / future work

- **No server-side receipt verification.** Forbidden by `CONSTRAINTS.md` for v1.0. Client-side receipt validation (`BillingClient.acknowledgePurchaseAsync` for consumables, subscription state from `queryPurchasesAsync`) is the ceiling. Server-side validation is a known post-v1.0 hardening step tracked separately.
- **No custom subscription-renewal UI.** The Season Pass renewal flows through Google's standard subscription management; the app only reads current state.
- **No price-tier experiments.** Prices are fixed in `BillingProduct` enum. A/B pricing is a post-v1.0 growth concern.
- **No grace-period billing.** Subscription grace period is Google's default (3 days typically); we don't maintain our own grace-period clock.
- **No promotional codes, intro pricing, or region-specific pricing in v1.0.** Play Console supports these but they compound the testing matrix; post-v1.0.
- **Do not refactor `BillingManager` interface.** It stays stable through the swap. Adding a new method (e.g. `queryProductDetails()` for a product-details screen) is a future-PR concern.

## Open questions (to be resolved in C.5 PR 1 scoping)

- **Reconnection policy.** `BillingClient` can disconnect (Google Play Services update, user revokes permissions, etc.). Retry with exponential backoff? Give up after N attempts? Surface the error in `StoreUiState.userMessage`?
- **Acknowledgment timing.** For consumables (Gem packs), when exactly does `BillingClient.consumeAsync()` fire — before or after `playerRepository.addGems()`? The ordering matters for the "app killed mid-purchase" edge case.
- **Test SKU IDs for debug builds.** Use Google's reserved test SKUs (`android.test.purchased` etc.) so the debug flag path can exercise the real SDK against Google's test endpoints? Or stub-only in debug?
- **Subscription proration + upgrades.** Season Pass is currently monthly-only; a future yearly tier would need proration handling. Out of scope for C.5 but note it here.
- **Anti-fraud hardening.** Play Billing provides `purchase.obfuscatedAccountId` / `purchase.obfuscatedProfileId` for fraud detection; do we use them, and if so, what player-identifier do we hash? (No Google account is required by the app; all state is device-local.)

All five will be decided in the PR description for C.5 PR 1 (which will upgrade this ADR from "Proposed (stub)" to "Accepted" with concrete answers).

## References

- `devdocs/evolution/implementation_roadmap.md` §C.5 — full 3-PR breakdown with files, success criteria, risk mitigations, verification, rollback.
- `devdocs/evolution/refactoring_opportunities.md` — no direct RO entry (billing is gap-fill, not refactor).
- `docs/plans/plan-26-monetization.md` — original stub architecture.
- `docs/plans/plan-31-play-console.md` — Play Console external-steps dependency chain.
- `docs/monetization.md` — product table + revenue-stream shape.
- `CONSTRAINTS.md` — no-server invariant.
- `app/src/main/java/.../domain/model/BillingProduct.kt` — SKU ID source of truth.
- `app/src/main/java/.../domain/repository/BillingManager.kt` — interface contract.
- `app/src/main/java/.../data/billing/StubBillingManager.kt` — current behaviour reference, to be deleted in C.5 PR 3.
- `app/src/test/java/.../fakes/FakeBillingManager.kt` — test-path coverage shape, stays through the swap.
