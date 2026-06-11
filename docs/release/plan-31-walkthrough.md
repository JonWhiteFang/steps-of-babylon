# Plan 31 Walkthrough — Steps of Babylon Play Console publication

Companion to `release-checklist.md`. The checklist tracks status; this doc tells
you exactly what to do, in what order, with which values from this codebase.

Ordered by dependency. Phases A and B can run in parallel — start them first
because they have external delays (account verification, hosting setup).

> **📝 Updated 2026-05-18 after the first end-to-end run.** Four things this doc
> didn't anticipate when first written, all surfaced during the live walk-through
> with the user. Each is fixed inline; this list links them up so a future reader
> doesn't trip on the same rakes.
>
> 1. **Android Developer Verification** is now mandatory on first package
>    registration (new Google policy, ~late 2025). The fix: register your
>    debug-keystore SHA-256 with the package via the ADV proof-of-ownership APK
>    upload. See the new "Phase E1 detour: Android Developer Verification"
>    section below + ADR-0007.
> 2. **Play Console product IDs must match `[a-z0-9._]`** — lowercase only. The
>    `BillingProduct.skuId()` method now returns `name.lowercase()` so the wire
>    format is `gem_pack_small` / `ad_removal` / `season_pass` etc., not the
>    UPPER_SNAKE_CASE enum names. Phase F's SKU table is updated to match.
> 3. **Closed testing is now MANDATORY before applying for production access**
>    — minimum 12 opted-in testers, minimum 14 days of closed testing. Phase I1
>    is no longer optional; it's a 14-day clock you must run before you can
>    even apply for production. See updated Phase I1 below.
> 4. **The "native debug symbols" warning at AAB upload is unfixable for any
>    AAB containing SQLCipher's pre-stripped `.so` files.** AGP's
>    `ndk { debugSymbolLevel = "FULL" }` is correct config but bundles zero
>    symbols because the prebuilts have no debug info. The warning is
>    informational only — not a release blocker. See Phase G note.
>
> Other minor footguns surfaced:
>
> - **`versionCode` is a forward-only counter that Play Console retains for
>   every uploaded AAB, even withdrawn drafts.** A `bundleRelease` smoke test
>   during Phase C will permanently consume `versionCode = 1`; the first real
>   upload will need `versionCode = 2` or higher. Always bump on rejection.
> - **Phase E6 (pricing & distribution) is a no-op in the modern Play Console**
>   layout. Country/region selection moved into the Phase G release flow itself.
> - **AAB upload to internal track sets `versionCode` permanently** — see
>   `CHANGELOG.md` `## [Unreleased]` for live versionCode tracking.

Project constants you will paste repeatedly:

| Field | Value |
|---|---|
| Application ID | `com.whitefang.stepsofbabylon` |
| Version | `versionName` / `versionCode` — always read the current values from `app/build.gradle.kts` (this walkthrough is not the source of truth for the live version) |
| Min SDK / Target SDK | 34 / 36 |
| Category | Games → Strategy |
| Contact email | `jonwhitefang@gmail.com` (per `play-store-listing.md`) |

---

## Phase A — Google accounts (start now, has 1–2 day delays)

### A1. Google Play Console developer account

- URL: <https://play.google.com/console/signup>
- Cost: $25 one-time
- Identity verification: government ID + payment method. **Takes 1–2 days.**
  Start this before anything else.
- Account type: **Organization** if `whitefanggames.com` is a real entity, else
  **Personal**. Org accounts need DUNS number; personal accounts don't.

### A2. AdMob account

- URL: <https://admob.google.com/>
- Use the same Google account as Play Console for cleaner linking.
- Approval: usually instant for individual accounts; can take 1–2 days for some
  geographies.
- After approval you'll have an AdMob **App ID** (format `ca-app-pub-XXXX~YYYY`)
  and you'll create three rewarded ad units in step D1.

---

## Phase B — Privacy policy hosting (parallel to A)

The Play Console listing requires a **public URL** to the privacy policy.
Privacy text is already written at `docs/release/privacy-policy.md`.

### B1. Pick a host

Cheapest path: **GitHub Pages**.
- Create a public repo (or reuse this one if you're OK with the repo being
  public).
- Enable Pages: repo Settings → Pages → Source = `main` branch, `/docs`
  folder.
- Convert `docs/release/privacy-policy.md` to a hosted page or copy it into
  `docs/index.md` so the URL is `https://<user>.github.io/<repo>/`.

Alternatives: any static host (Netlify, Vercel), or a single `privacy.html` on
your own domain. The URL just needs to resolve to readable HTML and stay up.

### B2. Verify it's reachable

Open the URL in an incognito window before pasting it into Play Console.

---

## Phase C — Release keystore (do now, no waiting)

Already documented at `docs/release/signing-guide.md`. Recap with this project's
specifics:

```bash
mkdir -p release
keytool -genkeypair -v \
  -keystore release/upload-keystore.jks \
  -keyalg RSA -keysize 2048 \
  -validity 10000 \
  -alias upload
```

Pick a strong password. Store it in your password manager **immediately** —
losing it after enrolling in Play App Signing is recoverable via Play Console
key reset, but losing it before enrolling is not.

Create `keystore.properties` in the project root:

```properties
storeFile=release/upload-keystore.jks
storePassword=<your-password>
keyAlias=upload
keyPassword=<your-password>
```

`keystore.properties` and `*.jks` are already gitignored. Verify with:

```bash
git status
# both files should be absent from tracked changes
```

Test that release builds work:

```bash
./run-gradle.sh bundleRelease
# Output: app/build/outputs/bundle/release/app-release.aab
```

Do NOT commit `release/upload-keystore.jks` or `keystore.properties` to git.
Back them up to your password manager / encrypted cloud storage instead.

---

## Phase D — AdMob ad units + production IDs (after A2)

### D1. Create three rewarded ad units in AdMob

In the AdMob console: **Apps → Add app → Android → Manual entry** (since the
app isn't published yet). Use Application ID `com.whitefang.stepsofbabylon`.
Note the AdMob **App ID** for step D2.

Then create three **rewarded** ad units (one per `AdPlacement` enum value):

| AdMob ad unit name | Maps to enum |
|---|---|
| `Steps of Babylon — Post-round gem reward` | `POST_ROUND_GEM` |
| `Steps of Babylon — Post-round double power-stones` | `POST_ROUND_DOUBLE_PS` |
| `Steps of Babylon — Daily free card pack` | `DAILY_FREE_CARD_PACK` |

Each ad unit gives you an **Ad unit ID** (format `ca-app-pub-XXXX/ZZZZ`). Save
all three plus the App ID — you'll wire them into the build in D2.

### D2. Wire production IDs into the release build

Currently `app/build.gradle.kts` hardcodes Google's documented test ad unit IDs
in both debug and release builds. Replace the **release** block's defaults with
real IDs sourced from gitignored `local.properties` (mirrors the keystore
pattern).

Add to `local.properties` (gitignored):

```properties
admob.appId=ca-app-pub-XXXXXXXXXXXXXXXX~YYYYYYYYYY
admob.adUnit.postRoundGem=ca-app-pub-XXXXXXXXXXXXXXXX/ZZZZZZZZZZ
admob.adUnit.postRoundDoublePs=ca-app-pub-XXXXXXXXXXXXXXXX/ZZZZZZZZZZ
admob.adUnit.dailyFreeCardPack=ca-app-pub-XXXXXXXXXXXXXXXX/ZZZZZZZZZZ
```

In `app/build.gradle.kts` `release { }` block, override the BuildConfig fields
to read from these properties (Gradle's `localProperties.getProperty(...)` —
the keystore-properties loader pattern at the top of the file is the template).
Debug build keeps the test IDs.

Ask Kiro to do this wiring as a small PR after you have the real IDs — it's
~15 lines of build script and worth a code review.

---

## Phase E — Play Console listing (after A1, B, D2 keystore exists)

### E1. Create the app

Play Console → **Create app**.
- App name: `Steps of Babylon`
- Default language: English (United States)
- App or game: **Game**
- Free or paid: **Free**
- Declarations: confirm developer program policies + US export laws apply.

#### E1 detour: Android Developer Verification (mandatory since late 2025)

When you go to register the package name `com.whitefang.stepsofbabylon`, Play
Console now demands ADV proof-of-ownership before it will accept the package.
The flow shows a list of "eligible" SHA-256 fingerprints already known to
Google's package registry; in practice the fingerprint offered will be your
local **debug keystore** (`~/.android/debug.keystore`), not the production
upload keystore you generated in Phase C — because every prior debug install on
a Google-account-signed-in device pre-registers the (package, debug-fingerprint)
pair with Google.

**Path of least resistance: register with the debug keystore.** Don't fight it.

1. Confirm the offered fingerprint matches your debug keystore:
   ```bash
   keytool -list -v -keystore ~/.android/debug.keystore \
     -alias androiddebugkey -storepass android -keypass android | grep SHA256
   ```
2. Play Console hands you a snippet like `CHE2JNVSSL3U4AAAAAAAAAAAAA`. Create:
   ```
   app/src/main/assets/adi-registration.properties
   ```
   with the snippet on a single line (no quotes, no key name).
3. Build a **debug** APK so it's signed with the debug keystore Play Console is
   expecting:
   ```bash
   ./run-gradle.sh assembleDebug
   ```
4. Verify the APK signature SHA-256 matches the registered fingerprint and that
   `assets/adi-registration.properties` is at the expected path:
   ```bash
   apksigner verify --print-certs app/build/outputs/apk/debug/app-debug.apk
   unzip -l app/build/outputs/apk/debug/app-debug.apk | grep adi-registration
   ```
5. Upload the debug APK (~70 MB) to Play Console's ADV verification page.
6. After Play Console accepts the upload, **delete `adi-registration.properties`**
   — it's a one-time-use snippet and is gitignored anyway.

ADR-0007 documents the rationale + the future migration path to add the
release keystore as an additional ADV key. Until then, both keystores are
functionally fine for Plan 31; the debug keystore handles ADV, the release
keystore signs uploads.

### E2. Store listing

**Main store listing** in the left nav. Paste copy from
`docs/release/play-store-listing.md`:

| Field | Source |
|---|---|
| Short description | "Short Description" section, line 5 (57 chars) |
| Full description | "Full Description" section block |
| App icon | Upload `docs/release/store-assets/play-store-icon-512.png` |
| Feature graphic | Upload `docs/release/store-assets/play-store-feature-graphic-1024x500.png` |
| Phone screenshots | **Pending — see Phase G** (need device capture) |
| Tablet screenshots | Optional; skip unless you have them |
| Category | Games → Strategy |
| Tags | "Casual", "Strategy", "Tower defense" |
| Contact email | Update `play-store-listing.md` if `support@whitefanggames.com` is a placeholder |
| Privacy policy URL | From Phase B |

### E3. Content rating

**Policy → App content → Content ratings → Start questionnaire**. Use answers
from `play-store-listing.md` "Content Rating Questionnaire Notes":
- Violence: Abstract/fantasy only.
- User interaction: None.
- PII: None collected.
- Gambling: None (Card Packs use earned in-game Gems, not real money).
- IAP: Yes — cosmetic + convenience only. Steps cannot be purchased.
- Ads: Optional reward ads.
- Controlled substances / crude humor: None.

Expected outcome: **Everyone (E) / PEGI 3**.

### E4. Data safety

**Policy → App content → Data safety**. Match the privacy policy:
- **Data collection: None shared off device** (the app stores all game state
  locally in SQLCipher; nothing is transmitted to your servers because there
  are no servers).
- **Health data**: collected via Health Connect with explicit user permission;
  used only for in-app gameplay validation; not shared.
- **Purchase history**: handled by Google Play Billing (declare it as
  "collected by third party").
- **Ads**: declare AdMob as a third-party SDK. AdMob's data collection is
  governed by its own privacy policy.
- **Encryption in transit**: not applicable (no network calls from app code;
  Play Billing + AdMob handle their own).
- **Data deletion**: users can clear via app settings → Storage → Clear data.

### E5. Target audience

**Policy → App content → Target audience and content**:
- Target age: **18+** (per ADR-0006 Q5 — game targets adults).
- This avoids COPPA / Families program complications.

### E6. Pricing & distribution

**Monetization → Products** comes later (Phase F). For now under
**Distribution**:
- Free.
- Countries: pick all (or your subset).
- Tablets / Wear OS / Auto / TV: leave Phone-only for v1.

---

## Phase F — In-app products (after E1)

Five SKUs. Product IDs are the **lowercase** value of `BillingProduct.skuId()`,
because Play Console rejects uppercase / `_` mixed-case combos with the message
*"Product ID must start with a number or lowercase letter. Can contain numbers,
lowercase letters, underscores, and periods."* See `BillingProduct.skuId()` for
the canonical mapping.

**Monetize with Play → Products → In-app products → Create product**. Then for the
subscription, **Monetize with Play → Products → Subscriptions → Create subscription**.

| Product ID | Type | Price | Name (shown to user) | Description |
|---|---|---|---|---|
| `gem_pack_small` | Managed (consumable) | $0.99 | Small Gem Pack | 50 Gems |
| `gem_pack_medium` | Managed (consumable) | $4.99 | Medium Gem Pack | 300 Gems |
| `gem_pack_large` | Managed (consumable) | $9.99 | Large Gem Pack | 700 Gems |
| `ad_removal` | Managed (non-consumable) | $3.99 | Remove Ads | One-time purchase. Removes all reward ads from the game. |
| `season_pass` | Subscription | $4.99 / month | Season Pass | +10 Gems per day, exclusive cosmetics, 30-day billing period. |

For the consumables: leave **Auto-fill in Play Console pricing** OFF unless you
want regional pricing variants. Activate each product after creation.

For `season_pass`: pick **Monthly** billing period (30 days). Grace period:
3 days. Account hold: 30 days. No free trial for v1. **Base plan ID:** `monthly`.

**Purchase option IDs.** Modern Play Console requires a per-product/per-base-plan
`Purchase option ID` field with format `[a-z0-9-]` (note: hyphens, not
underscores). The values never appear in our app code (`BillingManagerImpl`
queries by productId only; the SDK auto-resolves the offer/option). Recommended
values mirror the product IDs but with hyphens:

| Product | Purchase option ID |
|---|---|
| `gem_pack_small` | `gem-pack-small` |
| `gem_pack_medium` | `gem-pack-medium` |
| `gem_pack_large` | `gem-pack-large` |
| `ad_removal` | `ad-removal` |
| `season_pass` (base plan) | `monthly` |

**Beware in-app/Play-Console price drift.** v1 reads live prices from
`ProductDetails.priceDisplay` via `BillingManager.getPriceDisplay` (Plan 31 PR B)
so the Store screen shows whatever Play Console is currently charging. The
static `BillingProduct.priceDisplay` constants in code are a build-time fallback
for offline / pre-query. If you change a price in Play Console, the in-app
display will pick it up automatically on next Store entry; no code change needed.

---

## Phase G — Internal test track + verification (after C, D2, E, F)

### G1. Build signed AAB

```bash
./run-gradle.sh bundleRelease
```

Output at `app/build/outputs/bundle/release/app-release.aab`. Verify it:

```bash
ls -la app/build/outputs/bundle/release/app-release.aab
# should be ~25–30 MB
```

### G2. Upload to internal testing

Play Console → **Test and release → Internal testing → Create new release → Upload AAB**.

First upload also enrolls the app in **Play App Signing** (recommended) — Google
manages the signing key, your `upload-keystore.jks` becomes only the upload key.
Accept the Play App Signing terms.

Release notes for v1.0.0: keep it short, e.g.
> Initial release of Steps of Babylon. Walk to power your ziggurat in
> wave-based tower defense battles.

**Expect the "native debug symbols" warning.** Play Console will flag every AAB
upload with *"This App Bundle contains native code, and you've not uploaded
debug symbols."* This is **unfixable** for v1 because the native code in our
AAB is from SQLCipher (`libsqlcipher.so`, ~6 MB per ABI) and `androidx.graphics.path`,
both of which ship as pre-stripped prebuilt `.so` files — there's no debug info
for AGP to extract. The `ndk { debugSymbolLevel = "FULL" }` setting in
`app/build.gradle.kts` is correct config (will pick up symbols if/when SQLCipher
ever starts shipping `.dbg` files), but bundles zero symbols today. **The
warning is informational only and does not block rollout.** Click through it.

**Expect a versionCode rejection on first upload.** If you ran a `bundleRelease`
smoke test during Phase C, the AAB you produced consumed `versionCode = 1`
permanently in Play Console's history — even though it was never rolled out.
Fix by bumping `versionCode` in `app/build.gradle.kts` and re-running
`./run-gradle.sh bundleRelease`. The counter is forward-only; never decrease.

### G3. Add license testers

**Setup → License testing**. Add the Gmail addresses of devices that will
exercise the test track. License testers can install via the Play Store
internal sharing link, see test purchases, and don't get charged for SKUs.

### G4. Internal-track install + smoke test

Each tester clicks the **internal testing opt-in URL** in the Play Console
release page, accepts, then installs from the Play Store as normal. Install
takes 5–30 minutes after the AAB review completes.

Smoke checklist on a tester device:
- Launcher icon: Babylonian-ziggurat icon visible (not Android default).
- Onboarding: ACTIVITY_RECOGNITION + POST_NOTIFICATIONS prompts work.
- Step counting: walk 10–20 steps, watch the Home screen step balance go up.
- Battle: start a round, kill enemies, end round, post-round overlay appears.
- Store: tap a Gem pack → real Play Billing dialog appears with test card.
  Confirm purchase → wallet credits exactly the right amount.
  - Repeat for AD_REMOVAL and SEASON_PASS.
- Reward ad: tap "Watch ad for Gems" post-round → real AdMob test ad plays
  (or returns NO_FILL → user-visible message; the C.6 PR 2 verification PASS
  already covers this path).

### G5. Unblocks C.5 PR 3

Once a real Play Billing test purchase credits the wallet end-to-end, the
device-track verification gate for C.5 PR 2 closes. C.5 PR 3 (delete
`StubBillingManager`, collapse `BillingModule` to `@Binds BillingManagerImpl`)
becomes a single-file deletion PR, mechanically identical to today's C.6 PR 3.
Tell Kiro to land it.

### G6. Capture screenshots

While running on the tester device, take screenshots of (per the listing copy
suggestions):
- Home screen with step balance visible
- Workshop tab
- Battle in progress (multiple biomes if possible)
- Labs / Cards / Stats / Store

Minimum 2 phone screenshots, 8 recommended. Upload to Play Console store
listing → Phone screenshots (replaces the gap from Phase E2).

---

## Phase H — Pre-launch report

Play Console → **Testing → Pre-launch report**. Already auto-runs on every
internal track AAB upload via Firebase Test Lab — no extra wiring needed.

Review the report after each upload:
- **Stability**: any crashes / ANRs flagged? Fix and re-upload.
- **Security**: any insecure defaults? (We've already hardened — should pass.)
- **Performance**: any startup or rendering regressions?
- **Accessibility**: TalkBack issues? (Plan 24 deferred most of this; minor
  warnings are acceptable for v1.)

If anything critical surfaces, fix → bump `versionCode` in
`app/build.gradle.kts` → `bundleRelease` → re-upload.

---

## Phase I — Production rollout

After internal track is stable:

### I1. Promote to closed testing (mandatory — ≥12 testers, ≥14 days)

**This is no longer optional.** As of late 2025 Google requires a closed-testing
period before granting production access on a brand-new app:

- **Minimum 12 opted-in testers** (your tester list must show 12+ accounts that
  accepted the closed-track opt-in URL, not just 12 license-tester emails added).
- **Minimum 14 calendar days** of closed testing.
- The **Apply for production access** button stays grey until both are met.

Flow:

1. Promote internal v3 → closed: **Test and release → Closed testing → Create
   new release → Promote release → Internal testing**. Pick the v3 internal
   release as the source.
2. Open closed-track release → **Testers** tab → add an email list (or paste
   Gmail addresses one per line). Recruit ≥12 real testers.
3. Distribute the **closed-track opt-in URL** (separate from the internal one)
   to your testers. Each tester must click → Become a tester → install. Track
   opt-ins via Play Console.
4. Wait 14 days while collecting feedback / Pre-launch report findings / crash
   reports. Address anything critical — bump `versionCode` → `bundleRelease` →
   re-upload to closed track.
5. After day 14 with ≥12 opted-in testers, the Production access form unlocks.

Recruitment ideas: personal channels, walking-app communities, X/Reddit (small
game dev / fitness app subs), TestFlight-style beta swap groups. The 12 don't
have to be highly-engaged — just opted-in installs.

### I2. Promote to production

Play Console → **Production → Create new release → Promote from internal**.
Fill in:
- Countries: select your launch markets.
- Rollout percentage: start at **5–10%** for safety, increase to 100% over a
  few days as you watch crash-free rate / vitals.

Release review by Google: 1–7 days for a first release.

### I3. Tag the release in git

After Play Console accepts the release:

```bash
git tag -a v1.0.0 -m "v1.0.0 — initial Play Store release"
git push origin v1.0.0
```

Update `STATE.md` and `RUN_LOG.md` with the production launch.

---

## Quick gate map

```
A1 (Play Console acct) ─┐
A2 (AdMob acct) ────────┤
B (privacy hosting) ────┤
C (keystore) ───────────┤────► E (listing) ──► F (SKUs) ─┐
D (AdMob IDs) ──────────┘                                 ├──► G (internal track)
                                                          │      │
                                                          │      ├──► G5 unblocks C.5 PR 3
                                                          │      └──► G6 screenshots → back to E2
                                                          │
                                                          └──► H (pre-launch) ──► I (production)
```

The slowest serial path: A1 verification (1–2 days) → C/D/E/F (a few hours) →
G upload + review (a day or two) → H pre-launch (~6 hours after upload) →
I production review (1–7 days). **Plan ~2 weeks from now to live, longer if
content rating or AdMob approval hits delays.**

---

## What Kiro can do without external accounts

While you're working through A and B, Kiro can land:

- **Ad-error UX gap fix** in `CardsViewModel.watchFreePackAd`,
  `BattleViewModel.watchGemAd`, `BattleViewModel.watchPsAd` (3 call sites,
  mirrors `MissionsViewModel.userMessage` pattern).
- **`local.properties` AdMob ID wiring** for D2 — the build script change
  ready for the moment you have real IDs.
- **B.4 / B.5 debt cleanup** if you want.

Just ask.
