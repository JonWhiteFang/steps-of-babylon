# AGENTS.md

This repo's agent guidance is **not** duplicated here. This file is a redirect so a non-Claude agent
can find the authority.

- **Start here:** [`docs/agent/START_HERE.md`](docs/agent/START_HERE.md) — the agent contract
  (constraints + build commands + where the memory spine lives).
- **Full operating guide:** [`CLAUDE.md`](CLAUDE.md) — protocol, architecture, conventions, domain model.
- **Live state:** [`docs/agent/STATE.md`](docs/agent/STATE.md) — current objective, priorities, fragile zones.

## Before you touch code

1. Read the memory spine (`docs/agent/START_HERE.md` → `STATE.md` → `CONSTRAINTS.md`) — never rely on chat history.
2. Every spec and plan passes the **Adversarial Review Gate** (see `CLAUDE.md`) before the next stage.
3. Build/test via the wrappers, not bare Gradle: `./run-gradle.sh <task>` (JVM tests:
   `./run-gradle.sh testDebugUnitTest`) and `./lint-kotlin.sh` (formatting).
4. The three hardest invariants (details live at the pointers — **do not restate them here**):
   Steps are never generated in-game bar one bounded exception → ADR-0003; the battle engine's acyclic
   lock order → ADR-0038; currency moves via the atomic guarded-deduct pattern → ADR-0020.

**Do not duplicate `CLAUDE.md` into this file — it only redirects.**
