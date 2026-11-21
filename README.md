# MoonPhase for Android

A native Android app (Kotlin + Jetpack Compose, Material 3) ported from the
[MoonPhase](https://github.com/jamieharwood/MoonPhase) Spring Boot dashboard and its
[Ubuntu desktop port](https://github.com/jamieharwood/MoonPhase-uBuntu).
It shows the same astronomy data, computed on the phone.

## Features

| Card | Contents |
|---|---|
| 🌙 **Moon Phase** | Phase name, an animated moon with a live terminator and twinkling stars (or the original ASCII art), illumination %, age, days to full moon, Earth–Moon distance |
| ☀️ **Solar System** | Mercury → Pluto distance from the Sun with light travel time, plus Earth→Mars/Jupiter/Saturn |
| 🚀 **Deep Space Probes** | Hubble, James Webb, Voyager 1 & 2, New Horizons, light time to the Voyagers |
| 🌍 **Earth** | Orbital speed, daylight hours, sunrise/sunset (UTC), axial tilt, NOAA aurora Kp index (shown in red during a storm) |
| 📅 **Upcoming Events** | Full moon, solstices, perihelion and aphelion, sorted by days remaining |
| 🛰️ **LEO** | ISS / Tiangong / Hubble altitudes from live TLEs, Starlink and Kuiper counts, total objects in orbit, ISS crew |
| 👨‍🚀 **People in Space** | Number of people on each spacecraft (★ = docked to ISS) |
| 📊 **Historical Data** | Chart of any of the 35 stored metrics over 7 days to all time, CSV export, *Populate past* backfill, reset |

Other features:

- **Units**: AU, km or miles (the toolbar button).
- **Refresh**: pull down on the dashboard or tap ⟳. Calculated values update every minute.
  Live feeds are cached (Kp for 15 minutes, LEO and crew for 6 hours).
- **Background refresh**: a WorkManager job runs about every 6 hours when online.
  It saves history (at most one snapshot per 12-hour UTC window, as in the original),
  sends notifications for aurora storms (Kp ≥ 5) and the full moon tonight or tomorrow, and pushes to Awtrix if enabled.
- **Settings**: location, history on/off and retention, notifications, Awtrix clock push.
- Phones and tablets: cards reflow into columns on wide screens. Uses Material You colours on Android 12 and later.

Requires Android 8.0 (API 26) or later.

## Build and install

You need the Android SDK (platform 35, build-tools 35), from Android Studio or the
command-line tools. The build compiles with a **JDK 21** Gradle toolchain. Gradle finds
any installed JDK 21, including IntelliJ's `~/.jdks`, and downloads one automatically
if none is found. Your default `java` can be just a runtime (for example Ubuntu's
`openjdk-21-jre`).

```bash
git clone https://github.com/jamieharwood/MoonPhase-android.git
cd MoonPhase-android
echo "sdk.dir=$HOME/Android/Sdk" > local.properties   # or set ANDROID_HOME

./gradlew testDebugUnitTest     # JVM unit tests for the calculations and parsers
./gradlew assembleDebug         # → app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease       # → app/build/outputs/apk/release/app-release.apk (minified)
```

Install on a phone with USB debugging enabled:

```bash
adb install -r app/build/outputs/apk/release/app-release.apk
```

Or copy the APK to the phone and open it (allow *Install unknown apps* for your file manager when prompted).

> The release build is signed with the local **debug** key so it installs straight away.
> Before publishing to Google Play, create your own keystore and set a `signingConfig`
> in `app/build.gradle.kts`.

## Project layout

```
app/src/main/java/io/github/jamieharwood/moonphase/
  Astro.kt              deterministic calculations (ported from MoonPhaseModule/*.java)
  MainActivity.kt       Compose activity, toolbar, dialogs, theme
  MainViewModel.kt      state, refresh, history, CSV export
  data/LiveFetcher.kt   NOAA Kp, TLE API, people-in-space feeds (cached; TLE requests one at a time)
  data/History.kt       SQLite history store (original SnapshotDocument field names), CSV, backfill
  data/Background.kt    notifications and the periodic WorkManager refresh
  data/Awtrix.kt        optional Awtrix clock push
  data/Settings.kt      SharedPreferences settings
  ui/Dashboard.kt       the cards
  ui/Graphics.kt        Canvas-drawn moon and history chart
  ui/SettingsScreen.kt  settings
app/src/test/…/AstroTest.kt  unit tests
```

## Differences from the web app

- No server, REST API or MongoDB connection. History is kept on the device in SQLite,
  using the same metric names, and can be exported as CSV.
- The Claude moon-phase cross-check is not included. The calculated phase was always the one displayed anyway.
- Cleartext HTTP is allowed only so a plain-HTTP Awtrix clock on the local network can be reached.
  Every data feed uses HTTPS.
