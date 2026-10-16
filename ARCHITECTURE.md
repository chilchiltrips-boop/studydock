# StudyDock architecture and production roadmap

## MVP: shipped in this package

**Shared front end:** HTML/CSS/ES modules that run as a static PWA (web) and inside an Android Studio Kotlin WebView shell. Interactive screens and local import/export are operational without a server.

**Data layer:** Structured JavaScript/JSON objects for books, 8 parts, chapters, notes, source images, questions and quiz attempts. `app/model.mjs` validates imports and scores one-answer MCQs. `app/seed.mjs` contains only illustrative demo data. `app/assets/` contains sample SVGs created for this prototype, not source material from the user's book.

**Persistence:** LocalStorage holds imported JSON, local answer history and incorrect-question IDs; the offline web shell uses the Cache API and a service worker. Large source assets must be packaged or hosted separately.

**Android:** Native Kotlin activity loads the web app via `WebViewAssetLoader` at a synthetic HTTPS origin, permitting local ES module imports and DOM storage. Its file picker supports JSON import; a narrowly scoped JS bridge uses the Storage Access Framework for exports.

## Production target

```
PDF parts 1–8 (1,317 pages)
    ↓ extraction and human quality audit
Original text, images, Q&A, chapter notes
    ↓ verified JSON + image files (sha256, source traceability)
PostgreSQL / Supabase tables + object storage
    ↓ HTTPS authenticated API (role-based permissions, rate limits)
Web PWA / Android app  ← offline chapter packs in SQLite
    ↓
Question delivery → server-side grading → student progress analytics
```

**Data model:** `books → book_parts`, `books → chapters → topics`, `chapters → notes`, `chapters → questions → question_options`. Original source diagrams and graphs are normalized in `assets`, joined to notes/questions via `note_assets` and `question_assets`. `quiz_attempts/quiz_answers` store responses, and `user_roles` maps auth users to student/teacher/admin. Full SQL is in `database/schema.sql`.

**Import pipeline:** Use PDF page images and text to generate one import manifest per part with exact page range, chapter IDs, question IDs, options, correct choice, evidence tag, source page, image refs, origin and verification status. Ensure all original textbook questions are preserved; only eligible single-answer items become scored MCQs. Long answers remain in an expanded source question bank when added. A dedicated extraction pipeline is a separate phase, not something this app currently does.

**Recommended production steps:**

1. Attach and review Part 1 source PDF and its extracted files, validating the 135 indexed original questions, 14 non-scoreable originals and referenced images rather than copying screenshot metrics as data.
2. Add a teacher-only verification UI and an extraction audit table (page coverage, image presence, unresolved answers, checksums).
3. Set up Supabase Auth and Postgres with proper RLS, server-side import endpoints and protected storage for source images; avoid allowing regular students to upload or overwrite official textbook questions.
4. Add student accounts, synced progress, bookmarks and offline SQLite chapter pack download with per-book versioning.
5. Server-grade timed mock exams with authoritative scoring; protect answer keys until questions are submitted.
6. Accessible diagram zoom, alternative text, screen-reader support and Android on-device validation (screen rotations, file imports and connectivity).
7. Compile and sign a debug APK after Android SDK setup; test on real devices; publish production builds only after release QA and copyright authorization.

## Security and content integrity

Import validation prevents malformed question references and unsafe image paths, but is not a substitute for verifying diagrams, source provenance, answer truth or database security. All first-party assets should have hashes and reliable source page references. Cloud deployments need authenticated API upload endpoints, size limits, malware/content checks, server-side authorization, and backups. For classroom high-stakes assessments, server scoring and signed question bundles are recommended.
