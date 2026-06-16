# ADR-0018: Adopt GitHub Actions for CI/CD

**Status:** Accepted (merged to `main` via PR #100, 2026-06-03; post-merge setup — secrets, `release` environment, Play service account — completed 2026-06-04; pipeline fully live). **Branch protection was NOT actually configured at that time** (the workflows ran on PRs but nothing *required* them); it was applied 2026-06-16 — see the **2026-06-16 amendment** below.
**Date:** 2026-06-03
**Supersedes:** The deliberate "No CI for v1.0" posture documented in `devdocs/archaeology/intro2deployment.md` §4, `philosophy.md`, and the gap-analysis docs (all historical — left intact).
**Superseded by:** None

## Amendment — 2026-06-16: branch protection actually enforced

Decision #2 / decision #3 and the "Positive" consequence "the documented gate becomes machine-enforced …
block merges for the first time" described the *intent*. In practice `main` carried **no branch
protection** until 2026-06-16 — the original Status line's "branch protection … completed 2026-06-04" was
inaccurate (the API reported `main` unprotected; `statusCheckRollup` had no required checks). The Task-6
follow-up was, in effect, never done.

Branch protection is now applied to `main` (via the GitHub REST API; owner has ADMIN):

- **Required PR before merge** — `required_approving_review_count: 0` (this is a solo public repo; GitHub
  cannot require self-approval, so any non-zero count would hard-block the sole maintainer).
- **Required status checks (strict / up-to-date):** **`build-and-test`** (the `ci.yml` core gate — lint +
  unit + `assembleDebug` + Room schema-drift) and **`connected`** (the `instrumented.yml` emulator lane).
  Both must be green and the branch current with `main` before merge — making decisions #1 and #2 real.
- **`enforce_admins: true`** — protection applies to the owner too; **no direct pushes to `main`**, not
  even docs/checkpoint commits (this ADR amendment itself went via a PR).
- **`required_conversation_resolution: true`**; **force-pushes and branch deletion disabled.**

Reversible by the owner (ADMIN retained): `DELETE …/branches/main/protection/enforce_admins` for a one-off
bypass, or `DELETE …/branches/main/protection` to remove entirely. Flipping `enforce_admins` to `false`
gives the "protect normal flow but keep an owner escape hatch" posture that was the considered alternative.

## Context

The project shipped to a Play closed track with **no CI of any kind** — no `.github/`, `.circleci`, `.gitlab-ci.yml`, Jenkinsfile, fastlane, or Dockerfile. Every build and test ran on the maintainer's machine, and `docs/agent/STATE.md` / `RUN_LOG.md` served as the de-facto test log. This was an intentional v1.0 tradeoff (single maintainer, offline product, manual release checklist), but it has costs:

- Merged PRs were verified only on the submitter's laptop; `statusCheckRollup: []` (no required checks) means a green merge guarantees nothing.
- The `domain/` "zero Android imports" invariant was convention-only until `DomainPurityTest` (#27) landed — and even that test only blocks if someone runs it.
- The Room schema export (`app/schemas/`) can drift if a migration is added without committing the regenerated schema.
- Release signing + Play upload are entirely manual (a reused `versionCode` already caused the v13 Play rejection).

The build is well-suited to automation: a debug build needs **no secrets** (AdMob falls back to Google test IDs; there is no `google-services` plugin), the documented gate is a single Gradle line, and the repo is already on GitHub.

## Decision

**Adopt GitHub Actions** as the CI/CD platform, implemented per Plan 32 in three lanes plus supply-chain hardening:

1. **`ci.yml` — core gate.** On every PR and push to `main`: `./gradlew testDebugUnitTest lintDebug assembleDebug` on `ubuntu-latest` (JDK 17 temurin), plus a Room schema-drift guard (`git diff --exit-code app/schemas`). Secret-free, always blocking.
2. **`instrumented.yml` — emulator suite.** `connectedDebugAndroidTest` on an **API-34** emulator (`google_apis`, `x86_64`, KVM-accelerated) via `reactivecircus/android-emulator-runner`. **Blocking on PRs to `main` + nightly schedule + manual dispatch**, with AVD + Gradle caching.
3. **`release.yml` — release lane.** On a `v*` tag: unit-test guard → decode keystore from secrets → `bundleRelease` → `jarsigner -verify` → **auto-upload to the Play internal track** (`r0adkll/upload-google-play`) with R8 mapping + native debug symbols → attach AAB to a GitHub Release.
4. **Hardening:** Dependabot (`gradle` + `github-actions`), dependency-graph submission, least-privilege `permissions`, `concurrency` cancellation, and **all third-party actions SHA-pinned** with Dependabot maintaining the pins.

CI **does not auto-bump `versionCode`** — it builds the committed value, preserving the existing manual version discipline that the v13 rejection taught.

## Alternatives considered

- **A: Stay manual (status quo).** Rejected — the closed-track soak and any future contributor make the "verified on my laptop" model untenable; the cost of a missed regression now outweighs the setup cost.
- **B: GitLab CI / CircleCI / Jenkins.** Rejected — the repo is on GitHub; Actions has zero additional account/integration overhead and first-class Android emulator support via `android-emulator-runner`.
- **C: Instrumented tests nightly-only (non-blocking on PRs).** Rejected per decision #2 — the 9 instrumented tests guard real-framework invariants (SurfaceView lifecycle, Parcel deep-links) that the Robolectric suite can only stub; blocking-on-PR keeps them honest. A nightly run is added as the flake canary.
- **D: Stop the release lane at a signed-AAB artifact (no Play upload).** Rejected per decision #3 — auto-upload to the **internal** track directly advances Plan 31's "automate Play Console upload" priority. Internal (not closed/production) keeps blast radius low.
- **E: Major-version action tags (e.g. `@v4`) instead of SHA pins.** Rejected per decision #5 — SHA-pinning is the stronger supply-chain posture (a moved tag can't inject code); Dependabot neutralises the maintenance cost.

## Consequences

### Positive

- The documented gate becomes machine-enforced; `DomainPurityTest` and the schema-drift guard block merges for the first time.
- Release builds become reproducible and signed off-laptop; Play internal uploads are one `git tag` away.
- Supply-chain risk drops (SHA pins + Dependabot + dependency graph).
- Branch protection can finally require real checks before merge.

### Negative / tradeoffs

- Emulator jobs are slow (~8–12 min cached) and occasionally flaky — mitigated by AVD caching, a single retry, and the nightly canary; a persistent flake is re-run, not silenced.
- Signing secrets now live in GitHub (base64 keystore + passwords). Mitigated by an `environment: release` gate (optional manual approval) and least-privilege scoping; the keystore itself stays gitignored.
- SHA pins need periodic bumps — delegated to Dependabot's `github-actions` ecosystem.
- One-time Play prerequisites (service account, first manual AAB upload) before the release lane works end-to-end.

### Follow-ups

- Implement the three workflows + `dependabot.yml` (Plan 32 Tasks 1–4); resolve + commit the exact action SHAs.
- Configure the secrets + `release` environment (Task 5, done 2026-06-04) and branch protection (Task 6 — **done 2026-06-16, see the amendment above**; not actually applied in the original 2026-06-04 setup despite the original Status claim).
- On implementation, sync `README.md` (badge), `.kiro/steering/tech.md`, `source-files.md`, `structure.md`.
- Consider CodeQL (Kotlin) on a schedule as a later hardening pass.

## Links

- Plan: `docs/plans/plan-32-ci.md`
- Commit(s): _pending implementation_
- Related ADRs: ADR-0005 (billing SDK — release-build secrets context), ADR-0006 (ad SDK — test-ID fallback that makes secret-free CI builds safe), ADR-0007 (debug keystore)
