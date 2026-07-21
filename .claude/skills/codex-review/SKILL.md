---
name: codex-review
description: Run the mandatory Codex Review Gate on ONE artifact — a design spec, an implementation plan, or a final implementation diff — before the next stage begins. Use when the developer says "review this spec/plan/implementation", "codex review", "run the review gate", or "is this ready", and before advancing spec→plan→implementation→merge. Reviews a single artifact (not the whole repo — that is /complete-app-review).
disable-model-invocation: false
---

# /codex-review — the gate for ONE spec, plan, or implementation diff

The CLAUDE.md **Codex Review Gate** as a one-command workflow. Every design spec, every
implementation plan, and every final implementation MUST pass this review before the next stage
starts — review the spec before you write the plan, the plan before you touch code, the finished
diff before its PR merges. It is the **standing default**, not an on-request favour, and the model
may invoke it (hence both-invocable). It replaced the multi-agent-Workflow Adversarial Review Gate
(ADR-0043); the core discipline — code-grounded findings, skeptical verification, default-to-refuted
— is unchanged, only the reviewer is now the codex MCP server.

## Core principle (why this exists)

A finding is only as good as the **code it was checked against**. The failure mode this gate catches
is an artifact that reads cleanly but is wrong about the codebase: a stale `file:line`, an API used
the way it *isn't*, a step that violates a fragile-zone invariant. So Codex must be pointed at the
real repo (read-only sandbox, repo cwd) and required to cite the **actual code it opened** — and you
verify each finding against the code yourself before amending anything. The artifact is the *claim*;
the code is the *truth*.

## How to run

1. **Load context.** Read the artifact-under-review (spec/plan file, or `git diff <base>...HEAD` for
   an implementation). Identify the source of scope it must satisfy (issue #NNN, prior spec, external
   review) and the fragile zones it brushes (CLAUDE.md "Battle Renderer" + `docs/agent/STATE.md`
   fragile-zones list).
2. **Open the Codex session** — `mcp__codex__codex` with:
   - `sandbox: "read-only"`, `cwd`: the repo root (Codex reads code itself; never give it write access
     for a review),
   - a prompt that names the artifact path (or diff range), states what stage it is (spec / plan /
     implementation), lists the invariants to check (domain purity, presentation→port rule, atomic
     guarded-deduct, `entitiesLock` → `effectsLock` order, game-loop try/catch, per-key unique-index
     rule #127, Steps-generation allowlist), and requires findings in this shape: severity
     (`critical`/`major`/`minor`), the dimension (correctness / API-framework usage / fragile-zone &
     invariant safety / scope completeness / test-strategy feasibility / internal consistency),
     the **`file:line` Codex actually opened**, the artifact location concerned, why it matters, and
     the proposed amendment.
3. **Interrogate, don't accept.** Follow up on the same thread with `mcp__codex__codex-reply`: ask
   Codex to double-check its highest-severity claims against the cited code, probe dimensions it went
   quiet on, and challenge anything that smells like it was read off the artifact's prose. For
   audit-grade work or fragile-zone artifacts, run extra rounds on the specific risks.
4. **Verify then apply (default-to-refuted).** For each finding, open the cited code yourself and
   confirm the chain: the code exists → the artifact really says/omits what's claimed → the
   consequence follows → it isn't already handled. Findings that fail any link are refuted and
   dropped. Apply the survivors: specs/plans are **amended in place**; implementation findings are
   fixed in the diff.
5. **Commit** the amendments with a message summarising findings — **total / applied / refuted** —
   and the substantive fixes.

## Gate verdict

Report to the developer: total / applied / refuted counts, each applied finding with its severity and
cited code, what was refuted and why (a killed finding is a signal too), and the commit carrying the
amendments. Then state: **passed** (no unaddressed `critical`/`major` — next stage may begin) or
**blocked** (what must change first). Severity mapping to repo labels: critical→`severity:blocker`,
major→`severity:major`, minor→`severity:minor`.

## Guardrails

- **One artifact, in place.** A whole-repo audit is `/complete-app-review`; don't widen scope here.
- **Findings cite code, not prose.** A finding whose only evidence is the artifact's own text is not
  grounded — refute it.
- **Don't advance on an unreviewed artifact.** If the codex MCP server is unavailable, do not
  silently skip: flag the artifact as unreviewed and ask the developer whether to run an inline
  single-agent review or proceed without one.
- **No production-code changes** beyond the artifact being reviewed (specs/plans) or the diff's own
  fixes (implementation stage). The review does not implement the plan.
- **Read-only Codex.** The Codex session never gets `workspace-write`/`danger-full-access` for a
  review; you make the edits, not Codex.
- **Mandatory concurrency round (#372, ADR-0038 — folded into this gate by ADR-0043).** If the
  artifact touches the concurrency surface (`presentation/battle/engine/**`,
  `presentation/battle/effects/**`, `data/local/*Dao.kt`, `data/repository/PlayerRepositoryImpl`,
  the domain spend/claim use cases, or anything that structurally mutates a shared engine collection
  or moves a currency balance), the review MUST include a dedicated Codex concurrency round: a
  `codex-reply` round whose prompt pastes the invariant briefing at
  `concurrency-invariants.md` (in this skill's directory — lock model 1A–1E, atomic economy 2A–2E)
  and asks for its `SAFE | CONCERNS | BLOCK` verdict format. Treat `BLOCK` as `critical` and
  `CONCERNS` findings at the severity the briefing assigns.
