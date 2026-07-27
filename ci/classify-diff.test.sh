#!/usr/bin/env bash
# Fixture test for ci/classify-diff.sh — builds a throwaway git repo and asserts the verdict.
set -euo pipefail
here="$(cd "$(dirname "$0")" && pwd)"
tmp="$(mktemp -d)"; trap 'rm -rf "$tmp"' EXIT
cd "$tmp"; git init -q; git config user.email t@t; git config user.name t
mkdir -p docs .claude app/src; echo x > README.md; echo x > app/src/A.kt
git add -A; git commit -qm base
base="$(git rev-parse HEAD)"
verdict() { bash "$here/classify-diff.sh" "$1" HEAD 2>/dev/null | grep -oE 'CODE=(true|false)' | cut -d= -f2; }
echo more >> docs/x.md; git add -A; git commit -qm docs
[ "$(verdict "$base")" = "false" ] && echo "PASS: docs-only" || { echo "FAIL docs-only"; exit 1; }
echo more >> app/src/A.kt; git add -A; git commit -qm code
[ "$(verdict "$base")" = "true" ] && echo "PASS: code path" || { echo "FAIL code"; exit 1; }
[ "$(verdict 0000000000000000000000000000000000000000)" = "true" ] && echo "PASS: unknown-base fail-safe" || { echo "FAIL unknown-base"; exit 1; }
echo "ALL PASS"
