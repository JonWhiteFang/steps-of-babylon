#!/usr/bin/env bash
# Play "What's new" → fastlane supply's changelog layout: distribution/<locale>/changelogs/<versionCode>.txt
# (r0adkll used distribution/whatsnew/whatsnew-<locale>; fastlane's --metadata_path layout differs).
# Ports release.yml's notes step, hardened:
#  - reads the tag message ONLY for a real ANNOTATED tag; a lightweight tag's %(contents) is the COMMIT
#    message, which must never be published as store metadata → fall back to the generic line instead.
#  - truncates to 500 UNICODE chars (Play's limit), UTF-8-safe (head -c 500 can split a codepoint → invalid).
set -euo pipefail
tag="${1:?tag required}"
locale="${2:-en-US}"
vc="$(grep -oE 'versionCode = [0-9]+' app/build.gradle.kts | grep -oE '[0-9]+' | head -1)"
[ -n "$vc" ] || { echo "Could not parse versionCode from app/build.gradle.kts"; exit 1; }
notes=""
if [ "$(git cat-file -t "$tag" 2>/dev/null)" = "tag" ]; then
  notes="$(git tag -l --format='%(contents)' "$tag")"   # annotated-tag message
fi
if [ -z "$(printf '%s' "$notes" | tr -d '[:space:]')" ]; then notes="Bug fixes and improvements."; fi
dir="distribution/$locale/changelogs"
mkdir -p "$dir"
if command -v python3 >/dev/null 2>&1; then
  printf '%s' "$notes" | python3 -c "import sys; sys.stdout.buffer.write(sys.stdin.buffer.read().decode('utf-8','ignore')[:500].encode('utf-8'))" > "$dir/$vc.txt"
else
  printf '%s' "$notes" | head -c 500 > "$dir/$vc.txt"   # fallback (byte cap) if no python3
fi
echo "----- $dir/$vc.txt -----"; cat "$dir/$vc.txt"
