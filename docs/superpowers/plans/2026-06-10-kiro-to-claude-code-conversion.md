# Kiro-CLI → Claude Code Conversion Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Convert *Steps of Babylon* from Kiro-CLI tooling to Claude Code tooling, completing the committed project-memory spine via a SessionStart preflight hook + a `/checkpoint` skill, folding `AGENTS.md` into a new canonical `CLAUDE.md`, and re-homing `.kiro/steering/` reference docs to `docs/steering/`.

**Architecture:** Docs/config only — zero production code, Gradle, or schema changes. The existing `docs/agent/` spine (STATE/RUN_LOG/CONSTRAINTS/START_HERE/DECISIONS) is kept as-is. Kiro's two always-on behavioral docs are *absorbed* into `CLAUDE.md`; the eight reference docs are *moved* to `docs/steering/`; `.kiro/` and `AGENTS.md` are deleted. The whole change is executed on a feature branch and is the rollback unit.

**Tech Stack:** Bash (hook), Markdown (CLAUDE.md, SKILL.md, ADR), JSON (settings.json), git. No build tooling involved.

**Spec:** `docs/superpowers/specs/2026-06-10-kiro-to-claude-code-conversion-design.md` (hardened after a 6-lens adversarial review).

---

## Conventions for this plan

- This is a documentation/config migration, not application code. The TDD loop here is **write the verification → watch it fail → make the change → watch it pass**. "Tests" are concrete shell assertions (grep, `wc -c`, `jq`, the hook smoke-test, the fold-completeness gate).
- All paths are repo-relative to `/Users/jpawhite/Documents/Claude/steps-of-babylon`.
- The branch `feat/kiro-to-claude-code` **already exists** and the design spec is already committed on it (commit `2dd87f3`). Tasks build on that branch.
- Commit after each task. Commit messages use Conventional Commits.

---

## File structure (what this plan creates / moves / deletes)

**Create:**
- `CLAUDE.md` — single canonical guide (always-on rules + agent protocol + folded AGENTS.md body + spine pointer).
- `.claude/hooks/session-preflight.sh` — SessionStart hook (read-at-start).
- `.claude/skills/checkpoint/SKILL.md` — `/checkpoint` skill (write-at-end).
- `.claude/settings.json` — registers the hook.
- `docs/agent/DECISIONS/ADR-0019-kiro-to-claude-code.md` — records the conversion decision.

**Move (`git mv`, strip dead front-matter from the 3 `fileMatch` docs):**
- `.kiro/steering/{tech,structure,source-files,product,lib-coroutines,lib-hilt,lib-jetpack-compose,lib-room}.md` → `docs/steering/`

**Modify (live cross-refs + dogfood):**
- `README.md`, `docs/steering/tech.md` (post-move), `docs/agent/CONSTRAINTS.md`
- `docs/agent/STATE.md`, `docs/agent/RUN_LOG.md`, `CHANGELOG.md` (dogfood the protocol)

**Delete:**
- `.kiro/` (entire tree)
- `AGENTS.md`

---

## Task 1: Capture baselines for the verification gates

**Files:**
- Create: `/tmp/kiro-conversion/agents-headers.txt`, `/tmp/kiro-conversion/agents-size.txt`, `/tmp/kiro-conversion/snapshots/` (scratch, not committed)

This task captures pre-change facts the later fold-completeness (§7.7) and body-compare (§7.6) gates depend on. AGENTS.md is deleted in Task 8, so its section headers must be snapshotted **now**.

- [ ] **Step 1: Confirm starting point**

Run:
```bash
cd /Users/jpawhite/Documents/Claude/steps-of-babylon
git branch --show-current
git log --oneline -1
```
Expected: branch `feat/kiro-to-claude-code`; HEAD is `2dd87f3 docs(spec): Kiro-CLI → Claude Code conversion + memory spine design`.

- [ ] **Step 2: Snapshot AGENTS.md `##` section headers + size**

Run:
```bash
mkdir -p /tmp/kiro-conversion/snapshots
grep -nE '^## ' AGENTS.md | tee /tmp/kiro-conversion/agents-headers.txt
wc -c AGENTS.md | tee /tmp/kiro-conversion/agents-size.txt
```
Expected: a list of `## ` headers — **Project Overview, Project Memory (read first), Tech Stack, Architecture, Plans & Roadmap, Key Domain Concepts, Conventions, Battle Renderer, Testing, Important Notes** — and a size around 45 KB. Record these; Task 6's gate asserts each header (except the two that get *merged* — see Task 6) appears in CLAUDE.md.

- [ ] **Step 3: Snapshot the 8 reference docs' bodies (front-matter stripped) for the post-move body-compare**

Run:
```bash
for f in tech structure source-files product lib-coroutines lib-hilt lib-jetpack-compose lib-room; do
  # strip a leading YAML front-matter block if present, store the body
  awk 'NR==1 && $0=="---"{fm=1; next} fm && $0=="---"{fm=0; next} !fm{print}' \
    ".kiro/steering/$f.md" > "/tmp/kiro-conversion/snapshots/$f.body"
done
ls -la /tmp/kiro-conversion/snapshots/
```
Expected: 8 `.body` files. These are the front-matter-stripped bodies; Task 2's verification diffs the moved files' bodies against these (allowing only `tech.md`'s line-88 reword in Task 7).

- [ ] **Step 4: No commit (scratch only)**

This task writes only to `/tmp`. Nothing to commit.

---

## Task 2: Move the 8 reference docs to `docs/steering/` and strip dead Kiro front-matter

**Files:**
- Move: `.kiro/steering/{tech,structure,source-files,product,lib-coroutines,lib-hilt,lib-jetpack-compose,lib-room}.md` → `docs/steering/`
- Modify (front-matter strip): `docs/steering/{lib-coroutines,lib-jetpack-compose,lib-room}.md`

- [ ] **Step 1: Write the failing verification**

Run:
```bash
test -d docs/steering && echo "EXISTS" || echo "MISSING"
```
Expected: `MISSING` (the move hasn't happened yet).

- [ ] **Step 2: `git mv` the 8 docs**

Run:
```bash
mkdir -p docs/steering
for f in tech structure source-files product lib-coroutines lib-hilt lib-jetpack-compose lib-room; do
  git mv ".kiro/steering/$f.md" "docs/steering/$f.md"
done
ls docs/steering/
```
Expected: 8 `.md` files under `docs/steering/`.

- [ ] **Step 3: Strip the dead `inclusion: fileMatch` front-matter from the 3 lib docs**

These three files begin with a 4-line Kiro front-matter block (`---` / `inclusion: fileMatch` / `fileMatchPattern: "..."` / `---`). Remove that block from each, leaving a blank first line removed too so the file starts at its `# ` heading.

Run:
```bash
for f in lib-coroutines lib-jetpack-compose lib-room; do
  awk 'NR==1 && $0=="---"{fm=1; next} fm && $0=="---"{fm=0; next} fm{next} {print}' \
    "docs/steering/$f.md" > "docs/steering/$f.md.tmp"
  # drop a single leading blank line if present
  sed '1{/^$/d}' "docs/steering/$f.md.tmp" > "docs/steering/$f.md"
  rm "docs/steering/$f.md.tmp"
  echo "=== $f.md now starts with: ==="
  head -2 "docs/steering/$f.md"
done
```
Expected: each file now starts with its `# … Reference Guide` heading (no `---` / `inclusion:` lines).

- [ ] **Step 4: Run the body-compare verification (passes)**

Run:
```bash
for f in tech structure source-files product lib-coroutines lib-hilt lib-jetpack-compose lib-room; do
  awk 'NR==1 && $0=="---"{fm=1; next} fm && $0=="---"{fm=0; next} !fm{print}' \
    "docs/steering/$f.md" > "/tmp/kiro-conversion/snapshots/$f.body.post"
  if diff -q "/tmp/kiro-conversion/snapshots/$f.body" "/tmp/kiro-conversion/snapshots/$f.body.post" >/dev/null; then
    echo "✓ $f body unchanged"
  else
    echo "✗ $f body DIFFERS (expected only for tech.md after Task 7):"; diff "/tmp/kiro-conversion/snapshots/$f.body" "/tmp/kiro-conversion/snapshots/$f.body.post" | head
  fi
done
```
Expected: **all 8 print `✓ … unchanged`** at this point (the `tech.md` Kiro-CLI reword happens later in Task 7; until then its body still matches). The front-matter strip is invisible to this check because the snapshot in Task 1 already stripped front-matter — confirming the strip removed *only* front-matter and no body content.

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "refactor(docs): move .kiro/steering reference docs to docs/steering/

git mv the 8 reference docs (tech, structure, source-files, product,
lib-*) out of Kiro's steering folder into docs/steering/. Strip the dead
inclusion: fileMatch front-matter from the 3 lib docs (inert under Claude
Code). Behavioral docs 10/11 are not moved — absorbed into CLAUDE.md in a
later task. No body content changed."
```

---

## Task 3: Author and smoke-test the SessionStart preflight hook

**Files:**
- Create: `.claude/hooks/session-preflight.sh`

This is the read-at-start half of the spine. It prints plain text (Claude Code adds a SessionStart hook's stdout to context — no JSON wrapper needed), bounded well under the 10,000-char `additionalContext` cap, selecting STATE.md's live forward-looking sections rather than its giant history bullets. The script below is verified working (8,016 chars on the current tree, all content assertions pass).

- [ ] **Step 1: Write the failing smoke-test**

Run:
```bash
test -x .claude/hooks/session-preflight.sh && echo "EXISTS" || echo "MISSING"
```
Expected: `MISSING`.

- [ ] **Step 2: Create the hook script**

Create `.claude/hooks/session-preflight.sh` with exactly this content:

```bash
#!/usr/bin/env bash
# SessionStart preflight hook — prints repo state + the live, forward-looking
# sections of docs/agent/STATE.md so a fresh Claude Code session is grounded in
# current project truth (the "read-at-start" half of the committed memory spine).
#
# Output is plain text on stdout: Claude Code adds a SessionStart hook's stdout
# directly to the session context (no JSON wrapper required for a context-only
# hook). Kept dependency-free (bash + coreutils + awk/sed) and bounded well under
# the 10,000-char additionalContext cap by selecting STATE.md's live sections
# rather than its long historical "Current objective" / "Previous objective"
# bullets. Always exits 0 so a hook failure never blocks a session.
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
  # Newest "## Current objective" bullet only, capped at 1500 chars.
  awk '
    /^## Current objective/ { inco=1; next }
    /^## / && inco { exit }
    inco && /^- / { if (got) exit; got=1 }
    inco && got { print }
  ' "$STATE" | head -c 1500
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
```

- [ ] **Step 3: Make it executable**

Run:
```bash
chmod +x .claude/hooks/session-preflight.sh
```

- [ ] **Step 4: Run the smoke-test (passes)**

Run:
```bash
bash .claude/hooks/session-preflight.sh > /tmp/kiro-conversion/preflight.out 2>&1; echo "exit=$?"
chars=$(wc -c < /tmp/kiro-conversion/preflight.out); echo "chars=$chars"
[ "$chars" -lt 10000 ] && echo "✓ under 10000-char cap" || echo "✗ OVER cap"
grep -q "Branch:" /tmp/kiro-conversion/preflight.out && echo "✓ branch" || echo "✗ branch"
grep -q "Recent commits:" /tmp/kiro-conversion/preflight.out && echo "✓ commits" || echo "✗ commits"
grep -q "## Top priorities" /tmp/kiro-conversion/preflight.out && echo "✓ Top priorities" || echo "✗ Top priorities"
grep -q "## Next actions" /tmp/kiro-conversion/preflight.out && echo "✓ Next actions" || echo "✗ Next actions"
grep -q "## Do-not-touch" /tmp/kiro-conversion/preflight.out && echo "✓ Do-not-touch" || echo "✗ Do-not-touch"
grep -qi "Previous objective" /tmp/kiro-conversion/preflight.out && echo "✗ LEAKED history" || echo "✓ no history leak"
grep -q "see docs/agent/STATE.md for full state" /tmp/kiro-conversion/preflight.out && echo "✓ pointer" || echo "✗ pointer"
```
Expected: `exit=0`, `chars` < 10000 (≈8000), and **eight `✓` lines, zero `✗`**.

- [ ] **Step 5: Commit**

```bash
git add .claude/hooks/session-preflight.sh
git commit -m "feat(claude): add SessionStart preflight hook (read-at-start)

Plain-stdout hook that injects git state + STATE.md's live forward-looking
sections (newest objective bullet + Top priorities + Next actions +
Do-not-touch), bounded under the 10k additionalContext cap. Dependency-free;
always exits 0; degrades gracefully outside a git repo."
```

---

## Task 4: Author the `/checkpoint` skill (write-at-end)

**Files:**
- Create: `.claude/skills/checkpoint/SKILL.md`

This encodes the project's existing end-of-run protocol (from the old `.kiro/steering/11-agent-protocol.md`) as an explicit, user/model-invocable checklist.

- [ ] **Step 1: Write the failing verification**

Run:
```bash
test -f .claude/skills/checkpoint/SKILL.md && echo "EXISTS" || echo "MISSING"
```
Expected: `MISSING`.

- [ ] **Step 2: Create the skill file**

Create `.claude/skills/checkpoint/SKILL.md` with exactly this content:

```markdown
---
name: checkpoint
description: Run at the END of a work session to write project memory. Performs the doc-drift sweep, syncs current-state docs, updates docs/agent/STATE.md, appends docs/agent/RUN_LOG.md, and adds an ADR if a non-trivial decision was made. Use when finishing a task, before committing a code-changing PR, or when the user says "checkpoint", "end of session", "update memory", or "sync the docs".
disable-model-invocation: false
---

# /checkpoint — End-of-Session Memory Write

The write-at-end half of the committed project-memory spine. Run this before ending a
work session or finishing a code-changing PR. It is the counterpart to the SessionStart
preflight hook (read-at-start).

## When to run
- After completing a task or a code-changing PR, immediately before the commit step.
- When the user asks to "checkpoint", "update memory", "sync docs", or end the session.

## Checklist (do these in order)

Create a TodoWrite item per numbered step and complete them in order.

### 1. Doc-drift sweep
Review the current-state docs below and decide which the change actually invalidated.
Touch ONLY those — do not rewrite docs the change didn't affect.

| Doc | Update when… |
|---|---|
| `CLAUDE.md` | test count, architecture map, plan/roadmap status, tech stack, or conventions changed. ALWAYS update when the test count changes. |
| `CHANGELOG.md` | any PR — add a new section; update the current-state block if phase status / test count / roadmap shifted. |
| `docs/steering/source-files.md` | a source file was added, or an existing file's responsibility shape changed (new method/dependency/capability). |
| `docs/steering/structure.md` | new modules, directories, or architectural elements landed. |
| `docs/database-schema.md` | the Room schema or a migration changed. |
| `docs/steering/tech.md`, `docs/steering/lib-*.md` | dependency versions, conventions, or library-specific patterns changed. |
| `README.md` | user-facing build/run instructions changed. |

### 2. Sync the affected current-state docs
Apply the edits identified in step 1.

### 3. Update `docs/agent/STATE.md`
- Rotate the `## Current objective` section: new objective on top, prior one demoted to a
  `Previous objective` bullet.
- Keep STATE.md to roughly one page of *live* content — push detail into RUN_LOG / ADRs.
- Update `## Top priorities`, `## Next actions`, `## Do-not-touch / fragile zones` if they shifted.

### 4. Append `docs/agent/RUN_LOG.md`
- Add a NEW entry at the top (newest-first) with: goal, what changed, verification evidence
  (build/test results), doc-sync list, and what remains / next.
- NEVER edit prior RUN_LOG entries.

### 5. Add/update an ADR (only if a non-trivial decision was made)
- New decision → new `docs/agent/DECISIONS/ADR-NNNN-<slug>.md` from `ADR-0001-template.md`.
- Amend an existing ADR's status only if this change explicitly warrants it.

## Historical artifacts — NEVER modify
Appending a new RUN_LOG entry is fine; editing the past is not. Leave these untouched:
- Prior `docs/agent/RUN_LOG.md` entries.
- `docs/plans/plan-R*.md`, `docs/plans/plan-R2*.md` and other dated plan detail blocks.
- `docs/external-reviews/*` — historical at review date.
- `devdocs/*`, `smoke_tests/*` — historical per HEAD pin.
- Existing `docs/agent/DECISIONS/ADR-*.md` bodies — amend status only if explicitly warranted.

## PR Task-List Convention
For every PR that changes production code, tests, or config, the doc sync (steps 1–2) runs
BEFORE the STATE/RUN_LOG update (steps 3–4), and both run immediately before the commit step.
```

- [ ] **Step 3: Verify the front-matter is valid YAML with the required keys**

Run:
```bash
awk 'NR==1 && $0=="---"{fm=1; next} fm && $0=="---"{exit} fm{print}' .claude/skills/checkpoint/SKILL.md
```
Expected: three lines — `name: checkpoint`, a `description: …` line, and `disable-model-invocation: false`.

If `python3` is available, additionally validate it parses as YAML-ish key/values:
```bash
python3 - <<'PY'
import re,sys
txt=open('.claude/skills/checkpoint/SKILL.md').read()
m=re.match(r'^---\n(.*?)\n---\n', txt, re.S)
assert m, "no front-matter block"
fm=m.group(1)
for key in ('name:','description:','disable-model-invocation:'):
    assert key in fm, f"missing {key}"
assert 'name: checkpoint' in fm
print("✓ front-matter OK")
PY
```
Expected: `✓ front-matter OK`.

- [ ] **Step 4: Commit**

```bash
git add .claude/skills/checkpoint/SKILL.md
git commit -m "feat(claude): add /checkpoint skill (write-at-end)

Encodes the project's end-of-run protocol (doc-drift sweep, current-state
doc sync, STATE rotation, RUN_LOG append, maybe-ADR) as a user/model-invocable
skill, lifted from the old .kiro/steering/11-agent-protocol.md."
```

---

## Task 5: Register the hook in `.claude/settings.json`

**Files:**
- Create: `.claude/settings.json`

- [ ] **Step 1: Write the failing verification**

Run:
```bash
test -f .claude/settings.json && echo "EXISTS" || echo "MISSING"
```
Expected: `MISSING`.

- [ ] **Step 2: Create settings.json**

Create `.claude/settings.json` with exactly this content (matcher `*` so preflight re-injects on startup/resume/clear/compact; registers additively alongside any user-level SessionStart hooks):

```json
{
  "hooks": {
    "SessionStart": [
      {
        "matcher": "*",
        "hooks": [
          {
            "type": "command",
            "command": "$CLAUDE_PROJECT_DIR/.claude/hooks/session-preflight.sh"
          }
        ]
      }
    ]
  }
}
```

- [ ] **Step 3: Validate JSON + shape**

Run:
```bash
jq empty .claude/settings.json && echo "✓ valid JSON"
jq -e '.hooks.SessionStart[0].matcher == "*"' .claude/settings.json >/dev/null && echo "✓ matcher *"
jq -r '.hooks.SessionStart[0].hooks[0].command' .claude/settings.json
jq -e '.hooks.SessionStart[0].hooks[0].type == "command"' .claude/settings.json >/dev/null && echo "✓ type command"
```
Expected: `✓ valid JSON`, `✓ matcher *`, the command path printed, `✓ type command`.

- [ ] **Step 4: Confirm `$CLAUDE_PROJECT_DIR` resolution + hook path exist**

Run:
```bash
# The path uses $CLAUDE_PROJECT_DIR (set by Claude Code to the project root at hook run time).
# Confirm the target file exists relative to the repo root.
test -x .claude/hooks/session-preflight.sh && echo "✓ hook present + executable"
```
Expected: `✓ hook present + executable`.

> Implementation note: `$CLAUDE_PROJECT_DIR` is the documented Claude Code variable for the project root inside hook commands. If a docs re-confirmation shows a different canonical variable name, update the `command` string accordingly before committing; the hook itself resolves its own root independently (via `BASH_SOURCE`), so the variable is only used to locate the script.

- [ ] **Step 5: Commit**

```bash
git add .claude/settings.json
git commit -m "feat(claude): register SessionStart hook in .claude/settings.json

Matcher '*' so the preflight re-injects on startup/resume/clear/compact;
registers additively alongside any user-level SessionStart hooks."
```

---

## Task 6: Author `CLAUDE.md` (fold + merge) and run the fold-completeness gate

**Files:**
- Create: `CLAUDE.md`
- Reference (read, do not yet delete): `AGENTS.md`, the old behavioral rules now captured below.

`CLAUDE.md` is built as: **[A] always-on memory rules** + **[B] agent protocol** + **[C] the AGENTS.md body, refs rewritten** + **[D] spine pointer**. Sections [A], [B], [D] replace AGENTS.md's own `## Project Memory (read first)` table and its 3 operating-rule bullets (those are MERGED here, not duplicated). Every OTHER AGENTS.md `## ` section is carried over.

- [ ] **Step 1: Write the failing gate**

Run:
```bash
test -f CLAUDE.md && echo "EXISTS" || echo "MISSING"
```
Expected: `MISSING`.

- [ ] **Step 2: Create CLAUDE.md — header (sections A, B, D)**

Create `CLAUDE.md` starting with exactly this content (this is sections A + B + the spine pointer D; section C follows in Step 3):

````markdown
# CLAUDE.md — Steps of Babylon

This is the single canonical guide for working in this repository. It is auto-loaded every
session. Authority lives here; *progress and decisions* live in the committed memory spine
under `docs/agent/` (which points back here and never restates these rules).

## Project Memory (read first)

Treat these as the project source of truth — never rely on chat history.

| File | Purpose |
|---|---|
| `docs/agent/START_HERE.md` | Agent contract — what this is, how to work here |
| `docs/agent/STATE.md` | One-page live snapshot (current objective, priorities, next actions, fragile zones) |
| `docs/agent/CONSTRAINTS.md` | Architecture invariants, security rules, "never do" list |
| `docs/agent/RUN_LOG.md` | Append-only log of every work session |
| `docs/agent/DECISIONS/` | Architecture Decision Records (ADRs) |
| `docs/steering/` | Reference docs (tech stack, structure, source-file index, library guides) — read on demand |

## Always-on memory rules

- The memory spine (above) is truth. Do NOT rely on chat history as the project source of truth.
- **Before** planning or changing code: read the spine + check git state. (The SessionStart
  preflight hook injects git state + STATE.md's live sections automatically.)
- **After** finishing work: update `docs/agent/STATE.md` + append `docs/agent/RUN_LOG.md`.
- If you made/changed a meaningful decision: create/update an ADR in `docs/agent/DECISIONS/`.
- Keep `STATE.md` to one page. Push detail into RUN_LOG / ADRs.
- The end-of-session write is automated by the `/checkpoint` skill — run it to finish a session.

## Agent protocol

### Context Preflight (at session start)
1. Read `docs/agent/START_HERE.md`, `STATE.md`, `CONSTRAINTS.md`.
2. Review the latest `RUN_LOG.md` entry and any ADRs referenced in `STATE.md`.
3. Check repo state: `git status`, `git log -n 10 --oneline`.
4. Output a brief "Session Brief" (~10 bullets): what the project is, current state,
   constraints/invariants, today's objective, risks/unknowns.

### PR Task-List Convention (mandatory for every code-changing PR)
Every task list for a PR that changes production code, tests, or configuration MUST include
these two steps, in this order, immediately before the commit step:
1. **Sync current-state docs** affected by the change (runs BEFORE the STATE/RUN_LOG update).
2. **Update `docs/agent/STATE.md` + append `docs/agent/RUN_LOG.md`.**

Current-state docs to audit for every PR (touch only if the PR actually invalidates them):
- `CLAUDE.md` — test count, architecture, plan status, conventions. ALWAYS update when test count changes.
- `CHANGELOG.md` — add a section for the PR; update the current-state block if phase status / test count / roadmap shifted.
- `docs/steering/source-files.md` — add entries for new files; update existing entries when a file's responsibility shape changed.
- `docs/steering/structure.md` — update when new modules/directories/architectural elements land.
- `docs/database-schema.md` — only if the Room schema or a migration changed.
- `docs/steering/tech.md`, `docs/steering/lib-*.md` — only if dependency versions/conventions/patterns changed.
- `README.md` — only if user-facing build/run instructions changed.

Historical artifacts — **NEVER modify** in a current-PR doc sweep:
- `docs/agent/RUN_LOG.md` prior entries (appending the current PR's entry is fine; editing old ones is not).
- `docs/plans/plan-R*.md`, `docs/plans/plan-R2*.md` — historical at authoring date.
- `docs/external-reviews/*` — historical at review date.
- `devdocs/*`, `smoke_tests/*` — historical per HEAD pin.
- Individual `docs/agent/DECISIONS/ADR-*.md` files — amend status only if explicitly warranted.

### End-of-Run memory writes (run `/checkpoint`)
1. Current-state docs synced per the PR Task-List Convention above.
2. Update `docs/agent/STATE.md` (what changed + what's next).
3. Append `docs/agent/RUN_LOG.md` with what you did and what remains.
4. Add/update an ADR if you made a non-trivial decision.

---
````

- [ ] **Step 3: Append CLAUDE.md — section C (the AGENTS.md body, refs rewritten)**

Append to `CLAUDE.md` the **entire body of `AGENTS.md` starting from its `## Tech Stack` section through the end** (i.e. every section EXCEPT AGENTS.md's title line, its `## Project Overview` opener — see note — its `## Project Memory (read first)` table, and its operating-rule bullets, which are replaced by sections A/B/D above).

Carry these sections over verbatim **except** apply these exact ref rewrites inside the copied text:

| In the copied AGENTS.md text | Replace with |
|---|---|
| `see \`.kiro/steering/11-agent-protocol.md\`` (Project Memory operating-rules area — already replaced by section B, so this line is dropped) | *(dropped — do not carry the operating-rules bullets)* |
| `` `.kiro/steering/source-files.md` `` (Architecture section, "See … for the full source file index.") | `` `docs/steering/source-files.md` `` |
| `(e.g., Kiro CLI, CI)` (Important Notes, Gradle non-TTY bullet) | `(e.g., CI or other non-TTY environments)` |

Also carry over AGENTS.md's `## Project Overview` section (the opening paragraph + GDD pointer) — place it immediately after the `---` separator from Step 2, so CLAUDE.md reads: header (A/B/D) → Project Overview → Tech Stack → Architecture → Plans & Roadmap → Key Domain Concepts → Conventions → Battle Renderer → Testing → Important Notes.

> Practical method: copy `AGENTS.md` lines 24–297 (from `## Tech Stack` to EOF) into CLAUDE.md, then copy AGENTS.md's `## Project Overview` block (lines 3–8) in just above `## Tech Stack`. Then run the 3 ref rewrites with your editor. Confirm by grep in Step 4.

- [ ] **Step 4: Run the fold-completeness gate (passes)**

Run:
```bash
echo "=== every AGENTS.md ## header (minus the 2 merged ones) must appear in CLAUDE.md ==="
missing=0
while IFS= read -r h; do
  case "$h" in
    "## Project Memory (read first)") continue ;;  # merged into section A/B/D
  esac
  if grep -qF "$h" CLAUDE.md; then echo "✓ $h"; else echo "✗ MISSING: $h"; missing=1; fi
done < <(grep -E '^## ' AGENTS.md)
echo "missing=$missing  (must be 0)"

echo "=== sections A/B/D present ==="
grep -q "## Always-on memory rules" CLAUDE.md && echo "✓ memory rules" || echo "✗ memory rules"
grep -q "## Agent protocol" CLAUDE.md && echo "✓ protocol" || echo "✗ protocol"
grep -q "## Project Memory (read first)" CLAUDE.md && echo "✓ spine pointer" || echo "✗ spine pointer"

echo "=== ref rewrites applied (no Kiro paths remain in CLAUDE.md) ==="
grep -nE '\.kiro/|AGENTS\.md' CLAUDE.md && echo "✗ stale ref in CLAUDE.md" || echo "✓ no .kiro/ or AGENTS.md refs"
grep -q 'docs/steering/source-files.md' CLAUDE.md && echo "✓ source-files ref rewritten" || echo "✗ source-files ref"
```
Expected: every AGENTS.md header prints `✓` (the `## Project Memory (read first)` one is skipped), `missing=0`, sections A/B/D all `✓`, `✓ no .kiro/ or AGENTS.md refs`, `✓ source-files ref rewritten`.

- [ ] **Step 5: Commit**

```bash
git add CLAUDE.md
git commit -m "feat(claude): add CLAUDE.md as the single canonical guide

Folds AGENTS.md into CLAUDE.md and prepends the always-on memory rules +
agent protocol (absorbed from .kiro/steering/10 + 11), merging AGENTS.md's
own spine table + operating-rule bullets rather than duplicating them.
Rewrites .kiro/steering/source-files.md -> docs/steering/ and the Kiro-CLI
non-TTY mention. Fold-completeness gate: every AGENTS.md section preserved."
```

---

## Task 7: Rewrite live cross-references

**Files:**
- Modify: `README.md` (lines 61, 63, 114, 127), `docs/steering/tech.md` (line 88), `docs/agent/CONSTRAINTS.md` (line 38)

Only these three files contain live refs (verified by reading). `docs/agent/START_HERE.md` and the other 7 reference docs are already clean — do not edit them.

- [ ] **Step 1: Write the failing verification**

Run:
```bash
echo "--- README live refs (expect AGENTS.md x3 + Kiro CLI + .kiro/steering/tech.md) ---"
grep -nE 'AGENTS\.md|Kiro CLI|\.kiro/steering' README.md
echo "--- tech.md (expect 1 Kiro CLI) ---"
grep -nE 'Kiro CLI' docs/steering/tech.md
echo "--- CONSTRAINTS.md (expect 1 Kiro CLI) ---"
grep -nE 'Kiro CLI' docs/agent/CONSTRAINTS.md
```
Expected: README shows the 3 `AGENTS.md` link refs + the `Kiro CLI` heading + the `.kiro/steering/tech.md` link; tech.md + CONSTRAINTS.md each show one `Kiro CLI` string. These are what we will remove.

- [ ] **Step 2: Edit `README.md`**

Make these exact replacements:

1. Line 61 — `See [AGENTS.md](AGENTS.md) for the full coverage breakdown.` → `See [CLAUDE.md](CLAUDE.md) for the full coverage breakdown.`
2. Line 63 — heading `### Non-TTY Environments (Kiro CLI, CI, etc.)` → `### Non-TTY Environments (CI, etc.)`
3. Line 114 — table row `| [AGENTS.md](AGENTS.md) | Full tech stack, conventions, status checklist, test coverage |` → `| [CLAUDE.md](CLAUDE.md) | Full tech stack, conventions, status checklist, test coverage |`
4. Line 127 — `See [AGENTS.md](AGENTS.md) for the full tech stack with versions and conventions, and [.kiro/steering/tech.md](.kiro/steering/tech.md) for the canonical version table.` → `See [CLAUDE.md](CLAUDE.md) for the full tech stack with versions and conventions, and [docs/steering/tech.md](docs/steering/tech.md) for the canonical version table.`

- [ ] **Step 3: Edit `docs/steering/tech.md`**

Line 88 — replace:
`In non-TTY environments (Kiro CLI, CI), use \`./run-gradle.sh <task>\` instead of \`./gradlew\` to avoid output buffering. See \`README.md\` for the script.`
with:
`In non-TTY environments (CI, etc.), use \`./run-gradle.sh <task>\` instead of \`./gradlew\` to avoid output buffering. See \`README.md\` for the script.`

- [ ] **Step 4: Edit `docs/agent/CONSTRAINTS.md`**

Line 38 — replace:
`- Use \`./run-gradle.sh\` in non-TTY environments (Kiro CLI, CI).`
with:
`- Use \`./run-gradle.sh\` in non-TTY environments (CI, etc.).`

- [ ] **Step 5: Run the verification (passes)**

Run:
```bash
echo "--- no live AGENTS.md refs in README ---"
grep -nE 'AGENTS\.md' README.md && echo "✗ still present" || echo "✓ README clean of AGENTS.md"
echo "--- no .kiro/ refs in README ---"
grep -nE '\.kiro/' README.md && echo "✗ still present" || echo "✓ README clean of .kiro/"
echo "--- no 'Kiro CLI' in the 3 edited files ---"
grep -nE 'Kiro CLI' README.md docs/steering/tech.md docs/agent/CONSTRAINTS.md && echo "✗ still present" || echo "✓ no Kiro CLI strings"
echo "--- tech.md body now differs from snapshot by exactly the line-88 reword ---"
awk 'NR==1 && $0=="---"{fm=1; next} fm && $0=="---"{fm=0; next} !fm{print}' docs/steering/tech.md > /tmp/kiro-conversion/snapshots/tech.body.post2
diff /tmp/kiro-conversion/snapshots/tech.body /tmp/kiro-conversion/snapshots/tech.body.post2
```
Expected: `✓ README clean of AGENTS.md`, `✓ README clean of .kiro/`, `✓ no Kiro CLI strings`, and the final `diff` shows exactly one changed line (the Kiro-CLI reword) and nothing else.

- [ ] **Step 6: Commit**

```bash
git add README.md docs/steering/tech.md docs/agent/CONSTRAINTS.md
git commit -m "docs: repoint live cross-refs to CLAUDE.md / docs/steering/

README AGENTS.md links -> CLAUDE.md; .kiro/steering/tech.md -> docs/steering/tech.md;
'Kiro CLI' non-TTY mentions reworded in README, tech.md, CONSTRAINTS.md."
```

---

## Task 8: Delete `.kiro/` and `AGENTS.md`

**Files:**
- Delete: `.kiro/` (entire tree), `AGENTS.md`

Sources are now fully absorbed (10/11 → CLAUDE.md) or moved (8 reference docs → docs/steering/). Safe to delete.

- [ ] **Step 1: Confirm nothing live still references them (pre-delete)**

Run:
```bash
echo "=== remaining .kiro/ refs by file (history is OK; live docs must be clean) ==="
git grep -n '\.kiro/' -- \
  ':(exclude)docs/agent/RUN_LOG.md' ':(exclude)docs/agent/STATE.md' \
  ':(exclude)CHANGELOG.md' ':(exclude)docs/plans/*' ':(exclude)docs/external-reviews/*' \
  ':(exclude)devdocs/*' ':(exclude)smoke_tests/*' ':(exclude)docs/agent/DECISIONS/*' \
  ':(exclude)docs/superpowers/*' ':(exclude).kiro/*' \
  || echo "✓ no live .kiro/ refs outside history"
echo "=== remaining live AGENTS.md refs ==="
git grep -n 'AGENTS\.md' -- \
  ':(exclude)docs/agent/RUN_LOG.md' ':(exclude)docs/agent/STATE.md' \
  ':(exclude)CHANGELOG.md' ':(exclude)docs/plans/*' ':(exclude)docs/external-reviews/*' \
  ':(exclude)devdocs/*' ':(exclude)smoke_tests/*' ':(exclude)docs/agent/DECISIONS/*' \
  ':(exclude)docs/superpowers/*' ':(exclude)AGENTS.md' \
  || echo "✓ no live AGENTS.md refs outside history"
```
Expected: both print their `✓ … outside history` line (the `.kiro/*` and `AGENTS.md` self-excludes keep the soon-to-be-deleted files from matching). If any OTHER live file appears, fix it before deleting.

- [ ] **Step 2: Delete**

Run:
```bash
git rm -r .kiro
git rm AGENTS.md
```

- [ ] **Step 3: Verify deletion + spine intact**

Run:
```bash
test -d .kiro && echo "✗ .kiro still exists" || echo "✓ .kiro gone"
test -f AGENTS.md && echo "✗ AGENTS.md still exists" || echo "✓ AGENTS.md gone"
ls docs/steering/ | wc -l   # expect 8
test -f CLAUDE.md && echo "✓ CLAUDE.md present" || echo "✗ CLAUDE.md missing"
for f in START_HERE STATE CONSTRAINTS RUN_LOG; do test -f "docs/agent/$f.md" && echo "✓ $f.md" || echo "✗ $f.md"; done
```
Expected: `✓ .kiro gone`, `✓ AGENTS.md gone`, `8`, `✓ CLAUDE.md present`, and 4 `✓` spine files.

- [ ] **Step 4: Commit**

```bash
git commit -m "chore: remove .kiro/ and AGENTS.md (converted to Claude Code)

.kiro/steering behavioral docs absorbed into CLAUDE.md; reference docs moved
to docs/steering/; AGENTS.md folded into CLAUDE.md. .kiro/settings LSP config
removed per the clean-conversion decision."
```

---

## Task 9: Write ADR-0019 (the conversion decision)

**Files:**
- Create: `docs/agent/DECISIONS/ADR-0019-kiro-to-claude-code.md`

- [ ] **Step 1: Write the failing verification**

Run:
```bash
test -f docs/agent/DECISIONS/ADR-0019-kiro-to-claude-code.md && echo "EXISTS" || echo "MISSING"
```
Expected: `MISSING`.

- [ ] **Step 2: Create the ADR (following `ADR-0001-template.md`)**

Create `docs/agent/DECISIONS/ADR-0019-kiro-to-claude-code.md` with exactly this content:

```markdown
# ADR-0019: Convert agent tooling from Kiro-CLI to Claude Code

## Status
Accepted — 2026-06-10.

## Context
- The project was wired to Kiro-CLI: `.kiro/steering/` held two `inclusion: always`
  behavioral docs (memory rules + agent protocol), three `inclusion: fileMatch` library
  reference docs, and five plain reference docs (tech, structure, source-files, product,
  lib-hilt); `.kiro/settings/` held editor LSP config. The project guide was `AGENTS.md`.
- The committed memory spine (`docs/agent/STATE.md`, `RUN_LOG.md`, `CONSTRAINTS.md`,
  `START_HERE.md`, `DECISIONS/`) already existed and is tool-neutral.
- We want Claude Code's native automation (a SessionStart hook for read-at-start; a
  `/checkpoint` skill for write-at-end) to drive the spine instead of Kiro's `inclusion: always`
  mechanism, and a single canonical guide Claude Code auto-loads.

## Decision
- **Clean conversion:** delete `.kiro/` entirely (including the `.kiro/settings/` LSP config).
- Absorb the two behavioral steering docs into a new root `CLAUDE.md`; move the eight reference
  docs to `docs/steering/` (stripping the dead `fileMatch` front-matter from the three lib docs).
- **`CLAUDE.md` is the single canonical guide:** fold `AGENTS.md` into it (merging, not
  duplicating, AGENTS.md's own spine table + operating-rule bullets) and delete `AGENTS.md`.
- Add `.claude/hooks/session-preflight.sh` (plain-stdout SessionStart hook injecting git state +
  STATE.md's live sections, bounded under the 10k additionalContext cap), `.claude/skills/
  checkpoint/SKILL.md`, and `.claude/settings.json` (registers the hook, matcher `*`).
- The `docs/agent/` spine files are NOT renamed (heavy cross-referencing + append-only history).

## Alternatives considered
- A: Coexistence — keep `.kiro/` working and layer `.claude/` on top. Rejected: doesn't actually
  convert away from Kiro; two sources of always-on rules drift.
- B: Keep `AGENTS.md` as the guide and make `CLAUDE.md` a thin pointer / symlink. Rejected by the
  "CLAUDE.md is the guide" decision — one canonical source for Claude Code.
- C: Keep the `.kiro/settings/` LSP config. Rejected by the clean-conversion choice (editor LSP
  config is tool-neutral but was deleted deliberately; can be re-added to editor config if needed).

## Consequences
- Positive: portable committed memory driven by Claude Code's native hook + skill; one canonical
  guide; reference docs read on-demand (the 88 KB source-files index is no longer force-loaded);
  no Kiro-specific config remains.
- Negative / tradeoffs: loss of the tool-neutral `AGENTS.md` filename (other agents that look for
  it won't find it); editor LSP config removed; the SessionStart hook auto-runs on clone-and-open.
- Follow-ups: this change was dogfooded — STATE/RUN_LOG/CHANGELOG updated and `/checkpoint` run
  on it (see RUN_LOG entry 2026-06-10).

## Links
- Commit(s): (this PR / branch `feat/kiro-to-claude-code`)
- Spec: `docs/superpowers/specs/2026-06-10-kiro-to-claude-code-conversion-design.md`
- Plan: `docs/superpowers/plans/2026-06-10-kiro-to-claude-code-conversion.md`
- Related ADRs: ADR-0018 (CI/GitHub Actions — references the old `.kiro/steering/` paths in its
  historical body; left unmodified as dated history).
```

- [ ] **Step 3: Verify**

Run:
```bash
test -f docs/agent/DECISIONS/ADR-0019-kiro-to-claude-code.md && echo "✓ ADR-0019 exists"
grep -q "^## Status" docs/agent/DECISIONS/ADR-0019-kiro-to-claude-code.md && echo "✓ has Status"
grep -q "^## Decision" docs/agent/DECISIONS/ADR-0019-kiro-to-claude-code.md && echo "✓ has Decision"
ls docs/agent/DECISIONS/ | wc -l   # expect 17 now (was 16)
```
Expected: `✓ ADR-0019 exists`, `✓ has Status`, `✓ has Decision`, `17`.

- [ ] **Step 4: Commit**

```bash
git add docs/agent/DECISIONS/ADR-0019-kiro-to-claude-code.md
git commit -m "docs(adr): ADR-0019 Kiro-CLI -> Claude Code conversion"
```

---

## Task 10: Dogfood the protocol — STATE.md + RUN_LOG.md + CHANGELOG.md

**Files:**
- Modify: `docs/agent/STATE.md` (rotate current objective + references), `docs/agent/RUN_LOG.md` (append), `CHANGELOG.md` (add section)

This applies the project's own End-of-Run protocol to the conversion. Prefer to do it by **running the `/checkpoint` skill** (Step 1) — that is the acceptance test that the skill works end-to-end. If executing inline rather than interactively, perform the equivalent writes manually (Steps 2–4) following the skill's checklist.

- [ ] **Step 1: Run `/checkpoint`**

Invoke the `/checkpoint` skill on this change. It should: identify the doc-drift (CLAUDE.md is now the guide; docs/steering/ is the new ref home; the test count is UNCHANGED at 867 since no code changed), then update STATE.md, append RUN_LOG.md, and confirm ADR-0019 already exists. Verify it does not touch historical artifacts.

If `/checkpoint` is unavailable in the execution context, do Steps 2–4 manually.

- [ ] **Step 2: Update `docs/agent/STATE.md`**

Rotate the `## Current objective` section — add a NEW top entry (and demote the existing top bullet to a `Previous objective` bullet). New entry text:

```markdown
- **Tooling — converted Kiro-CLI → Claude Code + completed the committed memory spine (branch `feat/kiro-to-claude-code`, 2026-06-10, ADR-0019).** Deleted `.kiro/` entirely; absorbed the two `inclusion: always` behavioral steering docs (memory rules + agent protocol) into a new canonical root `CLAUDE.md`; moved the 8 reference docs to `docs/steering/` (stripping dead `fileMatch` front-matter from the 3 lib docs); folded `AGENTS.md` into `CLAUDE.md` and deleted it. Added Claude Code automation: `.claude/hooks/session-preflight.sh` (plain-stdout SessionStart hook injecting git state + STATE.md's live sections, ~8 KB, under the 10k cap), `.claude/skills/checkpoint/SKILL.md` (the end-of-run protocol as a skill), `.claude/settings.json` (registers the hook, matcher `*`). Repointed live cross-refs (README, tech.md, CONSTRAINTS.md). No code/test/schema change — test count unchanged at 867. Dogfooded: this entry + RUN_LOG + CHANGELOG + ADR-0019 + `/checkpoint` run on the change itself.
```

Also update the `## References` section: change `Master plan` and any spine pointers as needed, and note CLAUDE.md is now the guide. (Do NOT rewrite the dated historical bullets that mention `.kiro/steering/tech.md` — those are frozen history.)

- [ ] **Step 3: Append `docs/agent/RUN_LOG.md`**

Add a NEW entry at the TOP of the file:

```markdown
## 2026-06-10 — Converted Kiro-CLI → Claude Code + completed the committed memory spine

- **Goal:** convert the project's agent tooling from Kiro-CLI to Claude Code, and in doing so finish the committed project-memory spine (SessionStart preflight hook + `/checkpoint` skill).
- **What changed:**
  - Deleted `.kiro/` (steering + settings/LSP) and `AGENTS.md`.
  - New `CLAUDE.md` = always-on memory rules + agent protocol (absorbed from `.kiro/steering/10` + `11`) + the folded `AGENTS.md` body + spine pointer. AGENTS.md's own spine table + operating-rule bullets were merged, not duplicated.
  - Moved the 8 reference docs to `docs/steering/`; stripped dead `inclusion: fileMatch` front-matter from `lib-coroutines`/`lib-jetpack-compose`/`lib-room`.
  - Added `.claude/hooks/session-preflight.sh` (plain stdout; injects branch + status + last 10 commits + STATE.md's newest objective bullet + Top priorities + Next actions + Do-not-touch; ~8 KB, under the 10k additionalContext cap; dependency-free; exits 0), `.claude/skills/checkpoint/SKILL.md`, `.claude/settings.json` (SessionStart, matcher `*`, additive).
  - Repointed live cross-refs: README (`AGENTS.md` → `CLAUDE.md`, `.kiro/steering/tech.md` → `docs/steering/tech.md`, "Kiro CLI" reword), `docs/steering/tech.md` line 88, `docs/agent/CONSTRAINTS.md` line 38.
  - ADR-0019 records the decision.
- **Verification:** hook smoke-test `wc -c` < 10000 with all content assertions green; `jq` valid `settings.json`; fold-completeness gate (every AGENTS.md `##` section preserved in CLAUDE.md); concrete-pathspec greps show 0 live `.kiro/`/`AGENTS.md` refs (history excepted); no `.kt`/`.kts`/`.toml`/schema file touched; 6-lens adversarial spec review applied before implementation.
- **Doc sync:** CLAUDE.md (new guide), docs/steering/* (moved), README, CONSTRAINTS, STATE (this rotation), this RUN_LOG entry, CHANGELOG, ADR-0019.
- **Next:** open the PR for `feat/kiro-to-claude-code`; on merge, the SessionStart hook + `/checkpoint` skill become the project's read/write memory loop.
```

- [ ] **Step 4: Add a `CHANGELOG.md` section**

Add under the `[Unreleased]` heading (match the file's existing section style):

```markdown
### Tooling
- Converted agent tooling from Kiro-CLI to Claude Code (ADR-0019). Removed `.kiro/`; added
  `CLAUDE.md` (single canonical guide, folds the former `AGENTS.md`), `.claude/hooks/
  session-preflight.sh` (SessionStart read-at-start), `.claude/skills/checkpoint/SKILL.md`
  (end-of-run write), and `.claude/settings.json`. Moved `.kiro/steering` reference docs to
  `docs/steering/`. No production code, build, or schema change.
```

- [ ] **Step 5: Verify the dogfood writes + that history was not mutated**

Run:
```bash
grep -q "Kiro-CLI → Claude Code" docs/agent/STATE.md && echo "✓ STATE rotated" || echo "✗ STATE"
head -1 docs/agent/RUN_LOG.md | grep -q "2026-06-10" && echo "✓ RUN_LOG appended at top" || echo "✗ RUN_LOG"
grep -q "Converted agent tooling from Kiro-CLI to Claude Code" CHANGELOG.md && echo "✓ CHANGELOG" || echo "✗ CHANGELOG"
echo "--- confirm no PRIOR RUN_LOG entry was edited (only an addition at top) ---"
git diff --stat docs/agent/RUN_LOG.md
```
Expected: three `✓` lines; the `git diff --stat` shows RUN_LOG.md with only insertions (an addition at the top), no deletions of prior lines.

- [ ] **Step 6: Commit**

```bash
git add docs/agent/STATE.md docs/agent/RUN_LOG.md CHANGELOG.md
git commit -m "docs: dogfood the protocol for the Kiro->Claude Code conversion

Rotate STATE.md current objective; append RUN_LOG entry; add CHANGELOG
Tooling section. Test count unchanged at 867 (no code change)."
```

---

## Task 11: Final adversarial verification pass

**Files:** none (verification only)

Run all §7 done-criteria together, plus an independent re-grep. This is the spec's §6.2 adversarial verification step.

- [ ] **Step 1: Hook output is bounded + correct**

Run:
```bash
bash .claude/hooks/session-preflight.sh > /tmp/kiro-conversion/final-preflight.out 2>&1; echo "exit=$?"
chars=$(wc -c < /tmp/kiro-conversion/final-preflight.out); echo "chars=$chars (< 10000?)"
[ "$chars" -lt 10000 ] && echo "✓ under cap" || echo "✗ OVER cap"
for needle in "Branch:" "Recent commits:" "## Top priorities" "## Next actions" "## Do-not-touch" "see docs/agent/STATE.md for full state"; do
  grep -qF "$needle" /tmp/kiro-conversion/final-preflight.out && echo "✓ $needle" || echo "✗ $needle"
done
grep -qi "Previous objective" /tmp/kiro-conversion/final-preflight.out && echo "✗ history leak" || echo "✓ no history leak"
```
Expected: `exit=0`, under cap, all `✓`, no leak.

- [ ] **Step 2: Config validity**

Run:
```bash
jq empty .claude/settings.json && echo "✓ settings.json valid JSON"
jq -e '.hooks.SessionStart[0].matcher=="*"' .claude/settings.json >/dev/null && echo "✓ matcher *"
awk 'NR==1 && $0=="---"{fm=1; next} fm && $0=="---"{exit} fm{print}' .claude/skills/checkpoint/SKILL.md | grep -q "name: checkpoint" && echo "✓ skill name"
```
Expected: three `✓`.

- [ ] **Step 3: Zero live `.kiro/` and `AGENTS.md` refs (history excepted)**

Run:
```bash
EXCL=(':(exclude)docs/agent/RUN_LOG.md' ':(exclude)docs/agent/STATE.md' ':(exclude)CHANGELOG.md'
      ':(exclude)docs/plans/*' ':(exclude)docs/external-reviews/*' ':(exclude)devdocs/*'
      ':(exclude)smoke_tests/*' ':(exclude)docs/agent/DECISIONS/*' ':(exclude)docs/superpowers/*')
echo "--- live .kiro/ refs (expect none) ---"
git grep -n '\.kiro/' -- "${EXCL[@]}" && echo "✗ LIVE .kiro ref" || echo "✓ 0 live .kiro/ refs"
echo "--- live AGENTS.md refs (expect none) ---"
git grep -n 'AGENTS\.md' -- "${EXCL[@]}" && echo "✗ LIVE AGENTS.md ref" || echo "✓ 0 live AGENTS.md refs"
```
Expected: `✓ 0 live .kiro/ refs` and `✓ 0 live AGENTS.md refs`.

- [ ] **Step 4: Structure + deliverables tracked + no code touched**

Run:
```bash
test ! -d .kiro && test ! -f AGENTS.md && echo "✓ .kiro/ + AGENTS.md gone"
test -f CLAUDE.md && echo "✓ CLAUDE.md present"
ls docs/steering/*.md | wc -l   # expect 8
git ls-files .claude/hooks/session-preflight.sh .claude/skills/checkpoint/SKILL.md .claude/settings.json | wc -l  # expect 3
echo "--- no source/build/schema files changed on this branch vs main ---"
git diff --name-only main...HEAD -- '*.kt' '*.kts' '*.toml' 'app/schemas/**' || true
git diff --name-only main...HEAD -- '*.kt' '*.kts' '*.toml' 'app/schemas/**' | grep -q . && echo "✗ code/build/schema changed" || echo "✓ no code/build/schema changes"
```
Expected: `✓ .kiro/ + AGENTS.md gone`, `✓ CLAUDE.md present`, `8`, `3`, `✓ no code/build/schema changes`.

- [ ] **Step 5: (Optional, recommended) Independent adversarial re-check via a subagent**

Dispatch a fresh general-purpose agent (or a workflow) to independently verify: "Read `docs/superpowers/specs/2026-06-10-kiro-to-claude-code-conversion-design.md` §7, then check each criterion against the real working tree on branch `feat/kiro-to-claude-code`. Report any criterion not satisfied, any live `.kiro/`/`AGENTS.md` ref that survived, any AGENTS.md section missing from CLAUDE.md, and whether any historical artifact was mutated (git diff)." Fix anything it surfaces.

- [ ] **Step 6: No commit (verification only)** — unless Step 5 surfaced fixes, which get their own commit.

---

## Task 12: Open the pull request

**Files:** none

- [ ] **Step 1: Push the branch**

Run:
```bash
git push -u origin feat/kiro-to-claude-code
```

- [ ] **Step 2: Open the PR**

Run:
```bash
gh pr create --title "Convert Kiro-CLI → Claude Code + complete the committed memory spine" --body "$(cat <<'EOF'
## Summary
Converts the project's agent tooling from Kiro-CLI to Claude Code and completes the committed
project-memory spine. Docs/config only — zero production code, build, or schema changes
(test count unchanged at 867). See ADR-0019.

- Deleted `.kiro/` (steering + LSP settings) and `AGENTS.md`.
- New `CLAUDE.md`: single canonical guide = always-on memory rules + agent protocol
  (absorbed from `.kiro/steering/10` + `11`) + folded `AGENTS.md` body + spine pointer.
- Moved the 8 reference docs to `docs/steering/` (stripped dead `fileMatch` front-matter).
- Added `.claude/hooks/session-preflight.sh` (SessionStart read-at-start),
  `.claude/skills/checkpoint/SKILL.md` (end-of-run write), `.claude/settings.json`.
- Repointed live cross-refs (README, tech.md, CONSTRAINTS.md).

## Verification
- Hook smoke-test < 10k chars, all content assertions green; `settings.json` valid JSON;
  fold-completeness gate (every AGENTS.md section preserved in CLAUDE.md); 0 live `.kiro/` /
  `AGENTS.md` refs (history excepted); no `.kt`/`.kts`/`.toml`/schema file touched.
- Design spec hardened by a 6-lens adversarial review before implementation.

## Plan & spec
- Spec: `docs/superpowers/specs/2026-06-10-kiro-to-claude-code-conversion-design.md`
- Plan: `docs/superpowers/plans/2026-06-10-kiro-to-claude-code-conversion.md`
EOF
)"
```
Expected: a PR URL is printed.

- [ ] **Step 3: Done.** The conversion is complete once the PR merges; on the next session the new SessionStart hook + `/checkpoint` skill drive the memory loop.

---

## Self-review notes (for the implementer)

- **CI guard:** the repo's `ci.yml` runs on PRs to `main`. This PR changes no Kotlin/Gradle, so `build-and-test` + `connected` should pass unchanged — but they WILL run. Don't be alarmed by the emulator lane taking time.
- **`master-plan.md`:** README line 116 calls it a "34-entry development roadmap" and references `docs/plans/master-plan.md` — that file is NOT touched by this plan (it has no `.kiro/`/`AGENTS.md` live refs per the audit; if Task 11 Step 3 surfaces one, add a Task-7-style edit). 
- **`RECREATE_LOCAL_SETUP.md`** (untracked) references `docs/agent/*` — those paths are unchanged by this conversion, so no edit needed; it stays untracked.
- **If the §6 reference audit (a fuller fan-out than the spot-checks here) surfaces an extra live ref**, add an edit for it in Task 7 before Task 8's delete.
