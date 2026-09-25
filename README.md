# Car X BYD — Android Alpha 2 China

Native Android build of **Car X BYD Cloud** with a dedicated mainland-China authentication stack.

## Alpha 2 changes

- Mainland China mode is the default.
- Chinese phone accounts are supported; `+86` / `0086` is stripped locally before login.
- China endpoint: `https://dilinksuperappserver-cn.byd.auto`.
- China login endpoint: `/app/auth/login`.
- Native WBSK white-box envelope codec for the mainland app flow.
- WBSK golden-vector self-test runs **before credentials are sent**.
- China request headers: `version`, `platform: ANDROID`, `BrandFlag: dynasty`.
- China session handles `superId`, brand user id, `signToken`, and `encryToken` / `encryptToken`.
- China vehicle list uses `/app/auth/getAllListByUserId`.
- Overseas/global mode remains available for non-China accounts.
- Android Keystore AES-GCM protects locally saved username/password/control PIN.
- Realtime and remote-control foundation retained from Alpha 1.

## Safety during testing

Do not repeatedly submit credentials after an authentication rejection. Alpha 2 validates the WBSK engine locally before making the BYD login request.

## Build

Push to GitHub and run **Build Car X BYD APK**. The workflow produces the `CarX-BYD-Alpha2-China` artifact containing `app-debug.apk`.

Configuration:
- minSdk 26
- targetSdk 35
- Java 17
- no AndroidX/runtime third-party dependencies

## Sources / interoperability references

- `jkaberg/pyBYD` — MIT, overseas protocol/session behavior and public CN design work.
- `jkaberg/hass-byd-vehicle` — MIT, region/capability reference.
- Public BYD-re CN interoperability research was consulted for the CN wire format and WBSK test vectors.

Car X app code is a native Android implementation.
