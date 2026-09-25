# Car X BYD — Alpha 4.1

Android prototype for BYD China Cloud with an Arabic-first Car X interface.

## Alpha 4.1 highlights

- Keeps the BYD China login/WBSK engine that was validated against a mainland BYD account.
- Red/black premium theme is the default.
- Built-in theme selector with four themes:
  - Red Sport
  - Black Gold
  - Midnight Blue
  - Luxury White
- Arabic vehicle-name mapping, including `海鸥荣耀版` → `سيجل – الفئة الفاخرة`.
- Vehicle hero artwork can use a model image returned by BYD when an HTTPS image URL is available; otherwise it falls back to the selected theme artwork.
- Built-in QR camera scanner for the vehicle-screen login flow.
- QR flow targets the mainland BYD scan-login endpoints found in the working BYD 9.16.1 app:
  - `/user/scanlogin/scanLoginByAuth`
  - `/user/scanlogin/scanLoginByAction`
  - `/user/scanlogin/scanLoginCancel`
- The QR server payload is still considered **beta** until tested against a live car QR. Do not share active QR values outside the device; they may contain temporary authentication material.
- Entry for proximity unlock/lock-away is present in the UI, but automatic Bluetooth Digital Key behavior is deliberately not enabled until the vehicle-specific BLE protocol is verified.

## Security

Account credentials are stored locally using Android Keystore-backed encrypted preferences. Remote vehicle actions retain confirmation prompts for sensitive commands.

## Build

GitHub Actions builds the debug APK on pushes to `main`. The artifact name is `CarX-BYD-Alpha4-QR-Themes`.

## Important

This is an independent interoperability project and is not an official BYD application. BYD trademarks and vehicle imagery belong to their respective owners.


## Alpha 4.1 fixes

- Vehicle cloud state is no longer hard-coded to connected.
- The hero chip now means account session is logged in, not vehicle online state.
- Realtime refresh updates vehicle connection state dynamically when BYD returns it.
- QR scanning now runs inline in Car X instead of launching ZXing CaptureActivity, avoiding the immediate scanner crash seen on the test phone.
- Camera permission is requested explicitly before QR scanning.
