# How to get a downloadable StudyDock Android APK

This repository contains an automatic GitHub Actions build defined in `.github/workflows/build-android-apk.yml`.

1. Create an **empty** GitHub repository for StudyDock (separate from any drone-related projects).
2. Upload/commit the **contents** of `StudyDock_APK_Build_Ready.zip` to the repository root (including the hidden `.github/` directory), not the ZIP itself.
3. Open the GitHub repository > **Actions** > **Build StudyDock Android APK** > **Run workflow** (on the `main` branch).
4. Wait for the job to finish successfully. Open the completed run, scroll to **Artifacts**, and download **StudyDock-Android-debug-APK**.
5. Extract the downloaded artifact ZIP to find `app-debug.apk`. Transfer/install the APK on your Android device. Android may ask you to approve installations from that source.

The action also runs when relevant app/Android source files are pushed to `main`.

## What it produces

- A **debug signed** APK that can be installed for testing.
- It is **not** a production Play Store signed APK. Do not distribute it as a final production release.
- It includes demo questions and images only until verified textbook part data has been integrated and the APK rebuilt.

## If GitHub Actions does not appear

Check whether Actions is enabled for the repository and whether the `.github/workflows/build-android-apk.yml` file was uploaded as a real file in the default branch.

## Developer workstation alternative

Open `android/` in Android Studio, install Android SDK 35, use Gradle 8.9, and select **Build > Build APK(s)**. The debug APK should be at `android/app/build/outputs/apk/debug/app-debug.apk`.

## Build status

The CI workflow is provided but has **not** been executed in this environment. The APK will exist only after a successful build. The Python/container runtime available to ChatGPT here lacks Android SDK and Gradle and cannot reach the build-tool downloads.
