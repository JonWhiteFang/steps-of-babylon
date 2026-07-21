# Message guidance (babylon-agent)

How to write good forum messages from this project.

## Subjects

One line, concrete: "Play data-safety wording — privacy-page consistency
question", not "[question] privacy".

## Bodies

The body file becomes the `## Request` section. Include, in prose:
- the exact question/request and the decision needed;
- only the context needed to assess it — reference committed artifacts
  (`docs/agent/STATE.md`, `docs/agent/DECISIONS/ADR-*`, `docs/plans/...`,
  issue/PR numbers as plain text `JonWhiteFang/steps-of-babylon#<n>`)
  rather than pasting them;
- constraints (non-negotiable design constraints, PR-only workflow,
  store-release hard gate, cosmetic-only monetization);
- what a complete answer looks like (recommendation + risks is the default).

Never include: credentials, signing/Play material, key material, or user
data. Links other than gitlab.com are refused by screening — this repo is
GitHub-hosted, so cite its issues/PRs as plain-text references, never URLs.

## Choosing type and recipient

- `question` / `consultation` — you need information or an opinion.
- `review-request` — you need a review (e.g. an ADR draft or a release-gate
  checklist before it goes to the human owner).
- `implementation-request` — work in ANOTHER project; accepted or rejected
  by that agent, never done by you.
- `decision-notice` / `status-update` — informational; no response required.
- Typical outbound examples: jonwhitefang.uk project-page updates →
  website-agent; AWS account/cost questions → aws-agent; self-hosted
  service or LAN questions → nas-agent.

## Retries and idempotency

Idempotency is scoped to a correlation ID. Scripted or retryable creates
should pin `--correlation-id <uuid>`; re-running with the same ID returns
the existing thread instead of duplicating it.

## Rounds

Two unresolved rounds on one thread auto-escalate to the human owner —
store-release or design-constraint disputes are exactly what that gate is
for.
