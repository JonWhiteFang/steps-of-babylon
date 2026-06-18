export const meta = {
  name: 'complete-app-review',
  description: 'Audit-grade end-to-end review of Steps of Babylon: recon → dimension finders → severity-scaled adversarial refutation by separate subagents (3/3/2/1) → synthesis writes docs/reviews/<date>-complete-app-review.md + returns a deduped GitHub-issue plan (propose-then-confirm)',
  whenToUse: 'Full/complete app review, repo audit, production-readiness assessment. Discovery only — no fixes.',
  phases: [
    { title: 'Recon', detail: 'one agent maps the repo (Phase 1 archaeology)' },
    { title: 'Find', detail: '~17 dimension finders fan out, each citing real file:line evidence' },
    { title: 'Refute', detail: 'severity-scaled separate refuter subagents per finding: 3 crit/high, 2 medium, 1 low' },
    { title: 'Report', detail: 'synthesis agent writes the dated 20-section report + returns exec summary + issue plan' },
  ],
}

// ── Single source of truth for the brief/persona/method/output structure ──
const BRIEF = '.claude/skills/complete-app-review/review-brief.md'

// ── Date-stamped output path (scripts can't read the clock; date comes from args) ──
// Pass via Workflow({..., args: { date: 'YYYY-MM-DD' }}). Falls back to an undated name only if absent.
const REVIEW_DATE = (args && typeof args === 'object' && /^\d{4}-\d{2}-\d{2}$/.test(args.date || '')) ? args.date : null
const REPORT_PATH = REVIEW_DATE
  ? `docs/reviews/${REVIEW_DATE}-complete-app-review.md`
  : 'docs/reviews/complete-app-review.md'

// ── Schemas (validated at the tool-call layer; agents must conform) ──
const FINDINGS_SCHEMA = {
  type: 'object',
  properties: {
    findings: {
      type: 'array',
      items: {
        type: 'object',
        properties: {
          title: { type: 'string', description: 'one-line finding' },
          severity: { type: 'string', enum: ['critical', 'high', 'medium', 'low'] },
          area: { type: 'string', description: 'report area, e.g. security, performance, ux, testing' },
          file: { type: 'string', description: 'primary file path' },
          line: { type: 'string', description: 'line number or range, or "n/a"' },
          evidence: { type: 'string', description: 'the actual code/text observed at file:line' },
          why: { type: 'string', description: 'why it matters / consequence' },
          fix: { type: 'string', description: 'recommended fix' },
          effort: { type: 'string', enum: ['small', 'medium', 'large'] },
        },
        required: ['title', 'severity', 'area', 'file', 'evidence', 'why', 'fix', 'effort'],
      },
    },
  },
  required: ['findings'],
}

const VERDICT_SCHEMA = {
  type: 'object',
  properties: {
    verdict: { type: 'string', enum: ['confirmed', 'partial', 'refuted'] },
    adjustedSeverity: { type: 'string', enum: ['critical', 'high', 'medium', 'low'] },
    citedFileLine: { type: 'string', description: 'the file:line you ACTUALLY opened to check' },
    reasoning: { type: 'string', description: 'what you checked and the refutation you attempted' },
  },
  required: ['verdict', 'adjustedSeverity', 'citedFileLine', 'reasoning'],
}

// ── The refutation law: separate refuter subagents per severity ──
function refuterCount(sev) {
  if (sev === 'critical' || sev === 'high') return 3
  if (sev === 'medium') return 2
  return 1 // low
}

// Tally N independent verdicts into a status, per the law's survive/kill rules.
function tally(verdicts, n) {
  const confirms = verdicts.filter(v => v && v.verdict === 'confirmed').length
  const refutes = verdicts.filter(v => v && v.verdict === 'refuted').length
  if (n >= 3) {
    if (confirms >= 2) return 'confirmed'
    if (refutes >= 2) return 'refuted'
    return 'partial'
  }
  if (n === 2) {
    if (confirms === 2) return 'confirmed'
    if (refutes === 2) return 'refuted'
    return 'partial'
  }
  // n === 1
  if (confirms === 1) return 'confirmed'
  if (refutes === 1) return 'refuted'
  return 'partial'
}

// Consensus severity among non-refuting verdicts (mode; ties → most severe). Falls back to original.
function consensusSeverity(verdicts, original) {
  const order = { critical: 4, high: 3, medium: 2, low: 1 }
  const votes = verdicts.filter(v => v && v.verdict !== 'refuted').map(v => v.adjustedSeverity).filter(Boolean)
  if (!votes.length) return original
  const counts = {}
  for (const s of votes) counts[s] = (counts[s] || 0) + 1
  let best = original, bestCount = -1
  for (const s of Object.keys(counts)) {
    if (counts[s] > bestCount || (counts[s] === bestCount && order[s] > order[best])) {
      best = s; bestCount = counts[s]
    }
  }
  return best
}

function refutePrompt(f) {
  return [
    `You are an ADVERSARIAL VERIFIER on an audit of Steps of Babylon. Read ${BRIEF} for context, persona, and constraints.`,
    `Your job is to REFUTE the finding below by reading the ACTUAL code — not to agree with it.`,
    `Default to refuted if you cannot positively confirm every link of the chain.`,
    ``,
    `FINDING [${f.severity}] ${f.title}`,
    `  area: ${f.area}`,
    `  location: ${f.file}:${f.line || 'n/a'}`,
    `  claimed evidence: ${f.evidence}`,
    `  claimed consequence: ${f.why}`,
    ``,
    `Open ${f.file} (and any wiring/DI/caller/test it depends on) and check the FULL causal chain:`,
    `  1. Does the cited location actually contain what's claimed? (existence)`,
    `  2. Is that code reachable in the SHIPPED/release configuration (not dead/test-only/debug-flavor/overridden)?`,
    `  3. Does the claimed consequence actually follow end-to-end?`,
    `  4. Is it already mitigated elsewhere (a guard, Keystore mix, atomic-guard DAO, try/catch, existing test)?`,
    `If ANY link fails, the finding is refuted or its severity is wrong. You MAY adjust severity (up or down)`,
    `and explain why. citedFileLine MUST be a real location you opened — not the finding's own prose.`,
    `Note: in this repo, a "hardcoded key" is often SecureRandom+Keystore-wrapped, and "racy spends" are often`,
    `the atomic guarded-deduct pattern — verify, don't assume.`,
  ].join('\n')
}

// ════════════════════════════════════════════════════════════════════════
phase('Recon')
log('Phase 1 — repository archaeology')
const recon = await agent(
  [
    `You are mapping the Steps of Babylon repository for an audit-grade review. Read ${BRIEF} first.`,
    `Execute Phase 1 (Repository archaeology) ONLY: thoroughly inspect the repo and produce a dense MAP.`,
    `Identify: tech stack, framework, language/runtime versions, entry points, main screens/components,`,
    `data flow, state management, persistence, build/test commands, dependency manager, any backend/API/DB/`,
    `external integrations, existing docs, existing tests, CI/CD config, deployment/release setup.`,
    `Cite real paths. If something expected is missing, say so explicitly. Return a thorough text map`,
    `(this is shared context for ~17 specialist finders — be concrete and path-rich, not high-level).`,
  ].join('\n'),
  { label: 'recon', phase: 'Recon' }
)

// ════════════════════════════════════════════════════════════════════════
phase('Find')
log('Phase 2–8 — dimension finders fan out')
const DIMENSIONS = [
  { key: 'product-ux', focus: 'Product purpose, core loop, navigation clarity, UX friction, copy quality, information hierarchy, empty/loading/error states, onboarding/first-run, whether purpose is communicated.' },
  { key: 'accessibility', focus: 'TalkBack/content descriptions, touch target sizes, color contrast, dynamic font scaling, color-blind modes, focus order, Activity-Minute-Parity for non-ambulatory users.' },
  { key: 'architecture', focus: 'Clean Architecture layering (presentation→domain←data), separation of concerns, module/component boundaries, domain purity, DI graph correctness, over/under-engineering.' },
  { key: 'code-quality', focus: 'Naming consistency, complexity hotspots, duplication, dead code, type safety, runtime safety, data validation, readability.' },
  { key: 'state-mgmt', focus: 'StateFlow/ViewModel patterns, single-source-of-truth, state restoration, configuration-change survival, leaked state, race-prone shared mutable state.' },
  { key: 'data-persistence', focus: 'Room schema, DAOs, migrations + migration tests, SQLCipher usage, transactions, atomic guarded-deduct correctness, data integrity, backup/restore.' },
  { key: 'security', focus: 'Secrets in repo, key management (Keystore), DB encryption, manifest exported components & permissions, IAP/ad grant trust (SSV), input validation, injection, cleartext/network config, R8.' },
  { key: 'privacy', focus: 'Data collected, on-device vs transmitted, privacy policy accuracy vs Data-Safety claims, sensitive-data logging, GDPR-style data-deletion/export, third-party SDK data flows (ads/billing/health).' },
  { key: 'performance', focus: 'Startup, render/game-loop performance, per-frame allocations/GC churn, asset/APK size, expensive ops, memory leaks, DB/query efficiency, caching, image/media handling.' },
  { key: 'reliability', focus: 'Error handling, crash visibility, concurrency/thread-safety (game loop, singletons, WAL), null/edge handling, foreground-service/WorkManager robustness, failure states.' },
  { key: 'offline-network', focus: 'Offline-first correctness, poor-network behaviour, Health Connect sync gaps, billing/ads offline degradation, retry/backoff, data reconciliation on reconnect.' },
  { key: 'testing', focus: 'Unit/integration/instrumented coverage, test relevance & reliability, critical flows without coverage, fakes quality, whether the app can be safely refactored, missing migration/concurrency tests.' },
  { key: 'build-ci-release', focus: 'Build reproducibility, version catalog discipline, CI/CD gates, signing/release lane, schema-drift gate, Pages lane, versioning, env/config, release readiness rating.' },
  { key: 'dependencies', focus: 'Dependency health, outdated/vulnerable libs, version-catalog hygiene, transitive risk, license concerns, abandoned deps, SDK version currency (billing/ads/health-connect).' },
  { key: 'docs-maintainability', focus: 'Documentation quality & accuracy vs code, steering-doc drift, tech-debt register, maintainability, scalability of the architecture, fragile zones.' },
  { key: 'i18n-l10n', focus: 'Hardcoded user-facing strings vs strings.xml, locale/number/date formatting, RTL readiness, pluralization, currency/units, translation-readiness.' },
  { key: 'feature-completeness', focus: 'Which features fully exist vs partial vs planned-but-absent (Labs/Cards/UWs/Missions/Store/etc.), Coming-Soon stubs, dead navigation, GDD vs reality gaps.' },
]

const rawFindings = (await parallel(DIMENSIONS.map(d => () =>
  agent(
    [
      `You are a specialist reviewer for the "${d.key}" dimension of an audit-grade review of Steps of Babylon.`,
      `Read ${BRIEF} for persona, method, constraints, and output expectations. DISCOVERY ONLY — do not change code.`,
      ``,
      `Shared repository map from recon:`,
      recon || '(recon unavailable — inspect the repo yourself)',
      ``,
      `Focus: ${d.focus}`,
      ``,
      `Inspect the ACTUAL files. For every issue, capture real file:line evidence (the actual code/text you saw),`,
      `assign Severity (critical/high/medium/low), area, why it matters, a recommended fix, and effort.`,
      `Be specific and constructively critical; do not flatter. If something expected is missing, file it as a finding.`,
      `Only report issues you have evidence for — a later adversarial pass will try to refute each one.`,
    ].join('\n'),
    { label: `find:${d.key}`, phase: 'Find', schema: FINDINGS_SCHEMA }
  )
))).filter(Boolean).flatMap(r => (r && r.findings) ? r.findings : [])

// Dedup across finders (barrier already passed): same file + similar title → keep highest severity.
const order = { critical: 4, high: 3, medium: 2, low: 1 }
const byKey = new Map()
for (const f of rawFindings) {
  if (!f || !f.title) continue
  const sev = (f.severity || 'low').toLowerCase()
  f.severity = sev
  const key = `${(f.file || '?').toLowerCase()}::${f.title.toLowerCase().replace(/[^a-z0-9]+/g, ' ').trim().slice(0, 48)}`
  const existing = byKey.get(key)
  if (!existing || (order[sev] || 0) > (order[existing.severity] || 0)) byKey.set(key, f)
}
const findings = Array.from(byKey.values())
log(`${rawFindings.length} raw findings → ${findings.length} after dedup; refuting each with 3/3/2/1 separate subagents`)

// ════════════════════════════════════════════════════════════════════════
phase('Refute')
log('Adversarial refutation — separate subagents per finding, severity-scaled')

// Each finding is refuted independently; pipeline lets a low-finding finish while a critical is still refuting.
const judged = await pipeline(
  findings,
  async (f, _orig, i) => {
    const n = refuterCount(f.severity)
    // First round: N separate refuter subagents (distinct agent() calls = distinct subagents).
    let verdicts = (await parallel(
      Array.from({ length: n }, (_, k) => () =>
        agent(refutePrompt(f), { label: `refute:${f.area}#${i}.${k + 1}`, phase: 'Refute', schema: VERDICT_SCHEMA })
      )
    )).filter(Boolean)

    let status = tally(verdicts, n)
    let sev = consensusSeverity(verdicts, f.severity)

    // Upgrade re-refutation: if not killed and the consensus severity rose into a higher band than the
    // refuter count we ran (e.g. low→medium, medium→high, *→critical), dispatch additional SEPARATE
    // refuters up to the new band's required count, then re-tally at that count. Keeps the 3/2/1
    // severity-scaling invariant honest no matter which direction a refuter moves the severity.
    const required = refuterCount(sev)
    if (status !== 'refuted' && required > verdicts.length) {
      const need = required - verdicts.length
      const extra = (await parallel(
        Array.from({ length: need }, (_, k) => () =>
          agent(refutePrompt({ ...f, severity: sev }), { label: `refute:${f.area}#${i}.up${k + 1}`, phase: 'Refute', schema: VERDICT_SCHEMA })
        )
      )).filter(Boolean)
      verdicts = verdicts.concat(extra)
      status = tally(verdicts, required)
      sev = consensusSeverity(verdicts, sev)
    }

    return {
      ...f,
      finalSeverity: sev,
      status,
      refuters: verdicts.length,
      votes: {
        confirmed: verdicts.filter(v => v.verdict === 'confirmed').length,
        partial: verdicts.filter(v => v.verdict === 'partial').length,
        refuted: verdicts.filter(v => v.verdict === 'refuted').length,
      },
      verdicts,
    }
  }
)

const results = judged.filter(Boolean)
const survivors = results.filter(r => r.status === 'confirmed' || r.status === 'partial')
const killed = results.filter(r => r.status === 'refuted')
log(`Refutation complete: ${survivors.length} survived (confirmed/partial), ${killed.length} refuted/dropped`)

// ════════════════════════════════════════════════════════════════════════
phase('Report')
log(`Synthesis — writing ${REPORT_PATH}`)
if (!REVIEW_DATE) log('⚠️ No valid date arg passed — falling back to undated filename. Re-run with args:{date:"YYYY-MM-DD"} for the tracked, dated artifact.')
const summary = await agent(
  [
    `You are the synthesis lead for an audit-grade review of Steps of Babylon. Read ${BRIEF} now —`,
    `you MUST follow its persona, Phases 1–10 method, the exact 20-section output structure, the per-finding`,
    `format, and ALL constraints (no code changes beyond writing the report; no deletes; no refactors).`,
    ``,
    `The findings below have ALREADY been through severity-scaled adversarial refutation by separate subagents`,
    `(3 for critical/high, 2 for medium, 1 for low). Use ONLY survivors as live findings; present refuted ones`,
    `in an explicit "Refuted / Downgraded" subsection — what was killed and why is part of the report's value.`,
    `For each surviving finding include its refutation trail (refuter count, vote tally) as the trust signal,`,
    `and a STABLE finding ID (e.g. REL-2, CONC-1) in the Technical Debt Register so issues can backlink to it.`,
    `Use finalSeverity (post-refutation), not the finder's original severity.`,
    ``,
    `SURVIVING FINDINGS (confirmed/partial) as JSON:`,
    JSON.stringify(survivors, null, 1),
    ``,
    `REFUTED / DROPPED FINDINGS as JSON:`,
    JSON.stringify(killed.map(k => ({ title: k.title, area: k.area, file: k.file, originalSeverity: k.severity, votes: k.votes, why: k.why })), null, 1),
    ``,
    `Repository map (recon):`,
    recon || '(recon unavailable)',
    ``,
    `WRITE the complete report to ${REPORT_PATH} (create docs/reviews/ if absent), using the Write tool,`,
    `with all 20 sections fully written — synthesize across the findings; do not just dump JSON.`,
    `Fill every section with evidence-grounded prose; where a section has no findings, say so honestly.`,
    `Put the review date in the header. Include the readiness rating (Phase 8) and the /10 scorecard (Phase 10).`,
    ``,
    `Then RETURN (as your final message, not in the file) the three deliverables: (1) a short summary of the`,
    `most important findings, (2) the top 10 highest-priority fixes, (3) the exact report path (${REPORT_PATH}).`,
  ].join('\n'),
  { label: 'synthesis', phase: 'Report' }
)

// ── Issue-filing PLAN (propose-then-confirm — the workflow does NOT call gh) ──
// Default scope: one issue per Medium/High/Critical survivor; ALL Lows bundled into one tracker.
// Dedup against existing issues is done by the MAIN agent after the run (it has gh); here we just
// shape the plan and flag the standing convention so the proposal is ready to vet.
function sevLabel(s) {
  if (s === 'critical') return 'severity:blocker'
  if (s === 'high') return 'severity:major'
  if (s === 'medium') return 'severity:major'
  return 'severity:minor'
}
const medPlus = survivors.filter(r => r.finalSeverity !== 'low')
const lows = survivors.filter(r => r.finalSeverity === 'low')
const issuePlan = {
  convention: 'Med+ → one issue each (dedup against existing issues first); ALL Lows → a single [Audit] tracker issue (mirror #128).',
  filingMode: 'propose-then-confirm — present this plan to the developer; only run `gh issue create` after go-ahead.',
  dedupHint: 'Before filing, run `gh issue list --state all --limit 200`; skip any finding already covered (match on finding ID in title or the described defect).',
  backlink: `Each issue body should backlink to ${REPORT_PATH} (and its section/finding-ID anchor).`,
  proposedIndividualIssues: medPlus.map(r => ({
    title: `[Audit] ${r.title}`,
    severity: r.finalSeverity,
    suggestedLabels: ['bug', sevLabel(r.finalSeverity), r.area ? `area:${r.area}` : null].filter(Boolean),
    evidence: `${r.file}${r.line ? ':' + r.line : ''}`,
    why: r.why,
    fix: r.fix,
    effort: r.effort,
    status: r.status,
    refutation: `${r.refuters} refuters — ${r.votes.confirmed}✓/${r.votes.partial}~/${r.votes.refuted}✗`,
  })),
  proposedLowTracker: lows.length ? {
    title: `[Audit] Tracker: ${lows.length} Low-severity findings (${REVIEW_DATE || 'this run'})`,
    suggestedLabels: ['bug', 'severity:minor'],
    items: lows.map(r => `${r.title} — ${r.file}${r.line ? ':' + r.line : ''}`),
  } : null,
}

return {
  reportPath: REPORT_PATH,
  reviewDate: REVIEW_DATE,
  counts: {
    rawFindings: rawFindings.length,
    deduped: findings.length,
    survived: survivors.length,
    refuted: killed.length,
    bySeverity: survivors.reduce((a, r) => { a[r.finalSeverity] = (a[r.finalSeverity] || 0) + 1; return a }, {}),
  },
  issuePlan,
  executiveSummary: summary,
}
