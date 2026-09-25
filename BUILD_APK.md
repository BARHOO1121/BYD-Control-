# Building the APK

## Android Studio
1. Open the `CarXBYD` folder.
2. Allow Android Studio to install Android SDK 35 and Gradle dependencies.
3. Build > Build APK(s).
4. Output: `app/build/outputs/apk/debug/app-debug.apk`.

## GitHub Actions
1. Put this project in a GitHub repository.
2. Open Actions.
3. Select `Build Car X BYD APK`.
4. Run workflow.
5. Download artifact `CarX-BYD-debug`.

## Before customer distribution
- Test login on a dedicated BYD account first.
- Test on a single vehicle before enabling all remote controls.
- Verify Jordan (`JO`) region routing on the exact account.
- Replace debug signing with the Car X private signing key.
- Bundle/checksum `bangcle_tables.bin` instead of first-use download for production.
