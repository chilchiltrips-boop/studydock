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

## Current debug build (v0.3.1-native, versionCode 4)
GitHub Actions builds on `main` and verifies APK signing and package identity before publishing the `StudyDock-Native-Android-debug-APK` artifact. This is a **debug-signed** APK, not the production release. Hosted Actions runners may create different debug signing certificates, so an in-place update from a previously installed debug APK is not guaranteed. Use the manual production signing workflow with the existing release keystore for a stable update signing identity.
