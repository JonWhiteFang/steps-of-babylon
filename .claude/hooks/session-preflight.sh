#!/usr/bin/env bash
# SessionStart preflight hook — prints repo state + the live, forward-looking
# sections of docs/agent/STATE.md so a fresh Claude Code session is grounded in
# current project truth (the "read-at-start" half of the committed memory spine).
#
# Output is plain text on stdout: Claude Code adds a SessionStart hook's stdout
# directly to the session context (no JSON wrapper required for a context-only
# hook). Kept dependency-light (bash + coreutils + awk; iconv only as an
# optional multibyte-safe trim) and bounded well under the 10,000-char
# additionalContext cap by selecting STATE.md's live sections rather than its long
# historical "Current objective" / "Previous objective" bullets. Always exits 0 so
# a hook failure never blocks a session, and degrades gracefully outside a git repo.
set -u

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." 2>/dev/null && pwd)"
STATE="$ROOT/docs/agent/STATE.md"

echo "=== Session preflight: Steps of Babylon ==="
echo

if git -C "$ROOT" rev-parse --is-inside-work-tree >/dev/null 2>&1; then
  branch="$(git -C "$ROOT" rev-parse --abbrev-ref HEAD 2>/dev/null)"
  echo "## Git"
  echo "Branch: ${branch}"
  echo "Status:"
  git -C "$ROOT" status --short 2>/dev/null | head -n 40
  echo
  echo "Recent commits:"
  git -C "$ROOT" log -n 10 --oneline 2>/dev/null
  echo
fi

if [ -f "$STATE" ]; then
  echo "## STATE.md (live sections — see docs/agent/STATE.md for full state)"
  echo
  # Newest "## Current objective" bullet only, capped at 1500 bytes. The cap is a
  # byte count (head -c); if a future bullet exceeds it and a multibyte UTF-8 char
  # (em-dash, ≥, →, ×) straddles the boundary, iconv //IGNORE drops the partial tail
  # so the injected context never carries a corrupt byte sequence. iconv is present
  # on macOS + Linux; if absent, the `|| cat` keeps the raw (still-valid-99%) output.
  awk '
    /^## Current objective/ { inco=1; next }
    /^## / && inco { exit }
    inco && /^- / { if (got) exit; got=1 }
    inco && got { print }
  ' "$STATE" | head -c 1500 | { iconv -f UTF-8 -t UTF-8//IGNORE 2>/dev/null || cat; }
  echo
  echo
  # The three live, forward-looking sections in full.
  awk '
    /^## / {
      h=$0
      keep = (h=="## Top priorities (next 5)" || h=="## Next actions (explicit order)" || h=="## Do-not-touch / fragile zones")
    }
    keep { print }
  ' "$STATE"
  echo
  echo "— see docs/agent/STATE.md for full state —"
else
  echo "(docs/agent/STATE.md not found — skipping STATE injection)"
fi

exit 0
