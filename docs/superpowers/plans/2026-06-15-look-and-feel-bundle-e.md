# Look & Feel Bundle E — Identity / Art — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give Steps of Babylon a custom **Cinzel** display font (Display+Headline tiers), turn first-launch onboarding into a **themed biome journey** (per-slide `BiomeTheme` gradient + cross-fade + legibility scrim + a one-shot gold completion pulse), and replace the slide-1 🏛️ emoji with a **vector stepped-ziggurat emblem** — presentation-only, zero domain/economy/concurrency/renderer change.

**Architecture:** Bundle the OFL Cinzel `.ttf`s into a new `res/font/`, define a `Cinzel` `FontFamily`, and apply it in `Type.kt` to Display+Headline (adding the missing `displayMedium`/`displayLarge` tokens). Add a new VectorDrawable emblem in `res/drawable/`. Add two pure fields (`biome: Biome?`, `art: OnboardingArt?`) to `OnboardingSlide`, and in `OnboardingScreen` paint a shared biome gradient behind the pager (cross-faded via a pure `lerpArgb` helper), a scrim behind the text, the emblem on slide 1, and a `PurchasePulse`-style completion pulse sequenced **persist-first → pulse → navigate**.

**Tech Stack:** Kotlin, Jetpack Compose (Material3, Foundation Pager), `androidx.compose.ui.text.font` (static `res/font` family — no new gradle dep), JUnit Jupiter for pure-JVM tests. Build/test via `./run-gradle.sh`.

**Spec:** `docs/superpowers/specs/2026-06-15-look-and-feel-bundle-e-design.md` (commit `b7d3c94`, adversarial-review-hardened — see spec §7).

---

## File Structure

| File | Create/Modify | Responsibility |
|---|---|---|
| `app/src/main/res/font/cinzel_regular.ttf` | Create (binary asset) | Cinzel 400 weight (acquired from Google Fonts). |
| `app/src/main/res/font/cinzel_bold.ttf` | Create (binary asset) | Cinzel 700 weight. |
| `app/src/main/res/font/OFL.txt` | Create | SIL OFL 1.1 license text for the bundled Cinzel font. |
| `app/src/main/java/com/whitefang/stepsofbabylon/presentation/ui/theme/Type.kt` | Modify | Define `Cinzel` `FontFamily`; add `displayMedium`/`displayLarge`; apply Cinzel to Display+Headline; update KDoc. |
| `app/src/test/java/com/whitefang/stepsofbabylon/presentation/ui/theme/SobTypographyTest.kt` | Create | Pure-JVM guard: Cinzel'd tiers are non-default; regression guard for the latent displayMedium fallback. |
| `app/src/main/res/drawable/ic_ziggurat_emblem.xml` | Create | VectorDrawable: stepped ziggurat in a gold roundel (replaces slide-1 🏛️). |
| `app/src/main/java/com/whitefang/stepsofbabylon/presentation/ui/ColorLerp.kt` | Create | Pure `lerpArgb(a: Int, b: Int, t: Float): Int` packed-ARGB interpolation for the gradient cross-fade. |
| `app/src/test/java/com/whitefang/stepsofbabylon/presentation/ui/ColorLerpTest.kt` | Create | Pure-JVM tests for `lerpArgb` (endpoints, midpoint, clamp). |
| `app/src/main/java/com/whitefang/stepsofbabylon/presentation/onboarding/OnboardingSlide.kt` | Modify | Add `OnboardingArt` enum + `biome: Biome?` + `art: OnboardingArt?` fields; assign slide biomes + slide-1 art. |
| `app/src/test/java/com/whitefang/stepsofbabylon/presentation/onboarding/OnboardingContentTest.kt` | Modify | Add slide→biome map + single-ZIGGURAT-on-slide-1 assertions. |
| `app/src/main/java/com/whitefang/stepsofbabylon/presentation/onboarding/OnboardingScreen.kt` | Modify | Shared biome gradient behind pager + cross-fade + scrim + emblem render switch + completion pulse (persist-first/pulse/navigate). |
| `CLAUDE.md`, `CHANGELOG.md`, `docs/steering/source-files.md`, `app/build.gradle.kts`, `docs/agent/STATE.md`, `docs/agent/RUN_LOG.md` | Modify | Doc sweep + versionCode bump per the PR Task-List Convention. |

**Build order rationale:** font assets + `Type.kt` + its test first (Task 1–2) so the type system is complete and tested. The vector emblem (Task 3) and the pure `lerpArgb` helper + test (Task 4) are independent leaf pieces. `OnboardingSlide` data + test (Task 5) lands before `OnboardingScreen` (Task 6) so the screen compiles against the new fields. Docs + version bump (Task 7) then STATE/RUN_LOG (Task 8) close out per CLAUDE.md's PR Task-List Convention.

> **Conventions to honour (verified against HEAD):**
> - Pure helpers in `presentation/ui/` are JVM-unit-tested; `@Composable` pieces are not (`EnumDisplayName` + `EnumDisplayNameTest`; `PurchasePulse`'s `@Composable rememberPulse` is visual-only). The biome gradient/scrim/pulse here are visual → on-device sign-off (§5 of spec).
> - Tests use **JUnit Jupiter** (`org.junit.jupiter.api`) and iterate `.entries` where applicable (never hard-code counts).
> - `BiomeTheme.forBiome(biome)` returns ARGB `Int`s; the live precedent for a biome gradient in a Compose screen is `HomeScreen.kt:71,86` (`Brush.verticalGradient(Color(theme.skyColorTop).copy(alpha=…), …)`).
> - The `PurchasePulse` pattern (`PurchasePulse.kt:36-55`): `graphicsLayer` scale keyed off state, `snap()` under reduced-motion, auto-reset via a `LaunchedEffect`. The completion pulse mirrors this.
> - **Do NOT touch** `OnboardingViewModel`, `OnboardingPreferences`, `MainActivity` routing (the `onFinished` block at `MainActivity.kt:318-333` is unchanged — the pulse runs *before* `onFinished()` fires), `Screen.kt`, or any `domain/`/`data/`/battle file.
> - **Font filenames** must be lowercase `[a-z0-9_]` (no hyphens/uppercase) or the build fails.

---

## Task 1: Acquire + bundle the Cinzel font assets

**Files:**
- Create: `app/src/main/res/font/cinzel_regular.ttf`, `app/src/main/res/font/cinzel_bold.ttf`, `app/src/main/res/font/OFL.txt`

No test (binary assets; exercised by Task 2's `Type.kt` references + `SobTypographyTest`).

- [ ] **Step 1: Create the `res/font/` directory and fetch the Cinzel static weights**

Cinzel is SIL OFL 1.1 (free to bundle in the AAB). Download the **static** TTFs (not the variable font) for **Regular (400)** and **Bold (700)** from Google Fonts and place them with Android-resource-legal lowercase names:

```bash
cd /Users/jpawhite/Documents/Claude/steps-of-babylon
mkdir -p app/src/main/res/font
# Fetch the Cinzel family zip from Google Fonts (contains static TTFs + OFL.txt):
curl -L -o /tmp/cinzel.zip "https://fonts.google.com/download?family=Cinzel"
unzip -o /tmp/cinzel.zip -d /tmp/cinzel
# Copy + rename to resource-legal names (paths inside the zip: static/ or top-level):
cp "$(find /tmp/cinzel -iname 'Cinzel-Regular.ttf' | head -1)" app/src/main/res/font/cinzel_regular.ttf
cp "$(find /tmp/cinzel -iname 'Cinzel-Bold.ttf' | head -1)" app/src/main/res/font/cinzel_bold.ttf
cp "$(find /tmp/cinzel -iname 'OFL.txt' | head -1)" app/src/main/res/font/OFL.txt
ls -l app/src/main/res/font/
```

Expected: three files present; the two `.ttf`s are non-empty (typically 30–120 KB each) and `OFL.txt` contains the SIL Open Font License text.

> If `fonts.google.com/download` is unavailable in the environment, fetch the same static TTFs from the Cinzel GitHub source (`github.com/NDISCovers/Cinzel` → `fonts/ttf/`) or have the developer drop the files in manually. The only hard requirements: static TTF (not variable), weights 400 + 700, lowercase filenames, and the OFL text committed alongside.

- [ ] **Step 2: Verify the font files are valid TTFs**

Run: `file app/src/main/res/font/cinzel_regular.ttf app/src/main/res/font/cinzel_bold.ttf`
Expected: each reports `TrueType Font` (or `TrueType font data` / `OpenType font`). If either reports HTML/text, the download failed — re-fetch.

- [ ] **Step 3: Commit the assets**

```bash
git add app/src/main/res/font/cinzel_regular.ttf app/src/main/res/font/cinzel_bold.ttf app/src/main/res/font/OFL.txt
git commit -m "feat(#164): bundle Cinzel display font (OFL 1.1) in res/font/"
```

---

## Task 2: Wire Cinzel into `Type.kt` + guard it (TDD)

**Files:**
- Modify: `app/src/main/java/com/whitefang/stepsofbabylon/presentation/ui/theme/Type.kt`
- Test: `app/src/test/java/com/whitefang/stepsofbabylon/presentation/ui/theme/SobTypographyTest.kt`

The pure typography tokens are guarded by `SobTypographyTest` (TDD). Applying Cinzel is a one-place change in `Type.kt`; the test pins that the Display+Headline tiers carry Cinzel and the missing tokens now exist (closing the latent fallback, spec E4).

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/whitefang/stepsofbabylon/presentation/ui/theme/SobTypographyTest.kt`:

```kotlin
package com.whitefang.stepsofbabylon.presentation.ui.theme

import androidx.compose.ui.text.font.FontFamily
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

/**
 * Pure-JVM guard for the Bundle E (#164) custom-font wiring. Pins that the Display+Headline tiers
 * carry the Cinzel family (the app's identity lever) and that displayMedium/displayLarge are DEFINED
 * with Cinzel — closing the latent gap where OnboardingScreen consumed displayMedium and silently
 * fell back to the Material3 stock style (spec §2, E4). Body/Title/Label tiers must stay Roboto
 * (FontFamily.Default) for legibility (spec E3). Typography + FontFamily are on the unit-test
 * classpath via compose-ui-text (no Android resource needed to compare family identity).
 */
class SobTypographyTest {

    @Test
    fun `display and headline tiers use the Cinzel family`() {
        val cinzelTiers = listOf(
            "displayLarge" to SobTypography.displayLarge.fontFamily,
            "displayMedium" to SobTypography.displayMedium.fontFamily,
            "displaySmall" to SobTypography.displaySmall.fontFamily,
            "headlineLarge" to SobTypography.headlineLarge.fontFamily,
            "headlineMedium" to SobTypography.headlineMedium.fontFamily,
            "headlineSmall" to SobTypography.headlineSmall.fontFamily,
        )
        for ((name, family) in cinzelTiers) {
            assertEquals(Cinzel, family, "$name must use the Cinzel family")
            assertNotEquals(FontFamily.Default, family, "$name must NOT fall back to Roboto")
        }
    }

    @Test
    fun `body and label tiers stay on the default Roboto family`() {
        val robotoTiers = listOf(
            "titleLarge" to SobTypography.titleLarge.fontFamily,
            "titleMedium" to SobTypography.titleMedium.fontFamily,
            "bodyLarge" to SobTypography.bodyLarge.fontFamily,
            "bodyMedium" to SobTypography.bodyMedium.fontFamily,
            "labelLarge" to SobTypography.labelLarge.fontFamily,
        )
        for ((name, family) in robotoTiers) {
            assertEquals(FontFamily.Default, family, "$name must stay Roboto for legibility")
        }
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./run-gradle.sh testDebugUnitTest --tests "com.whitefang.stepsofbabylon.presentation.ui.theme.SobTypographyTest"`
Expected: FAIL — compilation error (`Cinzel` unresolved) and/or `displayMedium`/`displayLarge` resolve to the Material default (`FontFamily.Default`, not `Cinzel`).

- [ ] **Step 3: Define the `Cinzel` family + apply it in `Type.kt`**

Edit `app/src/main/java/com/whitefang/stepsofbabylon/presentation/ui/theme/Type.kt`. Replace the imports block (lines 1-7) and the KDoc + opening of `SobTypography` so the family is defined and the Display+Headline tiers use it.

Replace the import block at the top:

```kotlin
package com.whitefang.stepsofbabylon.presentation.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.whitefang.stepsofbabylon.R
```

Insert the `Cinzel` family definition + update the KDoc (replace lines 9-22, the KDoc through `val SobTypography = Typography(`):

```kotlin
/**
 * Cinzel — the Steps of Babylon display face (Bundle E, #164). Roman inscriptional caps, SIL OFL 1.1,
 * bundled in res/font/. Applied to the Display + Headline tiers + (via headlineLarge) the battle
 * BiomeTransitionOverlay biome name. Only Regular (400) + Bold (700) are bundled; every Cinzel'd tier
 * is FontWeight.Bold, so 700 is what renders (400 is the normal-weight anchor). A future Cinzel tier at
 * a non-bundled weight would silently synthesize — bundle that weight if you add one.
 */
val Cinzel = FontFamily(
    Font(R.font.cinzel_regular, FontWeight.Normal),
    Font(R.font.cinzel_bold, FontWeight.Bold),
)

/**
 * Steps of Babylon type scale.
 *
 * This object centralises the scale: deliberate weights + a touch of tracking on labels/titles for a
 * tighter, more "premium ancient" feel, with comfortable lineHeight for body copy.
 *
 * The Display + Headline tiers carry the custom [Cinzel] display face (Bundle E, #164) — this is the
 * app's typographic identity lever and re-themes every Display/Headline consumer app-wide (screen
 * headers, overlay titles, the Home steps hero, the Currency-dashboard balance, biome names on the
 * battle transition). Body / Title / Label tiers stay Roboto (FontFamily.Default) for dense-text
 * legibility. Swapping a tier's fontFamily here re-themes that tier everywhere from one place.
 */
val SobTypography = Typography(
```

Now set `fontFamily = Cinzel` on the six Display+Headline styles and **add** `displayLarge` + `displayMedium`. Replace the current `displaySmall` block (lines 23-26) with the three Display tokens + keep the three headline blocks but swap their family. The Display+Headline portion of `Typography(` becomes:

```kotlin
    displayLarge = TextStyle(
        fontFamily = Cinzel, fontWeight = FontWeight.Bold,
        fontSize = 57.sp, lineHeight = 64.sp, letterSpacing = (-0.25).sp,
    ),
    displayMedium = TextStyle(
        fontFamily = Cinzel, fontWeight = FontWeight.Bold,
        fontSize = 45.sp, lineHeight = 52.sp, letterSpacing = 0.sp,
    ),
    displaySmall = TextStyle(
        fontFamily = Cinzel, fontWeight = FontWeight.Bold,
        fontSize = 36.sp, lineHeight = 44.sp, letterSpacing = (-0.5).sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = Cinzel, fontWeight = FontWeight.Bold,
        fontSize = 32.sp, lineHeight = 40.sp, letterSpacing = 0.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = Cinzel, fontWeight = FontWeight.Bold,
        fontSize = 28.sp, lineHeight = 36.sp, letterSpacing = 0.sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = Cinzel, fontWeight = FontWeight.Bold,
        fontSize = 24.sp, lineHeight = 32.sp, letterSpacing = 0.sp,
    ),
```

Leave the `titleLarge` … `labelSmall` blocks (the remaining 7 styles) exactly as they are — `fontFamily = FontFamily.Default`.

- [ ] **Step 4: Run the test to verify it passes**

Run: `./run-gradle.sh testDebugUnitTest --tests "com.whitefang.stepsofbabylon.presentation.ui.theme.SobTypographyTest"`
Expected: PASS — both tests green (Display+Headline = Cinzel; Title/Body/Label = Default).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/whitefang/stepsofbabylon/presentation/ui/theme/Type.kt \
        app/src/test/java/com/whitefang/stepsofbabylon/presentation/ui/theme/SobTypographyTest.kt
git commit -m "feat(#164): apply Cinzel to Display+Headline tiers; add displayMedium/Large + guard test"
```

---

## Task 3: Create the vector ziggurat emblem

**Files:**
- Create: `app/src/main/res/drawable/ic_ziggurat_emblem.xml`

No unit test (a static VectorDrawable; referenced by Task 6, verified on-device).

- [ ] **Step 1: Create the VectorDrawable**

Create `app/src/main/res/drawable/ic_ziggurat_emblem.xml` — a stepped ziggurat (4 trapezoid layers + an apex finial) inside a gold roundel, in brand colours. 108×108 authoring grid (launcher-adjacent emblem proportions), flat fills only (no gradients/bitmaps):

```xml
<?xml version="1.0" encoding="utf-8"?>
<!-- Onboarding slide-1 emblem (Bundle E, #164): stepped ziggurat in a gold roundel. Replaces the
     wrong-building 🏛️ temple emoji. Flat-vector, brand colours (Gold #D4A843, Sand #C2B280,
     Ivory #FFF8E7). Rendered at 96.dp in OnboardingScreen; decorative (contentDescription = null). -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">

    <!-- Gold roundel: translucent fill + ring -->
    <path
        android:fillColor="#33D4A843"
        android:pathData="M54,6 a48,48 0 1,0 0.1,0 z" />
    <path
        android:strokeColor="#D4A843"
        android:strokeWidth="4"
        android:fillColor="#00000000"
        android:pathData="M54,8 a46,46 0 1,0 0.1,0 z" />

    <!-- Stepped ziggurat: 4 trapezoid layers, widest at the base, in sand→gold tones -->
    <!-- Base layer -->
    <path
        android:fillColor="#9C7C3E"
        android:pathData="M24,82 L84,82 L78,70 L30,70 z" />
    <!-- Layer 2 -->
    <path
        android:fillColor="#B8964A"
        android:pathData="M33,70 L75,70 L70,59 L38,59 z" />
    <!-- Layer 3 -->
    <path
        android:fillColor="#C2B280"
        android:pathData="M41,59 L67,59 L63,48 L45,48 z" />
    <!-- Top layer -->
    <path
        android:fillColor="#D4A843"
        android:pathData="M48,48 L60,48 L57,39 L51,39 z" />
    <!-- Apex finial (ivory) -->
    <path
        android:fillColor="#FFF8E7"
        android:pathData="M51,39 L57,39 L54,31 z" />
</vector>
```

- [ ] **Step 2: Confirm it compiles (vector drawables are validated at build time)**

Run: `./run-gradle.sh assembleDebug > /tmp/bundle-e-t3.log 2>&1; tail -n 15 /tmp/bundle-e-t3.log`
Expected: BUILD SUCCESSFUL (a malformed `pathData`/vector fails AAPT here).

- [ ] **Step 3: Commit**

```bash
git add app/src/main/res/drawable/ic_ziggurat_emblem.xml
git commit -m "feat(#164): add vector stepped-ziggurat emblem drawable for onboarding slide 1"
```

---

## Task 4: Create the `lerpArgb` cross-fade helper (TDD)

**Files:**
- Create: `app/src/main/java/com/whitefang/stepsofbabylon/presentation/ui/ColorLerp.kt`
- Test: `app/src/test/java/com/whitefang/stepsofbabylon/presentation/ui/ColorLerpTest.kt`

A pure packed-ARGB `Int` interpolation for the gradient cross-fade — pure `kotlin.Int` math, no Compose runtime needed (`BiomeTheme.skyColorTop/Bottom` are `Int`s).

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/whitefang/stepsofbabylon/presentation/ui/ColorLerpTest.kt`:

```kotlin
package com.whitefang.stepsofbabylon.presentation.ui

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Pure-JVM guard for the Bundle E (#164) onboarding gradient cross-fade. lerpArgb interpolates two
 * packed-ARGB Ints per channel; it is the math behind the per-slide biome gradient blend driven by
 * the pager scroll offset (spec E8). No Compose runtime needed — plain Int bit math.
 */
class ColorLerpTest {

    private val opaqueBlack = 0xFF000000.toInt()
    private val opaqueWhite = 0xFFFFFFFF.toInt()

    @Test
    fun `t of 0 returns the first colour`() {
        assertEquals(opaqueBlack, lerpArgb(opaqueBlack, opaqueWhite, 0f))
    }

    @Test
    fun `t of 1 returns the second colour`() {
        assertEquals(opaqueWhite, lerpArgb(opaqueBlack, opaqueWhite, 1f))
    }

    @Test
    fun `t of half is the per-channel midpoint`() {
        // 0x00 -> 0xFF midpoint is 0x7F (127) per channel with truncating rounding.
        val mid = lerpArgb(opaqueBlack, opaqueWhite, 0.5f)
        assertEquals(0xFF, (mid ushr 24) and 0xFF, "alpha")
        assertEquals(0x7F, (mid ushr 16) and 0xFF, "red")
        assertEquals(0x7F, (mid ushr 8) and 0xFF, "green")
        assertEquals(0x7F, mid and 0xFF, "blue")
    }

    @Test
    fun `t is clamped below 0 and above 1`() {
        assertEquals(opaqueBlack, lerpArgb(opaqueBlack, opaqueWhite, -0.5f))
        assertEquals(opaqueWhite, lerpArgb(opaqueBlack, opaqueWhite, 1.5f))
    }

    @Test
    fun `each channel interpolates independently`() {
        // From pure red (FFFF0000) to pure blue (FF0000FF) at t=0.5:
        // red 0xFF->0x00 = 0x7F, blue 0x00->0xFF = 0x7F, green stays 0, alpha stays 0xFF.
        val a = 0xFFFF0000.toInt()
        val b = 0xFF0000FF.toInt()
        val mid = lerpArgb(a, b, 0.5f)
        assertEquals(0xFF, (mid ushr 24) and 0xFF, "alpha")
        assertEquals(0x7F, (mid ushr 16) and 0xFF, "red")
        assertEquals(0x00, (mid ushr 8) and 0xFF, "green")
        assertEquals(0x7F, mid and 0xFF, "blue")
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./run-gradle.sh testDebugUnitTest --tests "com.whitefang.stepsofbabylon.presentation.ui.ColorLerpTest"`
Expected: FAIL — compilation error, `lerpArgb` unresolved (`ColorLerp.kt` not created yet).

- [ ] **Step 3: Create `ColorLerp.kt`**

Create `app/src/main/java/com/whitefang/stepsofbabylon/presentation/ui/ColorLerp.kt`:

```kotlin
package com.whitefang.stepsofbabylon.presentation.ui

/**
 * Linear per-channel interpolation between two packed-ARGB [Int] colours (Bundle E, #164).
 *
 * Pure Int bit-math (no Compose/Android dependency) so it is JVM-unit-testable. Used by the onboarding
 * biome-gradient cross-fade (spec E8): blends the current slide's BiomeTheme sky colour toward the
 * adjacent slide's as the pager scrolls. [t] is clamped to [0,1]; channels are interpolated
 * independently with truncating rounding.
 */
fun lerpArgb(a: Int, b: Int, t: Float): Int {
    val clamped = t.coerceIn(0f, 1f)
    val af = (a ushr 24) and 0xFF
    val rf = (a ushr 16) and 0xFF
    val gf = (a ushr 8) and 0xFF
    val bf = a and 0xFF
    val at = (b ushr 24) and 0xFF
    val rt = (b ushr 16) and 0xFF
    val gt = (b ushr 8) and 0xFF
    val bt = b and 0xFF
    val alpha = (af + (at - af) * clamped).toInt()
    val red = (rf + (rt - rf) * clamped).toInt()
    val green = (gf + (gt - gf) * clamped).toInt()
    val blue = (bf + (bt - bf) * clamped).toInt()
    return (alpha shl 24) or (red shl 16) or (green shl 8) or blue
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./run-gradle.sh testDebugUnitTest --tests "com.whitefang.stepsofbabylon.presentation.ui.ColorLerpTest"`
Expected: PASS — all 5 tests green.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/whitefang/stepsofbabylon/presentation/ui/ColorLerp.kt \
        app/src/test/java/com/whitefang/stepsofbabylon/presentation/ui/ColorLerpTest.kt
git commit -m "feat(#164): add pure lerpArgb helper for onboarding gradient cross-fade"
```

---

## Task 5: Add `biome` + `art` to `OnboardingSlide` (TDD)

**Files:**
- Modify: `app/src/main/java/com/whitefang/stepsofbabylon/presentation/onboarding/OnboardingSlide.kt`
- Test: `app/src/test/java/com/whitefang/stepsofbabylon/presentation/onboarding/OnboardingContentTest.kt`

- [ ] **Step 1: Extend the test (write the new failing assertions)**

Add two tests to `OnboardingContentTest.kt`. Add the imports at the top (after the existing JUnit imports):

```kotlin
import com.whitefang.stepsofbabylon.domain.model.Biome
import org.junit.jupiter.api.Assertions.assertEquals
```

Add these two `@Test` methods inside the class (after the existing `every slide has a title and body` test):

```kotlin
    @Test
    fun `slides map to the biome journey in order, skipping Underworld`() {
        // Bundle E (#164): the 4 slides walk Gardens -> Sands -> Frozen -> Celestial (the destination
        // biome). UNDERWORLD_OF_KUR is intentionally skipped (spec E5). Pin the exact ordered map.
        val expected = listOf(
            Biome.HANGING_GARDENS,
            Biome.BURNING_SANDS,
            Biome.FROZEN_ZIGGURATS,
            Biome.CELESTIAL_GATE,
        )
        assertEquals(expected, OnboardingContent.slides.map { it.biome })
        assertEquals(
            false,
            OnboardingContent.slides.any { it.biome == Biome.UNDERWORLD_OF_KUR },
            "Underworld of Kur must not theme an onboarding slide (spec E5)",
        )
    }

    @Test
    fun `exactly the first slide carries the ziggurat art`() {
        val slides = OnboardingContent.slides
        assertEquals(OnboardingArt.ZIGGURAT, slides.first().art, "slide 1 must carry the ziggurat emblem")
        assertEquals(
            1,
            slides.count { it.art != null },
            "exactly one slide carries art",
        )
    }
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./run-gradle.sh testDebugUnitTest --tests "com.whitefang.stepsofbabylon.presentation.onboarding.OnboardingContentTest"`
Expected: FAIL — compilation error (`OnboardingSlide` has no `biome`/`art`; `OnboardingArt` unresolved).

- [ ] **Step 3: Add the fields + assignments to `OnboardingSlide.kt`**

Replace the whole of `OnboardingSlide.kt` with the extended model + content (adds `OnboardingArt`, the two pure fields, and the per-slide biome + slide-1 art):

```kotlin
package com.whitefang.stepsofbabylon.presentation.onboarding

import com.whitefang.stepsofbabylon.domain.model.Biome

/** Decorative art slot for a slide (Bundle E, #164). Pure marker — OnboardingScreen maps it to an
 *  @DrawableRes. Kept out of the model (not an @DrawableRes Int / ImageVector) so the slide list
 *  stays Android-free and JVM-testable. */
enum class OnboardingArt { ZIGGURAT }

/**
 * One tutorial slide. Pure Kotlin (emoji icon, not an ImageVector) so the content list is
 * JVM-testable without Android. [isPermissionPrimer] marks the final slide that owns the
 * activity-recognition permission ask. [biome] supplies the per-slide background gradient (Bundle E,
 * #164) via BiomeTheme; [art] overrides the emoji [icon] with a vector asset (slide 1's ziggurat).
 * Both new fields are pure (a domain enum / a marker enum), preserving JVM-testability.
 */
data class OnboardingSlide(
    val icon: String,
    val title: String,
    val body: String,
    val isPermissionPrimer: Boolean = false,
    val biome: Biome? = null,
    val art: OnboardingArt? = null,
)

/** Canonical first-launch tutorial content. Final slide is the permission primer. */
object OnboardingContent {
    val slides: List<OnboardingSlide> = listOf(
        OnboardingSlide(
            icon = "🏛️",
            title = "Walk to power your ziggurat",
            body = "Every real step you take earns Steps — the permanent currency that " +
                "fuels everything. Steps are earned only by walking.",
            biome = Biome.HANGING_GARDENS,
            art = OnboardingArt.ZIGGURAT,
        ),
        OnboardingSlide(
            icon = "🔨",
            title = "Spend Steps in the Workshop",
            body = "Permanent upgrades make your tower stronger across three categories: " +
                "Attack, Defense, and Utility.",
            biome = Biome.BURNING_SANDS,
        ),
        OnboardingSlide(
            icon = "⚔️",
            title = "Send it into battle",
            body = "Your ziggurat auto-battles waves of enemies. Survive, climb tiers, and " +
                "unlock new biomes.",
            biome = Biome.FROZEN_ZIGGURATS,
        ),
        OnboardingSlide(
            icon = "👣",
            title = "Enable step counting",
            body = "To turn your real-world steps into Steps, we need activity-recognition " +
                "permission. Notifications are optional. Then go for a walk to earn your first Steps!",
            isPermissionPrimer = true,
            biome = Biome.CELESTIAL_GATE,
        ),
    )
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./run-gradle.sh testDebugUnitTest --tests "com.whitefang.stepsofbabylon.presentation.onboarding.OnboardingContentTest"`
Expected: PASS — all 4 tests green (2 existing + 2 new).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/whitefang/stepsofbabylon/presentation/onboarding/OnboardingSlide.kt \
        app/src/test/java/com/whitefang/stepsofbabylon/presentation/onboarding/OnboardingContentTest.kt
git commit -m "feat(#164): add per-slide biome + ziggurat art fields to OnboardingSlide"
```

---

## Task 6: Theme `OnboardingScreen` — gradient, cross-fade, scrim, emblem, completion pulse

**Files:**
- Modify: `app/src/main/java/com/whitefang/stepsofbabylon/presentation/onboarding/OnboardingScreen.kt`

No new unit test (`@Composable`; the visual behaviour is on-device sign-off per spec §5). The pure
pieces it consumes (`lerpArgb`, the slide→biome map) are already tested in Tasks 4–5.

- [ ] **Step 1: Add the new imports**

Add these imports alongside the existing ones at the top of `OnboardingScreen.kt`:

```kotlin
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.zIndex
import com.whitefang.stepsofbabylon.R
import com.whitefang.stepsofbabylon.domain.model.Biome
import com.whitefang.stepsofbabylon.presentation.battle.biome.BiomeTheme
import com.whitefang.stepsofbabylon.presentation.ui.lerpArgb
import kotlin.math.abs
import kotlin.math.sign
```

(`Box`, `fillMaxSize`, `background`, `HorizontalPager`, `rememberPagerState`, `clearAndSetSemantics`
are already imported in this file.)

- [ ] **Step 2: Add the gradient + cross-fade helpers (file-private, top-level)**

Add these top-level helpers at the end of `OnboardingScreen.kt` (after the `OnboardingScreen`
composable). They resolve a slide's biome sky colours and compute the cross-faded gradient:

```kotlin
/** A slide's biome sky colours as packed-ARGB Ints, or null when the slide has no biome. */
private fun slideSky(biome: Biome?): Pair<Int, Int>? =
    biome?.let { BiomeTheme.forBiome(it).let { t -> t.skyColorTop to t.skyColorBottom } }

/**
 * The cross-faded (top, bottom) gradient colours for the current pager position. Blends the settled
 * slide's biome sky toward the neighbour the pager is dragging toward, using the signed offset
 * (spec E8): neighbour = currentPage + sign(offset), clamped to [0, lastIndex]; t = abs(offset).
 * Falls back to the settled slide's colours at the ends (no neighbour) or when a slide has no biome.
 */
private fun crossfadedSky(
    slides: List<OnboardingSlide>,
    page: Int,
    offset: Float,
): Pair<Color, Color>? {
    val current = slideSky(slides[page].biome) ?: return null
    val neighbourIndex = (page + sign(offset).toInt()).coerceIn(0, slides.lastIndex)
    val neighbour = slideSky(slides[neighbourIndex].biome) ?: current
    val t = abs(offset).coerceIn(0f, 1f)
    val top = lerpArgb(current.first, neighbour.first, t)
    val bottom = lerpArgb(current.second, neighbour.second, t)
    return Color(top) to Color(bottom)
}
```

- [ ] **Step 3: Wire the completion-pulse state + persist-first sequencing**

Inside `OnboardingScreen`, replace the existing `finish()` function (lines 72-75):

```kotlin
    fun finish() {
        viewModel.completeOnboarding()
        onFinished()
    }
```

with the persist-first / pulse / navigate sequence (spec E10-seq):

```kotlin
    // Completion beat (spec E10-seq): persist the gating flag FIRST and unconditionally, then drive a
    // one-shot pulse, then navigate via the LaunchedEffect below. Navigation is never gated on the
    // animation, and the flag is already persisted, so backgrounding mid-pulse cannot re-onboard.
    var finishing by remember { mutableStateOf(false) }
    fun finish() {
        viewModel.completeOnboarding()  // (1) persist — first, unconditional
        finishing = true                // (2) drive the pulse
    }
    LaunchedEffect(finishing) {
        if (finishing) {
            if (!reducedMotion) kotlinx.coroutines.delay(FINISH_PULSE_MS)
            onFinished()                // (3) guaranteed exactly once; immediate under reduced-motion
        }
    }
    // One-shot scale pulse on the final-slide content (PurchasePulse pattern). 1f when idle.
    val finishScale by animateFloatAsState(
        targetValue = if (finishing) FINISH_PULSE_SCALE else 1f,
        animationSpec = if (reducedMotion) snap() else tween(FINISH_PULSE_MS.toInt()),
        label = "onboardingFinishPulse",
    )
```

Add the two constants at the top level of the file (next to the gradient helpers from Step 2):

```kotlin
private const val FINISH_PULSE_MS = 450L
private const val FINISH_PULSE_SCALE = 1.12f
```

- [ ] **Step 4: Paint the gradient + scrim behind the pager**

Replace the root `Surface(Modifier.fillMaxSize()) {` (line 77) and the `Column` opening so the biome
gradient is a single shared layer behind everything. Replace lines 77-78:

```kotlin
    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(24.dp)) {
```

with a `Box` that paints the cross-faded gradient, then the `Surface`/`Column` on top (transparent so
the gradient shows through):

```kotlin
    val sky = if (reducedMotion) {
        slideSky(slides[pagerState.currentPage].biome)?.let { Color(it.first) to Color(it.second) }
    } else {
        crossfadedSky(slides, pagerState.currentPage, pagerState.currentPageOffsetFraction)
    }
    Box(Modifier.fillMaxSize().then(
        if (sky != null) {
            Modifier.background(Brush.verticalGradient(listOf(sky.first, sky.second)))
        } else Modifier
    )) {
        Surface(Modifier.fillMaxSize(), color = Color.Transparent) {
            Column(Modifier.fillMaxSize().padding(24.dp)) {
```

This adds **one** extra closing brace at the end of the composable (the new `Box`). At the very end of
`OnboardingScreen`, after the existing final `}` that closes the `Surface`'s `Column`, the structure is
now `Column }`, `Surface }`, **`Box }`** — add the one extra `}` to close the new `Box`. (Verify by
building in Step 7; an unbalanced brace fails compilation immediately.)

- [ ] **Step 5: Add the legibility scrim + swap the icon slot for the emblem**

In the per-page `Column` (lines 91-113), the slide renders an icon `Text`, the title, and the body. Wrap
the title+body in a scrim and switch the icon to the emblem when `slide.art != null`. Replace the icon
`Text(...)` block (lines 99-103):

```kotlin
                    Text(
                        slide.icon,
                        style = MaterialTheme.typography.displayMedium,
                        modifier = Modifier.clearAndSetSemantics {},
                    )
```

with the art/emoji switch (the emblem also carries the completion pulse on the final slide, since the
finish CTA lives there — but a non-art slide's emoji pulses too if it's the primer):

```kotlin
                    if (slide.art != null) {
                        Image(
                            painter = painterResource(artDrawable(slide.art)),
                            contentDescription = null,
                            modifier = Modifier
                                .size(96.dp)
                                .graphicsLayer(scaleX = finishScale, scaleY = finishScale),
                        )
                    } else {
                        Text(
                            slide.icon,
                            style = MaterialTheme.typography.displayMedium,
                            modifier = Modifier
                                .graphicsLayer(scaleX = finishScale, scaleY = finishScale)
                                .clearAndSetSemantics {},
                        )
                    }
```

Then wrap the existing title `Text` + body `Text` (lines 105-112) in a scrim `Box`. Replace those two
`Text`s and the `Spacer` between them with:

```kotlin
                    Box(
                        Modifier
                            .clip(MaterialTheme.shapes.large)
                            .background(Color.Black.copy(alpha = 0.45f))
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(slide.title, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
                            Spacer(Modifier.height(16.dp))
                            Text(
                                slide.body,
                                style = MaterialTheme.typography.bodyLarge,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
```

(`clip` is already imported; `MaterialTheme.shapes` is available via the existing `MaterialTheme`
import. The scrim is non-interactive — no `clickable`/`pointerInput`.)

- [ ] **Step 6: Add the `artDrawable` mapper**

Add this file-private mapper at the end of `OnboardingScreen.kt` (next to the gradient helpers):

```kotlin
/** Maps an [OnboardingArt] marker to its drawable resource (kept screen-local so the model stays
 *  Android-free). */
@androidx.annotation.DrawableRes
private fun artDrawable(art: OnboardingArt): Int = when (art) {
    OnboardingArt.ZIGGURAT -> R.drawable.ic_ziggurat_emblem
}
```

- [ ] **Step 7: Build + lint to confirm it compiles cleanly**

Run: `./run-gradle.sh testDebugUnitTest lintDebug > /tmp/bundle-e-t6.log 2>&1; tail -n 25 /tmp/bundle-e-t6.log`
Expected: BUILD SUCCESSFUL; no unresolved-reference / brace-balance errors; lint clean (no new `HardcodedText` — the only literals are the existing slide copy, unchanged).

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/whitefang/stepsofbabylon/presentation/onboarding/OnboardingScreen.kt
git commit -m "feat(#164): onboarding biome gradient + cross-fade + scrim + ziggurat emblem + finish pulse"
```

---

## Task 7: Sync current-state docs + bump version (PR Task-List Convention step 1)

**Files:**
- Modify: `docs/steering/source-files.md`, `CLAUDE.md`, `CHANGELOG.md`, `app/build.gradle.kts`

- [ ] **Step 1: Add the new files to the source-file index**

In `docs/steering/source-files.md`, add these lines in the `presentation/ui/` block (after the
`ClaimCelebration.kt` line ~295, before `Rarity.kt`):

```
presentation/ui/ColorLerp.kt                       # Pure lerpArgb(a,b,t: packed-ARGB Int) per-channel colour interpolation, t clamped [0,1]. Backs the onboarding biome-gradient cross-fade (#164, Bundle E). JVM-tested (ColorLerpTest).
```

Update the `Type.kt` line (~298) to reflect Cinzel:

```
presentation/ui/theme/Type.kt                      # SobTypography — deliberate Material3 type scale (weights/tracking/lineHeight). Display+Headline tiers + displayMedium/Large carry the custom Cinzel display face (#164, Bundle E, res/font/, OFL); Title/Body/Label stay Roboto. Wired via StepsOfBabylonTheme. Guarded by SobTypographyTest.
```

Update the two onboarding lines (~325, ~327):

```
presentation/onboarding/OnboardingSlide.kt             # Pure-Kotlin slide model + canonical 4-slide OnboardingContent list (final slide is the permission primer). +biome: Biome? (per-slide BiomeTheme gradient) + art: OnboardingArt? (slide-1 ziggurat emblem) — both pure, JVM-testable (#164).
presentation/onboarding/OnboardingScreen.kt            # First-launch tutorial carousel (HorizontalPager): swipeable slides, Skip→primer, permission-primer final slide w/ granted/ask/denied states + Settings recovery. Per-slide biome sky-gradient (cross-faded via lerpArgb on pager offset; static under reduced-motion) + text scrim + slide-1 vector ziggurat emblem + one-shot completion pulse (persist-first → pulse → navigate, spec E10-seq) (#164, Bundle E).
```

Add a `res/font/` + drawable note in the `res/` section (near the `values/colors.xml` line ~345):

```
font/cinzel_{regular,bold}.ttf + OFL.txt           # Bundled Cinzel display font (SIL OFL 1.1), the app's custom display face (#164, Bundle E). Referenced by the Cinzel FontFamily in Type.kt.
drawable/ic_ziggurat_emblem.xml                    # Vector stepped-ziggurat emblem (gold roundel) — onboarding slide-1 icon, replaces the 🏛️ temple emoji (#164, Bundle E).
```

(Add `SobTypographyTest` + `ColorLerpTest` to the test index if the file enumerates tests; otherwise the source-file lines above suffice.)

- [ ] **Step 2: Update the headline test count in CLAUDE.md**

Bundle E adds **2** (`SobTypographyTest`) **+ 5** (`ColorLerpTest`) **+ 2** (`OnboardingContentTest`) = **9** new JVM tests → 996 + 9 = **1005**. In `CLAUDE.md`'s Testing section, update the headline count line:

```
- **Headline count: 1005 JVM tests + 9 instrumented tests.** Update this line when it changes; the
```

(Confirm the delta against the actual test run from the Final Verification — if a test was split/merged during implementation, use the real number.)

- [ ] **Step 3: Add a CHANGELOG entry**

In `CHANGELOG.md`, add a Bundle E section under the current-development block (match the Bundle D entry format):

```markdown
### Look & Feel Bundle E (#164) — Identity / Art

Presentation-only. Custom **Cinzel** display font (SIL OFL 1.1, bundled in `res/font/`) applied to the
Display + Headline tiers + biome names on the battle transition; added the missing
`displayMedium`/`displayLarge` tokens (closing a latent Material-fallback gap) with a `SobTypographyTest`
guard. Onboarding is now a themed biome journey: per-slide `BiomeTheme` sky-gradient (Gardens → Sands →
Frozen → Celestial), cross-faded on swipe via a pure `lerpArgb` helper (static under reduced-motion), a
legibility scrim behind the text, and a one-shot gold completion pulse sequenced persist-first → pulse →
navigate (gating/nav contract preserved). The slide-1 🏛️ temple emoji is replaced by a vector
stepped-ziggurat emblem (`ic_ziggurat_emblem.xml`). +9 JVM tests. Zero engine/economy/domain/routing
change. → v1.0.8 / versionCode 24.
```

- [ ] **Step 4: Bump the version**

In `app/build.gradle.kts`, bump `versionCode` 23 → 24 and `versionName` "1.0.7" → "1.0.8":

```kotlin
        versionCode = 24
        versionName = "1.0.8"
```

- [ ] **Step 5: Commit the doc sweep + version bump**

```bash
git add docs/steering/source-files.md CLAUDE.md CHANGELOG.md app/build.gradle.kts
git commit -m "docs(#164): sync source-files, headline count, CHANGELOG; bump v1.0.8 / versionCode 24"
```

---

## Task 8: Update STATE + RUN_LOG (PR Task-List Convention step 2)

**Files:**
- Modify: `docs/agent/STATE.md`, `docs/agent/RUN_LOG.md`

- [ ] **Step 1: Run `/checkpoint` (preferred) or update both files manually**

Preferred: invoke the `checkpoint` skill, which performs the doc-drift sweep, updates `STATE.md`'s
current-objective + headline, and appends a `RUN_LOG.md` entry.

If updating manually:
- `STATE.md`: set the headline + current-objective to "Bundle E (#164) implemented on
  `feat/164-look-and-feel-bundle-e`; pending feel sign-off + PR" and bump the test count to 1005 and
  the version to v1.0.8 / versionCode 24. Note Bundle E is the last of the A–E review bundles.
- `RUN_LOG.md`: append a dated entry summarising the work (Cinzel font on Display+Headline, onboarding
  biome theming + cross-fade + scrim + finish pulse, vector ziggurat emblem; 9 new tests; spec+plan both
  passed the Adversarial Review Gate; first bundled font/art asset).

- [ ] **Step 2: Commit**

```bash
git add docs/agent/STATE.md docs/agent/RUN_LOG.md
git commit -m "docs(#164): checkpoint — Bundle E implemented, pending feel sign-off"
```

---

## Final Verification

- [ ] **Run the full gate**

Run: `./run-gradle.sh testDebugUnitTest lintDebug assembleDebug > /tmp/bundle-e-final.log 2>&1; tail -n 20 /tmp/bundle-e-final.log`
Expected: BUILD SUCCESSFUL; all unit tests green (incl. `SobTypographyTest` 2, `ColorLerpTest` 5, the 2 new `OnboardingContentTest` cases); lint clean; debug APK assembles. Confirm the headline count in CLAUDE.md matches the actual total.

- [ ] **On-device feel sign-off (developer, manual)** — install the debug APK and confirm:
  - **Font:** app-wide titles/headers render in Cinzel (carved-stone caps); body/labels stay Roboto; no missing-glyph/tofu. The biome name on the **battle transition overlay** is Cinzel; the Home **TierSelector** biome name stays Roboto (expected, spec E3).
  - **Digit legibility:** the Currency-dashboard balance and the Home "{n} steps" hero (both Headline-tier, now Cinzel) render with legible, acceptably-aligned digits.
  - **Onboarding gradients:** each slide shows its biome sky-gradient (Gardens green → Sands amber → Frozen blue → Celestial near-black); swiping **cross-fades** smoothly with no flicker or out-of-range artefact at the first/last slide; under reduced-motion the gradient is static (no blend) and there is no finish pulse.
  - **Scrim:** title + body stay legible on all 4 palettes — explicitly check the lightest (Burning Sands amber) and the darkest (Celestial near-black).
  - **Emblem:** slide 1 shows the vector ziggurat emblem (gold roundel), not the 🏛️ emoji; slides 2–4 keep their emoji.
  - **Completion beat:** finishing onboarding plays the one-shot gold pulse on the final slide once, then lands on Home; the completion flag persists (no re-onboard on relaunch, including if you background the app mid-pulse); the permission branch (granted "Start playing" / ask "Enable" / denied "Continue without") all reach Home and the pulse plays on the two `finish()` paths.

---

## Notes for the implementer

- **Do NOT touch** any `domain/`, `data/`, ViewModel, use-case, DAO, or battle/renderer file, nor
  `MainActivity` routing, `OnboardingViewModel`, `OnboardingPreferences`, or `Screen.kt`. This is
  presentation + assets only. The onboarding gating/routing contract is a fragile zone (ADR-0021): the
  completion pulse runs **before** `onFinished()` and `completeOnboarding()` is called first and
  unconditionally — never reorder or gate these on the animation.
- If `./run-gradle.sh` is missing, recreate it per `README.md` (it's gitignored).
- Save every gradle build to a temp log and `tail`/`grep` it (long, verbose output) — the run commands
  above already redirect.
- The font files are **binary assets fetched in Task 1** — they are not authored in code. If the
  download path is blocked, the developer must supply the static Cinzel 400/700 TTFs + OFL.txt manually
  before Task 2 will build (the `R.font.cinzel_*` references fail the build if the files are absent).
- `BiomeTheme` lives in `presentation/battle/biome/` and is already used by `HomeScreen` — importing it
  into onboarding is a presentation→presentation reference (no layering violation; both are
  `presentation/`).
- The completion pulse is keyed to the same `finishScale` on both the emblem (slide 1) and the emoji
  branch (other slides). The pulse is only *triggered* on the final slide (where `finish()` fires), so
  in practice it animates the final slide's `👣` emoji; the shared `graphicsLayer` is harmless on
  non-final slides (scale stays 1f).

---

## Adversarial Review Record

> _Pending._ Per the CLAUDE.md **Adversarial Review Gate**, this plan must pass a multi-agent
> code-grounded review (compile-correctness · TDD-soundness · Compose/Android-API · spec-coverage ·
> line-refs/consistency · fragile-zone safety) with adversarial verification (default-to-refuted)
> before any implementation. Record total / surviving / refuted counts and the substantive fixes here
> once complete.
