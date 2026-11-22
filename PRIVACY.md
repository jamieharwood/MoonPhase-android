# Orbitly privacy policy

*Last updated: 21 November 2026*

Orbitly (the Android phone app and the Wear OS watch app, package
`io.github.jamieharwood.moonphase`) is an open-source project. This policy explains what
the app does with information. In short: **it does not collect, sell or share any personal data,
and it has no accounts, ads or analytics.**

## Information stored on your device

- **Location.** You can type a latitude and longitude into Settings so sunrise and sunset are
  calculated for where you are. The app does not use your device's location service. These
  numbers are stored only on your phone and, if you have the watch app, on your watch.
- **Settings and history.** Your settings (units, notifications, history options, Awtrix address)
  and the astronomy snapshots used for the history chart are stored in the app's private storage
  on your device. Uninstalling the app deletes them.
- **CSV export.** If you export history, the file is saved to the location you choose.

## Network requests

To show live data the app downloads public information from these services. The requests do not
include your location or any identifier; like any web request, they reveal your IP address to the
service you connect to.

| Data | Service |
|---|---|
| Aurora Kp index | NOAA Space Weather Prediction Center (`services.swpc.noaa.gov`) |
| Satellite orbits and counts | TLE API (`tle.ivanstanojevic.me`) |
| People in space | `corquaid.github.io` (GitHub Pages) |

If you turn on the **Awtrix** option, the app sends astronomy values to the Awtrix clock address
you enter, which is normally a device on your own home network.

## Phone ↔ watch

The phone app sends your location and unit choice to the Orbitly watch app using Google Play
services' Wearable Data Layer, so the watch can calculate sunrise and sunset for the same place.
This data goes only to your own paired watch.

## Notifications

If you allow notifications, the phone app shows local alerts for aurora storms and the full moon.
They are generated on the device; no push service is used.

## Children

The app is not directed at children and does not knowingly collect information from anyone.

## Changes and contact

Changes to this policy will be published at this address with a new date.
Questions: open an issue at <https://github.com/jamieharwood/MoonPhase-android/issues>.
