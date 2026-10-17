# StudyDock v0.3 — Fresh revision notes and questions

**What changed:** The default demo set (plants, water cycle and motion) has been replaced by **48 new adapted exam-style MCQs, 13 detailed study notes, 6 revision chapters and 4 original StudyDock schematic figures** covering selected topics from `TARGET HIGH BOOK 151-300.pdf` (Part 2 PDF pages 11–66). These are paraphrased, newly written practice resources **not** the source textbook's original numbered question bank and **not** a full 150-page extraction. They have been reviewed against the selected visible scan pages; clinical guidance must be checked against current standards.

This update keeps the original application ID `com.studydock.learning`, retains the original Part 1 local ZIP importer, and provides a migration that removes legacy demo records **without changing already imported Part 1 textbook questions, images, notes, unresolved originals or historical attempts**. A question-source selector distinguishes `Original textbook` from `New adapted questions`.

**Android debug-signing warning:** GitHub Actions builds may use a new ephemeral debug key on each run. If Android rejects installation over a previous testing APK, save a **StudyDock backup** first (Import & Admin → Export backup), keep your original Part 1 content ZIP, then reinstall, reimport Part 1 and use **Restore saved progress**. An in-place update cannot be guaranteed until a stable release key is configured. Do not uninstall before saving the content ZIP and exported backup.

**Privacy:** The public GitHub repository contains only newly written paraphrased educational content and StudyDock-owned concept diagrams. Original textbook scans, image files and numbered question text remain local and are not committed to GitHub. Verify redistribution permissions before sharing textbook content.

**Known limitations:** Real textbook content requires a local ZIP import; full Part 2 extraction has NOT been completed. Live clinical decision-making must not rely on older textbook material. Browser localStorage is limited; cloud multi-user scoring is not part of this prototype.

---

# StudyDock v0.2 — Android ZIP import

**Important:** This public source project contains NO textbook question, copyrighted image, or scan assets. The original textbook archive is imported directly on the user's Android phone and stored only in app-private storage.

Android setup: install the debug APK, open StudyDock → 8-part library → Import new part → **Import textbook ZIP + images**, select `StudyDock_Part1_Content.zip`, and wait for SHA-256 verification of all 355 original image files. Once import completes, 173 scored questions, 41 image-based quizzes, 105 study notes and the 14 unverified originals become available locally. The 14 unresolved questions are read-only and excluded from scored quizzes. Some originally video-based questions have only printed still-image evidence. Clinical information comes from a 2020 textbook and is NOT a current clinical protocol.

This is a prototype with locally stored content and scores, not a secure examination system. Publishing/distributing copyrighted textbook scans requires appropriate rights.

---

# StudyDock — textbook learning MVP

**Status: working offline-first web prototype + Android Studio source project.** No production server, textbook content, or compiled APK is included.

## What is working

- Responsive desktop/tablet/mobile UI: Home, Study Notes, MCQ Practice, Image Quiz, Progress, Library, Import & Admin.
- Structured starter nursing notes with four newly drawn schematic concept maps.
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

Starter practice questions are newly written adaptations of selected scanned Part 2 pages. The original Part 1 content remains a separate optional local import and is never silently replaced.

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
