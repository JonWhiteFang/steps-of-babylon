#!/usr/bin/env bash
# PostToolUse(Edit|Write) — auto-format a single edited Kotlin file with ktlint.
#
# Why: ktlint formatting is CI-enforced (the PR gate runs ./lint-kotlin.sh). Formatting drift is
# the most common avoidable gate failure. This hook formats ONLY the file Claude just edited (not
# all of app/src, the way `lint-kotlin.sh --format` does) right after the edit, so the working tree
# stays gate-clean as it goes.
#
# NON-BLOCKING by contract: always exits 0. If ktlint isn't found, the file isn't Kotlin, the file
# isn't under app/src, or jq is missing, it's a silent no-op. It never denies or rolls back the edit
# — at worst the file is left as Claude wrote it and the existing CI gate still catches drift.
#
# Mirrors lint-kotlin.sh's binary-location + version-match discipline so the same ktlint that gates
# CI is the one that formats here. The single source of truth for the pinned version stays in
# lint-kotlin.sh; this reads it from there so the two can't drift.
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." 2>/dev/null && pwd)"
input="$(cat 2>/dev/null || true)"

command -v jq >/dev/null 2>&1 || exit 0

# Resolve the edited path from the tool payload (Edit/Write both use tool_input.file_path).
file="$(printf '%s' "$input" | jq -r '.tool_input.file_path // empty' 2>/dev/null || true)"
[ -z "$file" ] && exit 0

# Kotlin only, and only inside app/src (matches the scope ktlint checks in CI).
case "$file" in
  *.kt|*.kts) ;;
  *) exit 0 ;;
esac
case "$file" in
  "$ROOT"/app/src/*|app/src/*) ;;
  *) exit 0 ;;
esac
[ -f "$file" ] || exit 0

# Read the pinned version from lint-kotlin.sh (single source of truth).
KTLINT_VERSION="$(awk -F'"' '/^KTLINT_VERSION=/{print $2; exit}' "$ROOT/lint-kotlin.sh" 2>/dev/null || true)"
[ -z "$KTLINT_VERSION" ] && exit 0

# Locate a version-matched ktlint, same precedence as lint-kotlin.sh (repo cache, then PATH).
ktlint_bin=""
if [ -x "$ROOT/.ktlint/ktlint" ]; then
  v="$("$ROOT/.ktlint/ktlint" --version 2>/dev/null | awk '{print $NF}')"
  [ "$v" = "$KTLINT_VERSION" ] && ktlint_bin="$ROOT/.ktlint/ktlint"
fi
if [ -z "$ktlint_bin" ] && command -v ktlint >/dev/null 2>&1; then
  v="$(ktlint --version 2>/dev/null | awk '{print $NF}')"
  [ "$v" = "$KTLINT_VERSION" ] && ktlint_bin="ktlint"
fi
[ -z "$ktlint_bin" ] && exit 0

# Format just this file (no baseline — same as lint-kotlin.sh --format, so the file ends up in the
# fully-formatted state CI expects). Suppress output; this is a silent background tidy.
"$ktlint_bin" -F "$file" >/dev/null 2>&1 || true

exit 0
