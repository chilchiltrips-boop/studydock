"""Static source checks. Native Gradle CI and physical devices require separate acceptance."""
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
activity = (ROOT / 'android/app/src/main/java/com/studydock/learning/MainActivity.kt').read_text()
data = (ROOT / 'android/app/src/main/java/com/studydock/learning/StudyData.kt').read_text()
gradle = (ROOT / 'android/app/build.gradle.kts').read_text()
manifest = (ROOT / 'android/app/src/main/AndroidManifest.xml').read_text()
assert 'android.webkit' not in activity
assert not re.search(r'\bWebView\b\s*\(', activity)
assert 'A ZEBJUS LLP Product' in activity
assert 'com.studydock.learning' in gradle
assert 'minSdk = 24' in gradle and 'compose = true' in gradle
assert 'SHA-256' in data and 'unresolvedOriginalQuestions' in data
assert 'assets/images/' in data
assert 'INTERNET' not in manifest
print('Native static structure checks passed')
