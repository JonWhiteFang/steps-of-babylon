# GitHub → GitLab Migration — Design Spec

**Date:** 2026-07-21
**Status:** Draft (pending adversarial review gate)
**Informed by:** agent-forum thread AF-2026-000016 (gaslight-agent consultation — firsthand
migration of gaslight-and-grimoire on 2026-07-20; their `docs/DECISIONS/ADR-0014-gitlab-migration.md`,
`.gitlab-ci.yml`, `renovate.json` at gitlab.com/kn0ck3r/gaslight-and-grimoire)

## Motivation

Consolidate all kn0ck3r projects on gitlab.com — the agent forum and gaslight-and-grimoire
already live there. Full cutover; GitHub is archived, not deleted.

## Decisions made during brainstorming

| Decision | Choice |
|---|---|
| Privacy-policy URL | Move to a jonwhitefang.uk custom domain (GitLab Pages behind it), forge-neutral forever; one human-gated Play Console URL update |
| Release lane | 1:1 port — protected `v*` tag auto-builds signed AAB and auto-publishes to Play internal track (no manual-click gate) |
| Emulator lane fallback | Self-hosted GitLab runner on own hardware if the shared-runner KVM spike fails |
| Dependency tooling | Adopt the gaslight stack: self-hosted Renovate weekly job + port gitleaks/osv-scan; no OWASP Dependency-Check job |
| Approach | Phased with pre-migration spike (Approach A) |

## Target state

**Repo:** `gitlab.com/kn0ck3r/steps-of-babylon` (public). The GitHub repo
(`JonWhiteFang/steps-of-babylon`) is archived and declared — in the migration ADR — the
canonical resolver for every historical `#N` citation in the memory spine. Historical docs are
never rewritten (they are point-in-time records); new work cites GitLab iids. GitHub's shared
issue/PR number sequence does not survive the import (GitLab numbers issues and MRs
independently), so this resolver rule is the numbering strategy, not a workaround.

**CI:** one `.gitlab-ci.yml` with an explicit `workflow: rules:` block enumerating pipeline
sources (`merge_request_event`, push to `main`, `schedule`, `web`) — required to avoid
duplicate branch+MR pipelines. Lane mapping from the seven GitHub workflows:

| Today (Actions) | On GitLab |
|---|---|
| `ci.yml` (PR gate) | MR-pipeline jobs: ktlint/detekt, JVM unit tests, assembleDebug, Room schema-drift, scoped Kover floor. Docs-only fast path preserved via `rules:changes` (classifier semantics kept: only `docs/**`, `*.md`, `.claude/**`, `.mcp.json` skip the heavy steps; fail-safe default is the full gate) |
| `instrumented.yml` | Blocking MR job + nightly schedule (distinguished by a schedule-level variable). Runs on shared runners if the Phase-0 KVM spike passes; otherwise on a self-hosted runner tagged for this job only |
| `release.yml` | 1:1 port: protected `v*` tag → build signed AAB → auto-publish to Play internal track. `resource_group: play-release` so two release pipelines can never publish concurrently. Keystore + Play service-account JSON as **protected, masked, file-type** CI variables — injected only on protected refs, structurally unavailable to every MR/branch pipeline |
| `gitleaks.yml` | Direct port (config already repo-committed in `.gitleaks.toml`) |
| `osv-scan.yml` | Direct port |
| `dependency-submission.yml` | **Retired, no direct replacement.** GitLab has no equivalent of GitHub's dependency-submission API on our tier. Transitive-CVE visibility is covered by osv-scan + Renovate. Accepted regression, documented in the ADR |
| Dependabot | Self-hosted Renovate: weekly scheduled CI job running the `renovate/renovate` image (`RENOVATE_PLATFORM=gitlab`), policy in a committed `renovate.json` adapted from gaslight-and-grimoire, Dependency Dashboard as a pinned issue. Needs a project access token (api + write_repository — **expires annually; calendar the renewal**) and a GitHub PAT (`public_repo`) for changelog fetching. Also maintains CI image pins (a `gitlab-ci` image group replaces Dependabot's Actions-SHA-pin maintenance) |
| `pages.yml` | GitLab Pages job publishing **only** the top-level `site/` folder (copy into `public/` artifact), behind a **jonwhitefang.uk custom domain** (DNS TXT verification + CNAME/A, Let's Encrypt built in). The internal `docs/` tree must never be publishable — the job publishes an explicit artifact, same guarantee as today's workflow |

**Merge gating (recreated deliberately — the importer drops all branch protection; this was
the Blocker finding in gaslight-agent's own pre-migration review):**
- `only_allow_merge_if_pipeline_succeeds` = true; merge-commit-only (no squash) to match
  current convention.
- `workflow: rules:` must guarantee an MR pipeline always exists — the setting blocks merge
  when there is *no* pipeline at all.
- `main` protected branch; `v*` protected tags (only the owner can push them — the
  "forum can never cause a store release" rule keeps its exact current meaning).
- Sequential stacked-MR merges stay manual as today (merge trains are paid-tier).
- Gating semantics are coarser than GitHub's named required checks: the *whole pipeline*
  must be green. Every job in the MR pipeline must therefore deserve to block merge.

**Daily tooling:** `gh` → `glab` (raw GitLab REST API where `glab` falls short). Citation
convention in docs and the agent forum flips from plain-text
`JonWhiteFang/steps-of-babylon#<n>` to the GitLab project's paths; gitlab.com links become
permitted (they pass forum screening).

## Phases

**Phase 0 — Spike (go/no-go input, nothing else moves first).** Throwaway GitLab project
with a ~20-line pipeline: (a) does `/dev/kvm` exist on gitlab.com shared runners on our tier,
and can a hardware-accelerated API-34 emulator boot and run one instrumented test; (b) what
compute-minute allowance does a public project on our tier actually get, and what would the
current CI volume consume. Outcome decides shared-runner vs self-hosted for the instrumented
lane. (Fallback pre-decided: self-hosted runner; the spike failing does not block migration.)

**Phase 1 — CI port on a scratch import.** Import a scratch copy of the repo, author the full
`.gitlab-ci.yml` (all lanes; release job present but publish step inert until real secrets
exist — e.g. gated on a variable only the real project defines), iterate until everything is
green. The real repo is untouched throughout.

**Phase 2 — Privacy-policy URL move (independent; must complete before Phase 3's archive
step).** Stand up the custom domain (exact hostname — subdomain vs path under jonwhitefang.uk — is
decided in the website-agent coordination thread, not here): DNS via website-agent,
GitLab Pages domain verification, then the **human owner** updates the Play Console
privacy-policy + Data-safety URLs (including the `#delete-data` anchor). The github.io URL
keeps serving until the Play Console change is confirmed live — no gap.

**Phase 3 — Cutover (single sitting).** Runbook below.

**Phase 4 — Post-cutover.** Renovate job + tokens; docs & cross-project sweep; close out
forum thread AF-2026-000016.

## Cutover runbook (Phase 3)

1. Quiesce GitHub: land or park every open PR, close Dependabot PRs, prune stale branches,
   final `/checkpoint` so the spine's last GitHub-era entry is clean.
2. Record the fingerprint: HEAD SHA, commit count, open/closed issue count, PR count.
3. Run GitLab's GitHub importer (NOT `git push --mirror` — the importer carries issues,
   PRs-as-MRs, comments, labels, open/closed state).
4. Verify the fingerprint matches; spot-check a sample of imported issues/MRs.
5. Recreate merge gating + protected `main` / protected `v*` tags; land the Phase-1
   `.gitlab-ci.yml`.
6. **Human:** load release secrets as protected/masked/file-type CI variables.
7. Prove the gate: a deliberately red MR must be refused; a docs-only MR must go green in
   seconds (fast path intact).
8. Archive the GitHub repo. (Pages keeps serving until Phase 2 is confirmed — ordering
   guarantees no policy-URL gap. Nothing destructive ever happens: the GitHub repo remains
   intact, read-only, forever.)

## Human-gated checklist (never agent-executed)

- Play Console privacy-policy + Data-safety URL update (Phase 2).
- Release keystore + Play service-account JSON into GitLab protected variables (Phase 3).
- DNS record for the policy domain (via website-agent coordination) (Phase 2).
- First real `v*` tag on GitLab — the owner pushes it and watches the publish end-to-end
  (Phase 4, whenever the next release is due).
- Renovate project access token creation + annual renewal reminder (Phase 4).
- Self-hosted runner registration, if the spike says we need one (Phase 0 outcome).

## Docs & cross-project sweep (Phase 4)

- `README.md` — badges, clone URLs, policy links.
- `CLAUDE.md` — CI/CD section, `gh`→`glab` in preferred tooling, forge references.
- `.agent-forum/message-guidance.md` + `.agent-forum/security.md` — both currently say the
  repo is GitHub-hosted and require plain-text citations because github.com links are
  refused; the convention flips to gitlab.com project links.
- `agents/babylon-agent.yaml` in the agent-forum repo — authority wording says "GitHub
  issue/PR tracking"; needs an MR in that repo.
- New ADR: the migration itself, the archived-GitHub-as-numbering-resolver rule, and the
  dependency-submission retirement.
- `docs/agent/STATE.md` + `RUN_LOG.md` per `/checkpoint`; `docs/plans/plan-32-ci.md` and
  ADR-0018 get amended-status pointers (historical content unedited).
- Closing reply on forum thread AF-2026-000016.

## Risks

1. **Emulator lane infeasible on shared runners** → settled first by the Phase-0 spike;
   pre-decided fallback is a self-hosted runner (adds a machine that must be online for MRs
   to merge — accepted).
2. **CI-minute exhaustion on the free tier** → measured in the spike; self-hosted runner
   also mitigates.
3. **Policy-URL gap breaking Play compliance** → eliminated by ordering (Phase 2 completes
   before the archive step).
4. **Release-lane misfire** → protected-tag + protected-variable structure; owner-witnessed
   first tag; `resource_group` serialization.
5. **Importer surprises (missing issues/comments, wrong states)** → fingerprint verification
   with the GitHub repo still fully intact; abort costs nothing.
6. **Renovate hosted app assumption** → none: Mend discontinued the hosted GitLab app;
   self-hosted is the plan of record from the start.

## Success criteria

- MR gate green on a real change; red MR provably refused; docs-only fast path green in
  seconds.
- Instrumented lane blocking on MRs (shared or self-hosted runner).
- One owner-verified `v*` tag → signed AAB on the Play internal track.
- Privacy policy live on the custom domain and registered in Play Console.
- GitHub repo archived; historical-number resolver rule in an ADR.
- Docs and forum registry swept; thread AF-2026-000016 resolved.

## Out of scope

- Any change to release cadence, versioning, or Play track promotion.
- Rewriting historical docs/issue citations.
- Moving the privacy-policy *content* or its data-safety wording (URL/host only).
- GitLab paid-tier features (merge trains, etc.).
