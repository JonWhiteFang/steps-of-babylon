# ADR-0022: Design-token layer, ActionBar removal, and de-emoji of UI controls

Status: Accepted (2026-06-12)

## Context

A senior UX/art-direction review (15-agent fan-out + adversarial verification + a live on-device
walkthrough on the emulator) found the app was functionally complete but read as a *prototype*, not a
premium "ancient-Mesopotamian mythic" game. Root causes, all in the presentation layer:

1. **No `themes.xml`** → the single Compose Activity inherited the platform default theme, which ships
   an ActionBar. A redundant black "Steps of Babylon" title bar rendered above the Compose content on
   *every* screen, including the immersive battle screen (where `BattleScreen` even carries a
   `top = 80.dp` HUD pad partly to dodge it). Also risked a white cold-start flash (no `windowBackground`).
2. **No `Typography`/`Shapes` token set** — `Theme.kt` passed only a 5-colour `darkColorScheme`; screens
   hand-rolled `FontWeight.Bold` and hardcoded radii, so hierarchy drifted screen-to-screen.
3. **Emoji used as production UI controls** (📋 ⚙️ ❓ 🏪 ⭐ on Home, 🏪 in Economy) rendering as
   inconsistent multicolour glyphs against the otherwise-monochrome Material icon set.
4. **One real WCAG failure:** `LapisLazuli` (#26619C) as *text* on `DeepBronze` is ~1.45:1 (hard AA
   fail) — used for the most-read number on Home ("Today / {n} steps"). (The review agents over-claimed
   broader contrast failures; independent WCAG math showed Ivory 8.76:1 and Gold/SandStone pass at large
   sizes — only lapis-as-text genuinely fails.)
5. Off-palette **Material green/purple/blue** currency colours clashing with the brand.

## Decision

Introduce a proper **design-token layer** and fix the systemic visual issues, presentation-only:

- **Kill the ActionBar app-wide** via a new `res/values/themes.xml` (`Theme.StepsOfBabylon`, a
  `NoActionBar` parent) with `windowBackground = @color/brand_deep_bronze` (new `res/values/colors.xml`)
  to also remove the cold-start flash; `AndroidManifest` sets `android:theme`.
- **Add token files** `ui/theme/Type.kt` (`SobTypography`) and `ui/theme/Shape.kt` (`SobShapes`), wired
  into `StepsOfBabylonTheme`. Extend `Color.kt` with derived **role tokens** (`LapisLight`,
  `BronzeSurface`, `TextPrimary/Secondary`), **semantic status** tokens (`StatusSuccess/Warning/Danger`),
  and **currency** tokens (`GemColor/PowerStoneColor/StepColor`). `Theme.kt` maps `onPrimary = DeepBronze`
  (dark-on-gold: 2.1→4.2:1) and `surfaceVariant = BronzeSurface`.
- **Lapis-as-text → `LapisLight`** (≈5.3:1); keep deep `LapisLazuli` for fills/containers only.
- **De-emoji interactive controls** → Material vector icons + text labels (the icon set already imported
  in `Screen.kt`). Emoji are retained only in decorative/reward *text* (e.g. Post-Round 🏆, reward lines),
  which is unaffected.
- **Palette-align currency/status colours** to the tokens.

Hide the bottom nav bar on `Screen.Onboarding` (it was bleeding through the first-launch carousel),
matching the existing `Battle` exclusion.

## Alternatives considered

- **A — Leave the ActionBar, pad around it:** rejected; it's the single strongest "unfinished" signal and
  wastes ~12% of every screen, including battle. A theme is the correct, one-file root fix.
- **B — Add a custom display/serif font now:** deferred. A font asset in `res/font/` is the bigger
  identity win but needs art/licensing; the token layer is structured so swapping `fontFamily` later
  re-themes the whole app from one place.
- **C — Replace emoji with bespoke themed icon art:** deferred to a future art pass; the Material vector
  set is a clean, consistent, accessible interim that ships now.
- **D — Trust the review agents' contrast numbers:** rejected; they over-claimed. Used independent WCAG
  math to target only the genuine failure (lapis-as-text).

## Consequences

- **Positive:** immersive full-bleed screens; consistent type/shape rhythm; the brand palette is now the
  single source for colour roles; the most-read Home number passes WCAG; gold buttons (BATTLE, Claim) gain
  dark-on-gold legibility (2.1→4.2:1); future font/colour changes are centralised.
- **Negative / tradeoffs:** `onPrimary = DeepBronze` changes the default content colour on any
  `containerColor = Gold` button that doesn't override it — audited (BATTLE, Supplies Claim/Claim All):
  all are strict contrast improvements. Existing explicit `RoundedCornerShape(...)` call-sites are left
  as-is (no forced migration to `SobShapes`).
- **Follow-ups (not done here):** custom font; UW/Card rarity visual system; a haptics utility;
  Post-Round/claim reward animation + non-placeholder SFX; onboarding per-slide biome theming + a real
  stepped-ziggurat asset to replace the 🏛️ temple emoji; investigate the bottom-nav "restore wrong saved
  screen" repro.

## Links
- Commit(s): (working tree at authoring time — see CHANGELOG `[Unreleased]` + RUN_LOG 2026-06-12 polish entry)
- Related ADRs: ADR-0021 (onboarding explain-only), ADR-0014 (i18n — strings layer this builds on)
