# ADR-0043: Codex Review Gate replaces the multi-agent Adversarial Review Gate

**Status:** Accepted (2026-07-21) · **Requested by:** developer (permanent process change)

## Context
- The review procedure for design specs and implementation plans was the **Adversarial Review Gate**:
  a multi-agent `Workflow` (code-grounded multi-dimension fan-out → adversarial verify →
  synthesis), gated on the ultracode opt-in, with a documented flag-and-ask fallback when ultracode
  was off. In practice recent artifacts (e.g. the #306 Slice 2 spec/plan) ran the "lighter inline"
  fallback, and the full multi-agent form is token-heavy and opt-in-gated.
- The codex MCP server (`mcp__codex__codex` / `mcp__codex__codex-reply`) is connected to this
  project and provides an independent second-model reviewer that can read the repo directly in a
  read-only sandbox — an outside perspective a self-review fan-out cannot give.

## Decision
- **Every design spec, every implementation plan, and every final implementation (the finished diff,
  pre-merge) is reviewed via the codex MCP server.** This extends the gate to a third stage — the
  old gate covered spec and plan only; implementation review previously relied on PR-time review.
- Mechanics (canonical wording in CLAUDE.md → Agent protocol → Codex Review Gate; one-command form
  is the `/codex-review` skill, which replaces `/adversarial-review`):
  - Codex session opened `sandbox: read-only` with `cwd` at the repo root so findings are grounded
    in real code; follow-up interrogation rounds continue the same thread via `codex-reply`.
  - Findings must cite the actual `file:line` opened, carry `critical`/`major`/`minor` severity, and
    cover the same dimensions the old fan-out did (correctness, API/framework usage, fragile-zone &
    invariant safety, scope completeness, test-strategy feasibility, internal consistency).
  - **Default-to-refuted survives:** each Codex finding is verified against the code before being
    applied; survivors are amended in place (specs/plans) or fixed in the diff (implementations),
    committed with total / applied / refuted counts.
  - No advancing a stage with unaddressed `critical`/`major` findings.
- **Concurrency lane folded in (developer-directed, same day):** the mandatory
  `concurrency-reviewer` subagent lane (#372, ADR-0038) is replaced by a **mandatory Codex
  concurrency round** on the same surface: the subagent's system prompt (the lock-model 1A–1E +
  atomic-economy 2A–2E invariant briefing, with its SAFE/CONCERNS/BLOCK verdict format) moves
  verbatim to `.claude/skills/codex-review/concurrency-invariants.md` and is pasted into a dedicated
  `codex-reply` round whenever a diff touches that surface. `.claude/agents/concurrency-reviewer.md`
  is retired; the tier-4 PreToolUse advisory in `guard-sensitive-edits.sh` now points at the Codex
  round. **Unchanged:** the advisory's trigger surface, the build-gated tripwires
  (`StepCreditAllowlistTest`, `BattleEngineLockScanTest`), and `/complete-app-review` (whole-repo
  audit). ADR-0038's enforcement *mechanism* is amended (see its status note); its invariants and
  deterministic-nudge design are not.
- **Fallback:** if the codex MCP server is unavailable, the gate is not silently skipped — flag the
  artifact as unreviewed and ask the developer (inline single-agent review, or proceed without).

## Consequences
- Reviews no longer depend on the ultracode opt-in or Workflow orchestration; the gate is cheaper to
  run and therefore harder to justify skipping. The ultracode-off flag-and-ask paragraph in CLAUDE.md
  is superseded by the MCP-unavailable fallback above.
- A second, independent model reviews every artifact — cross-model diversity replaces same-model
  fan-out diversity. The trade: fewer parallel perspectives per pass, mitigated by interrogation
  rounds and the retained skeptical-verification step.
- New dependency: the codex MCP server must be configured in the session for the gate to run; its
  absence is now a visible process event (flag-and-ask), not a silent downgrade.
- `.claude/skills/adversarial-review/` is removed; `.claude/skills/codex-review/` replaces it.
  CLAUDE.md, `docs/agent/START_HERE.md`, and `AGENTS.md` carry the updated gate wording.
