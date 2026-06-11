# ADR-0005: Real Google Play Billing Library integration

**Date:** 2026-05-08 (Proposed), 2026-05-11 (Accepted via C.5 PR 1)
**Status:** Accepted — concrete decisions landed in C.5 PR 1 (`BillingManagerImpl` + `billing_receipts` Room table + MIGRATION_8_9 + unit tests). Stub `@Binds` remains in place until C.5 PR 2 flips the flag-gated binding.

## Context

All In-App Purchase functionality is currently stubbed via `StubBillingManager` (`app/src/main/java/.../data/billing/`). The stub satisfies the `BillingManager` interface (`domain/repository/`), credits Gems / flags instantly after a 500 ms simulated delay, and always returns `PurchaseResult.Success`. It was landed in Plan 26 as a deliberate architectural seam: the whole app — `StoreViewModel`, `CurrencyDashboardViewModel`, `TrackDailyLogin` (Season Pass bonus), `LabsViewModel` (free Lab rush), `BattleViewModel` (ad-removal visibility), `CardsViewModel` (free pack ad), and the test fakes — talks only to `BillingManager`, so swapping to a real implementation is a single DI-binding flip away.

Shipping v1.0 to the Play Store per Plan 31 requires replacing the stub with a real integration:

- **5 products** defined in `domain/model/BillingProduct.kt`: 3 consumable Gem packs (50 / 300 / 700 Gems at $0.99 / $4.99 / $9.99), 1 one-time Ad Removal ($3.99), 1 monthly Season Pass subscription ($4.99/mo).
- **2 enum constants** carry the SKU IDs as source-of-truth in code.
- **`PurchaseResult` sealed class** has two variants (`Success`, `Error(message)`) — both must have realistic failure-path coverage before C.5 PR 1 lands, since A.4 already exercised them on the stub fakes.
- **`CONSTRAINTS.md` forbids a server backend** for v1.0, so receipt validation is client-side only. Server-side validation is a known post-v1.0 hardening step.

Risk shape: **high.** Real Play Billing failure paths (network loss mid-purchase, user cancels in the Google sheet, pending purchase state after app kill, subscription renewal edge cases, SKU mismatch between Play Console and code) have never run against production code. A broken billing path directly affects revenue and player trust.

## Decision

C.5 PR 1 locked in the following concrete commitments.

1. **Library: Google Play Billing Library `billing-ktx:8.3.0`.** Pinned exact version (never ranged) in `gradle/libs.versions.toml` under the `billingPlay` version alias. v7 sunsets 2026-08-31 per Google's two-year deprecation policy; v8 is the current stable line and what PR 1 integrates against. v9 upgrade will be evaluated when it becomes stable; the `BillingClientAdapter` seam contains the blast radius.
2. **Impl location:** `data/billing/BillingManagerImpl.kt` alongside `StubBillingManager`. Both coexist until C.5 PR 2 introduces the `BuildConfig.USE_REAL_BILLING` flag and flips the `@Binds` behind it. PR 1 leaves the binding pointed at the stub.
3. **SDK-neutral adapter seam.** An internal `BillingClientAdapter` interface (in `data/billing/internal/`) with plain-Kotlin result types (`SdkBillingResult`, `SdkPurchase`, `SdkProductDetails`, `QueryProductDetailsResult`, `StartPurchaseResult`, `QueryPurchasesResult`) is the only dependency `BillingManagerImpl` has on the SDK. `RealBillingClientAdapter` is the one place in the codebase that imports `com.android.billingclient.*`. This structure lets `BillingManagerImplTest` mock the adapter with mockito-kotlin (no `mockito-inline` needed for final Play Billing classes) and insulates the app from SDK version upgrades.
4. **Receipt-idempotency table in Room.** `billing_receipts` entity (C.5 PR 1) keyed by `purchaseToken` (the SDK's `orderId` is nullable on pending purchases, so is unsuitable as a primary key). `BillingReceiptDao.grantOnceAtomic(receipt, grantedAt, walletCredit)` is a `@Transaction` default method that flips `granted = true` and calls the wallet-credit lambda atomically in one SQLite transaction. Re-delivery of the same `purchaseToken` sees `granted = true` and short-circuits to `PurchaseResult.Success` without re-crediting. DB schema bump v8 → v9 with an explicit `MIGRATION_8_9` object per `CONSTRAINTS.md`; `billing_receipt` CREATE SQL byte-matches the Room-generated schema exported to `app/schemas/.../9.json` (verified via `RoomSchemaTest`).
5. **Pending-purchase reconciliation.** `BillingManager.reconcilePendingPurchases()` is a new interface method with a default no-op body (so `StubBillingManager` and `FakeBillingManager` inherit do-nothing behaviour automatically). The real impl calls `adapter.queryPurchases()` for both `INAPP` and `SUBS`, promotes `PENDING → PURCHASED` receipts through `grantOnceAtomic`, and retries consume/acknowledge RPCs on `granted = true, consumed/acknowledged = false` rows — without re-crediting the wallet. C.5 PR 2 will wire MainActivity `onResume` to call this.
6. **SKU drift mitigation.** `BillingProduct` enum stays the single source of truth. Enum `.name` (uppercase: `GEM_PACK_SMALL`, `AD_REMOVAL`, `SEASON_PASS`) is the Play Console product ID, byte-for-byte. An opt-in `companion object` on `BillingProduct` hosts the `fromSkuIdOrNull(skuId)` data-layer extension for reconciliation lookups (the enum itself stays in the pure-Kotlin domain layer). Startup sanity check via `queryProductDetailsAsync` is a follow-up — PR 1 surfaces missing products as a per-purchase "Product not available" error, not a startup warning. Play Console SKU IDs must match enum names byte-for-byte; this is documented in `docs/release/signing-guide.md` follow-up.
7. **3-PR rollout** (RO-07-style incremental swap):
   - **PR 1** ✅ landed: new `BillingManagerImpl` + `RealBillingClientAdapter` + `BillingClientAdapter` seam + `ActivityProvider` + receipt table + migration + unit tests. `@Binds` still points at `StubBillingManager`. Zero runtime behaviour change.
   - **PR 2**: flip `@Binds` to `BillingManagerImpl` behind `BuildConfig.USE_REAL_BILLING`. Flag defaults to false in debug, true in release. Wire `MainActivity.onResume/onPause` to `ActivityProvider.set/clear` and call `billingManager.reconcilePendingPurchases()` on Store entry. Verify on internal test track + closed track.
   - **PR 3**: delete `StubBillingManager` once internal + closed tracks confirm the real path for ~1 week. Rollback window closes.

## Rationale

- **v8, not v7.** The original ADR stub pinned v7. The revenuecat.com migration guide plus Google's release notes confirm v7 sunsets 2026-08-31 — only ~3 months after this work lands. Pinning to v7 would force an immediate v8 migration post-launch; going direct to v8 avoids that and puts us on the currently-supported line. v8 also ships `enableAutoServiceReconnection()` which removes the need for a custom reconnection policy (see Q1 answer below).
- **No server = idempotency lives in Room.** `CONSTRAINTS.md` forbids a backend for v1.0. The risk of grant duplication (user gets charged twice, or user gets credited twice for one charge) is the single highest-impact billing failure. Receipt-idempotency in Room sidesteps the whole category by making "this purchaseToken has been granted" an atomic, process-survivable predicate — and reuses the RO-02 `@Transaction` pattern proven across 5 sites in Phase B.2.
- **Adapter seam, not direct `BillingClient` usage.** `BillingClient` and its collaborators (`Purchase`, `ProductDetails`, `BillingResult`) are final classes that cannot be mocked with mockito's default subclass mock-maker. Without the adapter seam, `BillingManagerImplTest` would need `mockito-inline` (adds a byte-code-manipulation dependency to the test classpath) or Robolectric-driven integration tests against a fake Play Services. The thin adapter interface keeps unit coverage cheap and fast.
- **`BuildConfig` flag, not a runtime flag.** A runtime flag would need a remote-config dependency (Firebase / back-end) forbidden by `CONSTRAINTS.md`. A compile-time flag is good enough because the rollout strategy is internal-track → closed-track → release, each built separately.
- **Listener-to-suspend adaptation is well-trodden.** `suspendCancellableCoroutine` + `BillingClient` is a standard Android pattern; no custom abstraction needed. The fake `FakeBillingManager` already returns `suspend` on its `purchase` method, so the interface is pre-shaped for this.
- **3-PR rollout avoids a big-bang swap.** The stub stays deletable for ~1 week post-enablement so a regression found in early rollout can hotfix by flipping the flag, not by reverting a large PR chain.

## Consequences

- New `com.android.billingclient:billing-ktx:8.3.0` dependency in `gradle/libs.versions.toml` (pinned exact).
- New `app/proguard-rules.pro` keep rules: `-keep class com.android.billingclient.** { *; }`, `-keep interface com.android.billingclient.** { *; }`, `-dontwarn com.android.billingclient.**`. Over-broad on purpose — Play Billing uses reflection/AIDL and R8 gets aggressive without explicit keeps.
- `BuildConfig.USE_REAL_BILLING` (boolean, buildType-specific) will be added in PR 2 via `app/build.gradle.kts`. Debug default: false. Release default: true. Override for internal-track testing via `keystore.properties` entry.
- Room DB v8 → v9 with `billing_receipts` entity + explicit `MIGRATION_8_9`. `AppDatabase` entity list grew from 12 to 13.
- `StubBillingManager` will eventually be deleted in PR 3 — one call site (`di/BillingModule.kt` `@Binds`) and the `FakeBillingManager` test fake stays on the test classpath (inherits the no-op `reconcilePendingPurchases` default).
- `StoreViewModel` contract **unchanged** — the swap is pure impl-side. Existing failure-path coverage from A.4 (`FakeBillingManager.resultQueue`) continues to be the unit-test safety net.
- `BillingManager` interface extended with one new `suspend fun reconcilePendingPurchases()` with a default no-op — existing callers and fakes need zero changes.
- `BillingProduct` domain enum gained an empty `companion object` so data-layer extensions can attach reverse-lookup helpers (`fromSkuIdOrNull`). No Android import introduced — domain layer stays pure.
- `TrackDailyLogin` Season Pass bonus path unchanged — still reads `profile.seasonPassActive` + `profile.seasonPassExpiry`. The real `BillingManagerImpl` writes those fields from the Billing library's subscription state instead of from the stub's 30-day hardcode.
- Play Console setup (product IDs, subscription base plan, test accounts) is external and lives in `docs/release/signing-guide.md` follow-up.

## Non-goals / future work

- **No server-side receipt verification.** Forbidden by `CONSTRAINTS.md` for v1.0. Client-side receipt validation (`BillingClient.acknowledgePurchaseAsync` for non-consumables, `consumeAsync` for consumables, subscription state from `queryPurchasesAsync`) is the ceiling. Server-side validation is a known post-v1.0 hardening step tracked separately. **(Amended by #124 — see Amendment below: client-side *RSA signature* verification is now ALSO done, and is distinct from the forbidden server-side verification.)**
- **No custom subscription-renewal UI.** The Season Pass renewal flows through Google's standard subscription management; the app only reads current state.
- **No Real-Time Developer Notifications (RTDN).** Would require a backend. Post-v1.0.
- **No price-tier experiments.** Prices are fixed in `BillingProduct` enum. A/B pricing is a post-v1.0 growth concern.
- **No grace-period billing.** Subscription grace period is Google's default (3 days typically); we don't maintain our own grace-period clock.
- **No promotional codes, intro pricing, or region-specific pricing in v1.0.** Play Console supports these but they compound the testing matrix; post-v1.0.
- **No caching of `ProductDetails` in `BillingManagerImpl`.** Every `purchase()` re-queries (~100 ms on normal networks). Sidesteps cache-invalidation-on-price-change bugs. Revisit post-v1.0 if latency becomes user-visible.
- **Do not refactor `BillingManager` interface.** Adding new methods (like `reconcilePendingPurchases` with a default body) is fine. Breaking existing signatures is not.

## Amendment — #124 client-side purchase signature verification (2026-06-11)

**Context.** The original ADR listed "no client-side signature verification" implicitly under the no-server ceiling, and the shipped C.5 pipeline trusted any `SdkPurchase` with `purchaseState == PURCHASED`. The audit (`docs/external-reviews/2026-06-10-multi-agent-code-audit.md`, #124) showed that *client-side* RSA signature verification needs **no backend** and is therefore NOT covered by the `CONSTRAINTS.md` no-server-side-verification rule. The developer chose to fix it as Closed-Test Readiness-Gate (Gate D) hardening.

**Decision.**

1. **New `PurchaseVerifier` seam** (`data/billing/internal/`), mirroring the `BillingClientAdapter` seam: an interface + pure-JVM `RealPurchaseVerifier` doing standard Play `SHA1withRSA` verification (a local port of the Play Billing sample's `Security.verifyPurchase`) against the Base64 Play "Licensing" public key. JVM-unit-tested against a real RSA keypair.
2. **`SdkPurchase` carries `originalJson` + `signature`** (populated by `RealBillingClientAdapter.toSdk()` from Play's `getOriginalJson()`/`getSignature()`; previously dropped).
3. **Both grant paths gated** — `handleCompletedPurchase` (live) and `reconcileType` (reconcile) call `verifier.isValidPurchase(...)` before `grantOnceAtomic`. A failed check rejects with no receipt, no wallet credit, no consume/acknowledge.
4. **Signature bound to the grant.** The verifier also requires the *signed* `productId` + `purchaseToken` to equal the product/token being granted, so a genuinely-signed cheap receipt can't be replayed for an expensive product (audit finding 2 from the adversarial review of this fix).
5. **Key wiring + fail policy.** `BuildConfig.PLAY_LICENSE_KEY` sourced from gitignored `local.properties` (`play.licenseKey`), mirroring the AdMob prod-ID pattern. **Blank key → fail-open** (debug/CI, which use Play Console license-test accounts whose signatures can't be verified offline — preserves pre-#124 behaviour). **Configured-but-unparseable key → fail-closed.** A **release** build (`bundleRelease`/`assembleRelease`) with a blank key is **hard-failed by a Gradle `taskGraph.whenReady` guard** so fail-open can never ship; the release CI lane injects the key from a required `PLAY_LICENSE_KEY` secret (`.github/workflows/release.yml`, and the secrets table in `plan-32-ci.md`).

**Residual risk (unchanged from the audit).** Client-side verification is defence-in-depth — bypassable on a fully-repackaged APK (patch the embedded key). All grants are local-only (no server economy), so this raises the bar against casual local IAP fraud but is not a complete fix for a determined local attacker. Server-side verification remains the post-v1.0 ceiling, still gated by the no-backend constraint.

## Resolved open questions

C.5 PR 1 locked in concrete answers for all 5 questions flagged when the ADR was proposed.

### Q1. Reconnection policy

**Decision: delegate to Play Billing v8's `enableAutoServiceReconnection()`.** Introduced in v8.0.0, the SDK handles Play Services disconnection + reconnection transparently. `BillingManagerImpl.ensureConnected()` calls `adapter.connect()` once per session and caches the `connected` flag; a subsequent `ServiceDisconnected` result surfaces to the user via `PurchaseResult.Error("Billing service unavailable. Try again shortly.")`. No custom backoff, no retry-loop UI, no state machine. This was the simplification that made the custom reconnection policy from the proposed ADR unnecessary.

### Q2. Acknowledgment timing

**Decision: consume/acknowledge AFTER the grant transaction commits.** Specifically:

1. `adapter.launchPurchase()` returns with a `SdkPurchase` in `PURCHASED` state.
2. `BillingReceiptDao.grantOnceAtomic(receipt, grantedAt, walletCredit = { creditWallet(product, purchase) })` runs — receipt row + wallet credit commit atomically.
3. After the transaction commits, `finalizePurchase(product, purchase)` calls `adapter.consume()` (consumables) or `adapter.acknowledge()` (non-consumables + subscriptions) and flips `consumed = true` / `acknowledged = true` on the receipt row.

If the consume/acknowledge RPC fails, the receipt row has `granted = true, consumed/acknowledged = false`. `retryUnresolvedConsumeOrAck()` (called from `reconcilePendingPurchases()`) retries only the failed side — the wallet-credit path is NOT re-run because the `granted = true` guard in `grantOnceAtomic` short-circuits. Google auto-refunds purchases not acknowledged within 3 days, which is why logging-only failure on ack is correct (the retry path is the recovery) rather than catastrophic (we'd just lose the sale).

Critically: consume/acknowledge runs OUTSIDE the Room transaction. Holding a SQLite write lock across a Google Play Services RPC (seconds on poor networks) would serialise the entire database.

### Q3. Test-SKU strategy for debug builds

**Decision: real Play Console SKU IDs always; debug builds rely on Play Console's internal test tracks + license test accounts.** Google's reserved test SKUs (`android.test.purchased` etc.) are NOT used. Rationale:

- Test SKUs exist for in-app items only — they cannot simulate subscriptions or non-consumables. Season Pass and Ad Removal would fall back to real SKUs anyway, producing an asymmetric debug environment.
- Real SKUs on Google Play's internal test track give us a fully-integrated debug experience at zero dev cost (test purchases are free for license test accounts, configured per Google Play Console → Setup → License Testing).
- Adding a `BuildConfig.BILLING_USES_STATIC_TEST_SKUS` flag would introduce a debug-only code path that is not what release runs.

C.5 PR 2 documentation in `docs/release/signing-guide.md` will enumerate the Play Console test-account setup steps.

### Q4. Subscription proration + upgrades

**Decision: explicitly out of scope for v1.0.** Season Pass is monthly-only. If v1.x introduces a yearly tier, the proration path will be evaluated at that point — adding `BillingFlowParams.SubscriptionUpdateParams.setSubscriptionReplacementMode()` is the Play Billing hook. No scaffolding added in C.5 PR 1 to anticipate this; YAGNI.

### Q5. Anti-fraud hardening

**Decision: use `purchase.obfuscatedAccountId` derived from a device-local UUID.** Specifically:

- At first `purchase()` call, `BillingManagerImpl.obfuscatedAccountIdHex()` reads key `obfuscated_account_uuid` from `SharedPreferences("billing_anti_fraud")`.
- If absent, generates a random `UUID.randomUUID()`, persists it, and continues.
- Computes `SHA-256(uuid.toString().utf8)` and returns the hex string.
- Passed to `adapter.launchPurchase(..., obfuscatedAccountId = ...)` which routes it to `BillingFlowParams.Builder.setObfuscatedAccountId()`.

No PII — the UUID is random, device-local, and the hash is one-way. If the user clears app data, a new UUID regenerates; this is acceptable because the signal is probabilistic (Play fraud detection degrades gracefully when the id shifts). `obfuscatedProfileId` is unused (no in-app profile concept).

## References

- `devdocs/evolution/implementation_roadmap.md` §C.5 — 3-PR breakdown with files, success criteria, risk mitigations, verification, rollback.
- `devdocs/evolution/refactoring_opportunities.md` — no direct RO entry (billing is gap-fill, not refactor).
- `docs/plans/plan-26-monetization.md` — original stub architecture.
- `docs/plans/plan-31-play-console.md` — Play Console external-steps dependency chain.
- `docs/monetization.md` — product table + revenue-stream shape.
- `CONSTRAINTS.md` — no-server invariant.
- `docs/agent/DECISIONS/ADR-0006-ad-sdk.md` — sibling ADR for AdMob (C.6).
- `app/src/main/java/.../domain/model/BillingProduct.kt` — SKU ID source of truth.
- `app/src/main/java/.../domain/repository/BillingManager.kt` — interface contract.
- `app/src/main/java/.../data/billing/BillingManagerImpl.kt` — real impl landed in C.5 PR 1.
- `app/src/main/java/.../data/billing/internal/BillingClientAdapter.kt` — SDK-neutral seam.
- `app/src/main/java/.../data/billing/internal/RealBillingClientAdapter.kt` — concrete SDK glue.
- `app/src/main/java/.../data/local/BillingReceiptEntity.kt` — idempotency table.
- `app/src/main/java/.../data/local/BillingReceiptDao.kt` — `grantOnceAtomic` @Transaction.
- `app/src/main/java/.../data/local/Migrations.kt` — `MIGRATION_8_9` adds `billing_receipt`.
- `app/schemas/com.whitefang.stepsofbabylon.data.local.AppDatabase/9.json` — generated schema for v9.
- `app/src/test/java/.../data/billing/BillingManagerImplTest.kt` — 14 tests against mocked adapter.
- `app/src/test/java/.../data/local/BillingReceiptDaoTest.kt` — 7 tests for the atomic grant + finalize lifecycle.
- `app/src/main/java/.../data/billing/StubBillingManager.kt` — current behaviour reference, to be deleted in C.5 PR 3.
- `app/src/test/java/.../fakes/FakeBillingManager.kt` — test-path coverage shape, stays through the swap.
