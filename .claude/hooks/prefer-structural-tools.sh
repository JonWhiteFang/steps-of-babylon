#!/usr/bin/env bash
# PreToolUse(Bash) advisory — nudge toward the CLAUDE.md "Preferred CLI tooling" (sg/ast-grep, fd)
# when a Bash command does a Kotlin search with grep/rg or file discovery with find.
#
# NON-BLOCKING by contract: always exits 0 and never denies the call. When it detects a
# wrong-tool pattern it emits hookSpecificOutput.additionalContext (a reminder injected into the
# model's context for the NEXT call); otherwise it prints nothing. Low false-positive: silent for
# plain text/log scans, non-Kotlin work, and commands already using sg/ast-grep/fd. Degrades to a
# no-op if jq is missing or the input can't be parsed.
set -euo pipefail

input="$(cat 2>/dev/null || true)"
cmd="$(printf '%s' "$input" | jq -r '.tool_input.command // empty' 2>/dev/null || true)"
[ -z "$cmd" ] && exit 0

emit() {
  # $1 = reminder text → inject as additionalContext, then stop (one nudge per call).
  jq -cn --arg ctx "$1" \
    '{hookSpecificOutput:{hookEventName:"PreToolUse",additionalContext:$ctx}}' 2>/dev/null || true
  exit 0
}

# --- grep/rg/egrep over Kotlin → prefer sg/ast-grep -------------------------------------------
# Fire when a grep-family command (as a command, not a substring) references .kt files or app/src,
# AND sg/ast-grep isn't already in use, AND it isn't a git log/grep commit search.
if printf '%s' "$cmd" | grep -Eq '(^|[;&|]|\$\(|[[:space:]])(grep|egrep|rg)([[:space:]]|$)' \
   && printf '%s' "$cmd" | grep -Eq '\.kt([[:space:]"'\''/:)]|$)|app/src' \
   && ! printf '%s' "$cmd" | grep -Eq '(^|[[:space:]])(sg|ast-grep)([[:space:]]|$)' \
   && ! printf '%s' "$cmd" | grep -Eq 'git[[:space:]]+(log|grep)'; then
  emit "Tool nudge (CLAUDE.md 'Preferred CLI tooling'): this looks like a Kotlin search with grep/rg. For *structural* Kotlin queries (call sites, ctor/param sweeps, enum/API-shape surfacing) prefer ast-grep: sg -l kotlin -p '<pattern>' <path>. Use grep/rg only for literal text/log/comment scans. Advisory — the command still ran; switch the tool on your next search if this was structural."
fi

# --- find over a path → prefer fd -------------------------------------------------------------
# Fire when `find` is invoked as a command and fd isn't already in use.
if printf '%s' "$cmd" | grep -Eq '(^|[;&|]|\$\(|[[:space:]])find[[:space:]]' \
   && ! printf '%s' "$cmd" | grep -Eq '(^|[[:space:]])fd([[:space:]]|$)'; then
  emit "Tool nudge (CLAUDE.md 'Preferred CLI tooling'): prefer fd over find for file discovery (e.g. fd -e kt Foo, fd -t f pattern path). Use find only when fd genuinely can't express the query. Advisory — the command still ran."
fi

exit 0
