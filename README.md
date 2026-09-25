# Car X BYD — Android Alpha 1

Native Android proof-of-product for a standalone **Car X BYD Cloud** app.

## What is already implemented

- Arabic/RTL Car X black/gold UI.
- BYD region selector with Jordan as the default.
- Android Keystore AES-GCM protection for locally stored username/password/control PIN.
- Native BYD login request compatible with the public `pyBYD` protocol:
  - AES-CBC inner payload encryption.
  - MD5-derived login/session keys.
  - BYD SHA1 mixed-case request signing.
  - BYD checkcode generation.
  - Bangcle white-box AES envelope codec ported to Java.
- First-use download of `bangcle_tables.bin` from a pinned `pyBYD` commit, cached in app-private storage.
- Session handling (`userId`, `signToken`, `encryToken`) with 12h local TTL and auto re-login on known session-expiry codes.
- Vehicle list fetch from `/app/account/getAllListByUserId`.
- Realtime trigger/poll from BYD Cloud.
- Control PIN verification endpoint.
- Remote commands with trigger + result polling:
  - lock / unlock
  - flash lights
  - find car
  - open / close trunk
  - climate on / off
- Confirmation dialogs before sensitive actions.

## Current status

This is **Alpha 1**. The protocol implementation is based on the open-source MIT projects listed below, but it has not been validated in this environment against a real BYD account/vehicle. BYD can change private cloud APIs without notice, and vehicle/region capabilities vary.

The app deliberately does **not** bypass BYD account ownership, control PIN checks, or vehicle permissions.

## Build

Open the project in Android Studio and build `app`.

Or push the project to GitHub and run the included workflow **Build Car X BYD APK**. It produces `app-debug.apk` as an Actions artifact.

Configuration:
- minSdk: 26
- targetSdk: 35
- Java: 17
- no AndroidX or third-party runtime dependencies

## Important security notes

- Account password and control PIN are encrypted at rest using a key generated in `AndroidKeyStore`.
- Session tokens are held in memory only in Alpha 1.
- The first-use Bangcle table download is pinned to a specific upstream commit instead of `main`.
- For a production release, bundle and checksum the Bangcle table asset inside the APK, add certificate pinning/stronger network hardening, and perform full on-car testing.

## Credits / license

Protocol behavior and the Bangcle algorithm were studied/ported from:

- `jkaberg/pyBYD` — MIT
- `jkaberg/hass-byd-vehicle` — MIT

See `THIRD_PARTY_NOTICES.md`.

Car X application code in this project is a new Android implementation prepared for Car X.
