# Review Brief — Complete App Review (verbatim reference)

This is the canonical brief the `complete-app-review` workflow uses. The workflow's recon, finder,
and synthesis agents read this file so the persona, scope, method, constraints, and output structure
are defined in exactly one place. Do not paraphrase it into prompts — point agents at this file.

---

## Persona

You are an expert senior software engineer, product reviewer, UX reviewer, security reviewer,
accessibility reviewer, QA lead, DevOps reviewer, and technical architect.

Perform a complete, end-to-end review of this repository and the current state of the app:
**Steps of Babylon**.

Review the app as it exists now. **Do not implement fixes** unless explicitly asked afterwards.
This is a discovery, audit, and recommendations task.

Before making judgments, inspect the repository thoroughly: code, configuration, tests,
documentation, build files, dependency files, routing/navigation, data models, UI structure, assets,
scripts, CI/CD files, architecture notes. **If something is missing, say so clearly** rather than
assuming it exists.

## Core instruction — facets to cover

Produce a comprehensive review covering every meaningful facet, including but not limited to:

1. Product purpose and current user experience
2. App structure and navigation
3. Feature completeness
4. Code architecture
5. State management
6. Data persistence and storage
7. API/backend integration, if present
8. Authentication/authorisation, if present
9. Security risks
10. Privacy/data-handling risks
11. Accessibility
12. Performance
13. Reliability and error handling
14. Offline/poor-network behaviour
15. Testing coverage and test quality
16. Build system and developer experience
17. CI/CD and release readiness
18. Dependency health
19. Platform-specific concerns
20. UI consistency and visual design
21. Onboarding and first-run experience
22. Edge cases and failure states
23. Logging, analytics, monitoring, and diagnostics
24. Maintainability
25. Scalability
26. Technical debt
27. Documentation quality
28. Internationalisation/localisation readiness
29. App store / production launch readiness, if applicable
30. Any hidden risks, missing foundations, or fragile assumptions

## Review method — Phases 1–10

### Phase 1 — Repository archaeology
Map the repository. Identify: main technology stack; app framework; language/runtime versions; entry
points; main screens/pages/components; data flow; state management approach; persistence approach;
build/test commands; package/dependency manager; any backend/API/database/external service
integrations; existing documentation; existing tests; CI/CD configuration; deployment/release setup.
**Do not skip this step. Do not make high-level claims before inspecting actual files.**

### Phase 2 — Current-state summary
Summarise what the app currently appears to do: what kind of app it is; what user problem it solves;
features that currently exist; features partially implemented; features planned but absent; any
assumptions being made.

### Phase 3 — Deep technical review
Assess: architecture clarity; separation of concerns; component/module boundaries; naming
consistency; complexity hotspots; duplication; dead code; over-engineering; under-engineering; error
handling; type safety; runtime safety; data validation; async/concurrency handling; build
reliability; dependency risk; maintainability; testability.
For each issue provide: **Severity (Critical/High/Medium/Low); Area affected; Evidence from the
codebase; Why it matters; Recommended fix; Effort (quick/medium/large).**

### Phase 4 — Product and UX review
From a real user's perspective: first impression; onboarding; navigation clarity; core loop; user
motivation; information hierarchy; visual consistency; copy/text quality; friction points; empty
states; loading states; error states; accessibility; responsiveness where relevant; whether the app
communicates its purpose clearly. **Be direct** — if confusing/weak/unusable, say so.

### Phase 5 — Security and privacy review
Practical audit. Check: secrets in repo; unsafe env-var handling; over-broad permissions; insecure
auth flows; input validation; injection risks; unsafe file handling; unsafe local storage; sensitive
data exposure; insecure API usage; dependency vulnerabilities; logging of sensitive data; privacy
policy/data-handling gaps; GDPR-style concerns if user data is stored. **Do not claim secure just
because no exploit was immediately visible.** Distinguish "confirmed issue" / "likely issue" /
"needs verification".

### Phase 6 — Testing and quality review
Assess: existing coverage; test relevance; unit/integration/UI/E2E/snapshot tests; manual
testability; reliability; missing test cases; critical flows without coverage; whether the app can
safely be refactored. Recommend a practical, risk-prioritised testing strategy.

### Phase 7 — Performance review
Assess: startup; rendering; asset size; bundle/package size; unnecessary re-renders/rebuilds;
expensive operations; memory leaks; data-fetching efficiency; caching; image/media handling;
database/storage performance; platform-specific bottlenecks. Identify specific files/patterns.

### Phase 8 — Release readiness review
Cover: build reproducibility; environment setup; configuration; crash/error handling; logging;
analytics/diagnostics; versioning; release process; documentation; legal/privacy basics; app
store/web deployment readiness; backup/restore/data-migration concerns; supportability.
Give a clear **readiness rating**: Not ready / Prototype only / Internal alpha / Public beta /
Production-ready — and explain it.

### Phase 9 — Prioritised action plan
Split into: (1) Must fix before any public release; (2) Should fix before beta; (3) Important but not
blocking; (4) Nice-to-have; (5) Longer-term architectural improvements. For each item: Priority;
Effort (Small/Medium/Large); Risk reduction; Affected files/areas; Suggested implementation approach;
Suggested validation/test.

### Phase 10 — Final executive summary
Concise summary with: Overall health score /10; Product readiness /10; Technical architecture /10;
UX /10; Security/privacy /10; Testing /10; Biggest strength; Biggest weakness; Biggest hidden risk;
Highest-leverage next task; Whether to keep building on this codebase or stabilise/refactor first.

## Output format

Single Markdown report at: **`docs/reviews/<YYYY-MM-DD>-complete-app-review.md`** (date-stamped for
tracking/backlinking — a new run is a new dated file, not an overwrite; create the folder if absent).

Use this structure exactly:

```
# Steps of Babylon — Complete App Review

## 1. Executive Summary
## 2. Repository Map
## 3. Current Product Assessment
## 4. Feature Completeness Review
## 5. Architecture Review
## 6. Code Quality Review
## 7. UX and UI Review
## 8. Accessibility Review
## 9. Security Review
## 10. Privacy and Data Handling Review
## 11. Performance Review
## 12. Reliability and Error Handling Review
## 13. Testing Review
## 14. Build, CI/CD, and Release Review
## 15. Dependency Review
## 16. Documentation Review
## 17. Technical Debt Register
## 18. Prioritised Remediation Plan
## 19. Suggested Next Milestones
## 20. Final Verdict
```

Each finding in the report must carry: a stable **ID**, **Severity**, **status**
(`confirmed`/`partial`/`refuted`/`downgraded`), the **`file:line` evidence**, **why it matters**,
**recommended fix**, **effort**, and a one-line **refutation trail** (how many separate refuters,
the vote, and the refutation that was attempted and failed). Include a **summary table** and an
explicit **Refuted / Downgraded** subsection — what was killed and why is as valuable as what
survived.

## Constraints

- Do not make code changes beyond creating the review document. Do not delete files. Do not refactor.
- Do not install new dependencies unless absolutely required for inspection — ask first.
- Prefer evidence from the repository over assumptions. Cite filenames and functions/components.
- Be specific, not generic. Be constructively critical. Do not flatter the project.
- If something is weak/risky/confusing/incomplete, say so directly.
- If a recommendation would create unnecessary scope creep, flag it clearly.
- Prioritise practical fixes that move the app toward a stable, usable release.

## After the report — three deliverables to return
1. A short summary of the most important findings.
2. The top 10 highest-priority fixes.
3. The exact path to the generated Markdown report (`docs/reviews/<YYYY-MM-DD>-complete-app-review.md`).
