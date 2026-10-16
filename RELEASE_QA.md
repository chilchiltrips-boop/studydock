# Release QA — StudyDock native Android
- [x] Existing textbook question-model tests pass (local 11/11).
- [x] Native source has no WebView classes.
- [x] Package ID retained: com.studydock.learning.
- [x] Native source import contract includes SHA-256 validation.
- [ ] GitHub Actions native Gradle APK build green on this commit.
- [ ] Android physical touchscreen tests: navigation, image zoom, answer feedback and progress.
- [ ] Android tablets, small phones, landscape/foldable, rotation and large-font tests.
- [ ] 93 MB ZIP import endurance and rollback test on actual devices.
- [ ] Existing WebView user data migration/preservation acceptance.
- [ ] Production signing certificate matched and signed release produced.
Do not claim production readiness until the unchecked gates pass.
