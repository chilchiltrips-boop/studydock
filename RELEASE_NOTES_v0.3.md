# StudyDock v0.3.0 — Release notes

- Removed the 18 old demonstration questions and their 3 generic notes from the app starter library.
- Added 48 new source-linked, **adapted** nursing MCQs (4 diagram questions) with answer explanations.
- Added 13 detailed notes grouped under 6 study chapters and 4 new original schematic illustrations.
- Added question-source filtering: original textbook vs newly adapted.
- Retained local original textbook import support; preserved all previously imported Part 1 records through local-storage migration.
- Added StudyDock backup progress restore for debug APK reinstall scenarios.
- Upgraded Android version code from 2 to 3; package name unchanged.
- Validated Part 1 JSON import compatibility, preservation of 14 unresolved originals and stable IDs.

**Extraction scope:** Selected pages of Part 2 only, not all 150 PDF pages. This does not mark Part 2 extracted.

**Android signing:** Debug builds made on separate GitHub runners can use different signing keys. Back up local progress and keep Part 1 ZIP before reinstalling. Stable update signing is not yet configured.
