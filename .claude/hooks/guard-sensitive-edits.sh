#!/usr/bin/env bash
# PreToolUse(Edit|Write) — guard documented fragile/process-controlled files.
#
# Two tiers, both fail-open (a hook error never blocks a legit edit; jq missing → no-op exit 0):
#
#   1. app/schemas/**  → permissionDecision "ask".  Room schema JSON is CI schema-drift-gated and
#      migrations are a documented fragile zone. Schema files SHOULD only change via a real Room
#      migration, never hand-edited. "ask" surfaces a confirmation rather than hard-blocking, since
#      a migration legitimately adds a new schema file.
#
#   2. app/build.gradle.kts version block (versionCode/versionName) → advisory only. These follow
#      the strict release process (use the /release skill); a mid-task edit is usually accidental.
#      Injects a reminder via additionalContext but lets the edit proceed (matches the house style
#      of prefer-structural-tools.sh).
#
#   3. data/local/Migrations.kt → advisory only. The hand-written companion to the app/schemas/**
#      JSON (tier 1): a Room version bump needs a Migration(N-1, N) here AND a regenerated schema
#      file, and editing one without the other is the classic schema-drift gate failure. Nudges
#      toward the /new-migration skill (the full choreography) but lets the edit proceed.
#
# Output contract: PreToolUse JSON. For tier 1 we emit permissionDecision=ask with a reason. For
# tiers 2 and 3 we emit additionalContext. Everything else prints nothing and exits 0.
set -uo pipefail

input="$(cat 2>/dev/null || true)"
command -v jq >/dev/null 2>&1 || exit 0

file="$(printf '%s' "$input" | jq -r '.tool_input.file_path // empty' 2>/dev/null || true)"
[ -z "$file" ] && exit 0

# --- Tier 1: Room schema files → ask for confirmation ------------------------------------------
case "$file" in
  */app/schemas/*|app/schemas/*)
    jq -cn --arg r "This edits a Room schema file under app/schemas/. Schema JSON is CI schema-drift-gated and migrations are a documented fragile zone (see CLAUDE.md / docs/database-schema.md). Schema files should change only as the output of a real Room migration + version bump, never hand-edited. Confirm this is an intended schema change." \
      '{hookSpecificOutput:{hookEventName:"PreToolUse",permissionDecision:"ask",permissionDecisionReason:$r}}' 2>/dev/null || true
    exit 0
    ;;
esac

# --- Tier 2: version block in app/build.gradle.kts → advisory ----------------------------------
case "$file" in
  */app/build.gradle.kts|app/build.gradle.kts)
    new_str="$(printf '%s' "$input" | jq -r '.tool_input.new_string // .tool_input.content // empty' 2>/dev/null || true)"
    if printf '%s' "$new_str" | grep -Eq 'versionCode|versionName'; then
      jq -cn --arg ctx "Advisory (CLAUDE.md release process): this edits versionCode/versionName in app/build.gradle.kts. Version bumps follow the strict release checklist — prefer the /release skill, which bumps the version, promotes the CHANGELOG, writes release notes, syncs version pointers, and tags v* to trigger release.yml. The edit still proceeds; reconsider if this isn't part of a release." \
        '{hookSpecificOutput:{hookEventName:"PreToolUse",additionalContext:$ctx}}' 2>/dev/null || true
    fi
    exit 0
    ;;
esac

# --- Tier 3: Room migrations (data/local/Migrations.kt) → advisory -----------------------------
case "$file" in
  */data/local/Migrations.kt|data/local/Migrations.kt)
    jq -cn --arg ctx "Advisory (CLAUDE.md fragile zone): this edits the Room Migrations.kt. A migration is a multi-step, schema-drift-gated change — the hand-written Migration(N-1, N) here must land WITH a bumped AppDatabase version and a regenerated app/schemas/N.json, or CI's schema-drift gate fails. Prefer the /new-migration skill, which walks the full ordered choreography (edit @Entity → bump version → author + register the migration → rebuild to regenerate the schema JSON → run the drift gate → extend AtomicDaoConcurrencyTest if a guarded DAO changed → update docs/database-schema.md). The edit still proceeds." \
      '{hookSpecificOutput:{hookEventName:"PreToolUse",additionalContext:$ctx}}' 2>/dev/null || true
    exit 0
    ;;
esac

# --- Tier 4: battle-engine / effects / DAO / currency surface → advisory (#372, ai-2) ----------
# The lock-order invariant (entitiesLock → effectsLock; collaborators hold no monitor) and the
# currency-move surface are the two bug classes the project was burned by (#118/#191) and the
# ones an agent is most likely to trip. The concurrency-reviewer subagent is MANDATORY on these
# diffs (CLAUDE.md Adversarial Review Gate). Advisory nudge (matches Tiers 2/3 house style) — the
# edit proceeds, but flags that the mandatory concurrency-review lane applies.
case "$file" in
  */presentation/battle/engine/*|*/presentation/battle/effects/*|*/data/local/*Dao.kt|*/data/repository/PlayerRepositoryImpl.kt)
    jq -cn --arg ctx "Advisory (#372 / CLAUDE.md Adversarial Review Gate): this edits the battle-engine/effects, a Room DAO, or the currency-moving PlayerRepositoryImpl. The concurrency-reviewer subagent is a MANDATORY review lane for this diff (thread-safety: entitiesLock→effectsLock order, collaborators hold no monitor; atomic guarded-deduct economy). Run it before committing. The edit proceeds." \
      '{hookSpecificOutput:{hookEventName:"PreToolUse",additionalContext:$ctx}}' 2>/dev/null || true
    exit 0
    ;;
esac

exit 0
