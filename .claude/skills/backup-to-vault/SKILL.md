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
