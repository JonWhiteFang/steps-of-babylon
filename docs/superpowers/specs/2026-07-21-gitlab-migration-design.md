# GitHub → GitLab Migration — Design Spec

**Date:** 2026-07-21
**Status:** Reviewed — Codex Review Gate passed 2026-07-21 (19 findings: 17 major / 2 minor; all 19
verified against code and applied; 0 refuted)
**Informed by:** agent-forum thread AF-2026-000016 (gaslight-agent consultation — firsthand
migration of gaslight-and-grimoire on 2026-07-20; their `docs/DECISIONS/ADR-0014-gitlab-migration.md`,
`.gitlab-ci.yml`, `renovate.json` at gitlab.com/kn0ck3r/gaslight-and-grimoire)

## Motivation

Consolidate all kn0ck3r projects on gitlab.com — the agent forum and gaslight-and-grimoire
already live there. Full cutover; GitHub is archived, not deleted.

## Decisions made during brainstorming

| Decision | Choice |
|---|---|
| Privacy-policy URL | Move to a jonwhitefang.uk custom **hostname** (GitLab Pages behind it), forge-neutral forever; one human-gated Play Console URL update + an in-app URL update (see Phase 2) |
| Release lane | 1:1 port — protected `v*` tag auto-builds signed AAB and auto-publishes to Play internal track (no manual-click gate) |
| Emulator lane fallback | Self-hosted GitLab runner on own hardware if the shared-runner KVM spike fails — with the isolation hardening required below |
| Dependency tooling | Adopt the gaslight stack: self-hosted Renovate weekly job + port gitleaks/osv-scan; no OWASP Dependency-Check job |
| Approach | Phased with pre-migration spike (Approach A) |

## Target state

**Repo:** `gitlab.com/kn0ck3r/steps-of-babylon` (public). The GitHub repo
(`JonWhiteFang/steps-of-babylon`) is archived and declared — in the migration ADR — the
canonical resolver for every historical `#N` citation in the memory spine. Historical docs are
never rewritten (they are point-in-time records); new work cites GitLab iids. GitHub's shared
issue/PR number sequence does not survive the import (GitLab numbers issues and MRs
independently), so this resolver rule is the numbering strategy, not a workaround.

**CI:** one `.gitlab-ci.yml` replacing the seven GitHub workflows. Two cross-cutting requirements
before the lane mapping:

- **Trigger matrix, not a generic rules block.** The seven workflows do NOT share one trigger set
  (core CI: PRs + `main` pushes; instrumented: MRs to `main` + nightly cron + manual dispatch;
  osv-scan: weekly cron + `main` + manual; pages: `main` path-filtered + manual; release: `v*`
  tags). The `workflow: rules:` block must enumerate `merge_request_event`, branch push, **tag
  push**, `schedule`, and `web` — and distinguish schedules/manual runs with a
  `PIPELINE_KIND=instrumented|osv|renovate|pages` variable keyed at the schedule/web level, so a
  generic schedule can't run the wrong jobs. Each current manual (`workflow_dispatch`) trigger is
  either preserved as a guarded `web` path or its retirement is recorded in the migration ADR.
  **Tag-pipeline gotcha:** in GitLab a tag push arrives as source `push` (with `CI_COMMIT_TAG`
  set) — a branch-only `push` rule would silently suppress every release pipeline. The workflow
  rules must admit tag pipelines explicitly.
- **The docs-only fast path keeps its scripted classifier — `rules:changes` cannot express it.**
  The current invariant is "skip the heavy gate only when EVERY changed path is allowlisted
  (`docs/**`, `*.md`, `.claude/**`, `.mcp.json`); any unknown path — and any unknown diff base —
  runs the full gate." GitLab `rules:changes` is match-any-path and cannot encode that fail-safe
  inversion. Port the classifier as a first-stage script job (diff base from
  `CI_MERGE_REQUEST_DIFF_BASE_SHA` / push SHAs, sufficient fetch depth, `code=true` on missing or
  invalid base), exporting its verdict via a dotenv artifact consumed by both the core-gate and
  instrumented jobs.

| Today (Actions) | On GitLab |
|---|---|
| `ci.yml` (PR gate) | MR-pipeline jobs preserving the **full current contract**, not just the headline steps: gradle-wrapper JAR validation (#212), `testDebugUnitTest` + `lintDebug` + `lintRelease` + `assembleDebug`, the seeded-license **unsigned `assembleRelease` R8/shrink guard** (seed + post-run cleanup of the license key), whole-app Kover reports (informational, #218) **and** `:app:koverVerifyDebug` (the fragile-zone ratchet, #373), benchmark-module compilation (type-check only), the Room schema-drift guard, detekt, CI report artifacts, and the **standalone SHA-pinned ktlint job** (own job, no Gradle/JDK setup). Docs-only fast path via the scripted classifier above. Runs on MR pipelines and pushes to `main` |
| `instrumented.yml` | Blocking MR job + nightly schedule (`PIPELINE_KIND=instrumented`) + manual trigger. Runs on shared runners if the Phase-0 KVM spike passes; otherwise on a **hardened** self-hosted runner (requirements in Phase 0) |
| `release.yml` | 1:1 port of behavior: protected `v*` tag → wrapper validation + guards → build signed AAB → publish to Play internal track → **GitLab Release with the AAB as a durable asset** (Generic Package Registry or equivalent non-expiring location linked from the release — NOT a short-lived job artifact; today's GitHub Release ships `app-release.aab` and that artifact must not be silently dropped). `resource_group: play-release` so two release pipelines can never publish concurrently. **Uploader must be chosen and proven in Phase 1** — `r0adkll/upload-google-play` is a GitHub Action with no GitLab equivalent; candidates are a pinned Fastlane (`supply`) container or the Gradle Play Publisher plugin (version-catalogued). Whichever is chosen must reproduce the full current upload config: package `com.whitefang.stepsofbabylon`, track `internal`, status `completed`, the AAB, `mapping.txt`, and `distribution/whatsnew`. **Full CI-variable inventory** (protected; masked where the format allows, base64-wrapped single-line values where raw content can't be masked; file-type where a file path is needed): `UPLOAD_KEYSTORE_BASE64`, `KEYSTORE_STORE_PASSWORD`, `KEYSTORE_KEY_ALIAS`, `KEYSTORE_KEY_PASSWORD`, `PLAY_SERVICE_ACCOUNT_JSON`, **`PLAY_LICENSE_KEY` (mandatory — a missing value fails the release build by design, #124)**, plus the four optional production AdMob values (`ADMOB_APP_ID`, `ADMOB_AD_UNIT_POST_ROUND_GEM`, `ADMOB_AD_UNIT_POST_ROUND_DOUBLE_PS`, `ADMOB_AD_UNIT_DAILY_FREE_CARD_PACK`; absent → Google test IDs). Materialization and post-job cleanup of secret files specified in the plan; protected-ref-only availability proven in the Phase-1 scratch job |
| `gitleaks.yml` | Port with **full history** (`GIT_DEPTH: 0` or explicit unshallow — a shallow default silently weakens the scan) and a defined findings surface (job log + retained report artifact). The current PR summary-comment behavior is retired as a small accepted regression (or reproduced via an MR note if cheap). **Layered-defense regression:** GitHub-native secret scanning + push protection do not port; GitLab Secret Push Protection is tier-dependent — verify availability on our tier in Phase 0, and if absent, record prevention→detection as an accepted, documented regression with an incident-response note |
| `osv-scan.yml` | NOT a mechanical port — today it is a Google **reusable workflow** that uploads SARIF to GitHub Code Scanning (non-gating). Port as a pinned osv-scanner binary/container job with the same triggers (weekly cron via `PIPELINE_KIND=osv` + `main` + manual), recursive scan, fail-open (`fail-on-vuln=false` semantics), and a retained human-readable + SARIF artifact. GitLab security-dashboard ingestion needs a paid tier — the Code-Scanning dashboard loss is an accepted regression recorded in the ADR |
| `dependency-submission.yml` | **Retired, no direct replacement.** GitLab has no equivalent of GitHub's dependency-submission API on our tier. Transitive-CVE visibility is covered by the osv-scan lane + Renovate. Accepted regression, documented in the ADR |
| Dependabot | Self-hosted Renovate: weekly scheduled CI job (`PIPELINE_KIND=renovate`) running the `renovate/renovate` image (`RENOVATE_PLATFORM=gitlab`), policy in a committed `renovate.json`. **Must carry over this repo's dependency policy, not just gaslight's defaults** — the current `.github/dependabot.yml` grouping exists because individually-green/combined-broken updates burned us: Gradle wrapper isolated in its own group, all other Gradle deps grouped (`all-gradle`), CI images in their own group, weekly cadence, no automerge, bounded open-MR count, Dependency Dashboard pinned issue, pinned image digests. Needs a project access token (api + write_repository — **expires annually; calendar the renewal**) and a GitHub PAT (`public_repo`) for changelog fetching. `.github/dependabot.yml` and the obsolete `.github/workflows/*` are deleted from the GitLab tree at cutover (Phase 3), after the fingerprint verification |
| `pages.yml` | The site is **Jekyll** (minima theme, front-matter Markdown) — a raw copy of `site/` into `public/` produces no `index.html`. The Pages job runs a **pinned Jekyll build** of `site/` into `public/` and asserts before publishing: `public/index.html` exists, contains the privacy heading, and preserves the `#delete-data` anchor. Publishes ONLY the built `site/` output (the internal `docs/` tree must never be publishable — same guarantee as today's workflow). Served behind a **jonwhitefang.uk custom hostname** (see Phase 2) |

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
current CI volume consume; (c) is GitLab Secret Push Protection available on our tier (feeds the
gitleaks-lane regression note). Outcome decides shared-runner vs self-hosted for the instrumented
lane. **Self-hosted fallback hardening is part of the go/no-go, not an afterthought:** on a public
repo, MR pipelines execute checked-out build code — a job tag is not isolation. The runner must be
a dedicated disposable VM/container executor with `/dev/kvm` passthrough, per-job reset, no
release/forum credentials, restricted network and filesystem access, and no access to other
projects. If that hardening is not achievable on available hardware, the fallback decision
reopens (Firebase Test Lab / demote-to-local become the candidates).

**Phase 1 — CI port on a scratch import.** Import a scratch copy of the repo, author the full
`.gitlab-ci.yml` (all lanes), iterate until everything is green. The release job runs **inert**
(publish step gated on a variable only the real project defines) but must still **prove the
uploader**: the chosen Fastlane/Gradle-Play-Publisher path validates against the full current
upload config (package name, `internal` track, `completed` status, AAB, `mapping.txt`,
`whatsnew`) — a dry-run/validate mode, not a live upload — and proves protected-variable
availability semantics on a protected ref. The real repo is untouched throughout.

**Phase 2 — Privacy-policy URL move (independent; must complete before Phase 3's archive
step).** The policy URL is not just a Play Console setting — it is **embedded in the shipped
app**: `presentation/ui/PrivacyPolicy.kt` (`PRIVACY_POLICY_URL`), the EN + ES
`hc_privacy_policy_body` strings, the `PrivacyPolicyUrlTest` drift guard, and the Data-Safety
runbook (`docs/release/data-safety-form.md`). The move therefore has four coordinated parts:
1. Stand up the new **hostname** (e.g. `privacy.jonwhitefang.uk` — a custom domain binds a
   hostname; a *path* under the apex site cannot be DNS-routed and would instead make the main
   website deployment own routing/TLS for it. The exact hostname is decided in the website-agent
   coordination thread; if a path is chosen anyway, that thread must explicitly assign the
   proxy/TLS ownership to the website deployment): GitLab Pages custom-domain TXT verification,
   DNS via website-agent, Let's Encrypt cert.
2. **Code + docs update in this repo** (normal PR): `PRIVACY_POLICY_URL`, both locale strings,
   `PrivacyPolicyUrlTest`, `site/_config.yml`/`site/index.md` self-references, data-safety
   runbook. Ships with the next `v*` release — the ordering constraint is "new URL live before
   the release that embeds it ships".
3. **Human:** Play Console privacy-policy + Data-safety URL update (incl. the `#delete-data`
   anchor) after the new hostname serves.
4. The github.io URL keeps serving (or 301-redirects) **indefinitely** — not merely until the
   Play Console change lands — because already-installed app versions render the old URL from
   their baked strings. Archiving the GitHub repo freezes but does not unpublish Pages, which
   satisfies this; the redirect option needs the old site's content replaced with a pointer page
   before archive.

**Phase 3 — Cutover (single sitting).** Runbook below.

**Phase 4 — Post-cutover.** Renovate job + tokens; the non-load-bearing docs sweep; close out
forum thread AF-2026-000016. (The load-bearing automation updates move INTO Phase 3 — see
runbook step 9.)

## Cutover runbook (Phase 3)

1. Quiesce GitHub: land or park every open PR, close Dependabot PRs, prune stale branches,
   final `/checkpoint` so the spine's last GitHub-era entry is clean.
2. Record the fingerprint: HEAD SHA, commit count, open/closed issue count, PR count, **and the
   full `v*` tag inventory — each tag's name, peeled commit SHA, object type (annotated, not
   lightweight-converted), and annotated message hash**. The release lane depends on tags: the
   versionCode-predecessor guard reads them and the annotated messages carry the Play "What's
   new" notes — a tag-lossy import breaks releases in ways HEAD/count checks never show.
3. Run GitLab's GitHub importer (NOT `git push --mirror` — the importer carries issues,
   PRs-as-MRs, comments, labels, open/closed state).
4. Verify the fingerprint matches — including the tag inventory — and spot-check a sample of
   imported issues/MRs for **authorship attribution, labels, comment/review threads, and
   attachments**, not just open/closed state.
5. Recreate merge gating + protected `main` / protected `v*` tags; land the Phase-1
   `.gitlab-ci.yml`; delete `.github/workflows/*` + `.github/dependabot.yml` from the GitLab
   tree.
6. **Human:** load the full release-variable inventory (the nine variables in the release-lane
   row) as protected CI variables.
7. Prove the gate: a deliberately red MR must be refused; a docs-only MR must go green in
   seconds (fast path intact).
8. **Flip the local remotes:** rename `origin` → `github-archive` (read-only), set `origin` to
   the GitLab URL, fetch, verify `origin/main` + tags, and run an authenticated non-release
   push/MR round-trip. (Without this, day-to-day pushes — and the first `v*` tag — still target
   the archived GitHub repo; `.git/config` currently points `origin` at github.com.)
9. **Update the load-bearing automation in-repo** (these break the moment GitHub is archived, so
   they cannot wait for Phase 4): `.claude/skills/checkpoint/`, `.claude/skills/release/`,
   `.claude/skills/complete-app-review/` (incl. `review-workflow.js`), `.claude/skills/new-migration/`,
   `.agent-forum/startup.md` + `shutdown.md`, `docs/agent/BACKLOG.md` regeneration — everything
   that shells out to `gh`, opens GitHub PRs/issues, watches Actions runs, or points at
   `.github/workflows/*` paths, moving to `glab`/GitLab equivalents. Historical issue citations
   stay untouched under the resolver rule.
10. Archive the GitHub repo. (Pages keeps serving the old policy URL per Phase 2 step 4.
    Nothing destructive ever happens: the GitHub repo remains intact, read-only, forever.)

## Human-gated checklist (never agent-executed)

- Play Console privacy-policy + Data-safety URL update (Phase 2).
- The full release-variable inventory into GitLab protected variables — keystore material,
  `PLAY_SERVICE_ACCOUNT_JSON`, `PLAY_LICENSE_KEY`, optional AdMob IDs (Phase 3).
- DNS record for the policy hostname (via website-agent coordination) (Phase 2).
- First real `v*` tag on GitLab — the owner pushes it and watches the publish end-to-end
  (Phase 4, whenever the next release is due).
- Renovate project access token creation + annual renewal reminder (Phase 4).
- Self-hosted runner provisioning + hardening sign-off, if the spike says we need one (Phase 0
  outcome).

## Docs & cross-project sweep (Phase 4 — the load-bearing automation subset runs in Phase 3
step 9 instead)

- `README.md` — badges, clone URLs, policy links.
- `CLAUDE.md` — CI/CD section, `gh`→`glab` in preferred tooling, forge references.
- `.agent-forum/message-guidance.md` + `.agent-forum/security.md` — both currently say the
  repo is GitHub-hosted and require plain-text citations because github.com links are
  refused; the convention flips to gitlab.com project links.
- `agents/babylon-agent.yaml` in the agent-forum repo — authority wording says "GitHub
  issue/PR tracking"; needs an MR in that repo.
- Steering/runbook surfaces that name GitHub facilities: `docs/steering/tech.md` (Actions, Code
  Scanning), `docs/steering/security-model.md` (secret-scanning/push-protection description),
  `docs/steering/source-files.md` (workflow entries), `docs/release/release-checklist.md`
  (release.yml references), `site/_config.yml` + `site/index.md` (old URL self-references —
  part of Phase 2), `app/build.gradle.kts` comments that cite Actions secrets by name.
- New ADR: the migration itself, the archived-GitHub-as-numbering-resolver rule, and the
  accepted regressions (dependency-submission, Code-Scanning dashboard, secret push protection
  if tier-gated, gitleaks MR comment).
- `docs/agent/STATE.md` + `RUN_LOG.md` per `/checkpoint`; `docs/plans/plan-32-ci.md` and
  ADR-0018 get amended-status pointers (historical content unedited).
- Closing reply on forum thread AF-2026-000016.
- **Preserve, never bulk-rewrite:** dated release notes, prior RUN_LOG entries, archived plans
  and reviews, and historical issue citations.

## Risks

1. **Emulator lane infeasible on shared runners** → settled first by the Phase-0 spike;
   pre-decided fallback is a self-hosted runner, which carries its own risk — **running public-MR
   code on personal hardware** — mitigated by the mandatory isolation hardening in Phase 0 (and
   the fallback decision reopens if that hardening isn't achievable).
2. **CI-minute exhaustion on the free tier** → measured in the spike; self-hosted runner
   also mitigates.
3. **Policy-URL breakage for shipped app versions** → eliminated by Phase 2's four-part
   ordering: old URL serves/redirects indefinitely; new URL embedded via a normal release.
4. **Release-lane misfire** → protected-tag + protected-variable structure; the Phase-1 inert
   uploader proof; owner-witnessed first tag; `resource_group` serialization; the tag-inventory
   fingerprint (step 2) protects the versionCode guard + release notes.
5. **Importer surprises (missing tags/issues/comments, wrong states, lost authorship)** →
   fingerprint + sampling verification with the GitHub repo still fully intact; abort costs
   nothing.
6. **Security-posture regressions** (secret push protection, Code-Scanning dashboard, gitleaks
   MR comments) → each is either verified-available on our tier (Phase 0) or explicitly accepted
   and documented in the migration ADR — never silently assumed ported.
7. **Renovate hosted app assumption** → none: Mend discontinued the hosted GitLab app;
   self-hosted is the plan of record from the start.

## Success criteria

- MR gate green on a real change; red MR provably refused; docs-only fast path green in
  seconds.
- Instrumented lane blocking on MRs (shared or hardened self-hosted runner).
- One owner-verified `v*` tag → signed AAB on the Play internal track **and a GitLab Release
  carrying the AAB as a durable asset**.
- Privacy policy live on the custom hostname, registered in Play Console, **and embedded in the
  app via a shipped release**; old URL still serving/redirecting.
- GitHub repo archived; historical-number resolver rule in an ADR; local `origin` points at
  GitLab (old remote kept read-only as `github-archive`).
- Docs and forum registry swept; automation (`/checkpoint`, `/release`, backlog regen, forum
  procedures) proven working against GitLab; thread AF-2026-000016 resolved.

## Out of scope

- Any change to release cadence, versioning, or Play track promotion.
- Rewriting historical docs/issue citations.
- Changing the privacy-policy *content* or its data-safety wording (URL/host + the in-app URL
  constant only).
- GitLab paid-tier features (merge trains, security dashboards, etc.).
