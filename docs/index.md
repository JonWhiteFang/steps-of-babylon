<!-- GitHub Pages root (published at https://jonwhitefang.github.io/steps-of-babylon/). This is the
     hosted privacy policy, intentionally kept in sync with docs/release/privacy-policy.md (the
     canonical source — identical policy body; this file adds only this GitHub-Pages header). It
     is NOT a documentation index despite the filename;
     do not repurpose without updating the Play Console data-safety URL + README links. -->
# Privacy Policy — Steps of Babylon

**Effective Date:** June 18, 2026

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
If you make in-app purchases, transactions are processed by **Google Play Billing**. Google handles your payment information; we never receive or store your payment details. We store a local record of purchased items on your device to unlock the corresponding in-game content.

### Advertising Identifier (Google AdMob)
The app integrates **Google AdMob** to show optional, opt-in reward advertisements (for example, watch an ad to earn extra Gems or Power Stones — ads are never forced or interruptive). To serve these ads, Google's advertising SDK collects your device's **advertising ID** and related ad-service identifiers, and may collect device and usage information as described in Google's policies. This data is collected and transmitted **by Google**, not by us.

The first time advertising is relevant, the app shows a **Google User Messaging Platform (UMP) consent prompt** that governs ad personalisation in line with your choice and your region's requirements.

## How Your Data Is Stored

Your game data — including step counts, Health Connect readings, player progress, and purchase records — is stored **locally on your device only** in an encrypted database (SQLCipher with Android Keystore-managed encryption keys).

Steps of Babylon has **no server backend operated by us**, and we do not upload your game, step, or health data to any server. The exception is the data collected directly by the Google SDKs described under "Third-Party Services" below, which Google transmits to its own services.

## Third-Party Services

Steps of Babylon integrates the following Google services, each governed by its own privacy policy:

- **Google Play Billing** — processes in-app purchases ([Google Privacy Policy](https://policies.google.com/privacy))
- **Google AdMob** — serves optional reward advertisements and, with the User Messaging Platform (UMP), manages ad-consent ([Google Ads Privacy](https://policies.google.com/technologies/ads))

These services collect and process data as described in their respective privacy policies. We do not control and are not responsible for Google's data practices.

## Data Sharing

We do **not** sell or trade your personal data, and we do not ourselves share your game, step, or health data with third parties. The Google SDKs above collect and transmit data (including the **advertising ID** for AdMob) directly to Google for the purposes described in their policies.

## Data Retention

Your game data persists on your device until you uninstall the app or clear the app's data through your device settings. We have no ability to access or delete your on-device data remotely, as it exists only on your device. Data collected directly by Google (such as the AdMob advertising ID) is retained and controlled by Google under its own policies; you can reset or limit your advertising ID in your device's **Settings → Google → Ads**.

<a name="delete-data"></a>
## Data Deletion

The game data Steps of Babylon stores is held **locally on your device only**. We have no servers and cannot access or delete that data remotely.

To delete your on-device data, either:

- **In-app:** open **Settings → Delete All Data** inside Steps of Babylon, or
- **From the system:**
  1. Open your device **Settings**.
  2. Go to **Apps** (or **Application Manager**).
  3. Select **Steps of Babylon**.
  4. Tap **Storage** → **Clear Data**.

This permanently deletes all game progress, step history, purchase records, and any other data the app stores on your device.

**Types of data deleted:** step count history, player profile and progress, workshop upgrades, purchase receipts, and all other game state stored locally.

**Retention after deletion:** none of the app's local data is retained — it is removed immediately from your device, and we hold no copies. Data collected directly by Google (such as the AdMob advertising ID) is not stored by the app and is governed by Google; reset or limit it via your device's **Settings → Google → Ads**.

## Children's Privacy

Steps of Babylon is not directed to children under 13 and does not knowingly collect personal information from them. The app requires no account creation, and we do not ourselves collect identifying information. Note that the optional reward-ads feature uses Google AdMob, which collects an advertising identifier as described in "Advertising Identifier" above; you can reset or limit it via your device's **Settings → Google → Ads**.

## Changes to This Policy

We may update this privacy policy from time to time. Changes will be posted on this page with an updated effective date.

## Contact Us

If you have questions about this privacy policy, contact us at:

**Email:** jonwhitefang@gmail.com
