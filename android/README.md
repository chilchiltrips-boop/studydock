# StudyDock Android project

This is an Android Studio project wrapping the bundled offline StudyDock HTML/CSS/JS experience in a native Kotlin WebView. WebViewAssetLoader serves ES modules under a local HTTPS-like origin for secure module loading and localStorage. Its app assets are copied from `../app`.

Requirements: Android Studio (recent version), Android SDK platform 35, Gradle 8.9+, JDK 17, internet on the development machine for the Gradle plugins.

1. Open this `android` directory as a project in Android Studio.
2. Configure SDK 35 and Gradle 8.9 or later in Android Studio.
3. If Android Studio prompts for a missing Gradle wrapper, use an installed Gradle 8.9 to run `gradle wrapper --gradle-version 8.9`, or allow Android Studio to configure Gradle.
4. Build > Build APK(s). Expected debug artifact: `app/build/outputs/apk/debug/app-debug.apk`.
5. When changing the web app, copy `../app/*` into `app/src/main/assets/` before rebuilding.

No APK has been compiled here: this environment does not have Android SDK or Gradle installed.

Notes:
- Offline assets and localStorage are bundled with the APK. File inputs are supported via Android's document picker.
- Backup JSON download works through a narrow Android Storage Access Framework bridge.
- No internet permissions are required for the offline prototype.
- This is not an app-store-ready release. Add production sign-in, server sync, secure admin authorization, accessibility testing, licensing verification, and release signing before production.
