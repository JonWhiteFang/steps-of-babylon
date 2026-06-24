# Design: `/backup-to-vault` skill

**Date:** 2026-06-24
**Status:** Approved (brainstorming) → ready for implementation plan
**Author:** Claude + Jon White

## Purpose

A user-invocable Claude Code skill that mirrors this repository's **un-committed essentials**
into the developer's Obsidian vault, for two reasons:

1. **Disaster recovery** — a cloud-backed copy of secrets (release signing keystore, signing
   passwords, AdMob production IDs, gitignored build helper) that are NOT recoverable from
   GitHub. Losing the upload keystore means losing the ability to ship updates to the existing
   Play listing, so a recoverable backup is genuinely valuable.
2. **Fresh-machine contribution** — everything `git clone` won't give you: the gitignored
   helpers, a setup/restore guide, and an environment snapshot, so the project can be rebuilt
   on another machine.

The tracked source code already lives on GitHub (`https://github.com/JonWhiteFang/steps-of-babylon.git`);
this skill deliberately does **not** duplicate it. It captures only the gitignored essentials plus
a documentation snapshot for offline reading inside Obsidian.

**Vault target:** `/Users/jpawhite/Documents/kn0ck3r-vault/Claude/steps-of-babylon`

## Security context (drives the whole design)

The vault is cloud-synced. Therefore raw release-signing keys and plaintext signing passwords
**must not** be written to the vault unencrypted — that would place the keys that sign the Play
release in third-party cloud storage in the clear.

**Resolution:** all secret files are packed into a single tar and encrypted with `age` (passphrase
mode) before anything is written to the vault. The vault holds only the ciphertext plus a manifest
listing the *filenames* in the bundle (never their contents).

**Passphrase isolation:** Claude must never see or type the passphrase, because the transcript may
itself sync. The encrypt/decrypt step is therefore run **by the developer** via the `! ` prompt
prefix; `age -p` prompts interactively on the TTY. Claude only stages the tar, verifies the
resulting `secrets.enc` exists and is non-empty, then shreds the staged plaintext tar.

## Form

A checklist skill (`.claude/skills/backup-to-vault/SKILL.md`) that Claude follows step-by-step —
the same shape as the existing `/checkpoint` and `/release` skills. Not a standalone shell script,
so it stays readable/editable and Claude can report what changed and adapt.

It is **not** part of the PR doc-sweep convention — it is a personal backup utility and must not
touch `docs/agent/STATE.md` / `RUN_LOG.md`.

## Prerequisites

- `age` must be installed (`command -v age`). If missing, the skill stops and instructs the
  developer to `brew install age`. (`openssl` is present as a documented fallback but `age` is the
  chosen tool.)
- `rsync` (present at `/usr/bin/rsync`).

## Vault layout (mirror mode)

```
kn0ck3r-vault/Claude/steps-of-babylon/
├── SETUP.md               # generated: clone URL, JDK 17, Android SDK + ANDROID_HOME, CLI tooling
│                          #   (ast-grep/fd/detekt/ktlint/delta), where each secret file goes on
│                          #   restore, and the exact `age -d` + `tar xf` decrypt commands.
├── ENV-SNAPSHOT.md        # generated: java -version, gradle version, installed CLI tool versions,
│                          #   OS version — a reference manifest captured at backup time.
├── secrets.enc            # age-encrypted tar — the ONLY place raw secrets live in the vault.
├── secrets.manifest.md    # plaintext list of WHICH files are in the bundle (filenames only).
└── docs/                  # rsync mirror of the repo documentation snapshot:
    ├── CLAUDE.md
    ├── README.md
    └── <full docs/ tree>  # everything under docs/ (agent, steering, plans, design docs, archives,
                           #   external-reviews, balance, performance, etc.)
```

## What goes in the encrypted bundle

Exactly the gitignored, not-recoverable-from-GitHub files:

| File | Why it matters |
|---|---|
| `local.properties` | SDK path + AdMob production IDs (loaded by `app/build.gradle.kts` release block) |
| `keystore.properties` | release signing store/key passwords (plaintext) |
| `release/upload-keystore.jks` | the Play **upload signing key** — irreplaceable |
| `release/upload-cert.pem` | upload certificate |
| `run-gradle.sh` | gitignored; the entire CLAUDE.md build workflow depends on it |
| `app/src/main/assets/adi-registration.properties` | device-verification snippet — included **only if present** |

`secrets.manifest.md` lists these filenames (and notes which were present at backup time) so a
restore knows what to expect — but never echoes any file contents.

## What is documentation-mirrored (not encrypted)

The full `docs/` tree plus the two root guides `CLAUDE.md` and `README.md`. This includes historical
artifacts (archives, external-reviews) by explicit choice — the developer wants a complete offline
documentation snapshot in Obsidian. These are non-sensitive (already in git).

## Re-run behavior: mirror + fresh bundle

The skill is a backup utility run repeatedly over time. On each run:

1. `docs/` is refreshed with `rsync --delete` so the vault is a true mirror — files removed from the
   repo are removed from the vault copy. Stale docs never accumulate.
2. `secrets.enc`, `secrets.manifest.md`, `SETUP.md`, and `ENV-SNAPSHOT.md` are regenerated fresh.

No timestamped snapshots, no additive-only accumulation, no scheduling/cron — invoked manually.

## Step sequence (what SKILL.md instructs)

1. **Preflight:** confirm `age` and `rsync` exist; confirm the vault parent directory
   (`/Users/jpawhite/Documents/kn0ck3r-vault/Claude`) exists. Refuse to run otherwise (guards
   against path typos creating junk directories). Create the target dir if the parent is valid.
2. **Mirror docs:** `rsync -a --delete` the documentation set into `<vault>/docs/`, excluding
   `.git/`, `build/`, `.gradle/`, `.idea/`, `*.log`.
3. **Stage secrets tar:** tar the bundle file list (skipping any not present) to a temp path
   outside the vault, e.g. `/tmp/sob-secrets-<pid>.tar`. Write `secrets.manifest.md` listing the
   included filenames.
4. **Developer-run encryption:** instruct the developer to run, via `! `:
   `age -p -o "<vault>/secrets.enc" /tmp/sob-secrets-<pid>.tar`
   (prompts for passphrase; Claude never sees it).
5. **Verify + shred:** confirm `<vault>/secrets.enc` exists and is non-empty; then securely remove
   the staged temp tar (`rm -P` / `rm`). If `secrets.enc` is missing/empty, do NOT delete the tar —
   report the failure.
6. **Generate SETUP.md + ENV-SNAPSHOT.md** with current clone URL, toolchain versions, restore
   steps, and the decrypt commands.
7. **Summary:** print what was copied, what was deleted by the mirror, bundle size, and a reminder
   of where the passphrase is needed on restore.

## Restore path (documented in SETUP.md)

```sh
git clone https://github.com/JonWhiteFang/steps-of-babylon.git
cd steps-of-babylon
age -d -o secrets.tar /path/to/vault/secrets.enc   # prompts for passphrase
tar xf secrets.tar                                  # restores files to repo-relative paths
# then: install JDK 17, Android SDK, set ANDROID_HOME, install CLI tooling per SETUP.md
```

The tar stores repo-relative paths so extraction from the repo root drops each secret back where
the build expects it.

## Safety rails

- Refuses to run if the vault parent directory doesn't exist.
- Never copies `.git/`, `build/`, `.gradle/`, `.idea/`, `*.log`.
- Passphrase never enters the transcript (developer-run `age -p`).
- On encryption failure, the staged plaintext tar is preserved (not silently lost) and the failure
  is reported.
- Not wired into the PR doc-sweep; does not modify STATE.md / RUN_LOG.md.

## Out of scope (YAGNI)

- Timestamped/versioned snapshots.
- Automatic scheduling (cron / launchd).
- Backing up tracked source code (GitHub already has it).
- Syncing the vault itself (Obsidian/iCloud handles that).
```