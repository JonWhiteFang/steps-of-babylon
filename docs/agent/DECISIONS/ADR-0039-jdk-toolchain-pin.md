# ADR-0039: JDK toolchain pin (JVM-17, local-detection only)

## Context
- The build declared only **bytecode-level** targets (`compileOptions { sourceCompatibility =
  targetCompatibility = VERSION_17 }`) — there was **no Gradle JVM toolchain**, so the JDK that actually
  ran the compiler was whatever ambient JDK Gradle happened to pick (from `JAVA_HOME` / `org.gradle.java.home`
  / the default on `PATH`).
- On a first clone with a too-new ambient JDK (21/25), the build failed with **opaque toolchain/KSP
  errors** that CI — which runs on a pinned temurin JDK-17 runner — never reproduced. The bytecode target
  said "produce 17-level bytecode" but did nothing to constrain which JDK does the compiling, so the
  divergence only surfaced on a fresh developer machine.
- Raised as Finding **#378 / `devenv-1`** in the Phase-2 tooling-gap assessment: reduce first-clone
  friction by making the JDK requirement explicit and self-diagnosing.

## Decision
- Pin a **JVM-17 Gradle toolchain on all three modules** (`:app` + `:baselineprofile` + `:macrobenchmark`)
  via `kotlin { jvmToolchain(17) }`.
- AGP-9's built-in Kotlin registers the `kotlin {}` extension on **all three** modules — including the two
  `com.android.test` benchmark modules — so the **primary `kotlin { jvmToolchain(17) }` DSL worked** and
  the KGP-task-level fallback (configuring `KotlinCompile`/`JavaCompile` tasks directly) was **not needed**.
- **Local-detection only** — deliberately **NO foojay-resolver-convention** (no third-party JDK
  auto-download). If a matching local JDK is absent, Gradle fails with a clear, actionable
  toolchain-not-found error naming JDK 17.
- The bytecode-level `compileOptions { VERSION_17 }` is **RETAINED** as orthogonal: the toolchain governs
  *which JDK runs the compiler*; `compileOptions` governs the *bytecode/target level*. They answer
  different questions and both stay. (A code comment on the build files, commit 6278368, records this so a
  future cleanup does not delete one thinking it duplicates the other.)

## Alternatives considered
- **A: foojay-resolver-convention plugin for auto-download** — REJECTED. It pulls an **unpinned** JDK from
  a third party, which is at odds with the project's strict supply-chain posture
  (`dependency-verification=strict` + `verification-metadata.xml`). Local-detection instead fails with a
  clear actionable error, which is exactly the improvement the finding asked for — no silent third-party
  fetch.
- **B: top-level `java { toolchain { languageVersion = … } }` block** — REJECTED / not usable. The
  `com.android.*` modules apply **no `java` / `java-base` plugin**, so the `JavaPluginExtension` (`java {}`
  DSL) does not exist; an unguarded `java {}` block fails at configuration time. `kotlin { jvmToolchain() }`
  is the correct seam for these modules.
- **C: leave as-is (bytecode target only)** — REJECTED. That is precisely the first-clone friction #378
  targets: an ambient-JDK mismatch produces an opaque failure instead of a clear one.

## Consequences
- **Positive:** a too-new ambient JDK now yields a **clear toolchain-not-found error naming JDK 17**, not
  an opaque KSP failure. IDE and CLI resolve the same JDK, so builds are consistent across Android Studio
  and the command line. No app / test / schema change — purely a build-configuration hardening.
- **Negative / tradeoffs:** a fresh machine must have **JDK 17 installed** (there is no auto-download).
  This is documented in `README.md` under Prerequisites.
- **Follow-ups:** a **config-drift guard** — a `StepCreditAllowlistTest`-style scan of the three
  `build.gradle.kts` files asserting the `jvmToolchain(17)` block is present — is **FEASIBLE but
  DELIBERATELY DEFERRED** at `devenv-1`'s **Low** priority. Rationale: a drift ships **no broken code** and
  breaks **no invariant** — a fresh clone that lost the pin simply hits an immediate, clear
  toolchain/first-build error rather than a latent runtime defect. This mirrors ADR-0038's deferred detekt
  nested-lock rule (follow-up #396). CI runs on a temurin JDK-17 runner, so CI verifies **JDK-17
  compilation**, not the **presence** of the pin block.

## Links
- Commit(s): `7851710` (pin JVM-17 toolchain on :app + both benchmark modules), `6278368` (comment why
  jvmToolchain + compileOptions both pin 17).
- Related ADRs: ADR-0025 (benchmark modules / AGP-9 built-in Kotlin), ADR-0037 (lint tooling), ADR-0038
  (deferred detekt rule precedent).
