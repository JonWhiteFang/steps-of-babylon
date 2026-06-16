# ADR-0024: Custom display font + onboarding biome theming + persist-first completion beat (Bundle E, #164)

**Status:** Accepted — 2026-06-15. Implemented on `feat/164-look-and-feel-bundle-e` (pre-PR; v1.0.8 / versionCode 24).

## Context

Bundle E (#164) is the last of the five A–E look-&-feel waves off the 2026-06-12 UX review. Unlike A–D
(pure presentation, confined to `presentation/`), it required the project's **first bundled font and art
assets** (touching `res/`) and edits to the **onboarding flow** — a documented fragile zone (ADR-0021).
Three things needed deciding beyond "what it looks like": (1) how to deliver a custom display font; (2)
how onboarding slides carry per-biome theming without breaking the JVM-testable, Android-free slide
model; and (3) how to add a "completion flourish" without violating the onboarding gating/navigation
contract.

## Decision

1. **Custom font = Cinzel (SIL OFL 1.1), bundled statically in `res/font/`, applied to Display + Headline
   tiers only.** Two static weights (Regular 400, Bold 700) as `cinzel_*.ttf` + a `Cinzel` `FontFamily`
   in `Type.kt`; every Cinzel'd tier is `FontWeight.Bold`. Body/Title/Label stay Roboto for dense-text
   legibility. Also added the previously-missing `displayMedium`/`displayLarge` tokens (closing a latent
   gap where `OnboardingScreen` consumed an undefined `displayMedium` and silently fell back to the
   Material3 stock style), guarded by a pure-JVM `SobTypographyTest`.
   - **The font license (`OFL.txt`) lives at the repo root `licenses/OFL-Cinzel.txt`, NOT in `res/font/`.**
     AAPT's resource merger only accepts `.xml/.ttf/.ttc/.otf` under `res/font/` and fails
     `mergeDebugResources` on any other file. (Discovered at build time during execution — neither the
     spec nor plan adversarial review caught it.)

2. **Onboarding per-slide theming via two PURE fields on `OnboardingSlide`:** `biome: Biome?` (the domain
   enum, reused as the gradient source via `BiomeTheme.forBiome`) and `art: OnboardingArt?` (a marker enum,
   not an `@DrawableRes Int`, so the model stays Android-free and the JVM `OnboardingContentTest` keeps
   working). The 4 slides map to a biome journey — Hanging Gardens → Burning Sands → Frozen Ziggurats →
   Celestial Gate — deliberately **skipping** Underworld of Kur (keeps the journey reading as ascent). The
   gradient is a single shared layer behind the `HorizontalPager`, cross-faded on the signed pager offset
   via pure `lerpArgb` + `crossfadeNeighborIndex` (`presentation/ui/ColorLerp.kt`, JVM-tested); reduced-
   motion → static per-page gradient. A translucent black scrim (alpha 0.45) behind the text holds WCAG-AA
   contrast across all four palettes. Slide 1's 🏛️ emoji is replaced by a flat vector ziggurat emblem
   (`ic_ziggurat_emblem.xml`).

3. **Completion beat = persist-first → pulse → navigate, reusing `PurchasePulse`.** `finish()` calls
   `completeOnboarding()` FIRST and unconditionally (persists the device-local gating flag), guards re-taps
   with `if (finishing) return`, then triggers a `rememberPulse()` one-shot scale pulse on the final-slide
   icon + the two `finish()` CTAs; `onFinished()` fires exactly once from a `LaunchedEffect(finishing)`
   (immediately under reduced-motion, else after a ~450ms beat). This keeps the gating/nav contract
   (ADR-0021) intact: navigation is never gated on the animation, and the flag is already persisted, so
   backgrounding mid-pulse cannot re-onboard. The branch order (granted → !permissionAsked → denied) is
   unchanged; the "Enable step counting" button (which calls `onEnableStepCounting`, not `finish()`) does
   not pulse.

## Alternatives considered

- **Font delivery:** (A) **bundle static `.ttf`** — chosen: renders correctly on first launch, offline, no
  Play-Services font-provider dependency or fallback flash, no new gradle dep. (B) Downloadable Google
  Fonts — rejected (provider dependency + first-paint flash). (C) variable font — rejected (only static
  weights are needed; the family ships a variable `Cinzel[wght].ttf` but two static instances are simpler).
- **Font scope:** Display+Headline only (chosen) vs. app-wide vs. marquee-only. Display+Headline maximises
  identity while keeping dense/body text on legible Roboto; the one residual risk (numeric Headline content
  like the currency balance rendering in caps) is an on-device sign-off item, not assumed away.
- **Completion beat:** the spec originally said "gold shimmer reusing Bundle C's celebration" — **rejected
  as unbuildable** (no shimmer/particle infra exists; `ClaimCelebration` is an event-driven text chip that
  navigation would unmount before it played). Reusing the `PurchasePulse` scale pattern, hosted in-screen
  and sequenced before navigation, was the buildable alternative. A "play a welcome beat on Home arrival"
  option was rejected as scope-widening (would touch HomeScreen + MainActivity state).
- **Art-field type:** `@DrawableRes Int` on the model — rejected (pulls Android into the slide list, breaks
  the pure-JVM content test); a marker enum mapped to a drawable in the screen was chosen.

## Consequences

- **Positive:** distinct typographic identity from one-place `Type.kt` swap; onboarding reads as a themed
  journey reusing the existing `BiomeTheme` vocabulary; the slide model stays pure/JVM-testable; the
  completion beat reuses shipped infra (no new effect system) and provably preserves the gating contract;
  +14 JVM tests pin the typography wiring, the colour math, and the slide→biome map.
- **Negative / tradeoffs:** Cinzel re-themes Display+Headline app-wide, including a couple of numeric
  Headline consumers (currency balance, Home steps hero) — caps-style digits are a visual judgment verified
  on-device, not unit-tested. The gradient/scrim/pulse are `@Composable` and not unit-tested (house norm;
  Compose UI tests don't run under Robolectric here, PR-4736) — correctness rests on the on-device sign-off
  + the pure helpers underneath. The font adds ~135 KB of bundled assets.
- **Follow-ups:** on-device feel sign-off (font/no-tofu, digit legibility, cross-fade + reduced-motion,
  scrim contrast on the lightest [Sands] and darkest [Celestial] palettes, emblem, completion beat,
  replay-from-Settings feel) → PR → v1.0.8 release. If the replay-from-Settings beat feels wrong, gate the
  pulse to first-launch only at the `MainActivity` call site (out of the fragile zone).

## Links

- Commits: `a1e4169` (font assets) · `d6ff174` (Type.kt + SobTypographyTest) · `bcf55e8` (license
  relocation fix) · `bbd2081` (ziggurat emblem) · `9c773ca` + `1603e08` (ColorLerp + tests) · `da1e81e`
  (OnboardingSlide fields) · `56f19bd` + `d6ef7de` (OnboardingScreen theming + reindent) · `12f4b3e`
  (doc sweep + v1.0.8 bump).
- Spec: `docs/superpowers/specs/2026-06-15-look-and-feel-bundle-e-design.md` (§7 review record).
- Plan: `docs/superpowers/plans/2026-06-15-look-and-feel-bundle-e.md` (review record).
- Related ADRs: **ADR-0021** (onboarding explain-only / gating contract — this builds on top, unchanged) ·
  ADR-0022 (design tokens + de-emoji — the #159 token layer this font swap rides on) · ADR-0010 (Cards
  copy-based) is unrelated.
