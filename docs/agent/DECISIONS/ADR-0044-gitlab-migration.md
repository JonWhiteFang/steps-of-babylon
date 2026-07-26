# ADR-0044: Migrate from GitHub to GitLab (archived GitHub as the historical-number resolver)

**Status:** Accepted (2026-07-23; decisions final, **cutover not yet executed**). Phase 0 spike *run*;
Phase 1 CI port *proven green on a scratch import*; Phases 2–4 **authored but not executed** — Phase 2's
code half waits on the new URL going live, and Phase 3's cutover sitting plus all of Phase 4 are
outstanding.
**Requested by:** developer (consolidation)

> This ADR is deliberately written *before* the cutover, because every decision below is already made and
> several are non-obvious enough to be worth recording while the reasoning is fresh. What it does **not**
> claim is that the migration happened. Update the status line — do not rewrite the decisions — once the
> first owner-witnessed `v*` tag has published from GitLab (Phase 4, Task 4.1 Step 3).

## Context

- `steps-of-babylon` lived on GitHub (`JonWhiteFang/steps-of-babylon`): 7 Actions workflows, Dependabot,
  Code Scanning, Secret Push Protection, and a `v*`-tag release lane publishing a signed AAB to the Play
  internal track (ADR-0018 / Plan 32).
- Two sibling projects — the **agent forum** and **gaslight-and-grimoire** — already run on gitlab.com.
  Keeping one project on a second forge means two CI dialects, two credential stores, and two review UIs
  for one person.
- The forge is load-bearing beyond CI: the privacy-policy URL is served by **GitHub Pages** *and baked into
  the shipped app*, and a large body of automation (`/checkpoint`, `/release`, `/complete-app-review`, the
  backlog regen, the agent-forum procedures) shells out to `gh`.

## Decision

**Migrate to `gitlab.com/kn0ck3r-group/steps-of-babylon`, and archive — never delete — the GitHub repo.**

Executed as five phases, each gated: Phase 0 spike (go/no-go) → Phase 1 CI port proven on a throwaway
scratch import → Phase 2 privacy-URL move → Phase 3 cutover + automation flip → Phase 4 Renovate, doc sweep,
first owner-witnessed release. The real repository is untouched until Phase 3, and abort is free through
cutover step 9.

### The archived-GitHub numbering-resolver rule

**Historical `#N` citations are never bulk-rewritten.** GitHub shares one number space across issues and
PRs; GitLab gives issues and MRs **separate `iid` spaces**, so the importer cannot preserve numbering. The
docs, ADRs, RUN_LOG and CHANGELOG contain hundreds of `#N` references accumulated over the project's life.

Rather than rewrite history (lossy, error-prone, and it would invalidate every external review artifact),
the archived GitHub repo stays intact and read-only **as the canonical resolver** for those numbers. New
work is cited with the project path and GitLab's sigils — `#<iid>` for an issue, `!<iid>` for a merge
request — and pre-cutover numbers are marked `GitHub-era #<n>`. **A bare `#204` is ambiguous after the
import and must not be used.** (`.agent-forum/security.md` item 8 carries the binding wording.)

This is also why archiving is not merely "tidy": it is a functional dependency of the documentation.

### Namespace: the group, not the personal account

Target is the **`kn0ck3r-group`** group, not the personal `kn0ck3r` namespace. The Phase-0 spike found the
personal namespace has **0 CI minutes**; the group has 10k/month. Nothing else distinguished them.

### Instrumented tests demoted to local-only

The Phase-0 spike's decisive finding: **gitlab.com shared runners have no `/dev/kvm`**, so no emulator, so
`:app:connectedDebugAndroidTest` cannot run in the pipeline. A self-hosted runner was considered and
declined — on a public project an MR pipeline executes checked-out branch code, so a job tag is not
isolation; doing it safely needs a disposable VM with per-job reset and no release credentials. Firebase
Test Lab was also declined.

So the 9 instrumented tests become a **human pre-release device run**, recorded in
`docs/release/release-checklist.md`. This is the one accepted regression that genuinely weakens the merge
gate — on GitHub those tests blocked MRs; now nothing does but a person remembering. Recorded honestly
rather than presented as parity.

### Privacy policy: an apex path on the website deployment, not GitLab Pages

`«NEW_URL»` = **`https://jonwhitefang.uk/legal/steps-of-babylon-privacy/`**, served by the website agent's
Cloudflare Workers deployment.

**GitLab Pages was rejected even though it was this project's own first proposal.** The decisive argument
(from the website agent, accepted 2026-07-26): a Pages custom domain makes the *hostname* forge-neutral but
leaves the *serving* coupled to a forge — it swaps GitHub for GitLab and keeps precisely the dependency the
phase exists to shed. An apex path adds no DNS record, no certificate and no new failure domain, and
attaches the availability obligation to a host that is already load-bearing for its owner.

Conditional on a scoped **Cloudflare WAF exception** (owner-approved): that zone's Super Bot Fight Mode
`definitely_automated: block` returns **403 to non-browser clients for HTML documents**. Play validates the
privacy URL automatically, so a 403 there is a compliance failure, not a cosmetic one.

### The old privacy URL keeps a full copy, and is not redirected

Already-installed builds have the github.io URL baked in, so it must keep serving indefinitely. It serves a
**full copy of the policy text**, *not* a redirect: GitHub Pages has no server-side 301, so a
`meta refresh` / `jekyll-redirect-from` pointer gives no guaranteed `#delete-data` fragment propagation and
is invisible to a non-JS fetcher — i.e. exactly the Play-validator failure the move exists to prevent.

Because archiving makes that copy read-only, an **unarchive → update → re-archive** procedure is written
into the cutover runbook and the release checklist. Without it, a future policy revision leaves **two
divergent live policies, one of them the URL declared to Google** — worse than a stale URL. The divergence
risk is therefore procedural, not structural, and that is the accepted price of covering old installs.

## Alternatives considered

- **A: Stay on GitHub.** Zero risk, zero cost — but leaves three projects across two forges permanently.
  Rejected on the consolidation goal, which is the whole point.
- **B: `git push --mirror` instead of the GitHub importer.** Carries git objects only: every issue, MR,
  comment, review thread and label is lost. Rejected — most of what is being preserved is not git objects.
- **C: Self-hosted runner for the instrumented lane.** See above: viable only with real isolation
  hardening; declined by the owner in favour of demote-to-local.
- **D: Gradle Play Publisher for the release upload.** No GPP plugin is wired in `app/build.gradle.kts`
  (verified), so adopting it would mean a build-script change inside a fragile zone during a migration.
  Rejected in favour of **Fastlane `supply` in its own pinned container**, proven on the scratch import to
  the AAB-signature boundary with the real service account.
- **E: A `privacy.` subdomain.** Rejected: the website deployment has no host-based routing, so a second
  hostname would serve the entire site duplicated under a second permanent HSTS obligation.

## Consequences

### Positive
- One forge, one CI dialect, one credential store across all three projects.
- 10 GitLab jobs replace 6 workflows, every image/binary/gem **digest- or checksum-pinned** (no `:latest`
  survived Phase 1).
- The privacy policy becomes genuinely forge-independent, which was the original goal and which GitLab
  Pages would not have achieved.
- Merge gating is on the **whole pipeline**, so a job cannot be forgotten in a required-checks list.

### Negative / accepted regressions
Each was verified as lost rather than assumed ported:

| Regression | Mitigation |
|---|---|
| **Instrumented lane retired from CI** (no `/dev/kvm`) | Human device run before each release; the one item with no automated backstop |
| GitHub dependency-submission API | `osv-scan` job + Renovate cover transitive CVEs |
| Code-Scanning SARIF dashboard | `osv-scan` uploads a SARIF **artifact**; no dashboard |
| gitleaks PR summary comment | Job log + SARIF artifact; the gate still blocks |
| Release `workflow_dispatch` fallback | Tag-only trigger; a re-run means a new tag |
| GitHub auto-generated release notes | GitLab Release carries a static description; Play "What's new" still comes from the annotated tag |
| Dependabot **alerts** (not PRs) | No equivalent inbox on our tier — cutover step 1 says resolve-or-record, since alerts vanish silently |
| Issue/PR **numbering** | Archived GitHub as resolver (above) — the deliberate core of this ADR |

Secret Push Protection was confirmed **available** on our tier (Phase-0 Q3 — the setting exists), so the
prevention→detection downgrade contemplated in the plan is avoidable. It was observed `false`, and
**enabling it is an unticked cutover task** (runbook step 5) — an available mitigation, not a delivered one.

### Follow-ups
- Cutover sitting (runbook steps 1–10) and Phase 4 remain. Renovate needs a GitLab project access token
  that **expires annually** — calendar the renewal or dependency updates stop silently.
- Regenerate `docs/agent/BACKLOG.md` against real GitLab `iid`s on the first post-cutover `/checkpoint`;
  until then its numbers are GitHub-era and are not `glab issue view` arguments.
- Task 2.3 is now a **repair**, not just a URL swap: the Console's current privacy URL is a typo'd 404
  (`steps-of-bablylon`) — see `docs/release/data-safety-form.md`.

## Links
- Spec: `docs/superpowers/specs/2026-07-21-gitlab-migration-design.md` (Codex-reviewed, 19/19 applied)
- Plan: `docs/superpowers/plans/2026-07-23-gitlab-migration.md` (Codex-reviewed, 19/19 applied)
- Runbooks: `docs/migration/phase0-spike.md` · `phase1-ci-port.md` · `phase3-cutover-runbook.md`
- Tooling: `tools/migration-fingerprint.sh` (capture + verify, both tested against the live repo)
- PRs: #441 (Phase 1 CI port) · #442 (Phase 2 host decision) · #443 (Phase 3, lands at cutover) · #445
  (Play Console findings)
- Related ADRs: **ADR-0018** (CI on GitHub Actions — superseded in mechanism, amended-status pointer only,
  content unedited) · ADR-0043 (Codex Review Gate, which gated every artifact here) · ADR-0014 (i18n —
  the privacy URL appears in `values/` **and** `values-es/`, so the URL move touches both locales)
