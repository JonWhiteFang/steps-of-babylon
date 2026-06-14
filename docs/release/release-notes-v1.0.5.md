# Release Notes — v1.0.5 (versionCode 21)

**Track:** Play Console **internal**
**Tag:** `v1.0.5` · **Supersedes:** `v1.0.4` (versionCode 20, 2026-06-14)
**Release lane:** automated — `v*` tag → CI `release.yml` → signed AAB → Play internal track. See `docs/plans/plan-32-ci.md` + ADR-0018.

---

## What's new — Play Console "What's new in this version" (≤500 chars)

This is the message published to the Play internal track via the annotated tag (`release.yml`
reads `git tag -l --format='%(contents)'` into `distribution/whatsnew/whatsnew-en-US`, capped at
Play's 500-char limit). Verbatim:

```
Polish update:

• Tidied up the battle screen — the wave and cash info now sits neatly at the top instead of floating below the tower's health bar

Thanks for playing — keep walking!
```

---

## What shipped (developer detail)

Two post-v1.0.4 follow-ups surfaced by the v1.0.4 release audit, fixed and verified on 2026-06-14
(PR #169, squash `85ce889`). **Presentation + CI config only** — no gameplay/economy/concurrency/
persistence/engine code touched. Full detail is in `CHANGELOG.md` under `[1.0.5]`.

### Battle HUD vertical offset (player-visible)

- The in-round HUD `Column` (`BattleScreen.kt`) carried a hardcoded `top = 80.dp` (quit button
  `top = 72.dp`) calibrated for a **status-bar (~24dp) + platform-ActionBar (~56dp)** offset that no
  longer applies: `MainActivity` is edge-to-edge and its `Scaffold` already supplies the status-bar
  inset via `innerPadding`, and the app-wide ActionBar was removed in #159 (shipped in v1.0.4). The
  stale offset double-counted removed chrome, leaving the wave/cash text floating ~53dp below the
  engine-rendered ziggurat health bar.
- **Fix:** `80.dp → 40.dp` (clears the engine health bar — `HealthBarRenderer` draws it at
  40px..72px ≈ 36dp@2x — with a small margin) + quit button `72.dp → 32.dp` (preserves the 8dp
  differential so the back arrow stays aligned with the wave header). **Reproduced and re-verified
  on the emulator** (1080×2400 @ 420dpi). The v1.0.4 audit's suggested "−56dp → 24.dp" was discarded
  — 24dp would have collided with the ~27dp health-bar bottom.

### `release.yml` `track` → `tracks` (developer infra; no player impact)

- The `r0adkll/upload-google-play` step used the deprecated `track:` input. Renamed
  `track: internal` → `tracks: internal`. Verified against the action's `action.yml` **at the pinned
  SHA** (`v1.1.5` / `e738b9dd`) that both inputs exist and `tracks` is the documented successor — a
  non-breaking rename. **This release is the first to exercise the `tracks:` input.**

> **981 JVM tests** (unchanged from v1.0.4 — the HUD change is a Compose-UI-surface layout tweak with
> no JVM seam, per the file's established "verify on-device" convention) + 9 instrumented.

---

## Verification (release lane)

- CI `release.yml` builds the **committed** `versionCode` (no auto-bump — Play rejects reused codes).
  versionCode advanced 20 → 21; versionName 1.0.4 → 1.0.5.
- `bundleRelease` runs R8 minify + lint-vital + signing; `jarsigner -verify` confirms the AAB is signed
  with the production upload keystore (Play App Signing enrolled).
- The release environment requires the `PLAY_LICENSE_KEY` secret — a release build with a blank key is
  hard-failed by the `app/build.gradle.kts` taskGraph guard so the billing fail-open can never ship.
- JVM unit suite green locally (981 tests, 0 failures) + CI PR gate + instrumented lane before tagging.

---

## Next

Promotion to the **closed** track remains judgment-gated on the Closed-Test Readiness Gate (see
`docs/plans/plan-FORWARD.md`). The remaining code-actionable gate items are **#29** (upgrade
decision-support, Gate F) and **#26** (performance/battery, Gate G); the manual play-feel gates
(A audio, E balance) are the developer's call. The look-&-feel bundles **#162/#163/#164** are the
next feature work. The ≥14-day tester soak and production access are Phase 2 — they begin *after*
the developer decides to promote.
