# Release Notes — v1.0.3 (versionCode 19)

**Track:** Play Console **internal**
**Tag:** `v1.0.3` · **Supersedes:** `v1.0.2` (versionCode 18, 2026-06-11)
**Release lane:** automated — `v*` tag → CI `release.yml` → signed AAB → Play internal track. See `docs/plans/plan-32-ci.md` + ADR-0018.

---

## What's new — Play Console "What's new in this version" (≤500 chars)

This is the message published to the Play internal track via the annotated tag (`release.yml`
reads `git tag -l --format='%(contents)'` into `distribution/whatsnew/whatsnew-en-US`, capped at
Play's 500-char limit). Verbatim:

```
New: a quick first-time walkthrough!

• A short tutorial now shows new players how walking earns Steps, how to spend them in the Workshop, and how battles work
• Clearer step-counting permission request, with an easy way to enable it from Settings if you skipped it
• Replay the walkthrough any time from Settings

Thanks for playing — keep walking!
```

---

## What shipped (developer detail)

### v1.0.3 (versionCode 19) — #24 first-launch onboarding (Closed-Test Readiness Gate C)

- **#24 — first-launch onboarding (Gate C slice of V1X-22).** A brand-new player now sees a one-time
  4-slide tutorial carousel teaching the **walk → spend → battle** loop, then a **contextual permission
  primer** for activity recognition (replacing the prior cold system dialog). A **"Replay tutorial"**
  entry was added to Settings.
  - **Explain-only (ADR-0021):** onboarding grants **no** currency — the original V1X-22 "100 free
    Steps welcome bonus" was rejected because it violates the hard *Steps are never generated in-game*
    invariant. The first-session call-to-action is simply "Go for a walk to earn your first Steps."
  - **Permission recovery:** denial leaves an in-carousel "Open Settings" path; a permanently-denied
    post-onboarding re-prompt surfaces a Snackbar → app-settings deep-link instead of the prior silent
    no-op.
  - **No Room schema change** — the completion flag is a device-local `SharedPreferences` value
    (`OnboardingPreferences`), deliberately not game state, so it won't sync under a future cloud-save
    and a reinstall correctly re-shows the tutorial.
  - **Gating:** `MainActivity` chooses the start destination from a synchronous flag read via the pure
    `Screen.startDestination(Boolean)` helper; only the cold-permission request branch is gated behind
    onboarding-completion (foreground-service start / Health-Connect chaining unchanged); the deep-link
    collector is gated on live nav state. `Screen.Onboarding` is kept out of the public deep-link
    allowlist.
  - Closes Readiness **Gate C**. #24 stays **open** for the deferred retention scope (D2/D7 push,
    wave-5 celebration, projected-reward estimates — these pair with telemetry #23). Shipped via PR #157.
  - **960 → 973 JVM tests** (+13). Built spec-first with adversarial spec + plan reviews and
    subagent-driven, per-task-reviewed execution; see `docs/superpowers/specs/` + `docs/superpowers/plans/`.

> Per-PR detail and the full entry list live in `CHANGELOG.md`. This doc is the release-collateral
> summary; the CHANGELOG is the authoritative change record.

---

## Verification (release lane)

- CI `release.yml` builds the **committed** `versionCode` (no auto-bump — Play rejects reused codes).
  versionCode advanced 18 → 19; versionName 1.0.2 → 1.0.3.
- `bundleRelease` runs R8 minify + lint-vital + signing; `jarsigner -verify` confirms the AAB is signed
  with the production upload keystore (Play App Signing enrolled).
- The release environment requires the `PLAY_LICENSE_KEY` secret — a release build with a blank key is
  hard-failed by the `app/build.gradle.kts` taskGraph guard so the billing fail-open can never ship.
- The onboarding carousel render + live system permission dialog are device-verified-only (no JVM
  seam); worth a manual on-device pass on the internal build.

---

## Next

Promotion to the **closed** track is judgment-gated on the Closed-Test Readiness Gate (see
`docs/plans/plan-FORWARD.md`). With Gate C now shipped, the remaining code-actionable gate items are
**#29** (upgrade decision-support, Gate F) and **#26** (performance/battery, Gate G); the manual
play-feel gates (A audio, E balance) are the developer's call. The ≥14-day tester soak and production
access are Phase 2 — they begin *after* the developer decides to promote.
