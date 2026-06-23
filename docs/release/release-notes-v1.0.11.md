# Release Notes — v1.0.11 (versionCode 27)

**Track:** Play Console **internal**
**Tag:** `v1.0.11` · **Supersedes:** `v1.0.10` (versionCode 26, 2026-06-19)
**Release lane:** automated — `v*` tag → CI `release.yml` → signed AAB → Play internal track. See `docs/plans/plan-32-ci.md` + ADR-0018.

---

## What's new — Play Console "What's new in this version" (≤500 chars)

This is the message published to the Play internal track via the annotated tag (`release.yml`
reads `git tag -l --format='%(contents)'` into `distribution/whatsnew/whatsnew-en-US`, capped at
Play's 500-char limit). Verbatim (454 chars):

```
A polish update — no new mechanics, just a smoother, clearer game:

• Battle now talks to TalkBack, and buttons meet contrast standards
• Cleaner on-screen text: correct singular/plural counts and proper names everywhere
• Smoother battles and menu music with fewer hitches when switching screens
• Steps you walk while the app is asleep are no longer lost
• Offline purchase problems now tell you what happened instead of failing silently

Keep walking!
```

---

## What shipped (developer detail)

Everything accumulated since v1.0.10 — a large polish/hardening body (48 commits; 26 CHANGELOG entries).
**No new features, no schema change** (the `app/schemas` tree is byte-identical to v1.0.10). Full
per-change detail is in `CHANGELOG.md` under `[1.0.11]` and the entries beneath it. **1110 → 1254 JVM
tests** across the accumulated waves.

### Player-facing

- **Accessibility (#213/#214).** Primary-button label contrast raised to WCAG AA (a new `OnGold` text
  token, ~5.99:1), and the canvas-only battle `SurfaceView` now announces wave / phase / 25%-health
  bracket / round-over / battle-error to TalkBack via a polite Compose live region.
- **Cleaner on-screen text (#225/#259/#260).** Correct singular/plural counts (a new `plurals.xml` with
  13 count-driven plurals), templated + localized wave-composition / boss-countdown / reward text, and
  no more raw `SCREAMING_CASE` enum names leaking into the UI (`HANGING GARDENS` → `Hanging Gardens`).
- **First-walk guidance (#224).** Right after onboarding, Home now shows an "Earn your first Steps"
  zero-state prompt instead of a screen of zeros (suppressed for a returning player with a stockpile).
- **Smoother battles + menu music (#242/#243).** Background music is no longer decoded on the main
  thread on every Battle↔menu navigation, and the projectile-trail effect is throttled so it can't
  exhaust the particle pool and starve death/UW effects. (ADR-0033.)
- **Steps walked while the app sleeps are no longer lost (#251).** Health-Connect-verified offline
  recovery gaps were being clamped to ~200 steps/min by the live-walking rate limiter and the remainder
  permanently lost; a trusted batch-credit path now credits them (keeping the 50k daily ceiling).
- **Offline purchase errors are surfaced (#249).** The Store's purchase functions were discarding the
  result, so a poor/absent network silently cleared the spinner; the offline error strings now reach the
  Store Snackbar.
- **Process-death state survival (#234).** Transient UI selections and the reveal-once card-pack payload
  now survive a process kill via `SavedStateHandle` / `rememberSaveable`.

### Not player-visible (hardening / architecture / infra)

- **Kotlin lint enforcement + repo-wide format (#312 + #311 + ktlint stages 1–6).** A baseline-gated
  detekt + ktlint CI gate (ADR-0037), the CLI-tooling guidance hook (#311), and a completed 6-stage
  layer-by-layer `ktlint -F` format (pure formatting — ktlint baseline 9256 → 157 cumulative).
- **Test integrity (#252/#253).** The project's first Compose UI tests on the Robolectric/JVM lane
  (critical screens + Home) and an `AtomicDaoConcurrencyTest` that reproduces concurrent-writer races on
  a file-backed Room DB (mutation-verified).
- **Supply-chain + CI hardening (#256/#257/#254/#212/#255).** Gradle dependency-verification (SHA-256
  over every CI artifact), a coroutines runtime pin, a strengthened schema-drift gate, Gradle-wrapper
  validation in both lanes, and Dependabot grouping.
- **Clock-tamper resistance (#211, ADR-0036).** Time-gated mechanics (daily-login streak, Labs research)
  no longer trust the unguarded device wall-clock — pure-domain `TimeIntegrity` over a reboot-durable
  floor + capped-accrual anchor closes a backward-rollback and an in-session forward-jump exploit.
- **Architecture (#230/#231/#220/#227/#228/#219/#229).** The 1233-line `GameEngine` god-class
  decomposed into focused collaborators (ADR-0012 Phase 4, strictly behavior-preserving), and the
  Clean-Architecture dependency rule restored + machine-enforced (`DomainPurityTest` /
  `PresentationPurityTest`; ADR-0034/0035).
- **Privacy / monetization (#240/#241/#239).** In-app Privacy Policy link in Settings; AdMob content
  rating capped at PG; hosted policy reconciled with the Data-Safety form. (ADR-0032; ADR-0006 Q5.)
- **Dependency hygiene (#199 / all-gradle / #287).** compileSdk 36 → 37 + dependency unblock (ADR-0031);
  a Dependabot all-gradle wave (11 of 12 bumps; Kotlin 2.4.0 held on two unreleased upstream blockers);
  a grouped GitHub Actions bump (#287).
- **Color-blind deferral (#226).** The GDD's color-blind palette promise was honestly deferred
  post-v1.0 (GDD reworded; survey confirmed no status is conveyed by color alone) — no code change.

> **1254 JVM tests** + 9 instrumented (up from 1110 JVM at v1.0.10). `@Composable` visual pieces beyond
> the new Robolectric coverage remain on-device-verified per the house norm.

---

## Provenance (review trail)

Each substantive wave went through the repo's review discipline (the Adversarial Review Gate for
specs/plans) before implementation, then TDD where there's a seam. Per the established norm, several
of these sessions ran the gate in its lighter single-agent form (flagged + chosen each time); the
GameEngine decomposition, process-death survival, and i18n waves ran the full multi-agent fan-out under
ultracode. The release collateral itself was grounded by a verification fan-out (CHANGELOG↔commit
reconcile, version-pointer sweep, "What's new" classification — each adversarially refuted), which
confirmed: 48 commits / 26 entries all backed, no schema change, the test-count delta chain lands
exactly at 1254, and the lint entry's PR number corrected (#311 → #312).

---

## Verification (release lane)

- CI `release.yml` builds the **committed** `versionCode` (no auto-bump — Play rejects reused codes).
  versionCode advanced 26 → 27; versionName 1.0.10 → 1.0.11 (the bump rides in with this release PR).
- `bundleRelease` runs R8 minify + lint-vital + signing; `jarsigner -verify` confirms the AAB is signed
  with the production upload keystore (Play App Signing enrolled).
- The release environment requires the `PLAY_LICENSE_KEY` secret — a release build with a blank key is
  hard-failed by the `app/build.gradle.kts` taskGraph guard so the billing fail-open can never ship.
- JVM unit suite green locally + in CI (1254 tests, 0 failures); CI PR gate + instrumented lane gate this
  release PR before merge. On-device feel/visual sign-off is the developer's call.

---

## Next

Promotion to the **closed** track remains judgment-gated on the Closed-Test Readiness Gate (see
`docs/plans/plan-FORWARD.md`). With this release, the 2026-06-18 audit's Med+ backlog player-facing
findings (accessibility, performance, i18n, privacy, correctness/UX, offline reliability) are shipped,
alongside the architecture/test-integrity/supply-chain hardening. The remaining promotion prerequisite
the repo cannot satisfy is the **manual Play Console Data-Safety action** (#192,
`docs/release/data-safety-form.md`). The med/low backlog (#262 tracker, #128 Lows, and the larger #233
clean Simulation-hoist / ADR-0012 follow-up) is post-this-release work.
</content>
</invoke>
