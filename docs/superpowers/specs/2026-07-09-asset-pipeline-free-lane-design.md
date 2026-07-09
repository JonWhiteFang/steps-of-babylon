# Design Spec — #391 Asset Pipeline: the free / code-drawable lane

**Date:** 2026-07-09 · **Issue:** #391 (research/backlog epic) · **Status:** spec (light single-agent
review folded in; ultracode OFF, so the multi-agent Adversarial Review Gate was consciously waived by the
developer for this low-risk docs+consolidation plan). · **Scope decision:** free/in-code lane only.

## 0. Context & framing

#391 is a two-pass `/deep-research` **backlog epic** on generating consistent art/audio/store/text assets
with Claude + paid AI tools. Its own headline conclusion: the highest-value, **zero-cost, zero-licensing**
lane is *code-drawable* art governed by a single source of truth — "a stochastic image generator fights
consistency, a Kotlin function does not." This spec scopes **only that lane**.

**Ground truth from the code (verified 2026-07-09):**

- **Visuals are 100% procedural** — the battle renderer draws with Canvas primitives; **zero raster
  sprites** (only rasters are the vector launcher icons + `ic_ziggurat_emblem.xml`).
- **But the "single source of truth" the research assumes does not exist yet.** The palette is fragmented
  across three uncoordinated sites, only one of which has design intent:
  - `presentation/ui/theme/Color.kt` — the curated brand palette (Gold/LapisLazuli/SandStone/DeepBronze/
    Ivory + derived role tokens) **with contrast guards** (`ContrastTest`). The *only* intentional palette.
  - `presentation/battle/biome/BiomeTheme.kt` — 5 biomes × ~9 raw `0xFF…` ARGB ints (~45 hand-tuned values),
    unconnected to the brand palette.
  - `presentation/battle/entities/EnemyEntity.kt` — its own `BASE_COLORS` map (raw Material red/orange/
    purple), independent again; plus `ZigguratEntity.DEFAULT_COLORS`.
- **Audio already ships** (7 `.ogg` SFX + 2 BGM in `res/raw/`) — NOT in this lane.
- **Text is 100% locale-ready + Spanish shipped/native-reviewed (#410 closed)** — no new strings needed.

So the free lane's real work is: **(a) author the missing single-source-of-truth (a style bible), then
(b) make the procedural art derive from it, guarded so it can't re-fragment.** This is a consistency
refactor with a build-gated tripwire — squarely this repo's idiom (cf. `ComposeHardcodedStringTest`,
`StepCreditAllowlistTest`).

## 1. Goals / non-goals

**Goals**
1. Produce the **style bible** (`docs/steering/style-bible.md`) — the palette + shape/line/motif vocabulary
   + per-biome mood, derived from GDD §12.2 (Mesopotamian art direction, cuneiform/lapis motifs).
2. Codify the *color* half as a **single Kotlin source of truth** (`BattlePalette`) that biome + enemy +
   ziggurat art reference; behaviour-preserving consolidation (no visual regression unless intended + noted).
3. Tighten the **enemy** and **biome/ziggurat** art to read as one coherent system (GDD §6.3 "cohesive enemy
   visual language", §12.2 per-biome palettes).
4. Formalise **per-biome particle/effect configs** feeding `EffectEngine`/`ParticlePool`.
5. Add a **build-gated guard** against a new raw ART-palette `0xFF…` literal re-fragmenting the source of truth.
6. (Optional) A **tone bible** (`docs/steering/tone-bible.md`) as a consistency doc for future copy.

**Non-goals (deferred — belong to the paid lanes / separate issues)**
- Raster assets: app icon, Play feature graphic (1024×500), screenshots — need Midjourney/Stability + a
  **human edit/curate copyright pass**; blocked on the licensing gates in #391 (FLUX.1-dev is non-commercial).
- Audio (re)generation — audio ships already; ElevenLabs re-gen (dev **has an account**) is a *separate*
  "feel"/polish issue (Gate A), not this lane. Note the unresolved **ElevenLabs "Studio Games" clause** must
  be closed before shipping any *new* ElevenLabs audio in a monetized build (per #391).
- New user-facing strings / new locales.
- Any change to battle **game logic, thread-safety, or economy** — this lane is render-color/config +
  docs only. It must not touch `entitiesLock`/`effectsLock` regions' *behaviour*.

## 2. The decomposition (bible-first; children 2–4 each a separate small PR)

### Child 1 — Style Bible + `BattlePalette` (own PR; the reviewed contract)
- **Doc:** `docs/steering/style-bible.md` — brand palette (reuse `Color.kt` tokens verbatim as the anchor),
  per-biome mood/palette rationale, the **shape vocabulary** (enemy silhouettes, ziggurat layers, projectile/
  orb forms), line weights, and Babylonian motif guidance. Cross-links GDD §6.3 + §12.2.
- **Code:** a new `presentation/battle/biome/BattlePalette.kt` (Android-free-ish: it's presentation-layer, may
  use `android.graphics.Color` helpers) that names the currently-anonymous biome/enemy ARGB ints as intentful
  constants (e.g. per-biome sky/ground/ziggurat ramps, per-enemy base colors). **No visual change** — same
  ARGB values, just named + centralised. `BiomeTheme`/`EnemyEntity`/`ZigguratEntity` are NOT yet repointed
  in this PR (keeps the diff to "introduce the source of truth"); or repoint `BiomeTheme` only if the reviewer
  prefers a proof-of-use — decide at plan time.
- **Rationale:** the bible is the contract every later PR is reviewed against. Land + review it alone.

### Child 2 — Enemy shape/color language
- Repoint `EnemyEntity.BASE_COLORS` at `BattlePalette`. Confirm the 6-enemy shape set (circle BASIC/BOSS/
  SCATTER, triangle FAST, square TANK, diamond RANGED) reads as a coherent language; adjust colors *only* if
  the bible calls for it (note any intended visual change explicitly). HP-bar ratio colors, armor stroke, and
  the FAST/RANGED path geometry are **functional feedback**, documented in the bible but treated as UI-signal,
  not free art (see §4 guard scoping).

### Child 3 — Biome + ziggurat consistency
- Re-derive `BiomeTheme`'s 5 palettes and `ZigguratEntity.DEFAULT_COLORS` from `BattlePalette` so biomes are
  variations on one system. `BackgroundRenderer` already consumes `BiomeTheme` (no change there). Behaviour-
  preserving; any deliberate re-tune is noted + eyeballed on-device.

### Child 4 — Per-biome particle/effect configs
- `BiomeTheme` already carries `particleColor/particleDriftX/particleDriftY/particleCount`. Formalise these
  into a named per-biome emitter config vocabulary (still fed to `BackgroundRenderer` + `EffectEngine`/
  `ParticlePool`). Pure config; no lock-region behaviour change. **Do not** restructure `EffectEngine`'s
  `effectsLock` regions (fragile zone).

### Child 5 (optional) — Tone bible
- `docs/steering/tone-bible.md` (mythic-Babylonian voice, register, forbidden words). Doc only; no strings.

### Guard (lands with Child 1 or Child 3 — plan-time call)
- `architecture/BattleArtPaletteTest` (mirrors `ComposeHardcodedStringTest`/`StepCreditAllowlistTest`:
  dependency-free, walks source, comment-strips, pure predicate). Fails on a **new** raw `0xFF…`/`0x…`-int
  ARGB literal in the **art-color** surface (`BiomeTheme.kt`, `EnemyEntity` BASE_COLORS region,
  `ZigguratEntity` layer colors) that isn't sourced from `BattlePalette`.
  - **Scoping nuance (review finding — must respect):** several literals are *functional UI feedback*, not
    art palette, and must be **allowlisted/excluded**, not centralised into the biome palette: HP-bar ratio
    colors (green/yellow/red thresholds in `EnemyEntity.render`), the armor cyan stroke, the ziggurat
    range-circle alphas (`0x22FFFFFF`/`0x44FFFFFF`) and origin gold. Model the allowlist exactly like
    `ComposeHardcodedStringTest.allowlist` (each entry justified by a code comment). A green run means "no
    NEW un-sourced art color", NOT "zero literals exist."

## 3. Sequencing, dependencies, PR convention

- **Order:** Child 1 (+ optionally the guard) → Child 2 → Child 3 → Child 4 → Child 5. Merge **one at a time**
  (sequential-merge rule for stacked PRs); rebase each onto updated `main`.
- Each PR follows the **PR Task-List Convention**: sync current-state docs (add `style-bible.md`/`tone-bible.md`
  to `docs/steering/source-files.md`; update `CLAUDE.md` headline test count when the guard test lands; note
  the new guard in the Testing section + a fragile-zone line for `BattlePalette` as the single source) → then
  STATE.md + RUN_LOG.md → commit.
- **`concurrency-reviewer` lane:** the mandatory-lane trigger (ADR-0038) fires on edits to
  `presentation/battle/engine/**` and `effects/**`. Children 2–4 touch `entities/`/`biome/` render color +
  effect *config*. If a diff stays purely color-constant/config and touches **no** lock region or shared-
  collection mutation, the lane is advisory-not-required — but **run the concurrency-reviewer anyway on
  Child 4** (it brushes `EffectEngine`/`ParticlePool`) to be safe. Flag in each PR whether a lock region was touched.

## 4. Testing strategy

- **Consolidation PRs are behaviour-preserving** → the primary proof is "same ARGB values, no visual diff."
  Add a small JVM test asserting `BattlePalette` exposes the expected constants and that `BiomeTheme.forBiome`
  returns the palette-sourced values (pins the wiring, catches an accidental value change).
- **Guard test** `BattleArtPaletteTest` as above (JVM, dependency-free).
- On-device eyeball for any *intended* visual re-tune (there is no Compose/Canvas snapshot harness in-repo;
  battle render is on-device sign-off per existing convention, e.g. #171).
- Headline count moves by the tests added (update the `CLAUDE.md` Testing line in that PR).

## 5. Risks & review findings (light single-agent review, folded in)

1. **Functional-vs-art color conflation (addressed §2 Child 2 / §4 guard scoping).** The biggest trap: HP
   bars, armor rings, range circles are UI *signal* colored deliberately (WCAG-adjacent), NOT free art.
   Sweeping them into a "biome palette" would be wrong. The guard must allowlist them; the bible documents
   them as a *functional* palette section distinct from the *art* palette.
2. **No visual-regression harness.** Consolidation is asserted by value-equality tests + on-device eyeball,
   not pixels. Accept (matches repo convention); keep each PR small so an eyeball is tractable.
3. **Fragile zones.** `presentation/battle/effects/` is a do-not-touch fragile zone (STATE.md). Child 4 must
   change *config values/structs only*, never the `effectsLock` acquire/drain/render regions. Run
   `concurrency-reviewer`.
4. **Scope creep into paid lanes.** Raster/audio explicitly out (§1). If the dev later wants the icon/feature
   graphic, that's a *new* issue on the paid lane with the licensing gates as blockers — do not fold it here.
5. **Bible drift.** A doc bible without a code anchor rots. Mitigation: `BattlePalette` is the *code* anchor
   the guard enforces; the doc bible cross-links to it and to the GDD, and is added to `source-files.md`.
6. **ElevenLabs note (not this lane, but recorded):** dev has an account; before any *new* ElevenLabs audio
   ships in the monetized build, close the "Studio Games" carve-out question (#391 open risk). Audio feel/re-gen
   should be its own issue.

## 6. Deliverables checklist (for the plan that follows this spec)

- [ ] `docs/steering/style-bible.md`
- [ ] `presentation/battle/biome/BattlePalette.kt` (single color source of truth)
- [ ] `architecture/BattleArtPaletteTest` (+ allowlist for functional-feedback colors)
- [ ] Child-2/3/4 repointings (separate PRs)
- [ ] (opt) `docs/steering/tone-bible.md`
- [ ] Per-PR doc sync (source-files.md, CLAUDE.md test count + Testing/fragile-zone note) + STATE/RUN_LOG
- [ ] GitHub: file child issues off #391 (keep #391 as the tracking epic; label per-category)
