# Kiro-CLI → Claude Code Conversion + Committed Memory Spine

**Date:** 2026-06-10
**Status:** Approved (design); hardened after a 6-lens adversarial review (53 raw findings → 28
confirmed → 5 must-fix + 7 should-fix applied); pending final user sign-off.
**Scope:** Tooling/config + docs only. Zero production code, Gradle, or schema changes.

---

## 1. Goal

Convert *Steps of Babylon* from a **Kiro-CLI**-driven agent setup to a **Claude Code**-native
one, and in doing so complete the project's **committed project-memory spine** — durable,
version-controlled memory that lives in the repo and is driven by Claude Code's native
automation (a SessionStart hook + a `/checkpoint` skill) instead of Kiro's `inclusion: always`
steering mechanism.

The key realisation that scopes this work: **the project already implements ~80% of the
memory-spine pattern** under `docs/agent/`. The conversion is therefore *not* a from-scratch
build — it is (a) replacing Kiro's always-on machinery with Claude Code's equivalents, and
(b) re-homing the content Kiro's steering folder held.

---

## 2. Current state (what exists before this work)

### 2.1 The spine already present under `docs/agent/`

| Memory-spine concept | This project's file | Notes |
|---|---|---|
| `PROJECT_STATE.md` (live one-page truth) | `docs/agent/STATE.md` | Rich; current-objective log + priorities + next actions + do-not-touch zones |
| `RUN_LOG.md` (append-only history) | `docs/agent/RUN_LOG.md` | Newest-on-top, per-session entries |
| `DECISIONS/*.md` (ADRs) | `docs/agent/DECISIONS/` | 16 ADR files (see note below) |
| Cardinal rules / invariants | `docs/agent/CONSTRAINTS.md` + `docs/agent/START_HERE.md` | Architecture/anti-cheat/"never do" lists + agent contract |
| The project guide | `AGENTS.md` (45 KB, tool-neutral) | Canonical detailed guide today |

`docs/agent/DECISIONS/` holds **16 ADR files** (`0001` template, `0002`–`0010`, `0012`,
`0014`–`0018`; numbers `0011` and `0013` were reserved but never written). ADR *content* is out
of scope (§9); the count is recorded only for inventory accuracy.

### 2.2 The Kiro machinery being replaced

`.kiro/steering/` (Kiro auto-loads files according to their `inclusion:` front-matter). The
inclusion modes here are a **mix** — verified by reading each file's front-matter:

- **Behavioral — `inclusion: always` (the genuinely always-on mechanism):**
  - `10-project-memory.md` — memory rules ("treat the spine as truth; read before, write after").
  - `11-agent-protocol.md` — Context Preflight + PR doc-sync convention + End-of-Run memory writes.
- **Reference — NOT always-on:**
  - `inclusion: fileMatch` (loaded only when matching files are in context, via
    `fileMatchPattern`): `lib-coroutines.md`, `lib-jetpack-compose.md`, `lib-room.md`. These
    carry Kiro-specific `inclusion:` / `fileMatchPattern:` YAML front-matter.
  - **No front-matter at all** (Kiro does not auto-load these every session): `tech.md`,
    `structure.md`, `source-files.md` (88 KB), `product.md`, `lib-hilt.md`.

`.kiro/settings/`:

- `lsp.json` — editor language-server config (multi-language).
- `kotlin-ls-wrapper.sh` — Kotlin LS JAVA_HOME wrapper.

### 2.3 What is missing (the conversion gap)

- No `CLAUDE.md` — Claude Code therefore loads no project guide or always-on rules.
- No `.claude/` directory — no SessionStart hook (read-at-start), no `/checkpoint` skill
  (write-at-end), no settings.
- The end-of-run protocol exists only as Kiro steering prose, not as an executable skill.

---

## 3. Decisions locked (from brainstorming)

1. **Clean conversion** — delete `.kiro/` *entirely* (including `.kiro/settings/` LSP config).
   Migrate behavioral rules into `CLAUDE.md`; move reference docs to `docs/steering/`.
2. **`CLAUDE.md` is the guide** — fold `AGENTS.md`'s content into `CLAUDE.md` as the single
   canonical source of truth, then delete `AGENTS.md`.

---

## 4. Target design

### 4.1 New files

#### `CLAUDE.md` (repo root) — the single canonical guide

Auto-loaded by Claude Code every session. Contents, in order:

1. **Always-on memory rules** (lifted from steering `10-project-memory.md`): the spine is
   truth; read the spine + git state before planning; write STATE + RUN_LOG after; ADR for
   meaningful decisions; keep STATE one page.
2. **Agent protocol** (lifted from steering `11-agent-protocol.md`): Context Preflight steps;
   the **PR Task-List Convention** (sync current-state docs → update STATE/RUN_LOG, in that
   order, before commit); End-of-Run memory writes; the "current-state docs to audit" list and
   the "historical artifacts — never modify" list.
3. **The full AGENTS.md body** — project overview, tech stack, architecture map, plans/roadmap
   index, build/test commands, conventions, gotchas. **Merged, not duplicated:** AGENTS.md
   already contains its own `## Project Memory (read first)` table and three operating-rule
   bullets that restate items 1–2. Those are folded into the prepended rules (item 1–2) and the
   spine pointer (item 4) — *not* copied a second time. Every other AGENTS.md `## ` section is
   carried over with its cross-refs updated per §4.4 (notably: AGENTS.md's `see
   .kiro/steering/11-agent-protocol.md` resolves to CLAUDE.md's own protocol section — see §4.4
   carve-out — and its `.kiro/steering/source-files.md` ref becomes `docs/steering/source-files.md`).
4. **Spine pointer** — a "Project Memory (read first)" table pointing at `docs/agent/*` and
   `docs/steering/*`.

Authoring constraint: the always-on rules at the top must be concise (they are *always* in
context); detail lives below and in the referenced docs. The 88 KB `source-files.md` is
**pointed at, not inlined** — and since it has no Kiro front-matter, Kiro never auto-loaded it
every session anyway; the win is that CLAUDE.md does not inline 88 KB into the always-on guide.

**Fold completeness is gated, not assumed** (§7): before deleting AGENTS.md, enumerate its
`## ` section headers and assert each is represented in CLAUDE.md, plus a content-size sanity
check. AGENTS.md is the source and is deleted (§4.3), so a silently dropped section is
unrecoverable without this gate.

#### `.claude/hooks/session-preflight.sh` — SessionStart hook (read-at-start)

A bash script that **prints plain text to stdout** (which Claude Code adds to context for a
`SessionStart` hook — no JSON wrapper is required for a context-only hook, and avoiding it
removes the arbitrary-content escaping that is the top silent-no-op risk). It prints:

- branch + `git status --short`
- `git log -n 10 --oneline`
- A **bounded, section-anchored slice of `docs/agent/STATE.md`** (see below)

**STATE.md injection — bounded by characters and selected by section, NOT by line count.**
`head -60 docs/agent/STATE.md` is **39,140 chars** (verified) — ~4× the documented **10,000-char
`additionalContext` cap** — because the `## Current objective` section is a stack of giant
single-line history bullets. A line count is the wrong unit and would silently overflow the cap
(content dropped → read-at-start defeated) *and* waste the budget on dated `Previous objective`
history while never reaching the live sections. Instead the hook extracts, by header:

- the **newest 1–2 `## Current objective` bullets only** (not the whole section),
- the full **`## Top priorities (next 5)`**, **`## Next actions (explicit order)`**, and
  **`## Do-not-touch / fragile zones`** sections,

then **hard-caps the total at ~6–8 KB** (well under 10 KB) and appends a
`— see docs/agent/STATE.md for full state` pointer. This deliberately *excludes* the dated
history bullets, which also avoids re-injecting the now-dead `.kiro/steering/*` path tokens that
live inside that history (§4.4 freezes them but the old head-60 window would have surfaced them).

Constraints: `chmod +x`; **no `jq`/`python3` dependency** (plain `cat`/`echo`/`awk`/`sed` only,
all guaranteed present); degrade gracefully outside a git repo / on detached HEAD / empty repo
(skip the git sections, still emit STATE); always `exit 0`.

**Hook-schema verification.** The plain-stdout-for-SessionStart behavior and the 10,000-char
cap are confirmed against current Claude Code hook docs (verified by the `claude-code-guide`
review lens). The `settings.json` registration shape (§4.1) is re-confirmed against docs during
implementation before finalising.

#### `.claude/skills/checkpoint/SKILL.md` — the `/checkpoint` skill (write-at-end)

Front-matter (`name: checkpoint`, `description: …`) + body encoding the **existing** end-of-run
protocol as an explicit checklist:

1. Doc-drift sweep over the current-state docs list.
2. Sync affected current-state docs (`CLAUDE.md` test-count/architecture, `CHANGELOG.md`,
   `docs/steering/source-files.md`, `docs/steering/structure.md`, `docs/database-schema.md`,
   `docs/steering/tech.md` + `lib-*.md`, `README.md`) — touch only those the change invalidates.
3. Update `docs/agent/STATE.md` (what changed + what's next).
4. Append a `docs/agent/RUN_LOG.md` entry (never edit prior entries).
5. Add/update an ADR if a non-trivial decision was made.

It explicitly lists the **historical artifacts never to modify** (prior RUN_LOG entries,
`docs/plans/plan-R*.md`, `docs/external-reviews/*`, `devdocs/*`, `smoke_tests/*`, existing ADR
bodies unless warranted).

**Invocation control:** front-matter sets `disable-model-invocation: false` (the default) so
`/checkpoint` is both user-invocable and model-invocable. This is a *deliberate* choice, not an
oversight: the source Kiro protocol made End-of-Run writes agent-autonomous, and every write the
skill performs is committed, reversible Markdown. Recorded so §7.3 can verify the chosen value.

#### `.claude/settings.json` — registers the hook

Registers `session-preflight.sh` on the `SessionStart` event, **additively** alongside the two
pre-existing plugin `SessionStart` hooks (superpowers + episodic-memory) already active in this
environment — the project hook does not override them; Claude Code runs all registered
`SessionStart` hooks. **Matcher:** use `*` (or omit it) so preflight context is re-injected on
**startup, resume, clear, and compact** — matching the "read the spine every session" intent
(a `startup`-only matcher would silently skip re-grounding after resume/clear/compaction). The
registration shape (`hooks.SessionStart[].hooks[] = {type: "command", command: …}`) is
re-confirmed against Claude Code docs during implementation. Valid JSON.

### 4.2 Moved files (`.kiro/steering/` reference docs → `docs/steering/`)

`tech.md`, `structure.md`, `source-files.md`, `product.md`, `lib-coroutines.md`, `lib-hilt.md`,
`lib-jetpack-compose.md`, `lib-room.md` → `docs/steering/` (same filenames). Behavioral docs
`10-project-memory.md` and `11-agent-protocol.md` are **not** moved — their content is absorbed
into `CLAUDE.md` (§4.1).

**Strip dead Kiro front-matter on move.** Three of the moved docs (`lib-coroutines.md`,
`lib-jetpack-compose.md`, `lib-room.md`) carry `inclusion: fileMatch` / `fileMatchPattern:` YAML
front-matter that is meaningless under Claude Code — it becomes inert dead syntax. Remove that
front-matter block as part of the move so no Kiro-specific config survives the clean conversion
(the other 5 docs have no front-matter). This is why §7.6 cannot be a raw byte-compare (see §7).

**Use `git mv`** for the moves to keep the change legible (rename continuity is a read-time
heuristic in git and survives delete+recreate, so this is a readability nicety, not a
correctness requirement).

### 4.3 Deleted

- `.kiro/` (whole tree: `steering/` after the moves + absorptions, and `settings/` LSP config).
- `AGENTS.md` (content folded into `CLAUDE.md`).

### 4.4 Cross-reference updates (live docs only)

Across the tree, in **live** docs only, three rewrite rules:

- `.kiro/steering/<x>.md` → `docs/steering/<x>.md` — **with a carve-out** (below).
- `AGENTS.md` → `CLAUDE.md`
- "Kiro CLI" / "Kiro" as a non-TTY example → "non-TTY environments (CI, etc.)"

**Carve-out — the two behavioral docs are absorbed, not moved.** References to
`10-project-memory.md` and `11-agent-protocol.md` must **not** be rewritten to a
`docs/steering/` path — those files do not exist there (§4.2 absorbs them into CLAUDE.md). In
live docs such a reference resolves to **CLAUDE.md's own memory-rules / protocol section** (the
content is now in-document). Concretely: AGENTS.md line 20 (`see
.kiro/steering/11-agent-protocol.md`), when folded into CLAUDE.md, points at CLAUDE.md's protocol
section — *not* `docs/steering/11-agent-protocol.md` (which the blanket rule would wrongly
manufacture, recreating the exact dangling link this conversion removes).

**Known live files needing edits — verified by reading each (the §6 audit is still the
authoritative final list, but this is now accurate, not guesswork):**

| File | Edit needed | Verified |
|---|---|---|
| `README.md` | line 61 (**two** sites: inline `see AGENTS.md` + the `[AGENTS.md](AGENTS.md)` link), 114, 127 (`AGENTS.md` → `CLAUDE.md`) + 63, 127 (`Kiro CLI` → non-TTY; `.kiro/steering/tech.md` → `docs/steering/tech.md`) | ✅ 5 edit sites |
| `docs/steering/tech.md` (moved) | line 88 (`Kiro CLI` non-TTY reword) | ✅ 1 site |
| `docs/agent/CONSTRAINTS.md` | line 38 (`Kiro CLI` non-TTY reword) — **no** `.kiro/`/`AGENTS.md` refs | ✅ 1 site |
| `CLAUDE.md` (new) | its own folded `.kiro/`/`AGENTS.md` refs (from AGENTS.md body) resolve per the rules above | n/a (authored) |

**Verified to need NO edit** (do not hunt for refs here): `docs/agent/START_HERE.md` (zero
`.kiro/`/`AGENTS.md`/`Kiro` tokens — already in target phrasing) and **7 of the 8 reference
docs** (`structure`, `source-files`, `product`, `lib-coroutines`, `lib-hilt`,
`lib-jetpack-compose`, `lib-room` — all zero relevant tokens; only `tech.md` has one). The
earlier draft's claim that the moved docs contain "self-refs + protocol references" was wrong
and is corrected here.

**Historical artifacts are left byte-for-byte unchanged** (the project's own protocol forbids
rewriting them): `docs/agent/RUN_LOG.md` prior entries, `docs/agent/STATE.md` historical
current-objective bullets that name Kiro / `.kiro/steering/tech.md` (these are dated history —
a **line-level** exception: STATE.md is a live doc but those specific dated bullets are frozen),
`CHANGELOG.md` past sections, `docs/plans/*`, `docs/external-reviews/*`, `devdocs/*`,
`smoke_tests/*`, and the ADR bodies. Mentions of "Kiro" inside these are accurate historical
record and stay.

**`.claude/` is committed.** The hook, skill, and `settings.json` are git-tracked — committed,
portable memory is the entire point. `.claude/` is verified **not** ignored by `.gitignore`, so
no `.gitignore` change is needed. Any machine-local `settings.local.json`, if ever created, is
git-ignored by Claude Code convention and stays local.

### 4.5 Explicitly NOT changed

- The spine files are **not renamed** to the guide's generic names (`STATE.md` stays `STATE.md`,
  etc.) — heavy cross-referencing + append-only history make renaming pure risk for no benefit.
- No Kotlin source, Gradle/`libs.versions.toml`, Room schema, GDD, plan files, or ADR *content*.
- `run-gradle.sh` stays (still useful in CI/non-TTY); its rationale text is reworded away from
  "Kiro CLI" only where it appears in *live* docs.

---

## 5. Data / control flow after conversion

```
START of session                                   END of session
┌───────────────────────────┐                     ┌──────────────────────────┐
│ .claude/hooks/             │                     │ /checkpoint skill        │
│   session-preflight.sh     │ ── reads ──┐        │ doc-drift sweep +        │
│ (SessionStart hook)        │            │        │ sync current-state docs +│
│ injects git state +        │            ▼        │ update STATE.md +        │
│ STATE.md live sections     │   ┌──────────────────────────────────────┐    │
│ (section-anchored, ≤8 KB)  │   │ Committed memory spine (Markdown):    │    │
└───────────────────────────┘   │  docs/agent/STATE.md  (live truth)    │    │
                                 │  docs/agent/RUN_LOG.md (append-only)  │◀───┤ appends RUN_LOG +
CLAUDE.md (auto-loaded):         │  docs/agent/DECISIONS/*.md (the why)  │    │ maybe writes ADR
  always-on rules + protocol     │  docs/agent/CONSTRAINTS.md (invariants)│   │
  + full guide + spine pointer   └──────────────────────────────────────┘    │
                                                                  └──────────┘
```

Authority lives in `CLAUDE.md`; *progress and decisions* live in the spine; the spine points back
at `CLAUDE.md` and never restates the rules.

---

## 6. Implementation method

All work happens on a **dedicated feature branch** (e.g. `feat/kiro-to-claude-code`), so the
single PR diff is the rollback unit and `main` is never in a half-converted state. (`.kiro/` and
`AGENTS.md` are fully git-tracked, so any misstep is recoverable via `git restore` regardless.)

Authoring is done directly (the pieces are interdependent and must stay coherent). A **workflow**
is used for exactly two bounded fan-out tasks:

1. **Reference audit** — fan-out grep/read across all tracked files to produce the *exact,
   complete* list of live `.kiro/`, `AGENTS.md`, and "Kiro CLI" references, each classified
   live-doc (edit) vs historical-artifact (leave). This de-risks §4.4.
2. **Adversarial verification** — a final independent pass that re-greps for dangling live refs,
   validates the hook stdout (size + content), validates `settings.json` + skill front-matter,
   runs the **fold-completeness gate** (§7.7), and confirms no historical artifact was mutated.

### 6.1 Safe order of operations

A naive top-down execution can create a transient broken intermediate (e.g. CLAUDE.md pointing
at `docs/steering/*` before the move, or `.kiro/` deleted before its content is absorbed). The
safe sequence:

1. Branch.
2. **Capture** AGENTS.md's `## ` section headers + size (for the §7.7 gate) and snapshot the 8
   reference docs' bodies (for the §7.6 body-compare).
3. **`git mv`** the 8 reference docs to `docs/steering/`; strip dead Kiro front-matter from the 3
   `fileMatch` docs (§4.2).
4. **Author `.claude/`** (hook + skill + settings.json) and **smoke-test the hook** in isolation
   (size + content) before relying on it.
5. **Author `CLAUDE.md`** (fold + merge per §4.1), then run the fold-completeness gate.
6. **Rewrite live cross-refs** (§4.4) — targets now all exist.
7. **Delete** `.kiro/` and `AGENTS.md` (sources now fully absorbed/moved).
8. **Verify** (§7), including the adversarial pass.
9. **Apply the project's own protocol to this change** (§6.2).

### 6.2 Dogfood the protocol on this very change

The protocol being migrated mandates that every config/docs change logs itself. This conversion
is a major config+docs change *and* an ADR-worthy decision, so it must eat its own dog food —
this is also the first real end-to-end test that the new machinery executes, not just parses:

- Write **ADR-0019** (Kiro-CLI → Claude Code conversion: context, decision, alternatives,
  consequences) using the existing ADR template.
- Update `docs/agent/STATE.md` (current objective + next) and append a `docs/agent/RUN_LOG.md`
  entry; add a `CHANGELOG.md` section.
- **Run the new `/checkpoint` skill** on this change as the acceptance test that it actually
  performs the doc-drift sweep + spine writes end-to-end.

---

## 7. Verification criteria (done = all true)

1. `bash .claude/hooks/session-preflight.sh` exits 0 and prints **plain text** (no JSON wrapper)
   whose content includes the current branch, recent commits, and the named STATE.md live
   sections (`Top priorities`, `Next actions`, `Do-not-touch / fragile zones`). Its output is
   **< 10,000 chars** (`bash .claude/hooks/session-preflight.sh | wc -c` < 10000), satisfying the
   `additionalContext` cap, and contains **no** dated `Previous objective` history bullet.
2. The SessionStart plain-stdout behavior + 10,000-char cap and the `settings.json` registration
   shape match current Claude Code docs (cited in ADR-0019 / implementation notes).
3. `.claude/settings.json` is valid JSON registering the hook on `SessionStart` with matcher `*`
   (additive to existing hooks); `SKILL.md` front-matter is valid YAML with `name`,
   `description`, and the deliberate `disable-model-invocation: false`.
4. **Live `.kiro/` refs = 0**, via a concrete pathspec that excludes the §4.4 historical set:
   `git grep -n '\.kiro/' -- ':(exclude)docs/agent/RUN_LOG.md' ':(exclude)docs/agent/STATE.md'
   ':(exclude)CHANGELOG.md' ':(exclude)docs/plans/*' ':(exclude)docs/external-reviews/*'
   ':(exclude)devdocs/*' ':(exclude)smoke_tests/*' ':(exclude)docs/agent/DECISIONS/*'
   ':(exclude)docs/superpowers/*'` → empty. Note the exclude is `docs/superpowers/*` (the **whole**
   superpowers tree, not just `specs/`) — the plan file under `docs/superpowers/plans/` also
   contains `.kiro/`/`AGENTS.md` tokens by design and must be excluded too. STATE.md is excluded at
   file level because its only remaining `.kiro/` tokens are the frozen dated bullets (a line-level
   exception confirmed by reading, per §4.4), not live refs.
5. **Live `AGENTS.md` refs = 0**, same exclude set: `git grep -n 'AGENTS\.md' -- <same excludes>`
   → empty.
6. `.kiro/` and `AGENTS.md` no longer exist; the 8 reference docs exist under `docs/steering/`.
   Content preserved **modulo intended changes** — a **body-compare** (not raw byte-compare):
   each moved doc equals its pre-move snapshot except for (a) the stripped Kiro front-matter on
   the 3 `fileMatch` docs and (b) `tech.md`'s line-88 Kiro-CLI reword. No other content drift.
7. **Fold-completeness gate:** every `## ` section header present in the pre-fold AGENTS.md
   snapshot is represented in `CLAUDE.md`, and `CLAUDE.md` additionally contains the always-on
   memory rules + agent protocol + spine pointer (the merged, non-duplicated forms per §4.1).
8. No file under the "historical artifacts" list (§4.4) differs except by intended additions
   (`git diff` review of those paths).
9. No `.kt`, `.kts`, `.toml`, or schema file is modified (`git diff --name-only` review).
10. **Deliverables are tracked:** `git ls-files .claude/hooks/session-preflight.sh
    .claude/skills/checkpoint/SKILL.md .claude/settings.json` lists all three.
11. **Protocol applied to self:** `ADR-0019` exists; `STATE.md` updated; a new `RUN_LOG.md` entry
    appended; `CHANGELOG.md` section added; `/checkpoint` run on this change (§6.2).

---

## 8. Risks & mitigations

| Risk | Mitigation |
|---|---|
| **STATE.md injection overflows the 10,000-char `additionalContext` cap → read-at-start silently fails** | Section-anchored, ≤8 KB injection (newest objective bullet + 3 named live sections), NOT line-count (§4.1); §7.1 asserts `wc -c` < 10000. |
| Wrong `settings.json` registration shape → hook never fires | Re-confirm shape against Claude Code docs (§4.1); §7.2/§7.3 validate; §6.1 step 4 smoke-tests the hook before relying on it. |
| Too-narrow matcher silently skips re-grounding on resume/clear/compact | Matcher `*` (§4.1); §7.3 asserts it. |
| Botched JSON escaping of arbitrary STATE content → malformed-output no-op | Eliminated by design: hook prints plain stdout, no JSON wrapper, no `jq`/`python3` dependency (§4.1). |
| Missing a live `.kiro/`/`AGENTS.md` reference → dangling link | The §6 audit enumerates every reference; the §7.4/7.5 concrete-pathspec greps prove zero live remain; §4.4 carve-out prevents manufacturing a new dangling `docs/steering/11-…` link. |
| AGENTS.md section silently dropped during the manual fold (source is deleted) | Pre-fold header snapshot + §7.7 fold-completeness gate; deletion (§4.3 / §6.1 step 7) happens only after the gate passes. |
| Accidentally editing append-only history | Explicit historical-artifacts list (§4.4); §7.8 diff check; the `/checkpoint` skill itself encodes the rule. |
| Broken intermediate state mid-migration | Safe ordered sequence (§6.1: move → author → fold-gate → rewrite refs → delete → verify); all work on a feature branch (§6). |
| Losing the 88 KB source-files index or LSP value | source-files.md is moved (not deleted) to docs/steering/; LSP deletion is an explicit, accepted clean-conversion choice (flagged to user). |
| Dead Kiro `fileMatch` front-matter survives into `docs/steering/` | Stripped on move from the 3 `fileMatch` docs (§4.2); §7.6 body-compare allows exactly this change. |
| Folding 45 KB AGENTS.md bloats always-in-context CLAUDE.md | Net always-on budget actually *drops* vs the Kiro baseline (source-files/structure/tech were referenced, not all force-loaded; CLAUDE.md keeps source-files pointed-at, not inlined; always-on rules kept terse at top). |

---

## 9. Out of scope

Game code, build logic, tests, schema, ADR content, the GDD, plan documents, and the live
Play-Store release process. This change is invisible to the built app — it only changes how an
agent reads and writes project memory.
