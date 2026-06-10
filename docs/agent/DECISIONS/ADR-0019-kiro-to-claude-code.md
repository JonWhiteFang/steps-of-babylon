# ADR-0019: Convert agent tooling from Kiro-CLI to Claude Code

**Status:** Accepted — 2026-06-10.
**Date:** 2026-06-10
**Supersedes:** The Kiro-CLI steering mechanism (`.kiro/steering/` `inclusion: always` docs) and `AGENTS.md` as the project guide.
**Superseded by:** None

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
