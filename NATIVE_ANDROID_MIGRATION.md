# StudyDock native Android migration
StudyDock is a Kotlin/Jetpack Compose Android application branded "A ZEBJUS LLP Product".
Android screens include Home, Notes, Practice, Image Quiz, Progress and Library/Import.
The main Kotlin sources are android/app/src/main/java/com/studydock/learning/.
No Android WebView, JavaScript bridge, internet permission or service worker is required.
The app/ folder remains a legacy prototype and is not packaged in the APK.

## Offline textbook content
StudyDock_Part1_Content.zip is imported on device, not committed publicly. It holds Part 1
schemaVersion 1, 173 eligible single-answer questions, 355 scanned source assets, 14 unresolved
reference originals and 2 unsupported multi-select adaptations. Verify license to redistribute.

## Testing / limitations
The repository runs 11 preexisting model tests, a static Kotlin contract check,
and GitHub Actions Gradle compile. Real Android touch, TalkBack, 5-second splash behavior,
rotation, low-memory ZIP import and data migration require device acceptance.

## Signing
Retain application ID com.studydock.learning and production signing certificate to update
installed versions. Set STUDYDOCK_KEYSTORE_FILE, STUDYDOCK_KEYSTORE_PASSWORD,
STUDYDOCK_KEY_ALIAS, STUDYDOCK_KEY_PASSWORD and optionally STUDYDOCK_VERSION_CODE.
Never commit keystores or signing passwords. Android doesn't provide a general
key-based prevention of voluntary app uninstallation.
