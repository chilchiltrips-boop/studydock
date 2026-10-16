# StudyDock — textbook learning MVP

**Status: working offline-first web prototype + Android Studio source project.** No production server, textbook content, or compiled APK is included.

## What is working

- Responsive desktop/tablet/mobile UI: Home, Study Notes, MCQ Practice, Image Quiz, Progress, Library, Import & Admin.
- Chapter notes with demo visual diagrams.
- One-correct-answer MCQ quiz engine, image-based MCQ support with image zoom, answer checking, explanations and hints.
- Randomized question selection, chapter/type/difficulty/count filters (5/10/20/50/100; limited by available questions).
- Results and incorrect-question revision saved to device localStorage.
- Eight import slots; JSON validation, cross-reference checks and merging into one persistent local library.
- Backup export and example JSON importer.
- PWA installation assets and offline web shell cache; Android Studio Kotlin WebView wrapper using the same web files.
- Future multi-user PostgreSQL/Supabase data model included in `database/schema.sql`.

## Run the web app (no npm or dependencies needed)

From the project folder:

```sh
python3 -m http.server 8080 --directory app
```

Open `http://localhost:8080`. You can also host `app/` directly on GitHub Pages or any static HTTPS host; service workers require HTTPS or localhost. The app itself is pure HTML/CSS/JavaScript with native ES modules.

**Do not simply double-click `index.html`: ES module loading needs a local HTTP server or Android's WebViewAssetLoader.**

## Android build

See `android/README.md`. The Android Studio project packages the same `app` files in `android/app/src/main/assets` and uses `androidx.webkit.WebViewAssetLoader` for a safe HTTPS-like origin. No native APK was built in this environment (Android SDK/Gradle are missing).

To sync edits into Android assets:

```sh
./scripts/sync-android-assets.sh
```

## Importing textbook content, one PDF part at a time

1. Extract and verify the real PDF part; this prototype **does not perform OCR or PDF extraction**.
2. Produce a JSON file following `app` → Import & admin → Sample JSON, with `schemaVersion=1`, `partId` 1–8, `firstPage`, `lastPage`, `chapters`, `notes`, `questions`, `images`.
3. Use stable IDs that do not collide with other parts; a chapter continuing across two parts should reuse its chapter ID.
4. Put actual original image files in `app/assets/` with paths of the form `assets/question-001.png` (then re-sync Android assets before building). Browser-only imports cannot copy referenced image files into the deployed static `assets` folder. Alternatively, a small image can be embedded as a `data:image/png;base64,...` URL, though localStorage quota limits apply.
5. For an original image-based question set `type:'image'`, `imageIds:['...']`, matching IDs in the images array. Record real original source page numbers and `origin:'textbook'`.
6. Set `status:'verified'` on a question **only after human or documented source verification**. The importer validates record structure and cross-references; it cannot verify truth, diagram correctness, or actual file existence automatically.
7. After import, check Library, Notes, MCQs and Image Quiz sections. Keep a separate checkpoint file outside browser storage.

The demo questions and sample diagrams are **not extracted from the user’s 1,317-page textbook**. The 8 textbook parts are pending, and the reported Part 1 metrics were not used as content.

## Project map

```
app/                   # deployable offline-first web application
  index.html, styles.css, app.js, model.mjs, seed.mjs
  assets/              # demo diagrams; add verified textbook assets here
  sw.js, manifest.webmanifest
android/               # Android Studio Kotlin WebView project
  app/src/main/assets/ # synchronized copy of web app
  app/src/main/java/    # native shell with file import/export
scripts/sync-android-assets.sh
database/schema.sql    # future PostgreSQL/Supabase relational schema
tests/model.test.mjs   # node built-in model tests
ARCHITECTURE.md        # complete development and release roadmap
```

## Tests

```sh
node --test tests/model.test.mjs
```

## MVP limitations and security cautions

- Browser localStorage is a prototype data store, not a scalable database for thousands of images or multi-user classrooms. Real deployments need authenticated cloud or device SQLite storage.
- The Admin screen has **no sign-in** and must not be exposed as a privileged production console. It only changes local prototype state.
- Question/answer keys are visible in client-side data, so current quiz scores are not tamper resistant. For official examinations, use authenticated server-side scoring.
- No original images or questions are included unless they are actually imported and bundled.
- The Android project hasn't been compiled or tested on a device; no APK or release signing is claimed.
- Rights to reproduce/distribute textbook images and text must be confirmed before sharing with students.
