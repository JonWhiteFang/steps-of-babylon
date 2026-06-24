---
name: adversarial-review
description: Run the mandatory Adversarial Review Gate on ONE design spec or implementation plan before the next stage begins. Use when the developer says "review this spec/plan", "adversarial review", "run the review gate", "is this spec/plan ready", or before advancing spec→plan→implementation. Reviews a single artifact (not the whole repo — that is /complete-app-review).
disable-model-invocation: false
---

# /adversarial-review — the gate for ONE spec or plan

The CLAUDE.md **Adversarial Review Gate** as a one-command workflow. Every design spec and every
implementation plan MUST pass this review before the next stage starts — review the spec before you
write the plan, review the plan before you touch code. It is the **standing default**, not an
on-request favour: the developer should not have to ask for it each time, and the model may invoke it
(hence both-invocable).

This is the **single-artifact sibling** of `/complete-app-review`. That skill audits the whole repo
for discovery (no fixes), writing a dated report to `docs/reviews/<date>-complete-app-review.md`; this
one pressure-tests one artifact and then **amends it in place**. Same core discipline — code-grounded
findings, then adversarial refutation, default-to-refuted — applied to exactly one spec/plan.

## Core principle (why this exists)

A spec/plan finding is only as good as the **code it was checked against**. The failure mode this gate
exists to catch is a plan that reads cleanly but is wrong about the codebase: a stale `file:line`, an
API used the way it *isn't*, a step that violates a fragile-zone invariant, a "we'll just add a field"
that ignores the atomic guarded-deduct pattern. So every finding must cite the **actual code** it
checked — not the artifact's own prose — and then a skeptic tries to **refute** it against spec + code.
Only `confirmed`/`partial` survivors get applied. Default to refuted.

> A finding you "read in the plan" is not verified. The plan is the *claim*; the code is the *truth*.
> The refute pass is what stops a confident-but-wrong amendment from being baked into the artifact.

## Before you fan out (operator setup)

1. **Load the artifact-under-review.** Read the exact spec/plan file (e.g. a `docs/plans/plan-*.md`,
   an issue body, an inline spec the developer pasted). The reviewers need its concrete text — every
   claim, `file:line`, API call, and task step.
2. **Load the relevant code** the artifact touches, so the fan-out has the real ground truth in hand:
   the files/symbols the plan cites, the fragile zones it brushes (see CLAUDE.md "Battle Renderer" +
   `docs/agent/STATE.md` "Do-not-touch / fragile zones"), the invariants it could break
   (`architecture/DomainPurityTest`, `architecture/PresentationPurityTest`, the atomic guarded-deduct
   pattern, the `entitiesLock`/`effectsLock` order, `GameLoopThread`'s try/catch).
3. **Identify the source of scope** the artifact is meant to satisfy — the issue (#NNN), the prior
   spec, or the external review — so "scope completeness" has something to measure against.

## Confirm ultracode (multi-agent), or take the documented fallback

The full gate IS a multi-agent `Workflow` — separate refuter subagents, each reading real code. It
cannot be done well single-agent.

**If ultracode is OFF** (a session reminder will say so), this gate's multi-agent form is disabled by
the opt-in rules — do **not** silently skip it. **Flag to the developer that the artifact is unreviewed
and ask** whether to (a) turn ultracode on for the review, (b) run a lighter single-agent review
inline, or (c) proceed without one. Never advance spec→plan→implementation on an unreviewed artifact
without that explicit choice.

## How to run (the three stages, mirroring CLAUDE.md exactly)

Drive it with the `Workflow` tool (see the Workflow tool's quality patterns; same `agent` / `parallel`
/ `pipeline` / `phase` / `log` primitives and the `{ label, phase, schema }` agent options that
`complete-app-review/review-workflow.js` uses). The shape is:

### Stage 1 — Code-grounded multi-dimension fan-out

One reviewer **per dimension**, each handed the artifact text + the loaded code, each citing the
**actual code** it checked (`file:line` + the symbol), not the artifact's prose. Dimensions:

- **code-grounding** — does every `file:line` / symbol the artifact cites still exist and say what the
  artifact claims? (Line numbers drift; check the symbol.)
- **API/framework correctness** — are the Kotlin/Compose/Room/Hilt/coroutine APIs used the way they
  actually behave? (e.g. Hilt with KSP not kapt; `StateFlow` patterns; Room `@Transaction` semantics.)
- **fragile-zone & invariant safety** — does any step break a guarded invariant: domain purity, the
  presentation→port rule, the atomic guarded-deduct / one-shot-claim pattern, the `entitiesLock` →
  `effectsLock` order, the game-loop try/catch, the per-key unique-index rule (#127)?
- **scope completeness vs the issue/review** — does the artifact cover everything the issue (#NNN) /
  prior spec / external review asked for, with nothing silently dropped or smuggled in?
- **test-strategy feasibility** — are the proposed tests actually runnable here (JVM vs instrumented
  lane, Robolectric Compose lane, the `test/fakes/`), and do they cover the change's real risk?
- **internal consistency / ambiguity** — does the artifact contradict itself, leave a step
  underspecified, or assume an order that won't hold?

Each finding carries: severity (`critical`/`high`/`medium`/`low`, the finder scale `complete-app-review`
uses — these map to this repo's GitHub labels as critical→`severity:blocker`, high/medium→`severity:major`,
low→`severity:minor`), the dimension/area, the **code `file:line` it actually opened**, the artifact
location it concerns, why it matters, and the proposed amendment.

### Stage 2 — Adversarial verify (default-to-refuted)

A **skeptic re-checks each finding against spec + code and tries to refute it** — distinct subagent(s)
reading the real code, not the finder re-grading itself. A finding survives only if it withstands the
attempt: **only `confirmed`/`partial` findings survive**; the rest are dropped. If the verifier cannot
positively confirm every link (the cited code exists → the artifact really says/omits what's claimed →
the consequence follows → it isn't already handled), the finding is refuted.

### Stage 3 — Synthesis (apply + commit)

**Apply every surviving finding to the artifact** (edit the spec/plan file directly — this gate's
output is an amended artifact, unlike `/complete-app-review` which only writes a report). Then **commit
the amendments** with a message summarising the findings — **total / surviving / refuted** — and the
substantive fixes. Do **not** advance to the next stage with unaddressed `critical`/`major` findings.

## Severity-scale the response

Match the effort to the artifact, exactly as CLAUDE.md says:

- **Quick spec / small plan** → a **lean fan-out** (the dimensions that actually apply) and a single
  refuter per finding.
- **"Be thorough" / audit-grade / a plan that touches a fragile zone or the economy** → the **full
  pattern with 3–5-vote verification** (multiple separate refuters per finding, survive on majority),
  the same severity-scaling discipline `complete-app-review` uses.

Scale the fan-out too: skip dimensions the artifact can't touch (e.g. no `test-strategy` reviewer for a
pure narrative/GDD spec), but never drop **fragile-zone & invariant safety** or **code-grounding** —
those are where a plan silently goes wrong.

## Output to the developer

Report: the **total / surviving / refuted** counts, the surviving findings with their refutation trail
(how many refuters, the vote, what refutation was attempted and failed), an explicit note of what was
**refuted and why** (a killed finding is a signal too), and the commit that carries the amendments.
Then state the gate verdict: **passed** (no unaddressed `critical`/`major` — next stage may begin) or
**blocked** (what must change first).

## Guardrails

- **One artifact, in place.** This gate reviews and amends a single spec/plan. A whole-repo audit is
  `/complete-app-review`; don't widen scope into one here.
- **Findings cite code, not prose.** A finding whose only evidence is the artifact's own text is not
  grounded — refute it. The cited `file:line` must be a real location the reviewer opened.
- **Don't advance on an unreviewed artifact.** No spec→plan→implementation hop without either a clean
  gate or the explicit ultracode-OFF choice (a/b/c) above.
- **No production-code changes** beyond editing the artifact being reviewed (and, in stage 3, the
  commit of those edits). The review does not implement the plan.
