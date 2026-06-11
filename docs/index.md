<!-- GitHub Pages root (published at https://jonwhitefang.github.io/steps-of-babylon/). This is the
     hosted privacy policy, intentionally byte-identical to docs/release/privacy-policy.md (the
     canonical source — keep them in sync). It is NOT a documentation index despite the filename;
     do not repurpose without updating the Play Console data-safety URL + README links. -->
# Privacy Policy — Steps of Babylon

**Effective Date:** March 10, 2026

Whitefang Games ("we", "us", "our") built Steps of Babylon as a free-to-play Android game. This page informs you of our policies regarding the collection, use, and disclosure of information when you use our app.

## Information We Collect

### Step Count Data
Steps of Babylon reads your device's built-in step counter sensor (`TYPE_STEP_COUNTER`) to track your daily walking activity. This data powers all in-game progression.

### Health Connect Data
With your explicit permission, the app reads the following from Android Health Connect:
- **Step count records** (`READ_STEPS`) — used to cross-validate sensor readings and recover missed steps when the app is closed
- **Exercise session records** (`READ_EXERCISE`) — used for Activity Minute Parity, which converts indoor workout minutes into step-equivalent credits

Health Connect data is accessed only for gameplay validation. You can revoke these permissions at any time through your device's Health Connect settings.

### Purchase Data
If you make in-app purchases, transaction records are handled by Google Play Billing. We store a local record of purchased items to unlock the corresponding in-game content.

## How Your Data Is Stored

All game data — including step counts, player progress, and purchase records — is stored **locally on your device only** in an encrypted database (SQLCipher with Android Keystore-managed encryption keys).

Steps of Babylon v1.0 has **no server backend**. Your data is never uploaded to any remote server operated by us.

## Third-Party Services

Future versions of the app may integrate the following third-party services, each with their own privacy policies:

- **Google Play Billing** — for in-app purchases ([Google Privacy Policy](https://policies.google.com/privacy))
- **Google AdMob** — for optional reward advertisements ([Google Ads Privacy](https://policies.google.com/technologies/ads))

When these services are integrated, they may collect data as described in their respective privacy policies.

## Data Sharing

We do **not** sell, trade, or share your personal data with third parties, except as required by the third-party SDKs listed above when they are integrated.

## Data Retention

Your game data persists on your device until you uninstall the app or clear the app's data through your device settings. We have no ability to access or delete your data remotely, as it exists only on your device.

<a name="delete-data"></a>
## Data Deletion

All data collected by Steps of Babylon is stored **locally on your device only**. We have no servers and cannot access or delete your data remotely.

To delete your data:

1. Open your device **Settings**.
2. Go to **Apps** (or **Application Manager**).
3. Select **Steps of Babylon**.
4. Tap **Storage** → **Clear Data**.

This permanently deletes all game progress, step history, purchase records, and any other data stored by the app.

**Types of data deleted:** step count history, player profile and progress, workshop upgrades, purchase receipts, all other game state.

**Retention after deletion:** none — all data is removed immediately from your device. We retain no copies.

## Children's Privacy

Steps of Babylon does not knowingly collect personal information from children under 13. The app does not require account creation or collect identifying information.

## Changes to This Policy

We may update this privacy policy from time to time. Changes will be posted on this page with an updated effective date.

## Contact Us

If you have questions about this privacy policy, contact us at:

**Email:** jonwhitefang@gmail.com
