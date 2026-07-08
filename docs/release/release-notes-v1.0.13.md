# Release Notes — v1.0.13 (versionCode 29)

**Track:** Play Console **internal**
**Tag:** `v1.0.13` · **Supersedes:** `v1.0.12` (versionCode 28, 2026-06-23)
**Release lane:** automated — `v*` tag → CI `release.yml` → signed AAB → Play internal track. See `docs/plans/plan-32-ci.md` + ADR-0018.

---

## What's new — Play Console "What's new in this version" (≤500 chars)

This is the message published to the Play internal track via the annotated tag (`release.yml`
reads `git tag -l --format='%(contents)'` into `distribution/whatsnew/whatsnew-en-US`, capped at
Play's 500-char limit). Developer-approved. Verbatim (~250 chars):

```
¡Ahora en español! Steps of Babylon now speaks Spanish — the whole game auto-translates on Spanish-language devices. Our first step toward more languages.

Also in this update:
• New project email + info site
• Behind-the-scenes cleanup and stability work

Keep walking!
```

---

## What shipped (developer detail)

Everything accumulated since v1.0.12. **No new player mechanics, no schema change** (the `app/schemas`
tree is byte-identical to v1.0.12). Full per-change detail is in `CHANGELOG.md` under `[1.0.13]` and the
entries beneath it. **1277 → 1332 JVM tests** across the body.

### Player-facing

- **First non-English locale — Spanish (`es`) (#34).** Complete `values-es/` translation (566 strings +
  16 plurals) mirroring the English default. **Device-language only** — Android auto-selects `values-es`
  on Spanish-set devices; no in-app picker, every other locale byte-for-byte unaffected. `app_name` +
  four documented residuals (supply-drop message, USD price fallback, seed-cosmetic fallback, the
  Apache-2.0 `oss_notices` body) stay English by design. A native copy-quality review (#410) is a
  before-promotion follow-up, not a code blocker. Guarded by the new pure-JVM
  `architecture/LocaleCompletenessTest` (key / format-arg / plurals parity).
- **New project contact + information site.** In-app support contact email →
  `steps-of-babylon@jonwhitefang.uk` (Health Connect privacy rationale + the crash-report email path);
  new information site `https://jonwhitefang.uk/projects/steps-of-babylon`. The hosted **privacy policy**
  (`jonwhitefang.github.io/steps-of-babylon`) is unchanged — no Data Safety impact.

### Not player-visible (cleanup / hardening / infra)

- **Ziggurat damage resolution hoisted to pure domain (#306, ADR-0012 Phase 5 Slice 1).** First slice of
  the tracked effect-resolution hoist: the pure defense/death-defy/second-wind/HP-floor/shake-threshold
  arithmetic + HP mutation moved from presentation `CombatResolver.applyDamageToZiggurat` into a new
  pure-domain `ZigguratDamageResolver` over a new `Damageable` port. `CombatResolver` is now a thin
  adapter. **Behaviour-preserving** (pre-hoist characterization tests as the baseline oracle + the new
  resolver unit tests). Thread-safety unchanged (resolver holds no monitor; runs inside the engine's held
  `entitiesLock`). #306 stays open for the remaining slices (enemy HP + UW effect bodies).
- **i18n locale-readiness plumbing (phases 2–3, #34, ADR-0014).** The Compose-screen string extraction +
  the `UiMessage` / `EnumLabels` / `domain/Strings` / `formatted="false"` seams that made the app 100%
  locale-ready — the substrate the Spanish locale ships on. No behaviour change.
- **Tooling / CI / observability (Phases 1–4).** Crash-report exit path + Vitals runbook (#374/#380);
  release-variant build + SHA-pinned gitleaks secret-scanning in the PR gate (#370/#376); AI-safety
  invariant tripwires — `StepCreditAllowlistTest` + the fallback lock-scan + the concurrency-reviewer
  advisory (#371/#372); JDK-17 toolchain pin (#378, ADR-0039); versionCode-collision fail-fast guard in
  the release lane (#379); DEBUG-only frame-stats overlay (#384); OSS-attribution notice in Help (#377);
  the Kover coverage ratchet + migration-chain + hardcoded-string guards (#373/#375/#381/#382); plus
  Claude Code automations + developer-experience docs. All internal — no shipped-app behaviour change.
- **Service/boot-receiver glue test coverage (#217).** Instrumented coverage of the Android glue; no app
  change.

### Not shipped in the app (docs/tooling-only, rode along on `main`)

- Docs-only entries in the `[1.0.13]` CHANGELOG block (tooling-gap audit, UX/UI-skills assessment,
  sequential-merge rule, "Steps never generated" invariant accuracy) change no app/test/schema surface.

## Provenance (review trail)

- **#34 Spanish locale** — spec/plan `docs/superpowers/{specs,plans}/2026-07-07-first-spanish-locale*.md`
  (Adversarial Review Gate 22/16/5); PR #411.
- **#306 ziggurat hoist** — spec/plan `docs/superpowers/{specs,plans}/2026-07-08-ziggurat-damage-hoist*.md`
  (spec review 18/17/1 applied; final holistic branch review 6 nits, 0 major); PR #413.
- **Contact email + site** — PR #415.
- **Tooling Phases 1–4** — trackers #367/#389; PRs #392–#409. **i18n phases 2–3** — PRs #349–#366.
- Per-change detail: `CHANGELOG.md` `[1.0.13]` block + `docs/agent/RUN_LOG.md`.

## Verification (release lane)

- `versionCode` 28 → 29, `versionName` 1.0.12 → 1.0.13 (bump rides in with the release PR; the
  release lane builds the committed code — no auto-bump).
- **No schema change** — `app/schemas/` byte-identical to v1.0.12 (verified `git diff v1.0.12..HEAD -- app/schemas/` empty).
- Unit suite green: **1332 JVM tests** (the release lane gates on `testDebugUnitTest`).
- Release PR runs the full PR gate (lint + unit + assembleDebug + minified-release variant +
  schema-drift) + the instrumented emulator lane before merge.
- After merge, the annotated `v1.0.13` tag triggers `release.yml`: builds versionCode 29, signs the AAB,
  and uploads to the Play **internal** track. The tag message above becomes the Play "What's new".

## Next

- Promotion internal → closed remains the developer's judgment call (Closed-Test Readiness Gate,
  `docs/plans/plan-FORWARD.md`) — no remaining repo-external blocker.
- i18n follow-through: the #410 Spanish native copy review before promoting the locale beyond internal;
  further `values-xx` locales.
- #306 remaining slices (enemy `takeDamage`/`onDeath`/SCATTER, `UWController.when(type)` effect bodies).
