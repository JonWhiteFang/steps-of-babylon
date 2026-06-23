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
# Output contract: PreToolUse JSON. For tier 1 we emit permissionDecision=ask with a reason. For
# tier 2 we emit additionalContext. Everything else prints nothing and exits 0.
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

exit 0
