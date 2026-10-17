# StudyDock GitHub Content Delivery

StudyDock is an online-first native Kotlin Android app. It loads the official catalog at https://raw.githubusercontent.com/chilchiltrips-boop/studydock/main/content/catalog.json on startup and when Library > Sync is tapped.

There are no public textbook assets in this repository yet: publishing copyrighted source questions, diagrams and scans requires distribution rights. No dummy content is included.

## After obtaining rights to publish
1. Create a GitHub Release with tag content-p1-v1.
2. Upload the verified original StudyDock_Part1_Content.zip (root part1.json, all original images and checksums).
3. Calculate SHA-256 of the ZIP you uploaded: shasum -a 256 StudyDock_Part1_Content.zip.
4. Add an item to content/catalog.json parts:
   {"partId":1,"archiveUrl":"https://github.com/chilchiltrips-boop/studydock/releases/download/content-p1-v1/StudyDock_Part1_Content.zip","sha256":"64_lowercase_hex_digits"}
5. Commit the catalog to main. Tap Sync inside the installed StudyDock app. Subsequent content updates do not require APK rebuilds.

The downloader restricts URLs to this repository's GitHub Releases, follows only HTTPS GitHub asset redirects, checks the full archive SHA-256, and uses StudyLibrary's original image/MCQ verification before activation. Failed refreshes do not remove already verified local content.

## Levels 1-10
Only eligible verified source questions are divided across 10 levels, sorted by source page then unique question ID. Each level unlocks the next when at least 80% have been answered correctly once; 14 unresolved originals remain unscored. The app preserves mastered question IDs in private Android storage.

The source repository intentionally does not include the scanned textbook. Confirm rights to distribute before uploading an image-containing release. Real-device network/performance and signing/upgrade tests remain required.
