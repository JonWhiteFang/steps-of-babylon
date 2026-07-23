#!/usr/bin/env bash
# Docs-only fast-path classifier (ports ci.yml's fail-safe inversion; rules:changes can't express it).
# Usage: classify-diff.sh <base_sha> <head_sha>  → prints "CODE=true|false" to stdout, log to stderr.
# Verdict: CODE=false ONLY when every changed path is docs/tooling
#   (^docs/ | .md | ^.claude/ | ^.mcp.json$); unknown/invalid base ⇒ CODE=true (never silently skip).
set -uo pipefail
base="${1:-}"; head="${2:-HEAD}"
emit() { echo "CODE=$1"; echo "$2" >&2; }
if [ -z "$base" ] || [ "$base" = "0000000000000000000000000000000000000000" ] || ! git rev-parse -q --verify "$base^{commit}" >/dev/null; then
  emit true "Unknown/invalid diff base ($base) → full gate."; exit 0
fi
files="$(git diff --name-only "$base" "$head")" || { emit true "git diff failed → full gate."; exit 0; }
echo "Changed files:" >&2; echo "$files" >&2
non_docs="$(printf '%s\n' "$files" | grep -vE '^$' | grep -vE '^docs/|\.md$|^\.claude/|^\.mcp\.json$' || true)"
if [ -z "$non_docs" ]; then
  emit false "docs/tooling-only → heavy gate skipped."
else
  emit true "code change → full gate (first non-docs: $(printf '%s' "$non_docs" | head -1))."
fi
