# Plan 24 — Accessibility

**Status:** Deferred (post-v1.0)
**Dependencies:** Plan 18 (Narrative Biome Progression)
**Layer:** `presentation/` — cross-cutting

---

## Objective

Ensure the game is accessible to all players: TalkBack support for all menu screens, audio cues for the battle screen, three color-blind mode palettes, adjustable text size respecting system settings, and rest day encouragement after consecutive high-activity days.

Reference: GDD §17 for accessibility requirements.

---

## Task Breakdown

### Task 1: TalkBack Support for Menus

Audit and update all Compose screens:
- Add `contentDescription` to all icons, buttons, and interactive elements
- Add `semantics` blocks for complex composables (upgrade cards, chart bars)
- Ensure logical focus order via `Modifier.semantics { traversalIndex }`
- Test navigation flow with TalkBack enabled
- Screens: Home, Workshop, Labs, Cards, Stats, Missions, Settings

---

### Task 2: Battle Screen Audio Cues

Create `presentation/battle/audio/BattleAudioCueManager.kt`:
- Audio cues for key battle events (accessible without visual):
  - Wave start: distinct tone
  - Enemy approaching: proximity-based audio
  - Ziggurat taking damage: impact sound
  - Low health warning: urgent repeating tone
  - Wave cleared: success chime
  - Round end: defeat sound
  - UW ready: activation-ready ping
  - Overdrive active: sustained hum
- Uses `SoundPool` for low-latency playback
- Volume respects system media volume

---

### Task 3: Color-Blind Modes

Create `presentation/ui/theme/ColorBlindPalette.kt`:
- Three alternative palettes:
  - Protanopia (red-green, red-weak)
  - Deuteranopia (red-green, green-weak)
  - Tritanopia (blue-yellow)
- Each palette remaps: enemy colors, health bar gradient, upgrade affordability indicators, biome themes, rarity borders
- Applied globally via Compose `CompositionLocal`

Create `presentation/settings/AccessibilitySettings.kt`:
- Color-blind mode selector (Off, Protanopia, Deuteranopia, Tritanopia)
- Persisted in DataStore

---

### Task 4: Adjustable Text Size

Update Compose theme:
- All text styles use `sp` units (already Compose default)
- Verify all screens render correctly at system font sizes: Small, Default, Large, Largest
- Fix any layout overflow or truncation issues at large sizes
- Ensure minimum touch target sizes (48dp) on all interactive elements

---

### Task 5: Rest Day Encouragement

Create `domain/usecase/CheckRestDayRecommendation.kt`:
- After 3+ consecutive days of 10,000+ steps: suggest rest
- Show gentle message: "You've been crushing it! Consider a rest day. Here's a bonus Gem."
- Award 1 bonus Gem for acknowledging the rest suggestion
- Only triggers once per streak, not daily

---

### Task 6: Reduced Motion Support

Update animations:
- Respect `Settings.Global.ANIMATOR_DURATION_SCALE`
- If reduced motion enabled: skip particle effects, simplify transitions
- Battle renderer: reduce particle count, disable ambient particles
- Compose: use `Modifier.animateContentSize()` conditionally

---

## File Summary

```
presentation/battle/audio/
└── BattleAudioCueManager.kt   (new)

presentation/ui/theme/
└── ColorBlindPalette.kt        (new)

presentation/settings/
└── AccessibilitySettings.kt    (new)

domain/usecase/
└── CheckRestDayRecommendation.kt (new)

presentation/ (various screens)
└── (updates for contentDescription, semantics, text sizing)
```

## Completion Criteria

- All menu screens navigable via TalkBack with meaningful descriptions
- Battle audio cues convey key events without visual dependency
- Three color-blind palettes render correctly across all screens
- App renders correctly at all system font sizes without layout breaks
- Rest day encouragement triggers after 3+ consecutive 10k+ days
- Minimum 48dp touch targets on all interactive elements
- Reduced motion setting respected in animations and particles
