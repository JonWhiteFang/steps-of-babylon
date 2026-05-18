# Changelog

All notable changes to Steps of Babylon are documented here.

## [Unreleased]

### C.5 PR 3 — Delete `StubBillingManager`, collapse `BillingModule` to `@Binds BillingManagerImpl` (2026-05-18)

Mechanical follow-up to the Phase G internal-track on-device smoke-test PASS earlier the same day. With real Play Billing v8 verified end-to-end on a real device, the second `BillingManager` implementation has no remaining purpose.

- **`StubBillingManager` deleted** (`data/billing/StubBillingManager.kt`, 36 lines). The class simulated purchases with a 500 ms delay and credited gems / set flags directly on `PlayerRepository` — useful while real Play Billing was unwired, redundant now.
- **`BillingModule` collapsed** from a flag-gated `@Provides` Provider-switch to two plain `@Binds` abstract classes. `BillingModule` now binds `BillingManager → BillingManagerImpl`; sibling `BillingInternalModule` keeps the existing `BillingClientAdapter → RealBillingClientAdapter` binding. KDoc rewritten to capture the C.5 PR 1–3 history. Mirrors the C.6 PR 3 collapse of `AdModule`.
- **`BuildConfig.USE_REAL_BILLING` removed.** No code reads it anymore. Removed from `app/build.gradle.kts` defaultConfig + debug + release blocks; the `buildFeatures.buildConfig` opt-in comment now references `USE_REAL_ADS` (the surviving flag) only. `AdModule.kt` KDoc lost its "symmetric with USE_REAL_BILLING" line. The `app/build.gradle.kts` Play Billing dependency comment was also refreshed to note `BillingManagerImpl` is the sole binding.
- **KDoc cleanup across 5 production files.** `BillingManagerImpl.kt` lost its "PR 1 wiring status" block; `BillingManager.kt` interface lost its `@link` to `StubBillingManager`; `ActivityProvider.kt` lost its "binding still points at Stub" line; `StoreViewModel.kt` lost its mention of Stub + `USE_REAL_BILLING`; `AdModule.kt` lost the "symmetric flag" reference. All replaced with present-tense descriptions.
- **`BillingManagerParityTest` deleted** (3 tests). It existed to assert that Stub and Real produce equivalent wallet/flag effects on the golden path during the C.5 PR 2 transition. With Stub gone, the only remaining side is Real, and that's already exhaustively covered by `BillingManagerImplTest` (14 tests — 3 happy paths + 5 failure paths + idempotency + 2 reconciliation cases + delegation).
- **`docs/monetization.md` Implementation Status block fully refreshed.** The doc had been stale since pre-C.5/C.6: still described stubs, said real SDK integration was "deferred," listed Play Billing v7 as the target. Now it lists Real-SDK reality (Play Billing v8 + AdMob v25 + UMP v4 wired end-to-end), the atomic idempotency guarantees, and an honest "What's Out-of-Scope for v1" section (no server-side verification, no real-time subscription notifications, no ad mediation, no live formatted-price display from `ProductDetails.priceDisplay`).

#### Verification

- `./run-gradle.sh test` — BUILD SUCCESSFUL, **524 JVM tests** pass (down from 527 — the 3 BillingManagerParityTest tests were removed, no others changed).
- `./run-gradle.sh bundleRelease` — BUILD SUCCESSFUL. Lint vital + R8 minify + signing all clean. Signed AAB at `app/build/outputs/bundle/release/app-release.aab` (~18 MB) at versionCode 4. Not uploaded — v3 is the live internal-track AAB and there's no functional reason to bump it. v4 stays reserved for the next legitimate upload (e.g. closed-track promotion or post-closed-test bug fix).

#### Phase G internal-track smoke test PASS (2026-05-18)

Required context for this PR landing. User installed the v3 internal-track AAB on a real device via the opt-in URL; full smoke checklist passed:

- Launcher icon, walking-tracked step accumulation, battle round flow.
- All 3 Gem packs purchased on real Play Billing with the test card and credited the wallet correctly: `gem_pack_small` → +50 Gems; `gem_pack_medium` → +300 Gems; `gem_pack_large` → +700 Gems.
- `ad_removal` purchased on real Play Billing; `adRemoved` flag set; reward-ad UI hidden across the app.
- `season_pass` subscription purchased; `seasonPassActive = true` with `purchaseTime + 30-day` expiry; +10 Gems/day daily-login bonus active.
- AdMob test ad served on the post-round reward path.

This closes the device-verification gate for C.5 PR 2 (real Play Billing + receipt-table idempotency works end-to-end on a real device with the rolled-out internal-track AAB) and unblocks this PR.

### v3 rolled out to internal track + versionCode 3 → 4 (2026-05-15)

User uploaded the v3 AAB (with the new `ndk { debugSymbolLevel = "FULL" }` config landed in the previous commit) and rolled it out to the internal-testing track instead of rolling out the earlier v2 draft. v3 is functionally equivalent to v2 — the symbol-warning is structurally unfixable for any AAB containing SQLCipher's pre-stripped .so prebuilts — but v3 is the cleaner build to ship.

Local forward-only counter bump versionCode 3 → 4 in `app/build.gradle.kts`. v4 is reserved for the next upload (most likely a post-smoke-test bug fix or C.5 PR 3 deletion).

No code change. No test impact. No new AAB built locally.

### Native debug symbols + versionCode 2 → 3 (2026-05-15)

Play Console flagged the v2 internal-track AAB upload with the standard "This App Bundle contains native code, and you've not uploaded debug symbols" warning. Investigated whether AGP's `ndk { debugSymbolLevel = "FULL" }` could fix it.

- **Added `ndk { debugSymbolLevel = "FULL" }` to the release build type.** AGP runs an `extractReleaseNativeDebugMetadata` task that pulls native debug info out of any `.so` files going into the AAB and packages it into `BUNDLE-METADATA/com.android.tools.build.debugsymbols/`. Inline comment block documents intent + the SDK_TABLE-vs-FULL trade-off.
- **Findings.** AGP task ran cleanly but produced **zero bundled symbols**. The two native libraries in the AAB are SQLCipher (`libsqlcipher.so`, ~6 MB per ABI) and `androidx.graphics.path` — both ship as pre-stripped prebuilts. No debug info exists in those `.so` files for AGP to extract. Play Console warning will persist on every upload until either (a) we build SQLCipher from source ourselves, or (b) upstream SQLCipher AAR starts shipping with `.dbg` files. Both are out-of-scope for v1.
- **Why keep the config anyway.** Documents intent for any future maintainer; correct config; cost is one extra Gradle task per release build (~seconds); auto-correct if dependencies start shipping symbols later.
- **versionCode 2 → 3.** Forward-only counter bump for the next upload. Internal-track v2 from earlier today is staying as the rollout candidate — the symbol-warning is informational and not a release blocker, so we don't need to re-upload just to dismiss it.

No test impact. Signed AAB rebuilt at `app/build/outputs/bundle/release/app-release.aab`, ~18 MB, merged manifest confirms `versionCode="3"`. Not uploaded — v2 stays the internal-track AAB pending smoke test.

### versionCode bump 1 → 2 (2026-05-14)

Play Console retains every uploaded AAB's versionCode forever (even from withdrawn drafts), so an earlier `bundleRelease` smoke-test upload during the Plan 31 walk-through session permanently consumed `versionCode = 1`. Internal-track upload of the lowercase-SKU AAB rejected with "Version code 1 has already been used. Try another version code." One-line bump in `app/build.gradle.kts` (`versionCode = 1` → `versionCode = 2`); `versionName` stays `1.0.0` because nothing user-visible changed. Re-built signed AAB at `app/build/outputs/bundle/release/app-release.aab` (~18 MB), merged manifest confirms `versionCode="2"`. No test impact.

### Phase F unblocker — lowercase SKU wire format (2026-05-14)

Unblocks Play Console SKU creation. Play Console rejects product IDs that don't match `[a-z0-9._]`; our wire format previously sent `BillingProduct.name` (UPPER_SNAKE_CASE) byte-for-byte (per ADR-0005 decision #6), which Play Console refused.

- **`BillingProduct.skuId(): String`** — promoted from a private extension in `BillingManagerImpl` to a **public** method on the `BillingProduct` enum, returning `name.lowercase()`. KDoc cites Play Console's `[a-z0-9._]` rule. Any code that needs to compute the wire id should use `product.skuId()`; tests can do the same.
- **`BillingManagerImpl`** — KDoc invariant #4 updated from "uppercase enum name" to "lowercase enum name, e.g. `gem_pack_small`, `ad_removal`, `season_pass`". Private `BillingProduct.skuId()` extension deleted; the existing call sites at `purchase()` (queryProductDetails + error message) and `reconcileType()` (PENDING + PURCHASED branches) automatically pick up the new public method via name resolution. `fromSkuIdOrNull` companion extension now compares `it.skuId() == skuId` so reverse lookup matches the lowercase wire format.
- **`BillingReceiptEntity.productId` KDoc** — now refers to `BillingProduct.skuId()` and the lowercase wire format directly (refines ADR-0005 decision #6 post-Plan 31 Phase F).
- **Tests updated** — 4 test files. `BillingManagerImplTest` (5 hardcoded uppercase strings + the `stubHappyPath` helper switched from `product.name` to `product.skuId()`); `BillingManagerParityTest` (helper switched to `product.skuId()`); `BillingReceiptDaoTest` (10 hardcoded strings, all now lowercase — productId is opaque to the DAO, but staying consistent with production wire format keeps the fixtures realistic); `RoomSchemaTest` (2 strings in the billing-receipt round-trip).
- **No DB schema or migration change.** `productId TEXT NOT NULL` accepts any case; existing devices with uppercase rows from prior debug builds are not in the wild, so a one-time data migration is unnecessary. Plan 31 has not entered closed testing yet.

#### Verification

- `./run-gradle.sh test` — BUILD SUCCESSFUL, 527 tests pass (no count change, parity with last session).
- `./run-gradle.sh bundleRelease` — BUILD SUCCESSFUL. Lint vital + R8 minify + signing all clean. Signed AAB at `app/build/outputs/bundle/release/app-release.aab`, ~18 MB. Rebuild required because Plan 31 Phase G AAB upload should carry the lowercase wire format from day one (Play Console SKUs will be created lowercase, and an old uppercase AAB in the same internal-testing track would silently route product-details queries to empty results).
- One pre-existing Kotlin warning carries over (`@ApplicationContext` parameter-target annotation; unrelated to this PR; tracked to land alongside KT-73255 follow-up).

#### Next session

Plan 31 Phase G: upload the new AAB to Internal testing → create 5 lowercase SKUs (`gem_pack_small`, `gem_pack_medium`, `gem_pack_large`, `ad_removal`, `season_pass`) → license testers → on-device verification of a real Play Billing test purchase. The on-device PASS unblocks C.5 PR 3 (delete `StubBillingManager`) and the closed-testing recruitment workstream.

### Plan 31 walk-through session — Phases A-F mostly landed, ADV registered, AAB built (2026-05-13)

Multi-hour live walk-through of the `docs/release/plan-31-walkthrough.md` doc. Most of Plan 31's external-account + listing work landed, plus three small code-side build-config changes batched into a single `feat(release): Plan 31 prep` commit. Stops at the SKU-creation step where Play Console requires lowercase product IDs (resumes next session).

#### External work completed

- **Phase A1.** Google Play Console developer account created + identity-verified (jonwhitefang@gmail.com, Personal account, $25 fee paid).
- **Phase A2.** AdMob account created, linked to the same Google account.
- **Phase B.** Privacy policy hosted on GitHub Pages at <https://jonwhitefang.github.io/steps-of-bablylon/>. Verified reachable in incognito. Mid-session the Play Console data-safety form required a `delete-data` URL; added a `Data Deletion` section + `<a name="delete-data"></a>` anchor to both `docs/release/privacy-policy.md` and `docs/index.md` (separate commit on `main` mid-flow). Final URL: `https://jonwhitefang.github.io/steps-of-bablylon/#delete-data`.
- **Phase C.** Production upload keystore generated locally at `release/upload-keystore.jks` (RSA 2048, 10000-day validity, alias `upload`, CN / O / etc. set). `keystore.properties` populated at project root. SHA-256 fingerprint `C4:00:72:90:D8:40:32:92:86:06:C0:E1:E4:CB:8E:86:95:80:6A:FE:54:81:A1:15:9A:74:93:62:F2:BE:BA:E8`. Both files gitignored. Smoke-tested via `./run-gradle.sh bundleRelease` after a one-line build script fix (see code section below).
- **Phase D1.** AdMob registered the Android app + created three rewarded ad units (one per `AdPlacement` enum value: `POST_ROUND_GEM`, `POST_ROUND_DOUBLE_PS`, `DAILY_FREE_CARD_PACK`). App ID + 3 ad unit IDs landed in `local.properties` (gitignored).
- **Phase E1.** App created in Play Console ("Steps of Babylon", Game, Free, package `com.whitefang.stepsofbabylon`).
- **Phase E1 detour — Android Developer Verification (new Google policy).** Modern Play Console flow demanded ADV before letting the package name register. Initially confused because Play Console only offered an "eligible" key fingerprint `47:E8:9F:0A:3D:C1:8C:EA:B4:F5:A5:80:4D:74:B0:9E:C6:67:92:3B:C6:49:5E:C6:05:2A:26:AD:48:9D:75:5D` to select — not our newly-generated upload keystore. Forensic check revealed it was the **local Android debug keystore** at `~/.android/debug.keystore`; every prior debug install on a Google-account-signed-in device had registered the package + debug fingerprint with Google's known-package-names registry, routing us into Step 2B (existing package) instead of Step 2A (new package). Decided to register with the debug keystore (path of least resistance) rather than the release upload keystore (rationale + Google review path). See ADR-0007.
- **ADV proof-of-ownership executed.** Play Console issued snippet `CHE2JNVSSL3U4AAAAAAAAAAAAA`. Created `app/src/main/assets/adi-registration.properties` with the snippet on a single line (matches Google's sample format from `android/security-samples`). Built debug APK via `./run-gradle.sh assembleDebug`, verified `apksigner verify --print-certs` shows the SHA-256 matches `47:E8:9F:0A:...` and the APK contains `assets/adi-registration.properties` at the expected path. Uploaded the 70 MB debug APK; Play Console verified ownership and registered the package name to the developer account. Snippet file deleted post-verification (one-time use); gitignored anyway so future verifications don't accidentally commit account-specific tokens.
- **Phase E2.** Main store listing populated: app name + short description (57/80 chars) + full description (2,389 chars from `docs/release/play-store-listing.md`) + 512×512 hi-res icon + 1024×500 feature graphic + 5 phone screenshots. Phone screenshots captured on-device from emulator-5554 (1080×2400 raw → centre-cropped to 1080×1920 9:16 + flattened to 24-bit RGB to satisfy Play Store requirements): `screenshot-1-home.png`, `screenshot-2-workshop.png`, `screenshot-3-battle.png`, `screenshot-4-labs.png`, `screenshot-5-stats.png`. All in gitignored `release/screenshots/`. Battle screenshot is the hero — 5-tier ziggurat in Hanging Gardens biome with full HUD.
- **Phase E2c.** Store settings: category Games → Strategy, tags `Casual` / `Strategy` / `Tower defense`, contact email `jonwhitefang@gmail.com`.
- **Phase E2d.** Privacy policy URL pasted into Play Console.
- **Phase E3.** Content rating questionnaire completed per the matrix in `docs/release/play-store-listing.md` (mostly No, Yes only on IAP + reward ads). Ratings issued.
- **Phase E4.** Data safety form completed: collects step/health (functionality, encrypted at rest) + purchase history (third-party Play Billing); shares with Google Play Billing + AdMob; users can delete via app Settings → Storage → Clear data; delete-data URL = `https://jonwhitefang.github.io/steps-of-bablylon/#delete-data`.
- **Phase E5.** Target audience set to `18+` per ADR-0006 Q5 to keep us out of COPPA / Families program complications.
- **Phase E6.** Effectively a no-op in the modern Play Console layout — country / region selection now happens inside the release flow in Phase G, not as a standalone form. Free pricing was locked in at app creation and is not configurable post-create without a paid-app license.

#### Modern-Play-Console deviations from the walk-through doc

- **ADV (Android Developer Verification) flow.** Walk-through pre-dated this Google policy; addressed mid-session via the debug-keystore path.
- **Pricing & distribution form.** Removed in modern Play Console; integrated into the release flow.
- **Closed testing prerequisite for production.** Dashboard explicitly said "Have at least 12 testers opted-in" + "Run your closed test with at least 12 testers, for at least 14 days". This adds ~14 days to the launch timeline. Internal track is still our immediate target (verifies the AAB on a real device + exercises Play Billing); closed track recruitment becomes a separate workstream.

#### Code-side changes (committed as `feat(release): Plan 31 prep`, sha bb6b253)

- **`app/build.gradle.kts`** — fixed the long-latent keystore path bug (`file(...)` resolved relative to the `app/` module, but the signing guide and Plan 31 walk-through both consistently document `storeFile=release/upload-keystore.jks` as a project-root path). Switched to `rootProject.file(...)`. The bug surfaced the moment someone first followed the documented signing flow; build was failing with `Keystore file '/Users/jpawhite/Documents/Kiro Projects/steps-of-bablylon/app/release/upload-keystore.jks' not found for signing config 'release'`.
- **`app/build.gradle.kts`** — Wired AdMob production IDs from gitignored `local.properties` into the `release { }` block: 3 `buildConfigField` overrides for `AD_UNIT_POST_ROUND_GEM` / `_DOUBLE_PS` / `_DAILY_FREE_CARD_PACK` + 1 `manifestPlaceholders["admobAppId"]` override. Falls back to Google's documented test IDs (`ca-app-pub-3940256099942544/5224354917` for the unit; `~3347511713` for the app id) when local.properties is absent or missing keys, so a CI build or a fresh clone never mints revenue from accidental impressions. Debug build keeps the test IDs from defaultConfig untouched. Two new `val` constants `ADMOB_TEST_APP_ID` + `ADMOB_TEST_REWARDED_AD_UNIT` declared at the top of the file alongside the existing keystore-properties loader for grep-friendly symmetry. Verified by inspecting `app/build/generated/source/buildConfig/release/.../BuildConfig.java` (production IDs) + the merged release manifest (production app ID), and confirming debug BuildConfig still reads the test IDs.
- **`app/src/main/AndroidManifest.xml`** — Added `<uses-permission android:name="com.android.vending.BILLING" />` explicitly. Play Console's in-app-product creation page hard-gates SKU creation on the uploaded AAB declaring this permission, and Play Billing Library v8 no longer auto-merges it (older versions did). Inline comment documents the rationale so a future cleanup doesn't strip it. Verified the permission appears in the merged release manifest at `app/build/intermediates/merged_manifest/release/processReleaseMainManifest/AndroidManifest.xml`.
- **`.gitignore`** — added `release/` directory ignore (covers `upload-keystore.jks`, `upload-cert.pem`, `screenshots/*.png` — all release-prep artifacts that don't belong in source control), and `app/src/main/assets/adi-registration.properties` (account-specific ADV one-time-use snippet). `*.jks` and `keystore.properties` ignores were already present from Plan 30.

#### Verification

- `./run-gradle.sh testDebugUnitTest` — BUILD SUCCESSFUL, 527 tests pass (no test changes).
- `./run-gradle.sh bundleRelease` — BUILD SUCCESSFUL. Signed AAB at `app/build/outputs/bundle/release/app-release.aab`, 19,396,531 bytes (≈19.4 MB). `jarsigner -verify` reports `jar verified` (PKIX warning is normal for self-signed upload keystores; Play App Signing handles the upstream chain). Merged manifest contains `com.android.vending.BILLING`. AdMob production app ID + 3 ad unit IDs flow into release BuildConfig.
- `./run-gradle.sh assembleDebug` — BUILD SUCCESSFUL. Debug BuildConfig still uses Google's test ad units. Debug APK signed with `~/.android/debug.keystore` per the registered ADV fingerprint.

#### Where we stopped + immediate next steps

User hit a Play Console block at Phase F (in-app product creation): "Product ID must start with a number or lowercase letter. Can contain numbers, lowercase letters, underscores, and periods." Our `BillingProduct` enum constants are UPPER_SNAKE_CASE (`GEM_PACK_SMALL`, `AD_REMOVAL`, `SEASON_PASS`, etc.) and `BillingManagerImpl.skuId()` maps `BillingProduct.name` directly to the Play Billing `productId`. Need to map UPPER_SNAKE_CASE ↔ lowercase for the wire format. Decision deferred to next session; recommendation is to update the `skuId()` private extension + the public `fromSkuIdOrNull` companion extension to lowercase the enum name (e.g. `GEM_PACK_SMALL` → `gem_pack_small`), keeping the Kotlin enum constants idiomatic and only the Play-side string changing.

**Next session's task list (in order):**

1. Update `BillingManagerImpl.skuId()` + `BillingProduct.fromSkuIdOrNull(skuId)` to use lowercase wire format. Audit existing tests (`BillingManagerImplTest`, `BillingManagerParityTest`, `BillingReceiptDaoTest`) for any hardcoded `GEM_PACK_SMALL` strings and update.
2. Rebuild signed AAB, upload to Play Console **Internal testing** track (already-built AAB at `app/build/outputs/bundle/release/app-release.aab` is fine for the AAB-upload step — it has BILLING; but a fresh build with the lowercase mapping should land before SKUs are wired up).
3. Create the 5 SKUs in Play Console: `gem_pack_small`, `gem_pack_medium`, `gem_pack_large`, `ad_removal`, `season_pass`. First three are managed consumables, `ad_removal` is managed non-consumable, `season_pass` is a monthly subscription with 3-day grace + 30-day account hold + no free trial.
4. Add license testers (Gmail addresses), roll out to internal testing track.
5. Internal-track on-device verification: real Play Billing test purchase credits the wallet end-to-end. Unblocks C.5 PR 3 (delete `StubBillingManager` + collapse `BillingModule` Provider-switch to `@Binds`).
6. Recruit ≥12 closed testers, run closed-track release for ≥14 days (new Google production-access prerequisite).
7. Apply for production access, promote to production after Google review (1-7 days).

#### Local artifacts created this session (gitignored)

- `release/upload-keystore.jks` — RSA 2048 production upload key. Backed up to user's password manager.
- `release/upload-cert.pem` — public cert exported for ADV (would have gone via Path B if ADV had not auto-routed via debug keystore). Kept on disk for the future "add additional ADV key" flow that lets the release upload key act as a verified key alongside the debug keystore.
- `release/screenshots/screenshot-{1..5}-{home,workshop,battle,labs,stats}.png` — 5× 1080×1920 24-bit RGB phone screenshots used in the Play Store listing.
- `keystore.properties` — Gradle signing credentials at project root.
- `local.properties` (existing file) — 4 new `admob.*` keys appended.
- `~/Desktop/Screenshot 2026-05-13 at 13.43.19.png` + `~/Desktop/Screenshot2.png` (user-supplied) — Play Console UI screenshots used to diagnose the ADV + pricing-form flows.

### Play Store feature graphic — 1024×500 PNG (2026-05-13)

- **`docs/release/store-assets/play-store-feature-graphic-1024x500.png`** — 1024×500, 621.5 KB, 8-bit RGB. 40% under Play Store's 1024 KB cap.
- **Source.** User supplied `docs/release/store-assets/StepsOfBabylonArt.png` (1376×768, 1.2 MB pixel-art Tower of Babel scene — ziggurat-style tower under a swirling-cloud sky, walking figure on the lower-left third, path leading to the tower base, ruined Mesopotamian buildings framing left and right). Aspect mismatch: source 1.792 vs target 2.048 means 96 px of total height needs cropping.
- **Crop choice.** Center vertical crop `y=48..720` (lose 48 px top + 48 px bottom) for a 1376×672 intermediate, then LANCZOS-downsampled to 1024×500. Considered top-aligned (would clip the character's feet — rejected) and bottom-aligned (would lose dramatic sky-swirl detail — rejected). Center balances composition: full tower retained, character whole, ~85% of swirl pattern preserved, path + framing ruins intact. The minor AI-tool sparkle artifact at the source's bottom-right is sub-perceptual at storefront sizes.
- **PNG over JPEG** because pixel-art crispness matters; JPEG would smear the chunky pixel grid. PNG palette compression on this many distinct colors produced 621.5 KB — well within budget.
- **Source preserved in-repo** for future re-crops. If the Play Store presentation ever wants a different framing (e.g., tighter on the tower, more sky bias), regenerate from the original PNG.
- **Plan 31 raster blocker count: 3 → 1.** Only screenshots remain on the raster-asset list.

### Play Store hi-res icon — 512×512 PNG rendered from vector source (2026-05-13)

- **`docs/release/store-assets/play-store-icon-512.png`** — 512×512, 3.8 KB, 8-bit RGB. Generated artifact, tracked in git as a Play Store release asset.
- **`tools/render_play_store_icon.py`** — reproducible Pillow-only renderer. Reads the same 108-viewport polygon coords (20 vertices) + the same 3-stop vertical gradient stops as the in-app vector XML drawables, supersamples 4× to 2048×2048 for crisp polygon edges, then LANCZOS-downsamples to the final 512×512. No external SVG renderer needed (no rsvg-convert, ImageMagick, or Inkscape install) — the path data + gradient model are reimplemented directly in Pillow drawing primitives.
- **Source-of-truth coordinates duplicated, not symlinked.** The Android adaptive icon uses Android-specific XML schema (`<aapt:attr>` gradient, `android:pathData` SVG-subset) that no off-the-shelf vector renderer accepts directly. The script duplicates the polygon vertex list + gradient stops verbatim and includes a header docstring telling future-you to edit BOTH files when changing the design. Cleaner than maintaining an SVG-XML translation pipeline for a single icon.
- **Pixel sanity check** validates the output: corner `#0E2247` exact match; ziggurat top `#D3A846` (vs Gold `#D4A843`, within 1 channel of perfect — gradient interpolation + LANCZOS blend); middle exact `#C2B280`; bottom `#8D5E3D` (vs lightened DeepBronze `#8B5A3A`, within 4 channels at the polygon edge). All deviations are sub-perceptual at icon-render sizes.
- **Plan 31 status update.** App icon (512×512 PNG) blocker for Play Console upload — resolved. Remaining raster assets: 1024×500 feature graphic + screenshots. The feature graphic is a different composition problem (banner + tagline, not a logo); a future render script could plausibly do it but a designer or image-gen prompt is more cost-effective. Screenshots need device capture from the running app.

### App launcher icon — vector adaptive icon (2026-05-12)

- **Closes the "No app icon resources" debt item** tracked in STATE.md since Plan 30. Four new vector XML resources + `AndroidManifest.xml` wiring; `./run-gradle.sh assembleDebug` BUILD SUCCESSFUL.
- **`res/drawable/ic_launcher_background.xml`** — solid `#0E2247` deep-lapis vector fill. Echoes the `LapisLazuli` brand color from `presentation/ui/theme/Color.kt` (darkened from `#26619C` for contrast against the warm ziggurat foreground) and the Celestial Gate biome night-sky palette from `presentation/battle/biome/BiomeTheme.kt`. Solid over gradient deliberately — gradients in adaptive-icon backgrounds can mis-render on OEM launchers that composite their own mask shape.
- **`res/drawable/ic_launcher_foreground.xml`** — 5-tier stepped-ziggurat silhouette as a single compound closed path, filled with a vertical 3-stop linear gradient: Gold `#D4A843` (top) → SandStone `#C2B280` (mid) → lightened DeepBronze `#8B5A3A` (bottom). The lightened base swaps out brand `DeepBronze #6B3A2A` because pure `DeepBronze` against the `#0E2247` background is tonally too close and loses the silhouette at 24dp launcher renderings. All five tiers echo the 5-layer ziggurat entity in `presentation/battle/entities/ZigguratEntity.kt` and the 5-entry `zigguratColors` list per biome.
- **Geometry invariants.** Canvas 108×108 (adaptive-icon standard); all tower content inside the 72dp safe zone (`x=22..86, y=29..79`); tower visual center at `(54, 54)` matches canvas center so every launcher mask (round / squircle / teardrop / square) crops evenly. Each tier is 10dp tall and steps in 6dp on each side. Dimensions documented inline in the foreground XML header so future edits know the constraint budget.
- **`res/mipmap-anydpi-v26/ic_launcher.xml` + `ic_launcher_round.xml`** — `<adaptive-icon>` wrappers pointing at the two drawables. Round variant has identical contents to the primary — Android handles round masking from the adaptive source, so a separate round asset is unnecessary. `minSdk=34` means every target device supports adaptive icons; no raster density fallbacks needed in `mipmap-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}`.
- **`AndroidManifest.xml`** — added `android:icon="@mipmap/ic_launcher"` + `android:roundIcon="@mipmap/ic_launcher_round"` on the `<application>` tag. Previously these attributes were absent entirely and Android was defaulting to the generic system placeholder.
- **Plan 31 status.** In-app launcher icon: ✅ shippable. Play Store 512×512 hi-res PNG + 1024×500 feature graphic + screenshots: still pending, require raster tooling (Android Studio asset studio / Figma / image-gen service) outside the agent's capability. The vector source is ready to export from.

### Phase C.6 PR 3 — Delete `StubRewardAdManager`, collapse `AdModule` to `@Binds RewardAdManagerImpl` (2026-05-12)

- **`StubRewardAdManager` deleted** (`data/ads/StubRewardAdManager.kt`, 16 lines). The C.6 PR 2 internal-track verification PASS earlier this session (two on-device sessions / two placements exercised the real AdMob pipeline) removed the last reason to keep a second `RewardAdManager` implementation around. Developer debug affordance previously supplied by the stub's always-`Rewarded` path is replaced by `FakeRewardAdManager` in unit tests + Google's documented rewarded-ad test unit id (`ca-app-pub-3940256099942544/5224354917`, already wired as the default `BuildConfig.AD_UNIT_*`) for any device-level exercise.
- **`di/AdModule.kt` rewritten to collapse the C.6 PR 2 Provider switch.** With only one implementation left, the runtime `BuildConfig.USE_REAL_ADS` branch is dead code: `internal object AdModule` with `@Provides + Provider<Stub> + Provider<Real>` → `internal abstract class AdModule` with a single `@Binds RewardAdManager → RewardAdManagerImpl`. Sibling `AdInternalModule` kept as-is (still supplies `RewardedAdAdapter` + `ConsentManager` to both `RewardAdManagerImpl` and `MainActivity`'s direct `ConsentManager` injection). Module stays `internal` because `RewardAdManagerImpl` is `internal`. KDoc now reads as a three-PR history (PR 1 landed real impl → PR 2 flipped binding behind flag → PR 3 deleted stub + collapsed shape).
- **`RewardAdManagerParityTest` deleted** (4 tests, 140 lines). Without a stub to compare against, parity has nothing left to assert. `RewardAdManagerImplTest` (8 tests covering every `AdResult` variant + per-placement ad-unit routing + consent-denied-still-grants) remains the full coverage surface.
- **`BuildConfig.USE_REAL_ADS` retained.** The flag no longer gates the Hilt binding but still gates the `MainActivity.onResume` UMP consent prefetch so debug emulators without Play Services don't pay the UMP init cost on every app start. Symmetric with `USE_REAL_BILLING` and cheap to keep. Debug builds now bind `RewardAdManagerImpl` too — a bare emulator will return `AdResult.Error` on any ad tap (no Play Services → adapter load fails), which matches the release behaviour when `NO_FILL` happens. That's intentional: debug no longer has the "always rewards" surface that was silently masking client code that assumed `Rewarded` as the only outcome.
- **KDoc swept for stale stub references.** Updated `RewardAdManagerImpl.kt` ("PR 1 wiring status" block → three-PR history + minor follow-on rewording), `ConsentManager.kt` ("Scope" paragraph), `RealConsentManager.kt` ("Not wired into MainActivity" block), `MainActivity.kt` (consent prefetch comment), and three `app/build.gradle.kts` comment blocks on `USE_REAL_ADS`. Zero remaining code-level references to `StubRewardAdManager` in `app/src/main` or `app/src/test`; four surviving mentions in `di/AdModule.kt` + `RewardAdManagerImpl.kt` KDoc are historical-only backticked references explaining what PR 3 did.
- **Tests: 531 → 527 (-4).** Exactly the parity test's 4 cases. `./run-gradle.sh testDebugUnitTest` BUILD SUCCESSFUL, 0 failures. `./run-gradle.sh assembleDebug` BUILD SUCCESSFUL — Hilt graph resolves with the collapsed `@Binds` + sibling internal module. Pre-existing Kotlin KT-73255 `@ApplicationContext` param-vs-field warnings on 4 files unchanged (unrelated batch cleanup).
- **Release loop shortens.** Plan 31 Play Console setup is now the only upstream for C.5 PR 3 (symmetric `StubBillingManager` deletion). After Plan 31 unblocks device-track purchase verification, C.5 PR 3 is a similar single-file deletion PR. The pre-existing `AdResult.Error` silent-swallow UX gap in 3 call sites (`CardsViewModel.watchFreePackAd`, `BattleViewModel.watchGemAd`, `BattleViewModel.watchPsAd`) is now more user-visible by default in debug (no stub masking it) but is still not a release blocker.

### Hotfix — Battle-step-credit NOT NULL crash on fresh install + C.6 PR 2 device-track verification PASS (2026-05-12)

- **Crash fixed: `SQLiteConstraintException: NOT NULL constraint failed: daily_step_record.sensorSteps`** on first enemy kill of a fresh install. `DailyStepDao.incrementBattleSteps`'s UPSERT SQL supplied only `date + battleStepsEarned` on the INSERT half; every other column in `daily_step_record` is `NOT NULL` with no SQL `DEFAULT`. SQLite evaluates NOT NULL BEFORE the `ON CONFLICT(date)` clause — `ON CONFLICT(date)` only catches UNIQUE violations, not NOT NULL — so the INSERT aborted on the first NOT NULL check before the conflict handler could route to UPDATE. Reproduced on the C.6 PR 2 device-track emulator: fresh install, start battle, first kill, process dies. Bug was latent since B.2 PR 2 (battle-step-credit atomicity); unit tests never hit it because `FakeDailyStepDao` is a plain in-memory Map with no `NOT NULL` enforcement.
- **Fix**: expanded the INSERT half of `incrementBattleSteps`' UPSERT to supply every NOT NULL column explicitly (`0` for every numeric column, `'{}'` for the JSON-encoded `activityMinutes` map which matches the `Converters.fromStringIntMap(emptyMap())` round-trip). The UPDATE branch on conflict still touches only `battleStepsEarned`, preserving any existing sensor / HC / escrow data populated earlier in the day by the step sensor path. Kotlin-level entity defaults (`val sensorSteps: Long = 0`, `val activityMinutes: Map<String, Int> = emptyMap()`) unchanged — they govern Kotlin construction, not Room-generated SQL, which was the root of the bug.
- **First-attempt miss documented**: initially added an `insertIfAbsent` pre-seed via `@Insert(onConflict = OnConflictStrategy.IGNORE)` inside the atomic transaction, hoping it would make the subsequent UPSERT hit the UPDATE branch. Reverted after tests still failed with the same constraint exception — because SQLite evaluates NOT NULL on the new INSERT attempt regardless of whether an existing row would satisfy the ON CONFLICT target. Fixing the SQL directly is the only path.
- **Single source of truth preserved**: no schema migration, no DB version bump (still v9), no `@ColumnInfo(defaultValue = "0")` sprinkled across the entity (would have required a migration to update the CREATE TABLE statement). The hardcoded zeros in the SQL duplicate the entity's Kotlin defaults, which is a minor lint but the most surgical fix for an already-released schema.
- **Device-track verified on-device**: same emulator that crashed earlier in the session — rebuilt, reinstalled, started a battle, killed enemies, **no crash**. Full battle→round-end→post-round overlay flow works.
- **Tests: 526 → 531 (+5).** New `DailyStepDaoTest` (Robolectric + real in-memory Room, mirrors `BillingReceiptDaoTest` pattern): direct `incrementBattleSteps succeeds on empty table` regression guard, `creditBattleStepsAtomic credits successfully on empty table` (wallet credit + step balance + all Kotlin-default columns populated), `creditBattleStepsAtomic preserves existing sensor data` (ON CONFLICT UPDATE branch doesn't clobber populated rows), `creditBattleStepsAtomic returns partial credit near the cap`, `creditBattleStepsAtomic returns zero when cap already exhausted`. `./run-gradle.sh test` = BUILD SUCCESSFUL, 531 tests, 0 failures.
- **C.6 PR 2 device-track verification marked PASS.** Two on-device sessions with two different `AdPlacement` values (`DAILY_FREE_CARD_PACK` → AdMob `NO_FILL` code 3; `POST_ROUND_GEM` → DNS resolution failure code 0) both exercised the complete real-SDK pipeline end-to-end: real `RewardAdManagerImpl` selected by the flag-gated `AdModule` Provider switch, UMP consent dialog fired and completed from the `MainActivity.onResume` prefetch, `ActivityProvider` supplied the Activity, sibling `AdInternalModule` bindings resolved `RewardedAdAdapter` + `ConsentManager`, AdMob SDK v25 made the outbound request, error codes mapped and surfaced by the impl. The only un-exercised-live branch is `AdResult.Rewarded`, which is mechanistically symmetric to `Error` in our code — no conditional logic gates the happy-vs-error flow beyond the obvious wallet credit.
- **UX gap surfaced (pre-existing, not C.6 PR 2)**: `CardsViewModel.watchFreePackAd` + `BattleViewModel.watchGemAd` + `BattleViewModel.watchPsAd` all silently swallow `AdResult.Error` and `AdResult.Cancelled` — only `AdResult.Rewarded` has observable effect. User sees "nothing happens" on an ad tap whenever Google returns NO_FILL or the network stutters. Logged as a follow-up; not a release-blocker but worth a small snackbar plumbing pass before public launch. Affects 3 call sites.
- **Unblocks C.6 PR 3** (stub deletion). Paired with outstanding C.5 PR 2 device verification, which remains blocked on Plan 31 Play Console setup.

### Phase C.6 PR 2 — Flag-gated ad manager binding + MainActivity consent prefetch (2026-05-12)

- **`di/AdModule.kt` rewritten.** `abstract class` with a single `@Binds StubRewardAdManager` → `internal object` with `@Provides provideRewardAdManager` that accepts `Provider<StubRewardAdManager>` + `Provider<RewardAdManagerImpl>` and picks via `BuildConfig.USE_REAL_ADS`. Lazy `Provider` injection means the unselected impl is never constructed: the stub's `delay(1000)` never fires in release, and the real impl's AdMob + UMP clients never start in debug. Sibling `internal abstract class AdInternalModule` `@Binds` `RewardedAdAdapter` → `RealRewardedAdAdapter` and `ConsentManager` → `RealConsentManager` so Dagger can construct `RewardAdManagerImpl` when asked. Both modules are `internal` because they reference `internal` types. Mirrors the `BillingModule` / `BillingInternalModule` shape from C.5 PR 2.
- **MainActivity consent prefetch.** `@Inject internal lateinit var consentManager: ConsentManager` added alongside the existing `activityProvider`. `onResume()` fires a flag-gated one-shot `consentManager.ensureInitialized(this@MainActivity)` launch on the existing `activityScope` (Main.immediate, cancelled on destroy) guarded by an `AtomicBoolean consentPrefetchAttempted`. Flag-gate on `BuildConfig.USE_REAL_ADS` means debug builds (stub binding) never hit UMP / Play Services — emulator-friendly. Release builds amortise the ~200-500ms UMP init before the user's first reward-ad tap. UMP's own idempotency makes a missed guard harmless; the guard exists to skip launching coroutines that would immediately no-op.
- **Dagger graph resolution.** No missing-binding error on first build — the `AdInternalModule` bindings for `RewardedAdAdapter` + `ConsentManager` satisfy both `RewardAdManagerImpl`'s constructor deps and `MainActivity`'s direct injection of `ConsentManager`. Unlike C.5 PR 2 (which hit a missing-binding on first run and had to add the sibling module in a second pass), C.6 PR 2 shipped both modules in the initial cut.
- **No new dependencies.** `play-services-ads:25.0.0` + `user-messaging-platform:4.0.0` already on the classpath from C.6 PR 1. `BuildConfig.USE_REAL_ADS` already present from C.6 PR 1 (debug=false, release=true, `buildConfig = true` opted in via `buildFeatures`). The flag was dormant until this PR read it.
- **Tests: 522 → 526 (+4).** New `RewardAdManagerParityTest` (4 tests, plain-Kotlin mockito-kotlin — no Robolectric): 3 per-placement happy-path parity (`POST_ROUND_GEM` / `POST_ROUND_DOUBLE_PS` / `DAILY_FREE_CARD_PACK` all produce `AdResult.Rewarded` from both `StubRewardAdManager` and `RewardAdManagerImpl` when consent + adapter mocks are wired to happy responses); 1 `isAdAvailable` parity test (both return `true` for all 3 placements per ADR-0006 decision 4 where the real availability check moves into `showRewardAd`). Mirrors `BillingManagerParityTest`'s per-shape test structure from C.5 PR 2. Completes in 20ms total.
- **Not in this PR:** device-only internal test track verification of a real AdMob reward-ad render. That's the C.6 PR 2 → PR 3 gate, and it happens outside the unit-test loop. C.6 PR 3 (delete `StubRewardAdManager`) lands after ~1 week of closed-track confirmation.
- **Kotlin KT-73255 forward-compat warnings on `RealRewardedAdAdapter:56` and `RealConsentManager:55`** (plus the two pre-existing billing warnings) are all the same `@ApplicationContext` param-vs-field annotation-target issue; not addressed in this PR. Would land as a batch cleanup when `-Xannotation-default-target=param-property` flips.

### Phase C.6 PR 1 — Real AdMob `RewardAdManagerImpl` + UMP consent (2026-05-11)

- **Deps:** `com.google.android.gms:play-services-ads:25.0.0` + `com.google.android.ump:user-messaging-platform:4.0.0` pinned in `libs.versions.toml`. v25 is the current stable Google Mobile Ads SDK line as of 2026-05. ADR-0006 promoted Proposed → Accepted with 9 concrete commitments + answers to Q1–Q6.
- **New files:** `data/ads/RewardAdManagerImpl.kt` (orchestrates consent → load → show → `AdResult` mapping with per-placement `BuildConfig` ad-unit routing + sessionMutex + AdMob error-code-to-user-message translation); `data/ads/internal/RewardedAdAdapter.kt` (SDK-neutral seam with `SdkAdLoadResult` + `SdkAdShowResult` + `SdkRewardedAd` sealed types); `data/ads/internal/RealRewardedAdAdapter.kt` (the only file importing `com.google.android.gms.ads.*` — lazy `MobileAds.initialize` on first load, `CompletableDeferred` bridging all three AdMob callbacks, `AtomicBoolean` `rewarded` flag set ONLY in `onUserEarnedReward`); `data/ads/internal/ConsentManager.kt` (UMP-neutral interface); `data/ads/internal/RealConsentManager.kt` (the only file importing `com.google.android.ump.*` — Mutex-guarded once-per-session `requestConsentInfoUpdate` + `loadAndShowConsentFormIfRequired`).
- **Shared infrastructure.** `RewardAdManagerImpl` consumes the existing `data/billing/internal/ActivityProvider` (introduced in C.5 PR 1 and wired via MainActivity in C.5 PR 2). Both `RewardedAd.show()` and `BillingClient.launchBillingFlow()` need an Activity; sharing the WeakReference holder avoids a second lifecycle observer and duplicate onResume/onPause code.
- **Build config.** `BuildConfig.USE_REAL_ADS` introduced (debug=false, release=true) via `buildConfigField`. Three additional `buildConfigField` strings for per-placement ad-unit IDs — all three default to Google's documented rewarded-ad test unit (`ca-app-pub-3940256099942544/5224354917`) in debug so any dev can exercise the real SDK path without a production AdMob account; release overrides sourced from `local.properties` will be wired in C.6 PR 2. AdMob `APPLICATION_ID` supplied to `AndroidManifest.xml` via new `admobAppId` manifestPlaceholder (debug: `ca-app-pub-3940256099942544~3347511713` test app ID; release override in PR 2).
- **Manifest.** `<meta-data android:name="com.google.android.gms.ads.APPLICATION_ID" android:value="${admobAppId}"/>` added inside `<application>` per AdMob's SDK-init contract — missing entry causes a `RuntimeException` on first request.
- **ProGuard.** Added `-keep class com.google.android.gms.ads.** { *; }` + `-keep class com.google.android.ump.** { *; }` + matching `-dontwarn` rules. Both SDKs ship internal keep manifests in their AARs; explicit rules here guard against R8 regressions across SDK version bumps.
- **Binding unchanged.** `di/AdModule.kt` still `@Binds`s `StubRewardAdManager`. C.6 PR 2 is where the `BuildConfig.USE_REAL_ADS` flag is read and the binding swap + MainActivity consent-flow wiring lands (mirroring C.5 PR 2's Provider-based switch in `BillingModule`).
- **ADR-0006 Proposed → Accepted.** 6 open questions resolved with concrete decisions: Q1 (consent-denied reward) YES grant the reward; Q2 (ad-load timeout) defer to AdMob's ~60s default to preserve distinct error codes; Q3 (per-session cap) NO — opt-in ads already capped per-placement; Q4 (mediation scaffolding) NO upfront abstraction; Q5 (child-directed flag) NO — game targets adults; Q6 (test ads in release debug) NO — internal-track tests real ads. See `docs/agent/DECISIONS/ADR-0006-ad-sdk.md` for the full decision table.
- **Tests: 514 → 522 (+8).** New `RewardAdManagerImplTest` (8 tests, plain-Kotlin sealed adapter + mockito-kotlin; no Robolectric): happy `Rewarded` path; `Cancelled` on user-dismiss; 4 `Error` paths (no activity, consent unavailable, load failed with AdMob code 3 → "No ad available" message, show failed with code 1 → "already shown" message); consent-denied-still-grants per Q1; placement → ad-unit routing for all 3 `AdPlacement` values. `./run-gradle.sh test` = BUILD SUCCESSFUL.
- **Kotlin KT-73255 forward-compat warning on `RealRewardedAdAdapter:56`** is the same `@ApplicationContext` param-vs-field annotation target issue that exists on `BillingManagerImpl:79` and `RealBillingClientAdapter:54` — not addressed in this PR; lands as a batch cleanup when `-Xannotation-default-target=param-property` flips.

### Phase C.5 PR 2 — Flag-gated binding swap + MainActivity lifecycle wiring + reconcile hook (2026-05-11)

- **New build flag.** `BuildConfig.USE_REAL_BILLING` — `false` in debug (binds `StubBillingManager`), `true` in release (binds `BillingManagerImpl`). `buildFeatures.buildConfig = true` opted in because AGP 9 disables it by default. `defaultConfig` sets a safe `false` baseline for any future flavour that forgets to override; `debug { }` / `release { }` override explicitly for grep-friendly symmetry.
- **`di/BillingModule.kt` rewritten.** `abstract class` with `@Binds` → `internal object` with `@Provides` that accepts `Provider<StubBillingManager>` + `Provider<BillingManagerImpl>` and picks via `BuildConfig.USE_REAL_BILLING`. Lazy `Provider` injection means the unselected impl is never constructed: the stub's `PlayerRepository` observer never attaches in release, and the real impl's Play Billing client never starts in debug. Sibling `internal abstract class BillingInternalModule` `@Binds` `BillingClientAdapter → RealBillingClientAdapter` so Dagger can construct `BillingManagerImpl` when asked (required even if the debug Provider is never invoked — Dagger resolves the whole graph at compile time). Both modules are `internal` because they reference `internal` types (`BillingManagerImpl`, `RealBillingClientAdapter`).
- **`MainActivity` lifecycle wiring.** `@Inject internal lateinit var activityProvider: ActivityProvider` added. `onResume()` calls `activityProvider.set(this)` BEFORE the existing `updateLastActiveAt` launch. New `onPause()` override calls `activityProvider.clear()` BEFORE `super.onPause()` so nothing observes a stale Activity reference mid-teardown. The `WeakReference` in `ActivityProvider` is the belt — the explicit `clear()` is the suspenders, because a paused-but-not-yet-GC'd Activity could otherwise race with a purchase attempt.
- **`StoreViewModel.init` reconcile hook.** Added `viewModelScope.launch { billingManager.reconcilePendingPurchases() }` as a second `init` block launch (runs concurrently with the existing `cosmeticRepository.ensureSeedData()`). `BillingManager.reconcilePendingPurchases()` inherits a default no-op, so `StubBillingManager` + `FakeBillingManager` stay silent outside release. In release builds this sweeps `PENDING → PURCHASED` transitions on Store entry and retries any consume/ack that failed after a prior grant landed in Room — without re-crediting the wallet (the `granted = true` guard short-circuits `grantOnceAtomic`).
- **`FakeBillingManager` gained `reconcileCallCount: Int` with `private set`** so the StoreViewModel reconcile-hook invariant can be test-asserted. Default no-op `reconcilePendingPurchases()` overridden to increment the counter; call count stays zero for any test that doesn't construct a `StoreViewModel`.
- **Tests: 510 → 514 (+4).** New `BillingManagerParityTest` (3 tests, Robolectric + 2 independent in-memory Room DBs + real `PlayerRepositoryImpl` on both sides + mocked `BillingClientAdapter` on real side): `GEM_PACK_SMALL` parity (both credit 50 gems + `totalGemsEarned`), `AD_REMOVAL` parity (both flip `adRemoved` + leave gem wallet alone), `SEASON_PASS` parity (both activate + expiry within 60s tolerance of `now + 30 days` — stub uses call-time, real uses mocked `purchaseTime`, 60s is exhaustive for "30-day window within a test run"). Plus 1 new `StoreViewModelTest` case asserting `billingManager.reconcileCallCount == 1` after VM init. `./run-gradle.sh test` = BUILD SUCCESSFUL, 0 failures, 0 errors.
- **Not in this PR:** device-only internal test track verification of a real Play Billing purchase. That's the C.5 PR 2 → PR 3 gate, and it happens outside the unit-test loop. C.5 PR 3 (delete `StubBillingManager`) lands after ~1 week of closed-track confirmation.
- **Kotlin KT-73255 forward-compat warnings on `BillingManagerImpl:79` and `RealBillingClientAdapter:54`** are pre-existing from C.5 PR 1 (Hilt `@ApplicationContext` param-vs-field targeting); not addressed in this PR.

### Phase C.5 PR 1 — Real Play Billing v8 `BillingManagerImpl` (2026-05-11)

- **Dep:** `com.android.billingclient:billing-ktx:8.3.0` pinned in `libs.versions.toml`. v7 sunsets 2026-08-31 per Google's two-year deprecation window, so v8 is the current line. ADR-0005 amended from v7 → v8.
- **New files:** `data/billing/BillingManagerImpl.kt` (orchestrates purchases + receipts + wallet credits + anti-fraud `obfuscatedAccountId`); `data/billing/internal/BillingClientAdapter.kt` (SDK-neutral seam with sealed result types); `data/billing/internal/RealBillingClientAdapter.kt` (the one file that imports `com.android.billingclient.*`); `data/billing/internal/ActivityProvider.kt` (weak-ref holder, wired by MainActivity in PR 2); `data/local/BillingReceiptEntity.kt` + `data/local/BillingReceiptDao.kt` (idempotency store with `grantOnceAtomic` @Transaction).
- **DB schema bump v8 → v9.** `AppDatabase` grew to 13 entities + 13 DAOs. New `MIGRATION_8_9` creates the `billing_receipt` table with DDL byte-matching the Room-generated `app/schemas/…/9.json` export (verified via `RoomSchemaTest`). `DatabaseModule` gained `provideBillingReceiptDao`.
- **Interface extended.** `BillingManager` gained `suspend fun reconcilePendingPurchases()` with a default no-op body. `StubBillingManager` and `FakeBillingManager` inherit the no-op automatically — zero changes to existing callers.
- **Domain model shift.** `BillingProduct` gained an empty `companion object` so the data layer can attach reverse-lookup extensions (`fromSkuIdOrNull`). No Android import introduced — domain layer stays pure.
- **ProGuard.** Added `-keep class com.android.billingclient.** { *; }` + `-keep interface` + `-dontwarn` per Play Billing release-note guidance.
- **Binding unchanged.** `di/BillingModule.kt` still `@Binds`s `StubBillingManager`. C.5 PR 2 is where the `BuildConfig.USE_REAL_BILLING` flag and binding swap land.
- **Atomicity model.** Wallet credits run INSIDE `BillingReceiptDao.grantOnceAtomic` — receipt flip + wallet write commit atomically. Play Services RPCs (`consumeAsync`, `acknowledgePurchaseAsync`) run AFTER the transaction, so the SQLite lock is never held across a Google round-trip. Failed consume/ack is retried by `retryUnresolvedConsumeOrAck()` on the next reconciliation sweep — wallet is NOT re-credited (the `granted = true` guard short-circuits). Pending purchases persist with `granted = false` and are promoted to PURCHASED on the next sweep.
- **5 ADR open questions resolved** (promoting ADR-0005 Proposed → Accepted): Q1 delegates to v8's `enableAutoServiceReconnection()`; Q2 orders consume/ack after grant-commit with retry-without-re-credit; Q3 uses real Play Console SKUs + license test accounts (no static test SKUs); Q4 marks subscription proration out of scope for v1.0; Q5 sets `obfuscatedAccountId` to SHA-256 of a device-local UUID stored in `SharedPreferences("billing_anti_fraud")`.
- **Tests: 488 → 510.** `BillingReceiptDaoTest` (7 tests, Robolectric + real in-memory Room): upsert/get round-trip, `getByToken` not-found, `grantOnceAtomic` flip + wallet-credit lambda ran exactly once, idempotency (second call returns false, wallet lambda NOT run, `grantedAt` from first call preserved), `markConsumed`/`markAcknowledged` target-only, `getGrantedButUnresolved` filter, `getAll` orders by `purchaseTime` DESC. `RoomSchemaTest` extended with billing_receipt round-trip touching every column (incl. 4 nullables). `BillingManagerImplTest` (14 tests, Robolectric + real in-memory Room + mockito-kotlin on adapter): 3 happy paths (GEM_PACK_SMALL + consume / AD_REMOVAL + ack / SEASON_PASS + 30-day expiry), 5 failure paths (user cancel, product unavailable, no activity, connect fails, pending purchase persists receipt without credit), idempotency (same `purchaseToken` → Success + no double-credit), 2 reconciliation cases (PENDING→PURCHASED transition grants exactly once across repeated sweeps; `retryUnresolvedConsumeOrAck` retries consume without re-crediting wallet), `isAdRemoved` / `isSeasonPassActive` delegation to `PlayerRepository`. `./run-gradle.sh test` = BUILD SUCCESSFUL.

### Phase A — Foundation (2026-05-07, all 9 PRs merged)
- **A.2** Added `junit-vintage-engine` to test classpath — recovered 9 previously-hidden Robolectric tests (`RoomSchemaTest`, `DeepLinkRoutingTest`, `StepWidgetProviderTest`). Each needed `@Config(sdk = [34], application = android.app.Application::class)` as a Robolectric 4.14.1 / compileSdk 36 workaround.
- **A.3** `DatabaseKeyManager` now deletes the on-disk DB file (plus -shm/-wal) when the passphrase blob fails to decrypt, preventing crash-on-launch loops after device restore.
- **A.6** `DailyStepManager.runFollowOnPipeline` now forwards `seasonPassActive` / `seasonPassExpiry` to `TrackDailyLogin`, so the +10 Gems/day Season Pass bonus is credited even when step ingestion runs from the worker or background service.
- **A.5** `Screen.fromRoute` + `argumentFreeRoutes` whitelist — deep-links now reach all 12 argument-free routes (previously: 4). Unknown routes fall through silently.
- **A.4** `FakeBillingManager` / `FakeRewardAdManager` gained `resultQueue` scripting, configurable `isAdRemoved`/`isSeasonPassActive`/`isAdAvailable`, and call-log history. Store / Cards ViewModel tests now exercise every `PurchaseResult` / `AdResult` variant.
- **A.7** Capped Battle Step kills no longer spawn a misleading "+N Step" FloatingText. Callback signature changed to `(amount, x, y)` and spawn responsibility moved from GameEngine into BattleViewModel's callback.
- **A.8** Removed dead `PlaceholderScreen` and 4 orphaned imports from `MainActivity.kt`.
- **A.9** Deleted unused `SupplyDropTrigger.STEP_BURST` enum entry (no producer, no Room rows, no tests). Commit body preserves the original notification copy.
- **A.1** Current-state docs synced to DB schema v8 and 453-test baseline.

### Phase B.1 — Core Refactoring (2026-05-07, TimeProvider narrow migration)
- **B.1 PR 1** Added `TimeProvider` interface in `domain/time/` with `now(): Instant` and `today(): LocalDate`. Production `SystemTimeProvider` in `data/time/`, Hilt wiring in `di/TimeModule.kt`.
- **B.1 PR 2** Migrated 3 date-reading sites to a `timeProvider` default-arg parameter: `AwardBattleSteps`, `BattleViewModel`, `MissionsViewModel`. ~50 other wall-clock sites left on the real clock by design — narrow migration, not a sweep.
- **B.1 PR 3** Added `FakeTimeProvider` and 2 midnight-boundary tests that were previously impossible to write against the real clock. BattleViewModel now propagates its `timeProvider` into the inline `AwardBattleSteps` construction so the abstraction is end-to-end.
- ADR-0004 FollowOnPipeline stub recorded (status: Proposed, upgrade to Accepted when B.4 PR 1 lands).

### Phase B.2 — RO-02 atomic multi-writes (2026-05-07, PRs 1–3 of 5)
- **B.2 PR 1** `PurchaseUpgrade` now commits through `WorkshopDao.purchaseUpgradeAtomic` — a suspend `@Transaction` default interface method that takes `PlayerProfileDao` as a param. SQL-guarded deduct `UPDATE … WHERE currentStepBalance >= :cost` plus the workshop-level upsert runs in a single SQLite transaction. Closes the partial-failure gap between `spendSteps` and `setUpgradeLevel` and the double-tap race where two concurrent purchases could both pass an in-memory affordability check and double-spend. `PurchaseUpgrade` dropped its `PlayerRepository` dep; body shrank to a single delegation. First `@Transaction` marker in `app/src/main`.
- **B.2 PR 2** `AwardBattleSteps` now commits through `DailyStepDao.creditBattleStepsAtomic` — same pattern as PR 1, cross-DAO `@Transaction` default method taking `PlayerProfileDao`. Cap check + `incrementBattleSteps` + `adjustStepBalance` wrapped atomically. Closes the partial-failure gap (wallet credited without cap counter advancing) and the concurrent-kill race (two kills with 1 headroom could overflow by 1). `AwardBattleSteps` dropped its `PlayerRepository` dep; `BattleViewModel` gained a Hilt-injected `PlayerProfileDao`.
- **B.2 PR 3** `StepCrossValidator` wraps its 5 multi-write branches (Level 3 / Level 2 cap-excess, Level 1 / Level 0 first-escrow, reconciliation release) in `AppDatabase.withTransaction { }`. Different idiom from PRs 1–2 (repo-level not DAO-level) because the validator lives in `data/healthconnect/` and needs parallel transaction scopes; RO-02 explicitly licenses the cross-layer `AppDatabase` import here. Introduced a test-friendly `@VisibleForTesting internal var runInTransaction` seam so existing Mockito-based tests keep working without a real Room DB. `SharedPreferences` anti-cheat writes (`recordCvOffense`, `decayCvOffenses`) deliberately stay outside the transaction (not SQLite-backed).
- **B.2 PRs 4–5 still pending:** `ClaimMilestone` atomic (same pattern as PRs 1–2) and the `runEndRoundPersistence` `@Transaction` wrap (single-call-site change thanks to B.3 PR 1).

### Phase B.3 PR 1 — Resilient `runEndRoundPersistence` (2026-05-07)
- Extracted `BattleViewModel.endRound` body into a private suspend `runEndRoundPersistence(eng, wave)`. Each of 5 writes + 1 notification is wrapped in its own `runCatching { }.onFailure { Log.w(TAG, "endRound: <writeName> failed", it) }` so a single Room / notification-manager exception can no longer leave the player on a frozen battle screen with no post-round overlay.
- Writes whose results feed `RoundEndState` (`updateBestWave` → isNewBestWave/previousBest, `awardWaveMilestone` → psAwarded, `checkTierUnlock` + `updateHighestUnlockedTier` → tierUnlocked) use `.getOrNull()` / `.getOrDefault(0)` fallbacks, so the `_uiState.update` push **always** runs — even when every write throws.
- Writes 4–5 (`incrementBattleStats`, `dailyMissionDao.updateProgress`) moved from ad-hoc `try / catch (_: Exception) { /* best-effort */ }` swallows to `runCatching + Log.w` for observability parity with the R2-07 `StepSyncWorker` precedent.
- `endRound()` shrank from ~35 lines to 6 (guard + null-check + `viewModelScope.launch { runEndRoundPersistence(eng, wave) }`). `quitRound()` and the polling-loop call site unchanged; `roundEnded` guard still dedupes.
- `FakePlayerRepository` opened up (`class` → `open class`; 4 write methods marked `open override`) so tests can inject per-method throwing overrides to exercise the failure-isolation paths.
- `onCleared` mid-navigation round-loss fix is deliberately deferred to B.3 PR 2 per the RO-03 spec.

### Phase B.2 PR 4 — Atomic @Transaction for ClaimMilestone (2026-05-08)
- **MilestoneDao** gained `claimMilestoneAtomic(milestoneId, gems, powerStones, claimedAt, playerDao)` — a suspend `@Transaction` default method. Read-modify-write pattern matches `DailyStepDao.creditBattleStepsAtomic`: check existing → bail if already claimed → `upsert(MilestoneEntity(claimed=true, claimedAt))` → credit gems + power stones via `playerDao.adjustGems` + `incrementGemsEarned` (and Power Stones equivalents) inside the tx. Closes the partial-failure gap between reward credits and the mark-claimed write, and the double-claim race where two concurrent clicks could both see `claimed=false` and both credit.
- **ClaimMilestone** use case: dep shape `(milestoneDao, playerRepository)` → `(milestoneDao, playerRepository, playerProfileDao)`. Still reads `totalStepsEarned` through `PlayerRepository` (monotonic read, safe outside the tx). Body shrank from reward-iteration loop + upsert to a single atomic delegation. `MilestoneReward.Cosmetic` still a no-op pending C.4 detection fix.
- **MissionsViewModel** gained a Hilt-injected `PlayerProfileDao`. **FakeMilestoneDao** gained optional `linkedPlayer: FakePlayerRepository? = null` + Mutex-guarded `claimMilestoneAtomic` override + `claimMilestoneAtomicCallCount` counter. Existing no-arg construction sites (CheckMilestonesTest, HomeViewModelTest) stay source-compatible.
- **ClaimMilestoneTest**: 5 → 8 cases (+3 RO-02 atomicity cases).

### Phase B.2 PR 5 — Room @Transaction around runEndRoundPersistence (2026-05-08, FINAL RO-02 site)
- **BattleViewModel.runEndRoundPersistence** now commits its 5 SQLite writes (`updateBestWave`, `awardWaveMilestone`, `updateHighestUnlockedTier`, `incrementBattleStats`, `dailyMissionDao.updateProgress`) inside a single `AppDatabase.withTransaction { }` block. External readers (Flow-based reactive reads) now see either the pre-PR state or the post-PR state, never a partial fan-out.
- Constructor grew to 12 params (+`AppDatabase`). Introduced `@VisibleForTesting internal var runInTransaction` seam matching `StepCrossValidator`'s B.2 PR 3 idiom — Mockito mocks of `AppDatabase` can't run Room's `withTransaction` extension, so tests override with a direct-invocation pass-through.
- Non-SQLite side effects (milestone notification, `_uiState.update`) moved to *after* the tx — no DB lock held across Android system calls or UI pushes.
- Outer `runCatching { runInTransaction { ... } }` preserves RO-03 resilience: Room infrastructure failures (disk full, SQLCipher decrypt failure) still let the post-round overlay appear with safe defaults (`isNewBestWave = false`, etc).
- **RO-02 family complete: 5/5 sites landed** (3 DAO-level `@Transaction` + 2 repo-level `withTransaction`).
- **BattleViewModelTest**: 19 → 21 cases (+2 atomicity cases: tx opened exactly once per round; UI push runs AFTER tx commits).

### Phase B.3 PR 2 — onCleared guard preserves mid-nav round progress (2026-05-08, FINAL RO-03 site)
- **New `di/CoroutineScopeModule.kt`** with `@ApplicationScope` qualifier + `@Singleton @Provides fun provideApplicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)`. Outlives VM cancellation — fire-and-forget work launched here completes even if the originating ViewModel is cleared mid-operation.
- **Deviation from RO-03 spec** (documented in the module's KDoc): spec suggested `ProcessLifecycleOwner.lifecycleScope`, but (a) `androidx.lifecycle:lifecycle-process` isn't on the classpath, (b) its default dispatcher is `Dispatchers.Main` — wrong for DB writes, (c) Hilt-injected scope is more testable (`TestScope`-backed), (d) matches the project's Hilt-first conventions (BillingModule / AdModule / TimeModule precedent).
- **BattleViewModel**: +`@param:ApplicationScope applicationScope: CoroutineScope` (12 → 13 params). Extracted `markEndedAndLaunchPersistence(scope, eng)` helper that sets `roundEnded + roundOver` and launches `runEndRoundPersistence` on the provided scope; `endRound()` delegates via `viewModelScope`, new `onCleared()` override delegates via `applicationScope` when `engine != null && !roundEnded && engine.hasWaveProgress()`. Three-way guard prevents bounce-through phantom persistence and double-persistence on the normal `quitRound → onCleared` sequence.
- **GameEngine.hasWaveProgress()**: `elapsedTimeSeconds > 0f || totalEnemiesKilled > 0`. Thread-safe (reads `@Volatile` fields only).
- **RO-03 family complete: 2/2 sites landed** (B.3 PR 1 resilient extraction + B.3 PR 2 mid-nav scope guard).
- **BattleViewModelTest**: 21 → 24 cases (+3 B.3 PR 2 tests: mid-round onCleared persists, no-progress bounce-through is no-op, post-quitRound onCleared is no-op).
- Fixed the `@ApplicationScope` → `@param:ApplicationScope` forward-compat warning (Kotlin KT-73255) on the same PR for clean build.

### Phase C.2 PR 1 — Cosmetic renderer override pipeline (2026-05-08, RO-07 plumbing)
- **CosmeticItem** domain model: +`overrideColors: List<Int>? = null` nullable field. Pure Kotlin (no Android imports in domain). All existing construction sites stay source-compatible.
- **CosmeticRepositoryImpl**: +private `ZIGGURAT_COLOR_LOOKUP: Map<String, List<Int>>` empty map + KDoc (first entry ships in C.2 PR 2 with ZIG_JADE). `toDomain` populates `overrideColors = ZIGGURAT_COLOR_LOOKUP[cosmeticId]`. No DB schema change — colors are content, live in code.
- **GameEngine**: +`@Volatile var cosmeticOverrides: Map<CosmeticCategory, CosmeticItem> = emptyMap()` public property. In `init()`, selects `cosmeticOverrides[ZIGGURAT_SKIN]?.overrideColors ?: biomeTheme.zigguratColors` when constructing ZigguratEntity — null-coalesce guarantees no regression when no cosmetic equipped.
- **BattleViewModel** constructor grew to 14 params (+`CosmeticRepository`). Loads equipped cosmetics in the init launch (`cosmeticRepository.observeEquipped().first().associateBy { it.category }`) and pushes to `engine?.cosmeticOverrides` in TWO places: init-launch completion and `startPollingEngine` — idempotent double-push handles the load-vs-attach race; whichever fires last wins.
- Pure additive plumbing. No user-visible change until C.2 PR 2 seeds ZIG_JADE + removes the R2-11 "Coming Soon" guard for that single ID in StoreScreen.
- **BattleViewModelTest**: 24 → 26 cases (+2 C.2 PR 1 tests: empty equipped set stays empty on engine; equipped ZIGGURAT_SKIN cosmetic propagates to `engine.cosmeticOverrides`).

### Phase C.2 PR 2 — Seed zig_jade as first end-to-end cosmetic (2026-05-08, RO-07 content)
- **CosmeticRepositoryImpl**: `ZIGGURAT_COLOR_LOOKUP` populated with its first entry — `"zig_jade"` mapped to the 5-color jade palette `[0xFF104E3C, 0xFF1A6B52, 0xFF2A8F6E, 0xFF3CAB82, 0xFF54C79A]` (bottom layer → top highlight). Matches the fixture used by the PR 1 synthetic VM→engine test.
- **SEED_COSMETICS**: +1 row — `CosmeticEntity(cosmeticId = "zig_jade", category = "ZIGGURAT_SKIN", name = "Jade Ziggurat", description = "Deep jade stone with pale highlights", priceGems = 150)`. Placed first in the list so it surfaces at the top of the Store cosmetics section. Total seed count: 7 → 8 (4 ZIGGURAT_SKIN, 2 PROJECTILE_EFFECT, 2 ENEMY_SKIN).
- **StoreScreen**: R2-11 "Coming Soon" guard lifted for `zig_jade` only via a new file-level `ENABLED_COSMETIC_ID` allow-list const. Unowned jade shows `💎 {priceGems}` on an enabled Button wired to `viewModel.purchaseCosmetic`, disabled while `state.isPurchasing` (double-tap guard). All other unowned cosmetics stay behind the existing "Coming Soon" disabled button until their palette ships in C.2 PR 3+. Disclaimer line updated: "Most cosmetic visuals are still being finalized. Jade Ziggurat is available now."
- **FakeCosmeticDao**: new in-memory fake (`test/fakes/`, 75 LOC) simulating Room's `@PrimaryKey(autoGenerate = true)` via a monotonic counter, plus per-cosmeticId upsert / equip / unequip / unequipCategory semantics matching the real DAO contract.
- **CosmeticRepositoryImplTest**: new (`test/data/repository/`, 134 LOC, 5 cases) proves the `seed → ZIGGURAT_COLOR_LOOKUP → CosmeticItem.overrideColors` chain on the real impl — the last mile of the C.2 pipeline that PR 1's VM test could not cover with a fake repo. Cases: (1) `ensureSeedData` inserts `zig_jade` with correct metadata; (2) `zig_jade.overrideColors` matches the 5-color jade palette exactly (content-as-code contract); (3) other 7 seeds have `null` overrideColors (regression guard: lookup is selective, not blanket); (4) equipped `zig_jade` surfaces via `observeEquipped` with palette intact — repo-layer mirror of the PR 1 VM→engine test; (5) `ensureSeedData` idempotent on repeat call (documents the current all-or-nothing `dao.count() > 0` gate).
- **480 tests** (475 → 480 via the 5 new repo-layer cases). Zero balance-math changes.
- **Known debt flagged for release:** `ensureSeedData` currently short-circuits when `dao.count() > 0`, so future content PRs (C.2 PR 3+) that add new seed rows won't land on already-seeded installs without a data clear. Acceptable for pre-release; must be replaced with per-cosmeticId upsert logic (or a DB migration) before v1.0.

### Phase C.4 — ClaimMilestone UnknownCosmetic detection (2026-05-08, RO-07 follow-up)
- **ClaimMilestone**: return type `Boolean` → `ClaimMilestoneResult` sealed class with four variants — `Success` (atomic credit ran), `InsufficientSteps` (step threshold unmet), `AlreadyClaimed` (atomic DAO returned false), `UnknownCosmetic(cosmeticId)` (one of the milestone's `MilestoneReward.Cosmetic` ids has no matching row in `SEED_COSMETICS`). Pre-flight check runs BEFORE the atomic DAO call so no partial credit when a cosmetic id is unknown. Constructor grew to 4 params (+`CosmeticRepository`).
- **CosmeticRepository**: +`suspend fun idExists(cosmeticId: String): Boolean` on the domain interface. Real impl lazy-seeds via `ensureSeedData()` then queries `observeAll().first().any { it.cosmeticId == cosmeticId }`; `FakeCosmeticRepository` checks its `items` StateFlow directly. KDoc on the interface documents the C.4 detection-only rationale and flags resolution as C.2 PR 3+ content work.
- **MissionsViewModel**: gained a Hilt-injected `CosmeticRepository` (7 constructor params). `claimMilestone(milestone)` now pattern-matches `ClaimMilestoneResult` and surfaces non-Success outcomes as user-visible snackbar messages via a new `userMessage: StateFlow<String?>` + `clearMessage()` method. The `combine()` grew from 4 to 5 flows (+userMessage). `MissionsUiState` gained `userMessage: String?` field with KDoc.
- **MissionsScreen**: wrapped in `Scaffold(snackbarHost = { SnackbarHost(…) })` with a `LaunchedEffect(state.userMessage)` that shows the snackbar and clears. First time Missions gets user feedback on failed claims (previously silent).
- **User-visible impact today:** the 3 currently-mismatched milestone cosmetic ids (`garden_ziggurat_skin` on MARATHON_WALKER, `lapis_lazuli_skin` on IRON_SOLES, `sandals_of_gilgamesh` on GLOBE_TROTTER) now surface as a snackbar ("Reward temporarily unavailable …") instead of silently dropping. Those 3 milestones cannot be claimed until C.2 PR 3+ adds matching seed rows; until then the claim rejects cleanly with zero partial credit.
- **ClaimMilestoneTest**: 8 → 12 cases (-1 merged + 5 new). Removed the old `credits Gems and Power Stones for IRON_SOLES` success-path case; coverage preserved by (a) new `UnknownCosmetic surfaces offending cosmetic id for IRON_SOLES` (default-state UnknownCosmetic rejection) + (b) new `milestone with matching cosmetic id credits rewards via atomic path` (seeds a `lapis_lazuli_skin` cosmetic fixture and shows the atomic credit runs cleanly, emulating post-C.2-PR-3 state). Renamed 3 cases for Result-type clarity. Switched the concurrent-claims race target from IRON_SOLES (unknown cosmetic) to MORNING_JOGGER (Gems-only) so the atomicity invariant being tested is independent of the cosmetic-id pre-flight check. 4 new C.4 cases: `UnknownCosmetic` x 3 (one per mismatched milestone, asserting the exact offending id), `UnknownCosmetic rejects claim before the atomic DAO call with no credit` (regression guard on the pre-flight ordering).
- **MissionsViewModelTest**: direct `ClaimMilestone` construction updated to 4-arg (+`FakeCosmeticRepository()`); asserts `ClaimMilestoneResult.Success` on the FIRST_STEPS claim path.
- **484 tests** (480 → 484 via +4 net). Zero balance-math changes.

### Fix — `ensureSeedData` per-cosmeticId filter (2026-05-08)
- **CosmeticRepositoryImpl.ensureSeedData**: replaced the all-or-nothing `if (dao.count() > 0) return` short-circuit with a per-`cosmeticId` filter. Reads existing ids once via `observeAll().first().mapTo(HashSet())`, computes `missing = SEED_COSMETICS.filter { it.cosmeticId !in existingIds }`, and `upsertAll(missing)` only when non-empty. Three behaviours:
  - **Fresh install:** `existingIds` empty → every SEED_COSMETICS row inserted. Identical to pre-fix behaviour.
  - **Partial-catalogue upgrade:** device already has the pre-`zig_jade` 7-row catalogue → only `zig_jade` inserted; 7 legacy rows untouched. Before the fix, this case was broken (count > 0 short-circuit skipped everything), so `zig_jade` never landed on already-installed devs without a data clear.
  - **Steady state:** all ids present → `missing` empty → no DAO write. Same as before, different mechanism.
- **Why the filter instead of a universal upsert:** `CosmeticEntity`'s primary key is `id` (auto-gen), not `cosmeticId`. Re-upserting a seed row with `id = 0` would insert a new auto-gen row alongside the existing one, not replace it. The explicit filter sidesteps that entirely by never handing already-present rows to the DAO.
- **Unblocks C.2 PR 3+** (the 3 milestone cosmetic seed rows that resolve the C.4 UnknownCosmetic detections): content PRs can now land on any install regardless of its catalogue history. Also removes the "data clear required" friction for any dev who installed a pre-C.2-PR-2 debug build.
- **Tests:** CosmeticRepositoryImplTest gained 2 regression-guard cases:
  - `ensureSeedData inserts newly-added rows on partial catalogue upgrade` — pre-seeds 7 legacy rows manually (no `zig_jade`), asserts `ensureSeedData` inserts `zig_jade` with its `ZIGGURAT_COLOR_LOOKUP` palette and leaves the 7 legacy rows intact.
  - `ensureSeedData preserves player state on existing rows (isOwned, isEquipped)` — pre-seeds `zig_jade` with `isOwned=true, isEquipped=true`, runs `ensureSeedData`, asserts the player state survives (never overwritten because the filter skips the row entirely).
  Existing idempotency test renamed (removed "count gate holds" phrase); end-state assertion unchanged because the filter produces the same steady-state behaviour via a different mechanism.
- **486 tests** (484 → 486). Zero balance-math changes.

### Phase C.2 PR 3 — Seed `lapis_lazuli_skin` (IRON_SOLES milestone reward) (2026-05-08)
- **CosmeticRepositoryImpl.SEED_COSMETICS**: +1 row — `CosmeticEntity(cosmeticId = "lapis_lazuli_skin", category = "ZIGGURAT_SKIN", name = "Lapis Lazuli Ziggurat Skin", description = "Deep lapis lazuli stone with pyrite-gold flecks", priceGems = 500)`. Placed second in the list (directly after `zig_jade`) so the two palette-shipping cosmetics appear grouped at the top of the Store section. Intentionally NOT added to `StoreScreen.ENABLED_COSMETIC_ID` — still shows "Coming Soon" in the Store; primary acquisition path is the IRON_SOLES milestone claim. Store pricing is a future UX decision. Total seed count: 8 → 9.
- **ZIGGURAT_COLOR_LOOKUP**: +1 entry — `"lapis_lazuli_skin"` maps to `[0xFF1A1F5C, 0xFF2A3880, 0xFF3B4FAB, 0xFF4F68C8, 0xFFD4A84A]` (bottom → top: deep lapis base → bright lapis → pyrite-gold crown). The gold crown is the traditional pyrite-fleck reference that distinguishes lapis lazuli from plain blue stone. Same 5-int / layer-ordered contract as the `zig_jade` palette (C.2 PR 2).
- **Resolves C.4 UnknownCosmetic for IRON_SOLES**: `CosmeticRepository.idExists("lapis_lazuli_skin")` now returns `true`, so `ClaimMilestone(Milestone.IRON_SOLES)` passes the pre-flight cosmetic-id check and runs the atomic credit (200 Gems + 50 Power Stones). Before this PR, IRON_SOLES claims returned `UnknownCosmetic("lapis_lazuli_skin")` and rejected cleanly; post-PR, they return `Success`.
- **ClaimMilestoneTest**: rewired 12 → 11 cases. Removed `UnknownCosmetic surfaces offending cosmetic id for IRON_SOLES` (prod semantics flipped — lapis_lazuli_skin is now seeded, IRON_SOLES no longer returns UnknownCosmetic). Switched `UnknownCosmetic rejects claim before the atomic DAO call with no credit` to target MARATHON_WALKER (garden_ziggurat_skin still unknown) so the test reflects prod behaviour. Rewrote the former `milestone with matching cosmetic id credits rewards via atomic path` as `IRON_SOLES claim succeeds end-to-end via real CosmeticRepositoryImpl` — uses `CosmeticRepositoryImpl(FakeCosmeticDao())` instead of a `FakeCosmeticRepository` fixture, proving the full `SEED_COSMETICS → ensureSeedData → idExists → ClaimMilestone atomic credit → wallet` chain on the real implementation.
- **CosmeticRepositoryImplTest**: updated 7 → 8 cases. +1 new `C2PR3 - lapis_lazuli_skin propagates lapis palette via overrideColors from ZIGGURAT_COLOR_LOOKUP` with exact-value palette assertion matching the `zig_jade` pattern (content-as-code contract). Updated `ensureSeedData is idempotent` count assertion 8 → 9. Updated `ensureSeedData inserts newly-added rows on partial catalogue upgrade` count 8 → 9 with a new lapis palette check alongside the existing jade one (proves both palettes land on the same upgrade path). Updated `ensureSeedData preserves player state on existing rows` count 8 → 9. Updated the `other seeded ziggurat cosmetics have null overrideColors` comment to reflect that both `zig_jade` (PR 2) and `lapis_lazuli_skin` (PR 3) now ship palettes.
- **486 tests, unchanged count** (-1 ClaimMilestoneTest removed case + 1 CosmeticRepositoryImplTest new lapis palette case = 0 net). Zero balance-math changes.
- **Next up (C.2 PR 3b / 3c):** `garden_ziggurat_skin` (MARATHON_WALKER, 600 Gems) and `sandals_of_gilgamesh` (GLOBE_TROTTER, 500 Gems). Each PR will flip one more `UnknownCosmetic` detection to `Success`. After all 3 land, all 6 Milestone entries are fully claimable end-to-end — closes the "shipped but disabled" monetization gap that has been tracked since Plan R2-11.

### Phase C.2 PR 3b + 3c — Seed remaining milestone cosmetics (MARATHON_WALKER + GLOBE_TROTTER) (2026-05-08)
- **CosmeticRepositoryImpl.SEED_COSMETICS**: +2 rows — `garden_ziggurat_skin` (ZIGGURAT_SKIN, 600 Gems, MARATHON_WALKER reward) and `sandals_of_gilgamesh` (ZIGGURAT_SKIN, 500 Gems, GLOBE_TROTTER reward). Total seed count: 9 → 11. Both placed directly after `lapis_lazuli_skin` so the 4 palette-shipping cosmetics appear as a block at the top of the catalogue. Neither is in `StoreScreen.ENABLED_COSMETIC_ID` — both are milestone-acquisition-only, still show "Coming Soon" in the Store.
- **ZIGGURAT_COLOR_LOOKUP**: +2 palettes.
  - `garden_ziggurat_skin`: `[0xFF8B4726, 0xFFAD7B4C, 0xFF5E7F47, 0xFF7BA85A, 0xFFE0C890]` — Hanging Gardens biome theme. Terracotta ziggurat base → sun-bleached sandstone → mossy vines → lush foliage → pale bloom canopy. Evokes the stone structure overtaken by cascading gardens.
  - `sandals_of_gilgamesh`: `[0xFF3B2A1A, 0xFF6B4A2A, 0xFF8B6B42, 0xFFB89152, 0xFFE8C068]` — weathered bronze → polished bronze → gold crown. Heroic motif; the gold crown echoes `lapis_lazuli_skin` as a shared "legendary" visual cue.
- **Category decision for `sandals_of_gilgamesh`:** the id carries footwear semantics ("walking the edges of the world") but the cosmetic is implemented as a `ZIGGURAT_SKIN` — a bronze Gilgamesh-themed ziggurat variant. Kept the existing category + pipeline intact (no new `CosmeticCategory` enum value, no schema change, no new rendering path). Description text ("Bronze ziggurat in honour of Gilgamesh, whose sandals walked the edges of the world") bridges the name-vs-implementation gap. Revisit a `PLAYER_AVATAR` category only if future milestones introduce multiple player-avatar cosmetics.
- **Resolves the remaining 2 C.4 UnknownCosmetic detections**: `ClaimMilestone(MARATHON_WALKER)` now returns `Success` (600 Gems credited); `ClaimMilestone(GLOBE_TROTTER)` now returns `Success` (500 Gems credited). Combined with C.2 PR 3 (IRON_SOLES), all 3 previously-mismatched milestone cosmetic ids are fixed. **All 6 Milestone entries now claim cleanly end-to-end.**
- **ClaimMilestoneTest**: 11 → 11 cases (net 0: -2 + 2). Removed both `UnknownCosmetic surfaces offending cosmetic id for MARATHON_WALKER` and `... for GLOBE_TROTTER` (prod semantics flipped to Success for both). Kept the `UnknownCosmetic rejects claim before the atomic DAO call with no credit` synthetic regression guard (still uses MARATHON_WALKER against the empty fake — no prod Milestone currently reaches this rejection path, but the guard protects against future content work introducing a new Milestone with an unseeded Cosmetic reward). Setup comment rewritten to reflect: no more prod mismatches. Added `MARATHON_WALKER claim succeeds end-to-end via real CosmeticRepositoryImpl` + `GLOBE_TROTTER claim succeeds end-to-end via real CosmeticRepositoryImpl` — same shape as the existing IRON_SOLES test. Together with the IRON_SOLES test, every Milestone with a Cosmetic reward has a dedicated end-to-end success test.
- **CosmeticRepositoryImplTest**: 8 → 10 cases (+2). Added `C2PR3b - garden_ziggurat_skin propagates hanging-gardens palette` + `C2PR3c - sandals_of_gilgamesh propagates bronze-ziggurat palette`, each with exact-value palette assertion matching the `zig_jade` / `lapis_lazuli_skin` pattern (content-as-code contract). Updated all 3 count assertions (9 → 11): idempotency, partial-catalogue upgrade (now verifies all 4 palette-shipping cosmetics land correctly on the same upgrade path), existing-row preservation. Updated the `other seeded ziggurat cosmetics have null overrideColors` comment to list all 4 palette cosmetics.
- **488 tests** (486 → 488 via +2 net). Zero balance-math changes.
- **Monetization gap closed.** The RO-07 "shipped but disabled" cosmetic gap that has been tracked since Plan R2-11 is fully resolved: renderer pipeline live (C.2 PR 1), first store cosmetic live (C.2 PR 2 `zig_jade`), all 3 milestone cosmetics live (C.2 PR 3 / 3b / 3c), UnknownCosmetic detection still guards against regressions (C.4), `ensureSeedData` lands new rows on any install (fix). Players who hit IRON_SOLES / MARATHON_WALKER / GLOBE_TROTTER now get their full rewards atomically.

### Current state
- **531 JVM tests** green (412 baseline → 488 after C.2 PR 3b+3c → 510 after C.5 PR 1 → 514 after C.5 PR 2 → 522 after C.6 PR 1 → 526 after C.6 PR 2 → 531 after the battle-step-credit hotfix). Zero balance-math changes across all of Phase B, Phase C, and the hotfix.
- Plan 31 (Play Console & Store Publication) remains the only release-blocker; unblocked since the end of Plan R2.
- **RO-02 complete: 5/5 atomic sites landed** (`PurchaseUpgrade`, `AwardBattleSteps`, `StepCrossValidator`, `ClaimMilestone`, `runEndRoundPersistence`).
- **RO-03 complete: 2/2 resilience sites landed** (extraction + `onCleared` guard).
- **RO-07 complete for the milestone-cosmetic gap: C.2 PRs 1+2+3+3b+3c + C.4 + ensureSeedData fix landed.** All 6 Milestone entries claim cleanly end-to-end. Only 3 of 7 seeded ziggurat skins ship palettes (the 3 milestone rewards + `zig_jade`); the 3 original placeholder skins (`zig_obsidian`, `zig_crystal`, `zig_golden`) + 4 non-ziggurat seeds (`proj_*`, `enemy_*`) remain "Coming Soon" in the Store until their visual content is designed.
- Real Billing/Ad SDK swaps (Phase C.5/C.6) still gated on ADR-0005/ADR-0006 — now the top release-critical item.
- B.4 (FollowOnPipeline extraction) + B.5 (UpdateMissionProgress use case) remain as pure debt, not release blockers.

## [1.0.0] — 2026-03-10

### Core Gameplay
- Step-powered progression: earn Steps currency by real-world walking via device step counter
- Workshop with 23 permanent upgrade types across Attack, Defense, and Utility categories
- Tower defense battle system with custom SurfaceView renderer and fixed-timestep game loop
- 6 enemy types (Basic, Fast, Tank, Ranged, Boss, Scatter) with wave-based spawning
- Stats resolution engine combining Workshop (permanent) × In-Round (temporary) upgrades multiplicatively
- In-round upgrades purchased with Cash earned from kills, with interest mechanic
- Crit system, knockback, lifesteal, thorn damage, death defy, damage/meter bonus
- Advanced combat: orbiting projectiles, multishot, bounce shot

### Progression
- 10 tier system with wave-based unlock requirements and escalating battle conditions (Tier 6+)
- 5 narrative biomes: Hanging Gardens, Burning Sands, Frozen Ziggurats, Underworld of Kur, Celestial Gate
- Labs research system with 10 research types, real-time background timers, up to 4 slots, Gem rush
- Cards system with 9 card types, 3 rarities, Card Dust upgrades, loadout of 3
- 6 Ultimate Weapons unlocked with Power Stones, loadout of 3, cooldown-based activation
- 4 Step Overdrive types for mid-battle 60-second combat buffs

### Economy & Rewards
- Walking Encounters with seeded random Supply Drops delivered via push notification
- Weekly step challenges with Power Stone rewards (50k/75k/100k thresholds)
- Daily login streaks with Gem and Power Stone rewards
- 6 walking milestones from First Steps to Globe Trotter
- 3 random daily missions refreshed at midnight (walking/battle/upgrade categories)
- Wave milestone Power Stone awards on personal-best waves

### Battle Polish
- Particle effects: projectile trails, enemy death bursts (6 types), UW activation spectacles, overdrive auras
- Screen shake with decaying amplitude
- Wave announcements with boss warnings and cooldown countdowns
- Floating text for cash pickups
- Biome-themed color palettes and ambient background particles
- Sound effects (7 types) with volume control and shoot throttling
- Speed controls: 1x / 2x / 4x

### Infrastructure
- Foreground step-counting service (health type, START_STICKY) with boot receiver
- WorkManager 15-minute periodic sync with Health Connect cross-validation and gap-filling
- Activity Minute Parity: indoor workout minutes converted to step-equivalents
- Anti-cheat: 200 steps/min rate limit, step velocity analysis, 50k daily ceiling, graduated Health Connect cross-validation (4 offense levels)
- SQLCipher encrypted Room database with Android Keystore key management
- Home screen widget (2×2) with step count display
- Smart upgrade proximity reminders
- Milestone and wave record notifications

### Monetization (Stub)
- Store screen with Gem packs, ad removal, Season Pass, and cosmetic items
- Stub billing and reward ad implementations (real SDK integration in future update)

### Stats & UI
- Stats screen with walking history bar charts (daily/weekly/monthly), battle stats, all-time aggregates
- Currency dashboard with weekly challenge progress and login streak tracking
- Missions screen with daily missions and walking milestones
- Settings screen with 4 notification toggles
- 12-screen Compose navigation with bottom nav bar

### Testing
- 397 JVM unit tests covering all use cases, domain models, balance validation, ViewModels, anti-cheat, effects, step ingestion coordination, widget balance, walking mission progress, activity-minute idempotency, currency guards, UX feedback, and integration tests

### Remediation (R01–R05)
- Fixed step double-crediting between StepCounterService and StepSyncWorker via heartbeat + Room baseline coordination
- Fixed Health Connect escrow to actually deduct suspicious steps from player balance
- Fixed battle engine receiving empty workshop utility levels (CASH_BONUS/CASH_PER_WAVE/INTEREST)
- Hidden unimplemented STEP_MULTIPLIER and RECOVERY_PACKAGES from Workshop UI
- Disabled backup, added SQLCipher key recovery on keystore mismatch

### Remediation (R06–R09)
- Fixed widget showing 0 balance — now displays real step balance after crediting
- Fixed widget click target not responding (missing android:id on root layout)
- Walking missions now update live on step credit, not only when screen opens
- Fixed notification settings label to accurately describe toggle behavior
- lastActiveAt now updated on app resume for smart reminder accuracy
- Fixed deep-link navigation when app is already open (warm-start intent handling)
- Fixed Season Pass expiry check in Store screen (was ignoring expiry timestamp)
- Fixed adRemoved state lost on Play Again in battle

### Remediation (R10–R11)
- Added user feedback messages (snackbar) for failed purchases across Workshop, Cards, Labs, Store
- Added double-tap guards on all purchase/ad actions — prevents overlapping coroutines
- Added DAO-level non-negative guards on gems, power stones, and card dust (MAX(0, ...))
- Fixed midnight date staleness in Missions, Home, and Stats screens
- Added content descriptions to all symbol-only battle controls for TalkBack accessibility
- Added semantics to Ultimate Weapon bar slots
- Replaced placeholder contact emails with real address in privacy policy, store listing, and Health Connect activity
- Fixed README instrumented test reference (deferred, not available)

### Remediation (R12)
- Added Robolectric integration tests for widget SharedPreferences round-trip
- Added deep-link intent routing tests
- Added Room v7 schema round-trip tests (PlayerProfile, DailyStepRecord, WorkshopUpgrade)
- Added end-to-end escrow lifecycle integration tests (escrow→release and escrow→discard)

### Release Prep
- R8/ProGuard rules hardened for Room, Hilt, SQLCipher, Health Connect, sensors, WorkManager
- Release signing configuration with gitignored keystore.properties
- Privacy policy and Play Store listing text

### Scaffold & Foundation
- Gradle 9.3.1 project with Kotlin DSL and version catalog
- Hilt DI setup with `@HiltAndroidApp`
- Room database skeleton, Compose theme, single Activity
- Written detailed plan files for Plans 02–30 in `docs/plans/`
- All core domain models (Plan 01): Currency, PlayerWallet, UpgradeType (23), TierConfig (1–10), BattleCondition (7), Biome (5), EnemyType (6), UltimateWeaponType (6), OverdriveType (4), ResearchType (10), CardType (9), CardRarity (3)
- CalculateUpgradeCost and CanAffordUpgrade use cases
