# /backup-to-vault Skill Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a user-invocable `/backup-to-vault` skill that mirrors the repo's gitignored essentials (secrets, build helper) and full documentation set into the developer's Obsidian vault — secrets `age`-encrypted, docs rsync-mirrored — for disaster recovery and fresh-machine bootstrap.

**Architecture:** A single checklist-style `SKILL.md` (the same shape as the existing `/checkpoint` and `/release` skills) that Claude follows step-by-step. There is no executable code and no compiled artifact, so verification is by **dry-run** of each procedure against the real repo — never by a unit-test runner. The passphrase never enters the transcript: the `age` encrypt/decrypt step is run by the developer via the `! ` prompt prefix.

**Tech Stack:** Markdown skill definition; `age` (passphrase mode) for encryption; `rsync --delete` for the docs mirror; `tar` + `mktemp` for staging; macOS `zsh`.

**Reference spec:** `docs/superpowers/specs/2026-06-24-backup-to-vault-skill-design.md`

---

## File Structure

- **Create:** `.claude/skills/backup-to-vault/SKILL.md` — the entire skill. One file, one responsibility (the backup procedure). This matches the existing single-file skill convention in `.claude/skills/*/SKILL.md`; no helper scripts (the skill instructs ad-hoc shell, as `/release` does).

No other repo files change. The skill is a personal utility and is deliberately **not** wired into the PR doc-sweep, so `STATE.md`/`RUN_LOG.md`/`CHANGELOG.md` are untouched.

**Verification model:** Because a `SKILL.md` is instructions, not code, each task's "test" is a dry-run: execute the exact shell the section prescribes against the real repo into a **scratch directory** (NOT the real vault, NOT with the real passphrase), and confirm the observable output. The real vault write and real `age -p` run happen only once, in the final end-to-end task, with the developer driving the passphrase.

---

## Task 1: Scaffold the skill file with frontmatter and overview

**Files:**
- Create: `.claude/skills/backup-to-vault/SKILL.md`

- [ ] **Step 1: Create the skill directory and file with frontmatter + overview**

Create `.claude/skills/backup-to-vault/SKILL.md` with exactly this content:

````markdown
---
name: backup-to-vault
description: Back up the repo's gitignored essentials (release keystore, signing passwords, AdMob IDs, run-gradle.sh) and full documentation set to the developer's Obsidian vault — secrets age-encrypted into one bundle, docs rsync-mirrored. Run when the developer says "backup to vault", "back up to obsidian", "sync to my vault", or wants a disaster-recovery / fresh-machine copy of the project essentials.
disable-model-invocation: true
---

# /backup-to-vault — Mirror repo essentials to the Obsidian vault

User-invoked only (it writes outside the repo, into a cloud-synced vault). This skill captures the
things `git clone` will NOT give you on a fresh machine — the gitignored secrets and build helper —
plus a full documentation snapshot for offline reading in Obsidian. The tracked source code is
already on GitHub and is deliberately NOT copied.

**Reference design:** `docs/superpowers/specs/2026-06-24-backup-to-vault-skill-design.md`.

## Mental model

- **Vault target:** `/Users/jpawhite/Documents/kn0ck3r-vault/Claude/steps-of-babylon`
- **Secrets** are packed into one tar and encrypted with `age` (passphrase mode) → `secrets.enc`.
  The vault holds only ciphertext + a manifest of filenames (never values).
- **Docs** are rsync-mirrored (`--delete`, so the vault tracks the repo exactly).
- **Passphrase isolation:** Claude must NEVER see or type the passphrase — the transcript itself may
  sync. The developer runs the `age` step via the `! ` prompt prefix. Claude only stages, verifies,
  and cleans up.
- **Re-run behavior:** mirror mode — each run refreshes docs and regenerates the bundle + generated
  docs. No timestamped snapshots, no cron, not part of the PR doc-sweep.
````

- [ ] **Step 2: Verify the file exists and frontmatter is well-formed**

Run: `head -5 .claude/skills/backup-to-vault/SKILL.md`
Expected: the `---` frontmatter block with `name: backup-to-vault` and `disable-model-invocation: true`.

- [ ] **Step 3: Commit**

```bash
git add .claude/skills/backup-to-vault/SKILL.md
git commit -m "feat(skill): scaffold /backup-to-vault (frontmatter + mental model)"
```

---

## Task 2: Add the Preflight section

**Files:**
- Modify: `.claude/skills/backup-to-vault/SKILL.md` (append)

- [ ] **Step 1: Append the Preflight section**

Append to `SKILL.md`:

````markdown
## Step 1 — Preflight (refuse to run if any check fails)

Create a TodoWrite item per numbered step below and work them in order.

Run these checks and STOP with a clear message if any fails:

```bash
# Tooling
command -v age   || echo "MISSING age — install with: brew install age"
command -v rsync || echo "MISSING rsync"

# Vault parent must already exist (guards against path typos creating junk dirs)
VAULT_PARENT="/Users/jpawhite/Documents/kn0ck3r-vault/Claude"
VAULT="$VAULT_PARENT/steps-of-babylon"
[ -d "$VAULT_PARENT" ] && echo "vault parent OK: $VAULT_PARENT" || echo "MISSING vault parent: $VAULT_PARENT — STOP"

# Must be run from the repo root (where CLAUDE.md + docs/ live)
[ -f CLAUDE.md ] && [ -d docs ] && echo "repo root OK" || echo "NOT at repo root — STOP"
```

- If `age` is missing: STOP and tell the developer to `brew install age`, then re-run.
- If the vault parent or repo-root check fails: STOP — do not create anything.
- Only if every check passes: `mkdir -p "$VAULT"` and continue.
````

- [ ] **Step 2: Dry-run the preflight against the real environment**

Run the preflight block above in the shell.
Expected: `MISSING age — install with: brew install age` (age is not yet installed), `rsync` OK, `vault parent OK`, `repo root OK`. This confirms the checks fire correctly. (The age line is the expected current state; it proves the guard works.)

- [ ] **Step 3: Commit**

```bash
git add .claude/skills/backup-to-vault/SKILL.md
git commit -m "feat(skill): /backup-to-vault preflight (age/rsync/vault-parent/repo-root guards)"
```

---

## Task 3: Add the Docs Mirror section

**Files:**
- Modify: `.claude/skills/backup-to-vault/SKILL.md` (append)

- [ ] **Step 1: Append the Docs Mirror section**

Append to `SKILL.md`:

````markdown
## Step 2 — Mirror documentation into the vault

Mirror the full docs set so the vault tracks the repo exactly (files deleted from the repo are
removed from the vault copy). Copy the two root guides individually, then the whole `docs/` tree.

```bash
# Root guides
rsync -a CLAUDE.md README.md "$VAULT/docs/"

# Full docs tree (excludes transient/VCS noise; docs/ has none normally, but be defensive)
rsync -a --delete \
  --exclude '.git/' --exclude 'build/' --exclude '.gradle/' \
  --exclude '.idea/' --exclude '*.log' \
  docs/ "$VAULT/docs/docs/"
```

Note the nested `docs/docs/` is intentional: `$VAULT/docs/` holds the two root guides + the mirrored
`docs/` subtree, keeping the repo's `docs/`-relative paths intact under `$VAULT/docs/docs/`.

After mirroring, capture a count for the summary:

```bash
find "$VAULT/docs" -type f | wc -l
```
````

- [ ] **Step 2: Dry-run the mirror into a scratch dir (NOT the real vault)**

```bash
SCRATCH="$(mktemp -d -t sob-vault-dryrun)"
rsync -a CLAUDE.md README.md "$SCRATCH/docs/"
rsync -a --delete --exclude '.git/' --exclude 'build/' --exclude '.gradle/' --exclude '.idea/' --exclude '*.log' docs/ "$SCRATCH/docs/docs/"
echo "files mirrored: $(find "$SCRATCH/docs" -type f | wc -l)"
ls "$SCRATCH/docs/" | head
rm -rf "$SCRATCH"
```

Expected: a non-zero file count, and `ls` shows `CLAUDE.md`, `README.md`, and a `docs` subdirectory. Scratch dir removed.

- [ ] **Step 3: Commit**

```bash
git add .claude/skills/backup-to-vault/SKILL.md
git commit -m "feat(skill): /backup-to-vault docs mirror (rsync --delete, root guides + docs tree)"
```

---

## Task 4: Add the Stage-Secrets + Manifest section

**Files:**
- Modify: `.claude/skills/backup-to-vault/SKILL.md` (append)

- [ ] **Step 1: Append the Stage-Secrets section**

Append to `SKILL.md`:

````markdown
## Step 3 — Stage the secrets tar (Claude does this; passphrase NOT involved yet)

Build the list of secret files, skipping any that are absent (e.g. `adi-registration.properties` may
not exist). Tar them — with repo-relative paths — into a `0600` temp file OUTSIDE the vault.

```bash
# Candidate secret files (repo-relative). adi-registration is optional.
CANDIDATES=(
  local.properties
  keystore.properties
  release/upload-keystore.jks
  release/upload-cert.pem
  run-gradle.sh
  app/src/main/assets/adi-registration.properties
)

PRESENT=()
for f in "${CANDIDATES[@]}"; do
  if [ -e "$f" ]; then PRESENT+=("$f"); echo "PRESENT: $f"; else echo "ABSENT (skipped): $f"; fi
done

# 0600 temp file so the plaintext tar is owner-only while it briefly exists
STAGED_TAR="$(mktemp -t sob-secrets)"
chmod 600 "$STAGED_TAR"
tar -cf "$STAGED_TAR" "${PRESENT[@]}"
echo "staged tar: $STAGED_TAR ($(wc -c < "$STAGED_TAR") bytes)"
```

Write the manifest into the vault — **filenames only, never contents:**

```bash
{
  echo "# Secrets bundle manifest"
  echo
  echo "Files packed into \`secrets.enc\` at last backup (paths are repo-relative):"
  echo
  for f in "${PRESENT[@]}"; do echo "- \`$f\`"; done
  echo
  echo "Decrypt + restore: see SETUP.md."
} > "$VAULT/secrets.manifest.md"
```
````

- [ ] **Step 2: Dry-run staging against the real repo (tar to scratch, inspect, delete)**

```bash
SCRATCH_TAR="$(mktemp -t sob-secrets-dryrun)"; chmod 600 "$SCRATCH_TAR"
tar -cf "$SCRATCH_TAR" local.properties keystore.properties release/upload-keystore.jks release/upload-cert.pem run-gradle.sh
echo "perms: $(stat -f '%Lp' "$SCRATCH_TAR")  size: $(wc -c < "$SCRATCH_TAR")"
tar -tf "$SCRATCH_TAR"
rm "$SCRATCH_TAR"
```

Expected: perms `600`, non-zero size, and `tar -tf` lists the 5 present files with their repo-relative paths (no `adi-registration.properties`, which is absent). Temp tar removed.

- [ ] **Step 3: Commit**

```bash
git add .claude/skills/backup-to-vault/SKILL.md
git commit -m "feat(skill): /backup-to-vault stage secrets tar (mktemp 0600) + filenames-only manifest"
```

---

## Task 5: Add the Encrypt + Verify + Cleanup section (passphrase isolation)

**Files:**
- Modify: `.claude/skills/backup-to-vault/SKILL.md` (append)

- [ ] **Step 1: Append the Encrypt/Verify/Cleanup section**

Append to `SKILL.md`:

````markdown
## Step 4 — Encrypt (DEVELOPER runs this — Claude must NOT type the passphrase)

The passphrase must never enter the transcript. Ask the developer to run this themselves using the
`! ` prompt prefix (substitute the real `$STAGED_TAR` path printed in Step 3 and the real `$VAULT`):

```
! age -p -o "<VAULT>/secrets.enc" "<STAGED_TAR>"
```

`age -p` prompts interactively on the TTY for a passphrase. Tell the developer to store that
passphrase somewhere safe and OUTSIDE the vault (e.g. a password manager) — it is required to
restore, and the vault backup is useless without it.

## Step 5 — Verify the bundle, then clean up the staged tar

```bash
if [ -s "$VAULT/secrets.enc" ]; then
  echo "secrets.enc OK: $(wc -c < "$VAULT/secrets.enc") bytes"
  rm "$STAGED_TAR"           # best-effort cleanup (NOT a secure wipe — see below)
  echo "staged tar removed"
else
  echo "secrets.enc MISSING or EMPTY — keeping staged tar at $STAGED_TAR so you can retry. DO NOT delete it."
fi
```

> **Why no secure wipe:** `rm -P` is a documented no-op on modern macOS and APFS gives no
> secure-overwrite guarantee, so this skill does not claim one. The mitigation is the `0600` perms
> and the short lifetime of the staged tar (it exists only between Step 3 and this cleanup).
````

- [ ] **Step 2: Dry-run the verify/cleanup logic with a fake bundle (no real age, no passphrase)**

```bash
SCRATCH="$(mktemp -d -t sob-verify-dryrun)"
STAGED_TAR="$(mktemp -t sob-secrets-dryrun)"; echo data > "$STAGED_TAR"
# Simulate a successful encryption by creating a non-empty secrets.enc:
echo ciphertext > "$SCRATCH/secrets.enc"
if [ -s "$SCRATCH/secrets.enc" ]; then echo "OK path: removing staged tar"; rm "$STAGED_TAR"; else echo "would keep tar"; fi
# Simulate the failure path:
rm -f "$SCRATCH/secrets.enc"; STAGED_TAR2="$(mktemp -t sob-secrets-dryrun2)"; echo data > "$STAGED_TAR2"
if [ -s "$SCRATCH/secrets.enc" ]; then echo "unexpected"; else echo "FAIL path: keeping $STAGED_TAR2"; fi
rm -f "$STAGED_TAR2"; rm -rf "$SCRATCH"
```

Expected: prints `OK path: removing staged tar` then `FAIL path: keeping ...` — confirming both branches behave. All scratch artifacts removed.

- [ ] **Step 3: Commit**

```bash
git add .claude/skills/backup-to-vault/SKILL.md
git commit -m "feat(skill): /backup-to-vault developer-run age encrypt + verify/cleanup (passphrase isolation)"
```

---

## Task 6: Add the SETUP.md + ENV-SNAPSHOT.md generation section

**Files:**
- Modify: `.claude/skills/backup-to-vault/SKILL.md` (append)

- [ ] **Step 1: Append the generated-docs section**

Append to `SKILL.md`:

````markdown
## Step 6 — Generate SETUP.md (restore/bootstrap guide)

Write `$VAULT/SETUP.md` with the fresh-machine restore steps. Capture the live clone URL rather than
hardcoding it:

```bash
CLONE_URL="$(git remote get-url origin)"
cat > "$VAULT/SETUP.md" <<EOF
# Steps of Babylon — fresh-machine setup & restore

## 1. Clone the tracked code
\`\`\`sh
git clone $CLONE_URL
cd steps-of-babylon
\`\`\`

## 2. Restore the gitignored secrets
The release keystore, signing passwords, AdMob IDs, and run-gradle.sh are NOT in git — they live in
\`secrets.enc\` in this vault. Restore them from the repo root:
\`\`\`sh
age -d -o secrets.tar /path/to/this-vault/secrets.enc   # prompts for the passphrase you set
tar xf secrets.tar                                       # extracts to repo-relative paths
rm secrets.tar
\`\`\`
See \`secrets.manifest.md\` for the exact file list. The passphrase is NOT stored here — retrieve it
from your password manager.

## 3. Toolchain
- JDK 17 (JVM target 17).
- Android SDK; set \`ANDROID_HOME\` (e.g. \`~/Library/Android/sdk\`). \`local.properties\` (restored
  above) sets \`sdk.dir\`.
- CLI tooling used by this repo's workflow: \`ast-grep\` (sg), \`fd\`, \`detekt\`, \`ktlint\`, \`delta\`,
  and \`age\` (\`brew install age fd ast-grep git-delta\`).
- \`run-gradle.sh\` (restored above) is the gitignored build wrapper — see README.md.

## 4. Build
\`\`\`sh
./run-gradle.sh testDebugUnitTest      # JVM unit tests
./run-gradle.sh :app:assembleDebug
\`\`\`

See the mirrored \`docs/CLAUDE.md\` for the full operating guide.

_Generated by /backup-to-vault. Versions captured in ENV-SNAPSHOT.md._
EOF
echo "wrote $VAULT/SETUP.md"
```

## Step 7 — Generate ENV-SNAPSHOT.md (version reference manifest)

```bash
{
  echo "# Environment snapshot (at last backup)"
  echo
  echo '```'
  echo "OS:    $(sw_vers -productName) $(sw_vers -productVersion)"
  echo "Java:  $(java -version 2>&1 | head -1)"
  echo "Gradle:$(./gradlew --version 2>/dev/null | grep -i '^Gradle' | head -1)"
  echo "age:   $(age --version 2>/dev/null)"
  echo "fd:    $(fd --version 2>/dev/null)"
  echo "sg:    $(sg --version 2>/dev/null)"
  echo "delta: $(delta --version 2>/dev/null)"
  echo '```'
} > "$VAULT/ENV-SNAPSHOT.md"
echo "wrote $VAULT/ENV-SNAPSHOT.md"
```
````

- [ ] **Step 2: Dry-run the generators into a scratch dir and inspect output**

```bash
SCRATCH="$(mktemp -d -t sob-gen-dryrun)"; VAULT="$SCRATCH"; CLONE_URL="$(git remote get-url origin)"
# (paste the SETUP.md heredoc and ENV-SNAPSHOT block here with VAULT=$SCRATCH)
# Then:
echo "--- SETUP.md head ---"; head -8 "$SCRATCH/SETUP.md" 2>/dev/null
echo "--- ENV-SNAPSHOT.md ---"; cat "$SCRATCH/ENV-SNAPSHOT.md" 2>/dev/null
rm -rf "$SCRATCH"
```

Expected: `SETUP.md` shows the real clone URL `https://github.com/JonWhiteFang/steps-of-babylon.git`; `ENV-SNAPSHOT.md` shows OS + Java + (possibly empty for not-yet-installed tools like age) — empty values are acceptable, they reflect the machine state. Scratch removed.

- [ ] **Step 3: Commit**

```bash
git add .claude/skills/backup-to-vault/SKILL.md
git commit -m "feat(skill): /backup-to-vault generate SETUP.md + ENV-SNAPSHOT.md (live clone URL + versions)"
```

---

## Task 7: Add the Summary section and final skill review

**Files:**
- Modify: `.claude/skills/backup-to-vault/SKILL.md` (append)

- [ ] **Step 1: Append the Summary section**

Append to `SKILL.md`:

````markdown
## Step 8 — Print the run summary

Report to the developer:
- Number of doc files mirrored (from Step 2's `find … | wc -l`).
- Which secret files were packed vs skipped (from Step 3's PRESENT/ABSENT list).
- `secrets.enc` size, or the failure + retained-tar path if encryption failed.
- That SETUP.md + ENV-SNAPSHOT.md + secrets.manifest.md were regenerated.
- Reminder: the passphrase is required to restore and is NOT stored in the vault.

## Notes
- This skill is a personal backup utility. It does NOT update STATE.md / RUN_LOG.md / CHANGELOG.md
  and is NOT part of the PR doc-sweep convention.
- It never copies tracked source code (GitHub has it) and never copies `.git/`, `build/`,
  `.gradle/`, `.idea/`, or `*.log`.
- Mirror mode: re-running refreshes everything to match the current repo state.
````

- [ ] **Step 2: Read the whole skill end-to-end for consistency**

Run: `cat .claude/skills/backup-to-vault/SKILL.md`
Verify: variable names are consistent across sections (`$VAULT`, `$VAULT_PARENT`, `$STAGED_TAR`, `$PRESENT`), step numbering is sequential (Preflight → Step 8), no placeholder/TODO text, and the passphrase-isolation rule is stated in both the mental model and Step 4.

- [ ] **Step 3: Commit**

```bash
git add .claude/skills/backup-to-vault/SKILL.md
git commit -m "feat(skill): /backup-to-vault run summary + scope notes (final section)"
```

---

## Task 8: End-to-end live run (real vault, developer-driven passphrase)

This is the one task that writes to the real vault and runs real `age`. It is the acceptance test.

**Files:** none modified — this exercises the finished skill.

- [ ] **Step 1: Install age if needed**

Run: `command -v age || brew install age`
Expected: `age` resolves on PATH afterward.

- [ ] **Step 2: Invoke the skill and run Steps 1–3 (preflight, mirror, stage)**

Follow `SKILL.md` Steps 1–3 against the real vault. After Step 3, confirm:
- `$VAULT/docs/` is populated, `$VAULT/secrets.manifest.md` lists the 5 present secret files.
- `$STAGED_TAR` path is printed and the file exists with `0600` perms.

- [ ] **Step 3: Developer runs the age encryption (Step 4)**

The developer runs `! age -p -o "<VAULT>/secrets.enc" "<STAGED_TAR>"` and enters a passphrase.
Expected: `secrets.enc` appears in the vault. Claude never sees the passphrase.

- [ ] **Step 4: Verify + cleanup + generate docs + summary (Steps 5–8)**

Run Steps 5–8. Expected: `secrets.enc` non-empty → staged tar removed; `SETUP.md` + `ENV-SNAPSHOT.md` written; summary printed.

- [ ] **Step 5: Prove restore works (round-trip test, in a throwaway dir)**

```bash
T="$(mktemp -d -t sob-restore-test)"
age -d -o "$T/secrets.tar" "/Users/jpawhite/Documents/kn0ck3r-vault/Claude/steps-of-babylon/secrets.enc"  # developer enters passphrase
tar -tf "$T/secrets.tar"     # should list the same repo-relative secret paths
rm -rf "$T"
```
Expected: decryption succeeds with the passphrase and `tar -tf` lists the original secret files — proving the backup is genuinely recoverable.

- [ ] **Step 6: No commit needed** — Task 8 changes no repo files (it only writes to the vault). The skill itself was committed in Tasks 1–7.

---

## Self-Review (completed during authoring)

**Spec coverage:** Preflight/age-check (Task 2) ✓; docs mirror with `--delete` (Task 3) ✓; secrets bundle file list + `mktemp 0600` staging + filenames-only manifest (Task 4) ✓; developer-run `age -p` passphrase isolation + verify + honest best-effort cleanup (Task 5) ✓; SETUP.md + ENV-SNAPSHOT.md with live clone URL & restore commands (Task 6) ✓; summary + scope/no-doc-sweep notes (Task 7) ✓; restore round-trip proof (Task 8) ✓. No spec section unmapped.

**Placeholder scan:** No TBD/TODO; every shell block is concrete. The only `<...>` are deliberate developer-substituted paths in the `! age` command (the passphrase-isolation requirement forbids Claude pre-filling and running it).

**Consistency:** `$VAULT`, `$VAULT_PARENT`, `$STAGED_TAR`, `$PRESENT`, `$CANDIDATES` used consistently; step numbering Preflight→Step 8 sequential; `secrets.enc` / `secrets.manifest.md` / `SETUP.md` / `ENV-SNAPSHOT.md` filenames match the spec layout throughout.
