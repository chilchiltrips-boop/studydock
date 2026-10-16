# StudyDock — Native Android
A ZEBJUS LLP Product

Kotlin + Jetpack Compose Material 3. The Android APK is a pure native app, not a WebView/PWA.
The legacy app/ web prototype remains outside the Android build for historical reference.
Android 7.0+ is configured (minSdk 24); requires Android SDK 35, Gradle 8.9 and Java 17.

Open android/ in Android Studio, or run gradle -p android :app:assembleDebug.
After build, choose Library > Import Part 1 ZIP to import the separate StudyDock_Part1_Content.zip.
The public repository intentionally does not contain copyrighted textbook scans.
The import validates schema, cross-references, bounded ZIP paths/sizes and SHA-256 image hashes.

Application ID com.studydock.learning is retained, but upgrading an installed release
requires its original signing certificate and an increased versionCode.
Do not publish/debug-sign as if it were a production update.
Android compilation, real-device layout/accessibility and migration of existing WebView localStorage
must be verified before a stable production release.
