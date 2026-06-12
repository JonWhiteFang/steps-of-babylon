# Steps of Babylon — Look & Feel / UX / Art-Direction Review

**Date:** 2026-06-12
**Scope:** The whole player-facing surface — every Jetpack Compose screen (`presentation/{home,workshop,weapons,cards,labs,missions,stats,economy,store,supplies,settings,help,onboarding}`), the navigation shell (`MainActivity`, `Screen`, `BottomNavBar`), the battle HUD + overlays (`presentation/battle/ui/*`), the Canvas-drawn battle renderer (`battle/biome`, `battle/entities`, `battle/effects`), the design system (`presentation/ui/theme/*`), and assets (`res/`). Evaluated against a target tone of a **premium, cohesive ancient-Mesopotamian mythic** identity that also reads as a tactile idle/TD game.
**Method:** (1) Read the committed memory spine. (2) **Built + installed the debug APK on a live emulator** (API 36, 1080×2400 @ 420 dpi) and walked the full app, capturing real screenshots + UI-hierarchy text dumps of every screen and state. (3) Ran a **15-agent orchestrated fan-out** (one reviewer per screen / cross-cutting dimension) with an **adversarial verify stage** that re-checked every High/Critical finding against the actual code. (4) Computed **WCAG contrast ratios independently** rather than trusting agent estimates. 150 findings catalogued (**7 Critical · 42 High · 65 Medium · 36 Low**); the verify stage **down-rated 7 over-claimed** High/Critical findings.
**Outcome:** The game is functionally complete and mechanically rich, but visually reads as a competent *prototype*, not a shippable premium game — almost entirely fixable in the presentation layer without touching gameplay/economy/concurrency. A first wave of **safe fixes was implemented the same day** (see "Implemented" below and CHANGELOG `[Unreleased]` / RUN_LOG 2026-06-12 polish entry / ADR-0022).

> This file is a historical, point-in-time artifact (per the CLAUDE.md docs convention). It is not maintained after authoring. The implemented subset is recorded in CHANGELOG/RUN_LOG/ADR-0022; remaining recommendations are summarised at the end and should be tracked as issues if picked up.

---

## 1. Executive Summary

- **Functionally complete, visually a prototype.** Every screen is stock Material-3-dark over a single flat brown (`DeepBronze`). The "ancient-Mesopotamian" identity is *named* everywhere and *expressed* almost nowhere outside the battle renderer.
- **The single biggest, cheapest win is killing the leftover platform ActionBar.** Every screen rendered a redundant black **"Steps of Babylon"** title bar above the Compose content (there was *no* `themes.xml`, so the app inherited the default `Theme.Material` with an ActionBar). It wasted the top ~12% of the screen — including the supposedly-immersive battle screen — and was the #1 reason the app looked unfinished. One file fixes it everywhere. *(Implemented.)*
- **The bottom navigation bar bled through the onboarding carousel.** On a fresh install — the literal first 10 seconds — Home/Workshop/Battle/Labs/Stats sat under the tutorial, so the "first-launch" flow looked escapable and cluttered. *(Implemented.)*
- **The design system was a stub.** `Theme.kt` was one `darkColorScheme` over 5 hardcoded colours with **no `Typography`, no `Shapes`, no custom font** — so ad-hoc `FontWeight.Bold` and hardcoded `Color(0xFF…)` were scattered across screens. A token layer is the foundation everything else needs. *(Implemented: tokens; font deferred.)*
- **One real, verified accessibility failure:** `LapisLazuli` (#26619C) as *text* hits **1.45:1 on DeepBronze — a hard WCAG fail**. Used for the Home "Today / {n} steps" headline (the most-looked-at number on the home screen) and as Material `secondary`. *(My WCAG math; the review agents mislabeled which colours fail — Ivory is 8.76:1 pass, Gold 4.19:1 / SandStone 4.41:1 large-text pass. Lapis-as-text is the genuine miss.)* *(Implemented.)*
- **Emoji were used as production UI** (📋 ⚙️ ❓ 🏪 ⭐ 🌙 💎 🏛️ 🔨), rendering as multicolour glyphs next to flat monochrome Material icons. They mostly *do* carry adjacent text labels (so not a screen-reader blocker — adversarial check corrected that over-claim), but they are the loudest "asset-flip" tell. The onboarding "ziggurat" is the **🏛️ Greco-Roman temple emoji** — literally the wrong building.
- **There's a real visual vocabulary to build on.** The battle's `BiomeTheme` (per-biome sky/ground/ziggurat/particle palettes), the in-round upgrade bottom-sheet, and the Post-Round "Round Over" overlay (colour-coded reward lines) are genuinely well-designed — proof the team *can* do tactile themed UI; the menus just haven't caught up. Reward moments outside battle (claims, purchases, milestones) are flat: **zero haptics anywhere in the codebase.**
- **Net:** none of the headline fixes require touching the fragile battle engine/economy/concurrency. They concentrate in `ui/theme/`, a new `themes.xml`, `MainActivity`, and a handful of Compose screens.

---

## 2. Repo Findings

**Stack (confirmed):** Kotlin/JVM 17, `com.whitefang.stepsofbabylon`, min SDK 34 / target 36. Jetpack Compose (Material3) for menus; custom `SurfaceView` + `GameLoopThread` for battle. MVVM, Hilt+KSP, Room+SQLCipher. Build via `./run-gradle.sh`.

**Where look & feel lives:**
- **Design system:** `presentation/ui/theme/Color.kt`, `Theme.kt`. (At review start: no `Typography`/`Shapes`, no `res/values/themes.xml`/`colors.xml`, no `res/font/`.)
- **Screens:** `presentation/{home,workshop,weapons,cards,labs,missions,stats,economy,store,supplies,settings,help,onboarding}` + `navigation/{Screen,BottomNavBar}` + `MainActivity` (NavHost, edge-to-edge, transitions).
- **Battle UI:** `presentation/battle/BattleScreen.kt` (Compose HUD over the SurfaceView) + `battle/ui/*` overlays; `battle/biome/BiomeTheme.kt` (rich per-biome palettes); `battle/effects/*` (perf-tuned particle pool — fragile).
- **Assets:** `res/raw/` 9 `.ogg` (placeholder sine tones per project debt); `res/drawable/` only the 2 launcher vectors; `res/mipmap-anydpi-v26/` adaptive launcher. No content drawables or fonts.
- **Strings:** `res/values/strings.xml` (battle/workshop/onboarding migrated to resources; ~110 other Compose literals still inline — known i18n phase-2 debt).

**Build/test/lint:** `./run-gradle.sh assembleDebug | testDebugUnitTest | lintDebug`. Baseline **973 JVM + 9 instrumented**. `lint { error += "HardcodedText" }` is set but is **XML-only** — it does not flag Compose `Text()`.

**Constraints created by the implementation:**
- **Compose-vs-SurfaceView split:** the battle HUD is Compose laid *over* a Canvas-drawn SurfaceView. HUD/overlay polish is safe Compose work; the rendered entities/biomes/effects are Canvas geometry inside the fragile, lock-guarded engine — higher-risk and mostly out of scope.
- **No theme tokens** ⇒ every screen re-implemented colour/type/shape inline.
- **Missing `themes.xml`** ⇒ default ActionBar + risk of a white launch flash. `BattleScreen`'s `padding(top = 80.dp)` was a workaround for exactly this.

---

## 3. Current Strengths

- **The palette itself is good** — Gold / Lapis / SandStone / DeepBronze / Ivory is authentically "ancient premium," not generic Material. The problem is application, not choice.
- **The battle `BiomeTheme` system is genuinely strong** — five distinct, atmospheric per-biome palettes (sky gradients, ground, 5-stop ziggurat ramps, particle drift). This is the identity the menus lack.
- **In-round upgrade bottom-sheet is the best-built surface:** good contrast, clear category tabs, and an excellent **"Now: 10.0 dmg → 10.2 dmg"** before/after preview with gold cost chips.
- **Post-Round "Round Over" overlay** has real reward hierarchy: 🏆 New Record, purple "+1 Power Stones," green "+9 Steps," clean stat table, clear primary CTA. The template for reward moments elsewhere.
- **Onboarding *copy* is excellent** and correctly teaches walk→spend→battle and "Steps only from walking"; permission primer + denial recovery are thoughtfully engineered.
- **Accessibility groundwork exists:** reduced-motion is honoured in onboarding/transitions/upgrade-pulse; the battle HUD and several controls already set `contentDescription`; onboarding correctly uses `clearAndSetSemantics{}` on decorative emoji.
- **Layout robustness:** battle control row scrolls horizontally + respects `navigationBars` insets; edge-to-edge is enabled.

---

## 4. Main Problems (ranked by severity)

### CRITICAL

**C1 — Redundant platform ActionBar on every screen.** A black "Steps of Babylon" title bar rendered above all Compose content, including Battle. No `res/values/themes.xml`; `AndroidManifest` set no `android:theme`. The `BattleScreen` `padding(top = 80.dp)` existed partly to dodge it. **Fix:** `themes.xml` `NoActionBar` + `windowBackground = DeepBronze`; set `android:theme`. **Fix now.** *(Implemented; pixel-verified.)*

**C2 — Bottom nav bar visible during onboarding.** The tutorial carousel showed the 5-tab bottom bar underneath it. `MainActivity` hid the bar only when `currentRoute == Battle`. **Fix:** also hide for `Screen.Onboarding`. **Fix now.** *(Implemented.)*

**C3 — No Typography token system.** `Theme.kt` passed no `typography`; screens hand-rolled `FontWeight.Bold` + sizes. **Fix:** a deliberate `Typography` (sizes/weights/tracking/lineHeight). **Fix now.** *(Implemented.)*

**C4 — LapisLazuli text fails WCAG (1.45:1).** Used for Home "Today / {n} steps" and `secondary` selected states. **Fix:** a `LapisLight` (#A7C7E7 → 5.29:1) token for lapis-as-text; keep deep Lapis for fills. **Fix now.** *(Implemented.)*

**C5 — No visual identity layer = generic store presentation.** Stock Material components + flat brown everywhere; the (excellent) listing copy promises a polished mythic game the screenshots won't deliver. Cumulative effect of C1/C3 + tokens + de-emoji + Home/Battle polish. **Short-term.** *(Partially implemented.)*

*(The raw agent output also produced two C-level items — "emoji lack labels" and "Gold/SandStone fail WCAG everywhere" — that were **refuted on verification**: emoji rows carry text labels and the bottom-nav `Icon` has a `contentDescription`; Gold/SandStone pass at large-text sizes. The real residue is folded into the H-items below.)*

### HIGH (42 total)

- **Identity/theme:** no `Shapes` tokens (radii scattered 8/16/Circle); no custom font (Roboto, *later*); off-palette Material green/purple/blue currency chips; emoji-as-UI across Home/Labs/Economy/Store/Onboarding; Workshop/Cards/Labs/Help visually flat; in-round upgrades show raw `ENUM_NAME`.
- **Navigation/IA:** secondary screens have **no back/up affordance** (8 of 13 rely on system back only); bottom-nav vanishes without animation on Battle; Workshop→Weapons/Cards depth unsignaled; bottom-nav "Home" tap restored the *wrong* saved screen *(live-device repro: from Cards, tapping Home returned to Cards)*.
- **Clarity/data:** missing thousands separators on big numbers; **chart legend mislabeled** "Activity Minutes" while plotting step-equivalents, and axis labels used `Color.hashCode()` not `.toArgb()` (wrong colour); Cards header showed **"💎 1 / 💎 1 Gems" twice**; biome title lower-cased "gardens"; "1 Gems" pluralization in Store.
- **States/feedback:** **zero haptics in the entire codebase**; button presses lack audio/tactile confirmation; Post-Round rewards are static text (no entrance animation/SFX); claim moments (missions/milestones/supplies) lack celebration; reward SFX not layered.
- **Battle HUD:** top-left HUD is a loose text blob with no container, `top=80.dp` overlapping the status area.
- **Collectibles:** UWs have no rarity/visual identity; equipped-vs-owned + 3-cap is buried; card rarity is thin generic blue/purple/gray.
- **Onboarding:** emoji-only slide icons (wrong-building 🏛️); no per-slide colour/visual progression.
- **a11y residue (real):** a couple of color-only status spots without icon/text; `Icons.Filled.Upgrade` and the Settings delete icon have `contentDescription = null`.

### MEDIUM (65, selected)
Workshop card title/desc go low-contrast at balance 0 because `alpha(0.5f)` dimmed the *whole* card incl. the label *(live-device)*; Home applied the rich BiomeTheme gradient at only 0.3/0.15 alpha so Home looked flat-brown vs the battle's green; Settings screen titled "Notification Settings" (no true settings hub); inconsistent screen-title type sizes; empty-states missing on Cards/Weapons/Workshop-category/Supplies-history; loading states never render an indicator; tier-condition text cramped; pause overlay could use stronger emphasis.

### LOW (36, selected)
Onboarding completion has no celebration; cost-color snaps without animation; purchase pulse (1.05×) nearly invisible; biome transition has no thematic art; help is an unscannable wall of text; safe-area padding not explicitly applied on Home.

---

## 5. Screen-by-Screen Review

*(Captured live on-device. Common to all at review start: redundant ActionBar [C1] and flat-brown background.)*

- **Onboarding (4 slides):** clear copy; nav bar bled through [C2]; emoji icons, the **🏛️ temple is the wrong building**; flat single background; abrupt finish. *Improve:* hide nav (done); themed vectors / stylized ziggurat; per-slide biome tint; small finish flourish.
- **Home + TierSelector:** decent hierarchy; **Lapis "0 steps" headline failed contrast [C4]** (fixed); emoji menu rows (fixed → Material icons); flat-brown (gradient under-applied, raised); "Best Wave" ambiguous (global vs per-tier).
- **Workshop + UpgradeCard:** good cost/level/stat columns; #154 max-disable solid; at balance 0 the whole card dimmed to 0.5α incl. the title (fixed → dim value only).
- **Weapons (UWs):** clear cards/names/costs; no rarity/visual identity; equipped state a tiny ✓; 3-cap buried; no empty state.
- **Cards:** **header double-"💎 Gems" bug** (fixed → owned-count); mostly empty screen, no empty-state (fixed); rarity = thin border color; pack-open a plain AlertDialog.
- **Labs:** uses **🌙 crescent-moon emoji as the Steps currency icon** ("Start (2000 🌙)") — confusing; "Free ⭐" rush has no special treatment; slot row can cramp.
- **Missions:** clean progress bars; claim buttons lack priority/celebration; reward icons emoji; no "requires real walking" context.
- **Stats:** clear sections, ceiling line; **legend mislabeled + axis color via `hashCode()`** (both fixed); no thousands separators (a `NumberFormat` is even initialized but unused).
- **Economy/Currency:** color-coded Gems/Power Stones, login streak, weekly challenge; **off-palette Material green/purple** (fixed); 🏪 emoji rendered as a "24" calendar glyph (fixed → icon).
- **Store:** clean pack list, clear prices, #154-correct disabled states; **"1 Gems" plural bug** (fixed); blue Material gem (fixed); large empty top area.
- **Settings:** titled "Notification Settings"; toggles/volume/replay fine; delete flow lacks destructive-red + haptic; title size inconsistent.
- **Help:** dense wall of text with emoji headers; readable but unstyled.
- **Battle HUD + overlays:** in-round sheet + Post-Round overlay are the best surfaces; **ActionBar ate the top of the immersive screen [C1]** (fixed); HUD a loose text blob, no container; ziggurat = flat rectangles, enemies = tiny red dots (renderer, mostly out of scope); pause overlay emphasis bumped; biome title capitalization (fixed).
- **Loading/empty/win-loss:** loading never shows an indicator; several screens lack empty states; win/loss = the strong Post-Round overlay (keep; add entrance animation + SFX/haptic).

---

## 6. Look & Feel Direction

**Art direction:** lean into the battle renderer's atmosphere across the whole app — carved lapis-and-gold "tablets" lit warmly, not Material cards on brown. Subtle biome-tinted vertical gradients (reuse `BiomeTheme`), carded content on slightly elevated bronze, gold reserved for primary actions/values.

**Color (concrete tokens — keep the 5, add roles):** keep `Gold #D4A843` (primary/CTA), `LapisLazuli #26619C` (fills only), `SandStone #C2B280` (tertiary/large text), `DeepBronze #6B3A2A` (bg), `Ivory #FFF8E7` (body, 8.76:1 ✓). **Add** `LapisLight #A7C7E7` (lapis-as-text, 5.29:1 ✓), `BronzeSurface` (elevated cards), semantic `Success/Warning/Danger`, and palette-aligned currency colours. *(All implemented.)*

**Typography:** a deliberate scale with tighter title weights + tracking now (Roboto); later a display serif/engraved font in `res/font/` for headers/biome names only.

**Shape:** `Shapes(small=8, medium=14, large=20.dp)`. *(Implemented.)*

**Iconography:** replace emoji-as-control with the Material vector set already imported in `Screen.kt`, keeping emoji only in decorative/reward text. Themed currency glyphs are a medium-term art task.

**Animation & feel:** `performHapticFeedback` on key taps; entrance animation + SFX on Post-Round rewards; bigger purchase pulse; pack-open reveal.

**Audio:** replace placeholder sine SFX (known debt); layer a reward sting on claims/records.

**Study:** *Tower of Fortune*, *Random Dice*, *Arknights* (premium mobile-TD hierarchy), *Walkr* (step-game reward loops). **Avoid:** raw Material defaults, emoji-as-icon, flat single-color backgrounds, color-only status.

---

## 7. Prioritised Improvement Plan

**Immediate (1–2 days, safe) — DONE this pass:** ActionBar removal; hide nav on onboarding; Type+Shape tokens; LapisLight contrast fix; verified bugs (Cards header, Stats legend+`toArgb`, biome capitalization, Store plural, separators); de-emoji Home; Workshop value-only dim.

**Short-term (1–2 weeks):** palette-align all currency colours (started); de-emoji Economy/Store/Labs/Cards/Missions; **haptics util** wired to purchase/claim/equip/BATTLE/pause; HUD container scrim + pause veil/"PAUSED"; empty/loading states; raise Home gradient (done); add back affordances on secondary screens.

**Medium-term (1–2 months):** Post-Round + claim celebration animations + layered SFX; UW/Card rarity visual system; onboarding per-slide biome theming + real ziggurat art; replace placeholder audio; custom font.

**Later:** light scheme/Material-You; help search/ToC; biome transition art; safe-area polish.

---

## 8. Concrete Implementation Tasks

| # | Goal | Files | Status |
|---|------|-------|--------|
| T1 | Remove ActionBar | `res/values/themes.xml`+`colors.xml`, `AndroidManifest.xml` | ✅ done |
| T2 | Hide nav on onboarding | `MainActivity.kt` | ✅ done |
| T3 | Type+Shape tokens | `Type.kt`,`Shape.kt`,`Theme.kt` | ✅ done |
| T4 | Lapis contrast | `Color.kt`,`HomeScreen.kt` | ✅ done |
| T5 | Verified bugs | `CardsScreen`,`WalkingHistoryChart`,`BiomeTransitionOverlay`,`StoreScreen`,Economy | ✅ done |
| T6 | De-emoji Home | `HomeScreen.kt` | ✅ done |
| T7 | Workshop legibility | `UpgradeCard.kt` | ✅ done |
| T8 | Currency palette | `CurrencyDashboardScreen`,`StoreScreen`,`Color.kt` | ✅ done |
| T9 | Haptics util | new `Haptics.kt` + call sites | ⬜ recommended |
| T10 | UW/Card rarity visuals | `UltimateWeaponScreen`,`CardsScreen` | ⬜ recommended |
| T11 | Reward animation/SFX | `PostRoundOverlay`, claim flows | ⬜ recommended |
| T12 | Custom font | `res/font/`, `Type.kt` | ⬜ recommended |

*(Maps to Closed-Test Readiness-Gate C/F UX polish in `plan-FORWARD.md`; no existing GitHub issue — candidate new "look-and-feel polish pass".)*

---

## 9. Before / After

- **ActionBar:** *Before:* black "Steps of Babylon" bar steals the top of every screen incl. Battle. *After:* full-bleed content, immersive battle, ~12% more usable height. **Why better:** removes the strongest "unfinished" tell. *(Pixel-verified: ActionBar row #1A1B20 → DeepBronze #6B3A2A.)*
- **Onboarding:** *Before:* tutorial with a live bottom-nav bar under it. *After:* clean, contained first-run.
- **Home "Today" number:** *Before:* `LapisLazuli` 1.45:1 (fails). *After:* `LapisLight`/gold ≥5:1.
- **Currencies:** *Before:* Material green/purple/blue clashing. *After:* gold/lapis/bronze-aligned.
- **Typography:** *Before:* ad-hoc bold/sizes per screen. *After:* one deliberate scale.

---

## 10. Mobile-Specific Checklist

- **Touch targets:** mostly OK (full-width buttons); verify emoji/icon buttons ≥48 dp after de-emoji.
- **Font scaling:** text uses `sp` ✓; verify card heights at 1.3× (add `maxLines`).
- **Contrast:** Ivory 8.76 ✓ / Gold 4.19 / SandStone 4.41 (large ✓) / **Lapis-text 1.45 ✗ → fixed (LapisLight 5.29)**.
- **Safe areas/notches:** edge-to-edge on; battle controls respect nav insets ✓; `windowBackground` added; Home could add inset padding (low).
- **Orientation:** portrait-centric; no lock declared — verify or lock portrait.
- **Scaling across sizes:** captured clean at 1080×2400/420 dpi; battle row scrolls on narrow.
- **VFX/particle pool:** do **not** touch (fragile, perf-tuned).
- **Battery:** foreground step service runs always — avoid always-on menu animations; keep new animation one-shot.
- **a11y:** fix the 2 `contentDescription = null` (Upgrade icon, delete icon); reduced-motion already honoured.
- **Back/gesture nav:** add visible back affordance on secondary screens; investigate the **bottom-nav restore-wrong-screen** repro.
- **Resume mid-battle:** lifecycle pause wired ✓.
- **Haptics:** none today — add.

---

## 11. Implemented This Pass (safe subset)

12 modified + 4 new files, all `presentation/` + `ui/theme/` + `res/`; zero engine/economy/concurrency. **973 JVM tests unchanged, 0 fail; lint + assembleDebug green; key fixes verified on-device.** See CHANGELOG `[Unreleased]`, RUN_LOG 2026-06-12 polish entry, and ADR-0022 for the full record.

- C1 ActionBar removal (`themes.xml`, `colors.xml`, manifest) — pixel-verified.
- C2 nav hidden during onboarding — verified.
- C3 design tokens (`Type.kt`, `Shape.kt`, `Color.kt` roles, `Theme.kt`).
- C4 Lapis-as-text contrast fix (`LapisLight`).
- De-emoji Home + Economy controls → Material icons — verified.
- Palette-aligned currency/status colours (Economy, Store).
- Verified bug fixes: Cards double-Gems header, Stats legend label + `toArgb`, biome-title capitalization, Store "1 Gems" plural, thousands separators, Workshop value-only dim, Cards empty-state + COMMON contrast, Pause veil/title.

## Remaining Recommendations (not implemented)

1. **Custom display font** in `res/font/` applied only to `SobTypography` display/headline + biome names — biggest remaining identity lever (token layer makes it a one-place change).
2. **Haptics utility** (`performHapticFeedback`) wired to purchase/claim/equip/BATTLE/pause — currently zero haptics in the codebase.
3. **Reward celebration:** Post-Round entrance animation + sting; same for claim flows (currently static text / silent removals).
4. **UW + Card rarity visual system:** per-rarity accent border + badge + prominent EQUIPPED chip + "3/3 — unequip one" messaging.
5. **Onboarding per-slide biome theming + real stepped-ziggurat asset** to replace the 🏛️ temple emoji.
6. Investigate the **bottom-nav "restore wrong saved screen"** repro.
