---
name: complete-app-review
description: Use when the developer asks for a full, end-to-end, audit-grade review of Steps of Babylon — a "complete app review", "full review", "audit the whole app/repo", "review everything", "production-readiness assessment", or "is this ready to ship". Discovery/audit only (no fixes). Triggers on requests for a comprehensive multi-facet review whose findings must be trustworthy.
---

# Complete App Review (Steps of Babylon)

Audit-grade, end-to-end review of the whole repo + app, where **every finding survives
severity-scaled adversarial refutation by *separate* subagents before it reaches the report.**
Discovery / audit / recommendations only — **do not implement fixes, do not delete or refactor**
anything except creating the one report file.

## Core principle

A finding you "saw with your own eyes" is **not** verified. A finding is a *causal chain*
(artifact exists → reachable in the shipped config → has the claimed consequence → not already
mitigated elsewhere). Reading the code confirms only link one. The other links — and the severity —
are what separate subagents adversarially try to **refute**. Default to refuted: a finding ships
only if it withstands refutation.

> **Proven, not hypothetical.** In baseline testing of this exact repo, **2 of 3** "confident"
> findings (a CRITICAL "hardcoded DB key" and a MEDIUM "racy spend") were **demonstrably FALSE** —
> the key is `SecureRandom` + Keystore-wrapped; spends use the atomic guarded-deduct pattern.
> Shipping unverified findings would have put two false claims at the top of the report. This is
> why the refutation pass is **non-negotiable for every finding**, including the "obvious" ones.

## The refutation law (non-negotiable)

Each finding gets **N separate adversarial refuter subagents**, scaled by the finder's severity.
These are *distinct subagents*, each reading the real code — **not** N "verification dimensions"
done by one agent, and **not** the finder re-checking its own work.

| Severity | Separate refuter subagents | Survives (ships) when | Killed (dropped) when |
|---|---|---|---|
| **Critical** | **3** | ≥2 of 3 confirm | ≥2 of 3 refute |
| **High** | **3** | ≥2 of 3 confirm | ≥2 of 3 refute |
| **Medium** | **2** | both confirm | both refute |
| **Low** | **1** | the 1 confirms | the 1 refutes |

- Mixed verdicts (some confirm, some refute/partial) → keep as **partial** and note the dissent.
- A refuter may **adjust severity** (e.g. CRITICAL→MEDIUM because a Keystore mix exists). If a
  finding is *upgraded* into a higher band than the refuters already run (low→medium, medium→high,
  any→critical), it must be re-refuted up to that band's count (the workflow does this automatically).
- "Obvious" findings get the **same** count — obviousness covers existence, not the chain.

## Requires ultracode (multi-agent)

This review IS a `Workflow` — it cannot be done well single-agent. If a session reminder says
**ultracode is OFF**, do not silently downgrade: tell the developer this review needs multi-agent
orchestration and ask them to turn ultracode on (or explicitly accept a weaker single-agent review).

## How to run

1. **Confirm ultracode is on** (see above).
2. **Pass today's date** (`YYYY-MM-DD`) as the workflow arg — scripts can't read the clock, so the
   filename must come from you:
   ```
   Workflow({ scriptPath: ".claude/skills/complete-app-review/review-workflow.js", args: { date: "<YYYY-MM-DD>" } })
   ```
   It does: recon → ~17 dimension finders (real `file:line` evidence) → dedup → severity-scaled
   adversarial refutation (3/3/2/1 separate subagents) → synthesis agent writes the report → returns
   the survivor list + executive summary.
3. **The synthesis agent writes** the report to **`docs/reviews/<YYYY-MM-DD>-complete-app-review.md`**
   (the full 20-section report per `review-brief.md`), creating `docs/reviews/` if absent. The
   **date-stamped filename is deliberate** — each run is a point-in-time artifact kept for tracking +
   backlinking, so a new run does **not** overwrite the prior one.
4. **File findings as GitHub issues (propose-then-confirm).** After refutation, the workflow returns
   a deduped issue plan; **do not file blindly** — see "Filing issues" below. This is an outward-facing
   bulk action, so you present the plan and get the developer's go-ahead before any `gh issue create`.
5. **Relay to the developer** the three required deliverables: (1) short summary of the most
   important findings, (2) the top-10 highest-priority fixes, (3) the exact report path.
6. These dated reports are point-in-time artifacts — like `docs/external-reviews/*`, **do not edit a
   past dated report** in a later checkpoint sweep; a new review = a new dated file.

The verbatim review brief, the review method (Phases 1–10), and the exact 20-section report
structure live in `review-brief.md` — the workflow's finder and synthesis prompts read it.

## Filing issues (propose-then-confirm)

Every surviving finding should be trackable as a GitHub issue, **without creating duplicates** and
matching this repo's conventions. The workflow does NOT call `gh` itself — it returns a plan; you
execute it only after the developer confirms.

1. **Dedup against existing issues first.** `gh issue list --state all --limit 200 --json number,title,labels`.
   A finding already has an issue when an open/closed issue clearly covers the same root cause (match
   on the finding ID in the title, e.g. `(REL-2)`, or on the described defect — not just keyword
   overlap). Findings that map onto an existing issue are reported as "already tracked → #N", not refiled.
2. **Scope (the standing default):** file **Medium / High / Critical** survivors that lack a dedicated
   issue, one issue each. **Bundle all Low findings into a single tracker issue** (mirrors the existing
   `[Audit] Tracker: NN Low-severity findings` convention, e.g. #128) — do not open one issue per Low.
3. **Conventions:** title `[Audit] <finding title> (<ID>)`; labels `bug` + the right
   `severity:{blocker|major|minor}` (map Critical/High→`major` or `blocker`, Medium→`major`/`minor`,
   Low→`minor`) + an `area:*` label; body = the report's evidence (`file:line`), why-it-matters, fix,
   effort, and a backlink to `docs/reviews/<date>-complete-app-review.md#<section>`.
4. **Present the plan, then confirm.** Show the developer the list (new issues to create + "already
   tracked" mappings) and wait for go-ahead. Only then run `gh issue create`. After filing, note the
   new issue numbers back into the report's Technical Debt Register if asked.

## Red flags — STOP, you are rationalizing away the refutation

These are the **actual excuses** agents used in baseline testing to skip or shrink the refutation.
If you catch yourself (or the developer pushes) any of these, the answer is still: run the law in full.

| Rationalization | Reality |
|---|---|
| "That's overkill for a review." | 2 of 3 baseline findings were FALSE. The refutation is what makes the report trustworthy. |
| "I trust your analysis — don't burn tokens on extra agents." | Trust is exactly when false positives ship unchecked. Ultracode = cost is not a constraint. Run it. |
| "I'm not going to spin up a swarm." | The swarm IS the deliverable. Separate refuters per finding, severity-scaled. |
| "The critical one is obvious — I literally saw the constant." | Obviousness covers link 1 (existence). The claim is the *consequence*. Refute all links. |
| "HIGH is single-file, no fan-out needed." | High gets **3** separate refuters, same as Critical. Not 1. |
| "I'll mark the rest unverified and ship." | Unverified findings do not ship. Every finding faces its full refuter count or it's dropped. |
| "Verification passes/dimensions by me = the refuters." | No. N **separate subagents**, each reading real code, each trying to refute. |
| "Let me just compile the findings." | Findings are raw until refuted. Compile only survivors + a transparent Refuted/Downgraded section. |

## Constraints (from the brief)

- **No code changes** beyond creating the one report file. No deletes. No refactors.
- Don't install dependencies without asking first.
- **Evidence over assumptions** — cite `file:line` and functions/components everywhere. If something
  is missing, say so; don't assume it exists.
- Be specific, constructively critical, do not flatter the project. If something is weak/risky/
  confusing/incomplete, say so directly. Flag recommendations that would create scope creep.
- Distinguish **confirmed** / **likely** / **needs-verification** — never call something secure just
  because no exploit was immediately visible.

## Iterating on this skill

This skill was built TDD-style (baseline failures captured above drove every bulletproofing line).
Per `superpowers:writing-skills`: **no edit without a failing test first.** If you change the
refutation law or the workflow, re-run a pressure scenario (e.g. the "that's overkill, just compile"
prompt) and confirm an agent still runs the law in full before committing the change.
