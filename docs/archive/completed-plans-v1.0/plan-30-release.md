# Plan 30 — Release Prep

**Status:** ✅ Complete
**Dependencies:** Plan 29 (Testing & QA)
**Layer:** Build configuration + assets

---

## Objective

Prepare the app for release: ProGuard/R8 optimization, app signing configuration, Play Store listing assets, privacy policy, final build verification, and AAB generation. Play Console upload and store configuration deferred to Plan 31.

---

## Task Breakdown

### Task 1: ProGuard / R8 Configuration

Update `app/build.gradle.kts`:
- Enable `minifyEnabled = true` and `shrinkResources = true` for release build type
- Configure `proguard-rules.pro`:
  - Keep Room entities and DAOs
  - Keep Hilt-generated classes
  - Keep Kotlin serialization models
  - Keep Health Connect API classes
  - Keep SensorEvent callback methods
- Test release build to verify no runtime crashes from over-aggressive shrinking

---

### Task 2: App Signing

Configure release signing:
- Generate release keystore (if not already created)
- Configure `signingConfigs` in `build.gradle.kts`
- Store keystore credentials securely (not in version control)
- Document keystore backup procedure
- Consider Google Play App Signing enrollment

---

### Task 3: Version Finalization

Update version info:
- Set `versionName = "1.0.0"` and `versionCode = 1` (or appropriate)
- Update `CHANGELOG.md` with v1.0.0 release notes
- Tag release in version control

---

### Task 4: Play Store Listing Assets

Create `docs/release/` directory with:
- App icon: 512×512 PNG (hi-res)
- Feature graphic: 1024×500 PNG
- Screenshots: minimum 2, recommended 8 (phone + tablet)
  - Home screen, Workshop, Battle (multiple biomes), Labs, Cards, Stats
- Short description (80 chars): "Walk to power your ziggurat. Every step builds the tower."
- Full description (4000 chars): game overview, features, accessibility
- Category: Games → Strategy
- Content rating questionnaire answers
- Contact email

---

### Task 5: Privacy Policy

Create `docs/release/privacy-policy.md`:
- Data collected: step count (device sensor), Health Connect data (with consent), purchase history
- Data stored: locally on device only (Room database)
- No server-side data collection in v1.0
- Health Connect: permissions `READ_STEPS` and `READ_EXERCISE`, data used only for step validation
- AdMob: standard ad SDK data collection disclosure (when integrated)
- Google Play Billing: standard purchase data (when integrated)
- No personal data shared with third parties beyond ad/billing SDKs
- Host privacy policy at a public URL (GitHub Pages or similar)

---

### Task 6: Final Build Verification

Pre-release checklist:
- [ ] Release APK installs and runs on API 34 device/emulator
- [ ] Release APK installs and runs on API 36 device/emulator
- [ ] Step counting works in background (release build)
- [ ] Health Connect permissions and step reading works
- [ ] No ANRs or crashes in 30-minute play session
- [ ] ProGuard didn't break any functionality
- [ ] All notification channels work
- [ ] Widget renders correctly
- [ ] Battery usage acceptable (< 5% per day for step counting)

---

### Task 7: AAB Generation

Generate release Android App Bundle:
- `./gradlew bundleRelease`
- Verify AAB size and contents
- Test universal APK from AAB on emulator

---

## File Summary

```
app/
├── build.gradle.kts            (update — signing, minify, version)
├── proguard-rules.pro          (update — keep rules)
└── release/                    (keystore — NOT committed)

docs/release/
├── privacy-policy.md           (new)
├── play-store-description.md   (new)
├── release-checklist.md        (new)
└── screenshots/                (new — Play Store screenshots)

CHANGELOG.md                    (update — v1.0.0 release notes)
```

## Completion Criteria

- Release build compiles, installs, and runs without crashes
- R8 shrinking doesn't break any functionality
- App signed with release keystore
- All Play Store listing text assets created
- Privacy policy written
- AAB generated and verified
- Final version tagged in version control
