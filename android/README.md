# StudyDock — Native Android
A ZEBJUS LLP Product

Kotlin + Jetpack Compose Material 3. The Android APK is a pure native app, not a WebView/PWA.
The legacy app/ web prototype remains outside the Android build for historical reference.

The **online-first** native app reads the GitHub content catalog on launch and on Library Sync. Verified Part 1 ZIPs are distributed through GitHub Releases, pinned to a SHA-256 hash and checked by the existing importer. The catalog currently has no textbook asset: publish only material you have the legal right to distribute. Levels 1–10 use verified questions already present in downloaded content; 80% mastery of each level unlocks the next. No dummy questions are generated.
Android 7.0+ is configured (minSdk 24); requires Android SDK 35, Gradle 8.9 and Java 17.

Open android/ in Android Studio, or run gradle -p android :app:assembleDebug.
After build, use Library > Sync Part 1 from GitHub. See content/README.md for the rights-aware publishing workflow.
The public repository intentionally does not contain copyrighted textbook scans.
The import validates schema, cross-references, bounded ZIP paths/sizes and SHA-256 image hashes.

Application ID com.studydock.learning is retained, but upgrading an installed release
requires its original signing certificate and an increased versionCode.
Do not publish/debug-sign as if it were a production update.
Android compilation, real-device layout/accessibility and migration of existing WebView localStorage
must be verified before a stable production release.

## Current debug build (v0.3.1-native, versionCode 4)
GitHub Actions builds on `main` and verifies APK signing and package identity before publishing the `StudyDock-Native-Android-debug-APK` artifact. This is a **debug-signed** APK, not the production release. Hosted Actions runners may create different debug signing certificates, so an in-place update from a previously installed debug APK is not guaranteed. Use the manual production signing workflow with the existing release keystore for a stable update signing identity.

## StudyDock online guided learning v0.5.0
StudyDock downloads 10 original lessons and 50 authored concept-check questions from the GitHub content/learning/v1.json file. The native Android UI teaches three concepts per level before its five-question quiz. Score 80% or higher to unlock the next level. Progress stays local, and the supplementary original textbook database is not required. Android 7.0+ is configured; real-device verification is still required.


## NCLEX local archive
Open the Home-screen **NCLEX archive** tile and select the private ZIP. The on-device viewer supports RN/PN, INC subject, working drafts and source-only filters, full-text search, available figures and provisional draft rationales. It deliberately does not use the older 8-part verified-quiz importer. All 2,894 NCLEX entries remain **unscored**, since a final question-by-question clinical approval is not documented. The original 10 guided levels and their progress are untouched. For a reproducible pack, follow content/nclex/README.md.
