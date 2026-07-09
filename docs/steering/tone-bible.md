# Tone Bible — Steps of Babylon

The single reference for the game's **written voice**. Companion to `docs/steering/style-bible.md` (the
visual reference). Authored for #425 (#391 free / code-drawable lane).

> **Scope.** This is a *consistency guide for copy quality*, not a source of new strings. The app is 100%
> locale-ready and every user-facing string already resolves via `stringResource(...)` (i18n #34, ADR-0014).
> Use this when writing or revising any player-facing text — string resources, notifications, store copy,
> onboarding — so the voice stays coherent. It does **not** license hardcoded literals (see
> `ComposeHardcodedStringTest` + the i18n contract).

See also: GDD §1 (fantasy), §6.3 (biome narrative), §12.3 (notifications).

---

## 1. The voice in one line

**Grounded, encouraging, quietly mythic.** You are the architect of Babylon; the game is your calm,
motivating companion — never a drill sergeant, never a slot machine. Every word should reinforce the core
fantasy: *your real-world steps build an ancient wonder.*

## 2. Voice pillars

| Pillar | Do | Don't |
|---|---|---|
| **Earned, not given** | Frame progress as the player's physical achievement ("You walked 8,000 steps"). | Imply free/passive gains, hype, or luck ("Claim your free reward!"). |
| **Mythic, lightly** | Use Babylonian/Mesopotamian flavour as seasoning (ziggurat, biome names, Ishtar/Nergal/Anu proper nouns). | Purple prose, faux-archaic grammar ("thou hast"), lore dumps. |
| **Clear first** | Say the useful thing plainly; a UI label is a label. | Sacrifice clarity for theme — a button must read as its action. |
| **Warm & motivating** | Encourage the next walk ("2,000 steps from Chain Lightning"). | Guilt, dark-pattern urgency, fake scarcity, FOMO. |
| **Respectful of the body** | Inclusive of all activity (Activity Minute Parity — cycling/rowing count). | Assume everyone runs; ableist "get off the couch" framing. |

## 3. Register & mechanics

- **Person:** address the player as **"you"**; the ziggurat is **"your ziggurat"** (lowercase unless it
  starts a sentence). Avoid first-person from the game.
- **Tense:** present/near-future for prompts ("Tap to battle"), past for achievements ("You reached Wave 87").
- **Reading level:** plain, ~grade 6–8. Short sentences. One idea per line in UI.
- **Numbers:** always group thousands (`8,000`, not `8000`) — matches `formatCurrency` (`Locale.US` grouping
  in code; localized at display). Spell out only in prose where a figure would jar.
- **Capitalization:** game nouns that are defined systems are **Title Case** proper nouns — Steps, Cash,
  Gems, Power Stones, Workshop, Labs, Cards, Ultimate Weapons, Rapid Fire, Supply Drop, the five biome names.
  Generic words stay lowercase (wave, round, enemy, upgrade).
- **Currency glyphs:** rendered by `CurrencyDisplay` (#160) — copy refers to the currency by name, never
  bakes in an emoji/glyph.

## 4. The five biomes (naming + mood)

Use the full proper name on first reference; the definite article is part of it. Mood words to echo:

| Biome | Voice mood |
|---|---|
| The Hanging Gardens | verdant, welcoming, the beginning of the journey |
| The Burning Sands | harsh, sun-scoured, endurance |
| The Frozen Ziggurats | stark, crystalline, howling cold |
| The Underworld of Kur | oppressive, shadowed, the descent |
| The Celestial Gate | cosmic, luminous, the ascent's summit |

## 5. Copy patterns by surface

- **Buttons / labels:** the action, Title Case where it's a defined noun ("Start Battle", "Upgrade",
  "Claim"). No trailing punctuation.
- **Notifications (GDD §12.3):** warm, specific, actionable. Good: *"You're 2,000 steps away from upgrading
  Chain Lightning!"* / *"New personal best! Wave 87 in The Burning Sands!"* Never nagging or guilt-based.
- **Supply Drops:** thematic + celebratory, one-tap claim ("A caravan left supplies on your path").
- **Onboarding:** explain-only, never over-promises; never implies Steps come from anything but movement
  (preserves the hard invariant + ADR-0021 explain-only rule).
- **Errors:** plain, blameless, actionable ("Couldn't load your stats. Retry.") — pairs with the #194
  error-state pattern.
- **Store:** honest, cosmetic-framed; no pay-to-win language (Steps are never purchasable — hard rule).

## 6. Forbidden / caution words

- **Never imply passive/free Steps.** No "idle earnings", "free steps", "auto-collect", "while you sleep".
  Steps come only from real movement (the one sanctioned exception — the daily-capped battle-step reward —
  is framed as a reward for *active play*, never passive generation; ADR-0003).
- **No dark-pattern urgency:** "Hurry!", "Last chance!", "Don't miss out!", fake countdowns.
- **No pay-to-win framing** anywhere near Steps.
- **Inclusive-language rule** applies to all copy (see the global builder-context guide): avoid
  master/slave, whitelist/blacklist, etc.
- **No faux-archaic English** ("thee", "hark") — mythic flavour comes from *nouns and imagery*, not grammar.

## 7. Localization note

Copy is authored in `values/strings.xml` (source of truth) and mirrored per-locale (`values-es/`, …). When
writing English, keep it **translation-friendly**: avoid idioms that won't localize, keep placeholders
(`%1$s`, `%1$d`) explicit and ordered, and remember plurals use `one`/`other` only (the
`LocaleCompletenessTest` guard requires identical quantity-item sets — ADR-0014). Proper nouns
(`app_name` = "Steps of Babylon", biome names) stay consistent across locales.

---

## Maintenance

- This is a *quality* guide, not a gate — the machine-enforced rules are `ComposeHardcodedStringTest`
  (no new hardcoded prose), `LocaleCompletenessTest` (locale parity), and the i18n contract (ADR-0014).
- When adding player-facing copy, read §2 pillars + §6 forbidden words first; keep the art/tone pair coherent
  with `style-bible.md`.
