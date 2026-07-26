#!/usr/bin/env bash
# Capture the pre-cutover GitHub fingerprint, or verify an import against it.
#
# This is the oracle the whole GitHub->GitLab cutover is checked against
# (docs/migration/phase3-cutover-runbook.md steps 2 and 4), so it is a script rather than an
# inline copy-paste: the capture happens once, under time pressure, in a single sitting.
#
#   ./tools/migration-fingerprint.sh capture [outfile]
#       Writes the fingerprint (default: docs/migration/fingerprint-github.txt). Needs `gh`.
#
#   ./tools/migration-fingerprint.sh verify <gitlab-clone-url> [fingerprint-file]
#       Mirror-clones the GitLab import to a temp dir and diffs its git-side fields against the
#       fingerprint. Exits non-zero on ANY mismatch. Does NOT need `gh`.
#
# Design notes (each one is a bug this script exists to prevent):
#  - `set -euo pipefail` + explicit numeric validation: `echo "$(gh …)"` exits 0 even when gh fails,
#    which would bake a BLANK count into the oracle and still look successful.
#  - COMMITS_ALL_REFS as well as COMMITS_HEAD: the runbook allows PRs to be parked, and a parked
#    branch's commits are invisible to `rev-list HEAD`. These two numbers genuinely differ here.
#  - Tag TYPE is recorded, not just the peeled SHA. A `v*` tag that arrives LIGHTWEIGHT still points
#    at the right commit and still triggers the release pipeline — it just has no message, so
#    ci/prepare-whatsnew.sh would silently publish the generic line as Play "What's new".
#  - Tag MESSAGE is hashed, so content drift is caught, not merely presence.
#  - `verify` clones the import instead of re-reading the local checkout: `origin` still points at
#    GitHub until the runbook's step 8, so re-running the capture locally would re-measure GitHub
#    and pass no matter what the importer did.
set -euo pipefail

FINGERPRINT_DEFAULT="docs/migration/fingerprint-github.txt"

die() { echo "ERROR: $*" >&2; exit 1; }

# Emit the git-side fields. Runs against whatever repo is the CWD, so it serves both capture
# (the GitHub checkout) and verify (a mirror clone of the GitLab import).
git_fields() {
  echo "HEAD $(git rev-parse HEAD)"
  echo "COMMITS_HEAD $(git rev-list --count HEAD)"
  # Branches + tags ONLY — deliberately NOT `rev-list --all`.
  # `--all` includes every ref the forge advertises, and those sets are forge-specific: a GitHub mirror
  # of this repo carries 293 `refs/pull/*` refs (measured), so `--all` reads 1244 while branches+tags
  # read 805. A GitLab import has no `refs/pull/*` (it has its own internal MR refs), so comparing
  # `--all` across forges would MISMATCH on a perfectly healthy import and abort the cutover.
  # Branches+tags is the logical content that must actually survive the move.
  echo "COMMITS_BRANCHES_TAGS $(git rev-list --count --branches --tags)"
  echo "--- v* tag inventory (name | peeled-sha | type | msg-sha) ---"
  local t sha type msg
  for t in $(git tag -l 'v*' --sort=v:refname); do
    sha="$(git rev-parse "$t^{commit}")"
    type="$(git cat-file -t "$t")"          # MUST be 'tag' (annotated), never 'commit'
    msg="$(git tag -l --format='%(contents)' "$t" | git hash-object --stdin)"
    echo "$t | $sha | $type | $msg"
  done
}

cmd_capture() {
  local out="${1:-$FINGERPRINT_DEFAULT}"
  command -v gh >/dev/null 2>&1 || die "gh not found — issue/PR counts would be missing."
  gh auth status >/dev/null 2>&1 || die "gh not authenticated (gh auth login) — counts would be blank."

  # Measure the git fields from a MIRROR of the source, not from this working checkout.
  # Why: `verify` measures a mirror clone of the import, and the two are not comparable otherwise —
  # a local checkout's `rev-list --all` sees only the refs it happens to have fetched, while a mirror
  # sees every ref the remote advertises. Measured here: 808 local vs 1243 mirror on the same repo.
  # Comparing those would abort a perfectly good cutover on a false COMMITS_ALL_REFS mismatch.
  local src_url; src_url="$(git remote get-url origin)" || die "no 'origin' remote to fingerprint."
  local srctmp; srctmp="$(mktemp -d)"
  # shellcheck disable=SC2064
  trap "rm -rf '$srctmp'" EXIT
  echo "Mirror-cloning the SOURCE so capture and verify measure identically ($src_url) …"
  git clone --quiet --mirror "$src_url" "$srctmp/source.git" || die "mirror clone failed: $src_url"

  # Validate every count BEFORE writing anything: a blank field in the oracle is worse than no oracle.
  # `num "$(gh …)"` is NOT enough on its own — command substitution used as an argument DISCARDS the
  # inner exit status, so a gh that prints partial/cached numeric output and *then* fails would sail
  # through both `num` and `set -e`. So capture stdout and status separately and check both.
  count_or_die() {
    local out rc label="$2"
    out="$(eval "$1")" && rc=0 || rc=$?
    [ "$rc" -eq 0 ] || die "$label: command exited $rc"
    case "$out" in ''|*[!0-9]*) die "$label: expected a number, got '$out'";; esac
    printf '%s' "$out"
  }
  local issues_open issues_closed prs
  issues_open="$(count_or_die "gh issue list -s open   -L 999 --json number -q 'length'" ISSUES_OPEN)"
  issues_closed="$(count_or_die "gh issue list -s closed -L 999 --json number -q 'length'" ISSUES_CLOSED)"
  prs="$(count_or_die "gh pr list -s all -L 999 --json number -q 'length'" PRS)"

  local lightweight
  lightweight="$(cd "$srctmp/source.git" && git tag -l 'v*' | while read -r t; do
    [ "$(git cat-file -t "$t")" = tag ] || echo "$t"; done)"

  # In a mirror the source's branches are refs/heads (not refs/remotes/origin), which is also how
  # verify sees the import — so the inventories line up name-for-name.
  local git_out branch_out
  git_out="$(cd "$srctmp/source.git" && git_fields)"
  branch_out="$(cd "$srctmp/source.git" && git for-each-ref --format='%(refname:short) | %(objectname)' refs/heads)"

  mkdir -p "$(dirname "$out")"
  {
    printf '%s\n' "$git_out"
    echo "ISSUES_OPEN $issues_open"
    echo "ISSUES_CLOSED $issues_closed"
    echo "PRS $prs"
    echo "--- branch inventory (name | sha) ---"
    printf '%s\n' "$branch_out"
  } > "$out"

  echo "Fingerprint written to $out"
  echo
  cat "$out"
  echo
  if [ -n "$lightweight" ]; then
    echo "WARNING: these v* tags are LIGHTWEIGHT, not annotated:" >&2
    printf '  %s\n' $lightweight >&2
    echo "  Their release would publish the generic Play 'What's new' line. Fix before cutover." >&2
  else
    echo "OK: every v* tag is annotated."
  fi
  echo "Now COMMIT this file — it must survive the remote flip (runbook step 8)."
}

cmd_verify() {
  local url="${1:?usage: verify <gitlab-clone-url> [fingerprint-file]}"
  local fp="${2:-$FINGERPRINT_DEFAULT}"
  [ -f "$fp" ] || die "fingerprint not found: $fp (run 'capture' first, before the import)"
  local fp_abs; fp_abs="$(cd "$(dirname "$fp")" && pwd)/$(basename "$fp")"

  local tmp; tmp="$(mktemp -d)"
  # shellcheck disable=SC2064
  trap "rm -rf '$tmp'" EXIT
  echo "Mirror-cloning the import (all refs + tag objects) …"
  git clone --quiet --mirror "$url" "$tmp/import.git" || die "clone failed: $url"

  local actual; actual="$(cd "$tmp/import.git" && git_fields)"
  # Extract the same fields from the fingerprint by SECTION, not by pattern-guessing line shapes.
  # A `grep -E '^v[0-9]'` would both over-match (a branch named `v1-spike` in the inventory) and
  # under-match (a legitimate pipeline-triggering tag like `vNext`), so slice structurally instead:
  # the three named scalars, then everything between the tag header and the next `---` section.
  local expected
  expected="$(awk '
    /^HEAD /{print; next} /^COMMITS_HEAD /{print; next} /^COMMITS_BRANCHES_TAGS /{print; next}
    /^--- v\* tag inventory/{print; intag=1; next}
    # End the tag section on the next section header OR on any line that is not a `name | … | … | …`
    # row — the ISSUES_*/PRS scalars follow the tag block without a header of their own, and an
    # earlier version of this filter silently swallowed them into the comparison.
    intag && (/^--- / || $0 !~ / \| /){intag=0}
    intag{print}
  ' "$fp_abs")"

  echo
  if diff <(printf '%s\n' "$expected") <(printf '%s\n' "$actual"); then
    echo "MATCH: HEAD, commit counts, and every v* tag (peeled sha + type + message hash) agree."
    echo
    echo "--- branch inventory IN THE IMPORT (compare against the fingerprint's own block) ---"
    (cd "$tmp/import.git" && git for-each-ref --format='%(refname:short) | %(objectname)' refs/heads)
    echo "--- fingerprint's recorded branch inventory ---"
    awk '/^--- branch inventory/{inb=1;next} inb&&/^--- /{inb=0} inb' "$fp_abs"
    echo
    echo "Branches are printed, NOT diffed: step 1's pruning legitimately changes the set between"
    echo "capture and import, so a human decides. Also still to check by hand: issue/MR counts, and"
    echo "authorship, labels, comment/review threads and attachments on sampled items."
  else
    echo >&2
    echo "MISMATCH (< expected fingerprint, > the import). ABORT the cutover." >&2
    echo "A 'type' column reading 'commit' instead of 'tag' means an annotated tag arrived lightweight." >&2
    exit 1
  fi
}

case "${1:-}" in
  capture) shift; cmd_capture "$@" ;;
  verify)  shift; cmd_verify  "$@" ;;
  *) die "usage: $0 {capture [outfile] | verify <gitlab-clone-url> [fingerprint-file]}" ;;
esac
