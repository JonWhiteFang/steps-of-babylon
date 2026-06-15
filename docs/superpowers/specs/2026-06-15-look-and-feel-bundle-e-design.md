# Look & Feel — Bundle E: Identity / Art (Custom Font + Onboarding Biome Theming + Ziggurat Asset)

**Date:** 2026-06-15
**Issue:** #164
**Source review:** `docs/external-reviews/2026-06-12-look-and-feel-ux-review.md` (§4 HIGH Onboarding, §6 Typography/Iconography, §8 T12, §10 Remaining Recs 1 & 5)
**Predecessors:** #159 (design tokens / ActionBar removal, ADR-0022), #160 / PR #165 (Bundle A — de-emoji, loading/empty, a11y), #161 / PRs #166+#167 (Bundle B — back affordances, ADR-0023), #162 / PR #172 (Bundle C — haptics + celebrations + shared purchase pulse), #163 / PR #174 (Bundle D — collectibles rarity visual system)
**Status:** Design approved; hardened by Adversarial Review Gate (§7); ready for implementation plan.

---

## 1. Goal & Scope

Bundle E is the **identity / art** wave of the 2026-06-12 look-and-feel review — and the **last** of
the five review bundles (A–E). The review's findings: §6 *"Typography is the biggest remaining
identity lever — still Roboto everywhere; a display/serif font for headers + biome names is cheap now
that #159 centralised the type scale"*; §4 HIGH *"Onboarding: emoji-only icons, the 🏛️ is literally
the wrong building, flat single background with no per-slide colour progression, abrupt finish."*

This bundle gives the app a **distinct typographic identity** (a carved-stone display font) and turns
first-launch onboarding from a flat emoji carousel into a **themed biome journey** with a real
stepped-ziggurat emblem and a proper finishing beat.

Unlike Bundles A–D, this one is **not purely presentation-trivial**: it adds the project's **first
`res/font/` assets and first `res/drawable` art asset**, and it touches the **onboarding flow**
(`OnboardingSlide` / `OnboardingScreen`) — a documented fragile zone (ADR-0021). It remains additive
and presentation-scoped: **zero** change to domain logic, economy, concurrency, the battle
renderer/engine, loadout logic, or `Screen.kt` routes. The onboarding **gating/routing contract**
(device-local completion flag, `Screen.Onboarding` deliberately out of `allScreens`) is preserved
exactly.

**In scope (this PR):**
1. **Cinzel display font (greenfield `res/font/`).** Bundle the OFL-licensed **Cinzel** static weights
   into a new `res/font/` directory + a `Cinzel` `FontFamily`, and apply it via `Type.kt` to the
   **Display + Headline** tiers only. Body / Title / Label tiers stay Roboto (`FontFamily.Default`).
   **This re-themes every Display/Headline consumer app-wide, not just titles** — see the Cinzel
   blast-radius enumeration in §2. The battle `BiomeTransitionOverlay` biome name (rendered in
   `headlineLarge`) gets Cinzel for free; **other** biome-name render sites (Home `TierSelector`,
   Help copy, notifications) are *not* Headline-tier and intentionally stay Roboto (§2).
2. **Fill the `displayMedium` / `displayLarge` gap.** `SobTypography` defines `displaySmall` but **not**
   `displayMedium` / `displayLarge`, yet `OnboardingScreen.kt:101` already consumes `displayMedium` and
   silently falls back to the Material3 stock style (§2). Add both tokens (with Cinzel) so the
   onboarding title is intentional and the scale is complete; guard them with a new test (§5) so the
   latent fallback cannot silently regress.
3. **Onboarding per-slide biome theming.** Add a pure `biome: Biome?` field to `OnboardingSlide`; paint
   each slide with that biome's `BiomeTheme` sky-gradient (the existing `HomeScreen` pattern), with a
   **cross-fade** between slides driven by pager scroll offset (reduced-motion → static per-page
   gradient), and a **legibility scrim** behind the title/body text with locked color/alpha/contrast
   targets (§3 E9, §4.5).
4. **Completion beat.** Replace the abrupt finish with a **one-shot gold pulse on the final slide**
   (reusing Bundle C's `PurchasePulse` scale/graphics-layer pattern — *not* a "shimmer"/particle
   effect, which does not exist in the codebase, §2), played **in-onboarding before navigation** via a
   persist-first / pulse / then-navigate sequence (§4.5, §4.6). Reduced-motion → navigate immediately,
   no pulse.
5. **Vector ziggurat emblem (greenfield `res/drawable`).** A new `ic_ziggurat_emblem.xml`
   VectorDrawable (stepped ziggurat inside a gold roundel — the "Emblem" direction) replaces the 🏛️
   emoji on onboarding slide 1. Modelled via a pure `art: OnboardingArt?` marker on `OnboardingSlide`;
   the other three slides keep their emoji icons.

**Explicitly out of scope (tracked elsewhere — do NOT do here):**
- **Applying Cinzel to Title / Body / Label tiers** or to in-battle `android.graphics.Paint` text
  (HealthBarRenderer, WaveAnnouncement, HP%/boss countdown). Those use `Paint`/`Typeface`, not Compose
  Typography — a separate mechanism, deliberately not in scope. Body legibility keeps Roboto.
- **The in-battle ziggurat renderer.** `ZigguratEntity` already draws a procedural stepped ziggurat
  from `BiomeTheme.zigguratColors` / the `ZIGGURAT_SKIN` cosmetic override. Bundle E's ziggurat is the
  **onboarding slide-1 icon only**; the renderer is untouched.
- **Any new particle/shimmer effect infra**, and **the battle `ParticlePool`/`EffectEngine`** (in
  `presentation/battle/effects/`). The completion beat reuses the existing `PurchasePulse` scale
  pattern only.
- **Launcher icon / app-branding refresh, themed currency glyphs, downloadable Google Fonts.** Not in
  #164's three bullets.
- **Reconciling the biome→tier-range discrepancy** between `HelpScreen.kt` / GDD §6.3 and `Biome.kt`
  (noted in §2). Onboarding copy does **not** print tier numbers, so it is not load-bearing here; left
  to a separate correctness ticket to avoid scope creep.
- **Any change to the battle renderer/engine/effects, economy, concurrency, loadout logic, domain
  models, step-counting, or `Screen.kt` routes.**

**Risk:** Low–moderate. Presentation-scoped and additive, but higher-touch than A–D because it (a)
introduces the first bundled font + art assets (build/shrink/licensing surface), (b) edits the
onboarding flow (a fragile zone), and (c) re-themes Display/Headline app-wide (legibility blast
radius, §2). Mitigations: assets are referenced `R.font`/`R.drawable` (not stripped by
`shrinkResources` — it resolves R-class references in compiled Kotlin); the new `OnboardingSlide`
fields are pure-Kotlin (no Android import); the completion beat persists the flag **first** and
navigation is guaranteed by a `LaunchedEffect`, so it is never gated on the animation (§4.6).

---

## 2. Ground Truth (verified against current `HEAD`, post-v1.0.7)

The design rests on these verified facts, not the review's prose:

| Fact | Evidence |
|---|---|
| **Typography is 100% Roboto.** All 13 `TextStyle`s in `SobTypography` set `fontFamily = FontFamily.Default`. The header KDoc explicitly flags the custom display/serif font as "a deliberate later step (needs a `res/font/` asset)" and that swapping `fontFamily` "will re-theme the whole app from one place." | `theme/Type.kt:18-20` (KDoc), `:22-75` (every style). |
| **`SobTypography` defines `displaySmall` but NOT `displayMedium` / `displayLarge`.** `displaySmall` is 36sp/44 lineHeight, letterSpacing **−0.5sp** (hand-tuned, not raw Material3). | `theme/Type.kt:23-26` (only `displaySmall`); no `displayMedium`/`displayLarge` anywhere in the file. |
| **`OnboardingScreen` consumes `displayMedium`** for the slide icon — so today it silently uses the Material3 **stock** `displayMedium`, not a SoB token. `displayLarge` currently has **zero** consumers app-wide. | `OnboardingScreen.kt:101`. |
| **The font is wired in one place.** `StepsOfBabylonTheme` passes `typography = SobTypography` to `MaterialTheme`. Changing the family in `Type.kt` re-themes the app. | `theme/Theme.kt:28`. |
| **Cinzel blast radius — every Display/Headline consumer switches to Cinzel** (not just titles). The full set (grep of `typography.(display\|headline)` over `app/src/main`): `HomeScreen.kt:111` live "{n} steps" hero (`headlineLarge`); **`CurrencyDashboardScreen.kt:159` the numeric currency balance `"%,d".format(amount)` (`headlineMedium`) — a *number* in inscriptional caps**; `SettingsScreen.kt:36/52/67` section headers (`headlineSmall`); `MissionsScreen.kt:59/72` section titles (`headlineSmall`); `PostRoundOverlay.kt:105` "Round Over" (`headlineMedium`); `PauseOverlay.kt:46` pause title (`headlineLarge`); `HealthConnectPermissionActivity.kt:30` "Privacy Policy" (`headlineMedium`); `OnboardingScreen.kt:101` icon (`displayMedium`) + `:105` title (`headlineMedium`); `BiomeTransitionOverlay.kt:46` biome name (`headlineLarge`). The currency digit case is the one legibility risk E3 must own (digit-legibility check in §6). | grep results above; `Type.kt:23-38` (the Cinzel'd tiers). |
| **Biome names render in MORE than one tier.** Only the battle `BiomeTransitionOverlay` biome name is `headlineLarge` (→ Cinzel for free). The **Home `TierSelector`** renders `"Tier N — <Biome>"` in **`titleMedium`** (stays Roboto), and it shows on every Home visit (more-seen than the transition overlay). `HelpScreen` lists biomes in body copy; `BattleViewModel` notification text also names biomes. So "biome names get Cinzel" is true **only** for the transition overlay. | `BiomeTransitionOverlay.kt:46` (`headlineLarge`); `TierSelector.kt:38` (`titleMedium`, used by `HomeScreen.kt:94`); `HelpScreen.kt:56`; `BattleViewModel.kt:375`. |
| **No `res/font/` exists; no font deps; no `Typeface`/`.ttf`/`.otf` anywhere.** `res/` has only `drawable, layout, mipmap-anydpi-v26, raw, values, xml`. No `ui-text-google-fonts`; `androidx.compose.ui.text` ships transitively via the Compose BOM (already imported in `Type.kt`). | `app/src/main/res/` listing; `gradle/libs.versions.toml`; `app/build.gradle.kts`. |
| **`OnboardingSlide` is a pure 4-field data class** (`icon: String`, `title`, `body`, `isPermissionPrimer`), held in `object OnboardingContent.slides` (exactly 4 slides). Its KDoc states the emoji `icon` (not an `ImageVector`) keeps the list JVM-testable. It carries **no** colour / biome / art field today. | `OnboardingSlide.kt:8-13`, `:16-44`, `:3-7`. |
| **Slide 1 icon is literally `"🏛️"`** ("Walk to power your ziggurat"). Slides 2/3/4 = `🔨` / `⚔️` / `👣`; slide 4 is the permission primer (`isPermissionPrimer = true`). | `OnboardingSlide.kt:18-42`. |
| **Onboarding is flat-themed.** Root is a plain `Surface(Modifier.fillMaxSize())`; the only colour drawn is the page-dot indicator (`colorScheme.primary` / `onSurfaceVariant`). Icon = `Text(slide.icon, style = displayMedium)` with `clearAndSetSemantics {}` (decorative). The whole app renders inside the single dark `StepsOfBabylonTheme`. Body text uses `colorScheme.onSurfaceVariant` (= `TextSecondary` `#D8C7A8`, muted sandstone — **not** Ivory); the title (`headlineMedium`) inherits `onSurface` = `TextPrimary` Ivory `#FFF8E7`. | `OnboardingScreen.kt:77`, `:99-103`, `:107-112`; `Theme.kt:20`. |
| **`reducedMotion` is a parameter** threaded into `OnboardingScreen` (from `MainActivity`, which derives it via `ReducedMotionCheck.isReducedMotionEnabled`) and used to choose `scrollToPage` vs `animateScrollToPage`. New motion (cross-fade, completion pulse) must honor it at the call site. | `OnboardingScreen.kt:55`, `:66-70`; `MainActivity.kt:294-301`. |
| **The final-slide branch is the "abrupt finish."** It ends in a plain `Button`/`TextButton` calling `finish()` (= `viewModel.completeOnboarding(); onFinished()`, **synchronous**). No flourish. Branch order (granted → !asked → denied) is a deliberate replay-safety contract (must be preserved). `finish()` is reached only on the **granted** ("Start playing") and **denied** ("Continue without") paths — *not* the "Enable step counting" path (which fires `onEnableStepCounting`). | `OnboardingScreen.kt:72-75`, `:148-194`. |
| **`onFinished()` navigates AWAY and pops Onboarding off the back stack**, unmounting `OnboardingScreen`. So any effect hosted in the onboarding composable and fired on the same tick as navigation is torn down before it can play — the completion beat must run **before** `onFinished()`. | `MainActivity.kt:318-333` (`navigate(Home){ popUpTo(Onboarding){inclusive=true} }`). |
| **Bundle C's "celebration" infra is a text chip + a scale pulse — NOT a shimmer or particle system.** `ClaimCelebration` is a `primaryContainer` `Surface` showing `event.label` text that `scaleIn()+fadeIn()`s, fires a haptic, and auto-dismisses after 1.4s (event-driven, nullable-event API). `PurchasePulse` is a `graphicsLayer` scale pulse (1f→1.12f). There is **no** gold shimmer / particle composable in `presentation/ui/`. The only particle infra (`ParticlePool`) is in `presentation/battle/effects/` and is out of scope. The completion beat therefore reuses the **`PurchasePulse` scale pattern**, not a "shimmer." | `ClaimCelebration.kt:23-63`; `PurchasePulse.kt:36-55`; `presentation/ui/` listing. |
| **A reusable per-biome palette exists and is already reused in a Compose menu.** `BiomeTheme.forBiome(biome)` returns ARGB `Int`s (sky top/bottom, ground, ziggurat layers, enemy tint, particle). `HomeScreen` paints `Brush.verticalGradient(skyColorTop@0.55α, skyColorBottom@0.30α)`; `BiomeTransitionOverlay` paints a full-screen sky gradient. Onboarding has no equivalent yet. | `battle/biome/BiomeTheme.kt:5-55`; `HomeScreen.kt:71,86`; `BiomeTransitionOverlay.kt:31-34`. |
| **Biome sky palettes (the onboarding gradient inputs).** HANGING_GARDENS `#2E5D3A`→`#4A7C59`; BURNING_SANDS `#B85C1E`→`#D4943A` (**lightest** sky-bottom — the contrast worst-case for the scrim); FROZEN_ZIGGURATS `#1A3A5C`→`#4682B4` (its light `#B0C4DE` is the **ground**, not the sky); UNDERWORLD_OF_KUR `#1A0A2E`→`#2D1B4E`; **CELESTIAL_GATE `#0A0A2A`→`#1A1A4A` (near-black navy — the *darkest* sky, darker than the Underworld it skips; its identity comes from gold `#FFD700` ziggurat accents, not sky brightness).** | `BiomeTheme.kt:18-52`. |
| **`Biome` is a 5-value enum** (`HANGING_GARDENS 1..3`, `BURNING_SANDS 4..6`, `FROZEN_ZIGGURATS 7..8`, `UNDERWORLD_OF_KUR 9..10`, `CELESTIAL_GATE 11..Int.MAX_VALUE`, `isComingSoon = true`). Pure Kotlin in `domain/model/`. | `domain/model/Biome.kt:3-15`. |
| **`OnboardingContentTest` pins structure, not the icon.** It asserts slides non-empty, last slide is the permission primer, no earlier primer, every slide has non-blank title+body. It does **not** assert on `icon`. It is a pure-JVM (JUnit Jupiter) test exercising `OnboardingContent.slides` directly. | `test/.../onboarding/OnboardingContentTest.kt`. |
| **Release build minifies + shrinks resources.** `isMinifyEnabled = true`, `isShrinkResources = true`. The Gradle resource shrinker resolves `R.font`/`R.drawable` references in compiled Kotlin, so referenced font/art are retained — no keep rule needed. Font filenames must be lowercase `[a-z0-9_]` (no hyphens). | `app/build.gradle.kts:103-104`; `proguard-rules.pro` (no resource keeps). |
| **`compose-ui-graphics` is an `implementation` dep on the JVM unit-test classpath.** `Color`/packed-ARGB math runs in pure-JVM tests (proven by `RarityTest` resolving `Color(0xFF…)`). | `app/build.gradle.kts:208`; `libs.versions.toml:42`; `RarityTest.kt`. |

**Audit corrections vs. the review** (recorded so a future reviewer doesn't re-raise):

| Review claim | Reality (verified) |
|---|---|
| "real stepped-ziggurat asset" / "replace the ziggurat art" | The **in-battle** ziggurat is already a correct procedural stepped ziggurat. The wrong-building problem is **only** the onboarding slide-1 **🏛️ emoji**. Bundle E replaces that one icon; the renderer is untouched. |
| "custom font for headers **and biome names**" | Theming the Headline tier covers biome names **only on the battle `BiomeTransitionOverlay`** (`headlineLarge`). The most-seen biome-name label — Home `TierSelector` (`titleMedium`) — and Help/notification biome names are *not* Headline-tier and **intentionally stay Roboto** (E3 keeps Title/Body/Label on Roboto). Pulling `TierSelector` into Cinzel is explicitly *not* in scope. |
| "reuse Bundle C's celebration for a gold shimmer" (our own earlier draft) | Bundle C shipped a **text chip** (`ClaimCelebration`) and a **scale pulse** (`PurchasePulse`) — **no shimmer/particle effect exists**. The completion beat reuses the `PurchasePulse` *scale* pattern; "shimmer/particle" wording is removed (§3 E10). |
| (implicit) onboarding theming is trivial like A–D | It edits a **fragile zone** (onboarding flow, ADR-0021) and adds the first `res/` assets — hence the dedicated fragile-zone analysis (§4.6) A–D never needed. |

---

## 3. Decisions (locked during brainstorming + hardened by §7)

| # | Decision | Choice | Driver |
|---|---|---|---|
| E1 | Display font | **Cinzel** (Google Fonts, SIL OFL 1.1). | User pick (specimen A). Roman inscriptional caps — "carved in stone," fits the ancient-ziggurat identity. OFL ⇒ bundles cleanly in the AAB; resolves the issue's font-licensing prerequisite. |
| E2 | Font delivery | **Bundle static `.ttf` weights in `res/font/`** (not downloadable Google Fonts). | Titles must render correctly on first launch, offline, with no Play-Services font-provider dependency or fallback flash. No new gradle dep. |
| E3 | Font scope | **Display + Headline tiers only.** Body / Title / Label stay Roboto. This re-themes ALL Display/Headline consumers app-wide (§2 blast radius), incl. the `CurrencyDashboard` numeric balance and several non-title headers — accepted, with a digit-legibility sign-off (§6). Biome names get Cinzel **only** on the battle transition overlay (`headlineLarge`); `TierSelector`/Help/notification biome names stay Roboto. | User pick. Cinzel is a caps display face — stunning on big titles, less legible on dense UI/body. Display+Headline maximises identity; the one residual legibility risk (currency digits) is verified on-device, not assumed away. |
| E4 | `displayMedium`/`displayLarge` gap | **Add both tokens** to `SobTypography` (SoB scale + Cinzel) **and add a `SobTypographyTest`** asserting the Cinzel'd tiers are non-default (§5). | User pick. `OnboardingScreen` already references `displayMedium` and silently falls back to Material default (§2) — a real latent gap. The test prevents a future refactor silently re-introducing the fallback (the build stays green either way without it). |
| E5 | Onboarding slide→biome map | **Journey: 1→HANGING_GARDENS, 2→BURNING_SANDS, 3→FROZEN_ZIGGURATS, 4→CELESTIAL_GATE.** UNDERWORLD_OF_KUR skipped. | User pick. Mirrors the real tier journey and **ends at the symbolic destination** — the Celestial Gate (the endgame biome, gold-gated). Rationale is *journey escalation + destination symbolism*, **not** sky brightness: CELESTIAL_GATE's sky is near-black (§2), so the gold completion pulse + scrim carry the "arrival," not the gradient. UNDERWORLD_OF_KUR is skipped to keep the journey reading as ascent, not detour. |
| E6 | Biome field placement | **Add `biome: Biome?` to `OnboardingSlide`** (the domain enum). | The slide is the natural carrier; `Biome` is pure Kotlin, so the content list stays JVM-testable (honors the OnboardingSlide.kt:3-7 design note). Nullable so a future non-themed slide is expressible. |
| E7 | Gradient source | **Reuse `BiomeTheme.forBiome().skyColorTop/Bottom`** (the `HomeScreen` pattern). | One source of truth for biome palettes; visually unifies onboarding with Home and the battle biome transition. No new palette. |
| E8 | Slide transition | **Cross-fade gradients via `pagerState.currentPageOffsetFraction`** (signed `[-0.5,0.5]`): `neighbor = currentPage + sign(offset)` clamped to `[0, lastIndex]`, `t = abs(offset)`, blended with a pure `lerpArgb`. Reduced-motion → static per-page gradient. | User pick. "Visual progression" the review asked for; premium feel. Signed-offset + edge guards (page 0 / lastIndex) pinned so overscroll can't index out of range. The lerp is a pure ARGB-int helper (JVM-testable, §5). |
| E9 | Text contrast | **Translucent scrim behind title/body.** Locked: `Color.Black` at **alpha ≈ 0.45**, drawn as a layer behind the title+body block (sized to the text column with padding, not full-screen), giving a **uniform** treatment tuned for the **lightest** palette (Burning Sands amber `#D4943A`) so all 4 — including the near-black Celestial — clear WCAG-AA. Targets: title (`headlineMedium`, large text) ≥ **3:1**, body (`bodyLarge`, normal text) ≥ **4.5:1**. Measured colors: title = Ivory `#FFF8E7`; body = `onSurfaceVariant` sandstone `#D8C7A8`. Verification: computed contrast of each color over (sky-bottom ⊕ black@0.45) for all 4 biomes during implementation + on-device sign-off. | User pick + §7. A uniform scrim tuned for the worst case is simpler than per-palette tuning and guarantees AA everywhere; the concrete alpha/targets/colors remove the "tuned somehow" ambiguity and the earlier ivory/Frozen-sky slips. |
| E10 | Completion beat | **One-shot gold pulse on the final slide** reusing Bundle C's **`PurchasePulse` scale pattern** (a brief `graphicsLayer` scale/glow on the final-slide icon + CTA, ~400–500ms). **NOT** a shimmer/particle effect. Played **in-onboarding before navigation** (E10-seq below). Reduced-motion → no pulse, navigate immediately. | User pick + §7. The earlier "gold shimmer reusing Bundle C's celebration" was unbuildable — no shimmer infra exists, and the event-driven `ClaimCelebration` chip would be unmounted by navigation. The pulse is self-contained in onboarding and reuses real shipped infra. The beat plays on **slide 4** (where `finish()` fires), not the slide-1 emblem. |
| E10-seq | Beat ⇄ navigation ordering | On the final CTA (`finish()` paths only): **(1)** call `viewModel.completeOnboarding()` immediately and unconditionally (persists the gating flag); **(2)** set a local `finishing` state that drives the pulse; **(3)** a `LaunchedEffect(finishing)` calls `onFinished()` after `~450ms` if `!reducedMotion`, else immediately. Navigation is thus guaranteed by the effect and the gating flag is already persisted — the beat is non-blocking and the completion contract is never gated on the animation. | §7 (fragile-zone). Reconciles "show a beat" with "must not gate completion/nav on animation" and with the unmount-on-navigate constraint: completion is persisted first, so even if the app is backgrounded mid-pulse, relaunch won't re-onboard; `onFinished()` always fires exactly once. |
| E11 | Ziggurat style | **"Emblem" — stepped ziggurat inside a gold roundel**, as a `res/drawable` VectorDrawable. | User pick (direction D). Strongest standalone identity (badge/crest, app-icon-adjacent); vector matches the repo's all-vector asset style and needs no bitmap-decode infra. |
| E12 | Ziggurat field | **Add a pure `art: OnboardingArt?` marker enum** (`ZIGGURAT`) to `OnboardingSlide`; resolve to `R.drawable` in the screen. Slide 1 sets `art = ZIGGURAT` and **keeps `icon = "🏛️"` as an inert fallback string** (never rendered when `art != null`; kept so the "non-blank icon" shape is uniform and no future test conflicts). | Keeps `OnboardingSlide` Android-free / JVM-testable (no `@DrawableRes Int` in the model). The screen maps the marker → painter. |
| E13 | Render switch | **When `slide.art != null`, render `Image(painterResource(...), contentDescription = null)`; else the emoji `Text(... , Modifier.clearAndSetSemantics {})`.** | Minimal, additive edit to the existing icon slot. `contentDescription = null` is the (mandatory) decorative treatment for `Image` and emits no semantics node — `clearAndSetSemantics {}` is **not** added on the `Image` (redundant there; it remains load-bearing only on the emoji `Text`, which auto-generates a node). |
| E14 | Implementation structure | **Assets + font first** (`res/font/`, `FontFamily`, `Type.kt` + `SobTypographyTest`), **then** the ziggurat drawable, **then** onboarding theming/beat. One PR. | Matches Bundles A–D's "tokens/assets first, wire consumers second" order; each step independently buildable. |
| E15 | Delivery / version | **One PR → v1.0.8 / versionCode 24**, Play internal track. | User pick. Matches the A/C/D single-patch-release cadence; all three parts are unblocked (vector ziggurat made in-house, font OFL). |

---

## 4. Architecture

New assets + a font family + four edited files + two new tests. **No ViewModel / domain-logic / state
change.**

### 4.1 `res/font/` — bundled Cinzel (greenfield)
- New directory `app/src/main/res/font/` with static Cinzel weights, lowercase-named per Android
  resource rules: **`cinzel_regular.ttf` (400) + `cinzel_bold.ttf` (700)**. The Display/Headline tiers
  all use `FontWeight.Bold` (→ 700), so 700 is what renders; 400 is the family's normal-weight anchor.
  (SemiBold 600 is *not* required — no Cinzel'd tier uses it; a future Cinzel tier at a non-bundled
  weight would silently synthesize, so any such addition must bundle that weight.)
- New `OFL.txt` (the Cinzel license) alongside the fonts or in a repo `licenses/` dir — **font
  licensing is satisfied by SIL OFL 1.1**, which permits AAB redistribution.
- Font files are **fetched from Google Fonts during implementation** (acquisition step in the plan,
  §6) — binary assets not currently in the repo.

### 4.2 `theme/` — the `Cinzel` FontFamily + Type.kt edits
- Define the family once (a small `presentation/ui/theme/Font.kt`, or top-level in `Type.kt`):
  ```kotlin
  val Cinzel = FontFamily(
      Font(R.font.cinzel_regular, FontWeight.Normal),
      Font(R.font.cinzel_bold, FontWeight.Bold),
  )
  ```
  (Static `.ttf` → `FontFamily` in Kotlin needs **no** `res/font/*.xml` family file.)
- `Type.kt`: set `fontFamily = Cinzel` on **`displaySmall`, `displayMedium` (new), `displayLarge`
  (new), `headlineLarge`, `headlineMedium`, `headlineSmall`** only. All Title / Body / Label styles
  keep `fontFamily = FontFamily.Default`. Update the header KDoc to record that Display+Headline now
  carry Cinzel (and the app-wide blast radius per §2).
- **`displayMedium` / `displayLarge` scale** (locked, extends the existing scale above `displaySmall
  36sp`): `displayMedium = 45sp / 52 lineHeight`, `displayLarge = 57sp / 64 lineHeight`, both
  `FontWeight.Bold`. letterSpacing: inherit `displaySmall`'s tightening intent — `displayMedium = 0sp`,
  `displayLarge = (-0.25).sp` (the existing scale tightens larger sizes; `displaySmall` is −0.5sp). The
  planner may refine within ±2sp but the tokens MUST be defined and Cinzel'd.

### 4.3 `res/drawable/ic_ziggurat_emblem.xml` — vector ziggurat (greenfield)
- A `<vector>` drawable: stepped ziggurat (≈4 trapezoid/rect layers + a small apex/finial) centred in
  a gold roundel (ring + translucent fill), drawn in brand tokens (Gold `#D4A843`, Sand `#C2B280`,
  Ivory `#FFF8E7`). Authoring grid `viewportWidth/Height = 108×108` (launcher-adjacent emblem
  proportions), rendered at the call-site `Image` size (96.dp, §4.5). No gradients/bitmaps (clean flat
  vector).

### 4.4 `OnboardingSlide.kt` edits
```kotlin
/** Decorative art slot for a slide. Pure marker — the screen maps it to an @DrawableRes.
 *  Kept out of the model so OnboardingSlide stays Android-free / JVM-testable. */
enum class OnboardingArt { ZIGGURAT }

data class OnboardingSlide(
    val icon: String,                       // emoji fallback (slides 2–4; inert when art != null)
    val title: String,
    val body: String,
    val isPermissionPrimer: Boolean = false,
    val biome: Biome? = null,               // E6 — per-slide gradient source
    val art: OnboardingArt? = null,         // E12 — vector art overriding the emoji
)
```
- Slide 1: `biome = HANGING_GARDENS, art = ZIGGURAT, icon = "🏛️"` (icon kept as inert fallback, E12).
- Slide 2: `biome = BURNING_SANDS`. Slide 3: `biome = FROZEN_ZIGGURATS`. Slide 4: `biome =
  CELESTIAL_GATE`.
- KDoc updated to explain the two new pure fields and that they preserve JVM-testability.

### 4.5 `OnboardingScreen.kt` edits
- **Base per-page gradient:** behind the pager content, paint `Brush.verticalGradient` from the current
  slide's `BiomeTheme.forBiome(biome)` sky colours. When `biome == null`, fall back to the existing
  flat `Surface` colour.
- **Cross-fade (E8):** a **single shared gradient layer behind the whole `HorizontalPager`** (not
  per-page — a per-page `Box` only ever sees its own biome and can't blend). Compute the blended
  top/bottom colour from `pagerState.currentPage` + `currentPageOffsetFraction`:
  `neighbor = (currentPage + sign(offset)).coerceIn(0, lastIndex)`, `t = abs(offset).coerceIn(0f, 1f)`,
  `lerpArgb(currentBiomeColor, neighborBiomeColor, t)` per channel. `if (reducedMotion)` → use the
  settled page's static gradient (no offset blend). `lerpArgb(a: Int, b: Int, t: Float): Int` is a
  **pure** packed-ARGB helper in `presentation/ui/` (alongside the other shared helpers) — JVM-tested
  (§5).
- **Legibility scrim (E9):** a `Box`/`Brush` of `Color.Black` at alpha ≈ 0.45 behind the title+body
  column (above the gradient, below the text; non-interactive). Page dots + buttons keep their
  gold/`primary` tokens. (Concrete targets in E9.)
- **Art slot (E13):**
  ```kotlin
  if (slide.art != null) {
      Image(painterResource(artDrawable(slide.art)), contentDescription = null,
            modifier = Modifier.size(96.dp))
  } else {
      Text(slide.icon, style = MaterialTheme.typography.displayMedium,
           modifier = Modifier.clearAndSetSemantics {})
  }
  ```
  `artDrawable(OnboardingArt): @DrawableRes Int` is a tiny screen-local mapper (`ZIGGURAT →
  R.drawable.ic_ziggurat_emblem`).
- **Completion beat (E10 / E10-seq):** wrap `finish()` so the final CTA does persist-first → pulse →
  navigate:
  ```kotlin
  fun finish() {
      viewModel.completeOnboarding()          // (1) persist gating flag — unconditional, first
      finishing = true                        // (2) drive the pulse (local state)
  }
  LaunchedEffect(finishing) {
      if (finishing) {
          if (!reducedMotion) delay(FINISH_PULSE_MS)   // ~450ms
          onFinished()                        // (3) guaranteed exactly once; immediate if reduced-motion
      }
  }
  ```
  The pulse is a `PurchasePulse`-style `graphicsLayer` scale/glow keyed off `finishing`, applied to the
  final-slide icon + CTA. The granted → `!permissionAsked` → denied branch order is **unchanged**; only
  the two `finish()` call sites route through this.

### 4.6 Fragile-zone analysis (onboarding flow — ADR-0021)
A–D never edited onboarding; Bundle E does, so this is explicit:
- **Gating/routing untouched:** no change to `OnboardingPreferences` (device-local SharedPreferences
  completion flag), to `Screen.Onboarding`'s deliberate absence from `allScreens` /
  `argumentFreeRoutes` / nav items, or to the `MainActivity` routing that decides Home vs.
  back-to-Settings. Theming is purely visual within the existing composable.
- **Completion contract preserved & not gated on animation (E10-seq):** `completeOnboarding()` runs
  **first and unconditionally** on the CTA tap (persisting the device-local flag); `onFinished()` is
  invoked exactly once by the `LaunchedEffect`, after the pulse if motion is on, **immediately** if
  reduced-motion. Backgrounding mid-pulse cannot cause a re-onboard (flag already persisted). The
  ~450ms sequencing is a brief, bounded delay of *navigation only*, never of the completion flag.
- **Unmount-safe:** because the beat runs **before** `onFinished()` (which pops/disposes the screen),
  it is not torn down mid-play — the earlier "host inside the screen fired on navigation" failure mode
  is avoided.
- **Branch order & replay safety:** the final-slide `when(granted → !asked → denied)` order is not
  reordered; the beat is additive on the two `finish()` paths only.
- **Onboarding never grants Steps** — unchanged (explain-only).
- **a11y preserved:** the art is decorative (`contentDescription = null`, no semantics node); the emoji
  keeps `clearAndSetSemantics {}`; the page-dot row keeps its single "Page X of N" label; the scrim and
  pulse are non-interactive.

### 4.7 Unchanged
`OnboardingViewModel` (still just exposes `slides` + `completeOnboarding()`), `OnboardingPreferences`,
`MainActivity` routing, every domain model, the battle renderer/engine/effects (incl. `ParticlePool`),
`Screen.kt`. The new `OnboardingSlide` fields are populated in `OnboardingContent` and read in
`OnboardingScreen` only.

---

## 5. Testing

New + extended **pure JVM** tests (JUnit Jupiter), consistent with the A–D house norm (pure helpers are
unit-tested; `@Composable` visuals get the on-device feel sign-off, since Compose UI tests don't run
under Robolectric here):

1. **`OnboardingContentTest` extensions** (pure, no Android):
   - Slide→biome map is exactly `[HANGING_GARDENS, BURNING_SANDS, FROZEN_ZIGGURATS, CELESTIAL_GATE]` in
     order over the 4 slides (UNDERWORLD_OF_KUR absent).
   - Exactly one slide carries `art == ZIGGURAT`, and it is the **first** slide.
   - Existing invariants still hold (last slide is the permission primer; non-blank title/body).
2. **`lerpArgb` helper test** (pure): `t = 0f` → colour A; `t = 1f` → colour B; `t = 0.5f` → per-channel
   midpoint; alpha channel handled; `t` clamped to `[0,1]`; plus a **signed-offset neighbor-selection**
   case (negative offset at page>0 picks the previous biome; guards at page 0 / lastIndex fall back to
   the settled colour). `lerpArgb` operates on packed-ARGB `Int`s (`BiomeTheme.skyColorTop/Bottom` are
   `Int`), so it is pure `kotlin.Int` math — no Compose runtime needed.
3. **`SobTypographyTest`** (pure, NEW — closes the latent-fallback regression vector, E4): assert
   `SobTypography.displayMedium` and `.displayLarge` (and `displaySmall` + the three `headline*` tiers)
   have `fontFamily == Cinzel` and are **not** `FontFamily.Default`. `Typography` and `FontFamily` are
   on the JVM unit-test classpath via `compose-ui-graphics`/`ui-text` (no Android resource needed to
   compare family identity). This guards both "the tokens exist" and "they carry Cinzel" so a future
   refactor can't silently drop back to the Material stock style.
4. **Font/art wiring is compile-checked** — `R.font.cinzel_*` / `R.drawable.ic_ziggurat_emblem`
   references fail the build if the assets are missing. `DomainPurityTest` stays green (no Android
   import added to `domain/`).

**Compose-bound / visual pieces** — the biome gradient, cross-fade, scrim contrast, completion pulse,
and the vector emblem — are verified by the **on-device feel sign-off** (§6), not unit tests.

**Headline count:** 996 JVM → 996 + N (the new `OnboardingContent` cases + `lerpArgb` cases +
`SobTypographyTest` cases). The plan pins the literal N; update `CLAUDE.md`'s headline line +
`CHANGELOG.md` when it lands.

---

## 6. Delivery

One PR, on a `feat/164-look-and-feel-bundle-e` branch. Build order (E14):
1. **Acquire + add font assets** → `res/font/cinzel_*.ttf` + `OFL.txt`; define the `Cinzel`
   `FontFamily`.
2. **`Type.kt`** → add `displayMedium`/`displayLarge` (locked scale, §4.2); apply Cinzel to Display +
   Headline tiers; update KDoc. Add **`SobTypographyTest`** (§5 #3).
3. **`res/drawable/ic_ziggurat_emblem.xml`** → the vector emblem.
4. **`OnboardingSlide.kt`** → `biome` + `art` fields, `OnboardingArt` enum, slide assignments; extend
   `OnboardingContentTest`.
5. **`OnboardingScreen.kt`** → shared-layer gradient + `lerpArgb` cross-fade (signed offset + edge
   guards) + scrim + art slot + completion beat (E10-seq); add `lerpArgb` + its test.
6. **Doc sweep** (PR Task-List Convention): `CLAUDE.md` headline count, `CHANGELOG.md` entry,
   `docs/steering/source-files.md` (new `res/font/`, `ic_ziggurat_emblem.xml`, `Font.kt`/`lerpArgb`
   helper, `SobTypographyTest`), `docs/steering/structure.md` if a new module lands, master-plan status
   — **then** `docs/agent/STATE.md` + `docs/agent/RUN_LOG.md` (via `/checkpoint`). Bump `versionCode 24`
   / `versionName 1.0.8`.

**Gate:** `./run-gradle.sh testDebugUnitTest lintDebug assembleDebug` green, **then** on-device feel
sign-off:
- App-wide titles/headers render in Cinzel; body/labels stay Roboto; no missing-glyph/tofu.
- **Digit legibility:** the `CurrencyDashboard` balance and `Home` "{n} steps" hero (both Headline-tier,
  now Cinzel) render with legible, acceptably-aligned digits.
- Biome name on the battle transition overlay is Cinzel; the Home `TierSelector` biome name stays
  Roboto (expected, E3).
- Onboarding: each slide shows its biome gradient; swiping cross-fades smoothly (no flicker/out-of-range
  at first/last slide); title/body legible on all 4 palettes — explicitly check the lightest (Burning
  Sands amber) and the darkest (near-black Celestial) under the scrim; reduced-motion shows static
  gradients + no pulse.
- Slide 1 shows the ziggurat emblem (not the 🏛️ emoji); slides 2–4 keep their emoji.
- Finishing onboarding plays the one-shot gold pulse on the final slide once, then lands on Home; the
  completion flag persists (no re-onboard on relaunch, incl. backgrounding mid-pulse); the permission
  branch (granted / ask / denied-recovery) all still work and reach Home.

A non-trivial decision here (first bundled-asset + font pipeline, onboarding-flow edit) warrants a new
**ADR** (next free number) recording E1/E2 (Cinzel + bundled delivery) and the onboarding-theming +
persist-first completion-beat approach (E10-seq).

---

## 7. Adversarial Review Record (2026-06-15)

Per the CLAUDE.md **Adversarial Review Gate**, this spec passed a multi-agent code-grounded review
before the plan: 6 reviewers (code-grounding · API/framework · fragile-zone/invariant ·
scope-completeness · test-feasibility · consistency/ambiguity), each finding adversarially verified by
a skeptic (default-to-refuted). **~39 findings raised → 13 survived (1 major cluster + minors/nits) →
~26 refuted** as style-only or already-handled.

Surviving findings, all applied above:
- **Completion beat was unbuildable (MAJOR cluster — 5 converging findings).** Bundle C ships no
  shimmer/particle effect (only a text chip + a scale pulse), and the event-driven `ClaimCelebration`
  would be unmounted by the navigation `finish()` triggers. Redefined E10 as a **`PurchasePulse`-style
  gold scale pulse on the final slide**, played **before** navigation via the **persist-first /
  pulse / then-navigate** sequence (E10-seq, §4.5, §4.6); removed all "shimmer/particle/no-new-infra"
  wording and corrected the §2 ground-truth mischaracterization.
- **"Biome names get Cinzel for free" was over-broad (minor).** True only for the battle transition
  overlay; the Home `TierSelector` (`titleMedium`) and Help/notification biome names stay Roboto.
  Narrowed §1/§2/E3 and added a ground-truth row enumerating all biome-name render sites.
- **Cinzel blast radius understated (minor).** Added a §2 enumeration of all 9 Display/Headline
  consumers, flagged the `CurrencyDashboard` numeric balance specifically, and added a digit-legibility
  check to the §6 sign-off.
- **CELESTIAL_GATE is near-black, not "brightest" (minor).** Fixed E5's rationale to journey escalation
  + destination/gold-gate symbolism; E9 now requires the scrim-contrast check on the dark Celestial
  palette and tunes the scrim for the *lightest* palette (Burning Sands).
- **Scrim was hand-wavy (minor).** Locked `Color.Black` @ alpha 0.45, the measured text colors (Ivory
  title / sandstone body), and the WCAG targets (3:1 large / 4.5:1 normal); fixed the ivory-vs-sandstone
  and Frozen-sky-vs-ground factual slips.
- **No guard on `displayMedium`/`displayLarge` (minor).** Added `SobTypographyTest` (§5 #3) so the
  latent fallback can't silently regress.
- **`currentPageOffsetFraction` is signed (minor).** Pinned neighbor-by-sign + `[0,lastIndex]` clamp +
  `t = abs(offset)` edge guards (E8/§4.5) and added a signed-offset test case (§5 #2).
- **Nits applied:** dropped the redundant `clearAndSetSemantics {}` on the `Image` (kept
  `contentDescription = null`; E13); pinned slide-1 `icon = "🏛️"` as an inert fallback (E12); pinned the
  `lerpArgb` home (`presentation/ui/`) and the ziggurat viewport (108×108); fixed the
  `Int.MAX_VALUE`/KDoc-line citation precision.

Refuted (~26): style-only path/citation nits, the "double-source reducedMotion" non-issue, the
"isComingSoon" / slide-count items the spec already handled, and over-specification findings the
spec deliberately defers to the plan. No unaddressed critical/major findings remain — cleared to
advance to the implementation plan.
