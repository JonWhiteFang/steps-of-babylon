# Phase 3 — Cutover runbook (single sitting)

> Plan: `docs/superpowers/plans/2026-07-23-gitlab-migration.md` (Task 3.1). Branch for the automation half:
> `chore/gitlab-automation` (PR-3, merges at step 9).
> Target: `gitlab.com/kn0ck3r-group/steps-of-babylon` (namespace decided by the Phase-0 spike — the personal
> `kn0ck3r` namespace has 0 CI minutes; the group has 10k/mo).

**Abort costs nothing through step 9.** The GitHub repo stays fully intact and authoritative until step 10,
and step 10 is *archive*, never delete. If any verification below fails, stop — the worst case is a stale
GitLab project nobody uses.

**Do this in one sitting.** The window between step 1 (quiesce) and step 8 (remote flip) is the only period
where a commit could land in the wrong forge. Don't leave it open overnight.

**Prerequisite:** Phase 1 is merged (`d58722b`) so `.gitlab-ci.yml` + `ci/*.sh` are already on `main` —
inert on GitHub, live the moment GitLab imports them. Phase 2's URL move is **independent** and does not
gate cutover (see step 10's note on Pages).

---

## Step 1 — Quiesce GitHub

- [ ] Land or park every open PR. A PR left open on GitHub becomes an orphan: the importer brings MRs across,
      but any branch you then push to GitLab diverges from the GitHub PR nobody will merge.
- [ ] Close Renovate/Dependabot PRs (Renovate is re-established on GitLab in Phase 4 — its PRs are
      regenerable, so closing them loses nothing).
- [ ] Prune stale branches so the import doesn't carry dead refs. **Measured 2026-07-26: 27 branches**
      — mostly spent work (`docs/checkpoint-*`, the ten `i18n/34-pr*` branches, `release/v1.0.13`, two
      `dependabot/*`, `claude/ux-ui-design-resources-*`). Prune **before** capturing the fingerprint at
      step 2, or the fingerprint enshrines the mess and step 4's branch comparison becomes noise.
      `git branch -r --merged origin/main` lists the safe ones; an unmerged branch needs a decision,
      not a delete.
      > Do **not** reach for `rev-list --all` to reason about branch sprawl. A GitHub mirror of this repo
      > carries **293 `refs/pull/*` refs** (measured), so `--all` reads 1244 while branches+tags read
      > 805 — that gap is PR refs, *not* side-branch commits. It is also why the fingerprint records
      > `COMMITS_BRANCHES_TAGS`: a GitLab import has no `refs/pull/*`, so an `--all` comparison would
      > fail on a perfectly healthy import.
- [ ] Run `/checkpoint` — the last GitHub-era memory write.
- [ ] Confirm `git status` clean and `origin/main` == local `main`.

> **Dependabot alert check:** alert 34 (`google-protobuf` in the Pages `Gemfile.lock`) was **resolved
> 2026-07-26** (PR #444 — patched 3.25.8 plus an explicit security floor). Re-check for *new* alerts at
> quiesce time: alerts do NOT migrate, and GitLab has no equivalent inbox on our tier, so anything still
> open must be resolved or recorded in `docs/agent/BACKLOG.md` or it silently disappears.

## Step 2 — Record the fingerprint

This is the artifact the whole cutover is verified against, and it is captured once under time
pressure — so it is a **script**, not a copy-paste:

```bash
./tools/migration-fingerprint.sh capture      # writes docs/migration/fingerprint-github.txt
```

It aborts rather than writing a partial file if `gh` is missing/unauthenticated or any count comes back
blank or non-numeric, and it warns if any `v*` tag is lightweight. **Commit the output** — it must survive
the remote flip at step 8.

> **Both modes were tested end-to-end on 2026-07-26** (against the live GitHub repo): `capture` produced a
> full fingerprint, `verify` returned MATCH → exit 0 against an identical mirror and MISMATCH → exit 1 when
> HEAD differed, with all 13 tag lines comparing byte-identically across a mirror clone. **As of that test
> every `v*` tag is annotated** — the pre-condition holds today; re-confirm at capture time.

**What it records, and why each field is shaped that way** — read the script for the authoritative version;
this list is a summary, deliberately *not* a second copy of the commands (an earlier draft kept an
"equivalent" inline block and it immediately drifted out of equivalence):

| Field | Why |
|---|---|
| `HEAD` | The obvious one. |
| `COMMITS_HEAD` | Commits reachable from the default branch. |
| `COMMITS_BRANCHES_TAGS` | Branches + tags **only** — *not* `rev-list --all`. `--all` includes every ref the forge advertises, and those sets are forge-specific: a GitHub mirror of this repo carries **293 `refs/pull/*`** refs, so `--all` reads 1244 vs 805 for branches+tags. A GitLab import has no `refs/pull/*`, so an `--all` comparison would fail on a healthy import. |
| `ISSUES_OPEN` / `ISSUES_CLOSED` / `PRS` | Counted via `gh`, each validated as numeric **and** checked for a non-zero exit — a `gh` that prints partial output then fails would otherwise bake a plausible-looking wrong number into the oracle. |
| branch inventory | Recorded for a human comparison at step 4; not diffed, because step 1's pruning legitimately changes the set. |
| `v*` tag inventory | Name, peeled SHA, **object type**, and a hash of the message. See below. |

> **Why it mirror-clones the source rather than reading your checkout:** `verify` measures a mirror of the
> import, so `capture` must measure a mirror too or the two aren't comparable — a working checkout's ref set
> depends on what it happens to have fetched.

**Why the tag loop is the load-bearing part.** The release lane reads the **annotated tag message** to build
Play's "What's new" (`ci/prepare-whatsnew.sh`). A tag that arrives as *lightweight* still points at the right
commit and still triggers the pipeline — it just has no message, so the next release would silently publish
"Bug fixes and improvements." to the Play listing. `git cat-file -t` must print `tag`, not `commit`, for
every one. The `msg-sha` column pins the message *content*, not just its presence.

- [ ] Script ran to completion (it aborts on any failed count — no blank fields).
- [ ] It printed `OK: every v* tag is annotated` (not a lightweight-tag warning).
- [ ] Fingerprint committed.

## Step 3 — Run GitLab's GitHub importer

New project → Import project → GitHub. **Not `git push --mirror`** — a mirror push carries git objects only
and drops every issue, MR, comment, review thread, and label, which is most of what we're preserving.

- [ ] Import into `kn0ck3r-group`, project path `steps-of-babylon`.
- [ ] Wait for the importer to report complete (it is asynchronous; a partial import looks like a successful
      one if you check too early).

## Step 4 — Verify the fingerprint  ⛔ MISMATCH → ABORT

```bash
./tools/migration-fingerprint.sh verify git@gitlab.com:kn0ck3r-group/steps-of-babylon.git
```

It mirror-clones the **import** and diffs HEAD, both commit counts, and every `v*` tag (peeled SHA + object
type + message hash) against the committed fingerprint. **Exit 0 = match; non-zero = ABORT.**

> **Why it clones rather than reading your working tree:** `origin` still points at GitHub until step 8, so
> re-running the capture locally would re-measure *GitHub* and pass no matter what the importer did. This is
> the single easiest way to "verify" a cutover while proving nothing.

- [ ] `verify` exited 0. **A `type` column reading `commit` instead of `tag` means an annotated tag arrived
      lightweight** — the failure that silently degrades the next Play release's "What's new".
- [ ] Branch inventory compared by hand. On a MATCH the script prints **both** the import's branches and
      the fingerprint's recorded block, side by side; it deliberately does not diff them, because step 1's
      pruning legitimately changes the set between capture and import.
- [ ] Issue/MR counts sanity-checked against `ISSUES_OPEN`/`ISSUES_CLOSED`/`PRS` — remember GitLab splits
      PRs into MRs with their own `iid` space, so compare *totals*, never per-number.
- [ ] Sampled issues/MRs spot-checked for authorship, labels, comment/review threads, attachments — sample
      across the range (an early issue, a recent one, one with review comments, one with an image).
- [ ] Spot-check sampled issues and MRs for: authorship attribution, labels, comment threads, review threads,
      attachments. Sample across the range (an early issue, a recent one, one with review comments, one with
      an image).
- [ ] Confirm the issue/MR **counts** against the fingerprint, not just "looks about right".

> **Numbering will not match, and that is expected, not a failure.** GitHub PRs and issues share one number
> space; GitLab issues and MRs have separate `iid` space. Historical `#N` citations across the docs therefore
> resolve against **archived GitHub**, which is exactly why step 10 archives rather than deletes. Do **not**
> bulk-rewrite historical citations (ADR-0044).

## Step 5 — Recreate merge gating and land the pipeline

**The importer drops all branch protection.** Everything here is by hand, and every item is load-bearing.

- [ ] Settings → Merge requests → **Pipelines must succeed** (`only_allow_merge_if_pipeline_succeeds = true`).
      GitLab gates on the **whole pipeline**, not named checks — so every job in an MR pipeline blocks merge.
- [ ] Merge method → **merge commit** (no squash). Squash would rewrite the history we just verified.
- [ ] Protect `main`.
- [ ] Protect `v*` tags, **owner-only push**. This is the store-release hard gate: a `v*` tag publishes to the
      Play internal track, so this protection is what makes "the forum can never cause a release" true rather
      than aspirational. It is also what makes the release lane's protected CI variables available.
- [ ] Enable **Secret Push Protection** (Phase-0 Q3 confirmed it's available on our tier).
- [ ] Delete `.github/workflows/*` and `.github/dependabot.yml` from the GitLab tree (they are inert there,
      but leaving them invites editing the dead copy).
- [ ] Confirm `.gitlab-ci.yml` is present and a pipeline runs on the default branch.

## Step 6 — Load the release variables  [HUMAN]

Ten inputs, all as **protected** CI variables on release-eligible protected refs only. Sources: the OneDrive
`steps-of-babylon-local-files` bundle (keystore + passwords + AdMob), Play Console Licensing
(`PLAY_LICENSE_KEY`), and the Play service account JSON.

| Variable | Type | Note |
|---|---|---|
| `UPLOAD_KEYSTORE_BASE64` | var | base64 of the irreplaceable upload keystore |
| `KEYSTORE_STORE_PASSWORD` | var | |
| `KEYSTORE_KEY_ALIAS` | var | |
| `KEYSTORE_KEY_PASSWORD` | var | |
| `PLAY_SERVICE_ACCOUNT_JSON` | **file** | file-type, not var — the lane passes a path to `--json_key` |
| `PLAY_LICENSE_KEY` | var | **mandatory** — see below |
| `ADMOB_APP_ID` | var | |
| `ADMOB_AD_UNIT_POST_ROUND_GEM` | var | |
| `ADMOB_AD_UNIT_POST_ROUND_DOUBLE_PS` | var | |
| `ADMOB_AD_UNIT_DAILY_FREE_CARD_PACK` | var | |

- [ ] **`PLAY_LICENSE_KEY` is non-optional.** A blank value must fail the release build by design (#124 — the
      `app/build.gradle.kts` guard plus an explicit check in `release-build`). A release built fail-open would
      accept forged purchase receipts. Never weaken it to get a build out.
- [ ] **Confirm `RELEASE_VALIDATE_ONLY` is NOT set on this project.** It was `true` on the scratch project so
      Fastlane ran `--validate_only`. If it leaks onto the real project, tags will appear to succeed while
      publishing nothing to Play — a silent no-op release.
- [ ] Confirm in the UI that each of the ten exists, is marked **Protected**, and (for
      `PLAY_SERVICE_ACCOUNT_JSON`) has type **File**. Check for stray whitespace/newlines in the pasted
      values — a trailing newline in `KEYSTORE_STORE_PASSWORD` fails the signing step, not the variable check.
- [ ] **Do NOT treat a green pipeline on protected `main` as proof these variables work.** Nothing in an
      ordinary `main` pipeline reads them: the only consumers are `release-build`/`release-publish`, whose
      rules require `$CI_COMMIT_TAG =~ /^v/ && $CI_COMMIT_REF_PROTECTED == "true"`. A protected-`main` run
      would go green with all ten variables missing. **The first real proof is the first owner-pushed `v*`
      tag** (Phase 4, Task 4.1 Step 3) — which is exactly why that step is a required success criterion and
      is owner-witnessed end-to-end rather than assumed.

## Step 7 — Prove the gate

- [ ] A deliberately-red MR is **refused** merge (this is the `only_allow_merge_if_pipeline_succeeds` setting
      proving itself — it is a project setting, not a config-file behaviour, so it is untestable before now).
- [ ] A docs-only MR goes green in seconds via the `classify` fast path.
- [ ] A code MR runs the full gate.

## Step 8 — Flip local remotes

```bash
git remote rename origin github-archive
git remote set-url --push github-archive DISABLED      # push to the archive fails loudly, not silently
git remote add origin git@gitlab.com:kn0ck3r-group/steps-of-babylon.git
git fetch origin && git rev-parse origin/main && git ls-remote --tags origin | grep 'v'
```

- [ ] `git rev-parse origin/main` matches the fingerprint HEAD.
- [ ] `git ls-remote --tags origin` lists every `v*` tag.
- [ ] **Authenticated non-release round-trip:** push a throwaway branch and open + close an MR via `glab`.
      This proves the credential path works *without* touching a tag. Do not test authentication by pushing
      a `v*` tag — that publishes to Play.

## Step 9 — Land the automation flip

Merge PR-3 (`chore/gitlab-automation`, Task 3.2): every `gh`→`glab` conversion in the skills and forum
procedures. Until this merges, `/checkpoint`, `/release`, the backlog regen, and the forum procedures still
shell out to `gh` against a repo that is now read-only — they will fail, and `/release` failing *partway*
is the dangerous one.

- [ ] PR-3 merged.
- [ ] Smoke-test `/checkpoint`'s backlog regen against GitLab (`glab issue list`).

## Step 10 — Archive the GitHub repo

Settings → Archive this repository. **Never delete.**

- [ ] Archived.
- [ ] Confirm the old privacy URL still serves 200 (archiving freezes the repo but does not unpublish Pages).

### The old privacy URL is a live obligation, not a leftover

`https://jonwhitefang.github.io/steps-of-babylon/` stays serving a **full copy of the policy text**
indefinitely — every already-installed build has that URL baked in, and Phase 2 deliberately chose a full
copy over a redirect (GitHub Pages has no server-side 301, so `#delete-data` propagation is unguaranteed and
a JS pointer is invisible to a non-JS fetcher such as a Play validator).

Because archiving makes the repo **read-only**, a future policy revision cannot reach that copy without the
following procedure. Write it down here precisely because you will need it under time pressure — a policy
revision is usually triggered by an external deadline (a new SDK, a new data category, a law change), and
the failure mode is shipping a revision to the new URL while the old URL keeps serving superseded text, i.e.
**two divergent live policies, one of them the URL declared to Google**.

**Unarchive → update → re-archive:**

1. GitHub → Settings → **Unarchive** `JonWhiteFang/steps-of-babylon`.
2. Update `site/index.md` on the GitHub copy to the new text **and** bump its effective date.
3. Let the GitHub Pages workflow deploy (`.github/workflows/pages.yml` still exists on that copy — it is
   inert on GitLab but live on GitHub, which is what makes this possible at all).
4. Verify the old URL serves the new text **and** still resolves `#delete-data`.
5. **Re-archive** immediately.
6. Same sitting: update the canonical copy in GitLab (`site/index.md`), the new hosted URL via the website
   agent's forum thread, and both Play Console URL fields if the deletion anchor moved.

Do steps 1–6 as one unit. Both copies must always state the same policy; a half-finished revision is the
divergence this procedure exists to prevent.

---

## Post-cutover (Phase 4, separate)

Renovate tokens + first scheduled run; the first **owner-witnessed** `v*` tag publishing end-to-end on
GitLab (release-build → release-publish → Play internal + a GitLab Release carrying the durable AAB);
the doc/forum sweep; ADR-0044. See the plan's Phase 4.

## What this runbook deliberately does not do

- **No instrumented-test lane.** Phase-0 Q1 failed (no `/dev/kvm` on shared runners) and self-hosted/Firebase
  were declined, so the 9 `:app:connectedDebugAndroidTest` tests are a **local pre-release device step**, not
  a pipeline job. That replacement gate lives in `docs/release/release-checklist.md` — it is the one accepted
  regression that silently weakens the MR gate, so it needs a human to actually run it.
- **No history rewrite.** No `#N` citation renumbering, no squash, no filter-branch.
- **No production-track change.** Promotion to production stays a manual Console action (#383).
