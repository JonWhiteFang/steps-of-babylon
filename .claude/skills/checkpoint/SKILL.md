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

### 6. Regenerate `docs/agent/BACKLOG.md`
Mechanically refresh the open-issue backlog snapshot so the spine — not chat — is the agent's source of
truth. This is a generated file; do not hand-edit its body.

Run:
```bash
gh issue list --state open --limit 200 --json number,title,labels \
  --jq '.[] | "- #\(.number) — \(.title) — [\([.labels[].name] | join(", "))]"'
```
Write the result into `docs/agent/BACKLOG.md` under a **GENERATED — do not hand-edit** header that names
this exact command and a "last generated: <today's date>" line (stamp the current date at run time).
Group the lines by phase/label where it is obvious (e.g. the `tooling` phases, `severity:*`), otherwise
list newest-issue-number first.

**Graceful degradation:** if `gh` is absent or unauthenticated (the command errors or returns nothing),
**log a one-line skip and leave any existing `docs/agent/BACKLOG.md` untouched** — never write a
truncated or empty file.

## Historical artifacts — NEVER modify
Appending a new RUN_LOG entry is fine; editing the past is not. Leave these untouched:
- Prior `docs/agent/RUN_LOG.md` entries.
- `docs/archive/completed-plans-v1.0/*` — the shipped v1.0 plan files (Plans 01–30, 10b, R, R2, R3, R4, RO-*); historical at authoring date.
- `docs/external-reviews/*` — historical at review date.
- `docs/archive/pre-claude-devdocs/*`, `docs/archive/smoke_tests/*` — pre-Claude analysis corpus; historical per HEAD pin.
- Existing `docs/agent/DECISIONS/ADR-*.md` bodies — amend status only if explicitly warranted.

## PR Task-List Convention
For every PR that changes production code, tests, or config, the doc sync (steps 1–2) runs
BEFORE the STATE/RUN_LOG update (steps 3–4), and both run immediately before the commit step.
