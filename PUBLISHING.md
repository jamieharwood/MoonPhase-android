# Publishing Orbitly on Google Play

The phone app and the watch app are one Play listing: both use the package name
`io.github.jamieharwood.moonphase`. You upload two app bundles (`.aab`) to the same app,
the phone one to the phone tracks and the watch one to the Wear OS tracks.

| | Phone (`:app`) | Watch (`:wear`) |
|---|---|---|
| Target SDK | 36 (Android 16) | 35 (Play's minimum for Wear OS) |
| Min SDK | 26 (Android 8.0) | 30 (Wear OS 3) |
| versionCode | 1, 2, 3 … | 100001, 100002 … (must never clash with the phone's) |

Bump `versionCode` (and `versionName`) in `app/build.gradle.kts` or `wear/build.gradle.kts`
every time you upload a new bundle for that device.

## 1. Create your upload key (once)

```bash
./tools/create-upload-key.sh
```

It asks for a password and your name, then creates `~/keys/moonphase-upload.jks` and
`keystore.properties` in the repo root. Both are git-ignored. **Back up the `.jks` file and the
password** in a password manager and offline. With Play App Signing (the default), Google holds
the real app signing key; if you lose the upload key you can ask Play support to reset it, but
it takes days.

Without `keystore.properties` the release build falls back to the debug key, which Play
rejects, so check that file exists before building for upload.

## 2. Build the bundles

```bash
./gradlew :app:bundleRelease :wear:bundleRelease
```

Upload these:

- `app/build/outputs/bundle/release/app-release.aab`: phone
- `wear/build/outputs/bundle/release/wear-release.aab`: watch

## 3. Set up the app in Play Console

1. **Create app**: name *Orbitly*, App, Free, accept the declarations.
2. **App content** (Policy → App content):
   - **Privacy policy URL**: `https://github.com/jamieharwood/MoonPhase-android/blob/main/PRIVACY.md`
   - **Ads**: No ads.
   - **App access**: All functionality is available without special access.
   - **Content rating**: fill in the IARC questionnaire. Category *Reference, news or educational*;
     answer No to everything (no violence, user content, purchases, location sharing…).
     Expected result: Everyone / PEGI 3.
   - **Target audience**: 13+ (choosing under-13 brings in the Families policy).
   - **Data safety** (see below).
   - **Government apps / Financial features / Health**: No.
3. **Store listing**: copy the text and images from `fastlane/metadata/android/en-GB/`:
   - App name: `title.txt` (30 characters max)
   - Short description: `short_description.txt` (80 max)
   - Full description: `full_description.txt`
   - App icon: `images/icon.png` (512 × 512)
   - Feature graphic: `images/featureGraphic.png` (1024 × 500)
   - Phone screenshots: **at least 2 needed, put them in `images/phoneScreenshots/`** (take them
     on your phone: power + volume down). 9:16 portrait works well.
   - Wear OS screenshots: `images/wearScreenshots/` (480 × 480 from a Galaxy Watch; Play wants
     1:1, at least 384 × 384, no watch frame)
   - Category: *Education* (or *Weather*). Contact email: required, shown publicly.
4. **Wear OS**: Advanced settings → Form factors → **Add form factor → Wear OS**, opt in, add the
   Wear OS screenshots, and upload `wear-release.aab` to the Wear OS track. Google reviews
   Wear OS apps against the Wear OS quality guidelines separately from the phone app.

### Data safety answers

- Does your app collect or share any of the required user data types? **No.**
  - The location is typed in by the user and stays on their devices; the phone → watch sync
    uses the Wearable Data Layer between the user's own devices.
  - The live feeds (NOAA, TLE API, people-in-space JSON) are downloads that send no user data.
  - Awtrix push goes to a user-entered device on their own network.
- Is all data encrypted in transit? The live feeds use HTTPS. The optional Awtrix push uses plain
  HTTP on the local network, so answer per what Play asks; with "no data collected" this section
  is not shown.
- Can users request deletion? Not applicable (nothing collected); uninstalling removes all data.

### Orbitly Pro (in-app product)

The app is free with a one-time **Orbitly Pro** unlock (£2.99). The code expects an in-app
product with the ID **`orbitly_pro`**.

1. **Payments profile**: Play Console → Settings → Payments profile. Create or link one (it asks
   for your bank account for payouts, and Google collects and pays UK/EU VAT for you). Products
   can't be created until this is done.
2. **Upload a bundle first**: the Monetise → Products → In-app products page unlocks only after
   a build that includes Play Billing (any `app-release.aab` from this branch on) has been uploaded
   to a testing track.
3. **Create the product**: Monetise with Play → Products → In-app products → Create product.
   - Product ID: `orbitly_pro` (this can never be changed or reused)
   - Name: *Orbitly Pro*
   - Description: *Widgets, sky calendar, watch complications and Tile, night mode and history charts.*
   - Price: **£2.99**, then *Update exchange rates* to fill in the other countries.
   - Activate it.
4. **Licence testers**: Settings → License testing → add your own Google account and your
   testers. They see "Test card, always approves" at checkout and are never charged, which is how
   you check the purchase and *Restore purchase* before release.
5. **Store listing**: the description already has an *ORBITLY PRO* section; Play adds the
   "In-app purchases" label to the listing automatically.

How it works in the app:

- The purchase is a non-consumable one-time product. The app acknowledges it straight away
  (Play refunds unacknowledged purchases after three days) and re-checks it with Play every time
  the app starts, so refunds lock Pro again.
- Free: dashboard (Moon, Sun, planets, probes, Earth, events, satellites, people in space), basic
  watch app and the basic Moon complication, aurora notifications.
- Pro: the three home-screen widgets, the meteor/eclipse calendar, the 12 astronomy complications
  and the Tile on the watch, night mode, history charts, populate and CSV export. History
  snapshots keep being recorded without Pro, so the charts are full when someone unlocks.
- The watch learns about Pro from the phone over the Data Layer. People who use the watch app
  without the Android phone app can't unlock Pro.
- **Debug builds** have a *Debug: unlock Pro without Play* button in the Pro dialog, because
  sideloaded builds can't reach Play Billing. Release builds don't show it.

### Data safety and Pro

The app doesn't collect purchase data itself: it asks Play whether the account owns the product
and stores an "unlocked" flag on the device. Payment information is handled by Google Play.
Keep answering **No** to "Does your app collect or share any of the required user data types?",
but read Play's current help text for "Purchase history" when you fill the form in, as Google
changes that wording from time to time.

## 4. Testing before production

**New personal developer accounts** must run a **closed test with at least 12 testers who stay
opted in for 14 days** before Play lets you apply for production access. Plan for it:

1. Testing → Closed testing → create a track, add a Google Group or email list of 12+ testers.
2. Upload both bundles to that track and roll out.
3. Testers join through the opt-in link and install from Play (phone and watch).
4. After 14 days, Dashboard → **Apply for production** and answer the questions about the test.

Organisation accounts can skip this and go straight to production, but internal testing
(up to 100 testers, available within minutes) is still the quickest way to check the Play build.

## Before each release

- [ ] `./gradlew testDebugUnitTest lintRelease` passes
- [ ] versionCode bumped in the module(s) you are uploading
- [ ] Add `fastlane/metadata/android/en-GB/changelogs/<versionCode>.txt` (500 characters max)
      and paste it into "Release notes"
- [ ] Phone app installed from the Play test track still syncs location to the watch app from
      the Play test track (both are re-signed by Google with the same key)

`store-src/make_store_graphics.py` regenerates the icon and feature graphic.

## The watch face (a separate Play app)

Google Play requires a Watch Face Format watch face to be its **own app**, separate from the
app that contains code. Create a second app in Play Console, *Orbitly Mission watch face*:

1. Build: `./gradlew :watchface:bundleRelease` →
   `watchface/build/outputs/bundle/release/watchface-release.aab` (signed with the same upload key).
2. Create app → Advanced settings → Form factors → add **Wear OS**, and upload the bundle to a
   Wear OS testing track. The closed-testing rule for new personal accounts applies to this app too.
3. Listing: screenshots in `watchface/store/` (active and always-on), and
   `watchface/src/main/res/drawable-nodpi/preview.png` as a starting point for the icon.
   Make it **free**. Say in the description that the time, date and Moon are free, and that the
   five astronomy complications need the Orbitly app with Orbitly Pro (without Pro those slots
   stay empty).
4. Content rating, data safety ("no data collected") and privacy policy as for the main app.
5. Don't use the SpaceX name or logo in the listing: Play rejects listings that use
   another company's trademarks without permission.

The watch app's astronomy complications ship in `wear-release.aab`. Upload a new version of
it (bump `versionCode` in `wear/build.gradle.kts`) at the same time.
