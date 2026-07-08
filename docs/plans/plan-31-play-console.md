# Plan 31 — Play Console & Store Publication

**Status:** In Progress (Phases A–G landed: developer account verified, AdMob ad units created + wired, privacy policy hosted, upload keystore + Play App Signing enrolled, package registered via Android Developer Verification, store listing populated, content rating + data safety + target audience submitted, real Play Billing v8 + AdMob v25 + UMP v4 wired end-to-end and verified on-device 2026-05-18. The internal track is now live and CI-driven via `release.yml`; latest build **v1.0.13 / vc 29** — see `docs/agent/STATE.md` + `CHANGELOG.md` for the current build. Promotion to the closed track is judgment-gated on the Closed-Test Readiness Gate (`docs/plans/plan-FORWARD.md`); the ≥14-day soak / ≥12-tester clock + production access are a Phase 2 concern that begins **after** promotion.)
**Dependencies:** Plan 30 (Release Prep), Plan R (Tier 1), Plan R2 (Tier 1), Plan R3 (Tier 1), Plan R4 (Tier 1)
**Layer:** Store publication

---

## Objective

Upload the signed AAB to Google Play Console, configure the store listing, set up test tracks, and complete all Play Console requirements for publication.

---

## Task Breakdown

### Task 1: Play Console Setup

Configure Google Play Console:
- Create app listing
- Set up internal testing track
- Configure pricing: Free (with IAP)
- Set target countries/regions
- Complete content rating questionnaire
- Complete data safety section
- Link privacy policy URL (hosted from Plan 30)

---

### Task 2: Store Listing Upload

Upload all assets created in Plan 30:
- App icon (512×512 PNG)
- Feature graphic (1024×500 PNG)
- Screenshots (phone + tablet)
- Short description and full description
- Category: Games → Strategy
- Contact email

---

### Task 3: AAB Upload & Test Tracks

- Upload signed AAB from Plan 30 to internal testing track
- Verify AAB with `bundletool` if not already done
- Test universal APK from AAB on device
- Set up closed/open testing tracks as needed
- Configure tester groups

---

### Task 4: IAP & Ad Verification

- Configure IAP products in Play Console (Gem packs, Ad Removal, Season Pass)
- Test IAPs via licensed test accounts on internal track
- ~~Integrate real Google Play Billing Library (replace StubBillingManager)~~ — done in **C.5 PR 1–3** (real `BillingManagerImpl` on Play Billing v8; `StubBillingManager` deleted post on-device PASS 2026-05-18)
- ~~Integrate real AdMob SDK (replace StubRewardAdManager)~~ — done in **C.6 PR 1–3** (real `RewardAdManagerImpl` on AdMob v25 + UMP v4; `StubRewardAdManager` deleted)
- Verify reward ads load and grant rewards
- Verify purchase flows complete end-to-end

---

### Task 5: Pre-Launch Report

- Enable Firebase Test Lab pre-launch report
- Review automated crawl results for crashes/ANRs
- Fix any critical issues found
- Re-upload if needed

---

### Task 6: Production Release

- Promote from internal → closed → open → production track
- Monitor crash reports and vitals
- Tag final release in version control

---

## Completion Criteria

- App listed on Google Play Console with all assets
- Internal testing track functional
- IAPs configured and testable
- Pre-launch report shows no critical issues
- Ready for production rollout
