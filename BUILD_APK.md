# Build Car X BYD Alpha 4.2

Requires JDK 17 (including `javac` and `jlink`), Gradle 8.9, Android SDK Platform 35 and Android Build Tools 34.0.0 or 35.0.0. Set `ANDROID_HOME` and `JAVA_HOME` for your installation. Dependency downloads require network access on the first build.

GitHub Actions artifact: `CarX-BYD-1.0.1-alpha4.2-unsigned`.

Local Android Studio/Gradle command:

```bash
gradle :app:testDebugUnitTest :app:lintRelease :app:assembleRelease
```

APK output:

`app/build/outputs/apk/release/app-release-unsigned.apk`

The release has no signing configuration. Sign with your own key before installing; an unsigned APK cannot be installed directly. Keep `resources.arsc` and DEX entries stored, align before signing, and verify the final signature. Build number: `1.0.1-alpha4.2` / version code `6`.

Reports:

- Unit tests: `app/build/reports/tests/testDebugUnitTest/index.html`
- Lint: `app/build/reports/lint-results-release.html`
- Machine-readable unit results: `app/build/test-results/testDebugUnitTest/`

Tests use synthetic records only. They do not connect to BYD or issue vehicle commands. Local build success does not validate account binding, server compatibility, camera behavior, QR completion or vehicle controls on hardware.
