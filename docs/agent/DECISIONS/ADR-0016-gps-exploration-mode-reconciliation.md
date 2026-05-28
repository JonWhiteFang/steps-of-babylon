# ADR-0016: GPS / Exploration Mode Reconciliation

**Status:** Accepted
**Date:** 2026-05-28
**Supersedes:** None
**Superseded by:** None

## Context

The original Game Design Document `docs/StepsOfBabylon_GDD.md` §2.3 proposed an "Exploration Mode" feature using GPS-based distance tracking. The supply-drop trigger table contained a row "Every 1.5km GPS distance → 1–2 Power Stones" and a footer note saying GPS distance tracking was an optional opt-in feature for battery/privacy reasons.

The shipping v1.0 codebase contains zero GPS / location code:

- `grep -r "LocationManager\|FusedLocationProviderClient" app/src/main/` returns no matches.
- `AndroidManifest.xml` contains no `<uses-permission>` for `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`, or `ACCESS_BACKGROUND_LOCATION`.
- `gradle/libs.versions.toml` does not depend on `play-services-location`.
- The Play Console data-safety submission for v1.0 declares "no location data collected".

This is a verified-accurate finding from GitHub issue #40 (2026-05-25 triage). The GDD and the shipping product were out of sync.

## Decision

**Drop "Exploration Mode" from the GDD for v1.0 and v1.x. Reserve as a v2.x meta-progression concept.**

Rationale:

1. **Battery cost.** GPS is one of the most battery-hungry permissions on Android. Even passive (`requestLocationUpdates(highPriority = false)`) location access measurably drains battery on long walks — the exact use case the game is designed for.

2. **Privacy trade-off.** Location is a sensitive permission category. Adding it requires a Play Console data-safety re-submission, a privacy policy revision, and on first install the user sees an extra runtime-permission dialog ("Allow Steps of Babylon to access this device's location?"). The aesthetic cost is real for a game that explicitly markets "no tracking" privacy.

3. **Play Console review delay.** Adding `ACCESS_BACKGROUND_LOCATION` triggers an extended app review (3–5 days additional) even on incremental track promotions. Plan 31 Phase E was already submitted; re-submitting would block the closed-track soak window.

4. **Gameplay payoff is unproven.** The original 1.5km GPS reward (1-2 Power Stones every ~1500m, ~30 min of walking at average pace) is not significantly more compelling than the existing step-based supply-drop triggers (every 2,000 steps + step-burst + 10k milestone + 1% random per 500 steps). The GPS row added a 5th trigger to a system already producing 2-4 drops per 10k steps — diminishing returns on game economy without breaking it.

5. **v2.x reservation matches V1X-25 long-term meta-progression.** A future Exploration Mode could plausibly ship alongside prestige / ascension / discovered-locations as a v2.x feature where the integration cost amortises across multiple new mechanics.

## Consequences

### Positive

- Privacy policy stays accurate (no location data collected).
- Battery profile unchanged.
- No Play Console re-review needed.
- One row removed from the supply-drop trigger table; GDD now reads consistent with shipping product.

### Negative

- Players who read the original GDD draft and expected an Exploration Mode will not find it. Mitigated by removing the feature entirely from §2.3 (rather than leaving it as "Coming Soon", which sets a v1.x expectation we don't intend to meet).

### Neutral

- The supply-drop economy tuning (drop rates, reward distribution) was previously specced as 4 trigger types + the GPS row = 5 sources. Removing GPS leaves 4 sources, which is what the existing balance tests (`SupplyDropEconomyTest`) already exercise. No balance work needed.

## Implementation

V1X-19 sub-plan implements this ADR:

- `docs/StepsOfBabylon_GDD.md` §2.3 — GPS row removed from trigger table; clarifying paragraph added explaining the v2.x reservation and the ADR reference.
- `docs/architecture.md` — new "Privacy & Permissions" section explicitly states no location services and lists the requirements for any future agent attempting to reintroduce GPS.

## Future Reconsideration

If Exploration Mode is reintroduced (most likely as part of v2.x prestige work), this ADR should be marked Superseded by a new ADR documenting:

- The new gameplay design (visit-counts? heatmap? geofenced events?)
- The Play Console data-safety re-submission plan
- The privacy policy update
- The battery-budget audit results
- The opt-in flow design (GPS must be opt-in, never required for core progression — Steps stay the only required currency source)

## References

- GitHub issue #40 — original triage finding
- `docs/StepsOfBabylon_GDD.md` §2.3 — updated supply-drop section
- `docs/architecture.md` — Privacy & Permissions section
- `docs/release/privacy-policy.md` — canonical privacy declaration ("no location data collected")
- ADR-0017 (queued) — ENEMY_INTEL design (V1X-15b, separate concern)
