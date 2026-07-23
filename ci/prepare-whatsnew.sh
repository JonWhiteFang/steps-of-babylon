#!/usr/bin/env bash
# Play "What's new" from the annotated tag message, capped at Play's 500-char limit.
# Ports the release.yml "Prepare Play release notes" step.
set -euo pipefail
tag="${1:?tag required}"
mkdir -p distribution/whatsnew
notes="$(git tag -l --format='%(contents)' "$tag")"
if [ -z "$(printf '%s' "$notes" | tr -d '[:space:]')" ]; then notes="Bug fixes and improvements."; fi
printf '%s' "$notes" | head -c 500 > distribution/whatsnew/whatsnew-en-US
echo "----- whatsnew-en-US -----"; cat distribution/whatsnew/whatsnew-en-US
