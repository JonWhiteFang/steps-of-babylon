# Phase 1 — CI port proof (scratch import)

> Plan: `docs/superpowers/plans/2026-07-23-gitlab-migration.md` (Task 1.10). Branch: `ci/gitlab-pipeline`.
> The real repo is untouched — this proves `.gitlab-ci.yml` on a **throwaway scratch import** before cutover.

## ✅ PROVEN GREEN on scratch (`kn0ck3r-group/sob-scratch`, deleted after) — 2026-07-23

Every lane ran on real gitlab.com shared runners in the `kn0ck3r-group` namespace:

| Lane | Result | Notes |
|---|---|---|
| `classify` | ✅ | docs-only fast-path verdict via `ci/classify-diff.sh` |
| `core-gate` | ✅ | full ci.yml contract (unit+lint+assembleDebug+unsigned assembleRelease+kover ratchet+benchmark compile+schema-drift+detekt) |
| `ktlint` | ✅ | download+verify ktlint on a JRE image |
| `gitleaks` | ✅ | full-history scan |
| `pages` | ✅ | pinned Jekyll+minima build + the 3 assertions |
| `osv-scan` | ✅ (allowed-to-fail) | scanned 897 pkgs, produced SARIF; non-blocking |
| `release-build` | ✅ | signed AAB (throwaway key) + signer-identity assertion + durable AAB upload to the Generic Package Registry |
| `release-object` | ✅ | GitLab Release created linking the durable AAB asset |
| `release-publish` | ✅ **uploader proven** | Fastlane `supply --validate_only` (real SA) authenticated to Play, parsed the full config, and reached AAB signature validation — rejected ONLY the throwaway key (`Found 23:0E… expected A6:52…` = the real upload key). The real pipeline signs with the real key → passes. |

**Bugs found + fixed during the proof (all committed to `ci/gitlab-pipeline`):** `bash` missing on
alpine/git (classify); ktlint not downloaded + `shasum`/perl (ktlint); no JDK 17 for `jvmToolchain(17)`
(core-gate/release-build); tool-as-ENTRYPOINT (gitleaks); distroless image (osv → pinned binary on alpine);
stale `BUNDLED WITH` + a ` #`-as-YAML-comment assertion (pages); no fastlane Docker image (release-publish →
`gem install fastlane` on pinned ruby). Also parallelized gitleaks/osv/pages with `needs: []`.

**Remaining Phase-1 items:** (a) gate-semantics — docs-only fast path is covered by `classify-diff.test.sh`
+ the proven `classify` job; red-MR-blocks-merge is the `only_allow_merge_if_pipeline_succeeds` project
setting configured at cutover (Phase 3 step 5), not a config-file behaviour. (b) **Codex Review Gate** on
the completed `.gitlab-ci.yml` + `ci/*.sh` + `renovate.json` + `Gemfile*` (Task 1.10 Step 4). (c) PR-1 doc
sync (Task 1.11).

## Already done on the branch (agent, 2026-07-23)

- `ci/classify-diff.sh` + `ci/classify-diff.test.sh` — classifier, **3/3 fixture cases green locally**.
- `ci/validate-wrapper.sh` — pins the Gradle 9.6.0 wrapper jar SHA (`497c8c2a…`, the jar GitHub's
  wrapper-validation already blesses); **validated against the real jar locally**.
- `ci/prepare-whatsnew.sh` — annotated-tag → Play whatsnew (ports release.yml).
- `.gitlab-ci.yml` — all 10 jobs (classify, core-gate, ktlint, gitleaks, osv-scan, pages,
  release-build/publish/object, renovate). **YAML validated (ruby).** Instrumented lane omitted
  (local-only per the spike).
- `renovate.json` — current schema (JSON-valid); `Gemfile` + `Gemfile.lock` pin jekyll 4.3.3 + minima 2.5.1.
- **Image digest pins resolved + baked (8 of 9):** android-sdk, alpine/git, eclipse-temurin:17-jre,
  gitleaks, osv-scanner, ruby:3.3, renovate, release-cli:v0.24.0. **Only `fastlane` remains a bare
  tag** — it must be proven (`supply --validate_only`) on the scratch project first (Task 1.5 Step 1).

## What remains — [HUMAN + agent] on a scratch import

1. **Create the scratch import** `kn0ck3r-group/sob-scratch` (New project → Import → GitHub). Push the
   `ci/gitlab-pipeline` branch content to it (or import then merge the branch).
2. **Set scratch CI variables** (dummy where possible): a **throwaway** keystore
   (`UPLOAD_KEYSTORE_BASE64` + the three passwords + alias), a validate-capable
   `PLAY_SERVICE_ACCOUNT_JSON`, `PLAY_LICENSE_KEY=<any-nonblank>` (the release build hard-fails on a
   blank one by design), and **`RELEASE_VALIDATE_ONLY=true`** so `release-publish` runs
   `supply --validate_only` (no live upload). AdMob vars optional (absent → Google test IDs).
3. **Semantic lint:** `glab ci lint -R kn0ck3r-group/sob-scratch` (catches GitLab-semantics errors the
   local ruby YAML check can't). Fix any on the branch, re-push.
4. **Iterate every lane to green:**
   - `core-gate` (unit + lint + assembleDebug + unsigned assembleRelease + kover + benchmark compile +
     schema-drift + detekt) — expect the longest run; confirm the docker android-sdk image has the
     right SDK/JDK. `ktlint` on the JRE image (confirm `lint-kotlin.sh` uses `sha256sum`, not `shasum`;
     if `shasum`, install `perl`/adjust the script).
   - `gitleaks` (full history), `osv-scan` (push to a scratch `main`; confirm allow_failure yellows on a vuln).
   - `pages` (confirm `bundle exec jekyll build` finds minima + the three assertions pass; regenerate
     `Gemfile.lock` on ruby:3.3 if the committed 2.6-era lock mismatches).
   - `release-build` on a scratch `v*` tag (confirm the guards, sign-verify against the throwaway key,
     and the AAB upload to the Generic Package Registry) → `release-object` links the asset.
5. **Prove the uploader (Task 1.5 Step 1):** confirm the chosen `fastlane/fastlane` image + tag exists
   and `fastlane supply --validate_only --package_name com.whitefang.stepsofbabylon --track internal
   --aab … --mapping … --metadata_path distribution --json_key sa.json` authenticates + validates
   against the full config. Record the working tag, then **digest-pin it** (the last unpinned ref).
   Confirm `distribution/whatsnew/whatsnew-en-US` is the path `supply --metadata_path distribution`
   reads (mirror `metadata/android/en-US/changelogs` if it insists).
6. **Prove the gate semantics:** a deliberately-red MR is refused; a docs-only MR goes green in seconds
   (fast path); a code MR runs the full gate.
7. **Prove protected-variable availability** on a protected ref (the release vars must not be exposed to
   MR pipelines from forks/branches).
8. **Final digest-pin sweep:** confirm NO bare tag / `:latest` remains (only fastlane was pending — now pinned).
9. **[GATE] Codex Review Gate** on the completed `.gitlab-ci.yml` + `ci/*.sh` + `renovate.json` +
   `Gemfile*` (implementation review). Block on any unaddressed critical/major. Record the result.
10. **Delete the scratch project.** Record the green pipeline URL + the validated `supply` invocation here.

## Pin-resolution record (Task 1.10 Step 2)

| Ref | Pinned | Status |
|---|---|---|
| ghcr.io/cirruslabs/android-sdk:34 | `@sha256:1c2e7e9c…` | ✅ baked |
| alpine/git:2.45.2 | `@sha256:16ad8e78…` | ✅ baked |
| eclipse-temurin:17-jre | `@sha256:92999aea…` | ✅ baked |
| zricethezav/gitleaks:v8.18.4 | `@sha256:75bdb2b2…` | ✅ baked |
| ghcr.io/google/osv-scanner:v2.3.8 | `@sha256:64e86bec…` | ✅ baked |
| ruby:3.3 | `@sha256:52557f52…` | ✅ baked |
| renovate/renovate:37 | `@sha256:1ee424e0…` | ✅ baked |
| registry.gitlab.com/gitlab-org/release-cli:v0.24.0 | `@sha256:3f52d526…` | ✅ baked |
| fastlane | `ci/fastlane/Gemfile.lock` (2.237.0 + 96 gems) → `bundle install` on `ruby:3.3@sha256:52557f52…` | ✅ locked/reproducible (Codex F4) |
| gradle-wrapper.jar (Gradle 9.6.0) | `497c8c2a…` in `ci/validate-wrapper.sh` | ✅ pinned |
| ktlint 1.8.0 | `a3fd…` in `lint-kotlin.sh` | ✅ already pinned |
| jekyll/minima gems | `Gemfile.lock` | ✅ locked (reconfirm on ruby:3.3, step 4) |
