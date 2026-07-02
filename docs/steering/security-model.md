# Security Model

A single consolidated view of how Steps of Babylon protects player data, game-state integrity, and
the in-app economy. This is a **reference map**, not the source of truth — each section links to the
authoritative doc/code/ADR. When they disagree, the linked source wins.

> Scope: v1.0 is offline-first with **no server backend**. There is no account system, no remote data
> store, and no server-side verification. All protections are on-device. (`docs/agent/CONSTRAINTS.md`)

## 1. Data-at-rest encryption

| Layer | Measure | Source |
|---|---|---|
| Database | SQLCipher (AES-256) full-DB encryption at rest via `net.zetetic:sqlcipher-android` | `docs/architecture.md` § Security |
| Key management | DB passphrase generated randomly on first run, encrypted with an AES-256-GCM Android Keystore key, stored as a blob in SharedPreferences | `data/local/DatabaseKeyManager.kt` |
| Key recovery | On a passphrase-decrypt failure the response is **scoped to the cause (#238)**: the DB is wiped + a fresh passphrase regenerated **only** when the Keystore alias is provably absent (true device-restore — on-disk DB unrecoverable); a decrypt failure with the alias still present is treated as a *transient* Keystore fault and **rethrown** (open retries next launch) so non-regenerable Steps progress is preserved | `data/local/DatabaseKeyManager.kt` |
| Backup | `android:allowBackup="false"` — local-only game; prevents restore-related crashes and cross-device key mismatch | `app/src/main/AndroidManifest.xml` |

Room is the single source of truth for all game state; schema exports are committed to `app/schemas/`.
See `docs/database-schema.md` for the encrypted schema (current version in that doc + `AppDatabase.kt`).

## 2. Network & build hardening

| Layer | Measure | Source |
|---|---|---|
| Network | Cleartext traffic blocked via `res/xml/network_security_config.xml` (`networkSecurityConfig` in the manifest) | `AndroidManifest.xml` |
| Release build | R8 code shrinking + obfuscation + resource shrinking; hardened rules in `app/proguard-rules.pro` | `docs/architecture.md` § Security |
| No location | The app requests **no** location permissions — step data comes only from `TYPE_STEP_COUNTER` + Health Connect (activity-permission-gated, not location). Reintroducing location has a documented checklist. | `docs/architecture.md` § Privacy & Permissions; ADR-0016 |

## 3. Step-integrity / anti-cheat

Steps are the permanent currency and are earned from real-world walking — never generated *passively*
in-game, never purchasable with money. The **one** sanctioned in-game source is the bounded battle-step
reward (see the Battle Steps cap row below), which is active play, not passive generation. The anti-cheat
stack guards that invariant. Full thresholds and the data-flow diagram live in `docs/step-tracking.md`;
the invariant list is in `docs/agent/CONSTRAINTS.md`.

- **Rate limit** — 200 steps/min (250 burst for running); excess silently discarded.
- **Step velocity analysis** — detects shakers (constant rate, CV<5%) and spoofers (instant 0→150/min jump); penalty multiplier 0.5×/0.0×.
- **Daily ceiling** — 50,000 steps/day hard cap.
- **Health Connect cross-validation** — graduated response over 4 offense levels (escrow → faster discard → cap at HC value → cap at HC −10%).
- **Activity-minute validation** — rejects micro-sessions (<2min), truncates >4hr to 240min, caps at 5 activity types/day.
- **Per-minute overlap deduction** — an activity minute is credited only when sensor steps are **<50/min** that minute (≥50/min means the sensor already captured the motion). `ActivityMinuteConverter`, `MAX_SENSOR_STEPS_PER_MIN = 50`.
- **Battle Steps cap** — 2,000/day (`AwardBattleSteps.DAILY_BATTLE_STEP_CAP`), separate from and never additive to the 50k ceiling; flat per-enemy-type, never multiplied by any in-round source. ADR-0003.
- **`DailyStepManager` mutex (#120)** — credit read-check-write runs under a non-reentrant `Mutex`; don't add an un-locked counter mutation.

## 3a. Time axis (#211)

Time-gated mechanics (daily-login streak, Labs research auto-completion) no longer trust the unguarded
device wall-clock. Authoritative record: **ADR-0036**; pure decision core: `domain/time/TimeIntegrity.kt`.
Adversary in scope: a player using **OS Settings → Date & time** (the cheap, ubiquitous exploit).

- **Pure decision core** — `TimeIntegrity.evaluate(baseline, reading)` reasons over a persisted 4-slot
  `TimeBaseline` + a fresh `TimeReading` and returns `Trusted | Rollback` carrying the advanced baseline.
  No Android imports (`DomainPurityTest`); the Android clock reads live in `AntiCheatPreferences`.
- **Backward-rollback guard (reboot-durable)** — `maxWallClockSeen` is the highest wall-clock ever
  observed; `reading.wallClock < maxWallClockSeen` ⇒ `Rollback`. Persisted, so it survives reboot.
- **Forward-jump guard (in-session)** — the `trustedWallClock` capped-accrual anchor only advances by
  `min(wallDelta, elapsedDelta)`, so an in-session forward jump's excess is never folded in (it's an
  order-independent persisted anchor, not a re-derivation).
- **What's closed** — (a) backward rollback: `TrackDailyLogin.checkAndAward(isRollback = true)` refuses
  ALL credit for the tampered date and writes nothing (no streak/gem/season-bonus, no `DailyLogin` row,
  no `gemsClaimed` latch); (b) in-session forward jump on research: `CheckResearchCompletion` gates on
  the capped trusted-now.
- **Single owner = `DailyStepManager`** — evaluates + persists the advanced baseline under its #120
  mutex (`writeTimeBaseline` is `apply()`, async/non-throwing). `HomeViewModel` / `LabsViewModel` are
  **read-only consumers** (derive trusted-now/verdict, never persist). Baseline lives in the existing
  `anti_cheat_prefs` SharedPreferences (no Room schema/migration); wiped by `DataDeletionManager`.
- **Explicitly accepted (out of scope this wave, per ADR-0036)** — rooted/file-editing adversary (can
  edit the plaintext baseline directly — same trust tier as the whole plaintext anti-cheat stack);
  reboot-spanning forward jump on research (`elapsedRealtime` resets, so the capped delta falls back to
  the full wall delta — the `Rollback` floor guards only backward); `RushResearch` gem rush-cost (reads
  raw `now`, floored at 50 gems); `BillingManagerImpl` season-pass-expiry authority (unchanged).

## 4. Economy integrity (atomic spend/claim)

The in-app economy uses an **atomic guarded-deduct pattern** so currency can't be double-spent or a
grant issued off a stale snapshot. Authoritative record: **ADR-0020**; invariant summary in
`docs/agent/STATE.md` (fragile zones) and `CLAUDE.md` § Conventions.

- Guarded DAO `UPDATE … WHERE balance >= cost` returns rows-affected; the grant is gated on rows>0, inside a `@Transaction`. Template: `WorkshopDao.purchaseUpgradeAtomic`, `MilestoneDao.claimMilestoneAtomic`.
- `spendGems` / `spendPowerStones` / `spendStepsIfSufficient` return `Boolean` — gate the grant on the result, never on a stale wallet snapshot (#122).
- One-shot claims (supply drops, daily missions) use a guarded `UPDATE … WHERE id AND claimed = 0` returning rows-affected, and **mark-first** (credit only when it returns 1). Validated against real SQL by `GuardedClaimDaoTest`.
- Per-key generators (e.g. daily missions) need a **DB-level unique index**, not a read-then-insert check — the read-check is racy on a WAL pool (#127, `(date, missionType)` unique index).

## 5. Purchase verification (billing)

Client-side Play purchase signature verification (#124, **ADR-0005 amendment**). Detail in
`docs/monetization.md` § What's Implemented.

- Every wallet grant first calls `PurchaseVerifier.isValidPurchase(originalJson, signature, expectedProductId, expectedPurchaseToken)` (standard Play `SHA1withRSA` against the Base64 Play licensing key) **before** `grantOnceAtomic`, on both the purchase and reconciliation paths.
- The **product + token binding** is load-bearing: it blocks replaying a signed cheap receipt for an expensive product.
- A blank `PLAY_LICENSE_KEY` fail-opens in **debug/CI only**. A **release** build with a blank key is hard-failed by the `app/build.gradle.kts` taskGraph guard + the `release.yml` `PLAY_LICENSE_KEY` secret step — so the fail-open can never ship. Don't weaken either.
- Idempotency: `BillingReceiptDao` is keyed by `purchaseToken`; a `granted = true` row guarantees at-most-once crediting across crashes / `PENDING→PURCHASED` / repeat reconciliation.
- **Out of scope for v1:** server-side receipt verification (no backend, per `CONSTRAINTS.md`). Client-side verification above is the v1 protection.

## 6. Release-signing integrity

- Release AABs are signed with the production upload keystore (`release/upload-keystore.jks`, gitignored, Play App-Signing-enrolled). The CI release lane runs `jarsigner -verify` on the built AAB. See `docs/plans/plan-32-ci.md` + ADR-0018.

## 7. Secret scanning (#376)

Committed-secret defense is layered:

- **GitHub-native secret scanning + push protection** — enabled at the repo level (public repo).
  Push protection blocks a push that introduces a recognized secret. Alerts:
  `gh api repos/JonWhiteFang/steps-of-babylon/secret-scanning/alerts`. **Non-provider patterns are
  NOT yet enabled** — the REST API PATCH is a silent no-op for this toggle on a personal-account
  repo; flip it manually in **Settings → Code security → Secret scanning → Non-provider patterns**.
  (The gitleaks gate below already covers the custom keystore/password patterns, so this is
  incremental, not a coverage gap.)
- **gitleaks CI gate** (`.github/workflows/gitleaks.yml` + `.gitleaks.toml`) — repo-committed,
  runs on every PR and push to `main` over full history. Extends the default ruleset with rules
  the built-ins miss: binary `*.jks`/`*.keystore` files and `storePassword=`/`keyPassword=`/
  `play.licenseKey=` `.properties` lines. The CI non-publishing placeholder is allowlisted.
- **`.gitignore`** — the first line of defense (keystore.properties/local.properties/keystores),
  now backstopped by the two scanners above rather than being the only guard.

---

**Related ADRs:** ADR-0003 (battle-step rewards), ADR-0005 (billing SDK + #124 verification amendment),
ADR-0016 (no GPS/location), ADR-0018 (CI/CD + signing), ADR-0020 (economy atomicity),
ADR-0036 (time-axis anti-cheat / clock-tamper resistance).
