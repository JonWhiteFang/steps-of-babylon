# Play Console — Data Safety form (Steps of Babylon)

**Status:** authored 2026-06-18 for #192 (PRIV-1/SEC-1). This is the **manual Play Console action** that
must accompany the in-app + hosted privacy-policy rewrite. The repo cannot inspect or set the live form —
a human must apply this in **Play Console → App content → Data safety** and confirm it matches.

> **Why this exists:** the v1.0.8 release ships **Google AdMob v25 + UMP v4** (reward ads) and **Google
> Play Billing v8**. The AdMob SDK auto-merges `com.google.android.gms.permission.AD_ID` +
> `ACCESS_ADSERVICES_AD_ID/ATTRIBUTION/TOPICS` into the manifest (verified in the built
> `nonMinifiedRelease` merged manifest). The Data Safety form **must** declare the advertising ID as
> collected + shared (by Google, for advertising). Game/step/health data genuinely stays on-device.

## Ground truth (what the app actually does — verified in code)

| Behaviour | Evidence |
|---|---|
| Reward ads via real AdMob (release) | `app/build.gradle.kts:123` `USE_REAL_ADS=true`; dep `play-services-ads` |
| UMP consent prompt governs personalisation | `user-messaging-platform` dep; `MainActivity` prefetches consent (release-gated) |
| Advertising ID + ad-service IDs collected | merged `nonMinifiedRelease` manifest: `AD_ID`, `ACCESS_ADSERVICES_AD_ID/ATTRIBUTION/TOPICS` |
| Ads are opt-in, reward-only (3 placements) | `AdPlacement = {POST_ROUND_GEM, POST_ROUND_DOUBLE_PS, DAILY_FREE_CARD_PACK}` |
| In-app purchases via Google Play Billing v8 | `billing.ktx` dep; `data/billing/BillingManagerImpl` |
| Game/step/Health Connect data stays on-device | SQLCipher DB, no server backend; `PlayerRepository`/Room |
| No account / no sign-in | no auth code anywhere |

## Data Safety answers to set

### "Does your app collect or share any of the required user data types?" → **Yes**

The app *itself* transmits nothing off-device (game/step/Health Connect/purchase data is on-device only —
SQLCipher Room, no server backend — so it is NOT "collected" in the Play sense). **But the Play form
requires declaring data collected by BUNDLED SDKs.** The Google Mobile Ads (AdMob) SDK auto-collects and
shares **four** data types — so the form's collected/shared set is the AdMob SDK's set, NOT just the
advertising ID.

### Data types — declare ALL of these (the AdMob/GMA SDK set)

> **Authoritative source — verify against the live page before submitting:** Google's
> "Google Mobile Ads SDK — Play Data Safety guidance"
> (https://developers.google.com/admob/android/privacy/play-data-disclosure). As of GMA SDK **25.3.x**
> it lists the four rows below, all **Collected + Shared automatically** for advertising/analytics/fraud
> prevention. If Google's table has changed by submission time, match the live page.

| Category → Type | Collected? | Shared? | Ephemeral? | Required/optional | Purpose(s) |
|---|---|---|---|---|---|
| **Device or other IDs** (Android advertising ID, app set ID) | **Yes** | **Yes** | No | **Optional** (ad ID — user can reset/limit; ads are opt-in) | Advertising or marketing; Analytics; Fraud prevention/security |
| **App activity → App interactions** (ad launches, taps, video views) | **Yes** | **Yes** | No | Required (auto by the SDK) | Advertising or marketing; Analytics; Fraud prevention/security |
| **App info and performance → Diagnostics** (launch time, hang rate, energy usage) | **Yes** | **Yes** | No | Required (auto by the SDK) | Advertising or marketing; Analytics; Fraud prevention/security |
| **Location → Approximate location** (derived from IP address) | **Yes** | **Yes** | No | Required (auto by the SDK) | Advertising or marketing; Analytics; Fraud prevention/security |

- These four are the **AdMob SDK's** transmissions — declare them because Play counts bundled-SDK
  collection, not only first-party transmission.
- **Do NOT** declare the app's OWN on-device data (Steps, Health Connect / fitness data, player progress,
  purchase history) as collected/shared — the app never transmits it off-device. (On-device-only data is
  not "collected" under Play's definition.)
- **Purchases:** Google Play Billing handles payment data on Google's side; you do not add a developer
  "Financial info" collection entry — Google's own processing is covered by Google. If the form prompts
  about purchase history transmitted *to you*, answer No (the app keeps only a local receipt).

### Security practices
- **Is data encrypted in transit?** Yes (Google SDK traffic is HTTPS).
- **Can users request data deletion?** Yes — provide the deletion path: in-app **Settings → Delete All
  Data**, system **Clear Data**, and advertising-ID reset via **Settings → Google → Ads**. Deletion URL:
  the hosted policy's `#delete-data` anchor (https://jonwhitefang.github.io/steps-of-babylon/#delete-data).

### Privacy policy URL
`https://jonwhitefang.github.io/steps-of-babylon/` — served from `site/index.md` (the single canonical
policy source), published to GitHub Pages by `.github/workflows/pages.yml`. The Pages site is built from
the top-level `site/` folder ONLY, so the internal `docs/` tree is never published publicly.

### Ads declaration (separate Console question, "App content → Ads")
- **Does your app contain ads?** **Yes** (reward ads).

## Acceptance checklist (do before promoting internal → closed)
- [ ] Cross-checked the four declared types against the **live** AdMob Play-Data-Safety page (the published
      set can change with the SDK version) — see the link above.
- [ ] Data Safety form declares all four AdMob SDK types Collected + Shared (Device/other IDs incl. ad ID;
      App interactions; Diagnostics; Approximate location), advertising/analytics/fraud purposes; ad-ID
      collection marked Optional.
- [ ] App's own on-device data (Steps, Health Connect, progress, purchases) NOT declared as collected.
- [ ] "Contains ads" set to Yes.
- [ ] Privacy-policy URL points to the hosted page and the page shows the **June 20, 2026** effective date
      with the AdMob/advertising-ID disclosure — incl. the four declared categories (Device/other IDs,
      Approximate location, App interactions, Diagnostics) (GitHub Pages may cache — confirm the live page updated).
- [ ] Data-deletion question answered Yes with the deletion URL.
- [ ] Re-read the live hosted policy + the in-app Health Connect rationale on a device against this form —
      no "future versions" / "never shared" wording remains.
