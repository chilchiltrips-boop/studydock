# StudyDock production signing - GitHub Actions

A ZEBJUS LLP Product. The manual-only GitHub Actions workflow is in .github/workflows/build-android-release.yml. The signed APK, not a keystore, is uploaded as an artifact. The existing debug workflow stays unchanged.

## 1. Reuse the existing certificate if users already have StudyDock installed
An update without uninstalling needs the SAME Android signing identity for the application ID com.studydock.learning, and a higher versionCode. Do not generate a different key for an already-distributed production APK. A new release key cannot directly update a GitHub Actions debug-key-signed APK. Back up student progress before any debug-to-release migration. Inspect the previous APK locally with:

    apksigner verify --print-certs previous.apk

## 2. If no release keystore exists, generate it on a trusted local computer

    keytool -genkeypair -v -keystore studydock-release.jks -alias studydock -keyalg RSA -keysize 3072 -validity 10000

Keep redundant secure offline backups of the JKS, the signing alias and passwords. Never commit or share private keys. To prepare the base64 secret:

    base64 < studydock-release.jks | tr -d '\n'

To read the certificate SHA-256 fingerprint (this is NOT the SHA256 hash of the JKS file):

    keytool -list -v -keystore studydock-release.jks -alias studydock

## 3. Enter protected settings in GitHub yourself
At https://github.com/chilchiltrips-boop/studydock/settings/secrets/actions > Repository secrets, create:
- STUDYDOCK_KEYSTORE_BASE64 : single-line base64 keystore
- STUDYDOCK_KEYSTORE_PASSWORD : keystore password
- STUDYDOCK_KEY_ALIAS : key alias
- STUDYDOCK_KEY_PASSWORD : key password

Under GitHub Actions > Variables, create:
- STUDYDOCK_SIGNING_CERT_SHA256 : certificate SHA256 fingerprint from keytool -list -v, hexadecimal, with or without colons.

Do not paste passwords or keystore contents into chat. GitHub app permissions do not allow setting secrets from this chat.

## 4. Run the workflow
Open Actions > StudyDock Production Signed APK > Run workflow, choose branch main. Supply:
- version_code : strictly greater than all earlier installed/released app versionCodes, e.g. 4 only if none is already 4 or higher.
- version_name : version label e.g. 1.0.0.

Successful build produces the downloadable StudyDock-Production-Signed-APK artifact. Missing secrets, malformed version/fingerprint, signature mismatch, incorrect package ID or incorrect version code all prevent publication. No unsigned fallback.

## 5. Pre-release acceptance
Test Android installation/update on existing devices, offline textbook ZIP import, screen sizes, progress retention and previous WebView-to-native storage migration before wide release. A successful GitHub Gradle build does not prove real-device behavior. For Google Play App Signing, distinguish its upload key from the Play app-signing key. No Android signing key can universally prevent app uninstallation. Textbook scans are intentionally not committed to the public repository.
