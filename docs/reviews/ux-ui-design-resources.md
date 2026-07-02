# Steps of Babylon — UX/UI Design Skills & Plugins Assessment

> Research report: which Claude Code **skills/plugins** would strengthen UX/UI design work in this
> repo. Method: ecosystem sweep (official Anthropic marketplaces + community directories + targeted
> Android/Compose search), then per-candidate verification against the source repository (scope,
> install path, maintenance signals). **Report only — nothing was installed and no configuration
> changed in this PR.** Web-sourced facts (versions, stars, dates) are as of **2026-07-02** and
> will drift.

---

## Executive summary

- **The project's UI surface is Jetpack Compose + Material-flavoured design tokens**
  (`presentation/ui/theme/` — brand/role `Color` tokens, `SobTypography`, `SobShapes`) plus a
  custom `SurfaceView` battle renderer. The Compose side is exactly what the ecosystem's Android
  design skills target; **no skill found covers custom Canvas/game rendering** — the battle
  renderer stays out of scope for all candidates.
- **Recommended baseline (2 skills):** `hamen/material-3-skill` (what the UI should look like —
  MD3 tokens/components/theming + a compliance-audit mode) and `aldefy/compose-skill` (how to
  build it correctly — androidx-source-grounded Compose guidance). Both actively maintained,
  Compose-first, and complementary with near-zero overlap.
- **Optional third:** `phazurlabs/ux-ui-mastery` for designer-brain workflows (`/ux-audit`,
  `/design-critique`, `/accessibility-check`). Deep UX content and directly relevant to the
  post-v1.0 accessibility priority (TalkBack, color-blind modes), but young and iOS-leaning.
- **Situational:** Anthropic's official `example-skills` (frontend-design, theme-factory,
  canvas-design, web-artifacts-builder) are **web/HTML-oriented** — useful only for the `site/`
  privacy-policy page and for HTML design mockups ahead of Compose implementation. The remote
  session already ships built-in `dataviz` + `artifact-design` skills that overlap part of this.
- **Adoption constraint:** remote sessions run in **ephemeral containers with no marketplaces
  configured**, so ad-hoc installs vanish. Anything adopted must be committed to the repo —
  vendored into `.claude/skills/` (auto-discovered) or registered via
  `extraKnownMarketplaces`/`enabledPlugins` in `.claude/settings.json`.

---

## Candidates

### 1. `hamen/material-3-skill` — Material Design 3 (RECOMMENDED)

- **Repo:** <https://github.com/hamen/material-3-skill>
- **What it provides:** MD3 guidance across 30+ components, design tokens, theming, responsive/
  adaptive layout, and an **MD3 compliance audit** mode. **Jetpack Compose is the primary
  target** (Flutter secondary, web limited). Incorporates Google I/O 2026 expressive-layout and
  spacing-system guidance.
- **Maintenance signals:** v1.1.1 (2026-06-29), ~1.1k stars / 58 forks — the most adopted
  Android-design skill found.
- **Fit here:** maps directly onto the `presentation/ui/theme/` token system and every Compose
  screen (`home/ workshop/ weapons/ labs/ cards/ supplies/ economy/ missions/ settings/ stats/
  store/ help/ onboarding/`). The compliance-audit mode gives a repeatable "is this screen
  MD3-consistent?" check.
- **Install:** `claude plugin marketplace add hamen/material-3-skill` →
  `claude plugin install material-3@material-3-skill` (or `npx --yes skills add
  hamen/material-3-skill --skill material-3 -y`).

### 2. `aldefy/compose-skill` — Jetpack Compose expert (RECOMMENDED)

- **Repo:** <https://github.com/aldefy/compose-skill>
- **What it provides:** 24 reference guides covering Compose topics plus **6 source files pulled
  from `androidx/androidx`** as "receipts" against hallucinated APIs. Covers state management
  (`remember`/`derivedStateOf`/hoisting), recomposition performance + stability annotations,
  type-safe navigation (Nav 2→3), Material 3 motion tokens, design-to-code decomposition, and six
  production crash patterns with defensive-coding strategies.
- **Maintenance signals:** v2.3.1 (May 2026), 7 releases, ~526 stars, 6 contributors.
- **Fit here:** more engineering-leaning than pure design, but complementary to #1 — this repo's
  conventions (ViewModels exposing `StateFlow`, `SavedStateHandle` process-death survival per
  #234, Robolectric Compose UI tests per #253) all sit in its coverage area. Its recomposition/
  stability guidance is relevant to the perf-smoothness track (#242/#243).
- **Install:** copy `skills/compose-expert` into `.claude/skills/` (repo-committed) — no plugin
  manifest needed; Claude Code auto-discovers it.

### 3. `phazurlabs/ux-ui-mastery` — general UX depth (OPTIONAL)

- **Repo:** <https://github.com/phazurlabs/ux-ui-mastery>
- **What it provides:** 19 skills / 10 commands / 55 reference docs (~310k words): cognitive
  psychology + 50+ biases mapped to design patterns, component patterns, design systems,
  accessibility, gesture/touch optimization, Material 3 Expressive. Commands include `/ux-audit`,
  `/design-critique`, `/component-build`, `/accessibility-check`, `/figma-to-code`. Uses
  progressive disclosure, so the corpus does not bloat context wholesale.
- **Maintenance signals:** v3.0.0 but young — 24 stars / 8 forks / 4 commits; knowledge base
  references 2025–2026 research (WCAG 3.0 April-2026 draft).
- **Fit here:** the `/accessibility-check` and critique workflows line up with the stated
  post-v1.0 accessibility priority (TalkBack, color-blind modes) and with the shipped a11y work
  (#213/#214). Caveats: Android-specific guidance is thinner than its iOS 26 coverage, and its
  40+ production components are React/SwiftUI/CSS — the *principles* transfer, the code doesn't.
- **Install:** `claude plugin marketplace add phazurlabs/ux-ui-mastery` → install the plugin.

### 4. Anthropic official skills (`anthropics/skills`) — SITUATIONAL

- **Repo:** <https://github.com/anthropics/skills> (marketplace: `example-skills` plugin)
- **Design-relevant members:** `frontend-design` (anti-generic-AI-aesthetic guidance),
  `theme-factory` (theme styling), `canvas-design` (abstract visual composition),
  `web-artifacts-builder`.
- **Fit here:** all **web/HTML-oriented** — no Compose coverage. Two legitimate uses: (a) the
  top-level `site/` folder (the Pages-published privacy policy), (b) producing HTML design
  mockups/artifacts to iterate on a screen's look before implementing it in Compose. Note the
  remote harness already provides built-in `dataviz` and `artifact-design` skills covering part
  of this ground.
- **Install:** `claude plugin marketplace add anthropics/skills` → install `example-skills`.

### 5. Seen but not recommended

- **`wshobson/agents` — `mobile-android-design` skill** and assorted MD3 skills on directory
  sites (mcpmarket.com "Android Material 3 UI Guide", "android-jetpack-compose-expert", etc.) —
  thinner than candidates 1–2 with weaker maintenance signals; superseded by the picks above.
- **Directories for future browsing:** <https://claudemarketplaces.com/>,
  <https://www.claudepluginhub.com/>.

---

## Fit map (project surface → candidate)

| Project surface | Best-fit resource |
|---|---|
| Design tokens (`ui/theme/` Color, `SobTypography`, `SobShapes`) | material-3-skill (tokens/theming, audit mode) |
| Compose screens (`presentation/*`) — build correctness, state, perf | compose-skill |
| Accessibility (post-v1.0 TalkBack / color-blind priority) | ux-ui-mastery `/accessibility-check` + material-3-skill a11y guidance |
| UX critique / heuristic audits of flows | ux-ui-mastery (`/ux-audit`, `/design-critique`) |
| `site/` privacy page + HTML design mockups | anthropics `frontend-design` / `web-artifacts-builder` |
| Battle renderer (`SurfaceView`/Canvas game loop) | **no candidate covers this** — out of scope for all skills found |

## Adoption path (proposed, not executed)

1. **Phase 1 — baseline:** vendor `compose-skill`'s `skills/compose-expert` into `.claude/skills/`
   and register `hamen/material-3-skill` via `.claude/settings.json`
   (`extraKnownMarketplaces` + `enabledPlugins`) so both survive the ephemeral remote containers.
2. **Phase 2 — optional:** add `ux-ui-mastery` if the critique/audit command workflow proves
   wanted in practice (start it as a trial, not vendored).
3. **Phase 3 — situational:** add the official `anthropics/skills` marketplace when `site/` or
   mockup work actually comes up; skip until then.

**Pre-adoption checks (apply to each candidate before committing it):**

- **License** — verify the repo's license permits vendoring/redistribution before copying skill
  content into this repo (relevant to the compose-skill vendoring route).
- **Content review** — a skill is instruction text loaded into the agent's context; read it before
  enabling (third-party instructions are an injection surface). One-time review per version bump.
- **Context cost** — run `claude plugin details <name>` after install to see the projected token
  cost; prefer progressive-disclosure skills (all three recommendations qualify).
- **Drift** — pin versions where the install route allows it; re-verify maintenance status at
  adoption time (this report's figures date to 2026-07-02).
