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
assert 'android.permission.INTERNET' in manifest
remote = (ROOT / 'android/app/src/main/java/com/studydock/learning/RemoteStudySource.kt').read_text()
levels = (ROOT / 'android/app/src/main/java/com/studydock/learning/LevelEngine.kt').read_text()
catalog = (ROOT / 'content/catalog.json').read_text()
import json
assert 'raw.githubusercontent.com/chilchiltrips-boop/studydock/main/content/catalog.json' in remote
assert 'studydock/releases/download/' in remote and 'SHA-256' in remote
assert 'LEVEL_COUNT = 10' in levels
assert len(json.loads(catalog)['parts']) == 0
assert 'Levels 1–10' in activity and 'syncRemote()' in activity
guided = (ROOT / 'android/app/src/main/java/com/studydock/learning/GuidedLearning.kt').read_text()
assert 'GuidedLearningScreen' in activity and 'GuidedLearningSource' in activity
assert 'original-concept-learning' in guided
assert 'studydock_guided_levels' in guided
print('Native static structure checks passed')
