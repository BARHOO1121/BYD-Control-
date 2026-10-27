# Car X BYD — Alpha 4.2

Android prototype for BYD China Cloud with an Arabic-first Car X interface.

## Vehicle-list diagnostics

- Authentication and vehicle retrieval are separate stages. A vehicle-list failure no longer says that login failed.
- Only an explicitly recognized empty array is described as an empty response from BYD. It is not proof that no vehicle is linked.
- The parser keeps support for a direct array and `diLinkAutoInfoList`, with bounded defensive `data` / `result` wrappers. The wrapper examples are synthetic compatibility cases, not captured BYD responses or an official API contract.
- Unknown/ambiguous shapes, invalid rows and server rejection have distinct outcomes. Incomplete records do not activate vehicle controls.
- After successful login, the account screen offers retry, region change and mainland QR scanning even if the list is unavailable.
- Copyable diagnostics include only fixed stage/status labels, known shape paths, counts, selected region and build version. Server messages, arbitrary keys, account IDs, phone numbers, VINs, credentials and QR values are omitted.
- Version `1.0.1-alpha4.2` / code `6` is shared by Gradle and the UI via `BuildConfig`.

## Existing features

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

Use JDK 17, Gradle 8.9, Android SDK 35 and Build Tools 34/35. GitHub Actions runs unit tests and lint, then builds an unsigned release on pull requests, pushes to `main`, or manual dispatch. See [BUILD_APK.md](BUILD_APK.md).

## Important

This is an independent interoperability project and is not an official BYD application. BYD trademarks and vehicle imagery belong to their respective owners.


## QR and SMS boundaries

- Existing QR routes and scan fields are retained. The camera sends the QR only to the configured BYD service; it does not navigate to the scanned URL.
- A QR payload without an explicit success status is no longer treated as authorized. Confirmation requires a scan-session ID and a separate user action. Back/outside cancellation also attempts the existing cancel route.
- BYD accepting QR confirmation is reported as request acceptance, not proof of ownership binding or successful login on the vehicle. The confirmation indicator is scoped to the current app/account session.
- The QR routes originate from the project's interoperability research. They are **not** presented as a public, official BYD developer API. Live QR/camera validation is still required, especially for server variants omitting status codes.
- SMS login is not implemented. The official [BYD app privacy policy](https://profilesys.bydauto.com.cn/byd/h5/app/privacys/2024-12-02.html) describes verification codes and password recovery but does not provide the endpoint/signature/challenge/session contract needed to implement SMS. No guessed SMS endpoint, CAPTCHA bypass or account-binding bypass is added.

## Earlier fixes retained

- Vehicle cloud state is no longer hard-coded to connected.
- The hero chip now means account session is logged in, not vehicle online state.
- Realtime refresh updates vehicle connection state dynamically when BYD returns it.
- QR scanning now runs inline in Car X instead of launching ZXing CaptureActivity, avoiding the immediate scanner crash seen on the test phone.
- Camera permission is requested explicitly before QR scanning.
