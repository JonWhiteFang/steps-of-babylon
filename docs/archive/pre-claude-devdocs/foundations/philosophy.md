# Philosophy (Doc-Inferred)

> Scope: the product, engineering, and process philosophy **as stated in the
> repository's non-code documentation**. Source files: see the provenance list
> in `project_description.md`. Where documentation is silent or self-contradictory
> this is called out rather than papered over with code inference.

---

## 1. The one immovable idea

> *"Every Step Builds the Tower."* — `README.md`, `docs/StepsOfBabylon_GDD.md`
> cover.

Steps of Babylon's identity rests on a single axiom: **permanent in-game power
must trace back to real physical movement**. Every subsequent rule, guardrail
and feature exists to protect that axiom.

GDD §1 phrases the fantasy: "The more you walk, the further you survive. The
further you survive, the more motivated you are to walk tomorrow." The design
philosophy sentence in the same section is unambiguous: *"Steps are never
generated passively in-game. Every upgrade, every advancement, every unlock
traces back to real physical movement. The game respects the player's effort
by making every step count — literally."*

## 2. Design pillars (as stated)

The GDD §1.1 defines four pillars. They are repeated nearly verbatim in
`.kiro/steering/product.md` and `docs/agent/START_HERE.md`:

| Pillar | What it means (stated) |
|---|---|
| **Walk to Power** | Steps are the sole permanent currency. No pay-to-win, no idle step generation. The ziggurat reflects physical effort. |
| **Satisfying Depth** | The upgrade layering of *The Tower* (Workshop, Labs, Ultimate Weapons, Cards) adapted for step-based progression. |
| **One More Walk** | Escalating costs and tantalising unlocks create motivation to walk further, mirroring *The Tower*'s "one more run" compulsion. |
| **Journey of Discovery** | Tier progression is mapped to narrative biomes, giving the player a visual sense of traveling through a world powered by their footsteps. |
| **Real-Time Spectacle** | Watching the ziggurat obliterate waves provides the dopamine reward for physical investment. |

(The GDD table lists five rows above; "Real-Time Spectacle" is sometimes
presented as a fifth pillar and sometimes folded into "Satisfying Depth" in
shorter summaries.)

## 3. Hard design rules (game)

Collected verbatim or near-verbatim from GDD §13, `.kiro/steering/product.md`,
`docs/agent/START_HERE.md`, and `docs/agent/CONSTRAINTS.md`:

- Steps can **never** be generated passively in-game.
- Steps can **never** be purchased with real money. This is "non-negotiable"
  (`docs/monetization.md`).
- All monetization is cosmetic or convenience only.
- No pay-to-win, no loot boxes with real money. Card Packs cost Gems, which
  are earnable through play.
- No energy systems, no play-gating.
- No FOMO mechanics — "missing a day has zero penalty" (`docs/monetization.md`).
- Ads are always opt-in rewards, never interruptions.
- Loadout caps: **3 Ultimate Weapons, 3 Cards** per round (GDD §7, §9,
  `CONSTRAINTS.md`).
- Step Overdrive is **once per round**, 60-second duration.
- Cash is round-local; Steps/Gems/Power Stones/Card Dust are permanent.
- Cost formula for every upgrade is **`baseCost × scaling ^ level`** with no
  exceptions (`CONSTRAINTS.md`).
- Battle Steps (per ADR-0003) are capped at 2,000/day and are **not** multiplied
  by Fortune Overdrive, Cash Bonus upgrades or Golden Ziggurat UW. They are a
  supplement, not a replacement, for walking.

## 4. Fair-play / anti-cheat stance

Documented consistently across GDD §11.3, `docs/step-tracking.md`,
`CONSTRAINTS.md`, and the Remediation plans:

- The game is **single-player and solo**, which means anti-cheat exists to
  protect the *player's own sense of achievement*, not a leaderboard. GDD §19
  explicitly notes: "Solo-only limits impact".
- Anti-cheat is **graduated, not punitive** — a 4-level offense system escrows
  steps on first flag and only ramps up to hard caps on repeated offenses
  (`docs/step-tracking.md`).
- Rate limit of 200 steps/min with a 250-step burst "for running".
- Daily ceiling of 50,000 steps/day is a hard cap — "prevents extreme exploits".
- Step-velocity analysis targets **phone shakers and spoofers** via statistical
  patterns (constant rate, instant jumps), not user-agent fingerprinting.
- Cross-validation against Health Connect is a **confidence check, not a
  judgement** — discrepancies below 20% are ignored.
- Battle Steps are kept on a **separate counter** so they "never confuse the
  Health Connect cross-validation pipeline" (ADR-0003).

## 5. Monetization philosophy

From `docs/monetization.md` and GDD §13:

- **Cosmetics first.** "Cosmetics are the primary revenue driver."
- **Convenience optional.** Gem Packs, Lab rush, Season Pass all save time
  but are never required.
- **Ads never interrupt.** All ads are opt-in reward placements.
- **Free players reach everything.** "A non-subscriber can earn everything
  through play."
- **No loot boxes with real money.** Card Packs are bought with Gems, and
  Gems are earnable.
- **Effort-gated, not money-gated.** The Season Pass "no gameplay advantages
  beyond convenience" statement is the explicit guarantee.

## 6. Accessibility & inclusivity stance

Stated aspirations (GDD §17, `docs/plans/plan-24-accessibility.md`):

- Wheelchair users and non-ambulatory users are first-class through Activity
  Minute Parity — cycling, rowing, swimming, wheelchair propulsion, yoga all
  have documented conversion rates.
- Three colour-blind palettes planned.
- TalkBack-compatible menus planned; battle screen to provide audio cues.
- Adjustable text size via system font settings.
- **Rest Day Encouragement** — after 3+ consecutive 10k-step days, the game
  should nudge the player to rest and reward them with a Gem. This exists as
  a design principle specifically so the product does not become a
  compulsion engine.
- "No punishment for inactivity. Missing days never results in penalties or
  FOMO mechanics."

## 7. Privacy & data posture

From `docs/release/privacy-policy.md` and `docs/architecture.md` "Security":

- **Local-only by default.** "Steps of Babylon v1.0 has **no server backend**.
  Your data is never uploaded to any remote server operated by us."
- All game state is stored locally in an SQLCipher-encrypted Room database.
- The database passphrase is managed by the Android Keystore (AES-256-GCM),
  with automatic recovery on keystore mismatch.
- `allowBackup="false"` — explicitly chosen because there is no valuable
  state to restore across devices and because restore-related crashes are a
  known failure mode (R05).
- Network-security config blocks cleartext traffic.
- Health Connect data is requested with the narrowest scopes required
  (`READ_STEPS`, `READ_EXERCISE`) and users can revoke them at any time.
- No account system, no personally identifying information collected.
- The only third-party services planned are Google Play Billing and AdMob,
  each with their own policies, integrated only when activated.

## 8. Architectural philosophy

Spelled out consistently across `docs/architecture.md`,
`.kiro/steering/structure.md`, `.kiro/steering/tech.md`, `AGENTS.md`, and
`CONSTRAINTS.md`:

- **Clean Architecture with a pure domain.** `domain/` must have zero Android
  imports. Pure Kotlin, pure function cores. This is non-negotiable.
- **One-way dependency.** `presentation → domain ← data`. No shortcuts.
- **Single source of truth for state is Room.** State lives in Room, flows up
  through repository interfaces as `Flow`, is collected by ViewModels and
  exposed as `StateFlow`. Compose collects `StateFlow`. (See the Docs vs Code
  delta about SharedPreferences-backed exceptions.)
- **Offline-first.** The game must be fully playable without any network.
- **Reactive async everywhere.** "Kotlin coroutines and Flow for all async
  operations. No RxJava, no callbacks."
- **Hilt + KSP, never kapt.**
- **Version catalog only.** Dependency versions are centralised in
  `gradle/libs.versions.toml`; hardcoded versions in `build.gradle.kts` are
  forbidden.
- **UI split by fitness for purpose.** Jetpack Compose for menus/screens;
  a custom `SurfaceView` + dedicated thread + fixed-timestep game loop for
  the battle renderer, because "no heavy engine needed for 2D gameplay"
  (GDD §15.1).

## 9. Testing philosophy (stated)

From `AGENTS.md`, `docs/plans/plan-29-testing.md`, and the Balance Report:

- **Pure-JVM first.** The majority of logic is tested with JUnit 5 and
  `kotlinx-coroutines-test` without emulators.
- **Fakes, not mocks, for repositories.** The doc catalogues an
  in-memory `Fake*Repository` for every real repository.
- **Balance constants are regression-guarded.** The Balance Report describes
  39 tests whose purpose is "if any constant is changed in the future,
  these tests will catch unintended side effects".
- **External reviews drive remediation.** Plan R and Plan R2 each began with
  an outside code review (`docs/external-reviews/REPO_ANALYSIS_BUGS_AND_UX*.md`)
  and their findings were decomposed into numbered sub-plans with explicit
  priority tiers (1 = release-blocking, 2 = pre-release recommended, 3 =
  post-release polish).

## 10. Process philosophy — "doc-first project memory"

This is codified in `.kiro/steering/10-project-memory.md` and
`.kiro/steering/11-agent-protocol.md` and is unusual enough to be worth
calling out as a first-class tenet:

- **Documentation is canonical memory, not a convenience.** The rule is
  explicit: *"Do NOT rely on chat history as the project source of truth."*
- **Required reading at session start.** `START_HERE.md`, `STATE.md`,
  `CONSTRAINTS.md` plus the latest `RUN_LOG.md` entry and any ADRs
  referenced in `STATE.md`.
- **Required writing at session end.** `STATE.md` is updated, `RUN_LOG.md`
  is appended, and any meaningful decision becomes an ADR under
  `docs/agent/DECISIONS/`.
- **STATE.md is one page.** Detail belongs in the run log and ADRs, not in
  STATE.md.
- **Plans precede code.** `AGENTS.md`, the steering docs, and `README.md` all
  instruct the agent/developer to "check the relevant plan file before
  implementing a feature". The `docs/plans/` folder is the real roadmap.
- **ADRs are the tie-breaker.** When the narrative docs and the codebase
  disagree, recent decisions are expected to be memorialised as ADRs so that
  the disagreement has a resolution story.

## 11. Game-feel philosophy

Less often stated as a pillar, but present throughout the plan files and
the CHANGELOG:

- **Immediate feedback beats accurate feedback.** ADR-0003 chose per-kill
  Step crediting over end-of-round batching because "accumulating and
  crediting only at round-end would hide the reward behind a quit/crash
  risk".
- **Predictable > surprising for balance.** ADR-0003 also forbids multiplying
  Battle Steps by Fortune/Cash-Bonus/Golden-Ziggurat because predictable
  yield "stops late-wave runs from minting Steps at an accelerating rate".
- **Build diversity beats a single strongest path.** The Balance Report
  concludes that "the game is designed so that Workshop upgrades alone
  aren't enough — players need crits, multishot, orbs, cards, and in-round
  upgrades to push higher waves. This creates meaningful build diversity."
- **Spectacle is earned, not forced.** Biome transitions, UW activations,
  overdrive auras and boss waves are documented as the emotional
  high-points; the ordinary loop stays legible so those moments stand out.

## 12. What the product refuses to be (negative philosophy)

Captured cleanly in the "Never do" list of `CONSTRAINTS.md` and the "Hard
rule" section of `docs/monetization.md`:

- Never add Android imports to `domain/`.
- Never hardcode dependency versions in `build.gradle.kts`.
- Never generate Steps passively or allow Step purchase with real money.
- Never use kapt — always KSP.
- Never skip reading the relevant plan file before implementing a feature.
- Never ship forced ads.
- Never monetise Steps.
- Never collect player data onto a backend in v1.0.

---

## Docs vs Code delta

**Where docs and code agree:**

- The "domain layer has zero Android imports" invariant is independently
  asserted in `architecture.md`, `CONSTRAINTS.md`, and the file-layout
  conventions in `.kiro/steering/structure.md`. Source-file naming in
  `.kiro/steering/source-files.md` is consistent with it (all models and
  use cases live under `domain/` with Kotlin-only filenames).
- "Steps are earned only by walking" is enforced at multiple layers of
  documentation (GDD, `product.md`, `CONSTRAINTS.md`, `START_HERE.md`) and
  the monetization doc reiterates the purchase prohibition.
- Anti-cheat philosophy (graduated response, not punishment) matches
  between `docs/step-tracking.md`, GDD §11.3, and the Plan R/R2 scope of
  the escrow redesign.

**Where docs appear outdated:**

- `docs/agent/DECISIONS/ADR-0001-template.md` is still literally the
  placeholder template (title `<Decision Title>`) — not an outdated doc per
  se, but the DECISIONS folder's taxonomy would read more cleanly with
  that file renamed, or removed once real ADRs exist.
- GDD §1.1 is inconsistent with itself: the table has five pillars
  ("Walk to Power", "Satisfying Depth", "One More Walk", "Journey of
  Discovery", "Real-Time Spectacle"), but the prose in the same section
  and in derived summaries (`product.md`) sometimes lists four. The doc
  does not reconcile this.
- Privacy policy promises local-only storage "in v1.0". Since stub billing
  and stub ads already exist and Plan 31 is pending, the moment real SDKs
  ship the third-party-services list must be re-published. The policy
  foreshadows this ("future versions may integrate…"), but the statement
  "data is never uploaded to any remote server operated by us" will remain
  technically true only as long as Play Billing + AdMob remain the only
  third parties.

**Where docs describe intended future state rather than current behaviour:**

- The "Accessibility" section of this document summarises GDD §17 in
  aspirational terms. In reality, `docs/plans/plan-24-accessibility.md`
  status is "Deferred (post-v1.0)" and only narrow TalkBack content
  descriptions from R11 have shipped. Readers should treat §6 above as
  *stated intent*, not *current state*.
- The "Rest Day Encouragement" behaviour is described in GDD §17 and in
  `plan-24-accessibility.md` Task 5 but no ADR, CHANGELOG entry, or plan
  tick-box confirms that the feature ships in v1.0.
- The "Exploration Mode" GPS tracking promise (GDD §2.3) has no matching
  implementation plan; it is a design intent only.
- The `TYPE_STEP_DETECTOR` tertiary sensor is openly marked deferred in
  `docs/step-tracking.md`.

**Where code contains behaviour not documented in narrative docs:**

- The process philosophy is heavily documented but one practical
  consequence is **not**: multiple docs (monetization.md, step-tracking.md,
  database-schema.md) appear to lag behind the code by one or two
  remediation cycles. Only the Remediation plan files and ADRs capture the
  most recent behaviour. New readers should treat the remediation plans
  and ADRs as the freshest truth.
- `AntiCheatPreferences`, `BiomePreferences`,
  `MilestoneNotificationPreferences`, and other `SharedPreferences`-backed
  stores (catalogued only in `source-files.md`) violate the letter, if not
  the spirit, of the "Room is the single source of truth for all game
  state" invariant. The narrative docs do not discuss the trade-off.

**Where documentation is too vague to verify:**

- The boundary between "cosmetic" and "convenience" in the monetization
  philosophy is clear for Ad Removal and Gem Packs, but the Season Pass
  ("1 free Lab rush/day", "exclusive cosmetics", "bonus Gems") sits in
  both categories simultaneously. The doc does not state whether the
  Lab rush per day counts as pay-to-win-adjacent or pure convenience.
- "No FOMO mechanics" is stated as a principle, but Daily Missions refresh
  at midnight and Daily Logins reward streaks. The docs do not address the
  tension: streaks are ordinarily considered a mild FOMO mechanic.
- The "graduated, not punitive" philosophy sits uneasily with the Level-3
  escrow behaviour ("cap at HC minus 10% penalty"), which *is* punitive.
  Whether that is intended as a hard edge-case last resort or as a final
  graduated step is not explicitly reconciled in any doc.
- The "Build diversity" claim in the Balance Report is a hypothesis based
  on 39 regression tests, not a measured player-behaviour statement — the
  doc itself acknowledges the numbers are estimates.
